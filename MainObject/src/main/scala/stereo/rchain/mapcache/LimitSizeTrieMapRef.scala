package stereo.rchain.mapcache

import cats.syntax.all._
import scala.annotation.tailrec
import cats.effect.Sync
import cats.effect.concurrent.Ref


case class CacheItem[V, K](value: V, mayBeNextKey: Option[K], mayBePrevKey: Option[K]) {
  def setNextKey(mayBeNextKey: Option[K]): CacheItem[V, K] = {
    CacheItem(this.value, mayBeNextKey, this.mayBePrevKey)
  }
}


case class TrieMapCache[V, K](val sizeLimit: Int,
                              val records: Map[K, CacheItem[V, K]] = Map.empty[K, CacheItem[V, K]],
                              val mayBeTopKey: Option[K] = None,
                              val mayBeBottomKey: Option[K] = None) {
  def setValueByKey(key: K, value: V): TrieMapCache[V, K] = {
    val record = this.records(key)
    val records = this.records + (key -> CacheItem(value, record.mayBeNextKey, record.mayBePrevKey))
    TrieMapCache(this.sizeLimit, records, this.mayBeTopKey, this.mayBeBottomKey)
  }

  private def removeLastRecord(): TrieMapCache[V, K] = {
    val key = this.mayBeBottomKey.get
    TrieMapCache(this.sizeLimit, this.records - key, this.mayBeTopKey, this.records(key).mayBeNextKey)
  }

  @tailrec
  private def removeLastRecords(count: Int): TrieMapCache[V, K] = {
    if (0 < count) this.removeLastRecord().removeLastRecords(count - 1)
    else this.copy()
  }

  def cleanOldRecords(): TrieMapCache[V, K] = {
    if (this.sizeLimit < this.records.size) this.removeLastRecords(this.records.size - (0.7 * this.sizeLimit).toInt)
    else this.copy()
  }
}

final case class TrieMapCacheRef[F[_]: Sync, V, K](val sizeLimit: Int) {
  val refToCache: F[Ref[F, TrieMapCache[V, K]]] = Ref[F].of(TrieMapCache[V, K](sizeLimit))

  class ExtendedMap(cache: Map[K, CacheItem[V, K]]) {
    def update(item: Option[(K, CacheItem[V, K])]): Map[K, CacheItem[V, K]] =
      (for {record <- item} yield cache + record).getOrElse(cache)
  }
  implicit def mapToMap(cache: Map[K, CacheItem[V, K]]): ExtendedMap = new ExtendedMap(cache)

  private def moveRecordOnTop(cache: TrieMapCache[V, K], key: K): TrieMapCache[V, K] = {
    val currentMapValue = cache.records(key)

    val currentRecord = key -> CacheItem(currentMapValue.value, None, cache.mayBeTopKey)
    val mayBeNextRecord = for {key <- currentMapValue.mayBeNextKey} yield(key -> cache.records(key))
    val meyBePrevRecord = for {key <- currentMapValue.mayBePrevKey} yield(key -> cache.records(key))
    val topRecord= cache.mayBeTopKey.get -> cache.records(cache.mayBeTopKey.get).setNextKey(Some(key))

    val bottomKey = cache.mayBeBottomKey.get
    val mayBeBottomKey = bottomKey match {
      case bottomKey if bottomKey == key && cache.records(key).mayBeNextKey.isDefined => cache.records(key).mayBeNextKey
      case _ => cache.mayBeBottomKey
    }

    val records = (cache.records + currentRecord + topRecord).update(mayBeNextRecord).update(meyBePrevRecord)

    TrieMapCache(cache.sizeLimit, records, Some(key), mayBeBottomKey)
  }

  def get(key: K): F[Option[V]] = {
    for {
      refToCacheValue <- refToCache
      value <- refToCacheValue.modify(cache => {
        val mayBeValue = cache.records.get(key)
        mayBeValue match {
          case None => (cache, None)
          case _ => (moveRecordOnTop(cache, key), Some(mayBeValue.get.value))
        }
      })
    } yield(value)
  }

  def set(key: K, value: V): F[Unit] = {
    for {
      refToCacheValue <- refToCache
      _ <- refToCacheValue.update(cache => {moveRecordOnTop(cache.setValueByKey(key, value), key).cleanOldRecords()})
    } yield()
  }
}

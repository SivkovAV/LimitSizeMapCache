package stereo.rchain.mapcache

import cats.syntax.all._
import cats.effect.Sync
import cats.effect.concurrent.Ref


case class CacheItem[K, V](value: V, mayBeNextKey: Option[K], mayBePrevKey: Option[K]) {
  def setNextKey(mayBeKey: Option[K]): CacheItem[K, V] = {
    CacheItem(value, mayBeKey, mayBePrevKey)
  }

  def setPrevKey(mayBeKey: Option[K]): CacheItem[K, V] = {
    CacheItem(value, mayBeNextKey, mayBeKey)
  }
}


case class TrieMapCache[K, V](val sizeLimit: Int, val reducingFactor: Double = 0.7,
                              val records: Map[K, CacheItem[K, V]] = Map.empty[K, CacheItem[K, V]],
                              val mayBeTopKey: Option[K] = None,
                              val mayBeBottomKey: Option[K] = None) {
  class ExtendedMap(cache: Map[K, CacheItem[K, V]]) {
    def update(mayBeItem: Option[(K, CacheItem[K, V])]): Map[K, CacheItem[K, V]] =
      mayBeItem.map(item => cache + item).getOrElse(cache)
  }
  implicit def mapToMap(cache: Map[K, CacheItem[K, V]]): ExtendedMap = new ExtendedMap(cache)

  def updateOnTop(key: K, value: V): TrieMapCache[K, V] = {
    if (!records.contains(key)) addValueByKeyOnTop(key, value)
    else setValueByKey(key, value).moveRecordOnTop(key)
  }

  private def addValueByKeyOnTop(key: K, value: V): TrieMapCache[K, V] = {
    val currentRecord = key -> CacheItem(value, None, mayBeTopKey)
    val mayBeTopRecord = for {topKey <- mayBeTopKey; item = topKey -> records(topKey).setNextKey(Some(key))} yield(item)
    val newRecords = (records + currentRecord).update(mayBeTopRecord)
    val newMayBeTopKey = Some(key)
    val newMayBeBottomKey = Some(mayBeBottomKey.getOrElse(key))
    TrieMapCache(sizeLimit, reducingFactor, newRecords, newMayBeTopKey, newMayBeBottomKey)
  }

  private def setValueByKey(key: K, value: V): TrieMapCache[K, V] = {
    val record = records(key)
    val item = key -> CacheItem(value, record.mayBeNextKey, record.mayBePrevKey)
    val newRecords = records + item
    TrieMapCache(sizeLimit, reducingFactor, newRecords, mayBeTopKey, mayBeBottomKey)
  }

  def moveRecordOnTop(key: K): TrieMapCache[K, V] = {
    if (mayBeTopKey.isDefined && mayBeTopKey.get==key)
      this
    else {
      val mapValue = records(key)
      val currentRecord = key -> CacheItem(mapValue.value, None, mayBeTopKey)
      val topRecord = mayBeTopKey.get -> records(mayBeTopKey.get).setNextKey(Some(key))
      val newRecords = records + currentRecord + topRecord

      val mayBeNextRecord = for {nk <- mapValue.mayBeNextKey; pk = mapValue.mayBePrevKey; r = nk -> newRecords(nk).setPrevKey(pk)} yield (r)
      val mayBePrevRecord = for {pk <- mapValue.mayBePrevKey; nk = mapValue.mayBeNextKey; r = pk -> newRecords(pk).setNextKey(nk)} yield (r)

      val newMayBeBottomKey = mayBeBottomKey match {
        case mayBeBottomKey if mayBeBottomKey.get == key && records(key).mayBeNextKey.isDefined => records(key).mayBeNextKey
        case _ => mayBeBottomKey
      }
      val finalRecords = newRecords.update(mayBeNextRecord).update(mayBePrevRecord)

      TrieMapCache(sizeLimit, reducingFactor, finalRecords, Some(key), newMayBeBottomKey)
    }
  }

  private def removeLastRecord(): TrieMapCache[K, V] = {
    val key = mayBeBottomKey.get
    TrieMapCache(sizeLimit, reducingFactor, records - key, mayBeTopKey, records(key).mayBeNextKey)
  }

  private def removeLastRecords(count: Int): TrieMapCache[K, V] = {
    if (0 < count) {
      val cache = this.removeLastRecord().removeLastRecords(count - 1)
      val bottomKey = cache.mayBeBottomKey.get
      val newBottomKey = bottomKey -> cache.records(bottomKey).setPrevKey(None)
      val records = cache.records.update(Some(newBottomKey))
      TrieMapCache[K, V](cache.sizeLimit, cache.reducingFactor, records, cache.mayBeTopKey, cache.mayBeBottomKey)
    }
    else this.copy()
  }

  def cleanOldRecords(): TrieMapCache[K, V] = {
    if (this.sizeLimit < this.records.size) this.removeLastRecords(this.records.size - (0.7 * this.sizeLimit).toInt)
    else this.copy()
  }
}


case class TrieMapCacheRef[F[_]: Sync, K, V](val refToCache: Ref[F, TrieMapCache[K, V]]) {
  def get(key: K): F[Option[V]] = {
    for {
      value <- refToCache.modify(cache => {
        val mayBeValue = cache.records.get(key)
        mayBeValue match {
          case None => (cache, None)
          case _ => (cache.moveRecordOnTop(key), Some(mayBeValue.get.value))
        }
      })
    } yield(value)
  }

  def set(key: K, value: V): F[Unit] = {
    for {
      _ <- refToCache.update(cache => {cache.updateOnTop(key, value).cleanOldRecords()})
    } yield()
  }
}

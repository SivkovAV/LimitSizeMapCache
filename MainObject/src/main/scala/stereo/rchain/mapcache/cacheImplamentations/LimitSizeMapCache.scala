package stereo.rchain.mapcache.cacheImplamentations

import cats.effect.Sync
import cats.effect.concurrent.Ref


/**
 * [[LimitSizeMapCacheItem]] is minimal data item for [[LimitSizeMapCacheState]]. It's immutable structure.
 * It stores "user value" and system data about item's position inside [[LimitSizeMapCacheState]].
 *
 * @param value - user value
 * @param mayBeNextKey - key for higher [[LimitSizeMapCacheItem]] or None if item is top inside [[LimitSizeMapCacheState]]
 * @param mayBePrevKey - key for lower [[LimitSizeMapCacheItem]] or None if item is bottom inside [[LimitSizeMapCacheState]]
 */
case class LimitSizeMapCacheItem[K, V](value: V, mayBeNextKey: Option[K] = None, mayBePrevKey: Option[K] = None) {
  def setNextKey(mayBeKey: Option[K]): LimitSizeMapCacheItem[K, V] = {
    LimitSizeMapCacheItem(value, mayBeKey, mayBePrevKey)
  }

  def setPrevKey(mayBeKey: Option[K]): LimitSizeMapCacheItem[K, V] = {
    LimitSizeMapCacheItem(value, mayBeNextKey, mayBeKey)
  }
}


/**
 * [[LimitSizeMapCacheState]] is functional style key-value storage.
 * It's like immutable [[scala.collection.immutable.Map]] with limited [[Map.size]].
 * If we will try to build [[LimitSizeMapCacheState]] with items count more then [[maxItemCount]]
 * we will have [[LimitSizeMapCacheState]] with items count of [[itemCountAfterSizeCorrection]]; items will contents of
 * most fresh source items.
 *
 * @param maxItemCount - maximum count of [[items]]'s elements
 * @param itemCountAfterSizeCorrection - count of [[items]]'s elements after [[items]] size correction
 * @param items - key-value style storage for items
 * @param mayBeTopKey - top element key or None if storage is empty
 * @param mayBeBottomKey - bottom element key or None if storage is empty
 */
case class LimitSizeMapCacheState[K, V](val maxItemCount: Int, val itemCountAfterSizeCorrection: Int,
                                        val items: Map[K, LimitSizeMapCacheItem[K, V]] = Map.empty[K, LimitSizeMapCacheItem[K, V]],
                                        val mayBeTopKey: Option[K] = None,
                                        val mayBeBottomKey: Option[K] = None) {
  class ExtendedMap(state: Map[K, LimitSizeMapCacheItem[K, V]]) {
    def update(mayBeItem: Option[(K, LimitSizeMapCacheItem[K, V])]): Map[K, LimitSizeMapCacheItem[K, V]] =
      mayBeItem.map(item => state + item).getOrElse(state)
  }
  implicit def mapToMap(state: Map[K, LimitSizeMapCacheItem[K, V]]): ExtendedMap = new ExtendedMap(state)

  def updateOnTop(key: K, value: V): LimitSizeMapCacheState[K, V] = {
    if (!items.contains(key)) addValueByKeyOnTop(key, value)
    else setValueByKey(key, value).moveRecordOnTop(key)
  }

  private def addValueByKeyOnTop(key: K, value: V): LimitSizeMapCacheState[K, V] = {
    val currentRecord = key -> LimitSizeMapCacheItem(value, None, mayBeTopKey)
    val mayBeTopRecord = for {topKey <- mayBeTopKey; item = topKey -> items(topKey).setNextKey(Some(key))} yield(item)
    val newRecords = (items + currentRecord).update(mayBeTopRecord)
    val newMayBeTopKey = Some(key)
    val newMayBeBottomKey = Some(mayBeBottomKey.getOrElse(key))
    LimitSizeMapCacheState(maxItemCount, itemCountAfterSizeCorrection, newRecords, newMayBeTopKey, newMayBeBottomKey)
  }

  private def setValueByKey(key: K, value: V): LimitSizeMapCacheState[K, V] = {
    val record = items(key)
    val item = key -> LimitSizeMapCacheItem(value, record.mayBeNextKey, record.mayBePrevKey)
    val newRecords = items + item
    LimitSizeMapCacheState(maxItemCount, itemCountAfterSizeCorrection, newRecords, mayBeTopKey, mayBeBottomKey)
  }

  def moveRecordOnTop(key: K): LimitSizeMapCacheState[K, V] = {
    if (mayBeTopKey.isDefined && mayBeTopKey.get==key)
      this
    else {
      val mapValue = items(key)
      val currentRecord = key -> LimitSizeMapCacheItem(mapValue.value, None, mayBeTopKey)
      val topRecord = mayBeTopKey.get -> items(mayBeTopKey.get).setNextKey(Some(key))
      val newRecords = items + currentRecord + topRecord

      val mayBeNextRecord = for {nk <- mapValue.mayBeNextKey; pk = mapValue.mayBePrevKey; r = nk -> newRecords(nk).setPrevKey(pk)} yield (r)
      val mayBePrevRecord = for {pk <- mapValue.mayBePrevKey; nk = mapValue.mayBeNextKey; r = pk -> newRecords(pk).setNextKey(nk)} yield (r)

      val newMayBeBottomKey = mayBeBottomKey match {
        case mayBeBottomKey if mayBeBottomKey.get == key && items(key).mayBeNextKey.isDefined => items(key).mayBeNextKey
        case _ => mayBeBottomKey
      }
      val finalRecords = newRecords.update(mayBeNextRecord).update(mayBePrevRecord)

      LimitSizeMapCacheState(maxItemCount, itemCountAfterSizeCorrection, finalRecords, Some(key), newMayBeBottomKey)
    }
  }

  private def removeLastRecord(): LimitSizeMapCacheState[K, V] = {
    val key = mayBeBottomKey.get
    LimitSizeMapCacheState(maxItemCount, itemCountAfterSizeCorrection, items - key, mayBeTopKey, items(key).mayBeNextKey)
  }

  private def removeLastRecords(count: Int): LimitSizeMapCacheState[K, V] = {
    if (0 < count) {
      val state = this.removeLastRecord().removeLastRecords(count - 1)
      val bottomKey = state.mayBeBottomKey.get
      val newBottomKey = bottomKey -> state.items(bottomKey).setPrevKey(None)
      val records = state.items.update(Some(newBottomKey))
      LimitSizeMapCacheState[K, V](state.maxItemCount, state.itemCountAfterSizeCorrection, records, state.mayBeTopKey, state.mayBeBottomKey)
    }
    else this.copy()
  }

  def cleanOldItems(): LimitSizeMapCacheState[K, V] = {
    if (this.maxItemCount < this.items.size) this.removeLastRecords(this.items.size - (0.7 * this.maxItemCount).toInt)
    else this.copy()
  }
}


/**
 * [[LimitSizeMapCache]] - multi thread version of [[LimitSizeMapCacheState]]
 *
 * @param state - instance of [[LimitSizeMapCacheState]] covered in cats.Ref for multi thread safety
 */
case class LimitSizeMapCache[F[_]: Sync, K, V](val state: Ref[F, LimitSizeMapCacheState[K, V]]) {
  def get(key: K): F[Option[V]] = {
    state.modify(state => {
      val mayBeValue = state.items.get(key)
      mayBeValue match {
        case None => (state, None)
        case _ => (state.moveRecordOnTop(key), Some(mayBeValue.get.value))
      }
    })
  }

  def set(key: K, value: V): F[Unit] = state.update(state => {state.updateOnTop(key, value).cleanOldItems()})
}

package stereo.rchain.limitsizemapcache.cacheImplamentations

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.syntax.all._


/**
 * [[LimitSizeMapItemValue]] is minimal data item for [[LimitSizeMapCacheState]]. It's immutable structure.
 * It stores "user value" and system data about item's position inside [[LimitSizeMapCacheState]].
 *
 * @param value        - user value
 * @param mayBeNextKey - key for higher [[LimitSizeMapItemValue]] or None if item is top inside [[LimitSizeMapCacheState]]
 * @param mayBePrevKey - key for lower [[LimitSizeMapItemValue]] or None if item is bottom inside [[LimitSizeMapCacheState]]
 */
case class LimitSizeMapItemValue[K, V](value: V, mayBeNextKey: Option[K] = None, mayBePrevKey: Option[K] = None) {
  def setNextKey(mayBeKey: Option[K]): LimitSizeMapItemValue[K, V] = {
    LimitSizeMapItemValue(value, mayBeKey, mayBePrevKey)
  }

  def setPrevKey(mayBeKey: Option[K]): LimitSizeMapItemValue[K, V] = {
    LimitSizeMapItemValue(value, mayBeNextKey, mayBeKey)
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
                                        val items: Map[K, LimitSizeMapItemValue[K, V]] = Map.empty[K, LimitSizeMapItemValue[K, V]],
                                        val mayBeTopKey: Option[K] = None,
                                        val mayBeBottomKey: Option[K] = None) {
  class ExtendedMap(state: Map[K, LimitSizeMapItemValue[K, V]]) {
    def update(mayBeItem: Option[(K, LimitSizeMapItemValue[K, V])]): Map[K, LimitSizeMapItemValue[K, V]] =
      mayBeItem.map(item => state + item).getOrElse(state)
  }
  implicit def mapToMap(state: Map[K, LimitSizeMapItemValue[K, V]]): ExtendedMap = new ExtendedMap(state)

  def modify(key: K): (LimitSizeMapCacheState[K, V], Option[V]) = {
    val mayBeValue = items.get(key)
    mayBeValue match {
      case None => (this, None)
      case _ => (moveItemOnTop(key), Some(mayBeValue.get.value))
    }
  }

  def update(key: K, value: V): LimitSizeMapCacheState[K, V] = {
    updateOnTop(key, value).cleanOldItems()
  }

  private def updateOnTop(key: K, value: V): LimitSizeMapCacheState[K, V] = {
    if (!items.contains(key)) addValueByKeyOnTop(key, value)
    else setValueByKey(key, value).moveItemOnTop(key)
  }

  private def addValueByKeyOnTop(key: K, value: V): LimitSizeMapCacheState[K, V] = {
    val currentItem = key -> LimitSizeMapItemValue(value, None, mayBeTopKey)
    val mayBeTopItem = for {topKey <- mayBeTopKey; item = topKey -> items(topKey).setNextKey(Some(key))} yield(item)
    val newItems = (items + currentItem).update(mayBeTopItem)
    val newMayBeTopKey = Some(key)
    val newMayBeBottomKey = Some(mayBeBottomKey.getOrElse(key))
    LimitSizeMapCacheState(maxItemCount, itemCountAfterSizeCorrection, newItems, newMayBeTopKey, newMayBeBottomKey)
  }

  private def setValueByKey(key: K, value: V): LimitSizeMapCacheState[K, V] = {
    val itemValue = items(key)
    val item = key -> LimitSizeMapItemValue(value, itemValue.mayBeNextKey, itemValue.mayBePrevKey)
    val newItems = items + item
    LimitSizeMapCacheState(maxItemCount, itemCountAfterSizeCorrection, newItems, mayBeTopKey, mayBeBottomKey)
  }

  private def moveItemOnTop(key: K): LimitSizeMapCacheState[K, V] = {
    if (mayBeTopKey.isDefined && mayBeTopKey.get==key)
      this
    else {
      val mapValue = items(key)
      val currentItem = key -> LimitSizeMapItemValue(mapValue.value, None, mayBeTopKey)
      val topItem = mayBeTopKey.get -> items(mayBeTopKey.get).setNextKey(Some(key))
      val newItems = items + currentItem + topItem

      val mayBeNextItem = for {nk <- mapValue.mayBeNextKey; pk = mapValue.mayBePrevKey; r = nk -> newItems(nk).setPrevKey(pk)} yield (r)
      val mayBePrevItem = for {pk <- mapValue.mayBePrevKey; nk = mapValue.mayBeNextKey; r = pk -> newItems(pk).setNextKey(nk)} yield (r)

      val newMayBeBottomKey = mayBeBottomKey match {
        case mayBeBottomKey if mayBeBottomKey.get == key && items(key).mayBeNextKey.isDefined => items(key).mayBeNextKey
        case _ => mayBeBottomKey
      }
      val finalItems = newItems.update(mayBeNextItem).update(mayBePrevItem)

      LimitSizeMapCacheState(maxItemCount, itemCountAfterSizeCorrection, finalItems, Some(key), newMayBeBottomKey)
    }
  }

  private def removeOldItem(): LimitSizeMapCacheState[K, V] = {
    val key = mayBeBottomKey.get
    LimitSizeMapCacheState(maxItemCount, itemCountAfterSizeCorrection, items - key, mayBeTopKey, items(key).mayBeNextKey)
  }

  private def removeOldItems(itemCountForRemoving: Int): LimitSizeMapCacheState[K, V] = {
    if (0 < itemCountForRemoving) {
      val state = this.removeOldItem().removeOldItems(itemCountForRemoving - 1)
      val bottomKey = state.mayBeBottomKey.get
      val newBottomKey = bottomKey -> state.items(bottomKey).setPrevKey(None)
      val newItems = state.items.update(Some(newBottomKey))
      LimitSizeMapCacheState[K, V](state.maxItemCount, state.itemCountAfterSizeCorrection,
        newItems, state.mayBeTopKey, state.mayBeBottomKey)
    }
    else this.copy()
  }

  private def cleanOldItems(): LimitSizeMapCacheState[K, V] = {
    if (this.maxItemCount < this.items.size) this.removeOldItems(this.items.size - itemCountAfterSizeCorrection)
    else this.copy()
  }
}


/**
 * [[LimitSizeMapCache]] - multi thread version of [[LimitSizeMapCacheState]]
 *
 * @param stateRef - instance of [[LimitSizeMapCacheState]] covered in cats.Ref for multi thread safety
 */
case class LimitSizeMapCache[F[_]: Sync, K, V](val stateRef: Ref[F, LimitSizeMapCacheState[K, V]]) {
  def get(key: K): F[Option[V]] = stateRef.modify(state => state.modify(key))
  def set(key: K, value: V): F[Unit] = stateRef.update(state => {state.update(key, value)})
}


object LimitSizeMapCache {
  def apply[F[_]: Sync, K, V](maxItemCount: Int, itemCountAfterSizeCorrection: Int): F[LimitSizeMapCache[F, K, V]] = {
    assert(maxItemCount >= itemCountAfterSizeCorrection)
    assert(itemCountAfterSizeCorrection >= 0)
    for {
      ref <- Ref.of(LimitSizeMapCacheState[K, V](maxItemCount, itemCountAfterSizeCorrection))
      cache = new LimitSizeMapCache(ref)
    } yield (cache)
  }
}

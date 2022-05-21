package stereo.rchain.mapcache.cacheImplamentations

import cats.effect.Sync
import cats.effect.concurrent.Ref


/**
 * [[CustomCacheItem]] is element of LimitSizeTrieMapRef.
 * It store "user value" and data about element position inside LimitSizeTrieMapRef
 *
 * @param value - stored user value
 * @param mayBeNextKey - pointer to higher CacheItem or None if current item is top item
 * @param mayBePrevKey - pointer to lower CacheItem or None if current item is bottom item
 */
case class CustomCacheItem[K, V](value: V, mayBeNextKey: Option[K], mayBePrevKey: Option[K]) {
  def setNextKey(mayBeKey: Option[K]): CustomCacheItem[K, V] = {
    CustomCacheItem(value, mayBeKey, mayBePrevKey)
  }

  def setPrevKey(mayBeKey: Option[K]): CustomCacheItem[K, V] = {
    CustomCacheItem(value, mayBeNextKey, mayBeKey)
  }
}


/**
 * [[LimitSizeMapCacheState]] is functional style key-value cache. It's like Map with limited size.
 * If count of cache elements will be more then limited size, several old element will been remove.
 *
 * @param sizeLimit - maximum count of cache elements (limited size)
 * @param reducingFactor - cache size reduction factor for the case if the number of elements exceeds the sizeLimit
 * @param records - key-value style storage with cache elements
 * @param mayBeTopKey - cache top element key or None if cache is empty
 * @param mayBeBottomKey - cache bottom element key or None if cache is empty
 */
case class LimitSizeMapCacheState[K, V](val sizeLimit: Int, val reducingFactor: Double = 0.7,
                                        val records: Map[K, CustomCacheItem[K, V]] = Map.empty[K, CustomCacheItem[K, V]],
                                        val mayBeTopKey: Option[K] = None,
                                        val mayBeBottomKey: Option[K] = None) {
  class ExtendedMap(state: Map[K, CustomCacheItem[K, V]]) {
    def update(mayBeItem: Option[(K, CustomCacheItem[K, V])]): Map[K, CustomCacheItem[K, V]] =
      mayBeItem.map(item => state + item).getOrElse(state)
  }
  implicit def mapToMap(state: Map[K, CustomCacheItem[K, V]]): ExtendedMap = new ExtendedMap(state)

  def updateOnTop(key: K, value: V): LimitSizeMapCacheState[K, V] = {
    if (!records.contains(key)) addValueByKeyOnTop(key, value)
    else setValueByKey(key, value).moveRecordOnTop(key)
  }

  private def addValueByKeyOnTop(key: K, value: V): LimitSizeMapCacheState[K, V] = {
    val currentRecord = key -> CustomCacheItem(value, None, mayBeTopKey)
    val mayBeTopRecord = for {topKey <- mayBeTopKey; item = topKey -> records(topKey).setNextKey(Some(key))} yield(item)
    val newRecords = (records + currentRecord).update(mayBeTopRecord)
    val newMayBeTopKey = Some(key)
    val newMayBeBottomKey = Some(mayBeBottomKey.getOrElse(key))
    LimitSizeMapCacheState(sizeLimit, reducingFactor, newRecords, newMayBeTopKey, newMayBeBottomKey)
  }

  private def setValueByKey(key: K, value: V): LimitSizeMapCacheState[K, V] = {
    val record = records(key)
    val item = key -> CustomCacheItem(value, record.mayBeNextKey, record.mayBePrevKey)
    val newRecords = records + item
    LimitSizeMapCacheState(sizeLimit, reducingFactor, newRecords, mayBeTopKey, mayBeBottomKey)
  }

  def moveRecordOnTop(key: K): LimitSizeMapCacheState[K, V] = {
    if (mayBeTopKey.isDefined && mayBeTopKey.get==key)
      this
    else {
      val mapValue = records(key)
      val currentRecord = key -> CustomCacheItem(mapValue.value, None, mayBeTopKey)
      val topRecord = mayBeTopKey.get -> records(mayBeTopKey.get).setNextKey(Some(key))
      val newRecords = records + currentRecord + topRecord

      val mayBeNextRecord = for {nk <- mapValue.mayBeNextKey; pk = mapValue.mayBePrevKey; r = nk -> newRecords(nk).setPrevKey(pk)} yield (r)
      val mayBePrevRecord = for {pk <- mapValue.mayBePrevKey; nk = mapValue.mayBeNextKey; r = pk -> newRecords(pk).setNextKey(nk)} yield (r)

      val newMayBeBottomKey = mayBeBottomKey match {
        case mayBeBottomKey if mayBeBottomKey.get == key && records(key).mayBeNextKey.isDefined => records(key).mayBeNextKey
        case _ => mayBeBottomKey
      }
      val finalRecords = newRecords.update(mayBeNextRecord).update(mayBePrevRecord)

      LimitSizeMapCacheState(sizeLimit, reducingFactor, finalRecords, Some(key), newMayBeBottomKey)
    }
  }

  private def removeLastRecord(): LimitSizeMapCacheState[K, V] = {
    val key = mayBeBottomKey.get
    LimitSizeMapCacheState(sizeLimit, reducingFactor, records - key, mayBeTopKey, records(key).mayBeNextKey)
  }

  private def removeLastRecords(count: Int): LimitSizeMapCacheState[K, V] = {
    if (0 < count) {
      val state = this.removeLastRecord().removeLastRecords(count - 1)
      val bottomKey = state.mayBeBottomKey.get
      val newBottomKey = bottomKey -> state.records(bottomKey).setPrevKey(None)
      val records = state.records.update(Some(newBottomKey))
      LimitSizeMapCacheState[K, V](state.sizeLimit, state.reducingFactor, records, state.mayBeTopKey, state.mayBeBottomKey)
    }
    else this.copy()
  }

  def cleanOldRecords(): LimitSizeMapCacheState[K, V] = {
    if (this.sizeLimit < this.records.size) this.removeLastRecords(this.records.size - (0.7 * this.sizeLimit).toInt)
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
      val mayBeValue = state.records.get(key)
      mayBeValue match {
        case None => (state, None)
        case _ => (state.moveRecordOnTop(key), Some(mayBeValue.get.value))
      }
    })
  }

  def set(key: K, value: V): F[Unit] = state.update(state => {state.updateOnTop(key, value).cleanOldRecords()})
}

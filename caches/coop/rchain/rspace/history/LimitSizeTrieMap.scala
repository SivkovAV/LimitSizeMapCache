package coop.rchain.rspace.history

import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap
import java.util.concurrent.locks.ReentrantReadWriteLock


/**
  * [[TrieMap]] with limit size. Not multithread-safe (see [[MultiThreadLimitSizeTrieMap]]).
  * @param maxSize - items count after which old records should be cleared
  * Inner fields description:
  * sizeLimit - verified version of [[maxSize]]
  * cache     - TrieMap[key, (value, Option[nextKey], Option[prevKey])]; nextKey closer to topKey; prevKey closer to bottomKey;
  * topKey    - last read item's key
  * bottomKey - most old item's key
  */
@SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.NonUnitStatements"))
class LimitSizeTrieMap[A, B](private val maxSize: Int) {
  private val sizeLimit: Int = prepareMaxSize(maxSize)
  private val cache: TrieMap[A, (B, Option[A], Option[A])] = TrieMap.empty[A, (B, Option[A], Option[A])]
  private var topKey: Option[A] = None
  private var bottomKey: Option[A] = None

  private def prepareMaxSize(maxSize: Int): Int = if (maxSize <= 0) 1 else maxSize

  def get(key: A): Option[B] = {
    val optionValue = cache.get(key)

    if (optionValue.isEmpty) {
      None
    } else {
      // modify next cache value
      if (cache(bottomKey.get)._2.isDefined) {
        val nextKey = cache(bottomKey.get)._2.get
        val (v, k, _) = cache(nextKey)
        cache(nextKey) = (v, k, cache(bottomKey.get)._3)
      }

      // modify previous cache value
      if (cache(bottomKey.get)._3.isDefined) {
        val prevKey = cache(bottomKey.get)._3.get
        val (v, _, k) = cache(prevKey)
        cache(prevKey) = (v, cache(bottomKey.get)._2, k)
      }

      // modify bottom pointer
      if (bottomKey.contains(key) && cache(bottomKey.get)._2.isDefined)
        bottomKey = cache(bottomKey.get)._2

      if (!topKey.contains(key)) {
        // modify current cache value
        cache(key) = (cache(key)._1, None, topKey)

        // modify top cache value
        val (v, _, k) = cache(topKey.get)
        cache(topKey.get) = (v, Some(key), k)

        // modify top pointer
        topKey = Some(key)
      }

      Some(optionValue.get._1)
    }
  }

  def set(key: A, value: B): Unit = {
    val optionValue = cache.get(key)
    if (optionValue.isDefined) {
      val (_, nextKey, prevKey) = cache(key)
      cache(key) = (value, nextKey, prevKey)
      get(key)
    } else {
      cache(key) = (value, None, topKey)
      if (topKey.isEmpty) {
        topKey = Some(key)
        bottomKey = Some(key)
      } else {
        val nextBottomKey       = clearOldItems()
        val (value, _, prevKey) = cache(topKey.get)
        cache(topKey.get) = (value, Some(key), prevKey)
        topKey = Some(key)
        bottomKey = nextBottomKey
      }
    }
  }

  @tailrec
  private def prepareOldItems(
      oldItemsCount: Int,
      bottomKey: Option[A],
      currentOldItemsList: List[A]
  ): (List[A], Option[A]) =
    if (bottomKey.isEmpty || oldItemsCount == 0)
      (currentOldItemsList, bottomKey)
    else {
      val nextBottomKey = cache(bottomKey.get)._2
      prepareOldItems(oldItemsCount - 1, nextBottomKey, bottomKey.get :: currentOldItemsList)
    }

  private def clearOldItems(): Option[A] =
    if (sizeLimit < cache.size) {
      val (oldItems, nextBottomKey) = prepareOldItems(sizeLimit / 3, bottomKey, Nil)
      oldItems.foreach(cache.remove)
      nextBottomKey
    } else
      bottomKey
}

/**
 * [[TrieMap]] with limit size. Multi thread version of [[LimitSizeTrieMap]] class.
 * @param maxSize - items count after which old records should be cleared
 */
final class MultiThreadLimitSizeTrieMap[A, B](val maxSize: Int) extends LimitSizeTrieMap[A, B](maxSize) {
  val lock = new ReentrantReadWriteLock()
  override def get(key: A): Option[B] = {
    lock.writeLock().lock()
    val res = super.get(key)
    lock.writeLock().unlock()
    res
  }
  override def set(key: A, value: B): Unit = {
    lock.writeLock().lock()
    super.set(key, value)
    lock.writeLock().unlock()
  }
}

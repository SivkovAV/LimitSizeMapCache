/*
 * Copyright (c) 2020 Aleksei Sivkov.
 * All rights reserved.
 */

package stereo.rchain.limitsizemapcache.cacheImplamentations

import java.util.concurrent.locks.ReentrantReadWriteLock
import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap


/**
  * [[TrieMap]] with limit size. Not multithread-safe (see [[ImperativeLimitSizeMapCache]]).
 *
 * @param maxItemCount - items count after which old items should be cleared
 * @param itemCountAfterSizeCorrection - items count after clearing
 * [[cache]]     - TrieMap[key, (value, Option[nextKey], Option[prevKey])]; nextKey closer to mayBeTopKey; prevKey closer to mayBeBottomKey;
 * [[mayBeTopKey]]    - last read item's key
 * [[mayBeBottomKey]] - most old item's key
 */
@SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.NonUnitStatements"))
class LimitSizeTrieMapThreadUnsafe[A, B](private val maxItemCount: Int,
                                         private val itemCountAfterSizeCorrection: Int) {
  private val cache: TrieMap[A, (B, Option[A], Option[A])] = TrieMap.empty[A, (B, Option[A], Option[A])]
  private var mayBeTopKey: Option[A] = None
  private var mayBeBottomKey: Option[A] = None

  def get(key: A): Option[B] = {
    val optionValue = cache.get(key)

    if (optionValue.isEmpty) {
      None
    } else {
      // modify next cache value
      if (cache(mayBeBottomKey.get)._2.isDefined) {
        val (_, mayBeNextKey, mayBePrevKey) = cache(mayBeBottomKey.get)
        val nextKey = mayBeNextKey.get
        val (v, k, _) = cache(nextKey)
        cache(nextKey) = (v, k, mayBePrevKey)
      }

      // modify previous cache value
      if (cache(mayBeBottomKey.get)._3.isDefined) {
        val (_, mayBeNextKey, mayBePrevKey) = cache(mayBeBottomKey.get)
        val prevKey = mayBePrevKey.get
        val (v, _, k) = cache(prevKey)
        cache(prevKey) = (v, mayBeNextKey, k)
      }

      // modify bottom pointer
      if (mayBeBottomKey.contains(key)) {
        val mayBeBottomNextKey = cache(mayBeBottomKey.get)._2
        if (mayBeBottomNextKey.isDefined)
          mayBeBottomKey = mayBeBottomNextKey
      }

      if (!mayBeTopKey.contains(key)) {
        // modify current cache value
        cache(key) = (cache(key)._1, None, mayBeTopKey)

        // modify top cache value
        val (v, _, k) = cache(mayBeTopKey.get)
        cache(mayBeTopKey.get) = (v, Some(key), k)

        // modify top pointer
        mayBeTopKey = Some(key)
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
      ()
    } else {
      cache(key) = (value, None, mayBeTopKey)
      if (mayBeTopKey.isEmpty) {
        mayBeTopKey = Some(key)
        mayBeBottomKey = Some(key)
      } else {
        val nextBottomKey       = clearOldItems()
        val (value, _, prevKey) = cache(mayBeTopKey.get)
        cache(mayBeTopKey.get) = (value, Some(key), prevKey)
        mayBeTopKey = Some(key)
        mayBeBottomKey = nextBottomKey
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
    if (maxItemCount < cache.size) {
      val (oldItems, nextBottomKey) = prepareOldItems(maxItemCount - itemCountAfterSizeCorrection, mayBeBottomKey, Nil)
      oldItems.foreach(cache.remove)
      nextBottomKey
    } else
      mayBeBottomKey
}

/**
 * [[TrieMap]] with limit size. Multi thread version of [[LimitSizeTrieMapThreadUnsafe]] class.
 *
 * @param maxItemCount - items count after which old items should be cleared
 */
final class ImperativeLimitSizeMapCache[A, B](val maxItemCount: Int, val itemCountAfterSizeCorrection: Int)
  extends LimitSizeTrieMapThreadUnsafe[A, B](maxItemCount, itemCountAfterSizeCorrection) {
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

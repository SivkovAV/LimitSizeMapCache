package stereo.rchain.mapcache

import cats.effect.Sync
import org.scalatest.PrivateMethodTester
import org.scalatest.flatspec.AnyFlatSpec
import cats.effect.concurrent.Ref
import cats.syntax.all._
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import stereo.rchain.mapcache.cacheImplamentations._


class CustomCacheItemSpec extends AnyFlatSpec {
  def checkNextKey(newMayBeNextKey: Option[Int], oldMayBeNextKey: Option[Int]): Unit = {
    val srcCacheItem = LimitSizeMapStateItem[Int, String](value="this is string value", mayBeNextKey=oldMayBeNextKey, mayBePrevKey=None)
    val dstCacheItem = srcCacheItem.setNextKey(newMayBeNextKey)
    assert(srcCacheItem.value == dstCacheItem.value)
    assert(newMayBeNextKey == dstCacheItem.mayBeNextKey)
    assert(srcCacheItem.mayBePrevKey == dstCacheItem.mayBePrevKey)
    ()
  }

  "setNextKey" should "modify next key" in {
    checkNextKey(newMayBeNextKey=None,    oldMayBeNextKey=None)
    checkNextKey(newMayBeNextKey=Some(1), oldMayBeNextKey=None)
    checkNextKey(newMayBeNextKey=Some(1), oldMayBeNextKey=Some(2))
    checkNextKey(newMayBeNextKey=Some(1), oldMayBeNextKey=Some(1))
  }

  def checkPrevKey(newMayBeNextKey: Option[Int], oldMayBeNextKey: Option[Int]): Unit = {
    val srcCacheItem = LimitSizeMapStateItem[Int, String](value="this is string value", mayBeNextKey=None, mayBePrevKey=oldMayBeNextKey)
    val dstCacheItem = srcCacheItem.setPrevKey(newMayBeNextKey)
    assert(srcCacheItem.value == dstCacheItem.value)
    assert(srcCacheItem.mayBeNextKey == dstCacheItem.mayBeNextKey)
    assert(newMayBeNextKey == dstCacheItem.mayBePrevKey)
    ()
  }

  "setPrevKey" should "set next key to None" in {
    checkPrevKey(newMayBeNextKey=None,    oldMayBeNextKey=None)
    checkPrevKey(newMayBeNextKey=Some(1), oldMayBeNextKey=None)
    checkPrevKey(newMayBeNextKey=Some(1), oldMayBeNextKey=Some(2))
    checkPrevKey(newMayBeNextKey=Some(1), oldMayBeNextKey=Some(1))
  }
}


class CustomCacheStateSpec extends AnyFlatSpec with PrivateMethodTester{
  val cleanOldItemsMethod = PrivateMethod[LimitSizeMapCacheState[Int, String]](methodName = Symbol("cleanOldItems"))
  val updateOnTopMethod = PrivateMethod[LimitSizeMapCacheState[Int, String]](methodName = Symbol("updateOnTop"))
  val setValueByKeyMethod = PrivateMethod[LimitSizeMapCacheState[Int, String]](methodName = Symbol("setValueByKey"))

  "cleanOldRecords()" should "not clean old items in empty LimitSizeMapCacheState" in {
    val maxItemCount = 100
    val itemCountAfterSizeCorrection = 70
    val srcState = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection)
    val dstState = srcState invokePrivate cleanOldItemsMethod()

    assert(srcState == dstState)
  }

  "cleanOldRecords()" should "not clean old items while LimitSizeMapCacheState size not more then maxItemCount" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3
    val innerMap = Map(
      0 -> LimitSizeMapStateItem[Int, String](value="0", None, Some(1)),
      1 -> LimitSizeMapStateItem[Int, String](value="1", Some(0), Some(2)),
      2 -> LimitSizeMapStateItem[Int, String](value="2", Some(1), Some(3)),
      3 -> LimitSizeMapStateItem[Int, String](value="3", Some(2), Some(4)),
      4 -> LimitSizeMapStateItem[Int, String](value="4", Some(3), None))
    val srcState = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, innerMap, Some(0), Some(4))
    val dstState = srcState invokePrivate cleanOldItemsMethod()

    assert(srcState == dstState)
  }

  "cleanOldRecords()" should "clean old items" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3

    val srcInnerMap = Map(
      0 -> LimitSizeMapStateItem[Int, String](value="0", None, Some(1)),
      1 -> LimitSizeMapStateItem[Int, String](value="1", Some(0), Some(2)),
      2 -> LimitSizeMapStateItem[Int, String](value="2", Some(1), Some(3)),
      3 -> LimitSizeMapStateItem[Int, String](value="3", Some(2), Some(4)),
      4 -> LimitSizeMapStateItem[Int, String](value="4", Some(3), Some(5)),
      5 -> LimitSizeMapStateItem[Int, String](value="5", Some(4), Some(6)),
      6 -> LimitSizeMapStateItem[Int, String](value="6", Some(5), Some(7)),
      7 -> LimitSizeMapStateItem[Int, String](value="7", Some(6), Some(8)),
      8 -> LimitSizeMapStateItem[Int, String](value="8", Some(7), Some(9)),
      9 -> LimitSizeMapStateItem[Int, String](value="9", Some(8), None))
    val srcState = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(0), Some(9))

    val requiredInnerMap = Map(
      0 -> LimitSizeMapStateItem[Int, String]("0", None, Some(1)),
      1 -> LimitSizeMapStateItem[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapStateItem[Int, String]("2", Some(1), None))
    val requiredDstCache = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, requiredInnerMap, Some(0), Some(2))

    val dstState = srcState invokePrivate cleanOldItemsMethod()

    assert(dstState != srcState)
    assert(dstState == requiredDstCache)
  }

  "moveRecordOnTop()" should "not move top item on top (should stay LimitSizeMapCacheState without modifications)" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3

    val srcInnerMap = Map(
      0 -> LimitSizeMapStateItem[Int, String]("0", None,    Some(1)),
      1 -> LimitSizeMapStateItem[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapStateItem[Int, String]("2", Some(1), Some(3)),
      3 -> LimitSizeMapStateItem[Int, String]("3", Some(2), Some(4)),
      4 -> LimitSizeMapStateItem[Int, String]("4", Some(3), None))
    val srcState = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(0), Some(4))

    val dstState = srcState.moveRecordOnTop(0)

    assert(dstState == srcState)
  }

  "moveRecordOnTop()" should "move before the top item on top" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3

    val srcInnerMap = Map(
      0 -> LimitSizeMapStateItem[Int, String]("0", None, Some(1)),
      1 -> LimitSizeMapStateItem[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapStateItem[Int, String]("2", Some(1), Some(3)),
      3 -> LimitSizeMapStateItem[Int, String]("3", Some(2), Some(4)),
      4 -> LimitSizeMapStateItem[Int, String]("4", Some(3), None))
    val srcState = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(0), Some(4))

    val requiredInnerMap = Map(
      1 -> LimitSizeMapStateItem[Int, String]("1", None, Some(0)),
      0 -> LimitSizeMapStateItem[Int, String]("0", Some(1), Some(2)),
      2 -> LimitSizeMapStateItem[Int, String]("2", Some(0), Some(3)),
      3 -> LimitSizeMapStateItem[Int, String]("3", Some(2), Some(4)),
      4 -> LimitSizeMapStateItem[Int, String]("4", Some(3), None))
    val requiredDstCache = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, requiredInnerMap, Some(1), Some(4))

    val dstState = srcState.moveRecordOnTop(1)

    assert(dstState != srcState)
    assert(dstState == requiredDstCache)
  }

  "moveRecordOnTop()" should "move central item on top" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3

    val srcInnerMap = Map(
      0 -> LimitSizeMapStateItem[Int, String]("0", None, Some(1)),
      1 -> LimitSizeMapStateItem[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapStateItem[Int, String]("2", Some(1), Some(3)),
      3 -> LimitSizeMapStateItem[Int, String]("3", Some(2), Some(4)),
      4 -> LimitSizeMapStateItem[Int, String]("4", Some(3), None))
    val srcState = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(0), Some(4))

    val requiredInnerMap = Map(
      2 -> LimitSizeMapStateItem[Int, String]("2", None, Some(0)),
      0 -> LimitSizeMapStateItem[Int, String]("0", Some(2), Some(1)),
      1 -> LimitSizeMapStateItem[Int, String]("1", Some(0), Some(3)),
      3 -> LimitSizeMapStateItem[Int, String]("3", Some(1), Some(4)),
      4 -> LimitSizeMapStateItem[Int, String]("4", Some(3), None))
    val requiredDstCache = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, requiredInnerMap, Some(2), Some(4))

    val dstState = srcState.moveRecordOnTop(2)

    assert(dstState != srcState)
    assert(dstState == requiredDstCache)
  }

  "moveRecordOnTop()" should "move before the bottom item on top" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3

    val srcInnerMap = Map(
      0 -> LimitSizeMapStateItem[Int, String]("0", None, Some(1)),
      1 -> LimitSizeMapStateItem[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapStateItem[Int, String]("2", Some(1), Some(3)),
      3 -> LimitSizeMapStateItem[Int, String]("3", Some(2), Some(4)),
      4 -> LimitSizeMapStateItem[Int, String]("4", Some(3), None))
    val srcState = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(0), Some(4))

    val requiredInnerMap = Map(
      3 -> LimitSizeMapStateItem[Int, String]("3", None, Some(0)),
      0 -> LimitSizeMapStateItem[Int, String]("0", Some(3), Some(1)),
      1 -> LimitSizeMapStateItem[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapStateItem[Int, String]("2", Some(1), Some(4)),
      4 -> LimitSizeMapStateItem[Int, String]("4", Some(2), None))
    val requiredDstCache = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, requiredInnerMap, Some(3), Some(4))

    val dstState = srcState.moveRecordOnTop(3)

    assert(dstState != srcState)
    assert(dstState == requiredDstCache)
  }

  "moveRecordOnTop()" should "move bottom item on top" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3

    val srcInnerMap = Map(
      0 -> LimitSizeMapStateItem[Int, String]("0", None, Some(1)),
      1 -> LimitSizeMapStateItem[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapStateItem[Int, String]("2", Some(1), Some(3)),
      3 -> LimitSizeMapStateItem[Int, String]("3", Some(2), Some(4)),
      4 -> LimitSizeMapStateItem[Int, String]("4", Some(3), None))
    val srcState = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(0), Some(4))

    val requiredInnerMap = Map(
      4 -> LimitSizeMapStateItem[Int, String]("4", None, Some(0)),
      0 -> LimitSizeMapStateItem[Int, String]("0", Some(4), Some(1)),
      1 -> LimitSizeMapStateItem[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapStateItem[Int, String]("2", Some(1), Some(3)),
      3 -> LimitSizeMapStateItem[Int, String]("3", Some(2), None))
    val requiredDstCache = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, requiredInnerMap, Some(4), Some(3))

    val dstState = srcState.moveRecordOnTop(key=4)

    assert(dstState != srcState)
    assert(dstState == requiredDstCache)
  }

  "moveRecordOnTop()" should "move bottom item on top in small cache" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3

    val srcInnerMap = Map(
      0 -> LimitSizeMapStateItem[Int, String]("0", None, Some(1)),
      1 -> LimitSizeMapStateItem[Int, String]("1", Some(0), None))
    val srcState = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(0), Some(1))

    val requiredInnerMap = Map(
      1 -> LimitSizeMapStateItem[Int, String]("1", None, Some(0)),
      0 -> LimitSizeMapStateItem[Int, String]("0", Some(1), None))
    val requiredDstCache = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, requiredInnerMap, Some(1), Some(0))

    val dstState = srcState.moveRecordOnTop(key=1)

    assert(dstState != srcState)
    assert(dstState == requiredDstCache)
  }

  "setValueByKey()" should "modify single LimitSizeMapCacheState item value" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3
    val key1 = 0
    val key2 = key1 + 1
    val newValue = (key2 + 1).toString

    val srcInnerMap = Map(
      key1 -> LimitSizeMapStateItem[Int, String](key1.toString, None, Some(key2)),
      key2 -> LimitSizeMapStateItem[Int, String](key2.toString, Some(key1), None))
    val srcState = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(key1), Some(1))

    val requiredInnerMap = Map(
      key1 -> LimitSizeMapStateItem[Int, String](newValue, None, Some(key2)),
      key2 -> LimitSizeMapStateItem[Int, String](key2.toString, Some(key1), None))
    val requiredDstCache = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, requiredInnerMap, Some(0), Some(1))

    val dstState = srcState invokePrivate setValueByKeyMethod(key1, newValue)

    assert(dstState != srcState)
    assert(dstState == requiredDstCache)
  }

  "updateOnTop()" should "just modify top item" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3

    val srcInnerMap = Map(
      0 -> LimitSizeMapStateItem[Int, String]("0", None,    Some(1)),
      1 -> LimitSizeMapStateItem[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapStateItem[Int, String]("2", Some(1), Some(3)),
      3 -> LimitSizeMapStateItem[Int, String]("3", Some(2), Some(4)),
      4 -> LimitSizeMapStateItem[Int, String]("4", Some(3), None))
    val srcState = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(0), Some(4))

    val requiredInnerMap = Map(
      0 -> LimitSizeMapStateItem[Int, String]("00", None,    Some(1)),
      1 -> LimitSizeMapStateItem[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapStateItem[Int, String]("2", Some(1), Some(3)),
      3 -> LimitSizeMapStateItem[Int, String]("3", Some(2), Some(4)),
      4 -> LimitSizeMapStateItem[Int, String]("4", Some(3), None))
    val requiredDstCache = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, requiredInnerMap, Some(0), Some(4))

    val dstState = srcState invokePrivate  updateOnTopMethod(0, "00")

    assert(dstState != srcState)
    assert(dstState == requiredDstCache)
  }

  "updateOnTop()" should "modify and move before the top item on top" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3

    val srcInnerMap = Map(
      0 -> LimitSizeMapStateItem[Int, String]("0", None, Some(1)),
      1 -> LimitSizeMapStateItem[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapStateItem[Int, String]("2", Some(1), Some(3)),
      3 -> LimitSizeMapStateItem[Int, String]("3", Some(2), Some(4)),
      4 -> LimitSizeMapStateItem[Int, String]("4", Some(3), None))
    val srcState = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(0), Some(4))

    val requiredInnerMap = Map(
      1 -> LimitSizeMapStateItem[Int, String]("11", None, Some(0)),
      0 -> LimitSizeMapStateItem[Int, String]("0", Some(1), Some(2)),
      2 -> LimitSizeMapStateItem[Int, String]("2", Some(0), Some(3)),
      3 -> LimitSizeMapStateItem[Int, String]("3", Some(2), Some(4)),
      4 -> LimitSizeMapStateItem[Int, String]("4", Some(3), None))
    val requiredDstCache = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, requiredInnerMap, Some(1), Some(4))

    val dstState = srcState invokePrivate  updateOnTopMethod(1, "11")

    assert(dstState != srcState)
    assert(dstState == requiredDstCache)
  }

  "updateOnTop()" should "modify and move central item on top" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3

    val srcInnerMap = Map(
      0 -> LimitSizeMapStateItem[Int, String]("0", None, Some(1)),
      1 -> LimitSizeMapStateItem[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapStateItem[Int, String]("2", Some(1), Some(3)),
      3 -> LimitSizeMapStateItem[Int, String]("3", Some(2), Some(4)),
      4 -> LimitSizeMapStateItem[Int, String]("4", Some(3), None))
    val srcState = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(0), Some(4))

    val requiredInnerMap = Map(
      2 -> LimitSizeMapStateItem[Int, String]("22", None, Some(0)),
      0 -> LimitSizeMapStateItem[Int, String]("0", Some(2), Some(1)),
      1 -> LimitSizeMapStateItem[Int, String]("1", Some(0), Some(3)),
      3 -> LimitSizeMapStateItem[Int, String]("3", Some(1), Some(4)),
      4 -> LimitSizeMapStateItem[Int, String]("4", Some(3), None))
    val requiredDstCache = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, requiredInnerMap, Some(2), Some(4))

    val dstState = srcState invokePrivate  updateOnTopMethod(2, "22")

    assert(dstState != srcState)
    assert(dstState == requiredDstCache)
  }

  "updateOnTop()" should "modify and move before the bottom item on top" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3

    val srcInnerMap = Map(
      0 -> LimitSizeMapStateItem[Int, String]("0", None, Some(1)),
      1 -> LimitSizeMapStateItem[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapStateItem[Int, String]("2", Some(1), Some(3)),
      3 -> LimitSizeMapStateItem[Int, String]("3", Some(2), Some(4)),
      4 -> LimitSizeMapStateItem[Int, String]("4", Some(3), None))
    val srcState = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(0), Some(4))

    val requiredInnerMap = Map(
      3 -> LimitSizeMapStateItem[Int, String]("33", None, Some(0)),
      0 -> LimitSizeMapStateItem[Int, String]("0", Some(3), Some(1)),
      1 -> LimitSizeMapStateItem[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapStateItem[Int, String]("2", Some(1), Some(4)),
      4 -> LimitSizeMapStateItem[Int, String]("4", Some(2), None))
    val requiredDstCache = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, requiredInnerMap, Some(3), Some(4))

    val dstState = srcState invokePrivate  updateOnTopMethod(3, "33")

    assert(dstState != srcState)
    assert(dstState == requiredDstCache)
  }

  "updateOnTop()" should "modify and move bottom item on top" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3

    val srcInnerMap = Map(
      0 -> LimitSizeMapStateItem[Int, String]("0", None, Some(1)),
      1 -> LimitSizeMapStateItem[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapStateItem[Int, String]("2", Some(1), Some(3)),
      3 -> LimitSizeMapStateItem[Int, String]("3", Some(2), Some(4)),
      4 -> LimitSizeMapStateItem[Int, String]("4", Some(3), None))
    val srcState = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(0), Some(4))

    val requiredInnerMap = Map(
      4 -> LimitSizeMapStateItem[Int, String]("44", None, Some(0)),
      0 -> LimitSizeMapStateItem[Int, String]("0", Some(4), Some(1)),
      1 -> LimitSizeMapStateItem[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapStateItem[Int, String]("2", Some(1), Some(3)),
      3 -> LimitSizeMapStateItem[Int, String]("3", Some(2), None))
    val requiredDstCache = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, requiredInnerMap, Some(4), Some(3))

    val dstState = srcState invokePrivate  updateOnTopMethod(4, "44")

    assert(dstState != srcState)
    assert(dstState == requiredDstCache)
  }

  "updateOnTop()" should "modify and move bottom item on top in small cache" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3

    val srcInnerMap = Map(
      0 -> LimitSizeMapStateItem[Int, String]("0", None, Some(1)),
      1 -> LimitSizeMapStateItem[Int, String]("1", Some(0), None))
    val srcState = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(0), Some(1))

    val requiredInnerMap = Map(
      1 -> LimitSizeMapStateItem[Int, String]("11", None, Some(0)),
      0 -> LimitSizeMapStateItem[Int, String]("0", Some(1), None))
    val requiredDstCache = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, requiredInnerMap, Some(1), Some(0))

    val dstState = srcState invokePrivate  updateOnTopMethod(1, "11")

    assert(dstState != srcState)
    assert(dstState == requiredDstCache)
  }

  "updateOnTop()" should "add new pair of key->value on top" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3

    val srcInnerMap = Map(
      0 -> LimitSizeMapStateItem[Int, String]("0", None, Some(1)),
      1 -> LimitSizeMapStateItem[Int, String]("1", Some(0), None))
    val srcState = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(0), Some(1))

    val requiredInnerMap = Map(
      2 -> LimitSizeMapStateItem[Int, String]("2", None, Some(0)),
      0 -> LimitSizeMapStateItem[Int, String]("0", Some(2), Some(1)),
      1 -> LimitSizeMapStateItem[Int, String]("1", Some(0), None))
    val requiredDstCache = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, requiredInnerMap, Some(2), Some(1))

    val dstState = srcState invokePrivate  updateOnTopMethod(2, "2")

    assert(dstState != srcState)
    assert(dstState == requiredDstCache)
  }
}


class CustomCacheSpec extends AnyFlatSpec {
  "Initialized cache" should "be empty" in {
    def test[F[_] : Sync](): F[Unit] = {
      val maxItemCount = 5
      val itemCountAfterSizeCorrection = 3
      val beginState = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection)
      for {
        ref <- Ref.of[F, LimitSizeMapCacheState[Int, String]](beginState)
        cache = LimitSizeMapCache(ref)

        item <- cache.get(0)
        _ = assert(item.isEmpty)
        state <- cache.stateRef.get
        records = state.items
        _ = assert(records.isEmpty)
        _ = assert(state.mayBeTopKey.isEmpty)
        _ = assert(state.mayBeBottomKey.isEmpty)
      } yield ()
    }

    test[Task]().runSyncUnsafe()
  }

  "Cache after 1 call of set()" should "be have 1 item" in {
    def test[F[_] : Sync](): F[Unit] = {
      val maxItemCount = 5
      val itemCountAfterSizeCorrection = 3
      val beginState = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection)
      for {
        ref <- Ref.of[F, LimitSizeMapCacheState[Int, String]](beginState)
        cache = LimitSizeMapCache(ref)
        _ <- cache.set(0, "0")

        item0 <- cache.get(0)
        _ = assert(item0.isDefined && item0.get == "0")
        item1 <- cache.get(1)
        _ = assert(item1.isEmpty)
        state <- cache.stateRef.get
        records = state.items
        _ = assert(records.size == 1)
        _ = assert(records.contains(0))
        _ = assert(records(0).value == "0")
        _ = assert(records(0).mayBeNextKey.isEmpty && records(0).mayBePrevKey.isEmpty)
        _ = assert(state.mayBeTopKey.isDefined && state.mayBeBottomKey.isDefined)
        _ = assert(state.mayBeTopKey.contains(0) && state.mayBeBottomKey.contains(0))
      } yield ()
    }

    test[Task]().runSyncUnsafe()
  }

  "Cache after 2 call of set()" should "be have 2 item" in {
    def test[F[_] : Sync](): F[Unit] = {
      val maxItemCount = 5
      val itemCountAfterSizeCorrection = 3
      val beginState = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection)
      for {
        ref <- Ref.of[F, LimitSizeMapCacheState[Int, String]](beginState)
        cache = LimitSizeMapCache(ref)
        _ <- cache.set(0, "0")
        _ <- cache.set(1, "1")

        item0 <- cache.get(0)
        _ = assert(item0.isDefined && item0.get == "0")
        item1 <- cache.get(1)
        _ = assert(item1.isDefined && item1.get == "1")
        item2 <- cache.get(2)
        _ = assert(item2.isEmpty)
        state <- cache.stateRef.get
        records = state.items
        _ = assert(records.size == 2)
        _ = assert(records.contains(0) && records.contains(1))
        _ = assert(records(0).value == "0" && records(1).value == "1")
        _ = assert(records(1).mayBeNextKey.isEmpty && records(1).mayBePrevKey.isDefined)
        _ = assert(records(0).mayBeNextKey.isDefined && records(0).mayBePrevKey.isEmpty)
        _ = assert(state.mayBeTopKey.isDefined && state.mayBeBottomKey.isDefined)
        _ = assert(state.mayBeTopKey.contains(1) && state.mayBeBottomKey.contains(0))
      } yield ()
    }

    test[Task]().runSyncUnsafe()
  }

  "Reading exists item from Cache" should "modify items order if item is not on top" in {
    def test[F[_] : Sync](): F[Unit] = {
      val maxItemCount = 5
      val itemCountAfterSizeCorrection = 3
      val beginState = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection)
      for {
        ref <- Ref.of[F, LimitSizeMapCacheState[Int, String]](beginState)
        cache = LimitSizeMapCache(ref)
        _ <- cache.set(0, "0")
        _ <- cache.set(1, "1")
        srcCache <- cache.stateRef.get
        _ <- cache.get(0)
        dstCache <- cache.stateRef.get

        _ = assert(srcCache.mayBeTopKey.contains(1) && srcCache.mayBeBottomKey.contains(0))
        _ = assert(dstCache.mayBeTopKey.contains(0) && dstCache.mayBeBottomKey.contains(1))
        _ = assert(srcCache.items(1).mayBeNextKey.isEmpty && srcCache.items(1).mayBePrevKey.isDefined)
        _ = assert(dstCache.items(0).mayBeNextKey.isEmpty && dstCache.items(0).mayBePrevKey.isDefined)
        _ = assert(dstCache.items(0).value == srcCache.items(0).value)
        _ = assert(dstCache.items(1).value == srcCache.items(1).value)
      } yield ()
    }

    test[Task]().runSyncUnsafe()
  }

  "Cache" should "store all added data if size not more then limitSize" in {
    def test[F[_] : Sync](): F[Unit] = {
      val maxItemCount = 5
      val itemCountAfterSizeCorrection = 3
      val beginState = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection)
      for {
        ref <- Ref.of[F, LimitSizeMapCacheState[Int, String]](beginState)
        cache = LimitSizeMapCache(ref)
        _ <- cache.set(0, "0")
        _ <- cache.set(1, "1")
        _ <- cache.set(2, "2")
        _ <- cache.set(3, "3")
        _ <- cache.set(4, "4")

        state <- cache.stateRef.get
        records = state.items
        _ = assert(records.contains(0) && records.contains(1) && records.contains(2) && records.contains(3) && records.contains(4))
        _ = assert(records(0).value == "0" && records(1).value == "1" && records(2).value == "2" && records(3).value == "3" && records(4).value == "4")
        _ = assert(state.mayBeTopKey.contains(4))
        _ = assert(state.mayBeBottomKey.contains(0))
      } yield ()
    }

    test[Task]().runSyncUnsafe()
  }

  "Cache" should "decrease inner map size if this size more then limitSize" in {
    def test[F[_] : Sync](): F[Unit] = {
      val maxItemCount = 5
      val itemCountAfterSizeCorrection = 3
      val beginState = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection)
      for {
        ref <- Ref.of[F, LimitSizeMapCacheState[Int, String]](beginState)
        cache = LimitSizeMapCache(ref)
        _ <- cache.set(0, "0")
        _ <- cache.set(1, "1")
        _ <- cache.set(2, "2")
        _ <- cache.set(3, "3")
        _ <- cache.set(4, "4")
        _ <- cache.set(5, "5")

        state <- cache.stateRef.get
        records = state.items
        _ = assert(!records.contains(0) && !records.contains(1) && !records.contains(2))
        _ = assert(records.contains(3) && records.contains(4) && records.contains(5))
        _ = assert(records(3).value == "3" && records(4).value == "4" && records(5).value == "5")
        _ = assert(state.mayBeTopKey.contains(5))
        _ = assert(state.mayBeBottomKey.contains(3))
      } yield ()
    }

    test[Task]().runSyncUnsafe()
  }
}
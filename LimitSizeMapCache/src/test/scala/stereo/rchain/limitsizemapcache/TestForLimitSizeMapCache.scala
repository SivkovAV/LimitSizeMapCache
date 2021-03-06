package stereo.rchain.limitsizemapcache

import cats.Parallel
import cats.effect.Sync
import cats.syntax.all._
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.scalatest.PrivateMethodTester
import org.scalatest.flatspec.AnyFlatSpec
import stereo.rchain.limitsizemapcache.cacheImplamentations._

class LimitSizeMapItemValueSpec extends AnyFlatSpec {
  def checkNextKey(newMayBeNextKey: Option[Int], oldMayBeNextKey: Option[Int]): Unit = {
    val srcItemValue = LimitSizeMapItemValue[Int, String](
      value = "this is string value",
      mayBeNextKey = oldMayBeNextKey,
      mayBePrevKey = None
    )
    val dstItemValue = srcItemValue.setNextKey(newMayBeNextKey)
    assert(srcItemValue.value == dstItemValue.value)
    assert(newMayBeNextKey == dstItemValue.mayBeNextKey)
    assert(srcItemValue.mayBePrevKey == dstItemValue.mayBePrevKey)
    ()
  }

  "setNextKey" should "modify next key" in {
    checkNextKey(newMayBeNextKey = None, oldMayBeNextKey = None)
    checkNextKey(newMayBeNextKey = Some(1), oldMayBeNextKey = None)
    checkNextKey(newMayBeNextKey = Some(1), oldMayBeNextKey = Some(2))
    checkNextKey(newMayBeNextKey = Some(1), oldMayBeNextKey = Some(1))
  }

  def checkPrevKey(newMayBeNextKey: Option[Int], oldMayBeNextKey: Option[Int]): Unit = {
    val srcItemValue = LimitSizeMapItemValue[Int, String](
      value = "this is string value",
      mayBeNextKey = None,
      mayBePrevKey = oldMayBeNextKey
    )
    val dstItemValue = srcItemValue.setPrevKey(newMayBeNextKey)
    assert(srcItemValue.value == dstItemValue.value)
    assert(srcItemValue.mayBeNextKey == dstItemValue.mayBeNextKey)
    assert(newMayBeNextKey == dstItemValue.mayBePrevKey)
    ()
  }

  "setPrevKey" should "set next key to None" in {
    checkPrevKey(newMayBeNextKey = None, oldMayBeNextKey = None)
    checkPrevKey(newMayBeNextKey = Some(1), oldMayBeNextKey = None)
    checkPrevKey(newMayBeNextKey = Some(1), oldMayBeNextKey = Some(2))
    checkPrevKey(newMayBeNextKey = Some(1), oldMayBeNextKey = Some(1))
  }
}

class LimitSizeMapCacheStateSpec extends AnyFlatSpec with PrivateMethodTester {
  val cleanOldItemsMethod = PrivateMethod[LimitSizeMapCacheState[Int, String]](methodName = Symbol("cleanOldItems"))
  val updateOnTopMethod = PrivateMethod[LimitSizeMapCacheState[Int, String]](methodName = Symbol("updateOnTop"))
  val moveItemOnTopMethod = PrivateMethod[LimitSizeMapCacheState[Int, String]](methodName = Symbol("moveItemOnTop"))
  val setValueByKeyMethod = PrivateMethod[LimitSizeMapCacheState[Int, String]](methodName = Symbol("setValueByKey"))

  "cleanOldItems()" should "not clean old items in empty LimitSizeMapCacheState" in {
    val maxItemCount = 100
    val itemCountAfterSizeCorrection = 70
    val srcState = LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection)
    val dstState = srcState invokePrivate cleanOldItemsMethod()

    assert(srcState == dstState)
  }

  "cleanOldItems()" should "not clean old items while LimitSizeMapCacheState size not more then maxItemCount" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3
    val innerMap = Map(
      0 -> LimitSizeMapItemValue[Int, String](value = "0", None, Some(1)),
      1 -> LimitSizeMapItemValue[Int, String](value = "1", Some(0), Some(2)),
      2 -> LimitSizeMapItemValue[Int, String](value = "2", Some(1), Some(3)),
      3 -> LimitSizeMapItemValue[Int, String](value = "3", Some(2), Some(4)),
      4 -> LimitSizeMapItemValue[Int, String](value = "4", Some(3), None)
    )
    val srcState =
      LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, innerMap, Some(0), Some(4))
    val dstState = srcState invokePrivate cleanOldItemsMethod()

    assert(srcState == dstState)
  }

  "cleanOldItems()" should "clean old items" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3

    val srcInnerMap = Map(
      0 -> LimitSizeMapItemValue[Int, String](value = "0", None, Some(1)),
      1 -> LimitSizeMapItemValue[Int, String](value = "1", Some(0), Some(2)),
      2 -> LimitSizeMapItemValue[Int, String](value = "2", Some(1), Some(3)),
      3 -> LimitSizeMapItemValue[Int, String](value = "3", Some(2), Some(4)),
      4 -> LimitSizeMapItemValue[Int, String](value = "4", Some(3), Some(5)),
      5 -> LimitSizeMapItemValue[Int, String](value = "5", Some(4), Some(6)),
      6 -> LimitSizeMapItemValue[Int, String](value = "6", Some(5), Some(7)),
      7 -> LimitSizeMapItemValue[Int, String](value = "7", Some(6), Some(8)),
      8 -> LimitSizeMapItemValue[Int, String](value = "8", Some(7), Some(9)),
      9 -> LimitSizeMapItemValue[Int, String](value = "9", Some(8), None)
    )
    val srcState =
      LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(0), Some(9))

    val requiredInnerMap = Map(
      0 -> LimitSizeMapItemValue[Int, String]("0", None, Some(1)),
      1 -> LimitSizeMapItemValue[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapItemValue[Int, String]("2", Some(1), None)
    )
    val requiredDstState = LimitSizeMapCacheState[Int, String](
      maxItemCount,
      itemCountAfterSizeCorrection,
      requiredInnerMap,
      Some(0),
      Some(2)
    )

    val dstState = srcState invokePrivate cleanOldItemsMethod()

    assert(dstState != srcState)
    assert(dstState == requiredDstState)
  }

  "cleanOldItems()" should "clean old items until itemCountAfterSizeCorrection" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection1 = 3
    val itemCountAfterSizeCorrection2 = 2
    val srcInnerMap = Map(
      0 -> LimitSizeMapItemValue[Int, String](value = "0", None, Some(1)),
      1 -> LimitSizeMapItemValue[Int, String](value = "1", Some(0), Some(2)),
      2 -> LimitSizeMapItemValue[Int, String](value = "2", Some(1), Some(3)),
      3 -> LimitSizeMapItemValue[Int, String](value = "3", Some(2), Some(4)),
      4 -> LimitSizeMapItemValue[Int, String](value = "4", Some(3), Some(5)),
      5 -> LimitSizeMapItemValue[Int, String](value = "5", Some(4), Some(6)),
      6 -> LimitSizeMapItemValue[Int, String](value = "6", Some(5), Some(7)),
      7 -> LimitSizeMapItemValue[Int, String](value = "7", Some(6), Some(8)),
      8 -> LimitSizeMapItemValue[Int, String](value = "8", Some(7), Some(9)),
      9 -> LimitSizeMapItemValue[Int, String](value = "9", Some(8), None)
    )
    val srcState1 =
      LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection1, srcInnerMap, Some(0), Some(9))
    val dstState1 = srcState1 invokePrivate cleanOldItemsMethod()
    val srcState2 =
      LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection2, srcInnerMap, Some(0), Some(9))
    val dstState2 = srcState2 invokePrivate cleanOldItemsMethod()

    assert(dstState1.items.size == itemCountAfterSizeCorrection1)
    assert(dstState2.items.size == itemCountAfterSizeCorrection2)
  }

  "moveItemOnTop()" should "not move top item on top (should stay LimitSizeMapCacheState without modifications)" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3

    val srcInnerMap = Map(
      0 -> LimitSizeMapItemValue[Int, String]("0", None, Some(1)),
      1 -> LimitSizeMapItemValue[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapItemValue[Int, String]("2", Some(1), Some(3)),
      3 -> LimitSizeMapItemValue[Int, String]("3", Some(2), Some(4)),
      4 -> LimitSizeMapItemValue[Int, String]("4", Some(3), None)
    )
    val srcState =
      LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(0), Some(4))

    val dstState = srcState invokePrivate moveItemOnTopMethod(0)

    assert(dstState == srcState)
  }

  "moveItemOnTop()" should "move before the top item on top" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3

    val srcInnerMap = Map(
      0 -> LimitSizeMapItemValue[Int, String]("0", None, Some(1)),
      1 -> LimitSizeMapItemValue[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapItemValue[Int, String]("2", Some(1), Some(3)),
      3 -> LimitSizeMapItemValue[Int, String]("3", Some(2), Some(4)),
      4 -> LimitSizeMapItemValue[Int, String]("4", Some(3), None)
    )
    val srcState =
      LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(0), Some(4))

    val requiredInnerMap = Map(
      1 -> LimitSizeMapItemValue[Int, String]("1", None, Some(0)),
      0 -> LimitSizeMapItemValue[Int, String]("0", Some(1), Some(2)),
      2 -> LimitSizeMapItemValue[Int, String]("2", Some(0), Some(3)),
      3 -> LimitSizeMapItemValue[Int, String]("3", Some(2), Some(4)),
      4 -> LimitSizeMapItemValue[Int, String]("4", Some(3), None)
    )
    val requiredDstState = LimitSizeMapCacheState[Int, String](
      maxItemCount,
      itemCountAfterSizeCorrection,
      requiredInnerMap,
      Some(1),
      Some(4)
    )

    val dstState = srcState invokePrivate moveItemOnTopMethod(1)

    assert(dstState != srcState)
    assert(dstState == requiredDstState)
  }

  "moveItemOnTop()" should "move central item on top" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3

    val srcInnerMap = Map(
      0 -> LimitSizeMapItemValue[Int, String]("0", None, Some(1)),
      1 -> LimitSizeMapItemValue[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapItemValue[Int, String]("2", Some(1), Some(3)),
      3 -> LimitSizeMapItemValue[Int, String]("3", Some(2), Some(4)),
      4 -> LimitSizeMapItemValue[Int, String]("4", Some(3), None)
    )
    val srcState =
      LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(0), Some(4))

    val requiredInnerMap = Map(
      2 -> LimitSizeMapItemValue[Int, String]("2", None, Some(0)),
      0 -> LimitSizeMapItemValue[Int, String]("0", Some(2), Some(1)),
      1 -> LimitSizeMapItemValue[Int, String]("1", Some(0), Some(3)),
      3 -> LimitSizeMapItemValue[Int, String]("3", Some(1), Some(4)),
      4 -> LimitSizeMapItemValue[Int, String]("4", Some(3), None)
    )
    val requiredDstState = LimitSizeMapCacheState[Int, String](
      maxItemCount,
      itemCountAfterSizeCorrection,
      requiredInnerMap,
      Some(2),
      Some(4)
    )

    val dstState = srcState invokePrivate moveItemOnTopMethod(2)

    assert(dstState != srcState)
    assert(dstState == requiredDstState)
  }

  "moveItemOnTop()" should "move before the bottom item on top" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3

    val srcInnerMap = Map(
      0 -> LimitSizeMapItemValue[Int, String]("0", None, Some(1)),
      1 -> LimitSizeMapItemValue[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapItemValue[Int, String]("2", Some(1), Some(3)),
      3 -> LimitSizeMapItemValue[Int, String]("3", Some(2), Some(4)),
      4 -> LimitSizeMapItemValue[Int, String]("4", Some(3), None)
    )
    val srcState =
      LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(0), Some(4))

    val requiredInnerMap = Map(
      3 -> LimitSizeMapItemValue[Int, String]("3", None, Some(0)),
      0 -> LimitSizeMapItemValue[Int, String]("0", Some(3), Some(1)),
      1 -> LimitSizeMapItemValue[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapItemValue[Int, String]("2", Some(1), Some(4)),
      4 -> LimitSizeMapItemValue[Int, String]("4", Some(2), None)
    )
    val requiredDstState = LimitSizeMapCacheState[Int, String](
      maxItemCount,
      itemCountAfterSizeCorrection,
      requiredInnerMap,
      Some(3),
      Some(4)
    )

    val dstState = srcState invokePrivate moveItemOnTopMethod(3)

    assert(dstState != srcState)
    assert(dstState == requiredDstState)
  }

  "moveItemOnTop()" should "move bottom item on top" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3

    val srcInnerMap = Map(
      0 -> LimitSizeMapItemValue[Int, String]("0", None, Some(1)),
      1 -> LimitSizeMapItemValue[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapItemValue[Int, String]("2", Some(1), Some(3)),
      3 -> LimitSizeMapItemValue[Int, String]("3", Some(2), Some(4)),
      4 -> LimitSizeMapItemValue[Int, String]("4", Some(3), None)
    )
    val srcState =
      LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(0), Some(4))

    val requiredInnerMap = Map(
      4 -> LimitSizeMapItemValue[Int, String]("4", None, Some(0)),
      0 -> LimitSizeMapItemValue[Int, String]("0", Some(4), Some(1)),
      1 -> LimitSizeMapItemValue[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapItemValue[Int, String]("2", Some(1), Some(3)),
      3 -> LimitSizeMapItemValue[Int, String]("3", Some(2), None)
    )
    val requiredDstState = LimitSizeMapCacheState[Int, String](
      maxItemCount,
      itemCountAfterSizeCorrection,
      requiredInnerMap,
      Some(4),
      Some(3)
    )

    val dstState = srcState invokePrivate moveItemOnTopMethod(4)

    assert(dstState != srcState)
    assert(dstState == requiredDstState)
  }

  "moveItemOnTop()" should "move bottom item on top in small state" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3

    val srcInnerMap = Map(
      0 -> LimitSizeMapItemValue[Int, String]("0", None, Some(1)),
      1 -> LimitSizeMapItemValue[Int, String]("1", Some(0), None)
    )
    val srcState =
      LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(0), Some(1))

    val requiredInnerMap = Map(
      1 -> LimitSizeMapItemValue[Int, String]("1", None, Some(0)),
      0 -> LimitSizeMapItemValue[Int, String]("0", Some(1), None)
    )
    val requiredDstState = LimitSizeMapCacheState[Int, String](
      maxItemCount,
      itemCountAfterSizeCorrection,
      requiredInnerMap,
      Some(1),
      Some(0)
    )

    val dstState = srcState invokePrivate moveItemOnTopMethod(1)

    assert(dstState != srcState)
    assert(dstState == requiredDstState)
  }

  "setValueByKey()" should "modify single LimitSizeMapCacheState item value" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3
    val key1 = 0
    val key2 = key1 + 1
    val newValue = (key2 + 1).toString

    val srcInnerMap = Map(
      key1 -> LimitSizeMapItemValue[Int, String](key1.toString, None, Some(key2)),
      key2 -> LimitSizeMapItemValue[Int, String](key2.toString, Some(key1), None)
    )
    val srcState =
      LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(key1), Some(1))

    val requiredInnerMap = Map(
      key1 -> LimitSizeMapItemValue[Int, String](newValue, None, Some(key2)),
      key2 -> LimitSizeMapItemValue[Int, String](key2.toString, Some(key1), None)
    )
    val requiredDstState = LimitSizeMapCacheState[Int, String](
      maxItemCount,
      itemCountAfterSizeCorrection,
      requiredInnerMap,
      Some(0),
      Some(1)
    )

    val dstState = srcState invokePrivate setValueByKeyMethod(key1, newValue)

    assert(dstState != srcState)
    assert(dstState == requiredDstState)
  }

  "updateOnTop()" should "just modify top item" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3

    val srcInnerMap = Map(
      0 -> LimitSizeMapItemValue[Int, String]("0", None, Some(1)),
      1 -> LimitSizeMapItemValue[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapItemValue[Int, String]("2", Some(1), Some(3)),
      3 -> LimitSizeMapItemValue[Int, String]("3", Some(2), Some(4)),
      4 -> LimitSizeMapItemValue[Int, String]("4", Some(3), None)
    )
    val srcState =
      LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(0), Some(4))

    val requiredInnerMap = Map(
      0 -> LimitSizeMapItemValue[Int, String]("00", None, Some(1)),
      1 -> LimitSizeMapItemValue[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapItemValue[Int, String]("2", Some(1), Some(3)),
      3 -> LimitSizeMapItemValue[Int, String]("3", Some(2), Some(4)),
      4 -> LimitSizeMapItemValue[Int, String]("4", Some(3), None)
    )
    val requiredDstState = LimitSizeMapCacheState[Int, String](
      maxItemCount,
      itemCountAfterSizeCorrection,
      requiredInnerMap,
      Some(0),
      Some(4)
    )

    val dstState = srcState invokePrivate updateOnTopMethod(0, "00")

    assert(dstState != srcState)
    assert(dstState == requiredDstState)
  }

  "updateOnTop()" should "modify and move before the top item on top" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3

    val srcInnerMap = Map(
      0 -> LimitSizeMapItemValue[Int, String]("0", None, Some(1)),
      1 -> LimitSizeMapItemValue[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapItemValue[Int, String]("2", Some(1), Some(3)),
      3 -> LimitSizeMapItemValue[Int, String]("3", Some(2), Some(4)),
      4 -> LimitSizeMapItemValue[Int, String]("4", Some(3), None)
    )
    val srcState =
      LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(0), Some(4))

    val requiredInnerMap = Map(
      1 -> LimitSizeMapItemValue[Int, String]("11", None, Some(0)),
      0 -> LimitSizeMapItemValue[Int, String]("0", Some(1), Some(2)),
      2 -> LimitSizeMapItemValue[Int, String]("2", Some(0), Some(3)),
      3 -> LimitSizeMapItemValue[Int, String]("3", Some(2), Some(4)),
      4 -> LimitSizeMapItemValue[Int, String]("4", Some(3), None)
    )
    val requiredDstState = LimitSizeMapCacheState[Int, String](
      maxItemCount,
      itemCountAfterSizeCorrection,
      requiredInnerMap,
      Some(1),
      Some(4)
    )

    val dstState = srcState invokePrivate updateOnTopMethod(1, "11")

    assert(dstState != srcState)
    assert(dstState == requiredDstState)
  }

  "updateOnTop()" should "modify and move central item on top" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3

    val srcInnerMap = Map(
      0 -> LimitSizeMapItemValue[Int, String]("0", None, Some(1)),
      1 -> LimitSizeMapItemValue[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapItemValue[Int, String]("2", Some(1), Some(3)),
      3 -> LimitSizeMapItemValue[Int, String]("3", Some(2), Some(4)),
      4 -> LimitSizeMapItemValue[Int, String]("4", Some(3), None)
    )
    val srcState =
      LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(0), Some(4))

    val requiredInnerMap = Map(
      2 -> LimitSizeMapItemValue[Int, String]("22", None, Some(0)),
      0 -> LimitSizeMapItemValue[Int, String]("0", Some(2), Some(1)),
      1 -> LimitSizeMapItemValue[Int, String]("1", Some(0), Some(3)),
      3 -> LimitSizeMapItemValue[Int, String]("3", Some(1), Some(4)),
      4 -> LimitSizeMapItemValue[Int, String]("4", Some(3), None)
    )
    val requiredDstState = LimitSizeMapCacheState[Int, String](
      maxItemCount,
      itemCountAfterSizeCorrection,
      requiredInnerMap,
      Some(2),
      Some(4)
    )

    val dstState = srcState invokePrivate updateOnTopMethod(2, "22")

    assert(dstState != srcState)
    assert(dstState == requiredDstState)
  }

  "updateOnTop()" should "modify and move before the bottom item on top" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3

    val srcInnerMap = Map(
      0 -> LimitSizeMapItemValue[Int, String]("0", None, Some(1)),
      1 -> LimitSizeMapItemValue[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapItemValue[Int, String]("2", Some(1), Some(3)),
      3 -> LimitSizeMapItemValue[Int, String]("3", Some(2), Some(4)),
      4 -> LimitSizeMapItemValue[Int, String]("4", Some(3), None)
    )
    val srcState =
      LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(0), Some(4))

    val requiredInnerMap = Map(
      3 -> LimitSizeMapItemValue[Int, String]("33", None, Some(0)),
      0 -> LimitSizeMapItemValue[Int, String]("0", Some(3), Some(1)),
      1 -> LimitSizeMapItemValue[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapItemValue[Int, String]("2", Some(1), Some(4)),
      4 -> LimitSizeMapItemValue[Int, String]("4", Some(2), None)
    )
    val requiredDstState = LimitSizeMapCacheState[Int, String](
      maxItemCount,
      itemCountAfterSizeCorrection,
      requiredInnerMap,
      Some(3),
      Some(4)
    )

    val dstState = srcState invokePrivate updateOnTopMethod(3, "33")

    assert(dstState != srcState)
    assert(dstState == requiredDstState)
  }

  "updateOnTop()" should "modify and move bottom item on top" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3

    val srcInnerMap = Map(
      0 -> LimitSizeMapItemValue[Int, String]("0", None, Some(1)),
      1 -> LimitSizeMapItemValue[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapItemValue[Int, String]("2", Some(1), Some(3)),
      3 -> LimitSizeMapItemValue[Int, String]("3", Some(2), Some(4)),
      4 -> LimitSizeMapItemValue[Int, String]("4", Some(3), None)
    )
    val srcState =
      LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(0), Some(4))

    val requiredInnerMap = Map(
      4 -> LimitSizeMapItemValue[Int, String]("44", None, Some(0)),
      0 -> LimitSizeMapItemValue[Int, String]("0", Some(4), Some(1)),
      1 -> LimitSizeMapItemValue[Int, String]("1", Some(0), Some(2)),
      2 -> LimitSizeMapItemValue[Int, String]("2", Some(1), Some(3)),
      3 -> LimitSizeMapItemValue[Int, String]("3", Some(2), None)
    )
    val requiredDstState = LimitSizeMapCacheState[Int, String](
      maxItemCount,
      itemCountAfterSizeCorrection,
      requiredInnerMap,
      Some(4),
      Some(3)
    )

    val dstState = srcState invokePrivate updateOnTopMethod(4, "44")

    assert(dstState != srcState)
    assert(dstState == requiredDstState)
  }

  "updateOnTop()" should "modify and move bottom item on top in small state" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3

    val srcInnerMap = Map(
      0 -> LimitSizeMapItemValue[Int, String]("0", None, Some(1)),
      1 -> LimitSizeMapItemValue[Int, String]("1", Some(0), None)
    )
    val srcState =
      LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(0), Some(1))

    val requiredInnerMap = Map(
      1 -> LimitSizeMapItemValue[Int, String]("11", None, Some(0)),
      0 -> LimitSizeMapItemValue[Int, String]("0", Some(1), None)
    )
    val requiredDstState = LimitSizeMapCacheState[Int, String](
      maxItemCount,
      itemCountAfterSizeCorrection,
      requiredInnerMap,
      Some(1),
      Some(0)
    )

    val dstState = srcState invokePrivate updateOnTopMethod(1, "11")

    assert(dstState != srcState)
    assert(dstState == requiredDstState)
  }

  "updateOnTop()" should "add new pair of key->value on top" in {
    val maxItemCount = 5
    val itemCountAfterSizeCorrection = 3

    val srcInnerMap = Map(
      0 -> LimitSizeMapItemValue[Int, String]("0", None, Some(1)),
      1 -> LimitSizeMapItemValue[Int, String]("1", Some(0), None)
    )
    val srcState =
      LimitSizeMapCacheState[Int, String](maxItemCount, itemCountAfterSizeCorrection, srcInnerMap, Some(0), Some(1))

    val requiredInnerMap = Map(
      2 -> LimitSizeMapItemValue[Int, String]("2", None, Some(0)),
      0 -> LimitSizeMapItemValue[Int, String]("0", Some(2), Some(1)),
      1 -> LimitSizeMapItemValue[Int, String]("1", Some(0), None)
    )
    val requiredDstState = LimitSizeMapCacheState[Int, String](
      maxItemCount,
      itemCountAfterSizeCorrection,
      requiredInnerMap,
      Some(2),
      Some(1)
    )

    val dstState = srcState invokePrivate updateOnTopMethod(2, "2")

    assert(dstState != srcState)
    assert(dstState == requiredDstState)
  }
}

class LimitSizeMapCacheSpec extends AnyFlatSpec {
  "Initialized cache" should "be empty" in {
    def test[F[_]: Sync: Parallel](): F[Unit] = {
      val maxItemCount = 5
      val itemCountAfterSizeCorrection = 3
      for {
        cache <- LimitSizeMapCache[F, Int, String](maxItemCount, itemCountAfterSizeCorrection)

        item <- cache.get(0)
        _ = assert(item.isEmpty)
        state <- cache.stateRef.get
        items = state.items
        _ = assert(items.isEmpty)
        _ = assert(state.mayBeTopKey.isEmpty)
        _ = assert(state.mayBeBottomKey.isEmpty)
      } yield ()
    }

    test[Task]().runSyncUnsafe()
  }

  "Cache after 1 call of set()" should "be have 1 item" in {
    def test[F[_]: Sync: Parallel](): F[Unit] = {
      val maxItemCount = 5
      val itemCountAfterSizeCorrection = 3
      for {
        cache <- LimitSizeMapCache[F, Int, String](maxItemCount, itemCountAfterSizeCorrection)
        _ <- cache.set(0, "0")

        item0 <- cache.get(0)
        _ = assert(item0.isDefined && item0.get == "0")
        item1 <- cache.get(1)
        _ = assert(item1.isEmpty)
        state <- cache.stateRef.get
        items = state.items
        _ = assert(items.size == 1)
        _ = assert(items.contains(0))
        _ = assert(items(0).value == "0")
        _ = assert(items(0).mayBeNextKey.isEmpty && items(0).mayBePrevKey.isEmpty)
        _ = assert(state.mayBeTopKey.isDefined && state.mayBeBottomKey.isDefined)
        _ = assert(state.mayBeTopKey.contains(0) && state.mayBeBottomKey.contains(0))
      } yield ()
    }

    test[Task]().runSyncUnsafe()
  }

  "Cache after 2 call of set()" should "be have 2 item" in {
    def test[F[_]: Sync: Parallel](): F[Unit] = {
      val maxItemCount = 5
      val itemCountAfterSizeCorrection = 3
      for {
        cache <- LimitSizeMapCache[F, Int, String](maxItemCount, itemCountAfterSizeCorrection)
        _ <- cache.set(0, "0")
        _ <- cache.set(1, "1")

        item0 <- cache.get(0)
        _ = assert(item0.isDefined && item0.get == "0")
        item1 <- cache.get(1)
        _ = assert(item1.isDefined && item1.get == "1")
        item2 <- cache.get(2)
        _ = assert(item2.isEmpty)
        state <- cache.stateRef.get
        items = state.items
        _ = assert(items.size == 2)
        _ = assert(items.contains(0) && items.contains(1))
        _ = assert(items(0).value == "0" && items(1).value == "1")
        _ = assert(items(1).mayBeNextKey.isEmpty && items(1).mayBePrevKey.isDefined)
        _ = assert(items(0).mayBeNextKey.isDefined && items(0).mayBePrevKey.isEmpty)
        _ = assert(state.mayBeTopKey.isDefined && state.mayBeBottomKey.isDefined)
        _ = assert(state.mayBeTopKey.contains(1) && state.mayBeBottomKey.contains(0))
      } yield ()
    }

    test[Task]().runSyncUnsafe()
  }

  "Reading exists item from cache" should "modify items order if item is not on top" in {
    def test[F[_]: Sync: Parallel](): F[Unit] = {
      val maxItemCount = 5
      val itemCountAfterSizeCorrection = 3
      for {
        cache <- LimitSizeMapCache[F, Int, String](maxItemCount, itemCountAfterSizeCorrection)
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

  "Cache" should "store all added data if size not more then maxItemCount" in {
    def test[F[_]: Sync: Parallel](): F[Unit] = {
      val maxItemCount = 5
      val itemCountAfterSizeCorrection = 3
      for {
        cache <- LimitSizeMapCache[F, Int, String](maxItemCount, itemCountAfterSizeCorrection)
        _ <- cache.set(0, "0")
        _ <- cache.set(1, "1")
        _ <- cache.set(2, "2")
        _ <- cache.set(3, "3")
        _ <- cache.set(4, "4")

        state <- cache.stateRef.get
        items = state.items
        _ =
          assert(items.contains(0) && items.contains(1) && items.contains(2) && items.contains(3) && items.contains(4))
        _ = assert(
          items(0).value == "0" && items(1).value == "1" && items(2).value == "2" && items(3).value == "3" && items(
            4
          ).value == "4"
        )
        _ = assert(state.mayBeTopKey.contains(4))
        _ = assert(state.mayBeBottomKey.contains(0))
      } yield ()
    }

    test[Task]().runSyncUnsafe()
  }

  "Cache" should "decrease inner map size if this size more then maxItemCount" in {
    def test[F[_]: Sync: Parallel](): F[Unit] = {
      val maxItemCount = 5
      val itemCountAfterSizeCorrection = 3
      for {
        cache <- LimitSizeMapCache[F, Int, String](maxItemCount, itemCountAfterSizeCorrection)
        _ <- cache.set(0, "0")
        _ <- cache.set(1, "1")
        _ <- cache.set(2, "2")
        _ <- cache.set(3, "3")
        _ <- cache.set(4, "4")
        _ <- cache.set(5, "5")

        state <- cache.stateRef.get
        items = state.items
        _ = assert(!items.contains(0) && !items.contains(1) && !items.contains(2))
        _ = assert(items.contains(3) && items.contains(4) && items.contains(5))
        _ = assert(items(3).value == "3" && items(4).value == "4" && items(5).value == "5")
        _ = assert(state.mayBeTopKey.contains(5))
        _ = assert(state.mayBeBottomKey.contains(3))
      } yield ()
    }

    test[Task]().runSyncUnsafe()
  }

  /**
    * Next function need just for visual proof of multi-threaded work in next unit-tests
    * @param key - current cache key
    */
  def printThreadIdAndKey(key: Int): Unit = {
    val threadId = Thread.currentThread().getId()
    println(s"threadId: $threadId    \twrite key: $key")
  }

  "Cache" should "successfully perform multithread insertions" in {
    import cats.implicits._
    val cacheSize = 1000000
    val validKeys = (0 until cacheSize).toList
    val allKeys = validKeys ::: List(-1, cacheSize)

    val cache = LimitSizeMapCache[Task, Int, String](cacheSize, cacheSize).runSyncUnsafe()
    val tasks = validKeys.map(key => cache.set(key, key.toString) *> Task(printThreadIdAndKey(key)))
    Task.parSequence(tasks).runSyncUnsafe()

    val values = allKeys.traverse(key => cache.get(key)).runSyncUnsafe()
    values.take(cacheSize).map(v => assert(v.isDefined))
    values.takeRight(2).map(v => assert(v.isEmpty))
  }

  "Cache" should "successfully perform multithread reading" in {
    import cats.implicits._
    val cacheSize = 1000000
    val validKeys = (0 until cacheSize).toList
    val allKeys = validKeys ::: List(-1, cacheSize)
    val cache = LimitSizeMapCache[Task, Int, Int](cacheSize, cacheSize).runSyncUnsafe()
    validKeys.traverse(v => cache.set(v, v)).runSyncUnsafe()

    val tasks = allKeys.map(key => cache.get(key) <* Task(printThreadIdAndKey(key)))
    val values = Task.parSequence(tasks).runSyncUnsafe()

    values.take(cacheSize).map(v => assert(v.isDefined))
    values.takeRight(2).map(v => assert(v.isEmpty))
  }
}

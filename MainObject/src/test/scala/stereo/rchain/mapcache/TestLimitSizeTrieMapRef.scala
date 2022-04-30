package stereo.rchain.mapcache

import cats.effect.Sync
import org.scalatest.PrivateMethodTester
import org.scalatest.flatspec.AnyFlatSpec
import cats.effect.concurrent.Ref
import cats.syntax.all._
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global

class CacheItemSpec extends AnyFlatSpec {
  def checkNextKey(newMayBeNextKey: Option[Int], oldMayBeNextKey: Option[Int]): Unit = {
    val srcCacheItem = CacheItem[Int, String](value="this is string value", mayBeNextKey=oldMayBeNextKey, mayBePrevKey=None)
    val dstCacheItem = srcCacheItem.setNextKey(newMayBeNextKey)
    assert(srcCacheItem.value == dstCacheItem.value)
    assert(newMayBeNextKey == dstCacheItem.mayBeNextKey)
    assert(srcCacheItem.mayBePrevKey == dstCacheItem.mayBePrevKey)
    ()
  }

  "CacheItem::setNextKey" should "modify next key" in {
    checkNextKey(newMayBeNextKey=None,    oldMayBeNextKey=None)
    checkNextKey(newMayBeNextKey=Some(1), oldMayBeNextKey=None)
    checkNextKey(newMayBeNextKey=Some(1), oldMayBeNextKey=Some(2))
    checkNextKey(newMayBeNextKey=Some(1), oldMayBeNextKey=Some(1))
  }

  def checkPrevKey(newMayBeNextKey: Option[Int], oldMayBeNextKey: Option[Int]): Unit = {
    val srcCacheItem = CacheItem[Int, String](value="this is string value", mayBeNextKey=None, mayBePrevKey=oldMayBeNextKey)
    val dstCacheItem = srcCacheItem.setPrevKey(newMayBeNextKey)
    assert(srcCacheItem.value == dstCacheItem.value)
    assert(srcCacheItem.mayBeNextKey == dstCacheItem.mayBeNextKey)
    assert(newMayBeNextKey == dstCacheItem.mayBePrevKey)
    ()
  }

  "CacheItem::setPrevKey" should "set next key to None" in {
    checkPrevKey(newMayBeNextKey=None,    oldMayBeNextKey=None)
    checkPrevKey(newMayBeNextKey=Some(1), oldMayBeNextKey=None)
    checkPrevKey(newMayBeNextKey=Some(1), oldMayBeNextKey=Some(2))
    checkPrevKey(newMayBeNextKey=Some(1), oldMayBeNextKey=Some(1))
  }
}


class TrieMapCacheSpec extends AnyFlatSpec with PrivateMethodTester{
  "TrieMapCache::cleanOldRecords" should "not clean old records in empty cache" in {
    val limitSize = 100
    val srcCache = TrieMapCache[Int, String](limitSize)
    val dstCache = srcCache.cleanOldRecords()
    assert(srcCache == dstCache)
  }

  "TrieMapCache::cleanOldRecords" should "not clean old records while cache size not more then limitSize" in {
    val limitSize = 5
    val reducingFactor = 0.7
    val innerMap = Map(
      0 -> CacheItem[Int, String]("0", None, Some(1)),
      1 -> CacheItem[Int, String]("1", Some(0), Some(2)),
      2 -> CacheItem[Int, String]("2", Some(1), Some(3)),
      3 -> CacheItem[Int, String]("3", Some(2), Some(4)),
      4 -> CacheItem[Int, String]("4", Some(3), None))
    val srcCache = TrieMapCache[Int, String](limitSize, reducingFactor, innerMap, Some(0), Some(4))
    val dstCache = srcCache.cleanOldRecords()
    assert(srcCache == dstCache)
  }

  "TrieMapCache::cleanOldRecords" should "clean old records" in {
    val limitSize = 5
    val reducingFactor = 0.7

    val srcInnerMap = Map(
      0 -> CacheItem[Int, String]("0", None, Some(1)),
      1 -> CacheItem[Int, String]("1", Some(0), Some(2)),
      2 -> CacheItem[Int, String]("2", Some(1), Some(3)),
      3 -> CacheItem[Int, String]("3", Some(2), Some(4)),
      4 -> CacheItem[Int, String]("4", Some(3), Some(5)),
      5 -> CacheItem[Int, String]("5", Some(4), Some(6)),
      6 -> CacheItem[Int, String]("6", Some(5), Some(7)),
      7 -> CacheItem[Int, String]("7", Some(6), Some(8)),
      8 -> CacheItem[Int, String]("8", Some(7), Some(9)),
      9 -> CacheItem[Int, String]("9", Some(8), None))
    val srcCache = TrieMapCache[Int, String](limitSize, reducingFactor, srcInnerMap, Some(0), Some(9))

    // size of requiredInnerMap is (limitSize * reducingFactor).toInt = (5 * 0.7).toInt = 3
    val requiredInnerMap = Map(
      0 -> CacheItem[Int, String]("0", None, Some(1)),
      1 -> CacheItem[Int, String]("1", Some(0), Some(2)),
      2 -> CacheItem[Int, String]("2", Some(1), None))
    val requiredDstCache = TrieMapCache[Int, String](limitSize, reducingFactor, requiredInnerMap, Some(0), Some(2))

    val dstCache = srcCache.cleanOldRecords()

    assert(dstCache != srcCache)
    assert(dstCache == requiredDstCache)
  }

  "TrieMapCache::moveRecordOnTop" should "not move top item on top (should stay cache without modifications)" in {
    val limitSize = 5
    val reducingFactor = 0.7

    val srcInnerMap = Map(
      0 -> CacheItem[Int, String]("0", None,    Some(1)),
      1 -> CacheItem[Int, String]("1", Some(0), Some(2)),
      2 -> CacheItem[Int, String]("2", Some(1), Some(3)),
      3 -> CacheItem[Int, String]("3", Some(2), Some(4)),
      4 -> CacheItem[Int, String]("4", Some(3), None))
    val srcCache = TrieMapCache[Int, String](limitSize, reducingFactor, srcInnerMap, Some(0), Some(4))

    val dstCache = srcCache.moveRecordOnTop(0)

    assert(dstCache == srcCache)
  }

  "TrieMapCache::moveRecordOnTop" should "move before the top item on top" in {
    val limitSize = 5
    val reducingFactor = 0.7

    val srcInnerMap = Map(
      0 -> CacheItem[Int, String]("0", None, Some(1)),
      1 -> CacheItem[Int, String]("1", Some(0), Some(2)),
      2 -> CacheItem[Int, String]("2", Some(1), Some(3)),
      3 -> CacheItem[Int, String]("3", Some(2), Some(4)),
      4 -> CacheItem[Int, String]("4", Some(3), None))
    val srcCache = TrieMapCache[Int, String](limitSize, reducingFactor, srcInnerMap, Some(0), Some(4))

    val requiredInnerMap = Map(
      1 -> CacheItem[Int, String]("1", None, Some(0)),
      0 -> CacheItem[Int, String]("0", Some(1), Some(2)),
      2 -> CacheItem[Int, String]("2", Some(0), Some(3)),
      3 -> CacheItem[Int, String]("3", Some(2), Some(4)),
      4 -> CacheItem[Int, String]("4", Some(3), None))
    val requiredDstCache = TrieMapCache[Int, String](limitSize, reducingFactor, requiredInnerMap, Some(1), Some(4))

    val dstCache = srcCache.moveRecordOnTop(1)

    assert(dstCache != srcCache)
    assert(dstCache == requiredDstCache)
  }

  "TrieMapCache::moveRecordOnTop" should "move central item on top" in {
    val limitSize = 5
    val reducingFactor = 0.7

    val srcInnerMap = Map(
      0 -> CacheItem[Int, String]("0", None, Some(1)),
      1 -> CacheItem[Int, String]("1", Some(0), Some(2)),
      2 -> CacheItem[Int, String]("2", Some(1), Some(3)),
      3 -> CacheItem[Int, String]("3", Some(2), Some(4)),
      4 -> CacheItem[Int, String]("4", Some(3), None))
    val srcCache = TrieMapCache[Int, String](limitSize, reducingFactor, srcInnerMap, Some(0), Some(4))

    val requiredInnerMap = Map(
      2 -> CacheItem[Int, String]("2", None, Some(0)),
      0 -> CacheItem[Int, String]("0", Some(2), Some(1)),
      1 -> CacheItem[Int, String]("1", Some(0), Some(3)),
      3 -> CacheItem[Int, String]("3", Some(1), Some(4)),
      4 -> CacheItem[Int, String]("4", Some(3), None))
    val requiredDstCache = TrieMapCache[Int, String](limitSize, reducingFactor, requiredInnerMap, Some(2), Some(4))

    val dstCache = srcCache.moveRecordOnTop(2)

    assert(dstCache != srcCache)
    assert(dstCache == requiredDstCache)
  }

  "TrieMapCache::moveRecordOnTop" should "move before the bottom item on top" in {
    val limitSize = 5
    val reducingFactor = 0.7

    val srcInnerMap = Map(
      0 -> CacheItem[Int, String]("0", None, Some(1)),
      1 -> CacheItem[Int, String]("1", Some(0), Some(2)),
      2 -> CacheItem[Int, String]("2", Some(1), Some(3)),
      3 -> CacheItem[Int, String]("3", Some(2), Some(4)),
      4 -> CacheItem[Int, String]("4", Some(3), None))
    val srcCache = TrieMapCache[Int, String](limitSize, reducingFactor, srcInnerMap, Some(0), Some(4))

    val requiredInnerMap = Map(
      3 -> CacheItem[Int, String]("3", None, Some(0)),
      0 -> CacheItem[Int, String]("0", Some(3), Some(1)),
      1 -> CacheItem[Int, String]("1", Some(0), Some(2)),
      2 -> CacheItem[Int, String]("2", Some(1), Some(4)),
      4 -> CacheItem[Int, String]("4", Some(2), None))
    val requiredDstCache = TrieMapCache[Int, String](limitSize, reducingFactor, requiredInnerMap, Some(3), Some(4))

    val dstCache = srcCache.moveRecordOnTop(3)

    assert(dstCache != srcCache)
    assert(dstCache == requiredDstCache)
  }

  "TrieMapCache::moveRecordOnTop" should "move bottom item on top" in {
    val limitSize = 5
    val reducingFactor = 0.7

    val srcInnerMap = Map(
      0 -> CacheItem[Int, String]("0", None, Some(1)),
      1 -> CacheItem[Int, String]("1", Some(0), Some(2)),
      2 -> CacheItem[Int, String]("2", Some(1), Some(3)),
      3 -> CacheItem[Int, String]("3", Some(2), Some(4)),
      4 -> CacheItem[Int, String]("4", Some(3), None))
    val srcCache = TrieMapCache[Int, String](limitSize, reducingFactor, srcInnerMap, Some(0), Some(4))

    val requiredInnerMap = Map(
      4 -> CacheItem[Int, String]("4", None, Some(0)),
      0 -> CacheItem[Int, String]("0", Some(4), Some(1)),
      1 -> CacheItem[Int, String]("1", Some(0), Some(2)),
      2 -> CacheItem[Int, String]("2", Some(1), Some(3)),
      3 -> CacheItem[Int, String]("3", Some(2), None))
    val requiredDstCache = TrieMapCache[Int, String](limitSize, reducingFactor, requiredInnerMap, Some(4), Some(3))

    val dstCache = srcCache.moveRecordOnTop(key=4)

    assert(dstCache != srcCache)
    assert(dstCache == requiredDstCache)
  }

  "TrieMapCache::moveRecordOnTop" should "move bottom item on top in small cache" in {
    val limitSize = 5
    val reducingFactor = 0.7

    val srcInnerMap = Map(
      0 -> CacheItem[Int, String]("0", None, Some(1)),
      1 -> CacheItem[Int, String]("1", Some(0), None))
    val srcCache = TrieMapCache[Int, String](limitSize, reducingFactor, srcInnerMap, Some(0), Some(1))

    val requiredInnerMap = Map(
      1 -> CacheItem[Int, String]("1", None, Some(0)),
      0 -> CacheItem[Int, String]("0", Some(1), None))
    val requiredDstCache = TrieMapCache[Int, String](limitSize, reducingFactor, requiredInnerMap, Some(1), Some(0))

    val dstCache = srcCache.moveRecordOnTop(key=1)

    assert(dstCache != srcCache)
    assert(dstCache == requiredDstCache)
  }

  "TrieMapCache::setValueByKey" should "modify single cache item value" in {
    val limitSize = 5
    val reducingFactor = 0.7
    val key1 = 0
    val key2 = key1 + 1
    val newValue = (key2 + 1).toString

    val srcInnerMap = Map(
      key1 -> CacheItem[Int, String](key1.toString, None, Some(key2)),
      key2 -> CacheItem[Int, String](key2.toString, Some(key1), None))
    val srcCache = TrieMapCache[Int, String](limitSize, reducingFactor, srcInnerMap, Some(key1), Some(1))

    val requiredInnerMap = Map(
      key1 -> CacheItem[Int, String](newValue, None, Some(key2)),
      key2 -> CacheItem[Int, String](key2.toString, Some(key1), None))
    val requiredDstCache = TrieMapCache[Int, String](limitSize, reducingFactor, requiredInnerMap, Some(0), Some(1))

    val setValueByKeyMethod = PrivateMethod[TrieMapCache[Int, String]](methodName = Symbol("setValueByKey"))
    val dstCache = srcCache invokePrivate setValueByKeyMethod(key1, newValue)

    assert(dstCache != srcCache)
    assert(dstCache == requiredDstCache)
  }

  "TrieMapCache::updateOnTop" should "just modify top item" in {
    val limitSize = 5
    val reducingFactor = 0.7

    val srcInnerMap = Map(
      0 -> CacheItem[Int, String]("0", None,    Some(1)),
      1 -> CacheItem[Int, String]("1", Some(0), Some(2)),
      2 -> CacheItem[Int, String]("2", Some(1), Some(3)),
      3 -> CacheItem[Int, String]("3", Some(2), Some(4)),
      4 -> CacheItem[Int, String]("4", Some(3), None))
    val srcCache = TrieMapCache[Int, String](limitSize, reducingFactor, srcInnerMap, Some(0), Some(4))

    val requiredInnerMap = Map(
      0 -> CacheItem[Int, String]("00", None,    Some(1)),
      1 -> CacheItem[Int, String]("1", Some(0), Some(2)),
      2 -> CacheItem[Int, String]("2", Some(1), Some(3)),
      3 -> CacheItem[Int, String]("3", Some(2), Some(4)),
      4 -> CacheItem[Int, String]("4", Some(3), None))
    val requiredDstCache = TrieMapCache[Int, String](limitSize, reducingFactor, requiredInnerMap, Some(0), Some(4))

    val dstCache = srcCache.updateOnTop(0, "00")

    assert(dstCache != srcCache)
    assert(dstCache == requiredDstCache)
  }

  "TrieMapCache::updateOnTop" should "modify and move before the top item on top" in {
    val limitSize = 5
    val reducingFactor = 0.7

    val srcInnerMap = Map(
      0 -> CacheItem[Int, String]("0", None, Some(1)),
      1 -> CacheItem[Int, String]("1", Some(0), Some(2)),
      2 -> CacheItem[Int, String]("2", Some(1), Some(3)),
      3 -> CacheItem[Int, String]("3", Some(2), Some(4)),
      4 -> CacheItem[Int, String]("4", Some(3), None))
    val srcCache = TrieMapCache[Int, String](limitSize, reducingFactor, srcInnerMap, Some(0), Some(4))

    val requiredInnerMap = Map(
      1 -> CacheItem[Int, String]("11", None, Some(0)),
      0 -> CacheItem[Int, String]("0", Some(1), Some(2)),
      2 -> CacheItem[Int, String]("2", Some(0), Some(3)),
      3 -> CacheItem[Int, String]("3", Some(2), Some(4)),
      4 -> CacheItem[Int, String]("4", Some(3), None))
    val requiredDstCache = TrieMapCache[Int, String](limitSize, reducingFactor, requiredInnerMap, Some(1), Some(4))

    val dstCache = srcCache.updateOnTop(1, "11")

    assert(dstCache != srcCache)
    assert(dstCache == requiredDstCache)
  }

  "TrieMapCache::updateOnTop" should "modify and move central item on top" in {
    val limitSize = 5
    val reducingFactor = 0.7

    val srcInnerMap = Map(
      0 -> CacheItem[Int, String]("0", None, Some(1)),
      1 -> CacheItem[Int, String]("1", Some(0), Some(2)),
      2 -> CacheItem[Int, String]("2", Some(1), Some(3)),
      3 -> CacheItem[Int, String]("3", Some(2), Some(4)),
      4 -> CacheItem[Int, String]("4", Some(3), None))
    val srcCache = TrieMapCache[Int, String](limitSize, reducingFactor, srcInnerMap, Some(0), Some(4))

    val requiredInnerMap = Map(
      2 -> CacheItem[Int, String]("22", None, Some(0)),
      0 -> CacheItem[Int, String]("0", Some(2), Some(1)),
      1 -> CacheItem[Int, String]("1", Some(0), Some(3)),
      3 -> CacheItem[Int, String]("3", Some(1), Some(4)),
      4 -> CacheItem[Int, String]("4", Some(3), None))
    val requiredDstCache = TrieMapCache[Int, String](limitSize, reducingFactor, requiredInnerMap, Some(2), Some(4))

    val dstCache = srcCache.updateOnTop(2, "22")

    assert(dstCache != srcCache)
    assert(dstCache == requiredDstCache)
  }

  "TrieMapCache::updateOnTop" should "modify and move before the bottom item on top" in {
    val limitSize = 5
    val reducingFactor = 0.7

    val srcInnerMap = Map(
      0 -> CacheItem[Int, String]("0", None, Some(1)),
      1 -> CacheItem[Int, String]("1", Some(0), Some(2)),
      2 -> CacheItem[Int, String]("2", Some(1), Some(3)),
      3 -> CacheItem[Int, String]("3", Some(2), Some(4)),
      4 -> CacheItem[Int, String]("4", Some(3), None))
    val srcCache = TrieMapCache[Int, String](limitSize, reducingFactor, srcInnerMap, Some(0), Some(4))

    val requiredInnerMap = Map(
      3 -> CacheItem[Int, String]("33", None, Some(0)),
      0 -> CacheItem[Int, String]("0", Some(3), Some(1)),
      1 -> CacheItem[Int, String]("1", Some(0), Some(2)),
      2 -> CacheItem[Int, String]("2", Some(1), Some(4)),
      4 -> CacheItem[Int, String]("4", Some(2), None))
    val requiredDstCache = TrieMapCache[Int, String](limitSize, reducingFactor, requiredInnerMap, Some(3), Some(4))

    val dstCache = srcCache.updateOnTop(3, "33")

    assert(dstCache != srcCache)
    assert(dstCache == requiredDstCache)
  }

  "TrieMapCache::updateOnTop" should "modify and move bottom item on top" in {
    val limitSize = 5
    val reducingFactor = 0.7

    val srcInnerMap = Map(
      0 -> CacheItem[Int, String]("0", None, Some(1)),
      1 -> CacheItem[Int, String]("1", Some(0), Some(2)),
      2 -> CacheItem[Int, String]("2", Some(1), Some(3)),
      3 -> CacheItem[Int, String]("3", Some(2), Some(4)),
      4 -> CacheItem[Int, String]("4", Some(3), None))
    val srcCache = TrieMapCache[Int, String](limitSize, reducingFactor, srcInnerMap, Some(0), Some(4))

    val requiredInnerMap = Map(
      4 -> CacheItem[Int, String]("44", None, Some(0)),
      0 -> CacheItem[Int, String]("0", Some(4), Some(1)),
      1 -> CacheItem[Int, String]("1", Some(0), Some(2)),
      2 -> CacheItem[Int, String]("2", Some(1), Some(3)),
      3 -> CacheItem[Int, String]("3", Some(2), None))
    val requiredDstCache = TrieMapCache[Int, String](limitSize, reducingFactor, requiredInnerMap, Some(4), Some(3))

    val dstCache = srcCache.updateOnTop(key=4, "44")

    assert(dstCache != srcCache)
    assert(dstCache == requiredDstCache)
  }

  "TrieMapCache::updateOnTop" should "modify and move bottom item on top in small cache" in {
    val limitSize = 5
    val reducingFactor = 0.7

    val srcInnerMap = Map(
      0 -> CacheItem[Int, String]("0", None, Some(1)),
      1 -> CacheItem[Int, String]("1", Some(0), None))
    val srcCache = TrieMapCache[Int, String](limitSize, reducingFactor, srcInnerMap, Some(0), Some(1))

    val requiredInnerMap = Map(
      1 -> CacheItem[Int, String]("11", None, Some(0)),
      0 -> CacheItem[Int, String]("0", Some(1), None))
    val requiredDstCache = TrieMapCache[Int, String](limitSize, reducingFactor, requiredInnerMap, Some(1), Some(0))

    val dstCache = srcCache.updateOnTop(key=1, "11")

    assert(dstCache != srcCache)
    assert(dstCache == requiredDstCache)
  }

  "TrieMapCache::updateOnTop" should "add new pair of key->value on top" in {
    val limitSize = 5
    val reducingFactor = 0.7

    val srcInnerMap = Map(
      0 -> CacheItem[Int, String]("0", None, Some(1)),
      1 -> CacheItem[Int, String]("1", Some(0), None))
    val srcCache = TrieMapCache[Int, String](limitSize, reducingFactor, srcInnerMap, Some(0), Some(1))

    val requiredInnerMap = Map(
      2 -> CacheItem[Int, String]("2", None, Some(0)),
      0 -> CacheItem[Int, String]("0", Some(2), Some(1)),
      1 -> CacheItem[Int, String]("1", Some(0), None))
    val requiredDstCache = TrieMapCache[Int, String](limitSize, reducingFactor, requiredInnerMap, Some(2), Some(1))

    val dstCache = srcCache.updateOnTop(key=2, "2")

    assert(dstCache != srcCache)
    assert(dstCache == requiredDstCache)
  }
}


class LimitSizeTrieMapThreadUnsafeRefSpec extends AnyFlatSpec {
  "Initialized TrieMapCache" should "be empty" in {
    def test[F[_] : Sync](): F[Unit] = {
      val sizeLimit = 5
      val reducingFactor = 0.7
      for {
        ref <- Ref.of[F, TrieMapCache[Int, String]](TrieMapCache[Int, String](sizeLimit, reducingFactor))
        cache = LimitSizeTrieMapRef(ref)

        item <- cache.get(0)
        _ = assert(item.isEmpty)
        innerCache <- cache.refToCache.get
        records = innerCache.records
        _ = assert(records.isEmpty)
        _ = assert(innerCache.mayBeTopKey.isEmpty)
        _ = assert(innerCache.mayBeBottomKey.isEmpty)
      } yield ()
    }

    test[Task]().runSyncUnsafe()
  }

  "TrieMapCache after 1 call of set()" should "be have 1 item" in {
    def test[F[_] : Sync](): F[Unit] = {
      val sizeLimit = 5
      val reducingFactor = 0.7
      for {
        ref <- Ref.of[F, TrieMapCache[Int, String]](TrieMapCache[Int, String](sizeLimit, reducingFactor))
        cache = LimitSizeTrieMapRef(ref)
        _ <- cache.set(0, "0")

        item0 <- cache.get(0)
        _ = assert(item0.isDefined && item0.get == "0")
        item1 <- cache.get(1)
        _ = assert(item1.isEmpty)
        innerCache <- cache.refToCache.get
        records = innerCache.records
        _ = assert(records.size == 1)
        _ = assert(records.contains(0))
        _ = assert(records(0).value == "0")
        _ = assert(records(0).mayBeNextKey.isEmpty && records(0).mayBePrevKey.isEmpty)
        _ = assert(innerCache.mayBeTopKey.isDefined && innerCache.mayBeBottomKey.isDefined)
        _ = assert(innerCache.mayBeTopKey.contains(0) && innerCache.mayBeBottomKey.contains(0))
      } yield ()
    }

    test[Task]().runSyncUnsafe()
  }

  "TrieMapCache after 2 call of set()" should "be have 2 item" in {
    def test[F[_] : Sync](): F[Unit] = {
      val sizeLimit = 5
      val reducingFactor = 0.7
      for {
        ref <- Ref.of[F, TrieMapCache[Int, String]](TrieMapCache[Int, String](sizeLimit, reducingFactor))
        cache = LimitSizeTrieMapRef(ref)
        _ <- cache.set(0, "0")
        _ <- cache.set(1, "1")

        item0 <- cache.get(0)
        _ = assert(item0.isDefined && item0.get == "0")
        item1 <- cache.get(1)
        _ = assert(item1.isDefined && item1.get == "1")
        item2 <- cache.get(2)
        _ = assert(item2.isEmpty)
        innerCache <- cache.refToCache.get
        records = innerCache.records
        _ = assert(records.size == 2)
        _ = assert(records.contains(0) && records.contains(1))
        _ = assert(records(0).value == "0" && records(1).value == "1")
        _ = assert(records(1).mayBeNextKey.isEmpty && records(1).mayBePrevKey.isDefined)
        _ = assert(records(0).mayBeNextKey.isDefined && records(0).mayBePrevKey.isEmpty)
        _ = assert(innerCache.mayBeTopKey.isDefined && innerCache.mayBeBottomKey.isDefined)
        _ = assert(innerCache.mayBeTopKey.contains(1) && innerCache.mayBeBottomKey.contains(0))
      } yield ()
    }

    test[Task]().runSyncUnsafe()
  }

  "Reading exists item from TrieMapCache" should "modify items order if item is not on top" in {
    def test[F[_] : Sync](): F[Unit] = {
      val sizeLimit = 5
      val reducingFactor = 0.7
      for {
        ref <- Ref.of[F, TrieMapCache[Int, String]](TrieMapCache[Int, String](sizeLimit, reducingFactor))
        cache = LimitSizeTrieMapRef(ref)
        _ <- cache.set(0, "0")
        _ <- cache.set(1, "1")
        srcCache <- cache.refToCache.get
        _ <- cache.get(0)
        dstCache <- cache.refToCache.get

        _ = assert(srcCache.mayBeTopKey.contains(1) && srcCache.mayBeBottomKey.contains(0))
        _ = assert(dstCache.mayBeTopKey.contains(0) && dstCache.mayBeBottomKey.contains(1))
        _ = assert(srcCache.records(1).mayBeNextKey.isEmpty && srcCache.records(1).mayBePrevKey.isDefined)
        _ = assert(dstCache.records(0).mayBeNextKey.isEmpty && dstCache.records(0).mayBePrevKey.isDefined)
        _ = assert(dstCache.records(0).value == dstCache.records(0).value)
        _ = assert(dstCache.records(1).value == dstCache.records(1).value)
      } yield ()
    }

    test[Task]().runSyncUnsafe()
  }

  "TrieMapCache" should "store all added data if size not more then limitSize" in {
    def test[F[_] : Sync](): F[Unit] = {
      val sizeLimit = 5
      val reducingFactor = 0.7
      for {
        ref <- Ref.of[F, TrieMapCache[Int, String]](TrieMapCache[Int, String](sizeLimit, reducingFactor))
        cache = LimitSizeTrieMapRef(ref)
        _ <- cache.set(0, "0")
        _ <- cache.set(1, "1")
        _ <- cache.set(2, "2")
        _ <- cache.set(3, "3")
        _ <- cache.set(4, "4")

        innerCache <- cache.refToCache.get
        records = innerCache.records
        _ = assert(records.contains(0) && records.contains(1) && records.contains(2) && records.contains(3) && records.contains(4))
        _ = assert(records(0).value == "0" && records(1).value == "1" && records(2).value == "2" && records(3).value == "3" && records(4).value == "4")
        _ = assert(innerCache.mayBeTopKey.contains(4))
        _ = assert(innerCache.mayBeBottomKey.contains(0))
      } yield ()
    }

    test[Task]().runSyncUnsafe()
  }

  "TrieMapCache" should "decrease inner map size if this size more then limitSize" in {
    def test[F[_] : Sync](): F[Unit] = {
      val sizeLimit = 5
      val reducingFactor = 0.7
      for {
        ref <- Ref.of[F, TrieMapCache[Int, String]](TrieMapCache[Int, String](sizeLimit, reducingFactor))
        cache = LimitSizeTrieMapRef(ref)
        _ <- cache.set(0, "0")
        _ <- cache.set(1, "1")
        _ <- cache.set(2, "2")
        _ <- cache.set(3, "3")
        _ <- cache.set(4, "4")
        _ <- cache.set(5, "5")

        innerCache <- cache.refToCache.get
        records = innerCache.records
        _ = assert(!records.contains(0) && !records.contains(1) && !records.contains(2))
        _ = assert(records.contains(3) && records.contains(4) && records.contains(5))
        _ = assert(records(3).value == "3" && records(4).value == "4" && records(5).value == "5")
        _ = assert(innerCache.mayBeTopKey.contains(5))
        _ = assert(innerCache.mayBeBottomKey.contains(3))
      } yield ()
    }

    test[Task]().runSyncUnsafe()
  }

  "TrieMapCache2" should "decrease inner map size if this size more then limitSize" in {
    def test[F[_] : Sync](): F[Unit] = {
      val sizeLimit = 5
      val reducingFactor = 0.7
      for {
        ref <- Ref.of[F, TrieMapCache[Int, String]](TrieMapCache[Int, String](sizeLimit, reducingFactor))
        cache = LimitSizeTrieMapRef(ref)
        _ <- cache.set(0, "0")
        _ <- cache.set(1, "1")
        _ <- cache.set(2, "2")
        _ <- cache.set(3, "3")
        _ <- cache.set(4, "4")
        _ <- cache.set(5, "5")
        _ <- cache.get(0)
        _ <- cache.get(1)
        _ <- cache.get(2)
        _ <- cache.get(3)
        _ <- cache.get(4)
        _ <- cache.set(5, "0")
      } yield ()
    }

    test[Task]().runSyncUnsafe()
  }
}
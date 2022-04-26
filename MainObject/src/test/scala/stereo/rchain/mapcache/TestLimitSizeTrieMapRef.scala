package stereo.rchain.mapcache

import org.scalatest.flatspec.AnyFlatSpec


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

class TrieMapCacheSpec extends AnyFlatSpec {
  "TrieMapCache::cleanOldRecords" should "not clean old records in empty cache" in {
    val limitSize = 100
    val srcCache = TrieMapCache[Int, String](limitSize)
    val dstCache = srcCache.cleanOldRecords()
    assert(srcCache == dstCache)
  }

  "TrieMapCache::cleanOldRecords" should "not clean old records while cache size not more then limit" in {
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

  "TrieMapCache::moveRecordOnTop" should "not move top item on top" in {
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
}
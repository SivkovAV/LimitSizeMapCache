package coop.rchain.rspace.history

import java.io.{File, PrintWriter}
import scala.collection.concurrent.TrieMap
import java.security.MessageDigest


object CacheExperiments {
  sealed trait TrieMapTestTrait {
    val name: String

    def get(key: Array[Byte]): Option[Int]

    def set(key: Array[Byte], value: Int): Unit
  }

  class LimitTrieMap(val size: Int) extends TrieMapTestTrait {
    override val name: String = "LimitSizeTrieMap"
    private val cache = new LimitSizeTrieMap[Array[Byte], Int](size)

    override def get(key: Array[Byte]): Option[Int] = cache.get(key)

    override def set(key: Array[Byte], value: Int): Unit = cache.set(key, value)
  }

  class MultiThreadLimitTrieMap(val size: Int) extends TrieMapTestTrait {
    override val name: String = "MultiThreadLimitTrieMap"
    private val cache = new MultiThreadLimitSizeTrieMap[Array[Byte], Int](size)

    override def get(key: Array[Byte]): Option[Int] = cache.get(key)

    override def set(key: Array[Byte], value: Int): Unit = cache.set(key, value)
  }

  class SimpleTriaMap extends TrieMapTestTrait {
    override val name: String = "TrieMap"
    private val cache = new TrieMap[Array[Byte], Int]

    override def get(key: Array[Byte]): Option[Int] = cache.get(key)

    override def set(key: Array[Byte], value: Int): Unit = cache(key) = value
  }

  sealed trait TrieMapEvent {
    val key: Array[Byte]
  }

  case class GetValue(override val key: Array[Byte]) extends TrieMapEvent

  case class SetValue(override val key: Array[Byte], value: Int) extends TrieMapEvent

  def processEventsQueue(cache: TrieMapTestTrait, queue: List[TrieMapEvent]): Unit = {
    queue.foreach {
      case GetValue(key) => cache.get(key)
      case SetValue(key, value) => cache.set(key, value)
    }
  }

  def calculateCacheWorkTime(cache: TrieMapTestTrait, queue: List[TrieMapEvent]): Long = {
    val beginTime = System.nanoTime
    processEventsQueue(cache, queue)
    System.nanoTime - beginTime
  }

  def calculateCachesWorkTime(caches: List[TrieMapTestTrait],
                              queue: List[TrieMapEvent]): List[Long] = {
    caches.map(cache => calculateCacheWorkTime(cache, queue))
  }

  def writeGoogleVisualizationFile(fileNamePostfix: String, googleVisualizationTemplate: GoogleVisualizationTemplate,
                                   resultFileDir: String, resultFileName: String,
                                   caches: List[TrieMapTestTrait], periods: List[List[Long]]): Unit = {
    val resultFilePath = List(resultFileDir, "/", resultFileName, fileNamePostfix).mkString
    val directory = new File(resultFileDir)
    if (!directory.exists())
      directory.mkdir()
    val bottomAxeName = "'Measurements'"
    val labels = bottomAxeName :: caches.map(cache => "'" + cache.name + "'")
    val chartSeries = periods.indices.map(i => (i.toLong :: periods(i)).mkString("[", ",", "]")).toList
    val chartData = (labels.mkString("[", ",", "]") :: chartSeries).mkString("[", ",", "]")

    val printWriter = new PrintWriter(new File(resultFilePath))
    printWriter.write(googleVisualizationTemplate.html(chartData))
    printWriter.close()

    print(s"Results was saved by path $resultFilePath\n")
  }

  def writeLineChartFile(resultFileDir: String, resultFileName: String,
                         caches: List[TrieMapTestTrait], periods: List[List[Long]]): Unit = {
    writeGoogleVisualizationFile("_lineChart.html", new LineChartTemplate,
      resultFileDir, resultFileName, caches, periods)
  }


  def writeBarChartFile(resultFileDir: String, resultFileName: String,
                        caches: List[TrieMapTestTrait], periods: List[List[Long]]): Unit = {
    writeGoogleVisualizationFile("_barChart.html", new BarChartTemplate,
      resultFileDir, resultFileName, caches, periods)
  }

  def sha256(value: Int) = {
    MessageDigest.getInstance("sha-256").digest(BigInt(value).toByteArray)
  }

  def prepareGetEvents(uniqueCount: Int, copyCount: Int = 1): List[TrieMapEvent] = {
    val uniqueEvents = (0 until uniqueCount).toList.map(i => GetValue(sha256(i)))
    List.fill(copyCount)(uniqueEvents).flatten
  }

  def prepareSetEvents(uniqueCount: Int, copyCount: Int = 1): List[TrieMapEvent] = {
    val uniqueEvents = (0 until uniqueCount).toList.map(i => SetValue(sha256(i), i))
    List.fill(copyCount)(uniqueEvents).flatten
  }

  def prepareCaches(limitTriaMapSize: Int = 100): List[TrieMapTestTrait] = {
    val triaMap1 = new SimpleTriaMap
    val triaMap2 = new LimitTrieMap(limitTriaMapSize)
    val triaMap3 = new MultiThreadLimitTrieMap(limitTriaMapSize)
    List(triaMap1, triaMap2, triaMap3)
  }

  def repeat(experimentCount: Int, process: Unit => List[Long]) = List.fill(experimentCount)(process())

  def testReadManyOldItemsOnly(experimentCount: Int, resultFileDir: String, resultFileName: String): Unit = {
    println("testReadManyOldItemsOnly")
    val limitTriaMapSize = 1000
    val scale = 1000
    val ItemsCount = limitTriaMapSize * scale
    val caches = prepareCaches(limitTriaMapSize)
    val queue = prepareGetEvents(ItemsCount)

    // setup cache data
    calculateCachesWorkTime(caches, prepareSetEvents(ItemsCount))
    val periods = repeat(experimentCount, Unit => {calculateCachesWorkTime(caches, queue)})
    writeLineChartFile(resultFileDir, resultFileName, caches, periods)
    //writeBarChartFile(resultFileDir, resultFileName, caches, periods)
  }

  def testReadOldItemsOnly(experimentCount: Int, resultFileDir: String, resultFileName: String): Unit = {
    println("testReadOldItemsOnly")
    val limitTriaMapSize = 1000
    val scale = 1
    val ItemsCount = limitTriaMapSize * scale
    val caches = prepareCaches(limitTriaMapSize)
    val queue = prepareGetEvents(limitTriaMapSize)

    // setup cache data
    calculateCachesWorkTime(caches, prepareSetEvents(ItemsCount))
    val periods = repeat(experimentCount, Unit => {calculateCachesWorkTime(caches, queue)})
    writeLineChartFile(resultFileDir, resultFileName, caches, periods)
    //writeBarChartFile(resultFileDir, resultFileName, caches, periods)
  }

  def testAddNewItemsOnly(experimentCount: Int, resultFileDir: String, resultFileName: String): Unit = {
    println("testAddNewItemsOnly")
    val limitTriaMapSize = 1000
    val caches = prepareCaches(limitTriaMapSize)
    val periods = repeat(experimentCount, Unit => {calculateCachesWorkTime(caches, prepareSetEvents(limitTriaMapSize))})
    writeLineChartFile(resultFileDir, resultFileName, caches, periods)
    //writeBarChartFile(resultFileDir, resultFileName, caches, periods)
  }

  def main(args: Array[String]): Unit = {
    val resultDir = "./resultHTML"
    val experimentCount = 20
    testReadManyOldItemsOnly(experimentCount, resultDir, "cachesCompare_readManyOld")
    testReadOldItemsOnly(    experimentCount, resultDir, "cachesCompare_readOld")
    testAddNewItemsOnly(     experimentCount, resultDir, "cachesCompare_writeNew")
  }
}
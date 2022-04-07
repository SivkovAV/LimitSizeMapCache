package stereo.rchain.mapcache

import java.io.{File, PrintWriter}
import scala.collection.concurrent.TrieMap
import java.security.MessageDigest
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}


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
                                   caches: List[TrieMapTestTrait], periods: List[List[Long]],
                                   description: String): Unit = {
    val resultFilePath = List(resultFileDir, "/", resultFileName, fileNamePostfix).mkString
    val directory = new File(resultFileDir)
    if (!directory.exists())
      directory.mkdir()
    val bottomAxeName = "'Measurements'"
    val labels = bottomAxeName :: caches.map(cache => "'" + cache.name + "'")
    val chartSeries = periods.indices.map(i => (i.toLong :: periods(i)).mkString("[", ",", "]")).toList
    val chartData = (labels.mkString("[", ",", "]") :: chartSeries).mkString("[", ",", "]")

    val printWriter = new PrintWriter(new File(resultFilePath))
    printWriter.write(googleVisualizationTemplate.html(chartData, description))
    printWriter.close()

    print(s"Results was saved by path $resultFilePath\n")
  }

  def writeLineChartFile(resultFileDir: String, resultFileName: String,
                         caches: List[TrieMapTestTrait], periods: List[List[Long]],
                         description: String = "Caches compare"): Unit = {
    writeGoogleVisualizationFile("_lineChart.html", new LineChartTemplate,
      resultFileDir, resultFileName, caches, periods, description)
  }


  def writeBarChartFile(resultFileDir: String, resultFileName: String,
                        caches: List[TrieMapTestTrait], periods: List[List[Long]],
                        description: String = "Caches compare"): Unit = {
    writeGoogleVisualizationFile("_barChart.html", new BarChartTemplate,
      resultFileDir, resultFileName, caches, periods, description)
  }

  def sha256(value: Int): Array[Byte] = {
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

  def prepareCaches(limitTriaMapSize: Int = 100, multiThreadMode: Boolean): List[TrieMapTestTrait] = {
    val triaMap1 = new SimpleTriaMap
    val triaMap2 = new LimitTrieMap(limitTriaMapSize)
    val triaMap3 = new MultiThreadLimitTrieMap(limitTriaMapSize)
    if (multiThreadMode) List(triaMap1, triaMap3)
    else List(triaMap1, triaMap2, triaMap3)
  }

  def repeat(multiThreadMode: Boolean, experimentCount: Int, process: Unit => List[Long]): List[List[Long]] = {
    if (multiThreadMode) {
      List.fill(experimentCount)(Future{process()}).map(f => Await.result(f, 1000.seconds))
    }
    else List.fill(experimentCount)(process())
  }

  def addThreadModeToFilename(filename: String, multiThreadMode: Boolean): String = {
    if (multiThreadMode) "multiThread_" + filename
    else "singleThread_" + filename
  }

  def getDescription(limitTriaMapSize: Int, multiThreadMode: Boolean): String = {
    s"""limitTriaMapSize: $limitTriaMapSize; multiThreadMode: $multiThreadMode"""
  }

  def testReadManyOldItemsOnly(limitTriaMapSize: Int, multiThreadMode: Boolean, experimentCount: Int,
                               resultFileDir: String, fileName: String): Unit = {
    println("testReadManyOldItemsOnly")
    val scale = 1000
    val ItemsCount = limitTriaMapSize * scale
    val caches = prepareCaches(limitTriaMapSize, multiThreadMode)
    val queue = prepareGetEvents(ItemsCount)

    // setup cache data
    calculateCachesWorkTime(caches, prepareSetEvents(ItemsCount))
    val periods = repeat(multiThreadMode, experimentCount, Unit => {calculateCachesWorkTime(caches, queue)})
    val description = getDescription(limitTriaMapSize, multiThreadMode)
    writeLineChartFile(resultFileDir, addThreadModeToFilename(fileName, multiThreadMode), caches, periods, description)
    //writeBarChartFile(resultFileDir, addThreadModeToFilename(fileName, multiThreadMode), caches, periods, description)
  }

  def testReadOldItemsOnly(limitTriaMapSize: Int, multiThreadMode: Boolean, experimentCount: Int,
                           resultFileDir: String, fileName: String): Unit = {
    println("testReadOldItemsOnly")
    val scale = 1
    val ItemsCount = limitTriaMapSize * scale
    val caches = prepareCaches(limitTriaMapSize, multiThreadMode)
    val queue = prepareGetEvents(limitTriaMapSize)

    // setup cache data
    calculateCachesWorkTime(caches, prepareSetEvents(ItemsCount))
    val periods = repeat(multiThreadMode, experimentCount, Unit => {calculateCachesWorkTime(caches, queue)})
    val description = getDescription(limitTriaMapSize, multiThreadMode)
    writeLineChartFile(resultFileDir, addThreadModeToFilename(fileName, multiThreadMode), caches, periods, description)
    //writeBarChartFile(resultFileDir, addThreadModeToFilename(fileName, multiThreadMode), caches, periods, description)
  }

  def testAddNewItemsOnly(limitTriaMapSize: Int, multiThreadMode: Boolean, experimentCount: Int,
                          resultFileDir: String, fileName: String): Unit = {
    println("testAddNewItemsOnly")
    val caches = prepareCaches(limitTriaMapSize, multiThreadMode)
    val periods = repeat(multiThreadMode, experimentCount, Unit => {calculateCachesWorkTime(caches, prepareSetEvents(limitTriaMapSize))})
    val description = getDescription(limitTriaMapSize, multiThreadMode)
    writeLineChartFile(resultFileDir, addThreadModeToFilename(fileName, multiThreadMode), caches, periods, description)
    //writeBarChartFile(resultFileDir, addThreadModeToFilename(fileName, multiThreadMode), caches, periods, description)
  }

  def main(args: Array[String]): Unit = {
    val limitTriaMapSize = 1000
    val multiThreadMode = true
    val experimentCount = 50
    val resultDir = "./resultHTML"

    println("This program compare performance of TrieMap and LimitSizeTrieMap and represent results in HTML-files.")
    testReadManyOldItemsOnly(limitTriaMapSize, multiThreadMode, experimentCount, resultDir, "readManyOld")
    testReadOldItemsOnly(    limitTriaMapSize, multiThreadMode, experimentCount, resultDir, "readOld")
    testAddNewItemsOnly(     limitTriaMapSize, multiThreadMode, experimentCount, resultDir, "writeNew")
    println(s"""HTML-files with Google Visualization graphics are saved in this path: <$resultDir>.""")
  }
}

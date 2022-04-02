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

  sealed trait TriaMapEvent {
    val key: Array[Byte]
  }

  case class GetValue(override val key: Array[Byte]) extends TriaMapEvent

  case class SetValue(override val key: Array[Byte], value: Int) extends TriaMapEvent

  def processEventsQueue(cache: TrieMapTestTrait, queue: List[TriaMapEvent]): Unit = {
    queue.foreach {
      case GetValue(key) => cache.get(key)
      case SetValue(key, value) => cache.set(key, value)
    }
  }

  def calculateCacheWorkTime(cache: TrieMapTestTrait, queue: List[TriaMapEvent]): Long = {
    val beginTime = System.nanoTime
    processEventsQueue(cache, queue)
    System.nanoTime - beginTime
  }

  def calculateCachesWorkTime(caches: List[TrieMapTestTrait],
                              queue: List[TriaMapEvent]): List[Long] = {
    caches.map(cache => calculateCacheWorkTime(cache, queue))
  }

  def writeLineChartFile(caches: List[TrieMapTestTrait], periods: List[List[Long]]): Unit = {
    val resultFilePath = "./tmp/cachesCompare_lineChart.html"
    val dir_path = resultFilePath.substring(0, resultFilePath.lastIndexOf("/"))
    val directory = new File(dir_path)
    if (!directory.exists())
      directory.mkdir()
    val printWriter = new PrintWriter(new File(resultFilePath))
    val bottomAxeName = "'Measurements'"
    val labels = bottomAxeName :: caches.map(cache => "'" + cache.name + "'")
    val chartSeries = periods.indices.map(i => (i.toLong :: periods(i)).mkString("[", ",", "]")).toList
    val chartData = (labels.mkString("[", ",", "]") :: chartSeries).mkString("[", ",", "]")

    val html =
      s"""
         |  <html>
         |  <head>
         |    <script type="text/javascript" src="https://www.gstatic.com/charts/loader.js"></script>
         |    <script type="text/javascript">
         |      google.charts.load('current', {'packages':['corechart']});
         |      google.charts.setOnLoadCallback(drawChart);
         |      function drawChart() {
         |        var data = google.visualization.arrayToDataTable($chartData);
         |        var options = {
         |          title: 'Caches compare',
         |          curveType: 'function',
         |          legend: { position: 'bottom' }
         |        };
         |        var chart = new google.visualization.LineChart(document.getElementById('curve_chart'));
         |        chart.draw(data, options);
         |      }
         |      window.onload = drawChart;
         |      window.onresize = drawChart;
         |    </script>
         |  </head>
         |  <body>
         |    <div id="curve_chart" style="width: 100%; height: 100%"></div>
         |  </body>
         |</html>
         |""".stripMargin
    printWriter.write(html)
    printWriter.close()
    print(s"Results was saved by path $resultFilePath\n")
  }


  def writeBarChartFile(caches: List[TrieMapTestTrait], periods: List[List[Long]]): Unit = {
    val resultFilePath = "./tmp/cachesCompare_barChart.html"
    val dir_path = resultFilePath.substring(0, resultFilePath.lastIndexOf("/"))
    val directory = new File(dir_path)
    if (!directory.exists())
      directory.mkdir()
    val printWriter = new PrintWriter(new File(resultFilePath))

    val bottomAxeName = "'Measurements'"
    val labels = bottomAxeName :: caches.map(cache => "'" + cache.name + "'")
    val chartSeries = periods.indices.map(i => (i.toLong :: periods(i)).mkString("[", ",", "]")).toList
    val chartData = (labels.mkString("[", ",", "]") :: chartSeries).mkString("[", ",", "]")

    val html =
      s"""
         |<html>
         |  <head>
         |    <script type="text/javascript" src="https://www.gstatic.com/charts/loader.js"></script>
         |    <script type="text/javascript">
         |      google.charts.load('current', {'packages':['bar']});
         |      google.charts.setOnLoadCallback(drawChart);
         |      function drawChart() {
         |        var data = google.visualization.arrayToDataTable($chartData);
         |        var options = {
         |          chart: {
         |            title: 'Caches compares',
         |            subtitle: 'work time - nanoseconds',
         |          }
         |        };
         |        var chart = new google.charts.Bar(document.getElementById('columnchart_material'));
         |        chart.draw(data, google.charts.Bar.convertOptions(options));
         |      }
         |      window.onload = drawChart;
         |      window.onresize = drawChart;
         |    </script>
         |  </head>
         |  <body>
         |    <div id="columnchart_material" style="width: 100%; height: 100%;"></div>
         |  </body>
         |</html>
         |""".stripMargin
    printWriter.write(html)
    printWriter.close()
    print(s"Results was saved by path $resultFilePath\n")
  }

  def sha256(value: Int) = {
    MessageDigest.getInstance("sha-256").digest(BigInt(value).toByteArray)
  }

  def prepareGetEvents(uniqueCount: Int, copyCount: Int = 1): List[TriaMapEvent] = {
    val uniqueEvents = (0 until uniqueCount).toList.map(i => GetValue(sha256(i)))
    List.fill(copyCount)(uniqueEvents).flatten
  }

  def prepareSetEvents(uniqueCount: Int, copyCount: Int = 1): List[TriaMapEvent] = {
    val uniqueEvents = (0 until uniqueCount).toList.map(i => SetValue(sha256(i), i))
    List.fill(copyCount)(uniqueEvents).flatten
  }

  def prepareCaches(): List[TrieMapTestTrait] = {
    val limitTriaMapSize = 100
    val triaMap1 = new SimpleTriaMap
    val triaMap2 = new LimitTrieMap(limitTriaMapSize)
    val triaMap3 = new MultiThreadLimitTrieMap(limitTriaMapSize)
    List(triaMap1, triaMap2, triaMap3)
  }

  def testReadOldItemsOnly(): Unit = {
    println("testReadOldItemsOnly")
    val caches = prepareCaches()
    val limitTriaMapSize = 1000000

    val queue = prepareGetEvents(limitTriaMapSize)

    // setup cache data
    calculateCachesWorkTime(caches, prepareSetEvents(limitTriaMapSize / 1000))

    val periods =
      calculateCachesWorkTime(caches, queue) ::
        calculateCachesWorkTime(caches, queue) ::
        calculateCachesWorkTime(caches, queue) ::
        calculateCachesWorkTime(caches, queue) ::
        calculateCachesWorkTime(caches, queue) ::
        calculateCachesWorkTime(caches, queue) ::
        calculateCachesWorkTime(caches, queue) ::
        calculateCachesWorkTime(caches, queue) ::
        Nil

    writeLineChartFile(caches, periods)
    writeBarChartFile(caches, periods)
  }

  def testAddNewItemsOnly(): Unit = {
    println("testReadOldItemsOnly")
    val caches = prepareCaches()
    val limitTriaMapSize = 1000000
    val periods =
      calculateCachesWorkTime(caches, prepareSetEvents(limitTriaMapSize)) ::
        calculateCachesWorkTime(caches, prepareSetEvents(limitTriaMapSize)) ::
        calculateCachesWorkTime(caches, prepareSetEvents(limitTriaMapSize)) ::
        calculateCachesWorkTime(caches, prepareSetEvents(limitTriaMapSize)) ::
        calculateCachesWorkTime(caches, prepareSetEvents(limitTriaMapSize)) ::
        calculateCachesWorkTime(caches, prepareSetEvents(limitTriaMapSize)) ::
        calculateCachesWorkTime(caches, prepareSetEvents(limitTriaMapSize)) ::
        calculateCachesWorkTime(caches, prepareSetEvents(limitTriaMapSize)) ::
        Nil

    writeLineChartFile(caches, periods)
    writeBarChartFile(caches, periods)
  }

  def main(args: Array[String]): Unit = {
    testReadOldItemsOnly()
    //testAddNewItemsOnly()
  }
}
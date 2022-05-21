package stereo.rchain.mapcache

import cats.effect.Sync
import cats.syntax.all._

import scala.collection.concurrent.TrieMap
import java.io.{File, PrintWriter}
import java.security.MessageDigest
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import stereo.rchain.mapcache.cacheImplamentations.{ImperativeLimitSizeMapCache, LimitSizeMapCache, LimitSizeMapCacheState}


object CacheExperiments {

  case class ExperimentParameters(val maxItemCount: Int,
                                  val itemCountAfterSizeCorrection: Int,
                                  val multiThreadMode: Boolean,
                                  val experimentCount: Int,
                                  val notImportantExperimentsCount: Int,
                                  val resultFileDir: String) {}

  abstract class AbstractTestCache[F[_]: Sync] {
    val name: String

    def get(key: Array[Byte]): F[Option[Int]]

    def set(key: Array[Byte], value: Int): F[Unit]
  }

  class ImperativeTestCache[F[_]: Sync](val size: Int) extends AbstractTestCache[F] {
    override val name: String = "ImperativeLimitSizeMapCache"
    private val cache = new ImperativeLimitSizeMapCache[Array[Byte], Int](size)

    override def get(key: Array[Byte]): F[Option[Int]] = cache.get(key).pure

    override def set(key: Array[Byte], value: Int): F[Unit] = cache.set(key, value).pure
  }

  class RegularTrieMapTestCache[F[_]: Sync] extends AbstractTestCache[F] {
    override val name: String = "RegularTrieMapCache"
    private val cache = new TrieMap[Array[Byte], Int]

    override def get(key: Array[Byte]): F[Option[Int]] = cache.get(key).pure

    override def set(key: Array[Byte], value: Int): F[Unit] = (cache(key) = value).pure
  }

  class LimitSizeTestCache[F[_]: Sync](val maxItemCount: Int, val itemCountAfterSizeCorrection: Int) extends AbstractTestCache[F] {
    override val name: String = "LimitSizeMapCache"
    private val cacheRef = LimitSizeMapCache[F, Array[Byte], Int](maxItemCount, itemCountAfterSizeCorrection)

    override def get(key: Array[Byte]): F[Option[Int]] = for {cache <- cacheRef; value <- cache.get(key)} yield(value)

    override def set(key: Array[Byte], value: Int): F[Unit] = for {cache <- cacheRef; _ <- cache.set(key, value)} yield()
  }

  class UnlimitedLimitSizeTestCache[F[_]: Sync](val maxItemCount: Int) extends AbstractTestCache[F] {
    private val pseudoUnlimitedSize = maxItemCount * maxItemCount
    override val name: String = "UnlimitedLimitSizeMapCache"
    private val cacheRef = LimitSizeMapCache[F, Array[Byte], Int](pseudoUnlimitedSize, pseudoUnlimitedSize)

    override def get(key: Array[Byte]): F[Option[Int]] = for {cache <- cacheRef; value <- cache.get(key)} yield(value)

    override def set(key: Array[Byte], value: Int): F[Unit] = for {cache <- cacheRef; _ <- cache.set(key, value)} yield()
  }


  sealed trait TrieMapEvent {
    val key: Array[Byte]
  }

  case class GetValue(override val key: Array[Byte]) extends TrieMapEvent

  case class SetValue(override val key: Array[Byte], value: Int) extends TrieMapEvent

  def processEventsQueue[F[_]: Sync](cache: AbstractTestCache[F], queue: List[TrieMapEvent]): Unit = {
    queue.foreach {
      case GetValue(key) => cache.get(key)
      case SetValue(key, value) => cache.set(key, value)
    }
  }

  def calculateCacheWorkTime[F[_]: Sync](cache: AbstractTestCache[F], queue: List[TrieMapEvent]): Long = {
    val beginTime = System.nanoTime
    processEventsQueue(cache, queue)
    System.nanoTime - beginTime
  }

  def calculateCachesWorkTime[F[_]: Sync](caches: List[AbstractTestCache[F]],
                                          queue: List[TrieMapEvent]): List[Long] = {
    caches.map(cache => calculateCacheWorkTime(cache, queue))
  }

  def writeGoogleVisualizationFile[F[_]: Sync](fileNamePostfix: String, googleVisualizationTemplate: GoogleVisualizationTemplate,
                                               resultFileDir: String, resultFileName: String,
                                               caches: List[AbstractTestCache[F]], periods: List[List[Long]],
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

  def writeLineChartFile[F[_]: Sync](resultFileDir: String, resultFileName: String,
                                     caches: List[AbstractTestCache[F]], periods: List[List[Long]],
                                     description: String = "Caches compare"): Unit = {
    writeGoogleVisualizationFile("_lineChart.html", new LineChartTemplate,
      resultFileDir, resultFileName, caches, periods, description)
  }


  def writeBarChartFile[F[_]: Sync](resultFileDir: String, resultFileName: String,
                                    caches: List[AbstractTestCache[F]], periods: List[List[Long]],
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

  def prepareCaches[F[_]: Sync](maxItemCount: Int, itemCountAfterSizeCorrection: Int): F[List[AbstractTestCache[F]]] = {
    //val triaMap1 = new RegularTrieMapTestCache
    //val triaMap2 = new ImperativeTestCache(limitTriaMapSize)
    val triaMap3 = new LimitSizeTestCache[F](maxItemCount, itemCountAfterSizeCorrection)
    val triaMap4 = new UnlimitedLimitSizeTestCache[F](maxItemCount)

    List(/*triaMap1, triaMap2,*/ triaMap3, triaMap4).pure
  }

  def repeat(multiThreadMode: Boolean, experimentCount: Int, process: (Unit) => List[Long]): List[List[Long]] = {
    if (multiThreadMode) {
      List.fill(experimentCount)(Future{process.apply(())}).map(f => Await.result(f, 1000.seconds))
    }
    else List.fill(experimentCount)(process.apply(()))
  }

  def addThreadModeToFilename(filename: String, multiThreadMode: Boolean): String = {
    if (multiThreadMode) "multiThread_" + filename
    else "singleThread_" + filename
  }

  def getDescription(limitTriaMapSize: Int, multiThreadMode: Boolean): String = {
    s"""limitTriaMapSize: $limitTriaMapSize; multiThreadMode: $multiThreadMode"""
  }

  def testReadManyOldItemsOnly[F[_]: Sync](params: ExperimentParameters, fileName: String): F[Unit] = {
    println("testReadManyOldItemsOnly")
    val scale = 1000
    val ItemsCount = params.maxItemCount * scale
    for {
      caches <- prepareCaches[F](params.maxItemCount, params.itemCountAfterSizeCorrection)
      queue = prepareGetEvents(ItemsCount)

      // setup cache data
      _ = calculateCachesWorkTime[F](caches, prepareSetEvents(ItemsCount))
      periods = repeat(params.multiThreadMode, params.experimentCount, _ => {calculateCachesWorkTime[F](caches, queue)})
      userPeriods = periods.slice(params.notImportantExperimentsCount, params.experimentCount)
      description = getDescription(params.maxItemCount, params.multiThreadMode)
      _ = writeLineChartFile(params.resultFileDir, addThreadModeToFilename(fileName, params.multiThreadMode), caches, userPeriods, description)
      //_ = writeBarChartFile(params.resultFileDir, addThreadModeToFilename(fileName, params.multiThreadMode), caches, userPeriods, description)
    } yield()
  }

  def testReadOldItemsOnly[F[_]: Sync](params: ExperimentParameters, fileName: String): F[Unit] = {
    println("testReadOldItemsOnly")
    val scale = 1
    val ItemsCount = params.maxItemCount * scale
    for {
      caches <- prepareCaches[F](params.maxItemCount, params.itemCountAfterSizeCorrection)
      queue = prepareGetEvents(params.maxItemCount)

      // setup cache data
      _ = calculateCachesWorkTime[F](caches, prepareSetEvents(ItemsCount))
      periods = repeat(params.multiThreadMode, params.experimentCount, (_:Unit) => calculateCachesWorkTime[F](caches, queue))
      userPeriods = periods.slice(params.notImportantExperimentsCount, params.experimentCount)
      description = getDescription(params.maxItemCount, params.multiThreadMode)
      _ = writeLineChartFile(params.resultFileDir, addThreadModeToFilename(fileName, params.multiThreadMode), caches, userPeriods, description)
      //_ = writeBarChartFile(params.resultFileDir, addThreadModeToFilename(fileName, params.multiThreadMode), caches, userPeriods, description)
    } yield()
  }

  def testAddNewItemsOnly[F[_]: Sync](params: ExperimentParameters, fileName: String): F[Unit] = {
    println("testAddNewItemsOnly")
    for {
      caches <- prepareCaches[F](params.maxItemCount, params.itemCountAfterSizeCorrection)
      periods = repeat(params.multiThreadMode, params.experimentCount, (_:Unit) => {calculateCachesWorkTime[F](caches, prepareSetEvents(params.maxItemCount))})
      userPeriods = periods.slice(params.notImportantExperimentsCount, params.experimentCount)
      description = getDescription(params.maxItemCount, params.multiThreadMode)
      _ = writeLineChartFile(params.resultFileDir, addThreadModeToFilename(fileName, params.multiThreadMode), caches, userPeriods, description)
      //_ = writeBarChartFile(params.resultFileDir, addThreadModeToFilename(fileName, params.multiThreadMode), caches, userPeriods, description)
    } yield()
  }


  def main(args: Array[String]): Unit = {
    val maxItemCount = 1000
    val itemCountAfterSizeCorrection = 700
    val multiThreadMode = false
    val experimentCount = 500
    val notImportantExperimentsCount = 100
    val resultDir = "./resultHTML"

    val parameters = ExperimentParameters(maxItemCount, itemCountAfterSizeCorrection,
      multiThreadMode, experimentCount, notImportantExperimentsCount, resultDir)

    println("This program compare performance of LimitSizeCache's implementations and represent results in HTML-files.")
    testReadManyOldItemsOnly[Task](parameters, "readManyOld").runSyncUnsafe()
    testReadOldItemsOnly[Task](parameters, "readOld").runSyncUnsafe()
    testAddNewItemsOnly[Task](parameters, "writeNew").runSyncUnsafe()
    println(s"""HTML-files with Google Visualization graphics are saved in this path: <$resultDir>.""")
  }
}

package stereo.rchain.limitsizemapcache

import cats.Parallel
import cats.effect.Sync
import cats.implicits.catsStdInstancesForList
import cats.syntax.all._
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global

import java.io.{File, PrintWriter}

object PerformanceComparison {

  case class ExperimentParameters(
    val maxItemCount: Int,
    val itemCountAfterSizeCorrection: Int,
    val multiThreadMode: Boolean,
    val experimentCount: Int,
    val jvmWarmingExperimentsCount: Int = 0, // should be less then experimentCount
    val resultFileDir: String
  ) {}

  def processEventsQueue[F[_]: Sync: Parallel](
    cache: AbstractTestCache[F],
    eventList: List[TrieMapEvent],
    multiThreadMode: Boolean
  ): F[Unit] = {
    val serialTask = eventList.traverse_(TrieMapEventUtils.prepareEventTask(cache, _))
    val parallelTask = eventList.map(TrieMapEventUtils.prepareEventTask(cache, _)).parSequence_
    parallelTask.whenA(multiThreadMode).orElse(serialTask)
  }

  def calculateCacheWorkTime[F[_]: Sync: Parallel](
    cache: AbstractTestCache[F],
    initEventList: List[TrieMapEvent],
    workEventList: List[TrieMapEvent],
    multiThreadMode: Boolean
  ): F[Long] =
    for {
      _ <- processEventsQueue(cache, initEventList, multiThreadMode)
      beginTime <- Sync[F].delay(System.nanoTime)
      _ <- processEventsQueue(cache, workEventList, multiThreadMode)
    } yield System.nanoTime - beginTime

  def calculateCachesWorkTime[F[_]: Sync: Parallel](
    params: ExperimentParameters,
    initEventList: List[TrieMapEvent],
    workEventList: List[TrieMapEvent],
    multiThreadMode: Boolean = true
  ): F[List[Long]] =
    CachesAggregator()
      .prepareCaches[F](params.maxItemCount, params.itemCountAfterSizeCorrection)
      .traverse(calculateCacheWorkTime(_, initEventList, workEventList, multiThreadMode))

  def writeGoogleVisualizationFile[F[_]: Sync: Parallel](
    fileNamePostfix: String,
    googleVisualizationTemplate: GoogleVisualizationTemplate,
    resultFileDir: String,
    resultFileName: String,
    cachesNames: List[String],
    periods: List[List[Long]],
    description: String
  ): Unit = {
    val resultFilePath = List(resultFileDir, "/", resultFileName, fileNamePostfix).mkString
    val directory = new File(resultFileDir)
    if (!directory.exists())
      directory.mkdir()
    val labels = cachesNames.map("'" + _ + "'")
    val chartSeries = periods.indices.map(i => (i.toLong :: periods(i)).mkString("[", ",", "]")).toList
    val chartData = (labels.mkString("[", ",", "]") :: chartSeries).mkString("[", ",", "]")

    val printWriter = new PrintWriter(new File(resultFilePath))
    printWriter.write(googleVisualizationTemplate.html(chartData, description))
    printWriter.close()

    print(s"Results was saved by path $resultFilePath\n")
  }

  def writeLineChartFile[F[_]: Sync: Parallel](
    resultFileDir: String,
    resultFileName: String,
    cachesNames: List[String],
    periods: List[List[Long]],
    description: String = "Caches compare"
  ): Unit = {
    writeGoogleVisualizationFile(
      "_lineChart.html",
      new LineChartTemplate,
      resultFileDir,
      resultFileName,
      cachesNames,
      periods,
      description
    )
  }

  def writeBarChartFile[F[_]: Sync: Parallel](
    resultFileDir: String,
    resultFileName: String,
    cachesNames: List[String],
    periods: List[List[Long]],
    description: String = "Caches compare"
  ): Unit = {
    writeGoogleVisualizationFile(
      "_barChart.html",
      new BarChartTemplate,
      resultFileDir,
      resultFileName,
      cachesNames,
      periods,
      description
    )
  }

  def repeatCalculations[F[_]: Sync: Parallel](
    multiThreadMode: Boolean,
    experimentCount: Int,
    params: ExperimentParameters,
    initEventList: List[TrieMapEvent],
    workEventList: List[TrieMapEvent]
  ): F[List[List[Long]]] =
    (0 until experimentCount).toList
      .traverse(_ => calculateCachesWorkTime[F](params, initEventList, workEventList, multiThreadMode))

  def addThreadModeToFilename(filename: String, multiThreadMode: Boolean): String =
    if (multiThreadMode) "multiThread_" + filename
    else "singleThread_" + filename

  def getDescription(params: ExperimentParameters): String =
    s"""multiThreadMode: ${params.multiThreadMode}; """ +
      s"""experimentCount=${params.experimentCount}; """ +
      s"""hiddenResultCountForWarmUpJVM=${params.jvmWarmingExperimentsCount}""".stripMargin

  def performTest[F[_]: Sync: Parallel](
    params: ExperimentParameters,
    fileName: String,
    workEventList: List[TrieMapEvent],
    initEventList: List[TrieMapEvent] = List.empty[TrieMapEvent]
  ): F[Unit] = {
    for {
      results <-
        repeatCalculations(params.multiThreadMode, params.experimentCount, params, initEventList, workEventList)
      userPeriods = results.slice(params.jvmWarmingExperimentsCount, params.experimentCount)
      description = getDescription(params)
      fullFilename = addThreadModeToFilename(fileName, params.multiThreadMode)
      cachesNames = CachesAggregator().cachesNames
      _ = writeLineChartFile(params.resultFileDir, fullFilename, cachesNames, userPeriods, description)
      //_ = writeBarChartFile(params.resultFileDir, fullFilename, cachesNames, userPeriods, description)
    } yield ()
  }

  def testReadFromEmptyCacheOneTime[F[_]: Sync: Parallel](params: ExperimentParameters): F[Unit] = {
    println("ReadFromEmptyCacheOneTime")
    val workEventList = TrieMapEventUtils.prepareGetEvents(params.itemCountAfterSizeCorrection, 1)
    performTest(params, "ReadFromEmptyCacheOneTime", workEventList)
  }

  def testReadFromEmptyCacheSeveralTimes[F[_]: Sync: Parallel](params: ExperimentParameters): F[Unit] = {
    println("ReadFromEmptyCacheSeveralTimes")
    val workEventList = TrieMapEventUtils.prepareGetEvents(params.itemCountAfterSizeCorrection, 100)
    performTest(params, "ReadFromEmptyCacheSeveralTimes", workEventList)
  }

  def testReadNotExistItemsOneTime[F[_]: Sync: Parallel](params: ExperimentParameters): F[Unit] = {
    println("ReadNotExistNItemsOneTime")
    val initEventList = TrieMapEventUtils.prepareSetEvents(2 * params.maxItemCount, 1)
    val workEventList = TrieMapEventUtils.prepareGetEvents(params.itemCountAfterSizeCorrection, 1)
    performTest(params, "ReadNotExistNItemsOneTime", workEventList, initEventList)
  }

  def testReadNotExistItemsSeveralTimes[F[_]: Sync: Parallel](params: ExperimentParameters): F[Unit] = {
    println("ReadNotExistItemsSeveralTime")
    val initEventList = TrieMapEventUtils.prepareSetEvents(2 * params.maxItemCount, 1)
    val workEventList = TrieMapEventUtils.prepareGetEvents(params.itemCountAfterSizeCorrection, 100)
    performTest(params, "ReadNotExistItemsSeveralTime", workEventList, initEventList)
  }

  def testReadExistItemsOneTime[F[_]: Sync: Parallel](params: ExperimentParameters): F[Unit] = {
    println("ReadExistItemsOneTime")
    val initEventList = TrieMapEventUtils.prepareSetEvents(params.maxItemCount, 1)
    val workEventList = TrieMapEventUtils.prepareGetEvents(params.maxItemCount, 1)
    performTest(params, "ReadExistItemsOneTime", workEventList, initEventList)
  }

  def testReadExistItemsSeveralTimes[F[_]: Sync: Parallel](params: ExperimentParameters): F[Unit] = {
    println("ReadExistItemsSeveralTime")
    val initEventList = TrieMapEventUtils.prepareSetEvents(params.maxItemCount, 1)
    val workEventList = TrieMapEventUtils.prepareGetEvents(params.maxItemCount, 100)
    performTest(params, "ReadExistItemsSeveralTime", workEventList, initEventList)
  }

  def testFillOneTime[F[_]: Sync: Parallel](params: ExperimentParameters): F[Unit] = {
    println("FillOneTime")
    val workEventList = TrieMapEventUtils.prepareSetEvents(params.maxItemCount, 1)
    performTest(params, "FillOneTime", workEventList)
  }

  def testFillSeveralTimes[F[_]: Sync: Parallel](params: ExperimentParameters): F[Unit] = {
    println("FillSeveralTimes")
    val workEventList = TrieMapEventUtils.prepareSetEvents(params.maxItemCount, 100)
    performTest(params, "FillSeveralTimes", workEventList)
  }

  /**
    * [[maxItemCount]] - maximum item count for caches with limit size
    * [[itemCountAfterSizeCorrection]] - item count for caches with limit size after size correction
    * [[multiThreadMode]] - if True - perform multi thread experiment; if False - perform single thread experiment
    * [[experimentCount]] - count of experiment iterations
    * [[jvmWarmingExperimentsCount]] - count of experiment what needed only for JVM warm up (results will hide)
    * [[resultFileDir]] - path to result HTML file's directory
    */
  def main(args: Array[String]): Unit = {
    val parameters = ExperimentParameters(
      maxItemCount = 1000,
      itemCountAfterSizeCorrection = 700,
      multiThreadMode = false,
      experimentCount = 100,
      jvmWarmingExperimentsCount = 0,
      resultFileDir = "./resultHTML"
    )

    println("This program compare performance of LimitSizeCache's implementations and represent results in HTML-files.")
    testReadFromEmptyCacheOneTime[Task](parameters).runSyncUnsafe()
    testReadFromEmptyCacheSeveralTimes[Task](parameters).runSyncUnsafe()
    testReadNotExistItemsOneTime[Task](parameters).runSyncUnsafe()
    testReadNotExistItemsSeveralTimes[Task](parameters).runSyncUnsafe()
    testReadExistItemsOneTime[Task](parameters).runSyncUnsafe()
    testReadExistItemsSeveralTimes[Task](parameters).runSyncUnsafe()
    testFillOneTime[Task](parameters).runSyncUnsafe()
    testFillSeveralTimes[Task](parameters).runSyncUnsafe()
    println("HTML-files with Google Visualization graphics are saved in this path: " + parameters.resultFileDir)
  }
}

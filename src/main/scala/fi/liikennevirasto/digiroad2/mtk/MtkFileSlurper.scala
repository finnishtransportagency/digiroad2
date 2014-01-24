package fi.liikennevirasto.digiroad2.mtk

import scala.io.Source
import fi.liikennevirasto.digiroad2.Digiroad2Context._
import org.apache.commons.io._
import scala.collection.mutable
import java.io.File
import scala.concurrent.ExecutionContext
import akka.actor.Cancellable
import fi.liikennevirasto.digiroad2.asset.AssetProvider
import org.slf4j.LoggerFactory
import org.joda.time.format.{DateTimeFormat}
import org.joda.time.DateTime

object MtkFileSlurper  {
  val logger = LoggerFactory.getLogger(getClass)
  var oracleAssetProvider: AssetProvider = null

  def startWatching() {
    schedule
  }

  def stopWatching() {
    schedule.cancel()
  }

  private lazy val schedule: Cancellable = {
    oracleAssetProvider = assetProvider
    val pollingInterval = getProperty("digiroad2.mtkPollingInterval").toLong
    val pollingFolder = FileUtils.getFile(getProperty("digiroad2.mtkPollingFolder"))
    logger.info(s"Mtk message parser is watching directory $pollingFolder using $pollingInterval ms polling interval")
    import scala.concurrent.duration._
    import scala.language.postfixOps
    import ExecutionContext.Implicits.global
    val system = akka.actor.ActorSystem("system")
    val processingQueue = new mutable.SynchronizedQueue[File]()
    system.scheduler.schedule(0 milliseconds, pollingInterval milliseconds) {
      try {
        handleSchedulerTick((pollingFolder, processingQueue))
      } catch {
        case e: Exception => logger.error("Mtk file processign caused exception", e)
      }
    }
  }

  def handleSchedulerTick(params: (File, mutable.SynchronizedQueue[File])) = {
    val queue = addMissingItemsToQueue(params)
    while(queue.isEmpty == false) {
      (getFileFromQueue _)
        .andThen(parseMtkMessage _)
        .andThen(storeMtkData _)
        .andThen(moveFileToProcessed _).apply(queue)
    }
  }

  def addMissingItemsToQueue(params: (File, mutable.SynchronizedQueue[File])) = {
    val (directory, processingQueue) = params
    def addItemToQueue(file: File) {
      if(processingQueue.find(x => x.getAbsolutePath == file.getAbsolutePath).isEmpty) {
        logger.info(s"Adding $file to processing queue")
        processingQueue.enqueue(file)
      }
    }
    import scala.collection.JavaConversions._
    FileUtils.listFiles(directory, Array("xml"), false).foreach(addItemToQueue)
    processingQueue
  }

  def moveFileToProcessed(fileOption: Option[File]) {
    fileOption.foreach(file => {
      logger.info(s"Moving $file to processed")
      val processedFolder = FilenameUtils.getFullPath(file.getPath) + "processed" + File.separator
      val fmt = DateTimeFormat.forPattern("yyyy_MM_dd_mm_ss")
      val newFile =new File(file.getName.stripSuffix(".xml") + "_" + fmt.print(new DateTime()) + ".xml")
      FileUtils.copyFile(file, new File(processedFolder + newFile.getName))
      FileUtils.forceDelete(file)
    })
  }

  def storeMtkData(dataOption: Option[(File, Seq[MtkRoadLink])]) = {
    dataOption.foreach(x =>
      {
        logger.info(s"Storing roadlinks to Oracle (amount ${x._2.size})")
        oracleAssetProvider.updateRoadLinks(x._2)
      })
    dataOption.map(_._1)
  }

  def parseMtkMessage(fileOption: Option[File]) = {
    fileOption.map(file =>  {
      logger.info(s"Parsing file $file")
      (file, MtkMessageParser.parseMtkMessage(Source.fromFile(file)))
    })
  }

  def getFileFromQueue(queue: mutable.SynchronizedQueue[File]) = if(queue.isEmpty) None else Some(queue.dequeue())
}
package fi.liikennevirasto.digiroad2.mtk

import scala.io.Source
import fi.liikennevirasto.digiroad2.Digiroad2Context._
import org.apache.commons.io._
import scala.collection.mutable
import java.io.File
import scala.concurrent.ExecutionContext
import akka.actor.Cancellable
import fi.liikennevirasto.digiroad2.feature.FeatureProvider

object MtkFileSlurper  {
  var oracleFeatureProvider: FeatureProvider = null

  def startWatching() {
    schedule
  }

  def stopWatching() {
    schedule.cancel()
  }

  private lazy val schedule: Cancellable = {
    oracleFeatureProvider = featureProvider
    val pollingInterval = getProperty("digiroad2.mtkPollingInterval").toLong

    val baseFolder = FileUtils.getFile(this.getClass.getClassLoader.getResource(".").getPath)
    val pollingFolder = FileUtils.getFile(baseFolder.getParentFile.getParentFile.getParentFile.getAbsolutePath + getProperty("digiroad2.mtkPollingFolder"))
    // TODO: replace with proper logger implementation
    println(s"Mtk message parser is watching directory $pollingFolder using $pollingInterval ms polling interval")
    import scala.concurrent.duration._
    import scala.language.postfixOps
    import ExecutionContext.Implicits.global
    val system = akka.actor.ActorSystem("system")
    val processingQueue = new mutable.SynchronizedQueue[File]()
    system.scheduler.schedule(0 milliseconds, pollingInterval milliseconds) {
      handleSchedulerTick((pollingFolder, processingQueue))
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
      if(processingQueue.find(x => x.getAbsolutePath == file.getAbsolutePath).isEmpty) processingQueue.enqueue(file)
    }
    import scala.collection.JavaConversions._
    FileUtils.listFiles(directory, Array("xml"), false).foreach(addItemToQueue)
    processingQueue
  }

  def moveFileToProcessed(fileOption: Option[File]) {
    fileOption.foreach(file => {
      val processedFolder = FilenameUtils.getFullPath(file.getPath) + "processed" + File.separator
      FileUtils.moveFileToDirectory(file, new File(processedFolder), true)
    })
  }

  def storeMtkData(dataOption: Option[(File, Seq[MtkRoadLink])]) = {
    dataOption.foreach(x => oracleFeatureProvider.updateRoadLinks(x._2))
    dataOption.map(_._1)
  }

  def parseMtkMessage(fileOption: Option[File]) = {
    fileOption.map(file => (file, MtkMessageParser.parseMtkMessage(Source.fromFile(file))))
  }

  def getFileFromQueue(queue: mutable.SynchronizedQueue[File]) = if(queue.isEmpty) None else Some(queue.dequeue())
}
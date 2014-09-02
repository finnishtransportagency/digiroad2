package fi.liikennevirasto.digiroad2.mtk

import fi.liikennevirasto.digiroad2.Point
import org.scalatest._
import org.apache.commons.io._
import java.io.File
import scala.collection.mutable
import fi.liikennevirasto.digiroad2.asset.AssetProvider
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import org.mockito.Matchers._
import org.joda.time.DateTime

class MtkFileSlurperSpec extends FlatSpec with MustMatchers with BeforeAndAfter with BeforeAndAfterAll
                          with MockitoSugar {

  val tmpDir = FileUtils.getTempDirectoryPath + File.separator + "MktFileSlurperSpec"
  val mockedProvider = mock[AssetProvider]
  var originalFeatureProvider = MtkFileSlurper.oracleAssetProvider

  before {
    MtkFileSlurper.oracleAssetProvider = mockedProvider
  }

  override def afterAll() {
    FileUtils.forceDelete(new File(tmpDir))
    MtkFileSlurper.oracleAssetProvider = originalFeatureProvider
  }

  "MtkFileSlurper" must "add missing files to processing queue" in {
    val dirPath = tmpDir + File.separator + "adding_spec"
    FileUtils.forceMkdir(new File(dirPath))
    val queue = new mutable.SynchronizedQueue[File]()
    def addFileToFolder(filename: String) = {
      val file = new File(dirPath + File.separator + filename)
      file.createNewFile()
      MtkFileSlurper.addMissingItemsToQueue(new File(dirPath), queue)
      file
    }
    queue.size must equal(0)
    val file = addFileToFolder("file1.xml")
    queue.toList must equal(List(file))
    val file2 = addFileToFolder("file2.xml")
    queue.toList must equal(List(file, file2))
  }

  it must "return None if queue is empty" in {
    MtkFileSlurper.getFileFromQueue(new mutable.SynchronizedQueue[File]()) must equal(None)
  }

  it must "return file if queue is not empty" in {
    val fileA = new File("a.xml"); val fileB = new File("b.xml")
    val queue = new mutable.SynchronizedQueue[File]()
    queue ++= List(fileA, fileB)
    MtkFileSlurper.getFileFromQueue(queue) must equal(Some(fileA))
    queue.size must equal(1)
    MtkFileSlurper.getFileFromQueue(queue) must equal(Some(fileB))
    queue.size must equal(0)
  }

  it must "handle option None in parseMtkMessage" in {
    MtkFileSlurper.parseMtkMessage(None) must equal(None)
  }

  it must "parse file using parseMtkMessage in MtkMessageParser" in {
    val mtkFile = new File(this.getClass.getResource("/mtk_example.xml").toURI)
    val (file, seq) = MtkFileSlurper.parseMtkMessage(Some(mtkFile)).get
    seq.size must equal(6)
    file must equal(mtkFile)
  }

  it must "handle option None in updateRoadlink" in {
    MtkFileSlurper.storeMtkData(None) must equal(None)
    verify(mockedProvider, never()).updateRoadLinks(anyObject())
  }

  it must "call feature provider's updateRoadlink with right data" in {
    val fileWithParsedRoadlinks = Some(new File("f"), List(MtkRoadLink(1, new DateTime(), None, 12, List(Point(4.5, 5.4, 6.4)).toSeq)).toSeq)
    MtkFileSlurper.storeMtkData(fileWithParsedRoadlinks)
    verify(mockedProvider).updateRoadLinks(fileWithParsedRoadlinks.get._2)
  }

  it must "handle option None in move file" in {
    MtkFileSlurper.moveFileToProcessed(None)
  }

  it must "move file to processed folder" in {
    val dirPath = tmpDir + File.separator + "moving_spec"
    FileUtils.forceMkdir(new File(dirPath))
    val file = new File(dirPath + File.separator + "file.xml")
    file.createNewFile()
    val targetDir = new File(dirPath + File.separator + "processed" + File.separator)
    targetDir.exists() must equal(false)
    MtkFileSlurper.moveFileToProcessed(Some(file))
    file.exists() must equal(false)
    import scala.collection.JavaConversions._
    val target = FileUtils.listFiles(targetDir, Array("xml"), false).toList
    target.size must equal(1)
    target.head.getName.startsWith("file") must equal(true)
  }

  it must "do e2e scenario when new data is available" in {
    val dirPath = tmpDir + File.separator + "e2e_spec" + File.separator
    val mtkFile = new File(this.getClass.getResource("/initial.xml").toURI)
    val pollingFolder = new File(dirPath)
    FileUtils.copyFileToDirectory(mtkFile, pollingFolder, true)
    val queue = new mutable.SynchronizedQueue[File]()
    MtkFileSlurper.handleSchedulerTick((pollingFolder, queue))
    val point1 = Point(375295.264, 6678420.423, 36.41)
    val point2 = Point(375290.952,6678385.118, 35.027)
    verify(mockedProvider).updateRoadLinks(List(MtkRoadLink(1457964389L, new DateTime(2010,12,30,0,0,0,0), None, 49, List(point1, point2))))
    new File(dirPath + "initial.xml").exists() must equal(false)
    import scala.collection.JavaConversions._
    val files = FileUtils.listFiles(new File(dirPath + "processed" + File.separator), Array("xml"), false).toList
    files.size must equal(1)
  }
}

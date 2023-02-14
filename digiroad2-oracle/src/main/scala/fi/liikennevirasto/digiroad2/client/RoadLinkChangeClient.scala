package fi.liikennevirasto.digiroad2.client

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.service.AwsService
import fi.liikennevirasto.digiroad2.util.Digiroad2Properties
import org.joda.time.DateTime
import org.json4s.FieldSerializer.{renameFrom, renameTo}
import org.json4s._
import org.json4s.jackson.parseJson
import org.postgis.PGgeometry
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.s3.model._

import scala.Seq
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.io.Source.fromInputStream

class RoadLinkChangeClient {
  val awsService = new AwsService
  val s3Service: awsService.S3.type = awsService.S3
  val s3Client = s3Service.s3
  val s3Bucket: String = Digiroad2Properties.roadLinkChangeS3BucketName
  val logger = LoggerFactory.getLogger(getClass)

  sealed trait ChangeType2 {
    def value: String
  }
  object ChangeType2 {
    val values = Set(Add, Remove, Replace, Split, Unknown)

    def apply(stringValue: String): ChangeType2 = {
      values.find(_.value == stringValue).getOrElse(Unknown)
    }

    case object Add extends ChangeType2 {
      def value = "add"
    }

    case object Remove extends ChangeType2 {
      def value = "remove"
    }

    case object Replace extends ChangeType2 {
      def value = "replace"
    }

    case object Split extends ChangeType2 {
      def value = "split"
    }

    case object Unknown extends ChangeType2 {
      def value = "unknown"
    }
  }

  private def lineStringToPoints(lineString: String): List[Point] = {
    val geometry = PGgeometry.geomFromString(lineString)
    val pointsList = ListBuffer[List[Double]]()
    for (i <- 0 until geometry.numPoints()) {
      val point = geometry.getPoint(i)
      pointsList += List(point.x, point.y, point.z, point.m)
    }
    pointsList.map(point => Point(point(0), point(1), point(2))).toList
  }

  val changeItemSerializer: FieldSerializer[RoadLinkChange] = FieldSerializer[RoadLinkChange](
    renameTo("newLinks", "new") orElse renameTo("oldLink", "old"),
    renameFrom("new", "newLinks") orElse renameFrom("old", "oldLink"))

  object ChangeTypeSerializer extends CustomSerializer[ChangeType2](_ => (
    {
      case JString(stringValue) =>
        ChangeType2(stringValue)
    },
    {
      case changeType: ChangeType2 =>
        JObject(JField("changeType", JString(changeType.value)))
    }
  ))

  object GeometrySerializer extends CustomSerializer[List[Point]](_ => (
    {
      case JString(lineString) =>
        lineStringToPoints(lineString)
    },
    {
      case points: List[Point] =>
        JObject(JField("geometry", JString("")))
    }
  ))



  implicit val formats = DefaultFormats + changeItemSerializer + ChangeTypeSerializer + GeometrySerializer


  case class RoadLinkInfo(linkId: String, linkLength: Double, geometry: List[Point], roadClass: Int, adminClass: Option[Int], municipality: Int, trafficDirection: Int)
  case class ReplaceInfo(oldLinkId: String, newLinkId: String, oldFromMValue: Double, oldToMValue: Double, newFromMValue: Double, newToMValue: Double)
  case class RoadLinkChange(changeType: ChangeType2, oldLink: Option[RoadLinkInfo], newLinks: Seq[RoadLinkInfo], replaceInfo: Seq[ReplaceInfo])

  def fetchLatestSuccessfulUpdateDate(): DateTime = {
    // date of transfer to frozen road link network as a placeholder
    DateTime.parse("2022-05-10")
  }

  def listFilesAccordingToDates(since: DateTime, until: DateTime) = {
    def isValidKey(key: String): Boolean = {
      try {
        val keySince = DateTime.parse(key.substring(0, 9))
        val keyUntil = DateTime.parse(key.substring(10, 19))
        if (keySince.isBefore(since) || keyUntil.isAfter(until)) false else true
      } catch {
        case illegalArgument: IllegalArgumentException => {
          logger.error("Key provides no valid dates.")
          false
        }
        case e: Exception => {
          logger.error(e.getMessage)
          false
        }
      }
    }

    val request = ListObjectsV2Request.builder().bucket(s3Bucket).build()
    val objects = s3Client.listObjectsV2(request).contents().asScala.toList
    val keys = objects.map(_.key()).filter(key => isValidKey(key))
    keys
  }

  def fetchChangeSetFromS3(filename: String) = {
    val s3Object = s3Service.getObjectFromS3(s3Bucket, filename)
    fromInputStream(s3Object).mkString
  }


  def convertToRoadLinkChange(changeJson: String) : Seq[RoadLinkChange] = {
    val json = parseJson(changeJson)
    try {
      val roadLinkChanges = json.extract[Seq[RoadLinkChange]]
      roadLinkChanges
    } catch {
      case e => {
        logger.error(e.getMessage)
        Seq.empty[RoadLinkChange]
      }
    }

  }

  def getRoadLinkChanges(since: DateTime = fetchLatestSuccessfulUpdateDate(), until: DateTime = DateTime.now()): Seq[RoadLinkChange] = {
    val keys = listFilesAccordingToDates(since, until)
    val changes = keys.map(key => fetchChangeSetFromS3(key))
    val roadLinkChanges = changes.map(change => convertToRoadLinkChange(change)).flatten
    roadLinkChanges
  }

}

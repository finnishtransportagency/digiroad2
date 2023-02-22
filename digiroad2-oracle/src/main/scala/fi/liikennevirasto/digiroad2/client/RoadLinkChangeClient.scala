package fi.liikennevirasto.digiroad2.client

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, TrafficDirection, Unknown}
import fi.liikennevirasto.digiroad2.service.AwsService
import fi.liikennevirasto.digiroad2.util.Digiroad2Properties
import org.joda.time.DateTime
import org.json4s.FieldSerializer.{renameFrom, renameTo}
import org.json4s.JsonAST.JString
import org.json4s.jackson.parseJson
import org.json4s.{CustomSerializer, _}
import org.postgis.PGgeometry
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.io.Source.fromInputStream

trait RoadLinkChangeType {
  def value: String
}
object RoadLinkChangeType {
  val values = Set(Add, Remove, Replace, Split, Unknown)

  def apply(stringValue: String): RoadLinkChangeType = {
    values.find(_.value == stringValue).getOrElse(Unknown)
  }

  case object Add extends RoadLinkChangeType { def value = "add" }
  case object Remove extends RoadLinkChangeType { def value = "remove" }
  case object Replace extends RoadLinkChangeType { def value = "replace"}
  case object Split extends RoadLinkChangeType { def value = "split" }
  case object Unknown extends RoadLinkChangeType { def value = "unknown" }
}

case class RoadLinkInfo(linkId: String, linkLength: Double, geometry: List[Point], roadClass: Int, adminClass: AdministrativeClass, municipality: Int, trafficDirection: TrafficDirection)
case class ReplaceInfo(oldLinkId: String, newLinkId: String, oldFromMValue: Double, oldToMValue: Double, newFromMValue: Double, newToMValue: Double, digitizationChange: Boolean)
case class RoadLinkChange(changeType: RoadLinkChangeType, oldLink: Option[RoadLinkInfo], newLinks: Seq[RoadLinkInfo], replaceInfo: Seq[ReplaceInfo])

class RoadLinkChangeClient {
  lazy val awsService = new AwsService
  lazy val s3Service: awsService.S3.type = awsService.S3
  lazy val s3Bucket: String = Digiroad2Properties.roadLinkChangeS3BucketName
  val logger = LoggerFactory.getLogger(getClass)

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

  object RoadLinkChangeTypeSerializer extends CustomSerializer[RoadLinkChangeType](_ => (
    {
      case JString(stringValue) =>
        RoadLinkChangeType(stringValue)
    },
    {
      case changeType: RoadLinkChangeType =>
        JObject(JField("changeType", JString(changeType.value)))
    }
  ))

  object AdminClassSerializer extends CustomSerializer[AdministrativeClass](_ => (
    {
      case JInt(bigIntValue) =>
        AdministrativeClass(bigIntValue.toInt)
      case JNull => Unknown
    },
    {
      case adminClass: AdministrativeClass =>
        JObject(JField("adminClass", JInt(adminClass.value)))
    }
  ))

  object TrafficDirectionSerializer extends CustomSerializer[TrafficDirection](_ => (
  {
    case JInt(directionValue) =>
      directionValue.toInt match {
        case 0 => TrafficDirection.BothDirections
        case 1 => TrafficDirection.TowardsDigitizing
        case 2 => TrafficDirection.AgainstDigitizing
        case _ => TrafficDirection.UnknownDirection
      }
  },
  {
    case trafficDirection: TrafficDirection =>
      trafficDirection match {
        case TrafficDirection.BothDirections => JInt(0)
        case TrafficDirection.TowardsDigitizing => JInt(1)
        case TrafficDirection.AgainstDigitizing => JInt(2)
        case _ => JNull
      }
  }
  ))

  object GeometrySerializer extends CustomSerializer[List[Point]](_ => (
    {
      case JString(lineString) =>
        lineStringToPoints(lineString)
    },
    {
      case points: List[Point] =>
        JObject(JField("geometry", JString(""))) // not implemented until reverse operation is needed
    }
  ))

  implicit val formats = DefaultFormats + changeItemSerializer + RoadLinkChangeTypeSerializer + GeometrySerializer +
    AdminClassSerializer + TrafficDirectionSerializer

  def fetchLatestSuccessfulUpdateDate(): DateTime = {
    // placeholder value as long as fetching this date from db is possible
    DateTime.parse("2022-05-10")
  }

  def listFilesAccordingToDates(since: DateTime, until: DateTime) = {
    def isValidKey(key: String): Boolean = {
      try {
        val keyParts = key.replace(".json", "").split("_")
        val keySince = DateTime.parse(keyParts.head)
        val keyUntil = DateTime.parse(keyParts.last)
        !(keySince.isBefore(since) || keyUntil.isAfter(until)) // get no changes before or after the requested period
      } catch {
        case illegalArgument: IllegalArgumentException =>
          logger.error("Key provides no valid dates.")
          false
        case e: Exception =>
          logger.error(e.getMessage)
          false
      }
    }

    val objects = s3Service.listObjects(s3Bucket).asScala.toList
    objects.map(_.key()).filter(key => isValidKey(key))
  }

  def fetchChangeSetFromS3(filename: String) = {
    val s3Object = s3Service.getObjectFromS3(s3Bucket, filename)
    fromInputStream(s3Object).mkString
  }


  def convertToRoadLinkChange(changeJson: String) : Seq[RoadLinkChange] = {
    val json = parseJson(changeJson)
    try {
      json.extract[Seq[RoadLinkChange]]
    } catch {
      case e =>
        logger.error(e.getMessage)
        Seq.empty[RoadLinkChange]
    }
  }

  def getRoadLinkChanges(since: DateTime = fetchLatestSuccessfulUpdateDate(), until: DateTime = DateTime.now()): Seq[RoadLinkChange] = {
    val keys = listFilesAccordingToDates(since, until)
    val changes = keys.map(key => fetchChangeSetFromS3(key))
    changes.map(change => convertToRoadLinkChange(change)).flatten
  }
}
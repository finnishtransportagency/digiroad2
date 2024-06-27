package fi.liikennevirasto.digiroad2.client

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, ConstructionType, TrafficDirection, Unknown}
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.linearasset.SurfaceType
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.AwsService
import fi.liikennevirasto.digiroad2.util.Digiroad2Properties
import org.joda.time.DateTime
import org.json4s.FieldSerializer.{renameFrom, renameTo}
import org.json4s.JsonAST.JString
import org.json4s.jackson.parseJson
import org.json4s.{CustomSerializer, _}
import org.postgis.PGgeometry
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable.ListBuffer

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

case class RoadLinkInfo(linkId: String, linkLength: Double, geometry: List[Point], roadClass: Int,
                        adminClass: AdministrativeClass, municipality: Option[Int], trafficDirection: TrafficDirection,
                        surfaceType: SurfaceType = SurfaceType.Unknown, lifeCycleStatus:ConstructionType = ConstructionType.UnknownConstructionType)
case class ReplaceInfo(oldLinkId: Option[String], newLinkId: Option[String], oldFromMValue: Option[Double], oldToMValue: Option[Double], newFromMValue: Option[Double], newToMValue: Option[Double], digitizationChange: Boolean)
case class ReplaceInfoWithGeometry(oldLinkId: Option[String], oldGeometry: List[Point], newLinkId: Option[String], newGeometry: List[Point], oldFromMValue: Option[Double], oldToMValue: Option[Double], newFromMValue: Option[Double], newToMValue: Option[Double], digitizationChange: Boolean)
case class RoadLinkChange(changeType: RoadLinkChangeType, oldLink: Option[RoadLinkInfo], newLinks: Seq[RoadLinkInfo], replaceInfo: Seq[ReplaceInfo])
case class ChangeSetId(key: String, statusDate: DateTime, targetDate: DateTime)
case class RoadLinkChangeSet(key: String, statusDate: DateTime, targetDate: DateTime, changes: Seq[RoadLinkChange])

class RoadLinkChangeClient {
  lazy val awsService = new AwsService
  lazy val s3Service: awsService.S3.type = awsService.S3
  lazy val s3Bucket: String = Digiroad2Properties.roadLinkChangeS3BucketName
  val logger: Logger = LoggerFactory.getLogger(getClass)

  implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)


  def withLinkGeometry(replaceInfos: Seq[ReplaceInfo], changes: Seq[RoadLinkChange]): Seq[ReplaceInfoWithGeometry] = {
    val oldLinks = changes.flatMap(_.oldLink)
    val newLinks = changes.flatMap(_.newLinks)
    replaceInfos.map(ri => {
      val oldLinkGeom = if(ri.oldLinkId.nonEmpty)  {
        oldLinks.find(_.linkId == ri.oldLinkId.get).get.geometry
      } else Nil
      val newLinkgeom = if(ri.newLinkId.nonEmpty)  {
        newLinks.find(_.linkId == ri.newLinkId.get).get.geometry
      } else Nil
      ReplaceInfoWithGeometry(ri.oldLinkId, oldLinkGeom, ri.newLinkId, newLinkgeom, ri.oldFromMValue, ri.oldToMValue, ri.newFromMValue, ri.newToMValue, ri.digitizationChange)
    })
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

  object RoadLinkChangeTypeSerializer extends CustomSerializer[RoadLinkChangeType](_ => (
    {
      case JString(stringValue) =>
        RoadLinkChangeType(stringValue)
      case JNull => RoadLinkChangeType.Unknown
    },
    {
      case changeType: RoadLinkChangeType =>
        JObject(JField("changeType", JString(changeType.value)))
      case _ => JNull
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
      case _ => JNull
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
    case JNull => TrafficDirection.UnknownDirection
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

  object SurfaceTypeSerializer extends CustomSerializer[SurfaceType](_ => (
  {
    case JInt(directionValue) =>
      directionValue.toInt match {
        case 1 => SurfaceType.None
        case 2 => SurfaceType.Paved
        case _ => SurfaceType.Unknown
      }
    case JNull => SurfaceType.Unknown
  },
  {
    case surfaceType: SurfaceType =>
      surfaceType match {
        case SurfaceType.Unknown => JInt(0)
        case SurfaceType.None => JInt(1)
        case SurfaceType.Paved => JInt(2)
        case _ => JNull
      }
  }
  ))
  object ConstructionTypeSerializer extends CustomSerializer[ConstructionType](_ => ( {
    case JInt(value) =>
      value.toInt match {
        case 1 => ConstructionType.Planned
        case 2 => ConstructionType.UnderConstruction
        case 3 => ConstructionType.InUse
        case 4 => ConstructionType.TemporarilyOutOfUse
        case 5 => ConstructionType.ExpiringSoon
        case _ => ConstructionType.UnknownConstructionType
      }
    case JNull => ConstructionType.UnknownConstructionType
  }, {
    case c: ConstructionType =>
      c match {
        case ConstructionType.Planned =>  JInt(1)
        case ConstructionType.UnderConstruction =>  JInt(2)
        case ConstructionType.InUse =>  JInt(3)
        case ConstructionType.TemporarilyOutOfUse =>  JInt(4)
        case ConstructionType.ExpiringSoon =>  JInt(5)
        case ConstructionType.UnknownConstructionType =>  JInt(6)
        case _ => JNull
      }
  }
  ))

  object GeometrySerializer extends CustomSerializer[List[Point]](_ => (
    {
      case JString(lineString) =>
        lineStringToPoints(lineString)
      case JNull => null
    },
    {
      case points: List[Point] =>
        JObject(JField("geometry", JString(""))) // not implemented until reverse operation is needed
      case _ => JNull
    }
  ))

  implicit val formats = DefaultFormats + changeItemSerializer + RoadLinkChangeTypeSerializer + GeometrySerializer +
    AdminClassSerializer + TrafficDirectionSerializer + SurfaceTypeSerializer + ConstructionTypeSerializer

  def fetchLatestSuccessfulUpdateDate(): DateTime = {
    // placeholder value as long as fetching this date from db is possible
    DateTime.parse("2022-05-10")
  }

  def listFilesAccordingToDates(since: DateTime): List[ChangeSetId] = {
    def isValidKey(key: String): Option[ChangeSetId] = {
      try {
        val keyParts = key.replace(".json", "").split("_")
        val keyStatusDate = DateTime.parse(keyParts.head)
        val keyTargetDate = DateTime.parse(keyParts.last)
        if (!(keyStatusDate.isBefore(since) || keyTargetDate.isAfterNow)) {
          Some(ChangeSetId(key, keyStatusDate, keyTargetDate))
        } else None
      } catch {
        case _: IllegalArgumentException =>
          logger.error(s"Key ($key) provides no valid dates.")
          None
        case e: Throwable =>
          logger.error(e.getMessage)
          None
      }
    }

    val objects = s3Service.listObjects(s3Bucket)
    objects.flatMap(s3Object => isValidKey(s3Object.key())).sortBy(_.statusDate)
  }

  def fetchChangeSetFromS3(filename: String): String = {
    s3Service.getObjectFromS3(s3Bucket, filename)
  }

  def convertToRoadLinkChange(changeJson: String) : Seq[RoadLinkChange] = {

    def filterChanges(changes: Seq[RoadLinkChange]) = {
      def changeMissingMunicipality(a: RoadLinkChange): Boolean = {
        val newLinksEmptyMunicipality = a.newLinks.exists(_.municipality.isEmpty)
        val oldLinkEmptyMunicipality = a.oldLink.exists(_.municipality.isEmpty)
        newLinksEmptyMunicipality || oldLinkEmptyMunicipality
      }

      val (changesWithNullMunicipality, changesWithMunicipality) = changes.partition(changeMissingMunicipality)
      val linkIdsInNullMunicipalityChanges = changesWithNullMunicipality.flatMap(change => {
        change.newLinks.map(_.linkId) ++ change.oldLink.map(_.linkId)
      }).distinct

      changesWithMunicipality.filterNot(change => {
        val linkIdsInChange = change.newLinks.map(_.linkId) ++ change.oldLink.map(_.linkId)
        linkIdsInChange.exists(linkId => linkIdsInNullMunicipalityChanges.contains(linkId))
      })

    }

    val json = parseJson(changeJson)
    try {
      val extractedChanges = json.extract[Seq[RoadLinkChange]]
      filterChanges(extractedChanges)
    } catch {
      case e: Throwable =>
        logger.error(e.getMessage)
        throw e
    }
  }

  def getRoadLinkChanges(since: DateTime = fetchLatestSuccessfulUpdateDate()): Seq[RoadLinkChangeSet] = {
    val keys = listFilesAccordingToDates(since)
    keys.map(key => {
      val changes = fetchChangeSetFromS3(key.key)
      RoadLinkChangeSet(key.key, key.statusDate, key.targetDate, convertToRoadLinkChange(changes))
    })
  }
}
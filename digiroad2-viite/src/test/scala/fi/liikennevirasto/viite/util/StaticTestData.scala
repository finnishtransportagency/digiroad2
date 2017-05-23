package fi.liikennevirasto.viite.util

import java.io.{File, FileReader}

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, ValidityPeriodDayOfWeek}
import fi.liikennevirasto.digiroad2.util.VVHSerializer
import fi.liikennevirasto.digiroad2.{ChangeInfo, FeatureClass, VVHHistoryRoadLink, VVHRoadNodes}
import org.json4s.JsonAST.{JInt, JString}
import org.json4s.jackson.Serialization.read
import org.json4s.{CustomSerializer, DefaultFormats, Formats}

object StaticTestData {
  val deserializer = new RoadLinkDeserializer

  val roadLinkFile = new File(getClass.getResource("/road1130links.json").toURI)
  val historyRoadLinkFile = new File(getClass.getResource("/road1130historyLinks.json").toURI)
  // Contains Road 1130 part 4 and 5 links plus some extra that fit the bounding box approx (351714,6674367)-(361946,6681967)
  val road1130Links: Seq[RoadLink] = deserializer.readCachedGeometry(roadLinkFile)
  val road1130HistoryLinks: Seq[VVHHistoryRoadLink] = deserializer.readCachedHistoryLinks(historyRoadLinkFile)
}

class RoadLinkDeserializer extends VVHSerializer {
  case object SideCodeSerializer extends CustomSerializer[SideCode](format => ( {
    null
  }, {
    case s: SideCode => JInt(s.value)
  }))

  case object TrafficDirectionSerializer extends CustomSerializer[TrafficDirection](format => ( {
    case JString(direction) => TrafficDirection(direction)
  }, {
    case t: TrafficDirection => JString(t.toString)
  }))

  case object DayofWeekSerializer extends CustomSerializer[ValidityPeriodDayOfWeek](format => ( {
    case JString(dayOfWeek) => ValidityPeriodDayOfWeek(dayOfWeek)
  }, {
    case d: ValidityPeriodDayOfWeek => JString(d.toString)
  }))

  case object LinkTypeSerializer extends CustomSerializer[LinkType](format => ( {
    case JInt(linkType) => LinkType(linkType.toInt)
  }, {
    case lt: LinkType => JInt(BigInt(lt.value))
  }))

  case object AdministrativeClassSerializer extends CustomSerializer[AdministrativeClass](format => ( {
    case JInt(typeInt) => AdministrativeClass(typeInt.toInt)
  }, {
    case ac: AdministrativeClass =>
      JInt(BigInt(ac.value))
  }))

  case object LinkGeomSourceSerializer extends CustomSerializer[LinkGeomSource](format => ( {
    case JInt(typeInt) => LinkGeomSource(typeInt.toInt)
  }, {
    case geomSource: LinkGeomSource =>
      JInt(BigInt(geomSource.value))
  }))

  case object ConstructionTypeSerializer extends CustomSerializer[ConstructionType](format => ( {
    case JInt(typeInt) => ConstructionType(typeInt.toInt)
  }, {
    case constructionType: ConstructionType =>
      JInt(BigInt(constructionType.value))
  }))

  case object FeatureClassSerializer extends CustomSerializer[FeatureClass](format => ( {
    case _ => FeatureClass.AllOthers
  }, {
    case fc: FeatureClass =>
      JString("")
  }))

  protected implicit val jsonFormats: Formats = DefaultFormats + SideCodeSerializer + TrafficDirectionSerializer +
    LinkTypeSerializer + DayofWeekSerializer + AdministrativeClassSerializer + LinkGeomSourceSerializer + ConstructionTypeSerializer +
    FeatureClassSerializer

  def readCachedHistoryLinks(file: File): Seq[VVHHistoryRoadLink] = {
    val json = new FileReader(file)
    read[Seq[VVHHistoryRoadLink]](json)
  }

  override def readCachedGeometry(file: File): Seq[RoadLink] = {
    val json = new FileReader(file)
    read[Seq[RoadLink]](json)
  }

  override def readCachedChanges(file: File): Seq[ChangeInfo] = {
    val json = new FileReader(file)
    read[Seq[ChangeInfo]](json)
  }
  override def writeCache(file: File, objects: Seq[Object]): Boolean = {
    false
  }

  override def readCachedNodes(file: File): Seq[VVHRoadNodes] = {
    val json = new FileReader(file)
    read[Seq[VVHRoadNodes]](json)
  }
}

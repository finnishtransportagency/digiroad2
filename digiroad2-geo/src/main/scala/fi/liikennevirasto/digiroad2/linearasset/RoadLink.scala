package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.ConstructionType.{Planned, UnderConstruction}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.lane.PersistedLane
import org.joda.time.DateTime

import scala.util.Try

trait RoadLinkLike extends PolyLine{
  def linkId: String
  def municipalityCode: Int
  def length: Double
  def administrativeClass: AdministrativeClass
  def trafficDirection: TrafficDirection
  def roadNumber: Option[String]
  def linkSource: LinkGeomSource
  def attributes: Map[String, Any]
  def constructionType: ConstructionType
  def timeStamp: Long
}

case class RoadLinkProperties(linkId: String,
                              functionalClass: Int,
                              linkType: LinkType,
                              trafficDirection: TrafficDirection,
                              administrativeClass: AdministrativeClass ,
                              modifiedAt: Option[String],
                              modifiedBy: Option[String])

case class TinyRoadLink(linkId: String)

case class RoadLink(linkId: String, geometry: Seq[Point],
                    length: Double, administrativeClass: AdministrativeClass,
                    functionalClass: Int, trafficDirection: TrafficDirection,
                    linkType: LinkType, modifiedAt: Option[String], modifiedBy: Option[String],
                    attributes: Map[String, Any] = Map(), constructionType: ConstructionType = ConstructionType.InUse,
                    linkSource: LinkGeomSource = LinkGeomSource.NormalLinkInterface,lanes:Seq[PersistedLane]=Seq()) extends RoadLinkLike {

  def municipalityCode: Int = attributes("municipalitycode").asInstanceOf[BigInt].intValue
  def verticalLevel : Int = attributes("surfacerelation").asInstanceOf[BigInt].intValue
  def surfaceType : Int = attributes("surfacetype").asInstanceOf[BigInt].intValue
  def roadNumber: Option[String] = attributes.get("roadnumber").map(_.toString)
  def roadPartNumber: Option[String] = attributes.get("roadpartnumber").map(_.toString)
  val dateValue = attributes.getOrElse("versionstarttime", attributes.getOrElse("starttime", ""))
  val timeStamp: Long = Try(new DateTime(dateValue).getMillis).getOrElse(0L)
  def accessRightId: Option[String] = attributes.get("ACCESS_RIGHT_ID").map(_.toString)
  def privateRoadAssociation: Option[String] = attributes.get("PRIVATE_ROAD_ASSOCIATION").map(_.toString)
  def additionalInfo: Option[String] = attributes.get("ADDITIONAL_INFO").map(_.toString)

  //TODO isPaved = !isNotPaved = SurfaceType.apply(surfaceType) match {case SurfaceType.Paved => true case _ => false
  def isPaved : Boolean = surfaceType == SurfaceType.Paved.value
  def isNotPaved : Boolean = surfaceType == SurfaceType.None.value

  def extractMTKClass(attributes: Map[String, Any]): MTKClassWidth = {
    Try(attributes("roadclass").asInstanceOf[BigInt])
      .map(_.toInt)
      .map(MTKClassWidth.apply)
      .getOrElse(MTKClassWidth.Unknown)
  }

  def roadIdentifier: Option[Either[Int, String]] = {
    Try(Left(attributes("roadnumber").asInstanceOf[BigInt].intValue()))
      .orElse(Try(Right(getStringAttribute("roadnamefin"))))
      .orElse(Try(Right(getStringAttribute("roadnameswe"))))
      .toOption
  }

  def isCarRoadOrCyclePedestrianPath : Boolean = {
    val allowedFunctionalClasses = Set(1, 2, 3, 4, 5, 6)
    val disallowedLinkTypes = Set(UnknownLinkType.value, PedestrianZone.value, CableFerry.value)

    allowedFunctionalClasses.contains(functionalClass % 10) && !disallowedLinkTypes.contains(linkType.value)
  }

  def isCarTrafficRoad : Boolean = {
    isCarRoadOrCyclePedestrianPath && !(CycleOrPedestrianPath.value == linkType.value)
  }

  def isSimpleCarTrafficRoad: Boolean = {
    val roadLinkTypeAllowed = Seq(ServiceOrEmergencyRoad, CycleOrPedestrianPath, PedestrianZone, TractorRoad, MotorwayServiceAccess, SpecialTransportWithoutGate, SpecialTransportWithGate, CableFerry, RestArea)
    val constructionTypeAllowed: Seq[ConstructionType] = Seq(UnderConstruction, Planned)

    !(constructionTypeAllowed.contains(constructionType) || (roadLinkTypeAllowed.contains(linkType) && administrativeClass == State))
  }

  def roadNameIdentifier: Option[String] = {
    Try(getStringAttribute("roadnamefin"))
      .orElse(Try(getStringAttribute("roadnameswe")))
      .toOption
  }

  private def getStringAttribute(name: String) = attributes(name).asInstanceOf[String]
}

sealed trait LinkId {
  def value: String
}

object LinkId {
  case object Unknown extends LinkId { def value = "99" }

  def isUnknown(id: String): Boolean = {
    id == null || id == Unknown.value
  }
}

sealed trait SurfaceType {
  def value: Int
}

object SurfaceType {
  val values = Set(Unknown, None, Paved)

  def apply(intValue: Int): SurfaceType = {
    values.find(_.value == intValue).getOrElse(None)
  }

  case object Unknown extends SurfaceType { def value = 0 }
  case object None extends SurfaceType { def value = 1 }
  case object Paved extends SurfaceType { def value = 2}
}

sealed trait MTKClassWidth {
  def value: Int
  def width: Int
}

object MTKClassWidth {
  val values = Set(CarRoad_Ia, CarRoad_Ib, CarRoad_IIa, CarRoad_IIb, CarRoad_IIIa, CarRoad_IIIb, DriveWay)

  def apply(intValue: Int): MTKClassWidth = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object CarRoad_Ia extends MTKClassWidth { def value = 12111; def  width = 1100}
  case object CarRoad_Ib extends MTKClassWidth { def value = 12112; def  width	= 1100}
  case object CarRoad_IIa extends MTKClassWidth { def value = 12121; def width = 650 }
  case object CarRoad_IIb extends MTKClassWidth { def value = 12122; def  width = 650 }
  case object CarRoad_IIIa extends MTKClassWidth { def value = 12131; def width = 400 }
  case object CarRoad_IIIb extends MTKClassWidth { def value = 12132; def width = 400 }
  case object DriveWay	extends MTKClassWidth { def value = 12141; def width = 250}
  case object Unknown extends MTKClassWidth {def value=0; def width = 0}
}
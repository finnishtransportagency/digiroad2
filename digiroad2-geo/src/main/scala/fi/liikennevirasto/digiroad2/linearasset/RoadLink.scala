package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset._

import scala.util.Try

trait RoadLinkLike extends PolyLine{
  def linkId: Long
  def municipalityCode: Int
  def length: Double
  def administrativeClass: AdministrativeClass
  def trafficDirection: TrafficDirection
  def roadNumber: Option[String]
  def linkSource: LinkGeomSource
  def attributes: Map[String, Any]
  def constructionType: ConstructionType
}

case class RoadLinkProperties(linkId: Long,
                              functionalClass: Int,
                              linkType: LinkType,
                              trafficDirection: TrafficDirection,
                              administrativeClass: AdministrativeClass ,
                              modifiedAt: Option[String],
                              modifiedBy: Option[String])

case class RoadLink(linkId: Long, geometry: Seq[Point],
                    length: Double, administrativeClass: AdministrativeClass,
                    functionalClass: Int, trafficDirection: TrafficDirection,
                    linkType: LinkType, modifiedAt: Option[String], modifiedBy: Option[String],
                    attributes: Map[String, Any] = Map(), constructionType: ConstructionType = ConstructionType.InUse,
                    linkSource: LinkGeomSource = LinkGeomSource.NormalLinkInterface) extends RoadLinkLike {

  def municipalityCode: Int = attributes("MUNICIPALITYCODE").asInstanceOf[BigInt].intValue
  def verticalLevel : Int = attributes("VERTICALLEVEL").asInstanceOf[BigInt].intValue
  def surfaceType : Int = attributes("SURFACETYPE").asInstanceOf[BigInt].intValue
  def roadNumber: Option[String] = attributes.get("ROADNUMBER").map(_.toString)

  //TODO isPaved = !isNotPaved = SurfaceType.apply(surfaceType) match {case SurfaceType.Paved => true case _ => false
  def isPaved : Boolean = surfaceType == SurfaceType.Paved.value
  def isNotPaved : Boolean = surfaceType == SurfaceType.None.value

  def extractMTKClass(attributes: Map[String, Any]): MTKClassWidth = {
    Try(attributes("MTKCLASS").asInstanceOf[BigInt])
      .map(_.toInt)
      .map(MTKClassWidth.apply)
      .getOrElse(MTKClassWidth.Unknown)
  }

  def roadIdentifier: Option[Either[Int, String]] = {
    Try(Left(attributes("ROADNUMBER").asInstanceOf[BigInt].intValue()))
      .orElse(Try(Right(getStringAttribute("ROADNAME_FI"))))
      .orElse(Try(Right(getStringAttribute("ROADNAME_SE"))))
      .orElse(Try(Right(getStringAttribute("ROADNAME_SM"))))
      .toOption
  }

  def isCarTrafficRoad = {
    val allowedFunctionalClasses = Set(1, 2, 3, 4, 5, 6)
    val disallowedLinkTypes = Set(UnknownLinkType.value, CycleOrPedestrianPath.value, PedestrianZone.value, CableFerry.value)

    allowedFunctionalClasses.contains(functionalClass % 10) && !disallowedLinkTypes.contains(linkType.value)
  }

  private def getStringAttribute(name: String) = attributes(name).asInstanceOf[String]
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
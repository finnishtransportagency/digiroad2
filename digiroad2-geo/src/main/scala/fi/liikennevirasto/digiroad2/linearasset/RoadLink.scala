package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset._

import scala.util.Try

trait RoadLinkLike {
  def linkId: Long
  def municipalityCode: Int
  def geometry: Seq[Point]
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
                    linkSource: LinkGeomSource = LinkGeomSource.NormalLinkInterface ) extends PolyLine with RoadLinkLike {

  val Roadlink_SurfaceType_Unknown = 0
  val Roadlink_SurfaceType_None = 1
  val Roadlink_SurfaceType_Paved = 2

  def municipalityCode: Int = attributes("MUNICIPALITYCODE").asInstanceOf[BigInt].intValue
  def verticalLevel : Int = attributes("VERTICALLEVEL").asInstanceOf[BigInt].intValue
  def surfaceType : Int = attributes("SURFACETYPE").asInstanceOf[BigInt].intValue
  def roadNumber: Option[String] = attributes.get("ROADNUMBER").map(_.toString)
  def isPaved : Boolean = surfaceType == Roadlink_SurfaceType_Paved
  def isNotPaved : Boolean = surfaceType == Roadlink_SurfaceType_None

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
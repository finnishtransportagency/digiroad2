package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset._

import scala.util.Try

case class VVHRoadLinkWithProperties(mmlId: Long, geometry: Seq[Point],
                                     length: Double, administrativeClass: AdministrativeClass,
                                     functionalClass: Int, trafficDirection: TrafficDirection,
                                     linkType: LinkType, modifiedAt: Option[String], modifiedBy: Option[String],
                                     attributes: Map[String, Any] = Map()) extends PolyLine {

  def municipalityCode: Int = attributes("MUNICIPALITYCODE").asInstanceOf[BigInt].intValue

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
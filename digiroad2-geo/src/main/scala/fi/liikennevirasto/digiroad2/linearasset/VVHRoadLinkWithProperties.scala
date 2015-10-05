package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{LinkType, TrafficDirection, AdministrativeClass}

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

  private def getStringAttribute(name: String) = attributes(name).asInstanceOf[String]
}
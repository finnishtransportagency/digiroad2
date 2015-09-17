package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{LinkType, TrafficDirection, AdministrativeClass}

import scala.util.Try

trait PolyLine {
  val geometry: Seq[Point]
}

case class VVHRoadLinkWithProperties(mmlId: Long, geometry: Seq[Point],
                                     length: Double, administrativeClass: AdministrativeClass,
                                     functionalClass: Int, trafficDirection: TrafficDirection,
                                     linkType: LinkType, modifiedAt: Option[String], modifiedBy: Option[String],
                                     attributes: Map[String, Any] = Map()) extends PolyLine

object RoadLinkUtility {
  def municipalityCodeFromAttributes(attributes: Map[String, Any]): Int = {
    attributes("MUNICIPALITYCODE").asInstanceOf[BigInt].intValue()
  }

  def roadIdentifierFromRoadLink(link: VVHRoadLinkWithProperties): Option[Either[Int, String]] = {
    def getAttributeWithoutNull(attribute: String) = link.attributes(attribute).asInstanceOf[String].toString
    Try(Left(link.attributes("ROADNUMBER").asInstanceOf[BigInt].intValue()))
      .orElse(Try(Right(getAttributeWithoutNull("ROADNAME_FI"))))
      .orElse(Try(Right(getAttributeWithoutNull("ROADNAME_SE"))))
      .orElse(Try(Right(getAttributeWithoutNull("ROADNAME_SM"))))
      .toOption
  }
}

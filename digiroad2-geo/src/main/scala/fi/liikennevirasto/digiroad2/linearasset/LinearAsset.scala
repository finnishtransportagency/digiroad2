package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{LinkType, TrafficDirection, AdministrativeClass}

trait PolyLine {
  val geometry: Seq[Point]
}

case class VVHRoadLinkWithProperties(mmlId: Long, geometry: Seq[Point],
                                     length: Double, administrativeClass: AdministrativeClass,
                                     functionalClass: Int, trafficDirection: TrafficDirection,
                                     linkType: LinkType, modifiedAt: Option[String], modifiedBy: Option[String],
                                     attributes: Map[String, Any] = Map()) extends PolyLine

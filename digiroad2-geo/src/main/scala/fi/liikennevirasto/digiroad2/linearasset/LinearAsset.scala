package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.SideCode

case class LinearAsset(id: Long, mmlId: Long, sideCode: SideCode, value: Option[Int], geometry: Seq[Point], expired: Boolean,
                       startMeasure: Double, endMeasure: Double,
                       endpoints: Set[Point], modifiedBy: Option[String], modifiedDateTime: Option[String],
                       createdBy: Option[String], createdDateTime: Option[String], typeId: Int) extends PolyLine

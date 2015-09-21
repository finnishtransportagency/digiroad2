package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point

case class LinearAsset(id: Long, mmlId: Long, sideCode: Int, value: Option[Int], points: Seq[Point], expired: Boolean,
                       startMeasure: Double, endMeasure: Double,
                       endpoints: Set[Point], modifiedBy: Option[String], modifiedDateTime: Option[String],
                       createdBy: Option[String], createdDateTime: Option[String], typeId: Int)

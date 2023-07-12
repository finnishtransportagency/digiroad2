package fi.liikennevirasto.digiroad2.model

import fi.liikennevirasto.digiroad2.Point

case class PointAssetLRMPosition(id: Long, startMeasure: Double, point: Option[Point], timeStamp: Long, linkSource: Int)
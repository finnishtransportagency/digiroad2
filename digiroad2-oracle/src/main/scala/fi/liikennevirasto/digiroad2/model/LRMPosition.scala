package fi.liikennevirasto.digiroad2.model

import fi.liikennevirasto.digiroad2.Point

case class LRMPosition(id: Long, startMeasure: Double, endMeasure: Double, point: Option[Point], vvhTimeStamp: Long, linkSource: Int)
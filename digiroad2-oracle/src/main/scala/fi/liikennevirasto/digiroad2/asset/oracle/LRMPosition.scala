package fi.liikennevirasto.digiroad2.asset.oracle

import oracle.spatial.geometry.JGeometry
import fi.liikennevirasto.digiroad2.Point

case class LRMPosition(id: Long, startMeasure: Int, endMeasure: Int, point: Option[Point])
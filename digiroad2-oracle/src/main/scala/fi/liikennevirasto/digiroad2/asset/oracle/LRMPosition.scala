package fi.liikennevirasto.digiroad2.asset.oracle

import oracle.spatial.geometry.JGeometry

case class LRMPosition(id: Long, startMeasure: Int, endMeasure: Int, geometry: JGeometry)
package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point, VVHClient}
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.viite.dao.RoadAddress
import fi.liikennevirasto.viite._

trait RoadAddressMapper {

  def mapRoadAddresses(roadAddressMapping: Seq[RoadAddressMapping])(ra: RoadAddress): Seq[RoadAddress] = {
    def truncate(geometry: Seq[Point], d1: Double, d2: Double) = {
      val startM = Math.max(Math.min(d1, d2), 0.0)
      val endM = Math.min(Math.max(d1, d2), GeometryUtils.geometryLength(geometry))
      GeometryUtils.truncateGeometry3D(geometry,
        startM, endM)
    }

    // When mapping contains a larger span (sourceStart, sourceEnd) than the road address then split the mapping
    def adjust(mapping: RoadAddressMapping, startM: Double, endM: Double) = {
      if (withinTolerance(mapping.sourceStartM, startM) && withinTolerance(mapping.sourceEndM, endM))
        mapping
      else {
        val (newStartM, newEndM) =
          (if (withinTolerance(startM, mapping.sourceStartM) || startM < mapping.sourceStartM) mapping.sourceStartM else startM,
            if (withinTolerance(endM, mapping.sourceEndM) || endM > mapping.sourceEndM) mapping.sourceEndM else endM)
        val (newTargetStartM, newTargetEndM) = (mapping.interpolate(newStartM), mapping.interpolate(newEndM))
        val geomStartM = Math.min(mapping.sourceStartM, mapping.sourceEndM)
        val geomTargetStartM = Math.min(mapping.interpolate(mapping.sourceStartM), mapping.interpolate(mapping.sourceEndM))
        mapping.copy(sourceStartM = newStartM, sourceEndM = newEndM, sourceGeom =
          truncate(mapping.sourceGeom, newStartM - geomStartM, newEndM - geomStartM),
          targetStartM = newTargetStartM, targetEndM = newTargetEndM, targetGeom =
            truncate(mapping.targetGeom, newTargetStartM - geomTargetStartM, newTargetEndM - geomTargetStartM))
      }
    }

    roadAddressMapping.filter(_.matches(ra)).map(m => adjust(m, ra.startMValue, ra.endMValue)).map(adjMap => {
      val (sideCode, mappedGeom, (mappedStartAddrM, mappedEndAddrM)) =
        if (isDirectionMatch(adjMap))
          (ra.sideCode, adjMap.targetGeom, splitRoadAddressValues(ra, adjMap))
        else {
          (switchSideCode(ra.sideCode), adjMap.targetGeom.reverse,
            splitRoadAddressValues(ra, adjMap))
        }
      val (startM, endM) = (Math.min(adjMap.targetEndM, adjMap.targetStartM), Math.max(adjMap.targetEndM, adjMap.targetStartM))

      val startCP = ra.startCalibrationPoint match {
        case None => None
        case Some(cp) => if (cp.addressMValue == mappedStartAddrM) Some(cp.copy(linkId = adjMap.targetLinkId,
          segmentMValue = if (sideCode == SideCode.AgainstDigitizing) Math.max(startM, endM) else 0.0)) else None
      }
      val endCP = ra.endCalibrationPoint match {
        case None => None
        case Some(cp) => if (cp.addressMValue == mappedEndAddrM) Some(cp.copy(linkId = adjMap.targetLinkId,
          segmentMValue = if (sideCode == SideCode.TowardsDigitizing) Math.max(startM, endM) else 0.0)) else None
      }
      ra.copy(id = NewRoadAddress, linkId = adjMap.targetLinkId, startAddrMValue = startCP.map(_.addressMValue).getOrElse(mappedStartAddrM),
        endAddrMValue = endCP.map(_.addressMValue).getOrElse(mappedEndAddrM), floating = false,
        sideCode = sideCode, startMValue = startM, endMValue = endM, geom = mappedGeom, calibrationPoints = (startCP, endCP),
        adjustedTimestamp = VVHClient.createVVHTimeStamp())
    })
  }

  /** Used when road address span is larger than mapping: road address must be split into smaller parts
    *
    * @param roadAddress Road address to split
    * @param mapping Mapping entry that may or may not have smaller or larger span than road address
    * @return A pair of address start and address end values this mapping and road address applies to
    */
  private def splitRoadAddressValues(roadAddress: RoadAddress, mapping: RoadAddressMapping): (Long, Long) = {
    if (withinTolerance(roadAddress.startMValue, mapping.sourceStartM) && withinTolerance(roadAddress.endMValue, mapping.sourceEndM))
      (roadAddress.startAddrMValue, roadAddress.endAddrMValue)
    else {
      val (startM, endM) = GeometryUtils.overlap((roadAddress.startMValue, roadAddress.endMValue),(mapping.sourceStartM, mapping.sourceEndM)).get
      roadAddress.addressBetween(startM, endM)
    }
  }



  /**
    * Check if the sequence of points are going in matching direction (best matching)
    * This means that the starting and ending points are closer to each other than vice versa
    *
    * @param geom1 Geometry one
    * @param geom2 Geometry two
    */
  def isDirectionMatch(geom1: Seq[Point], geom2: Seq[Point]): Boolean = {
    val x = distancesBetweenEndPoints(geom1, geom2)
    x._1 < x._2
  }

  def isSideCodeChange(geom1: Seq[Point], geom2: Seq[Point]): Boolean = {
    GeometryUtils.areAdjacent(geom1.last, geom2.last) ||
      GeometryUtils.areAdjacent(geom1.head, geom2.head)
  }

  /**
    * Measure summed distance between two geometries: head-to-head + tail-to-head vs. head-to-tail + tail-to-head
    * @param geom1 Geometry 1
    * @param geom2 Goemetry 2
    * @return h2h distance, h2t distance sums
    */
  def distancesBetweenEndPoints(geom1: Seq[Point], geom2: Seq[Point]) = {
    (geom1.head.distance2DTo(geom2.head) + geom1.last.distance2DTo(geom2.last),
      geom1.last.distance2DTo(geom2.head) + geom1.head.distance2DTo(geom2.last))
  }

  def minDistanceBetweenEndPoints(geom1: Seq[Point], geom2: Seq[Point]) = {
    val x = distancesBetweenEndPoints(geom1, geom2)
    Math.min(x._1, x._2)
  }

  def isDirectionMatch(r: RoadAddressMapping): Boolean = {
    ((r.sourceStartM - r.sourceEndM) * (r.targetStartM - r.targetEndM)) > 0
  }
  def withinTolerance(mValue1: Double, mValue2: Double) = {
    Math.abs(mValue1 - mValue2) < MinAllowedRoadAddressLength
  }


}

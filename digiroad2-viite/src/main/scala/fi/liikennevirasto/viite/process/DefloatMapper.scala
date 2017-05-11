package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao.RoadAddress
import fi.liikennevirasto.viite.model.RoadAddressLink

object DefloatMapper {

  def createAddressMap(sources: Seq[RoadAddressLink], targets: Seq[RoadAddressLink]): Seq[RoadAddressMapping] = {
    def formMapping(startSourceLink: RoadAddressLink, startSourceM: Double,
                    endSourceLink: RoadAddressLink, endSourceM: Double,
                    startTargetLink: RoadAddressLink, startTargetM: Double,
                    endTargetLink: RoadAddressLink, endTargetM: Double): RoadAddressMapping = {
      RoadAddressMapping(startSourceLink.linkId, startTargetLink.linkId, startSourceM,
        if (startSourceLink.linkId == endSourceLink.linkId) endSourceM else Double.NaN,
        startTargetM,
        if (startTargetLink.linkId == endTargetLink.linkId) endTargetM else Double.NaN,
        Seq(GeometryUtils.calculatePointFromLinearReference(startSourceLink.geometry, startSourceM).getOrElse(Point(Double.NaN, Double.NaN)),
        GeometryUtils.calculatePointFromLinearReference(endSourceLink.geometry, endSourceM).getOrElse(Point(Double.NaN, Double.NaN))),
        Seq(GeometryUtils.calculatePointFromLinearReference(startTargetLink.geometry, startTargetM).getOrElse(Point(Double.NaN, Double.NaN)),
          GeometryUtils.calculatePointFromLinearReference(endTargetLink.geometry, endTargetM).getOrElse(Point(Double.NaN, Double.NaN)))
      )
    }

    val (orderedSource, orderedTarget) = orderRoadAddressLinks(sources, targets)
    // The lengths may not be exactly equal: coefficient is to adjust that we advance both chains at the same relative speed
    val targetCoeff = orderedSource.map(_.length).sum / orderedTarget.map(_.length).sum
    val runningLength = (orderedSource.scanLeft(0.0)((len, link) => len+link.length) ++
      orderedTarget.scanLeft(0.0)((len, link) => len+targetCoeff*link.length)).map(setPrecision).distinct.sorted
    val pairs = runningLength.zip(runningLength.tail).map{ case (st, end) =>
      (findStartLRMLocation(st, orderedSource), findEndLRMLocation(end, orderedSource), findStartLRMLocation(st/targetCoeff, orderedTarget), findEndLRMLocation(end/targetCoeff, orderedTarget))}
    pairs.map(x => formMapping(x._1._1, x._1._2, x._2._1, x._2._2, x._3._1, x._3._2, x._4._1, x._4._2))
  }

  def mapRoadAddresses(roadAddressMapping: Seq[RoadAddressMapping])(ra: RoadAddress): Seq[RoadAddress] = {
    val mappings = roadAddressMapping.filter(mapping => mapping.sourceLinkId == ra.linkId &&
      isMatch(ra.startMValue, ra.endMValue, mapping.sourceStartM, mapping.sourceEndM))
    mappings.map(mapping => {
      val (mappedStartM, mappedEndM) = (mapping.targetStartM, mapping.targetEndM)
      val (sideCode, mappedGeom, (mappedStartAddrM, mappedEndAddrM)) =
        if (isDirectionMatch(mapping))
          (ra.sideCode, mapping.targetGeom, splitRoadAddressValues(ra, mapping.sourceStartM, mapping.sourceEndM))
        else {
          (switchSideCode(ra.sideCode), mapping.targetGeom.reverse,
            splitRoadAddressValues(ra, mapping.sourceStartM, mapping.sourceEndM).swap)
        }
      val (startM, endM, startAddrM, endAddrM) =
        if (mappedStartM > mappedEndM)
          (mappedEndM, mappedStartM, mappedEndAddrM, mappedStartAddrM)
        else
          (mappedStartM, mappedEndM, mappedStartAddrM, mappedEndAddrM)
      val startCP = ra.startCalibrationPoint match {
        case None => None
        case Some(cp) => if (cp.addressMValue == startAddrM) Some(cp.copy(linkId = mapping.targetLinkId,
          segmentMValue = if (sideCode == SideCode.AgainstDigitizing) Math.max(startM, endM) else 0.0)) else None
      }
      val endCP = ra.endCalibrationPoint match {
        case None => None
        case Some(cp) => if (cp.addressMValue == endAddrM) Some(cp.copy(linkId = mapping.targetLinkId,
          segmentMValue = if (sideCode == SideCode.TowardsDigitizing) Math.max(startM, endM) else 0.0)) else None
      }
      ra.copy(id = -1000L, linkId = mapping.targetLinkId, startAddrMValue = startCP.map(_.addressMValue).getOrElse(startAddrM),
        endAddrMValue = endCP.map(_.addressMValue).getOrElse(endAddrM), floating = false,
        sideCode = sideCode, startMValue = startM, endMValue = endM, geom = mappedGeom, calibrationPoints = (startCP, endCP))
    })
  }

  private def setPrecision(d: Double) = {
    BigDecimal(d).setScale(3, BigDecimal.RoundingMode.HALF_UP).toDouble
  }

  private def isMatch(xStart: Double, xEnd: Double, limit1: Double, limit2: Double) = {
    GeometryUtils.overlapAmount((xStart, xEnd), (limit1, limit2)) > 0.999
  }

  private def splitRoadAddressValues(roadAddress: RoadAddress, startM: Double, endM: Double): (Long, Long) = {
    // The lengths may not be exactly equal: coefficient is to adjust that
    val coefficient = (roadAddress.endAddrMValue - roadAddress.startAddrMValue) / (roadAddress.endMValue - roadAddress.startMValue)
    roadAddress.sideCode match {
      case SideCode.AgainstDigitizing =>
        (roadAddress.endAddrMValue - Math.round(endM*coefficient), roadAddress.endAddrMValue - Math.round(startM*coefficient))
      case SideCode.TowardsDigitizing =>
        (roadAddress.startAddrMValue + Math.round(startM*coefficient), roadAddress.startAddrMValue + Math.round(endM*coefficient))
      case _ => throw new InvalidAddressDataException(s"Bad sidecode ${roadAddress.sideCode} on road address $roadAddress")
    }
  }

  private def findStartLRMLocation(mValue: Double, links: Seq[RoadAddressLink]): (RoadAddressLink, Double) = {
    if (links.isEmpty)
      throw new InvalidAddressDataException(s"Unable to map linear locations $mValue beyond links end")
    val current = links.head
    if (Math.abs(current.length - mValue) < 0.1) {
      if (links.tail.nonEmpty)
        findStartLRMLocation(0.0, links.tail)
      else
        (current, setPrecision(applySideCode(current.length, current.length, current.sideCode)))
    } else if (current.length < mValue) {
      findStartLRMLocation(mValue - current.length, links.tail)
    } else {
      val dist = applySideCode(mValue, current.length, current.sideCode)
      (current, setPrecision(Math.min(Math.max(0.0, dist), current.length)))
    }
  }

  private def findEndLRMLocation(mValue: Double, links: Seq[RoadAddressLink]): (RoadAddressLink, Double) = {
    if (links.isEmpty)
      throw new InvalidAddressDataException(s"Unable to map linear locations $mValue beyond links end")
    val current = links.head
    if (Math.abs(current.length - mValue) < 1.0 && links.tail.isEmpty || Math.abs(current.length - mValue) < 0.1) {
      (current, setPrecision(applySideCode(current.length, current.length, current.sideCode)))
    } else if (current.length < mValue) {
      findEndLRMLocation(mValue - current.length, links.tail)
    } else {
      val dist = applySideCode(mValue, current.length, current.sideCode)
      (current, setPrecision(Math.min(Math.max(0.0, dist), current.length)))
    }
  }

  private def applySideCode(mValue: Double, linkLength: Double, sideCode: SideCode) = {
    sideCode match {
      case SideCode.AgainstDigitizing => linkLength - mValue
      case SideCode.TowardsDigitizing => mValue
      case _ => throw new InvalidAddressDataException(s"Unhandled sidecode $sideCode")
    }
  }

  /**
    * Take two sequences of road address links and order them so that the sequence covers the same road geometry
    * and logical addressing in the same order
    * @param sources Source road address links (floating)
    * @param targets Target road address links (missing addresses)
    * @return
    */
  def orderRoadAddressLinks(sources: Seq[RoadAddressLink], targets: Seq[RoadAddressLink]): (Seq[RoadAddressLink], Seq[RoadAddressLink]) = {
    /*
      Calculate sidecode changes - if next road address link starts with the current (end) point
      then side code remains the same. Otherwise it's reversed
     */
    def getSideCode(roadAddressLink: RoadAddressLink, previousSideCode: SideCode, currentPoint: Point) = {
      val geom = roadAddressLink.geometry
      if (GeometryUtils.areAdjacent(geom.head, currentPoint))
        previousSideCode
      else
        switchSideCode(previousSideCode)
    }

    def extending(link: RoadAddressLink, ext: RoadAddressLink) = {
      link.roadNumber == ext.roadNumber && link.roadPartNumber == ext.roadPartNumber &&
        link.trackCode == ext.trackCode && link.endAddressM == ext.startAddressM
    }
    def extendChainByAddress(ordered: Seq[RoadAddressLink], unordered: Seq[RoadAddressLink]): Seq[RoadAddressLink] = {
      if (ordered.isEmpty)
        return extendChainByAddress(Seq(unordered.head), unordered.tail)
      if (unordered.isEmpty)
        return ordered
      val (next, rest) = unordered.partition(u => extending(ordered.last, u))
      if (next.nonEmpty)
        extendChainByAddress(ordered ++ next, rest)
      else {
        val (previous, rest) = unordered.partition(u => extending(u, ordered.head))
        if (previous.isEmpty)
          throw new IllegalArgumentException("Non-contiguous road addressing")
        else
          extendChainByAddress(previous ++ ordered, rest)
      }
    }
    def extendChainByGeometry(ordered: Seq[RoadAddressLink], unordered: Seq[RoadAddressLink], sideCode: SideCode): Seq[RoadAddressLink] = {
      // First link gets the assigned side code
      if (ordered.isEmpty)
        return extendChainByGeometry(Seq(unordered.head.copy(sideCode=sideCode)), unordered.tail, sideCode)
      if (unordered.isEmpty) {
        return ordered
      }
      // Find a road address link that continues from current last link
      unordered.find(ral => GeometryUtils.areAdjacent(ral.geometry, ordered.last.geometry)) match {
        case Some(link) =>
          val sideCode = if (isSideCodeChange(link.geometry, ordered.last.geometry))
            switchSideCode(ordered.last.sideCode)
          else
            ordered.last.sideCode
          extendChainByGeometry(ordered ++ Seq(link.copy(sideCode=sideCode)), unordered.filterNot(link.equals), sideCode)
        case _ => throw new InvalidAddressDataException("Non-contiguous road target geometry")
      }
    }
    val orderedSources = extendChainByAddress(Seq(sources.head), sources.tail)
    val startingPoint = orderedSources.head.sideCode match {
      case SideCode.TowardsDigitizing => orderedSources.head.geometry.head
      case SideCode.AgainstDigitizing => orderedSources.head.geometry.last
      case _ => throw new InvalidAddressDataException("Bad sidecode on source")
    }

    val (endingLinks, middleLinks) = targets.partition(t => targets.count(t2 => GeometryUtils.areAdjacent(t.geometry, t2.geometry)) < 3)
    val preSortedTargets = endingLinks.sortBy(l => minDistanceBetweenEndPoints(Seq(startingPoint), l.geometry)) ++ middleLinks
    val startingSideCode = if (isDirectionMatch(orderedSources.head.geometry, preSortedTargets.head.geometry))
      orderedSources.head.sideCode
    else
      switchSideCode(orderedSources.head.sideCode)
    (orderedSources, extendChainByGeometry(Seq(), preSortedTargets, startingSideCode))
  }

  def switchSideCode(sideCode: SideCode) = {
    // Switch between against and towards 2 -> 3, 3 -> 2
    SideCode.apply(5-sideCode.value)
  }

  /**
    * Check if the sequence of points are going in matching direction (best matching)
    * This means that the starting and ending points are closer to each other than vice versa
    *
    * @param geom1 Geometry one
    * @param geom2 Geometry two
    */
  private def isDirectionMatch(geom1: Seq[Point], geom2: Seq[Point]): Boolean = {
    val x = distancesBetweenEndPoints(geom1, geom2)
    x._1 < x._2
  }

  private def isSideCodeChange(geom1: Seq[Point], geom2: Seq[Point]): Boolean = {
    GeometryUtils.areAdjacent(geom1.last, geom2.last) ||
      GeometryUtils.areAdjacent(geom1.head, geom2.head)
  }

  private def distancesBetweenEndPoints(geom1: Seq[Point], geom2: Seq[Point]) = {
    (geom1.head.distance2DTo(geom2.head) + geom1.last.distance2DTo(geom2.last),
      geom1.last.distance2DTo(geom2.head) + geom1.head.distance2DTo(geom2.last))
  }

  private def minDistanceBetweenEndPoints(geom1: Seq[Point], geom2: Seq[Point]) = {
    val x = distancesBetweenEndPoints(geom1, geom2)
    Math.min(x._1, x._2)
  }

  private def isDirectionMatch(r: RoadAddressMapping): Boolean = {
    ((r.sourceStartM - r.sourceEndM) * (r.targetStartM - r.targetEndM)) > 0
  }

  def postTransferChecks(seq: Seq[RoadAddress]): Unit = {
    if (seq.count(_.startCalibrationPoint.nonEmpty) > 1)
      throw new InvalidAddressDataException("Too many starting calibration points after transfer")
    if (seq.count(_.endCalibrationPoint.nonEmpty) > 1)
      throw new InvalidAddressDataException("Too many starting calibration points after transfer")
    val startCPAddr = seq.find(_.startCalibrationPoint.nonEmpty)
    val endCPAddr = seq.find(_.endCalibrationPoint.nonEmpty)
    if (startCPAddr.nonEmpty) {
      val (addr, cp) = (startCPAddr.get, startCPAddr.get.startCalibrationPoint.get)
      if (addr.startAddrMValue != cp.addressMValue)
        throw new InvalidAddressDataException(s"Start calibration point value mismatch in $cp")
      if (seq.exists(_.startAddrMValue < cp.addressMValue))
        throw new InvalidAddressDataException(s"Start calibration point not in the beginning of chain $cp")
      if (addr.sideCode == SideCode.TowardsDigitizing && Math.abs(cp.segmentMValue) > 0.0 ||
        addr.sideCode == SideCode.AgainstDigitizing && Math.abs(cp.segmentMValue - addr.endMValue) > 0.001)
        throw new InvalidAddressDataException(s"Start calibration point LRM mismatch in $cp, sideCode = ${addr.sideCode}, ${addr.startMValue}-${addr.endMValue}")
    }
    if (endCPAddr.nonEmpty) {
      val (addr, cp) = (endCPAddr.get, endCPAddr.get.endCalibrationPoint.get)
      if (addr.endAddrMValue != cp.addressMValue)
        throw new InvalidAddressDataException(s"End calibration point value mismatch in $cp")
      if (seq.exists(_.endAddrMValue > cp.addressMValue))
        throw new InvalidAddressDataException(s"End calibration point not in the end of chain $cp")
      if (addr.sideCode == SideCode.AgainstDigitizing && Math.abs(cp.segmentMValue) > 0.0 ||
        addr.sideCode == SideCode.TowardsDigitizing && Math.abs(cp.segmentMValue - addr.endMValue) > 0.001)
        throw new InvalidAddressDataException(s"End calibration point LRM mismatch in $cp, sideCode = ${addr.sideCode}, ${addr.startMValue}-${addr.endMValue}")
    }
    val grouped = seq.groupBy(_.linkId).mapValues(_.groupBy(_.sideCode).keySet.size)
    if (grouped.exists{ case (_, sideCodes) => sideCodes > 1})
      throw new InvalidAddressDataException(s"Multiple sidecodes generated for links ${grouped.filter(_._2 > 1).keySet.mkString(", ")}")
  }

  def preTransferChecks(seq: Seq[RoadAddress]): Unit = {
    if (seq.count(_.startCalibrationPoint.nonEmpty) > 1)
      throw new IllegalArgumentException("Too many starting calibration points before transfer")
    if (seq.count(_.endCalibrationPoint.nonEmpty) > 1)
      throw new IllegalArgumentException("Too many starting calibration points before transfer")
    val startCPAddr = seq.find(_.startCalibrationPoint.nonEmpty)
    val endCPAddr = seq.find(_.endCalibrationPoint.nonEmpty)
    if (startCPAddr.nonEmpty) {
      val (addr, cp) = (startCPAddr.get, startCPAddr.get.startCalibrationPoint.get)
      if (addr.startAddrMValue != cp.addressMValue)
        throw new IllegalArgumentException(s"Start calibration point value mismatch in $cp")
      if (seq.exists(_.startAddrMValue < cp.addressMValue))
        throw new IllegalArgumentException("Start calibration point not in the first link of source")
      if (addr.sideCode == SideCode.TowardsDigitizing && Math.abs(cp.segmentMValue) > 0.0 ||
        addr.sideCode == SideCode.AgainstDigitizing && Math.abs(cp.segmentMValue - addr.endMValue) > 0.001)
        throw new IllegalArgumentException(s"Start calibration point LRM mismatch in $cp")
    }
    if (endCPAddr.nonEmpty) {
      val (addr, cp) = (endCPAddr.get, endCPAddr.get.endCalibrationPoint.get)
      if (addr.endAddrMValue != cp.addressMValue)
        throw new IllegalArgumentException(s"End calibration point value mismatch in $cp")
      if (seq.exists(_.endAddrMValue > cp.addressMValue))
        throw new IllegalArgumentException("Start calibration point not in the last link of source")
      if (Math.abs(cp.segmentMValue -
        (addr.sideCode match {
          case SideCode.AgainstDigitizing => 0.0
          case SideCode.TowardsDigitizing => addr.endMValue
          case _ => Double.NegativeInfinity
        })
      ) > 0.1)
        throw new IllegalArgumentException(s"End calibration point LRM mismatch in $cp")
    }
    val grouped = seq.groupBy(_.linkId).mapValues(_.groupBy(_.sideCode).keySet.size)
    if (grouped.exists{ case (_, sideCodes) => sideCodes > 1})
      throw new IllegalArgumentException(s"Multiple sidecodes found for links ${grouped.filter(_._2 > 1).keySet.mkString(", ")}")
  }

  def invalidMapping(roadAddressMapping: RoadAddressMapping): Boolean = {
    roadAddressMapping.sourceStartM.isNaN || roadAddressMapping.sourceEndM.isNaN ||
      roadAddressMapping.targetStartM.isNaN || roadAddressMapping.targetEndM.isNaN
  }
}

case class RoadAddressMapping(sourceLinkId: Long, targetLinkId: Long, sourceStartM: Double, sourceEndM: Double,
                              targetStartM: Double, targetEndM: Double, sourceGeom: Seq[Point], targetGeom: Seq[Point])

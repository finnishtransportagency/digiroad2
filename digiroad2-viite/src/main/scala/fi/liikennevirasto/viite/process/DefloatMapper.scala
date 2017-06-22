package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point, RoadLinkType, VVHClient}
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.viite.switchSideCode
import fi.liikennevirasto.viite.dao.RoadAddress
import fi.liikennevirasto.viite.model.{Anomaly, RoadAddressLink}
import fi.liikennevirasto.viite.{MaxAllowedMValueError, MaxDistanceDiffAllowed, MinAllowedRoadAddressLength, NewRoadAddress}

object DefloatMapper extends RoadAddressMapper {

  def createAddressMap(sources: Seq[RoadAddressLink], targets: Seq[RoadAddressLink]): Seq[RoadAddressMapping] = {
    def formMapping(startSourceLink: RoadAddressLink, startSourceM: Double,
                    endSourceLink: RoadAddressLink, endSourceM: Double,
                    startTargetLink: RoadAddressLink, startTargetM: Double,
                    endTargetLink: RoadAddressLink, endTargetM: Double): RoadAddressMapping = {
      if (startSourceM > endSourceM)
        formMapping(startSourceLink, endSourceM, endSourceLink, startSourceM,
          startTargetLink, endTargetM, endTargetLink, startTargetM)
      else
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
    /* For mapping purposes we have to fuse all road addresses on link to get it right. Otherwise start of a segment
       is assumed to be the start of a road link
     */
    def fuseAddressByLinkId(s: Seq[RoadAddressLink], fused: Seq[RoadAddressLink] = Seq()): Seq[RoadAddressLink] = {
      if (s.isEmpty)
      // We collect them in reverse order because we're interested in the last one and .head is so much better
        fused.reverse
      else {
        val next = s.head
        val previousOpt = fused.headOption.filter(_.linkId == next.linkId)
        if (previousOpt.nonEmpty) {
          // At this point we know that the chain is continuous, all are on same road part and track and ordered
          val edited = previousOpt.get.copy(startAddressM = Math.min(previousOpt.get.startAddressM, next.startAddressM),
            endAddressM = Math.max(previousOpt.get.endAddressM, next.endAddressM),
            startMValue = Math.min(previousOpt.get.startMValue, next.startMValue),
            endMValue = Math.max(previousOpt.get.endMValue, next.endMValue),
            geometry =
              if (next.sideCode == SideCode.AgainstDigitizing)
                next.geometry ++ previousOpt.get.geometry
              else
                previousOpt.get.geometry ++ next.geometry,
            length = previousOpt.get.length + next.length
          )
          fuseAddressByLinkId(s.tail, Seq(edited) ++ fused.tail)
        } else {
          fuseAddressByLinkId(s.tail, Seq(next) ++ fused)
        }
      }
    }

    val (orderedSource, orderedTarget) = orderRoadAddressLinks(sources, targets)
    val joinedSource = fuseAddressByLinkId(orderedSource)
    // The lengths may not be exactly equal: coefficient is to adjust that we advance both chains at the same relative speed
    val targetCoeff = joinedSource.map(_.length).sum / orderedTarget.map(_.length).sum
    val runningLength = (joinedSource.scanLeft(0.0)((len, link) => len+link.length) ++
      orderedTarget.scanLeft(0.0)((len, link) => len+targetCoeff*link.length)).map(setPrecision).distinct.sorted
    val pairs = runningLength.zip(runningLength.tail).map{ case (st, end) =>
      val startSource = findStartLRMLocation(st, joinedSource)
      val endSource = findEndLRMLocation(end, joinedSource, startSource._1.linkId)
      val startTarget = findStartLRMLocation(st/targetCoeff, orderedTarget)
      val endTarget = findEndLRMLocation(end/targetCoeff, orderedTarget, startTarget._1.linkId)
      (startSource, endSource, startTarget, endTarget)}
    pairs.map(x => formMapping(x._1._1, x._1._2, x._2._1, x._2._2, x._3._1, x._3._2, x._4._1, x._4._2))
  }


  private def setPrecision(d: Double) = {
    BigDecimal(d).setScale(3, BigDecimal.RoundingMode.HALF_UP).toDouble
  }

  private def findStartLRMLocation(mValue: Double, links: Seq[RoadAddressLink]): (RoadAddressLink, Double) = {
    if (links.isEmpty)
      throw new InvalidAddressDataException(s"Unable to map linear locations $mValue beyond links end")
    val current = links.head
    if (Math.abs(current.length - mValue) < MinAllowedRoadAddressLength) {
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

  private def findEndLRMLocation(mValue: Double, links: Seq[RoadAddressLink], linkId: Long): (RoadAddressLink, Double) = {
    if (links.isEmpty)
      throw new InvalidAddressDataException(s"Unable to map linear locations $mValue beyond links end")
    val current = links.head
    if (current.linkId != linkId)
      findEndLRMLocation(mValue - current.length, links.tail, linkId)
    else {
      if (Math.abs(current.length - mValue) < MaxDistanceDiffAllowed) {
        (current, setPrecision(applySideCode(current.length, current.length, current.sideCode)))
      } else {
        val dist = applySideCode(mValue, current.length, current.sideCode)
        (current, setPrecision(Math.min(Math.max(0.0, dist), current.length)))
      }
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
    def countTouching(p: Point, points: Seq[Point]) = {
      points.count(x => (x-p).to2D().length() < MaxDistanceDiffAllowed)
    }
    def hasIntersection(roadLinks: Seq[RoadAddressLink]): Boolean = {
      val endPoints = roadLinks.map(rl =>GeometryUtils.geometryEndpoints(rl.geometry))
      val flattened = endPoints.flatMap(pp => Seq(pp._1, pp._2))
      !endPoints.forall(ep =>
        countTouching(ep._1, flattened) < 3 && countTouching(ep._2, flattened) < 3
      )
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

    if (hasIntersection(targets))
      throw new IllegalArgumentException("Non-contiguous road addressing")

    // Partition target links by counting adjacency: anything that touches only the neighbor (and itself) is a starting or ending link
    val (endingLinks, middleLinks) = targets.partition(t => targets.count(t2 => GeometryUtils.areAdjacent(t.geometry, t2.geometry)) < 3)
    val preSortedTargets = endingLinks.sortBy(l => minDistanceBetweenEndPoints(Seq(startingPoint), l.geometry)) ++ middleLinks
    val startingSideCode = if (isDirectionMatch(orderedSources.head.geometry, preSortedTargets.head.geometry))
      orderedSources.head.sideCode
    else
      switchSideCode(orderedSources.head.sideCode)
    (orderedSources, extendChainByGeometry(Seq(), preSortedTargets, startingSideCode))
  }

  def postTransferChecks(seq: Seq[RoadAddress], source: Seq[RoadAddress]): Unit = {
    val (addrMin, addrMax) = (source.map(_.startAddrMValue).min, source.map(_.endAddrMValue).max)
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
        addr.sideCode == SideCode.AgainstDigitizing && Math.abs(cp.segmentMValue - addr.endMValue) > MaxAllowedMValueError)
        throw new InvalidAddressDataException(s"Start calibration point LRM mismatch in $cp, sideCode = ${addr.sideCode}, ${addr.startMValue}-${addr.endMValue}")
    }
    if (endCPAddr.nonEmpty) {
      val (addr, cp) = (endCPAddr.get, endCPAddr.get.endCalibrationPoint.get)
      if (addr.endAddrMValue != cp.addressMValue)
        throw new InvalidAddressDataException(s"End calibration point value mismatch in $cp")
      if (seq.exists(_.endAddrMValue > cp.addressMValue))
        throw new InvalidAddressDataException(s"End calibration point not in the end of chain $cp")
      if (addr.sideCode == SideCode.AgainstDigitizing && Math.abs(cp.segmentMValue) > 0.0 ||
        addr.sideCode == SideCode.TowardsDigitizing && Math.abs(cp.segmentMValue - addr.endMValue) > MaxAllowedMValueError)
        throw new InvalidAddressDataException(s"End calibration point LRM mismatch in $cp, sideCode = ${addr.sideCode}, ${addr.startMValue}-${addr.endMValue}")
    }
    val grouped = seq.groupBy(_.linkId).mapValues(_.groupBy(_.sideCode).keySet.size)
    if (grouped.exists{ case (_, sideCodes) => sideCodes > 1})
      throw new InvalidAddressDataException(s"Multiple sidecodes generated for links ${grouped.filter(_._2 > 1).keySet.mkString(", ")}")
    if (!seq.exists(_.startAddrMValue == addrMin))
      throw new InvalidAddressDataException(s"Generated address list does not start at $addrMin but ${seq.map(_.startAddrMValue).min}")
    if (!seq.exists(_.endAddrMValue == addrMax))
      throw new InvalidAddressDataException(s"Generated address list does not end at $addrMax but ${seq.map(_.endAddrMValue).max}")
    if (!seq.forall(ra => ra.startAddrMValue == addrMin || seq.exists(_.endAddrMValue == ra.startAddrMValue)))
      throw new InvalidAddressDataException(s"Generated address list was non-continuous")
    if (!seq.forall(ra => ra.endAddrMValue == addrMax || seq.exists(_.startAddrMValue == ra.endAddrMValue)))
      throw new InvalidAddressDataException(s"Generated address list was non-continuous")
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
        addr.sideCode == SideCode.AgainstDigitizing && Math.abs(cp.segmentMValue - addr.endMValue) > MaxAllowedMValueError)
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
      ) > MinAllowedRoadAddressLength)
        throw new IllegalArgumentException(s"End calibration point LRM mismatch in $cp")
    }
    val grouped = seq.groupBy(_.linkId).mapValues(_.groupBy(_.sideCode).keySet.size)
    if (grouped.exists{ case (_, sideCodes) => sideCodes > 1})
      throw new IllegalArgumentException(s"Multiple sidecodes found for links ${grouped.filter(_._2 > 1).keySet.mkString(", ")}")
    val tracks = seq.map(_.track).toSet
    if (tracks.size > 1)
      throw new IllegalArgumentException(s"Multiple track codes found ${tracks.mkString(", ")}")
  }

  def invalidMapping(roadAddressMapping: RoadAddressMapping): Boolean = {
    roadAddressMapping.sourceStartM.isNaN || roadAddressMapping.sourceEndM.isNaN ||
      roadAddressMapping.targetStartM.isNaN || roadAddressMapping.targetEndM.isNaN
  }
}

case class RoadAddressMapping(sourceLinkId: Long, targetLinkId: Long, sourceStartM: Double, sourceEndM: Double,
                              targetStartM: Double, targetEndM: Double, sourceGeom: Seq[Point], targetGeom: Seq[Point],
                              vvhTimeStamp: Option[Long] = None) {
  override def toString: String = {
    s"$sourceLinkId -> $targetLinkId: $sourceStartM-$sourceEndM ->  $targetStartM-$targetEndM, $sourceGeom -> $targetGeom"
  }
  /**
    * Test if this mapping matches the road address: Road address is on source link and overlap match is at least 99,9%
    * (overlap amount is the overlapping length divided by the smallest length)
   */
  def matches(roadAddress: RoadAddress): Boolean = {
    sourceLinkId == roadAddress.linkId &&
      (vvhTimeStamp.isEmpty || roadAddress.adjustedTimestamp < vvhTimeStamp.get) &&
      GeometryUtils.overlapAmount((roadAddress.startMValue, roadAddress.endMValue), (sourceStartM, sourceEndM)) > 0.001
  }

  val sourceDelta = sourceEndM - sourceStartM
  val targetDelta = targetEndM - targetStartM
  val sourceLen: Double = Math.abs(sourceDelta)
  val targetLen: Double = Math.abs(targetDelta)
  val coefficient: Double = targetDelta/sourceDelta
  /**
    * interpolate location mValue on starting measure to target location
    * @param mValue Source M value to map
    */
  def interpolate(mValue: Double): Double = {
    if (DefloatMapper.withinTolerance(mValue, sourceStartM))
      targetStartM
    else if (DefloatMapper.withinTolerance(mValue, sourceEndM))
      targetEndM
    else {
      // Affine transformation: y = ax + b
      val a = coefficient
      val b = targetStartM - sourceStartM
      a * mValue + b
    }
  }
}

package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.GeometryUtils
import fi.liikennevirasto.digiroad2.asset.{TrafficDirection, SideCode}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, MValueAdjustment, SideCodeAdjustment}

object SpeedLimitFiller {
  private val MaxAllowedMValueError = 0.1
  private val Epsilon = 1E-6 // Smallest value we can tolerate to equal the same. One micrometer.
  private val MinAllowedSpeedLimitLength = 0.1

  private def adjustSegment(segment: SpeedLimit, roadLink: RoadLink): (SpeedLimit, Seq[MValueAdjustment]) = {
    val startError = segment.startMeasure
    val roadLinkLength = GeometryUtils.geometryLength(roadLink.geometry)
    val endError = roadLinkLength - segment.endMeasure
    val mAdjustment =
      if (startError > MaxAllowedMValueError || endError > MaxAllowedMValueError)
        Seq(MValueAdjustment(segment.id, segment.linkId, 0, roadLinkLength))
      else
        Nil
    val modifiedSegment = segment.copy(geometry = GeometryUtils.truncateGeometry(roadLink.geometry, 0, roadLinkLength), startMeasure = 0, endMeasure = roadLinkLength)
    (modifiedSegment, mAdjustment)
  }

  private def modifiedSort(left: SpeedLimit, right: SpeedLimit) = {
    val leftStamp = left.modifiedDateTime.orElse(left.createdDateTime)
    val rightStamp = right.modifiedDateTime.orElse(right.createdDateTime)
    (leftStamp, rightStamp) match {
      case (Some(l), Some(r)) => l.isAfter(r)
      case (None, Some(r)) => false
      case (Some(l), None) => true
      case (None, None) => true
    }
  }

  private def adjustTwoWaySegments(roadLink: RoadLink,
                                   segments: Seq[SpeedLimit]):
  (Seq[SpeedLimit], Seq[MValueAdjustment]) = {
    val twoWaySegments = segments.filter(_.sideCode == SideCode.BothDirections).sortWith(modifiedSort)
    if (twoWaySegments.length == 1 && segments.forall(_.sideCode == SideCode.BothDirections)) {
      val segment = segments.last
      val (adjustedSegment, mValueAdjustments) = adjustSegment(segment, roadLink)
      (Seq(adjustedSegment), mValueAdjustments)
    } else {
      (twoWaySegments, Nil)
    }
  }

  private def adjustOneWaySegments(roadLink: RoadLink,
                                   segments: Seq[SpeedLimit],
                                   runningDirection: SideCode):
  (Seq[SpeedLimit], Seq[MValueAdjustment]) = {
    val segmentsTowardsRunningDirection = segments.filter(_.sideCode == runningDirection).sortWith(modifiedSort)
    if (segmentsTowardsRunningDirection.length == 1 && !segments.exists(_.sideCode == SideCode.BothDirections)) {
      val segment = segmentsTowardsRunningDirection.last
      val (adjustedSegment, mValueAdjustments) = adjustSegment(segment, roadLink)
      (Seq(adjustedSegment), mValueAdjustments)
    } else {
      (segmentsTowardsRunningDirection, Nil)
    }
  }

  private def adjustSegmentMValues(roadLink: RoadLink, segments: Seq[SpeedLimit], changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
    val (towardsGeometrySegments, towardsGeometryAdjustments) = adjustOneWaySegments(roadLink, segments, SideCode.TowardsDigitizing)
    val (againstGeometrySegments, againstGeometryAdjustments) = adjustOneWaySegments(roadLink, segments, SideCode.AgainstDigitizing)
    val (twoWayGeometrySegments, twoWayGeometryAdjustments) = adjustTwoWaySegments(roadLink, segments)
    val mValueAdjustments = towardsGeometryAdjustments ++ againstGeometryAdjustments ++ twoWayGeometryAdjustments
    (towardsGeometrySegments ++ againstGeometrySegments ++ twoWayGeometrySegments,
      changeSet.copy(adjustedMValues = changeSet.adjustedMValues ++ mValueAdjustments))
  }

  private def capToGeometry(roadLink: RoadLink, segments: Seq[SpeedLimit], changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
    val linkLength = GeometryUtils.geometryLength(roadLink.geometry)
    val (overflowingSegments, passThroughSegments) = segments.partition(_.endMeasure - MaxAllowedMValueError > linkLength)
    val cappedSegments = overflowingSegments.map { s =>
      (s.copy(geometry = GeometryUtils.truncateGeometry(roadLink.geometry, s.startMeasure, linkLength), endMeasure = linkLength),
        MValueAdjustment(s.id, roadLink.linkId, s.startMeasure, linkLength))
    }
    (passThroughSegments ++ cappedSegments.map(_._1), changeSet.copy(adjustedMValues = changeSet.adjustedMValues ++ cappedSegments.map(_._2)))
  }

  private def adjustLopsidedLimit(roadLink: RoadLink, segments: Seq[SpeedLimit], changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
    val onlyLimitOnLink = segments.length == 1 && segments.head.sideCode != SideCode.BothDirections
    if (onlyLimitOnLink) {
      val segment = segments.head
      val sideCodeAdjustments = Seq(SideCodeAdjustment(segment.id, SideCode.BothDirections))
      (Seq(segment.copy(sideCode = SideCode.BothDirections)), changeSet.copy(adjustedSideCodes = changeSet.adjustedSideCodes ++ sideCodeAdjustments))
    } else {
      (segments, changeSet)
    }
  }

  private def adjustSideCodeOnOneWayLink(roadLink: RoadLink, segments: Seq[SpeedLimit], changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
    if (roadLink.trafficDirection == TrafficDirection.BothDirections) {
      (segments, changeSet)
    } else {
      val (twoSided, oneSided) = segments.partition { s => s.sideCode == SideCode.BothDirections }
      val adjusted = oneSided.map { s => (s.copy(sideCode = SideCode.BothDirections), SideCodeAdjustment(s.id, SideCode.BothDirections)) }
      (twoSided ++ adjusted.map(_._1), changeSet.copy(adjustedSideCodes = changeSet.adjustedSideCodes ++ adjusted.map(_._2)))
    }
  }

  private def dropRedundantSegments(roadLink: RoadLink, segments: Seq[SpeedLimit], changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
    val sortedSegments = segments.sortWith(modifiedSort)
    val headOption = sortedSegments.headOption
    val valueShared = segments.length > 1 && headOption.exists(first => segments.forall(_.value == first.value))
    valueShared match {
      case true =>
        val first = headOption.get
        val rest = sortedSegments.tail
        val segmentDrops = rest.map(_.id).toSet
        (Seq(first), changeSet.copy(droppedAssetIds = changeSet.droppedAssetIds ++ segmentDrops))
      case false => (segments, changeSet)
    }
  }

  private def dropShortLimits(roadLinks: RoadLink, speedLimits: Seq[SpeedLimit], changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
    val limitsToDrop = speedLimits.filter { limit => GeometryUtils.geometryLength(limit.geometry) < MinAllowedSpeedLimitLength }.map(_.id).toSet
    val limits = speedLimits.filterNot { x => limitsToDrop.contains(x.id) }
    (limits, changeSet.copy(droppedAssetIds = changeSet.droppedAssetIds ++ limitsToDrop))
  }

  private def generateUnknownSpeedLimitsForLink(roadLink: RoadLink, segmentsOnLink: Seq[SpeedLimit]): Seq[SpeedLimit] = {
    val lrmPositions: Seq[(Double, Double)] = segmentsOnLink.map { x => (x.startMeasure, x.endMeasure) }
    val remainders = lrmPositions.foldLeft(Seq((0.0, roadLink.length)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > MinAllowedSpeedLimitLength}
    remainders.map { segment =>
      val geometry = GeometryUtils.truncateGeometry(roadLink.geometry, segment._1, segment._2)
      SpeedLimit(0, roadLink.linkId, SideCode.BothDirections, roadLink.trafficDirection, None, geometry, segment._1, segment._2, None, None, None, None, 0, None)
    }
  }

  private def dropSegmentsOutsideGeometry(roadLink: RoadLink, assets: Seq[SpeedLimit], changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
    val (segmentsWithinGeometry, segmentsOutsideGeometry) = assets.partition(_.startMeasure < roadLink.length)
    val droppedAssetIds = segmentsOutsideGeometry.map(_.id).toSet
    (segmentsWithinGeometry, changeSet.copy(droppedAssetIds = changeSet.droppedAssetIds ++ droppedAssetIds))
  }

  private def combine(roadLink: RoadLink, speedLimits: Seq[SpeedLimit], changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
    /**
      * Pick the latest asset for each single piece
     */
    def squash(startM: Double, endM: Double, speedLimits: Seq[SpeedLimit]): Seq[SegmentPiece] = {
      val sl = speedLimits.filter(sl => sl.startMeasure <= startM && sl.endMeasure >= endM)
      val a = sl.filter(sl => sl.sideCode.equals(SideCode.AgainstDigitizing) || sl.sideCode.equals(SideCode.BothDirections)).sortWith(modifiedSort).headOption
      val t = sl.filter(sl => sl.sideCode.equals(SideCode.TowardsDigitizing) || sl.sideCode.equals(SideCode.BothDirections)).sortWith(modifiedSort).headOption

      (a, t) match {
        case (Some(x),Some(y)) => Seq(SegmentPiece(x.id, startM, endM, SideCode.AgainstDigitizing), SegmentPiece(y.id, startM, endM, SideCode.TowardsDigitizing))
        case (Some(x),None) => Seq(SegmentPiece(x.id, startM, endM, SideCode.AgainstDigitizing))
        case (None,Some(y)) => Seq(SegmentPiece(y.id, startM, endM, SideCode.TowardsDigitizing))
        case _ => Seq()
      }
    }
    def combineEqualValues(segmentPieces: Seq[SegmentPiece], limits: Seq[SpeedLimit]) = {
      val seg1 = limits.find(_.id == segmentPieces.head.assetId).get
      val seg2 = limits.find(_.id == segmentPieces.last.assetId).get
      if (seg1.value.equals(seg2.value)) {
        val winner = Seq(seg1,seg2).sortWith(modifiedSort).head
        Seq(segmentPieces.head.copy(assetId = winner.id, sideCode = SideCode.BothDirections))
      } else {
        segmentPieces
      }
    }
    def findMatching(toMatch: SegmentPiece, limits: Seq[SpeedLimit]) = {
      limits.find(sl => sl.id == toMatch.assetId && sl.startMeasure == toMatch.startM)
    }
    def extendOrDivide(segmentPieces: Seq[SegmentPiece], limits: Seq[SpeedLimit]): (SpeedLimit, Seq[SegmentPiece]) = {
      val current = segmentPieces.head
      val rest = segmentPieces.tail
      if (rest.nonEmpty) {
        val currentSpeedLimitValue = findMatching(current, limits).map(_.value)
        val next = rest.find(sp => sp.startM == current.endM && sp.sideCode == current.sideCode &&
          findMatching(sp, limits).map(_.value).equals(currentSpeedLimitValue))
        if (next.nonEmpty) {
          return extendOrDivide(Seq(current.copy(endM = next.get.endM)) ++ rest.filterNot(sp => sp.equals(next.get)), limits)
        }
      }
      (limits.find(_.id == current.assetId).get.copy(sideCode = current.sideCode, startMeasure = current.startM, endMeasure = current.endM), rest)
    }
    def generateLimitsForOrphanSegments(origin: SpeedLimit, orphans: Seq[SegmentPiece]): Seq[SpeedLimit] = {
      if (orphans.nonEmpty) {
        val segmentPiece = orphans.head
        val sl = origin.copy(id = 0L, startMeasure = segmentPiece.startM, endMeasure = segmentPiece.endM, sideCode = segmentPiece.sideCode)
        if (orphans.tail.nonEmpty) {
          val t = extendOrDivide(orphans.tail, Seq(origin))
          return Seq(t._1) ++ generateLimitsForOrphanSegments(origin, t._2)
        }
        return Seq(sl)
      }
      Seq()
    }
    def updateGeometry(limits: Seq[SpeedLimit], roadLink: RoadLink): (Seq[(SpeedLimit, Option[MValueAdjustment])]) = {
      limits.map { sl =>
        val newGeom = GeometryUtils.truncateGeometry(roadLink.geometry, sl.startMeasure, sl.endMeasure)
        newGeom.equals(sl.geometry) match {
          case true => (sl, None)
          case false => (sl.copy(geometry = newGeom), Option(MValueAdjustment(sl.id, sl.linkId, sl.startMeasure, sl.endMeasure)))
        }
      }
    }
    println("before combine")
    speedLimits.foreach(println)
    val pointsOfInterest = (speedLimits.map(_.startMeasure) ++ speedLimits.map(_.endMeasure)).distinct.sorted
    if (pointsOfInterest.length < 2)
      return (speedLimits, changeSet)

    val pieces = pointsOfInterest.zip(pointsOfInterest.tail)
    val squashed = pieces.flatMap(p => squash(p._1, p._2, speedLimits))
    val combo = squashed.groupBy(_.startM).flatMap(n => combineEqualValues(n._2, speedLimits))
    val result = combo.groupBy(_.assetId).map(n => extendOrDivide(n._2.toSeq, speedLimits))
    val combinedLimits = result.keys.toSeq
    val splitLimits = combinedLimits.map(sl => generateLimitsForOrphanSegments(sl, result.getOrElse(sl, Seq())))

    val updatedGeometries = updateGeometry(combinedLimits, roadLink)
    val mValueAdjustments = updatedGeometries.flatMap(n => n._2)
    val changedSideCodes = combinedLimits.filter(cl =>
      speedLimits.exists(sl => sl.id == cl.id && !sl.sideCode.equals(cl.sideCode))).
      map(sl => SideCodeAdjustment(sl.id, sl.sideCode))
    val updatedSpeedLimits = updatedGeometries.map(n => n._1) ++ updateGeometry(splitLimits.flatten, roadLink).map(n => n._1)
    val droppedIds = speedLimits.map(_.id).toSet.--(updatedSpeedLimits.map(_.id).toSet)

    (updatedSpeedLimits, changeSet.copy(droppedAssetIds = changeSet.droppedAssetIds ++ droppedIds,
      adjustedMValues = changeSet.adjustedMValues ++ mValueAdjustments, adjustedSideCodes = changeSet.adjustedSideCodes ++ changedSideCodes))
  }

  private def fuse(roadLink: RoadLink, speedLimits: Seq[SpeedLimit], changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
    println("before fuse")
    speedLimits.foreach(println)
    val sortedList = speedLimits.sortBy(_.startMeasure)
    if (speedLimits.nonEmpty) {
      val origin = sortedList.head
      val target = sortedList.tail.find(sl => sl.startMeasure == origin.endMeasure && sl.value.equals(origin.value) && sl.sideCode.equals(origin.sideCode))
      if (target.nonEmpty) {
        // pick id if it already has one regardless of which one is newer
        val toBeFused = Seq(origin, target.get).sortWith(modifiedSort)
        val newId = toBeFused.find(_.id != 0).map(_.id).getOrElse(0L)
        val modified = toBeFused.head.copy(id=newId, startMeasure = origin.startMeasure, endMeasure = target.get.endMeasure, geometry = GeometryUtils.truncateGeometry(roadLink.geometry, origin.startMeasure, target.get.endMeasure))
        val droppedId = Set(origin.id, target.get.id) -- Set(modified.id, 0L) // never attempt to drop id zero
        val mValueAdjustment = Seq(MValueAdjustment(modified.id, modified.linkId, modified.startMeasure, modified.endMeasure))
        fuse(roadLink, Seq(modified) ++ sortedList.tail.filterNot(sl => Set(origin, target.get).contains(sl)),
          changeSet.copy(droppedAssetIds = changeSet.droppedAssetIds ++ droppedId, adjustedMValues = changeSet.adjustedMValues ++ mValueAdjustment))
      } else {
        val fused = fuse(roadLink, sortedList.tail, changeSet)
        (Seq(origin) ++ fused._1, fused._2)
      }
    } else {
      (speedLimits, changeSet)
    }
  }

  private def fillHoles(roadLink: RoadLink, speedLimits: Seq[SpeedLimit], changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
    def fillBySideCode(speedLimits: Seq[SpeedLimit], roadLink: RoadLink, changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
      if (speedLimits.size > 1) {
        val left = speedLimits.head
        val right = speedLimits.find(sl => sl.startMeasure >= left.endMeasure)
        if (right.nonEmpty && Math.abs(left.endMeasure - right.get.startMeasure) < MinAllowedSpeedLimitLength && Math.abs(left.endMeasure - right.get.startMeasure) >= Epsilon) {
          val adjustedLeft = left.copy(endMeasure = right.get.startMeasure,
            geometry = GeometryUtils.truncateGeometry(roadLink.geometry, left.startMeasure, right.get.startMeasure))
          val adj = MValueAdjustment(adjustedLeft.id, adjustedLeft.linkId, adjustedLeft.startMeasure, adjustedLeft.endMeasure)
          val recurse = fillBySideCode(speedLimits.tail, roadLink, changeSet)
          (Seq(adjustedLeft) ++ recurse._1, recurse._2.copy(adjustedMValues = recurse._2.adjustedMValues ++ Seq(adj)))
        } else {
          val recurse = fillBySideCode(speedLimits.tail, roadLink, changeSet)
          (Seq(left) ++ recurse._1, recurse._2)
        }
      } else {
        (speedLimits, changeSet)
      }
    }
    println("before fill")
    speedLimits.foreach(println)
    val (towardsGeometrySegments, towardsGeometryAdjustments) = fillBySideCode(speedLimits, roadLink, ChangeSet(Set(), Nil, Nil, Set()))
    (towardsGeometrySegments.toSeq,
      changeSet.copy(adjustedMValues = changeSet.adjustedMValues ++ towardsGeometryAdjustments.adjustedMValues))
  }

  /**
    * Removes obsoleted mvalue adjustments from the list
    * @param roadLink
    * @param speedLimits
    * @param changeSet
    * @return
    */
  private def clean(roadLink: RoadLink, speedLimits: Seq[SpeedLimit], changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
    def prune(adj: Seq[MValueAdjustment]): Seq[MValueAdjustment] = {
      if (adj.isEmpty)
        return adj
      adj.tail.exists(a => a.assetId == adj.head.assetId) match {
        case true => prune(adj.tail)
        case false => Seq(adj.head) ++ prune(adj.tail)
      }
    }
    def pruneSideCodes(adj: Seq[SideCodeAdjustment]): Seq[SideCodeAdjustment] = {
      if (adj.isEmpty)
        return adj
      adj.tail.exists(a => a.assetId == adj.head.assetId) match {
        case true => pruneSideCodes(adj.tail)
        case false => Seq(adj.head) ++ pruneSideCodes(adj.tail)
      }
    }
    println("before clean")
    speedLimits.foreach(println)
    val droppedIds = changeSet.droppedAssetIds
    val adjustments = prune(changeSet.adjustedMValues.filterNot(a => droppedIds.contains(a.assetId)))
    val sideAdjustments = pruneSideCodes(changeSet.adjustedSideCodes.filterNot(a => droppedIds.contains(a.assetId)))
    (speedLimits, changeSet.copy(droppedAssetIds = droppedIds, adjustedMValues = adjustments, adjustedSideCodes = sideAdjustments))
  }

    def fillTopology(roadLinks: Seq[RoadLink], speedLimits: Map[Long, Seq[SpeedLimit]]): (Seq[SpeedLimit], ChangeSet) = {
    val fillOperations: Seq[(RoadLink, Seq[SpeedLimit], ChangeSet) => (Seq[SpeedLimit], ChangeSet)] = Seq(
      dropSegmentsOutsideGeometry,
      combine,
      fuse,
      dropRedundantSegments,
      adjustSegmentMValues,
      capToGeometry,
      adjustLopsidedLimit,
      adjustSideCodeOnOneWayLink,
      dropShortLimits,
      fillHoles,
      clean
    )

    val initialChangeSet = ChangeSet(Set.empty, Nil, Nil, Set.empty)

    roadLinks.foldLeft(Seq.empty[SpeedLimit], initialChangeSet) { case (acc, roadLink) =>
      val (existingSegments, changeSet) = acc
      val segments = speedLimits.getOrElse(roadLink.linkId, Nil)
      val validSegments = segments.filterNot { segment => changeSet.droppedAssetIds.contains(segment.id) }

      val (adjustedSegments, segmentAdjustments) = fillOperations.foldLeft(validSegments, changeSet) { case ((currentSegments, currentAdjustments), operation) =>
        operation(roadLink, currentSegments, currentAdjustments)
      }

      val generatedSpeedLimits = generateUnknownSpeedLimitsForLink(roadLink, adjustedSegments)
      (existingSegments ++ adjustedSegments ++ generatedSpeedLimits, segmentAdjustments)
    }
  }

  def projectSpeedLimit(asset: SpeedLimit, to: RoadLink, projection: Projection) = {
    val newLinkId = to.linkId
    val assetId = asset.linkId match {
      case to.linkId => asset.id
      case _ => 0
    }
    val oldLength = projection.oldEnd - projection.oldStart
    val newLength = projection.newEnd - projection.newStart
    var newSideCode = asset.sideCode
    var newDirection = asset.trafficDirection
    var newStart = projection.newStart + (asset.startMeasure - projection.oldStart) * Math.abs(newLength/oldLength)
    var newEnd = projection.newEnd + (asset.endMeasure - projection.oldEnd) * Math.abs(newLength/oldLength)

    // Test if the direction has changed - side code will be affected, too
    if (oldLength * newLength < 0) {
      newSideCode = newSideCode match {
        case (SideCode.AgainstDigitizing) => SideCode.TowardsDigitizing
        case (SideCode.TowardsDigitizing) => SideCode.AgainstDigitizing
        case _ => newSideCode
      }
      newDirection = newDirection match {
        case (TrafficDirection.AgainstDigitizing) => TrafficDirection.TowardsDigitizing
        case (TrafficDirection.TowardsDigitizing) => TrafficDirection.AgainstDigitizing
        case _ => newDirection
      }
      newStart = projection.newStart - (asset.endMeasure - projection.oldStart) * Math.abs(newLength/oldLength)
      newEnd = projection.newEnd - (asset.startMeasure - projection.oldEnd) * Math.abs(newLength/oldLength)
    }

    newStart = Math.min(to.length, Math.max(0.0, newStart))
    newEnd = Math.max(0.0, Math.min(to.length, newEnd))

    val geometry = GeometryUtils.truncateGeometry(
      Seq(GeometryUtils.calculatePointFromLinearReference(to.geometry, newStart).getOrElse(to.geometry.head),
        GeometryUtils.calculatePointFromLinearReference(to.geometry, newEnd).getOrElse(to.geometry.last)),
      0, to.length)

    SpeedLimit(id = assetId, linkId = newLinkId, sideCode = newSideCode, trafficDirection = newDirection,
      asset.value, geometry, newStart, newEnd,
      modifiedBy = asset.modifiedBy,
      modifiedDateTime = asset.modifiedDateTime, createdBy = asset.createdBy, createdDateTime = asset.createdDateTime,
      vvhTimeStamp = projection.vvhTimeStamp, geomModifiedDate = None
    )
  }

  case class Projection(oldStart: Double, oldEnd: Double, newStart: Double, newEnd: Double, vvhTimeStamp: Long)
  case class SegmentPiece(assetId: Long, startM: Double, endM: Double, sideCode: SideCode)
}

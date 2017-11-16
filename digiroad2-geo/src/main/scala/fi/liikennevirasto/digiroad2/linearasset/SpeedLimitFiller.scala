package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.GeometryUtils
import fi.liikennevirasto.digiroad2.GeometryUtils.Projection
import fi.liikennevirasto.digiroad2.asset.{TrafficDirection, SideCode}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, MValueAdjustment, SideCodeAdjustment}

object SpeedLimitFiller {
  private val MaxAllowedMValueError = 0.1
  private val Epsilon = 1E-6 /* Smallest mvalue difference we can tolerate to be "equal to zero". One micrometer.
                                See https://en.wikipedia.org/wiki/Floating_point#Accuracy_problems
                             */
  private val MinAllowedSpeedLimitLength = 2.0

  private def adjustSegment(segment: SpeedLimit, roadLink: RoadLink): (SpeedLimit, Seq[MValueAdjustment]) = {
    val startError = segment.startMeasure
    val roadLinkLength = GeometryUtils.geometryLength(roadLink.geometry)
    val endError = roadLinkLength - segment.endMeasure
    val mAdjustment =
      if (startError > MaxAllowedMValueError || endError > MaxAllowedMValueError)
        Seq(MValueAdjustment(segment.id, segment.linkId, 0, roadLinkLength))
      else
        Nil
    val modifiedSegment = segment.copy(geometry = GeometryUtils.truncateGeometry3D(roadLink.geometry, 0, roadLinkLength), startMeasure = 0, endMeasure = roadLinkLength)
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
      (s.copy(geometry = GeometryUtils.truncateGeometry3D(roadLink.geometry, Math.min(s.startMeasure, linkLength), linkLength), endMeasure = linkLength),
        MValueAdjustment(s.id, roadLink.linkId, Math.min(s.startMeasure, linkLength), linkLength))
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

  private def dropShortLimits(roadLink: RoadLink, speedLimits: Seq[SpeedLimit], changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
    val limitsToDrop = speedLimits.filter { limit => GeometryUtils.geometryLength(limit.geometry) < MinAllowedSpeedLimitLength &&
      roadLink.length > MinAllowedSpeedLimitLength }.map(_.id).toSet
    val limits = speedLimits.filterNot { x => limitsToDrop.contains(x.id) }
    (limits, changeSet.copy(droppedAssetIds = changeSet.droppedAssetIds ++ limitsToDrop))
  }

  private def generateUnknownSpeedLimitsForLink(roadLink: RoadLink, segmentsOnLink: Seq[SpeedLimit]): Seq[SpeedLimit] = {
    val lrmPositions: Seq[(Double, Double)] = segmentsOnLink.map { x => (x.startMeasure, x.endMeasure) }
    val remainders = lrmPositions.foldLeft(Seq((0.0, roadLink.length)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > MinAllowedSpeedLimitLength}
    remainders.map { segment =>
      val geometry = GeometryUtils.truncateGeometry3D(roadLink.geometry, segment._1, segment._2)
      SpeedLimit(0, roadLink.linkId, SideCode.BothDirections, roadLink.trafficDirection, None, geometry, segment._1, segment._2, None, None, None, None, 0, None, municipalityCode = None, linkSource = roadLink.linkSource)
    }
  }

  private def dropSegmentsOutsideGeometry(roadLink: RoadLink, assets: Seq[SpeedLimit], changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
    val (segmentsWithinGeometry, segmentsOutsideGeometry) = assets.partition(_.startMeasure < roadLink.length)
    val droppedAssetIds = segmentsOutsideGeometry.map(_.id).toSet
    (segmentsWithinGeometry, changeSet.copy(droppedAssetIds = changeSet.droppedAssetIds ++ droppedAssetIds))
  }

  /**
    * Combines speed limits so that there is no overlapping, no consecutive speed limits with same values and
    * that the speed limits with both directions are preferred.
    * Speed limits go through the following process
    * - squash: road link is cut to small one-sided pieces defined by the startMeasures and endMeasures of all speed limits
    * - combine: squashed pieces are turned into two-sided (SideCode.BothDirections) where speed limit is equal,
    *            keeping the latest edited SpeedLimit id and timestamps
    * - extend: combined pieces are merged if they have the same side code, speed limit value and one starts where another ends
    * - orphans: pieces that are orphans (as newer, projected speed limit may overwrite another in the middle!) are collected
    *            and then extended just like above.
    * - geometry update: all the result geometries and sidecodes are revised and written in the change set
    *
    * @param roadLink Roadlink speed limits are related to
    * @param limits Sequence of speed limits to combine where possible
    * @param changeSet Original changeset
    * @return Sequence of SpeedLimits and ChangeSet containing the changes done here
    */
  private def combine(roadLink: RoadLink, limits: Seq[SpeedLimit], changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {

    def replaceUnknownAssetIds(asset: SpeedLimit, pseudoId: Long) = {
      asset.id match {
        case 0 => asset.copy(id = pseudoId)
        case _ => asset
      }
    }
    /**
      * Pick the latest asset for each single piece and create SegmentPiece objects for those
     */
    def squash(startM: Double, endM: Double, speedLimits: Seq[SpeedLimit]): Seq[SegmentPiece] = {
      val sl = speedLimits.filter(sl => sl.startMeasure <= startM && sl.endMeasure >= endM)
      val a = sl.filter(sl => sl.sideCode.equals(SideCode.AgainstDigitizing) || sl.sideCode.equals(SideCode.BothDirections)).sortWith(modifiedSort).headOption
      val t = sl.filter(sl => sl.sideCode.equals(SideCode.TowardsDigitizing) || sl.sideCode.equals(SideCode.BothDirections)).sortWith(modifiedSort).headOption

      (a, t) match {
        case (Some(x),Some(y)) => Seq(SegmentPiece(x.id, startM, endM, SideCode.AgainstDigitizing, x.value), SegmentPiece(y.id, startM, endM, SideCode.TowardsDigitizing, y.value))
        case (Some(x),None) => Seq(SegmentPiece(x.id, startM, endM, SideCode.AgainstDigitizing, x.value))
        case (None,Some(y)) => Seq(SegmentPiece(y.id, startM, endM, SideCode.TowardsDigitizing, y.value))
        case _ => Seq()
      }
    }
    /**
      * Combine sides together if matching m-values and speed limit values.
      * @param segmentPieces Sequence of one or two segment pieces (guaranteed by squash operation)
      * @param limits All speed limits on road link
      * @return Sequence of segment pieces (1 or 2 segment pieces in sequence)
      */
    def combineEqualValues(segmentPieces: Seq[SegmentPiece], limits: Seq[SpeedLimit]): Seq[SegmentPiece] = {
      val seg1 = segmentPieces.head
      val seg2 = segmentPieces.last
      (seg1.value, seg2.value) match {
        case (Some(v1), Some(v2)) =>
          val sl1 = limits.find(_.id == seg1.assetId).get
          val sl2 = limits.find(_.id == seg2.assetId).get
          if (v1.equals(v2) && GeometryUtils.withinTolerance(sl1.geometry, sl2.geometry, MaxAllowedMValueError)) {
            val winner = limits.filter(l => l.id == seg1.assetId || l.id == seg2.assetId).sortBy(s =>
              s.endMeasure - s.startMeasure).head
            Seq(segmentPieces.head.copy(assetId = winner.id, sideCode = SideCode.BothDirections))
          } else {
            segmentPieces
          }
        case (Some(v1), None) => Seq(segmentPieces.head.copy(sideCode = SideCode.BothDirections))
        case (None, Some(v2)) => Seq(segmentPieces.last.copy(sideCode = SideCode.BothDirections))
        case _ => segmentPieces
      }
    }

    /**
      * Take pieces of segment and combine two of them if they are related to the similar speed limits and extend each other
      * (matching startM, endM, value and sidecode)
      * Return altered SpeedLimit as well as the pieces that could not be added to that speed limit.
      * @param segmentPieces
      * @param limit
      * @return
      */
    def extendOrDivide(segmentPieces: Seq[SegmentPiece], limit: SpeedLimit): (SpeedLimit, Seq[SegmentPiece]) = {
      val sorted = segmentPieces.sortBy(_.startM)
      val current = sorted.head
      val rest = sorted.tail
      if (rest.nonEmpty) {
        val next = rest.find(sp => sp.startM == current.endM && sp.sideCode == current.sideCode &&
          sp.value == current.value)
        if (next.nonEmpty) {
          return extendOrDivide(Seq(current.copy(endM = next.get.endM)) ++ rest.filterNot(sp => sp.equals(next.get)), limit)
        }
      }
      (limit.copy(sideCode = current.sideCode, startMeasure = current.startM, endMeasure = current.endM), rest)
    }

    /**
      * Creates speed limits from orphaned segments (segments originating from a speed limit but no longer connected
      * to them)
      * @param origin Speed limit that was split by overwriting a segment piece(s)
      * @param orphans List of orphaned segment pieces
      * @return New speed limits for orphaned segment pieces
      */
    def generateLimitsForOrphanSegments(origin: SpeedLimit, orphans: Seq[SegmentPiece]): Seq[SpeedLimit] = {
      if (orphans.nonEmpty) {
        val segmentPiece = orphans.sortBy(_.startM).head
        if (orphans.tail.nonEmpty) {
          // Try to extend this segment as far as possible: if SegmentPieces are consecutive produce just one SpeedLimit
          val t = extendOrDivide(orphans, origin.copy(startMeasure = segmentPiece.startM, endMeasure = segmentPiece.endM, sideCode = segmentPiece.sideCode))
          // t now has a speed limit and any orphans it left behind: recursively call this method again
          return Seq(t._1.copy(id = 0L)) ++ generateLimitsForOrphanSegments(origin, t._2)
        }
        // Only orphan in the list, create a new speed limit for it
        val sl = origin.copy(id = 0L, startMeasure = segmentPiece.startM, endMeasure = segmentPiece.endM, sideCode = segmentPiece.sideCode)
        return Seq(sl)
      }
      Seq()
    }
    /**
      * Update geometry for speed limits and return modified speedlimits together with adjustments (if any)
      * @param limits Speed limits
      * @param roadLink current road link
      * @return Sequence of tuples (SpeedLimit, optional MValueAdjustment)
      */
    def updateGeometry(limits: Seq[SpeedLimit], roadLink: RoadLink): (Seq[(SpeedLimit, Option[MValueAdjustment])]) = {
      limits.map { sl =>
        val newGeom = GeometryUtils.truncateGeometry3D(roadLink.geometry, sl.startMeasure, sl.endMeasure)
        GeometryUtils.withinTolerance(newGeom, sl.geometry, MaxAllowedMValueError) match {
          case true => (sl, None)
          case false => (sl.copy(geometry = newGeom), Option(MValueAdjustment(sl.id, sl.linkId, sl.startMeasure, sl.endMeasure)))
        }
      }
    }
    /**
      * Make sure that no two speed limits share the same id, rewrite the ones appearing later with id=0
      * @param toProcess List of speed limits to go thru
      * @param processed List of processed speed limits
      * @return List of speed limits with unique or zero ids
      */
    def cleanSpeedLimitIds(toProcess: Seq[SpeedLimit], processed: Seq[SpeedLimit]): Seq[SpeedLimit] = {
      val (current, rest) = (toProcess.head, toProcess.tail)
      val modified = processed.exists(_.id == current.id) || current.id < 0L match {
        case true => current.copy(id = 0L)
        case _ => current
      }
      if (rest.nonEmpty) {
        cleanSpeedLimitIds(rest, processed ++ Seq(modified))
      } else {
        processed ++ Seq(modified)
      }
    }

    val speedLimits = limits.zipWithIndex.map(n => replaceUnknownAssetIds(n._1, 0L-n._2))
    val pointsOfInterest = (speedLimits.map(_.startMeasure) ++ speedLimits.map(_.endMeasure)).distinct.sorted
    if (pointsOfInterest.length < 2)
      return (speedLimits, changeSet)

    val pieces = pointsOfInterest.zip(pointsOfInterest.tail)
    val segmentPieces = pieces.flatMap(p => squash(p._1, p._2, speedLimits)).sortBy(_.assetId)
    val combinedSegmentPieces = segmentPieces.groupBy(_.startM).flatMap(n => combineEqualValues(n._2, speedLimits))
    val speedLimitsAndOrphanPieces = combinedSegmentPieces.groupBy(_.assetId).map(n => extendOrDivide(n._2.toSeq, speedLimits.find(_.id == n._1).get))
    val combinedLimits = speedLimitsAndOrphanPieces.keys.toSeq
    val newSpeedLimits = combinedLimits.flatMap(sl => generateLimitsForOrphanSegments(sl, speedLimitsAndOrphanPieces.getOrElse(sl, Seq()).sortBy(_.startM)))

    val updatedSpeedLimitsAndMValueAdjustments = updateGeometry(combinedLimits, roadLink)
    val mValueAdjustments = updatedSpeedLimitsAndMValueAdjustments.flatMap(_._2)
    val changedSideCodes = combinedLimits.filter(cl =>
      speedLimits.exists(sl => sl.id == cl.id && !sl.sideCode.equals(cl.sideCode))).
      map(sl => SideCodeAdjustment(sl.id, sl.sideCode))
    val resultingSpeedLimits = updatedSpeedLimitsAndMValueAdjustments.map(n => n._1) ++ updateGeometry(newSpeedLimits, roadLink).map(_._1)
    val droppedIds = speedLimits.map(_.id).toSet.--(resultingSpeedLimits.map(_.id).toSet)

    val returnSpeedLimits = cleanSpeedLimitIds(resultingSpeedLimits, Seq())
    (returnSpeedLimits, changeSet.copy(droppedAssetIds = changeSet.droppedAssetIds ++ droppedIds,
      adjustedMValues = changeSet.adjustedMValues ++ mValueAdjustments.filter(_.assetId > 0), adjustedSideCodes = changeSet.adjustedSideCodes ++ changedSideCodes))
  }

  /**
    * After other change operations connect speed limits that have same values and side codes if they extend each other
    * @param roadLink Road link we are working on
    * @param speedLimits List of speed limits
    * @param changeSet Changes done previously
    * @return List of speed limits and a change set
    */
  private def fuse(roadLink: RoadLink, speedLimits: Seq[SpeedLimit], changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
    val sortedList = speedLimits.sortBy(_.startMeasure)
    if (speedLimits.nonEmpty) {
      val origin = sortedList.head
      val target = sortedList.tail.find(sl => Math.abs(sl.startMeasure - origin.endMeasure) < MaxAllowedMValueError &&
        sl.value.equals(origin.value) && sl.sideCode.equals(origin.sideCode))
      if (target.nonEmpty) {
        // pick id if it already has one regardless of which one is newer
        val toBeFused = Seq(origin, target.get).sortWith(modifiedSort)
        val newId = toBeFused.find(_.id > 0).map(_.id).getOrElse(0L)
        val modified = toBeFused.head.copy(id=newId, startMeasure = origin.startMeasure, endMeasure = target.get.endMeasure,
          geometry = GeometryUtils.truncateGeometry3D(roadLink.geometry, origin.startMeasure, target.get.endMeasure),
          vvhTimeStamp = latestTimestamp(toBeFused.head, target))
        val droppedId = Set(origin.id, target.get.id) -- Set(modified.id, 0L) // never attempt to drop id zero
        val mValueAdjustment = Seq(MValueAdjustment(modified.id, modified.linkId, modified.startMeasure, modified.endMeasure))
        // Replace origin and target with this new item in the list and recursively call itself again
        fuse(roadLink, Seq(modified) ++ sortedList.tail.filterNot(sl => Set(origin, target.get).contains(sl)),
          changeSet.copy(droppedAssetIds = changeSet.droppedAssetIds ++ droppedId, adjustedMValues = changeSet.adjustedMValues.filter(_.assetId > 0) ++ mValueAdjustment))
      } else {
        val fused = fuse(roadLink, sortedList.tail, changeSet)
        (Seq(origin) ++ fused._1, fused._2)
      }
    } else {
      (speedLimits, changeSet)
    }
  }

  /**
    * Fills any missing pieces in the middle of speed limits.
    * - If the gap is smaller than minimum allowed speed limit length the first speed limit is extended
    *   !!! But if it is smaller than 1E-6 we let it be and treat it as a rounding error to avoid repeated writes !!!
    * - If the gap is larger it's let to be and will be generated as unknown speed limit later
    * @param roadLink Road link being handled
    * @param speedLimits List of speed limits
    * @param changeSet Set of changes
    * @return List of speed limits and change set so that there are no small gaps between speed limits
    */
  private def fillHoles(roadLink: RoadLink, speedLimits: Seq[SpeedLimit], changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
    def firstAndLastLimit(speedLimits: Seq[SpeedLimit], sideCode: SideCode) = {
      val filtered = speedLimits.filter(_.sideCode == sideCode)
      (filtered.sortBy(_.startMeasure).headOption,
        filtered.sortBy(0-_.endMeasure).headOption)
    }
    def extendToGeometry(speedLimits: Seq[SpeedLimit], roadLink: RoadLink, changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
      val (startTwoSided, endTwoSided) = firstAndLastLimit(speedLimits, SideCode.BothDirections)
      val (startTowards, endTowards) = firstAndLastLimit(speedLimits, SideCode.TowardsDigitizing)
      val (startAgainst, endAgainst) = firstAndLastLimit(speedLimits, SideCode.AgainstDigitizing)
      val sortedStarts = Seq(startTwoSided, startTowards, startAgainst).flatten.sortBy(_.startMeasure)
      val startChecks = sortedStarts.filter(sl => Math.abs(sl.startMeasure - sortedStarts.head.startMeasure) < Epsilon && sl.startMeasure > MaxAllowedMValueError)
      val newStarts = startChecks.map(sl => sl.copy(startMeasure = 0.0, geometry = GeometryUtils.truncateGeometry3D(roadLink.geometry, 0, sl.endMeasure)))
      val sortedEnds = Seq(endTwoSided, endTowards, endAgainst).flatten.sortBy(0.0 - _.endMeasure)
      val endChecks = sortedEnds.filter(sl => Math.abs(sl.endMeasure - sortedEnds.head.endMeasure) < Epsilon &&
        Math.abs(roadLink.length - sl.endMeasure) > MaxAllowedMValueError)
      val newEnds = endChecks.map(sl => sl.copy(endMeasure = roadLink.length, geometry = GeometryUtils.truncateGeometry3D(roadLink.geometry, sl.startMeasure, roadLink.length)))
      val newLimits = newStarts ++ newEnds
      (speedLimits.filterNot(sl => newLimits.map(_.id).contains(sl.id)) ++ newLimits,
        changeSet.copy(adjustedMValues = changeSet.adjustedMValues ++ newLimits.map(sl => MValueAdjustment(sl.id, roadLink.linkId, sl.startMeasure, sl.endMeasure))))
    }
    def fillBySideCode(speedLimits: Seq[SpeedLimit], roadLink: RoadLink, changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
      if (speedLimits.size > 1) {
        val left = speedLimits.head
        val right = speedLimits.find(sl => sl.startMeasure >= left.endMeasure)
        if (right.nonEmpty && Math.abs(left.endMeasure - right.get.startMeasure) < MinAllowedSpeedLimitLength &&
          Math.abs(left.endMeasure - right.get.startMeasure) >= Epsilon) {
          val adjustedLeft = left.copy(endMeasure = right.get.startMeasure,
            geometry = GeometryUtils.truncateGeometry3D(roadLink.geometry, left.startMeasure, right.get.startMeasure),
            vvhTimeStamp = latestTimestamp(left, right))
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
    val (extended, newChangeSet) = extendToGeometry(speedLimits, roadLink, changeSet)
    val (geometrySegments, geometryAdjustments) = fillBySideCode(extended, roadLink, ChangeSet(Set(), Nil, Nil, Set()))
    (geometrySegments.toSeq,
      newChangeSet.copy(adjustedMValues = newChangeSet.adjustedMValues ++ geometryAdjustments.adjustedMValues))
  }

  private def latestTimestamp(speedLimit: SpeedLimit, speedLimitO: Option[SpeedLimit]) = {
    speedLimitO match {
      case Some(slo) => Math.max(speedLimit.vvhTimeStamp, slo.vvhTimeStamp)
      case _ => speedLimit.vvhTimeStamp
    }
  }
  /**
    * Removes obsoleted mvalue adjustments and side code adjustments from the list
    * @param roadLink
    * @param speedLimits
    * @param changeSet
    * @return
    */
  private def clean(roadLink: RoadLink, speedLimits: Seq[SpeedLimit], changeSet: ChangeSet): (Seq[SpeedLimit], ChangeSet) = {
    /**
      * Remove adjustments that were overwritten later (new version appears later in the sequence)
      * @param adj list of adjustments
      * @return list of adjustment final values
      */
    def prune(adj: Seq[MValueAdjustment]): Seq[MValueAdjustment] = {
      if (adj.isEmpty)
        return adj
      adj.tail.exists(a => a.assetId == adj.head.assetId) match {
        case true => prune(adj.tail)
        case false => Seq(adj.head) ++ prune(adj.tail)
      }
    }
    /**
      * Remove side code adjustments that were overwritten
      * @param adj original list
      * @return list of final values
      */
    def pruneSideCodes(adj: Seq[SideCodeAdjustment]): Seq[SideCodeAdjustment] = {
      if (adj.isEmpty)
        return adj
      adj.tail.exists(a => a.assetId == adj.head.assetId) match {
        case true => pruneSideCodes(adj.tail)
        case false => Seq(adj.head) ++ pruneSideCodes(adj.tail)
      }
    }
    val droppedIds = changeSet.droppedAssetIds
    val adjustments = prune(changeSet.adjustedMValues.filterNot(a => droppedIds.contains(a.assetId)))
    val sideAdjustments = pruneSideCodes(changeSet.adjustedSideCodes.filterNot(a => droppedIds.contains(a.assetId)))
    (speedLimits, changeSet.copy(droppedAssetIds = droppedIds -- Set(0), adjustedMValues = adjustments, adjustedSideCodes = sideAdjustments))
  }

  def fillTopology(roadLinks: Seq[RoadLink], speedLimits: Map[Long, Seq[SpeedLimit]]): (Seq[SpeedLimit], ChangeSet) = {
    val fillOperations: Seq[(RoadLink, Seq[SpeedLimit], ChangeSet) => (Seq[SpeedLimit], ChangeSet)] = Seq(
      dropSegmentsOutsideGeometry,
      combine,
      fuse,
      adjustSegmentMValues,
      capToGeometry,
      adjustLopsidedLimit,
      adjustSideCodeOnOneWayLink,
      dropShortLimits,
      fillHoles,
      clean
    )
      // TODO: Do not create dropped asset ids but mark them expired when they are no longer valid or relevant
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

  /**
    * For debugging; print speed limit relevant data
    * @param speedLimit speedlimit to print
    */
  def printSL(speedLimit: SpeedLimit) = {
    val ids = "%d (%d)".format(speedLimit.id, speedLimit.linkId)
    val dir = speedLimit.sideCode match {
      case SideCode.BothDirections => "⇅"
      case SideCode.TowardsDigitizing => "↑"
      case SideCode.AgainstDigitizing => "↓"
      case _ => "?"
    }
    val details = "%d %.4f %.4f %s".format(speedLimit.value.getOrElse(NumericValue(0)).value, speedLimit.startMeasure, speedLimit.endMeasure, speedLimit.vvhTimeStamp.toString)
    if (speedLimit.expired) {
      println("N/A")
    } else {
      println("%s %s %s".format(ids, dir, details))
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
    if (GeometryUtils.isDirectionChangeProjection(projection)) {
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

    val geometry = GeometryUtils.truncateGeometry3D(
      Seq(GeometryUtils.calculatePointFromLinearReference(to.geometry, newStart).getOrElse(to.geometry.head),
        GeometryUtils.calculatePointFromLinearReference(to.geometry, newEnd).getOrElse(to.geometry.last)),
      0, to.length)

    SpeedLimit(id = assetId, linkId = newLinkId, sideCode = newSideCode, trafficDirection = newDirection,
      asset.value, geometry, newStart, newEnd,
      modifiedBy = asset.modifiedBy,
      modifiedDateTime = asset.modifiedDateTime, createdBy = asset.createdBy, createdDateTime = asset.createdDateTime,
      vvhTimeStamp = projection.vvhTimeStamp, geomModifiedDate = None,  municipalityCode = None, linkSource = asset.linkSource
    )
  }

  case class SegmentPiece(assetId: Long, startM: Double, endM: Double, sideCode: SideCode, value: Option[NumericValue])
}

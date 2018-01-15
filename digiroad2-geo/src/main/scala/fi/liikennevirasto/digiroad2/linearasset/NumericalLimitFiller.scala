package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.GeometryUtils.Projection
import fi.liikennevirasto.digiroad2.asset.{SideCode, TrafficDirection}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, MValueAdjustment, SideCodeAdjustment}
import fi.liikennevirasto.digiroad2.GeometryUtils
import org.joda.time.DateTime

object NumericalLimitFiller {
  val AllowedTolerance = 0.5
  val MaxAllowedError = 0.01
  val MinAllowedLength = 2.0

  private def modifiedSort(left: PersistedLinearAsset, right: PersistedLinearAsset) = {
    val leftStamp = left.modifiedDateTime.orElse(left.createdDateTime)
    val rightStamp = right.modifiedDateTime.orElse(right.createdDateTime)
    (leftStamp, rightStamp) match {
      case (Some(l), Some(r)) => l.isAfter(r)
      case (None, Some(r)) => false
      case (Some(l), None) => true
      case (None, None) => true
    }
  }

  private def adjustAsset(asset: PersistedLinearAsset, roadLink: RoadLink): (PersistedLinearAsset, Seq[MValueAdjustment]) = {
    val roadLinkLength = GeometryUtils.geometryLength(roadLink.geometry)
    val adjustedStartMeasure = if (asset.startMeasure < AllowedTolerance && asset.startMeasure > MaxAllowedError) Some(0.0) else None
    val endMeasureDifference: Double = roadLinkLength - asset.endMeasure
    val adjustedEndMeasure = if (endMeasureDifference < AllowedTolerance && endMeasureDifference > MaxAllowedError) Some(roadLinkLength) else None
    val mValueAdjustments = (adjustedStartMeasure, adjustedEndMeasure) match {
      case (None, None) => Nil
      case (s, e)       => Seq(MValueAdjustment(asset.id, asset.linkId, s.getOrElse(asset.startMeasure), e.getOrElse(asset.endMeasure)))
    }
    val adjustedAsset = asset.copy(
      startMeasure = adjustedStartMeasure.getOrElse(asset.startMeasure),
      endMeasure = adjustedEndMeasure.getOrElse(asset.endMeasure))
    (adjustedAsset, mValueAdjustments)
  }

  private def extendHead(mStart: Double, candidates: Seq[PersistedLinearAsset], changeSet: ChangeSet): (Double, ChangeSet) = {
    val maybeAsset = candidates.find(a => Math.abs(a.endMeasure - mStart) < MaxAllowedError)
    maybeAsset match {
      case Some(extension) =>
        extendHead(extension.startMeasure, candidates.diff(Seq(extension)),
          changeSet.copy(expiredAssetIds = changeSet.expiredAssetIds ++ Set(extension.id)))
      case None => (mStart, changeSet)

    }
  }
  private def extendTail(mEnd: Double, candidates: Seq[PersistedLinearAsset], changeSet: ChangeSet): (Double, ChangeSet) = {
    val maybeAsset = candidates.find(a => Math.abs(a.startMeasure - mEnd) < MaxAllowedError)
    maybeAsset match {
      case Some(extension) =>
        extendTail(extension.endMeasure, candidates.diff(Seq(extension)),
          changeSet.copy(expiredAssetIds = changeSet.expiredAssetIds ++ Set(extension.id)))
      case None => (mEnd, changeSet)
    }
  }

  /**
    * Try to find assets that start/end adjacent to the keeper asset and merge them to the keeper
    *
    * @param asset keeper
    * @param candidates merging candidates
    * @param changeSet current changeset
    * @return updated keeper asset and updated changeset
    */
  private def extend(asset: PersistedLinearAsset, candidates: Seq[PersistedLinearAsset], changeSet: ChangeSet): (PersistedLinearAsset, ChangeSet) = {
    if (candidates.isEmpty) {
      (asset, changeSet)
    } else {
      val (mStart, changeSetH) = extendHead(asset.startMeasure, candidates.filter(a =>
        (a.endMeasure < asset.startMeasure + MaxAllowedError) && a.value.equals(asset.value)), changeSet)
      val (mEnd, changeSetHT) = extendTail(asset.endMeasure, candidates.filter(a =>
        (a.startMeasure > asset.endMeasure - MaxAllowedError) && a.value.equals(asset.value)), changeSetH)
      val mValueAdjustments = (mStart == asset.startMeasure, mEnd == asset.endMeasure) match {
        case (true, true) => Nil
        case (s, e) => Seq(MValueAdjustment(asset.id, asset.linkId, mStart, mEnd))
      }
      val adjustedAsset = asset.copy(
        startMeasure = mStart,
        endMeasure = mEnd)
      (adjustedAsset, changeSetHT.copy(adjustedMValues = changeSet.adjustedMValues ++ mValueAdjustments))
    }
  }

  /**
    * Adjust two way segments and combine them if possible using tail recursion functions above
    *
    * @param roadLink
    * @param assets
    * @param changeSet
    * @return
    */
  def adjustAnyWaySegments(roadLink: RoadLink, sidecode: Int, assets: Seq[PersistedLinearAsset], changeSet: ChangeSet): (Seq[PersistedLinearAsset], ChangeSet) = {

        val anyWaySegments = assets.filter(_.sideCode == sidecode).sortWith(modifiedSort)
        if (anyWaySegments.length == 1 && assets.forall(_.sideCode == sidecode)) {
          val asset = anyWaySegments.head
          val (adjustedAsset, mValueAdjustments) = adjustAsset(asset, roadLink)
          (Seq(adjustedAsset), changeSet.copy(adjustedMValues = changeSet.adjustedMValues ++ mValueAdjustments))
        } else {
          if (anyWaySegments.length > 1) {
            val asset = anyWaySegments.head
            val rest = anyWaySegments.tail
            val (updatedAsset, newChangeSet) = extend(asset, rest, changeSet)
            val (adjustedAsset, mValueAdjustments) = adjustAsset(updatedAsset, roadLink)
            (rest.filterNot(p => newChangeSet.expiredAssetIds.contains(p.id)) ++ Seq(adjustedAsset),
              newChangeSet.copy(adjustedMValues = newChangeSet.adjustedMValues ++ mValueAdjustments))
          } else {
            (assets, changeSet)
          }
        }
  }

  def adjustSegments(roadLink: RoadLink, assets: Seq[PersistedLinearAsset], changeSet: ChangeSet): (Seq[PersistedLinearAsset], ChangeSet) = {
     assets.groupBy(_.sideCode).foldLeft((Seq[PersistedLinearAsset](), changeSet)) {
           case ((resultAssets, change),(sidecode, assets)) =>
     val (adjustAssets, changes) = adjustAnyWaySegments(roadLink, sidecode, assets, change)
             (resultAssets++adjustAssets, changes)
    }
  }

  private def expireSegmentsOutsideGeometry(roadLink: RoadLink, assets: Seq[PersistedLinearAsset], changeSet: ChangeSet): (Seq[PersistedLinearAsset], ChangeSet) = {
    val (segmentsWithinGeometry, segmentsOutsideGeometry) = assets.partition(_.startMeasure < roadLink.length)
    val expiredAssetIds = segmentsOutsideGeometry.map(_.id).toSet
    (segmentsWithinGeometry, changeSet.copy(expiredAssetIds = changeSet.expiredAssetIds ++ expiredAssetIds))
  }

  private def capSegmentsThatOverflowGeometry(roadLink: RoadLink, assets: Seq[PersistedLinearAsset], changeSet: ChangeSet): (Seq[PersistedLinearAsset], ChangeSet) = {
    val (validSegments, overflowingSegments) = assets.partition(_.endMeasure <= roadLink.length + MaxAllowedError)
    val cappedSegments = overflowingSegments.map { x => x.copy(endMeasure = roadLink.length)}
    val mValueAdjustments = cappedSegments.map { x => MValueAdjustment(x.id, x.linkId, x.startMeasure, x.endMeasure) }
    (validSegments ++ cappedSegments, changeSet.copy(adjustedMValues = changeSet.adjustedMValues ++ mValueAdjustments))
  }

  private def adjustSegmentSideCodes(roadLink: RoadLink, segments: Seq[PersistedLinearAsset], changeSet: ChangeSet): (Seq[PersistedLinearAsset], ChangeSet) = {
    val oneWayTrafficDirection =
      (roadLink.trafficDirection == TrafficDirection.TowardsDigitizing) ||
        (roadLink.trafficDirection == TrafficDirection.AgainstDigitizing)

    if (!oneWayTrafficDirection) {
      (segments, changeSet)
    } else {
      val (twoSided, oneSided) = segments.partition { s => s.sideCode == SideCode.BothDirections.value }
      val adjusted = oneSided.map { s => (s.copy(sideCode = SideCode.BothDirections.value), SideCodeAdjustment(s.id, SideCode.BothDirections)) }
      (twoSided ++ adjusted.map(_._1), changeSet.copy(adjustedSideCodes = changeSet.adjustedSideCodes ++ adjusted.map(_._2)))
    }
  }

  private def generateTwoSidedNonExistingLinearAssets(typeId: Int)(roadLink: RoadLink, segments: Seq[PersistedLinearAsset], changeSet: ChangeSet): (Seq[PersistedLinearAsset], ChangeSet) = {
    val lrmPositions: Seq[(Double, Double)] = segments.map { x => (x.startMeasure, x.endMeasure) }
    val remainders = lrmPositions.foldLeft(Seq((0.0, roadLink.length)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > 0.5}
    val generated = remainders.map { segment =>
      PersistedLinearAsset(0L, roadLink.linkId, 1, None, segment._1, segment._2, None, None, None, None, false, typeId, 0, None, roadLink.linkSource, None, None)
    }
    (segments ++ generated, changeSet)
  }

  private def generateOneSidedNonExistingLinearAssets(sideCode: SideCode, typeId: Int)(roadLink: RoadLink, segments: Seq[PersistedLinearAsset], changeSet: ChangeSet): (Seq[PersistedLinearAsset], ChangeSet) = {
    val generated = if (roadLink.trafficDirection == TrafficDirection.BothDirections) {
      val lrmPositions: Seq[(Double, Double)] = segments
        .filter { s => s.sideCode == sideCode.value || s.sideCode == SideCode.BothDirections.value }
        .map { x => (x.startMeasure, x.endMeasure) }
      val remainders = lrmPositions.foldLeft(Seq((0.0, roadLink.length)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > 0.5 }
      remainders.map { segment =>
        PersistedLinearAsset(0L, roadLink.linkId, sideCode.value, None, segment._1, segment._2, None, None, None, None, false, typeId, 0, None, roadLink.linkSource, None, None)
      }
    } else {
      Nil
    }
    (segments ++ generated, changeSet)
  }

  private def combine(roadLink: RoadLink, segments: Seq[PersistedLinearAsset], changeSet: ChangeSet): (Seq[PersistedLinearAsset], ChangeSet) = {

    def replaceUnknownAssetIds(asset: PersistedLinearAsset, pseudoId: Long) = {
      asset.id match {
        case 0 => asset.copy(id = pseudoId)
        case _ => asset
      }
    }
    /**
      * Pick the latest asset for each single piece and create SegmentPiece objects for those
      */
    def squash(startM: Double, endM: Double, segments: Seq[PersistedLinearAsset]): Seq[SegmentPiece] = {
      val sl = segments.filter(sl => sl.startMeasure <= startM && sl.endMeasure >= endM)
      val a = sl.filter(sl => sl.sideCode.equals(SideCode.AgainstDigitizing.value) || sl.sideCode.equals(SideCode.BothDirections.value)).sortWith(modifiedSort).headOption
      val t = sl.filter(sl => sl.sideCode.equals(SideCode.TowardsDigitizing.value) || sl.sideCode.equals(SideCode.BothDirections.value)).sortWith(modifiedSort).headOption

      (a, t) match {
        case (Some(x),Some(y)) => Seq(SegmentPiece(x.id, startM, endM, SideCode.AgainstDigitizing, x.value), SegmentPiece(y.id, startM, endM, SideCode.TowardsDigitizing, y.value))
        case (Some(x),None) => Seq(SegmentPiece(x.id, startM, endM, SideCode.AgainstDigitizing, x.value))
        case (None,Some(y)) => Seq(SegmentPiece(y.id, startM, endM, SideCode.TowardsDigitizing, y.value))
        case _ => Seq()
      }
    }
    /**
      * Combine sides together if matching m-values and segment values.
      * @param segmentPieces Sequence of one or two segment pieces (guaranteed by squash operation)
      * @param segments All segments on road link
      * @return Sequence of segment pieces (1 or 2 segment pieces in sequence)
      */
    def combineEqualValues(segmentPieces: Seq[SegmentPiece], segments: Seq[PersistedLinearAsset]): Seq[SegmentPiece] = {
      def chooseSegment(seg1 :SegmentPiece, seg2: SegmentPiece): Seq[SegmentPiece] = {
        val sl1 = segments.find(_.id == seg1.assetId).get
        val sl2 = segments.find(_.id == seg2.assetId).get
        if (sl1.startMeasure.equals(sl2.startMeasure) && sl1.endMeasure.equals(sl2.endMeasure)) {
          val winner = segments.filter(l => l.id == seg1.assetId || l.id == seg2.assetId).sortBy(s =>
            s.endMeasure - s.startMeasure).head
          Seq(segmentPieces.head.copy(assetId = winner.id, sideCode = SideCode.BothDirections))
        } else {
          segmentPieces
        }
      }

      if (segmentPieces.size < 2)
        return segmentPieces

      val seg1 = segmentPieces.head
      val seg2 = segmentPieces.last
      (seg1.value, seg2.value) match {
        case (Some(v1), Some(v2)) =>
          if (v1.equals(v2)) {
            chooseSegment(seg1, seg2)
          } else
            segmentPieces
        case (Some(v1), None) => Seq(segmentPieces.head.copy(sideCode = SideCode.BothDirections))
        case (None, Some(v2)) => Seq(segmentPieces.last.copy(sideCode = SideCode.BothDirections))
        case (None, None) =>
          chooseSegment(seg1, seg2)
        case _ => segmentPieces
      }
    }

    /**
      * Take pieces of segment and combine two of them if they are related to the similar segment and extend each other
      * (matching startM, endM, value and sidecode)
      * Return altered PersistedLinearAsset as well as the pieces that could not be added to that segments.
      * @param segmentPieces
      * @param segments
      * @return
      */
    def extendOrDivide(segmentPieces: Seq[SegmentPiece], segments: PersistedLinearAsset): (PersistedLinearAsset, Seq[SegmentPiece]) = {
      val sorted = segmentPieces.sortBy(_.startM)
      val current = sorted.head
      val rest = sorted.tail
      if (rest.nonEmpty) {
        val next = rest.find(sp => sp.startM == current.endM && sp.sideCode == current.sideCode &&
          sp.value.getOrElse(None) == current.value)
        if (next.nonEmpty) {
          return extendOrDivide(Seq(current.copy(endM = next.get.endM)) ++ rest.filterNot(sp => sp.equals(next.get)), segments)
        }
      }
      (segments.copy(sideCode = current.sideCode.value, startMeasure = current.startM, endMeasure = current.endM), rest)
    }

    /**
      * Creates Numerical limits from orphaned segments (segments originating from a linear assets but no longer connected
      * to them)
      * @param origin Segments Lanes that was split by overwriting a segment piece(s)
      * @param orphans List of orphaned segment pieces
      * @return New segments Lanes for orphaned segment pieces
      */
    def generateLimitsForOrphanSegments(origin: PersistedLinearAsset, orphans: Seq[SegmentPiece]): Seq[PersistedLinearAsset] = {
      if (orphans.nonEmpty) {
        val segmentPiece = orphans.sortBy(_.startM).head
        if (orphans.tail.nonEmpty) {
          // Try to extend this segment as far as possible: if SegmentPieces are consecutive produce just one Segments
          val t = extendOrDivide(orphans, origin.copy(startMeasure = segmentPiece.startM, endMeasure = segmentPiece.endM, sideCode = segmentPiece.sideCode.value))
          // t now has a Numerical limit and any orphans it left behind: recursively call this method again
          return Seq(t._1.copy(id = 0L)) ++ generateLimitsForOrphanSegments(origin, t._2)
        }
        // Only orphan in the list, create a new segment Lane for it
        val sl = origin.copy(id = 0L, startMeasure = segmentPiece.startM, endMeasure = segmentPiece.endM, sideCode = segmentPiece.sideCode.value)
        return Seq(sl)
      }
      Seq()
    }

    /**
      * Make sure that no two segments lanes share the same id, rewrite the ones appearing later with id=0
      * @param toProcess List of Numerical limits to go thru
      * @param processed List of processed Numerical limits
      * @return List of segments with unique or zero ids
      */
    def cleanNumericalLimitIds(toProcess: Seq[PersistedLinearAsset], processed: Seq[PersistedLinearAsset]): Seq[PersistedLinearAsset] = {
      val (current, rest) = (toProcess.head, toProcess.tail)
      val modified = processed.exists(_.id == current.id) || current.id < 0L match {
        case true => current.copy(id = 0L)
        case _ => current
      }
      if (rest.nonEmpty) {
        cleanNumericalLimitIds(rest, processed ++ Seq(modified))
      } else {
        processed ++ Seq(modified)
      }
    }
    val assets = segments.zipWithIndex.map(n => replaceUnknownAssetIds(n._1, 0L-n._2))
    val pointsOfInterest = (assets.map(_.startMeasure) ++ assets.map(_.endMeasure)).distinct.sorted
    if (pointsOfInterest.length < 2)
      return (assets, changeSet)

    val pieces = pointsOfInterest.zip(pointsOfInterest.tail)
    val segmentPieces = pieces.flatMap(p => squash(p._1, p._2, assets)).sortBy(_.assetId)
    val combinedSegmentPieces = segmentPieces.groupBy(_.startM).flatMap(n => combineEqualValues(n._2, assets))
    val segmentsAndOrphanPieces = combinedSegmentPieces.groupBy(_.assetId).map(n => extendOrDivide(n._2.toSeq, assets.find(_.id == n._1).get))
    val combinedSegment = segmentsAndOrphanPieces.keys.toSeq
    val newSegments = combinedSegment.flatMap(sl => generateLimitsForOrphanSegments(sl, segmentsAndOrphanPieces.getOrElse(sl, Seq()).sortBy(_.startM)))

    val changedSideCodes = combinedSegment.filter(cl =>
      assets.exists(sl => sl.id == cl.id && !sl.sideCode.equals(cl.sideCode))).
      map(sl => SideCodeAdjustment(sl.id, SideCode(sl.sideCode)))
    val resultingNumericalLimits = combinedSegment ++ newSegments
    val expiredIds = assets.map(_.id).toSet.--(resultingNumericalLimits.map(_.id).toSet)

    val returnSegments = cleanNumericalLimitIds(resultingNumericalLimits, Seq())
    (returnSegments, changeSet.copy(expiredAssetIds = changeSet.expiredAssetIds ++ expiredIds, adjustedSideCodes = changeSet.adjustedSideCodes ++ changedSideCodes))

  }

  case class SegmentPiece(assetId: Long, startM: Double, endM: Double, sideCode: SideCode, value: Option[Value])

  private def toLinearAsset(dbAssets: Seq[PersistedLinearAsset], roadLink: RoadLink): Seq[PieceWiseLinearAsset] = {
    dbAssets.map { dbAsset =>
      val points = GeometryUtils.truncateGeometry3D(roadLink.geometry, dbAsset.startMeasure, dbAsset.endMeasure)
      val endPoints = GeometryUtils.geometryEndpoints(points)
      PieceWiseLinearAsset(
        dbAsset.id, dbAsset.linkId, SideCode(dbAsset.sideCode), dbAsset.value, points, dbAsset.expired, dbAsset.startMeasure,
        dbAsset.endMeasure, Set(endPoints._1, endPoints._2), dbAsset.modifiedBy, dbAsset.modifiedDateTime, dbAsset.createdBy,
        dbAsset.createdDateTime, dbAsset.typeId, roadLink.trafficDirection, dbAsset.vvhTimeStamp, dbAsset.geomModifiedDate,
        dbAsset.linkSource, roadLink.administrativeClass,  verifiedBy = dbAsset.verifiedBy, verifiedDate = dbAsset.verifiedDate)
    }
  }

  private def toSegment(persistedLinearAsset: PersistedLinearAsset) = {
    (persistedLinearAsset.startMeasure, persistedLinearAsset.endMeasure)
  }

  private def sortNewestFirst(assets: Seq[PersistedLinearAsset]) = {
    assets.sortBy(s => 0L-s.modifiedDateTime.getOrElse(s.createdDateTime.getOrElse(DateTime.now())).getMillis)
  }

  /**
    * Remove recursively all overlapping linear assets or adjust the measures if the overlap is smaller than the allowed tolerance.
    * Keeping the order of the sorted sequence parameter.
    * 1) Find a overlapped linear asset between the first linear asset of the sorted sequence and the tail
    *   a) Split the overlapped linear asset and pick the linear assets minor than the allowed tolerance
    *     If
    *       the side code of the first linear asset and the overlaped linear asset are equal
    *     OR
    *       the first linear asset have both directions
    *
    * @param sortedAssets Sorted sequence of Persisted Liner Assets
    * @param result Recursive result of each iteration
    * @return Sequence without overlapping linear assets
    */
  private def expireOverlappedRecursively(sortedAssets: Seq[PersistedLinearAsset], result: Seq[PersistedLinearAsset]): Seq[PersistedLinearAsset] = {
    val keeperOpt = sortedAssets.headOption
    if (keeperOpt.nonEmpty) {
      val keeper = keeperOpt.get
      val (overlapping) = sortedAssets.tail.flatMap(asset => GeometryUtils.overlap(toSegment(keeper), toSegment(asset)) match {
        case Some(overlap) =>
          if (keeper.sideCode == asset.sideCode || keeper.sideCode == SideCode.BothDirections.value) {
            Seq(
              asset.copy(startMeasure = asset.startMeasure, endMeasure = overlap._1),
              asset.copy(id = 0L, startMeasure = overlap._2, endMeasure = asset.endMeasure)
            ).filter(a => a.endMeasure - a.startMeasure >= AllowedTolerance)
          } else {
            Seq(asset)
          }
        case None =>
          Seq(asset)
      }
      )
      expireOverlappedRecursively(overlapping, result ++ Seq(keeper))
    } else {
      result
    }
  }

  private def expireOverlappingSegments(roadLink: RoadLink, segments: Seq[PersistedLinearAsset], changeSet: ChangeSet): (Seq[PersistedLinearAsset], ChangeSet) = {
    def isChanged(p : PersistedLinearAsset) : Boolean = {
      segments.exists(s => p.id == s.id && (p.startMeasure != s.startMeasure || p.endMeasure != s.endMeasure))
    }

    if (segments.size >= 2) {
      val sortedSegments = expireOverlappedRecursively(sortNewestFirst(segments), Seq())
      val alteredSegments = sortedSegments.filterNot(_.id == 0)

      // Creates for each linear asset a new MValueAdjustment if the start or end measure have changed
      val mValueChanges = alteredSegments.filter(isChanged).
        map(s => MValueAdjustment(s.id, s.linkId, s.startMeasure, s.endMeasure))

      val expiredIds = segments.map(_.id).toSet -- alteredSegments.map(_.id) ++ changeSet.expiredAssetIds
      (sortedSegments,
        changeSet.copy(adjustedMValues = (changeSet.adjustedMValues ++ mValueChanges).filterNot(mvc => expiredIds.contains(mvc.assetId)),
          expiredAssetIds = expiredIds))
    } else
      (segments, changeSet)
  }

  private def dropShortSegments(roadLink: RoadLink, assets: Seq[PersistedLinearAsset], changeSet: ChangeSet): (Seq[PersistedLinearAsset], ChangeSet) = {
    val (shortSegments, linearSegments) = assets.partition(a => (a.endMeasure - a.startMeasure) < MinAllowedLength && roadLink.length >= MinAllowedLength )
    val droppedAssetIds = shortSegments.map(_.id).toSet
    (linearSegments, changeSet.copy(droppedAssetIds = changeSet.droppedAssetIds ++ droppedAssetIds))
  }

  private def latestTimestamp(linearAsset: PieceWiseLinearAsset, linearAssetO: Option[PieceWiseLinearAsset]) = {
    linearAssetO match {
      case Some(lao) => Math.max(linearAsset.vvhTimeStamp, lao.vvhTimeStamp)
      case _ => linearAsset.vvhTimeStamp
    }
  }

  private def modifiedSort(left: PieceWiseLinearAsset, right: PieceWiseLinearAsset) = {
    val leftStamp = left.modifiedDateTime.orElse(left.createdDateTime)
    val rightStamp = right.modifiedDateTime.orElse(right.createdDateTime)
    (leftStamp, rightStamp) match {
      case (Some(l), Some(r)) => l.isAfter(r)
      case (None, Some(r)) => false
      case (Some(l), None) => true
      case (None, None) => true
    }
  }

  /**
    * After other change operations connect linear assets that have same values and side codes if they extend each other
    * @param roadLink Road link we are working on
    * @param linearAssets List of linear assets
    * @param changeSet Changes done previously
    * @return List of linear assets and a change set
    */
  private def fuse(roadLink: RoadLink, linearAssets: Seq[PersistedLinearAsset], changeSet: ChangeSet): (Seq[PersistedLinearAsset], ChangeSet) = {
    val sortedList = linearAssets.sortBy(_.startMeasure)
    if (linearAssets.nonEmpty) {
      val origin = sortedList.head
      val target = sortedList.tail.find(sl => Math.abs(sl.startMeasure - origin.endMeasure) < 0.1 &&
          sl.value == origin.value && sl.sideCode.equals(origin.sideCode))
      if (target.nonEmpty) {
        // pick id if it already has one regardless of which one is newer
        val toBeFused = Seq(origin, target.get).sortWith(modifiedSort)
        val newId = toBeFused.find(_.id > 0).map(_.id).getOrElse(0L)
        val roadLength = GeometryUtils.geometryLength(roadLink.geometry)
        val modified =  toBeFused.head.copy(id = newId, startMeasure = origin.startMeasure, endMeasure = target.get.endMeasure)
        val expiredId = Set(origin.id, target.get.id) -- Set(modified.id, 0L) // never attempt to expire id zero
        val mValueAdjustment = Seq(changeSet.adjustedMValues.find(a => a.assetId == modified.id) match {
          case Some(adjustment) => adjustment.copy(startMeasure = modified.startMeasure, endMeasure = modified.endMeasure)
          case _ => MValueAdjustment(modified.id, modified.linkId, modified.startMeasure, modified.endMeasure)
        })
        // Replace origin and target with this new item in the list and recursively call itself again
        fuse(roadLink, Seq(modified) ++ sortedList.tail.filterNot(sl => Set(origin, target.get).contains(sl)),
          changeSet.copy(expiredAssetIds = changeSet.expiredAssetIds ++ expiredId, adjustedMValues = changeSet.adjustedMValues.filter(a => a.assetId > 0 && a.assetId != modified.id) ++ mValueAdjustment))
      } else {
        val fused = fuse(roadLink, sortedList.tail, changeSet)
        (Seq(origin) ++ fused._1, fused._2)
      }
    } else {
      (linearAssets, changeSet)
    }
  }

  private def adjustAssets(roadLink: RoadLink, linearAssets: Seq[PersistedLinearAsset], changeSet: ChangeSet): (Seq[PersistedLinearAsset], ChangeSet) = {
    linearAssets.foldLeft((Seq[PersistedLinearAsset](), changeSet)){
      case ((resultAssets, change),linearAsset) =>
        val (asset, adjustmentsMValues) = adjustAsset(linearAsset, roadLink)
        (resultAssets ++ Seq(asset), change.copy(adjustedMValues = change.adjustedMValues ++ adjustmentsMValues))
    }
  }

  def fillTopology(topology: Seq[RoadLink], linearAssets: Map[Long, Seq[PersistedLinearAsset]], typeId: Int): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val fillOperations: Seq[(RoadLink, Seq[PersistedLinearAsset], ChangeSet) => (Seq[PersistedLinearAsset], ChangeSet)] = Seq(
      expireSegmentsOutsideGeometry,
      dropShortSegments,
      capSegmentsThatOverflowGeometry,
      expireOverlappingSegments,
      combine,
      fuse,
      adjustAssets,
      adjustSegmentSideCodes,
      generateTwoSidedNonExistingLinearAssets(typeId),
      generateOneSidedNonExistingLinearAssets(SideCode.TowardsDigitizing, typeId),
      generateOneSidedNonExistingLinearAssets(SideCode.AgainstDigitizing, typeId)
    )

    topology.foldLeft(Seq.empty[PieceWiseLinearAsset], ChangeSet(Set.empty, Nil, Nil, Set.empty)) { case (acc, roadLink) =>
      val (existingAssets, changeSet) = acc
      val assetsOnRoadLink = linearAssets.getOrElse(roadLink.linkId, Nil)

      val (adjustedAssets, assetAdjustments) = fillOperations.foldLeft(assetsOnRoadLink, changeSet) { case ((currentSegments, currentAdjustments), operation) =>
        operation(roadLink, currentSegments, currentAdjustments)
      }

      (existingAssets ++ toLinearAsset(adjustedAssets, roadLink), assetAdjustments)
    }
  }

  private def calculateNewMValuesAndSideCode(asset: PersistedLinearAsset, projection: Projection, roadLinkLength: Double) = {
    val oldLength = projection.oldEnd - projection.oldStart
    val newLength = projection.newEnd - projection.newStart

    // Test if the direction has changed -> side code will be affected, too
    if (GeometryUtils.isDirectionChangeProjection(projection)) {
      val newSideCode = SideCode.apply(asset.sideCode) match {
        case (SideCode.AgainstDigitizing) => SideCode.TowardsDigitizing.value
        case (SideCode.TowardsDigitizing) => SideCode.AgainstDigitizing.value
        case _ => asset.sideCode
      }
      val newStart = projection.newStart - (asset.endMeasure - projection.oldStart) * Math.abs(newLength / oldLength)
      val newEnd = projection.newEnd - (asset.startMeasure - projection.oldEnd) * Math.abs(newLength / oldLength)
      // Test if asset is affected by projection
      if (asset.endMeasure <= projection.oldStart || asset.startMeasure >= projection.oldEnd)
        (asset.startMeasure, asset.endMeasure, newSideCode)
      else
        (Math.min(roadLinkLength, Math.max(0.0, newStart)), Math.max(0.0, Math.min(roadLinkLength, newEnd)), newSideCode)
    } else {
      val newStart = projection.newStart + (asset.startMeasure - projection.oldStart) * Math.abs(newLength / oldLength)
      val newEnd = projection.newEnd + (asset.endMeasure - projection.oldEnd) * Math.abs(newLength / oldLength)
      // Test if asset is affected by projection
      if (asset.endMeasure <= projection.oldStart || asset.startMeasure >= projection.oldEnd) {
        (asset.startMeasure, asset.endMeasure, asset.sideCode)
      } else {
        (Math.min(roadLinkLength, Math.max(0.0, newStart)), Math.max(0.0, Math.min(roadLinkLength, newEnd)), asset.sideCode)
      }
    }
  }

  def projectLinearAsset(asset: PersistedLinearAsset, to: RoadLink, projection: Projection) = {
    val newLinkId = to.linkId
    val assetId = asset.linkId match {
      case to.linkId => asset.id
      case _ => 0
    }
    val (newStart, newEnd, newSideCode) = calculateNewMValuesAndSideCode(asset, projection, to.length)

    PersistedLinearAsset(id = assetId, linkId = newLinkId, sideCode = newSideCode,
      value = asset.value, startMeasure = newStart, endMeasure = newEnd,
      createdBy = asset.createdBy, createdDateTime = asset.createdDateTime, modifiedBy = asset.modifiedBy,
      modifiedDateTime = asset.modifiedDateTime, expired = false, typeId = asset.typeId,
      vvhTimeStamp = projection.vvhTimeStamp, geomModifiedDate = None, linkSource = asset.linkSource, verifiedBy = asset.verifiedBy, verifiedDate = asset.verifiedDate
    )
  }
}

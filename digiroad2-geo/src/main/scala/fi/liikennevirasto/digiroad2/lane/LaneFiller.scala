package fi.liikennevirasto.digiroad2.lane

import fi.liikennevirasto.digiroad2.GeometryUtils
import fi.liikennevirasto.digiroad2.GeometryUtils.{Projection, areMeasuresCloseEnough}
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.lane.LaneFiller._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import org.joda.time.DateTime
import fi.liikennevirasto.digiroad2.Point


object LaneFiller {

  // TODO MValueAdjustment ja SideCodeAdjustment korvaaminen LanePositionAdjustment fillTopologyssä.
  case class MValueAdjustment(laneId: Long, linkId: String, startMeasure: Double, endMeasure: Double)
  case class SideCodeAdjustment(laneId: Long, sideCode: SideCode)
  case class LaneSplit(laneToCreate: PersistedLane, oldLane: PersistedLane)
  case class LanePositionAdjustment(laneId: Long, linkId: String, startMeasure: Double, endMeasure: Double, sideCode: SideCode)
  case class ChangeSet(adjustedMValues: Seq[MValueAdjustment] = Seq.empty[MValueAdjustment],
                       adjustedSideCodes: Seq[SideCodeAdjustment] = Seq.empty[SideCodeAdjustment],
                       positionAdjustments: Seq[LanePositionAdjustment] = Seq.empty[LanePositionAdjustment],
                       expiredLaneIds: Set[Long] = Set.empty[Long],
                       generatedPersistedLanes: Seq[PersistedLane] = Seq.empty[PersistedLane],
                       splitLanes: Seq[LaneSplit] = Seq.empty[LaneSplit]) {
    def isEmpty: Boolean = {
      this.positionAdjustments.isEmpty &&
        this.expiredLaneIds.isEmpty &&
        this.generatedPersistedLanes.isEmpty &&
        this.splitLanes.isEmpty
    }
  }

  def combineChangeSets: (ChangeSet, ChangeSet) => ChangeSet = (changeSet1, changeSet2) =>
    changeSet1.copy(positionAdjustments = changeSet1.positionAdjustments ++ changeSet2.positionAdjustments,
      expiredLaneIds = changeSet1.expiredLaneIds ++ changeSet2.expiredLaneIds,
      generatedPersistedLanes = changeSet1.generatedPersistedLanes ++ changeSet2.generatedPersistedLanes,
      splitLanes = changeSet1.splitLanes ++ changeSet2.splitLanes
    )

  case class SegmentPiece(laneId: Long, startM: Double, endM: Double, sideCode: SideCode, value: Seq[LaneProperty])
}

class LaneFiller {
  val AllowedTolerance = 2.0
  val MaxAllowedError = 0.01
  val MinAllowedLength = 2.0

  def getOperations(geometryChanged: Boolean) = {
    val fillOperations: Seq[(RoadLink, Seq[PersistedLane], ChangeSet ) => (Seq[PersistedLane], ChangeSet)] = Seq(
      expireSegmentsOutsideGeometry,
      capSegmentsThatOverflowGeometry,
      expireOverlappingSegments,
      combine,
      fuse,
      dropShortSegments,
      adjustAssets
    )

    val adjustmentOperations: Seq[(RoadLink, Seq[PersistedLane], ChangeSet ) => (Seq[PersistedLane], ChangeSet)] = Seq(
      combine,
      fuse,
      dropShortSegments,
      adjustAssets
    )

    if(geometryChanged) fillOperations
    else adjustmentOperations
  }

  def fillTopology(topology: Seq[RoadLink], groupedLanes: Map[String, Seq[PersistedLane]],
                   changedSet: Option[ChangeSet] = None, geometryChanged: Boolean = true ): (Seq[PersistedLane], ChangeSet) = {

    val operations = getOperations(geometryChanged)
    val changeSet = changedSet match {
      case Some(change) => change
      case None => ChangeSet()
    }

    topology.foldLeft(Seq.empty[PersistedLane], changeSet) { case (acc, roadLink) =>
      val (existingAssets, changeSet) = acc
      val assetsOnRoadLink = groupedLanes.getOrElse(roadLink.linkId, Nil)

      val (adjustedAssets, assetAdjustments) = operations.foldLeft(assetsOnRoadLink, changeSet) { case ((currentSegments, currentAdjustments), operation) =>
        operation(roadLink, currentSegments, currentAdjustments )
      }
      (existingAssets ++ adjustedAssets, assetAdjustments)
    }
  }

  def toLPieceWiseLaneOnMultipleLinks(persistedLanes: Seq[PersistedLane], roadLinks: Seq[RoadLink]): Seq[PieceWiseLane] = {
    val mappedTopology = persistedLanes.groupBy(_.linkId)
    mappedTopology.flatMap(pair => {
      val (linkId, assets) = pair
      val roadLink = roadLinks.find(_.linkId == linkId).get
      toLPieceWiseLane(assets, roadLink)
    }).toSeq
  }

  def toLPieceWiseLane(dbLanes: Seq[PersistedLane], roadLink: RoadLink): Seq[PieceWiseLane] = {
    dbLanes.map { dbLane =>
      val points = GeometryUtils.truncateGeometry3D(roadLink.geometry, dbLane.startMeasure, dbLane.endMeasure)
      val endPoints = GeometryUtils.geometryEndpoints(points)

      PieceWiseLane(dbLane.id,dbLane.linkId, dbLane.sideCode, dbLane.expired, points,
        dbLane.startMeasure, dbLane.endMeasure, Set(endPoints._1, endPoints._2), dbLane.modifiedBy, dbLane.modifiedDateTime,
        dbLane.createdBy, dbLane.createdDateTime, dbLane.timeStamp, dbLane.geomModifiedDate, roadLink.administrativeClass,
        laneAttributes = dbLane.attributes, attributes = Map("municipality" -> dbLane.municipalityCode, "trafficDirection" -> roadLink.trafficDirection))
    }
  }

  private def expireSegmentsOutsideGeometry(roadLink: RoadLink, lanes: Seq[PersistedLane], changeSet: ChangeSet): (Seq[PersistedLane], ChangeSet) = {
    val (segmentsWithinGeometry, segmentsOutsideGeometry) = lanes.partition(_.startMeasure < roadLink.length)
    val expiredLaneIds = segmentsOutsideGeometry.map(_.id).toSet

    val lanesWithFixedLength = segmentsOutsideGeometry.map { lane =>
          val  reductionPercent = (lane.endMeasure - roadLink.length) / lane.startMeasure
          val newStartMeasure = lane.startMeasure * (1 - reductionPercent )
          val newEndMeasure = lane.endMeasure * (1 - reductionPercent )

          lane.copy(id = 0L, startMeasure = newStartMeasure, endMeasure = newEndMeasure)
    }

    (segmentsWithinGeometry ++ lanesWithFixedLength, changeSet.copy(expiredLaneIds = changeSet.expiredLaneIds ++ expiredLaneIds))
  }

  private def expireOverlappingSegments(roadLink: RoadLink, segments: Seq[PersistedLane], changeSet: ChangeSet): (Seq[PersistedLane], ChangeSet) = {
    def isChanged(p : PersistedLane) : Boolean = {
      segments.exists(s => p.id == s.id && (p.startMeasure != s.startMeasure || p.endMeasure != s.endMeasure))
    }

    if (segments.size > 1) {
      val sortedSegments = expireOverlappedRecursively(sortNewestFirst(segments), Seq())
      val alteredSegments = sortedSegments.filterNot(_.id == 0)

      // Creates for each linear asset a new MValueAdjustment if the start or end measure have changed
      val mValueChanges = alteredSegments.filter(isChanged).
        map(s => MValueAdjustment(s.id, s.linkId, s.startMeasure, s.endMeasure))

      val expiredIds = segments.map(_.id).filterNot(_ == 0).toSet -- alteredSegments.map(_.id) ++ changeSet.expiredLaneIds
      (sortedSegments,
        changeSet.copy(adjustedMValues = (changeSet.adjustedMValues ++ mValueChanges).filterNot(mvc => expiredIds.contains(mvc.laneId)),
          expiredLaneIds = expiredIds))
    } else
      (segments, changeSet)
  }

  private def expireOverlappedRecursively(sortedAssets: Seq[PersistedLane], result: Seq[PersistedLane]): Seq[PersistedLane] = {
    val keeperOpt = sortedAssets.headOption
    if (keeperOpt.nonEmpty) {
      val keeper = keeperOpt.get
      val overlapping = sortedAssets.tail.flatMap(asset => GeometryUtils.overlap(toSegment(keeper), toSegment(asset)) match {
        case Some(overlap) if keeper.laneCode == asset.laneCode && keeper.sideCode == asset.sideCode=>
          Seq(
            asset.copy(startMeasure = asset.startMeasure, endMeasure = overlap._1),
            asset.copy(id = 0L, startMeasure = overlap._2, endMeasure = asset.endMeasure)
          ).filter(a => a.endMeasure - a.startMeasure >= AllowedTolerance)
        case _ =>
          Seq(asset)
      })
      expireOverlappedRecursively(overlapping, result ++ Seq(keeper))
    } else {
      result
    }
  }

  private def toSegment(persistedLinearAsset: PersistedLane) = {
    (persistedLinearAsset.startMeasure, persistedLinearAsset.endMeasure)
  }

  private def capSegmentsThatOverflowGeometry(roadLink: RoadLink, lanes: Seq[PersistedLane], changeSet: ChangeSet): (Seq[PersistedLane], ChangeSet) = {
    val (validSegments, overflowingSegments) = lanes.partition(_.endMeasure <= roadLink.length + MaxAllowedError)
    val cappedSegments = overflowingSegments.map { x => x.copy(endMeasure = roadLink.length)}
    val mValueAdjustments = cappedSegments.map { x => MValueAdjustment(x.id, x.linkId, x.startMeasure, x.endMeasure) }

    (validSegments ++ cappedSegments, changeSet.copy(adjustedMValues = changeSet.adjustedMValues ++ mValueAdjustments))
  }

  protected def sortNewestFirst(lanes: Seq[PersistedLane]) = {
    lanes.sortBy(s => 0L-s.modifiedDateTime.getOrElse(s.createdDateTime.getOrElse(DateTime.now())).getMillis)
  }

  private def dropShortSegments(roadLink: RoadLink, lanes: Seq[PersistedLane], changeSet: ChangeSet): (Seq[PersistedLane], ChangeSet) = {
    val (shortSegments, linearSegments) = lanes.partition(a => (a.endMeasure - a.startMeasure) < MinAllowedLength && roadLink.length >= MinAllowedLength )
    val expiredLaneIds = shortSegments.map(_.id).toSet

    (linearSegments, changeSet.copy(expiredLaneIds = changeSet.expiredLaneIds ++ expiredLaneIds.filterNot(_ == 0L)))
  }

  private def adjustAssets(roadLink: RoadLink, lanes: Seq[PersistedLane], changeSet: ChangeSet): (Seq[PersistedLane], ChangeSet) = {
    lanes.foldLeft((Seq[PersistedLane](), changeSet)){
      case ((resultAssets, change), lane) =>
        val (asset, adjustmentsMValues) = adjustAsset(lane, roadLink)
        (resultAssets ++ Seq(asset), change.copy(adjustedMValues = change.adjustedMValues ++ adjustmentsMValues))
    }
  }

  private def adjustAsset(lane: PersistedLane, roadLink: RoadLink): (PersistedLane, Seq[MValueAdjustment]) = {
    val roadLinkLength = GeometryUtils.geometryLength(roadLink.geometry)
    val adjustedStartMeasure = if (lane.startMeasure < AllowedTolerance && lane.startMeasure > MaxAllowedError) Some(0.0) else None
    val endMeasureDifference: Double = roadLinkLength - lane.endMeasure
    val adjustedEndMeasure = if (endMeasureDifference < AllowedTolerance && endMeasureDifference > MaxAllowedError) Some(roadLinkLength) else None
    val mValueAdjustments = (adjustedStartMeasure, adjustedEndMeasure) match {
      case (None, None) => Nil
      case (s, e)       => Seq(MValueAdjustment(lane.id, lane.linkId, s.getOrElse(lane.startMeasure), e.getOrElse(lane.endMeasure)))
    }
    val adjustedAsset = lane.copy(
      startMeasure = adjustedStartMeasure.getOrElse(lane.startMeasure),
      endMeasure = adjustedEndMeasure.getOrElse(lane.endMeasure))
    (adjustedAsset, mValueAdjustments)
  }




   def calculateNewMValuesAndSideCode(lane: PersistedLane, historyRoadLink: Option[RoadLink], projection: Projection,
                                      roadLinkLength: Double, isLengthened: Boolean = false): (Double, Double, Int) = {

     val isCutAdditionalLane = historyRoadLink match {
       case Some(historyLink) => lane.laneCode != 1 && (lane.startMeasure != 0 ||
         !areMeasuresCloseEnough(lane.endMeasure, historyLink.length, 0.5))
       case _ => false
     }

    val oldLength = projection.oldEnd - projection.oldStart
    val newLength = projection.newEnd - projection.newStart

    // Test if the direction has changed -> side code will be affected, too
    if (GeometryUtils.isDirectionChangeProjection(projection)) {
      val newSideCode = SideCode.apply(lane.sideCode) match {
        case (SideCode.AgainstDigitizing) => SideCode.TowardsDigitizing.value
        case (SideCode.TowardsDigitizing) => SideCode.AgainstDigitizing.value
        case _ => lane.sideCode
      }

      if(isCutAdditionalLane && isLengthened)
        (lane.startMeasure, lane.endMeasure, newSideCode)
      else {
        val newStart = projection.newStart - (lane.endMeasure - projection.oldStart) * Math.abs(newLength / oldLength)
        val newEnd = projection.newEnd - (lane.startMeasure - projection.oldEnd) * Math.abs(newLength / oldLength)

        // Test if asset is affected by projection
        if (lane.endMeasure <= projection.oldStart || lane.startMeasure >= projection.oldEnd)
          (lane.startMeasure, lane.endMeasure, newSideCode)
        else
          (Math.min(roadLinkLength, Math.max(0.0, newStart)), Math.max(0.0, Math.min(roadLinkLength, newEnd)), newSideCode)

      }} else {
      val newStart = projection.newStart + (lane.startMeasure - projection.oldStart) * Math.abs(newLength / oldLength)
      val newEnd = projection.newEnd + (lane.endMeasure - projection.oldEnd) * Math.abs(newLength / oldLength)

      if(isCutAdditionalLane && isLengthened)
        (lane.startMeasure, lane.endMeasure, lane.sideCode)
      // Test if asset is affected by projection
      else if (lane.endMeasure <= projection.oldStart || lane.startMeasure >= projection.oldEnd) {
        (lane.startMeasure, lane.endMeasure, lane.sideCode)
      } else {
        (Math.min(roadLinkLength, Math.max(0.0, newStart)), Math.max(0.0, Math.min(roadLinkLength, newEnd)), lane.sideCode)
      }
    }
  }

  private def combine(roadLink: RoadLink, lanes: Seq[PersistedLane], changeSet: ChangeSet): (Seq[PersistedLane], ChangeSet) = {

    /**
      * Convert the lanes with startMeasure and endMeasure equals to startM and endM to SegmentPiece
      */
    def SegmentPieceCreation(startM: Double, endM: Double, lanes: Seq[PersistedLane]): Seq[SegmentPiece] = {

      lanes.filter(lane => lane.startMeasure <= startM && lane.endMeasure >= endM)
           .map( x => SegmentPiece(x.id, startM, endM, SideCode(x.sideCode), x.attributes) )
    }


    /**
      * Take pieces of segment and combine two of them if they are related to the similar segment and extend each other
      * (matching startM, endM, value and sidecode)
      * Return altered PersistedLinearAsset as well as the pieces that could not be added to that segments.
      * @param segmentPieces
      * @param segments
      * @return
      */
    def extendOrDivide(segmentPieces: Seq[SegmentPiece], segments: PersistedLane): (PersistedLane, Seq[SegmentPiece]) = {
      val sorted = segmentPieces.sortBy(_.startM)
      val current = sorted.head
      val rest = sorted.tail

      if (rest.nonEmpty) {
        val next = rest.find(sp => sp.startM == current.endM && sp.sideCode == current.sideCode && sp.value.equals(current.value) )

        if (next.nonEmpty) {
          return extendOrDivide(Seq(current.copy(endM = next.get.endM)) ++ rest.filterNot(sp => sp.equals(next.get)), segments)
        }
      }
      (segments.copy(sideCode = current.sideCode.value, startMeasure = current.startM, endMeasure = current.endM), rest)
    }

    /**
      * Creates Numerical limits from orphaned segments (segments originating from a linear lane but no longer connected
      * to them)
      * @param origin Segments Lanes that was split by overwriting a segment piece(s)
      * @param orphans List of orphaned segment pieces
      * @return New segments Lanes for orphaned segment pieces
      */
    def generateLimitsForOrphanSegments(origin: PersistedLane, orphans: Seq[SegmentPiece]): Seq[PersistedLane] = {
      if (orphans.nonEmpty) {
        val segmentPiece = orphans.minBy(_.startM)
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
    def cleanNumericalLimitIds(toProcess: Seq[PersistedLane], processed: Seq[PersistedLane]): Seq[PersistedLane] = {
      val (current, rest) = (toProcess.head, toProcess.tail)

      val modified = if (processed.exists(_.id == current.id) || current.id < 0L) {
                      current.copy(id = 0L)
                    } else {
                      current
                    }

      if (rest.nonEmpty) {
        cleanNumericalLimitIds(rest, processed ++ Seq(modified))
      } else {
        processed ++ Seq(modified)
      }
    }


    val lanesZipped = lanes

    val pointsOfInterest = (lanesZipped.map(_.startMeasure) ++ lanesZipped.map(_.endMeasure)).distinct.sorted
    if (pointsOfInterest.length < 2)
      return (lanesZipped, changeSet)

    val pieces = pointsOfInterest.zip(pointsOfInterest.tail)
    val segmentPieces = pieces.flatMap(p => SegmentPieceCreation(p._1, p._2, lanesZipped))
                              .groupBy(lane => (lane.value.find(_.publicId == "lane_code").get.values.head.value.asInstanceOf[Int], lane.laneId))

    val segmentsAndOrphanPieces = segmentPieces.map(n => extendOrDivide(n._2, lanesZipped.find(lane => lane.laneCode == n._1._1 && lane.id == n._1._2).get))
    val combinedSegment = segmentsAndOrphanPieces.keys.toSeq
    val newSegments = combinedSegment.flatMap(sl => generateLimitsForOrphanSegments(sl, segmentsAndOrphanPieces.getOrElse(sl, Seq()).sortBy(_.startM)))

    val changedSideCodes = combinedSegment.filter(cl => lanesZipped.exists(sl => sl.id == cl.id && !sl.sideCode.equals(cl.sideCode)))
                                           .map(sl => SideCodeAdjustment(sl.id, SideCode(sl.sideCode)))

    val resultingNumericalLimits = combinedSegment ++ newSegments
    val expiredIds = lanesZipped.map(_.id).toSet.--(resultingNumericalLimits.map(_.id).toSet)

    val returnSegments = if (resultingNumericalLimits.nonEmpty) cleanNumericalLimitIds(resultingNumericalLimits, Seq())
                         else Seq()

    (returnSegments, changeSet.copy(expiredLaneIds = changeSet.expiredLaneIds ++ expiredIds, adjustedSideCodes = changeSet.adjustedSideCodes ++ changedSideCodes))

  }


  /**
    * After other change operations connect lanes that have same values and side codes if they extend each other
    * @param roadLink Road link we are working on
    * @param lanes List of PersistedLane
    * @param changeSet Changes done previously
    * @return List of lanes and a change set
    */
  private def fuse(roadLink: RoadLink, lanes: Seq[PersistedLane], changeSet: ChangeSet): (Seq[PersistedLane], ChangeSet) = {
    val sortedList = lanes.sortBy(_.startMeasure)

    if (lanes.nonEmpty) {
      val origin = sortedList.head
      val target = sortedList.tail.find(sl => Math.abs(sl.startMeasure - origin.endMeasure) < 0.1
                                          && sl.attributes.equals(origin.attributes)
                                          && sl.sideCode == origin.sideCode)

      if (target.nonEmpty) {
        // pick id if it already has one regardless of which one is newer
        val toBeFused = Seq(origin, target.get).sortWith(modifiedSort)
        val newId = toBeFused.find(_.id > 0).map(_.id).getOrElse(0L)

        val modified =  toBeFused.head.copy(id = newId, startMeasure = origin.startMeasure, endMeasure = target.get.endMeasure)
        val expiredId = Set(origin.id, target.get.id) -- Set(modified.id, 0L) // never attempt to expire id zero

        val mValueAdjustment = Seq(changeSet.adjustedMValues.find(a => a.laneId == modified.id) match {
          case Some(adjustment) => adjustment.copy(startMeasure = modified.startMeasure, endMeasure = modified.endMeasure)
          case _ => MValueAdjustment(modified.id, modified.linkId, modified.startMeasure, modified.endMeasure)
        })

        // Replace origin and target with this new item in the list and recursively call itself again
        fuse(roadLink, Seq(modified) ++ sortedList.tail.filterNot(sl => Set(origin, target.get).contains(sl)),
          changeSet.copy(expiredLaneIds = changeSet.expiredLaneIds ++ expiredId, adjustedMValues = changeSet.adjustedMValues.filter(a => a.laneId > 0 && a.laneId != modified.id) ++ mValueAdjustment) )

      } else {
        val fused = fuse(roadLink, sortedList.tail, changeSet)
        (Seq(origin) ++ fused._1, fused._2)
      }
    } else {
      (lanes, changeSet)
    }
  }

  private def modifiedSort(left: PersistedLane, right: PersistedLane): Boolean = {
    val leftStamp = left.modifiedDateTime.orElse(left.createdDateTime)
    val rightStamp = right.modifiedDateTime.orElse(right.createdDateTime)

    (leftStamp, rightStamp) match {
      case (Some(l), Some(r)) => l.isAfter(r)
      case (None, Some(r)) => false
      case (Some(l), None) => true
      case (None, None) => true
    }
  }

}

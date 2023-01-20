package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.GeometryUtils
import fi.liikennevirasto.digiroad2.GeometryUtils.Projection
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._

object SpeedLimitFiller extends AssetFiller {
  private val MaxAllowedMValueError = 0.1
  private val Epsilon = 1E-6 /* Smallest mvalue difference we can tolerate to be "equal to zero". One micrometer.
                                See https://en.wikipedia.org/wiki/Floating_point#Accuracy_problems
                             */
  private val MinAllowedSpeedLimitLength = 2.0

  def getOperations(geometryChanged: Boolean): Seq[(RoadLink, Seq[PieceWiseLinearAsset], ChangeSet) => (Seq[PieceWiseLinearAsset], ChangeSet)] = {
    val fillOperations: Seq[(RoadLink, Seq[PieceWiseLinearAsset], ChangeSet) => (Seq[PieceWiseLinearAsset], ChangeSet)] = Seq(
      expireSegmentsOutsideGeometry,
      combine,
      fuse,
      adjustSegmentMValues,
      capToGeometry,
      adjustLopsidedLimit,
      droppedSegmentWrongDirection,
      adjustSegmentSideCodes,
      dropShortSegments,
      fillHoles,
      clean
    )

    val adjustmentOperations: Seq[(RoadLink, Seq[PieceWiseLinearAsset], ChangeSet) => (Seq[PieceWiseLinearAsset], ChangeSet)] = Seq(
      combine,
      fuse,
      adjustSegmentMValues,
      adjustLopsidedLimit,
      droppedSegmentWrongDirection,
      adjustSegmentSideCodes,
      dropShortSegments,
      fillHoles,
      clean)

    if(geometryChanged) fillOperations
    else adjustmentOperations
  }

  private def adjustSegment(segment: PieceWiseLinearAsset, roadLink: RoadLink): (PieceWiseLinearAsset, Seq[MValueAdjustment]) = {
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


  private def adjustTwoWaySegments(roadLink: RoadLink,
                                   segments: Seq[PieceWiseLinearAsset]):
  (Seq[PieceWiseLinearAsset], Seq[MValueAdjustment]) = {
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
                                   segments: Seq[PieceWiseLinearAsset],
                                   runningDirection: SideCode):
  (Seq[PieceWiseLinearAsset], Seq[MValueAdjustment]) = {
    val segmentsTowardsRunningDirection = segments.filter(_.sideCode == runningDirection).sortWith(modifiedSort)
    if (segmentsTowardsRunningDirection.length == 1 && !segments.exists(_.sideCode == SideCode.BothDirections)) {
      val segment = segmentsTowardsRunningDirection.last
      val (adjustedSegment, mValueAdjustments) = adjustSegment(segment, roadLink)
      (Seq(adjustedSegment), mValueAdjustments)
    } else {
      (segmentsTowardsRunningDirection, Nil)
    }
  }

  private def adjustSegmentMValues(roadLink: RoadLink, segments: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val (towardsGeometrySegments, towardsGeometryAdjustments) = adjustOneWaySegments(roadLink, segments, SideCode.TowardsDigitizing)
    val (againstGeometrySegments, againstGeometryAdjustments) = adjustOneWaySegments(roadLink, segments, SideCode.AgainstDigitizing)
    val (twoWayGeometrySegments, twoWayGeometryAdjustments) = adjustTwoWaySegments(roadLink, segments)
    val mValueAdjustments = towardsGeometryAdjustments ++ againstGeometryAdjustments ++ twoWayGeometryAdjustments
    (towardsGeometrySegments ++ againstGeometrySegments ++ twoWayGeometrySegments,
      changeSet.copy(adjustedMValues = changeSet.adjustedMValues ++ mValueAdjustments))
  }


  private def adjustLopsidedLimit(roadLink: RoadLink, segments: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val onlyLimitOnLink = segments.length == 1 && segments.head.sideCode != SideCode.BothDirections
    if (onlyLimitOnLink) {
      val segment = segments.head
      val sideCodeAdjustments = Seq(SideCodeAdjustment(segment.id, SideCode.BothDirections, SpeedLimitAsset.typeId))
      (Seq(segment.copy(sideCode = SideCode.BothDirections)), changeSet.copy(adjustedSideCodes = changeSet.adjustedSideCodes ++ sideCodeAdjustments))
    } else {
      (segments, changeSet)
    }
  }

  override protected def dropShortSegments(roadLink: RoadLink, speedLimits: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val limitsToDrop = speedLimits.filter { limit => GeometryUtils.geometryLength(limit.geometry) < MinAllowedSpeedLimitLength &&
      roadLink.length > MinAllowedSpeedLimitLength }.map(_.id).toSet
    val limits = speedLimits.filterNot { x => limitsToDrop.contains(x.id) }
    (limits, changeSet.copy(droppedAssetIds = changeSet.droppedAssetIds ++ limitsToDrop))
  }


  def generateUnknownSpeedLimitsForLink(roadLink: RoadLink, segmentsOnLink: Seq[PieceWiseLinearAsset]): Seq[PieceWiseLinearAsset] = {
    val lrmPositions: Seq[(Double, Double)] = segmentsOnLink.map { x => (x.startMeasure, x.endMeasure) }

    if(roadLink.isSimpleCarTrafficRoad) {
      val remainders = lrmPositions.foldLeft(Seq((0.0, roadLink.length)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > MinAllowedSpeedLimitLength }
      remainders.map { segment =>
        val geometry = GeometryUtils.truncateGeometry3D(roadLink.geometry, segment._1, segment._2)
        PieceWiseLinearAsset(0, roadLink.linkId, SideCode.BothDirections, None, geometry, false,
          segment._1, segment._1, geometry.toSet, None, None, None, None,
          SpeedLimitAsset.typeId, roadLink.trafficDirection, 0, None,
          roadLink.linkSource, Unknown, Map(), None, None, None)
      }
    } else
      Seq()
  }


  /**
    * After other change operations connect speed limits that have same values and side codes if they extend each other
    * @param roadLink Road link we are working on
    * @param speedLimits List of speed limits
    * @param changeSet Changes done previously
    * @return List of speed limits and a change set
    */
  override protected def fuse(roadLink: RoadLink, speedLimits: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
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
          timeStamp = latestTimestamp(toBeFused.head, target))
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
  private def fillHoles(roadLink: RoadLink, speedLimits: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    def firstAndLastLimit(speedLimits: Seq[PieceWiseLinearAsset], sideCode: SideCode) = {
      val filtered = speedLimits.filter(_.sideCode == sideCode)
      (filtered.sortBy(_.startMeasure).headOption,
        filtered.sortBy(0-_.endMeasure).headOption)
    }
    def extendToGeometry(speedLimits: Seq[PieceWiseLinearAsset], roadLink: RoadLink, changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
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
    def fillBySideCode(speedLimits: Seq[PieceWiseLinearAsset], roadLink: RoadLink, changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
      if (speedLimits.size > 1) {
        val left = speedLimits.head
        val right = speedLimits.find(sl => sl.startMeasure >= left.endMeasure)
        if (right.nonEmpty && Math.abs(left.endMeasure - right.get.startMeasure) < MinAllowedSpeedLimitLength &&
          Math.abs(left.endMeasure - right.get.startMeasure) >= Epsilon) {
          val adjustedLeft = left.copy(endMeasure = right.get.startMeasure,
            geometry = GeometryUtils.truncateGeometry3D(roadLink.geometry, left.startMeasure, right.get.startMeasure),
            timeStamp = latestTimestamp(left, right))
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
    val (geometrySegments, geometryAdjustments) = fillBySideCode(extended, roadLink, ChangeSet(Set(), Nil, Nil, Nil, Set(), Nil))
    (geometrySegments.toSeq,
      newChangeSet.copy(adjustedMValues = newChangeSet.adjustedMValues ++ geometryAdjustments.adjustedMValues))
  }

  /**
    * Removes obsoleted mvalue adjustments and side code adjustments from the list
    * @param roadLink
    * @param speedLimits
    * @param changeSet
    * @return
    */
  private def clean(roadLink: RoadLink, speedLimits: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
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
    (speedLimits, changeSet.copy(droppedAssetIds = Set(), expiredAssetIds = (changeSet.expiredAssetIds ++ changeSet.droppedAssetIds) -- Set(0), adjustedMValues = adjustments, adjustedSideCodes = sideAdjustments))

  }

  override def fillTopology(roadLinks: Seq[RoadLink], speedLimits: Map[String, Seq[PieceWiseLinearAsset]], typeId:Int, changedSet: Option[ChangeSet] = None,
                   geometryChanged: Boolean = true): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val operations = getOperations(geometryChanged)
    // TODO: Do not create dropped asset ids but mark them expired when they are no longer valid or relevant
    val changeSet = changedSet match {
      case Some(change) => change
      case None => ChangeSet( droppedAssetIds = Set.empty[Long],
        expiredAssetIds = Set.empty[Long],
        adjustedMValues = Seq.empty[MValueAdjustment],
        adjustedVVHChanges = Seq.empty[VVHChangesAdjustment],
        adjustedSideCodes = Seq.empty[SideCodeAdjustment],
        valueAdjustments = Seq.empty[ValueAdjustment])
    }

    roadLinks.foldLeft(Seq.empty[PieceWiseLinearAsset], changeSet) { case (acc, roadLink) =>
      val (existingSegments, changeSet) = acc
      val segments = speedLimits.getOrElse(roadLink.linkId, Nil)
      val validSegments = segments.filterNot { segment => changeSet.droppedAssetIds.contains(segment.id) }

      val (adjustedSegments, segmentAdjustments) = operations.foldLeft(validSegments, changeSet) { case ((currentSegments, currentAdjustments), operation) =>
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
    val details = "%d %.4f %.4f %s".format(speedLimit.value.getOrElse(SpeedLimitValue(0)).value, speedLimit.startMeasure, speedLimit.endMeasure, speedLimit.timeStamp.toString)
    if (speedLimit.expired) {
      println("N/A")
    } else {
      println("%s %s %s".format(ids, dir, details))
    }
  }

  def projectSpeedLimit(asset: PieceWiseLinearAsset, to: RoadLink, projection: Projection , changedSet: ChangeSet): (PieceWiseLinearAsset, ChangeSet)= {
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

    val changeSet =
      if ((Math.abs(newStart - newEnd) > 0) && assetId != 0) {
        changedSet.copy(
          adjustedVVHChanges =  changedSet.adjustedVVHChanges ++ Seq(VVHChangesAdjustment(assetId, newLinkId, newStart, newEnd, projection.timeStamp)),
          adjustedSideCodes = changedSet.adjustedSideCodes ++ Seq(SideCodeAdjustment(assetId, newSideCode, SpeedLimitAsset.typeId))
        )
      }
      else
        changedSet

    (PieceWiseLinearAsset(id = assetId, linkId = newLinkId, sideCode = newSideCode, asset.value, geometry, false,
      newStart, newEnd, geometry.toSet, asset.modifiedBy, asset.modifiedDateTime, asset.createdBy, asset.createdDateTime,
      SpeedLimitAsset.typeId, newDirection, projection.timeStamp, None,
      asset.linkSource, Unknown, Map(), None, None, None), changeSet)
  }
}

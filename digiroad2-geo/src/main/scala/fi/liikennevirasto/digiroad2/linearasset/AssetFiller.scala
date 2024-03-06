package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.asset.ConstructionType.{Planned, UnderConstruction}
import fi.liikennevirasto.digiroad2.asset.SideCode.{BothDirections, switch}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._
import fi.liikennevirasto.digiroad2.{GeometryUtils, LogUtilsGeo, Point}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import scala.collection.{Seq, mutable}
import scala.util.Try

case class LinkAndAssets(assets:Set[PieceWiseLinearAsset], link:RoadLinkForFillTopology)

case class RoadLinkForFillTopology(linkId: String, length:Double, trafficDirection:TrafficDirection, administrativeClass:AdministrativeClass, linkSource:LinkGeomSource,
                                   linkType:LinkType, constructionType:ConstructionType, geometry: Seq[Point], municipalityCode:Int){
  def isSimpleCarTrafficRoad: Boolean = {
    val roadLinkTypeAllowed = Seq(ServiceOrEmergencyRoad, CycleOrPedestrianPath, PedestrianZone, TractorRoad, ServiceAccess, SpecialTransportWithoutGate, SpecialTransportWithGate, CableFerry, RestArea)
    val constructionTypeAllowed: Seq[ConstructionType] = Seq(UnderConstruction, Planned)

    !(constructionTypeAllowed.contains(constructionType) || (roadLinkTypeAllowed.contains(linkType) && administrativeClass == State))
  }
}

class AssetFiller {
  val AllowedTolerance = 2.0
  val MinAllowedLength = 2.0
  protected val MaxAllowedMValueError = 0.1
  val roadLinkLongAssets = AssetTypeInfo.roadLinkLongAssets

  /* Smallest mvalue difference we can tolerate to be "equal to zero". One micrometer.
     See https://en.wikipedia.org/wiki/Floating_point#Accuracy_problems */
  private val Epsilon = 1E-6

  val logger = LoggerFactory.getLogger(getClass)

  type FillTopologyOperation = Seq[(RoadLinkForFillTopology, Seq[PieceWiseLinearAsset], ChangeSet) => (Seq[PieceWiseLinearAsset], ChangeSet)]

  def getUpdateSideCodes: Seq[(RoadLinkForFillTopology, Seq[PieceWiseLinearAsset], ChangeSet) => (Seq[PieceWiseLinearAsset], ChangeSet)] = {
    Seq(
      adjustSegmentSideCodes,
      droppedSegmentWrongDirection,
      clean
    )
  }

  def getGenerateUnknowns(typeId: Int): Seq[(RoadLinkForFillTopology, Seq[PieceWiseLinearAsset], ChangeSet) => (Seq[PieceWiseLinearAsset], ChangeSet)] = {
    Seq(
      generateTwoSidedNonExistingLinearAssets(typeId),
      generateOneSidedNonExistingLinearAssets(SideCode.TowardsDigitizing, typeId),
      generateOneSidedNonExistingLinearAssets(SideCode.AgainstDigitizing, typeId)
    )
  }

  def toRoadLinkForFillTopology(roadLink: RoadLink): RoadLinkForFillTopology = {
    RoadLinkForFillTopology(linkId = roadLink.linkId,length =  roadLink.length,trafficDirection = roadLink.trafficDirection,administrativeClass = roadLink.administrativeClass,
      linkSource = roadLink.linkSource,linkType = roadLink.linkType,constructionType = roadLink.constructionType, geometry = roadLink.geometry,Try(roadLink.municipalityCode).getOrElse(0) )
  }

  def debugLogging(operationName:String)(roadLink: RoadLinkForFillTopology, segments: Seq[PieceWiseLinearAsset], changeSet: ChangeSet) ={
    logger.debug(operationName + ": " + roadLink.linkId)
    logger.debug("asset count on link: " + segments.size)
    logger.debug(s"side code adjustment count: ${changeSet.adjustedSideCodes.size}")
    logger.debug(s"mValue adjustment count: ${changeSet.adjustedMValues.size}")
    logger.debug(s"expire adjustment count: ${changeSet.expiredAssetIds.size}")
    logger.debug(s"dropped adjustment count: ${changeSet.droppedAssetIds.size}")
    (segments, changeSet)
  }

  protected def adjustAsset(asset: PieceWiseLinearAsset, roadLink: RoadLinkForFillTopology): (PieceWiseLinearAsset, Seq[MValueAdjustment]) = {
    val roadLinkLength = roadLink.length
    val adjustedStartMeasure = if (asset.startMeasure != 0.0 && asset.startMeasure < AllowedTolerance) Some(0.0) else None
    val endMeasureDifference: Double = roadLinkLength - asset.endMeasure
    val adjustedEndMeasure = if (endMeasureDifference != 0.0 && endMeasureDifference < AllowedTolerance) Some(roadLinkLength) else None
    val mValueAdjustments = (adjustedStartMeasure, adjustedEndMeasure) match {
      case (None, None) => Nil
      case (s, e)       => Seq(MValueAdjustment(asset.id, asset.linkId, s.getOrElse(asset.startMeasure), e.getOrElse(asset.endMeasure)))
    }
    val adjustedAsset = asset.copy(
      startMeasure = adjustedStartMeasure.getOrElse(asset.startMeasure),
      endMeasure = adjustedEndMeasure.getOrElse(asset.endMeasure))
    (adjustedAsset, mValueAdjustments)
  }

  /**
    * Remove asset which is no longer in geometry
    *
    * @param roadLink  which we are processing
    * @param assets    assets in link
    * @param changeSet record of changes for final saving stage
    * @return assets and changeSet
    */
  protected def expireSegmentsOutsideGeometry(roadLink: RoadLinkForFillTopology, assets: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val (segmentsWithinGeometry, segmentsOutsideGeometry) = assets.partition(_.startMeasure < roadLink.length)
    val expiredAssetIds = segmentsOutsideGeometry.map(_.id).toSet
    (segmentsWithinGeometry, changeSet.copy(expiredAssetIds = changeSet.expiredAssetIds ++ expiredAssetIds))
  }

  /**
    * Remove part which is outside of geometry
    * @param roadLink  which we are processing
    * @param segments  assets in link
    * @param changeSet record of changes for final saving stage
    * @return assets and changeSet
    */
  protected def capToGeometry(roadLink: RoadLinkForFillTopology, segments: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val linkLength = roadLink.length
    val (overflowingSegments, passThroughSegments) = segments.partition(_.endMeasure - MaxAllowedMValueError > linkLength)
    val cappedSegments = overflowingSegments.map { s =>
      (s.copy(geometry = GeometryUtils.truncateGeometry3D(roadLink.geometry, Math.min(s.startMeasure, linkLength), linkLength), endMeasure = linkLength),
        MValueAdjustment(s.id, roadLink.linkId, Math.min(s.startMeasure, linkLength), linkLength))
    }
    (passThroughSegments ++ cappedSegments.map(_._1), changeSet.copy(adjustedMValues = changeSet.adjustedMValues ++ cappedSegments.map(_._2)))
  }

  private def segmentFoundOnTheOtherSide(currentSegment: PieceWiseLinearAsset, allSegments: Seq[PieceWiseLinearAsset]): Boolean = {
    allSegments.exists(s => s.linkId == currentSegment.linkId && s.sideCode == switch(currentSegment.sideCode))
  }

  /**
  * Drops the opposite direction assets on a one sided link if there is an asset to the same direction
   */
  def droppedSegmentWrongDirection(roadLink: RoadLinkForFillTopology, segments: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    if (roadLink.trafficDirection == TrafficDirection.BothDirections) {
      (segments, changeSet)
    } else {
      val droppedAssetIds = (roadLink.trafficDirection match {
        case TrafficDirection.TowardsDigitizing => segments.filter(current => current.sideCode == SideCode.AgainstDigitizing && segmentFoundOnTheOtherSide(current, segments))
        case _ => segments.filter(current => current.sideCode == SideCode.TowardsDigitizing && segmentFoundOnTheOtherSide(current, segments))
      }).map(_.id)

      (segments.filterNot(s => droppedAssetIds.contains(s.id)), changeSet.copy(droppedAssetIds = changeSet.droppedAssetIds++ droppedAssetIds))
    }
  }

  def adjustSegmentSideCodes(roadLink: RoadLinkForFillTopology, segments: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {

    val adjusted = roadLink.trafficDirection match {
      case TrafficDirection.BothDirections => segments.map { current =>
        current.sideCode match {
          case SideCode.AgainstDigitizing if !AssetTypeInfo.assetsWithValidityDirectionExcludingSpeedLimits.contains(current.typeId) && !segmentFoundOnTheOtherSide(current, segments) =>
            (current.copy(sideCode = SideCode.BothDirections), SideCodeAdjustment(current.id, current.linkId, SideCode.BothDirections, current.typeId, oldId = current.oldId))
          case SideCode.TowardsDigitizing if !AssetTypeInfo.assetsWithValidityDirectionExcludingSpeedLimits.contains(current.typeId) && !segmentFoundOnTheOtherSide(current, segments) =>
            (current.copy(sideCode = SideCode.BothDirections), SideCodeAdjustment(current.id, current.linkId, SideCode.BothDirections, current.typeId, oldId = current.oldId))
          case _ => (current, SideCodeAdjustment(-1, "", SideCode.TowardsDigitizing, current.typeId))
        }
      }
      case TrafficDirection.AgainstDigitizing => segments.map { current =>
        current.sideCode match {
          case SideCode.BothDirections => (current.copy(sideCode = SideCode.AgainstDigitizing), SideCodeAdjustment(current.id, current.linkId, SideCode.AgainstDigitizing, current.typeId, oldId = current.oldId))
          case SideCode.TowardsDigitizing if !AssetTypeInfo.physicalObjectsWithValidityDirection.contains(current.typeId) && !segmentFoundOnTheOtherSide(current, segments) =>
            (current.copy(sideCode = SideCode.AgainstDigitizing), SideCodeAdjustment(current.id, current.linkId, SideCode.AgainstDigitizing, current.typeId, oldId = current.oldId))
          case _ => (current, SideCodeAdjustment(-1, "", SideCode.TowardsDigitizing, current.typeId))
        }
      }
      case TrafficDirection.TowardsDigitizing => segments.map { current =>
        current.sideCode match {
          case SideCode.BothDirections => (current.copy(sideCode = SideCode.TowardsDigitizing), SideCodeAdjustment(current.id, current.linkId, SideCode.TowardsDigitizing, current.typeId, oldId = current.oldId))
          case SideCode.AgainstDigitizing if !AssetTypeInfo.physicalObjectsWithValidityDirection.contains(current.typeId) && !segmentFoundOnTheOtherSide(current, segments) =>
            (current.copy(sideCode = SideCode.TowardsDigitizing), SideCodeAdjustment(current.id, current.linkId, SideCode.TowardsDigitizing, current.typeId, oldId = current.oldId))
          case _ => (current, SideCodeAdjustment(-1, "", SideCode.TowardsDigitizing, current.typeId))
        }
      }
    }

    (adjusted.map(_._1),
      changeSet.copy(adjustedSideCodes = changeSet.adjustedSideCodes ++
        adjusted.map(_._2).filterNot(s => s.assetId == 0 || s.assetId == -1)))
  }

  protected def generateTwoSidedNonExistingLinearAssets(typeId: Int)(roadLink: RoadLinkForFillTopology, segments: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val lrmPositions: Seq[(Double, Double)] = segments.map { x => (x.startMeasure, x.endMeasure) }
    val remainders = lrmPositions.foldLeft(Seq((0.0, roadLink.length)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > 0.5}
    val generated = remainders.map { segment =>
      PersistedLinearAsset(0L, roadLink.linkId, 1, None, segment._1, segment._2, None, None, None, None, false, typeId, 0, None, roadLink.linkSource, None, None, None)
    }
    val generatedLinearAssets = toLinearAsset(generated, roadLink)
    (segments ++ generatedLinearAssets, changeSet)
  }

  protected def generateOneSidedNonExistingLinearAssets(sideCode: SideCode, typeId: Int)(roadLink: RoadLinkForFillTopology, segments: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val generated = if (roadLink.trafficDirection == TrafficDirection.BothDirections) {
      val lrmPositions: Seq[(Double, Double)] = segments
        .filter { s => s.sideCode == sideCode || s.sideCode == SideCode.BothDirections }
        .map { x => (x.startMeasure, x.endMeasure) }
      val remainders = lrmPositions.foldLeft(Seq((0.0, roadLink.length)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > 0.5 }
      val persisted = remainders.map { segment =>
        PersistedLinearAsset(0L, roadLink.linkId, sideCode.value, None, segment._1, segment._2, None, None, None, None, false, typeId, 0, None, roadLink.linkSource, None, None, None)
      }
      toLinearAsset(persisted, roadLink)
    } else {
      Nil
    }
    (segments ++ generated, changeSet)
  }

  protected def updateValues(roadLink: RoadLinkForFillTopology, segments: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = (segments, changeSet)

  //  TODO Due to a bug in combine, the operation divides asset to smaller segments which are then combined in fuse operation back together
  //   causes an infinite loop when fillTopology is called recursively, this function might need total rework
  /**<br> <pre> 
    * Combines assets so that there is no overlapping, no consecutive assets with same values and
    * that the speed limits with both directions are preferred.
    * Assets go through the following process
    * - squash: road link is cut to small one-sided pieces defined by the startMeasures and endMeasures of all assets
    * - combine: squashed pieces are turned into two-sided (SideCode.BothDirections) where speed limit is equal,
    * keeping the latest edited assets id and timestamps
    * - extend: combined pieces are merged if they have the same side code, speed limit value and one starts where another ends
    * - orphans: pieces that are orphans (as newer, projected asset may overwrite another in the middle!) are collected
    * and then extended just like above.
    * - geometry update: all the result geometries and side codes are revised and written in the change set
    *
    * asset 1 ----
    * asset 2 ----
    *   to
    * asset 1 ----
    *
    * @param roadLink which we are processing
    * @param segments assets in link
    * @param changeSet record of changes for final saving stage
    * @return assets and changeSet
    */
  protected def combine(roadLink: RoadLinkForFillTopology, segments: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {

    def replaceUnknownAssetIds(asset: PieceWiseLinearAsset, pseudoId: Long) = {
      asset.id match {
        case 0 => asset.copy(id = pseudoId)
        case _ => asset
      }
    }
    /**
      * Pick the latest asset for each single piece and create SegmentPiece objects for those
      */
    def squash(startM: Double, endM: Double, segments: Seq[PieceWiseLinearAsset]): Seq[SegmentPiece] = {
      val sl = segments.filter(sl => sl.startMeasure <= startM && sl.endMeasure >= endM)
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
      * Take pieces of segment and combine two of them if they are related to the similar segment and extend each other
      * (matching startM, endM, value and sidecode)
      * Return altered PieceWiseLinearAsset as well as the pieces that could not be added to that segments.
      * @param segmentPieces
      * @param segments
      * @return
      */
    def extendOrDivide(segmentPieces: Seq[SegmentPiece], segments: PieceWiseLinearAsset): (PieceWiseLinearAsset, Seq[SegmentPiece]) = {
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
      (segments.copy(sideCode = current.sideCode, startMeasure = current.startM, endMeasure = current.endM), rest)
    }

    /**
      * Creates assets from orphaned segments (segments originating from a linear assets but no longer connected
      * to them)
      * @param origin Segments that was split by overwriting a segment piece(s)
      * @param orphans List of orphaned segment pieces
      * @return New segments for orphaned segment pieces
      */
    def generateLimitsForOrphanSegments(origin: PieceWiseLinearAsset, orphans: Seq[SegmentPiece]): Seq[PieceWiseLinearAsset] = {
      if (orphans.nonEmpty) {
        val segmentPiece = orphans.sortBy(_.startM).head
        if (orphans.tail.nonEmpty) {
          // Try to extend this segment as far as possible: if SegmentPieces are consecutive produce just one Segments
          val t = extendOrDivide(orphans, origin.copy(startMeasure = segmentPiece.startM, endMeasure = segmentPiece.endM, sideCode = segmentPiece.sideCode))
          // t now has a asset and any orphans it left behind: recursively call this method again
          return Seq(t._1.copy(id = 0L)) ++ generateLimitsForOrphanSegments(origin, t._2)
        }
        // Only orphan in the list, create a new segment Lane for it
        val sl = origin.copy(id = 0L, startMeasure = segmentPiece.startM, endMeasure = segmentPiece.endM, sideCode = segmentPiece.sideCode)
        return Seq(sl)
      }
      Seq()
    }
    def updateGeometry(assets: Seq[PieceWiseLinearAsset], roadLink: RoadLinkForFillTopology): (Seq[(PieceWiseLinearAsset, Option[MValueAdjustment])]) = {
      assets.map { asset =>
        val newGeom = GeometryUtils.truncateGeometry3D(roadLink.geometry, asset.startMeasure, asset.endMeasure)
        GeometryUtils.withinTolerance(newGeom, asset.geometry, MaxAllowedMValueError) match {
          case true => (asset, None)
          case false => (asset.copy(geometry = newGeom) , Option(MValueAdjustment(asset.id, asset.linkId, asset.startMeasure, asset.endMeasure)))
        }
      }
    }
    /**
      * Make sure that no two segments share the same id, rewrite the ones appearing later with id=0
      * @param toProcess List of assets to go thru
      * @param processed List of processed assets
      * @return List of segments with unique or zero ids
      */
    def cleanAssetIds(toProcess: Seq[PieceWiseLinearAsset], processed: Seq[PieceWiseLinearAsset]): Seq[PieceWiseLinearAsset] = {
      val (current, rest) = (toProcess.head, toProcess.tail)
      val modified = processed.exists(_.id == current.id) || current.id < 0L match {
        case true => current.copy(id = 0L)
        case _ => current
      }
      if (rest.nonEmpty) {
        cleanAssetIds(rest, processed ++ Seq(modified))
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
    val updatedAssetsAndMValueAdjustments = updateGeometry(combinedSegment, roadLink)
    val changedSideCodes = combinedSegment.filter(cl =>
      assets.exists(sl => sl.id == cl.id && !sl.sideCode.equals(cl.sideCode))).
      map(sl => SideCodeAdjustment(sl.id, sl.linkId, SideCode(sl.sideCode.value), sl.typeId))
    val resultingAssets = updatedAssetsAndMValueAdjustments.map(n => n._1) ++ updateGeometry(newSegments, roadLink).map(_._1)
    val expiredIds = assets.map(_.id).toSet.--(resultingAssets.map(_.id).toSet)

    val returnSegments = cleanAssetIds(resultingAssets, Seq())
    (returnSegments, changeSet.copy(expiredAssetIds = changeSet.expiredAssetIds ++ expiredIds, adjustedSideCodes = changeSet.adjustedSideCodes ++ changedSideCodes))

  }

  /**
    * Combine sides together if matching m-values and segment values.
    * @param segmentPieces Sequence of one or two segment pieces (guaranteed by squash operation)
    * @param segments All segments on road link
    * @return Sequence of segment pieces (1 or 2 segment pieces in sequence)
    */
  protected def combineEqualValues(segmentPieces: Seq[SegmentPiece], segments: Seq[PieceWiseLinearAsset]): Seq[SegmentPiece] = {
    def chooseSegment(seg1: SegmentPiece, seg2: SegmentPiece, combine: Boolean = false): Seq[SegmentPiece] = {
      val sl1 = segments.find(_.id == seg1.assetId).get
      val sl2 = segments.find(_.id == seg2.assetId).get
      if (sl1.startMeasure.equals(sl2.startMeasure) && sl1.endMeasure.equals(sl2.endMeasure)) { // if start and measure are same, values over each other
        val winner = segments.filter(l => l.id == seg1.assetId || l.id == seg2.assetId).sortBy(s =>
          s.endMeasure - s.startMeasure).head
        if (combine) {
          Seq(segmentPieces.head.copy(assetId = winner.id, sideCode = SideCode.BothDirections))
        } else {
          Seq(segmentPieces.head.copy(assetId = winner.id))
        }
      } else {
        segmentPieces
      }
    }

    def isInBothSide(sideCodes: Seq[Int]): Boolean = {
      sideCodes.equals(Seq(SideCode.TowardsDigitizing.value, SideCode.AgainstDigitizing.value)) || sideCodes.toSet.head == SideCode.BothDirections.value
    }

    val seg1 = segmentPieces.head
    val seg2 = segmentPieces.last

    val sideCodes = Seq(seg1, seg2).map(_.sideCode.value).sorted

    (seg1.value, seg2.value) match {
      case (Some(v1), Some(v2)) =>
        if (v1.equals(v2) && isInBothSide(sideCodes))
          chooseSegment(seg1, seg2, combine = true)
        else segmentPieces
      case (Some(v1), None) => Seq(segmentPieces.head)
      case (None, Some(v2)) => Seq(segmentPieces.last)
      case (None, None) =>
        if (isInBothSide(sideCodes))
          chooseSegment(seg1, seg2, combine = true)
        else chooseSegment(seg1, seg2)
      case _ => segmentPieces
    }
  }

  case class SegmentPiece(assetId: Long, startM: Double, endM: Double, sideCode: SideCode, value: Option[Value])

  def toLinearAsset(dbAssets: Seq[PersistedLinearAsset], roadLink: RoadLinkForFillTopology): Seq[PieceWiseLinearAsset] = {
    dbAssets.map { dbAsset =>
      val points = GeometryUtils.truncateGeometry3D(roadLink.geometry, dbAsset.startMeasure, dbAsset.endMeasure)
      val endPoints = GeometryUtils.geometryEndpoints(points)
      PieceWiseLinearAsset(
        dbAsset.id, dbAsset.linkId, SideCode(dbAsset.sideCode), dbAsset.value, points, dbAsset.expired, dbAsset.startMeasure,
        dbAsset.endMeasure, Set(endPoints._1, endPoints._2), dbAsset.modifiedBy, dbAsset.modifiedDateTime, dbAsset.createdBy,
        dbAsset.createdDateTime, dbAsset.typeId, roadLink.trafficDirection, dbAsset.timeStamp, dbAsset.geomModifiedDate,
        dbAsset.linkSource, roadLink.administrativeClass,  verifiedBy = dbAsset.verifiedBy, verifiedDate = dbAsset.verifiedDate, informationSource = dbAsset.informationSource,
        oldId = dbAsset.oldId
      )
    }
  }

  def toLinearAssetsOnMultipleLinks(PersistedLinearAssets: Seq[PersistedLinearAsset], roadLinks: Seq[RoadLinkForFillTopology]): Seq[PieceWiseLinearAsset] = {
    val mappedTopology = PersistedLinearAssets.groupBy(_.linkId)
    val assets = mappedTopology.flatMap(pair => {
      val (linkId, assets) = pair
      roadLinks.find(_.linkId == linkId).getOrElse(None) match {
        case roadLink: RoadLinkForFillTopology => toLinearAsset(assets, roadLink)
        case _ => None
      }
    }).toSeq
    assets.collect{case asset: PieceWiseLinearAsset => asset}
  }
  
  def mapLinkAndAssets(persistedLinearAssets: Seq[PersistedLinearAsset], roadLinks: Seq[RoadLink]): mutable.HashMap[String, LinkAndAssets] = {
    val mapForParallelRun = new mutable.HashMap[String, LinkAndAssets]()

    def update(asset: PersistedLinearAsset, link: RoadLinkForFillTopology): Unit = {
      val assets = toLinearAsset(Seq(asset), link).toSet
      val linkAndAssets = mapForParallelRun.getOrElseUpdate(asset.linkId, LinkAndAssets(assets, link))
      mapForParallelRun.update(asset.linkId, LinkAndAssets(assets ++ linkAndAssets.assets, link))
    }

    for (asset <- persistedLinearAssets) {
      mapForParallelRun.get(asset.linkId) match {
        case Some(a) => update(asset, a.link)
        case None => // find link if not already in hashmap
          roadLinks.find(_.linkId == asset.linkId).getOrElse(None) match {
            case link: RoadLink => update(asset, toRoadLinkForFillTopology(link))
            case _ => None
          }
      }
    }
    mapForParallelRun
  }
  
  private def toSegment(PieceWiseLinearAsset: PieceWiseLinearAsset) = {
    (PieceWiseLinearAsset.startMeasure, PieceWiseLinearAsset.endMeasure)
  }

  protected def sortNewestFirst(assets: Seq[PieceWiseLinearAsset]) = {
    assets.sortBy(s => 0L-s.modifiedDateTime.getOrElse(s.createdDateTime.getOrElse(DateTime.now())).getMillis)
  }

  private def sortByStartMeasure(assets: Seq[PieceWiseLinearAsset]) = {
    assets.sortBy(s => (s.startMeasure,s.endMeasure))
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
  private def expireOverlappedRecursively(sortedAssets: Seq[PieceWiseLinearAsset], result: Seq[PieceWiseLinearAsset]): Seq[PieceWiseLinearAsset] = {
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
  /**
    * Adjust asset so it does not overlaps: <br> <pre> 
    * asset 1 -------
    * asset 2    -------
    *     to
    * asset 1 ---
    * asset 2    -------
    * @see [[expireOverlappedRecursively]]
    * @param roadLink which we are processing
    * @param segments assets in link
    * @param changeSet record of changes for final saving stage
    * @return assets and changeSet
    */
  def expireOverlappingSegments(roadLink: RoadLinkForFillTopology, segments: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    def isChanged(p : PieceWiseLinearAsset) : Boolean = {
      segments.exists(s => p.id == s.id && (p.startMeasure != s.startMeasure || p.endMeasure != s.endMeasure))
    }

    if (segments.size >= 2) {
      val sortedSegments = expireOverlappedRecursively(sortNewestFirst(segments), Seq())
      val alteredSegments = sortedSegments.filterNot(_.id == 0)

      // Creates for each linear asset a new MValueAdjustment if the start or end measure have changed
      val mValueChanges = alteredSegments.filter(isChanged).
        map(s => MValueAdjustment(s.id, s.linkId, s.startMeasure, s.endMeasure))

      val expiredIds = segments.map(_.id).filterNot(_ == 0).toSet -- alteredSegments.map(_.id) ++ changeSet.expiredAssetIds
      (sortedSegments,
        changeSet.copy(adjustedMValues = (changeSet.adjustedMValues ++ mValueChanges).filterNot(mvc => expiredIds.contains(mvc.assetId)),
          expiredAssetIds = expiredIds))
    } else
      (segments, changeSet)
  }
  /**
    *  Expire asset which are shorter than minimal length.
    *
    * @see [[MinAllowedLength]]
    * @param roadLink  which we are processing
    * @param assets  assets in link
    * @param changeSet record of changes for final saving stage
    * @return assets and changeSet
    */
  def dropShortSegments(roadLink: RoadLinkForFillTopology, assets: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val (shortSegments, linearSegments) = assets.partition(a => (a.endMeasure - a.startMeasure) < MinAllowedLength && roadLink.length >= MinAllowedLength )
    val expiredAssetIds = shortSegments.map(_.id).toSet
    (linearSegments, changeSet.copy(expiredAssetIds = changeSet.expiredAssetIds ++ expiredAssetIds))
  }


  protected def latestTimestamp(linearAsset: PieceWiseLinearAsset, linearAssetO: Option[PieceWiseLinearAsset]) = {
    linearAssetO match {
      case Some(lao) => Math.max(linearAsset.timeStamp, lao.timeStamp)
      case _ => linearAsset.timeStamp
    }
  }

  protected def modifiedSort(left: PieceWiseLinearAsset, right: PieceWiseLinearAsset) = {
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
  def fuse(roadLink: RoadLinkForFillTopology, linearAssets: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val sortedList = linearAssets.sortBy(_.startMeasure)
    if (linearAssets.nonEmpty) {
      val origin = sortedList.head
      val target = sortedList.tail.find(sl => Math.abs(sl.startMeasure - origin.endMeasure) < MaxAllowedMValueError &&
        sl.value.equals(origin.value) && sl.sideCode.equals(origin.sideCode))
      if (target.nonEmpty) {
        // pick id if it already has one regardless of which one is newer
        val toBeFused = Seq(origin, target.get).sortWith(modifiedSort)
        val newId = toBeFused.find(_.id > 0).map(_.id).getOrElse(0L)
        val modified = toBeFused.head.copy(id = newId, startMeasure = origin.startMeasure, endMeasure = target.get.endMeasure,
          geometry = GeometryUtils.truncateGeometry3D(roadLink.geometry, origin.startMeasure, target.get.endMeasure),
          timeStamp = latestTimestamp(toBeFused.head, target))
        val expiredId = Set(origin.id, target.get.id) -- Set(modified.id, 0L) // never attempt to expire id zero
        val mValueAdjustment = Seq(MValueAdjustment(modified.id, modified.linkId, modified.startMeasure, modified.endMeasure))
        // Replace origin and target with this new item in the list and recursively call itself again
        fuse(roadLink, Seq(modified) ++ sortedList.tail.filterNot(sl => Set(origin, target.get).contains(sl)),
          changeSet.copy(expiredAssetIds = changeSet.expiredAssetIds ++ expiredId, adjustedMValues = changeSet.adjustedMValues.filter(a=>a.assetId > 0 && a.assetId != modified.id) ++ mValueAdjustment))
      } else {
        val fused = fuse(roadLink, sortedList.tail, changeSet)
        (Seq(origin) ++ fused._1, fused._2)
      }
    } else {
      val redundantFiltered = changeSet.adjustedMValues.filterNot(adjustment => {
        changeSet.droppedAssetIds.contains(adjustment.assetId) || changeSet.expiredAssetIds.contains(adjustment.assetId)
      })
      (linearAssets, changeSet.copy(adjustedMValues = redundantFiltered))
    }
  }

  def adjustRoadLinkLongAsset(asset: PieceWiseLinearAsset, roadLink: RoadLinkForFillTopology): (PieceWiseLinearAsset, Seq[MValueAdjustment]) = {
    val roadLinkLength = roadLink.length
    val mAdjustment =
      if (asset.startMeasure > 0 || asset.endMeasure < roadLinkLength)
        Seq(MValueAdjustment(asset.id, asset.linkId, 0, roadLinkLength))
      else
        Nil
    val modifiedSegment = asset.copy(geometry = GeometryUtils.truncateGeometry3D(roadLink.geometry, 0, roadLinkLength), startMeasure = 0, endMeasure = roadLinkLength)
    (modifiedSegment, mAdjustment)
  }

  def adjustRoadLinkLongAssetHead(asset: PieceWiseLinearAsset, roadLink: RoadLinkForFillTopology): (Option[PieceWiseLinearAsset], Seq[MValueAdjustment]) = {
    if (asset.startMeasure > 0) {
      val mAdjustment= Seq(MValueAdjustment(asset.id, asset.linkId, 0, asset.endMeasure))
      val modifiedSegment = asset.copy(geometry = GeometryUtils.truncateGeometry3D(roadLink.geometry, 0, asset.endMeasure), startMeasure = 0, endMeasure = asset.endMeasure)
      (Some(modifiedSegment), mAdjustment)
    }else {
      (Some(asset), Nil)
    }
  }

  def adjustRoadLinkLongAssetTail(asset: PieceWiseLinearAsset, roadLink: RoadLinkForFillTopology): (Option[PieceWiseLinearAsset], Seq[MValueAdjustment]) = {
    val roadLinkLength = roadLink.length
    if (asset.endMeasure < roadLinkLength) {
      val mAdjustment= Seq(MValueAdjustment(asset.id, asset.linkId, asset.startMeasure, roadLinkLength))
      val modifiedSegment = asset.copy(geometry = GeometryUtils.truncateGeometry3D(roadLink.geometry, asset.startMeasure, roadLinkLength), startMeasure = asset.startMeasure, endMeasure = roadLinkLength)
      (Some(modifiedSegment), mAdjustment)
    } else {
      (Some(asset), Nil)
    }
  }

  private def adjustTwoWaySegments(roadLink: RoadLinkForFillTopology,
                                   assets: Seq[PieceWiseLinearAsset]):
  (Seq[PieceWiseLinearAsset], Seq[MValueAdjustment]) = {
    val twoWaySegments = assets.filter(_.sideCode == SideCode.BothDirections).sortBy(_.startMeasure)
    if (twoWaySegments.isEmpty) {
      (Nil, Nil)
    }
    else if (twoWaySegments.length == 1  && assets.forall(_.sideCode == SideCode.BothDirections)) {
      val segment = assets.last
      val (adjustedSegment, mValueAdjustments) = adjustRoadLinkLongAsset(segment, roadLink)
      (Seq(adjustedSegment), mValueAdjustments)
    } else {
      val head = twoWaySegments.head
      val last = twoWaySegments.last
      val (adjustedHead, mValueAdjustmentsHead) = if (head.startMeasure <= assets.sortBy(_.startMeasure).head.startMeasure) adjustRoadLinkLongAssetHead(head, roadLink) else (None, Nil)
      val (adjustedLast, mValueAdjustmentsLast) = if (last.endMeasure >= assets.sortBy(_.endMeasure).last.endMeasure) adjustRoadLinkLongAssetTail(last, roadLink) else (None, Nil)
      (adjustedHead, adjustedLast) match {
        case (Some(aH), Some(aL)) =>
          val rest = twoWaySegments.filterNot(a => a.id == aH.id || a.id == aL.id)
          (Seq(aH, aL) ++ rest, mValueAdjustmentsHead ++ mValueAdjustmentsLast)
        case (Some(aH), None) =>
          val rest = twoWaySegments.filterNot(a => a.id == aH.id)
          (Seq(aH) ++ rest, mValueAdjustmentsHead)
        case (None, Some(aL)) =>
          val rest = twoWaySegments.filterNot(a => a.id == aL.id)
          (Seq(aL) ++ rest, mValueAdjustmentsLast)
        case (None, None) =>
          (twoWaySegments, Nil)
      }
    }
  }

  private def adjustOneWaySegments(roadLink: RoadLinkForFillTopology,
                                   assets: Seq[PieceWiseLinearAsset],
                                   runningDirection: SideCode):
  (Seq[PieceWiseLinearAsset], Seq[MValueAdjustment]) = {
    val segmentsTowardsRunningDirection = assets.filter(_.sideCode == runningDirection).sortBy(_.startMeasure)
    if (segmentsTowardsRunningDirection.isEmpty) {
      (Nil, Nil)
    } else if (segmentsTowardsRunningDirection.length == 1 && !assets.exists(_.sideCode == SideCode.BothDirections)) {
      val segment = segmentsTowardsRunningDirection.last
      val (adjustedSegment, mValueAdjustments) = adjustRoadLinkLongAsset(segment, roadLink)
      (Seq(adjustedSegment), mValueAdjustments)
    } else {
      val head = segmentsTowardsRunningDirection.head
      val firstTwo = assets.sortBy(_.startMeasure).slice(0, 2)
      val (adjustedHead, mValueAdjustmentsHead) = {
        val othersThanHead = firstTwo.filterNot(a => a.id == head.id)
        // the first asset with inspected side code is not among the two first
        if (othersThanHead.size == 2) {
          (None, Nil)
          // the first asset with inspected side code is the first of all
        } else if (head.id == firstTwo.head.id) {
          adjustRoadLinkLongAssetHead(head, roadLink)
          // the first asset with inspected side code is opposite to the first asset of all
        } else if (head.sideCode != othersThanHead.head.sideCode && othersThanHead.head.sideCode != BothDirections) {
          adjustRoadLinkLongAssetHead(head, roadLink)
        } else {
          (None, Nil)
        }
      }

      val last = segmentsTowardsRunningDirection.last
      val lastTwo = assets.sortBy(_.endMeasure).slice(assets.length - 2, assets.length)
      val (adjustedLast, mValueAdjustmentsLast) = {
        val othersThanLast = lastTwo.filterNot(a => a.id == last.id)
        // the last asset with inspected side code is not among the two last
        if (othersThanLast.size == 2) {
          (None, Nil)
          // the last asset with inspected side code is the last of all
        } else if (last.id == lastTwo.last.id) {
          adjustRoadLinkLongAssetTail(last, roadLink)
          // the last asset with inspected side code is opposite to the last asset of all
        } else if (head.sideCode != othersThanLast.head.sideCode && othersThanLast.head.sideCode != BothDirections) {
          adjustRoadLinkLongAssetTail(last, roadLink)
        } else {
          (None, Nil)
        }
      }
      (adjustedHead, adjustedLast) match {
        case (Some(aH), Some(aL)) =>
          val rest = segmentsTowardsRunningDirection.filterNot(a => a.id == aH.id || a.id == aL.id)
          (Seq(aH, aL) ++ rest, mValueAdjustmentsHead ++ mValueAdjustmentsLast)
        case (Some(aH), None) =>
          val rest = segmentsTowardsRunningDirection.filterNot(a => a.id == aH.id)
          (Seq(aH) ++ rest, mValueAdjustmentsHead)
        case (None, Some(aL)) =>
          val rest = segmentsTowardsRunningDirection.filterNot(a => a.id == aL.id)
          (Seq(aL) ++ rest, mValueAdjustmentsLast)
        case (None, None) =>
          (segmentsTowardsRunningDirection, Nil)
      }
    }
  }

  /**
    * Finally adjust asset length by snapping to links start and endpoint. <br> <pre> 
    * RoadLink -------
    * Asset    ----    
    * to                
    * RoadLink ------
    * Asset    ------   
    *
    * @see [[adjustAsset]]
    * @param roadLink  which we are processing
    * @param linearAssets  assets in link
    * @param changeSet record of changes for final saving stage
    * @return assets and changeSet
    */
  def adjustAssets(roadLink: RoadLinkForFillTopology, linearAssets: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    if (linearAssets.nonEmpty && roadLinkLongAssets.contains(linearAssets.head.typeId)) {
      val (towardsGeometrySegments, towardsGeometryAdjustments) = adjustOneWaySegments(roadLink, linearAssets, SideCode.TowardsDigitizing)
      val (againstGeometrySegments, againstGeometryAdjustments) = adjustOneWaySegments(roadLink, linearAssets, SideCode.AgainstDigitizing)
      val (twoWayGeometrySegments, twoWayGeometryAdjustments) = adjustTwoWaySegments(roadLink, linearAssets)
      val mValueAdjustments = towardsGeometryAdjustments ++ againstGeometryAdjustments ++ twoWayGeometryAdjustments
      (towardsGeometrySegments ++ againstGeometrySegments ++ twoWayGeometrySegments, changeSet.copy(adjustedMValues = changeSet.adjustedMValues ++ mValueAdjustments))
    } else {
      linearAssets.foldLeft((Seq[PieceWiseLinearAsset](), changeSet)) {
        case ((resultAssets, change), linearAsset) =>
          val (asset, adjustmentsMValues) = adjustAsset(linearAsset, roadLink)
          (resultAssets ++ Seq(asset), change.copy(adjustedMValues = change.adjustedMValues ++ adjustmentsMValues))
      }
    }
  }

  /**
    * Removes adjustments that were overwritten later. The latest adjustment has to be preserved.
    *
    * @param roadLink
    * @param assets
    * @param changeSet
    * @return
    */
  def clean(roadLink: RoadLinkForFillTopology, assets: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {

    val droppedIds = changeSet.droppedAssetIds
    val groupedMValueAdjustments = changeSet.adjustedMValues.filterNot(a => droppedIds.contains(a.assetId)).groupBy(_.assetId)
    val adjustments = groupedMValueAdjustments.map(grouped => grouped._2.last).toSeq
    val groupedSideCodeAdjustments = changeSet.adjustedSideCodes.filterNot(a => droppedIds.contains(a.assetId)).groupBy(_.assetId)
    val sideCodeAdjustments = groupedSideCodeAdjustments.map(grouped => grouped._2.last).toSeq
    (assets, changeSet.copy(droppedAssetIds = Set(), expiredAssetIds = (changeSet.expiredAssetIds ++ changeSet.droppedAssetIds) -- Set(0), adjustedMValues = adjustments, adjustedSideCodes = sideCodeAdjustments))

  }

  /**
    * Fills any missing pieces in the middle of asset.
    * - If the gap is smaller than minimum allowed asset length the first asset is extended
    * !!! But if it is smaller than 1E-6 we let it be and treat it as a rounding error to avoid repeated writes !!!
    * - If the gap is larger and assets is not in roadLinkLongAssets list, it's let to be and will be generated as unknown asset later
    *
    * @param roadLink    Road link being handled
    * @param assets List of asset limits
    * @param changeSet   Set of changes
    * @return List of asset and change set so that there are no small gaps between asset
    */
  private def fillHolesSteps(roadLink: RoadLinkForFillTopology, assets: Seq[PieceWiseLinearAsset], changeSet: ChangeSet, takesAccountSidCode:Boolean= true): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    def fillBySideCode(assets: Seq[PieceWiseLinearAsset], roadLink: RoadLinkForFillTopology, changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
      if (assets.size > 1) {
        val left = assets.head
        val right = if (takesAccountSidCode) assets.find(sl => sl.startMeasure >= left.endMeasure && sl.sideCode.value == left.sideCode.value)
        else assets.find(sl => sl.startMeasure >= left.endMeasure)
        val middleParts = if (right.nonEmpty) assets.filter(a =>
          a.startMeasure >= left.endMeasure && a.endMeasure <= right.get.startMeasure && !Set(left.id, right.get.id).contains(a.id)
        ) else Seq()

        val notTooShortGap = right.nonEmpty && Math.abs(left.endMeasure - right.get.startMeasure) >= Epsilon
        val gapIsSmallerThanTolerance = right.nonEmpty && Math.abs(left.endMeasure - right.get.startMeasure) < MinAllowedLength
        val valuesAreSame = right.nonEmpty && left.value.equals(right.get.value)
        val condition = if (!roadLinkLongAssets.contains(assets.head.typeId)) gapIsSmallerThanTolerance else {
          if (gapIsSmallerThanTolerance) true else valuesAreSame // do not check is same value if gap is 2 meter or smaller
        }
        if (middleParts.isEmpty && notTooShortGap && condition) {
          val adjustedLeft = left.copy(endMeasure = right.get.startMeasure,
            geometry = GeometryUtils.truncateGeometry3D(roadLink.geometry, left.startMeasure, right.get.startMeasure),
            timeStamp = latestTimestamp(left, right))
          val adj = MValueAdjustment(adjustedLeft.id, adjustedLeft.linkId, adjustedLeft.startMeasure, adjustedLeft.endMeasure)
          val recurse = fillBySideCode(assets.tail, roadLink, changeSet)
          (Seq(adjustedLeft) ++ recurse._1, recurse._2.copy(adjustedMValues = recurse._2.adjustedMValues ++ Seq(adj)))
        } else {
          val recurse = fillBySideCode(assets.tail, roadLink, changeSet)
          (Seq(left) ++ recurse._1, recurse._2)
        }
      } else {
        (assets, changeSet)
      }
    }

    val (geometrySegments, geometryAdjustments) = fillBySideCode(assets.sortBy(_.startMeasure), roadLink, changeSet)
    (geometrySegments, geometryAdjustments)
  }

  /**
    * Fills any missing pieces in the middle of assets.
    * @param roadLink  Road link being handled
    * @param assets    List of asset limits
    * @param changeSet Set of changes
    * @return List of asset and change set so that there are no small gaps between asset
    */
  def fillHolesTwoSteps(roadLink: RoadLinkForFillTopology, assets: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val (geometrySegments, geometryAdjustments) = fillHolesSteps(roadLink, assets, changeSet)
    fillHolesSteps(roadLink, geometrySegments, geometryAdjustments, takesAccountSidCode = false)
  }
  
  /**
    * Fills any missing pieces in the middle of assets.
    * First fill hole. Merge parts if possible by calling [[fuse]].
    * @param roadLink  Road link being handled
    * @param assets    List of asset limits
    * @param changeSet Set of changes
    * @return List of asset and change set so that there are no small gaps between asset
    */
  def fillHolesWithFuse(roadLink: RoadLinkForFillTopology, assets: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val (geometrySegments,geometryAdjustments) = fillHolesTwoSteps(roadLink, assets, changeSet)
    fuse(roadLink, geometrySegments, geometryAdjustments)
  }

  def fillTopology(topology: Seq[RoadLinkForFillTopology], linearAssets: Map[String, Seq[PieceWiseLinearAsset]], typeId: Int,
                   changedSet: Option[ChangeSet] = None, geometryChanged: Boolean = true): (Seq[PieceWiseLinearAsset], ChangeSet) = {

    val operations: FillTopologyOperation = Seq(
      combine,
      debugLogging("combine"),
      fuse,
      debugLogging("fuse"),
      dropShortSegments,
      debugLogging("dropShortSegments"),
      adjustAssets,
      debugLogging("adjustAssets"),
      adjustSegmentSideCodes,
      debugLogging("adjustSegmentSideCodes"),
      droppedSegmentWrongDirection,
      debugLogging("droppedSegmentWrongDirection"),
      generateTwoSidedNonExistingLinearAssets(typeId),
      debugLogging("generateTwoSidedNonExistingLinearAssets"),
      generateOneSidedNonExistingLinearAssets(SideCode.TowardsDigitizing, typeId),
      debugLogging("generateOneSidedNonExistingLinearAssets"),
      generateOneSidedNonExistingLinearAssets(SideCode.AgainstDigitizing, typeId),
      debugLogging("generateOneSidedNonExistingLinearAssets"),
      updateValues,
      debugLogging("updateValues"),
      clean
    )

    val changeSet = LinearAssetFiller.useOrEmpty(changedSet)

    topology.foldLeft(Seq.empty[PieceWiseLinearAsset], changeSet) { case (acc, roadLink) =>
      val (existingAssets, changeSet) = acc
      val assetsOnRoadLink = linearAssets.getOrElse(roadLink.linkId, Nil)

      val (adjustedAssets, assetAdjustments) = operations.foldLeft(assetsOnRoadLink, changeSet) { case ((currentSegments, currentAdjustments), operation) =>
        operation(roadLink, currentSegments, currentAdjustments)
      }
      (existingAssets ++ adjustedAssets, assetAdjustments)
    }
  }

  def fillTopologyChangesGeometry(topology: Seq[RoadLinkForFillTopology], linearAssets: Map[String, Seq[PieceWiseLinearAsset]], typeId: Int,
                                  changedSet: Option[ChangeSet] = None): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val operations: FillTopologyOperation = Seq(
      debugLogging("operation start"),
      fuse,
      debugLogging("fuse"),
      dropShortSegments,
      debugLogging("dropShortSegments"),
      adjustAssets,
      debugLogging("adjustAssets"),
      expireOverlappingSegments,
      debugLogging("expireOverlappingSegments"),
      adjustSegmentSideCodes,
      debugLogging("adjustSegmentSideCodes"),
      droppedSegmentWrongDirection,
      debugLogging("droppedSegmentWrongDirection"),
      fillHolesWithFuse,
      debugLogging("fillHoles"),
      updateValues,
      debugLogging("updateValues"),
      clean
    )

    val changeSet = LinearAssetFiller.useOrEmpty(changedSet)
    topology.foldLeft(Seq.empty[PieceWiseLinearAsset], changeSet) { case (acc, roadLink) =>
      val (existingAssets, changeSet) = acc
      val assetsOnRoadLink = linearAssets.getOrElse(roadLink.linkId, Nil)

      val (adjustedAssets, assetAdjustments) = operations.foldLeft(assetsOnRoadLink, changeSet) { case ((currentSegments, currentAdjustments), operation) =>
          operation(roadLink, currentSegments, currentAdjustments)
        }
      
      val filterExpiredAway = LinearAssetFiller.removeExpiredMValuesAdjustments2(assetAdjustments)

      val noDuplicate = filterExpiredAway.copy(
          adjustedMValues = filterExpiredAway.adjustedMValues.distinct,
          adjustedSideCodes = filterExpiredAway.adjustedSideCodes.distinct,
          valueAdjustments = filterExpiredAway.valueAdjustments.distinct
        )
      

      (existingAssets ++ adjustedAssets, noDuplicate)
    }
  }
  
  def adjustSideCodes(topology: Seq[RoadLinkForFillTopology], linearAssets: Map[String, Seq[PieceWiseLinearAsset]], typeId: Int, changedSet: Option[ChangeSet] = None): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val changeSet = LinearAssetFiller.useOrEmpty(changedSet)

    topology.foldLeft(Seq.empty[PieceWiseLinearAsset], changeSet) { case (acc, roadLink) =>
      val (existingAssets, changeSet) = acc
      val assetsOnRoadLink = linearAssets.getOrElse(roadLink.linkId, Nil)

      val (adjustedAssets, assetAdjustments) = getUpdateSideCodes.foldLeft(assetsOnRoadLink, changeSet) { case ((currentSegments, currentAdjustments), operation) =>
        operation(roadLink, currentSegments, currentAdjustments)
      }
      (existingAssets ++ adjustedAssets, assetAdjustments)
    }
  }
  
  def generateUnknowns(topology: Seq[RoadLinkForFillTopology], linearAssets: Map[String, Seq[PieceWiseLinearAsset]], typeId: Int): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val changeSet = LinearAssetFiller.emptyChangeSet

    topology.foldLeft(Seq.empty[PieceWiseLinearAsset], changeSet) { case (acc, roadLink) =>
      val (existingAssets, changeSet) = acc
      val assetsOnRoadLink = linearAssets.getOrElse(roadLink.linkId, Nil)

      val (adjustedAssets, assetAdjustments) = getGenerateUnknowns(typeId).foldLeft(assetsOnRoadLink, changeSet) { case ((currentSegments, currentAdjustments), operation) =>
        operation(roadLink, currentSegments, currentAdjustments)
      }
      (existingAssets ++ adjustedAssets, assetAdjustments)
    }
  }
}
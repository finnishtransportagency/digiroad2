package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.asset.Asset._
import fi.liikennevirasto.digiroad2.asset.{TrafficDirection, SideCode}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{SideCodeAdjustment, ChangeSet, MValueAdjustment}
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}

object NumericalLimitFiller {
  private val AllowedTolerance = 0.5
  private val MaxAllowedError = 0.01

  private def adjustAsset(asset: PersistedLinearAsset, roadLink: VVHRoadLinkWithProperties): (PersistedLinearAsset, Seq[MValueAdjustment]) = {
    val roadLinkLength = GeometryUtils.geometryLength(roadLink.geometry)
    val adjustedStartMeasure = if (asset.startMeasure < AllowedTolerance && asset.startMeasure > MaxAllowedError) Some(0.0) else None
    val endMeasureDifference: Double = roadLinkLength - asset.endMeasure
    val adjustedEndMeasure = if (endMeasureDifference < AllowedTolerance && endMeasureDifference > MaxAllowedError) Some(roadLinkLength) else None
    val mValueAdjustments = (adjustedStartMeasure, adjustedEndMeasure) match {
      case (None, None) => Nil
      case (s, e)       => Seq(MValueAdjustment(asset.id, asset.mmlId, s.getOrElse(asset.startMeasure), e.getOrElse(asset.endMeasure)))
    }
    val adjustedAsset = asset.copy(
      startMeasure = adjustedStartMeasure.getOrElse(asset.startMeasure),
      endMeasure = adjustedEndMeasure.getOrElse(asset.endMeasure))
    (adjustedAsset, mValueAdjustments)
  }

  private def adjustTwoWaySegments(roadLink: VVHRoadLinkWithProperties, assets: Seq[PersistedLinearAsset], changeSet: ChangeSet): (Seq[PersistedLinearAsset], ChangeSet) = {
    val twoWaySegments = assets.filter(_.sideCode == 1)
    if (twoWaySegments.length == 1 && assets.forall(_.sideCode == 1)) {
      val asset = assets.head
      val (adjustedAsset, mValueAdjustments) = adjustAsset(asset, roadLink)
      (Seq(adjustedAsset), changeSet.copy(adjustedMValues = changeSet.adjustedMValues ++ mValueAdjustments))
    } else {
      (assets, changeSet)
    }
  }

  private def dropSegmentsOutsideGeometry(roadLink: VVHRoadLinkWithProperties, assets: Seq[PersistedLinearAsset], changeSet: ChangeSet): (Seq[PersistedLinearAsset], ChangeSet) = {
    val (segmentsWithinGeometry, segmentsOutsideGeometry) = assets.partition(_.startMeasure < roadLink.length)
    val droppedAssetIds = segmentsOutsideGeometry.map(_.id).toSet
    (segmentsWithinGeometry, changeSet.copy(droppedAssetIds = changeSet.droppedAssetIds ++ droppedAssetIds))
  }

  private def adjustSegmentSideCodes(roadLink: VVHRoadLinkWithProperties, segments: Seq[PersistedLinearAsset], changeSet: ChangeSet): (Seq[PersistedLinearAsset], ChangeSet) = {
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

  private def generateTwoSidedNonExistingLinearAssets(typeId: Int)(roadLink: VVHRoadLinkWithProperties, segments: Seq[PersistedLinearAsset], changeSet: ChangeSet): (Seq[PersistedLinearAsset], ChangeSet) = {
    val lrmPositions: Seq[(Double, Double)] = segments.map { x => (x.startMeasure, x.endMeasure) }
    val remainders = lrmPositions.foldLeft(Seq((0.0, roadLink.length)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > 0.5}
    val generated = remainders.map { segment =>
      PersistedLinearAsset(0L, roadLink.mmlId, 1, None, segment._1, segment._2, None, None, None, None, false, typeId)
    }
    (segments ++ generated, changeSet)
  }

  private def generateOneSidedNonExistingLinearAssets(sideCode: SideCode, typeId: Int)(roadLink: VVHRoadLinkWithProperties, segments: Seq[PersistedLinearAsset], changeSet: ChangeSet): (Seq[PersistedLinearAsset], ChangeSet) = {
    val generated = if (roadLink.trafficDirection == TrafficDirection.BothDirections) {
      val lrmPositions: Seq[(Double, Double)] = segments
        .filter { s => s.sideCode == sideCode.value || s.sideCode == SideCode.BothDirections.value }
        .map { x => (x.startMeasure, x.endMeasure) }
      val remainders = lrmPositions.foldLeft(Seq((0.0, roadLink.length)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > 0.5 }
      remainders.map { segment =>
        PersistedLinearAsset(0L, roadLink.mmlId, sideCode.value, None, segment._1, segment._2, None, None, None, None, false, typeId)
      }
    } else {
      Nil
    }
    (segments ++ generated, changeSet)
  }

  private def toLinearAsset(dbAssets: Seq[PersistedLinearAsset], roadLink: VVHRoadLinkWithProperties): Seq[PieceWiseLinearAsset] = {
    dbAssets.map { dbAsset =>
      val points = GeometryUtils.truncateGeometry(roadLink.geometry, dbAsset.startMeasure, dbAsset.endMeasure)
      val endPoints = GeometryUtils.geometryEndpoints(points)
      PieceWiseLinearAsset(
        dbAsset.id, dbAsset.mmlId, SideCode(dbAsset.sideCode), dbAsset.value, points, dbAsset.expired,
        dbAsset.startMeasure, dbAsset.endMeasure,
        Set(endPoints._1, endPoints._2), dbAsset.modifiedBy, dbAsset.modifiedDateTime,
        dbAsset.createdBy, dbAsset.createdDateTime, dbAsset.typeId, roadLink.trafficDirection)
    }
  }

  def fillTopology(topology: Seq[VVHRoadLinkWithProperties], linearAssets: Map[Long, Seq[PersistedLinearAsset]], typeId: Int): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val fillOperations: Seq[(VVHRoadLinkWithProperties, Seq[PersistedLinearAsset], ChangeSet) => (Seq[PersistedLinearAsset], ChangeSet)] = Seq(
      dropSegmentsOutsideGeometry,
      adjustTwoWaySegments,
      adjustSegmentSideCodes,
      generateTwoSidedNonExistingLinearAssets(typeId),
      generateOneSidedNonExistingLinearAssets(SideCode.TowardsDigitizing, typeId),
      generateOneSidedNonExistingLinearAssets(SideCode.AgainstDigitizing, typeId)
    )

    topology.foldLeft(Seq.empty[PieceWiseLinearAsset], ChangeSet(Set.empty, Nil, Nil)) { case (acc, roadLink) =>
      val (existingAssets, changeSet) = acc
      val assetsOnRoadLink = linearAssets.getOrElse(roadLink.mmlId, Nil)

      val (adjustedAssets, assetAdjustments) = fillOperations.foldLeft(assetsOnRoadLink, changeSet) { case ((currentSegments, currentAdjustments), operation) =>
        operation(roadLink, currentSegments, currentAdjustments)
      }

      (existingAssets ++ toLinearAsset(adjustedAssets, roadLink), assetAdjustments)
    }
  }
}

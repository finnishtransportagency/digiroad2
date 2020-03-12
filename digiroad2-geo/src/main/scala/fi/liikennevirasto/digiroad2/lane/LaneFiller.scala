package fi.liikennevirasto.digiroad2.lane

import fi.liikennevirasto.digiroad2.GeometryUtils
import fi.liikennevirasto.digiroad2.GeometryUtils.Projection
import fi.liikennevirasto.digiroad2.asset.{SideCode, TrafficDirection}
import fi.liikennevirasto.digiroad2.lane.LaneFiller._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import org.joda.time.DateTime

object  LaneFiller {
  case class MValueAdjustment(laneId: Long, linkId: Long, startMeasure: Double, endMeasure: Double)
  case class VVHChangesAdjustment(laneId: Long, linkId: Long, startMeasure: Double, endMeasure: Double, vvhTimestamp: Long)
  case class SideCodeAdjustment(laneId: Long, sideCode: SideCode)
  case class ValueAdjustment(lane: PersistedLane)

  case class ChangeSet( adjustedMValues: Seq[MValueAdjustment],
                       adjustedVVHChanges: Seq[VVHChangesAdjustment],
                       adjustedSideCodes: Seq[SideCodeAdjustment],
                       expiredLaneIds: Set[Long],
                       valueAdjustments: Seq[ValueAdjustment])

  case class SegmentPiece(laneId: Long, startM: Double, endM: Double, sideCode: SideCode, value: LanePropertiesValues)
}

class LaneFiller {
  val AllowedTolerance = 0.5
  val MaxAllowedError = 0.01
  val MinAllowedLength = 2.0

  def fillTopology(topology: Seq[RoadLink], groupedLanes: Map[Long, Seq[PersistedLane]], changedSet: Option[ChangeSet] = None): (Seq[PieceWiseLane], ChangeSet) = {
    val fillOperations: Seq[(RoadLink, Seq[PersistedLane], ChangeSet) => (Seq[PersistedLane], ChangeSet)] = Seq(
      expireSegmentsOutsideGeometry,
      capSegmentsThatOverflowGeometry,
      combine,
      fuse,
      dropShortSegments,
      adjustAssets,
      adjustLanesSideCodes
    )

    val changeSet = changedSet match {
      case Some(change) => change
      case None => ChangeSet(
        expiredLaneIds = Set.empty[Long],
        adjustedMValues = Seq.empty[MValueAdjustment],
        adjustedVVHChanges = Seq.empty[VVHChangesAdjustment],
        adjustedSideCodes = Seq.empty[SideCodeAdjustment],
        valueAdjustments = Seq.empty[ValueAdjustment])
    }

    topology.foldLeft(Seq.empty[PieceWiseLane], changeSet) { case (acc, roadLink) =>
      val (existingAssets, changeSet) = acc
      val assetsOnRoadLink = groupedLanes.getOrElse(roadLink.linkId, Nil)

      val (adjustedAssets, assetAdjustments) = fillOperations.foldLeft(assetsOnRoadLink, changeSet) { case ((currentSegments, currentAdjustments), operation) =>
        operation(roadLink, currentSegments, currentAdjustments)
      }
      (existingAssets ++ toLPieceWiseLane(adjustedAssets, roadLink), assetAdjustments)
    }
  }

  private def toLPieceWiseLane(dbLanes: Seq[PersistedLane], roadLink: RoadLink): Seq[PieceWiseLane] = {
    dbLanes.map { dbLane =>
      val points = GeometryUtils.truncateGeometry3D(roadLink.geometry, dbLane.startMeasure, dbLane.endMeasure)
      val endPoints = GeometryUtils.geometryEndpoints(points)

      PieceWiseLane(dbLane.id,dbLane.linkId, dbLane.sideCode, dbLane.expired, points,
        dbLane.startMeasure, dbLane.endMeasure, Set(endPoints._1, endPoints._2), dbLane.modifiedBy, dbLane.modifiedDateTime,
        dbLane.createdBy, dbLane.createdDateTime, dbLane.vvhTimeStamp, dbLane.geomModifiedDate, roadLink.administrativeClass,
        laneAttributes = dbLane.attributes, attributes = Map("municipality" -> dbLane.municipalityCode))
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


  private def adjustLanesSideCodes(roadLink: RoadLink, lanes: Seq[PersistedLane], changeSet: ChangeSet): (Seq[PersistedLane], ChangeSet) = {

    if(lanes.isEmpty )
      return (lanes, changeSet)

    val lanesToProcess = lanes.filter(_.linkId == roadLink.linkId)
    val baseLane = lanesToProcess.head
    val baseProps = baseLane.attributes.properties.filterNot(_.publicId == "lane_code")

    roadLink.trafficDirection match {

      case TrafficDirection.BothDirections =>

        val mainLanes = ( lanesToProcess.exists(_.laneCode == 11),  lanesToProcess.exists(_.laneCode == 21) )

        val toAdd = mainLanes match {
          case (true, true) => Seq()
          case (false, true) => Seq(
                            PersistedLane(0L, roadLink.linkId, SideCode.TowardsDigitizing.value, 11, baseLane.municipalityCode,
                          0, roadLink.length, None, None, None, None, expired = false, roadLink.vvhTimeStamp, None,
                          LanePropertiesValues(baseProps ++ Seq(LaneProperty("lane_code", Seq(LanePropertyValue(11))))))
                        )

          case (true, false) => Seq(
                            PersistedLane(0L, roadLink.linkId, SideCode.AgainstDigitizing.value, 21, baseLane.municipalityCode,
                            0, roadLink.length, None, None, None, None, expired = false, roadLink.vvhTimeStamp, None,
                            LanePropertiesValues(baseProps ++ Seq(LaneProperty("lane_code", Seq(LanePropertyValue(21))))))
                      )
          case (false, false) => Seq(
            PersistedLane(0L, roadLink.linkId, SideCode.TowardsDigitizing.value, 11, baseLane.municipalityCode,
              0, roadLink.length, None, None, None, None, expired = false, roadLink.vvhTimeStamp, None,
              LanePropertiesValues(baseProps ++ Seq(LaneProperty("lane_code", Seq(LanePropertyValue(11)))))),

            PersistedLane(0L, roadLink.linkId, SideCode.AgainstDigitizing.value, 21, baseLane.municipalityCode,
              0, roadLink.length, None, None, None, None, expired = false, roadLink.vvhTimeStamp, None,
              LanePropertiesValues(baseProps ++ Seq(LaneProperty("lane_code", Seq(LanePropertyValue(21))))))
          )
          case _ => Seq()
        }

        (lanes ++ toAdd, changeSet)

      case TrafficDirection.TowardsDigitizing =>

        val toAdd = if ( !lanesToProcess.exists(_.laneCode == 11 )) {
                     Seq( PersistedLane(0L, roadLink.linkId, SideCode.TowardsDigitizing.value, 11, baseLane.municipalityCode,
                        0, roadLink.length, None, None, None, None, expired = false, roadLink.vvhTimeStamp, None,
                        LanePropertiesValues(baseProps ++ Seq(LaneProperty("lane_code", Seq(LanePropertyValue(11))))))
                     )

                  } else
                      Seq()

        val toRemove = lanesToProcess.map{ lane =>
          lane.attributes.properties.find(_.publicId == "lane_code") match  {
            case Some(x) => x.values match {
                                    case Seq(LanePropertyValue(v)) => if (v.toString.toInt >= 21 && v.toString.toInt <= 29) lane.id
                                                                  else 0L
                                    case _ => 0L
                                  }
            case _ => 0L
          }
        }.filterNot( _ == 0L)

        val lanesToAdd = (lanes ++ toAdd).filterNot(lane => toRemove.contains(lane.id))

        (lanesToAdd, changeSet.copy( expiredLaneIds = changeSet.expiredLaneIds ++ toRemove ))

      case TrafficDirection.AgainstDigitizing =>

        val toAdd = if ( !lanesToProcess.exists(_.laneCode == 21)) {
                   Seq( PersistedLane(0L, roadLink.linkId,  SideCode.AgainstDigitizing.value, 11, baseLane.municipalityCode,
                      0, roadLink.length, None, None, None, None, expired = false, roadLink.vvhTimeStamp, None,
                      LanePropertiesValues(baseProps ++ Seq(LaneProperty("lane_code", Seq(LanePropertyValue(21))))))
                   )
        } else
          Seq()

        val toRemove = lanesToProcess.map{ lane =>
          lane.attributes.properties.find(_.publicId == "lane_code") match  {
            case Some(x) => x.values match {
              case Seq(LanePropertyValue(v)) => if (v.toString.toInt >= 11 && v.toString.toInt <= 19) lane.id
                                             else 0L
              case _ => 0L
            }
            case _ => 0L
          }
        }.filterNot( _ == 0L)

        val lanesToAdd = (lanes ++ toAdd).filterNot(lane => toRemove.contains(lane.id))

        (lanesToAdd, changeSet.copy( expiredLaneIds = changeSet.expiredLaneIds ++ toRemove ))

      case _ => (lanes, changeSet)
    }

  }

  def projectLinearAsset(lane: PersistedLane, to: RoadLink, projection: Projection, changedSet: ChangeSet) : (PersistedLane, ChangeSet)= {
    val newLinkId = to.linkId
    val laneId = lane.linkId match {
      case to.linkId => lane.id
      case _ => 0
    }
    val (newStart, newEnd, newSideCode) = calculateNewMValuesAndSideCode(lane, projection, to.length)

    val changeSet = laneId match {
      case 0 => changedSet
      case _ => changedSet.copy(adjustedVVHChanges =  changedSet.adjustedVVHChanges ++ Seq(VVHChangesAdjustment(laneId, newLinkId, newStart, newEnd, projection.vvhTimeStamp)), adjustedSideCodes = changedSet.adjustedSideCodes ++ Seq(SideCodeAdjustment(laneId, SideCode.apply(newSideCode))))
    }

    (PersistedLane(laneId, newLinkId, newSideCode,lane.laneCode, lane.municipalityCode, newStart, newEnd, lane.createdBy,
      lane.createdDateTime, lane.modifiedBy, lane.modifiedDateTime, expired = false, projection.vvhTimeStamp,
      lane.geomModifiedDate, lane.attributes), changeSet)

  }


  private def calculateNewMValuesAndSideCode(asset: PersistedLane, projection: Projection, roadLinkLength: Double) = {
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

  private def combine(roadLink: RoadLink, lanes: Seq[PersistedLane], changeSet: ChangeSet): (Seq[PersistedLane], ChangeSet) = {

    def replaceUnknownAssetIds(lane: PersistedLane, pseudoId: Long) = {
      lane.id match {
        case 0 => lane.copy(id = pseudoId)
        case _ => lane
      }
    }
    /**
      * Convert the lanes with startMeasure and endMeasure equals to startM and endM to SegmentPiece
      */
    def SegmentPieceCreation(startM: Double, endM: Double, lanes: Seq[PersistedLane]): Seq[SegmentPiece] = {

      val sl = lanes.filter(lane => lane.startMeasure <= startM && lane.endMeasure >= endM)

      sl.map( x => SegmentPiece(x.id, startM, endM, SideCode(x.sideCode), x.attributes) )
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
    def cleanNumericalLimitIds(toProcess: Seq[PersistedLane], processed: Seq[PersistedLane]): Seq[PersistedLane] = {
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

    val lanesZipped = lanes.zipWithIndex.map(n => replaceUnknownAssetIds(n._1, 0L-n._2))
    val pointsOfInterest = (lanesZipped.map(_.startMeasure) ++ lanesZipped.map(_.endMeasure)).distinct.sorted
    if (pointsOfInterest.length < 2)
      return (lanesZipped, changeSet)

    val pieces = pointsOfInterest.zip(pointsOfInterest.tail)
    val segmentPieces = pieces.flatMap(p => SegmentPieceCreation(p._1, p._2, lanesZipped)).sortBy(_.laneId)
    val segmentsAndOrphanPieces = segmentPieces.groupBy(_.laneId).map(n => extendOrDivide(n._2, lanesZipped.find(_.id == n._1).get))
    val combinedSegment = segmentsAndOrphanPieces.keys.toSeq
    val newSegments = combinedSegment.flatMap(sl => generateLimitsForOrphanSegments(sl, segmentsAndOrphanPieces.getOrElse(sl, Seq()).sortBy(_.startM)))

    val changedSideCodes = combinedSegment.filter(cl => lanesZipped.exists(sl => sl.id == cl.id && !sl.sideCode.equals(cl.sideCode)))
                                           .map(sl => SideCodeAdjustment(sl.id, SideCode(sl.sideCode)))

    val resultingNumericalLimits = combinedSegment ++ newSegments
    val expiredIds = lanesZipped.map(_.id).toSet.--(resultingNumericalLimits.map(_.id).toSet)

    val returnSegments = if (resultingNumericalLimits.nonEmpty) cleanNumericalLimitIds(resultingNumericalLimits, Seq()) else Seq()
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
          changeSet.copy(expiredLaneIds = changeSet.expiredLaneIds ++ expiredId, adjustedMValues = changeSet.adjustedMValues.filter(a => a.laneId > 0 && a.laneId != modified.id) ++ mValueAdjustment))

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

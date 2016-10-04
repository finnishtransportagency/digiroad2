package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.GeometryUtils
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.viite.dao.RoadAddress

object RoadAddressFiller {
  case class LRMValueAdjustment(id: Long, linkId: Long, startMeasure: Option[Double], endMeasure: Option[Double])
  case class AddressChangeSet(
                               droppedAddressIds: Set[Long],
                               adjustedMValues: Seq[LRMValueAdjustment])
  private val MaxAllowedMValueError = 0.001
  private val Epsilon = 1E-6 /* Smallest mvalue difference we can tolerate to be "equal to zero". One micrometer.
                                See https://en.wikipedia.org/wiki/Floating_point#Accuracy_problems
                             */
  private val MinAllowedRoadAddressLength = 0.5

  private def capToGeometry(roadLink: RoadLink, segments: Seq[RoadAddress], changeSet: AddressChangeSet): (Seq[RoadAddress], AddressChangeSet) = {
    val linkLength = GeometryUtils.geometryLength(roadLink.geometry)
    val (overflowingSegments, passThroughSegments) = segments.partition(_.endMValue - MaxAllowedMValueError > linkLength)
    val cappedSegments = overflowingSegments.map { s =>
      (s.copy(endMValue = linkLength),
        LRMValueAdjustment(s.id, roadLink.linkId, None, Option(linkLength)))
    }
    (passThroughSegments ++ cappedSegments.map(_._1), changeSet.copy(adjustedMValues = changeSet.adjustedMValues ++ cappedSegments.map(_._2)))
  }

  private def dropSegmentsOutsideGeometry(roadLink: RoadLink, segments: Seq[RoadAddress], changeSet: AddressChangeSet): (Seq[RoadAddress], AddressChangeSet) = {
    val linkLength = GeometryUtils.geometryLength(roadLink.geometry)
    val (overflowingSegments, passThroughSegments) = segments.partition(_.startMValue + Epsilon > linkLength)
    val droppedSegmentIds = overflowingSegments.map (s => s.id)

    (passThroughSegments, changeSet.copy(droppedAddressIds = changeSet.droppedAddressIds ++ droppedSegmentIds))
  }

  private def extendToGeometry(roadLink: RoadLink, segments: Seq[RoadAddress], changeSet: AddressChangeSet): (Seq[RoadAddress], AddressChangeSet) = {
    val linkLength = GeometryUtils.geometryLength(roadLink.geometry)
    val lastSegment = segments.maxBy(_.endMValue)
    val restSegments = segments.tail
    val (adjustments) = lastSegment.endMValue < linkLength - MaxAllowedMValueError match {
      case true => (restSegments ++ Seq(lastSegment.copy(endMValue = linkLength)), Seq(LRMValueAdjustment(lastSegment.id, lastSegment.linkId, None, Option(linkLength))))
      case _ => (segments, Seq())
    }
    (adjustments._1, changeSet.copy(adjustedMValues = changeSet.adjustedMValues ++ adjustments._2))
  }

  def generateUnknownRoadAddressesForRoadLink(roadLink: RoadLink, adjustedSegments: Seq[RoadAddress]) = {
    //TODO: Unknown road address handling
    Seq()
  }

  def fillTopology(roadLinks: Seq[RoadLink], roadAddressMap: Map[Long, Seq[RoadAddress]]): (Seq[RoadAddress], AddressChangeSet) = {
    val fillOperations: Seq[(RoadLink, Seq[RoadAddress], AddressChangeSet) => (Seq[RoadAddress], AddressChangeSet)] = Seq(
      dropSegmentsOutsideGeometry,
      capToGeometry,
      extendToGeometry
    )
    val initialChangeSet = AddressChangeSet(Set.empty, Nil)

    roadLinks.foldLeft(Seq.empty[RoadAddress], initialChangeSet) { case (acc, roadLink) =>
      val (existingSegments, changeSet) = acc
      val segments = roadAddressMap.getOrElse(roadLink.linkId, Nil)
      val validSegments = segments.filterNot { segment => changeSet.droppedAddressIds.contains(segment.id) }

      val (adjustedSegments, segmentAdjustments) = fillOperations.foldLeft(validSegments, changeSet) { case ((currentSegments, currentAdjustments), operation) =>
        operation(roadLink, currentSegments, currentAdjustments)
      }
      val generatedRoadAddresses = generateUnknownRoadAddressesForRoadLink(roadLink, adjustedSegments)
      (existingSegments ++ adjustedSegments ++ generatedRoadAddresses, segmentAdjustments)
    }
  }

}

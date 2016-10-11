package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.GeometryUtils
import fi.liikennevirasto.digiroad2.asset.State
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao.{Discontinuity, MissingRoadAddress, RoadAddress, RoadType}
import org.joda.time.DateTime
import fi.liikennevirasto.viite.dao.{MissingRoadAddress, RoadAddress}
import fi.liikennevirasto.viite.model.RoadAddressLink
import fi.liikennevirasto.digiroad2.asset.State
import fi.liikennevirasto.viite.RoadAddressLinkBuilder

object RoadAddressFiller {
  case class LRMValueAdjustment(id: Long, linkId: Long, startMeasure: Option[Double], endMeasure: Option[Double])
  case class AddressChangeSet(
                               droppedAddressIds: Set[Long],
                               adjustedMValues: Seq[LRMValueAdjustment],
                               missingRoadAddresses: Seq[MissingRoadAddress])
  private val MaxAllowedMValueError = 0.001
  private val Epsilon = 1E-6 /* Smallest mvalue difference we can tolerate to be "equal to zero". One micrometer.
                                See https://en.wikipedia.org/wiki/Floating_point#Accuracy_problems
                             */
  private val MinAllowedRoadAddressLength = 0.5
  private val AnomalyNoAddressGiven = 1
  private val AnomalyNotFullyCovered = 2
  private val AnomalyIllogical = 3
  private val AnomalyNoAnomaly = 0


  private def capToGeometry(roadLink: RoadLink, segments: Seq[RoadAddressLink], changeSet: AddressChangeSet): (Seq[RoadAddressLink], AddressChangeSet) = {
    val linkLength = GeometryUtils.geometryLength(roadLink.geometry)
    val (overflowingSegments, passThroughSegments) = segments.partition(_.endMValue - MaxAllowedMValueError > linkLength)
    val cappedSegments = overflowingSegments.map { s =>
      (s.copy(endMValue = linkLength),
        LRMValueAdjustment(s.id, roadLink.linkId, None, Option(linkLength)))
    }
    (passThroughSegments ++ cappedSegments.map(_._1), changeSet.copy(adjustedMValues = changeSet.adjustedMValues ++ cappedSegments.map(_._2)))
  }

  private def dropSegmentsOutsideGeometry(roadLink: RoadLink, segments: Seq[RoadAddressLink], changeSet: AddressChangeSet): (Seq[RoadAddressLink], AddressChangeSet) = {
    val linkLength = GeometryUtils.geometryLength(roadLink.geometry)
    val (overflowingSegments, passThroughSegments) = segments.partition(_.startMValue + Epsilon > linkLength)
    val droppedSegmentIds = overflowingSegments.map (s => s.id)

    (passThroughSegments, changeSet.copy(droppedAddressIds = changeSet.droppedAddressIds ++ droppedSegmentIds))
  }

  private def extendToGeometry(roadLink: RoadLink, segments: Seq[RoadAddressLink], changeSet: AddressChangeSet): (Seq[RoadAddressLink], AddressChangeSet) = {
    if (segments.isEmpty)
      return (segments, changeSet)
    val linkLength = GeometryUtils.geometryLength(roadLink.geometry)
    val lastSegment = segments.maxBy(_.endMValue)
    val restSegments = segments.tail
    val (adjustments) = lastSegment.endMValue < linkLength - MaxAllowedMValueError match {
      case true => (restSegments ++ Seq(lastSegment.copy(endMValue = linkLength)), Seq(LRMValueAdjustment(lastSegment.id, lastSegment.linkId, None, Option(linkLength))))
      case _ => (segments, Seq())
    }
    (adjustments._1, changeSet.copy(adjustedMValues = changeSet.adjustedMValues ++ adjustments._2))
  }

  def generateUnknownRoadAddressesForRoadLink(roadLink: RoadLink, adjustedSegments: Seq[RoadAddressLink]): Seq[MissingRoadAddress] = {
    if (adjustedSegments.isEmpty)
      generateUnknownLink(roadLink)
    else
      Seq()
  }

  private def isPublicRoad(roadLink: RoadLink) = {
    roadLink.administrativeClass == State || roadLink.attributes.get("ROADNUMBER").exists {
      case Some(value) => value.toString.toInt > 0
      case _ => false
    }
  }

  private def generateUnknownLink(roadLink: RoadLink) = {
    if (isPublicRoad(roadLink))
      Seq(MissingRoadAddress(roadLink.linkId, None, None, None, None, Some(0.0), Some(roadLink.length), AnomalyNoAddressGiven))
    else
      Seq()
  }

  private def buildMissingRoadAddress(rl: RoadLink, roadAddrSeq: Seq[MissingRoadAddress]): Seq[RoadAddressLink] = {
    roadAddrSeq.map(mra => RoadAddressLinkBuilder.build(rl, mra))
  }

  def fillTopology(roadLinks: Seq[RoadLink], roadAddressMap: Map[Long, Seq[RoadAddressLink]]): (Seq[RoadAddressLink], AddressChangeSet) = {
    val fillOperations: Seq[(RoadLink, Seq[RoadAddressLink], AddressChangeSet) => (Seq[RoadAddressLink], AddressChangeSet)] = Seq(
      dropSegmentsOutsideGeometry,
      capToGeometry,
      extendToGeometry
    )
    val initialChangeSet = AddressChangeSet(Set.empty, Nil, Nil)

    roadLinks.foldLeft(Seq.empty[RoadAddressLink], initialChangeSet) { case (acc, roadLink) =>
      val (existingSegments, changeSet) = acc
      val segments = roadAddressMap.getOrElse(roadLink.linkId, Nil)
      val validSegments = segments.filterNot { segment => changeSet.droppedAddressIds.contains(segment.id) }

      val (adjustedSegments, segmentAdjustments) = fillOperations.foldLeft(validSegments, changeSet) { case ((currentSegments, currentAdjustments), operation) =>
        operation(roadLink, currentSegments, currentAdjustments)
      }
      val generatedRoadAddresses = generateUnknownRoadAddressesForRoadLink(roadLink, adjustedSegments)
      val generatedLinks = buildMissingRoadAddress(roadLink, generatedRoadAddresses)
      (existingSegments ++ adjustedSegments ++ generatedLinks,
        segmentAdjustments.copy(missingRoadAddresses = segmentAdjustments.missingRoadAddresses ++ generatedRoadAddresses))
    }
  }

}

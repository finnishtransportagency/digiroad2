package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset.State
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.viite.RoadAddressLinkBuilder
import fi.liikennevirasto.viite.RoadType.PublicRoad
import fi.liikennevirasto.viite.dao.{MissingRoadAddress, RoadAddress, RoadAddressDAO}
import fi.liikennevirasto.viite.model.{Anomaly, RoadAddressLink}

object RoadAddressFiller {
  case class LRMValueAdjustment(addressId: Long, linkId: Long, startMeasure: Option[Double], endMeasure: Option[Double])
  case class AddressChangeSet(
                               toFloatingAddressIds: Set[Long],
                               adjustedMValues: Seq[LRMValueAdjustment],
                               missingRoadAddresses: Seq[MissingRoadAddress])
  private val MaxAllowedMValueError = 0.001
  private val Epsilon = 1E-6 /* Smallest mvalue difference we can tolerate to be "equal to zero". One micrometer.
                                See https://en.wikipedia.org/wiki/Floating_point#Accuracy_problems
                             */
  private val MaxDistanceDiffAllowed = 1.0 /*Temporary restriction from PO: Filler limit on modifications
                                            (LRM adjustments) is limited to 1 meter. If there is a need to fill /
                                            cut more than that then nothing is done to the road address LRM data.
                                            */
  private val MinAllowedRoadAddressLength = 0.1

  private def capToGeometry(roadLink: RoadLink, segments: Seq[RoadAddressLink], changeSet: AddressChangeSet): (Seq[RoadAddressLink], AddressChangeSet) = {
    val linkLength = GeometryUtils.geometryLength(roadLink.geometry)
    val (overflowingSegments, passThroughSegments) = segments.partition(x => (x.endMValue - MaxAllowedMValueError > linkLength) && (x.endMValue - linkLength <= MaxDistanceDiffAllowed))
    val cappedSegments = overflowingSegments.map { s =>
      (s.copy(endMValue = linkLength),
        LRMValueAdjustment(s.id, roadLink.linkId, None, Option(linkLength)))
    }
    (passThroughSegments ++ cappedSegments.map(_._1), changeSet.copy(adjustedMValues = changeSet.adjustedMValues ++ cappedSegments.map(_._2)))
  }

  private def dropSegmentsOutsideGeometry(roadLink: RoadLink, segments: Seq[RoadAddressLink], changeSet: AddressChangeSet): (Seq[RoadAddressLink], AddressChangeSet) = {
    val linkLength = GeometryUtils.geometryLength(roadLink.geometry)
    val (overflowingSegments, passThroughSegments) = segments.partition(x => x.startMValue + Epsilon > linkLength)

    val droppedSegmentIds = overflowingSegments.map (s => s.id)

    (passThroughSegments, changeSet.copy(toFloatingAddressIds = changeSet.toFloatingAddressIds ++ droppedSegmentIds))
  }

  private def extendToGeometry(roadLink: RoadLink, segments: Seq[RoadAddressLink], changeSet: AddressChangeSet): (Seq[RoadAddressLink], AddressChangeSet) = {
    if (segments.isEmpty)
      return (segments, changeSet)
    val linkLength = GeometryUtils.geometryLength(roadLink.geometry)
    val sorted = segments.sortBy(_.endMValue)(Ordering[Double].reverse)
    val lastSegment = sorted.head
    val restSegments = sorted.tail
    val allowedDiff = ((linkLength - MaxAllowedMValueError) - lastSegment.endMValue) <= MaxDistanceDiffAllowed
    val (adjustments) = (lastSegment.endMValue < linkLength - MaxAllowedMValueError) && allowedDiff match {
      case true => (restSegments ++ Seq(lastSegment.copy(endMValue = linkLength)), Seq(LRMValueAdjustment(lastSegment.id, lastSegment.linkId, None, Option(linkLength))))
      case _ => (segments, Seq())
    }
    (adjustments._1, changeSet.copy(adjustedMValues = changeSet.adjustedMValues ++ adjustments._2))
  }

  private def dropShort(roadLink: RoadLink, segments: Seq[RoadAddressLink], changeSet: AddressChangeSet): (Seq[RoadAddressLink], AddressChangeSet) = {
    if (segments.size < 2)
      return (segments, changeSet)
    val (droppedSegments, passThroughSegments) = segments.partition (s => s.length < MinAllowedRoadAddressLength)
    val droppedSegmentIds = droppedSegments.map(_.id).toSet

    (passThroughSegments, changeSet.copy(toFloatingAddressIds = changeSet.toFloatingAddressIds ++ droppedSegmentIds))
  }

  def generateUnknownRoadAddressesForRoadLink(roadLink: RoadLink, adjustedSegments: Seq[RoadAddressLink]): Seq[MissingRoadAddress] = {
    if (adjustedSegments.isEmpty)
      generateUnknownLink(roadLink)
    else
      Seq()
  }

  private def isPublicRoad(roadLink: RoadLink) = {
    roadLink.administrativeClass == State || roadLink.attributes.get("ROADNUMBER").exists(_.toString.toInt > 0)
  }

  private def generateUnknownLink(roadLink: RoadLink) = {
    Seq(MissingRoadAddress(roadLink.linkId, None, None, PublicRoad, None, None, Some(0.0), Some(roadLink.length), isPublicRoad(roadLink) match {
      case true => Anomaly.NoAddressGiven
      case false => Anomaly.None
    }))
  }

  private def buildMissingRoadAddress(rl: RoadLink, roadAddrSeq: Seq[MissingRoadAddress]): Seq[RoadAddressLink] = {
    roadAddrSeq.map(mra => RoadAddressLinkBuilder.build(rl, mra))
  }

  def fillTopology(roadLinks: Seq[RoadLink], roadAddressMap: Map[Long, Seq[RoadAddressLink]]): (Seq[RoadAddressLink], AddressChangeSet) = {
    val fillOperations: Seq[(RoadLink, Seq[RoadAddressLink], AddressChangeSet) => (Seq[RoadAddressLink], AddressChangeSet)] = Seq(
      dropSegmentsOutsideGeometry,
      capToGeometry,
      extendToGeometry,
      dropShort
    )
    val initialChangeSet = AddressChangeSet(Set.empty, Nil, Nil)

    roadLinks.foldLeft(Seq.empty[RoadAddressLink], initialChangeSet) { case (acc, roadLink) =>
      val (existingSegments, changeSet) = acc
      val segments = roadAddressMap.getOrElse(roadLink.linkId, Nil)
      val validSegments = segments.filterNot { segment => changeSet.toFloatingAddressIds.contains(segment.id) }

      val (adjustedSegments, segmentAdjustments) = fillOperations.foldLeft(validSegments, changeSet) { case ((currentSegments, currentAdjustments), operation) =>
        operation(roadLink, currentSegments, currentAdjustments)
      }
      val generatedRoadAddresses = generateUnknownRoadAddressesForRoadLink(roadLink, adjustedSegments)
      val generatedLinks = buildMissingRoadAddress(roadLink, generatedRoadAddresses)
      (existingSegments ++ adjustedSegments ++ generatedLinks,
        segmentAdjustments.copy(missingRoadAddresses = segmentAdjustments.missingRoadAddresses ++
          generatedRoadAddresses.filterNot(_.anomaly == Anomaly.None)))
    }
  }


}

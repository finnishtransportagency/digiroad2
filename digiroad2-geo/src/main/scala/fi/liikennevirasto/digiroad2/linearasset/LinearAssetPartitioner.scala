package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.asset.SideCode
import scala.util.Try

object LinearAssetPartitioner extends GraphPartitioner {
  def partition[T <: LinearAsset](links: Seq[T], roadLinksForSpeedLimits: Map[String, RoadLink]): Seq[Seq[T]] = {
    val (twoWayLinks, oneWayLinks) = links.partition(_.sideCode == SideCode.BothDirections)
    val linkGroups = twoWayLinks.groupBy { link =>
      val roadLink = roadLinksForSpeedLimits.get(link.linkId)
      val roadIdentifier = roadLink.flatMap(_.roadIdentifier)
      (roadIdentifier, roadLink.map(_.administrativeClass), link.value, link.id == 0)
    }

    val (linksToPartition, linksToPass) = linkGroups.partition { case ((roadIdentifier, _, _, _), _) => roadIdentifier.isDefined }

    val clusters = for (linkGroup <- linksToPartition.values.toSeq;
                        cluster <- clusterLinks(linkGroup)) yield cluster
    val linkPartitions = clusters.map(linksFromCluster)

    linkPartitions ++ linksToPass.values.flatten.map(x => Seq(x)) ++ oneWayLinks.map(x => Seq(x))
  }

  def partition[T <: PieceWiseLinearAsset](links: Seq[T]): Seq[Seq[T]] = {
    val (twoWayLinks, oneWayLinks) = links.partition(_.sideCode == SideCode.BothDirections)

    def extractRoadIdentifier(link: PieceWiseLinearAsset): Option[Either[Long, String]] = {

      val roadNumber = link.attributes.get("ROADNUMBER").flatMap {
        case Some(value: Long) => Some(Left(value))
        case None => None
      }

      val roadNameFi = link.attributes.get("ROADNAME_FI").flatMap {
        case Some(value: String) => Some(Right(value))
        case None => None
      }

      val roadNameSe = link.attributes.get("ROADNAME_SE").flatMap {
        case Some(value: String) => Some(Right(value))
        case None => None
      }

      roadNumber.orElse(roadNameFi).orElse(roadNameSe)
    }

    def partitionLinkGroup(linkGroup:  Seq[T]):Seq[Seq[T]] = {
      val grouped = linkGroup.groupBy { link =>
        (extractRoadIdentifier(link), link.administrativeClass, link.value, link.id == 0, link.trafficDirection, link.attributes.get("ROADPARTNUMBER").orElse(None))
      }

      val (linksToPartition, linksToPass) = grouped.partition { case ((roadIdentifier, _, _, _, _, _), _) => roadIdentifier.isDefined }
      val clusters = linksToPartition.values.map(p => {
        clusterLinks(p)
      }).toSeq.flatten

      val linkPartitions = clusters.map(linksFromCluster)
      val result = linkPartitions ++ linksToPass.values.flatten.map(x => Seq(x))
      result
    }

    val twoWayResult = partitionLinkGroup(twoWayLinks)
    val oneWayResult = partitionLinkGroup(oneWayLinks)

    twoWayResult ++ oneWayResult
  }
}

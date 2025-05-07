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

    val groupedTwoWay = twoWayLinks.groupBy { link =>
      (extractRoadIdentifier(link), link.administrativeClass, link.value, link.id == 0, link.trafficDirection, link.attributes.get("ROADPARTNUMBER").orElse(None))
    }

    val groupedOneWay = oneWayLinks.groupBy { link =>
      (extractRoadIdentifier(link), link.administrativeClass, link.value, link.id == 0, link.trafficDirection, link.attributes.get("ROADPARTNUMBER").orElse(None))
    }

    val (linksToPartitionTwoWay, linksToPassTwoWay) = groupedTwoWay.partition { case ((roadIdentifier, _, _, _, _, _), _) => roadIdentifier.isDefined }
    val clustersTwoWay = linksToPartitionTwoWay.values.map(p => {
      clusterLinks(p)
    }).toSeq.flatten
    val linkPartitionsTwoWay = clustersTwoWay.map(linksFromCluster)
    val twoWayResult = linkPartitionsTwoWay ++ linksToPassTwoWay.values.flatten.map(x => Seq(x))

    val (linksToPartitionOneWay, linksToPassOneWay) = groupedOneWay.partition { case ((roadIdentifier, _, _, _, _, _), _) => roadIdentifier.isDefined }
    val clustersOneWay = linksToPartitionOneWay.values.map(p => {
      clusterLinks(p)
    }).toSeq.flatten
    val linkPartitionsOneWay = clustersOneWay.map(linksFromCluster)
    val oneWayResult = linkPartitionsOneWay ++ linksToPassOneWay.values.flatten.map(x => Seq(x))

    twoWayResult ++ oneWayResult
  }
}

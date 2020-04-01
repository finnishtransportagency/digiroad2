package fi.liikennevirasto.digiroad2.lane

import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.linearasset.{GraphPartitioner, RoadLink}

object LanePartitioner extends GraphPartitioner {

  def partition[T <: Lane](links: Seq[T], roadLinksForSpeedLimits: Map[Long, RoadLink]): Seq[Seq[T]] = {
    val (twoWayLinks, oneWayLinks) = links.partition(_.sideCode == SideCode.BothDirections)
    val linkGroups = oneWayLinks.groupBy { link =>
      val roadLink = roadLinksForSpeedLimits.get(link.linkId)
      val roadIdentifier = roadLink.flatMap(_.roadIdentifier)
      (roadIdentifier, roadLink.map(_.administrativeClass), link.laneAttributes, link.id == 0)
    }

    val (linksToPartition, linksToPass) = linkGroups.partition { case ((roadIdentifier, _, _, _), _) => roadIdentifier.isDefined }

    val clusters = for (linkGroup <- linksToPartition.values.toSeq;
                        cluster <- clusterLinks(linkGroup)) yield cluster

    val linkPartitions = clusters.map(linksFromCluster)

    linkPartitions ++ linksToPass.values.flatten.map(x => Seq(x)) ++ twoWayLinks.map(x => Seq(x))
  }

}

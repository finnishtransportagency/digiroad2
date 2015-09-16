package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.asset.SideCode

object SpeedLimitPartitioner extends GraphPartitioner {
  def partition(links: Seq[SpeedLimit], roadLinksForSpeedLimits: Map[Long, RoadLinkForSpeedLimit]): Seq[Seq[SpeedLimit]] = {
    val (twoWayLinks, oneWayLinks) = links.partition(_.sideCode == SideCode.BothDirections)
    val linkGroups = twoWayLinks.groupBy { link =>
      val roadLink = roadLinksForSpeedLimits.get(link.mmlId)
      (roadLink.map(_.roadIdentifier), roadLink.map(_.administrativeClass), link.value)
    }
    val (linksToPartition, linksToPass) = linkGroups.partition { case (key, _) => key._1.isDefined }

    val clusters = for (linkGroup <- linksToPartition.values.toSeq;
                        cluster <- clusterLinks(linkGroup)) yield cluster
    val linkPartitions = clusters.map(linksFromCluster)

    linkPartitions ++ linksToPass.values.flatten.map(x => Seq(x)) ++ oneWayLinks.map(x => Seq(x))
  }
}

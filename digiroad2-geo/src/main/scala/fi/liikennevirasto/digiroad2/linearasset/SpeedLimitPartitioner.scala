package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.asset.SideCode

object SpeedLimitPartitioner extends GraphPartitioner {
  def partition(links: Seq[SpeedLimit], roadIdentifiers: Map[Long, Either[Int, String]]): Seq[Seq[SpeedLimit]] = {
    val (twoWayLinks, oneWayLinks) = links.partition(_.sideCode == SideCode.BothDirections)
    val linkGroups = twoWayLinks.groupBy { link => (roadIdentifiers.get(link.mmlId), link.value) }
    val (linksToPartition, linksToPass) = linkGroups.partition { case (key, _) => key._1.isDefined }

    val clusters = for (linkGroup <- linksToPartition.values.toSeq;
                        cluster <- clusterLinks(linkGroup)) yield cluster
    val linkPartitions = clusters.map(linksFromCluster)

    linkPartitions ++ linksToPass.values.flatten.map(x => Seq(x)) ++ oneWayLinks.map(x => Seq(x))
  }
}

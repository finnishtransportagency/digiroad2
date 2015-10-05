package fi.liikennevirasto.digiroad2.linearasset

object RoadLinkPartitioner extends GraphPartitioner {
  def partition(links: Seq[VVHRoadLinkWithProperties]): Seq[Seq[VVHRoadLinkWithProperties]] = {
    val linkGroups = links.groupBy { link => (
      link.functionalClass, link.trafficDirection,
      link.linkType, link.administrativeClass, link.roadIdentifier)
    }

    val clusters = for (linkGroup <- linkGroups.values.toSeq;
                        cluster <- clusterLinks(linkGroup)) yield cluster

    clusters.map(linksFromCluster)
  }
}

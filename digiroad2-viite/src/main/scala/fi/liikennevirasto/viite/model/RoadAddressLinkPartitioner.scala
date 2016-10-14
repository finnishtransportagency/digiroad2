package fi.liikennevirasto.viite.model

import fi.liikennevirasto.digiroad2.linearasset.GraphPartitioner

object RoadAddressLinkPartitioner extends GraphPartitioner {

  def partition(links: Seq[RoadAddressLink]): Seq[Seq[RoadAddressLink]] = {
    val linkGroups = links.groupBy { link => (
      link.roadNumber, link.roadPartNumber, link.trackCode
      )
    }

    val clusters = for (linkGroup <- linkGroups.values.toSeq;
                        cluster <- clusterLinks(linkGroup)) yield cluster

    clusters.map(linksFromCluster)
  }

}

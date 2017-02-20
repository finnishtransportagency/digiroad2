package fi.liikennevirasto.viite.model

import fi.liikennevirasto.digiroad2.linearasset.GraphPartitioner

object RoadAddressLinkPartitioner extends GraphPartitioner {

  def partition(links: Seq[RoadAddressLink]): Seq[Seq[RoadAddressLink]] = {
    val linkGroups = links.groupBy { link => (
      /*
      Ignores the roadLinkType from the linkGroups, to the task 245 -> complementary roads
      */
      //link.anomaly.value, link.roadNumber, link.roadPartNumber, link.trackCode, link.roadLinkType.value
      link.anomaly.value, link.roadNumber, link.roadPartNumber, link.trackCode
      )
    }
    val clusters = for (linkGroup <- linkGroups.values.toSeq;
                        cluster <- clusterLinks(linkGroup)) yield cluster

    clusters.map(linksFromCluster)
  }
}

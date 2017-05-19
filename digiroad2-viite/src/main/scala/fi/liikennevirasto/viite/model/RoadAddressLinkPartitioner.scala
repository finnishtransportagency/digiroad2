package fi.liikennevirasto.viite.model

import fi.liikennevirasto.digiroad2.RoadLinkType
import fi.liikennevirasto.digiroad2.linearasset.GraphPartitioner

object RoadAddressLinkPartitioner extends GraphPartitioner {

  def partition[T <: RoadAddressLinkLike](links: Seq[T]): Seq[Seq[T]] = {
    val linkGroups = links.groupBy { link => (
      link.anomaly.value, link.roadNumber, link.roadPartNumber, link.trackCode, link.roadLinkType.equals(RoadLinkType.FloatingRoadLinkType)
      )
    }
    val clusters = for (linkGroup <- linkGroups.values.toSeq;
                        cluster <- clusterLinks(linkGroup)) yield cluster

    clusters.map(linksFromCluster)
  }
}

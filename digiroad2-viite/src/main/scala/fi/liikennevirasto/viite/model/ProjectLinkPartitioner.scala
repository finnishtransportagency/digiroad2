package fi.liikennevirasto.viite.model

import fi.liikennevirasto.digiroad2.RoadLinkType
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource
import fi.liikennevirasto.digiroad2.linearasset.GraphPartitioner

object ProjectLinkPartitioner extends GraphPartitioner {

  def partition[T <: ProjectAddressLinkLike](links: Seq[T]): Seq[Seq[T]] = {
    val linkGroups = links.groupBy { link => (
      link.anomaly, link.roadNumber, link.roadPartNumber, link.trackCode,
      link.roadLinkType.equals(RoadLinkType.FloatingRoadLinkType), link.roadLinkSource.equals(LinkGeomSource.ComplimentaryLinkInterface), link.roadLinkSource.equals(LinkGeomSource.SuravageLinkInterface),
      link.roadName, link.municipalityCode, link.status, link.sideCode
      )
    }
    val clusters = for (linkGroup <- linkGroups.values.toSeq;
                        cluster <- clusterLinks(linkGroup)) yield cluster
    clusters.map(linksFromCluster)
  }
}

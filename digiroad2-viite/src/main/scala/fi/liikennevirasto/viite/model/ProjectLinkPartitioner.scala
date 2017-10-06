package fi.liikennevirasto.viite.model

import fi.liikennevirasto.digiroad2.RoadLinkType
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource
import fi.liikennevirasto.digiroad2.linearasset.GraphPartitioner

object ProjectLinkPartitioner extends GraphPartitioner {

  def partition[T <: ProjectAddressLinkLike](links: Seq[T]): Seq[Seq[T]] = {
    val (roadsWithoutNumber, roadsWithNumber ) = links.partition(link => link.roadNumber == 0)
    val linkGroupWithNumber = roadsWithNumber.groupBy { link => (link.roadNumber, link.roadPartNumber, link.trackCode, link.status)}
    val linkGroupWithoutNumber = roadsWithoutNumber.groupBy{link => (link.anomaly,
      link.roadLinkType.equals(RoadLinkType.FloatingRoadLinkType), link.roadLinkSource.equals(LinkGeomSource.ComplimentaryLinkInterface), link.roadLinkSource.equals(LinkGeomSource.SuravageLinkInterface),
      link.roadName, link.municipalityCode, link.status)

    }
    val clusters = for (linkGroup <- linkGroupWithNumber.values.toSeq ++ linkGroupWithoutNumber.values.toSeq;
                        cluster <- clusterLinks(linkGroup)) yield cluster
    clusters.map(linksFromCluster)
  }
}

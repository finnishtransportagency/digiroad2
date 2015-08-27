package fi.liikennevirasto.digiroad2.linearasset

import org.geotools.graph.structure.Graph
import org.geotools.graph.structure.basic.BasicEdge
import scala.collection.JavaConversions._

object RoadLinkPartitioner extends GraphPartitioner {
  private def linksFromCluster(cluster: Graph): Seq[VVHRoadLinkWithProperties] = {
    val edges = cluster.getEdges.toList.asInstanceOf[List[BasicEdge]]
    edges.map { edge: BasicEdge =>
      edge.getObject.asInstanceOf[VVHRoadLinkWithProperties]
    }
  }

  def partition(links: Seq[VVHRoadLinkWithProperties]): Seq[Seq[VVHRoadLinkWithProperties]] = {
    val linkGroups = links.groupBy { link => (
      link.functionalClass, link.trafficDirection,
      link.linkType, LinearAsset.roadIdentifierFromRoadLink(link))
    }

    val clusters = for (linkGroup <- linkGroups.values.toSeq;
                        cluster <- clusterLinks(linkGroup)) yield cluster

    clusters.map(linksFromCluster)
  }
}

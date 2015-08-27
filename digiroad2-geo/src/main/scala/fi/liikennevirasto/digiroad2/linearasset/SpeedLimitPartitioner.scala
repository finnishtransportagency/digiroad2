package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.asset.SideCode
import org.geotools.graph.structure.Graph
import org.geotools.graph.structure.basic.BasicEdge

import scala.collection.JavaConversions._

object SpeedLimitPartitioner extends GraphPartitioner {
  private def linksFromCluster(cluster: Graph): Seq[SpeedLimit] = {
    val edges = cluster.getEdges.toList.asInstanceOf[List[BasicEdge]]
    edges.map { edge: BasicEdge =>
      edge.getObject.asInstanceOf[SpeedLimit]
    }
  }

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

package fi.liikennevirasto.digiroad2

import com.vividsolutions.jts.geom.LineSegment
import fi.liikennevirasto.digiroad2.linearasset.{SpeedLimitLink, SpeedLimitDTO}
import org.geotools.graph.build.line.BasicLineGraphGenerator
import org.geotools.graph.structure.Graph
import org.geotools.graph.structure.basic.BasicEdge
import org.geotools.graph.util.graph.GraphPartitioner
import scala.collection.JavaConversions._

object SpeedLimitPartitioner {
  private def clusterLinks(links: Seq[SpeedLimitDTO]): Seq[Graph] = {
    val generator = new BasicLineGraphGenerator(1.0)
    links.foreach { link =>
      val (sp, ep) = GeometryUtils.geometryEndpoints(link.geometry)
      val segment = new LineSegment(sp.x, sp.y, ep.x, ep.y)
      val graphable = generator.add(segment)
      graphable.setObject(link)
    }
    clusterGraph(generator.getGraph)
  }

  private def clusterGraph(graph: Graph): Seq[Graph] = {
    val partitioner = new GraphPartitioner(graph)
    partitioner.partition()
    partitioner.getPartitions.toList.asInstanceOf[List[Graph]]
  }

  private def linksFromCluster(cluster: Graph): Seq[SpeedLimitLink] = {
    val edges = cluster.getEdges.toList.asInstanceOf[List[BasicEdge]]
    edges.map { edge: BasicEdge =>
      val dto = edge.getObject.asInstanceOf[SpeedLimitDTO]
      speedLimitLinkFromDTO(dto)
    }
  }

  private def speedLimitLinkFromDTO(link: SpeedLimitDTO): SpeedLimitLink = {
    SpeedLimitLink(link.assetId, link.mmlId, link.sideCode, link.value,
      link.geometry, link.startMeasure, link.endMeasure, 0, true, link.modifiedBy, link.modifiedDateTime, link.createdBy, link.createdDateTime)
  }

  def partition(links: Seq[SpeedLimitDTO], roadIdentifiers: Map[Long, Either[Int, String]]): Seq[Seq[SpeedLimitLink]] = {
    val (twoWayLinks, oneWayLinks) = links.partition(_.sideCode == 1)
    val linkGroups = twoWayLinks.groupBy { link => (roadIdentifiers.get(link.mmlId), link.value) }
    val (linksToPartition, linksToPass) = linkGroups.partition { case (key, _) => key._1.isDefined }

    val clusters = for (linkGroup <- linksToPartition.values.toSeq;
                        cluster <- clusterLinks(linkGroup)) yield cluster
    val linkPartitions = clusters.map(linksFromCluster)

    linkPartitions ++ linksToPass.values.flatten.map(x => Seq(speedLimitLinkFromDTO(x))) ++ oneWayLinks.map(x => Seq(speedLimitLinkFromDTO(x)))
  }

}

package fi.liikennevirasto.digiroad2

import com.vividsolutions.jts.geom.LineSegment
import fi.liikennevirasto.digiroad2.linearasset.{SpeedLimitLink, SpeedLimitDTO}
import org.geotools.graph.build.line.BasicLineGraphGenerator
import org.geotools.graph.structure.Graph
import org.geotools.graph.structure.basic.BasicEdge
import org.geotools.graph.util.graph.GraphPartitioner
import scala.collection.JavaConversions._

object SpeedLimitPartitioner {
  def partition(links: Seq[SpeedLimitDTO], roadNumbers: Map[Long, Int]): Seq[SpeedLimitLink] = {
    val twoWayLinks = links.filter(_.sideCode == 1)
    val linkGroups = twoWayLinks.groupBy { link => (roadNumbers.get(link.mmlId), link.value) }
    val (linksToPartition, linksToPass) = linkGroups.partition { case (key, _) => key._1.isDefined && key._2.isDefined }

    val ret: Seq[SpeedLimitLink] = linksToPartition.values.zipWithIndex.map { case(groupLinks, groupIndex) =>
      val generator = new BasicLineGraphGenerator(1.0)
      groupLinks.foreach { link =>
        val (sp, ep) = GeometryUtils.geometryEndpoints(link.geometry)
        val segment = new LineSegment(sp.x, sp.y, ep.x, ep.y)
        val graphable = generator.add(segment)
        graphable.setObject(link)
      }
      val graph = generator.getGraph
      val partitioner = new GraphPartitioner(graph)
      partitioner.partition()
      val clusters = partitioner.getPartitions.toList
      clusters.zipWithIndex.map { case(cluster: Graph, clusterIndex: Int) =>
        val edges = cluster.getEdges.toList.asInstanceOf[List[BasicEdge]]
        edges.map { edge: BasicEdge =>
          val dto = edge.getObject.asInstanceOf[SpeedLimitDTO]
          SpeedLimitLink((groupIndex + 1) * 100 + clusterIndex + 10, dto.mmlId, dto.sideCode, dto.value,
            dto.geometry, dto.startMeasure, dto.endMeasure, 0, true)
        }
      }
    }.flatten.flatten.toSeq

    ret ++ linksToPass.values.flatten.toSeq.map { link =>
      SpeedLimitLink(link.assetId, link.mmlId, link.sideCode, link.value,
        link.geometry, link.startMeasure, link.endMeasure,
        0, true)
    }
  }

}

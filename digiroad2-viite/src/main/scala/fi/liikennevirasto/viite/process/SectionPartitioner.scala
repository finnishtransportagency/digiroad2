package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.linearasset.GraphPartitioner
import fi.liikennevirasto.viite.dao.ProjectLink

object SectionPartitioner extends GraphPartitioner {

  def partition[T <: ProjectLink](links: Seq[T]): Seq[Seq[T]] = {
    val linkGroups = links.groupBy { link => (
      link.roadNumber, link.roadPartNumber, link.track
    )
    }
    val clusters = for (linkGroup <- linkGroups.values.toSeq;
                        cluster <- clusterLinks(linkGroup)) yield cluster
    clusters.map(linksFromCluster)
  }
}

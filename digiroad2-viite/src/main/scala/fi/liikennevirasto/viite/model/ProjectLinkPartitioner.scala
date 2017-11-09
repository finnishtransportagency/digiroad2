package fi.liikennevirasto.viite.model

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource
import fi.liikennevirasto.digiroad2.linearasset.GraphPartitioner
import fi.liikennevirasto.viite.dao.LinkStatus

object ProjectLinkPartitioner extends GraphPartitioner {

  def partition[T <: ProjectAddressLinkLike](projectLinks: Seq[T]): Seq[Seq[T]] = {
    val (splitLinks, links) = projectLinks.partition(_.isSplit)
    // Group by suravage link id
    val splitGroups = splitLinks.groupBy(sl =>
      if (sl.roadLinkSource == LinkGeomSource.NormalLinkInterface)
        sl.linkId else sl.connectedLinkId.get)
    val (outside, inProject) = links.partition(_.status == LinkStatus.Unknown)
    val inProjectGroups = inProject.groupBy(l => (l.status, l.roadNumber, l.roadPartNumber, l.trackCode, l.roadType))
    val outsideGroup = outside.groupBy(link => (link.roadLinkSource, link.partitioningName))
    val clusters = for (linkGroup <- inProjectGroups.values.toSeq ++ outsideGroup.values.toSeq;
                        cluster <- clusterLinks(linkGroup, fi.liikennevirasto.viite.MaxDistanceForConnectedLinks)) yield cluster
    clusters.map(linksFromCluster) ++ splitGroups.values.toSeq
  }
}

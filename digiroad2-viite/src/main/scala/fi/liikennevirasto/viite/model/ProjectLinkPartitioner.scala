package fi.liikennevirasto.viite.model

import fi.liikennevirasto.digiroad2.linearasset.GraphPartitioner
import fi.liikennevirasto.viite.dao.LinkStatus

object ProjectLinkPartitioner extends GraphPartitioner {

  def partition[T <: ProjectAddressLinkLike](projectLinks: Seq[T]): Seq[Seq[T]] = {
    val (splittedLinks, links) = projectLinks.partition(_.connectedLinkId.nonEmpty)
    val splittedGroup = splittedLinks.groupBy(sl => (sl.connectedLinkId.get, sl.roadNumber, sl.roadPartNumber))
    val (outside, inProject) = links.partition(_.status == LinkStatus.Unknown)
    val inProjectGroups = inProject.groupBy(l => (l.status, l.roadNumber, l.roadPartNumber, l.trackCode))
    val outsideGroup = outside.groupBy(link => (link.roadLinkSource, link.partitioningName))
    val clusters = for (linkGroup <- inProjectGroups.values.toSeq ++ outsideGroup.values.toSeq ++ splittedGroup.values.toSeq;
                        cluster <- clusterLinks(linkGroup, fi.liikennevirasto.viite.MaxDistanceForConnectedLinks)) yield cluster
    clusters.map(linksFromCluster)
  }
}

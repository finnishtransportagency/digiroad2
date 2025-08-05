package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.asset.SideCode

object LinearAssetPartitioner extends GraphPartitioner {

  /**
   * Partitions a sequence of linear assets into clusters based on shared road-related attributes.
   * Partitioned groups are activated together on the map when a link in the group is clicked.
   *
   * Splits assets into two-way and one-way groups, then clusters them by road identifier,
   * administrative class, value, traffic direction, and other key attributes.
   * Assets without a valid road identifier are returned as single-asset partitions.
   *
   * @tparam T Subtype of PieceWiseLinearAsset.
   * @param links Linear assets to partition.
   * @return A sequence of asset clusters that are selected together in the UI.
   */
  def partition[T <: PieceWiseLinearAsset](assetLinks: Seq[T]): Seq[Seq[T]] = {

    val (twoWayLinks, oneWayLinks) = assetLinks.partition(_.sideCode == SideCode.BothDirections)

    def extractRoadIdentifier(link: PieceWiseLinearAsset): Option[Either[Long, String]] = {

      val roadNumber = link.attributes.get("ROADNUMBER").flatMap {
        case Some(value: Long) => Some(Left(value))
        case None => None
      }

      val roadNameFi = link.attributes.get("ROADNAME_FI").flatMap {
        case Some(value: String) => Some(Right(value))
        case None => None
      }

      val roadNameSe = link.attributes.get("ROADNAME_SE").flatMap {
        case Some(value: String) => Some(Right(value))
        case None => None
      }

      roadNumber.orElse(roadNameFi).orElse(roadNameSe)
    }

    def partitionLinkGroup(linkGroup:  Seq[T]):Seq[Seq[T]] = {
      val grouped = linkGroup.groupBy { link =>
        (extractRoadIdentifier(link), link.administrativeClass, link.value, link.id == 0, link.trafficDirection, link.attributes.get("ROADPARTNUMBER").orElse(None))
      }

      val (linksToPartition, linksToPass) = grouped.partition { case ((roadIdentifier, _, _, _, _, _), _) => roadIdentifier.isDefined }
      val clusters = linksToPartition.values.map(p => {
        clusterLinks(p)
      }).toSeq.flatten

      val linkPartitions = clusters.map(linksFromCluster)
      val result = linkPartitions ++ linksToPass.values.flatten.map(x => Seq(x))
      result
    }

    val twoWayResult = partitionLinkGroup(twoWayLinks)
    val oneWayResult = partitionLinkGroup(oneWayLinks)

    twoWayResult ++ oneWayResult
  }
}

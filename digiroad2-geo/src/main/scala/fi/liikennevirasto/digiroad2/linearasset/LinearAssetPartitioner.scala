package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.asset.SideCode

object LinearAssetPartitioner extends GraphPartitioner {

  /**
   * Partitions a sequence of linear assets by enriching them with road link address data
   * and delegating to a partitioning logic. It enriches each asset temporarily with additional
   * attributes such as road number and road name if a corresponding RoadLink
   * exists. After enrichment, it calls a secondary partitioning method on the enriched assets.
   *
   * @tparam T The specific subtype of PieceWiseLinearAsset.
   * @param assetLinks              A sequence of linear assets to enrichAndPartition.
   * @param roadAddressForPartition A map from link IDs to RoadLink objects,
   *                                used to enrich each asset with road-specific address data.
   * @return A sequence of partitions, where each enrichAndPartition is a sequence of assets of type T.
   */
  def enrichAndPartition[T <: PieceWiseLinearAsset](assetLinks: Seq[T], roadAddressForPartition: Map[String, RoadLink]): Seq[Seq[T]] = {

    val enrichedLinks: Seq[PieceWiseLinearAsset] = assetLinks.map {
      case pwla: PieceWiseLinearAsset =>
        val roadLinkOption = roadAddressForPartition.get(pwla.linkId)
        val additionalAttributes: Map[String, Any] = roadLinkOption match {
          case Some(roadLink) =>
            val attrs = roadLink.attributes
            Map(
              "ROADNUMBER" -> attrs.get("ROADNUMBER"),
              "ROADNAME_FI" -> attrs.get("ROADNAME_FI"),
              "ROADNAME_SE" -> attrs.get("ROADNAME_SE"),
              "ROADPARTNUMBER" -> attrs.get("ROADPARTNUMBER")
            ).collect { case (key, Some(value)) => key -> Some(value) }
          case None => Map.empty
        }

        val mergedAttributes = pwla.attributes ++ additionalAttributes

        pwla.copy(attributes = mergedAttributes)
    }
    partition(enrichedLinks).asInstanceOf[Seq[Seq[T]]]
  }

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

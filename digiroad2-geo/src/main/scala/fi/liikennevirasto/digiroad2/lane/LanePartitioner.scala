package fi.liikennevirasto.digiroad2.lane

import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.linearasset.{GraphPartitioner, RoadLink}


object LanePartitioner extends GraphPartitioner {


  protected def partitionAdditionalLanes(additionalLanesOnRoad: Seq[PieceWiseLane]): Seq[PieceWiseLane] = {
    val additionalLanesMapped = additionalLanesOnRoad.groupBy(lane =>
      lane.laneAttributes.find(_.publicId == "lane_code").get.values.headOption match {
      case Some(laneValue) =>
        laneValue.value
      case _ => false
    })
    additionalLanesMapped.values.toSeq.flatten
  }

  protected def partitionByMainLane(lanes: Seq[PieceWiseLane]): (Seq[PieceWiseLane], Seq[PieceWiseLane]) = {
    val (mainLanes, additionalLanes) = lanes.partition({ lane =>
      lane.laneAttributes.find(_.publicId == "lane_code").get.values.headOption match {
        case Some(laneValue) =>
          LaneNumberOneDigit.isMainLane(laneValue.value.asInstanceOf[Int])
        case _ => false
      }
    })
    (mainLanes, additionalLanes)
  }

  def partitionBySideCodeAndLaneCode(linearAssets: Seq[PieceWiseLane]): Seq[Seq[PieceWiseLane]] = {
    val (lanesOnOneDirectionRoad, lanesOnTwoDirectionRoad) = linearAssets.partition(_.sideCode == SideCode.BothDirections.value)
    val (lanesOnTowards, lanesOnAgainst) = lanesOnTwoDirectionRoad.partition(_.sideCode == SideCode.TowardsDigitizing.value)

    val (mainLanesOnOneDirectionRoad, additionalLanesOnOneDirectionRoad) = partitionByMainLane(lanesOnOneDirectionRoad)
    val (mainLanesOnTowards, additionalLanesOnTowards) = partitionByMainLane(lanesOnTowards)
    val (mainLanesOnAgainst, additionalLanesOnAgainst) = partitionByMainLane(lanesOnAgainst)

    val partitionedAdditionalLanesOne = partitionAdditionalLanes(additionalLanesOnOneDirectionRoad)
    val partitionedAdditionalLanesTowards = partitionAdditionalLanes(additionalLanesOnTowards)
    val partitionedAdditionalLanesAgainst = partitionAdditionalLanes(additionalLanesOnAgainst)

    Seq(mainLanesOnOneDirectionRoad, mainLanesOnTowards, mainLanesOnAgainst, partitionedAdditionalLanesOne,
      partitionedAdditionalLanesTowards, partitionedAdditionalLanesAgainst)

  }

  def partition(lanes: Seq[PieceWiseLane], roadLinks: Map[Long, RoadLink]): Seq[Seq[PieceWiseLane]] = {
    def groupLanes(lanes: Seq[PieceWiseLane]): Seq[Seq[PieceWiseLane]] = {
      val groupedLanesByRoadLinkAndSideCode = lanes.groupBy(lane => (lane.linkId, lane.sideCode))

      val (cutLaneConfiguration, uncutLaneConfiguration) = groupedLanesByRoadLinkAndSideCode.partition { case ((roadLinkId, _), lanes) =>
        roadLinks.get(roadLinkId) match {
          case Some(roadLink) =>
            lanes.exists { lane =>

              //end measures(have max 3 decimal digits) and roadlink length have different number of decimal digits
              val roadLinkLength = Math.round(roadLink.length * 1000).toDouble / 1000

              lane.startMeasure != 0.0d || lane.endMeasure != roadLinkLength
            }
          case _ => false
        }
      }

      val linkGroups = uncutLaneConfiguration.groupBy { case ((roadLinkId, _), lanes) =>
        val allLanesAttributes =
          lanes.flatMap(_.laneAttributes).sortBy { laneProp =>
            val lanePropValue = laneProp.values match {
              case laneProps if laneProps.nonEmpty => laneProps.head.value.toString
              case _ => ""
            }

            (laneProp.publicId, lanePropValue)
          }

        val roadLink = roadLinks.get(roadLinkId)
        val roadIdentifier = roadLink.flatMap(_.roadIdentifier)

        (roadIdentifier, roadLink.map(_.administrativeClass), allLanesAttributes, roadLinkId == 0)
      }

      val (linksToPartition, linksToPass) = linkGroups.partition { case ((roadIdentifier, _, _, _), _) => roadIdentifier.isDefined }
      val clustersAux = linksToPartition.values.map(_.values.flatten.filter(lane =>
        LaneNumberOneDigit.isMainLane(lane.laneAttributes.find(_.publicId == "lane_code").get.values.head.value.asInstanceOf[Int]))
      )

      val clusters = for (linkGroup <- clustersAux.asInstanceOf[Seq[Seq[PieceWiseLane]]];
                          cluster <- clusterLinks(linkGroup)) yield cluster

      clusters.map(linksFromCluster) ++ linksToPass.values.flatMap(_.values).toSeq ++ cutLaneConfiguration.values.toSeq
    }

    val partitionedLanes = partitionBySideCodeAndLaneCode(lanes)
    partitionedLanes.flatMap(lane => groupLanes(lane))
  }

}

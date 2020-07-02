package fi.liikennevirasto.digiroad2.lane

import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.linearasset.{GraphPartitioner, RoadLink}

object LanePartitioner extends GraphPartitioner {

  def partition[T <: Lane](lanes: Seq[T], roadLinks: Map[Long, RoadLink]): Seq[Seq[T]] = {
    def groupLanes(lanes: Seq[T]): Seq[Seq[T]] = {
      val groupedLanesByRoadLinkAndSideCode = lanes.groupBy(lane => (lane.linkId, lane.sideCode))

      val (cutLaneConfiguration, uncutLaneConfiguration) = groupedLanesByRoadLinkAndSideCode.partition { case ((roadLinkId, _), lanes) =>
        roadLinks.get(roadLinkId) match {
          case Some(roadLink) =>
            lanes.exists{lane =>
              val pieceWiseLane = lane.asInstanceOf[PieceWiseLane]

              //end measures(have max 3 decimal digits) and roadlink length have different number of decimal digits
              val roadLinkLength = Math.round(roadLink.length * 1000).toDouble / 1000

              pieceWiseLane.startMeasure != 0.0d || pieceWiseLane.endMeasure != roadLinkLength
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
        LaneNumber.isMainLane(lane.laneAttributes.find(_.publicId == "lane_code").get.values.head.value.asInstanceOf[Int]))
      )

      val clusters = for (linkGroup <- clustersAux.asInstanceOf[Seq[Seq[T]]];
                          cluster <- clusterLinks(linkGroup)) yield cluster

      clusters.map(linksFromCluster) ++ linksToPass.values.flatMap(_.values).toSeq ++ cutLaneConfiguration.values.toSeq
    }

    val (lanesOnOneDirectionRoad, lanesOnTwoDirectionRoad) = lanes.partition(_.sideCode == SideCode.BothDirections.value)
    groupLanes(lanesOnOneDirectionRoad) ++ groupLanes(lanesOnTwoDirectionRoad)
  }

}

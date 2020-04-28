package fi.liikennevirasto.digiroad2.lane

import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.linearasset.{GraphPartitioner, RoadLink}

object LanePartitioner extends GraphPartitioner {

  def partition[T <: Lane](lanes: Seq[T], roadLinks: Map[Long, RoadLink]): Seq[Seq[T]] = {
    def groupLanes(lanes: Seq[T]): Seq[Seq[T]] = {
      val groupedLanesByRoadLinkAndSideCode = lanes.groupBy(lane => (lane.linkId, lane.sideCode))

      val linkGroups = groupedLanesByRoadLinkAndSideCode.groupBy { case ((roadLinkId, _), lanes) =>
        val allLanesAttributes = lanes.flatMap(_.laneAttributes).sortBy(laneProp => (laneProp.publicId, laneProp.values.head.value.toString))
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

      clusters.map(linksFromCluster) ++ linksToPass.values.flatMap(_.values).toSeq
    }

    val (lanesOnOneDirectionRoad, lanesOnTwoDirectionRoad) = lanes.partition(_.sideCode == SideCode.BothDirections.value)
    groupLanes(lanesOnOneDirectionRoad) ++ groupLanes(lanesOnTwoDirectionRoad)
  }

}

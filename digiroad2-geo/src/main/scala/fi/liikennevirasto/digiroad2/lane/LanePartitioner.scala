package fi.liikennevirasto.digiroad2.lane

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{SideCode, TrafficDirection}
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

  //replaces lane if its sideCode is not correct. SideCode is tied to digitizing direction,
  //so two adjacent lanes with same sideCode can be on the opposite sides of the road
  def replaceLanesWithWrongSideCode(roadLinks: Map[Long, RoadLink], allLanes: Seq[PieceWiseLane],
                                    lanes: Seq[PieceWiseLane]): Seq[PieceWiseLane] = {
    val lanesGroupedByRoadIdentifier = lanes.groupBy(lane => {
      val roadLink = roadLinks.get(lane.linkId)
      val roadIdentifier = roadLink.flatMap(_.roadIdentifier)
      roadIdentifier
    })
    val (laneGroupsWithNoIdentifier, laneGroupsWithIdentifier) = lanesGroupedByRoadIdentifier.partition(group => group._1.isEmpty)
    val lanesGroupedWithContinuing = laneGroupsWithIdentifier.map(lanesOnRoad => lanesOnRoad._2.map(lane => {
      val roadIdentifier = lanesOnRoad._1
      val continuingLanes = getContinuingWithIdentifier(lane, roadIdentifier, lanes, roadLinks)
      Map(lane -> continuingLanes)
    })).toSeq

    handleLanes(lanesGroupedWithContinuing,allLanes, roadLinks) ++ laneGroupsWithNoIdentifier.values.flatten
  }

  def handleLanes(lanesGrouped: Seq[Seq[Map[PieceWiseLane, Seq[PieceWiseLane]]]],
                  allLanes: Seq[PieceWiseLane], roadLinks: Map[Long, RoadLink]):Seq[PieceWiseLane] = {
    lanesGrouped.flatMap(lanesOnRoad => {
      //Goes through roads lanes in order recursively. checkLane switches lane to other sideCode lane if necessary
      def getSortedLanes(continuingPoint: Point, sortedLanes: Seq[PieceWiseLane]): Seq[PieceWiseLane] = {
        val nextLane = lanesOnRoad.find(potentialLane => (potentialLane.keys.head.endpoints.head.round() == continuingPoint.round() ||
          potentialLane.keys.head.endpoints.last.round() == continuingPoint.round())
          && !sortedLanes.map(_.id).contains(potentialLane.keys.head.id) && !sortedLanes.map(_.linkId).contains(potentialLane.keys.head.linkId))
        nextLane match {
          case Some(lane) =>
            val checkedLane = checkLane(sortedLanes.last, lane.keys.head, allLanes)
            val nextPoint = checkedLane.endpoints.find(_.round() != continuingPoint.round())
            getSortedLanes(nextPoint.get, sortedLanes ++ Seq(checkedLane))
          case _ => sortedLanes
        }
      }

      //If road has roadLinks with one way trafficDirection and BothDirections, then startingLane must
      //be on a one way roadLink.
      val lanesRoadLinkIds = lanesOnRoad.map(_.keys.head.linkId)
      val lanesRoadLinks = roadLinks.filter(roadLink => lanesRoadLinkIds.contains(roadLink._1))
      val (roadLinksTwoWay, roadLinksOneWay) = lanesRoadLinks.values.partition(roadLink =>
        roadLink.trafficDirection == TrafficDirection.BothDirections)
      val TwoAndOneWayRoadLinks = roadLinksTwoWay.nonEmpty && roadLinksOneWay.nonEmpty
      val oneWayIds = roadLinksOneWay.map(_.linkId).toSeq
      val startingLane = if (TwoAndOneWayRoadLinks) getStartingLaneTwoAndOneWay(lanesOnRoad, oneWayIds)
      else getStartingLane(lanesOnRoad)

      val startingEndPoints = startingLane.keys.head.endpoints
      val continuingEndPoints = startingLane.values.flatten.flatMap(_.endpoints)
      val continuingPoint = continuingEndPoints.find(point =>
        point.round() == startingEndPoints.head.round() || point.round() == startingEndPoints.last.round())
      if (continuingPoint.isDefined) {
        getSortedLanes(continuingPoint.get, startingLane.keys.toSeq)
      }
      else {
        lanesOnRoad.map(_.keys.head)
      }
    })

  }

  def getStartingLaneTwoAndOneWay(lanesOnRoad: Seq[Map[PieceWiseLane, Seq[PieceWiseLane]]],
                                  oneWayIds: Seq[Long]): Map[PieceWiseLane, Seq[PieceWiseLane]] = {
    val startAndEndLane = lanesOnRoad.filter(laneAndContinuingLanes => {
      laneAndContinuingLanes.values.head.size == 1 || laneAndContinuingLanes.values.head.isEmpty
    })
    if (startAndEndLane.isEmpty) {
      lanesOnRoad.head
    }
    else {
      val startLinkId = startAndEndLane.head.keys.head.linkId
      if (oneWayIds.contains(startLinkId)) startAndEndLane.head
      else startAndEndLane.last
    }
  }

  //Returns first lane which has only one continuing lane, in other words, the starting lane for road.
  //For circular roads returns a random first value from lanesOnRoad
  def getStartingLane(lanesOnRoad: Seq[Map[PieceWiseLane, Seq[PieceWiseLane]]]): Map[PieceWiseLane, Seq[PieceWiseLane]] ={
    lanesOnRoad.find(laneAndContinuingLanes => {
      laneAndContinuingLanes.values.head.size == 1 || laneAndContinuingLanes.values.head.isEmpty
    }).getOrElse(lanesOnRoad.head)
  }

  def checkLane(previousLane: PieceWiseLane, currentLane: PieceWiseLane, allLanes: Seq[PieceWiseLane]): PieceWiseLane = {
    val connectionPoint = currentLane.endpoints.find(point =>
      point.round() == previousLane.endpoints.head.round() || point.round() == previousLane.endpoints.last.round())
    if(connectionPoint.isEmpty) currentLane
    else{
      val isSideCodeCorrect = sideCodeCorrect(previousLane, currentLane, connectionPoint.get)

      if (!isSideCodeCorrect) {
        val replacement = allLanes.find(replacementLane =>
          replacementLane.linkId == currentLane.linkId && replacementLane.sideCode != currentLane.sideCode)
        replacement match {
          case Some(replacement) =>
            replacement
          case None => currentLane
        }
      }
      else {
        currentLane
      }
    }
  }

  //Checks if the lanes sideCode is correct compared to previous lane.
  def sideCodeCorrect(previousLane: PieceWiseLane, currentLane: PieceWiseLane, connectionPoint: Point): Boolean ={
    val previousLaneStartPoint = previousLane.endpoints.minBy(_.y).round()
    val previousLaneEndPoint = previousLane.endpoints.maxBy(_.y).round()
    val currentLaneStartPoint = currentLane.endpoints.minBy(_.y).round()
    val currentLaneEndPoint = currentLane.endpoints.maxBy(_.y).round()

    val sameDirection = (previousLaneEndPoint == connectionPoint.round() && currentLaneStartPoint == connectionPoint.round()) ||
      (previousLaneStartPoint == connectionPoint.round() && currentLaneEndPoint == connectionPoint.round())
    if(previousLane.sideCode == currentLane.sideCode){
      sameDirection
    }
    else{
      !sameDirection
    }
  }

  //Returns lanes continuing from from given lane
  def getContinuingWithIdentifier(lane: PieceWiseLane, laneRoadIdentifier: Option[Either[Int,String]],
                                  lanes: Seq[PieceWiseLane], roadLinks: Map[Long, RoadLink]): Seq[PieceWiseLane] = {
    lanes.filter(potentialLane => potentialLane.endpoints.map(point =>
      point.round()).exists(lane.endpoints.map(point =>
      point.round()).contains)
      && potentialLane.id != lane.id &&
      laneRoadIdentifier == roadLinks.values.find(roadLink => roadLink.linkId == potentialLane.linkId).get.roadIdentifier)
  }

  def partition(allLanes: Seq[PieceWiseLane], roadLinks: Map[Long, RoadLink]): Seq[Seq[PieceWiseLane]] = {
    def groupLanes(lanes: Seq[PieceWiseLane]): Seq[Seq[PieceWiseLane]] = {
      val lanesAdjusted = replaceLanesWithWrongSideCode(roadLinks, allLanes, lanes)
      val groupedLanesByRoadLink = lanesAdjusted.groupBy(lane => lane.linkId)

      val (cutLaneConfiguration, uncutLaneConfiguration) = groupedLanesByRoadLink.partition { case (roadLinkId, lanes) =>
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

      val linkGroups = uncutLaneConfiguration.groupBy { case (roadLinkId, lanes) =>
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

    val partitionedLanes = partitionBySideCodeAndLaneCode(allLanes)
    partitionedLanes.flatMap(lane => groupLanes(lane))
  }

}

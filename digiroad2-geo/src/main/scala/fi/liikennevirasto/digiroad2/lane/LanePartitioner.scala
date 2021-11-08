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

  def partition(allLanes: Seq[PieceWiseLane], roadLinks: Map[Long, RoadLink]): Seq[Seq[PieceWiseLane]] = {
    def groupLanes(lanes: Seq[PieceWiseLane]): Seq[Seq[PieceWiseLane]] = {
      def handleLanes(lanesGrouped: Seq[Seq[Map[PieceWiseLane, Seq[PieceWiseLane]]]]):Seq[PieceWiseLane] = {
        def checkLane(previousLane: PieceWiseLane, currentLane: PieceWiseLane): PieceWiseLane = {

          val connectionPoint = currentLane.endpoints.find(point =>
            point.round() == previousLane.endpoints.head.round() || point.round() == previousLane.endpoints.last.round())
          if(connectionPoint.isEmpty) currentLane
          else{
            val previousLaneStartPoint = previousLane.endpoints.minBy(_.y).round()
            val previousLaneEndPoint = previousLane.endpoints.maxBy(_.y).round()
            val currentLaneStartPoint = currentLane.endpoints.minBy(_.y).round()
            val currentLaneEndPoint = currentLane.endpoints.maxBy(_.y).round()

            //Checks if the lanes sideCode is correct compared to previous lane.
            def sideCodeCorrect(): Boolean ={
              if(previousLane.sideCode == currentLane.sideCode){
                (previousLaneEndPoint == connectionPoint.get.round() && currentLaneStartPoint == connectionPoint.get.round()) ||
                  (previousLaneStartPoint == connectionPoint.get.round() && currentLaneEndPoint == connectionPoint.get.round())
              }
              else{
                !((previousLaneEndPoint == connectionPoint.get.round() && currentLaneStartPoint == connectionPoint.get.round()) ||
                  (previousLaneStartPoint == connectionPoint.get.round() && currentLaneEndPoint == connectionPoint.get.round()))
              }
            }
            if (!sideCodeCorrect()) {
              val replacement = allLanes.find(replacementLane => replacementLane.linkId == currentLane.linkId && replacementLane.sideCode != currentLane.sideCode)
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

        lanesGrouped.flatMap(lanesOnRoad => {
          //Goes through roads lanes recursively. checkLane switches lane to other sideCode lane if necessary
          def getSortedLanes(continuingPoint: Point, sortedLanes: Seq[PieceWiseLane]): Seq[PieceWiseLane] = {
            val nextLane = lanesOnRoad.find(potentialLane => (potentialLane.keys.head.endpoints.head.round() == continuingPoint.round() ||
              potentialLane.keys.head.endpoints.last.round() == continuingPoint.round())
              && !sortedLanes.map(_.id).contains(potentialLane.keys.head.id) && !sortedLanes.map(_.linkId).contains(potentialLane.keys.head.linkId))
            nextLane match {
              case Some(lane) =>
                val checkedLane = checkLane(sortedLanes.last, lane.keys.head)
                val nextPoint = checkedLane.endpoints.find(_.round() != continuingPoint.round())
                getSortedLanes(nextPoint.get, sortedLanes ++ Seq(checkedLane))
              case _ => sortedLanes
            }
          }

          //all of this code is for special situations such as: road has one and two way links
          //or road is a circle and has no clear starting point
          //getSortedLanes must start on one direction part of road if possible
          val lanesRoadLinkIds = lanesOnRoad.map(_.keys.head.linkId)
          val lanesRoadLinks = roadLinks.filter(roadLink => lanesRoadLinkIds.contains(roadLink._1))
          val (roadLinksTwoWay, roadLinksOneWay) = lanesRoadLinks.values.partition(roadLink =>
            roadLink.trafficDirection == TrafficDirection.BothDirections)
          val TwoAndOneWayRoadLinks = roadLinksTwoWay.nonEmpty && roadLinksOneWay.nonEmpty
          val oneWayIds = roadLinksOneWay.map(_.linkId).toSeq
          val startingLane = if (TwoAndOneWayRoadLinks) {
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
          } else {
            lanesOnRoad.find(laneAndContinuingLanes => {
              laneAndContinuingLanes.values.head.size == 1 || laneAndContinuingLanes.values.head.isEmpty
            }).getOrElse(lanesOnRoad.head)
          }
          val onlyStartingLaneEndPoints = startingLane.keys.head.endpoints
          val onlyContinuingLanesEndPoints = startingLane.values.flatten.flatMap(_.endpoints)
          val continuingPoint = onlyContinuingLanesEndPoints.find(point =>
            point.round() == onlyStartingLaneEndPoints.head.round() || point.round() == onlyStartingLaneEndPoints.last.round())
          if (continuingPoint.isDefined) {
            getSortedLanes(continuingPoint.get, startingLane.keys.toSeq)
          }
          else {
            lanesOnRoad.map(_.keys.head)
          }
        })

      }

//      Returns option of lane continuing from lane that we are inspecting
      def getContinuingWithIdentifier(lane: PieceWiseLane, laneRoadIdentifier: Option[Either[Int,String]]): Seq[PieceWiseLane] = {
        lanes.filter(potentialLane => potentialLane.endpoints.map(point =>
          point.round()).exists(lane.endpoints.map(point =>
          point.round()).contains)
          && potentialLane.id != lane.id &&
          laneRoadIdentifier == roadLinks.values.find(roadLink => roadLink.linkId == potentialLane.linkId).get.roadIdentifier)
      }

      //replaces lane if its sideCode is not correct. SideCode is tied to digitizing direction,
      //so two adjacent lanes with same sideCode can be on the opposite sides of the road
      def replaceLanesWithWrongSideCode(): Seq[PieceWiseLane] = {

        val lanesGroupedByRoadIdentifier = lanes.groupBy(lane => {
          val roadLink = roadLinks.get(lane.linkId)
          val roadIdentifier = roadLink.flatMap(_.roadIdentifier)
          roadIdentifier
        })
        val (laneGroupsWithNoIdentifier, laneGroupsWithIdentifier) = lanesGroupedByRoadIdentifier.partition(group => group._1 == None)
        val lanesGrouped = laneGroupsWithIdentifier.map(lanesOnRoad => lanesOnRoad._2.map(lane => {
          val roadIdentifier = lanesOnRoad._1
          val continuingLanes = getContinuingWithIdentifier(lane, roadIdentifier)
          Map(lane -> continuingLanes)
        })).toSeq

        handleLanes(lanesGrouped) ++ laneGroupsWithNoIdentifier.values.flatten
      }

      val lanesAdjusted = replaceLanesWithWrongSideCode()
      val groupedLanesByRoadLinkAndSideCode = lanesAdjusted.groupBy(lane => lane.linkId)

      val (cutLaneConfiguration, uncutLaneConfiguration) = groupedLanesByRoadLinkAndSideCode.partition { case (roadLinkId, lanes) =>
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

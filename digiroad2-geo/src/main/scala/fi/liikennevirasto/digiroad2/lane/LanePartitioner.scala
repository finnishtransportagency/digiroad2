package fi.liikennevirasto.digiroad2.lane

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.asset.TrafficDirection.BothDirections
import fi.liikennevirasto.digiroad2.linearasset.{GraphPartitioner, RoadLink}

import scala.annotation.tailrec


object LanePartitioner extends GraphPartitioner {

  case class LaneWithContinuingLanes(lane: PieceWiseLane, continuingLanes: Seq[PieceWiseLane])

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
  //Lanes must be partitioned like this before further operations, so that geotools.GraphPartitioner doesn't break
  def partitionBySideCodeAndLaneCode(linearAssets: Seq[PieceWiseLane], roadLinks: Map[Long, RoadLink]): Seq[Seq[PieceWiseLane]] = {
    val (lanesOnOneDirection, lanesOnTwoDirection) = linearAssets.partition(lane =>
      roadLinks(lane.linkId).trafficDirection.value != BothDirections.value)
    val (mainLanesOnOneDirection, additionalLanesOnOneDirection) = partitionByMainLane(lanesOnOneDirection)

    val (lanesBothDirections, lanesOneDirection) = lanesOnTwoDirection.partition(_.sideCode == SideCode.BothDirections.value)
    val (lanesTowards, lanesAgainst) = lanesOneDirection.partition(_.sideCode == SideCode.TowardsDigitizing.value)

    val (mainLanesBothDirections, additionalLanesBothDirections) = partitionByMainLane(lanesBothDirections)
    val (mainLanesTowards, additionalLanesTowards) = partitionByMainLane(lanesTowards)
    val (mainLanesAgainst, additionalLanesAgainst) = partitionByMainLane(lanesAgainst)

    Seq(mainLanesOnOneDirection, additionalLanesOnOneDirection, mainLanesBothDirections,
      mainLanesTowards, mainLanesAgainst, additionalLanesBothDirections,
      additionalLanesTowards, additionalLanesAgainst)

  }

  //Returns lanes continuing from from given lane
  def getContinuingWithIdentifier(lane: PieceWiseLane, laneRoadIdentifier: Option[Either[Int,String]],
                                  lanes: Seq[PieceWiseLane], roadLinks: Map[Long, RoadLink]): Seq[PieceWiseLane] = {
    lanes.filter(potentialLane =>
      potentialLane.endpoints.map(point =>
      point.round()).exists(lane.endpoints.map(point =>
      point.round()).contains)
      && potentialLane.id != lane.id &&
      laneRoadIdentifier == roadLinks(potentialLane.linkId).roadIdentifier &&
        potentialLane.laneAttributes.find(_.publicId == "lane_code") == lane.laneAttributes.find(_.publicId == "lane_code"))
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

  //Returns first lane which has only one continuing lane, in other words, the starting lane for road.
  //For circular roads returns a random first value from lanesOnRoad
  def getStartingLane(lanesOnRoad: Seq[LaneWithContinuingLanes]): LaneWithContinuingLanes ={
    lanesOnRoad.find(laneAndContinuingLanes => {
      laneAndContinuingLanes.continuingLanes.size == 1 || laneAndContinuingLanes.continuingLanes.isEmpty
    }).getOrElse(lanesOnRoad.head)
  }

  def handleLanes(lanesOnRoad:Seq[LaneWithContinuingLanes], allLanes: Seq[PieceWiseLane]):Seq[PieceWiseLane] = {
      //Goes through roads lanes in order recursively. checkLane switches lane to other sideCode lane if necessary
      @tailrec
      def getSortedLanes(continuingPoint: Point, sortedLanes: Seq[PieceWiseLane]): Seq[PieceWiseLane] = {
        val nextLane = lanesOnRoad.find(potentialLane => (potentialLane.lane.endpoints.head.round() == continuingPoint.round() ||
          potentialLane.lane.endpoints.last.round() == continuingPoint.round())
          && !sortedLanes.map(_.id).contains(potentialLane.lane.id) && !sortedLanes.map(_.linkId).contains(potentialLane.lane.linkId))
        nextLane match {
          case Some(laneWithContinuing) =>
            val checkedLane = checkLane(sortedLanes.last, laneWithContinuing.lane, allLanes)
            val nextPoint = checkedLane.endpoints.find(_.round() != continuingPoint.round())
            getSortedLanes(nextPoint.get, sortedLanes ++ Seq(checkedLane))
          case _ => sortedLanes
        }
      }

      val startingLane = getStartingLane(lanesOnRoad)
      val startingEndPoints = startingLane.lane.endpoints
      val continuingEndPoints = startingLane.continuingLanes.flatMap(_.endpoints)
      val continuingPoint = continuingEndPoints.find(point =>
        point.round() == startingEndPoints.head.round() || point.round() == startingEndPoints.last.round())
      if (continuingPoint.isDefined) {
        getSortedLanes(continuingPoint.get, Seq(startingLane.lane))
      }
      else {
        lanesOnRoad.map(_.lane)
      }
  }

  //replaces lane if its sideCode is not correct. SideCode is tied to digitizing direction,
  //so two adjacent lanes with same sideCode can be on the opposite sides of the road
  //Algorithm for replacing incorrect lanes:
  // 1. Group lanes by roadIdentifier and sideCode
  // 2. Find continuing lanes for each lane
  // 3. Find starting lane (Lane which has only one continuing lane)
  // 4. Compare starting lane to it's continuing lane
  // 5. If sideCodes are equal and lanes' connection point is previous lane's ending point and
  // current lane's starting point or vice versa then sideCode is OK
  // 6. If not OK, find lane with same linkId and different sideCode compared to current lane
  // and replace currentLane with it
  // 7. Repeat steps 4 to 6 until there is no next lane
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
      LaneWithContinuingLanes(lane, continuingLanes)
    })).toSeq

    lanesGroupedWithContinuing.flatMap(lanesOnRoad =>
      handleLanes(lanesOnRoad, allLanes))++ laneGroupsWithNoIdentifier.values.flatten
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

    val partitionedLanes = partitionBySideCodeAndLaneCode(allLanes, roadLinks)
    partitionedLanes.flatMap(lane => groupLanes(lane))
  }

}

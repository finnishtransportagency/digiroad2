package fi.liikennevirasto.digiroad2.lane

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.TrafficDirection.BothDirections
import fi.liikennevirasto.digiroad2.linearasset.RoadLink

import scala.annotation.tailrec


object LanePartitioner {

  case class LaneWithContinuingLanes(lane: PieceWiseLane, continuingLanes: Seq[PieceWiseLane])

  //Returns lanes continuing from from given lane
  def getContinuingWithIdentifier(lane: PieceWiseLane, laneRoadIdentifier: Option[Either[Int,String]],
                                  lanes: Seq[PieceWiseLane], roadLinks: Map[Long, RoadLink],
                                  SideCodesCorrected: Boolean = false): Seq[PieceWiseLane] = {
    val continuingLanes = lanes.filter(potentialLane =>
      potentialLane.endpoints.map(_.round()).exists(lane.endpoints.map(_.round()).contains)
        && potentialLane.id != lane.id &&
        laneRoadIdentifier == roadLinks(potentialLane.linkId).roadIdentifier &&
        potentialLane.laneAttributes.find(_.publicId == "lane_code") == lane.laneAttributes.find(_.publicId == "lane_code"))
    if(SideCodesCorrected) continuingLanes
    else continuingLanes.filter(_.sideCode == lane.sideCode)
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
    else {
      val isSideCodeCorrect = sideCodeCorrect(previousLane, currentLane, connectionPoint.get)

      if (!isSideCodeCorrect) {
        val replacement = allLanes.find(replacementLane =>
          replacementLane.linkId == currentLane.linkId && replacementLane.sideCode != currentLane.sideCode &&
          replacementLane.laneAttributes.find(_.publicId == "lane_code") == currentLane.laneAttributes.find(_.publicId == "lane_code"))
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
  def getStartingLanes(lanesOnRoad: Seq[LaneWithContinuingLanes]): Seq[LaneWithContinuingLanes] ={
    val startingLanes = lanesOnRoad.filter(laneAndContinuingLanes => {
      laneAndContinuingLanes.continuingLanes.size == 1 || laneAndContinuingLanes.continuingLanes.isEmpty
    })
    if(startingLanes.isEmpty) Seq(lanesOnRoad.head)
    else startingLanes
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

      val startingLane = getStartingLanes(lanesOnRoad).head
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

  def getConnectedLanes(connectedLanes: Seq[LaneWithContinuingLanes], usedLanes: Seq[Long], laneGroup: Seq[LaneWithContinuingLanes]): Seq[LaneWithContinuingLanes] = {
    val connectedIds = connectedLanes.map(_.lane.id)
    val nextLane = laneGroup.find(laneWithContinuing => { laneWithContinuing.continuingLanes.contains(connectedLanes.last.lane) &&
      !connectedIds.contains(laneWithContinuing.lane.id)
    })
    nextLane match {
      case Some(nextLane) => getConnectedLanes(connectedLanes :+ nextLane, usedLanes :+ nextLane.lane.id, laneGroup)
      case _ => connectedLanes
    }
  }

  //Returns lanes grouped by corrected sideCode, laneCode, additional lanes and connection (lanes in group must
  // be geometrically connected together)
  def partition(allLanes: Seq[PieceWiseLane], roadLinks: Map[Long, RoadLink]): Seq[Seq[PieceWiseLane]] = {

    //Groups lanes by roadIdentifier, sideCode, LaneCode, and other lanes on link.
    //Makes sure that all the lanes in group are connected and there are no gaps in lane selection
    def groupLanes(lanes: Seq[PieceWiseLane]): Seq[Seq[PieceWiseLane]] = {
      val lanesGrouped = lanes.groupBy(lane => {
        val roadLink = roadLinks.get(lane.linkId)
        val roadIdentifier = roadLink.flatMap(_.roadIdentifier)
        val laneCode = lane.laneAttributes.find(_.publicId == "lane_code")
        (roadIdentifier, lane.sideCode, laneCode)
      })
      val (laneGroupsWithNoIdentifier, laneGroupsWithIdentifier) = lanesGrouped.partition(group => group._1._1.isEmpty)
      val lanesGroupedWithContinuing = laneGroupsWithIdentifier.map(lanesOnRoad => lanesOnRoad._2.map(lane => {
        val roadIdentifier = lanesOnRoad._1._1
        val continuingLanes = getContinuingWithIdentifier(lane, roadIdentifier, lanesOnRoad._2, roadLinks)
        LaneWithContinuingLanes(lane, continuingLanes)
      })).toSeq

      val lanesGroupedWithCorrectSideCode = lanesGroupedWithContinuing.map(lanesOnRoad =>
        handleLanes(lanesOnRoad, allLanes))

      val partitionedByAdditional = lanesGroupedWithCorrectSideCode.flatMap(_.groupBy(lane => {
        val lanesOnLink = lanes.filter(potentialLane => potentialLane.linkId == lane.linkId &&
          potentialLane.sideCode == lane.sideCode)
        val laneProperties = lanesOnLink.flatMap(_.laneAttributes.find(_.publicId == "lane_code"))
        val lanePropValues = laneProperties.map(_.values)
        val laneCodesOnLink = lanePropValues.flatten.map(_.value.asInstanceOf[Int]).sorted.distinct
        laneCodesOnLink
      }).values)

      val partitionedLanesWithContinuing = partitionedByAdditional.map(laneGroup => laneGroup.map(lane => {
        val roadIdentifier = roadLinks(lane.linkId).roadIdentifier
        val continuingLanes = getContinuingWithIdentifier(lane, roadIdentifier, laneGroup, roadLinks, true)
        LaneWithContinuingLanes(lane, continuingLanes)
      }))

      val connectedGroups = partitionedLanesWithContinuing.flatMap(laneGroup => {
        val startingLanes = getStartingLanes(laneGroup)
        val connectedLanes = startingLanes.map(startingLane => {
          getConnectedLanes(Seq(startingLane), Seq(startingLane.lane.id), laneGroup).sortBy(_.lane.id)
        }).distinct
        connectedLanes.map(_.map(_.lane))
      })

      val noRoadIdentifier = laneGroupsWithNoIdentifier.values.flatten.map(lane => Seq(lane))
      connectedGroups ++ noRoadIdentifier
    }
    val (lanesOnOneDirectionLink, lanesOnTwoDirectionLink) = allLanes.partition(lane =>
      roadLinks(lane.linkId).trafficDirection.value != BothDirections.value)
    groupLanes(lanesOnOneDirectionLink) ++ groupLanes(lanesOnTwoDirectionLink)
  }
}

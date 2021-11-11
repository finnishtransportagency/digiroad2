package fi.liikennevirasto.digiroad2.lane

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.TrafficDirection.{AgainstDigitizing, BothDirections, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, Motorway, State, TrafficDirection}
import fi.liikennevirasto.digiroad2.lane.LanePartitioner._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import org.scalatest._

class LanePartitionerSpec extends FunSuite with Matchers {

  def createPieceWiseLane(id: Long, linkId: Long, sideCode: Int, laneCode: Int): PieceWiseLane = {
    PieceWiseLane(id, linkId, sideCode, false, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 0d, 10d, Set(Point(0.0, 0.0),
      Point(10.0, 0.0)), None, None, None, None, 0l, None, AdministrativeClass(1), Seq(LaneProperty("lane_code", Seq(LanePropertyValue(laneCode)))))
  }

  def createLaneWithGeometry(id: Long, linkId: Long, sideCode: Int, startPoint: Point, endPoint: Point): PieceWiseLane = {
    PieceWiseLane(id, linkId, sideCode, false, Seq(startPoint, endPoint), 0d, 1d, Set(startPoint,endPoint),
      None, None, None, None, 0l, None, AdministrativeClass(1), Seq(LaneProperty("lane_code", Seq(LanePropertyValue(1)))))
  }

  def createRoadLink(id: Long, geometry: Seq[Point], trafficDirection: TrafficDirection, roadIdentifier: String): RoadLink ={
    RoadLink(id,geometry, 1.0, State, 1, trafficDirection, Motorway, None, None, Map("ROADNAME_FI" -> roadIdentifier))
  }

  def createLanes(): Seq[PieceWiseLane] = {
    val mainLaneBothDirectionsA = createPieceWiseLane(1l, 0l, 1, 1)
    val mainLaneBothDirectionsB = createPieceWiseLane(2l, 0l,1, 1)
    val mainLaneTowards = createPieceWiseLane(3l, 1l, 2, 1)
    val firstLeftAdditionalTowards = createPieceWiseLane(4l, 1l, 2, 2)
    val firstRightAdditionalTowards = createPieceWiseLane(5l, 1l, 2, 3)

    Seq(mainLaneBothDirectionsA, mainLaneBothDirectionsB, mainLaneTowards, firstLeftAdditionalTowards, firstRightAdditionalTowards)
  }

  def createLanesWithGeometry(): Seq[PieceWiseLane] = {
    val lane0 = createLaneWithGeometry(0, 1 ,3,Point(0.0,0.0), Point(1.0, 1.0))
    val lane1 = createLaneWithGeometry(1, 1, 2,Point(0.0,0.0), Point(1.0, 1.0))

    val lane2 = createLaneWithGeometry(2, 2, 2,Point(1.0,1.0), Point(2.0, 2.0))
    val lane3 = createLaneWithGeometry(3, 2 ,3,Point(1.0,1.0), Point(2.0, 2.0))

    val lane4 = createLaneWithGeometry(4, 3 ,3,Point(2.0,2.0), Point(2.0, 3.0))
    val lane5 = createLaneWithGeometry(5, 3 ,2,Point(2.0,2.0), Point(2.0, 3.0))

    Seq(lane0, lane1,lane2,lane3,lane4,lane5)
  }

  test("Lanes should be partitioned by side code and lanecode and roadLink trafficDirection") {
    val lanes = createLanes()
    val roadLink0 = createRoadLink(0l, Seq(Point(0.0,0.0), Point(1.0,1.0)), BothDirections,"testiKatu")
    val roadLink1 = createRoadLink(1l, Seq(Point(4.0,4.0), Point(2.0,2.0)), TowardsDigitizing,"testiKatu")
    val roadLinks = Seq(roadLink0, roadLink1).groupBy(_.linkId).mapValues(_.head)
    val lanesPartitioned = partitionBySideCodeAndLaneCode(lanes, roadLinks)

    lanesPartitioned.size should equal(9)
    lanesPartitioned(0).size should equal(1)
    lanesPartitioned(2).size should equal(2)
    lanesPartitioned(3).size should equal(2)
    lanesPartitioned(5).size should equal(0)
  }

  test("Lanes should be partitioned to two groups according to correct sideCode") {
    val roadLink1 = createRoadLink(1, Seq(Point(0.0,0.0), Point(1.0, 1.0)), BothDirections, "testiKatu")
    val roadLink2 = createRoadLink(2, Seq(Point(1.0,1.0), Point(2.0, 2.0)), BothDirections, "testiKatu")
    val roadLink3 = createRoadLink(3, Seq(Point(2.0,2.0), Point(2.0, 1.0)), BothDirections, "testiKatu")

    val roadLinksMapped = Seq(roadLink1, roadLink2, roadLink3).groupBy(_.linkId).mapValues(_.head)
    val lanes = createLanesWithGeometry()

    val result = partition(lanes, roadLinksMapped)
    result.size should equal(2)
    result(0).contains(lanes(1)) should equal(true)
    result(0).contains(lanes(2)) should equal(true)
    result(0).contains(lanes(5)) should equal(true)

    result(1).contains(lanes(3)) should equal(true)
    result(1).contains(lanes(0)) should equal(true)
    result(1).contains(lanes(4)) should equal(true)
  }

  test("startingLane should be either one of the last lanes") {
    val lane0 = createLaneWithGeometry(1, 1, 2,Point(0.0,0.0), Point(1.0, 1.0))
    val lane1 = createLaneWithGeometry(2, 2, 2,Point(1.0,1.0), Point(2.0, 2.0))
    val lane2 = createLaneWithGeometry(5, 3 ,2,Point(2.0,2.0), Point(2.0, 3.0))

    val lanesWithContinuing = Seq(LaneWithContinuingLanes(lane0, Seq(lane1)), LaneWithContinuingLanes(lane1, Seq(lane2, lane0)),
      LaneWithContinuingLanes(lane2, Seq(lane1)))
    val startingLane = getStartingLane(lanesWithContinuing)
    Seq(lane0, lane2).contains(startingLane.lane) should equal(true)
  }

  test("On a circular road startingLane should be first lane in Seq") {
    val lane0 = createLaneWithGeometry(1, 1, 2,Point(0.0,0.0), Point(1.0, 1.0))
    val lane1 = createLaneWithGeometry(2, 2, 2,Point(1.0,1.0), Point(2.0, 2.0))
    val lane2 = createLaneWithGeometry(5, 3 ,2,Point(2.0,2.0), Point(2.0, 3.0))
    val lane3 = createLaneWithGeometry(5, 3 ,2,Point(2.0,3.0), Point(0.0, 0.0))

    val lanesWithContinuing = Seq(LaneWithContinuingLanes(lane0, Seq(lane1, lane3)), LaneWithContinuingLanes(lane1, Seq(lane2, lane0)),
      LaneWithContinuingLanes(lane2, Seq(lane1, lane3)), LaneWithContinuingLanes(lane3, Seq(lane2, lane0)))
    val startingLane = getStartingLane(lanesWithContinuing)
    startingLane should equal(lanesWithContinuing.head)
  }

  test("Lane with different sideCode but same linkId should be returned") {
    val previousLane = createLaneWithGeometry(1, 1, 2,Point(0.0,0.0), Point(1.0, 1.0))
    val currentLane = createLaneWithGeometry(2, 2, 2,Point(1.0,1.0), Point(2.0, 0.0))
    val differentSideCodeLane = createLaneWithGeometry(5, 2 ,3,Point(1.0,1.0), Point(2.0, 0.0))
    val lanes = Seq(previousLane, currentLane, differentSideCodeLane)
    val correctedLane = checkLane(previousLane, currentLane, lanes)

    correctedLane should equal(differentSideCodeLane)
  }

  test("sideCodeCorrect should return false") {
    val previousLane = createLaneWithGeometry(1, 1, 2,Point(0.0,0.0), Point(1.0, 1.0))
    val currentLane = createLaneWithGeometry(2, 2, 2,Point(1.0,1.0), Point(2.0, 0.0))
    val connectionPoint = currentLane.endpoints.find(point =>
      point.round() == previousLane.endpoints.head.round() || point.round() == previousLane.endpoints.last.round()).get

    sideCodeCorrect(previousLane, currentLane, connectionPoint) should equal(false)
  }

}

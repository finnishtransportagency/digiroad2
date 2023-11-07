package fi.liikennevirasto.digiroad2.lane

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.TrafficDirection.{AgainstDigitizing, BothDirections, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, Motorway, State, TrafficDirection}
import fi.liikennevirasto.digiroad2.lane.LanePartitioner._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import org.scalatest._
import java.util.UUID
import scala.util.Random

class LanePartitionerSpec extends FunSuite with Matchers {
  private def generateRandomLinkId(): String = s"${UUID.randomUUID()}:${Random.nextInt(100)}"

  val linkId1: String = generateRandomLinkId()
  val linkId2: String = generateRandomLinkId()
  val linkId3: String = generateRandomLinkId()
  val linkId4: String = generateRandomLinkId()

  def createPieceWiseLane(id: Long, linkId: String, sideCode: Int, laneCode: Int): PieceWiseLane = {
    PieceWiseLane(id, linkId, sideCode, false, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 0d, 10d, Set(Point(0.0, 0.0),
      Point(10.0, 0.0)), None, None, None, None, 0l, None, AdministrativeClass(1), Seq(LaneProperty("lane_code", Seq(LanePropertyValue(laneCode)))))
  }

  def createLaneWithGeometry(id: Long, linkId: String, sideCode: Int, startPoint: Point, endPoint: Point): PieceWiseLane = {
    PieceWiseLane(id, linkId, sideCode, false, Seq(startPoint, endPoint), 0d, 1d, Set(startPoint,endPoint),
      None, None, None, None, 0l, None, AdministrativeClass(1), Seq(LaneProperty("lane_code", Seq(LanePropertyValue(1)))))
  }

  def createRoadLink(id: String, geometry: Seq[Point], trafficDirection: TrafficDirection, roadIdentifier: String): RoadLink ={
    RoadLink(id,geometry, 1.0, State, 1, trafficDirection, Motorway, None, None, Map("ROADNAME_FI" -> roadIdentifier))
  }

  def createLanes(): Seq[PieceWiseLane] = {
    val mainLaneBothDirectionsA = createPieceWiseLane(1l, linkId1, 1, 1)
    val mainLaneBothDirectionsB = createPieceWiseLane(2l, linkId1,1, 1)
    val mainLaneTowards = createPieceWiseLane(3l, linkId2, 2, 1)
    val firstLeftAdditionalTowards = createPieceWiseLane(4l, linkId2, 2, 2)
    val firstRightAdditionalTowards = createPieceWiseLane(5l, linkId2, 2, 3)

    Seq(mainLaneBothDirectionsA, mainLaneBothDirectionsB, mainLaneTowards, firstLeftAdditionalTowards, firstRightAdditionalTowards)
  }

  def createLanesWithGeometry(): Seq[PieceWiseLane] = {
    val lane0 = createLaneWithGeometry(0, linkId2 ,3,Point(0.0,0.0), Point(1.0, 1.0))
    val lane1 = createLaneWithGeometry(1, linkId2, 2,Point(0.0,0.0), Point(1.0, 1.0))

    val lane2 = createLaneWithGeometry(2, linkId3, 2,Point(1.0,1.0), Point(2.0, 2.0))
    val lane3 = createLaneWithGeometry(3, linkId3 ,3,Point(1.0,1.0), Point(2.0, 2.0))

    val lane4 = createLaneWithGeometry(4, linkId4 ,3,Point(2.0,2.0), Point(2.0, 3.0))
    val lane5 = createLaneWithGeometry(5, linkId4 ,2,Point(2.0,2.0), Point(2.0, 3.0))

    Seq(lane0, lane1,lane2,lane3,lane4,lane5)
  }

  test("Lanes should be partitioned to two groups according to correct sideCode") {
    val roadLink1 = createRoadLink(linkId2, Seq(Point(0.0,0.0), Point(1.0, 1.0)), BothDirections, "testiKatu")
    val roadLink2 = createRoadLink(linkId3, Seq(Point(1.0,1.0), Point(2.0, 2.0)), BothDirections, "testiKatu")
    val roadLink3 = createRoadLink(linkId4, Seq(Point(2.0,2.0), Point(2.0, 1.0)), BothDirections, "testiKatu")

    val roadLinksMapped = Seq(roadLink1, roadLink2, roadLink3).groupBy(_.linkId).mapValues(_.head)
    val lanes = createLanesWithGeometry()
    val lanesGroupedById = lanes.groupBy(_.id)

    val result = partition(lanes, roadLinksMapped)
    result.size should equal(2)
    result.exists(group => group.map(_.id).forall(id => Set(1L, 2L, 5L).contains(id))) should equal(true)
    result.exists(group => group.map(_.id).forall(id => Set(3L, 0L, 4L).contains(id))) should equal(true)
  }

  test("startingLane should be either one of the last lanes") {
    val lane0 = createLaneWithGeometry(1, linkId2, 2,Point(0.0,0.0), Point(1.0, 1.0))
    val lane1 = createLaneWithGeometry(2, linkId3, 2,Point(1.0,1.0), Point(2.0, 2.0))
    val lane2 = createLaneWithGeometry(5, linkId4 ,2,Point(2.0,2.0), Point(2.0, 3.0))

    val lanesWithContinuing = Seq(LaneWithContinuingLanes(lane0, Seq(lane1)), LaneWithContinuingLanes(lane1, Seq(lane2, lane0)),
      LaneWithContinuingLanes(lane2, Seq(lane1)))
    val startingLane = getStartingLanes(lanesWithContinuing).head
    Seq(lane0, lane2).contains(startingLane.lane) should equal(true)
  }

  test("On a circular road startingLane should be first lane in Seq") {
    val lane0 = createLaneWithGeometry(1, linkId2, 2,Point(0.0,0.0), Point(1.0, 1.0))
    val lane1 = createLaneWithGeometry(2, linkId3, 2,Point(1.0,1.0), Point(2.0, 2.0))
    val lane2 = createLaneWithGeometry(5, linkId4 ,2,Point(2.0,2.0), Point(2.0, 3.0))
    val lane3 = createLaneWithGeometry(5, linkId4 ,2,Point(2.0,3.0), Point(0.0, 0.0))

    val lanesWithContinuing = Seq(LaneWithContinuingLanes(lane0, Seq(lane1, lane3)), LaneWithContinuingLanes(lane1, Seq(lane2, lane0)),
      LaneWithContinuingLanes(lane2, Seq(lane1, lane3)), LaneWithContinuingLanes(lane3, Seq(lane2, lane0)))
    val startingLane = getStartingLanes(lanesWithContinuing).head
    startingLane should equal(lanesWithContinuing.head)
  }

  test("Lane with different sideCode but same linkId should be returned") {
    val previousLane = createLaneWithGeometry(1, linkId2, 2,Point(0.0,0.0), Point(1.0, 1.0))
    val currentLane = createLaneWithGeometry(2, linkId3, 2,Point(1.0,1.0), Point(2.0, 0.0))
    val differentSideCodeLane = createLaneWithGeometry(5, linkId3 ,3,Point(1.0,1.0), Point(2.0, 0.0))
    val lanes = Seq(previousLane, currentLane, differentSideCodeLane)
    val correctedLane = checkLane(previousLane, currentLane, lanes)

    correctedLane should equal(differentSideCodeLane)
  }

  test("sideCodeCorrect should return false") {
    val previousLane = createLaneWithGeometry(1, linkId2, 2,Point(0.0,0.0), Point(1.0, 1.0))
    val currentLane = createLaneWithGeometry(2, linkId3, 2,Point(1.0,1.0), Point(2.0, 0.0))
    val connectionPoint = currentLane.endpoints.find(point =>
      point.round() == previousLane.endpoints.head.round() || point.round() == previousLane.endpoints.last.round()).get

    sideCodeCorrect(previousLane, currentLane, connectionPoint) should equal(false)
  }

}

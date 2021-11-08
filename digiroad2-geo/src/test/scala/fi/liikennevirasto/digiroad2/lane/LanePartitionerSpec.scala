package fi.liikennevirasto.digiroad2.lane

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.TrafficDirection.BothDirections
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, Motorway, State, TrafficDirection}
import fi.liikennevirasto.digiroad2.lane.LaneNumberOneDigit.MainLane
import fi.liikennevirasto.digiroad2.lane.LanePartitioner.{partition, partitionBySideCodeAndLaneCode}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import org.scalatest._

class LanePartitionerSpec extends FunSuite with Matchers {

  def createPieceWiseLane(id: Long, sideCode: Int, laneCode: Int): PieceWiseLane = {
    PieceWiseLane(id, 0l, sideCode, false, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 0d, 10d, Set(Point(0.0, 0.0),
      Point(10.0, 0.0)), None, None, None, None, 0l, None, AdministrativeClass(1), Seq(LaneProperty("lane_code", Seq(LanePropertyValue(laneCode)))))
  }

  def createLaneWithGeometry(id: Long, linkId: Long, sideCode: Int, startPoint: Point, endPoint: Point): PieceWiseLane = {
    PieceWiseLane(id, linkId, sideCode, false, Seq(startPoint, endPoint), 0d, 1d, Set(startPoint,endPoint),
      None, None, None, None, 0l, None, AdministrativeClass(1), Seq(LaneProperty("lane_code", Seq(LanePropertyValue(1)))))
  }

  def createRoadLink(id: Long, geometry: Seq[Point], roadIdentifier: String): RoadLink ={
    RoadLink(id,geometry, 1.0, State, 1, BothDirections, Motorway, None, None, Map("ROADNAME_FI" -> roadIdentifier))
  }

  def createLanes(): Seq[PieceWiseLane] = {
    val mainLaneBothDirectionsA = createPieceWiseLane(1l, 1, 1)
    val mainLaneBothDirectionsB = createPieceWiseLane(2l, 1, 1)
    val mainLaneTowards = createPieceWiseLane(3l, 2, 1)
    val firstLeftAdditionalTowards = createPieceWiseLane(4l, 2, 2)
    val firstRightAdditionalTowards = createPieceWiseLane(5l, 2, 3)

    Seq(mainLaneBothDirectionsA, mainLaneBothDirectionsB, mainLaneTowards, firstLeftAdditionalTowards, firstRightAdditionalTowards)
  }

  test("Lanes should be partitioned by side code and lanecode") {
    val lanes = createLanes()
    val lanesPartitioned = partitionBySideCodeAndLaneCode(lanes)

    lanesPartitioned.size should equal(6)
    lanesPartitioned(0).size should equal(2)
    lanesPartitioned(1).size should equal(1)
    lanesPartitioned(4).size should equal(2)
  }

  test("Lanes should be partitioned to two groups according to correct sideCode") {
    val lane0 = createLaneWithGeometry(0, 1 ,3,Point(0.0,0.0), Point(1.0, 1.0))
    val lane1 = createLaneWithGeometry(1, 1, 2,Point(0.0,0.0), Point(1.0, 1.0))
    val lane2 = createLaneWithGeometry(2, 2, 2,Point(1.0,1.0), Point(2.0, 2.0))
    val lane3 = createLaneWithGeometry(3, 2 ,3,Point(1.0,1.0), Point(2.0, 2.0))
    val lane4 = createLaneWithGeometry(4, 3 ,3,Point(2.0,2.0), Point(2.0, 1.0))
    val lane5 = createLaneWithGeometry(5, 3 ,2,Point(2.0,2.0), Point(2.0, 1.0))


    val roadLink1 = createRoadLink(1, Seq(Point(0.0,0.0), Point(1.0, 1.0)), "testiKatu")
    val roadLink2 = createRoadLink(2, Seq(Point(1.0,1.0), Point(2.0, 2.0)), "testiKatu")
    val roadLink3 = createRoadLink(3, Seq(Point(2.0,2.0), Point(2.0, 1.0)), "testiKatu")

    val roadLinksMapped = Seq(roadLink1, roadLink2, roadLink3).groupBy(_.linkId).mapValues(_.head)
    val allLanes = Seq(lane0, lane1,lane2,lane3,lane4,lane5)

    val result = partition(allLanes, roadLinksMapped)
    result.size should equal(2)
    result(0).contains(lane1) should equal(true)
    result(0).contains(lane2) should equal(true)
    result(0).contains(lane4) should equal(true)

    result(1).contains(lane3) should equal(true)
    result(1).contains(lane0) should equal(true)
    result(1).contains(lane5) should equal(true)
  }
}

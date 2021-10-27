package fi.liikennevirasto.digiroad2.lane

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.AdministrativeClass
import fi.liikennevirasto.digiroad2.lane.LanePartitioner.partitionBySideCodeAndLaneCode
import org.scalatest._

class LanePartitionerSpec extends FunSuite with Matchers {

  def createPieceWiseLane(id: Long, sideCode: Int, laneCode: Int): PieceWiseLane = {
    PieceWiseLane(id, 0l, sideCode, false, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 0d, 10d, Set(Point(0.0, 0.0),
      Point(10.0, 0.0)), None, None, None, None, 0l, None, AdministrativeClass(1), Seq(LaneProperty("lane_code", Seq(LanePropertyValue(laneCode)))))
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
}

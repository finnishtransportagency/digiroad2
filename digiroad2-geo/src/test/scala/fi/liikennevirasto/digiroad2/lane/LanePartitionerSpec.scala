package fi.liikennevirasto.digiroad2.lane

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.AdministrativeClass
import fi.liikennevirasto.digiroad2.lane.LanePartitioner.partitionBySideCodeAndLaneCode
import org.scalatest._

class LanePartitionerSpec extends FunSuite with Matchers {

  test("Lanes should be partitioned by side code and lanecode") {

    val mainLaneBothDirectionsA = PieceWiseLane(1l, 0l, 1, false, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 0d, 10d, Set(Point(0.0, 0.0),
      Point(10.0, 0.0)), None, None, None, None, 0l, None, AdministrativeClass(1), Seq(LaneProperty("lane_code", Seq(LanePropertyValue(1)))))

    val mainLaneBothDirectionsB = PieceWiseLane(2l, 0l, 1, false, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 0d, 10d, Set(Point(0.0, 0.0),
      Point(10.0, 0.0)), None, None, None, None, 0l, None, AdministrativeClass(1), Seq(LaneProperty("lane_code", Seq(LanePropertyValue(1)))))

    val mainLaneTowards = PieceWiseLane(3l, 0l, 2, false, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 0d, 10d, Set(Point(0.0, 0.0),
      Point(10.0, 0.0)), None, None, None, None, 0l, None, AdministrativeClass(1), Seq(LaneProperty("lane_code", Seq(LanePropertyValue(1)))))

    val firstLeftAdditionalTowards = PieceWiseLane(4l, 0l, 2, false, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 0d, 10d, Set(Point(0.0, 0.0),
      Point(10.0, 0.0)), None, None, None, None, 0l, None, AdministrativeClass(1), Seq(LaneProperty("lane_code", Seq(LanePropertyValue(2)))))

    val firstRightAdditionalTowards = PieceWiseLane(5l, 0l, 2, false, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 0d, 10d, Set(Point(0.0, 0.0),
      Point(10.0, 0.0)), None, None, None, None, 0l, None, AdministrativeClass(1), Seq(LaneProperty("lane_code", Seq(LanePropertyValue(3)))))

    val lanes = Seq(mainLaneBothDirectionsA, mainLaneBothDirectionsB, mainLaneTowards, firstLeftAdditionalTowards, firstRightAdditionalTowards)
    val lanesPartitioned = partitionBySideCodeAndLaneCode(lanes)

    lanesPartitioned should have size 6
    lanesPartitioned(0) should be (List(mainLaneBothDirectionsA, mainLaneBothDirectionsB))
    lanesPartitioned(1) should be (List(mainLaneTowards))
    lanesPartitioned(4) should be (Stream(firstLeftAdditionalTowards, firstRightAdditionalTowards))

  }

}

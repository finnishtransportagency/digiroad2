package fi.liikennevirasto.digiroad2.lane

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.AdministrativeClass
import fi.liikennevirasto.digiroad2.lane.LanePartitioner
import fi.liikennevirasto.digiroad2.lane.LanePartitioner.partitionBySideCodeAndLaneCode
import org.scalatest._

class LanePartitionerSpec extends FunSuite with Matchers {

  val mainLaneBothDirectionsA = PieceWiseLane(1l, 0l, 1, false, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 0d, 10d, Set(Point(0.0, 0.0),
    Point(10.0, 0.0)), None, None, None, None, 0l, None, AdministrativeClass(1), Seq(LaneProperty("lane_code", Seq(LanePropertyValue(1)))))

  val mainLaneBothDirectionsB = PieceWiseLane(1l, 0l, 1, false, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 0d, 10d, Set(Point(0.0, 0.0),
    Point(10.0, 0.0)), None, None, None, None, 0l, None, AdministrativeClass(1), Seq(LaneProperty("lane_code", Seq(LanePropertyValue(1)))))

  val mainLaneTowards = PieceWiseLane(1l, 0l, 2, false, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 0d, 10d, Set(Point(0.0, 0.0),
    Point(10.0, 0.0)), None, None, None, None, 0l, None, AdministrativeClass(1), Seq(LaneProperty("lane_code", Seq(LanePropertyValue(1)))))

  val firstLeftAdditionalTowards = PieceWiseLane(1l, 0l, 2, false, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 0d, 10d, Set(Point(0.0, 0.0),
    Point(10.0, 0.0)), None, None, None, None, 0l, None, AdministrativeClass(1), Seq(LaneProperty("lane_code", Seq(LanePropertyValue(2)))))

  val firstRightAdditionalTowards = PieceWiseLane(1l, 0l, 2, false, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 0d, 10d, Set(Point(0.0, 0.0),
    Point(10.0, 0.0)), None, None, None, None, 0l, None, AdministrativeClass(1), Seq(LaneProperty("lane_code", Seq(LanePropertyValue(3)))))

  test("Lanes should be partitioned by side code and lanecode") {
    val lanes = Seq(mainLaneBothDirectionsA, mainLaneBothDirectionsB, mainLaneTowards, firstLeftAdditionalTowards, firstRightAdditionalTowards)
    val lanesPartitioned = partitionBySideCodeAndLaneCode(lanes)

    lanesPartitioned should have size 27
    lanesPartitioned(0) should be equals(mainLaneBothDirectionsA, mainLaneBothDirectionsB)
    lanesPartitioned(1) should be equals (mainLaneTowards)
    lanesPartitioned(11) should be equals (firstLeftAdditionalTowards)
    lanesPartitioned(12) should be equals (firstRightAdditionalTowards)

  }

}

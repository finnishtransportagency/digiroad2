package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.SpeedLimitFiller.{SideCodeAdjustment, MValueAdjustment, SpeedLimitChangeSet}
import fi.liikennevirasto.digiroad2.asset.Unknown
import fi.liikennevirasto.digiroad2.linearasset.{SpeedLimit, RoadLinkForSpeedLimit}
import org.scalatest._

class SpeedLimitFillerSpec extends FunSuite with Matchers {

  test("drop segment outside of link geometry") {
    val topology = Map(
      2l -> RoadLinkForSpeedLimit(Seq(Point(1.0, 0.0), Point(2.0, 0.0)), 1.0, Unknown, 2, None))
    val speedLimits = Map(1l -> Seq(
      SpeedLimit(1, 2, 0, None, Nil, 0.0, 0.2, None, None, None, None)))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    changeSet.droppedSpeedLimitIds should be(Set(1))
  }

  test("adjust speed limit to cover whole link when its the only speed limit to refer to the link") {
    val topology = Map(
      1l -> RoadLinkForSpeedLimit(Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 1.0, Unknown, 1, None))
    val speedLimits = Map(1l -> Seq(
      SpeedLimit(1, 1, 1, Some(40), Seq(Point(2.0, 0.0), Point(9.0, 0.0)), 2.0, 9.0, None, None, None, None)))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    filledTopology.length should be(1)
    filledTopology.head.points should be(Seq(Point(0.0, 0.0), Point(10.0, 0.0)))
    filledTopology.head.startMeasure should be(0.0)
    filledTopology.head.endMeasure should be(10.0)
    changeSet should be(SpeedLimitChangeSet(Set.empty, Seq(MValueAdjustment(1, 1, 0, 10.0)), Seq.empty))
  }

  test("adjust one way speed limits to cover whole link when there are no multiple speed limits on one side of the link") {
    val topology = Map(
      1l -> RoadLinkForSpeedLimit(Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 1.0, Unknown, 1, None))
    val speedLimits = Map(
      1l -> Seq(SpeedLimit(1, 1, 2, Some(40), Seq(Point(2.0, 0.0), Point(9.0, 0.0)), 2.0, 9.0, None, None, None, None)),
      2l -> Seq(SpeedLimit(2, 1, 3, Some(40), Seq(Point(2.0, 0.0), Point(9.0, 0.0)), 2.0, 9.0, None, None, None, None)))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    println(filledTopology)
    filledTopology should have size 2
    filledTopology.map(_.points) should be(Seq(
      Seq(Point(0.0, 0.0), Point(10.0, 0.0)),
      Seq(Point(0.0, 0.0), Point(10.0, 0.0))))
    filledTopology.map(_.startMeasure) should be(Seq(0.0, 0.0))
    filledTopology.map(_.endMeasure) should be(Seq(10.0, 10.0))
    changeSet.adjustedMValues should have size 2
    changeSet.adjustedMValues should be(Seq(MValueAdjustment(1, 1, 0, 10.0), MValueAdjustment(2, 1, 0, 10.0)))
  }

  test("drop short speed limit") {
    val topology = Map(
      1l -> RoadLinkForSpeedLimit(Seq(Point(0.0, 0.0), Point(0.4, 0.0)), 0.4, Unknown, 1, None))
    val speedLimits = Map(
      1l -> Seq(SpeedLimit(1, 1, 2, Some(40), Seq(Point(0.0, 0.0), Point(0.4, 0.0)), 0.0, 0.4, None, None, None, None)))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    filledTopology should have size 0
    changeSet.droppedSpeedLimitIds should be(Set(1))
  }

  test("should not drop adjusted short speed limit") {
    val topology = Map(
      1l -> RoadLinkForSpeedLimit(Seq(Point(0.0, 0.0), Point(1.0, 0.0)), 1.0, Unknown, 1, None))
    val speedLimits = Map(
      1l -> Seq(SpeedLimit(1, 1, 2, Some(40), Seq(Point(0.0, 0.0), Point(0.4, 0.0)), 0.0, 0.4, None, None, None, None)))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    filledTopology should have size 1
    filledTopology.map(_.id) should be(Seq(1))
    changeSet.droppedSpeedLimitIds shouldBe empty
  }

  test("adjust side code of a speed limit") {
    val topology = Map(
      1l -> RoadLinkForSpeedLimit(Seq(Point(0.0, 0.0), Point(1.0, 0.0)), 1.0, Unknown, 1, None))
    val speedLimits = Map(
      1l -> Seq(SpeedLimit(1, 1, 2, Some(40), Seq(Point(0.0, 0.0), Point(1.0, 0.0)), 0.0, 1.0, None, None, None, None)))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    filledTopology should have size 1
    filledTopology.map(_.sideCode) should be(Seq(1))
    changeSet.adjustedSideCodes should have size 1
    changeSet.adjustedSideCodes.head should be(SideCodeAdjustment(1, 1))
  }
}

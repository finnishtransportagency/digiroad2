package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.SpeedLimitFiller.{UnknownLimit, SideCodeAdjustment, MValueAdjustment, SpeedLimitChangeSet}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.{SpeedLimit, RoadLinkForSpeedLimit}
import org.scalatest._

class SpeedLimitFillerSpec extends FunSuite with Matchers {

  test("drop segment outside of link geometry") {
    val topology = Map(
      2l -> RoadLinkForSpeedLimit(Seq(Point(1.0, 0.0), Point(2.0, 0.0)), 1.0, Unknown, 2, None, TrafficDirection.BothDirections, 235))
    val speedLimits = Map(1l -> Seq(
      SpeedLimit(1, 2, SideCode.BothDirections, TrafficDirection.BothDirections, None, Nil, 0.0, 0.2, None, None, None, None)))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    changeSet.droppedSpeedLimitIds should be(Set(1))
  }

  test("adjust speed limit to cover whole link when its the only speed limit to refer to the link") {
    val topology = Map(
      1l -> RoadLinkForSpeedLimit(Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 1.0, Unknown, 1, None, TrafficDirection.BothDirections, 235))
    val speedLimits = Map(1l -> Seq(
      SpeedLimit(1, 1, SideCode.BothDirections, TrafficDirection.BothDirections, Some(40), Seq(Point(2.0, 0.0), Point(9.0, 0.0)), 2.0, 9.0, None, None, None, None)))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    filledTopology.length should be(1)
    filledTopology.head.geometry should be(Seq(Point(0.0, 0.0), Point(10.0, 0.0)))
    filledTopology.head.startMeasure should be(0.0)
    filledTopology.head.endMeasure should be(10.0)
    changeSet should be(SpeedLimitChangeSet(Set.empty, Seq(MValueAdjustment(1, 1, 0, 10.0)), Nil, Nil))
  }

  test("adjust one way speed limits to cover whole link when there are no multiple speed limits on one side of the link") {
    val topology = Map(
      1l -> RoadLinkForSpeedLimit(Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 1.0, Unknown, 1, None, TrafficDirection.BothDirections, 235))
    val speedLimits = Map(
      1l -> Seq(SpeedLimit(1, 1, SideCode.TowardsDigitizing, TrafficDirection.BothDirections, Some(40), Seq(Point(2.0, 0.0), Point(9.0, 0.0)), 2.0, 9.0, None, None, None, None)),
      2l -> Seq(SpeedLimit(2, 1, SideCode.AgainstDigitizing, TrafficDirection.BothDirections, Some(50), Seq(Point(2.0, 0.0), Point(9.0, 0.0)), 2.0, 9.0, None, None, None, None)))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    println(filledTopology)
    filledTopology should have size 2
    filledTopology.map(_.geometry) should be(Seq(
      Seq(Point(0.0, 0.0), Point(10.0, 0.0)),
      Seq(Point(0.0, 0.0), Point(10.0, 0.0))))
    filledTopology.map(_.startMeasure) should be(Seq(0.0, 0.0))
    filledTopology.map(_.endMeasure) should be(Seq(10.0, 10.0))
    changeSet.adjustedMValues should have size 2
    changeSet.adjustedMValues should be(Seq(MValueAdjustment(1, 1, 0, 10.0), MValueAdjustment(2, 1, 0, 10.0)))
  }

  test("drop short speed limit") {
    val topology = Map(
      1l -> RoadLinkForSpeedLimit(Seq(Point(0.0, 0.0), Point(0.4, 0.0)), 0.4, Unknown, 1, None, TrafficDirection.BothDirections, 235))
    val speedLimits = Map(
      1l -> Seq(SpeedLimit(1, 1, SideCode.TowardsDigitizing, TrafficDirection.BothDirections, Some(40), Seq(Point(0.0, 0.0), Point(0.4, 0.0)), 0.0, 0.4, None, None, None, None)))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    filledTopology should have size 0
    changeSet.droppedSpeedLimitIds should be(Set(1))
  }

  test("should not drop adjusted short speed limit") {
    val topology = Map(
      1l -> RoadLinkForSpeedLimit(Seq(Point(0.0, 0.0), Point(1.0, 0.0)), 1.0, Unknown, 1, None, TrafficDirection.BothDirections, 235))
    val speedLimits = Map(
      1l -> Seq(SpeedLimit(1, 1, SideCode.TowardsDigitizing, TrafficDirection.BothDirections, Some(40), Seq(Point(0.0, 0.0), Point(0.4, 0.0)), 0.0, 0.4, None, None, None, None)))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    filledTopology should have size 1
    filledTopology.map(_.id) should be(Seq(1))
    changeSet.droppedSpeedLimitIds shouldBe empty
  }

  test("adjust side code of a speed limit") {
    val topology = Map(
      1l -> RoadLinkForSpeedLimit(Seq(Point(0.0, 0.0), Point(1.0, 0.0)), 1.0, Unknown, 1, None, TrafficDirection.BothDirections, 235))
    val speedLimits = Map(
      1l -> Seq(SpeedLimit(1, 1, SideCode.TowardsDigitizing, TrafficDirection.BothDirections, Some(40), Seq(Point(0.0, 0.0), Point(1.0, 0.0)), 0.0, 1.0, None, None, None, None)))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    filledTopology should have size 1
    filledTopology.map(_.sideCode) should be(Seq(SideCode.BothDirections))
    changeSet.adjustedSideCodes should have size 1
    changeSet.adjustedSideCodes.head should be(SideCodeAdjustment(1, SideCode.BothDirections))
  }

  test("merge speed limits with same value on shared road link") {
    val topology = Map(
      1l -> RoadLinkForSpeedLimit(Seq(Point(0.0, 0.0), Point(1.0, 0.0)), 1.0, Unknown, 1, None, TrafficDirection.BothDirections, 235))
    val speedLimits = Map(
      1l -> Seq(SpeedLimit(1, 1, SideCode.TowardsDigitizing, TrafficDirection.BothDirections, Some(40), Seq(Point(0.0, 0.0), Point(0.2, 0.0)), 0.0, 0.2, None, None, None, None)),
      2l -> Seq(SpeedLimit(2, 1, SideCode.AgainstDigitizing, TrafficDirection.BothDirections, Some(40), Seq(Point(0.2, 0.0), Point(0.5, 0.0)), 0.0, 0.3, None, None, None, None)),
      3l -> Seq(SpeedLimit(3, 1, SideCode.BothDirections, TrafficDirection.BothDirections, Some(40), Seq(Point(0.5, 0.0), Point(1.0, 0.0)), 0.0, 0.5, None, None, None, None)))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    filledTopology should have size 1
    filledTopology.map(_.sideCode) should be(Seq(SideCode.BothDirections))
    filledTopology.map(_.value) should be(Seq(Some(40)))
    filledTopology.map(_.id) should be(Seq(1))
    changeSet.adjustedMValues should be(Seq(MValueAdjustment(1, 1, 0.0, 1.0)))
    changeSet.adjustedSideCodes should be(Seq(SideCodeAdjustment(1, SideCode.BothDirections)))
    changeSet.droppedSpeedLimitIds should be(Set(2, 3))
  }

  test("create unknown speed limit on empty segments") {
    val topology = Map(
      1l -> RoadLinkForSpeedLimit(Seq(Point(0.0, 0.0), Point(1.0, 0.0)), 1.0, State, 1, None, TrafficDirection.BothDirections, 235))
    val speedLimits = Map.empty[Long, Seq[SpeedLimit]]
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    filledTopology should have size 1
    filledTopology.map(_.sideCode) should be(Seq(SideCode.BothDirections))
    filledTopology.map(_.value) should be(Seq(None))
    filledTopology.map(_.id) should be(Seq(0))
    changeSet.generatedUnknownLimits should be(Seq(UnknownLimit(1l, 235, State)))
  }
}

package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.SpeedLimitFiller.{MValueAdjustment, SpeedLimitChangeSet}
import fi.liikennevirasto.digiroad2.asset.Unknown
import fi.liikennevirasto.digiroad2.linearasset.{SpeedLimitLink, SpeedLimitDTO, RoadLinkForSpeedLimit}
import org.scalatest._

class SpeedLimitFillerSpec extends FunSuite with Matchers {
  test("extend middle segment of a speed limit") {
    val topology = Map(1l -> RoadLinkForSpeedLimit(Seq(Point(0.0, 0.0), Point(1.0, 0.0)), 1.0, Unknown, 1),
      2l -> RoadLinkForSpeedLimit(Seq(Point(1.0, 0.0), Point(2.0, 0.0)), 1.0, Unknown, 2),
      3l -> RoadLinkForSpeedLimit(Seq(Point(2.0, 0.0), Point(3.0, 0.0)), 1.0, Unknown, 3))
    val speedLimits = Map(1l -> Seq(
      SpeedLimitDTO(1, 1, 0, None, Seq(Point(0.0, 0.0), Point(1.0, 0.0)), 0.0, 1.0),
      SpeedLimitDTO(1, 2, 0, None, Seq(Point(1.0, 0.0), Point(1.2, 0.0)), 0.0, 0.2),
      SpeedLimitDTO(1, 3, 0, None, Seq(Point(2.0, 0.0), Point(3.0, 0.0)), 0.0, 1.0)))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    filledTopology.map(_.mmlId) should be (Seq(1, 2, 3))
    filledTopology.map(_.points) should be (Seq(Seq(Point(0.0, 0.0), Point(1.0, 0.0)),
      Seq(Point(1.0, 0.0), Point(2.0, 0.0)),
      Seq(Point(2.0, 0.0), Point(3.0, 0.0))))
    changeSet.adjustedMValues should be (Seq(MValueAdjustment(1, 2, 1.0)))
  }

  test("drop segment outside of link geometry") {
    val topology = Map(1l -> RoadLinkForSpeedLimit(Seq(Point(0.0, 0.0), Point(1.0, 0.0)), 1.0, Unknown, 1),
      2l -> RoadLinkForSpeedLimit(Seq(Point(1.0, 0.0), Point(2.0, 0.0)), 1.0, Unknown, 2))
    val speedLimits = Map(1l -> Seq(
      SpeedLimitDTO(1, 1, 0, None, Seq(Point(0.0, 0.0), Point(1.0, 0.0)), 0.0, 1.0),
      SpeedLimitDTO(1, 2, 0, None, Nil, 0.0, 0.2)))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    changeSet.droppedSpeedLimitIds should be(Set(1))
  }

  test("drop speed limit with a gap") {
    val topology = Map(1l -> RoadLinkForSpeedLimit(Seq(Point(0.0, 0.0), Point(1.0, 0.0)), 1.0, Unknown, 1),
      2l -> RoadLinkForSpeedLimit(Seq(Point(1.0, 0.0), Point(3.0, 0.0)), 2.0, Unknown, 2),
      3l -> RoadLinkForSpeedLimit(Seq(Point(3.0, 0.0), Point(4.0, 0.0)), 1.0, Unknown, 3))
    val speedLimits = Map(1l -> Seq(
      SpeedLimitDTO(1, 1, 0, None, Seq(Point(0.0, 0.0), Point(1.0, 0.0)), 0.0, 1.0),
      SpeedLimitDTO(1, 3, 0, None, Seq(Point(3.0, 0.0), Point(4.0, 0.0)), 0.0, 1.0)))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    filledTopology should be (Seq(
      SpeedLimitLink(0, 1, 1, None, Seq(Point(0.0, 0.0), Point(1.0, 0.0)), 0, true),
      SpeedLimitLink(0, 2, 1, None, Seq(Point(1.0, 0.0), Point(3.0, 0.0)), 0, true),
      SpeedLimitLink(0, 3, 1, None, Seq(Point(3.0, 0.0), Point(4.0, 0.0)), 0, true)))
    changeSet.droppedSpeedLimitIds should be(Set(1))
  }

  test("fill speed limit end segments") {
    val topology = Map(1l -> RoadLinkForSpeedLimit(Seq(Point(0.0, 0.0), Point(1.0, 0.0)), 1.0, Unknown, 1),
      2l -> RoadLinkForSpeedLimit(Seq(Point(1.0, 0.0), Point(2.0, 0.0)), 1.0, Unknown, 2),
      3l -> RoadLinkForSpeedLimit(Seq(Point(3.0, 0.0), Point(2.0, 0.0)), 1.0, Unknown, 3))
    val speedLimits = Map(1l -> Seq(
      SpeedLimitDTO(1, 1, 0, None, Seq(Point(0.0, 0.0), Point(0.8, 0.0)), 0.0, 0.8),
      SpeedLimitDTO(1, 2, 0, None, Seq(Point(1.0, 0.0), Point(2.0, 0.0)), 0.0, 1.0),
      SpeedLimitDTO(1, 3, 0, None, Seq(Point(3.0, 0.0), Point(2.2, 0.0)), 0.0, 0.8)))
    val (filledTopology, _) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    filledTopology.map(_.mmlId) should be (Seq(1, 2, 3))
    filledTopology.map(_.points) should be (Seq(Seq(Point(0.0, 0.0), Point(1.0, 0.0)),
      Seq(Point(1.0, 0.0), Point(2.0, 0.0)),
      Seq(Point(3.0, 0.0), Point(2.0, 0.0))))
  }

  test("adjust end segments of a two segment speed limit") {
    val topology = Map(
      1l -> RoadLinkForSpeedLimit(Seq(Point(0.0, 0.0), Point(1.0, 0.0)), 1.0, Unknown, 1),
      2l -> RoadLinkForSpeedLimit(Seq(Point(2.0, 0.0), Point(1.0, 0.0)), 1.0, Unknown, 2))
    val speedLimits = Map(1l -> Seq(
      SpeedLimitDTO(1, 1, 0, None, Seq(Point(0.0, 0.0), Point(0.8, 0.0)), 0.0, 0.8),
      SpeedLimitDTO(1, 2, 0, None, Seq(Point(2.0, 0.0), Point(1.5, 0.0)), 0.0, 0.5)))
    val (filledTopology, _) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    filledTopology.map(_.mmlId) should be(Seq(1, 2))
    filledTopology.map(_.points) should be(Seq(Seq(Point(0.0, 0.0), Point(1.0, 0.0)),
      Seq(Point(2.0, 0.0), Point(1.0, 0.0))))
  }
}

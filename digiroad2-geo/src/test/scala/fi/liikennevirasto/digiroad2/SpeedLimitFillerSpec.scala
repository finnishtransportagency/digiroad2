package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.SpeedLimitFiller.{MValueAdjustment, SpeedLimitChangeSet}
import fi.liikennevirasto.digiroad2.asset.Unknown
import fi.liikennevirasto.digiroad2.linearasset.{SpeedLimitLink, SpeedLimitDTO, RoadLinkForSpeedLimit}
import org.scalatest._

class SpeedLimitFillerSpec extends FunSuite with Matchers {

  test("drop segment outside of link geometry") {
    val topology = Map(
      2l -> RoadLinkForSpeedLimit(Seq(Point(1.0, 0.0), Point(2.0, 0.0)), 1.0, Unknown, 2))
    val speedLimits = Map(1l -> Seq(
      SpeedLimitDTO(1, 2, 0, None, Nil, 0.0, 0.2)))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    changeSet.droppedSpeedLimitIds should be(Set(1))
  }

  test("adjust speed limit to cover whole link when its the only speed limit to refer to the link") {
     val topology = Map(
      1l -> RoadLinkForSpeedLimit(Seq(Point(0.0, 0.0), Point(1.0, 0.0)), 1.0, Unknown, 1))
    val speedLimits = Map(1l -> Seq(
      SpeedLimitDTO(1, 1, 1, Some(40), Seq(Point(0.0, 0.0), Point(0.8, 0.0)), 0.0, 0.8)))
    val (filledTopology, _) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    filledTopology.length should be(1)
    filledTopology.head.points should be(Seq(Point(0.0, 0.0), Point(1.0, 0.0)))
    filledTopology.head.startMeasure should be(0.0)
    filledTopology.head.endMeasure should be(1.0)
  }
}

package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.SpeedLimitFiller.{MValueAdjustment, SpeedLimitChangeSet}
import fi.liikennevirasto.digiroad2.asset.Unknown
import fi.liikennevirasto.digiroad2.linearasset.{RoadLinkForSpeedLimit, SpeedLimitDTO}
import org.scalatest._

class SpeedLimitPartitionerSpec extends FunSuite with Matchers {
  private def speedLimitDTO(mmlId: Long, value: Int, geometry: Seq[Point]) = {
    SpeedLimitDTO(0, mmlId, 1, Some(value), geometry, 0.0, 0.0)
  }

  test("group speed limits with same limit value and road number") {
    val speedLimitLinks = Seq(
      speedLimitDTO(1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      speedLimitDTO(2, 50, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadNumbers = Map(1l -> 1, 2l -> 1)

    val groupedLinks = SpeedLimitPartitioner.partition(speedLimitLinks, roadNumbers)
    groupedLinks should have size 1
    groupedLinks.head should have size 2
    groupedLinks.head.map(_.mmlId) should equal(speedLimitLinks.map(_.mmlId))
  }

  test("separate link with different limit value") {
    val speedLimitLinks = Seq(
      speedLimitDTO(1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      speedLimitDTO(2, 60, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadNumbers = Map(1l -> 1, 2l -> 1)

    val groupedLinks = SpeedLimitPartitioner.partition(speedLimitLinks, roadNumbers)
    groupedLinks should have size 2
  }

  test("separate link with different road number") {
    val speedLimitLinks = Seq(
      speedLimitDTO(1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      speedLimitDTO(2, 50, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadNumbers = Map(1l -> 1)

    val groupedLinks = SpeedLimitPartitioner.partition(speedLimitLinks, roadNumbers)
    groupedLinks should have size 2
  }

  test("separate links with gap in between") {
    val speedLimitLinks = Seq(
      speedLimitDTO(1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      speedLimitDTO(2, 50, Seq(Point(11.2, 0.0), Point(20.0, 0.0))))
    val roadNumbers = Map(1l -> 1, 2l -> 1)

    val groupedLinks = SpeedLimitPartitioner.partition(speedLimitLinks, roadNumbers)
    groupedLinks should have size 2
  }
}

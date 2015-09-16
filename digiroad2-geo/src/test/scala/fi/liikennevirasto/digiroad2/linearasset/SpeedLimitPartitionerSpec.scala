package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset._
import org.scalatest._

class SpeedLimitPartitionerSpec extends FunSuite with Matchers {
  private def speedLimitLink(mmlId: Long, value: Int, geometry: Seq[Point]) = {
    SpeedLimit(0, mmlId, SideCode.BothDirections, TrafficDirection.BothDirections, Some(value), geometry, 0.0, 0.0, None, None, None, None)
  }
  private def roadLinkForSpeedLimit(roadIdentifier: Either[Int, String], administrativeClass: AdministrativeClass = Unknown): RoadLinkForSpeedLimit = {
    RoadLinkForSpeedLimit(Seq(Point(1.0, 0.0), Point(2.0, 0.0)), 1.0, administrativeClass, 2, Option(roadIdentifier), TrafficDirection.BothDirections, 235)
  }

  test("group speed limits with same limit value and road number") {
    val speedLimitLinks = Seq(
      speedLimitLink(1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      speedLimitLink(2, 50, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadLinksForSpeedLimits = Map(1l -> roadLinkForSpeedLimit(Left(1)), 2l -> roadLinkForSpeedLimit(Left(1)))

    val groupedLinks = SpeedLimitPartitioner.partition(speedLimitLinks, roadLinksForSpeedLimits)
    groupedLinks should have size 1
    groupedLinks.head should have size 2
    groupedLinks.head.map(_.mmlId).toSet should be(speedLimitLinks.map(_.mmlId).toSet)
  }

  test("separate link with different limit value") {
    val speedLimitLinks = Seq(
      speedLimitLink(1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      speedLimitLink(2, 60, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadLinksForSpeedLimits = Map(1l -> roadLinkForSpeedLimit(Left(1)), 2l -> roadLinkForSpeedLimit(Left(1)))

    val groupedLinks = SpeedLimitPartitioner.partition(speedLimitLinks, roadLinksForSpeedLimits)
    groupedLinks should have size 2
  }

  test("separate link with different road number") {
    val speedLimitLinks = Seq(
      speedLimitLink(1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      speedLimitLink(2, 50, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadLinksForSpeedLimits = Map(1l -> roadLinkForSpeedLimit(Left(1)))

    val groupedLinks = SpeedLimitPartitioner.partition(speedLimitLinks, roadLinksForSpeedLimits)
    groupedLinks should have size 2
  }

  test("separate links with gap in between") {
    val speedLimitLinks = Seq(
      speedLimitLink(1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      speedLimitLink(2, 50, Seq(Point(11.2, 0.0), Point(20.0, 0.0))))
    val roadLinksForSpeedLimits = Map(1l -> roadLinkForSpeedLimit(Left(1)), 2l -> roadLinkForSpeedLimit(Left(1)))

    val groupedLinks = SpeedLimitPartitioner.partition(speedLimitLinks, roadLinksForSpeedLimits)
    groupedLinks should have size 2
  }

  test("separate links with different administrative classes") {
    val speedLimitLinks = Seq(
      speedLimitLink(1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      speedLimitLink(2, 50, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadLinksForSpeedLimits = Map(1l -> roadLinkForSpeedLimit(Left(1), Municipality), 2l -> roadLinkForSpeedLimit(Left(1), State))

    val groupedLinks = SpeedLimitPartitioner.partition(speedLimitLinks, roadLinksForSpeedLimits)
    groupedLinks should have size 2
  }

  test("group links without road numbers into separate groups") {
     val speedLimitLinks = Seq(
      speedLimitLink(1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      speedLimitLink(2, 50, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadLinksForSpeedLimits = Map.empty[Long, RoadLinkForSpeedLimit]

    val groupedLinks = SpeedLimitPartitioner.partition(speedLimitLinks, roadLinksForSpeedLimits)
    groupedLinks should have size 2
  }

  test("group speed limits with same limit value and road name") {
    val speedLimitLinks = Seq(
      speedLimitLink(1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      speedLimitLink(2, 50, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadLinksForSpeedLimits = Map(1l -> roadLinkForSpeedLimit(Right("Opastinsilta")), 2l -> roadLinkForSpeedLimit(Right("Opastinsilta")))

    val groupedLinks = SpeedLimitPartitioner.partition(speedLimitLinks, roadLinksForSpeedLimits)
    groupedLinks should have size 1
    groupedLinks.head should have size 2
    groupedLinks.head.map(_.mmlId) should contain only(speedLimitLinks.map(_.mmlId): _*)
  }

  test("separate links with different road name") {
    val speedLimitLinks = Seq(
      speedLimitLink(1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      speedLimitLink(2, 50, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadLinksForSpeedLimits = Map(1l -> roadLinkForSpeedLimit(Right("Opastinsilta")), 2l -> roadLinkForSpeedLimit(Right("Ratamestarinkatu")))

    val groupedLinks = SpeedLimitPartitioner.partition(speedLimitLinks, roadLinksForSpeedLimits)
    groupedLinks should have size 2
  }
}

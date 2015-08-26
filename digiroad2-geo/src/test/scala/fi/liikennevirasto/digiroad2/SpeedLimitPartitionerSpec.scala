package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.{VVHRoadLinkWithProperties, SpeedLimit}
import org.scalatest._

class SpeedLimitPartitionerSpec extends FunSuite with Matchers {
  private def speedLimitLink(mmlId: Long, value: Int, geometry: Seq[Point]) = {
    SpeedLimit(0, mmlId, SideCode.BothDirections, TrafficDirection.BothDirections, Some(value), geometry, 0.0, 0.0, None, None, None, None)
  }

  private def roadLink(mmlId: Long, geometry: Seq[Point]) = {
    VVHRoadLinkWithProperties(mmlId, geometry, 0.0, Municipality, 0, TrafficDirection.BothDirections, Motorway, None, None)
  }

  test("group speed limits with same limit value and road number") {
    val speedLimitLinks = Seq(
      speedLimitLink(1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      speedLimitLink(2, 50, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadIdentifiers = Map(1l -> Left(1), 2l -> Left(1))

    val groupedLinks = SpeedLimitPartitioner.partition(speedLimitLinks, roadIdentifiers)
    groupedLinks should have size 1
    groupedLinks.head should have size 2
    groupedLinks.head.map(_.mmlId).toSet should be(speedLimitLinks.map(_.mmlId).toSet)
  }

  test("separate link with different limit value") {
    val speedLimitLinks = Seq(
      speedLimitLink(1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      speedLimitLink(2, 60, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadIdentifiers = Map(1l -> Left(1), 2l -> Left(1))

    val groupedLinks = SpeedLimitPartitioner.partition(speedLimitLinks, roadIdentifiers)
    groupedLinks should have size 2
  }

  test("separate link with different road number") {
    val speedLimitLinks = Seq(
      speedLimitLink(1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      speedLimitLink(2, 50, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadIdentifiers = Map(1l -> Left(1))

    val groupedLinks = SpeedLimitPartitioner.partition(speedLimitLinks, roadIdentifiers)
    groupedLinks should have size 2
  }

  test("separate links with gap in between") {
    val speedLimitLinks = Seq(
      speedLimitLink(1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      speedLimitLink(2, 50, Seq(Point(11.2, 0.0), Point(20.0, 0.0))))
    val roadIdentifiers = Map(1l -> Left(1), 2l -> Left(1))

    val groupedLinks = SpeedLimitPartitioner.partition(speedLimitLinks, roadIdentifiers)
    groupedLinks should have size 2
  }

  test("group links without road numbers into separate groups") {
     val speedLimitLinks = Seq(
      speedLimitLink(1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      speedLimitLink(2, 50, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadIdentifiers = Map.empty[Long, Either[Int, String]]

    val groupedLinks = SpeedLimitPartitioner.partition(speedLimitLinks, roadIdentifiers)
    groupedLinks should have size 2
  }

  test("group speed limits with same limit value and road name") {
    val speedLimitLinks = Seq(
      speedLimitLink(1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      speedLimitLink(2, 50, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadIdentifiers = Map(1l -> Right("Opastinsilta"), 2l -> Right("Opastinsilta"))

    val groupedLinks = SpeedLimitPartitioner.partition(speedLimitLinks, roadIdentifiers)
    groupedLinks should have size 1
    groupedLinks.head should have size 2
    groupedLinks.head.map(_.mmlId) should contain only(speedLimitLinks.map(_.mmlId): _*)
  }

  test("separate links with different road name") {
    val speedLimitLinks = Seq(
      speedLimitLink(1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      speedLimitLink(2, 50, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadIdentifiers = Map(1l -> Right("Opastinsilta"), 2l -> Right("Ratamestarinkatu"))

    val groupedLinks = SpeedLimitPartitioner.partition(speedLimitLinks, roadIdentifiers)
    groupedLinks should have size 2
  }

  // TODO: Separate cluster on different link type
  // TODO: Separate cluster on different road name or road number
  test("group road links") {
    val roadLinks = Seq(
      roadLink(0l, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      roadLink(1l, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))

    val groupedLinks = SpeedLimitPartitioner.partitionRoadLinks(roadLinks)
    groupedLinks should have size 1
    groupedLinks.head should have size 2
    groupedLinks.head.map(_.mmlId).toSet should be(roadLinks.map(_.mmlId).toSet)
  }

  test("separate road link group with functional class") {
    val roadLinks = Seq(
      roadLink(0l, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      roadLink(1l, Seq(Point(10.2, 0.0), Point(20.0, 0.0))).copy(functionalClass = 1))

    val groupedLinks = SpeedLimitPartitioner.partitionRoadLinks(roadLinks)
    groupedLinks should have size 2
    groupedLinks.map(_.length) should be(Seq(1, 1))
  }

  test("separate road link group with traffic direction") {
    val roadLinks = Seq(
      roadLink(0l, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      roadLink(1l, Seq(Point(10.2, 0.0), Point(20.0, 0.0))).copy(trafficDirection = TrafficDirection.AgainstDigitizing))

    val groupedLinks = SpeedLimitPartitioner.partitionRoadLinks(roadLinks)
    groupedLinks should have size 2
    groupedLinks.map(_.length) should be(Seq(1, 1))
  }
}

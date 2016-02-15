package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset._
import org.scalatest._

class RoadLinkPartitionerSpec extends FunSuite with Matchers {
  private def roadLink(mmlId: Long, geometry: Seq[Point]) = {
    RoadLink(mmlId, geometry, 0.0, Municipality, 0,
      TrafficDirection.BothDirections, Motorway, None, None,
      Map("ROADNUMBER" -> BigInt(123)))
  }

  test("group road links") {
    val roadLinks = Seq(
      roadLink(0l, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      roadLink(1l, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))

    val groupedLinks = RoadLinkPartitioner.partition(roadLinks)
    groupedLinks should have size 1
    groupedLinks.head should have size 2
    groupedLinks.head.map(_.linkId).toSet should be(roadLinks.map(_.linkId).toSet)
  }

  test("separate road link group with functional class") {
    val roadLinks = Seq(
      roadLink(0l, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      roadLink(1l, Seq(Point(10.2, 0.0), Point(20.0, 0.0))).copy(functionalClass = 1))

    val groupedLinks = RoadLinkPartitioner.partition(roadLinks)
    groupedLinks should have size 2
    groupedLinks.map(_.length) should be(Seq(1, 1))
  }

  test("separate road link group with traffic direction") {
    val roadLinks = Seq(
      roadLink(0l, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      roadLink(1l, Seq(Point(10.2, 0.0), Point(20.0, 0.0))).copy(trafficDirection = TrafficDirection.AgainstDigitizing))

    val groupedLinks = RoadLinkPartitioner.partition(roadLinks)
    groupedLinks should have size 2
    groupedLinks.map(_.length) should be(Seq(1, 1))
  }

  test("separate road link group with link type") {
    val roadLinks = Seq(
      roadLink(0l, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      roadLink(1l, Seq(Point(10.2, 0.0), Point(20.0, 0.0))).copy(linkType = Freeway))

    val groupedLinks = RoadLinkPartitioner.partition(roadLinks)
    groupedLinks should have size 2
    groupedLinks.map(_.length) should be(Seq(1, 1))
  }

  test("separate road link group with administrative class") {
    val roadLinks = Seq(
      roadLink(0l, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      roadLink(1l, Seq(Point(10.2, 0.0), Point(20.0, 0.0))).copy(administrativeClass = State))

    val groupedLinks = RoadLinkPartitioner.partition(roadLinks)
    groupedLinks should have size 2
    groupedLinks.map(_.length) should be(Seq(1, 1))
  }

  test("separate road link group with road number") {
    val roadLinks = Seq(
      roadLink(0l, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      roadLink(1l, Seq(Point(10.2, 0.0), Point(20.0, 0.0))).copy(attributes = Map("ROADNUMBER" -> BigInt(5))))

    val groupedLinks = RoadLinkPartitioner.partition(roadLinks)
    groupedLinks should have size 2
    groupedLinks.map(_.length) should be(Seq(1, 1))
  }

  test("separate road link group with road name") {
    val roadLinks = Seq(
      roadLink(0l, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      roadLink(1l, Seq(Point(10.2, 0.0), Point(20.0, 0.0))).copy(attributes = Map("ROADNAME_FI" -> "Opastinsilta")))

    val groupedLinks = RoadLinkPartitioner.partition(roadLinks)
    groupedLinks should have size 2
    groupedLinks.map(_.length) should be(Seq(1, 1))
  }

  test("separate road link group with undefined road identifier") {
    val roadLinks = Seq(
      roadLink(0l, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      roadLink(1l, Seq(Point(10.2, 0.0), Point(20.0, 0.0))).copy(attributes = Map()))

    val groupedLinks = RoadLinkPartitioner.partition(roadLinks)
    groupedLinks should have size 2
    groupedLinks.map(_.length) should be(Seq(1, 1))
  }
}

package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset._
import org.scalatest._
import java.util.UUID
import scala.util.Random

class RoadLinkPartitionerSpec extends FunSuite with Matchers {
  private def roadLink(linkId: String, geometry: Seq[Point]) = {
    RoadLink(linkId, geometry, 0.0, Municipality, 0,
      TrafficDirection.BothDirections, Motorway, None, None,
      Map("ROADNUMBER" -> BigInt(123)))
  }

  private def generateRandomLinkId(): String = s"${UUID.randomUUID()}:${Random.nextInt(100)}"
  
  val linkId1: String = generateRandomLinkId()
  val linkId2: String = generateRandomLinkId()

  test("group road links") {
    val roadLinks = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      roadLink(linkId2, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))

    val groupedLinks = RoadLinkPartitioner.partition(roadLinks)
    groupedLinks should have size 1
    groupedLinks.head should have size 2
    groupedLinks.head.map(_.linkId).toSet should be(roadLinks.map(_.linkId).toSet)
  }

  test("separate road link group with functional class") {
    val roadLinks = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      roadLink(linkId2, Seq(Point(10.2, 0.0), Point(20.0, 0.0))).copy(functionalClass = 1))

    val groupedLinks = RoadLinkPartitioner.partition(roadLinks)
    groupedLinks should have size 2
    groupedLinks.map(_.length) should be(Seq(1, 1))
  }

  test("separate road link group with traffic direction") {
    val roadLinks = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      roadLink(linkId2, Seq(Point(10.2, 0.0), Point(20.0, 0.0))).copy(trafficDirection = TrafficDirection.AgainstDigitizing))

    val groupedLinks = RoadLinkPartitioner.partition(roadLinks)
    groupedLinks should have size 2
    groupedLinks.map(_.length) should be(Seq(1, 1))
  }

  test("separate road link group with link type") {
    val roadLinks = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      roadLink(linkId2, Seq(Point(10.2, 0.0), Point(20.0, 0.0))).copy(linkType = Freeway))

    val groupedLinks = RoadLinkPartitioner.partition(roadLinks)
    groupedLinks should have size 2
    groupedLinks.map(_.length) should be(Seq(1, 1))
  }

  test("separate road link group with administrative class") {
    val roadLinks = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      roadLink(linkId2, Seq(Point(10.2, 0.0), Point(20.0, 0.0))).copy(administrativeClass = State))

    val groupedLinks = RoadLinkPartitioner.partition(roadLinks)
    groupedLinks should have size 2
    groupedLinks.map(_.length) should be(Seq(1, 1))
  }

  test("separate road link group with road number") {
    val roadLinks = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      roadLink(linkId2, Seq(Point(10.2, 0.0), Point(20.0, 0.0))).copy(attributes = Map("ROADNUMBER" -> BigInt(5))))

    val groupedLinks = RoadLinkPartitioner.partition(roadLinks)
    groupedLinks should have size 2
    groupedLinks.map(_.length) should be(Seq(1, 1))
  }

  test("separate road link group with road name") {
    val roadLinks = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      roadLink(linkId2, Seq(Point(10.2, 0.0), Point(20.0, 0.0))).copy(attributes = Map("ROADNAME_FI" -> "Opastinsilta")))

    val groupedLinks = RoadLinkPartitioner.partition(roadLinks)
    groupedLinks should have size 2
    groupedLinks.map(_.length) should be(Seq(1, 1))
  }

  test("separate road link group with undefined road identifier") {
    val roadLinks = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      roadLink(linkId2, Seq(Point(10.2, 0.0), Point(20.0, 0.0))).copy(attributes = Map()))

    val groupedLinks = RoadLinkPartitioner.partition(roadLinks)
    groupedLinks should have size 2
    groupedLinks.map(_.length) should be(Seq(1, 1))
  }
}

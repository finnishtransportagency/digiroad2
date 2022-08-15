package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import org.scalatest.{FunSuite, Matchers}

class RoundaboutProcessorSpec extends FunSuite with Matchers {

  private def getTrafficDirection(linkId: String, roadLinks: Seq[RoadLinkLike]): TrafficDirection ={
    roadLinks.find(_.linkId == linkId).getOrElse(throw new NoSuchElementException).trafficDirection
  }

  test("Set traffic direction to Roundabouts road links real case 1"){
    val linkId1 = LinkIdGenerator.generateRandom()
    val linkId2 = LinkIdGenerator.generateRandom()
    val linkId3 = LinkIdGenerator.generateRandom()
    val linkId4 = LinkIdGenerator.generateRandom()

    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(384970, 6671649), Point(384968, 6671653), Point(384969, 6671660), Point(384975, 6671664)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(linkId2, Seq(Point(384985, 6671649), Point(384980, 6671646), Point(384972, 6671647), Point(384970, 6671649)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(linkId3, Seq(Point(384985, 6671649), Point(384986, 6671650), Point(384987, 6671657), Point(384986, 6671660)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(linkId4, Seq(Point(384986, 6671660), Point(384983, 6671663), Point(384976, 6671665), Point(384975, 6671664)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None)
    )

    val (roadLinksWithDirections, _) = RoundaboutProcessor.setTrafficDirection(roadLinks)

    getTrafficDirection(linkId1, roadLinksWithDirections) should be (TrafficDirection.AgainstDigitizing)
    getTrafficDirection(linkId2, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
    getTrafficDirection(linkId3, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
    getTrafficDirection(linkId4, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
  }

  test("Set traffic direction to Roundabouts road links real case 2"){
    val linkId1 = LinkIdGenerator.generateRandom()
    val linkId2 = LinkIdGenerator.generateRandom()
    val linkId3 = LinkIdGenerator.generateRandom()
    val linkId4 = LinkIdGenerator.generateRandom()

    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(385152, 6671749), Point(385160, 6671757), Point(385168, 6671756)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(linkId2, Seq(Point(385160, 6671739), Point(385155, 6671742), Point(385152, 6671749)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(linkId3, Seq(Point(385160, 6671739), Point(385168, 6671740), Point(385171, 6671749)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(linkId4, Seq(Point(385171, 6671749), Point(385171, 6671752), Point(385168, 6671756)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None)
    )

    val (roadLinksWithDirections, _) = RoundaboutProcessor.setTrafficDirection(roadLinks)

    getTrafficDirection(linkId1, roadLinksWithDirections) should be (TrafficDirection.AgainstDigitizing)
    getTrafficDirection(linkId2, roadLinksWithDirections) should be (TrafficDirection.AgainstDigitizing)
    getTrafficDirection(linkId3, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
    getTrafficDirection(linkId4, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
  }

  test("Set traffic direction to Roundabouts road links real case 3"){
    val linkId1 = LinkIdGenerator.generateRandom()
    val linkId2 = LinkIdGenerator.generateRandom()
    val linkId3 = LinkIdGenerator.generateRandom()
    val linkId4 = LinkIdGenerator.generateRandom()
    val linkId5 = LinkIdGenerator.generateRandom()
    val linkId6 = LinkIdGenerator.generateRandom()
    val linkId7 = LinkIdGenerator.generateRandom()
    val linkId8 = LinkIdGenerator.generateRandom()

    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(385795, 6675342), Point(385800, 6675344), Point(385806, 6675345), Point(385811, 6675344)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(linkId2, Seq(Point(385789, 6675331), Point(385791, 6675336), Point(385795, 6675342)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(linkId3, Seq(Point(385791, 6675320), Point(385789, 6675324), Point(385789, 6675331)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(linkId4, Seq(Point(385802, 6675313), Point(385797, 6675314), Point(385791, 6675320)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(linkId5, Seq(Point(385802, 6675313), Point(385810, 6675313), Point(385817, 6675317)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(linkId6, Seq(Point(385817, 6675317), Point(385819, 6675320), Point(385821, 6675326)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(linkId7, Seq(Point(385821, 6675326), Point(385821, 6675331), Point(385819, 6675337)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(linkId8, Seq(Point(385819, 6675337), Point(385813, 6675343), Point(385811, 6675344)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None)
    )

    val (roadLinksWithDirections, _) = RoundaboutProcessor.setTrafficDirection(roadLinks)

    getTrafficDirection(linkId1, roadLinksWithDirections) should be (TrafficDirection.AgainstDigitizing)
    getTrafficDirection(linkId2, roadLinksWithDirections) should be (TrafficDirection.AgainstDigitizing)
    getTrafficDirection(linkId3, roadLinksWithDirections) should be (TrafficDirection.AgainstDigitizing)
    getTrafficDirection(linkId4, roadLinksWithDirections) should be (TrafficDirection.AgainstDigitizing)
    getTrafficDirection(linkId5, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
    getTrafficDirection(linkId6, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
    getTrafficDirection(linkId7, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
    getTrafficDirection(linkId8, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
  }

  test("Set traffic direction to Roundabouts road links real case 4"){
    val linkId1 = LinkIdGenerator.generateRandom()
    val linkId2 = LinkIdGenerator.generateRandom()
    val linkId3 = LinkIdGenerator.generateRandom()
    val linkId4 = LinkIdGenerator.generateRandom()
    val linkId5 = LinkIdGenerator.generateRandom()

    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(492905.449, 6707352.469), Point(492904.561, 6707358.171), Point(492905.511, 6707363.632), Point(492909.785, 6707368.382), Point(492917.209, 6707370.535)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(linkId2, Seq(Point(492941.523, 6707361.033), Point(492938.044, 6707366.245), Point(492931.327, 6707370.967), Point(492925.431, 6707371.581), Point(492917.209, 6707370.535)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(linkId3, Seq(Point(492928.555, 6707340.825), Point(492935.907, 6707346.06), Point(492939.944, 6707351.759), Point(492941.132, 6707356.271), Point(492941.523, 6707361.033)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(linkId4, Seq(Point(492920.105, 6707340.825), Point(492924.433, 6707340.355), Point(492928.555, 6707340.825)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(linkId5, Seq(Point(492920.105, 6707340.825), Point(492915.247, 6707341.547), Point(492910.26, 6707344.635), Point(492906.461, 6707348.435), Point(492905.449, 6707352.469)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None)
    )

    val (roadLinksWithDirections, _) = RoundaboutProcessor.setTrafficDirection(roadLinks)

    getTrafficDirection(linkId1, roadLinksWithDirections) should be (TrafficDirection.AgainstDigitizing)
    getTrafficDirection(linkId2, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
    getTrafficDirection(linkId3, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
    getTrafficDirection(linkId4, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
    getTrafficDirection(linkId5, roadLinksWithDirections) should be (TrafficDirection.AgainstDigitizing)
  }

  test("Set traffic direction to Roundabouts road links when the roundabout is a square (extreme case)"){
    val linkId1 = LinkIdGenerator.generateRandom()
    val linkId2 = LinkIdGenerator.generateRandom()
    val linkId3 = LinkIdGenerator.generateRandom()
    val linkId4 = LinkIdGenerator.generateRandom()

    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(30, 30), Point(35, 30), Point(40, 30)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(linkId2, Seq(Point(30, 30), Point(30, 35), Point(30, 40)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(linkId3, Seq(Point(40, 30), Point(40, 35), Point(40, 40)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(linkId4, Seq(Point(30, 40), Point(35, 40), Point(40, 40)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None)
    )

    val (roadLinksWithDirections, _) = RoundaboutProcessor.setTrafficDirection(roadLinks)

    getTrafficDirection(linkId1, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
    getTrafficDirection(linkId2, roadLinksWithDirections) should be (TrafficDirection.AgainstDigitizing)
    getTrafficDirection(linkId3, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
    getTrafficDirection(linkId4, roadLinksWithDirections) should be (TrafficDirection.AgainstDigitizing)
  }

  test("Set traffic direction to Roundabouts road links when the roundabout is a triangle (extreme case)"){
    val linkId1 = LinkIdGenerator.generateRandom()
    val linkId2 = LinkIdGenerator.generateRandom()
    val linkId3 = LinkIdGenerator.generateRandom()

    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(10, 10), Point(20, 30), Point(30, 50)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(linkId2, Seq(Point(30, 50), Point(40, 30), Point(50, 10)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(linkId3, Seq(Point(10, 10), Point(30, 10), Point(50, 10)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None)
    )

    val (roadLinksWithDirections, _) = RoundaboutProcessor.setTrafficDirection(roadLinks)

    getTrafficDirection(linkId1, roadLinksWithDirections) should be (TrafficDirection.AgainstDigitizing)
    getTrafficDirection(linkId2, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
    getTrafficDirection(linkId3, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
  }

  test("Add changed road links to Roundabouts change set"){
    val linkId1 = LinkIdGenerator.generateRandom()
    val linkId2 = LinkIdGenerator.generateRandom()
    val linkId3 = LinkIdGenerator.generateRandom()
    val linkId4 = LinkIdGenerator.generateRandom()
    val linkId5 = LinkIdGenerator.generateRandom()
    val linkId6 = LinkIdGenerator.generateRandom()
    val linkId7 = LinkIdGenerator.generateRandom()
    val linkId8 = LinkIdGenerator.generateRandom()

    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(385795, 6675342), Point(385800, 6675344), Point(385806, 6675345), Point(385811, 6675344)), 2, Municipality, 1, TrafficDirection.AgainstDigitizing, Roundabout, None, None),
      RoadLink(linkId2, Seq(Point(385789, 6675331), Point(385791, 6675336), Point(385795, 6675342)), 2, Municipality, 1, TrafficDirection.AgainstDigitizing, Roundabout, None, None),
      RoadLink(linkId3, Seq(Point(385791, 6675320), Point(385789, 6675324), Point(385789, 6675331)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(linkId4, Seq(Point(385802, 6675313), Point(385797, 6675314), Point(385791, 6675320)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(linkId5, Seq(Point(385802, 6675313), Point(385810, 6675313), Point(385817, 6675317)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(linkId6, Seq(Point(385817, 6675317), Point(385819, 6675320), Point(385821, 6675326)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(linkId7, Seq(Point(385821, 6675326), Point(385821, 6675331), Point(385819, 6675337)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(linkId8, Seq(Point(385819, 6675337), Point(385813, 6675343), Point(385811, 6675344)), 2, Municipality, 1, TrafficDirection.TowardsDigitizing, Roundabout, None, None)
    )

    val changedRoadLinkIds = Set(linkId3, linkId4, linkId5, linkId6, linkId7)

    val (_, changeSet) = RoundaboutProcessor.setTrafficDirection(roadLinks)

    changeSet.trafficDirectionChanges.size should be (5)
    changedRoadLinkIds.forall(linkId => changeSet.trafficDirectionChanges.exists(_.linkId == linkId)) should be (true)
  }

  test("check if the road link is a roundabout") {
    val linkId = LinkIdGenerator.generateRandom()
    val municipalityRoundaboutLink = RoadLink(linkId, Seq(Point(385795, 6675342), Point(385800, 6675344), Point(385806, 6675345), Point(385811, 6675344)), 2, Municipality, 1, TrafficDirection.AgainstDigitizing, Roundabout, None, None)
    val stateRoundaboutLink = RoadLink(linkId, Seq(Point(385795, 6675342), Point(385800, 6675344), Point(385806, 6675345), Point(385811, 6675344)), 2, State, 1, TrafficDirection.AgainstDigitizing, Roundabout, None, None)
    val stateMotorwayLink = RoadLink(linkId, Seq(Point(385795, 6675342), Point(385800, 6675344), Point(385806, 6675345), Point(385811, 6675344)), 2, State, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None)
    val municipalityFreewayLink = RoadLink(linkId, Seq(Point(385795, 6675342), Point(385800, 6675344), Point(385806, 6675345), Point(385811, 6675344)), 2, State, 1, TrafficDirection.AgainstDigitizing, Freeway, None, None)

    RoundaboutProcessor.isRoundaboutLink(municipalityRoundaboutLink) should be (false)
    RoundaboutProcessor.isRoundaboutLink(stateRoundaboutLink) should be (true)
    RoundaboutProcessor.isRoundaboutLink(stateMotorwayLink) should be (false)
    RoundaboutProcessor.isRoundaboutLink(municipalityFreewayLink) should be (false)
  }

  test("Group roundabouts links") {

    val roundabout1 = Seq(
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(385795, 6675342), Point(385800, 6675344), Point(385806, 6675345), Point(385811, 6675344)), 2, State, 1, TrafficDirection.AgainstDigitizing, Roundabout, None, None),
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(385789, 6675331), Point(385791, 6675336), Point(385795, 6675342)), 2, State, 1, TrafficDirection.AgainstDigitizing, Roundabout, None, None),
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(385791, 6675320), Point(385789, 6675324), Point(385789, 6675331)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(385802, 6675313), Point(385797, 6675314), Point(385791, 6675320)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(385802, 6675313), Point(385810, 6675313), Point(385817, 6675317)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(385817, 6675317), Point(385819, 6675320), Point(385821, 6675326)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(385821, 6675326), Point(385821, 6675331), Point(385819, 6675337)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(385819, 6675337), Point(385813, 6675343), Point(385811, 6675344)), 2, State, 1, TrafficDirection.TowardsDigitizing, Roundabout, None, None)
    )

    val roundabout2 = Seq(
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(385152, 6671749), Point(385160, 6671757), Point(385168, 6671756)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(385160, 6671739), Point(385155, 6671742), Point(385152, 6671749)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(385160, 6671739), Point(385168, 6671740), Point(385171, 6671749)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(385171, 6671749), Point(385171, 6671752), Point(385168, 6671756)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None)
    )

    //Roundabout 3 incomplete
    val roundabout3 = Seq(
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(384985, 6671649), Point(384980, 6671646), Point(384972, 6671647), Point(384970, 6671649)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(384970, 6671649), Point(384968, 6671653), Point(384969, 6671660), Point(384975, 6671664)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None)
    )

    val groupedRoundabouts = RoundaboutProcessor.groupByRoundabout(roundabout1 ++ roundabout2 ++ roundabout3).map(_.map(_.linkId))

    groupedRoundabouts.size should be (3)
    groupedRoundabouts.find(_.size == roundabout1.size).get.forall(roundabout1.map(_.linkId).contains) should be (true)
    groupedRoundabouts.find(_.size == roundabout2.size).get.forall(roundabout2.map(_.linkId).contains) should be (true)
    groupedRoundabouts.find(_.size == roundabout3.size).get.forall(roundabout3.map(_.linkId).contains) should be (true)
  }

  test("Ignoring incomplete roundabouts from grouping") {

    val roundabout1 = Seq(
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(385795, 6675342), Point(385800, 6675344), Point(385806, 6675345), Point(385811, 6675344)), 2, State, 1, TrafficDirection.AgainstDigitizing, Roundabout, None, None),
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(385789, 6675331), Point(385791, 6675336), Point(385795, 6675342)), 2, State, 1, TrafficDirection.AgainstDigitizing, Roundabout, None, None),
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(385791, 6675320), Point(385789, 6675324), Point(385789, 6675331)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(385802, 6675313), Point(385797, 6675314), Point(385791, 6675320)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(385802, 6675313), Point(385810, 6675313), Point(385817, 6675317)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(385817, 6675317), Point(385819, 6675320), Point(385821, 6675326)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(385821, 6675326), Point(385821, 6675331), Point(385819, 6675337)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(385819, 6675337), Point(385813, 6675343), Point(385811, 6675344)), 2, State, 1, TrafficDirection.TowardsDigitizing, Roundabout, None, None)
    )

    val roundabout2 = Seq(
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(385152, 6671749), Point(385160, 6671757), Point(385168, 6671756)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(385160, 6671739), Point(385155, 6671742), Point(385152, 6671749)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(385160, 6671739), Point(385168, 6671740), Point(385171, 6671749)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(385171, 6671749), Point(385171, 6671752), Point(385168, 6671756)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None)
    )

    //Roundabout 3 incomplete with less than 3 links
    val roundabout3 = Seq(
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(384985, 6671649), Point(384980, 6671646), Point(384972, 6671647), Point(384970, 6671649)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(384970, 6671649), Point(384968, 6671653), Point(384969, 6671660), Point(384975, 6671664)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None)
    )

    //Roundabout 4 incomplete with more than 3 links
    val roundabout4 = Seq(
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(30, 30), Point(35, 30), Point(40, 30)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(30, 30), Point(30, 35), Point(30, 40)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(40, 30), Point(40, 35), Point(40, 40)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None)
    )

    val groupedRoundabouts = RoundaboutProcessor.groupByRoundabout(roundabout1 ++ roundabout2 ++ roundabout3 ++ roundabout4, false).map(_.map(_.linkId))

    groupedRoundabouts.size should be (2)
    groupedRoundabouts.find(_.size == roundabout1.size).get.forall(roundabout1.map(_.linkId).contains) should be (true)
    groupedRoundabouts.find(_.size == roundabout2.size).get.forall(roundabout2.map(_.linkId).contains) should be (true)
  }

  test("Grouping roundabout with less than 3 links") {

    val roundabout1 = Seq(
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(384985, 6671649), Point(384980, 6671646), Point(384972, 6671647), Point(384970, 6671649)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(384970, 6671649), Point(384968, 6671653), Point(384969, 6671660), Point(384985, 6671649)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None)
    )

    val roundabout2 = Seq(
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(38498510, 6671649), Point(384980, 6671646), Point(384972, 6671647), Point(38498510, 6671649)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None)
    )

    val roundabout3 = Seq(
      RoadLink(LinkIdGenerator.generateRandom(), Seq(Point(38498530, 6671649), Point(384980, 6671646), Point(384972, 6671647), Point(38498520, 6671649)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None)
    )

    val groupedRoundabouts = RoundaboutProcessor.groupByRoundabout(roundabout1 ++ roundabout2 ++ roundabout3, false).map(_.map(_.linkId))

    groupedRoundabouts.size should be (2)
    groupedRoundabouts.find(_.size == roundabout1.size).get.forall(roundabout1.map(_.linkId).contains) should be (true)
    groupedRoundabouts.find(_.size == roundabout2.size).get.forall(roundabout2.map(_.linkId).contains) should be (true)

  }

}

package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, VVHRoadlink}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import org.scalatest.{FunSuite, Matchers}

class RoundaboutProcessorSpec extends FunSuite with Matchers {

  private def getTrafficDirection(linkId: Long, roadLinks: Seq[RoadLinkLike]): TrafficDirection ={
    roadLinks.find(_.linkId == linkId).getOrElse(throw new NoSuchElementException).trafficDirection
  }

  test("Set traffic direction to Roundabouts road links real case 1"){

    val roadLinks = Seq(
      RoadLink(442445, Seq(Point(384970, 6671649), Point(384968, 6671653), Point(384969, 6671660), Point(384975, 6671664)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(442443, Seq(Point(384985, 6671649), Point(384980, 6671646), Point(384972, 6671647), Point(384970, 6671649)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(442442, Seq(Point(384985, 6671649), Point(384986, 6671650), Point(384987, 6671657), Point(384986, 6671660)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(442444, Seq(Point(384986, 6671660), Point(384983, 6671663), Point(384976, 6671665), Point(384975, 6671664)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None)
    )

    val (roadLinksWithDirections, _) = RoundaboutProcessor.setTrafficDirection(roadLinks)

    getTrafficDirection(442445, roadLinksWithDirections) should be (TrafficDirection.AgainstDigitizing)
    getTrafficDirection(442443, roadLinksWithDirections) should be (TrafficDirection.AgainstDigitizing)
    getTrafficDirection(442442, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
    getTrafficDirection(442444, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
  }

  test("Set traffic direction to Roundabouts road links real case 2"){

    val roadLinks = Seq(
      RoadLink(442434, Seq(Point(385152, 6671749), Point(385160, 6671757), Point(385168, 6671756)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(442436, Seq(Point(385160, 6671739), Point(385155, 6671742), Point(385152, 6671749)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(442435, Seq(Point(385160, 6671739), Point(385168, 6671740), Point(385171, 6671749)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(442437, Seq(Point(385171, 6671749), Point(385171, 6671752), Point(385168, 6671756)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None)
    )

    val (roadLinksWithDirections, _) = RoundaboutProcessor.setTrafficDirection(roadLinks)

    getTrafficDirection(442434, roadLinksWithDirections) should be (TrafficDirection.AgainstDigitizing)
    getTrafficDirection(442436, roadLinksWithDirections) should be (TrafficDirection.AgainstDigitizing)
    getTrafficDirection(442435, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
    getTrafficDirection(442437, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
  }

  test("Set traffic direction to Roundabouts road links real case 3"){

    val roadLinks = Seq(
      RoadLink(438869, Seq(Point(385795, 6675342), Point(385800, 6675344), Point(385806, 6675345), Point(385811, 6675344)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(438871, Seq(Point(385789, 6675331), Point(385791, 6675336), Point(385795, 6675342)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(438870, Seq(Point(385791, 6675320), Point(385789, 6675324), Point(385789, 6675331)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(438873, Seq(Point(385802, 6675313), Point(385797, 6675314), Point(385791, 6675320)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(438872, Seq(Point(385802, 6675313), Point(385810, 6675313), Point(385817, 6675317)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(438839, Seq(Point(385817, 6675317), Point(385819, 6675320), Point(385821, 6675326)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(438840, Seq(Point(385821, 6675326), Point(385821, 6675331), Point(385819, 6675337)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(438841, Seq(Point(385819, 6675337), Point(385813, 6675343), Point(385811, 6675344)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None)
    )

    val (roadLinksWithDirections, _) = RoundaboutProcessor.setTrafficDirection(roadLinks)

    getTrafficDirection(438869, roadLinksWithDirections) should be (TrafficDirection.AgainstDigitizing)
    getTrafficDirection(438871, roadLinksWithDirections) should be (TrafficDirection.AgainstDigitizing)
    getTrafficDirection(438870, roadLinksWithDirections) should be (TrafficDirection.AgainstDigitizing)
    getTrafficDirection(438873, roadLinksWithDirections) should be (TrafficDirection.AgainstDigitizing)
    getTrafficDirection(438872, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
    getTrafficDirection(438839, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
    getTrafficDirection(438840, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
    getTrafficDirection(438841, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
  }

  test("Set traffic direction to Roundabouts road links when the roundabout is a square (extreme case)"){

    val roadLinks = Seq(
      RoadLink(1, Seq(Point(30, 30), Point(35, 30), Point(40, 30)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(2, Seq(Point(30, 30), Point(30, 35), Point(30, 40)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(3, Seq(Point(40, 30), Point(40, 35), Point(40, 40)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(4, Seq(Point(30, 40), Point(35, 40), Point(40, 40)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None)
    )

    val (roadLinksWithDirections, _) = RoundaboutProcessor.setTrafficDirection(roadLinks)

    getTrafficDirection(1, roadLinksWithDirections) should be (TrafficDirection.AgainstDigitizing)
    getTrafficDirection(2, roadLinksWithDirections) should be (TrafficDirection.AgainstDigitizing)
    getTrafficDirection(3, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
    getTrafficDirection(4, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
  }

  test("Set traffic direction to Roundabouts road links when the roundabout is a triangle (extreme case)"){

    val roadLinks = Seq(
      RoadLink(1, Seq(Point(35, 30), Point(20, 35), Point(25, 40)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(2, Seq(Point(25, 40), Point(35, 40), Point(45, 40)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(3, Seq(Point(45, 40), Point(35, 35), Point(35, 30)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None)
    )

    val (roadLinksWithDirections, _) = RoundaboutProcessor.setTrafficDirection(roadLinks)

    getTrafficDirection(1, roadLinksWithDirections) should be (TrafficDirection.AgainstDigitizing)
    getTrafficDirection(2, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
    getTrafficDirection(3, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
  }

  test("Add changed road links to Roundabouts change set"){

    val roadLinks = Seq(
      RoadLink(438869, Seq(Point(385795, 6675342), Point(385800, 6675344), Point(385806, 6675345), Point(385811, 6675344)), 2, Municipality, 1, TrafficDirection.AgainstDigitizing, Roundabout, None, None),
      RoadLink(438871, Seq(Point(385789, 6675331), Point(385791, 6675336), Point(385795, 6675342)), 2, Municipality, 1, TrafficDirection.AgainstDigitizing, Roundabout, None, None),
      RoadLink(438870, Seq(Point(385791, 6675320), Point(385789, 6675324), Point(385789, 6675331)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(438873, Seq(Point(385802, 6675313), Point(385797, 6675314), Point(385791, 6675320)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(438872, Seq(Point(385802, 6675313), Point(385810, 6675313), Point(385817, 6675317)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(438839, Seq(Point(385817, 6675317), Point(385819, 6675320), Point(385821, 6675326)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(438840, Seq(Point(385821, 6675326), Point(385821, 6675331), Point(385819, 6675337)), 2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(438841, Seq(Point(385819, 6675337), Point(385813, 6675343), Point(385811, 6675344)), 2, Municipality, 1, TrafficDirection.TowardsDigitizing, Roundabout, None, None)
    )

    val changedRoadLinkIds = Set(438870, 438873, 438872, 438839, 438840)

    val (_, changeSet) = RoundaboutProcessor.setTrafficDirection(roadLinks)

    changeSet.trafficDirectionChanges.size should be (5)
    changedRoadLinkIds.forall(linkId => changeSet.trafficDirectionChanges.exists(_.linkId == linkId)) should be (true)
  }

  test("check if the road link is a roundabout") {
    val municipalityRoundaboutLink = RoadLink(438869, Seq(Point(385795, 6675342), Point(385800, 6675344), Point(385806, 6675345), Point(385811, 6675344)), 2, Municipality, 1, TrafficDirection.AgainstDigitizing, Roundabout, None, None)
    val stateRoundaboutLink = RoadLink(438869, Seq(Point(385795, 6675342), Point(385800, 6675344), Point(385806, 6675345), Point(385811, 6675344)), 2, State, 1, TrafficDirection.AgainstDigitizing, Roundabout, None, None)
    val stateMotorwayLink = RoadLink(438869, Seq(Point(385795, 6675342), Point(385800, 6675344), Point(385806, 6675345), Point(385811, 6675344)), 2, State, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None)
    val municipalityFreewayLink = RoadLink(438869, Seq(Point(385795, 6675342), Point(385800, 6675344), Point(385806, 6675345), Point(385811, 6675344)), 2, State, 1, TrafficDirection.AgainstDigitizing, Freeway, None, None)

    RoundaboutProcessor.isRoundaboutLink(municipalityRoundaboutLink) should be (false)
    RoundaboutProcessor.isRoundaboutLink(stateRoundaboutLink) should be (true)
    RoundaboutProcessor.isRoundaboutLink(stateMotorwayLink) should be (false)
    RoundaboutProcessor.isRoundaboutLink(municipalityFreewayLink) should be (false)
  }

  test("Group roundabouts links") {

    val roundabout1 = Seq(
      RoadLink(438869, Seq(Point(385795, 6675342), Point(385800, 6675344), Point(385806, 6675345), Point(385811, 6675344)), 2, State, 1, TrafficDirection.AgainstDigitizing, Roundabout, None, None),
      RoadLink(438871, Seq(Point(385789, 6675331), Point(385791, 6675336), Point(385795, 6675342)), 2, State, 1, TrafficDirection.AgainstDigitizing, Roundabout, None, None),
      RoadLink(438870, Seq(Point(385791, 6675320), Point(385789, 6675324), Point(385789, 6675331)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(438873, Seq(Point(385802, 6675313), Point(385797, 6675314), Point(385791, 6675320)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(438872, Seq(Point(385802, 6675313), Point(385810, 6675313), Point(385817, 6675317)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(438839, Seq(Point(385817, 6675317), Point(385819, 6675320), Point(385821, 6675326)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(438840, Seq(Point(385821, 6675326), Point(385821, 6675331), Point(385819, 6675337)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(438841, Seq(Point(385819, 6675337), Point(385813, 6675343), Point(385811, 6675344)), 2, State, 1, TrafficDirection.TowardsDigitizing, Roundabout, None, None)
    )

    val roundabout2 = Seq(
      RoadLink(442434, Seq(Point(385152, 6671749), Point(385160, 6671757), Point(385168, 6671756)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(442436, Seq(Point(385160, 6671739), Point(385155, 6671742), Point(385152, 6671749)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(442435, Seq(Point(385160, 6671739), Point(385168, 6671740), Point(385171, 6671749)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(442437, Seq(Point(385171, 6671749), Point(385171, 6671752), Point(385168, 6671756)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None)
    )

    //Roundabout 3 incomplete
    val roundabout3 = Seq(
      RoadLink(442443, Seq(Point(384985, 6671649), Point(384980, 6671646), Point(384972, 6671647), Point(384970, 6671649)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(442445, Seq(Point(384970, 6671649), Point(384968, 6671653), Point(384969, 6671660), Point(384975, 6671664)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None)
    )

    val groupedRoundabouts = RoundaboutProcessor.groupByRoundabout(roundabout1 ++ roundabout2 ++ roundabout3).map(_.map(_.linkId))

    groupedRoundabouts.size should be (3)
    groupedRoundabouts.find(_.size == roundabout1.size).get.forall(roundabout1.map(_.linkId).contains) should be (true)
    groupedRoundabouts.find(_.size == roundabout2.size).get.forall(roundabout2.map(_.linkId).contains) should be (true)
    groupedRoundabouts.find(_.size == roundabout3.size).get.forall(roundabout3.map(_.linkId).contains) should be (true)
  }

  test("Ignoring incomplete roundabouts from grouping") {

    val roundabout1 = Seq(
      RoadLink(438869, Seq(Point(385795, 6675342), Point(385800, 6675344), Point(385806, 6675345), Point(385811, 6675344)), 2, State, 1, TrafficDirection.AgainstDigitizing, Roundabout, None, None),
      RoadLink(438871, Seq(Point(385789, 6675331), Point(385791, 6675336), Point(385795, 6675342)), 2, State, 1, TrafficDirection.AgainstDigitizing, Roundabout, None, None),
      RoadLink(438870, Seq(Point(385791, 6675320), Point(385789, 6675324), Point(385789, 6675331)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(438873, Seq(Point(385802, 6675313), Point(385797, 6675314), Point(385791, 6675320)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(438872, Seq(Point(385802, 6675313), Point(385810, 6675313), Point(385817, 6675317)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(438839, Seq(Point(385817, 6675317), Point(385819, 6675320), Point(385821, 6675326)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(438840, Seq(Point(385821, 6675326), Point(385821, 6675331), Point(385819, 6675337)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(438841, Seq(Point(385819, 6675337), Point(385813, 6675343), Point(385811, 6675344)), 2, State, 1, TrafficDirection.TowardsDigitizing, Roundabout, None, None)
    )

    val roundabout2 = Seq(
      RoadLink(442434, Seq(Point(385152, 6671749), Point(385160, 6671757), Point(385168, 6671756)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(442436, Seq(Point(385160, 6671739), Point(385155, 6671742), Point(385152, 6671749)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(442435, Seq(Point(385160, 6671739), Point(385168, 6671740), Point(385171, 6671749)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(442437, Seq(Point(385171, 6671749), Point(385171, 6671752), Point(385168, 6671756)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None)
    )

    //Roundabout 3 incomplete with less than 3 links
    val roundabout3 = Seq(
      RoadLink(442443, Seq(Point(384985, 6671649), Point(384980, 6671646), Point(384972, 6671647), Point(384970, 6671649)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(442445, Seq(Point(384970, 6671649), Point(384968, 6671653), Point(384969, 6671660), Point(384975, 6671664)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None)
    )

    //Roundabout 4 incomplete with more than 3 links
    val roundabout4 = Seq(
      RoadLink(1, Seq(Point(30, 30), Point(35, 30), Point(40, 30)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(2, Seq(Point(30, 30), Point(30, 35), Point(30, 40)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(3, Seq(Point(40, 30), Point(40, 35), Point(40, 40)), 2, State, 1, TrafficDirection.BothDirections, Roundabout, None, None)
    )

    val groupedRoundabouts = RoundaboutProcessor.groupByRoundabout(roundabout1 ++ roundabout2 ++ roundabout3 ++ roundabout4, false).map(_.map(_.linkId))

    groupedRoundabouts.size should be (2)
    groupedRoundabouts.find(_.size == roundabout1.size).get.forall(roundabout1.map(_.linkId).contains) should be (true)
    groupedRoundabouts.find(_.size == roundabout2.size).get.forall(roundabout2.map(_.linkId).contains) should be (true)
  }

}

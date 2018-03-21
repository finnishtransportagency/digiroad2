package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.{Municipality, TrafficDirection}
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, VVHRoadlink}
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import org.scalatest.{FunSuite, Matchers}

class RoundaboutProcessorSpec extends FunSuite with Matchers {

  private def getTrafficDirection(linkId: Long, roadLinks: Seq[VVHRoadlink]): TrafficDirection ={
    roadLinks.find(_.linkId == linkId).getOrElse(throw new NoSuchElementException).trafficDirection
  }

  test("Set traffic direction to Roundabouts road links real case 1"){

    val roadLinks = Seq(
      VVHRoadlink(442445, 235, Seq(Point(384970, 6671649), Point(384968, 6671653), Point(384969, 6671660), Point(384975, 6671664)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235))),
      VVHRoadlink(442443, 235, Seq(Point(384985, 6671649), Point(384980, 6671646), Point(384972, 6671647), Point(384970, 6671649)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235))),
      VVHRoadlink(442442, 235, Seq(Point(384985, 6671649), Point(384986, 6671650), Point(384987, 6671657), Point(384986, 6671660)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235))),
      VVHRoadlink(442444, 235, Seq(Point(384986, 6671660), Point(384983, 6671663), Point(384976, 6671665), Point(384975, 6671664)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235)))
    )

    val (roadLinksWithDirections, _) = RoundaboutProcessor.setTrafficDirection(roadLinks)

    getTrafficDirection(442445, roadLinksWithDirections) should be (TrafficDirection.AgainstDigitizing)
    getTrafficDirection(442443, roadLinksWithDirections) should be (TrafficDirection.AgainstDigitizing)
    getTrafficDirection(442442, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
    getTrafficDirection(442444, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
  }

  test("Set traffic direction to Roundabouts road links real case 2"){

    val roadLinks = Seq(
      VVHRoadlink(442434, 235, Seq(Point(385152, 6671749), Point(385160, 6671757), Point(385168, 6671756)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
      VVHRoadlink(442436, 235, Seq(Point(385160, 6671739), Point(385155, 6671742), Point(385152, 6671749)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
      VVHRoadlink(442435, 235, Seq(Point(385160, 6671739), Point(385168, 6671740), Point(385171, 6671749)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
      VVHRoadlink(442437, 235, Seq(Point(385171, 6671749), Point(385171, 6671752), Point(385168, 6671756)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)
    )

    val (roadLinksWithDirections, _) = RoundaboutProcessor.setTrafficDirection(roadLinks)

    getTrafficDirection(442434, roadLinksWithDirections) should be (TrafficDirection.AgainstDigitizing)
    getTrafficDirection(442436, roadLinksWithDirections) should be (TrafficDirection.AgainstDigitizing)
    getTrafficDirection(442435, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
    getTrafficDirection(442437, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
  }

  test("Set traffic direction to Roundabouts road links real case 3"){

    val roadLinks = Seq(
      VVHRoadlink(438869, 235, Seq(Point(385795, 6675342), Point(385800, 6675344), Point(385806, 6675345), Point(385811, 6675344)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
      VVHRoadlink(438871, 235, Seq(Point(385789, 6675331), Point(385791, 6675336), Point(385795, 6675342)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
      VVHRoadlink(438870, 235, Seq(Point(385791, 6675320), Point(385789, 6675324), Point(385789, 6675331)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
      VVHRoadlink(438873, 235, Seq(Point(385802, 6675313), Point(385797, 6675314), Point(385791, 6675320)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
      VVHRoadlink(438872, 235, Seq(Point(385802, 6675313), Point(385810, 6675313), Point(385817, 6675317)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
      VVHRoadlink(438839, 235, Seq(Point(385817, 6675317), Point(385819, 6675320), Point(385821, 6675326)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
      VVHRoadlink(438840, 235, Seq(Point(385821, 6675326), Point(385821, 6675331), Point(385819, 6675337)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
      VVHRoadlink(438841, 235, Seq(Point(385819, 6675337), Point(385813, 6675343), Point(385811, 6675344)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)
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
      VVHRoadlink(1, 235, Seq(Point(30, 30), Point(35, 30), Point(40, 30)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
      VVHRoadlink(2, 235, Seq(Point(30, 30), Point(30, 35), Point(30, 40)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
      VVHRoadlink(3, 235, Seq(Point(40, 30), Point(40, 35), Point(40, 40)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
      VVHRoadlink(4, 235, Seq(Point(30, 40), Point(35, 40), Point(40, 40)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)
    )

    val (roadLinksWithDirections, _) = RoundaboutProcessor.setTrafficDirection(roadLinks)

    getTrafficDirection(1, roadLinksWithDirections) should be (TrafficDirection.AgainstDigitizing)
    getTrafficDirection(2, roadLinksWithDirections) should be (TrafficDirection.AgainstDigitizing)
    getTrafficDirection(3, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
    getTrafficDirection(4, roadLinksWithDirections) should be (TrafficDirection.TowardsDigitizing)
  }

  test("Add changed road links to Roundabouts change set"){

    val roadLinks = Seq(
      VVHRoadlink(438869, 235, Seq(Point(385795, 6675342), Point(385800, 6675344), Point(385806, 6675345), Point(385811, 6675344)), Municipality, TrafficDirection.AgainstDigitizing, FeatureClass.AllOthers),
      VVHRoadlink(438871, 235, Seq(Point(385789, 6675331), Point(385791, 6675336), Point(385795, 6675342)), Municipality, TrafficDirection.AgainstDigitizing, FeatureClass.AllOthers),
      VVHRoadlink(438870, 235, Seq(Point(385791, 6675320), Point(385789, 6675324), Point(385789, 6675331)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
      VVHRoadlink(438873, 235, Seq(Point(385802, 6675313), Point(385797, 6675314), Point(385791, 6675320)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
      VVHRoadlink(438872, 235, Seq(Point(385802, 6675313), Point(385810, 6675313), Point(385817, 6675317)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
      VVHRoadlink(438839, 235, Seq(Point(385817, 6675317), Point(385819, 6675320), Point(385821, 6675326)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
      VVHRoadlink(438840, 235, Seq(Point(385821, 6675326), Point(385821, 6675331), Point(385819, 6675337)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
      VVHRoadlink(438841, 235, Seq(Point(385819, 6675337), Point(385813, 6675343), Point(385811, 6675344)), Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers)
    )

    val changedRoadLinkIds = Set(438870, 438873, 438872, 438839, 438840)

    val (_, changeSet) = RoundaboutProcessor.setTrafficDirection(roadLinks)

    changeSet.trafficDirectionChanges.size should be (5)
    changedRoadLinkIds.forall(linkId => changeSet.trafficDirectionChanges.exists(_.linkId == linkId)) should be (true)
  }

}

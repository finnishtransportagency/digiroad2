package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.util.LinkIdGenerator
import fi.liikennevirasto.digiroad2.{Point, RoadAddress, Track}
import org.scalatest.{FunSuite, Matchers}

class RoadAddressServiceSpec extends FunSuite with Matchers{
  lazy val roadAddressService: RoadAddressService = {
    new RoadAddressService()
  }
  
  test("LRM calculation on Road Address") {
    val linkId = LinkIdGenerator.generateRandom()
    val towards = RoadAddressForLink(1L, 1L, 1L, Track.RightSide, 100, 110, None, None, linkId, 1.5, 11.4, SideCode.TowardsDigitizing, Seq(), false, None, None, None)
    val against = RoadAddressForLink(1L, 1L, 1L, Track.RightSide, 100, 110, None, None, linkId, 1.5, 11.4, SideCode.AgainstDigitizing, Seq(), false, None, None, None)
    towards.addressMValueToLRM(100L).get should be (1.5 +- .001)
    against.addressMValueToLRM(100L).get should be (11.4 +- .001)
    towards.addressMValueToLRM(110L).get should be (11.4 +- .001)
    against.addressMValueToLRM(110L).get should be (1.5 +- .001)
    towards.addressMValueToLRM(103L).get should be (4.470 +- .001)
    against.addressMValueToLRM(103L).get should be (8.430 +- .001)
    towards.addressMValueToLRM(99L)  should be (None)
    towards.addressMValueToLRM(111L) should be (None)
    against.addressMValueToLRM(99L)  should be (None)
    against.addressMValueToLRM(111L) should be (None)
  }


  test("missing information in middle of the road") {
    val linkId2 = LinkIdGenerator.generateRandom()
    val linkId5 = LinkIdGenerator.generateRandom()

    val links = Seq(
      RoadLink(linkId2, Seq(Point(415512.94000000041, 6989434.0329999998), Point(415707.37399999984, 6989417.0780000016), Point(415976.35800000001, 6989464.9849999994)), 10
        , State, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None,
        Map("MUNICIPALITYCODE" -> BigInt(216), "ROADNAME_FI" -> "Sininentie", "ROADNAME_SE" -> null, "ROADNAME_SM" -> null, "ROADNUMBER" -> "77", "ROADPARTNUMBER" -> "8")),
      RoadLink(linkId5, Seq(Point(415468.00499999989, 6989158.6240000017), Point(415487.87299999967, 6989275.7030000016), Point(415512.94000000041, 6989434.0329999998)), 10
        , State, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None,
        Map("MUNICIPALITYCODE" -> BigInt(216), "ROADNAME_FI" -> "Yhteisahontie", "ROADNAME_SE" -> null, "ROADNAME_SM" -> null, "ROADNUMBER" -> "648", "ROADPARTNUMBER" -> "8"))
    )

    val testData = Map(
      (s"${linkId2}_start", RoadAddress(Some("216"), 77, 8, Track.Combined, 0)),
      (s"${linkId2}_end", RoadAddress(Some("216"), 77, 8, Track.Combined, 468)),
      (s"${linkId5}_start", RoadAddress(Some("216"), 648, 8, Track.Combined, 6416)),
      (s"${linkId5}_end", RoadAddress(Some("216"), 648, 8, Track.Combined, 6695)))


    val toCreate = roadAddressService.mapVkmInfosIntoRoadLinks(testData, links).map(_.roadAddress)

    toCreate.size should be(2)
    val createdInSininentie = toCreate.find(_.linkId == linkId2)
    createdInSininentie.nonEmpty should be(true)
    createdInSininentie.get.sideCode.get should be(SideCode.TowardsDigitizing)

    val createdInYhteisahontie = toCreate.find(_.linkId == linkId5)
    createdInYhteisahontie.nonEmpty should be(true)
    createdInYhteisahontie.get.sideCode.get should be(SideCode.TowardsDigitizing)
  }
}

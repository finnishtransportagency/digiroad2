package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.Track.{Combined, LeftSide, RightSide}
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.VKMClient
import fi.liikennevirasto.digiroad2.dao.RoadAddressTempDAO
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService, RoadAddressForLink}
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point, RoadAddress, Track}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verify, when}
import org.mockito.{ArgumentCaptor, Mockito}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

class ResolveFrozenRoadLinksSpec extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockRoadAddressService = MockitoSugar.mock[RoadAddressService]
  val mockVKMClient = MockitoSugar.mock[VKMClient]
  val mockRoadLinkTempDao = MockitoSugar.mock[RoadAddressTempDAO]

  object ResolvingFrozenRoadLinksTest extends ResolvingFrozenRoadLinks {
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val roadAddressService: RoadAddressService = mockRoadAddressService
    override lazy val vkmClient: VKMClient = mockVKMClient
    override lazy val roadLinkTempDao: RoadAddressTempDAO = mockRoadLinkTempDao
  }
  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  test("start processing test") {
    val linkId1 = LinkIdGenerator.generateRandom()
    val linkId2 = LinkIdGenerator.generateRandom()
    val linkId3 = LinkIdGenerator.generateRandom()
    val roadLinks = Seq(
      RoadLink(linkId1,List(Point(376570.341,6992722.195,160.24099999999453), Point(376534.023,6992725.668,160.875)),36.577,
        State,99, TrafficDirection.TowardsDigitizing,UnknownLinkType, None, None,
        Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16",
          "CREATED_DATE" -> BigInt(1446398762000L), "LAST_EDITED_DATE" -> BigInt(1584662329000L))),
      RoadLink(linkId2,List(Point(376586.275,6992719.353,159.9869999999937), Point(376570.341,6992722.195,160.24099999999453)),16.1855,
        State,99, TrafficDirection.TowardsDigitizing,UnknownLinkType, None, None,
        Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16",
          "CREATED_DATE" -> BigInt(1446398700000L))))

    when(mockRoadLinkService.getRoadLinksByMunicipality(312, false)).thenReturn(roadLinks)
    when(mockRoadAddressService.getAllByLinkIds(roadLinks.map(_.linkId))).thenReturn(Seq())
    when(mockRoadAddressService.groupRoadAddress(Seq())).thenReturn(Seq())

    val roadLinksTemp = Seq(RoadAddressTEMP(linkId1,7421,1,Combined,1312,1332,0.0,20.0,List(),Some(TowardsDigitizing),Some(5),Some("2019-11-30 21:55:45.0")),
      RoadAddressTEMP(linkId2,7421,1,Combined,1332,1500,0.0,20.0,List(),Some(TowardsDigitizing),Some(5),Some("2019-11-30 21:55:45.0")),
      RoadAddressTEMP(linkId3,7421,1,Combined,1312,1332,0.0,20.0,List(),Some(TowardsDigitizing),Some(5),Some("2019-11-30 21:55:45.0")))

    when(mockRoadLinkTempDao.getByMunicipality(312)).thenReturn(roadLinksTemp)

    ResolvingFrozenRoadLinksTest.resolveAddressesOnOverlappingGeometry(312)

    // Temp road address on linkId3 should be deleted, because linkId3 does not exist anymore
    val captor = ArgumentCaptor.forClass(classOf[Set[String]])
    verify(mockRoadLinkTempDao, Mockito.atLeastOnce).deleteInfoByLinkIds(captor.capture)
    captor.getAllValues.size() should be (1)
    captor.getValue.asInstanceOf[Set[String]].head should be (linkId3)
  }

  test("missing information in middle of the road"){
    val linkId1 = LinkIdGenerator.generateRandom()
    val linkId2 = LinkIdGenerator.generateRandom()
    val linkId3 = LinkIdGenerator.generateRandom()
    val linkId4 = LinkIdGenerator.generateRandom()
    val linkId5 = LinkIdGenerator.generateRandom()
    val linkId6 = LinkIdGenerator.generateRandom()

    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(415512.94000000041,6989434.0329999998), Point(415349.89199999999,6989472.9849999994), Point(415141.25800000038, 6989503.9090000018)), 10
        ,State, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None,
        Map("MUNICIPALITYCODE" -> BigInt(216), "ROADNAME_FI" -> "Sininentie", "ROADNAME_SE" -> null, "ROADNAME_SM" -> null, "ROADNUMBER" -> "77", "ROADPARTNUMBER" -> "7")),
      RoadLink(linkId2, Seq(Point(415512.94000000041, 6989434.0329999998), Point(415707.37399999984, 6989417.0780000016), Point(415976.35800000001, 6989464.9849999994)), 10
        ,State, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None,
        Map("MUNICIPALITYCODE" -> BigInt(216), "ROADNAME_FI"-> "Sininentie", "ROADNAME_SE"-> null, "ROADNAME_SM"-> null, "ROADNUMBER"-> "77", "ROADPARTNUMBER" -> "8")),
      RoadLink(linkId3, Seq(Point(415512.94000000041, 6989434.0329999998), Point(415530.69299999997, 6989518.8949999996)), 10
        ,State, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None,
        Map("MUNICIPALITYCODE" -> BigInt(216), "ROADNAME_FI" -> "Kämärintie", "ROADNAME_SE" -> null, "ROADNAME_SM" -> null, "ROADNUMBER" -> "16934", "ROADPARTNUMBER" -> "1")),
      RoadLink(linkId4, Seq(Point(415976.35800000001, 6989464.9849999994), Point(416063.48300000001, 6989495.443)), 10
        ,State, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None,
        Map("MUNICIPALITYCODE" -> BigInt(216), "ROADNAME_FI" -> "Sininentie", "ROADNAME_SE" -> null, "ROADNAME_SM" -> null, "ROADNUMBER" -> "77", "ROADPARTNUMBER" -> "8")),
      RoadLink(linkId5, Seq(Point(415468.00499999989, 6989158.6240000017), Point(415487.87299999967, 6989275.7030000016), Point(415512.94000000041, 6989434.0329999998)), 10
        , State, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None,
        Map("MUNICIPALITYCODE" -> BigInt(216), "ROADNAME_FI" -> "Yhteisahontie", "ROADNAME_SE" -> null, "ROADNAME_SM" -> null, "ROADNUMBER" -> "648", "ROADPARTNUMBER" -> "8")),
      RoadLink(linkId6, Seq(Point(415464.78699999955,6989139.6889999993), Point(415468.00499999989, 6989158.6240000017)), 10
        , State, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None,
        Map("MUNICIPALITYCODE" -> BigInt(216), "ROADNAME_FI" -> "Yhteisahontie", "ROADNAME_SE" -> null, "ROADNAME_SM" -> null, "ROADNUMBER" -> "648", "ROADPARTNUMBER" -> "8")))

    val viiteRoadAddress = Seq(RoadAddressForLink(21675,77,7,Combined,4082,4461,None,None,
      linkId1,0.0,378.889,AgainstDigitizing,List(),false,None,None,None),
      RoadAddressForLink(21707,77,8,Combined,469,562,None,None,
        linkId4,0.0,92.297,TowardsDigitizing,List(),false,None,None,None),
      RoadAddressForLink(21717,648,8,Combined,6396,6415,None,None,
        linkId6,0.0,19.207,TowardsDigitizing,List(),false,None,None,None),
      RoadAddressForLink(23366,16934,1,Combined,0,87,None,None,
        linkId3,0.0,86.741,TowardsDigitizing,List(),false,None,None,None))


    RoadAddress(Some("216"), 648, 8, Track.Combined, 6416)
    RoadAddress(Some("216"), 648, 8, Track.Combined, 6695)

    when(mockRoadLinkService.getRoadLinksByMunicipality(216, false)).thenReturn(roadLinks)
    when(mockRoadAddressService.getAllByLinkIds(roadLinks.map(_.linkId))).thenReturn(viiteRoadAddress)
    when(mockRoadAddressService.groupRoadAddress(viiteRoadAddress)).thenReturn(viiteRoadAddress)

    when(mockVKMClient.coordToAddress(Point(415512.9400000004, 6989434.033), Some(77), Some(8), includePedestrian = Some(true)))
      .thenReturn(RoadAddress(Some("216"), 77, 8, Track.Combined, 0))
    when(mockVKMClient.coordToAddress(Point(415976.358,6989464.984999999), Some(77), Some(8), includePedestrian = Some(true)))
      .thenReturn(RoadAddress(Some("216"), 77, 8, Track.Combined, 468))

    when(mockVKMClient.coordToAddress(Point(415468.0049999999,6989158.624000002), Some(648), Some(8), includePedestrian = Some(true)))
      .thenReturn(RoadAddress(Some("216"), 648, 8, Track.Combined, 6416))
    when(mockVKMClient.coordToAddress(Point(415512.9400000004,6989434.033), Some(648), Some(8), includePedestrian = Some(true)))
      .thenReturn(RoadAddress(Some("216"), 648, 8, Track.Combined, 6695))

    when(mockRoadLinkTempDao.getByMunicipality(216)).thenReturn(Seq())

    val toCreate = ResolvingFrozenRoadLinksTest.resolveAddressesOnOverlappingGeometry(216)._1.map(_.roadAddress)

    toCreate.size should be (2)
    val createdInSininentie = toCreate.find(_.linkId == linkId2)
    createdInSininentie.nonEmpty should be (true)
    createdInSininentie.get.sideCode.get should be (SideCode.TowardsDigitizing)

    val createdInYhteisahontie = toCreate.find(_.linkId == linkId5)
    createdInYhteisahontie.nonEmpty should be (true)
    createdInYhteisahontie.get.sideCode.get should be (SideCode.TowardsDigitizing)
  }

  test("missing right and left ajorata"){
    val linkId1 = LinkIdGenerator.generateRandom()
    val linkId2 = LinkIdGenerator.generateRandom()
    val linkId3 = LinkIdGenerator.generateRandom()
    val linkId4 = LinkIdGenerator.generateRandom()
    val linkId5 = LinkIdGenerator.generateRandom()
    val linkId6 = LinkIdGenerator.generateRandom()
    val linkId7 = LinkIdGenerator.generateRandom()
    val linkId8 = LinkIdGenerator.generateRandom()

    val roadLinks = Seq(
      RoadLink(linkId1,List(Point(376585.751,6992711.448,159.9759999999951), Point(376569.312,6992714.125,160.19400000000314)),16.65,
        State,99, TrafficDirection.AgainstDigitizing,UnknownLinkType, None, None,
        Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16")),
      RoadLink(linkId2,List(Point(376570.341,6992722.195,160.24099999999453), Point(376534.023,6992725.668,160.875)),36.577,
        State,99, TrafficDirection.TowardsDigitizing,UnknownLinkType, None, None,
        Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16")),
      RoadLink(linkId3,List(Point(376586.275,6992719.353,159.9869999999937), Point(376570.341,6992722.195,160.24099999999453)),16.1855,
        State,99, TrafficDirection.TowardsDigitizing,UnknownLinkType, None, None,
        Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16")),
      RoadLink(linkId4,List(Point(376519.312,6992724.148,161.00800000000163), Point(376534.023,6992725.668,160.875)),14.790,
        State,99, TrafficDirection.AgainstDigitizing,UnknownLinkType, None, None,
        Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16")),
      RoadLink(linkId5,List(Point(376569.312,6992714.125,160.19400000000314), Point(376519.312,6992724.148,161.00800000000163)),50.999,
        State,99, TrafficDirection.AgainstDigitizing,UnknownLinkType, None, None,
        Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16")),
      RoadLink(linkId6,List(Point(376412.388,6992717.601,161.53100000000268), Point(376502.352,6992724.075,161.04799999999523), Point(376519.312,6992724.148,161.00800000000163)),107.2053,
        State,99, TrafficDirection.BothDirections,UnknownLinkType, None, None,
        Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16")),
      RoadLink(linkId7,List(Point(376642.368,6992709.787,160.07399999999325), Point(376593.53,6992710.187,159.96400000000722), Point(376585.751,6992711.448,159.9759999999951)),56.9052,
        State,99,TrafficDirection.AgainstDigitizing,UnknownLinkType,None, None,
        Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29",  "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16")),
      RoadLink(linkId8,List(Point(376586.275,6992719.353,159.9869999999937), Point(376630.419,6992726.587,159.94599999999627), Point(376639.195,6992733.214,160.125)),56.885,
        State,99,TrafficDirection.AgainstDigitizing,UnknownLinkType, None, None,
        Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16")))

    val viiteRoadAddress = Seq(
      RoadAddressForLink(48229,16,29,Combined,4583,4690,None,None,
        linkId6,0.0,107.205,TowardsDigitizing,List(),false,None,None,None),
      RoadAddressForLink(81202,16,29,RightSide,4690,4741,None,None,
        linkId5,0.0,51.0,AgainstDigitizing,List(),false,None,None,None),
      RoadAddressForLink(81202,16,29,RightSide,4758,4815,None,None,
        linkId7,0.0,56.905,AgainstDigitizing,List(),false,None,None,None))

    when(mockRoadLinkService.getRoadLinksByMunicipality(312, false)).thenReturn(roadLinks)
    when(mockRoadAddressService.getAllByLinkIds(roadLinks.map(_.linkId))).thenReturn(viiteRoadAddress)
    when(mockRoadAddressService.groupRoadAddress(viiteRoadAddress)).thenReturn(viiteRoadAddress)

    when(mockVKMClient.coordToAddress(Point(376585.751,6992711.448,159.9759999999951),
      Some(16), Some(29), includePedestrian = Some(true))).thenThrow(new NullPointerException)
    when(mockVKMClient.coordToAddress(Point(376569.312,6992714.125,160.19400000000314),
      Some(16), Some(29), includePedestrian = Some(true))).thenThrow(new NullPointerException)

    when(mockVKMClient.coordToAddress(Point(376570.341,6992722.195,160.24099999999453), Some(16), Some(29), includePedestrian = Some(true)))
      .thenReturn(RoadAddress(Some("312"), 16, 29, Track.RightSide, 4740))
    when(mockVKMClient.coordToAddress(Point(376534.023,6992725.668,160.875), Some(16), Some(29), includePedestrian = Some(true)))
      .thenReturn(RoadAddress(Some("312"), 16, 29, Track.RightSide, 4704))

    when(mockVKMClient.coordToAddress(Point(376586.275,6992719.353,159.9869999999937), Some(16), Some(29), includePedestrian = Some(true)))
      .thenReturn(RoadAddress(Some("312"), 16, 29, Track.RightSide, 4757))
    when(mockVKMClient.coordToAddress(Point(376570.341,6992722.195,160.24099999999453), Some(16), Some(29), includePedestrian = Some(true)))
      .thenReturn(RoadAddress(Some("312"), 16, 29, Track.RightSide, 4740))

    when(mockVKMClient.coordToAddress(Point(376519.312,6992724.148,161.00800000000163), Some(16), Some(29), includePedestrian = Some(true)))
      .thenReturn(RoadAddress(Some("312"), 16, 29, Track.Combined, 4690))
    when(mockVKMClient.coordToAddress(Point(376534.023,6992725.668,160.875), Some(16), Some(29), includePedestrian = Some(true)))
      .thenReturn(RoadAddress(Some("312"), 16, 29, Track.RightSide, 4704))

    when(mockVKMClient.coordToAddress(Point(376586.275,6992719.353,159.9869999999937), Some(16), Some(29), includePedestrian = Some(true)))
      .thenReturn(RoadAddress(Some("312"), 16, 29, Track.RightSide, 4690))
    when(mockVKMClient.coordToAddress(Point(376639.195,6992733.214,160.125), Some(16), Some(29), includePedestrian = Some(true)))
      .thenReturn(RoadAddress(Some("312"), 16, 29, Track.RightSide, 4808))

    when(mockRoadLinkTempDao.getByMunicipality(312)).thenReturn(Seq())

    val toCreate = ResolvingFrozenRoadLinksTest.resolveAddressesOnOverlappingGeometry(312)._1.map(_.roadAddress)

    toCreate.size should be (4)
    toCreate.exists(x => x.linkId == linkId2 && x.sideCode.contains(SideCode.AgainstDigitizing) && x.track == Track.LeftSide) should be (true)
    toCreate.exists(x => x.linkId == linkId8 && x.sideCode.contains(SideCode.TowardsDigitizing) && x.track == Track.LeftSide) should be (true)
    toCreate.exists(x => x.linkId == linkId3 && x.sideCode.contains(SideCode.TowardsDigitizing) && x.track == Track.LeftSide) should be (true)
    toCreate.exists(x => x.linkId == linkId4 && x.sideCode.contains(SideCode.TowardsDigitizing) && x.track == Track.LeftSide) should be (true)
  }

  test("cleaning missing addresses without success") {
    val linkId1 = LinkIdGenerator.generateRandom()
    val linkId2 = LinkIdGenerator.generateRandom()
    val linkId3 = LinkIdGenerator.generateRandom()
    val linkId4 = LinkIdGenerator.generateRandom()
    val linkId5 = LinkIdGenerator.generateRandom()

    val road1 = RoadLink(linkId1,List(Point(376570.341,6992722.195,160.24099999999453), Point(376534.023,6992725.668,160.875)),36.577,
      State,99, TrafficDirection.TowardsDigitizing,UnknownLinkType, None, None,
      Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16"))
    val road2 = RoadLink(linkId2,List(Point(376586.275,6992719.353,159.9869999999937), Point(376570.341,6992722.195,160.24099999999453)),16.1855,
      State,99, TrafficDirection.TowardsDigitizing,UnknownLinkType, None, None,
      Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16"))
    val road3 = RoadLink(linkId3,List(Point(376519.312,6992724.148,161.00800000000163), Point(376534.023,6992725.668,160.875)),14.790,
      State,99, TrafficDirection.AgainstDigitizing,UnknownLinkType, None, None,
      Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16"))
    val road4 = RoadLink(linkId4,List(Point(376569.312,6992714.125,160.19400000000314), Point(376519.312,6992724.148,161.00800000000163)),50.999,
      State,99, TrafficDirection.AgainstDigitizing,UnknownLinkType, None, None,
      Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16"))
    val road5 = RoadLink(linkId5,List(Point(376412.388,6992717.601,161.53100000000268), Point(376502.352,6992724.075,161.04799999999523), Point(376519.312,6992724.148,161.00800000000163)),107.2053,
      State,99, TrafficDirection.BothDirections,UnknownLinkType, None, None,
      Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16"))

    val roadLinks = Seq(road1, road2, road3, road4, road5)

    val address = Seq(
      RoadAddressForLink(48229,16,29,Combined,4583,4690,None,None,
        linkId5,0.0,107.205,TowardsDigitizing,List(),false,None,None,None),
      RoadAddressForLink(81202,16,29,RightSide,4690,4741,None,None,
        linkId4,0.0,51.0,AgainstDigitizing,List(),false,None,None,None),
      RoadAddressForLink(81200, 16, 29, LeftSide, 4740,4757,None,None,
        linkId2,0.0,16.18,AgainstDigitizing,List(),false,None,None,None))

    when(mockRoadAddressService.getAllByLinkIds(any[Seq[String]])).thenReturn(address)

    val first = Point(376519.312, 6992724.148, 161.00800000000163)
    val last = Point(376534.023, 6992725.668, 160.875)
    val roadLinkWithPoints = RoadLinkWithPoints(first, last, road3)
    val missingRoadLinks = RoadLinkWithPointsAndAdjacents(roadLinkWithPoints, Seq(road1, road4, road5))

    val result = ResolvingFrozenRoadLinksTest.resolveAddressesRecursively(Seq(missingRoadLinks), Seq(), Seq())
    result.size should be (0)

  }

  test("cleaning missing addresses success") {
    val linkId1 = LinkIdGenerator.generateRandom()
    val linkId2 = LinkIdGenerator.generateRandom()
    val linkId3 = LinkIdGenerator.generateRandom()
    val linkId4 = LinkIdGenerator.generateRandom()
    val linkId5 = LinkIdGenerator.generateRandom()

    val road1 = RoadLink(linkId1,List(Point(376570.341,6992722.195,160.24099999999453), Point(376534.023,6992725.668,160.875)),36.577,
      State,99, TrafficDirection.TowardsDigitizing,UnknownLinkType, None, None,
      Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16"))
    val road2 = RoadLink(linkId2,List(Point(376586.275,6992719.353,159.9869999999937), Point(376570.341,6992722.195,160.24099999999453)),16.1855,
      State,99, TrafficDirection.TowardsDigitizing,UnknownLinkType, None, None,
      Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16"))
    val road3 = RoadLink(linkId3,List(Point(376519.312,6992724.148,161.00800000000163), Point(376534.023,6992725.668,160.875)),14.790,
      State,99, TrafficDirection.AgainstDigitizing,UnknownLinkType, None, None,
      Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16"))
    val road4 = RoadLink(linkId4,List(Point(376569.312,6992714.125,160.19400000000314), Point(376519.312,6992724.148,161.00800000000163)),50.999,
      State,99, TrafficDirection.AgainstDigitizing,UnknownLinkType, None, None,
      Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16"))
    val road5 = RoadLink(linkId5,List(Point(376412.388,6992717.601,161.53100000000268), Point(376502.352,6992724.075,161.04799999999523), Point(376519.312,6992724.148,161.00800000000163)),107.2053,
      State,99, TrafficDirection.BothDirections,UnknownLinkType, None, None,
      Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16"))

    val roadLinks = Seq(road1, road2, road3, road4, road5)

    val address = Seq(
      RoadAddressForLink(48229,16,29,Combined,4583,4690,None,None,
        linkId5,0.0,107.205,TowardsDigitizing,List(),false,None,None,None),
      RoadAddressForLink(81202,16,29,RightSide,4690,4741,None,None,
        linkId4,0.0,51.0,AgainstDigitizing,List(),false,None,None,None),
      RoadAddressForLink(81200, 16, 29, LeftSide, 4740,4757,None,None,
        linkId1,0.0,16.18,AgainstDigitizing,List(),false,None,None,None))


    val mappedAddresses = address.flatMap { address =>
      Seq(road1, road4, road5).find(_.linkId == address.linkId).map { roadLink =>
        val (first, last) = GeometryUtils.geometryEndpoints(roadLink.geometry)
        RoadAddressTEMPwithPoint(first, last, RoadAddressTEMP(address.linkId, address.roadNumber,
          address.roadPartNumber, address.track, address.startAddrMValue, address.endAddrMValue,
          address.startMValue, address.endMValue, address.geom, Some(address.sideCode), Some(roadLink.municipalityCode)))
      }
    }

    val first = Point(376519.312, 6992724.148, 161.00800000000163)
    val last = Point(376534.023, 6992725.668, 160.875)
    val roadLinkWithPoints = RoadLinkWithPoints(first, last, road3)
    val missingRoadLinks = RoadLinkWithPointsAndAdjacents(roadLinkWithPoints, Seq(road1, road4, road5))

    val result = ResolvingFrozenRoadLinksTest.resolveAddressesRecursively(Seq(missingRoadLinks), mappedAddresses, Seq())

    result.size should be (1)
    result.exists(x => x.track == LeftSide && x.sideCode.contains(SideCode.TowardsDigitizing))

  }

  test("Postgres drop trailing millisecond zero, add it back by using to_char(created_date, 'YYYY-MM-DD HH:MI:SS.MS')") {
    val linkId = LinkIdGenerator.generateRandom()
    val roadLinkTempDao = new RoadAddressTempDAO()
    runWithRollback{
      sqlu"""INSERT INTO temp_road_address_info (id,link_id,municipality_code,road_number,road_part,track_code,start_address_m,end_address_m,start_m_value,end_m_value,side_code,created_date,created_by) VALUES (nextval('primary_key_seq'),$linkId,1,1,1,2,1,1,0,11.1,1,'2019-12-01 12:41:21.000','test');""".execute
      val roadAddress1 =  roadLinkTempDao.getByLinkIds(Set(linkId))
      val roadAddress2 =  roadLinkTempDao.getByRoadNumber(1)
      val roadAddress3 =  roadLinkTempDao.getByMunicipality(1)
      roadAddress1.last.createdDate.get should be("2019-12-01 12:41:21.000")
      roadAddress2.last.createdDate.get should be("2019-12-01 12:41:21.000")
      roadAddress3.last.createdDate.get should be("2019-12-01 12:41:21.000")
    }
  }
}
package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset.{SideCode, _}
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.util.Track.{Combined, LeftSide, RightSide}
import org.mockito.Mockito.{verify, when}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import fi.liikennevirasto.digiroad2.dao.{RoadAddressTEMP, RoadLinkTempDAO, RoadAddress => ViiteRoadAddress}
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentCaptor, Mockito}

class ResolveFrozenRoadLinksSpec extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockRoadAddressService = MockitoSugar.mock[RoadAddressService]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockVKMGeometryTransform = MockitoSugar.mock[VKMGeometryTransform]
  val mockRoadLinkTempDao = MockitoSugar.mock[RoadLinkTempDAO]

  object ResolvingFrozenRoadLinksTest extends ResolvingFrozenRoadLinks {
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
    override lazy val roadAddressService: RoadAddressService = mockRoadAddressService
    override lazy val geometryVKMTransform: VKMGeometryTransform = mockVKMGeometryTransform
    // override lazy val geometryVKMTransform: VKMGeometryTransform = mockGeometryVKMTransform
    override lazy val roadLinkTempDao: RoadLinkTempDAO = mockRoadLinkTempDao
  }

  test("start processing test") {

    val roadLinks = Seq(
      RoadLink(11478947,List(Point(376570.341,6992722.195,160.24099999999453), Point(376534.023,6992725.668,160.875)),36.577,
        State,99, TrafficDirection.TowardsDigitizing,UnknownLinkType, None, None,
        Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16",
          "CREATED_DATE" -> BigInt(1446398762000L), "LAST_EDITED_DATE" -> BigInt(1584662329000L))),
      RoadLink(11478953,List(Point(376586.275,6992719.353,159.9869999999937), Point(376570.341,6992722.195,160.24099999999453)),16.1855,
        State,99, TrafficDirection.TowardsDigitizing,UnknownLinkType, None, None,
        Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16",
          "CREATED_DATE" -> BigInt(1446398700000L))))

    when(mockRoadLinkService.getRoadLinksFromVVHByMunicipality(312, false)).thenReturn(roadLinks)
    when(mockRoadAddressService.getAllByLinkIds(roadLinks.map(_.linkId))).thenReturn(Seq())

    val roadLinksTemp = Seq(RoadAddressTEMP(11478947,7421,1,Combined,1312,1332,0.0,20.0,List(),Some(TowardsDigitizing),Some(5),Some("2019-11-30 21:55:45.0")),
      RoadAddressTEMP(11478953,7421,1,Combined,1332,1500,0.0,20.0,List(),Some(TowardsDigitizing),Some(5),Some("2019-11-30 21:55:45.0")))

    when(mockRoadLinkTempDao.getByMunicipality(312)).thenReturn(roadLinksTemp)

    ResolvingFrozenRoadLinksTest.processing(312)

    val captor = ArgumentCaptor.forClass(classOf[Set[Long]])
    verify(mockRoadLinkTempDao, Mockito.atLeastOnce).deleteInfoByLinkIds(captor.capture)
    captor.getAllValues.size() should be (1)
    captor.getValue.asInstanceOf[Set[Long]].head should be (11478947)
  }

  test("missing information in middle of the road"){
    val roadLinks = Seq(
      RoadLink(1490363, Seq(Point(415512.94000000041,6989434.0329999998), Point(415349.89199999999,6989472.9849999994), Point(415141.25800000038, 6989503.9090000018)), 10
        ,State, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None,
        Map("MUNICIPALITYCODE" -> BigInt(216), "ROADNAME_FI" -> "Sininentie", "ROADNAME_SE" -> null, "ROADNAME_SM" -> null, "ROADNUMBER" -> "77", "ROADPARTNUMBER" -> "7")),
      RoadLink(1490369, Seq(Point(415512.94000000041, 6989434.0329999998), Point(415707.37399999984, 6989417.0780000016), Point(415976.35800000001, 6989464.9849999994)), 10
        ,State, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None,
        Map("MUNICIPALITYCODE" -> BigInt(216), "ROADNAME_FI"-> "Sininentie", "ROADNAME_SE"-> null, "ROADNAME_SM"-> null, "ROADNUMBER"-> "77", "ROADPARTNUMBER" -> "8")),
      RoadLink(1490371, Seq(Point(415512.94000000041, 6989434.0329999998), Point(415530.69299999997, 6989518.8949999996)), 10
        ,State, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None,
        Map("MUNICIPALITYCODE" -> BigInt(216), "ROADNAME_FI" -> "Kämärintie", "ROADNAME_SE" -> null, "ROADNAME_SM" -> null, "ROADNUMBER" -> "16934", "ROADPARTNUMBER" -> "1")),
      RoadLink(1490374, Seq(Point(415976.35800000001, 6989464.9849999994), Point(416063.48300000001, 6989495.443)), 10
        ,State, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None,
        Map("MUNICIPALITYCODE" -> BigInt(216), "ROADNAME_FI" -> "Sininentie", "ROADNAME_SE" -> null, "ROADNAME_SM" -> null, "ROADNUMBER" -> "77", "ROADPARTNUMBER" -> "8")),
      RoadLink(1490376, Seq(Point(415468.00499999989, 6989158.6240000017), Point(415487.87299999967, 6989275.7030000016), Point(415512.94000000041, 6989434.0329999998)), 10
        , State, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None,
        Map("MUNICIPALITYCODE" -> BigInt(216), "ROADNAME_FI" -> "Yhteisahontie", "ROADNAME_SE" -> null, "ROADNAME_SM" -> null, "ROADNUMBER" -> "648", "ROADPARTNUMBER" -> "8")),
      RoadLink(1490379, Seq(Point(415464.78699999955,6989139.6889999993), Point(415468.00499999989, 6989158.6240000017)), 10
        , State, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None,
        Map("MUNICIPALITYCODE" -> BigInt(216), "ROADNAME_FI" -> "Yhteisahontie", "ROADNAME_SE" -> null, "ROADNAME_SM" -> null, "ROADNUMBER" -> "648", "ROADPARTNUMBER" -> "8")))

    val viiteRoadAddress = Seq(ViiteRoadAddress(21675,77,7,Combined,4082,4461,None,None,
      1490363,0.0,378.889,AgainstDigitizing,List(),false,None,None,None),
      ViiteRoadAddress(21707,77,8,Combined,469,562,None,None,
        1490374,0.0,92.297,TowardsDigitizing,List(),false,None,None,None),
      ViiteRoadAddress(21717,648,8,Combined,6396,6415,None,None,
        1490379,0.0,19.207,TowardsDigitizing,List(),false,None,None,None),
      ViiteRoadAddress(23366,16934,1,Combined,0,87,None,None,
        1490371,0.0,86.741,TowardsDigitizing,List(),false,None,None,None))


    RoadAddress(Some("216"), 648, 8, Track.Combined, 6416)
    RoadAddress(Some("216"), 648, 8, Track.Combined, 6695)

    when(mockRoadLinkService.getRoadLinksFromVVHByMunicipality(216, false)).thenReturn(roadLinks)
    when(mockRoadAddressService.getAllByLinkIds(roadLinks.map(_.linkId))).thenReturn(viiteRoadAddress)

    when(mockVKMGeometryTransform.coordsToAddresses(Seq(Point(415512.9400000004, 6989434.033), Point(415976.358,6989464.984999999)), Some(77), Some(8), includePedestrian = Some(true)))
      .thenReturn( Seq(RoadAddress(Some("216"), 77, 8, Track.Combined, 0), RoadAddress(Some("216"), 77, 8, Track.Combined, 468)))

    when(mockVKMGeometryTransform.coordsToAddresses(Seq(Point(415468.0049999999,6989158.624000002), Point(415512.9400000004,6989434.033)), Some(648), Some(8), includePedestrian = Some(true)))
      .thenReturn( Seq(    RoadAddress(Some("216"), 648, 8, Track.Combined, 6416), RoadAddress(Some("216"), 648, 8, Track.Combined, 6695)))

    when(mockRoadLinkTempDao.getByMunicipality(216)).thenReturn(Seq())

    val toCreate = ResolvingFrozenRoadLinksTest.processing(216)._1.map(_.roadAddress)

    toCreate.size should be (2)
    val createdInSininentie = toCreate.find(_.linkId == 1490369)
    createdInSininentie.nonEmpty should be (true)
    createdInSininentie.get.sideCode.get should be (SideCode.TowardsDigitizing)

    val createdInYhteisahontie = toCreate.find(_.linkId == 1490376)
    createdInYhteisahontie.nonEmpty should be (true)
    createdInYhteisahontie.get.sideCode.get should be (SideCode.TowardsDigitizing)
  }

  test("missing right and left ajorata"){
    val roadLinks = Seq(
      RoadLink(11478950,List(Point(376585.751,6992711.448,159.9759999999951), Point(376569.312,6992714.125,160.19400000000314)),16.65,
        State,99, TrafficDirection.AgainstDigitizing,UnknownLinkType, None, None,
        Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16")),
      RoadLink(11478947,List(Point(376570.341,6992722.195,160.24099999999453), Point(376534.023,6992725.668,160.875)),36.577,
        State,99, TrafficDirection.TowardsDigitizing,UnknownLinkType, None, None,
        Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16")),
      RoadLink(11478953,List(Point(376586.275,6992719.353,159.9869999999937), Point(376570.341,6992722.195,160.24099999999453)),16.1855,
        State,99, TrafficDirection.TowardsDigitizing,UnknownLinkType, None, None,
        Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16")),
      RoadLink(11478956,List(Point(376519.312,6992724.148,161.00800000000163), Point(376534.023,6992725.668,160.875)),14.790,
        State,99, TrafficDirection.AgainstDigitizing,UnknownLinkType, None, None,
        Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16")),
      RoadLink(11478942,List(Point(376569.312,6992714.125,160.19400000000314), Point(376519.312,6992724.148,161.00800000000163)),50.999,
        State,99, TrafficDirection.AgainstDigitizing,UnknownLinkType, None, None,
        Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16")),
      RoadLink(6376556,List(Point(376412.388,6992717.601,161.53100000000268), Point(376502.352,6992724.075,161.04799999999523), Point(376519.312,6992724.148,161.00800000000163)),107.2053,
        State,99, TrafficDirection.BothDirections,UnknownLinkType, None, None,
        Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16")),
      RoadLink(2439671,List(Point(376642.368,6992709.787,160.07399999999325), Point(376593.53,6992710.187,159.96400000000722), Point(376585.751,6992711.448,159.9759999999951)),56.9052,
        State,99,TrafficDirection.AgainstDigitizing,UnknownLinkType,None, None,
        Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29",  "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16")),
      RoadLink(2439673,List(Point(376586.275,6992719.353,159.9869999999937), Point(376630.419,6992726.587,159.94599999999627), Point(376639.195,6992733.214,160.125)),56.885,
        State,99,TrafficDirection.AgainstDigitizing,UnknownLinkType, None, None,
        Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16")))

    val viiteRoadAddress = Seq(
      ViiteRoadAddress(48229,16,29,Combined,4583,4690,None,None,
        6376556,0.0,107.205,TowardsDigitizing,List(),false,None,None,None),
      ViiteRoadAddress(81202,16,29,RightSide,4690,4741,None,None,
        11478942,0.0,51.0,AgainstDigitizing,List(),false,None,None,None),
      ViiteRoadAddress(81202,16,29,RightSide,4758,4815,None,None,
        2439671,0.0,56.905,AgainstDigitizing,List(),false,None,None,None))

    when(mockRoadLinkService.getRoadLinksFromVVHByMunicipality(312, false)).thenReturn(roadLinks)
    when(mockRoadAddressService.getAllByLinkIds(roadLinks.map(_.linkId))).thenReturn(viiteRoadAddress)

    when(mockVKMGeometryTransform.coordsToAddresses(Seq(Point(376585.751,6992711.448,159.9759999999951),Point(376569.312,6992714.125,160.19400000000314)),
      Some(16), Some(29), includePedestrian = Some(true))).thenThrow(new NullPointerException);
    when(mockVKMGeometryTransform.coordsToAddresses(Seq(Point(376570.341,6992722.195,160.24099999999453),Point(376534.023,6992725.668,160.875)),
      Some(16), Some(29), includePedestrian = Some(true))).thenReturn(
      Seq(RoadAddress(Some("312"), 16, 29, Track.RightSide, 4740), RoadAddress(Some("312"), 16, 29, Track.RightSide, 4704)))
    when(mockVKMGeometryTransform.coordsToAddresses(Seq(Point(376586.275,6992719.353,159.9869999999937),Point(376570.341,6992722.195,160.24099999999453)),
      Some(16), Some(29), includePedestrian = Some(true))).thenReturn(
      Seq(RoadAddress(Some("312"), 16, 29, Track.RightSide, 4757), RoadAddress(Some("312"), 16, 29, Track.RightSide, 4740)))
    when(mockVKMGeometryTransform.coordsToAddresses(Seq(Point(376519.312,6992724.148,161.00800000000163),Point(376534.023,6992725.668,160.875)),
      Some(16), Some(29), includePedestrian = Some(true))).thenReturn(
      Seq(RoadAddress(Some("312"), 16, 29, Track.Combined, 4690), RoadAddress(Some("312"), 16, 29, Track.RightSide, 4704)))
    when(mockVKMGeometryTransform.coordsToAddresses(Seq(Point(376586.275,6992719.353,159.9869999999937), Point(376639.195,6992733.214,160.125)),
      Some(16), Some(29), includePedestrian = Some(true))).thenReturn(
      Seq(RoadAddress(Some("312"), 16, 29, Track.RightSide, 4690), RoadAddress(Some("312"), 16, 29, Track.RightSide, 4808)))

    when(mockRoadLinkTempDao.getByMunicipality(312)).thenReturn(Seq())

    val toCreate = ResolvingFrozenRoadLinksTest.processing(312)._1.map(_.roadAddress)

    toCreate.size should be (4)
    toCreate.exists(x => x.linkId == 11478947 && x.sideCode.contains(SideCode.AgainstDigitizing) && x.track == Track.LeftSide) should be (true)
    toCreate.exists(x => x.linkId == 2439673 && x.sideCode.contains(SideCode.TowardsDigitizing) && x.track == Track.LeftSide) should be (true)
    toCreate.exists(x => x.linkId == 11478953 && x.sideCode.contains(SideCode.AgainstDigitizing) && x.track == Track.LeftSide) should be (true)
    toCreate.exists(x => x.linkId == 11478956 && x.sideCode.contains(SideCode.TowardsDigitizing) && x.track == Track.LeftSide) should be (true)
  }

  test("cleaning missing addresses without success") {

    val road1 = RoadLink(11478947,List(Point(376570.341,6992722.195,160.24099999999453), Point(376534.023,6992725.668,160.875)),36.577,
      State,99, TrafficDirection.TowardsDigitizing,UnknownLinkType, None, None,
      Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16"))
    val road2 = RoadLink(11478953,List(Point(376586.275,6992719.353,159.9869999999937), Point(376570.341,6992722.195,160.24099999999453)),16.1855,
      State,99, TrafficDirection.TowardsDigitizing,UnknownLinkType, None, None,
      Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16"))
    val road3 = RoadLink(11478956,List(Point(376519.312,6992724.148,161.00800000000163), Point(376534.023,6992725.668,160.875)),14.790,
      State,99, TrafficDirection.AgainstDigitizing,UnknownLinkType, None, None,
      Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16"))
    val road4 = RoadLink(11478942,List(Point(376569.312,6992714.125,160.19400000000314), Point(376519.312,6992724.148,161.00800000000163)),50.999,
      State,99, TrafficDirection.AgainstDigitizing,UnknownLinkType, None, None,
      Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16"))
    val road5 = RoadLink(6376556,List(Point(376412.388,6992717.601,161.53100000000268), Point(376502.352,6992724.075,161.04799999999523), Point(376519.312,6992724.148,161.00800000000163)),107.2053,
      State,99, TrafficDirection.BothDirections,UnknownLinkType, None, None,
      Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16"))

    val roadLinks = Seq(road1, road2, road3, road4, road5)

    val address = Seq(
      ViiteRoadAddress(48229,16,29,Combined,4583,4690,None,None,
        6376556,0.0,107.205,TowardsDigitizing,List(),false,None,None,None),
      ViiteRoadAddress(81202,16,29,RightSide,4690,4741,None,None,
        11478942,0.0,51.0,AgainstDigitizing,List(),false,None,None,None),
      ViiteRoadAddress(81200, 16, 29, LeftSide, 4740,4757,None,None,
        11478953,0.0,16.18,AgainstDigitizing,List(),false,None,None,None))

    when(mockRoadAddressService.getAllByLinkIds(any[Seq[Long]] /*Seq(11478953, 11478956, 6376556, 11478942, 11478947)*/)).thenReturn(address)

    val missingRoadLinks = Map((Point(376519.312,6992724.148,161.00800000000163), Point(376534.023,6992725.668,160.875), road3) -> Seq(road1, road4, road5),
    (Point(376570.341,6992722.195,160.24099999999453), Point(376534.023,6992725.668,160.875), road1) -> Seq(road2, road3))

    val result = ResolvingFrozenRoadLinksTest.cleanning(missingRoadLinks, Seq(), Seq())
    result.size should be (0)

  }

  test("cleaning missing addresses success") {

    val road1 = RoadLink(11478947,List(Point(376570.341,6992722.195,160.24099999999453), Point(376534.023,6992725.668,160.875)),36.577,
      State,99, TrafficDirection.TowardsDigitizing,UnknownLinkType, None, None,
      Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16"))
    val road2 = RoadLink(11478953,List(Point(376586.275,6992719.353,159.9869999999937), Point(376570.341,6992722.195,160.24099999999453)),16.1855,
      State,99, TrafficDirection.TowardsDigitizing,UnknownLinkType, None, None,
      Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16"))
    val road3 = RoadLink(11478956,List(Point(376519.312,6992724.148,161.00800000000163), Point(376534.023,6992725.668,160.875)),14.790,
      State,99, TrafficDirection.AgainstDigitizing,UnknownLinkType, None, None,
      Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16"))
    val road4 = RoadLink(11478942,List(Point(376569.312,6992714.125,160.19400000000314), Point(376519.312,6992724.148,161.00800000000163)),50.999,
      State,99, TrafficDirection.AgainstDigitizing,UnknownLinkType, None, None,
      Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16"))
    val road5 = RoadLink(6376556,List(Point(376412.388,6992717.601,161.53100000000268), Point(376502.352,6992724.075,161.04799999999523), Point(376519.312,6992724.148,161.00800000000163)),107.2053,
      State,99, TrafficDirection.BothDirections,UnknownLinkType, None, None,
      Map("ROADNAME_FI" -> "Vaasantie", "ROADPARTNUMBER" -> "29", "MUNICIPALITYCODE" -> BigInt(312), "ROADNUMBER" -> "16"))

    val roadLinks = Seq(road1, road2, road3, road4, road5)

    val address = Seq(
      ViiteRoadAddress(48229,16,29,Combined,4583,4690,None,None,
        6376556,0.0,107.205,TowardsDigitizing,List(),false,None,None,None),
      ViiteRoadAddress(81202,16,29,RightSide,4690,4741,None,None,
        11478942,0.0,51.0,AgainstDigitizing,List(),false,None,None,None),
      ViiteRoadAddress(81200, 16, 29, LeftSide, 4740,4757,None,None,
        11478947,0.0,16.18,AgainstDigitizing,List(),false,None,None,None))


    val mappedAddresses = address.flatMap { address =>
      Seq(road1, road4, road5).find(_.linkId == address.linkId).map { roadLink =>
        val (first, last) = GeometryUtils.geometryEndpoints(roadLink.geometry)
        RoadAddressTEMPwithPoint(first, last, RoadAddressTEMP(address.linkId, address.roadNumber,
          address.roadPartNumber, address.track, address.startAddrMValue, address.endAddrMValue,
          address.startMValue, address.endMValue, address.geom, Some(address.sideCode), Some(roadLink.municipalityCode)))
      }
    }

    val result = ResolvingFrozenRoadLinksTest.cleanning(
      Map((Point(376519.312,6992724.148,161.00800000000163), Point(376534.023,6992725.668,160.875), road3) -> Seq(road1, road4, road5)), mappedAddresses, Seq())

    result.size should be (1)
    result.exists(x => x.track == LeftSide && x.sideCode.contains(SideCode.TowardsDigitizing))

  }
}

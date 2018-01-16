package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode}
import fi.liikennevirasto.digiroad2.client.vvh.ChangeInfo
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao.Discontinuity.EndOfRoad
import fi.liikennevirasto.viite.{LinkRoadAddressHistory, NewRoadAddress, RoadType}
import fi.liikennevirasto.viite.dao.{Discontinuity, RoadAddress}
import fi.liikennevirasto.viite.util.prettyPrint
import org.joda.time.DateTime
import org.scalatest.{FunSuite, Matchers}


class RoadAddressChangeInfoMapperSpec extends FunSuite with Matchers {
  test("resolve simple case") {
    val roadAddress = RoadAddress(1, 1, 1, RoadType.Unknown, Track.RightSide, Discontinuity.Continuous, 0, 1000, Some(DateTime.now), None,
      None, 0L, 123L, 0.0, 1000.234, SideCode.AgainstDigitizing, 86400L, (None, None), false, Seq(Point(0.0, 0.0), Point(1000.234, 0.0)), LinkGeomSource.NormalLinkInterface, 8)
    val roadAddress2 = RoadAddress(1, 1, 1, RoadType.Unknown, Track.RightSide, Discontinuity.Continuous, 1000, 1400, Some(DateTime.now), None,
      None, 0L, 124L, 0.0, 399.648, SideCode.AgainstDigitizing, 86400L, (None, None), false, Seq(Point(1000.234, 0.0), Point(1000.234, 399.648)), LinkGeomSource.NormalLinkInterface, 8)
    val map = Seq(roadAddress, roadAddress2).groupBy(_.linkId).mapValues(s => LinkRoadAddressHistory(s, Seq()))
    val changes = Seq(
      ChangeInfo(Some(123), Some(124), 123L, 2, Some(0.0), Some(1000.234), Some(399.648), Some(1399.882), 96400L),
      ChangeInfo(Some(124), Some(124), 123L, 1, Some(0.0), Some(399.648), Some(0.0), Some(399.648), 96400L))
    val results = RoadAddressChangeInfoMapper.resolveChangesToMap(map, Seq(), changes).mapValues(_.allSegments)
    results.get(123).isEmpty should be (true)
    results.get(124).isEmpty should be (false)
    results(124).size should be (2)
    results.values.flatten.exists(_.startAddrMValue == 0) should be (true)
    results.values.flatten.exists(_.startAddrMValue == 1000) should be (true)
    results.values.flatten.exists(_.endAddrMValue == 1000) should be (true)
    results.values.flatten.exists(_.endAddrMValue == 1400) should be (true)
    results.values.flatten.forall(_.adjustedTimestamp == 96400L) should be (true)
  }

  test("transfer 1 to 2, modify 2, then transfer 2 to 3") {
    val roadLinkId1 = 123L
    val roadLinkId2 = 456L
    val roadLinkId3 = 789L

    val roadAdjustedTimestamp = 0L
    val changesVVHTimestamp = 96400L

    val roadAddress1 = RoadAddress(1, 1, 1, RoadType.Unknown, Track.RightSide, Discontinuity.Continuous, 0, 1000, Some(DateTime.now), None,
      None, 0L, roadLinkId1, 0.0, 1000.234, SideCode.AgainstDigitizing, roadAdjustedTimestamp, (None, None), false, Seq(Point(0.0, 0.0), Point(1000.234, 0.0)), LinkGeomSource.NormalLinkInterface, 8)
    val roadAddress2 = RoadAddress(1, 1, 1, RoadType.Unknown, Track.RightSide, Discontinuity.Continuous, 1000, 1400, Some(DateTime.now), None,
      None, 0L, roadLinkId2, 0.0, 399.648, SideCode.AgainstDigitizing, roadAdjustedTimestamp, (None, None), false, Seq(Point(1000.234, 0.0), Point(1000.234, 399.648)), LinkGeomSource.NormalLinkInterface, 8)
    val map = Seq(roadAddress1, roadAddress2).groupBy(_.linkId).mapValues(s => LinkRoadAddressHistory(s, Seq()))
    val changes = Seq(
      ChangeInfo(Some(roadLinkId1), Some(roadLinkId2), 123L, 2, Some(0.0), Some(1000.234), Some(399.648), Some(1399.882), changesVVHTimestamp),
      ChangeInfo(Some(roadLinkId2), Some(roadLinkId2), 123L, 1, Some(0.0), Some(399.648), Some(0.0), Some(399.648), changesVVHTimestamp),
      ChangeInfo(Some(roadLinkId2), Some(roadLinkId3), 123L, 2, Some(0.0), Some(1399.882), Some(1399.882), Some(0.0), changesVVHTimestamp+1L)
    )
    val results = RoadAddressChangeInfoMapper.resolveChangesToMap(map, Seq(), changes).mapValues(_.allSegments)
    results.get(roadLinkId1).isEmpty should be (true)
    results.get(roadLinkId2).isEmpty should be (true)
    results.get(roadLinkId3).isEmpty should be (false)
    results(roadLinkId3).size should be (2)
    results(roadLinkId3).count(_.id == -1000) should be (2)
    results(roadLinkId3).count(rl => rl.id == -1000 && (rl.startAddrMValue == 0 || rl.startAddrMValue == 1000) && (rl.endAddrMValue == 1000 || rl.endAddrMValue == 1400)) should be (2)
    results.values.flatten.exists(_.startAddrMValue == 0) should be (true)
    results.values.flatten.exists(_.startAddrMValue == 1000) should be (true)
    results.values.flatten.exists(_.endAddrMValue == 1000) should be (true)
    results.values.flatten.exists(_.endAddrMValue == 1400) should be (true)
  }

  test("no changes should apply") {
    val roadLinkId1 = 123L
    val roadLinkId2 = 456L
    val roadLinkId3 = 789L

    val roadAdjustedTimestamp = 964000L
    val changesVVHTimestamp = 96400L

    val roadAddress1 = RoadAddress(1, 1, 1, RoadType.Unknown, Track.RightSide, Discontinuity.Continuous, 0, 1000, Some(DateTime.now), None,
      None, 0L, roadLinkId1, 0.0, 1000.234, SideCode.AgainstDigitizing, roadAdjustedTimestamp, (None, None), false,
      Seq(Point(0.0, 0.0), Point(1000.234, 0.0)), LinkGeomSource.NormalLinkInterface, 8)
    val roadAddress2 = RoadAddress(1, 1, 1, RoadType.Unknown, Track.RightSide, Discontinuity.Continuous, 1000, 1400, Some(DateTime.now), None,
      None, 0L, roadLinkId2, 0.0, 399.648, SideCode.AgainstDigitizing, roadAdjustedTimestamp, (None, None), false,
      Seq(Point(1000.234, 0.0), Point(1000.234, 399.648)), LinkGeomSource.NormalLinkInterface, 8)
    val roadAddress3 = RoadAddress(367,75,2, RoadType.Unknown, Track.Combined,Discontinuity.Continuous,3532,3598,None,None,
      Some("tr"),70000389,roadLinkId3,0.0,65.259,SideCode.TowardsDigitizing,roadAdjustedTimestamp,(None,None),true,
      List(Point(538889.668,6999800.979,0.0), Point(538912.266,6999862.199,0.0)), LinkGeomSource.NormalLinkInterface, 8)
    val map = Seq(roadAddress1, roadAddress2, roadAddress3).groupBy(_.linkId).mapValues(s => LinkRoadAddressHistory(s, Seq()))
    val changes = Seq(
      ChangeInfo(Some(roadLinkId1), Some(roadLinkId2), 123L, 2, Some(0.0), Some(1000.234), Some(399.648), Some(1399.882), changesVVHTimestamp),
      ChangeInfo(Some(roadLinkId2), Some(roadLinkId2), 123L, 1, Some(0.0), Some(399.648), Some(0.0), Some(399.648), changesVVHTimestamp),
      ChangeInfo(Some(roadLinkId2), Some(roadLinkId3), 123L, 2, Some(0.0), Some(6666), Some(200), Some(590), changesVVHTimestamp)
    )
    val results = RoadAddressChangeInfoMapper.resolveChangesToMap(map, Seq(), changes).mapValues(_.allSegments)
    results.get(roadLinkId1).isEmpty should be (false)
    results.get(roadLinkId2).isEmpty should be (false)
    results.get(roadLinkId3).isEmpty should be (false)
    results(roadLinkId1).size should be (1)
    results(roadLinkId1).count(_.id == -1000) should be (0)
    results(roadLinkId1).head.eq(roadAddress1) should be (true)
    results(roadLinkId2).size should be (1)
    results(roadLinkId2).count(_.id == -1000) should be (0)
    results(roadLinkId2).head.eq(roadAddress2) should be (true)
    results(roadLinkId3).size should be (1)
    results(roadLinkId3).count(_.id == -1000) should be (0)
    results(roadLinkId3).head.eq(roadAddress3) should be (true)
  }

  test("modify 1, transfer 2 To 1, transfer 3 to 1") {
    val roadLinkId1 = 123L
    val roadLinkId2 = 456L
    val roadLinkId3 = 789L

    val roadAdjustedTimestamp = 0L
    val changesVVHTimestamp = 96400L

    val roadAddress1 = RoadAddress(2, 1, 1, RoadType.Unknown, Track.RightSide, Discontinuity.Continuous, 400, 1400, Some(DateTime.now), None,
      None, 0L, roadLinkId1, 0.0, 1000.234, SideCode.AgainstDigitizing, roadAdjustedTimestamp, (None, None), false, Seq(Point(0.0, 0.0), Point(1000.234, 0.0)), LinkGeomSource.NormalLinkInterface, 8)
    val roadAddress2 = RoadAddress(1, 1, 1, RoadType.Unknown, Track.RightSide, Discontinuity.Continuous, 0, 400, Some(DateTime.now), None,
      None, 0L, roadLinkId2, 0.0, 399.648, SideCode.AgainstDigitizing, roadAdjustedTimestamp, (None, None), false, Seq(Point(1000.234, 0.0), Point(1000.234, 399.648)), LinkGeomSource.NormalLinkInterface, 8)
    val roadAddress3 = RoadAddress(367,1,1, RoadType.Unknown, Track.RightSide,Discontinuity.Continuous,1400,1465,None,None,
      Some("tr"),70000389,roadLinkId3,0.0,65.259,SideCode.TowardsDigitizing,roadAdjustedTimestamp,(None,None), false,List(Point(538889.668,6999800.979,0.0), Point(538912.266,6999862.199,0.0)), LinkGeomSource.NormalLinkInterface, 8)
    val map = Seq(roadAddress1, roadAddress2, roadAddress3).groupBy(_.linkId).mapValues(s => LinkRoadAddressHistory(s, Seq()))
    val changes = Seq(
      //Modifications
      ChangeInfo(Some(roadLinkId1), Some(roadLinkId1), 123L, 1, Some(0.0), Some(1000.234), Some(399.648), Some(1399.882), changesVVHTimestamp),
      //Transfers
      ChangeInfo(Some(roadLinkId2), Some(roadLinkId1), 123L, 2, Some(0.0), Some(399.648), Some(0.0), Some(399.648), changesVVHTimestamp),
      ChangeInfo(Some(roadLinkId3), Some(roadLinkId1), 123L, 2, Some(0.0), Some(65.259), Some(1465.0), Some(1399.882), changesVVHTimestamp)
    )
    val results = RoadAddressChangeInfoMapper.resolveChangesToMap(map, Seq(), changes).mapValues(_.allSegments.sortBy(_.startAddrMValue))
    results.get(roadLinkId1).isEmpty should be (false)
    results.get(roadLinkId2).isEmpty should be (true)
    results.get(roadLinkId3).isEmpty should be (true)
    results(roadLinkId1).size should be (3)
    results(roadLinkId1).forall(_.id == NewRoadAddress) should be (true)
    val addr1 = results(roadLinkId1)(0)
    addr1.startMValue should be (0)
    addr1.endMValue should be (399.648)
    addr1.startAddrMValue should be (0)
    addr1.endAddrMValue should be (400)
    addr1.adjustedTimestamp should be (changesVVHTimestamp)
    val addr2 = results(roadLinkId1)(1)
    addr2.startMValue should be (399.648)
    addr2.endMValue should be (1399.882)
    addr2.startAddrMValue should be (400)
    addr2.endAddrMValue should be (1400)
    addr2.adjustedTimestamp should be (changesVVHTimestamp)
    val addr3 = results(roadLinkId1)(2)
    addr3.startMValue should be (1399.882)
    addr3.endMValue should be (1465.0)
    addr3.startAddrMValue should be (1400)
    addr3.endAddrMValue should be (1465)
    addr3.adjustedTimestamp should be (changesVVHTimestamp)
  }

  test("split a road address link into three") {
    val roadLinkId1 = 123L
    val roadLinkId2 = 456L
    val roadLinkId3 = 789L

    val roadAdjustedTimestamp = 0L
    val changesVVHTimestamp = 96400L

    val roadAddress1 = RoadAddress(1, 1, 1, RoadType.Unknown, Track.RightSide, Discontinuity.EndOfRoad, 400, 1400, Some(DateTime.now), None,
      None, 0L, roadLinkId1, 0.0, 960.434, SideCode.AgainstDigitizing, roadAdjustedTimestamp, (None, None), false, Seq(Point(0.0, 0.0), Point(960.434, 0.0)), LinkGeomSource.NormalLinkInterface, 8)
    val map = Seq(roadAddress1).groupBy(_.linkId).mapValues(s => LinkRoadAddressHistory(s, Seq()))
    val changes = Seq(
      //Remain
      ChangeInfo(Some(roadLinkId1), Some(roadLinkId1), 123L, 5, Some(399.648), Some(847.331), Some(0.0), Some(447.682), changesVVHTimestamp),
      //Move
      ChangeInfo(Some(roadLinkId1), Some(roadLinkId2), 456L, 6, Some(0.0), Some(399.648), Some(0.0), Some(399.648), changesVVHTimestamp),
      ChangeInfo(Some(roadLinkId1), Some(roadLinkId3), 789L, 6, Some(847.331), Some(960.434), Some(113.103), Some(0.0), changesVVHTimestamp)
    )
    val results = RoadAddressChangeInfoMapper.resolveChangesToMap(map, Seq(), changes).mapValues(_.allSegments.sortBy(_.startAddrMValue))
    results.get(roadLinkId1).isEmpty should be (false)
    results.get(roadLinkId2).isEmpty should be (false)
    results.get(roadLinkId3).isEmpty should be (false)
    val addr1 = results(roadLinkId1)(0)
    addr1.startMValue should be (0.0)
    addr1.endMValue should be (447.682)
    addr1.startAddrMValue should be (518)
    addr1.endAddrMValue should be (984)
    addr1.adjustedTimestamp should be (changesVVHTimestamp)
    addr1.sideCode should be (AgainstDigitizing)
    val addr2 = results(roadLinkId2)(0)
    addr2.startMValue should be (0.0)
    addr2.endMValue should be (399.648)
    addr2.startAddrMValue should be (984)
    addr2.endAddrMValue should be (1400)
    addr2.adjustedTimestamp should be (changesVVHTimestamp)
    addr2.sideCode should be (AgainstDigitizing)
    val addr3 = results(roadLinkId3)(0)
    addr3.startMValue should be (0.0)
    addr3.endMValue should be (113.103)
    addr3.startAddrMValue should be (400)
    addr3.endAddrMValue should be (518)
    addr3.adjustedTimestamp should be (changesVVHTimestamp)
    addr3.sideCode should be (TowardsDigitizing)
    addr3.discontinuity should be (EndOfRoad)
  }

  test("Lengthened road links") {
    val roadLinkId1 = 123L
    val roadLinkId2 = 456L
    val roadLinkId3 = 789L

    val roadAdjustedTimestamp = 0L
    val changesVVHTimestamp = 96400L

    val roadAddress1 = RoadAddress(1, 1, 1, RoadType.Unknown, Track.RightSide, Discontinuity.Continuous, 400, 1400, Some(DateTime.now), None,
      None, 0L, roadLinkId1, 0.0, 960.434, SideCode.AgainstDigitizing, roadAdjustedTimestamp, (None, None), false, Seq(Point(0.0, 0.0), Point(960.434, 0.0)), LinkGeomSource.NormalLinkInterface, 8)
    val roadAddress2 = RoadAddress(1, 1, 1, RoadType.Unknown, Track.RightSide, Discontinuity.Continuous, 1400, 1600, Some(DateTime.now), None,
      None, 0L, roadLinkId2, 0.0, 201.333, SideCode.TowardsDigitizing, roadAdjustedTimestamp, (None, None), false, Seq(Point(0.0, 0.0), Point(0.0, 201.333)), LinkGeomSource.NormalLinkInterface, 8)
    val roadAddress3 = RoadAddress(1, 1, 1, RoadType.Unknown, Track.RightSide, Discontinuity.Continuous, 1600, 1610, Some(DateTime.now), None,
      None, 0L, roadLinkId3, 0.0, 10.0, SideCode.TowardsDigitizing, roadAdjustedTimestamp, (None, None), false, Seq(Point(0.0, 201.333), Point(0.0, 211.333)), LinkGeomSource.NormalLinkInterface, 8)
    val map = Seq(roadAddress1, roadAddress2, roadAddress3).groupBy(_.linkId).mapValues(s => LinkRoadAddressHistory(s, Seq()))
    val changes = Seq(
      //Old parts
      ChangeInfo(Some(roadLinkId1), Some(roadLinkId1), 123L, 3, Some(0.0), Some(960.434), Some(0.535), Some(960.969), changesVVHTimestamp),
      ChangeInfo(Some(roadLinkId2), Some(roadLinkId2), 456L, 3, Some(0.0), Some(201.333), Some(201.333), Some(0.0), changesVVHTimestamp),
      ChangeInfo(Some(roadLinkId3), Some(roadLinkId3), 757L, 3, Some(0.0), Some(10.0), Some(0.0), Some(10.0), changesVVHTimestamp),
      //New parts
      ChangeInfo(None, Some(roadLinkId1), 123L, 4, None, None, Some(0.0), Some(0.535), changesVVHTimestamp),
      ChangeInfo(None, Some(roadLinkId2), 456L, 4, None, None, Some(201.333), Some(201.986), changesVVHTimestamp),
      ChangeInfo(None, Some(roadLinkId3), 757L, 4, None, None, Some(10.0), Some(11.001), changesVVHTimestamp)
    )
    val results = RoadAddressChangeInfoMapper.resolveChangesToMap(map, Seq(), changes).mapValues(_.allSegments.sortBy(_.startAddrMValue))
    results.get(roadLinkId1).isEmpty should be (false)
    results.get(roadLinkId2).isEmpty should be (false)
    results.get(roadLinkId3).isEmpty should be (false)
    val addr1 = results(roadLinkId1)(0)
    addr1.startMValue should be (0.0)
    addr1.endMValue should be (960.969)
    addr1.startAddrMValue should be (400)
    addr1.endAddrMValue should be (1400)
    addr1.floating should be (false)
    addr1.adjustedTimestamp should be (changesVVHTimestamp)
    addr1.sideCode should be (AgainstDigitizing)
    val addr2 = results(roadLinkId2)(0)
    addr2.startMValue should be (0.0)
    addr2.endMValue should be (201.986)
    addr2.startAddrMValue should be (1400)
    addr2.endAddrMValue should be (1600)
    addr2.floating should be (false)
    addr2.adjustedTimestamp should be (changesVVHTimestamp)
    addr2.sideCode should be (AgainstDigitizing)
    val addr3 = results(roadLinkId3)(0)
    addr3.startMValue should be (0.0)
    addr3.endMValue should be (10.0)
    addr3.startAddrMValue should be (1600)
    addr3.endAddrMValue should be (1610)
    addr3.floating should be (true)
    addr3.adjustedTimestamp should be (roadAdjustedTimestamp)
    addr3.sideCode should be (TowardsDigitizing)
  }

  test("Shortened road links") {
    val roadLinkId1 = 123L
    val roadLinkId2 = 456L
    val roadLinkId3 = 789L

    val roadAdjustedTimestamp = 0L
    val changesVVHTimestamp = 96400L

    val roadAddress1 = RoadAddress(1, 1, 1, RoadType.Unknown, Track.Combined, Discontinuity.Continuous, 400, 1400, Some(DateTime.now), None,
      None, 0L, roadLinkId1, 0.0, 960.969, SideCode.AgainstDigitizing, roadAdjustedTimestamp, (None, None), false, Seq(Point(0.0, 0.0), Point(960.969, 0.0)), LinkGeomSource.NormalLinkInterface, 8)
    val roadAddress2 = RoadAddress(1, 1, 1, RoadType.Unknown, Track.Combined, Discontinuity.Continuous, 1400, 1600, Some(DateTime.now), None,
      None, 0L, roadLinkId2, 0.0, 201.986, SideCode.TowardsDigitizing, roadAdjustedTimestamp, (None, None), false, Seq(Point(0.0, 0.0), Point(0.0, 201.986)), LinkGeomSource.NormalLinkInterface, 8)
    val roadAddress3 = RoadAddress(1, 1, 1, RoadType.Unknown, Track.Combined, Discontinuity.Continuous, 1600, 1610, Some(DateTime.now), None,
      None, 0L, roadLinkId3, 0.0, 11.001, SideCode.TowardsDigitizing, roadAdjustedTimestamp, (None, None), false, Seq(Point(0.0, 201.333), Point(0.0, 212.334)), LinkGeomSource.NormalLinkInterface, 8)
    val map = Seq(roadAddress1, roadAddress2, roadAddress3).groupBy(_.linkId).mapValues(s => LinkRoadAddressHistory(s, Seq()))
    val changes = Seq(
      //common parts
      ChangeInfo(Some(roadLinkId1), Some(roadLinkId1), 123L, 7, Some(0.535), Some(960.969), Some(0.0), Some(960.434), changesVVHTimestamp),
      ChangeInfo(Some(roadLinkId2), Some(roadLinkId2), 456L, 7, Some(201.333), Some(0.0), Some(0.0), Some(201.333), changesVVHTimestamp),
      ChangeInfo(Some(roadLinkId3), Some(roadLinkId3), 757L, 7, Some(0.0), Some(10.0), Some(0.0), Some(10.0), changesVVHTimestamp),
      //removed parts
      ChangeInfo(Some(roadLinkId1), None, 123L, 8, Some(0.0), Some(0.535), None, None, changesVVHTimestamp),
      ChangeInfo(Some(roadLinkId2), None, 456L, 8, Some(201.333), Some(201.986), None, None, changesVVHTimestamp),
      ChangeInfo(Some(roadLinkId3), None, 757L, 8, Some(10.0), Some(11.001), None, None, changesVVHTimestamp)
    )
    val results = RoadAddressChangeInfoMapper.resolveChangesToMap(map, Seq(), changes).mapValues(_.allSegments.sortBy(_.startAddrMValue))
    results.get(roadLinkId1).isEmpty should be (false)
    results.get(roadLinkId2).isEmpty should be (false)
    results.get(roadLinkId3).isEmpty should be (false)
    val addr1 = results(roadLinkId1)(0)
    addr1.startMValue should be (0.0)
    addr1.endMValue should be (960.434)
    addr1.startAddrMValue should be (400)
    addr1.endAddrMValue should be (1400)
    addr1.floating should be (false)
    addr1.adjustedTimestamp should be (changesVVHTimestamp)
    addr1.sideCode should be (AgainstDigitizing)
    val addr2 = results(roadLinkId2)(0)
    addr2.startMValue should be (0.0)
    addr2.endMValue should be (201.333)
    addr2.startAddrMValue should be (1400)
    addr2.endAddrMValue should be (1600)
    addr2.floating should be (false)
    addr2.adjustedTimestamp should be (changesVVHTimestamp)
    addr2.sideCode should be (AgainstDigitizing)
    val addr3 = results(roadLinkId3)(0)
    addr3.id shouldNot be (NewRoadAddress)
    addr3.startMValue should be (0.0)
    addr3.endMValue should be (11.001)
    addr3.startAddrMValue should be (1600)
    addr3.endAddrMValue should be (1610)
    addr3.floating should be (true)
    addr3.adjustedTimestamp should be (roadAdjustedTimestamp)
    addr3.sideCode should be (TowardsDigitizing)
  }

  test("Removed link") {
    val roadLinkId1 = 123L

    val roadAdjustedTimestamp = 0L
    val changesVVHTimestamp = 96400L

    val roadAddress1 = RoadAddress(1, 1, 1, RoadType.Unknown, Track.Combined, Discontinuity.Continuous, 400, 1400, Some(DateTime.now), None,
      None, 0L, roadLinkId1, 0.0, 960.434, SideCode.AgainstDigitizing, roadAdjustedTimestamp, (None, None), false, Seq(Point(0.0, 0.0), Point(960.434, 0.0)), LinkGeomSource.NormalLinkInterface, 8)
    val map = Seq(roadAddress1).groupBy(_.linkId).mapValues(s => LinkRoadAddressHistory(s, Seq()))
    val changes = Seq(ChangeInfo(Some(roadLinkId1), Some(roadLinkId1), 123L, 11, Some(0.0), Some(960.434), None, None, changesVVHTimestamp))
    val results = RoadAddressChangeInfoMapper.resolveChangesToMap(map, Seq(), changes).mapValues(_.allSegments.sortBy(_.startAddrMValue))
    results.get(roadLinkId1).isEmpty should be (false)
    val addr1 = results(roadLinkId1)(0)
    addr1.startMValue should be (0.0)
    addr1.endMValue should be (960.434)
    addr1.startAddrMValue should be (400)
    addr1.endAddrMValue should be (1400)
    addr1.floating should be (true)
    addr1.adjustedTimestamp should be (roadAdjustedTimestamp)
    addr1.sideCode should be (AgainstDigitizing)
  }

  test("Multiple operations applied in order") {
    val roadLinkId1 = 123L
    val roadLinkId2 = 456L

    val roadAdjustedTimestamp = 0L
    val changesVVHTimestamp1 = 96400L
    val changesVVHTimestamp2 = 3*96400L

    val roadAddress1 = RoadAddress(1, 1, 1, RoadType.Unknown, Track.Combined, Discontinuity.Continuous, 400, 1400, Some(DateTime.now), None,
      None, 0L, roadLinkId1, 0.0, 960.434, AgainstDigitizing, roadAdjustedTimestamp, (None, None), false, Seq(Point(0.0, 0.0), Point(960.434, 0.0)), LinkGeomSource.NormalLinkInterface, 8)
    val map = Seq(roadAddress1).groupBy(_.linkId).mapValues(s => LinkRoadAddressHistory(s, Seq()))
    val changes = Seq(
      // Extend and turn digitization direction around
      ChangeInfo(Some(roadLinkId1), Some(roadLinkId1), 123L, 3, Some(0.0), Some(960.434), Some(960.969), Some(0.535), changesVVHTimestamp1),
      ChangeInfo(Some(roadLinkId1), Some(roadLinkId1), 123L, 4, None, None, Some(0.0), Some(0.535), changesVVHTimestamp1),
      //Remain
      ChangeInfo(Some(roadLinkId1), Some(roadLinkId1), 123L, 5, Some(399.648), Some(960.969), Some(0.0), Some(960.969-399.648), changesVVHTimestamp2),
      //Move and turn around
      ChangeInfo(Some(roadLinkId1), Some(roadLinkId2), 456L, 6, Some(0.0), Some(399.648), Some(399.648), Some(0.0), changesVVHTimestamp2)
    )
    val results = RoadAddressChangeInfoMapper.resolveChangesToMap(map, Seq(), changes).mapValues(_.allSegments.sortBy(_.startAddrMValue))
    results.get(roadLinkId1).isEmpty should be (false)
    results.get(roadLinkId2).isEmpty should be (false)
    val addr1 = results(roadLinkId1)(0)
    addr1.startMValue should be (0.0)
    addr1.endMValue should be (960.969-399.648 +- 0.001)
    addr1.startAddrMValue should be (816)
    addr1.endAddrMValue should be (1400)
    addr1.floating should be (false)
    addr1.adjustedTimestamp should be (changesVVHTimestamp2)
    addr1.sideCode should be (TowardsDigitizing)
    val addr2 = results(roadLinkId2)(0)
    addr2.startMValue should be (0.0)
    addr2.endMValue should be (399.648)
    addr2.startAddrMValue should be (400)
    addr2.endAddrMValue should be (addr1.startAddrMValue)
    addr2.floating should be (false)
    addr2.adjustedTimestamp should be (changesVVHTimestamp2)
    addr2.sideCode should be (AgainstDigitizing)
  }
}

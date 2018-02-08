package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode}
import fi.liikennevirasto.digiroad2.client.vvh.ChangeInfo
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao.Discontinuity.EndOfRoad
import fi.liikennevirasto.viite.dao.TerminationCode.NoTermination
import fi.liikennevirasto.viite.{LinkRoadAddressHistory, NewRoadAddress, RoadType}
import fi.liikennevirasto.viite.dao.{Discontinuity, RoadAddress}
import org.joda.time.DateTime
import org.scalatest.{FunSuite, Matchers}


class RoadAddressChangeInfoMapperSpec extends FunSuite with Matchers {

  val roadAddr = RoadAddress(1, 1, 1, RoadType.Unknown, Track.RightSide, Discontinuity.Continuous, 0, 1000, Some(DateTime.now), None,
    None, 0L, 0L, 0.0, 1000.0, SideCode.AgainstDigitizing, 0, (None, None), false, Seq(Point(0.0, 0.0), Point(1000.234, 0.0)),
    LinkGeomSource.NormalLinkInterface, 8, NoTermination, 123456)

  test("resolve simple case") {

    val roadAddress1 = roadAddr.copy(startAddrMValue = 0, endAddrMValue = 1000, linkId = 123L, endMValue = 1000.234, geometry = Seq(Point(0.0, 0.0), Point(1000.234, 0.0)))
    val roadAddress2 = roadAddr.copy(startAddrMValue = 1000, endAddrMValue = 1400, linkId = 124L, endMValue = 399.648, geometry = Seq(Point(1000.234, 0.0), Point(1000.234, 399.648)))
    val map = Seq(roadAddress1, roadAddress2).groupBy(_.linkId).mapValues(s => LinkRoadAddressHistory(s, Seq()))
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
    results.values.flatten.map(_.commonHistoryId).toSet.size should be (1)
    results.values.flatten.map(_.commonHistoryId).toSet.head should be (roadAddress.commonHistoryId)
  }

  test("transfer 1 to 2, modify 2, then transfer 2 to 3") {
    val roadLinkId1 = 123L
    val roadLinkId2 = 456L
    val roadLinkId3 = 789L

    val roadAdjustedTimestamp = 0L
    val changesVVHTimestamp = 96400L

    val roadAddress1 = roadAddr.copy(startAddrMValue = 0, endAddrMValue = 1000, linkId = roadLinkId1, endMValue = 1000.234, adjustedTimestamp = roadAdjustedTimestamp, geometry = Seq(Point(0.0, 0.0), Point(1000.234, 0.0)))
    val roadAddress2 = roadAddr.copy(startAddrMValue = 1000, endAddrMValue = 1400, linkId = roadLinkId2, endMValue = 399.648, adjustedTimestamp = roadAdjustedTimestamp, geometry = Seq(Point(1000.234, 0.0), Point(1000.234, 399.648)))
    val map = Seq(roadAddress1, roadAddress2).groupBy(_.linkId).mapValues(s => LinkRoadAddressHistory(s, Seq()))
    val changes = Seq(
      ChangeInfo(Some(roadLinkId1), Some(roadLinkId2), 123L, 2, Some(0.0), Some(1000.234), Some(399.648), Some(1399.882), changesVVHTimestamp),
      ChangeInfo(Some(roadLinkId2), Some(roadLinkId2), 123L, 1, Some(0.0), Some(399.648), Some(0.0), Some(399.648), changesVVHTimestamp),
      ChangeInfo(Some(roadLinkId2), Some(roadLinkId3), 123L, 2, Some(0.0), Some(1399.882), Some(1399.882), Some(0.0), changesVVHTimestamp + 1L)
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
    results.values.flatten.map(_.commonHistoryId).toSet.size should be (1)
    results.values.flatten.map(_.commonHistoryId).toSet.head should be (roadAddress1.commonHistoryId)
  }

  test("no changes should apply") {
    val roadLinkId1 = 123L
    val roadLinkId2 = 456L
    val roadLinkId3 = 789L

    val roadAdjustedTimestamp = 964000L
    val changesVVHTimestamp = 96400L

    val roadAddress1 = roadAddr.copy(startAddrMValue = 0, endAddrMValue = 1000, linkId = roadLinkId1, endMValue = 1000.234,
      adjustedTimestamp = roadAdjustedTimestamp, geometry = Seq(Point(0.0, 0.0), Point(1000.234, 0.0)))
    val roadAddress2 = roadAddr.copy(startAddrMValue = 1000, endAddrMValue = 1400, linkId = roadLinkId2, endMValue = 399.648,
      adjustedTimestamp = roadAdjustedTimestamp, geometry = Seq(Point(1000.234, 0.0), Point(1000.234, 399.648)))
    val roadAddress3 = roadAddr.copy(roadNumber = 75, roadPartNumber = 2, track = Track.Combined, startAddrMValue = 3532, endAddrMValue = 3598,
      modifiedBy = Some("tr"), lrmPositionId = 70000389, linkId = roadLinkId3, endMValue = 65.259, adjustedTimestamp = roadAdjustedTimestamp,
      floating = true, geometry = List(Point(538889.668, 6999800.979, 0.0), Point(538912.266, 6999862.199, 0.0)))
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
    results.values.flatten.map(_.commonHistoryId).toSet.size should be (1)
    results.values.flatten.map(_.commonHistoryId).toSet.head should be (roadAddress1.commonHistoryId)
  }

  test("modify 1, transfer 2 To 1, transfer 3 to 1") {
    val roadLinkId1 = 123L
    val roadLinkId2 = 456L
    val roadLinkId3 = 789L

    val changesVVHTimestamp = 96400L

    val roadAddress1 = roadAddr.copy(id = 2, startAddrMValue = 400, endAddrMValue = 1400, linkId = roadLinkId1, endMValue = 1000.234, geometry = Seq(Point(0.0, 0.0), Point(1000.234, 0.0)))
    val roadAddress2 = roadAddr.copy(id = 1, startAddrMValue = 0, endAddrMValue = 400, linkId = roadLinkId2, endMValue = 399.648, geometry = Seq(Point(1000.234, 0.0), Point(1000.234, 399.648)))
    val roadAddress3 = roadAddr.copy(id = 367, startAddrMValue = 1400, endAddrMValue = 1465, modifiedBy = Some("tr"), lrmPositionId = 70000389,
      linkId = roadLinkId3, endMValue = 65.259, sideCode = SideCode.TowardsDigitizing, geometry = List(Point(538889.668, 6999800.979, 0.0), Point(538912.266, 6999862.199, 0.0)))

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
    results.values.flatten.map(_.commonHistoryId).toSet.size should be (1)
    results.values.flatten.map(_.commonHistoryId).toSet.head should be (roadAddress1.commonHistoryId)
  }

  test("split a road address link into three") {
    val roadLinkId1 = 123L
    val roadLinkId2 = 456L
    val roadLinkId3 = 789L

    val changesVVHTimestamp = 96400L

    val roadAddress1 = roadAddr.copy(discontinuity = Discontinuity.EndOfRoad, startAddrMValue = 400, endAddrMValue = 1400, linkId = roadLinkId1, endMValue = 960.434, geometry = Seq(Point(0.0, 0.0), Point(960.434, 0.0)))
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
    results.values.flatten.map(_.commonHistoryId).toSet.size should be (1)
    results.values.flatten.map(_.commonHistoryId).toSet.head should be (roadAddress1.commonHistoryId)
  }

  test("Lengthened road links") {
    val roadLinkId1 = 123L
    val roadLinkId2 = 456L
    val roadLinkId3 = 789L

    val roadAdjustedTimestamp = 0L
    val changesVVHTimestamp = 96400L

    val roadAddress1 = roadAddr.copy(startAddrMValue = 400, endAddrMValue = 1400, linkId = roadLinkId1, endMValue = 960.434, geometry = Seq(Point(0.0, 0.0), Point(960.434, 0.0)), adjustedTimestamp = roadAdjustedTimestamp)
    val roadAddress2 = roadAddr.copy(startAddrMValue = 1400, endAddrMValue = 1600, linkId = roadLinkId2, endMValue = 201.333, sideCode = SideCode.TowardsDigitizing, geometry = Seq(Point(0.0, 0.0), Point(0.0, 201.333)), adjustedTimestamp = roadAdjustedTimestamp)
    val roadAddress3 = roadAddr.copy(startAddrMValue = 1600, endAddrMValue =  1610, linkId = roadLinkId3, endMValue = 10.0, sideCode = SideCode.TowardsDigitizing, geometry = Seq(Point(0.0, 201.333), Point(0.0, 211.333)), adjustedTimestamp = roadAdjustedTimestamp)
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
    results.values.flatten.map(_.commonHistoryId).toSet.size should be (1)
    results.values.flatten.map(_.commonHistoryId).toSet.head should be (roadAddress1.commonHistoryId)
  }

  test("Shortened road links") {
    val roadLinkId1 = 123L
    val roadLinkId2 = 456L
    val roadLinkId3 = 789L

    val roadAdjustedTimestamp = 0L
    val changesVVHTimestamp = 96400L

    val roadAddress1 = roadAddr.copy(startAddrMValue = 400, endAddrMValue = 1400, linkId = roadLinkId1, endMValue = 960.969, geometry = Seq(Point(0.0, 0.0), Point(960.969, 0.0)), adjustedTimestamp = roadAdjustedTimestamp, track = Track.Combined)
    val roadAddress2 = roadAddr.copy(startAddrMValue = 1400, endAddrMValue = 1600, linkId = roadLinkId2, endMValue = 201.986, sideCode = SideCode.TowardsDigitizing, geometry = Seq(Point(0.0, 0.0), Point(0.0, 201.986)), adjustedTimestamp = roadAdjustedTimestamp, track = Track.Combined)
    val roadAddress3 = roadAddr.copy(startAddrMValue = 1600, endAddrMValue = 1610, linkId = roadLinkId3, endMValue = 11.001, sideCode = SideCode.TowardsDigitizing, geometry = Seq(Point(0.0, 201.333), Point(0.0, 212.334)), adjustedTimestamp = roadAdjustedTimestamp, track = Track.Combined)
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
    results.values.flatten.map(_.commonHistoryId).toSet.size should be (1)
    results.values.flatten.map(_.commonHistoryId).toSet.head should be (roadAddress1.commonHistoryId)
  }

  test("Removed link") {
    val roadLinkId1 = 123L

    val roadAdjustedTimestamp = 0L
    val changesVVHTimestamp = 96400L

    val roadAddress1 = roadAddr.copy(startAddrMValue = 400, endAddrMValue = 1400, linkId = roadLinkId1, endMValue = 960.434, commonHistoryId = 0,
      geometry = Seq(Point(0.0, 0.0), Point(960.434, 0.0)), adjustedTimestamp = roadAdjustedTimestamp, track = Track.Combined)
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
    results.values.flatten.map(_.commonHistoryId).toSet.size should be (1)
    results.values.flatten.map(_.commonHistoryId).toSet.head should be (roadAddress1.commonHistoryId)
  }

  test("Multiple operations applied in order") {
    val roadLinkId1 = 123L
    val roadLinkId2 = 456L

    val roadAdjustedTimestamp = 0L
    val changesVVHTimestamp1 = 96400L
    val changesVVHTimestamp2 = 3 * 96400L

    val roadAddress1 = roadAddr.copy(track = Track.Combined, startAddrMValue = 400, endAddrMValue = 1400, linkId = roadLinkId1, endMValue = 960.434,
      adjustedTimestamp = roadAdjustedTimestamp, geometry = Seq(Point(0.0, 0.0), Point(960.434, 0.0)), commonHistoryId = 0)
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
    results.values.flatten.map(_.commonHistoryId).toSet.size should be (1)
    results.values.flatten.map(_.commonHistoryId).toSet.head should be (roadAddress1.commonHistoryId)
  }
}

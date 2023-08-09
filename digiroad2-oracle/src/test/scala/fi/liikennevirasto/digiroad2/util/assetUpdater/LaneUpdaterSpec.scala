package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset.{SideCode, TrafficDirection}
import fi.liikennevirasto.digiroad2.client.RoadLinkChangeType.Replace
import fi.liikennevirasto.digiroad2.client.{RoadLinkChange, RoadLinkChangeClient, RoadLinkChangeType, RoadLinkClient}
import fi.liikennevirasto.digiroad2.dao.MunicipalityDao
import fi.liikennevirasto.digiroad2.dao.lane.{LaneDao, LaneHistoryDao}
import fi.liikennevirasto.digiroad2.lane.LaneNumber.MainLane
import fi.liikennevirasto.digiroad2.lane._
import fi.liikennevirasto.digiroad2.service.lane.{LaneService, LaneWorkListService}
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.util.{LaneUtils, PolygonTools, TestTransactions}
import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer}
import org.joda.time.DateTime
import org.scalatest.mockito.MockitoSugar
import org.scalatest.mockito.MockitoSugar.mock
import org.scalatest.{FunSuite, Matchers}

import scala.io.Source

class LaneUpdaterSpec extends FunSuite with Matchers {
  val mockRoadLinkChangeClient: RoadLinkChangeClient = mock[RoadLinkChangeClient]
  val mockRoadLinkService: RoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockPolygonTools: PolygonTools = MockitoSugar.mock[PolygonTools]
  val mockMunicipalityDao: MunicipalityDao = MockitoSugar.mock[MunicipalityDao]
  val mockLaneDao: LaneDao = MockitoSugar.mock[LaneDao]
  val mockLaneHistoryDao: LaneHistoryDao = MockitoSugar.mock[LaneHistoryDao]
  val mockRoadAddressService: RoadAddressService = MockitoSugar.mock[RoadAddressService]
  val laneDao = new LaneDao()
  val laneHistoryDao = new LaneHistoryDao()

  val mockRoadLinkClient: RoadLinkClient = MockitoSugar.mock[RoadLinkClient]
  val roadLinkService = new RoadLinkService(mockRoadLinkClient, new DummyEventBus, new DummySerializer)
  val laneWorkListService = new LaneWorkListService


  object LaneServiceWithDao extends LaneService(mockRoadLinkService, new DummyEventBus, mockRoadAddressService) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: LaneDao = laneDao
    override def historyDao: LaneHistoryDao = laneHistoryDao
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao
  }


  val roadLinkChangeClient = new RoadLinkChangeClient
  val filePath: String = getClass.getClassLoader.getResource("smallChangeSet.json").getPath
  val jsonFile: String = Source.fromFile(filePath).mkString
  val testChanges: Seq[RoadLinkChange] = roadLinkChangeClient.convertToRoadLinkChange(jsonFile)

  val testUserName = "LaneUpdaterTest"
  val measureTolerance = 0.5

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  def generateTestLane(laneCode: Int, linkId: String, sideCode: SideCode, startM: Double, endM: Double, attributes: Seq[LaneProperty] = Seq()): PersistedLane = {
    PersistedLane(0, linkId = linkId, sideCode = sideCode.value, laneCode = laneCode, municipalityCode = 49,
      startMeasure = startM, endMeasure = endM, createdBy = Some(testUserName), createdDateTime = None, modifiedBy = None,
      modifiedDateTime = None, expiredBy = None, expiredDateTime = None, expired = false, timeStamp = DateTime.now().minusMonths(6).getMillis,
      geomModifiedDate = None, attributes = attributes)
  }

  val mainLaneLanePropertiesA = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(1))),
    LaneProperty("lane_type", Seq(LanePropertyValue("1"))),
    LaneProperty("start_date", Seq(LanePropertyValue("1.1.1970")))
  )

  val mainLaneLanePropertiesB = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(1))),
    LaneProperty("lane_type", Seq(LanePropertyValue("1"))),
    LaneProperty("start_date", Seq(LanePropertyValue("29.11.2008")))
  )

  val mainLaneLanePropertiesC = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(1))),
    LaneProperty("lane_type", Seq(LanePropertyValue("1"))),
    LaneProperty("start_date", Seq(LanePropertyValue("16.8.2015")))
  )

  val mainLaneLanePropertiesD = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(1))),
    LaneProperty("lane_type", Seq(LanePropertyValue("1"))),
    LaneProperty("start_date", Seq(LanePropertyValue("12.10.1998")))
  )

  val subLane2PropertiesA = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(2))),
    LaneProperty("lane_type", Seq(LanePropertyValue("2"))),
    LaneProperty("start_date", Seq(LanePropertyValue("1.1.1970")))
  )

  val subLane2PropertiesB = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(2))),
    LaneProperty("lane_type", Seq(LanePropertyValue("3"))),
    LaneProperty("start_date", Seq(LanePropertyValue("1.1.1985")))
  )

  test("Remove. 2 Road Links Removed, move all lanes on links to history") {
    // 2 road links removed
    val relevantChanges = testChanges.filter(_.changeType == RoadLinkChangeType.Remove)
    val oldLinkIds = relevantChanges.map(_.oldLink.get.linkId)
    runWithRollback {
      // Lanes on link 7766bff4-5f02-4c30-af0b-42ad3c0296aa:1
      val mainLaneLink1 = NewLane(0, 0.0, 30.92807173, 49, isExpired = false, isDeleted = false, mainLaneLanePropertiesA)
      LaneServiceWithDao.create(Seq(mainLaneLink1), Set("7766bff4-5f02-4c30-af0b-42ad3c0296aa:1"), SideCode.TowardsDigitizing.value, testUserName)
      val subLane2Link1 = NewLane(0, 0.0, 30.92807173, 49, isExpired = false, isDeleted = false, subLane2PropertiesA)
      LaneServiceWithDao.create(Seq(subLane2Link1), Set("7766bff4-5f02-4c30-af0b-42ad3c0296aa:1"), SideCode.TowardsDigitizing.value, testUserName)

      // Lanes on link 78e1984d-7dbc-4a7c-bd91-cb68f82ffccb:1
      val mainLaneLink2 = NewLane(0, 0.0, 55.71735255, 49, isExpired = false, isDeleted = false, mainLaneLanePropertiesA)
      LaneServiceWithDao.create(Seq(mainLaneLink2), Set("78e1984d-7dbc-4a7c-bd91-cb68f82ffccb:1"), SideCode.TowardsDigitizing.value, testUserName)
      val subLane2Link2 = NewLane(0, 0.0, 55.71735255, 49, isExpired = false, isDeleted = false, subLane2PropertiesA)
      LaneServiceWithDao.create(Seq(subLane2Link2), Set("78e1984d-7dbc-4a7c-bd91-cb68f82ffccb:1"), SideCode.TowardsDigitizing.value, testUserName)


      val existingLanes = LaneServiceWithDao.fetchExistingLanesByLinkIds(oldLinkIds)
      existingLanes.size should equal(4)
      val changeSet = LaneUpdater.handleChanges(relevantChanges)
      LaneUpdater.updateSamuutusChangeSet(changeSet, relevantChanges)

      val existingLanesAfterChanges = LaneServiceWithDao.fetchExistingLanesByLinkIds(oldLinkIds)
      existingLanesAfterChanges.size should equal(0)

      val historyLanes = LaneServiceWithDao.historyDao.fetchAllHistoryLanesByLinkIds(oldLinkIds, includeExpired = true)
      historyLanes.size should equal(4)
      historyLanes.foreach(lane => lane.expired should equal(true))
    }
  }

  test ("Add. 4 Road Link added, generate correct main lanes") {
    // 4 road links Added
    val newLinkIds = Seq("f2eba575-f306-4c37-b49d-a4d27a3fc049:1",
      "a15cf59b-c17c-4b6d-8e9b-a558143d0d47:1",
      "624df3a8-b403-4b42-a032-41d4b59e1840:1",
      "00bb2656-6da1-433a-84ec-ebfd8144bb43:1")
    val relevantChanges = testChanges.filter(change => change.changeType == RoadLinkChangeType.Add && newLinkIds.contains(change.newLinks.head.linkId))


    runWithRollback {
      val existingLanesOnNewLinks = LaneServiceWithDao.fetchExistingLanesByLinkIds(newLinkIds)
      val newRoadLinks = roadLinkService.getRoadLinksByLinkIds(newLinkIds.toSet, newTransaction = false)
      existingLanesOnNewLinks.size should equal(0)

      val changeSet = LaneUpdater.handleChanges(relevantChanges)
      LaneUpdater.updateSamuutusChangeSet(changeSet, relevantChanges)

      val createdLanesOnNewLinks = LaneServiceWithDao.fetchExistingLanesByLinkIds(newLinkIds)
      createdLanesOnNewLinks.size should equal(6)

      // BothDirections traffic direction link should have 2 main lanes
      createdLanesOnNewLinks.count(_.linkId == "f2eba575-f306-4c37-b49d-a4d27a3fc049:1") should equal(2)
      // BothDirections traffic direction link should have 2 main lanes
      createdLanesOnNewLinks.count(_.linkId == "a15cf59b-c17c-4b6d-8e9b-a558143d0d47:1") should equal(2)
      // AgainstDigitizing traffic direction link should have 1 main lane
      createdLanesOnNewLinks.count(_.linkId == "624df3a8-b403-4b42-a032-41d4b59e1840:1") should equal(1)
      // TowardsDigitizing traffic direction link should have 1 main lane
      createdLanesOnNewLinks.count(_.linkId == "00bb2656-6da1-433a-84ec-ebfd8144bb43:1") should equal(1)

      createdLanesOnNewLinks.foreach(lane => {
        val roadLink = newRoadLinks.find(_.linkId == lane.linkId).get
        lane.startMeasure should equal(0)
        lane.endMeasure should equal(LaneUtils.roundMeasure(roadLink.length))
        lane.laneCode should equal(1)
        val laneType = LaneServiceWithDao.getPropertyValue(lane, "lane_type").get.value
        laneType should equal("1")
      })
    }
  }

  test("Replacement. Link extended from digitizing start") {
    runWithRollback {
      val oldLinkId = "84e223f4-b349-4103-88ee-03b96153ef85:1"
      val newLinkId = "a1ebe866-e704-411d-8c31-53522bc04a37:1"
      val relevantChange = testChanges.filter(_.oldLink.nonEmpty).filter(_.oldLink.get.linkId == oldLinkId)
      val newRoadLink = roadLinkService.getRoadLinksByLinkIds(Set(newLinkId), newTransaction = false).head

      // Main lane towards digitizing
      val mainLane11 = NewLane(0, 0.0, 36.18939714, 49, isExpired = false, isDeleted = false, mainLaneLanePropertiesA)
      LaneServiceWithDao.create(Seq(mainLane11), Set(oldLinkId), SideCode.TowardsDigitizing.value, testUserName)

      // Main lane against digitizing
      val mainLane21 = NewLane(0, 0.0, 36.18939714, 49, isExpired = false, isDeleted = false, mainLaneLanePropertiesA)
      LaneServiceWithDao.create(Seq(mainLane21), Set(oldLinkId), SideCode.AgainstDigitizing.value, testUserName)

      // Cut additional lane towards digitizing
      val additionalLaneEndMeasure = 26.359
      val subLane12 = NewLane(0, 0.0, additionalLaneEndMeasure, 49, isExpired = false, isDeleted = false, subLane2PropertiesA)
      LaneServiceWithDao.create(Seq(subLane12), Set(oldLinkId), SideCode.TowardsDigitizing.value, testUserName)

      // Verify lanes are created
      val existingLanes = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(oldLinkId))
      existingLanes.size should equal(3)

      // Apply Changes
      val changeSet = LaneUpdater.handleChanges(relevantChange)
      LaneUpdater.updateSamuutusChangeSet(changeSet, relevantChange)

      // Same amount of lanes should persist
      val lanesAfterChanges = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(newLinkId))
      lanesAfterChanges.size should equal(3)

      // MainLanes should extend to cover whole of new road link
      val mainLanesAfterChanges = lanesAfterChanges.filter(_.laneCode == MainLane.oneDigitLaneCode)
      mainLanesAfterChanges.foreach(mainLane => {
        mainLane.startMeasure should equal(0.0)
        mainLane.endMeasure should equal(LaneUtils.roundMeasure(newRoadLink.length))
      })

      // Additional lane measures or side code should have not changed
      val additionalLaneAfterChanges = lanesAfterChanges.find(_.laneCode == 2).get
      additionalLaneAfterChanges.sideCode should equal(SideCode.TowardsDigitizing.value)

      //Verify lane history
      val newLaneIds = lanesAfterChanges.map(_.id)
      val oldLaneIds = existingLanes.map(_.id)
      val laneHistory = LaneServiceWithDao.historyDao.getHistoryLanesChangedSince(DateTime.now().minusDays(1), DateTime.now().plusDays(1), withAdjust = true)
      laneHistory.size should equal(3)
      laneHistory.foreach(historyLane => {
        // Old lanes are expired and replaced by new lanes with correct measures, history newId references new lane
        historyLane.expired should equal (true)
        oldLaneIds.contains(historyLane.oldId) should equal (true)
        newLaneIds.contains(historyLane.newId) should equal (true)
      })
    }
  }

  test("Split. Road Link split into two new links. Split 2 main lanes and 1 additional lane to both new links") {
    runWithRollback {
      val oldLinkID = "3a832249-d3b9-4c22-9b08-c7e9cd98cbd7:1"
      val newLinkID1 = "581687d9-f4d5-4fa5-9e44-87026eb74774:1"
      val newLinkID2 = "6ceeebf4-2351-46f0-b151-3ed02c7cfc05:1"

      val relevantChange = testChanges.filter(change => change.changeType == RoadLinkChangeType.Split && change.oldLink.get.linkId == "3a832249-d3b9-4c22-9b08-c7e9cd98cbd7:1")

      // Main lane towards digitizing
      val mainLane11 = NewLane(0, 0.0, 432.23526228, 49, isExpired = false, isDeleted = false, mainLaneLanePropertiesA)
      LaneServiceWithDao.create(Seq(mainLane11), Set(oldLinkID), SideCode.TowardsDigitizing.value, testUserName)

      // Main lane against digitizing
      val mainLane21 = NewLane(0, 0.0, 432.23526228, 49, isExpired = false, isDeleted = false, mainLaneLanePropertiesA)
      LaneServiceWithDao.create(Seq(mainLane21), Set(oldLinkID), SideCode.AgainstDigitizing.value, testUserName)

      // Cut additional lane towards digitizing
      val subLane12 = NewLane(0, 85.0, 215.0, 49, isExpired = false, isDeleted = false, subLane2PropertiesA)
      LaneServiceWithDao.create(Seq(subLane12), Set(oldLinkID), SideCode.TowardsDigitizing.value, testUserName)

      // Verify lanes are created
      val existingLanes = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(oldLinkID))
      existingLanes.size should equal (3)

      // Apply Changes
      val changeSet = LaneUpdater.handleChanges(relevantChange)
      LaneUpdater.updateSamuutusChangeSet(changeSet, relevantChange)

      // All 3 lanes should be split between two new links, main lane measures should equal new link length
      val lanesAfterChanges = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(newLinkID1, newLinkID2)).sortBy(lane => (lane.laneCode, lane.sideCode))

      lanesAfterChanges.size should equal (6)
      lanesAfterChanges.count(_.linkId == newLinkID1) should equal (3)
      lanesAfterChanges.count(_.linkId == newLinkID2) should equal (3)
      val (mainLanesAfterChanges, additionalLanesAfterChanges) = lanesAfterChanges.partition(_.laneCode == MainLane.oneDigitLaneCode)

      // Verify main lane changes
      mainLanesAfterChanges.count(mainLane => mainLane.sideCode == AgainstDigitizing.value) should equal(2)
      mainLanesAfterChanges.count(mainLane => mainLane.sideCode == TowardsDigitizing.value) should equal(2)
      mainLanesAfterChanges.foreach(mainLane => {
        val newRoadLink = relevantChange.flatMap(_.newLinks).find(_.linkId == mainLane.linkId).get
        mainLane.startMeasure should equal (0.0)
        mainLane.endMeasure should equal (LaneUtils.roundMeasure(newRoadLink.linkLength))
      })

      // Verify additional lane changes
      mainLanesAfterChanges.count(additionalLane => additionalLane.sideCode == AgainstDigitizing.value) should equal(2)
      mainLanesAfterChanges.count(additionalLane => additionalLane.sideCode == TowardsDigitizing.value) should equal(2)
      val originalAdditionalLane = existingLanes.find(_.laneCode == 2).get
      val originalAdditionalLaneLength = originalAdditionalLane.endMeasure - originalAdditionalLane.startMeasure
      val additionalLaneLengths = additionalLanesAfterChanges.map(additionalLane => additionalLane.endMeasure - additionalLane.startMeasure)
      val additionalLanesTotalLength = additionalLaneLengths.foldLeft(0.0)((length1 ,length2) => length1 + length2)
      val lengthDifferenceAfterChange = Math.abs(originalAdditionalLaneLength - additionalLanesTotalLength)
      (lengthDifferenceAfterChange < measureTolerance) should equal (true)


      //Verify lane history
      val newLaneIds = lanesAfterChanges.map(_.id)
      val oldLaneIds = existingLanes.map(_.id)
      val laneHistory = LaneServiceWithDao.historyDao.getHistoryLanesChangedSince(DateTime.now().minusDays(1), DateTime.now().plusDays(1), withAdjust = true)
      laneHistory.size should equal(6)
      laneHistory.foreach(historyLane => {
        historyLane.expired should equal (true)
        oldLaneIds.contains(historyLane.oldId) should equal (true)
        newLaneIds.contains(historyLane.newId) should equal (true)
      })
    }
  }

  test("Split. Given a Road Link that is split into 2 new Links; when 1 new Link is deleted; then the Main Lane's length should equal remaining Link's length.") {
    runWithRollback {
      val oldLinkID = "086404cc-ffaa-46e5-a0c5-b428a846261c:1"
      val newLinkID2 = "da1ce256-2f8a-43f9-9008-5bf058c1bcd7:1"

      val relevantChange = testChanges.filter(change => change.changeType == RoadLinkChangeType.Split && change.oldLink.get.linkId == oldLinkID)

      // Main lane towards digitizing
      val mainLane11 = NewLane(0, 0.0, 79.405, 624, isExpired = false, isDeleted = false, mainLaneLanePropertiesA)
      LaneServiceWithDao.create(Seq(mainLane11), Set(oldLinkID), SideCode.TowardsDigitizing.value, testUserName)

      // Main lane against digitizing
      val mainLane21 = NewLane(0, 0.0, 79.405, 624, isExpired = false, isDeleted = false, mainLaneLanePropertiesA)
      LaneServiceWithDao.create(Seq(mainLane21), Set(oldLinkID), SideCode.AgainstDigitizing.value, testUserName)

      // Verify lanes are created
      val existingLanes = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(oldLinkID))
      existingLanes.size should equal(2)

      // Apply Changes
      val changeSet = LaneUpdater.handleChanges(relevantChange)
      LaneUpdater.updateSamuutusChangeSet(changeSet, relevantChange)

      // Verify Main Lane length is equal to new Link length
      val lanesAfterChanges = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(newLinkID2)).sortBy(lane => (lane.laneCode, lane.sideCode))
      lanesAfterChanges.size should equal(2)
      lanesAfterChanges.toList.head.endMeasure should equal(111.028)
    }
  }

  test("Split. Given a Road Link that is split into 2 new Links; when 1 new Link is deleted; then Additional Lane within deleted Link should be removed.") {
    runWithRollback {
      val oldLinkID = "086404cc-ffaa-46e5-a0c5-b428a846261c:1"
      val newLinkID2 = "da1ce256-2f8a-43f9-9008-5bf058c1bcd7:1"

      val relevantChange = testChanges.filter(change => change.changeType == RoadLinkChangeType.Split && change.oldLink.get.linkId == oldLinkID)

      // Main lane towards digitizing
      val mainLane = NewLane(0, 0.0, 79.405, 624, isExpired = false, isDeleted = false, mainLaneLanePropertiesA)
      LaneServiceWithDao.create(Seq(mainLane), Set(oldLinkID), SideCode.TowardsDigitizing.value, testUserName)

      // Additional Lane towards digitizing, covering only the Deleted Link
      val subLane = NewLane(0, 74.0, 79.405, 624, isExpired = false, isDeleted = false, subLane2PropertiesA)
      LaneServiceWithDao.create(Seq(subLane), Set(oldLinkID), SideCode.TowardsDigitizing.value, testUserName)

      // Verify lanes are created
      val existingLanes = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(oldLinkID))
      existingLanes.size should equal(2)

      // Apply Changes
      val changeSet = LaneUpdater.handleChanges(relevantChange)
      LaneUpdater.updateSamuutusChangeSet(changeSet, relevantChange)

      // Verify that Lanes within deleted Link are removed, and the Main Lane is copied to New Link
      val lanesOnOldLinkAfterChanges = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(oldLinkID)).sortBy(lane => (lane.laneCode, lane.sideCode))
      lanesOnOldLinkAfterChanges.size should equal(0)
      val oldLinkHistoryLanes = laneHistoryDao.fetchAllHistoryLanesByLinkIds(Seq(oldLinkID), includeExpired = true)
      oldLinkHistoryLanes.map (lane => lane.expired should equal(true))
      val lanesOnNewLinkAfterChanges = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(newLinkID2)).sortBy(lane => (lane.laneCode, lane.sideCode))
      lanesOnNewLinkAfterChanges.size should equal(1)
      lanesOnNewLinkAfterChanges.head.laneCode should equal(1)
    }
  }

  test("Split. Given a Road Link that is split into 2 new Links, when 1 new Link is deleted, then Additional Lane within both the deleted and the remaining Link should have correct length.") {
    runWithRollback {
      val oldLinkID = "086404cc-ffaa-46e5-a0c5-b428a846261c:1"
      val newLinkID2 = "da1ce256-2f8a-43f9-9008-5bf058c1bcd7:1"

      val relevantChange = testChanges.filter(change => change.changeType == RoadLinkChangeType.Split && change.oldLink.get.linkId == oldLinkID)

      val subLane = NewLane(0, 50.0, 70.0, 624, isExpired = false, isDeleted = false, subLane2PropertiesA)
      LaneServiceWithDao.create(Seq(subLane), Set(oldLinkID), SideCode.TowardsDigitizing.value, testUserName)

      // Verify lanes are created
      val existingLanes = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(oldLinkID))
      existingLanes.size should equal(1)

      // Apply Changes
      val changeSet = LaneUpdater.handleChanges(relevantChange)
      LaneUpdater.updateSamuutusChangeSet(changeSet, relevantChange)

      // Verify that the Lane within both the deleted and the remaining Link has correct length.
      val lanesAfterChanges = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(newLinkID2)).sortBy(lane => (lane.laneCode, lane.sideCode))
      lanesAfterChanges.size should equal(1)
      lanesAfterChanges.head.laneCode should equal(2)
      val additionalLaneCutOnNewLink = 0.22 // 22% = additional lane's cut of remaining Link's length
      val newLinkLength = 111.028
      val additionalLaneApproxLengthAfterChange = newLinkLength * additionalLaneCutOnNewLink
      val additionalLaneTrueLengthAfterChange = lanesAfterChanges.map(additionalLane => additionalLane.endMeasure - additionalLane.startMeasure).head
      val lengthDifferenceAfterChange = Math.abs(additionalLaneTrueLengthAfterChange - additionalLaneApproxLengthAfterChange)
      (lengthDifferenceAfterChange < measureTolerance) should equal(true)
    }
  }

  test("Merge. 4 links merged together. Main lanes and two additional lanes should fuse together.") {
    runWithRollback {
      val newLinkId  = "fbea6a9c-6682-4a1b-9807-7fb11a67e227:1"
      val oldLinkId1 = "9d23b85a-d7bf-4c6f-83ff-aa391ff4879f:1"
      val oldLinkId2 = "41a67ca5-886f-44ff-a9ca-92d7d9bea129:1"
      val oldLinkId3 = "b05075a5-45e1-447e-9813-752ba3e07fe5:1"
      val oldLinkId4 = "7ce91531-42e8-424b-a528-b72af5cb1180:1"

      // Get merge changes
      val relevantChanges = testChanges.filter(change => change.changeType == Replace && change.newLinks.head.linkId == newLinkId)
      val oldLinks = relevantChanges.flatMap(_.oldLink)

      val newRoadLinkLength = 700.537
      val oldLink1Length = 580.215

      // Create test main lanes for all old links
      oldLinks.foreach(oldLink => {
        // Give different start dates to test inheriting latest date for merged main lane
        val propertiesToUse = oldLink.linkId match {
          case linkId if linkId == oldLinkId1 => mainLaneLanePropertiesA
          case linkId if linkId == oldLinkId2 => mainLaneLanePropertiesB
          case linkId if linkId == oldLinkId3 => mainLaneLanePropertiesC
          case linkId if linkId == oldLinkId4 => mainLaneLanePropertiesD
        }
        val oldLinkLengthRounded = LaneUtils.roundMeasure(oldLink.linkLength)
        val mainLane11 = NewLane(0, 0.0, oldLinkLengthRounded, 49, isExpired = false, isDeleted = false, propertiesToUse)
        LaneServiceWithDao.create(Seq(mainLane11), Set(oldLink.linkId), SideCode.TowardsDigitizing.value, testUserName)
        val mainLane21 = NewLane(0, 0.0, oldLinkLengthRounded, 49, isExpired = false, isDeleted = false, propertiesToUse)
        LaneServiceWithDao.create(Seq(mainLane21), Set(oldLink.linkId), SideCode.AgainstDigitizing.value, testUserName)
      })

      // Cut additional lane against digitizing
      val subLane12a = NewLane(0, 0.0, 91.5, 49, isExpired = false, isDeleted = false, subLane2PropertiesA)
      LaneServiceWithDao.create(Seq(subLane12a), Set(oldLinkId1), SideCode.AgainstDigitizing.value, testUserName)

      // Cut additional lane towards digitizing, ending at the end of link
      val subLane12b = NewLane(0, 80.5, oldLink1Length, 49, isExpired = false, isDeleted = false, subLane2PropertiesA)
      LaneServiceWithDao.create(Seq(subLane12b), Set(oldLinkId1), SideCode.TowardsDigitizing.value, testUserName)

      // Cut additional lane towards digitizing, starting from start of link
      val subLane12c = NewLane(0, 0.0, 21.7, 49, isExpired = false, isDeleted = false, subLane2PropertiesA)
      LaneServiceWithDao.create(Seq(subLane12c), Set(oldLinkId2), SideCode.TowardsDigitizing.value, testUserName)

      // Verify lanes are created
      val existingLanes = LaneServiceWithDao.fetchExistingLanesByLinkIds(oldLinks.map(_.linkId))
      existingLanes.size should equal (11)

      // Apply changes to lanes
      val changeSet = LaneUpdater.handleChanges(relevantChanges)
      LaneUpdater.updateSamuutusChangeSet(changeSet, relevantChanges)

      // Only 4 lanes should exist on new roadLink, Main lanes and connected additional lanes with the same attributes should combine
      val lanesAfterChanges = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(newLinkId)).sortBy(lane => (lane.laneCode, lane.sideCode))
      val (mainLanesAfterChanges, additionalLanesAfterChanges) = lanesAfterChanges.partition(_.laneCode == LaneNumber.MainLane.oneDigitLaneCode)

      // Validate main lane changes. New road link should have 2 main lanes fused together from the original main lanes
      mainLanesAfterChanges.size should equal(2)
      mainLanesAfterChanges.foreach(mainLane => {
        mainLane.startMeasure should equal(0.0)
        mainLane.endMeasure should equal(newRoadLinkLength)
        val startDate = LaneServiceWithDao.getPropertyValue(mainLane, "start_date")
        // Main lanes on original links had different start dates, latest one should be inherited
        startDate.get.value should equal("16.8.2015")
      })

      // Validate additional lane changes: additional lanes 12b and 12c should be fused together
      // and additional lane 12a should stay the same
      additionalLanesAfterChanges.size should equal(2)
      val fusedLaneTowardsDigitizing = additionalLanesAfterChanges.find(_.sideCode == TowardsDigitizing.value).get
      val originalSublane12bLength = subLane12b.endMeasure - subLane12b.startMeasure
      val originalSublane12cLength = subLane12c.endMeasure - subLane12c.startMeasure
      fusedLaneTowardsDigitizing.startMeasure should equal(subLane12b.startMeasure)
      val endMDiff = fusedLaneTowardsDigitizing.endMeasure - (originalSublane12bLength + originalSublane12cLength + fusedLaneTowardsDigitizing.startMeasure)
      endMDiff < 0.1 should equal(true)

      // Validate history information created due to changes
      val historyLanes = laneHistoryDao.fetchAllHistoryLanesByLinkIds(Seq(oldLinkId1, oldLinkId2, oldLinkId3, oldLinkId4), includeExpired = true)
      historyLanes.size should equal(11)
      // All original lanes get expired, only the lengthened lane gets to reference the newId
      historyLanes.count(_.expired) should equal(11)
      historyLanes.count(_.newId == 0) should equal(7)
      // There should be 1 history row referencing to current lanes with new ID
      lanesAfterChanges.foreach(lane => {
        historyLanes.count(_.newId == lane.id) should equal(1)
      })
    }
  }

  test("TrafficDirection changed on link, generate new main lanes, raise to work list if it has additional lanes") {
    runWithRollback {
      val newLinkId = "1438d48d-dde6-43db-8aba-febf3d2220c0:2"
      val relevantChange = testChanges.find(change => change.changeType == Replace && change.newLinks.head.linkId == newLinkId).get
      val oldLinkWithAgainstDigitizingTD = Option(relevantChange.oldLink.get.copy(trafficDirection = TrafficDirection.apply(3)))
      val trafficDirectionChange: RoadLinkChange = relevantChange.copy(oldLink = oldLinkWithAgainstDigitizingTD)
      val oldLink = trafficDirectionChange.oldLink.get

      // Main lane towards digitizing
      val mainLane1 = NewLane(0, 0.0, oldLink.linkLength, 49, isExpired = false, isDeleted = false, mainLaneLanePropertiesA)
      LaneServiceWithDao.create(Seq(mainLane1), Set(oldLink.linkId), SideCode.AgainstDigitizing.value, testUserName)

      // Main lane towards digitizing
      val additionalLane2 = NewLane(0, 0.0, oldLink.linkLength, 49, isExpired = false, isDeleted = false, subLane2PropertiesA)
      LaneServiceWithDao.create(Seq(additionalLane2), Set(oldLink.linkId), SideCode.AgainstDigitizing.value, testUserName)

      val currentItems = laneWorkListService.getLaneWorkList
      currentItems.size should equal(0)

      LaneUpdater.updateTrafficDirectionChangesLaneWorkList(Seq(trafficDirectionChange))

      val itemsAfterUpdate = laneWorkListService.workListDao.getAllItems
      itemsAfterUpdate.size should equal(1)

      val lanesOnOldLinkBefore = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(oldLink.linkId))
      // Old link has one main lane and one additional lane
      lanesOnOldLinkBefore.size should equal(2)

      val changeSet = LaneUpdater.handleChanges(Seq(), Seq(trafficDirectionChange))
      LaneUpdater.updateSamuutusChangeSet(changeSet, Seq(trafficDirectionChange))

      val lanesOnOldLinkAfter = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(oldLink.linkId))
      // Additional lane should stay on old link
      lanesOnOldLinkAfter.size should equal(1)

      val lanesOnNewLinkAfter = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(newLinkId))
      // Two main lanes should be generated for new link
      lanesOnNewLinkAfter.size should equal(2)
      lanesOnNewLinkAfter.foreach(lane => LaneNumber.isMainLane(lane.laneCode) should equal(true))

    }
  }

  test("changes come in multiple part, values same") {

    val oldLink1 = "ed1dff4a-b3f1-41a1-a1af-96e896c3145d:1"
    val oldLink2 = "197f22f2-3427-4412-9d2a-3848a570c996:1"

    val linkIdNew1 = "59704775-596d-46c8-99cf-e85013bbcb56:1"
    val linkIdNew2 = "d989ee2b-f6d0-4433-b5b6-0a4fe3d62400:1"

    val relevantChange = testChanges.filter(_.oldLink.isDefined).filter(change =>Seq(oldLink1,oldLink2).contains( change.oldLink.get.linkId))

    runWithRollback {
      val oldRoadLink1 = roadLinkService.getExpiredRoadLinkByLinkId(oldLink1).get
      val oldRoadLink2 = roadLinkService.getExpiredRoadLinkByLinkId(oldLink2).get

      val mainLane1 = NewLane(0, 0.0, oldRoadLink1.length, 49, isExpired = false, isDeleted = false, mainLaneLanePropertiesA)
      LaneServiceWithDao.create(Seq(mainLane1), Set(oldRoadLink1.linkId), SideCode.AgainstDigitizing.value, testUserName)

      val mainLane2 = NewLane(0, 0.0, oldRoadLink1.length, 49, isExpired = false, isDeleted = false, mainLaneLanePropertiesA)
      LaneServiceWithDao.create(Seq(mainLane2), Set(oldRoadLink1.linkId), SideCode.TowardsDigitizing.value, testUserName)

      val mainLane3 = NewLane(0, 0.0, oldRoadLink2.length, 49, isExpired = false, isDeleted = false, mainLaneLanePropertiesA)
      LaneServiceWithDao.create(Seq(mainLane3), Set(oldRoadLink2.linkId), SideCode.AgainstDigitizing.value, testUserName)

      val mainLane4 = NewLane(0, 0.0, oldRoadLink2.length, 49, isExpired = false, isDeleted = false, mainLaneLanePropertiesA)
      LaneServiceWithDao.create(Seq(mainLane4), Set(oldRoadLink2.linkId), SideCode.TowardsDigitizing.value, testUserName)


      val lanesBefore = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(oldLink1, oldLink2))
      lanesBefore.size should be(4)
      lanesBefore.head.expired should be(false)

      val changeSet = LaneUpdater.handleChanges(relevantChange)
      LaneUpdater.updateSamuutusChangeSet(changeSet, relevantChange)

      val assetsAfter = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(linkIdNew1, linkIdNew2))

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.foreach(p => {
        println(s"id: ${p.id}, value: ${p.laneCode} , linkId: ${p.linkId}, startMeasure: ${p.startMeasure}, endMeasure: ${p.endMeasure}")
      })

      sorted.size should be(4)

      sorted.filter(_.linkId == linkIdNew1).head.startMeasure should be(0)
      sorted.filter(_.linkId == linkIdNew1).head.endMeasure should be(101.922)

      sorted.filter(_.linkId == linkIdNew2).head.startMeasure should be(0)
      sorted.filter(_.linkId == linkIdNew2).head.endMeasure should be(337.589)

    }
  }

  test("changes come in multiple part, additional lanes") {

    val oldLink1 = "ed1dff4a-b3f1-41a1-a1af-96e896c3145d:1"
    val oldLink2 = "197f22f2-3427-4412-9d2a-3848a570c996:1"

    val linkIdNew1 = "59704775-596d-46c8-99cf-e85013bbcb56:1"
    val linkIdNew2 = "d989ee2b-f6d0-4433-b5b6-0a4fe3d62400:1"

    val relevantChange = testChanges.filter(_.oldLink.isDefined).filter(change => Seq(oldLink1, oldLink2).contains(change.oldLink.get.linkId))

    runWithRollback {
      val oldRoadLink1 = roadLinkService.getExpiredRoadLinkByLinkId(oldLink1).get
      val oldRoadLink2 = roadLinkService.getExpiredRoadLinkByLinkId(oldLink2).get

      val mainLane1 = NewLane(0, 0.0, oldRoadLink1.length, 49, isExpired = false, isDeleted = false, mainLaneLanePropertiesA)
      LaneServiceWithDao.create(Seq(mainLane1), Set(oldRoadLink1.linkId), SideCode.AgainstDigitizing.value, testUserName)

      val additionalLane1 = NewLane(0, 0.0, oldRoadLink1.length, 49, isExpired = false, isDeleted = false, subLane2PropertiesA)
      LaneServiceWithDao.create(Seq(additionalLane1), Set(oldRoadLink1.linkId), SideCode.AgainstDigitizing.value, testUserName)


      val mainLane2 = NewLane(0, 0.0, oldRoadLink1.length, 49, isExpired = false, isDeleted = false, mainLaneLanePropertiesA)
      LaneServiceWithDao.create(Seq(mainLane2), Set(oldRoadLink1.linkId), SideCode.TowardsDigitizing.value, testUserName)

      val additionalLane2 = NewLane(0, 0.0, oldRoadLink1.length, 49, isExpired = false, isDeleted = false, subLane2PropertiesA)
      LaneServiceWithDao.create(Seq(additionalLane2), Set(oldRoadLink1.linkId), SideCode.AgainstDigitizing.value, testUserName)

      val mainLane3 = NewLane(0, 0.0, oldRoadLink2.length, 49, isExpired = false, isDeleted = false, mainLaneLanePropertiesA)
      LaneServiceWithDao.create(Seq(mainLane3), Set(oldRoadLink2.linkId), SideCode.AgainstDigitizing.value, testUserName)

      val additionalLane3 = NewLane(0, 0.0, oldRoadLink2.length, 49, isExpired = false, isDeleted = false, subLane2PropertiesA)
      LaneServiceWithDao.create(Seq(additionalLane3), Set(oldRoadLink2.linkId), SideCode.AgainstDigitizing.value, testUserName)

      val mainLane4 = NewLane(0, 0.0, oldRoadLink2.length, 49, isExpired = false, isDeleted = false, mainLaneLanePropertiesA)
      LaneServiceWithDao.create(Seq(mainLane4), Set(oldRoadLink2.linkId), SideCode.TowardsDigitizing.value, testUserName)

      val additionalLane4 = NewLane(0, 0.0, oldRoadLink2.length, 49, isExpired = false, isDeleted = false, subLane2PropertiesA)
      LaneServiceWithDao.create(Seq(additionalLane4), Set(oldRoadLink2.linkId), SideCode.TowardsDigitizing.value, testUserName)

      val lanesBefore = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(oldLink1, oldLink2))
      lanesBefore.size should be(8)
      lanesBefore.head.expired should be(false)

      val changeSet = LaneUpdater.handleChanges(relevantChange)
      LaneUpdater.updateSamuutusChangeSet(changeSet, relevantChange)

      val assetsAfter = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(linkIdNew1, linkIdNew2))

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.size should be(8)

      val assetOnNewLink1 = sorted.filter(_.linkId == linkIdNew1).sortBy(_.startMeasure)

      val assetOnNewLink2 = sorted.filter(_.linkId == linkIdNew2).sortBy(_.endMeasure)

      assetOnNewLink1.head.startMeasure should be(0)
      assetOnNewLink1.head.endMeasure should be(101.922)

      assetOnNewLink1.last.startMeasure should be(52.15)
      assetOnNewLink1.last.endMeasure should be(101.922)

      assetOnNewLink2.map(a=> {
        a.startMeasure should be(0)
        a.endMeasure should be(337.589)
      })
    }
  }

  test("Merge. A completely new segment is added between two existing links as they are merged. " +
    "Main lanes should combine into one, additional lanes positions should persist") {
    runWithRollback {
      val oldLinkID1 = "be36fv60-6813-4b01-a57b-67136dvv6862:1"
      val oldLinkID2 = "38ebf780-ae0c-49f3-8679-a6e45ff8f56f:1"
      val newLinkID = "007b3d46-526d-46c0-91a5-9e624cbb073b:1"
      val oldRoadLink1 = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkID1).get
      val oldRoadLink2 = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkID2).get
      val newRoadLink = roadLinkService.getRoadLinkByLinkId(newLinkID).get
      val relevantChanges = testChanges.filter(change => change.newLinks.map(_.linkId).contains(newLinkID) && change.changeType == RoadLinkChangeType.Replace)

      // Main lane towards digitizing on link 1
      val mainLaneLink1 = NewLane(0, 0.0, oldRoadLink1.length, oldRoadLink1.municipalityCode, isExpired = false, isDeleted = false, mainLaneLanePropertiesA)
      LaneServiceWithDao.create(Seq(mainLaneLink1), Set(oldLinkID1), SideCode.TowardsDigitizing.value, testUserName)

      // Main lane against digitizing on link 2
      val mainLaneLink2 = NewLane(0, 0.0, oldRoadLink2.length, oldRoadLink2.municipalityCode, isExpired = false, isDeleted = false, mainLaneLanePropertiesB)
      LaneServiceWithDao.create(Seq(mainLaneLink2), Set(oldLinkID2), SideCode.AgainstDigitizing.value, testUserName)

      // Link 1, cut additional lane 2 towards digitizing, starting from 180.75, ending at road link end
      val subLaneLink1a = NewLane(0, 180.75, oldRoadLink1.length, oldRoadLink1.municipalityCode, isExpired = false, isDeleted = false, subLane2PropertiesA)
      LaneServiceWithDao.create(Seq(subLaneLink1a), Set(oldLinkID1), SideCode.TowardsDigitizing.value, testUserName)

      // Link 1, cut additional lane 2 towards digitizing, starting from 0.0, ending at 180.75
      val subLaneLink1b = NewLane(0, 10.5, 180.75, oldRoadLink1.municipalityCode, isExpired = false, isDeleted = false, subLane2PropertiesB)
      LaneServiceWithDao.create(Seq(subLaneLink1b), Set(oldLinkID1), SideCode.TowardsDigitizing.value, testUserName)

      // Verify lane is created
      val existingLanes = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(oldLinkID1))
      existingLanes.size should equal(3)

      // Apply Changes
      val changeSet = LaneUpdater.handleChanges(relevantChanges)
      LaneUpdater.updateSamuutusChangeSet(changeSet, relevantChanges)

      val lanesAfterChanges = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(newLinkID))
      lanesAfterChanges.size should equal(3)

      // Verify Main Lane length is equal to new Link length
      val mainLaneAfterChanges = lanesAfterChanges.find(lane => LaneNumber.isMainLane(lane.laneCode)).get
      val mainLaneLength = mainLaneAfterChanges.endMeasure - mainLaneAfterChanges.startMeasure
      mainLaneLength should equal(newRoadLink.length)
      // Digitizing direction has changed, main lane side code should be flipped
      mainLaneAfterChanges.sideCode should equal(SideCode.AgainstDigitizing.value)

      val additionalLanesAfterChanges = lanesAfterChanges.filter(_.laneCode == 2)
      // Two additional lanes should remain
      additionalLanesAfterChanges.size should equal(2)
      // Side codes should be flipped due to digitization direction change
      additionalLanesAfterChanges.foreach(additionalLane => additionalLane.sideCode should equal(SideCode.AgainstDigitizing.value))

      // Check that length of additional lanes doesn't change too much
      additionalLanesAfterChanges.exists(additionalLane => {
        val additionalLaneLengthAfterChanges = additionalLane.endMeasure - additionalLane.startMeasure
        val subLaneLink1aLength = subLaneLink1a.endMeasure - subLaneLink1a.startMeasure
        val diffInLength = Math.abs(additionalLaneLengthAfterChanges - subLaneLink1aLength)
        diffInLength < 2.0
      }) should equal(true)

      additionalLanesAfterChanges.exists(additionalLane => {
        val additionalLaneLengthAfterChanges = additionalLane.endMeasure - additionalLane.startMeasure
        val subLaneLink1bLength = subLaneLink1b.endMeasure - subLaneLink1b.startMeasure
        val diffInLength = Math.abs(additionalLaneLengthAfterChanges - subLaneLink1bLength)
        diffInLength < 2.0
      }) should equal(true)
    }
  }
}

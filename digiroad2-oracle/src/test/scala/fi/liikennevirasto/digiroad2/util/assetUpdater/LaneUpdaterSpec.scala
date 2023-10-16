package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset.{SideCode, TrafficDirection}
import fi.liikennevirasto.digiroad2.client.RoadLinkChangeType.{Replace, Split}
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
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

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
  val measureTolerance = 1.0

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

  val subLane3Properties = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(3))),
    LaneProperty("lane_type", Seq(LanePropertyValue("3"))),
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
      val mainLane11ID = LaneServiceWithDao.create(Seq(mainLane11), Set(oldLinkID), SideCode.TowardsDigitizing.value, testUserName).head

      // Main lane against digitizing
      val mainLane21 = NewLane(0, 0.0, 432.23526228, 49, isExpired = false, isDeleted = false, mainLaneLanePropertiesA)
      val mainLane21ID = LaneServiceWithDao.create(Seq(mainLane21), Set(oldLinkID), SideCode.AgainstDigitizing.value, testUserName).head

      // Cut additional lane towards digitizing
      val subLane12 = NewLane(0, 85.0, 215.0, 49, isExpired = false, isDeleted = false, subLane2PropertiesA)
      val sublane12ID = LaneServiceWithDao.create(Seq(subLane12), Set(oldLinkID), SideCode.TowardsDigitizing.value, testUserName).head

      // Cut additional lane against digitizing
      val subLane22 = NewLane(0, 15.50, 120.65, 49, isExpired = false, isDeleted = false, subLane2PropertiesA)
      val sublane22ID =LaneServiceWithDao.create(Seq(subLane22), Set(oldLinkID), SideCode.AgainstDigitizing.value, testUserName).head

      // Verify lanes are created
      val existingLanes = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(oldLinkID))
      existingLanes.size should equal (4)

      sqlu"""UPDATE LANE
          SET CREATED_BY = 'testCreator', CREATED_DATE = to_timestamp('2021-05-10T10:52:28.783Z', 'YYYY-MM-DD"T"HH24:MI:SS.FF3Z'),
              MODIFIED_BY = 'testModifier', MODIFIED_DATE = to_timestamp('2022-05-10T10:52:28.783Z', 'YYYY-MM-DD"T"HH24:MI:SS.FF3Z')
          WHERE id in (${mainLane11ID}, ${mainLane21ID}, ${sublane12ID}, ${sublane22ID})
      """.execute

      // Apply Changes
      val changeSet = LaneUpdater.handleChanges(relevantChange)
      LaneUpdater.updateSamuutusChangeSet(changeSet, relevantChange)

      // All 3 lanes should be split between two new links, main lane measures should equal new link length
      val lanesAfterChanges = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(newLinkID1, newLinkID2)).sortBy(lane => (lane.laneCode, lane.sideCode))

      lanesAfterChanges.size should equal (7)
      lanesAfterChanges.count(_.linkId == newLinkID1) should equal (4)
      lanesAfterChanges.count(_.linkId == newLinkID2) should equal (3)

      // Verify that original creation and modification data is preserved
      lanesAfterChanges.forall(_.createdBy.get == "testCreator") should be(true)
      lanesAfterChanges.forall(_.createdDateTime.get.toString().startsWith("2021-05-10")) should be(true)
      lanesAfterChanges.forall(_.modifiedBy.get == "testModifier") should be(true)
      lanesAfterChanges.forall(_.modifiedDateTime.get.toString().startsWith("2022-05-10")) should be(true)

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
      additionalLanesAfterChanges.count(additionalLane => additionalLane.sideCode == AgainstDigitizing.value) should equal(1)
      additionalLanesAfterChanges.count(additionalLane => additionalLane.sideCode == TowardsDigitizing.value) should equal(2)
      additionalLanesAfterChanges.count(additionalLane => additionalLane.linkId == newLinkID1) should equal(2)
      additionalLanesAfterChanges.count(additionalLane => additionalLane.linkId == newLinkID2) should equal(1)
      // Original lane 12 should be divided between two new links
      val originalAdditionalLane12 = existingLanes.find(_.id == sublane12ID).get
      val originalAdditionalLane12Length = originalAdditionalLane12.endMeasure - originalAdditionalLane12.startMeasure
      val dividedLanes = additionalLanesAfterChanges.filter(lane => lane.sideCode == TowardsDigitizing.value)
      val dividedLaneLengths = dividedLanes.map(additionalLane => additionalLane.endMeasure - additionalLane.startMeasure)
      val dividedLanesTotalLength = dividedLaneLengths.foldLeft(0.0)((length1 ,length2) => length1 + length2)
      val lengthDifferenceAfterChange = Math.abs(originalAdditionalLane12Length - dividedLanesTotalLength)
      (lengthDifferenceAfterChange < measureTolerance) should equal (true)
      // Original lane 22 measures should stay the same, just moved to new link
      val originalAdditionalLane22 = existingLanes.find(_.id == sublane22ID).get
      val afterChangeAdditionalLane22 = additionalLanesAfterChanges.find(_.sideCode == AgainstDigitizing.value).get
      afterChangeAdditionalLane22.startMeasure should equal(originalAdditionalLane22.startMeasure)
      afterChangeAdditionalLane22.endMeasure should equal(originalAdditionalLane22.endMeasure)
      afterChangeAdditionalLane22.linkId should equal(newLinkID1)

      //Verify lane history
      val newLaneIds = lanesAfterChanges.map(_.id)
      val oldLaneIds = existingLanes.map(_.id)
      val laneHistory = LaneServiceWithDao.historyDao.getHistoryLanesChangedSince(DateTime.now().minusDays(1), DateTime.now().plusDays(1), withAdjust = true)
      laneHistory.size should equal(7)
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

      val oldLinkHistoryLanes = laneHistoryDao.fetchAllHistoryLanesByLinkIds(Seq(oldLinkID), includeExpired = true)
      oldLinkHistoryLanes.map (lane => lane.expired should equal(true))
      oldLinkHistoryLanes.size should equal(2)
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
      oldLinkHistoryLanes.size should equal(2)
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

      val oldLinkHistoryLanes = laneHistoryDao.fetchAllHistoryLanesByLinkIds(Seq(oldLinkID), includeExpired = true)
      oldLinkHistoryLanes.map (lane => lane.expired should equal(true))
      oldLinkHistoryLanes.size should equal(1)
      oldLinkHistoryLanes.head.newId should equal(lanesAfterChanges.head.id)
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
        val (propertiesToUse, dateStringToUse) = oldLink.linkId match {
          case linkId if linkId == oldLinkId1 => (mainLaneLanePropertiesA, "2017-05-10T10:52:28.783Z")
          case linkId if linkId == oldLinkId2 => (mainLaneLanePropertiesB, "2020-05-10T10:52:28.783Z")
          case linkId if linkId == oldLinkId3 => (mainLaneLanePropertiesC, "2021-05-10T10:52:28.783Z")
          case linkId if linkId == oldLinkId4 => (mainLaneLanePropertiesD, "2018-05-10T10:52:28.783Z")
        }
        val oldLinkLengthRounded = LaneUtils.roundMeasure(oldLink.linkLength)
        val mainLane11 = NewLane(0, 0.0, oldLinkLengthRounded, 49, isExpired = false, isDeleted = false, propertiesToUse)
        val mainLane11ID = LaneServiceWithDao.create(Seq(mainLane11), Set(oldLink.linkId), SideCode.TowardsDigitizing.value, testUserName).head
        val mainLane21 = NewLane(0, 0.0, oldLinkLengthRounded, 49, isExpired = false, isDeleted = false, propertiesToUse)
        val mainLane21ID = LaneServiceWithDao.create(Seq(mainLane21), Set(oldLink.linkId), SideCode.AgainstDigitizing.value, testUserName).head
        sqlu"""UPDATE LANE
          SET CREATED_BY = 'testCreator', CREATED_DATE = to_timestamp(${dateStringToUse}, 'YYYY-MM-DD"T"HH24:MI:SS.FF3Z'),
              MODIFIED_BY = 'testModifier', MODIFIED_DATE = to_timestamp(${dateStringToUse}, 'YYYY-MM-DD"T"HH24:MI:SS.FF3Z')
          WHERE id in (${mainLane11ID}, ${mainLane21ID})
      """.execute
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

      // Validate main lane changes. New road link should have 2 main lanes fused together from the original main lanes, retaining the latest creation and modification data
      mainLanesAfterChanges.size should equal(2)
      mainLanesAfterChanges.foreach(mainLane => {
        mainLane.startMeasure should equal(0.0)
        mainLane.endMeasure should equal(newRoadLinkLength)
        mainLane.createdBy.get should be("testCreator")
        mainLane.createdDateTime.get.toString().startsWith("2021-05-10") should be(true)
        mainLane.modifiedBy.get should be("testModifier")
        mainLane.modifiedDateTime.get.toString().startsWith("2021-05-10") should be(true)
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

      // Main lane against digitizing
      val mainLane1 = NewLane(0, 0.0, oldLink.linkLength, 49, isExpired = false, isDeleted = false, mainLaneLanePropertiesA)
      LaneServiceWithDao.create(Seq(mainLane1), Set(oldLink.linkId), SideCode.AgainstDigitizing.value, testUserName)

      // Additional lane against digitizing
      val additionalLane2 = NewLane(0, 0.0, oldLink.linkLength, 49, isExpired = false, isDeleted = false, subLane2PropertiesA)
      LaneServiceWithDao.create(Seq(additionalLane2), Set(oldLink.linkId), SideCode.AgainstDigitizing.value, testUserName)

      val currentItems = laneWorkListService.getLaneWorkList()
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

  test("TrafficDirection changed on a split link with the other removed. Check that lane work list update doesn't fail on none.get") {
    runWithRollback {
      val newLinkId = "da1ce256-2f8a-43f9-9008-5bf058c1bcd7:1"
      val relevantChange = testChanges.find(change => change.changeType == Split && change.newLinks.head.linkId == newLinkId).get
      val oldLinkWithAgainstDigitizingTD = Option(relevantChange.oldLink.get.copy(trafficDirection = TrafficDirection.apply(3)))
      val trafficDirectionChange: RoadLinkChange = relevantChange.copy(oldLink = oldLinkWithAgainstDigitizingTD)
      val oldLink = trafficDirectionChange.oldLink.get

      // Main lane against digitizing
      val mainLane1 = NewLane(0, 0.0, oldLink.linkLength, 49, isExpired = false, isDeleted = false, mainLaneLanePropertiesA)
      LaneServiceWithDao.create(Seq(mainLane1), Set(oldLink.linkId), SideCode.AgainstDigitizing.value, testUserName)

      // Additional lane against digitizing
      val additionalLane2 = NewLane(0, 0.0, oldLink.linkLength, 49, isExpired = false, isDeleted = false, subLane2PropertiesA)
      LaneServiceWithDao.create(Seq(additionalLane2), Set(oldLink.linkId), SideCode.AgainstDigitizing.value, testUserName)

      val currentItems = laneWorkListService.getLaneWorkList()
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

  test("Changes come in multiple part." +
                "Old links is split into multiple part and one or more these part are merged into new link " +
                "which other parts come from different old links with Replace change message, Only main lane") {

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

      sorted.size should be(4)

      sorted.filter(_.linkId == linkIdNew1).head.startMeasure should be(0)
      sorted.filter(_.linkId == linkIdNew1).head.endMeasure should be(101.922)

      sorted.filter(_.linkId == linkIdNew2).head.startMeasure should be(0)
      sorted.filter(_.linkId == linkIdNew2).head.endMeasure should be(337.589)

    }
  }

  test("Changes come in multiple part. " +
                "Old links is split into multiple part and one or more these part are merged into new link " +
                "which other parts come from different old links with Replace change message, Additional lanes") {

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

  test("Replace. Given a Road Link that is replaced with a New Link; " +
    "when the New Link has grown outside of Old Link geometry from the beginning; " +
    "then the Main Lanes should grow to be New Link's length") {
    runWithRollback {
      val oldLinkID = "deb91a05-e182-44ae-ad71-4ba169d57e41:1"
      val newLinkID = "0a4cb6e7-67c3-411e-9446-975c53c0d054:1"
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkID).get
      val newRoadLink = roadLinkService.getRoadLinkByLinkId(newLinkID).get
      val relevantChanges = testChanges.filter(change => change.newLinks.map(_.linkId).contains(newLinkID) && change.changeType == RoadLinkChangeType.Replace)

      val mainLaneLink1 = NewLane(0, 0.0, oldRoadLink.length, oldRoadLink.municipalityCode, isExpired = false, isDeleted = false, mainLaneLanePropertiesA)
      LaneServiceWithDao.create(Seq(mainLaneLink1), Set(oldLinkID), SideCode.TowardsDigitizing.value, testUserName)
      val mainLaneLink2 = NewLane(0, 0.0, oldRoadLink.length, oldRoadLink.municipalityCode, isExpired = false, isDeleted = false, mainLaneLanePropertiesB)
      LaneServiceWithDao.create(Seq(mainLaneLink2), Set(oldLinkID), SideCode.AgainstDigitizing.value, testUserName)

      val existingLanes = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(oldLinkID))
      existingLanes.size should equal(2)

      val changeSet = LaneUpdater.handleChanges(relevantChanges)
      LaneUpdater.updateSamuutusChangeSet(changeSet, relevantChanges)

      val lanesAfterChanges = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(newLinkID))
      lanesAfterChanges.size should equal(2)

      val mainLaneAfterChanges1 = lanesAfterChanges.filter(lane => LaneNumber.isMainLane(lane.laneCode)).head
      val mainLaneLength1 = mainLaneAfterChanges1.endMeasure - mainLaneAfterChanges1.startMeasure
      mainLaneLength1 should equal(newRoadLink.length)

      val mainLaneAfterChanges2 = lanesAfterChanges.filter(lane => LaneNumber.isMainLane(lane.laneCode)).last
      val mainLaneLength2 = mainLaneAfterChanges2.endMeasure - mainLaneAfterChanges2.startMeasure
      mainLaneLength2 should equal(newRoadLink.length)
    }
  }

  test("Replace. Given a Road Link that is replaced with a New Link; " +
    "when the New Link has grown outside of Old Link geometry from the end; " +
    "then the Main Lanes should grow to be New Link's length") {
    runWithRollback {
      val oldLinkID = "18ce7a01-0ddc-47a2-9df1-c8e1be193516:1"
      val newLinkID = "016200a1-5dd4-47cc-8f4f-38ab4934eef9:1"
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkID).get
      val newRoadLink = roadLinkService.getRoadLinkByLinkId(newLinkID).get
      val relevantChanges = testChanges.filter(change => change.newLinks.map(_.linkId).contains(newLinkID) && change.changeType == RoadLinkChangeType.Replace)

      val mainLaneLink1 = NewLane(0, 0.0, oldRoadLink.length, oldRoadLink.municipalityCode, isExpired = false, isDeleted = false, mainLaneLanePropertiesA)
      LaneServiceWithDao.create(Seq(mainLaneLink1), Set(oldLinkID), SideCode.TowardsDigitizing.value, testUserName)
      val mainLaneLink2 = NewLane(0, 0.0, oldRoadLink.length, oldRoadLink.municipalityCode, isExpired = false, isDeleted = false, mainLaneLanePropertiesB)
      LaneServiceWithDao.create(Seq(mainLaneLink2), Set(oldLinkID), SideCode.AgainstDigitizing.value, testUserName)

      val existingLanes = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(oldLinkID))
      existingLanes.size should equal(2)

      val changeSet = LaneUpdater.handleChanges(relevantChanges)
      LaneUpdater.updateSamuutusChangeSet(changeSet, relevantChanges)

      val lanesAfterChanges = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(newLinkID))
      lanesAfterChanges.size should equal(2)

      val mainLaneAfterChanges1 = lanesAfterChanges.filter(lane => LaneNumber.isMainLane(lane.laneCode)).head
      val mainLaneLength1 = mainLaneAfterChanges1.endMeasure - mainLaneAfterChanges1.startMeasure
      mainLaneLength1 should equal(newRoadLink.length)

      val mainLaneAfterChanges2 = lanesAfterChanges.filter(lane => LaneNumber.isMainLane(lane.laneCode)).last
      val mainLaneLength2 = mainLaneAfterChanges2.endMeasure - mainLaneAfterChanges2.startMeasure
      mainLaneLength2 should equal(newRoadLink.length)
    }
  }

  test("Replace. Given a Road Link that is replaced with a New Link; " +
    "when the New Link has grown outside of Old Link geometry from the beginning; " +
    "then the Additional Lanes should scale correctly") {
    runWithRollback {
      val oldLinkID = "deb91a05-e182-44ae-ad71-4ba169d57e41:1"
      val newLinkID = "0a4cb6e7-67c3-411e-9446-975c53c0d054:1"
      val oldRoadLink1 = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkID).get
      val relevantChanges = testChanges.filter(change => change.newLinks.map(_.linkId).contains(newLinkID) && change.changeType == RoadLinkChangeType.Replace)

      val subLane1 = NewLane(0, 20, oldRoadLink1.length, oldRoadLink1.municipalityCode, isExpired = false, isDeleted = false, subLane2PropertiesA)
      LaneServiceWithDao.create(Seq(subLane1), Set(oldLinkID), SideCode.TowardsDigitizing.value, testUserName)

      val subLane2 = NewLane(0, 0, 30, oldRoadLink1.municipalityCode, isExpired = false, isDeleted = false, subLane2PropertiesB)
      LaneServiceWithDao.create(Seq(subLane2), Set(oldLinkID), SideCode.TowardsDigitizing.value, testUserName)

      val existingLanes = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(oldLinkID))
      existingLanes.size should equal(2)

      val changeSet = LaneUpdater.handleChanges(relevantChanges)
      LaneUpdater.updateSamuutusChangeSet(changeSet, relevantChanges)

      val lanesAfterChanges = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(newLinkID))
      lanesAfterChanges.size should equal(2)

      lanesAfterChanges.exists(additionalLane => {
        val countedLaneLength = additionalLane.endMeasure - additionalLane.startMeasure
        val approximatedLaneLength = 30
        val diffInLength = Math.abs(countedLaneLength - approximatedLaneLength)
        diffInLength < 2.0
      }) should equal(true)

      lanesAfterChanges.exists(additionalLane => {
        val countedLaneLength = additionalLane.endMeasure - additionalLane.startMeasure
        val approximatedLaneLength = 222.65
        val diffInLength = Math.abs(countedLaneLength - approximatedLaneLength)
        diffInLength < 2.0
      }) should equal(true)
    }
  }

  test("Replace. Given a Road Link that is replaced with a New Link; " +
    "when the New Link has grown outside of Old Link geometry from the end; " +
    "then the Additional Lanes should scale correctly") {
    runWithRollback {
      val oldLinkID = "18ce7a01-0ddc-47a2-9df1-c8e1be193516:1"
      val newLinkID = "016200a1-5dd4-47cc-8f4f-38ab4934eef9:1"
      val oldRoadLink1 = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkID).get
      val relevantChanges = testChanges.filter(change => change.newLinks.map(_.linkId).contains(newLinkID) && change.changeType == RoadLinkChangeType.Replace)

      val subLane1 = NewLane(0, 20, oldRoadLink1.length, oldRoadLink1.municipalityCode, isExpired = false, isDeleted = false, subLane2PropertiesA)
      LaneServiceWithDao.create(Seq(subLane1), Set(oldLinkID), SideCode.TowardsDigitizing.value, testUserName)

      val subLane2 = NewLane(0, 0, 30, oldRoadLink1.municipalityCode, isExpired = false, isDeleted = false, subLane2PropertiesB)
      LaneServiceWithDao.create(Seq(subLane2), Set(oldLinkID), SideCode.TowardsDigitizing.value, testUserName)

      val existingLanes = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(oldLinkID))
      existingLanes.size should equal(2)

      val changeSet = LaneUpdater.handleChanges(relevantChanges)
      LaneUpdater.updateSamuutusChangeSet(changeSet, relevantChanges)

      val lanesAfterChanges = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(newLinkID))
      lanesAfterChanges.size should equal(2)

      lanesAfterChanges.exists(additionalLane => {
        val countedLaneLength = additionalLane.endMeasure - additionalLane.startMeasure
        val approximatedLaneLength = 11.48
        val diffInLength = Math.abs(countedLaneLength - approximatedLaneLength)
        diffInLength < 2.0
      }) should equal(true)

      lanesAfterChanges.exists(additionalLane => {
        val countedLaneLength = additionalLane.endMeasure - additionalLane.startMeasure
        val approximatedLaneLength = 29.48
        val diffInLength = Math.abs(countedLaneLength - approximatedLaneLength)
        diffInLength < 2.0
      }) should equal(true)
    }
  }
  test("Lane split, digitizing direction changes on one new part. On new link, side codes should flip, measures should be calculated to match persisting position") {
    runWithRollback {
      val oldLinkId = "40ebb42a-f551-4eee-89da-dba6d6a379ac:1"
      val newLinkId1 = "9a8f130c-8e4b-492c-bf53-63ddf5b7ff9c:1"
      val newLinkId2 = "9a77a504-f7d8-4a05-a9ac-6a60aa452dae:1"

      val relevantChange = testChanges.filter(change => change.changeType == RoadLinkChangeType.Split && change.oldLink.get.linkId == oldLinkId)
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId).get
      val newLinks = roadLinkService.getRoadLinksByLinkIds(Set(newLinkId1, newLinkId2), newTransaction = false)

      val mainLane1 = NewLane(0, 0.0, oldRoadLink.length, 286, isExpired = false, isDeleted = false, mainLaneLanePropertiesA)
      LaneServiceWithDao.create(Seq(mainLane1), Set(oldRoadLink.linkId), SideCode.TowardsDigitizing.value, testUserName)

      val additionalLane2 = NewLane(0, 0.0, oldRoadLink.length, 286, isExpired = false, isDeleted = false, subLane2PropertiesA)
      LaneServiceWithDao.create(Seq(additionalLane2), Set(oldRoadLink.linkId), SideCode.TowardsDigitizing.value, testUserName)

      val additionalLane3 = NewLane(0, 192.65, 195.15, 286, isExpired = false, isDeleted = false, subLane3Properties)
      LaneServiceWithDao.create(Seq(additionalLane3), Set(oldRoadLink.linkId), SideCode.TowardsDigitizing.value, testUserName)

      val lanesBefore = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(oldLinkId))
      lanesBefore.size should be(3)

      val changeSet = LaneUpdater.handleChanges(relevantChange)
      LaneUpdater.updateSamuutusChangeSet(changeSet, relevantChange)

      val lanesOnOldLinkAfterChanges = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(oldLinkId))
      lanesOnOldLinkAfterChanges.size should equal(0)
      val oldLinkHistoryLanes = laneHistoryDao.fetchAllHistoryLanesByLinkIds(Seq(oldLinkId), includeExpired = true)
      // Split lanes create one history row for each new lane created from old one
      oldLinkHistoryLanes.size should equal(5)
      oldLinkHistoryLanes.map (lane => lane.expired should equal(true))

      val lanesAfterChanges = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(newLinkId1, newLinkId2))
      lanesAfterChanges.size should equal(5)

      val mainLanesAfterChanges = lanesAfterChanges.filter(_.laneCode == 1)
      mainLanesAfterChanges.size should equal(2)
      mainLanesAfterChanges.foreach(ml => {
        val newLink = newLinks.find(_.linkId == ml.linkId).get
        ml.startMeasure should equal(0.0)
        ml.endMeasure should equal(newLink.length)
      })

      val subLane2AfterChanges = lanesAfterChanges.filter(_.laneCode == 2)
      subLane2AfterChanges.size should equal(2)
      subLane2AfterChanges.foreach(lane => {
        val newLink = newLinks.find(_.linkId == lane.linkId).get
        lane.startMeasure should equal(0.0)
        lane.endMeasure should equal(newLink.length)
      })

      val subLane3AfterChanges = lanesAfterChanges.filter(_.laneCode == 3)
      subLane3AfterChanges.size should equal(1)
      val subLane3LengthOriginal = additionalLane3.endMeasure - additionalLane3.startMeasure
      val subLane3LengthAfterChanges = subLane3AfterChanges.head.endMeasure - subLane3AfterChanges.head.startMeasure
      Math.abs(subLane3LengthOriginal - subLane3LengthAfterChanges) < measureTolerance should equal(true)

    }
  }


  test("Lane split, digitizing direction changes on one new part.") {
    runWithRollback {
      val oldLinkId = "78ad826c-a5e3-47d8-af97-7a0dd7e95834:1"
      val newLinkId1 = "7819862b-2af8-47f0-a651-337c62bac745:1"
      val newLinkId2 = "c2c4e300-2777-45de-8345-51c302e76aeb:1"

      val relevantChange = testChanges.filter(change => change.changeType == RoadLinkChangeType.Split && change.oldLink.get.linkId == oldLinkId)
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId).get
      val newLinks = roadLinkService.getRoadLinksByLinkIds(Set(newLinkId1, newLinkId2), newTransaction = false)

      val mainLane1 = NewLane(0, 0.0, oldRoadLink.length, 286, isExpired = false, isDeleted = false, mainLaneLanePropertiesA)
      LaneServiceWithDao.create(Seq(mainLane1), Set(oldRoadLink.linkId), SideCode.TowardsDigitizing.value, testUserName)

      val additionalLane2 = NewLane(0, 0.0, oldRoadLink.length, 286, isExpired = false, isDeleted = false, subLane2PropertiesA)
      LaneServiceWithDao.create(Seq(additionalLane2), Set(oldRoadLink.linkId), SideCode.TowardsDigitizing.value, testUserName)

      val additionalLane3 = NewLane(0, 150.65, 752.50, 286, isExpired = false, isDeleted = false, subLane3Properties)
      LaneServiceWithDao.create(Seq(additionalLane3), Set(oldRoadLink.linkId), SideCode.TowardsDigitizing.value, testUserName)

      val lanesBefore = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(oldLinkId))
      lanesBefore.size should be(3)

      val changeSet = LaneUpdater.handleChanges(relevantChange)
      LaneUpdater.updateSamuutusChangeSet(changeSet, relevantChange)

      val lanesOnOldLinkAfterChanges = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(oldLinkId))
      lanesOnOldLinkAfterChanges.size should equal(0)
      val oldLinkHistoryLanes = laneHistoryDao.fetchAllHistoryLanesByLinkIds(Seq(oldLinkId), includeExpired = true)
      // Split lanes create one history row for each new lane created from old one
      oldLinkHistoryLanes.size should equal(5)
      oldLinkHistoryLanes.map (lane => lane.expired should equal(true))

      val lanesAfterChanges = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(newLinkId1, newLinkId2))
      lanesAfterChanges.size should equal(5)

      val mainLanesAfterChanges = lanesAfterChanges.filter(_.laneCode == 1)
      mainLanesAfterChanges.size should equal(2)
      mainLanesAfterChanges.foreach(ml => {
        val newLink = newLinks.find(_.linkId == ml.linkId).get
        ml.startMeasure should equal(0.0)
        ml.endMeasure should equal(newLink.length)
      })

      val subLane2AfterChanges = lanesAfterChanges.filter(_.laneCode == 2)
      subLane2AfterChanges.size should equal(2)
      subLane2AfterChanges.foreach(lane => {
        val newLink = newLinks.find(_.linkId == lane.linkId).get
        lane.startMeasure should equal(0.0)
        lane.endMeasure should equal(newLink.length)
      })

      val subLane3AfterChanges = lanesAfterChanges.filter(_.laneCode == 3)
      subLane3AfterChanges.size should equal(1)
      val subLane3LengthOriginal = additionalLane3.endMeasure - additionalLane3.startMeasure
      val subLane3LengthAfterChanges = subLane3AfterChanges.head.endMeasure - subLane3AfterChanges.head.startMeasure
      Math.abs(subLane3LengthOriginal - subLane3LengthAfterChanges) < measureTolerance should equal(true)


    }
  }
}

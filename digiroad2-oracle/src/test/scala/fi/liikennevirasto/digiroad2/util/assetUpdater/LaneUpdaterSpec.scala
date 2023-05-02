package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset.{SideCode, TrafficDirection}
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.client.RoadLinkChangeType.Replace
import fi.liikennevirasto.digiroad2.client.{RoadLinkChange, RoadLinkChangeClient, RoadLinkChangeType, RoadLinkClient}
import fi.liikennevirasto.digiroad2.dao.MunicipalityDao
import fi.liikennevirasto.digiroad2.dao.lane.{LaneDao, LaneHistoryDao}
import fi.liikennevirasto.digiroad2.lane.LaneNumber.MainLane
import fi.liikennevirasto.digiroad2.lane._
import fi.liikennevirasto.digiroad2.service.lane.{LaneService, LaneWorkListService}
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.util.{PolygonTools, TestTransactions}
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

  def debugPrint(lanesAfterChanges: Seq[PersistedLane], existingLanes: Seq[PersistedLane], relevantChange: Seq[RoadLinkChange]): Unit = {
    lanesAfterChanges.foreach(lane => {
      val newLink = relevantChange.head.newLinks.find(_.linkId == lane.linkId).get
      val oldLink = relevantChange.head.oldLink.get
      val parentLane = existingLanes.find(pl => pl.laneCode == lane.laneCode && pl.sideCode == lane.sideCode).get

      val newLaneLength = lane.endMeasure - lane.startMeasure
      val oldLaneLength = parentLane.endMeasure - parentLane.startMeasure
      println("New ID: " + lane.id)
      println("Old ID: " + parentLane.id)
      println("LaneCode: " + lane.laneCode)
      println("New LinkId: " + lane.linkId)
      println("Old LinkId: " + parentLane.linkId)
      println("New Link Length: " + newLink.linkLength)
      println("Old Link Length: " + oldLink.linkLength)
      println("New Lane Length: " + newLaneLength)
      println("Old Lane Length: " + oldLaneLength)
      println("New Fill percentage: " + ((newLaneLength / newLink.linkLength) * 100) + "%")
      println("Old Fill percentage: " + ((oldLaneLength / oldLink.linkLength) * 100) + "%")
      println("SideCode: " + lane.sideCode)
      println("New StartM: " + lane.startMeasure)
      println("Old StartM: " + parentLane.startMeasure)
      println("New EndM: " + lane.endMeasure)
      println("Old EndM: " + parentLane.endMeasure)
      println("-----------------------------------")
    })
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

  val subLane2Properties = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(2))),
    LaneProperty("lane_type", Seq(LanePropertyValue("2"))),
    LaneProperty("start_date", Seq(LanePropertyValue("1.1.1970")))
  )

  test("Remove. 2 Road Links Removed, move all lanes on links to history") {
    // 2 road links removed
    val relevantChanges = testChanges.filter(_.changeType == RoadLinkChangeType.Remove)
    val oldLinkIds = relevantChanges.map(_.oldLink.get.linkId)
    runWithRollback {
      // Lanes on link 7766bff4-5f02-4c30-af0b-42ad3c0296aa:1
      val mainLaneLink1 = NewLane(0, 0.0, 30.92807173, 49, isExpired = false, isDeleted = false, mainLaneLanePropertiesA)
      LaneServiceWithDao.create(Seq(mainLaneLink1), Set("7766bff4-5f02-4c30-af0b-42ad3c0296aa:1"), SideCode.TowardsDigitizing.value, testUserName)
      val subLane2Link1 = NewLane(0, 0.0, 30.92807173, 49, isExpired = false, isDeleted = false, subLane2Properties)
      LaneServiceWithDao.create(Seq(subLane2Link1), Set("7766bff4-5f02-4c30-af0b-42ad3c0296aa:1"), SideCode.TowardsDigitizing.value, testUserName)

      // Lanes on link 78e1984d-7dbc-4a7c-bd91-cb68f82ffccb:1
      val mainLaneLink2 = NewLane(0, 0.0, 55.71735255, 49, isExpired = false, isDeleted = false, mainLaneLanePropertiesA)
      LaneServiceWithDao.create(Seq(mainLaneLink2), Set("78e1984d-7dbc-4a7c-bd91-cb68f82ffccb:1"), SideCode.TowardsDigitizing.value, testUserName)
      val subLane2Link2 = NewLane(0, 0.0, 55.71735255, 49, isExpired = false, isDeleted = false, subLane2Properties)
      LaneServiceWithDao.create(Seq(subLane2Link2), Set("78e1984d-7dbc-4a7c-bd91-cb68f82ffccb:1"), SideCode.TowardsDigitizing.value, testUserName)


      val existingLanes = LaneServiceWithDao.fetchExistingLanesByLinkIds(oldLinkIds)
      existingLanes.size should equal(4)
      val changeSet = LaneUpdater.handleChanges(relevantChanges)
      LaneUpdater.updateChangeSet(changeSet)

      val existingLanesAfterChanges = LaneServiceWithDao.fetchExistingLanesByLinkIds(oldLinkIds)
      existingLanesAfterChanges.size should equal(0)

      val historyLanes = LaneServiceWithDao.historyDao.fetchAllHistoryLanesByLinkIds(oldLinkIds, includeExpired = true)
      historyLanes.size should equal(4)
      historyLanes.foreach(lane => lane.expired should equal(true))
    }
  }

  test ("Add. 2 Road Link added, generate correct main lanes") {
    // 2 road links Added
    val relevantChanges = testChanges.filter(_.changeType == RoadLinkChangeType.Add)
    val newLinkIds = relevantChanges.flatMap(_.newLinks.map(_.linkId))

    runWithRollback {
      val existingLanesOnNewLinks = LaneServiceWithDao.fetchExistingLanesByLinkIds(newLinkIds)
      val newRoadLinks = roadLinkService.getRoadLinksByLinkIds(newLinkIds.toSet, newTransaction = false)
      existingLanesOnNewLinks.size should equal(0)

      val changeSet = LaneUpdater.handleChanges(relevantChanges)
      LaneUpdater.updateChangeSet(changeSet)

      val createdLanesOnNewLinks = LaneServiceWithDao.fetchExistingLanesByLinkIds(newLinkIds)
      createdLanesOnNewLinks.size should equal(4)
      createdLanesOnNewLinks.count(_.linkId == "f2eba575-f306-4c37-b49d-a4d27a3fc049:1") should equal(2)
      createdLanesOnNewLinks.count(_.linkId == "a15cf59b-c17c-4b6d-8e9b-a558143d0d47:1") should equal(2)

      createdLanesOnNewLinks.foreach(lane => {
        val roadLink = newRoadLinks.find(_.linkId == lane.linkId).get
        lane.startMeasure should equal(0)
        lane.endMeasure should equal(Math.round(roadLink.length * 1000).toDouble / 1000)
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
      val subLane12 = NewLane(0, 0.0, additionalLaneEndMeasure, 49, isExpired = false, isDeleted = false, subLane2Properties)
      LaneServiceWithDao.create(Seq(subLane12), Set(oldLinkId), SideCode.TowardsDigitizing.value, testUserName)

      // Verify lanes are created
      val existingLanes = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(oldLinkId))
      existingLanes.size should equal(3)

      // Apply Changes
      val changeSet = LaneUpdater.handleChanges(relevantChange)
      LaneUpdater.updateChangeSet(changeSet)

      // Same amount of lanes should persist
      val lanesAfterChanges = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(newLinkId))
      lanesAfterChanges.size should equal(3)

      // MainLanes should extend to cover whole of new road link
      val mainLanesAfterChanges = lanesAfterChanges.filter(_.laneCode == MainLane.oneDigitLaneCode)
      mainLanesAfterChanges.foreach(mainLane => {
        mainLane.startMeasure should equal(0.0)
        mainLane.endMeasure should equal(Math.round(newRoadLink.length * 1000).toDouble / 1000)
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
      val subLane12 = NewLane(0, 85.0, 215.0, 49, isExpired = false, isDeleted = false, subLane2Properties)
      LaneServiceWithDao.create(Seq(subLane12), Set(oldLinkID), SideCode.TowardsDigitizing.value, testUserName)

      // Verify lanes are created
      val existingLanes = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(oldLinkID))
      existingLanes.size should equal (3)

      // Apply Changes
      val changeSet = LaneUpdater.handleChanges(relevantChange)
      LaneUpdater.updateChangeSet(changeSet)

      // All 3 lanes should be split between two new links, main lane measures should equal new link length
      val lanesAfterChanges = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(newLinkID1, newLinkID2)).sortBy(lane => (lane.laneCode, lane.sideCode))

      debugPrint(lanesAfterChanges, existingLanes, relevantChange)

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
        mainLane.endMeasure should equal (Math.round(newRoadLink.linkLength * 1000).toDouble / 1000)
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
        val oldLinkLengthRounded = Math.round(oldLink.linkLength * 1000).toDouble / 1000
        val mainLane11 = NewLane(0, 0.0, oldLinkLengthRounded, 49, isExpired = false, isDeleted = false, propertiesToUse)
        LaneServiceWithDao.create(Seq(mainLane11), Set(oldLink.linkId), SideCode.TowardsDigitizing.value, testUserName)
        val mainLane21 = NewLane(0, 0.0, oldLinkLengthRounded, 49, isExpired = false, isDeleted = false, propertiesToUse)
        LaneServiceWithDao.create(Seq(mainLane21), Set(oldLink.linkId), SideCode.AgainstDigitizing.value, testUserName)
      })

      // Cut additional lane against digitizing
      val subLane12a = NewLane(0, 0.0, 91.5, 49, isExpired = false, isDeleted = false, subLane2Properties)
      LaneServiceWithDao.create(Seq(subLane12a), Set(oldLinkId1), SideCode.AgainstDigitizing.value, testUserName)

      // Cut additional lane towards digitizing, ending at the end of link
      val subLane12b = NewLane(0, 80.5, oldLink1Length, 49, isExpired = false, isDeleted = false, subLane2Properties)
      LaneServiceWithDao.create(Seq(subLane12b), Set(oldLinkId1), SideCode.TowardsDigitizing.value, testUserName)

      // Cut additional lane towards digitizing, starting from start of link
      val subLane12c = NewLane(0, 0.0, 21.7, 49, isExpired = false, isDeleted = false, subLane2Properties)
      LaneServiceWithDao.create(Seq(subLane12c), Set(oldLinkId2), SideCode.TowardsDigitizing.value, testUserName)

      // Verify lanes are created
      val existingLanes = LaneServiceWithDao.fetchExistingLanesByLinkIds(oldLinks.map(_.linkId))
      existingLanes.size should equal (11)

      // Apply changes to lanes
      val changeSet = LaneUpdater.handleChanges(relevantChanges)
      LaneUpdater.updateChangeSet(changeSet)

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
      val additionalLane2 = NewLane(0, 0.0, oldLink.linkLength, 49, isExpired = false, isDeleted = false, subLane2Properties)
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
      LaneUpdater.updateChangeSet(changeSet)

      val lanesOnOldLinkAfter = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(oldLink.linkId))
      // Additional lane should stay on old link
      lanesOnOldLinkAfter.size should equal(1)

      val lanesOnNewLinkAfter = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(newLinkId))
      // Two main lanes should be generated for new link
      lanesOnNewLinkAfter.size should equal(2)
      lanesOnNewLinkAfter.foreach(lane => LaneNumber.isMainLane(lane.laneCode) should equal(true))

    }
  }

}

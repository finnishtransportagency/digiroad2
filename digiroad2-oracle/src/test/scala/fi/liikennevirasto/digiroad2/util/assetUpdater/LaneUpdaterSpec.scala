package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummyEventBus, DummySerializer}
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.client.RoadLinkChangeType.{Remove, Replace}
import fi.liikennevirasto.digiroad2.client.{RoadLinkChange, RoadLinkChangeClient, RoadLinkChangeType, RoadLinkClient, VKMClient}
import fi.liikennevirasto.digiroad2.dao.MunicipalityDao
import fi.liikennevirasto.digiroad2.dao.lane.{LaneDao, LaneHistoryDao}
import fi.liikennevirasto.digiroad2.lane.LaneNumber.MainLane
import fi.liikennevirasto.digiroad2.lane.{LaneChangeType, LaneProperty, LanePropertyValue, NewLane, PersistedLane}
import fi.liikennevirasto.digiroad2.service.lane.LaneService
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.util.{PolygonTools, TestTransactions}
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

  val mainLaneLaneProperties = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(1))),
    LaneProperty("lane_type", Seq(LanePropertyValue("1")))
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
      val mainLaneLink1 = NewLane(0, 0.0, 30.92807173, 49, isExpired = false, isDeleted = false, mainLaneLaneProperties)
      LaneServiceWithDao.create(Seq(mainLaneLink1), Set("7766bff4-5f02-4c30-af0b-42ad3c0296aa:1"), SideCode.TowardsDigitizing.value, testUserName)
      val subLane2Link1 = NewLane(0, 0.0, 30.92807173, 49, isExpired = false, isDeleted = false, subLane2Properties)
      LaneServiceWithDao.create(Seq(subLane2Link1), Set("7766bff4-5f02-4c30-af0b-42ad3c0296aa:1"), SideCode.TowardsDigitizing.value, testUserName)

      // Lanes on link 78e1984d-7dbc-4a7c-bd91-cb68f82ffccb:1
      val mainLaneLink2 = NewLane(0, 0.0, 55.71735255, 49, isExpired = false, isDeleted = false, mainLaneLaneProperties)
      LaneServiceWithDao.create(Seq(mainLaneLink2), Set("78e1984d-7dbc-4a7c-bd91-cb68f82ffccb:1"), SideCode.TowardsDigitizing.value, testUserName)
      val subLane2Link2 = NewLane(0, 0.0, 55.71735255, 49, isExpired = false, isDeleted = false, subLane2Properties)
      LaneServiceWithDao.create(Seq(subLane2Link2), Set("78e1984d-7dbc-4a7c-bd91-cb68f82ffccb:1"), SideCode.TowardsDigitizing.value, testUserName)


      val existingLanes = LaneServiceWithDao.fetchExistingLanesByLinkIds(oldLinkIds)
      existingLanes.size should equal(4)
      LaneUpdater.handleChanges(relevantChanges)

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

      LaneUpdater.handleChanges(relevantChanges)

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
      val mainLane11 = NewLane(0, 0.0, 36.18939714, 49, isExpired = false, isDeleted = false, mainLaneLaneProperties)
      LaneServiceWithDao.create(Seq(mainLane11), Set(oldLinkId), SideCode.TowardsDigitizing.value, testUserName)

      // Main lane against digitizing
      val mainLane21 = NewLane(0, 0.0, 36.18939714, 49, isExpired = false, isDeleted = false, mainLaneLaneProperties)
      LaneServiceWithDao.create(Seq(mainLane21), Set(oldLinkId), SideCode.AgainstDigitizing.value, testUserName)

      // Cut additional lane towards digitizing
      val additionalLaneEndMeasure = 26.359
      val subLane12 = NewLane(0, 0.0, additionalLaneEndMeasure, 49, isExpired = false, isDeleted = false, subLane2Properties)
      LaneServiceWithDao.create(Seq(subLane12), Set(oldLinkId), SideCode.TowardsDigitizing.value, testUserName)

      // Verify lanes are created
      val existingLanes = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(oldLinkId))
      existingLanes.size should equal(3)

      // Apply Changes
      LaneUpdater.handleChanges(relevantChange)

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
        historyLane.expired should equal (false)
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
      val mainLane11 = NewLane(0, 0.0, 432.23526228, 49, isExpired = false, isDeleted = false, mainLaneLaneProperties)
      LaneServiceWithDao.create(Seq(mainLane11), Set(oldLinkID), SideCode.TowardsDigitizing.value, testUserName)

      // Main lane against digitizing
      val mainLane21 = NewLane(0, 0.0, 432.23526228, 49, isExpired = false, isDeleted = false, mainLaneLaneProperties)
      LaneServiceWithDao.create(Seq(mainLane21), Set(oldLinkID), SideCode.AgainstDigitizing.value, testUserName)

      // Cut additional lane towards digitizing
      val subLane12 = NewLane(0, 85.0, 215.0, 49, isExpired = false, isDeleted = false, subLane2Properties)
      LaneServiceWithDao.create(Seq(subLane12), Set(oldLinkID), SideCode.TowardsDigitizing.value, testUserName)

      // Verify lanes are created
      val existingLanes = LaneServiceWithDao.fetchExistingLanesByLinkIds(Seq(oldLinkID))
      existingLanes.size should equal (3)

      // Apply Changes
      LaneUpdater.handleChanges(relevantChange)

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

}

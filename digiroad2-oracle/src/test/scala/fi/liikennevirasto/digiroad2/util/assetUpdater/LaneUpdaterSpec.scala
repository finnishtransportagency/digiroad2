package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummyEventBus, DummySerializer}
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.asset.SideCode.TowardsDigitizing
import fi.liikennevirasto.digiroad2.client.RoadLinkChangeType.Remove
import fi.liikennevirasto.digiroad2.client.{RoadLinkChange, RoadLinkChangeClient, RoadLinkChangeType, RoadLinkClient, VKMClient}
import fi.liikennevirasto.digiroad2.dao.MunicipalityDao
import fi.liikennevirasto.digiroad2.dao.lane.{LaneDao, LaneHistoryDao}
import fi.liikennevirasto.digiroad2.lane.{LaneProperty, LanePropertyValue, NewLane, PersistedLane}
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


  val laneUpdater = new LaneUpdater(mockRoadLinkChangeClient, roadLinkService, LaneServiceWithDao)

  val roadLinkChangeClient = new RoadLinkChangeClient
  val filePath: String = getClass.getClassLoader.getResource("smallChangeSet.json").getPath
  val jsonFile: String = Source.fromFile(filePath).mkString
  val testChanges: Seq[RoadLinkChange] = roadLinkChangeClient.convertToRoadLinkChange(jsonFile)

  val testUserName = "LaneUpdaterTest"

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  def generateTestLane(laneCode: Int, linkId: String, sideCode: SideCode, startM: Double, endM: Double, attributes: Seq[LaneProperty] = Seq()): PersistedLane = {
    PersistedLane(0, linkId = linkId, sideCode = sideCode.value, laneCode = laneCode, municipalityCode = 49,
      startMeasure = startM, endMeasure = endM, createdBy = Some(testUserName), createdDateTime = None, modifiedBy = None,
      modifiedDateTime = None, expiredBy = None, expiredDateTime = None, expired = false, timeStamp = DateTime.now().minusMonths(6).getMillis,
      geomModifiedDate = None, attributes = attributes)
  }

  val mainLaneLaneProperties = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(1))),
    LaneProperty("lane_type", Seq(LanePropertyValue("1")))
  )

  val subLane2Properties = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(1))),
    LaneProperty("lane_type", Seq(LanePropertyValue("1"))),
    LaneProperty("start_date", Seq(LanePropertyValue("1.1.1970")))
  )

  test("2 Road Links Removed, move all lanes on links to history") {
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
      laneUpdater.handleChanges(relevantChanges)

      val existingLanesAfterChanges = LaneServiceWithDao.fetchExistingLanesByLinkIds(oldLinkIds)
      existingLanesAfterChanges.size should equal(0)

      val historyLanes = LaneServiceWithDao.historyDao.fetchAllHistoryLanesByLinkIds(oldLinkIds, includeExpired = true)
      historyLanes.size should equal(4)
      historyLanes.foreach(lane => lane.expired should equal(true))
    }
  }

  test ("2 Road Link added, generate correct main lanes") {
    // 2 road links Added
    val relevantChanges = testChanges.filter(_.changeType == RoadLinkChangeType.Add)
    val newLinkIds = relevantChanges.flatMap(_.newLinks.map(_.linkId))

    runWithRollback {
      val existingLanesOnNewLinks = LaneServiceWithDao.fetchExistingLanesByLinkIds(newLinkIds)
      val newRoadLinks = roadLinkService.getRoadLinksByLinkIds(newLinkIds.toSet)
      existingLanesOnNewLinks.size should equal(0)

      laneUpdater.handleChanges(relevantChanges)

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

}

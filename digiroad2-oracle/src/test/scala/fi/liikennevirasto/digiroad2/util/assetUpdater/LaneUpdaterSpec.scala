package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.DummyEventBus
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.asset.SideCode.TowardsDigitizing
import fi.liikennevirasto.digiroad2.client.RoadLinkChangeType.Remove
import fi.liikennevirasto.digiroad2.client.{RoadLinkChange, RoadLinkChangeClient}
import fi.liikennevirasto.digiroad2.lane.{LaneProperty, PersistedLane}
import fi.liikennevirasto.digiroad2.service.lane.LaneService
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.joda.time.DateTime
import org.scalatest.mockito.MockitoSugar.mock
import org.scalatest.{FunSuite, Matchers}

import scala.io.Source

class LaneUpdaterSpec extends FunSuite with Matchers{
  val mockRoadLinkChangeClient: RoadLinkChangeClient = mock[RoadLinkChangeClient]
  val mockRoadLinkService: RoadLinkService = mock[RoadLinkService]
  val mockRoadAddressService: RoadAddressService = mock[RoadAddressService]

  val laneService: LaneService = new LaneService(mockRoadLinkService, new DummyEventBus, mockRoadAddressService)
  val laneUpdater = new LaneUpdater(mockRoadLinkChangeClient, mockRoadLinkService, laneService)

  val roadLinkChangeClient = new RoadLinkChangeClient
  val filePath: String = getClass.getClassLoader.getResource("smallChangeSet.json").getPath
  val jsonFile: String = Source.fromFile(filePath).mkString
  val testChanges: Seq[RoadLinkChange] = roadLinkChangeClient.convertToRoadLinkChange(jsonFile)

  val testUserName = "LaneUpdaterTest"

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)
  def generateTestLane(laneCode: Int, linkId: String, sideCode: SideCode, startM: Double, endM: Double, attributes: Seq[LaneProperty] = Seq()): PersistedLane = {
    PersistedLane(0, linkId = linkId, sideCode = sideCode.value, laneCode = laneCode, municipalityCode = 49,
      startMeasure = startM, endMeasure = endM, createdBy = Some(testUserName), createdDateTime = None, modifiedBy = None,
      modifiedDateTime = None, expiredBy = None, expiredDateTime = None, expired = false, timeStamp = DateTime.now().getMillis,
      geomModifiedDate = None, attributes = attributes)
  }

  test("Road Link Removed, move all lanes to history") {
    // 2 road links removed
    val relevantChanges = testChanges.filter(_.changeType == Remove)
    runWithRollback {
      val mainLaneLink1 = generateTestLane(1, "7766bff4-5f02-4c30-af0b-42ad3c0296aa:1", TowardsDigitizing, 0.0, 30.92807173)
      val subLaneLink1 = generateTestLane(2, "7766bff4-5f02-4c30-af0b-42ad3c0296aa:1", TowardsDigitizing, 0.0, 30.92807173)

      val mainLaneLink2 = generateTestLane(1, "78e1984d-7dbc-4a7c-bd91-cb68f82ffccb:1", TowardsDigitizing, 0.0, 55.71735255)
      val subLaneLink2 = generateTestLane(3, "78e1984d-7dbc-4a7c-bd91-cb68f82ffccb:1", TowardsDigitizing, 0.0, 55.71735255)

      val mainLaneLink1Id = laneService.createWithoutTransaction(mainLaneLink1, testUserName)
      val subLaneLink1Id = laneService.createWithoutTransaction(subLaneLink1, testUserName)

      val mainLaneLink2Id = laneService.createWithoutTransaction(mainLaneLink2, testUserName)
      val subLaneLink2Id = laneService.createWithoutTransaction(subLaneLink2, testUserName)

      val createdLanes = laneService.getPersistedLanesByIds(Set(mainLaneLink1Id, subLaneLink1Id, mainLaneLink2Id, subLaneLink2Id), false)
      laneUpdater.handleChanges(relevantChanges)
    }

  }

}

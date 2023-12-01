package fi.liikennevirasto.digiroad2.service.lane

import fi.liikennevirasto.digiroad2.DummyEventBus
import fi.liikennevirasto.digiroad2.asset.TrafficDirection.{AgainstDigitizing, BothDirections, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{FeatureClass, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.dao.lane.LaneWorkListDAO
import fi.liikennevirasto.digiroad2.service.{LinkProperties, LinkPropertyChange, RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class LaneWorkListServiceSpec extends FunSuite with Matchers {
  val laneWorkListDao: LaneWorkListDAO = new LaneWorkListDAO
  val laneWorkListService: LaneWorkListService = new LaneWorkListService

  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockRoadAddressService = MockitoSugar.mock[RoadAddressService]
  val laneService: LaneService = new LaneService(mockRoadLinkService, new DummyEventBus, mockRoadAddressService)
  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  val user: String = "testuser"


  test("traffic direction change with NO already existing overridden value should be identified and saved to lane work list") {
    val linkProperty = LinkProperties("1l", 5, PedestrianZone, BothDirections, Private)
    val fetchedRoadLink = RoadLinkFetched("1l", 91, Nil, Municipality, TowardsDigitizing, FeatureClass.AllOthers)
    runWithRollback {
      val itemsBeforeOperation = laneWorkListDao.getAllItems
      laneService.processRoadLinkPropertyChange(LinkPropertyChange("traffic_direction", None, linkProperty, fetchedRoadLink, Some(user)), newTransaction = false)
      val itemsAfterOperation = laneWorkListDao.getAllItems

      itemsBeforeOperation.size should equal(0)
      itemsAfterOperation.size should equal(1)

      itemsAfterOperation.head.propertyName should equal("traffic_direction")
      itemsAfterOperation.head.oldValue should equal(TowardsDigitizing.value)
      itemsAfterOperation.head.newValue should equal(BothDirections.value)
      itemsAfterOperation.head.createdBy should equal(user)
    }
  }

  test("traffic direction change with already existing overridden value should be identified and saved to lane work list") {
    runWithRollback {
      val linkProperty = LinkProperties("1l", 5, PedestrianZone, BothDirections, Private)
      val roadLinkFetched = RoadLinkFetched("1l", 91, Nil, Municipality, TowardsDigitizing, FeatureClass.AllOthers)
      val existingOverriddenValue = Option(3)
      val itemsBeforeOperation = laneWorkListDao.getAllItems

      laneService.processRoadLinkPropertyChange(LinkPropertyChange("traffic_direction", existingOverriddenValue, linkProperty, roadLinkFetched, Some(user)), newTransaction = false)
      val itemsAfterOperation = laneWorkListDao.getAllItems

      itemsBeforeOperation.size should equal(0)
      itemsAfterOperation.size should equal(1)

      itemsAfterOperation.head.propertyName should equal("traffic_direction")
      itemsAfterOperation.head.oldValue should equal(AgainstDigitizing.value)
      itemsAfterOperation.head.newValue should equal(BothDirections.value)
      itemsAfterOperation.head.createdBy should equal(user)
    }
  }

  test("link type change with already existing overridden value should be identified and saved to lane work list") {
    runWithRollback {
      val linkProperty = LinkProperties("1l", 5, BidirectionalLaneCarriageWay, TowardsDigitizing, Private)
      val fetchedRoadLink = RoadLinkFetched("1l", 91, Nil, Municipality, TowardsDigitizing, FeatureClass.AllOthers)
      val existingOverriddenValue = Option(3)
      val itemsBeforeOperation = laneWorkListDao.getAllItems

      laneService.processRoadLinkPropertyChange(LinkPropertyChange("link_type", existingOverriddenValue, linkProperty, fetchedRoadLink, Some(user)), newTransaction = false)
      val itemsAfterOperation = laneWorkListDao.getAllItems

      itemsBeforeOperation.size should equal(0)
      itemsAfterOperation.size should equal(1)

      itemsAfterOperation.head.propertyName should equal("link_type")
      itemsAfterOperation.head.oldValue should equal(SingleCarriageway.value)
      itemsAfterOperation.head.newValue should equal(BidirectionalLaneCarriageWay.value)
      itemsAfterOperation.head.createdBy should equal(user)
    }
  }

  test("link type and traffic direction should be identified and saved to lane work list and link type change deleted") {
    runWithRollback {
      val linkProperty = LinkProperties("1l", 5, BidirectionalLaneCarriageWay, BothDirections, Private)
      val fetchedRoadLink = RoadLinkFetched("1l", 91, Nil, Municipality, TowardsDigitizing, FeatureClass.AllOthers)
      val existingOverriddenValue = None
      val itemsBeforeOperation = laneWorkListDao.getAllItems

      laneService.processRoadLinkPropertyChange(LinkPropertyChange("link_type", existingOverriddenValue, linkProperty, fetchedRoadLink, Some(user)), newTransaction = false)
      laneService.processRoadLinkPropertyChange(LinkPropertyChange("traffic_direction", existingOverriddenValue, linkProperty, fetchedRoadLink, Some(user)), newTransaction = false)
      val itemsAfterOperation = laneWorkListDao.getAllItems

      itemsBeforeOperation.size should equal(0)
      itemsAfterOperation.size should equal(2)

      val linkTypeItem = itemsAfterOperation.find(_.propertyName == "link_type").get
      laneWorkListService.deleteFromLaneWorkList(Set(linkTypeItem.id), false)
      val itemsAfterDelete = laneWorkListDao.getAllItems
      itemsAfterDelete.size should equal(1)

      itemsAfterDelete.head.propertyName should equal("traffic_direction")
      itemsAfterDelete.head.oldValue should equal(TowardsDigitizing.value)
      itemsAfterDelete.head.newValue should equal(BothDirections.value)
      itemsAfterDelete.head.createdBy should equal(user)
    }
  }
}
package fi.liikennevirasto.digiroad2.service.lane

import fi.liikennevirasto.digiroad2.asset.TrafficDirection.{AgainstDigitizing, BothDirections, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.lane.LaneWorkListDAO
import fi.liikennevirasto.digiroad2.service.LinkProperties
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.scalatest.{FunSuite, Matchers}

class LaneWorkListServiceSpec extends FunSuite with Matchers {
  val laneWorkListDao: LaneWorkListDAO = new LaneWorkListDAO
  val laneWorkListService: LaneWorkListService = new LaneWorkListService

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  val user: String = "testuser"


  test("traffic direction change with NO already existing overridden value should be identified and saved to lane work list") {
    val linkProperty = LinkProperties(1, 5, PedestrianZone, BothDirections, Private)
    val vvhRoadLink = VVHRoadlink(1l, 91, Nil, Municipality, TowardsDigitizing, FeatureClass.AllOthers)
    runWithRollback {
      val itemsBeforeOperation = laneWorkListDao.getAllItems
      laneWorkListService.insertToLaneWorkList("traffic_direction", None, linkProperty, vvhRoadLink, Some(user), false)
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
      val linkProperty = LinkProperties(1, 5, PedestrianZone, BothDirections, Private)
      val vvhRoadLink = VVHRoadlink(1l, 91, Nil, Municipality, TowardsDigitizing, FeatureClass.AllOthers)
      val existingOverriddenValue = Option(3)
      val itemsBeforeOperation = laneWorkListDao.getAllItems

      laneWorkListService.insertToLaneWorkList("traffic_direction", existingOverriddenValue, linkProperty, vvhRoadLink, Some(user), false)
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
      val linkProperty = LinkProperties(1, 5, BidirectionalLaneCarriageWay, TowardsDigitizing, Private)
      val vvhRoadLink = VVHRoadlink(1l, 91, Nil, Municipality, TowardsDigitizing, FeatureClass.AllOthers)
      val existingOverriddenValue = Option(3)
      val itemsBeforeOperation = laneWorkListDao.getAllItems

      laneWorkListService.insertToLaneWorkList("link_type", existingOverriddenValue, linkProperty, vvhRoadLink, Some(user), false)
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
      val linkProperty = LinkProperties(1, 5, BidirectionalLaneCarriageWay, BothDirections, Private)
      val vvhRoadLink = VVHRoadlink(1l, 91, Nil, Municipality, TowardsDigitizing, FeatureClass.AllOthers)
      val existingOverriddenValue = None
      val itemsBeforeOperation = laneWorkListDao.getAllItems

      laneWorkListService.insertToLaneWorkList("link_type", existingOverriddenValue, linkProperty, vvhRoadLink, Some(user), false)
      laneWorkListService.insertToLaneWorkList("traffic_direction", existingOverriddenValue, linkProperty, vvhRoadLink, Some(user), false)
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
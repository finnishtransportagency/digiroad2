package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset.TrafficDirection.{AgainstDigitizing, BothDirections}
import fi.liikennevirasto.digiroad2.asset.{CycleOrPedestrianPath, Municipality, SingleCarriageway, State, TractorRoad}
import fi.liikennevirasto.digiroad2.client.RoadLinkChangeType.{Add, Replace}
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.dao.RoadLinkOverrideDAO
import fi.liikennevirasto.digiroad2.dao.RoadLinkOverrideDAO._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.{IncompleteLink, RoadLinkService}
import fi.liikennevirasto.digiroad2.util.{LinearAssetUtils, TestTransactions}
import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

import scala.io.Source

class RoadLinkPropertyUpdaterSpec extends FunSuite with Matchers{

  class TestRoadLinkPropertyUpdater extends RoadLinkPropertyUpdater {
    override def incompleteLinkIsInUse(incompleteLink: IncompleteLink, roadLinkData: Seq[RoadLink]): Boolean = true
  }

  val filePath = getClass.getClassLoader.getResource("smallChangeSet.json").getPath
  val jsonFile = Source.fromFile(filePath).mkString
  val roadLinkPropertyUpdater = new TestRoadLinkPropertyUpdater
  val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
  val roadLinkService = new RoadLinkService(mockRoadLinkClient, new DummyEventBus, new DummySerializer)
  val roadLinkChangeClient = roadLinkPropertyUpdater.roadLinkChangeClient
  val changes = roadLinkChangeClient.convertToRoadLinkChange(jsonFile)
  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  test("properties of an old link are deleted or expired") {
    val changesWithOldLink = changes.filterNot(_.changeType == Add)
    runWithRollback {
      val oldLinkIds = changesWithOldLink.map(_.oldLink.get.linkId)
      oldLinkIds.foreach { linkId =>
        RoadLinkOverrideDAO.insert(TrafficDirection, linkId, Some("test"), 2)
        RoadLinkOverrideDAO.get(TrafficDirection, linkId) should not be None
        RoadLinkOverrideDAO.insert(AdministrativeClass, linkId, Some("test"), 2)
        RoadLinkOverrideDAO.get(AdministrativeClass, linkId) should not be None
        RoadLinkOverrideDAO.insert(FunctionalClass, linkId, Some("test"), 7)
        RoadLinkOverrideDAO.get(FunctionalClass, linkId) should not be None
        RoadLinkOverrideDAO.insert(LinkType, linkId, Some("test"), SingleCarriageway.value)
        RoadLinkOverrideDAO.get(LinkType, linkId) should not be None
        LinkAttributesDao.insertAttributeValueByChanges(linkId, "test", "PRIVATE_ROAD_ASSOCIATION", "test association", LinearAssetUtils.createTimeStamp())
        LinkAttributesDao.insertAttributeValueByChanges(linkId, "test", "ADDITIONAL_INFO", "1", LinearAssetUtils.createTimeStamp())
        LinkAttributesDao.getExistingValues(linkId).size should be(2)
      }
      roadLinkPropertyUpdater.removePropertiesFromOldLinks(changesWithOldLink)
      oldLinkIds.foreach { linkId =>
        RoadLinkOverrideDAO.get(TrafficDirection, linkId) should be(None)
        RoadLinkOverrideDAO.get(AdministrativeClass, linkId) should be(None)
        RoadLinkOverrideDAO.get(FunctionalClass, linkId) should be(None)
        RoadLinkOverrideDAO.get(LinkType, linkId) should be(None)
        LinkAttributesDao.getExistingValues(linkId).size should be(0)
      }
    }
  }

  test("overridden values and private road attributes are transferred to a new link") {
    val replaceChange = changes.filter(_.changeType == Replace).head
    runWithRollback {
      val oldLinkId = replaceChange.oldLink.get.linkId
      RoadLinkOverrideDAO.insert(TrafficDirection, oldLinkId, Some("test"), AgainstDigitizing.value)
      RoadLinkOverrideDAO.get(TrafficDirection, oldLinkId).get should be(AgainstDigitizing.value)
      RoadLinkOverrideDAO.insert(AdministrativeClass, oldLinkId, Some("test"), Municipality.value)
      RoadLinkOverrideDAO.get(AdministrativeClass, oldLinkId).get should be(Municipality.value)
      LinkAttributesDao.insertAttributeValueByChanges(oldLinkId, "test", "PRIVATE_ROAD_ASSOCIATION", "test association", LinearAssetUtils.createTimeStamp())
      LinkAttributesDao.insertAttributeValueByChanges(oldLinkId, "test", "ADDITIONAL_INFO", "1", LinearAssetUtils.createTimeStamp())
      LinkAttributesDao.getExistingValues(oldLinkId).size should be(2)

      roadLinkPropertyUpdater.transferOverriddenPropertiesAndPrivateRoadInfo(Seq(replaceChange))
      val newLinkId = replaceChange.newLinks.head.linkId
      RoadLinkOverrideDAO.get(TrafficDirection, newLinkId).get should be(AgainstDigitizing.value)
      RoadLinkOverrideDAO.get(AdministrativeClass, newLinkId).get should be(Municipality.value)
      val privateRoadAttributes = LinkAttributesDao.getExistingValues(newLinkId)
      privateRoadAttributes.get("PRIVATE_ROAD_ASSOCIATION").get should be("test association")
      privateRoadAttributes.get("ADDITIONAL_INFO").get should be("1")
    }
  }

  test("overridden values are not transferred if they are the same as ordinary road link values") {
    val replaceChange = changes.filter(_.changeType == Replace).head
    runWithRollback {
      val oldLinkId = replaceChange.oldLink.get.linkId
      RoadLinkOverrideDAO.insert(TrafficDirection, oldLinkId, Some("test"), BothDirections.value)
      RoadLinkOverrideDAO.get(TrafficDirection, oldLinkId).get should be(BothDirections.value)
      RoadLinkOverrideDAO.insert(AdministrativeClass, oldLinkId, Some("test"), State.value)
      RoadLinkOverrideDAO.get(AdministrativeClass, oldLinkId).get should be(State.value)

      roadLinkPropertyUpdater.transferOverriddenPropertiesAndPrivateRoadInfo(Seq(replaceChange))
      val newLinkId = replaceChange.newLinks.head.linkId
      RoadLinkOverrideDAO.get(TrafficDirection, newLinkId) should be(None)
      RoadLinkOverrideDAO.get(AdministrativeClass, newLinkId) should be(None)
    }
  }

  test("functional class and link type are primarily transferred from old link") {
    val singleCarriageWayChange = changes(10)
    runWithRollback {
      RoadLinkOverrideDAO.insert(FunctionalClass, singleCarriageWayChange.oldLink.get.linkId, Some("test"), 7)
      RoadLinkOverrideDAO.get(FunctionalClass, singleCarriageWayChange.oldLink.get.linkId).get should be(7)
      RoadLinkOverrideDAO.insert(LinkType, singleCarriageWayChange.oldLink.get.linkId, Some("test"), TractorRoad.value)
      RoadLinkOverrideDAO.get(LinkType, singleCarriageWayChange.oldLink.get.linkId).get should be(TractorRoad.value)
      roadLinkPropertyUpdater.transferOrGenerateFunctionalClassesAndLinkTypes(Seq(singleCarriageWayChange))
      singleCarriageWayChange.newLinks.foreach { newLink =>
        RoadLinkOverrideDAO.get(FunctionalClass, newLink.linkId).get should be(7)
        RoadLinkOverrideDAO.get(LinkType, newLink.linkId).get should be(TractorRoad.value)
      }
      roadLinkService.getIncompleteLinks(None, false).size should be(0)
    }
  }

  test("with no old link values, functional class and link type are generated for a new link with a suitable mtkClass") {
    runWithRollback {
      val addChanges = changes.filter(_.changeType == Add)
      roadLinkPropertyUpdater.transferOrGenerateFunctionalClassesAndLinkTypes(addChanges)
      addChanges.foreach { changes =>
        val newLinkId = changes.newLinks.head.linkId
        RoadLinkOverrideDAO.get(FunctionalClass, newLinkId).get should be(8)
        RoadLinkOverrideDAO.get(LinkType, newLinkId).get should be(CycleOrPedestrianPath.value)
      }
      roadLinkService.getIncompleteLinks(None, false).size should be(0)
    }
  }

  test("functional class is transferred from old link, but missing link type is generated") {
    val singleCarriageWayChange = changes(10)
    runWithRollback {
      RoadLinkOverrideDAO.insert(FunctionalClass, singleCarriageWayChange.oldLink.get.linkId, Some("test"), 7)
      RoadLinkOverrideDAO.get(FunctionalClass, singleCarriageWayChange.oldLink.get.linkId).get should be(7)
      roadLinkPropertyUpdater.transferOrGenerateFunctionalClassesAndLinkTypes(Seq(singleCarriageWayChange))
      singleCarriageWayChange.newLinks.foreach { newLink =>
        RoadLinkOverrideDAO.get(FunctionalClass, newLink.linkId).get should be(7)
        RoadLinkOverrideDAO.get(LinkType, newLink.linkId).get should be(SingleCarriageway.value)
      }
      roadLinkService.getIncompleteLinks(None, false).size should be(0)
    }
  }

  test("link type is transferred from old link, but missing functional class is generated") {
    val singleCarriageWayChange = changes(10)
    runWithRollback {
      RoadLinkOverrideDAO.insert(LinkType, singleCarriageWayChange.oldLink.get.linkId, Some("test"), TractorRoad.value)
      RoadLinkOverrideDAO.get(LinkType, singleCarriageWayChange.oldLink.get.linkId).get should be(TractorRoad.value)
      roadLinkPropertyUpdater.transferOrGenerateFunctionalClassesAndLinkTypes(Seq(singleCarriageWayChange))
      singleCarriageWayChange.newLinks.foreach { newLink =>
        RoadLinkOverrideDAO.get(FunctionalClass, newLink.linkId).get should be(4)
        RoadLinkOverrideDAO.get(LinkType, newLink.linkId).get should be(TractorRoad.value)
      }
      roadLinkService.getIncompleteLinks(None, false).size should be(0)
    }
  }

  test("if functional class and link type cannot be generated or transferred, an incomplete link is created") {
    val changeWithoutSuitableMtkClass = changes(9)
    runWithRollback {
      roadLinkPropertyUpdater.transferOrGenerateFunctionalClassesAndLinkTypes(Seq(changeWithoutSuitableMtkClass))
      val incompleteLinksMap = roadLinkService.getIncompleteLinks(None, false)
      val incompleteLinks = incompleteLinksMap.get("Espoo").get("Municipality").toSet
      incompleteLinks should be(changeWithoutSuitableMtkClass.newLinks.map(_.linkId).toSet)
    }
  }
}

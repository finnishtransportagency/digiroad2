package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset.TrafficDirection.{AgainstDigitizing, BothDirections, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.RoadLinkChangeType.{Add, Replace, Split}
import fi.liikennevirasto.digiroad2.client.{ReplaceInfo, RoadLinkChange, RoadLinkClient, RoadLinkInfo}
import fi.liikennevirasto.digiroad2.dao.RoadLinkOverrideDAO
import fi.liikennevirasto.digiroad2.dao.RoadLinkOverrideDAO.{AdministrativeClass, FunctionalClass, LinkType, TrafficDirection, _}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.{IncompleteLink, RoadLinkService}
import fi.liikennevirasto.digiroad2.util.{LinearAssetUtils, TestTransactions}
import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer, Point}
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
      val reportedChanges = roadLinkPropertyUpdater.removePropertiesFromOldLinks(changesWithOldLink)
      reportedChanges.map(_.linkId).toSet should be(oldLinkIds.toSet)
      val trafficDirectionChanges = reportedChanges.filter(c => c.isInstanceOf[TrafficDirectionChange]).map(c => c.asInstanceOf[TrafficDirectionChange])
      trafficDirectionChanges.map(_.oldValue).toSet should be(Set(2))
      trafficDirectionChanges.map(_.newValue).toSet should be(Set(None))
      val adminClassChanges = reportedChanges.filter(c => c.isInstanceOf[AdministrativeClassChange]).map(c => c.asInstanceOf[AdministrativeClassChange])
      adminClassChanges.map(_.oldValue).toSet should be(Set(2))
      adminClassChanges.map(_.newValue).toSet should be(Set(None))
      val functionalClassChanges = reportedChanges.filter(c => c.isInstanceOf[FunctionalClassChange]).map(c => c.asInstanceOf[FunctionalClassChange])
      functionalClassChanges.map(_.oldValue).toSet should be(Set(Some(7)))
      functionalClassChanges.map(_.newValue).toSet should be(Set(None))
      val linkTypeChanges = reportedChanges.filter(c => c.isInstanceOf[LinkTypeChange]).map(c => c.asInstanceOf[LinkTypeChange])
      linkTypeChanges.map(_.oldValue).toSet should be(Set(Some(SingleCarriageway.value)))
      linkTypeChanges.map(_.newValue).toSet should be(Set(None))
      val attributeChanges = reportedChanges.filter(c => c.isInstanceOf[RoadLinkAttributeChange]).map(c => c.asInstanceOf[RoadLinkAttributeChange])
      attributeChanges.map(_.oldValues).foreach(oldValue => (oldValue.get("ADDITIONAL_INFO") == Some("1") && oldValue.get("PRIVATE_ROAD_ASSOCIATION") == Some("test association")) should be(true))
      attributeChanges.map(_.newValues).foreach(newValue => newValue should be(Map()))
      oldLinkIds.foreach { linkId =>
        RoadLinkOverrideDAO.get(TrafficDirection, linkId) should be(None)
        RoadLinkOverrideDAO.get(AdministrativeClass, linkId) should be(None)
        RoadLinkOverrideDAO.get(FunctionalClass, linkId) should be(None)
        RoadLinkOverrideDAO.get(LinkType, linkId) should be(None)
        LinkAttributesDao.getExistingValues(linkId).size should be(0)
      }
    }
  }

  test("on a version change, overridden values and private road attributes are transferred to a new link") {
    val oldLinkId = "41cca8ff-4644-41aa-8de1-2702f1a57f80:1"
    val newLinkId = "41cca8ff-4644-41aa-8de1-2702f1a57f80:2"
    val replaceChange = RoadLinkChange(Replace,Some(RoadLinkInfo(oldLinkId,186.08235918,
      List(Point(361966.115,6682342.526,52.256), Point(361938.529,6682516.719,54.601)),12131,State,49,BothDirections)),
      List(RoadLinkInfo(newLinkId,186.08235918,List(Point(361966.115,6682342.526,52.256),
        Point(361938.529,6682516.719,54.601)),12131,State,49,BothDirections)),List(ReplaceInfo(oldLinkId,newLinkId,0.0,
        186.0823591774,0.0,186.0823591774,false)))
    runWithRollback {
      RoadLinkOverrideDAO.insert(TrafficDirection, oldLinkId, Some("test"), AgainstDigitizing.value)
      RoadLinkOverrideDAO.get(TrafficDirection, oldLinkId).get should be(AgainstDigitizing.value)
      RoadLinkOverrideDAO.insert(AdministrativeClass, oldLinkId, Some("test"), Municipality.value)
      RoadLinkOverrideDAO.get(AdministrativeClass, oldLinkId).get should be(Municipality.value)
      LinkAttributesDao.insertAttributeValueByChanges(oldLinkId, "test", "PRIVATE_ROAD_ASSOCIATION", "test association", LinearAssetUtils.createTimeStamp())
      LinkAttributesDao.insertAttributeValueByChanges(oldLinkId, "test", "ADDITIONAL_INFO", "1", LinearAssetUtils.createTimeStamp())
      LinkAttributesDao.getExistingValues(oldLinkId).size should be(2)

      val reportedChanges = roadLinkPropertyUpdater.transferOverriddenPropertiesAndPrivateRoadInfo(Seq(replaceChange))
      val trafficDirectionChange = reportedChanges.find(c => c.isInstanceOf[TrafficDirectionChange]).get.asInstanceOf[TrafficDirectionChange]
      trafficDirectionChange.oldValue should be(AgainstDigitizing.value)
      trafficDirectionChange.newValue should be(Some(AgainstDigitizing.value))
      val adminClassChange = reportedChanges.find(c => c.isInstanceOf[AdministrativeClassChange]).get.asInstanceOf[AdministrativeClassChange]
      adminClassChange.oldValue should be(Municipality.value)
      adminClassChange.newValue should be(Some(Municipality.value))
      val attributeChange = reportedChanges.find(c => c.isInstanceOf[RoadLinkAttributeChange]).get.asInstanceOf[RoadLinkAttributeChange]
      attributeChange.oldValues should be(Map("ADDITIONAL_INFO" -> "1", "PRIVATE_ROAD_ASSOCIATION" -> "test association"))
      attributeChange.newValues should be(Map("ADDITIONAL_INFO" -> "1", "PRIVATE_ROAD_ASSOCIATION" -> "test association"))

      RoadLinkOverrideDAO.get(TrafficDirection, newLinkId).get should be(AgainstDigitizing.value)
      RoadLinkOverrideDAO.get(AdministrativeClass, newLinkId).get should be(Municipality.value)
      val privateRoadAttributes = LinkAttributesDao.getExistingValues(newLinkId)
      privateRoadAttributes.get("PRIVATE_ROAD_ASSOCIATION").get should be("test association")
      privateRoadAttributes.get("ADDITIONAL_INFO").get should be("1")
    }
  }

  test("overridden values are not transferred if they are the same as ordinary road link values") {
    val oldLinkId = "41cca8ff-4644-41aa-8de1-2702f1a57f80:1"
    val newLinkId = "41cca8ff-4644-41aa-8de1-2702f1a57f80:2"
    val replaceChange = RoadLinkChange(Replace,Some(RoadLinkInfo(oldLinkId,186.08235918,
      List(Point(361966.115,6682342.526,52.256), Point(361938.529,6682516.719,54.601)),12131,State,49,BothDirections)),
      List(RoadLinkInfo(newLinkId,186.08235918,List(Point(361966.115,6682342.526,52.256),
        Point(361938.529,6682516.719,54.601)),12131,State,49,BothDirections)),List(ReplaceInfo(oldLinkId,newLinkId,0.0,
        186.0823591774,0.0,186.0823591774,false)))
    runWithRollback {
      RoadLinkOverrideDAO.insert(TrafficDirection, oldLinkId, Some("test"), BothDirections.value)
      RoadLinkOverrideDAO.get(TrafficDirection, oldLinkId).get should be(BothDirections.value)
      RoadLinkOverrideDAO.insert(AdministrativeClass, oldLinkId, Some("test"), State.value)
      RoadLinkOverrideDAO.get(AdministrativeClass, oldLinkId).get should be(State.value)

      val reportedChanges = roadLinkPropertyUpdater.transferOverriddenPropertiesAndPrivateRoadInfo(Seq(replaceChange))
      reportedChanges.isEmpty should be(true)

      RoadLinkOverrideDAO.get(TrafficDirection, newLinkId) should be(None)
      RoadLinkOverrideDAO.get(AdministrativeClass, newLinkId) should be(None)
    }
  }

  test("on a geometry change, overridden values are not transferred") {
    val oldLinkId = "875766ca-83b1-450b-baf1-db76d59176be:1"
    val newLinkId = "6eec9a4a-bcac-4afb-afc8-f4e6d40ec571:1"
    val replaceChange = RoadLinkChange(Replace,Some(RoadLinkInfo(oldLinkId,45.317,List(Point(370276.441,6670348.945,7.94),
      Point(370234.985,6670367.114,9.638)),12131,Municipality,49,BothDirections)),List(RoadLinkInfo(newLinkId,35.212,
      List(Point(370276.441,6670348.945,7.94), Point(370243.874,6670361.794,9.145)),12131,Municipality,49,BothDirections)),
      List(ReplaceInfo(oldLinkId,newLinkId,0.0,45.317,0.0,35.212,false)))
    runWithRollback {
      RoadLinkOverrideDAO.insert(TrafficDirection, oldLinkId, Some("test"), AgainstDigitizing.value)
      RoadLinkOverrideDAO.get(TrafficDirection, oldLinkId).get should be(AgainstDigitizing.value)
      RoadLinkOverrideDAO.insert(AdministrativeClass, oldLinkId, Some("test"), State.value)
      RoadLinkOverrideDAO.get(AdministrativeClass, oldLinkId).get should be(State.value)

      val reportedChanges = roadLinkPropertyUpdater.transferOverriddenPropertiesAndPrivateRoadInfo(Seq(replaceChange))
      reportedChanges.isEmpty should be(true)

      RoadLinkOverrideDAO.get(TrafficDirection, newLinkId) should be(None)
      RoadLinkOverrideDAO.get(AdministrativeClass, newLinkId) should be(None)
    }
  }

  test("functional class and link type are primarily transferred from old link") {
    val oldLinkId = "c19bd6b4-9923-4ce9-a9cb-09779708913e:1"
    val newLinkId1 = "0d92e4a4-51cf-4abe-8f0e-8ee22241c3ff:1"
    val newLinkId2 = "d59dd3a9-a94d-4f26-b311-6b9b8361c717:1"
    val newLinkId3 = "e92c98c9-5a62-4995-a9c0-e40ae0b65747:1"
    val newLinkIds = Seq(newLinkId1, newLinkId2, newLinkId3)
    val singleCarriageWayChange = RoadLinkChange(Split,Some(RoadLinkInfo(oldLinkId,121.67349003,List(Point(370507.036,6678446.659,15.777),
      Point(370624.618,6678469.114,14.312)),12131,State,49,TowardsDigitizing)),List(RoadLinkInfo(newLinkId1,17.30470614,
      List(Point(370524.238,6678444.954,15.134), Point(370507.036,6678446.659,15.777)),12131,State,49,AgainstDigitizing),
      RoadLinkInfo(newLinkId2,86.94114036,List(Point(370541.665,6678445.318,14.602), Point(370624.618,6678469.114,14.312)),
        12131,State,49,TowardsDigitizing), RoadLinkInfo(newLinkId3,17.43162599,List(Point(370524.238,6678444.954,15.134),
        Point(370541.665,6678445.318,14.602)),12131,State,49,TowardsDigitizing)),List(ReplaceInfo(oldLinkId,newLinkId1,0.0,17.305,0.0,17.305,false),
      ReplaceInfo(oldLinkId,newLinkId2,34.733,121.673,0.0,86.941,false), ReplaceInfo(oldLinkId,newLinkId3,17.305,34.733,0.0,17.432,false)))
    runWithRollback {
      RoadLinkOverrideDAO.insert(FunctionalClass, oldLinkId, Some("test"), 7)
      RoadLinkOverrideDAO.get(FunctionalClass, oldLinkId).get should be(7)
      RoadLinkOverrideDAO.insert(LinkType, oldLinkId, Some("test"), TractorRoad.value)
      RoadLinkOverrideDAO.get(LinkType, oldLinkId).get should be(TractorRoad.value)
      val reportedChanges = roadLinkPropertyUpdater.transferOrGenerateFunctionalClassesAndLinkTypes(Seq(singleCarriageWayChange))
      val functionalClassChange = reportedChanges.find(c => c.isInstanceOf[FunctionalClassChange]).get.asInstanceOf[FunctionalClassChange]
      functionalClassChange.oldValue should be(Some(7))
      functionalClassChange.newValue should be(Some(7))
      functionalClassChange.source should be("oldLink")
      val linkTypeChange = reportedChanges.find(c => c.isInstanceOf[LinkTypeChange]).get.asInstanceOf[LinkTypeChange]
      linkTypeChange.oldValue should be(Some(TractorRoad.value))
      linkTypeChange.newValue should be(Some(TractorRoad.value))
      linkTypeChange.source should be("oldLink")

      newLinkIds.foreach { newLinkId =>
        RoadLinkOverrideDAO.get(FunctionalClass, newLinkId).get should be(7)
        RoadLinkOverrideDAO.get(LinkType, newLinkId).get should be(TractorRoad.value)
      }
      roadLinkService.getIncompleteLinks(None, false).size should be(0)
    }
  }

  test("with no old link values, functional class and link type are generated for a new link with a suitable mtkClass") {
    runWithRollback {
      val cycleOrPedestrianPathAddChanges = changes.filter(c => c.changeType == Add && c.newLinks.forall(_.roadClass == 12314))
      val reportedChanges = roadLinkPropertyUpdater.transferOrGenerateFunctionalClassesAndLinkTypes(cycleOrPedestrianPathAddChanges)
      val functionalClassChanges = reportedChanges.filter(c => c.isInstanceOf[FunctionalClassChange]).map(c => c.asInstanceOf[FunctionalClassChange])
      functionalClassChanges.map(_.oldValue).toSet should be(Set(None))
      functionalClassChanges.map(_.newValue).toSet should be(Set(Some(8)))
      functionalClassChanges.map(_.source).toSet should be(Set("mtkClass"))
      val linkTypeChanges = reportedChanges.filter(c => c.isInstanceOf[LinkTypeChange]).map(c => c.asInstanceOf[LinkTypeChange])
      linkTypeChanges.map(_.oldValue).toSet should be(Set(None))
      linkTypeChanges.map(_.newValue).toSet should be(Set(Some(CycleOrPedestrianPath.value)))
      linkTypeChanges.map(_.source).toSet should be(Set("mtkClass"))
      cycleOrPedestrianPathAddChanges.foreach { changes =>
        val newLinkId = changes.newLinks.head.linkId
        RoadLinkOverrideDAO.get(FunctionalClass, newLinkId).get should be(8)
        RoadLinkOverrideDAO.get(LinkType, newLinkId).get should be(CycleOrPedestrianPath.value)
      }
      roadLinkService.getIncompleteLinks(None, false).size should be(0)
    }
  }

  test("functional class is transferred from old link, but missing link type is generated") {
    val oldLinkId = "c19bd6b4-9923-4ce9-a9cb-09779708913e:1"
    val newLinkId1 = "0d92e4a4-51cf-4abe-8f0e-8ee22241c3ff:1"
    val newLinkId2 = "d59dd3a9-a94d-4f26-b311-6b9b8361c717:1"
    val newLinkId3 = "e92c98c9-5a62-4995-a9c0-e40ae0b65747:1"
    val newLinkIds = Seq(newLinkId1, newLinkId2, newLinkId3)
    val singleCarriageWayChange = RoadLinkChange(Split,Some(RoadLinkInfo(oldLinkId,121.67349003,List(Point(370507.036,6678446.659,15.777),
      Point(370624.618,6678469.114,14.312)),12131,State,49,TowardsDigitizing)),List(RoadLinkInfo(newLinkId1,17.30470614,
      List(Point(370524.238,6678444.954,15.134), Point(370507.036,6678446.659,15.777)),12131,State,49,AgainstDigitizing),
      RoadLinkInfo(newLinkId2,86.94114036,List(Point(370541.665,6678445.318,14.602), Point(370624.618,6678469.114,14.312)),
        12131,State,49,TowardsDigitizing), RoadLinkInfo(newLinkId3,17.43162599,List(Point(370524.238,6678444.954,15.134),
        Point(370541.665,6678445.318,14.602)),12131,State,49,TowardsDigitizing)),List(ReplaceInfo(oldLinkId,newLinkId1,0.0,17.305,0.0,17.305,false),
      ReplaceInfo(oldLinkId,newLinkId2,34.733,121.673,0.0,86.941,false), ReplaceInfo(oldLinkId,newLinkId3,17.305,34.733,0.0,17.432,false)))
    runWithRollback {
      RoadLinkOverrideDAO.insert(FunctionalClass, oldLinkId, Some("test"), 7)
      RoadLinkOverrideDAO.get(FunctionalClass, oldLinkId).get should be(7)
      val reportedChanges = roadLinkPropertyUpdater.transferOrGenerateFunctionalClassesAndLinkTypes(Seq(singleCarriageWayChange))
      val functionalClassChange = reportedChanges.find(c => c.isInstanceOf[FunctionalClassChange]).get.asInstanceOf[FunctionalClassChange]
      functionalClassChange.oldValue should be(Some(7))
      functionalClassChange.newValue should be(Some(7))
      functionalClassChange.source should be("oldLink")
      val linkTypeChange = reportedChanges.find(c => c.isInstanceOf[LinkTypeChange]).get.asInstanceOf[LinkTypeChange]
      linkTypeChange.oldValue should be(None)
      linkTypeChange.newValue should be(Some(SingleCarriageway.value))
      linkTypeChange.source should be("mtkClass")
      newLinkIds.foreach { newLinkId =>
        RoadLinkOverrideDAO.get(FunctionalClass, newLinkId).get should be(7)
        RoadLinkOverrideDAO.get(LinkType, newLinkId).get should be(SingleCarriageway.value)
      }
      roadLinkService.getIncompleteLinks(None, false).size should be(0)
    }
  }

  test("link type is transferred from old link, but missing functional class is generated") {
    val oldLinkId = "c19bd6b4-9923-4ce9-a9cb-09779708913e:1"
    val newLinkId1 = "0d92e4a4-51cf-4abe-8f0e-8ee22241c3ff:1"
    val newLinkId2 = "d59dd3a9-a94d-4f26-b311-6b9b8361c717:1"
    val newLinkId3 = "e92c98c9-5a62-4995-a9c0-e40ae0b65747:1"
    val newLinkIds = Seq(newLinkId1, newLinkId2, newLinkId3)
    val singleCarriageWayChange = RoadLinkChange(Split,Some(RoadLinkInfo(oldLinkId,121.67349003,List(Point(370507.036,6678446.659,15.777),
      Point(370624.618,6678469.114,14.312)),12131,State,49,TowardsDigitizing)),List(RoadLinkInfo(newLinkId1,17.30470614,
      List(Point(370524.238,6678444.954,15.134), Point(370507.036,6678446.659,15.777)),12131,State,49,AgainstDigitizing),
      RoadLinkInfo(newLinkId2,86.94114036,List(Point(370541.665,6678445.318,14.602), Point(370624.618,6678469.114,14.312)),
        12131,State,49,TowardsDigitizing), RoadLinkInfo(newLinkId3,17.43162599,List(Point(370524.238,6678444.954,15.134),
        Point(370541.665,6678445.318,14.602)),12131,State,49,TowardsDigitizing)),List(ReplaceInfo(oldLinkId,newLinkId1,0.0,17.305,0.0,17.305,false),
      ReplaceInfo(oldLinkId,newLinkId2,34.733,121.673,0.0,86.941,false), ReplaceInfo(oldLinkId,newLinkId3,17.305,34.733,0.0,17.432,false)))
    runWithRollback {
      RoadLinkOverrideDAO.insert(LinkType, oldLinkId, Some("test"), TractorRoad.value)
      RoadLinkOverrideDAO.get(LinkType, oldLinkId).get should be(TractorRoad.value)
      val reportedChanges = roadLinkPropertyUpdater.transferOrGenerateFunctionalClassesAndLinkTypes(Seq(singleCarriageWayChange))
      val functionalClassChange = reportedChanges.find(c => c.isInstanceOf[FunctionalClassChange]).get.asInstanceOf[FunctionalClassChange]
      functionalClassChange.oldValue should be(None)
      functionalClassChange.newValue should be(Some(4))
      functionalClassChange.source should be("mtkClass")
      val linkTypeChange = reportedChanges.find(c => c.isInstanceOf[LinkTypeChange]).get.asInstanceOf[LinkTypeChange]
      linkTypeChange.oldValue should be(Some(TractorRoad.value))
      linkTypeChange.newValue should be(Some(TractorRoad.value))
      linkTypeChange.source should be("oldLink")
      newLinkIds.foreach { newLinkId =>
        RoadLinkOverrideDAO.get(FunctionalClass, newLinkId).get should be(4)
        RoadLinkOverrideDAO.get(LinkType, newLinkId).get should be(TractorRoad.value)
      }
      roadLinkService.getIncompleteLinks(None, false).size should be(0)
    }
  }

  test("if functional class and link type cannot be generated or transferred, an incomplete link is created") {
    val oldLinkId = "99ade73f-979b-480b-976a-197ad365440a:1"
    val newLinkId1 = "1cb02550-5ce4-4c0a-8bee-c7f5e1f314d1:1"
    val newLinkId2 = "76637626-e794-4088-a9cf-20dd2ba92b6a:1"
    val newLinkId3 = "7c6fc2d3-f79f-4e03-a349-80103714a442:1"
    val changeWithoutSuitableMtkClass = RoadLinkChange(Split,Some(RoadLinkInfo(oldLinkId, 429.63910641,
      List(Point(367588.33,6673322.58,10.635), Point(367451.076,6673729.704,17.119)),12121,Municipality,49,BothDirections)),
      List(RoadLinkInfo(newLinkId1,267.44044079,List(Point(367536.298,6673476.206,12.767), Point(367451.076,6673729.704,17.119)),
        12121,Municipality,49,BothDirections), RoadLinkInfo(newLinkId2,145.91224664,List(Point(367588.33,6673322.58,10.635),
        Point(367541.489,6673460.769,12.506)),12121,Municipality,49,BothDirections), RoadLinkInfo(newLinkId3,16.28641919,
        List(Point(367541.489,6673460.769,12.506), Point(367536.298,6673476.206,12.767)),12121,Municipality,49,BothDirections)),
      List(ReplaceInfo(oldLinkId,newLinkId1,162.199,429.639,0.0,267.44,false), ReplaceInfo(oldLinkId,newLinkId2,0.0,145.912,0.0,145.912,false),
        ReplaceInfo(oldLinkId,newLinkId3,145.912,162.199,0.0,16.286,false)))
    runWithRollback {
      val reportedChanges = roadLinkPropertyUpdater.transferOrGenerateFunctionalClassesAndLinkTypes(Seq(changeWithoutSuitableMtkClass))
      reportedChanges.isEmpty should be(true)
      val incompleteLinksMap = roadLinkService.getIncompleteLinks(None, false)
      val incompleteLinks = incompleteLinksMap.get("Espoo").get("Municipality").toSet
      incompleteLinks should be(changeWithoutSuitableMtkClass.newLinks.map(_.linkId).toSet)
    }
  }

  test("on a merge, new value should be inserted and reported only once") {
    val oldLinkId1 = "d6a67594-6069-4295-a4d3-8495b7f76ef0:1"
    val oldLinkId2 = "7c21ce6f-d81b-481f-a67e-14778ad2b59c:1"
    val newLinkId = "524c67d9-b8af-4070-a4a1-52d7aec0526c:1"
    val mergeChanges = Seq(RoadLinkChange(Replace, Some(RoadLinkInfo(oldLinkId1, 23.48096698, List(Point(378439.895, 6674220.669, 6.021),
      Point(378461.027, 6674230.896, 1.993)), 12314, Municipality, 49, BothDirections)), List(RoadLinkInfo(newLinkId, 89.72825312,
      List(Point(378439.895, 6674220.669, 6.021), Point(378521.11, 6674258.813, 6.9)), 12314, Municipality, 49, BothDirections)),
      List(ReplaceInfo(oldLinkId1, newLinkId, 0.0, 23.4809669837, 0.0, 23.4762173445, false))),
      RoadLinkChange(Replace, Some(RoadLinkInfo(oldLinkId2, 66.25297685, List(Point(378461.027, 6674230.896, 1.993), Point(378521.11, 6674258.813, 6.9)),
        12314, Municipality, 49, BothDirections)), List(RoadLinkInfo(newLinkId, 89.72825312, List(Point(378439.895, 6674220.669, 6.021),
        Point(378521.11, 6674258.813, 6.9)), 12314, Municipality, 49, BothDirections)), List(ReplaceInfo(oldLinkId2, newLinkId, 0.0, 66.252976853, 23.4762173445, 89.7282531237, false))))
    runWithRollback {
      LinkAttributesDao.insertAttributeValueByChanges(oldLinkId1, "test", "PRIVATE_ROAD_ASSOCIATION", "test association", LinearAssetUtils.createTimeStamp())
      LinkAttributesDao.insertAttributeValueByChanges(oldLinkId2, "test", "PRIVATE_ROAD_ASSOCIATION", "test association", LinearAssetUtils.createTimeStamp())
      RoadLinkOverrideDAO.insert(FunctionalClass, oldLinkId1, Some("test"), 7)
      RoadLinkOverrideDAO.insert(FunctionalClass, oldLinkId2, Some("test"), 7)
      val overriddenAndPrivates = roadLinkPropertyUpdater.transferOverriddenPropertiesAndPrivateRoadInfo(mergeChanges)
      val functionalClassesAndLinkTypes = roadLinkPropertyUpdater.transferOrGenerateFunctionalClassesAndLinkTypes(mergeChanges)
      LinkAttributesDao.getExistingValues(newLinkId).get("PRIVATE_ROAD_ASSOCIATION").get should be("test association")
      FunctionalClassDao.getExistingValue(newLinkId) should be(Some(7))
      LinkTypeDao.getExistingValue(newLinkId) should be(Some(CycleOrPedestrianPath.value))
      overriddenAndPrivates.filter(_.isInstanceOf[RoadLinkAttributeChange]).size should be(1)
      functionalClassesAndLinkTypes.filter(_.isInstanceOf[FunctionalClassChange]).size should be(1)
      functionalClassesAndLinkTypes.filter(_.isInstanceOf[LinkTypeChange]).size should be(1)
    }
  }

  test("when digitization is changed, overridden traffic direction is turned around") {
    val oldLinkId = "41cca8ff-4644-41aa-8de1-2702f1a57f80:1"
    val newLinkId = "41cca8ff-4644-41aa-8de1-2702f1a57f80:2"
    val replaceChange = RoadLinkChange(Replace,Some(RoadLinkInfo(oldLinkId,186.08235918,
      List(Point(361966.115,6682342.526,52.256), Point(361938.529,6682516.719,54.601)),12131,State,49,BothDirections)),
      List(RoadLinkInfo(newLinkId,186.08235918,List(Point(361966.115,6682342.526,52.256),
        Point(361938.529,6682516.719,54.601)),12131,State,49,BothDirections)),List(ReplaceInfo(oldLinkId,newLinkId,0.0,
        186.0823591774,0.0,186.0823591774,true)))
    runWithRollback {
      RoadLinkOverrideDAO.insert(TrafficDirection, oldLinkId, Some("test"), TowardsDigitizing.value)
      val overriddenValues = roadLinkPropertyUpdater.transferOverriddenPropertiesAndPrivateRoadInfo(Seq(replaceChange))
      RoadLinkOverrideDAO.get(TrafficDirection, newLinkId) should be(Some(AgainstDigitizing.value))
      val trChange = overriddenValues.head.asInstanceOf[TrafficDirectionChange]
      trChange.linkId should be(newLinkId)
      trChange.oldValue should be(TowardsDigitizing.value)
      trChange.newValue.get should be(AgainstDigitizing.value)
    }
  }
}

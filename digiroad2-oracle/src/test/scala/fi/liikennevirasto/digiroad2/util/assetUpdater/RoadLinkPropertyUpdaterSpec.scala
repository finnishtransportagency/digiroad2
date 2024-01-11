package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset.TrafficDirection.{AgainstDigitizing, BothDirections, TowardsDigitizing, UnknownDirection}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.RoadLinkChangeType.{Add, Replace, Split}
import fi.liikennevirasto.digiroad2.client.{ReplaceInfo, RoadLinkChange, RoadLinkClient, RoadLinkInfo}
import fi.liikennevirasto.digiroad2.dao.RoadLinkOverrideDAO
import fi.liikennevirasto.digiroad2.dao.RoadLinkOverrideDAO.{AdministrativeClass, FunctionalClass, LinkType, TrafficDirection, _}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, SurfaceType}
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

  test("on a version change, overridden values and private road attributes are transferred to a new link") {
    val oldLinkId = "41cca8ff-4644-41aa-8de1-2702f1a57f80:1"
    val newLinkId = "41cca8ff-4644-41aa-8de1-2702f1a57f80:2"
    val replaceChange = RoadLinkChange(Replace,Some(RoadLinkInfo(oldLinkId,186.08235918,
      List(Point(361966.115,6682342.526,52.256), Point(361938.529,6682516.719,54.601)),12131,State,49,BothDirections)),
      List(RoadLinkInfo(newLinkId,186.08235918,List(Point(361966.115,6682342.526,52.256),
        Point(361938.529,6682516.719,54.601)),12131,State,49,BothDirections)),List(ReplaceInfo(Option(oldLinkId),Option(newLinkId),Option(0.0),
        Option(186.0823591774),Option(0.0),Option(186.0823591774),false)))
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
        Point(361938.529,6682516.719,54.601)),12131,State,49,BothDirections)),List(ReplaceInfo(Option(oldLinkId),Option(newLinkId),Option(0.0),
        Option(186.0823591774),Option(0.0),Option(186.0823591774),false)))
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
      List(ReplaceInfo(Option(oldLinkId),Option(newLinkId),Option(0.0),Option(45.317),Option(0.0),Option(35.212),false)))
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
        Point(370541.665,6678445.318,14.602)),12131,State,49,TowardsDigitizing)),List(ReplaceInfo(Option(oldLinkId),Option(newLinkId1),Option(0.0),Option(17.305),Option(0.0),Option(17.305),false),
      ReplaceInfo(Option(oldLinkId),Option(newLinkId2),Option(34.733),Option(121.673),Option(0.0),Option(86.941),false), ReplaceInfo(Option(oldLinkId),Option(newLinkId3),Option(17.305),Option(34.733),Option(0.0),Option(17.432),false)))
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
        Point(370541.665,6678445.318,14.602)),12131,State,49,TowardsDigitizing)),List(ReplaceInfo(Option(oldLinkId),Option(newLinkId1),Option(0.0),Option(17.305),Option(0.0),Option(17.305),false),
      ReplaceInfo(Option(oldLinkId),Option(newLinkId2),Option(34.733),Option(121.673),Option(0.0),Option(86.941),false), ReplaceInfo(Option(oldLinkId),Option(newLinkId3),Option(17.305),Option(34.733),Option(0.0),Option(17.432),false)))
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
        Point(370541.665,6678445.318,14.602)),12131,State,49,TowardsDigitizing)),List(ReplaceInfo(Option(oldLinkId),Option(newLinkId1),Option(0.0),Option(17.305),Option(0.0),Option(17.305),false),
      ReplaceInfo(Option(oldLinkId),Option(newLinkId2),Option(34.733),Option(121.673),Option(0.0),Option(86.941),false), ReplaceInfo(Option(oldLinkId),Option(newLinkId3),Option(17.305),Option(34.733),Option(0.0),Option(17.432),false)))
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
      List(ReplaceInfo(Option(oldLinkId),Option(newLinkId1),Option(162.199),Option(429.639),Option(0.0),Option(267.44),false), ReplaceInfo(Option(oldLinkId),Option(newLinkId2),Option(0.0),Option(145.912),Option(0.0),Option(145.912),false),
        ReplaceInfo(Option(oldLinkId),Option(newLinkId3),Option(145.912),Option(162.199),Option(0.0),Option(16.286),false)))
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
      List(ReplaceInfo(Option(oldLinkId1), Option(newLinkId), Option(0.0), Option(23.4809669837), Option(0.0), Option(23.4762173445), false))),
      RoadLinkChange(Replace, Some(RoadLinkInfo(oldLinkId2, 66.25297685, List(Point(378461.027, 6674230.896, 1.993), Point(378521.11, 6674258.813, 6.9)),
        12314, Municipality, 49, BothDirections)), List(RoadLinkInfo(newLinkId, 89.72825312, List(Point(378439.895, 6674220.669, 6.021),
        Point(378521.11, 6674258.813, 6.9)), 12314, Municipality, 49, BothDirections)), List(ReplaceInfo(Option(oldLinkId2), Option(newLinkId), Option(0.0), Option(66.252976853), Option(23.4762173445), Option(89.7282531237), false))))
    runWithRollback {
      LinkAttributesDao.insertAttributeValueByChanges(oldLinkId1, "test", "PRIVATE_ROAD_ASSOCIATION", "test association", LinearAssetUtils.createTimeStamp())
      LinkAttributesDao.insertAttributeValueByChanges(oldLinkId2, "test", "PRIVATE_ROAD_ASSOCIATION", "test association", LinearAssetUtils.createTimeStamp())
      RoadLinkOverrideDAO.insert(FunctionalClass, oldLinkId1, Some("test"), 7)
      RoadLinkOverrideDAO.insert(FunctionalClass, oldLinkId2, Some("test"), 7)
      val overriddenAndPrivates = roadLinkPropertyUpdater.transferOverriddenPropertiesAndPrivateRoadInfo(mergeChanges)
      val functionalClassesAndLinkTypes = roadLinkPropertyUpdater.transferOrGenerateFunctionalClassesAndLinkTypes(mergeChanges)
      LinkAttributesDao.getExistingValues(newLinkId).get("PRIVATE_ROAD_ASSOCIATION").get should be("test association")
      FunctionalClassDao.getExistingValue(newLinkId) should be(Some(7))
      overriddenAndPrivates.filter(_.isInstanceOf[RoadLinkAttributeChange]).size should be(1)
      functionalClassesAndLinkTypes.filter(_.isInstanceOf[FunctionalClassChange]).size should be(1)
      functionalClassesAndLinkTypes.filter(_.isInstanceOf[LinkTypeChange]).size should be(0)
    }
  }

  test("when digitization is changed, overridden traffic direction is turned around") {
    val oldLinkId = "41cca8ff-4644-41aa-8de1-2702f1a57f80:1"
    val newLinkId = "41cca8ff-4644-41aa-8de1-2702f1a57f80:2"
    val replaceChange = RoadLinkChange(Replace,Some(RoadLinkInfo(oldLinkId,186.08235918,
      List(Point(361966.115,6682342.526,52.256), Point(361938.529,6682516.719,54.601)),12131,State,49,BothDirections)),
      List(RoadLinkInfo(newLinkId,186.08235918,List(Point(361966.115,6682342.526,52.256),
        Point(361938.529,6682516.719,54.601)),12131,State,49,BothDirections)),List(ReplaceInfo(Option(oldLinkId),Option(newLinkId),Option(0.0),
        Option(186.0823591774),Option(0.0),Option(186.0823591774),true)))
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

  test("Given a merge; When old links are missing information; Then an incomplete link should be generated") {
    val oldLinkId1 = "7b8b02f4-317d-4dad-a2d4-f6ad02ea3bdd:1"
    val oldLinkId2 = "d8200078-712b-4062-8cc1-5119f04622d5:1"
    val newLinkId = "ced8a776-33e0-4ce7-a65f-2e380c4205f2:1"
    val mergeChanges = Seq(
      RoadLinkChange(Replace, Some(RoadLinkInfo(oldLinkId1, 555.312,
        List(Point(410580.098, 7524656.363, 186.118), Point(410698.996, 7525137.579, 186.329)), 12316, Private, 261, UnknownDirection)),
        List(RoadLinkInfo(newLinkId, 734.309, List(Point(410580.098, 7524656.363, 186.118), Point(410803.855, 7525268.116, 186.353)),
          12316, Unknown, 261, UnknownDirection)),
        List(ReplaceInfo(Option(oldLinkId1), Option(newLinkId), Option(0.0), Option(555.312), Option(0.0), Option(555.312), false))),
      RoadLinkChange(Replace, Some(RoadLinkInfo(oldLinkId2, 178.997,
        List(Point(378461.027, 6674230.896, 1.993), Point(378521.11, 6674258.813, 6.9)), 12314, Municipality, 49, BothDirections)),
        List(RoadLinkInfo(newLinkId, 734.309, List(Point(410580.098, 7524656.363, 186.118), Point(410803.855, 7525268.116, 186.353)),
          12316, Unknown, 261, UnknownDirection)),
        List(ReplaceInfo(Option(oldLinkId2), Option(newLinkId), Option(0.0), Option(178.997), Option(555.312), Option(734.309), false)))
    )
    runWithRollback {
      val transferredProperties = roadLinkPropertyUpdater.transferOverriddenPropertiesAndPrivateRoadInfo(mergeChanges)
      val createdProperties = roadLinkPropertyUpdater.transferOrGenerateFunctionalClassesAndLinkTypes(mergeChanges)
      transferredProperties.size should be(0)
      createdProperties.size should be(0)
      roadLinkService.getIncompleteLinks(None, false).size should be(1)
    }
  }

  test("Given a merge; When old links are not missing information; Then no incomplete link should be generated") {
    val oldLinkId1 = "7b8b02f4-317d-4dad-a2d4-f6ad02ea3bdd:1"
    val oldLinkId2 = "d8200078-712b-4062-8cc1-5119f04622d5:1"
    val newLinkId = "ced8a776-33e0-4ce7-a65f-2e380c4205f2:1"
    val mergeChanges = Seq(
      RoadLinkChange(Replace, Some(RoadLinkInfo(oldLinkId1, 555.312,
        List(Point(410580.098, 7524656.363, 186.118), Point(410698.996, 7525137.579, 186.329)), 12316, Private, 261, UnknownDirection)),
        List(RoadLinkInfo(newLinkId, 734.309, List(Point(410580.098, 7524656.363, 186.118), Point(410803.855, 7525268.116, 186.353)),
          12316, Unknown, 261, UnknownDirection)),
        List(ReplaceInfo(Option(oldLinkId1), Option(newLinkId), Option(0.0), Option(555.312), Option(0.0), Option(555.312), false))),
      RoadLinkChange(Replace, Some(RoadLinkInfo(oldLinkId2, 178.997,
        List(Point(378461.027, 6674230.896, 1.993), Point(378521.11, 6674258.813, 6.9)), 12314, Municipality, 49, BothDirections)),
        List(RoadLinkInfo(newLinkId, 734.309, List(Point(410580.098, 7524656.363, 186.118), Point(410803.855, 7525268.116, 186.353)),
          12316, Unknown, 261, UnknownDirection)),
        List(ReplaceInfo(Option(oldLinkId2), Option(newLinkId), Option(0.0), Option(178.997), Option(555.312), Option(734.309), false)))
    )
    runWithRollback {
      RoadLinkOverrideDAO.insert(FunctionalClass, oldLinkId1, Some("test"), 7)
      RoadLinkOverrideDAO.insert(FunctionalClass, oldLinkId2, Some("test"), 7)
      RoadLinkOverrideDAO.insert(LinkType, oldLinkId1, Some("test"), 1)
      RoadLinkOverrideDAO.insert(LinkType, oldLinkId2, Some("test"), 1)
      val transferredProperties = roadLinkPropertyUpdater.transferOverriddenPropertiesAndPrivateRoadInfo(mergeChanges)
      val createdProperties = roadLinkPropertyUpdater.transferOrGenerateFunctionalClassesAndLinkTypes(mergeChanges)
      transferredProperties.size should be(0)
      createdProperties.size should be(2)
      roadLinkService.getIncompleteLinks(None, false).size should be(0)
    }
  }

  test("Given a merge after a split; When old links are not missing information; Then no incomplete link should be generated") {
    val oldLinkId1 = "73565cbb-3009-4ba7-a779-44f93f09b516:1"
    val oldLinkId2 = "0382390e-f382-40f8-a442-677685b6e00e:1"
    val newLinkId1 = "6d391a6d-3420-4b7e-a4e9-33a56dd12291:1"
    val newLinkId2 = "de4a484f-5e96-46b2-8da7-4aaae50c23e5:1"
    val relevantChanges = Seq(
      RoadLinkChange(Replace, Some(RoadLinkInfo(oldLinkId1, 555.312,
        List(Point(410580.098, 7524656.363, 186.118), Point(410698.996, 7525137.579, 186.329)), 12316, Private, 261, UnknownDirection)),
        List(RoadLinkInfo(newLinkId1, 734.309, List(Point(410580.098, 7524656.363, 186.118), Point(410803.855, 7525268.116, 186.353)),
          12316, Unknown, 261, UnknownDirection)),
        List(ReplaceInfo(Option(oldLinkId1), Option(newLinkId1), Option(0.0), Option(555.312), Option(0.0), Option(555.312), false))),
      RoadLinkChange(Split, Some(RoadLinkInfo(oldLinkId2, 178.997,
        List(Point(378461.027, 6674230.896, 1.993), Point(378521.11, 6674258.813, 6.9)), 12314, Municipality, 49, BothDirections)),
        List(RoadLinkInfo(newLinkId1, 734.309, List(Point(410580.098, 7524656.363, 186.118), Point(410803.855, 7525268.116, 186.353)),
          12316, Unknown, 261, UnknownDirection),
          RoadLinkInfo(newLinkId2, 734.309, List(Point(410580.098, 7524656.363, 186.118), Point(410803.855, 7525268.116, 186.353)),
            12316, Unknown, 261, UnknownDirection)),
        List(ReplaceInfo(Option(oldLinkId2), Option(newLinkId1), Option(0.0), Option(178.997), Option(555.312), Option(734.309), false),
          ReplaceInfo(Option(oldLinkId2), Option(newLinkId2), Option(0.0), Option(178.997), Option(555.312), Option(734.309), false)))
    )
    runWithRollback {
      RoadLinkOverrideDAO.insert(FunctionalClass, oldLinkId1, Some("test"), 7)
      RoadLinkOverrideDAO.insert(FunctionalClass, oldLinkId2, Some("test"), 7)
      RoadLinkOverrideDAO.insert(LinkType, oldLinkId1, Some("test"), 1)
      RoadLinkOverrideDAO.insert(LinkType, oldLinkId2, Some("test"), 1)
      val transferredProperties = roadLinkPropertyUpdater.transferOverriddenPropertiesAndPrivateRoadInfo(relevantChanges)
      val createdProperties = roadLinkPropertyUpdater.transferOrGenerateFunctionalClassesAndLinkTypes(relevantChanges)
      transferredProperties.size should be(0)
      createdProperties.size should be(4)
      roadLinkService.getIncompleteLinks(None, false).size should be(0)
    }
  }

  test("Given a new road link; When new functional class and link type are not created due to RoadClass value; Then an incomplete link should be generated") {
    val newLinkId = "eea524dd-e371-47e3-9d58-44272ccf9db0:1"
    val relevantChanges = Seq(
      RoadLinkChange(Add, None,
        List(RoadLinkInfo(newLinkId, 20.311, List(Point(238192.995, 6716501.977, 31.415), Point(238197.49, 6716521.779, 31.56)),
          12121, State, 680, UnknownDirection)),
        List(ReplaceInfo(None, Option(newLinkId), None, None, Option(0.0), Option(20.311), false)))
    )
    runWithRollback {
      val createdProperties = roadLinkPropertyUpdater.transferOrGenerateFunctionalClassesAndLinkTypes(relevantChanges)
      createdProperties.size should be(0)
      val incompleteLinks = roadLinkService.getIncompleteLinks(None, false)
      incompleteLinks.size should be(1)
    }
  }

  test("Given a new road link; When new link already has functional class and link type; Then no properties or incomplete link should be generated") {
    val newLinkId = "eea524dd-e371-47e3-9d58-44272ccf9db0:1"
    val relevantChanges = Seq(
      RoadLinkChange(Add, None,
        List(RoadLinkInfo(newLinkId, 20.311, List(Point(238192.995, 6716501.977, 31.415), Point(238197.49, 6716521.779, 31.56)),
          12121, State, 680, UnknownDirection)),
        List(ReplaceInfo(None, Option(newLinkId), None, None, Option(0.0), Option(20.311), false)))
    )
    runWithRollback {
      RoadLinkOverrideDAO.insert(FunctionalClass, newLinkId, Some("test"), 7)
      RoadLinkOverrideDAO.insert(LinkType, newLinkId, Some("test"), 1)
      val createdProperties = roadLinkPropertyUpdater.transferOrGenerateFunctionalClassesAndLinkTypes(relevantChanges)
      createdProperties.size should be(0)
      val incompleteLinks = roadLinkService.getIncompleteLinks(None, false)
      incompleteLinks.size should be(0)
    }
  }

  test("Given two splits with a shared new link; When old links are not missing information; Then no incomplete link should be generated") {
    val newLinkId1 = "fba10b89-94f3-4857-95a9-ae8d3c1c276d:1"
    val newLinkId2 = "cd4f0b7f-e916-4b6a-99ac-3c56516b691c:1"
    val newLinkId3 = "b3539f88-88ac-4a92-8582-ef012cb0dbf3:1"
    val oldLinkId1 = "a5eb0323-1b71-4a00-8c4c-9386d311fa30:1"
    val oldLinkId2 = "cba1aef9-3c3d-4f50-aed2-f9eb0359b648:1"
    val relevantChanges = Seq(
      RoadLinkChange(
        Split, Some(RoadLinkInfo(oldLinkId1, 137.767,
        List(Point(477310.959, 7343262.118, 179.727), Point(477258.906, 7343389.476, 178.035)), 12132, Private, 698, UnknownDirection)),
        List(RoadLinkInfo(newLinkId1, 196.95, List(Point(410580.098, 7524656.363, 186.118), Point(410803.855, 7525268.116, 186.353)),
          12132, Private, 698, UnknownDirection),
          RoadLinkInfo(newLinkId2, 82.995, List(Point(410580.098, 7524656.363, 186.118), Point(410803.855, 7525268.116, 186.353)),
            12132, Private, 698, UnknownDirection)),
        List(ReplaceInfo(Option(oldLinkId1), Option(newLinkId1), Option(0.0), Option(555.312), Option(0.0), Option(555.312), false),
          ReplaceInfo(Option(oldLinkId1), Option(newLinkId2), Option(0.0), Option(555.312), Option(0.0), Option(555.312), false))),
      RoadLinkChange(
        Split, Some(RoadLinkInfo(oldLinkId2, 483.503,
        List(Point(378461.027, 6674230.896, 1.993), Point(378521.11, 6674258.813, 6.9)), 12132, State, 698, UnknownDirection)),
        List(RoadLinkInfo(newLinkId1, 196.95, List(Point(410580.098, 7524656.363, 186.118), Point(410803.855, 7525268.116, 186.353)),
          12132, State, 698, UnknownDirection),
          RoadLinkInfo(newLinkId3, 341.261, List(Point(410580.098, 7524656.363, 186.118), Point(410803.855, 7525268.116, 186.353)),
            12132, State, 698, UnknownDirection)),
        List(ReplaceInfo(Option(oldLinkId2), Option(newLinkId1), Option(0.0), Option(178.997), Option(555.312), Option(734.309), false),
          ReplaceInfo(Option(oldLinkId2), Option(newLinkId3), Option(0.0), Option(178.997), Option(555.312), Option(734.309), false)))
    )
    runWithRollback {
      RoadLinkOverrideDAO.insert(FunctionalClass, oldLinkId1, Some("test"), 7)
      RoadLinkOverrideDAO.insert(FunctionalClass, oldLinkId2, Some("test"), 7)
      RoadLinkOverrideDAO.insert(LinkType, oldLinkId1, Some("test"), 1)
      RoadLinkOverrideDAO.insert(LinkType, oldLinkId2, Some("test"), 1)
      val createdProperties = roadLinkPropertyUpdater.transferOrGenerateFunctionalClassesAndLinkTypes(relevantChanges)
      createdProperties.size should be(6)
      val incompleteLinks = roadLinkService.getIncompleteLinks(None, false)
      incompleteLinks.size should be(0)
    }
  }

  test("no link type or functional class should be transferred or generated when road class changes to hard shoulder") {
    val oldLinkId = "c51f721d-5c0b-4cd2-b6a5-6a0df207a8fe:1"
    val newLinkId = "ed66eaf4-c0b7-4356-86fb-10cf0624bf8a:1"
    val change = RoadLinkChange(Replace,Some(RoadLinkInfo(oldLinkId,119.687,
      List(Point(339271.771,6971427.195,153.038), Point(339265.3400000001,6971546.706,152.451)),12316,Unknown,5,BothDirections, SurfaceType.None)),
      List(RoadLinkInfo(newLinkId,119.687,List(Point(339271.771,6971427.195,153.038), Point(339265.3400000001,6971546.706,152.451)),
        12318,Unknown,5,BothDirections,SurfaceType.None)),List(ReplaceInfo(Some(oldLinkId), Some(newLinkId),Some(0.0),Some(119.687),Some(0.0),Some(119.687),false)))
    runWithRollback {
      RoadLinkOverrideDAO.insert(FunctionalClass, oldLinkId, Some("test"), 7)
      RoadLinkOverrideDAO.insert(LinkType, oldLinkId, Some("test"), 12)
      val createdProperties = roadLinkPropertyUpdater.transferOrGenerateFunctionalClassesAndLinkTypes(Seq(change))
      createdProperties.size should be(0)
    }
  }
}

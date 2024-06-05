package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.{DynamicAssetValue, DynamicValue, NewLinearAsset, RoadLink}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.RoadWidthService
import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer, Point}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class RoadWidthGeneratorSpec extends FunSuite with Matchers {

  val dao = new PostGISLinearAssetDao()
  val roadLinkClient = new RoadLinkClient()
  val roadWidthService = new RoadWidthService(new RoadLinkService(roadLinkClient, new DummyEventBus, new DummySerializer), new DummyEventBus)
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  object TestRoadWidthGenerator extends RoadWidthGenerator {
    override val roadLinkService = mockRoadLinkService
  }

  val (linkId1, linkId2, linkId3, linkId4) = (LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom(),
    LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom())

  val carRoad1 = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(1.5, 0.0)), 1.5, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MTKCLASS" -> 12111, "MUNICIPALITYCODE" -> BigInt(235)))
  val carRoad2 = RoadLink(linkId2, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Private,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MTKCLASS" -> 12121, "MUNICIPALITYCODE" -> BigInt(235)))
  val pedestrianRoad = RoadLink(linkId3, Seq(Point(0.0, 0.0), Point(11.5, 0.0)), 11.5, Municipality,
    1, TrafficDirection.BothDirections, CycleOrPedestrianPath, None, None, Map("MTKCLASS" -> 12314, "MUNICIPALITYCODE" -> BigInt(235)))
  val stateRoad = RoadLink(linkId4, Seq(Point(0.0, 0.0), Point(12.5, 0.0)), 12.5, State,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MTKCLASS" -> 12121, "MUNICIPALITYCODE" -> BigInt(235)))

  val roadLinks = Seq(carRoad1, carRoad2, pedestrianRoad, stateRoad)

  when(mockRoadLinkService.getRoadLinksByMunicipality(235)).thenReturn(roadLinks)

  test("update correct widths to road links excluding pedestrian and state roads") {

    runWithRollback {
      TestRoadWidthGenerator.fillRoadWidthsByMunicipality(235)
      val roadWidths = roadWidthService.fetchExistingAssetsByLinksIds(RoadWidth.typeId, roadLinks, Seq(), false).sortBy(_.endMeasure)
      roadWidths.size should be(2)
      roadWidths.head.linkId should be(linkId1)
      roadWidths.head.startMeasure should be(0.0)
      roadWidths.head.endMeasure should be(1.5)
      roadWidths.head.value.get.asInstanceOf[DynamicValue].value.properties.find(_.publicId == "width").get.values.head.value should be("1100")
      roadWidths.last.linkId should be(linkId2)
      roadWidths.last.startMeasure should be(0.0)
      roadWidths.last.endMeasure should be(10.0)
      roadWidths.last.value.get.asInstanceOf[DynamicValue].value.properties.find(_.publicId == "width").get.values.head.value should be("650")
    }
  }

  test("update correct widths to only road links that lack road width") {

    runWithRollback {
      val newAsset1 = NewLinearAsset(linkId2, 0.0, 5.0, DynamicValue(DynamicAssetValue(List(DynamicProperty("suggest_box","checkbox",false,List()),
        DynamicProperty("width","integer",true,List(DynamicPropertyValue(850)))))), 1, 234567, None)
      val newAsset2 = NewLinearAsset(linkId2, 5.0, 10.0, DynamicValue(DynamicAssetValue(List(DynamicProperty("suggest_box","checkbox",false,List()),
        DynamicProperty("width","integer",true,List(DynamicPropertyValue(800)))))), 1, 234567, None)
      roadWidthService.create(Seq(newAsset1, newAsset2), RoadWidth.typeId, "test")
      TestRoadWidthGenerator.fillRoadWidthsByMunicipality(235)
      val roadWidths = roadWidthService.fetchExistingAssetsByLinksIds(RoadWidth.typeId, roadLinks, Seq(), false).sortBy(_.endMeasure)
      roadWidths.size should be(3)
      roadWidths.head.linkId should be(linkId1)
      roadWidths.head.startMeasure should be(0.0)
      roadWidths.head.endMeasure should be(1.5)
      roadWidths.head.value.get.asInstanceOf[DynamicValue].value.properties.find(_.publicId == "width").get.values.head.value should be("1100")
      roadWidths(1).linkId should be(linkId2)
      roadWidths(1).startMeasure should be(0.0)
      roadWidths(1).endMeasure should be(5.0)
      roadWidths(1).value.get.asInstanceOf[DynamicValue].value.properties.find(_.publicId == "width").get.values.head.value should be("850")
      roadWidths.last.linkId should be(linkId2)
      roadWidths.last.startMeasure should be(5.0)
      roadWidths.last.endMeasure should be(10.0)
      roadWidths.last.value.get.asInstanceOf[DynamicValue].value.properties.find(_.publicId == "width").get.values.head.value should be("800")
    }
  }
}

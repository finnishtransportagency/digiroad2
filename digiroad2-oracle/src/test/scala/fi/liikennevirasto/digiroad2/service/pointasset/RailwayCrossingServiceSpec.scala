package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, VVHRoadlink}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util.TestTransactions
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class RailwayCrossingServiceSpec extends FunSuite with Matchers {
  def toRoadLink(l: VVHRoadlink) = {
    RoadLink(l.linkId, l.geometry, GeometryUtils.geometryLength(l.geometry),
      l.administrativeClass, 1, l.trafficDirection, UnknownLinkType, None, None, l.attributes + ("MUNICIPALITYCODE" -> BigInt(l.municipalityCode)))
  }

  val testUser = User(
    id = 1,
    username = "Hannu",
    configuration = Configuration(authorizedMunicipalities = Set(235)))
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  when(mockRoadLinkService.getRoadLinksFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn(Seq(
    VVHRoadlink(1611317, 235, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Municipality,
      TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink))
  when(mockRoadLinkService.getRoadLinkByLinkIdFromVVH(any[Long], any[Boolean])).thenReturn(Seq(
    VVHRoadlink(1611317, 235, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Municipality,
      TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink).headOption)

  val service = new RailwayCrossingService(mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = f

    override def withDynSession[T](f: => T): T = f
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(service.dataSource)(test)

  test("Can fetch by bounding box") {
    when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((List(), Nil))

    runWithRollback {
      val result = service.getByBoundingBox(testUser, BoundingRectangle(Point(374466.5, 6677346.5), Point(374467.5, 6677347.5))).head
      Set(600049, 600050, 600051) should contain (result.id)
      result.linkId should equal(1611317)
      result.lon should equal(374467)
      result.lat should equal(6677347)
      result.mValue should equal(103)
    }
  }

  test("Can fetch by municipality") {
    when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(235)).thenReturn((Seq(
      VVHRoadlink(1611317, 235, Seq(Point(0.0, 0.0), Point(200.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink), Nil))

    runWithRollback {
      val result = service.getByMunicipality(235).find(_.id == 600051).get

      result.id should equal(600051)
      result.linkId should equal(12345)
      result.lon should equal(374396)
      result.lat should equal(6677319)
      result.mValue should equal(103)
      result.createdAt shouldBe defined
    }
  }

  test("Expire railway crossing") {
    when(mockRoadLinkService.getRoadLinksFromVVH(235)).thenReturn(Seq(
      VVHRoadlink(1611317, 235, Seq(Point(0.0, 0.0), Point(200.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink))

    runWithRollback {
      val result = service.getByMunicipality(235).find(_.id == 600051).get
      result.id should equal(600051)

      service.expire(600051, "unit_test")

      service.getByMunicipality(235).find(_.id == 600051) should equal(None)
    }
  }

  test("Update railway crossing with geometry changes"){
    runWithRollback {
      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(0.0, 20.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val codeValue = TextPropertyValue("")
      val safetyEquipmentValue = TextPropertyValue("1")
      val nameValue = TextPropertyValue("testCode")
      val simpleCodeProperty = SimplePointAssetProperty("tasoristeystunnus", Seq(codeValue))
      val simpleSafetyEquipmentProperty = SimplePointAssetProperty("turvavarustus", Seq(safetyEquipmentValue))
      val simpleNameProperty = SimplePointAssetProperty("rautatien_tasoristeyksen_nimi", Seq(nameValue))
      val id = service.create(IncomingRailwayCrossing(0.0, 20.0, 388553075, Set(simpleCodeProperty, simpleSafetyEquipmentProperty, simpleNameProperty)), "jakke", roadLink )
      val oldAsset = service.getPersistedAssetsByIds(Set(id)).head
      oldAsset.modifiedAt.isDefined should equal(false)

      val updatedCodeValue = TextPropertyValue("")
      val updatedSafetyEquipmentValue = TextPropertyValue("2")
      val updatedNameValue = TextPropertyValue("testCode")
      val updatedSimpleCodeProperty = SimplePointAssetProperty("tasoristeystunnus", Seq(updatedCodeValue))
      val updatedSimpleSafetyEquipmentProperty = SimplePointAssetProperty("turvavarustus", Seq(updatedSafetyEquipmentValue))
      val updatedSimpleNameProperty = SimplePointAssetProperty("rautatien_tasoristeyksen_nimi", Seq(updatedNameValue))
      val newId = service.update(id, IncomingRailwayCrossing(0.0, 10.0, 388553075, Set(updatedSimpleCodeProperty, updatedSimpleNameProperty, updatedSimpleSafetyEquipmentProperty)), roadLink, "test")

      val updatedAsset = service.getPersistedAssetsByIds(Set(newId)).head
      updatedAsset.id should not be id
      updatedAsset.lon should equal (0.0)
      updatedAsset.lat should equal (10.0)
      updatedAsset.propertyData.find(_.publicId == "turvavarustus").get.values.head.asInstanceOf[TextPropertyValue].propertyValue.toInt should equal(2)
      updatedAsset.createdBy should equal (oldAsset.createdBy)
      updatedAsset.createdAt should equal (oldAsset.createdAt)
      updatedAsset.modifiedBy should equal (Some("test"))
      updatedAsset.modifiedAt.isDefined should equal(true)
    }
  }

  test("Update railway crossing without geometry changes"){
    runWithRollback {
      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(0.0, 20.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val codeValue = TextPropertyValue("")
      val safetyEquipmentValue = TextPropertyValue("1")
      val nameValue = TextPropertyValue("testCode")
      val simpleCodeProperty = SimplePointAssetProperty("tasoristeystunnus", Seq(codeValue))
      val simpleSafetyEquipmentProperty = SimplePointAssetProperty("turvavarustus", Seq(safetyEquipmentValue))
      val simpleNameProperty = SimplePointAssetProperty("rautatien_tasoristeyksen_nimi", Seq(nameValue))
      val id = service.create(IncomingRailwayCrossing(0.0, 20.0, 388553075, Set(simpleCodeProperty, simpleSafetyEquipmentProperty, simpleNameProperty)), "jakke", roadLink )
      val asset = service.getPersistedAssetsByIds(Set(id)).head

      val updatedCodeValue = TextPropertyValue("")
      val updatedSafetyEquipmentValue = TextPropertyValue("2")
      val updatedNameValue = TextPropertyValue("testCode")
      val updatedSimpleCodeProperty = SimplePointAssetProperty("tasoristeystunnus", Seq(updatedCodeValue))
      val updatedSimpleSafetyEquipmentProperty = SimplePointAssetProperty("turvavarustus", Seq(updatedSafetyEquipmentValue))
      val updatedSimpleNameProperty = SimplePointAssetProperty("rautatien_tasoristeyksen_nimi", Seq(updatedNameValue))
      val newId = service.update(id, IncomingRailwayCrossing(0.0, 20.0, 388553075, Set(updatedSimpleCodeProperty, updatedSimpleNameProperty, updatedSimpleSafetyEquipmentProperty)), roadLink, "test")

      val updatedAsset = service.getPersistedAssetsByIds(Set(newId)).head
      updatedAsset.id should be (id)
      updatedAsset.lon should be (asset.lon)
      updatedAsset.lat should be (asset.lat)
      updatedAsset.createdBy should equal (Some("jakke"))
      updatedAsset.modifiedBy should equal (Some("test"))
    }
  }
}

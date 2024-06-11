package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{FeatureClass, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.dao.pointasset.TrafficLight
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util.{LinkIdGenerator, TestTransactions}
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class TrafficLightServiceSpec  extends FunSuite with Matchers {
  def toRoadLink(l: RoadLinkFetched) = {
    RoadLink(l.linkId, l.geometry, GeometryUtils.geometryLength(l.geometry),
      l.administrativeClass, 1, l.trafficDirection, UnknownLinkType, None, None, l.attributes + ("MUNICIPALITYCODE" -> BigInt(l.municipalityCode)))
  }
  val testUser = User(
    id = 1,
    username = "Hannu",
    configuration = Configuration(authorizedMunicipalities = Set(235)))

  val testLinkId1 = "52d58ce5-39e8-4ab4-8c43-d347a9945ab5:1"
  val testLinkId2 = "a2c8e119-5739-456a-aa6c-cba0f300cc3c:1"
  val randomLinkId1: String = LinkIdGenerator.generateRandom()

  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  when(mockRoadLinkService.getRoadLinksByBoundsAndMunicipalities(any[BoundingRectangle], any[Set[Int]],any[Boolean])).thenReturn(Seq(
    RoadLinkFetched(testLinkId1, 235, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Municipality,
      TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink))

  val trafficLightPropertyData = Set(
    SimplePointAssetProperty("trafficLight_type", List(PropertyValue("1")), 1),
    SimplePointAssetProperty("trafficLight_relative_position", List(PropertyValue("1")), 1),
    SimplePointAssetProperty("trafficLight_structure", List(PropertyValue("99")), 1),
    SimplePointAssetProperty("trafficLight_height", List(PropertyValue("")), 1),
    SimplePointAssetProperty("trafficLight_sound_signal", List(PropertyValue("99")), 1),
    SimplePointAssetProperty("trafficLight_vehicle_detection", List(PropertyValue("99")), 1),
    SimplePointAssetProperty("trafficLight_push_button", List(PropertyValue("99")), 1),
    SimplePointAssetProperty("trafficLight_info", List(PropertyValue("")), 1),
    SimplePointAssetProperty("trafficLight_lane_type", List(PropertyValue("99")), 1),
    SimplePointAssetProperty("trafficLight_lane", List(PropertyValue("")), 1),
    SimplePointAssetProperty("location_coordinates_x", List(PropertyValue("")), 1),
    SimplePointAssetProperty("location_coordinates_y", List(PropertyValue("")), 1),
    SimplePointAssetProperty("trafficLight_municipality_id", List(PropertyValue("")), 1),
    SimplePointAssetProperty("trafficLight_state", List(PropertyValue("3")), 1),
    SimplePointAssetProperty("suggest_box", List(PropertyValue("0")), 1),
    SimplePointAssetProperty("bearing", List(PropertyValue("")), 1),
    SimplePointAssetProperty("sidecode", List(PropertyValue("2")), 1)
  )

  val secondPropertiesSet = Set(
    SimplePointAssetProperty("trafficLight_type", List(PropertyValue("4.1")), 2),
    SimplePointAssetProperty("trafficLight_relative_position", List(PropertyValue("2")), 2),
    SimplePointAssetProperty("trafficLight_structure", List(PropertyValue("3")), 2),
    SimplePointAssetProperty("trafficLight_height", List(PropertyValue("")), 2),
    SimplePointAssetProperty("trafficLight_sound_signal", List(PropertyValue("1")), 2),
    SimplePointAssetProperty("trafficLight_vehicle_detection", List(PropertyValue("99")), 2),
    SimplePointAssetProperty("trafficLight_push_button", List(PropertyValue("99")), 2),
    SimplePointAssetProperty("trafficLight_info", List(PropertyValue("")), 2),
    SimplePointAssetProperty("trafficLight_lane_type", List(PropertyValue("99")), 2),
    SimplePointAssetProperty("trafficLight_lane", List(PropertyValue("")), 2),
    SimplePointAssetProperty("location_coordinates_x", List(PropertyValue("")), 2),
    SimplePointAssetProperty("location_coordinates_y", List(PropertyValue("")), 2),
    SimplePointAssetProperty("trafficLight_municipality_id", List(PropertyValue("")), 2),
    SimplePointAssetProperty("trafficLight_state", List(PropertyValue("3")), 2),
    SimplePointAssetProperty("suggest_box", List(PropertyValue("0")), 2),
    SimplePointAssetProperty("bearing", List(PropertyValue("")), 2),
    SimplePointAssetProperty("sidecode", List(PropertyValue("2")), 2)
  )

  val service = new TrafficLightService(mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(PostGISDatabase.ds)(test)

  test("Can fetch by bounding box") {
    when(mockRoadLinkService.getRoadLinksWithComplementary(any[BoundingRectangle], any[Set[Int]], any[Boolean],any[Boolean])).thenReturn((List()))

    runWithRollback {
      val result = service.getByBoundingBox(testUser, BoundingRectangle(Point(374101, 6677437), Point(374102, 6677438))).head
      result.id should equal(600070)
      result.linkId should equal(testLinkId2)
      result.lon should equal(374101.60105163435)
      result.lat should equal(6677437.872017591)
      result.mValue should equal(16.592)
    }
  }

  test("Can fetch by municipality") {
    val linkId = testLinkId2
    when(mockRoadLinkService.getRoadLinksWithComplementary(235)).thenReturn((Seq(
      RoadLinkFetched(linkId, 235, Seq(Point(0.0, 0.0), Point(200.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink)))

    runWithRollback {
      val result = service.getByMunicipality(235).find(_.id == 600070).get

      result.id should equal(600070)
      result.linkId should equal(linkId)
      result.lon should equal(374101.60105163435)
      result.lat should equal(6677437.872017591)
      result.mValue should equal(16.592)
    }
  }

  test("Expire traffic light") {
    runWithRollback {
      val roadLink = RoadLink(randomLinkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingTrafficLight(2, 0.0, randomLinkId1, trafficLightPropertyData), "jakke", roadLink)
      service.getPersistedAssetsByIds(Set(id)).length should be(1)
      service.expire(id, testUser.username)
      service.getPersistedAssetsByIds(Set(id)) should be(Nil)
    }
  }

  test("Create new") {
    runWithRollback {
      val linkId = randomLinkId1
      val roadLink = RoadLink(linkId, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingTrafficLight(2, 0.0, linkId, trafficLightPropertyData), "jakke", roadLink)
      val assets = service.getPersistedAssetsByIds(Set(id))

      assets.size should be(1)

      val asset = assets.head

      asset.timeStamp should not be(0)

      val trafficLightTypeProperty = asset.propertyData.find(_.publicId == "trafficLight_type").head
      val assetGroupedId = trafficLightTypeProperty.groupedId
      val suggestProperty = asset.propertyData.find(_.publicId == "suggest_box").head

      val pointAssetProperties = Seq(
        Property(suggestProperty.id, "suggest_box", "checkbox", false, Seq(PropertyValue("0", Some("Tarkistettu"))), groupedId = assetGroupedId),
        Property(trafficLightTypeProperty.id, "trafficLight_type", "single_choice", true, Seq(PropertyValue("1", Some("Valo-opastin"))), groupedId = assetGroupedId)
      )

      asset.id should be(id)
      asset.linkId should be(linkId)
      asset.lon should be(2)
      asset.lat should be(0)
      asset.mValue should be(2)
      asset.municipalityCode should be(235)
      asset.propertyData.find(_.publicId == "suggest_box") should be (Some(pointAssetProperties.head))
      asset.propertyData.find(_.publicId == "trafficLight_type") should be (Some(pointAssetProperties.last))
      asset.propertyData.find(_.publicId == "trafficLight_sound_signal").get.groupedId should be (assetGroupedId)
      asset.propertyData.find(_.publicId == "trafficLight_structure").get.groupedId should be (assetGroupedId)
      asset.propertyData.find(_.publicId == "trafficLight_state").get.groupedId should be (assetGroupedId)
      asset.createdBy should be(Some("jakke"))
    }
  }

  test("Create multiple in one asset") {
    runWithRollback {
      val linkId = randomLinkId1
      val roadLink = RoadLink(linkId, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingTrafficLight(2, 0.0, linkId, trafficLightPropertyData ++ secondPropertiesSet), "jakke", roadLink)
      val assets = service.getPersistedAssetsByIds(Set(id))

      assets.size should be(1)

      val asset = assets.head

      asset.timeStamp should not be(0)

      val trafficLightTypeProperties = asset.propertyData.filter(_.publicId == "trafficLight_type")
      val assetGroupedIds = trafficLightTypeProperties.map(_.groupedId)
      val (firstGroupId, secondGroupId) = (assetGroupedIds.head, assetGroupedIds.last)

      val firstTypeProperty = asset.propertyData.find(property => property.publicId == "trafficLight_type" && property.groupedId == firstGroupId)
      val secondTypeProperty = asset.propertyData.find(property => property.publicId == "trafficLight_type" && property.groupedId == secondGroupId)

      asset.id should be(id)
      asset.propertyData.size should  be(34)
      firstTypeProperty should not be secondTypeProperty
      firstTypeProperty.get.values.head.asInstanceOf[PropertyValue].propertyValue should not be(secondTypeProperty.get.values.head.asInstanceOf[PropertyValue].propertyValue)

      val correctTypes = Seq("1", "4.1")
      correctTypes should contain (firstTypeProperty.get.values.head.asInstanceOf[PropertyValue].propertyValue)
      correctTypes should contain (secondTypeProperty.get.values.head.asInstanceOf[PropertyValue].propertyValue)
    }
  }

  test("update asset delete one of multiple") {
    runWithRollback {
      val linkId = randomLinkId1
      val roadLink = RoadLink(linkId, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingTrafficLight(2, 0.0, linkId, trafficLightPropertyData ++ secondPropertiesSet), "jakke", roadLink)
      val assets = service.getPersistedAssetsByIds(Set(id))

      assets.size should be(1)

      val asset = assets.head

      asset.timeStamp should not be(0)

     val updatedId = service.update(id = asset.id, IncomingTrafficLight(2, 0.0, linkId, trafficLightPropertyData), roadLink, "test")
      val afterUpdate = service.getPersistedAssetsByIds(Set(updatedId)).head

      afterUpdate.id should be(updatedId)
      afterUpdate.propertyData.size should  be(17)
      afterUpdate.propertyData.find(_.publicId == "trafficLight_type").get.values.head.asInstanceOf[PropertyValue].propertyValue should be("1")
    }
  }

  test("Update traffic light") {
    val linkGeometry = Seq(Point(0.0, 0.0), Point(200.0, 0.0))
    val linkIdBeforeUpdate = testLinkId2
    val linkIdAfterUpdate = LinkIdGenerator.generateRandom()
    when(mockRoadLinkService.getRoadLinksWithComplementary(235)).thenReturn((Seq(
      RoadLinkFetched(linkIdBeforeUpdate, 235, linkGeometry, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink)))
    when(mockRoadLinkService.getRoadLinksWithComplementary(91)).thenReturn((Seq(
      RoadLinkFetched(linkIdAfterUpdate, 91, linkGeometry, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink)))

    val roadLink = RoadLink(linkIdAfterUpdate, linkGeometry, 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)))

    runWithRollback {
      val beforeUpdate = service.getByMunicipality(235).find(_.id == 600070).get
      beforeUpdate.id should equal(600070)
      beforeUpdate.lon should equal(374101.60105163435)
      beforeUpdate.lat should equal(6677437.872017591)
      beforeUpdate.mValue should equal(16.592)
      beforeUpdate.linkId should equal(linkIdBeforeUpdate)
      beforeUpdate.municipalityCode should equal(235)
      beforeUpdate.createdBy should equal(Some("dr2_test_data"))
      beforeUpdate.createdAt.isDefined should equal(true)
      beforeUpdate.modifiedBy should equal(None)
      beforeUpdate.modifiedAt should equal(None)

      val newAssetId = service.update(id = 600070, IncomingTrafficLight(100, 0, linkIdAfterUpdate, Set()), roadLink, "test")

      val afterUpdate = service.getByMunicipality(91).find(_.id == newAssetId).get
      afterUpdate.id should equal(newAssetId)
      afterUpdate.lon should equal(100)
      afterUpdate.lat should equal(0)
      afterUpdate.mValue should equal(100)
      afterUpdate.linkId should equal(linkIdAfterUpdate)
      afterUpdate.municipalityCode should equal(91)
      afterUpdate.createdBy should equal(beforeUpdate.createdBy)
      afterUpdate.createdAt should equal(beforeUpdate.createdAt)
      afterUpdate.modifiedBy should equal(Some("test"))
      afterUpdate.modifiedAt.isDefined should equal(true)
    }
  }

  test("Update traffic light with geometry changes"){
    runWithRollback {
      val linkId = randomLinkId1
      val roadLink = RoadLink(linkId, Seq(Point(5.0, 0.0), Point(0.0, 20.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingTrafficLight(0.0, 20.0, linkId, Set()), "jakke", roadLink )
      val oldAsset = service.getPersistedAssetsByIds(Set(id)).head
      oldAsset.modifiedAt.isDefined should equal(false)
      val newId = service.update(id, IncomingTrafficLight(2.5, 10.0, linkId, Set()), roadLink, "test")

      val updatedAsset = service.getPersistedAssetsByIds(Set(newId)).head
      updatedAsset.id should not be id
      updatedAsset.lon should equal (2.5)
      updatedAsset.lat should equal (10.0)
      updatedAsset.createdBy should equal (oldAsset.createdBy)
      updatedAsset.createdAt should equal (oldAsset.createdAt)
      updatedAsset.modifiedBy should equal (Some("test"))
      updatedAsset.modifiedAt.isDefined should equal(true)
    }
  }

  test("Update traffic light without geometry changes"){
    runWithRollback {
      val linkId = randomLinkId1
      val roadLink = RoadLink(linkId, Seq(Point(10.0, 40.0), Point(0.0, 20.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingTrafficLight(6.0, 28.0, linkId, Set()), "jakke", roadLink )
      val asset = service.getPersistedAssetsByIds(Set(id)).head

      val newId = service.update(id, IncomingTrafficLight(asset.lon, asset.lat, linkId, Set(), asset.getValidityDirection, asset.getBearing, Some(asset.mValue)), roadLink,  "test")

      val updatedAsset = service.getPersistedAssetsByIds(Set(newId)).head
      updatedAsset.id should be (id)
      updatedAsset.lon should be (asset.lon)
      updatedAsset.lat should be (asset.lat)
      updatedAsset.mValue should be (asset.mValue)
      updatedAsset.createdBy should equal (Some("jakke"))
      updatedAsset.modifiedBy should equal (Some("test"))
    }
  }
}


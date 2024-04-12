package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{FeatureClass, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util.{LinkIdGenerator, TestTransactions}
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class DirectionalTrafficSignServiceSpec extends FunSuite with Matchers {
  def toRoadLink(l: RoadLinkFetched) = {
    RoadLink(l.linkId, l.geometry, GeometryUtils.geometryLength(l.geometry),
      l.administrativeClass, 1, l.trafficDirection, UnknownLinkType, None, None, l.attributes + ("MUNICIPALITYCODE" -> BigInt(l.municipalityCode)))
  }

  val testUser = User(
    id = 1,
    username = "Hannu",
    configuration = Configuration(authorizedMunicipalities = Set(235)))
  val linkId = "52d58ce5-39e8-4ab4-8c43-d347a9945ab5:1"

  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  when(mockRoadLinkService.getRoadLinksWithComplementaryByBoundsAndMunicipalities(any[BoundingRectangle], any[Set[Int]], any[Boolean],any[Boolean])).thenReturn(
    Seq(
    RoadLinkFetched(linkId, 235, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Municipality,
      TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink))

  val service = new DirectionalTrafficSignService(mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(PostGISDatabase.ds)(test)

    test("Can fetch by bounding box") {
      runWithRollback {
        val result = service.getByBoundingBox(testUser, BoundingRectangle(Point(374466.5, 6677346.5), Point(374467.5, 6677347.5))).head
        result.id should equal(600053)
        result.linkId should equal(linkId)
        result.lon should equal(374467)
        result.lat should equal(6677347)
        result.mValue should equal(103)
        result.propertyData.find(_.publicId == "opastustaulun_teksti").get.values.head.asInstanceOf[PropertyValue].propertyValue should equal("HELSINKI:HELSINGFORS;;;;1;1;")
      }
    }


  test("Create new") {
    runWithRollback {
      val roadLink = RoadLink(linkId, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val textValues = Seq(PropertyValue("HELSINKI:HELSINGFORS;;;;1;1;"))
      val simpleProperty = SimplePointAssetProperty("opastustaulun_teksti", textValues)
      val id = service.create(IncomingDirectionalTrafficSign(2, 0.0, linkId, 3, Some(0), Set(simpleProperty)), "jakke", roadLink)
      val assets = service.getPersistedAssetsByIds(Set(id))

      assets.size should be(1)

      val asset = assets.head

      asset.id should be(id)
      asset.linkId should be(linkId)
      asset.lon should be(2)
      asset.lat should be(0)
      asset.mValue should be(2)
      asset.floating should be(false)
      asset.municipalityCode should be(235)
      asset.validityDirection should be(3)
      asset.propertyData.find(_.publicId == "opastustaulun_teksti").get.values.head.asInstanceOf[PropertyValue].propertyValue should be ("HELSINKI:HELSINGFORS;;;;1;1;")
      asset.createdBy should be(Some("jakke"))
      asset.createdAt shouldBe defined

    }
  }
  test("Expire directional traffic sign") {
    when(mockRoadLinkService.getRoadLinksWithComplementaryByMunicipalityUsingCache(235)).thenReturn(Seq(
      RoadLinkFetched(linkId, 235, Seq(Point(0.0, 0.0), Point(200.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink))

    runWithRollback {
      val result = service.getByMunicipality(235).find(_.id == 600053).get
      result.id should equal(600053)


      service.expire(600053,testUser.username)

      service.getByMunicipality(235).find(_.id == 600053) should equal(None)
    }
  }

  test("Update directional traffic sign with geometry changes") {
    val linkGeometry = Seq(Point(0.0, 0.0), Point(200.0, 0.0))
    val linkId1 = LinkIdGenerator.generateRandom()
    when(mockRoadLinkService.getRoadLinksWithComplementaryByMunicipalityUsingCache(235)).thenReturn(Seq(
      RoadLinkFetched(linkId, 235, linkGeometry, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink))
    when(mockRoadLinkService.getRoadLinksWithComplementaryByMunicipalityUsingCache(91)).thenReturn(Seq(
      RoadLinkFetched(linkId1, 91, linkGeometry, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink))

    when(mockRoadLinkService.getRoadLinkByLinkId(linkId)).thenReturn(Seq(
      RoadLinkFetched(linkId, 235, linkGeometry, Municipality,
        TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink).headOption)


    runWithRollback {
      val beforeUpdate = service.getByMunicipality(235).find(_.id == 600053).get
      beforeUpdate.id should equal(600053)
      beforeUpdate.lon should equal(374467.0)
      beforeUpdate.lat should equal(6677347.0)
      beforeUpdate.mValue should equal(103.0)
      beforeUpdate.linkId should equal(linkId)
      beforeUpdate.municipalityCode should equal(235)
      beforeUpdate.createdBy should equal(Some("dr2_test_data"))
      beforeUpdate.createdAt.isDefined should equal(true)
      beforeUpdate.modifiedBy should equal(None)
      beforeUpdate.modifiedAt should equal(None)
      beforeUpdate.validityDirection should equal(2)

      val roadLink =  RoadLink(linkId1, linkGeometry, 200, Municipality, UnknownFunctionalClass.value, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)), linkSource = NormalLinkInterface)
      val textValues = Seq(PropertyValue("New text"))
      val simpleProperty = SimplePointAssetProperty("opastustaulun_teksti", textValues)
      val newAssetId = service.update(id = 600053, IncomingDirectionalTrafficSign(200, 0, linkId1, 3, Some(0), Set(simpleProperty)), roadLink,  "test")

      val afterUpdate = service.getByMunicipality(91).find(_.id == newAssetId).get
      afterUpdate.id should equal(newAssetId)
      afterUpdate.lon should equal(200)
      afterUpdate.lat should equal(0)
      afterUpdate.mValue should equal(200)
      afterUpdate.linkId should equal(linkId1)
      afterUpdate.municipalityCode should equal(91)
      afterUpdate.createdBy should equal(Some("dr2_test_data"))
      afterUpdate.createdAt should equal(beforeUpdate.createdAt)
      afterUpdate.modifiedBy should equal(Some("test"))
      afterUpdate.modifiedAt.isDefined should equal(true)
      afterUpdate.propertyData.find(_.publicId == "opastustaulun_teksti").get.values.head.asInstanceOf[PropertyValue].propertyValue should equal("New text")
      afterUpdate.validityDirection should equal(3)
    }
  }

  test("Update directional traffic sign without geometry changes") {
    val linkGeometry = Seq(Point(73.0, 9.0), Point(100.0, 18.0))
    val linkId = LinkIdGenerator.generateRandom()
    when(mockRoadLinkService.getRoadLinksWithComplementaryByMunicipalityUsingCache(235)).thenReturn(Seq(
      RoadLinkFetched(linkId, 235, linkGeometry, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink))
    when(mockRoadLinkService.getRoadLinkByLinkId(linkId)).thenReturn(Seq(
      RoadLinkFetched(linkId, 235, linkGeometry, Municipality,
        TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink).headOption)
    val roadLink = RoadLink(linkId, linkGeometry, 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

    runWithRollback {
      val textValues = Seq(PropertyValue("HELSINKI:HELSINGFORS;;;;1;1;"))
      val simpleProperty = SimplePointAssetProperty("opastustaulun_teksti", textValues)
      val assetCreatedID = service.create(IncomingDirectionalTrafficSign(76, 10, linkId, 3, Some(0), Set(simpleProperty)), "test", roadLink)
      val assets = service.getPersistedAssetsByIds(Set(assetCreatedID))

      val beforeUpdate = assets.head

      beforeUpdate.id should be(assetCreatedID)
      beforeUpdate.linkId should be(linkId)
      beforeUpdate.lon should be(76.0)
      beforeUpdate.lat should be(10.0)
      beforeUpdate.mValue should be(3.162)
      beforeUpdate.floating should be(false)
      beforeUpdate.municipalityCode should be(235)
      beforeUpdate.validityDirection should be(3)
      beforeUpdate.propertyData.find(_.publicId == "opastustaulun_teksti").get.values.head.asInstanceOf[PropertyValue].propertyValue should be ("HELSINKI:HELSINGFORS;;;;1;1;")
      beforeUpdate.createdBy should be(Some("test"))
      beforeUpdate.createdAt shouldBe defined

      val updatedTextValues = Seq(PropertyValue("New text"))
      val updatedSimpleProperty = SimplePointAssetProperty("opastustaulun_teksti", updatedTextValues)
      service.update(assetCreatedID, IncomingDirectionalTrafficSign(beforeUpdate.lon, beforeUpdate.lat, linkId, 3, Some(0), Set(updatedSimpleProperty), Some(beforeUpdate.mValue)), roadLink, "test")

      val afterUpdate = service.getByMunicipality(235).find(_.id == assetCreatedID).get
      afterUpdate.id should equal(assetCreatedID)
      afterUpdate.lon should equal(beforeUpdate.lon)
      afterUpdate.lat should equal(beforeUpdate.lat)
      afterUpdate.mValue should equal(beforeUpdate.mValue)
      afterUpdate.linkId should equal(linkId)
      afterUpdate.municipalityCode should equal(235)
      afterUpdate.createdBy should equal(Some("test"))
      afterUpdate.createdAt should equal(beforeUpdate.createdAt)
      afterUpdate.modifiedBy should equal(Some("test"))
      afterUpdate.modifiedAt.isDefined should equal(true)
      afterUpdate.propertyData.find(_.publicId == "opastustaulun_teksti").get.values.head.asInstanceOf[PropertyValue].propertyValue should equal("New text")
      afterUpdate.validityDirection should equal(3)
    }
  }
}

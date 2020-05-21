package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.pointasset.TrafficLight
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util.TestTransactions
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class TrafficLightServiceSpec  extends FunSuite with Matchers {
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

  val service = new TrafficLightService(mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = f

    override def withDynSession[T](f: => T): T = f
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(service.dataSource)(test)

  test("Can fetch by bounding box") {
    when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((List(), Nil))

    runWithRollback {
      val result = service.getByBoundingBox(testUser, BoundingRectangle(Point(374101, 6677437), Point(374102, 6677438))).head
      result.id should equal(600070)
      result.linkId should equal(1611387)
      result.lon should equal(374101.60105163435)
      result.lat should equal(6677437.872017591)
      result.mValue should equal(16.592)
    }
  }

  test("Can fetch by municipality") {
    when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(235)).thenReturn((Seq(
      VVHRoadlink(1611387, 235, Seq(Point(0.0, 0.0), Point(200.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink), Nil))

    runWithRollback {
      val result = service.getByMunicipality(235).find(_.id == 600070).get

      result.id should equal(600070)
      result.linkId should equal(1611387)
      result.lon should equal(374101.60105163435)
      result.lat should equal(6677437.872017591)
      result.mValue should equal(16.592)
    }
  }

  ignore("Expire traffic light") {
    runWithRollback {
      service.getPersistedAssetsByIds(Set(600029)).length should be(1)
      service.expire(600029, testUser.username)
      service.getPersistedAssetsByIds(Set(600029)) should be(Nil)
    }
  }

  test("Create new") {
    runWithRollback {
      val now = DateTime.now()
      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val simplePointPropertyData = Set(
        SimplePointAssetProperty("trafficLight_type", List(PropertyValue("1")), 1)
      )
      val id = service.create(IncomingTrafficLight(2, 0.0, 388553075, simplePointPropertyData), "jakke", roadLink)
      val assets = service.getPersistedAssetsByIds(Set(id))

      assets.size should be(1)

      val asset = assets.head

      asset.vvhTimeStamp should not be(0)

      val suggestProperty = asset.propertyData.find(_.publicId == "suggest_box").head
      val trafficLightTypeProperty = asset.propertyData.find(_.publicId == "trafficLight_type").head

      val pointAssetProperties = Seq(
        Property(suggestProperty.id, "suggest_box", "checkbox", false, Seq(PropertyValue("0", None, false)), groupedId = suggestProperty.groupedId.toInt),
        Property(trafficLightTypeProperty.id, "trafficLight_type", "single_choice", true, Seq(PropertyValue("1", Some("Valo-opastin"), false)), groupedId = trafficLightTypeProperty.groupedId.toInt)
      )

      asset.id should be(id)
      asset.linkId should be(388553075)
      asset.lon should be(2)
      asset.lat should be(0)
      asset.mValue should be(2)
      asset.municipalityCode should be(235)
      asset.propertyData.find(_.publicId == "suggest_box") should be (Some(pointAssetProperties.head))
      asset.propertyData.find(_.publicId == "trafficLight_type") should be (Some(pointAssetProperties.last))
      asset.createdBy should be(Some("jakke"))
    }
  }

  test("Update traffic light") {
    val linkGeometry = Seq(Point(0.0, 0.0), Point(200.0, 0.0))
    when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(235)).thenReturn((Seq(
      VVHRoadlink(1611387, 235, linkGeometry, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink), Nil))
    when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(91)).thenReturn((Seq(
      VVHRoadlink(123, 91, linkGeometry, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink), Nil))

    val roadLink = RoadLink(123, linkGeometry, 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)))

    runWithRollback {
      val beforeUpdate = service.getByMunicipality(235).find(_.id == 600070).get
      beforeUpdate.id should equal(600070)
      beforeUpdate.lon should equal(374101.60105163435)
      beforeUpdate.lat should equal(6677437.872017591)
      beforeUpdate.mValue should equal(16.592)
      beforeUpdate.linkId should equal(1611387)
      beforeUpdate.municipalityCode should equal(235)
      beforeUpdate.createdBy should equal(Some("dr2_test_data"))
      beforeUpdate.createdAt.isDefined should equal(true)
      beforeUpdate.modifiedBy should equal(None)
      beforeUpdate.modifiedAt should equal(None)

      val newAssetId = service.update(id = 600070, IncomingTrafficLight(100, 0, 123, Set()), roadLink, "test")

      val afterUpdate = service.getByMunicipality(91).find(_.id == newAssetId).get
      afterUpdate.id should equal(newAssetId)
      afterUpdate.lon should equal(100)
      afterUpdate.lat should equal(0)
      afterUpdate.mValue should equal(100)
      afterUpdate.linkId should equal(123)
      afterUpdate.municipalityCode should equal(91)
      afterUpdate.createdBy should equal(beforeUpdate.createdBy)
      afterUpdate.createdAt should equal(beforeUpdate.createdAt)
      afterUpdate.modifiedBy should equal(Some("test"))
      afterUpdate.modifiedAt.isDefined should equal(false)
    }
  }

  test("Update traffic light with geometry changes"){
    runWithRollback {
      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(0.0, 20.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingTrafficLight(0.0, 20.0, 388553075, Set()), "jakke", roadLink )
      val oldAsset = service.getPersistedAssetsByIds(Set(id)).head
      oldAsset.modifiedAt.isDefined should equal(false)
      val newId = service.update(id, IncomingTrafficLight(0.0, 10.0, 388553075, Set()), roadLink, "test")

      val updatedAsset = service.getPersistedAssetsByIds(Set(newId)).head
      updatedAsset.id should not be id
      updatedAsset.lon should equal (0.0)
      updatedAsset.lat should equal (10.0)
      updatedAsset.createdBy should equal (oldAsset.createdBy)
      updatedAsset.createdAt should equal (oldAsset.createdAt)
      updatedAsset.modifiedBy should equal (Some("test"))
      updatedAsset.modifiedAt.isDefined should equal(false)
    }
  }

  test("Update traffic light without geometry changes"){
    runWithRollback {
      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(0.0, 20.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingTrafficLight(0.0, 20.0, 388553075, Set()), "jakke", roadLink )
      val asset = service.getPersistedAssetsByIds(Set(id)).head

      val newId = service.update(id, IncomingTrafficLight(0.0, 20.0, 388553075, Set()), roadLink,  "test")

      val updatedAsset = service.getPersistedAssetsByIds(Set(newId)).head
      updatedAsset.id should be (id)
      updatedAsset.lon should be (asset.lon)
      updatedAsset.lat should be (asset.lat)
      updatedAsset.createdBy should equal (Some("jakke"))
      updatedAsset.modifiedBy should equal (Some("test"))
    }
  }
}


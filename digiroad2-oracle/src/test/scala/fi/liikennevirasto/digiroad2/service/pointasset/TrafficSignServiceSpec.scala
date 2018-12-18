package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, BothDirections, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.{OracleUserProvider, Queries}
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.ManoeuvreProvider
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util.DataFixture.trafficSignService
import fi.liikennevirasto.digiroad2.util.TestTransactions
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import org.joda.time.DateTime
import fi.liikennevirasto.digiroad2.{asset, _}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}
import slick.jdbc.StaticQuery.interpolation
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession


class TrafficSignServiceSpec extends FunSuite with Matchers with BeforeAndAfter {
  def toRoadLink(l: VVHRoadlink) = {
    RoadLink(l.linkId, l.geometry, GeometryUtils.geometryLength(l.geometry),
      l.administrativeClass, 1, l.trafficDirection, UnknownLinkType, None, None, l.attributes + ("MUNICIPALITYCODE" -> BigInt(l.municipalityCode)))
  }

  val testUser = User(
    id = 1,
    username = "Hannu",
    configuration = Configuration(authorizedMunicipalities = Set(235)))
  val trafficSignsTypeId = TrafficSigns.typeId
  val batchProcessName = "batch_process_trafficSigns"
  private val typePublicId = "trafficSigns_type"
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockUserProvider = MockitoSugar.mock[OracleUserProvider]
  val vvHRoadlink1 = Seq(VVHRoadlink(1611317, 235, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers))
  val vvHRoadlink2 = Seq(VVHRoadlink(1611400, 235, Seq(Point(2, 2), Point(4, 4)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers))
  when(mockRoadLinkService.getRoadLinksFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn(vvHRoadlink1.map(toRoadLink))
  when(mockRoadLinkService.getRoadLinkByLinkIdFromVVH(any[Long], any[Boolean])).thenReturn(vvHRoadlink1.map(toRoadLink).headOption)
  when(mockRoadLinkService.getClosestRoadlinkForCarTrafficFromVVH(any[User], any[Point])).thenReturn(vvHRoadlink2)
  when(mockRoadLinkService.enrichRoadLinksFromVVH(vvHRoadlink2)).thenReturn(vvHRoadlink2.map(toRoadLink))
  when(mockUserProvider.getCurrentUser()).thenReturn(testUser)

  when(mockRoadLinkService.getRoadLinkByLinkIdFromVVH(1191950690)).thenReturn(Seq(
    VVHRoadlink(1191950690, 235, Seq(Point(373500.349, 6677657.152), Point(373494.182, 6677669.918)), Private,
      TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink).headOption)
  val userProvider = new OracleUserProvider
  val service = new TrafficSignService(mockRoadLinkService, mockUserProvider, new DummyEventBus) {
    override def withDynTransaction[T](f: => T): T = f

    override def withDynSession[T](f: => T): T = f
  }

  val properties80 = Set(
    SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue("1"))),
    SimpleTrafficSignProperty("trafficSigns_value", List(TextPropertyValue("80"))),
    SimpleTrafficSignProperty("trafficSigns_info", List(TextPropertyValue("Additional Info for test"))))

  val properties90 = Set(
    SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue("2"))),
    SimpleTrafficSignProperty("trafficSigns_value", List(TextPropertyValue("90"))),
    SimpleTrafficSignProperty("trafficSigns_info", List(TextPropertyValue("Updated Additional Info for test"))))

  val properties60 = Set(
    SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue("2"))),
    SimpleTrafficSignProperty("trafficSigns_value", List(TextPropertyValue("60"))),
    SimpleTrafficSignProperty("trafficSigns_info", List(TextPropertyValue("Additional Info for test"))))

  val simpleProperties10 = Set(
    SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue("10"))),
    SimpleTrafficSignProperty("trafficSigns_info", List(TextPropertyValue("Additional Info for test"))))

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(service.dataSource)(test)

  test("Can fetch by bounding box") {
    when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((List(), Nil))

    runWithRollback {
      val result = service.getByBoundingBox(testUser, BoundingRectangle(Point(374466.5, 6677346.5), Point(374467.5, 6677347.5))).head
      result.id should equal(600073)
      result.linkId should equal(1611317)
      result.lon should equal(374467)
      result.lat should equal(6677347)
      result.mValue should equal(103)
    }
  }

  test("Can fetch by municipality") {
    when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(235)).thenReturn((Seq(
      VVHRoadlink(388553074, 235, Seq(Point(0.0, 0.0), Point(200.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink), Nil))

    runWithRollback {
      val result = service.getByMunicipality(235).find(_.id == 600073).get

      result.id should equal(600073)
      result.linkId should equal(1611317)
      result.lon should equal(374467)
      result.lat should equal(6677347)
      result.mValue should equal(103)
    }
  }

  test("Expire Traffic Sign") {
    runWithRollback {
      service.getPersistedAssetsByIds(Set(600073)).length should be(1)
      service.expire(600073, testUser.username)
      service.getPersistedAssetsByIds(Set(600073)) should be(Nil)
    }
  }

  test("Create new Traffic Sign") {
    runWithRollback {
      val properties = properties80

      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingTrafficSign(2.0, 0.0, 388553075, properties, 1, None), testUser.username, roadLink)

      val assets = service.getPersistedAssetsByIds(Set(id))

      assets.size should be(1)

      val asset = assets.head

      asset.id should be(id)
      asset.linkId should be(388553075)
      asset.lon should be(2)
      asset.lat should be(0)
      asset.mValue should be(2)
      asset.floating should be(false)
      asset.municipalityCode should be(235)
      asset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be ("1")
      asset.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be ("80")
      asset.propertyData.find(p => p.publicId == "trafficSigns_info").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be ("Additional Info for test")
      asset.createdBy should be(Some(testUser.username))
      asset.createdAt shouldBe defined
    }
  }

  test("Update Traffic Sign") {
    runWithRollback {
      val trafficSign = service.getById(600073).get
      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val updatedProperties = properties90
      val updated = IncomingTrafficSign(trafficSign.lon, trafficSign.lat, trafficSign.linkId, updatedProperties, 1, None)

      service.update(trafficSign.id, updated, roadLink, "unit_test")
      val updatedTrafficSign = service.getById(600073).get

      updatedTrafficSign.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be ("2")
      updatedTrafficSign.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be ("90")
      updatedTrafficSign.propertyData.find(p => p.publicId == "trafficSigns_info").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be ("Updated Additional Info for test")
      updatedTrafficSign.id should equal(updatedTrafficSign.id)
      updatedTrafficSign.modifiedBy should equal(Some("unit_test"))
      updatedTrafficSign.modifiedAt shouldBe defined
    }
  }

  test("Update traffic sign with geometry changes"){
    runWithRollback {

      val properties = properties80

      val propertiesToUpdate = properties60

      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(0.0, 20.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingTrafficSign(0.0, 20.0, 388553075, properties, 1, None), "jakke", roadLink )
      val oldAsset = service.getPersistedAssetsByIds(Set(id)).head
      oldAsset.modifiedAt.isDefined should equal(false)

      val newId = service.update(id, IncomingTrafficSign(0.0, 10.0, 388553075, propertiesToUpdate, 1, None), roadLink, "test")

      val updatedAsset = service.getPersistedAssetsByIds(Set(newId)).head
      updatedAsset.id should not be id
      updatedAsset.lon should equal (0.0)
      updatedAsset.lat should equal (10.0)
      updatedAsset.createdBy should equal (oldAsset.createdBy)
      updatedAsset.createdAt should equal (oldAsset.createdAt)
      updatedAsset.modifiedBy should equal (Some("test"))
      updatedAsset.modifiedAt.isDefined should equal(true)
      updatedAsset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be ("2")
      updatedAsset.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be ("60")
      updatedAsset.propertyData.find(p => p.publicId == "trafficSigns_info").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be ("Additional Info for test")
    }
  }

  test("Update traffic sign without geometry changes"){
    runWithRollback {
      val properties = properties80

      val propertiesToUpdate = properties60

      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(0.0, 20.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingTrafficSign(0.0, 20.0, 388553075, properties, 1, None), "jakke", roadLink )
      val asset = service.getPersistedAssetsByIds(Set(id)).head

      val newId = service.update(id, IncomingTrafficSign(0.0, 20.0, 388553075, propertiesToUpdate, 1, None), roadLink, "test")

      val updatedAsset = service.getPersistedAssetsByIds(Set(newId)).head
      updatedAsset.id should be (id)
      updatedAsset.lon should be (asset.lon)
      updatedAsset.lat should be (asset.lat)
      updatedAsset.createdBy should equal (Some("jakke"))
      updatedAsset.modifiedBy should equal (Some("test"))
      updatedAsset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be ("2")
      updatedAsset.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be ("60")
      updatedAsset.propertyData.find(p => p.publicId == "trafficSigns_info").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be ("Additional Info for test")
    }
  }

  test("Create traffic sign with direction towards digitizing using coordinates without asset bearing") {
    /*mock road link is set to (2,2), (4,4), so this asset is set to go towards digitizing*/
    runWithRollback {
      val properties = Set(
        SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue("1"))),
        SimpleTrafficSignProperty("trafficSigns_value", List(TextPropertyValue("100"))))

      val sign = IncomingTrafficSign(3, 2, 1611400, properties, TrafficDirection.UnknownDirection.value, None)

      val id = service.createFromCoordinates(sign, vvHRoadlink2, false)
      val assets = service.getPersistedAssetsByIds(Set(id))
      assets.size should be(1)
      val asset = assets.head
      asset.id should be(id)
      asset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be ("1")
      asset.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be ("100")
      asset.validityDirection should be (TowardsDigitizing.value)
      asset.bearing.get should be (45)
    }
  }

  test("Create traffic sign with direction against digitizing using coordinates without asset bearing") {
     /*mock road link is set to (2,2), (4,4), so this asset is set to go against digitizing*/
    runWithRollback {
      val properties = Set(
        SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue(SpeedLimitSign.TRvalue.toString))),
        SimpleTrafficSignProperty("trafficSigns_value", List(TextPropertyValue("100"))))

      val sign = IncomingTrafficSign(3, 4, 1611400, properties, TrafficDirection.UnknownDirection.value, None)

      val id = service.createFromCoordinates(sign, vvHRoadlink2, false)
      val assets = service.getPersistedAssetsByIds(Set(id))
      assets.size should be(1)
      val asset = assets.head
      asset.id should be(id)
      asset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be ("1")
      asset.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be ("100")
      asset.validityDirection should be (AgainstDigitizing.value)
    }
  }

  test("Create traffic sign with direction towards digitizing using coordinates with asset bearing") {
    /*asset bearing in this case indicates towards which direction the traffic sign is facing, not the flow of traffic*/
    runWithRollback {

      val properties = Set(
        SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue(SpeedLimitSign.TRvalue.toString))),
        SimpleTrafficSignProperty("trafficSigns_value", List(TextPropertyValue("100"))))

      val sign = IncomingTrafficSign(3, 2, 1611400, properties, TrafficDirection.UnknownDirection.value, Some(225))

      val id = service.createFromCoordinates(sign, vvHRoadlink2, false)
      val assets = service.getPersistedAssetsByIds(Set(id))
      assets.size should be(1)
      val asset = assets.head
      asset.id should be(id)
      asset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be ("1")
      asset.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be ("100")
      asset.validityDirection should be (TowardsDigitizing.value)
      asset.bearing.get should be (45)
    }
  }

  test("Create traffic sign with direction against digitizing using coordinates with asset bearing") {
    /*asset bearing in this case indicates towards which direction the traffic sign is facing, not the flow of traffic*/
    val properties = Set(
      SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue(SpeedLimitSign.TRvalue.toString))),
      SimpleTrafficSignProperty("trafficSigns_value", List(TextPropertyValue("100"))))

    val sign = IncomingTrafficSign(3, 4, 1611400, properties, TrafficDirection.UnknownDirection.value, Some(45))

    runWithRollback {
      val id = service.createFromCoordinates(sign, vvHRoadlink2, false)
      val assets = service.getPersistedAssetsByIds(Set(id))
      assets.size should be(1)
      val asset = assets.head
      asset.id should be(id)
      asset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be ("1")
      asset.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be ("100")
      asset.validityDirection should be(AgainstDigitizing.value)
    }
  }

  test("two-sided traffic signs are effective in both directions ") {
    runWithRollback {
      val properties = Set(
        SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue(PedestrianCrossingSign.TRvalue.toString))),
        SimpleTrafficSignProperty("trafficSigns_value", List(TextPropertyValue("100"))))

      val sign = IncomingTrafficSign(3, 4, 1611400, properties, TrafficDirection.UnknownDirection.value, Some(45))

      val id = service.createFromCoordinates(sign, vvHRoadlink2, true)
      val assets = service.getPersistedAssetsByIds(Set(id))
      assets.size should be(1)
      val asset = assets.head
      asset.id should be(id)
      asset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be ("7")
      asset.validityDirection should be(BothDirections.value)

    }
  }

  test("Create traffic sign with additional information") {
    runWithRollback {
      val properties = Set(
        SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue(FreeWidth.TRvalue.toString))),
        SimpleTrafficSignProperty("trafficSigns_info", List(TextPropertyValue("Info Test"))))

      val sign = IncomingTrafficSign(3, 4, 1611400, properties, TrafficDirection.UnknownDirection.value, Some(45))
      val id = service.createFromCoordinates(sign, vvHRoadlink2, false)

      val assets = service.getPersistedAssetsByIds(Set(id))
      assets.size should be(1)
      val asset = assets.head
      asset.id should be(id)
      asset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be ("45")
      asset.propertyData.find(p => p.publicId == "trafficSigns_info").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be ("Info Test")
      asset.validityDirection should be(AgainstDigitizing.value)
    }
  }

  test("Get trafficSigns by radius") {
    runWithRollback {
      val propertiesSpeedLimit = properties80

      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(0.0, 50.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingTrafficSign(0.0, 20.0, 388553075, propertiesSpeedLimit, 1, None), testUser.username, roadLink)

      val assets = service.getTrafficSignByRadius(roadLink.geometry.last, 50, None)

      assets.size should be(1)

      val asset = assets.head

      asset.id should be(id)
      asset.linkId should be(388553075)
      asset.lon should be(0)
      asset.lat should be(20)
      asset.mValue should be(20)
      asset.floating should be(false)
      asset.municipalityCode should be(235)
      asset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be ("1")
      asset.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be ("80")
      asset.propertyData.find(p => p.publicId == "trafficSigns_info").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be ("Additional Info for test")
      asset.createdBy should be(Some(testUser.username))
      asset.createdAt shouldBe defined
    }
  }

  test("Get trafficSigns by radius and sign type") {
    runWithRollback {
      val propertiesSpeedLimit = properties80

      val propertiesMaximumRestrictions= Set(
        SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue("8"))),
        SimpleTrafficSignProperty("trafficSigns_value", List(TextPropertyValue("10"))))

      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(0.0, 50.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      service.create(IncomingTrafficSign(0.0, 20.0, 388553075, propertiesSpeedLimit, 1, None), testUser.username, roadLink)
      service.create(IncomingTrafficSign(0.0, 20.0, 388553075, propertiesMaximumRestrictions, 1, None), testUser.username, roadLink)

      val assets = service.getTrafficSignByRadius(roadLink.geometry.last, 50, Some(TrafficSignTypeGroup.SpeedLimits))

      assets.size should be(1)

      val asset = assets.head
      asset.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be ("1")
      asset.propertyData.find(p => p.publicId == "trafficSigns_value").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be ("80")
      asset.propertyData.find(p => p.publicId == "trafficSigns_info").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be ("Additional Info for test")
    }
  }

  test("Should return only assets with traffic restrictions"){
    runWithRollback {

      val properties = properties80

      val properties1 = simpleProperties10

      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingTrafficSign(2.0, 0.0, 388553075, properties, 1, None), testUser.username, roadLink)
      val id1 = service.create(IncomingTrafficSign(2.0, 0.0, 388553075, properties1, 1, None), testUser.username, roadLink)

      val assets = service.getTrafficSignsWithTrafficRestrictions(235)

      assets.find(_.id == id).size should be(0)
      assets.find(_.id == id1).size should be(1)
    }
  }

  test("Should call creation of manoeuvre actor on traffic sign creation"){
    runWithRollback {

      val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
      val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
      val mockUserProvider = MockitoSugar.mock[OracleUserProvider]

      val trService = new TrafficSignService(mockRoadLinkService, mockUserProvider, mockEventBus) {
        override def withDynTransaction[T](f: => T): T = f
        override def withDynSession[T](f: => T): T = f
      }

      val properties = simpleProperties10

      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = trService.create(IncomingTrafficSign(2.0, 0.0, 388553075, properties, 1, None), testUser.username, roadLink)
      val asset = trService.getPersistedAssetsByIds(Set(id)).head

      verify(mockEventBus, times(1)).publish("manoeuvre:create", ManoeuvreProvider(asset, roadLink))
    }
  }

  test("Should call expire of manoeuvre actor on traffic sign expirinf"){
    runWithRollback {

      val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
      val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
      val mockUserProvider = MockitoSugar.mock[OracleUserProvider]

      val trService = new TrafficSignService(mockRoadLinkService, mockUserProvider, mockEventBus) {
        override def withDynTransaction[T](f: => T): T = f
        override def withDynSession[T](f: => T): T = f
      }

      val properties = simpleProperties10

      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = trService.create(IncomingTrafficSign(2.0, 0.0, 388553075, properties, 1, None), testUser.username, roadLink)
      val asset = trService.getPersistedAssetsByIds(Set(id)).head

      verify(mockEventBus, times(1)).publish("manoeuvre:create", ManoeuvreProvider(asset, roadLink))

      trService.expire(id, "test_user")
      verify(mockEventBus, times(1)).publish("manoeuvre:expire", id)
    }
  }

  test("Pedestrian crossings are filtered") {
    runWithRollback {
      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((List(), Nil))


      val linkId1 = 388553075
      val linkId2 = 388553074
      val roadLink = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(0.0, 20.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val adjacentRoadLink = RoadLink(linkId2, Seq(Point(0.0, 20.0), Point(0.0, 40.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val properties = Set(
        SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue("7"))),
        SimpleTrafficSignProperty("trafficSigns_value", List(TextPropertyValue(""))),
        SimpleTrafficSignProperty("trafficSigns_info", List(TextPropertyValue("Pedestrian crossing for test purpose"))))

      service.create(IncomingTrafficSign(0.0, 20.0, linkId1, properties, BothDirections.value, None), testUser.username, roadLink)
      service.create(IncomingTrafficSign(0.0, 20.0, linkId1, properties, BothDirections.value, None), batchProcessName, roadLink)
      service.create(IncomingTrafficSign(0.0, 20.0, linkId1, properties, TowardsDigitizing.value, None), batchProcessName, roadLink)
      service.create(IncomingTrafficSign(0.0, 20.0, linkId1, properties, AgainstDigitizing.value, None), batchProcessName, roadLink)
      service.create(IncomingTrafficSign(0.0, 19.0, linkId1, properties, BothDirections.value, None), batchProcessName, roadLink)
      service.create(IncomingTrafficSign(0.0, 16.9, linkId1, properties, BothDirections.value, None), batchProcessName, roadLink)

      service.create(IncomingTrafficSign(0.0, 21.0, linkId2, properties, BothDirections.value, None), batchProcessName, adjacentRoadLink)
      service.create(IncomingTrafficSign(0.0, 21.0, linkId2, properties, TowardsDigitizing.value, None), batchProcessName, adjacentRoadLink)
      service.create(IncomingTrafficSign(0.0, 21.0, linkId2, properties, AgainstDigitizing.value, None), batchProcessName, adjacentRoadLink)
      service.create(IncomingTrafficSign(0.0, 21.0, linkId2, properties, BothDirections.value, None), batchProcessName, adjacentRoadLink)

      val result = service.getByBoundingBox(testUser, BoundingRectangle(Point(0.0, 0.0), Point(0.0, 40.0)))

      val assetsOnLinkId1_vd1 = result.toList.groupBy(_.linkId).find(_._1 == linkId1).map(_._2).getOrElse(Seq.empty[PersistedTrafficSign]).filter(asset => asset.validityDirection == 1)
      val assetsOnLinkId2_vd1 = result.toList.groupBy(_.linkId).find(_._1 == linkId2).map(_._2).getOrElse(Seq.empty[PersistedTrafficSign]).filter(asset => asset.validityDirection == 1)
      val assetsOnLinkId2_vd2 = result.toList.groupBy(_.linkId).find(_._1 == linkId2).map(_._2).getOrElse(Seq.empty[PersistedTrafficSign]).filter(asset => asset.validityDirection == 2)

      val signGroupOnLinkId1 = assetsOnLinkId1_vd1.sortBy(_.lat).lift(1).head

      signGroupOnLinkId1.propertyData.filter(_.id == 0).head.values.head.asInstanceOf[TextPropertyValue].propertyValue.toInt should be (2)
      assetsOnLinkId2_vd1.head.propertyData.filter(_.id == 0).head.values.head.asInstanceOf[TextPropertyValue].propertyValue.toInt should be (2)
      assetsOnLinkId2_vd2.head.propertyData.filter(_.id == 0).head.values.head.asInstanceOf[TextPropertyValue].propertyValue.toInt should be (1)
    }
  }

  test("Get Traffic Signs changes by type group") {
    runWithRollback {
      val properties = Set(
        SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue("83"))),
        SimpleTrafficSignProperty("trafficSigns_value", List(TextPropertyValue(""))),
        SimpleTrafficSignProperty("trafficSigns_info", List(TextPropertyValue("Traffic Sign TwoWayTraffic type, part of Warning Signs"))))
      val properties1 = Set(
        SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue("84"))),
        SimpleTrafficSignProperty("trafficSigns_value", List(TextPropertyValue(""))),
        SimpleTrafficSignProperty("trafficSigns_info", List(TextPropertyValue("Traffic Sign MovingBridge type, part of Warning Signs"))))
      val properties2 = Set(
        SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue("85"))),
        SimpleTrafficSignProperty("trafficSigns_value", List(TextPropertyValue(""))),
        SimpleTrafficSignProperty("trafficSigns_info", List(TextPropertyValue("Traffic Sign ConstructionWork type, part of Warning Signs"))))
      val properties3 = Set(
        SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue("24"))),
        SimpleTrafficSignProperty("trafficSigns_value", List(TextPropertyValue(""))),
        SimpleTrafficSignProperty("trafficSigns_info", List(TextPropertyValue("Traffic Sign Pedestrians type, part of Prohibitions And Restrictions Signs"))))


      when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(Set(388553075l), false)).thenReturn(
        Seq(RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))))
      )

      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val (id, id1, id2, id3) =
        (service.create(IncomingTrafficSign(2.0, 0.0, 388553075, properties, 2, None), testUser.username, roadLink),
          service.create(IncomingTrafficSign(2.0, 0.0, 388553075, properties1, 2, None), testUser.username, roadLink),
          service.create(IncomingTrafficSign(2.0, 0.0, 388553075, properties2, 2, None), testUser.username, roadLink),
          service.create(IncomingTrafficSign(2.0, 0.0, 388553075, properties3, 2, None), testUser.username, roadLink))

      val changes = service.getChanged(service.getTrafficSignTypeByGroup(TrafficSignTypeGroup.GeneralWarningSigns), DateTime.parse("2016-11-01T12:00Z"), DateTime.now().plusDays(1))
      changes.length should be(3)

      service.expire(id)

      val changesAfterExpire = service.getChanged(service.getTrafficSignTypeByGroup(TrafficSignTypeGroup.GeneralWarningSigns), DateTime.parse("2016-11-01T12:00Z"), DateTime.now().plusDays(1))
      changesAfterExpire.length should be(3)
    }
  }

  test("Get only one Traffic Sign change"){
    runWithRollback{
      val properties = Set(
        SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue("83"))),
        SimpleTrafficSignProperty("trafficSigns_value", List(TextPropertyValue(""))),
        SimpleTrafficSignProperty("trafficSigns_info", List(TextPropertyValue("Traffic Sign TwoWayTraffic type, part of Warning Signs"))))

      val lrmPositionsIds = Queries.fetchLrmPositionIds(11)

      sqlu"""insert into asset (id,asset_type_id,floating, created_date) VALUES (11,$trafficSignsTypeId,0, TO_DATE('17/12/2016', 'DD/MM/YYYY'))""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(10)}, 388553075, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (11,${lrmPositionsIds(10)})""".execute
      sqlu"""insert into single_choice_value (asset_id, enumerated_value_id, property_id, modified_date, modified_by) values(11, 300153, 300144, timestamp '2016-12-17 19:01:13.000000', null)""".execute
      Queries.updateAssetGeometry(11, Point(5, 0))

      when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(Set(388553075))).thenReturn(Seq(RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))))

      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingTrafficSign(2.0, 0.0, 388553075, properties, 2, None), testUser.username, roadLink)

      val changes = service.getChanged(service.getTrafficSignTypeByGroup(TrafficSignTypeGroup.GeneralWarningSigns), DateTime.parse("2016-11-01T12:00Z"), DateTime.parse("2016-12-31T12:00Z"))
      changes.length should be(1)
    }
  }

  test(" Traffic Sign get change does not return floating obstacles"){
    runWithRollback {
      val lrmPositionsIds = Queries.fetchLrmPositionIds(11)

      sqlu"""insert into asset (id,asset_type_id,floating, created_date) VALUES (11,$trafficSignsTypeId, 1, TO_DATE('17/12/2016', 'DD/MM/YYYY'))""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(10)}, 388553075, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (11,${lrmPositionsIds(10)})""".execute
      sqlu"""insert into single_choice_value (asset_id, enumerated_value_id, property_id, modified_date, modified_by) values(11, 300153, 300144, timestamp '2016-12-17 19:01:13.000000', null)""".execute
      Queries.updateAssetGeometry(11, Point(5, 0))

      sqlu"""insert into asset (id,asset_type_id,floating) VALUES (2,$trafficSignsTypeId,1)""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(1)}, 388553075, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (2,${lrmPositionsIds(1)})""".execute
      sqlu"""insert into single_choice_value (asset_id, enumerated_value_id, property_id, modified_date, modified_by) values(2, 300153, 300144, timestamp '2016-12-17 20:01:13.000000', null)""".execute
      Queries.updateAssetGeometry(2, Point(5, 0))

      when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(Set(388553075l), false)).thenReturn(
        Seq(RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))))
      )

      val changes = service.getChanged(service.getTrafficSignTypeByGroup(TrafficSignTypeGroup.GeneralWarningSigns), DateTime.parse("2016-11-01T12:00Z"), DateTime.now().plusDays(1))
      changes.length should be(0)
    }
  }
  test("Prevent the creations of duplicated signs") {
    runWithRollback {
      val properties = Set(
        SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue(NoPedestrians.TRvalue.toString))),
        SimpleTrafficSignProperty("trafficSigns_info", List(TextPropertyValue("Original Traffic Sign!"))))

      val sign = IncomingTrafficSign(5, 4, 1611400, properties, TrafficDirection.UnknownDirection.value, None)
      val originalTrafficSignId = service.createFromCoordinates(sign, vvHRoadlink2, false)

      val assetsInRadius = service.getTrafficSignByRadius(Point(5, 4), 10, None)
      assetsInRadius.size should be(1)
      val assetO = assetsInRadius.head
      assetO.id should be(originalTrafficSignId)
      assetO.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be("24")
      assetO.propertyData.find(p => p.publicId == "trafficSigns_info").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be("Original Traffic Sign!")

      val properties2 = Set(
        SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue(NoPedestrians.TRvalue.toString))),
        SimpleTrafficSignProperty("trafficSigns_info", List(TextPropertyValue("Non Duplicated Traffic Sign!"))))

      val sign2 = IncomingTrafficSign(6, 4, 1611400, properties2, TrafficDirection.UnknownDirection.value, None)

      val duplicatedTrafficSignId = service.createFromCoordinates(sign2, vvHRoadlink2, false)
      val assetsInRadius2 = service.getTrafficSignByRadius(Point(5, 4), 10, None)
      assetsInRadius2.size should be(1)
      val assetD = assetsInRadius2.head
      assetD.id should be(duplicatedTrafficSignId)
      assetD.propertyData.find(p => p.publicId == "trafficSigns_type").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be("24")
      assetD.propertyData.find(p => p.publicId == "trafficSigns_info").get.values.head.asInstanceOf[TextPropertyValue].propertyValue should be("Non Duplicated Traffic Sign!")
    }

  }

  test("get by distance with same roadLink, trafficType and Direction") {
      val speedLimitProp = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(SpeedLimitSign.OTHvalue.toString))))
      val speedLimitZoneProp = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(SpeedLimitZone.OTHvalue.toString))))

      val trafficSigns = Seq(
        PersistedTrafficSign(1, 1002l, 2, 0, 2, false, 0, 235, speedLimitProp, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface),
        PersistedTrafficSign(2, 1002l, 4, 0, 4, false, 0, 235, speedLimitProp, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface),
        PersistedTrafficSign(3, 1002l, 12.1, 0, 12, false, 0, 235, speedLimitProp, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface),
        PersistedTrafficSign(4, 1002l, 2, 9, 12, false, 0, 235, speedLimitProp, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface),
        PersistedTrafficSign(5, 1002l, 5, 0, 5, false, 0, 235, speedLimitZoneProp, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface),
        PersistedTrafficSign(6, 1003l, 4, 0, 4, false, 0, 235, speedLimitProp, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface),
        PersistedTrafficSign(7, 1002l, 4, 0, 4, false, 0, 235, speedLimitProp, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)
      )

    val groupedAssets = trafficSigns.groupBy(_.linkId)
    val result = service.getTrafficSignsByDistance(trafficSigns.find(_.id == 1).get, groupedAssets, 10)
    result should have size 3
    result.exists(_.id == 1) should be (true)
    result.exists(_.id == 2) should be (true)
    result.exists(_.id == 3) should be (false) //more than 10 meter
    result.exists(_.id == 4) should be (true)
    result.exists(_.id == 5) should be (false) //different sign type
    result.exists(_.id == 6) should be (false) //different linkId
    result.exists(_.id == 7) should be (false) //different direction
  }

  test("Get traffic all traffic signs types by group name"){
    val groupType = TrafficSignTypeGroup.InformationSigns
    val assetsIncluded = TrafficSignType.apply(groupType)

    assetsIncluded.toSeq.sorted should be (Seq(113, 114, 115, 116, 117, 118, 119))
  }

  test("Get by municipality and group"){
    when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(235)).thenReturn((Seq(
      VVHRoadlink(388553075, 235,Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink), Nil))

    runWithRollback {

      val properties = Set(
      SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue("82"))),
      SimpleTrafficSignProperty("trafficSigns_value", List(TextPropertyValue(""))),
      SimpleTrafficSignProperty("trafficSigns_info", List(TextPropertyValue("Additional Info for test"))))

    val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val id = service.create(IncomingTrafficSign(2.0, 0.0, 388553075, properties, 1, None), testUser.username, roadLink)

    val properties1 = Set(
      SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue("83"))),
      SimpleTrafficSignProperty("trafficSigns_value", List(TextPropertyValue(""))),
      SimpleTrafficSignProperty("trafficSigns_info", List(TextPropertyValue("Additional Info for test"))))

    val id1 = service.create(IncomingTrafficSign(2.0, 0.0, 388553075, properties1, 1, None), testUser.username, roadLink)

    val properties2 = properties80

    val id2 = service.create(IncomingTrafficSign(2.0, 0.0, 388553075, properties2, 1, None), testUser.username, roadLink)

    val result = service.getByMunicipalityAndGroup(235, TrafficSignTypeGroup.GeneralWarningSigns)

    result.map(_.id) should be (Seq(id, id1))
    }
  }

  test("Should not return nothing on get by municipality and group"){
    when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(235)).thenReturn((Seq(
      VVHRoadlink(388553075, 235,Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink), Nil))

    runWithRollback {

      val properties = properties80

      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingTrafficSign(2.0, 0.0, 388553075, properties, 1, None), testUser.username, roadLink)

      val result = service.getByMunicipalityAndGroup(235, TrafficSignTypeGroup.GeneralWarningSigns)

      result.map(_.id) should be (Seq())
    }
  }

  test("Get additional panels in 2 meter buffer area for specific sign"){
    runWithRollback {

      val properties = Set(
        SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue("82"))),
        SimpleTrafficSignProperty("trafficSigns_value", List(TextPropertyValue(""))),
        SimpleTrafficSignProperty("trafficSigns_info", List(TextPropertyValue("Additional Info for test"))))

      val additionalPanelProperties = Set(
        SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue("45"))),
        SimpleTrafficSignProperty("trafficSigns_value", List(TextPropertyValue(""))),
        SimpleTrafficSignProperty("trafficSigns_info", List(TextPropertyValue("Additional Info for test"))))

      val roadLink = RoadLink(1000, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val trafficSign = service.create(IncomingTrafficSign(5.0, 0.0, 1000, properties, SideCode.TowardsDigitizing.value, None), testUser.username, roadLink)

      val additionalPanel = service.create(IncomingTrafficSign(6.0, 0.0, 1000, additionalPanelProperties, SideCode.TowardsDigitizing.value, None), testUser.username, roadLink)
      val additionalPanel2 = service.create(IncomingTrafficSign(4.0, 0.0, 1000, additionalPanelProperties, SideCode.TowardsDigitizing.value, None), testUser.username, roadLink)
      val additionalPanel3 = service.create(IncomingTrafficSign(8.0, 0.0, 1000, additionalPanelProperties, SideCode.TowardsDigitizing.value, None), testUser.username, roadLink)
      val additionalPanel4 = service.create(IncomingTrafficSign(4.5, 0.0, 1000, additionalPanelProperties, SideCode.AgainstDigitizing.value, None), testUser.username, roadLink)


      when(mockRoadLinkService.getRoadLinkFromVVH(roadLink.linkId)).thenReturn(Some(roadLink))
      when(mockRoadLinkService.getAdjacent(any[Long], any[Seq[Point]], any[Boolean])).thenReturn(Seq())

      val sign = service.getPersistedAssetsByIds(Set(trafficSign)).head

      val additionalPanels = service.getPersistedAssetsByIds(Set(additionalPanel, additionalPanel2, additionalPanel3, additionalPanel4)).map { panel =>
        AdditionalPanelInfo(Some(panel.id), panel.mValue, panel.linkId, panel.propertyData.map(x => SimpleTrafficSignProperty(x.publicId, x.values)).toSet, panel.validityDirection)
      }

      val result = service.getAdditionalPanels(sign.linkId, sign.mValue, sign.validityDirection, 82, roadLink.geometry, additionalPanels, Seq())

      result.size should be (2)
      result.exists(_.id.contains(additionalPanel)) should be (true)
      result.exists(_.id.contains(additionalPanel2)) should be (true)
      result.exists(_.id.contains(additionalPanel3)) should be (false)
      result.exists(_.id.contains(additionalPanel4)) should be (false)
    }
  }

  test("Get additional panels in 2 meter buffer area for specific sign with adjacent links"){
    runWithRollback {

      val properties = Set(
        SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue("82"))),
        SimpleTrafficSignProperty("trafficSigns_value", List(TextPropertyValue(""))),
        SimpleTrafficSignProperty("trafficSigns_info", List(TextPropertyValue("Additional Info for test"))))

      val additionalPanelProperties = Set(
        SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue("45"))),
        SimpleTrafficSignProperty("trafficSigns_value", List(TextPropertyValue(""))),
        SimpleTrafficSignProperty("trafficSigns_info", List(TextPropertyValue("Additional Info for test"))))

      val roadLink = RoadLink(1000, Seq(Point(10.0, 0.0), Point(13.0, 0.0)), 3, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(2000, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(3000, Seq(Point(13.0, 0.0), Point(20.0, 0.0)), 7, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val trafficSign = service.create(IncomingTrafficSign(12.0, 0.0, 1000, properties, SideCode.TowardsDigitizing.value, None), testUser.username, roadLink)
      val additionalPanel2 = service.create(IncomingTrafficSign(10.0, 0.0, 2000, additionalPanelProperties, SideCode.TowardsDigitizing.value, None), testUser.username, roadLink2)
      val additionalPanel3 = service.create(IncomingTrafficSign(14.0, 0.0, 3000, additionalPanelProperties, SideCode.TowardsDigitizing.value, None), testUser.username, roadLink3)


      when(mockRoadLinkService.getRoadLinkFromVVH(roadLink.linkId)).thenReturn(Some(roadLink))
      when(mockRoadLinkService.getAdjacent(1000, Seq(Point(10.0, 0.0)))).thenReturn(Seq(roadLink2))
      when(mockRoadLinkService.getAdjacent(1000, Seq(Point(13.0, 0.0)))).thenReturn(Seq(roadLink3))

      val sign = service.getPersistedAssetsByIds(Set(trafficSign)).head

      val additionalPanels = service.getPersistedAssetsByIds(Set(additionalPanel2, additionalPanel3)).map { panel =>
        AdditionalPanelInfo(Some(panel.id), panel.mValue, panel.linkId, panel.propertyData.map(x => SimpleTrafficSignProperty(x.publicId, x.values)).toSet, panel.validityDirection)
      }

      val result = service.getAdditionalPanels(sign.linkId, sign.mValue, sign.validityDirection, 82, roadLink.geometry, additionalPanels, Seq())

      result.size should be (2)
      result.exists(_.id.contains(additionalPanel2)) should be (true)
      result.exists(_.id.contains(additionalPanel3)) should be (true)
    }
  }

  test("No additional panels detected with adjacent links"){
    runWithRollback {

      val properties = Set(
        SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue("82"))),
        SimpleTrafficSignProperty("trafficSigns_value", List(TextPropertyValue(""))),
        SimpleTrafficSignProperty("trafficSigns_info", List(TextPropertyValue("Additional Info for test"))))

      val additionalPanelProperties = Set(
        SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue("46"))),
        SimpleTrafficSignProperty("trafficSigns_value", List(TextPropertyValue(""))),
        SimpleTrafficSignProperty("trafficSigns_info", List(TextPropertyValue("Additional Info for test"))))

      val roadLink = RoadLink(1000, Seq(Point(10.0, 0.0), Point(13.0, 0.0)), 3, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(2000, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(3000, Seq(Point(13.0, 0.0), Point(20.0, 0.0)), 7, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val trafficSign = service.create(IncomingTrafficSign(12.0, 0.0, 1000, properties, SideCode.TowardsDigitizing.value, None), testUser.username, roadLink)
      val additionalPanel2 = service.create(IncomingTrafficSign(10.0, 0.0, 2000, additionalPanelProperties, SideCode.TowardsDigitizing.value, None), testUser.username, roadLink2)
      val additionalPanel3 = service.create(IncomingTrafficSign(14.0, 0.0, 3000, additionalPanelProperties, SideCode.TowardsDigitizing.value, None), testUser.username, roadLink3)


      when(mockRoadLinkService.getRoadLinkFromVVH(roadLink.linkId)).thenReturn(Some(roadLink))
      when(mockRoadLinkService.getAdjacent(1000, Seq(Point(10.0, 0.0)))).thenReturn(Seq(roadLink2))
      when(mockRoadLinkService.getAdjacent(1000, Seq(Point(13.0, 0.0)))).thenReturn(Seq(roadLink3))

      val sign = service.getPersistedAssetsByIds(Set(trafficSign)).head

      val additionalPanels = service.getPersistedAssetsByIds(Set(additionalPanel2, additionalPanel3)).map { panel =>
        AdditionalPanelInfo(Some(panel.id), panel.mValue, panel.linkId, panel.propertyData.map(x => SimpleTrafficSignProperty(x.publicId, x.values)).toSet, panel.validityDirection)
      }

      val result = service.getAdditionalPanels(sign.linkId, sign.mValue, sign.validityDirection, 82, roadLink.geometry, additionalPanels, Seq())

      result.size should be (0)
    }
  }

  test("No additional panels detected"){
    runWithRollback {

      val properties = Set(
        SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue("82"))),
        SimpleTrafficSignProperty("trafficSigns_value", List(TextPropertyValue(""))),
        SimpleTrafficSignProperty("trafficSigns_info", List(TextPropertyValue("Additional Info for test"))))

      val additionalPanelProperties = Set(
        SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue("46"))),
        SimpleTrafficSignProperty("trafficSigns_value", List(TextPropertyValue(""))),
        SimpleTrafficSignProperty("trafficSigns_info", List(TextPropertyValue("Additional Info for test"))))

      val roadLink = RoadLink(1000, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val trafficSign = service.create(IncomingTrafficSign(5.0, 0.0, 1000, properties, SideCode.TowardsDigitizing.value, None), testUser.username, roadLink)

      val additionalPanel = service.create(IncomingTrafficSign(6.0, 0.0, 1000, additionalPanelProperties, SideCode.TowardsDigitizing.value, None), testUser.username, roadLink)
      val additionalPanel2 = service.create(IncomingTrafficSign(4.0, 0.0, 1000, additionalPanelProperties, SideCode.TowardsDigitizing.value, None), testUser.username, roadLink)
      val additionalPanel3 = service.create(IncomingTrafficSign(8.0, 0.0, 1000, additionalPanelProperties, SideCode.TowardsDigitizing.value, None), testUser.username, roadLink)
      val additionalPanel4 = service.create(IncomingTrafficSign(4.5, 0.0, 1000, additionalPanelProperties, SideCode.AgainstDigitizing.value, None), testUser.username, roadLink)


      when(mockRoadLinkService.getRoadLinkFromVVH(roadLink.linkId)).thenReturn(Some(roadLink))
      when(mockRoadLinkService.getAdjacent(any[Long], any[Seq[Point]], any[Boolean])).thenReturn(Seq())

      val sign = service.getPersistedAssetsByIds(Set(trafficSign)).head

      val additionalPanels = service.getPersistedAssetsByIds(Set(additionalPanel, additionalPanel2, additionalPanel3, additionalPanel4)).map { panel =>
        AdditionalPanelInfo(Some(panel.id), panel.mValue, panel.linkId, panel.propertyData.map(x => SimpleTrafficSignProperty(x.publicId, x.values)).toSet, panel.validityDirection)
      }

      val result = service.getAdditionalPanels(sign.linkId, sign.mValue, sign.validityDirection, 82, roadLink.geometry, additionalPanels, Seq())

      result.size should be (0)
    }
  }
}

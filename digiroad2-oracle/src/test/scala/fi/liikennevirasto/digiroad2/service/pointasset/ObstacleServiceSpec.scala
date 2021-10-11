package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import org.joda.time.DateTime
import slick.jdbc.StaticQuery.interpolation


class ObstacleServiceSpec extends FunSuite with Matchers {

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
  when(mockRoadLinkService.getRoadLinkByLinkIdFromVVH(1611317)).thenReturn(Seq(
    VVHRoadlink(1611317, 235, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Municipality,
      TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink).headOption)

  when(mockRoadLinkService.getRoadLinkByLinkIdFromVVH(1191950690)).thenReturn(Seq(
    VVHRoadlink(1191950690, 235, Seq(Point(373500.349, 6677657.152), Point(373494.182, 6677669.918)), Private,
      TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink).headOption)

  when(mockRoadLinkService.getHistoryDataLinksFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq())

  val service = new ObstacleService(mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(PostGISDatabase.ds)(test)

  test("Can fetch by bounding box") {
    when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((List(), Nil))

    runWithRollback {
      val result = service.getByBoundingBox(testUser, BoundingRectangle(Point(374466.5, 6677346.5), Point(374467.5, 6677347.5))).head
      result.id should equal(600046)
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
      val result = service.getByMunicipality(235).find(_.id == 600046).get

      result.id should equal(600046)
      result.linkId should equal(1611317)
      result.lon should equal(374467)
      result.lat should equal(6677347)
      result.mValue should equal(103)
    }
  }

  test("Expire obstacle") {
    when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[Int])).thenReturn((List(), Nil))

    runWithRollback {
      val result = service.getByMunicipality(235).find(_.id == 600046).get
      result.id should equal(600046)

      service.expire(600046, "unit_test")

      service.getByMunicipality(235).find(_.id == 600046) should equal(None)
    }
  }

  test("Create new obstacle") {
    runWithRollback {
      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val values = Seq(PropertyValue("2"))
      val simpleProperty = SimplePointAssetProperty("esterakennelma", values)
      val id = service.create(IncomingObstacle(2.0, 0.0, 388553075, Set(simpleProperty)), "jakke", roadLink)

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
      asset.propertyData.find(_.publicId == "esterakennelma").get.values.head.asInstanceOf[PropertyValue].propertyValue.toInt should be(2)
      asset.createdBy should be(Some("jakke"))
      asset.createdAt shouldBe defined
    }
  }

  test("Update obstacle") {
    runWithRollback {
      val obstacle = service.getById(600046).get
      val values = Seq(PropertyValue("2"))
      val simpleProperty = SimplePointAssetProperty("esterakennelma", values)
      val updated = IncomingObstacle(obstacle.lon, obstacle.lat, obstacle.linkId, Set(simpleProperty))

      val roadLink =  RoadLink(obstacle.linkId, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 200, Municipality, UnknownFunctionalClass.value, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)), linkSource = NormalLinkInterface)
      service.update(obstacle.id, updated, roadLink, "unit_test")
      val updatedObstacle = service.getById(600046).get

      updatedObstacle.propertyData.find(_.publicId == "esterakennelma").get.values.head.asInstanceOf[PropertyValue].propertyValue.toInt should equal(2)
      updatedObstacle.id should equal(obstacle.id)
      updatedObstacle.modifiedBy should equal(Some("unit_test"))
      updatedObstacle.modifiedAt shouldBe defined
    }
  }

  test("Asset can be outside link within treshold") {
    runWithRollback {
      val roadLink = RoadLink(1191950690, Seq(Point(373500.349, 6677657.152), Point(373494.182, 6677669.918)), 14.178, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val values = Seq(PropertyValue("2"))
      val simpleProperty = SimplePointAssetProperty("esterakennelma", values)
      val id = service.create(IncomingObstacle(373494.183, 6677669.927, 1191950690, Set(simpleProperty)), "unit_test", roadLink)
      val asset = service.getById(id).get
      asset.floating should be(false)
    }
  }

  test("should not float") {
    val testUser = User(
      id = 1,
      username = "Hannu",
      configuration = Configuration(authorizedMunicipalities = Set(235)))
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]

    val geometry = Seq(Point(240863.911, 6700590.15),
          Point(240864.188, 6700595.048),
          Point(240863.843, 6700601.473),
          Point(240862.771, 6700609.933),
          Point(240861.592, 6700619.412),
          Point(240859.882, 6700632.051),
          Point(240862.857, 6700644.888),
          Point(240864.957, 6700651.228),
          Point(240867.555, 6700657.523),
          Point(240869.228, 6700664.658),
          Point(240871.009, 6700670.273),
          Point(240877.602, 6700681.724),
          Point(240881.381, 6700685.655),
          Point(240885.898, 6700689.602))

    val roadLink = RoadLink(5797521, geometry, 101.85, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(853)))

    when(mockRoadLinkService.getRoadLinksFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn(Seq(roadLink))
    when(mockRoadLinkService.getRoadLinkByLinkIdFromVVH(5797521)).thenReturn(Seq(roadLink).headOption)

    val service = new ObstacleService(mockRoadLinkService) {
      override def withDynTransaction[T](f: => T): T = f
      override def withDynSession[T](f: => T): T = f
    }

    runWithRollback {
      val roadLink = RoadLink(5797521, geometry, 101.85, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(853)))
      val values = Seq(PropertyValue("2"))
      val simpleProperty = SimplePointAssetProperty("esterakennelma", values)
      val id = service.create(IncomingObstacle(240877.69416595, 6700681.8198731, 5797521, Set(simpleProperty)), "unit_test", roadLink)

      val asset = service.getById(id).get

      asset.floating should be(false)
    }

  }

  test("Can fetch a list of floating Obstacles") {
    PostGISDatabase.withDynTransaction {
      val lastIdUpdate = 0
      val lineRange = 1000
      val obstacleAssetTypeId = 220
      val lrmPositionsIds = Queries.fetchLrmPositionIds(11)

      sqlu"""insert into asset (id,asset_type_id,floating) VALUES (1,$obstacleAssetTypeId,'1')""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(0)}, 6000, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (1,${lrmPositionsIds(0)})""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) VALUES (400000, 1, (SELECT id FROM PROPERTY WHERE PUBLIC_ID ='esterakennelma'), 1)""".execute

      sqlu"""insert into asset (id,asset_type_id,floating) VALUES (2,$obstacleAssetTypeId,'1')""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(1)}, 6000, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (2,${lrmPositionsIds(1)})""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) VALUES (400001, 2, (SELECT id FROM PROPERTY WHERE PUBLIC_ID ='esterakennelma'), 1)""".execute

      sqlu"""insert into asset (id,asset_type_id,floating) VALUES (3,$obstacleAssetTypeId,'1')""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(2)}, 7000, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (3,${lrmPositionsIds(2)})""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) VALUES (400003, 3, (SELECT id FROM PROPERTY WHERE PUBLIC_ID ='esterakennelma'), 1)""".execute

      sqlu"""insert into asset (id,asset_type_id,floating) VALUES (4,$obstacleAssetTypeId,'1')""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(3)}, 8000, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (4,${lrmPositionsIds(3)})""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) VALUES (400004, 4, (SELECT id FROM PROPERTY WHERE PUBLIC_ID ='esterakennelma'), 1)""".execute

      sqlu"""insert into asset (id,asset_type_id,floating) VALUES (5,$obstacleAssetTypeId,'1')""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(4)}, 9000, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (5,${lrmPositionsIds(4)})""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) VALUES (400005, 5, (SELECT id FROM PROPERTY WHERE PUBLIC_ID ='esterakennelma'), 1)""".execute

      sqlu"""insert into asset (id,asset_type_id,floating) VALUES (6,$obstacleAssetTypeId,'1')""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(5)}, 1000, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (6,${lrmPositionsIds(5)})""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) VALUES (400006, 6, (SELECT id FROM PROPERTY WHERE PUBLIC_ID ='esterakennelma'), 1)""".execute

      sqlu"""insert into asset (id,asset_type_id,floating) VALUES (7,$obstacleAssetTypeId,'1')""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(6)}, 1100, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (7,${lrmPositionsIds(6)})""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) VALUES (400007, 7, (SELECT id FROM PROPERTY WHERE PUBLIC_ID ='esterakennelma'), 1)""".execute

      sqlu"""insert into asset (id,asset_type_id,floating) VALUES (8,$obstacleAssetTypeId,'1')""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(7)}, 1200, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (8,${lrmPositionsIds(7)})""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) VALUES (400008, 8, (SELECT id FROM PROPERTY WHERE PUBLIC_ID ='esterakennelma'), 1)""".execute

      sqlu"""insert into asset (id,asset_type_id,floating) VALUES (9,$obstacleAssetTypeId,'1')""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(8)}, 1300, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (9,${lrmPositionsIds(8)})""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) VALUES (400009, 9, (SELECT id FROM PROPERTY WHERE PUBLIC_ID ='esterakennelma'), 1)""".execute

      sqlu"""insert into asset (id,asset_type_id,floating) VALUES (10,$obstacleAssetTypeId,'1')""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(9)}, 1400, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (10,${lrmPositionsIds(9)})""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) VALUES (400010, 10, (SELECT id FROM PROPERTY WHERE PUBLIC_ID ='esterakennelma'), 1)""".execute

      sqlu"""insert into asset (id,asset_type_id,floating) VALUES (11,$obstacleAssetTypeId,'1')""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(10)}, 1500, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (11,${lrmPositionsIds(10)})""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) VALUES (400011, 11, (SELECT id FROM PROPERTY WHERE PUBLIC_ID ='esterakennelma'), 1)""".execute

      sqlu"""
            UPDATE asset
               SET geometry = ST_GeomFromText('POINT(374443.764141219 6677245.28337185 0 0)',3067)
            WHERE id IN (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
          """.execute


      val result = service.getFloatingObstacles('1', lastIdUpdate, lineRange).sortBy(_.id)

      result.foreach { fields =>
        fields.floating should be(true)
      }

      result(0).id should be (1)
      result(1).id should be (2)
      result(2).id should be (3)
      result(3).id should be (4)
      result(4).id should be (5)
      result(5).id should be (6)
      result(6).id should be (7)
      result(7).id should be (8)
      result(8).id should be (9)
      result(9).id should be (10)
      result(10).id should be (11)

      dynamicSession.rollback()
    }
  }

  test("Update floating obstacle") {
    PostGISDatabase.withDynTransaction {
      val obstacle = service.getById(600046).get
      val newMvalue = obstacle.mValue+1
      val pointAssetProperty = Property(111111, "suggest_box", "checkbox", false, Seq(PropertyValue("0", None, false)))
      val newObstacle = obstacle.copy(municipalityCode = 500, floating = true, mValue = newMvalue, linkId = 1611317, modifiedBy = Some("unit_test"), propertyData = Seq(pointAssetProperty))

      service.updateFloatingAsset(newObstacle)
      val updatedObstacle = service.getById(600046).get

      updatedObstacle.linkId should equal (1611317)
      updatedObstacle.mValue should equal (newMvalue)
      updatedObstacle.modifiedBy should equal (Some("unit_test"))
      updatedObstacle.municipalityCode should equal(500)
      updatedObstacle.id should equal(obstacle.id)
      updatedObstacle.floating should be(true)

      dynamicSession.rollback()
    }
  }

  test("Update obstacle with geometry changes"){
    runWithRollback {
      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(0.0, 20.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val values = Seq(PropertyValue("2"))
      val simpleProperty = SimplePointAssetProperty("esterakennelma", values)
      val id = service.create(IncomingObstacle(0.0, 20.0, 388553075, Set(simpleProperty)), "jakke", roadLink )
      val oldAsset = service.getPersistedAssetsByIds(Set(id)).head
      val newId = service.update(id, IncomingObstacle(0.0, 10.0, 388553075, Set(simpleProperty)), roadLink, "test")
      oldAsset.modifiedAt.isDefined should equal(false)
      val updatedAsset = service.getPersistedAssetsByIds(Set(newId)).head
      updatedAsset.id should not be id
      updatedAsset.lon should equal (0.0)
      updatedAsset.lat should equal (10.0)
      updatedAsset.propertyData.find(_.publicId == "esterakennelma").get.values.head.asInstanceOf[PropertyValue].propertyValue.toInt should equal(2)
      updatedAsset.createdBy should equal (oldAsset.createdBy)
      updatedAsset.createdAt should equal (oldAsset.createdAt)
      updatedAsset.modifiedBy should equal (Some("test"))
      updatedAsset.modifiedAt.isDefined should equal(true)
    }
  }

  test("Update obstacle without geometry changes"){
    runWithRollback {
      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(0.0, 20.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val values = Seq(PropertyValue("2"))
      val simpleProperty = SimplePointAssetProperty("esterakennelma", values)
      val id = service.create(IncomingObstacle(0.0, 20.0, 388553075, Set(simpleProperty)), "jakke", roadLink )
      val asset = service.getPersistedAssetsByIds(Set(id)).head

      val updatedValues = Seq(PropertyValue("1"))
      val updatedSimpleProperty = SimplePointAssetProperty("esterakennelma", updatedValues)
      val newId = service.update(id, IncomingObstacle(0.0, 20.0, 388553075, Set(updatedSimpleProperty)), roadLink, "test")

      val updatedAsset = service.getPersistedAssetsByIds(Set(newId)).head
      updatedAsset.id should be (id)
      updatedAsset.lon should be (asset.lon)
      updatedAsset.lat should be (asset.lat)
      updatedAsset.createdBy should equal (Some("jakke"))
      updatedAsset.modifiedBy should equal (Some("test"))
      updatedAsset.propertyData.find(_.publicId == "esterakennelma").get.values.head.asInstanceOf[PropertyValue].propertyValue.toInt should equal(1)
    }
  }

  test("Get obstacles changes") {
    runWithRollback {
      when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(Set(388553075))).thenReturn(Seq(RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))))

      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val values = Seq(PropertyValue("2"))
      val simpleProperty = SimplePointAssetProperty("esterakennelma", values)
      val (id, id1, id2) = (service.create(IncomingObstacle(2.0, 0.0, 388553075, Set(simpleProperty)), "jakke", roadLink), service.create(IncomingObstacle(2.0, 0.0, 388553075, Set(simpleProperty)), "jakke_1", roadLink), service.create(IncomingObstacle(2.0, 0.0, 388553075, Set(simpleProperty)), "jakke_2", roadLink))

      val changes = service.getChanged(DateTime.now().minusDays(1), DateTime.now().plusDays(1))
      changes.length should be(3)

      service.expire(id)

      val changesAfterExpire = service.getChanged(DateTime.now().minusDays(1), DateTime.now().plusDays(1))
      changesAfterExpire.length should be(3)

    }
  }

  test("Get only one obstacle change"){
    runWithRollback{
      val obstacleAssetTypeId = 220
      val lrmPositionsIds = Queries.fetchLrmPositionIds(11)

      sqlu"""insert into asset (id,asset_type_id,floating, created_date) VALUES (11,$obstacleAssetTypeId,'0', TO_DATE('17/12/2016', 'DD/MM/YYYY'))""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(10)}, 388553075, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (11,${lrmPositionsIds(10)})""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) VALUES (400011, 11, (SELECT id FROM PROPERTY WHERE PUBLIC_ID ='esterakennelma'), 1)""".execute
      Queries.updateAssetGeometry(11, Point(5, 0))

      val assets = service.getPersistedAssetsByIds(Set(11))

      when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(Set(388553075))).thenReturn(Seq(RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))))

      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val values = Seq(PropertyValue("2"))
      val simpleProperty = SimplePointAssetProperty("esterakennelma", values)
      val id = service.create(IncomingObstacle(2.0, 0.0, 388553075, Set(simpleProperty)), "jakke", roadLink)

      val changes = service.getChanged(DateTime.parse("2016-11-01T12:00Z"), DateTime.parse("2016-12-31T12:00Z"))
      changes.length should be(1)
    }
  }

  test("Obstacles get change does not return floating obstacles"){
    runWithRollback{
      val obstacleAssetTypeId = 220
      val lrmPositionsIds = Queries.fetchLrmPositionIds(11)

      sqlu"""insert into asset (id,asset_type_id,floating, created_date) VALUES (11,$obstacleAssetTypeId, '1', TO_DATE('17/12/2016', 'DD/MM/YYYY'))""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(10)}, 388553075, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (11,${lrmPositionsIds(10)})""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) VALUES (400011, 11, (SELECT id FROM PROPERTY WHERE PUBLIC_ID ='esterakennelma'), 1)""".execute
      Queries.updateAssetGeometry(11, Point(5, 0))

      sqlu"""insert into asset (id,asset_type_id,floating) VALUES (2,$obstacleAssetTypeId,'1')""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(1)}, 388553075, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (2,${lrmPositionsIds(1)})""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) VALUES (400001, 2, (SELECT id FROM PROPERTY WHERE PUBLIC_ID ='esterakennelma'), 1)""".execute
      Queries.updateAssetGeometry(2, Point(5, 0))

      when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq())

      val changes = service.getChanged(DateTime.now().minusDays(1), DateTime.now().plusDays(1))
      changes.length should be(0)
    }
  }
}

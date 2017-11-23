package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.pointasset.oracle.Obstacle
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.jdbc.StaticQuery.interpolation
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Queries
import org.joda.time.DateTime



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
  when(mockRoadLinkService.getRoadLinkFromVVH(1611317)).thenReturn(Seq(
    VVHRoadlink(1611317, 235, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Municipality,
      TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink).headOption)

  when(mockRoadLinkService.getRoadLinkFromVVH(1191950690)).thenReturn(Seq(
    VVHRoadlink(1191950690, 235, Seq(Point(373500.349, 6677657.152), Point(373494.182, 6677669.918)), Private,
      TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink).headOption)

  val service = new ObstacleService(mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = f

    override def withDynSession[T](f: => T): T = f
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(service.dataSource)(test)

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
      val id = service.create(IncomingObstacle(2.0, 0.0, 388553075, 2), "jakke", roadLink)

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
      asset.obstacleType should be(2)
      asset.createdBy should be(Some("jakke"))
      asset.createdAt shouldBe defined
    }
  }

  test("Update obstacle") {
    runWithRollback {
      val obstacle = service.getById(600046).get
      val updated = IncomingObstacle(obstacle.lon, obstacle.lat, obstacle.linkId, 2)

      service.update(obstacle.id, updated, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 235, "unit_test", linkSource = NormalLinkInterface)
      val updatedObstacle = service.getById(600046).get

      updatedObstacle.obstacleType should equal(2)
      updatedObstacle.id should equal(obstacle.id)
      updatedObstacle.modifiedBy should equal(Some("unit_test"))
      updatedObstacle.modifiedAt shouldBe defined
    }
  }

  test("Asset can be outside link within treshold") {
    runWithRollback {
      val roadLink = RoadLink(1191950690, Seq(Point(373500.349, 6677657.152), Point(373494.182, 6677669.918)), 14.178, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingObstacle(373494.183, 6677669.927, 1191950690, 2), "unit_test", roadLink)
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
    when(mockRoadLinkService.getRoadLinkFromVVH(5797521)).thenReturn(Seq(roadLink).headOption)

    val service = new ObstacleService(mockRoadLinkService) {
      override def withDynTransaction[T](f: => T): T = f
      override def withDynSession[T](f: => T): T = f
    }

    runWithRollback {
      val roadLink = RoadLink(5797521, geometry, 101.85, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(853)))
      val id = service.create(IncomingObstacle(240877.69416595, 6700681.8198731, 5797521, 2), "unit_test", roadLink)

      val asset = service.getById(id).get

      asset.floating should be(false)
    }

  }

  test("Can fetch a list of floating Obstacles") {
    OracleDatabase.withDynTransaction {
      val lastIdUpdate = 0
      val lineRange = 1000
      val obstacleAssetTypeId = 220
      val lrmPositionsIds = Queries.fetchLrmPositionIds(11)

      sqlu"""insert into asset (id,asset_type_id,floating) VALUES (1,$obstacleAssetTypeId,1)""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(0)}, 6000, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (1,${lrmPositionsIds(0)})""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) VALUES (400000, 1, 300080, 1)""".execute

      sqlu"""insert into asset (id,asset_type_id,floating) VALUES (2,$obstacleAssetTypeId,1)""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(1)}, 6000, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (2,${lrmPositionsIds(1)})""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) VALUES (400001, 2, 300080, 1)""".execute

      sqlu"""insert into asset (id,asset_type_id,floating) VALUES (3,$obstacleAssetTypeId,1)""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(2)}, 7000, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (3,${lrmPositionsIds(2)})""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) VALUES (400003, 3, 300080, 1)""".execute

      sqlu"""insert into asset (id,asset_type_id,floating) VALUES (4,$obstacleAssetTypeId,1)""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(3)}, 8000, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (4,${lrmPositionsIds(3)})""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) VALUES (400004, 4, 300080, 1)""".execute

      sqlu"""insert into asset (id,asset_type_id,floating) VALUES (5,$obstacleAssetTypeId,1)""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(4)}, 9000, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (5,${lrmPositionsIds(4)})""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) VALUES (400005, 5, 300080, 1)""".execute

      sqlu"""insert into asset (id,asset_type_id,floating) VALUES (6,$obstacleAssetTypeId,1)""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(5)}, 1000, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (6,${lrmPositionsIds(5)})""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) VALUES (400006, 6, 300080, 1)""".execute

      sqlu"""insert into asset (id,asset_type_id,floating) VALUES (7,$obstacleAssetTypeId,1)""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(6)}, 1100, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (7,${lrmPositionsIds(6)})""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) VALUES (400007, 7, 300080, 1)""".execute

      sqlu"""insert into asset (id,asset_type_id,floating) VALUES (8,$obstacleAssetTypeId,1)""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(7)}, 1200, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (8,${lrmPositionsIds(7)})""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) VALUES (400008, 8, 300080, 1)""".execute

      sqlu"""insert into asset (id,asset_type_id,floating) VALUES (9,$obstacleAssetTypeId,1)""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(8)}, 1300, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (9,${lrmPositionsIds(8)})""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) VALUES (400009, 9, 300080, 1)""".execute

      sqlu"""insert into asset (id,asset_type_id,floating) VALUES (10,$obstacleAssetTypeId,1)""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(9)}, 1400, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (10,${lrmPositionsIds(9)})""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) VALUES (400010, 10, 300080, 1)""".execute

      sqlu"""insert into asset (id,asset_type_id,floating) VALUES (11,$obstacleAssetTypeId,1)""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(10)}, 1500, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (11,${lrmPositionsIds(10)})""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) VALUES (400011, 11, 300080, 1)""".execute

      sqlu"""
            UPDATE asset SET geometry = MDSYS.SDO_GEOMETRY(4401,
              3067,
              NULL,
              MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1),
              MDSYS.SDO_ORDINATE_ARRAY(374443.764141219, 6677245.28337185, 0, 0))
            WHERE id IN (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
          """.execute


      val result = service.getFloatingObstacles(1, lastIdUpdate, lineRange)

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
    OracleDatabase.withDynTransaction {
      val obstacle = service.getById(600046).get
      val newMvalue = obstacle.mValue+1
      val newObstacle = obstacle.copy(municipalityCode = 500, floating = true, mValue = newMvalue, linkId = 1611317, modifiedBy = Some("unit_test"))

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
      val id = service.create(IncomingObstacle(0.0, 20.0, 388553075, 2), "jakke", roadLink )

      val newId = service.update(id, IncomingObstacle(0.0, 10.0, 388553075, 2),Seq(Point(0.0, 0.0), Point(0.0, 20.0)), 235, "test", linkSource = NormalLinkInterface)

      val updatedAsset = service.getPersistedAssetsByIds(Set(newId)).head
      updatedAsset.id should not be id
      updatedAsset.lon should equal (0.0)
      updatedAsset.lat should equal (10.0)
      updatedAsset.obstacleType should equal(2)
      updatedAsset.createdBy should equal (Some("test"))
    }
  }

  test("Update obstacle without geometry changes"){
    runWithRollback {
      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(0.0, 20.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingObstacle(0.0, 20.0, 388553075, 2), "jakke", roadLink )
      val asset = service.getPersistedAssetsByIds(Set(id)).head

      val newId = service.update(id, IncomingObstacle(0.0, 20.0, 388553075,1),Seq(Point(0.0, 0.0), Point(0.0, 20.0)), 235, "test", linkSource = NormalLinkInterface)

      val updatedAsset = service.getPersistedAssetsByIds(Set(newId)).head
      updatedAsset.id should be (id)
      updatedAsset.lon should be (asset.lon)
      updatedAsset.lat should be (asset.lat)
      updatedAsset.createdBy should equal (Some("jakke"))
      updatedAsset.modifiedBy should equal (Some("test"))
      updatedAsset.obstacleType should equal(1)
    }
  }
}

package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.dao.pointasset.{OraclePedestrianCrossingDao, PedestrianCrossing}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.process.AssetValidatorInfo
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util.TestTransactions
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils, Point}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

class PedestrianCrossingServiceSpec extends FunSuite with Matchers {
  def toRoadLink(l: VVHRoadlink) = {
    RoadLink(l.linkId, l.geometry, GeometryUtils.geometryLength(l.geometry),
      l.administrativeClass, 1, l.trafficDirection, UnknownLinkType, None, None, l.attributes + ("MUNICIPALITYCODE" -> BigInt(l.municipalityCode)))
  }

  val testUser = User(
    id = 1,
    username = "Hannu",
    configuration = Configuration(authorizedMunicipalities = Set(235)))
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  when(mockRoadLinkService.getRoadLinksFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn(Seq(
    VVHRoadlink(1611317, 235, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Municipality,
      TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink))

  val service = new PedestrianCrossingService(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
    override lazy val dao: OraclePedestrianCrossingDao = new OraclePedestrianCrossingDao()
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(service.dataSource)(test)

  test("Can fetch by bounding box") {
    when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((List(), Nil))

    runWithRollback {
      val result = service.getByBoundingBox(testUser, BoundingRectangle(Point(374466.5, 6677346.5), Point(374467.5, 6677347.5))).head
      result.id should equal(600029)
      result.linkId should equal(1611317)
      result.lon should equal(374467)
      result.lat should equal(6677347)
      result.mValue should equal(103)
    }
  }

  test("Pedestrian crossing is adjusted on road link") {
    val roadLinkGeom = Seq(Point(374380.916,6677290.793),
      Point(374385.234,6677296.0),
      Point(374395.277,6677302.165),
      Point(374406.587,6677308.58),
      Point(374422.658,6677317.759),
      Point(374435.392,6677325.601),
      Point(374454.855,6677338.327),
      Point(374476.866,6677355.235),
      Point(374490.755,6677366.834),
      Point(374508.979,6677381.08))
    when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((Seq(
      VVHRoadlink(1611317, 235, roadLinkGeom, Municipality,
        TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink), Nil))

    runWithRollback {
      val values = Seq(TextPropertyValue("0"))
      val simpleProperty = SimplePointAssetProperty("pedestrian_crossings_suggest_box", values)
      service.dao.update(600029, IncomingPedestrianCrossing( 374406.8,6677308.2, 1611317, Set(simpleProperty)), 31.550, "Hannu", 235, None, linkSource = NormalLinkInterface)
      val result = service.getByBoundingBox(testUser, BoundingRectangle(Point(374406, 6677306.5), Point(374408.5, 6677309.5))).head
      result.id should equal(600029)
      result.linkId should equal(1611317)
      result.mValue should be (31.549 +- 0.001)
      result.floating should be (false)
      GeometryUtils.minimumDistance(Point(result.lon, result.lat), roadLinkGeom) should be < 0.005
    }
  }

  test("Can fetch by municipality") {
    when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(235)).thenReturn((Seq(
      VVHRoadlink(1611317, 235, Seq(Point(0.0, 0.0), Point(200.0, 0.0)), Municipality, TrafficDirection.BothDirections,
        FeatureClass.AllOthers)).map(toRoadLink), Nil))

    runWithRollback {
      val result = service.getByMunicipality(235).find(_.id == 600029).get

      result.id should equal(600029)
      result.linkId should equal(1611317)
      result.lon should equal(374467)
      result.lat should equal(6677347)
      result.mValue should equal(103)
    }
  }

  test("Expire pedestrian crossing") {
    runWithRollback {
      service.getPersistedAssetsByIds(Set(600029)).length should be(1)
      service.expire(600029, testUser.username)
      service.getPersistedAssetsByIds(Set(600029)) should be(Nil)
    }
  }

  test("Create new") {
    runWithRollback {
      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingPedestrianCrossing(2, 0.0, 388553075, Set()), "jakke", roadLink )
      val assets = service.getPersistedAssetsByIds(Set(id))

      assets.size should be(1)

      val asset = assets.head

      asset.vvhTimeStamp should not be(0)

      val propertyId = asset.propertyData.head.id
      val pointAssetProperty = PointAssetProperty(propertyId, "pedestrian_crossings_suggest_box", "checkbox", false, Seq(TextPropertyValue("", None, false)))

      asset should be(PedestrianCrossing(
        id = id,
        linkId = 388553075,
        lon = 2,
        lat = 0,
        mValue = 2,
        floating = false,
        vvhTimeStamp = asset.vvhTimeStamp,
        municipalityCode = 235,
        propertyData = Seq(pointAssetProperty),
        createdBy = Some("jakke"),
        createdAt = asset.createdAt,
        linkSource = NormalLinkInterface
      ))

      verify(mockEventBus, times(1)).publish("pedestrianCrossing:Validator", AssetValidatorInfo(Set(asset.id)))
    }
  }

  test("Update pedestrian crossing with geometry changes"){
    runWithRollback {
      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(0.0, 20.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingPedestrianCrossing(0.0, 20.0, 388553075, Set()), "jakke", roadLink )
      val oldAsset = service.getPersistedAssetsByIds(Set(id)).head

      val newId = service.update(id, IncomingPedestrianCrossing(0.0, 10.0, 388553075, Set()), roadLink, "test")

      val updatedAsset = service.getPersistedAssetsByIds(Set(newId)).head
      updatedAsset.id should not be id
      updatedAsset.lon should equal (0.0)
      updatedAsset.lat should equal (10.0)
      updatedAsset.createdBy should equal (oldAsset.createdBy)
      updatedAsset.createdAt should equal (oldAsset.createdAt)
      updatedAsset.modifiedBy should equal (Some("test"))
      updatedAsset.modifiedAt.isDefined should equal(true)

      verify(mockEventBus, times(1)).publish("pedestrianCrossing:Validator", AssetValidatorInfo(Set(id, newId)))
    }
  }

  test("Update pedestrian crossing without geometry changes"){
    runWithRollback {
      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(0.0, 20.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingPedestrianCrossing(0.0, 20.0, 388553075, Set()), "jakke", roadLink )
      val asset = service.getPersistedAssetsByIds(Set(id)).head

      val newId = service.update(id, IncomingPedestrianCrossing(0.0, 20.0, 388553075, Set()), roadLink, "test")

      val updatedAsset = service.getPersistedAssetsByIds(Set(newId)).head
      updatedAsset.id should be (id)
      updatedAsset.lon should be (asset.lon)
      updatedAsset.lat should be (asset.lat)
      updatedAsset.createdBy should equal (Some("jakke"))
      updatedAsset.modifiedBy should equal (Some("test"))

      verify(mockEventBus, times(2)).publish("pedestrianCrossing:Validator", AssetValidatorInfo(Set(id, newId)))
    }
  }

  test("Should get asset changes") {

    val roadLink1 = RoadLink(100, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(345)))
    val roadLink2 = RoadLink(200, List(Point(10.0, 0.0), Point(20.0, 0.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(345)))
    val roadLink3 = RoadLink(300, List(Point(20.0, 0.0), Point(30.0, 0.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(345)))

    when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3))

    runWithRollback {
      val (lrm1, lrm2, lrm3) = (Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextLrmPositionPrimaryKeySeqValue)
      val (asset1, asset2, asset3) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)
      sqlu"""insert into lrm_position (id, link_id) VALUES ($lrm1, 100)""".execute
      sqlu"""insert into asset (id, asset_type_id, created_date) values ($asset1, ${PedestrianCrossings.typeId}, TO_TIMESTAMP('2017-11-01 16:00', 'YYYY-MM-DD HH24:MI'))""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset1, $lrm1)""".execute
      Queries.updateAssetGeometry(asset1, Point(5, 0))
      sqlu"""insert into lrm_position (id, link_id) VALUES ($lrm2, 200)""".execute
      sqlu"""insert into asset (id, asset_type_id, created_date, modified_date) values ($asset2,  ${PedestrianCrossings.typeId},  TO_TIMESTAMP('2016-11-01 16:00', 'YYYY-MM-DD HH24:MI'), TO_TIMESTAMP('2017-11-01 16:00', 'YYYY-MM-DD HH24:MI'))""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset2, $lrm2)""".execute
      Queries.updateAssetGeometry(asset2, Point(15, 0))
      sqlu"""insert into lrm_position (id, link_id) VALUES ($lrm3, 300)""".execute
      sqlu"""insert into asset (id, asset_type_id, valid_to) values ($asset3,  ${PedestrianCrossings.typeId}, TO_TIMESTAMP('2017-11-01 16:00', 'YYYY-MM-DD HH24:MI'))""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset3, $lrm3)""".execute
      Queries.updateAssetGeometry(asset3, Point(25, 0))


      val result = service.getChanged(DateTime.parse("2017-11-01T12:00Z"), DateTime.parse("2017-11-02T12:00Z"))
      result.length should be(3)
    }
  }
}

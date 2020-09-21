package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
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


class WidthOfRoadAxisMarkingSpec extends FunSuite with Matchers {
  def toRoadLink(l: VVHRoadlink) = {
    RoadLink(l.linkId, l.geometry, GeometryUtils.geometryLength(l.geometry),
      l.administrativeClass, 1, l.trafficDirection, UnknownLinkType, None, None, l.attributes + ("MUNICIPALITYCODE" -> BigInt(l.municipalityCode)))
  }

  val testUser = User(
    id = 1,
    username = "Hannu",
    configuration = Configuration(authorizedMunicipalities = Set(235)))
  val widthOfRoadAxisMarkingTypeId = WidthOfRoadAxisMarkings.typeId
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

  val service = new WidthOfRoadAxisMarkingService(mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }

  val propertiesL1 = Set(SimplePointAssetProperty("widthOfRoadAxisMarking_regulation_number", List(PropertyValue("1"))))
  val propertiesL2 = Set(SimplePointAssetProperty("widthOfRoadAxisMarking_regulation_number", List(PropertyValue("2"))))

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(service.dataSource)(test)

  test("Can fetch by bounding box") {
    when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((List(), Nil))

    runWithRollback {
      val result = service.getByBoundingBox(testUser, BoundingRectangle(Point(374466.5, 6677346.5), Point(374467.5, 6677347.5))).head
      result.id should equal(600081)
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
      val result = service.getByMunicipality(235).find(_.id == 600081).get

      result.id should equal(600081)
      result.linkId should equal(1611317)
      result.lon should equal(374467)
      result.lat should equal(6677347)
      result.mValue should equal(103)
    }
  }

  test("Expire Width Of RoadAxis Marking") {
    runWithRollback {
      service.getPersistedAssetsByIds(Set(600081)).length should be(1)
      service.expire(600081, testUser.username)
      service.getPersistedAssetsByIds(Set(600081)) should be(Nil)
    }
  }

  test("Create new Width Of RoadAxis Marking") {
    runWithRollback {
      val properties = propertiesL1

      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingWidthOfRoadAxisMarking(2.0, 0.0, 388553075, properties), testUser.username, roadLink)

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
      asset.propertyData.find(p => p.publicId == "widthOfRoadAxisMarking_regulation_number").get.values.head.asInstanceOf[PropertyValue].propertyValue should be ("1")
      asset.createdBy should be(Some(testUser.username))
      asset.createdAt shouldBe defined
    }
  }

  test("Update Width Of RoadAxis Marking") {
    runWithRollback {
      val widthOfRoadAxisMarking = service.getById(600081).get
      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val updatedProperties = propertiesL2
      val updated = IncomingWidthOfRoadAxisMarking(widthOfRoadAxisMarking.lon, widthOfRoadAxisMarking.lat, widthOfRoadAxisMarking.linkId, updatedProperties)

      service.update(widthOfRoadAxisMarking.id, updated, roadLink, "unit_test")
      val updatedTrafficSign = service.getById(600081).get

      updatedTrafficSign.propertyData.find(p => p.publicId == "widthOfRoadAxisMarking_regulation_number").get.values.head.asInstanceOf[PropertyValue].propertyValue should be ("2")
      updatedTrafficSign.id should equal(updatedTrafficSign.id)
      updatedTrafficSign.modifiedBy should equal(Some("unit_test"))
      updatedTrafficSign.modifiedAt shouldBe defined
    }
  }

  test("Update Width Of RoadAxis Marking with geometry changes"){
    runWithRollback {
      val properties = propertiesL1
      val propertiesToUpdate = propertiesL2

      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(0.0, 20.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingWidthOfRoadAxisMarking(0.0, 20.0, 388553075, properties), "jakke", roadLink )
      val oldAsset = service.getPersistedAssetsByIds(Set(id)).head
      oldAsset.modifiedAt.isDefined should equal(false)

      val newId = service.update(id, IncomingWidthOfRoadAxisMarking(0.0, 10.0, 388553075, propertiesToUpdate), roadLink, "test")

      val updatedAsset = service.getPersistedAssetsByIds(Set(newId)).head
      updatedAsset.id should not be id
      updatedAsset.lon should equal (0.0)
      updatedAsset.lat should equal (10.0)
      updatedAsset.createdBy should equal (oldAsset.createdBy)
      updatedAsset.createdAt should equal (oldAsset.createdAt)
      updatedAsset.modifiedBy should equal (Some("test"))
      updatedAsset.modifiedAt.isDefined should equal(true)
      updatedAsset.propertyData.find(p => p.publicId == "widthOfRoadAxisMarking_regulation_number").get.values.head.asInstanceOf[PropertyValue].propertyValue should be ("2")
    }
  }

  test("Update Width Of RoadAxis Marking without geometry changes"){
    runWithRollback {
      val properties = propertiesL1

      val propertiesToUpdate = propertiesL2

      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(0.0, 20.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingWidthOfRoadAxisMarking(0.0, 20.0, 388553075, properties), "jakke", roadLink )
      val asset = service.getPersistedAssetsByIds(Set(id)).head

      val newId = service.update(id, IncomingWidthOfRoadAxisMarking(0.0, 20.0, 388553075, propertiesToUpdate), roadLink, "test")

      val updatedAsset = service.getPersistedAssetsByIds(Set(newId)).head
      updatedAsset.id should be (id)
      updatedAsset.lon should be (asset.lon)
      updatedAsset.lat should be (asset.lat)
      updatedAsset.createdBy should equal (Some("jakke"))
      updatedAsset.modifiedBy should equal (Some("test"))
      updatedAsset.propertyData.find(p => p.publicId == "widthOfRoadAxisMarking_regulation_number").get.values.head.asInstanceOf[PropertyValue].propertyValue should be ("2")
    }
  }

  test("Get Width Of RoadAxis Marking changes") {
    runWithRollback {
      when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(Set(388553075))).thenReturn(Seq(RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))))

      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val properties = propertiesL1
      val (id, id1, id2) = (service.create(IncomingWidthOfRoadAxisMarking(2.0, 0.0, 388553075, properties), "jakke", roadLink), service.create(IncomingWidthOfRoadAxisMarking(2.0, 0.0, 388553075, properties), "jakke_1", roadLink), service.create(IncomingWidthOfRoadAxisMarking(2.0, 0.0, 388553075, properties), "jakke_2", roadLink))

      val changes = service.getChanged(DateTime.now().minusDays(1), DateTime.now().plusDays(1))
      changes.length should be(3)

      service.expire(id)

      val changesAfterExpire = service.getChanged(DateTime.now().minusDays(1), DateTime.now().plusDays(1))
      changesAfterExpire.length should be(3)

    }
  }

  test("Get only one Width Of RoadAxis Marking change"){
    runWithRollback{
      val properties = propertiesL1
      val widthOfRoadAxisTypeId = WidthOfRoadAxisMarkings.typeId
      val lrmPositionsIds = Queries.fetchLrmPositionIds(11)

      sqlu"""insert into asset (id,asset_type_id,floating, created_date) VALUES (11,$widthOfRoadAxisTypeId,0, TO_DATE('17/12/2016', 'DD/MM/YYYY'))""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(10)}, 388553075, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (11,${lrmPositionsIds(10)})""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) VALUES (400011, 11, 300080, 1)""".execute
      Queries.updateAssetGeometry(11, Point(5, 0))

      when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(Set(388553075))).thenReturn(Seq(RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))))

      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      service.create(IncomingWidthOfRoadAxisMarking(2.0, 0.0, 388553075, properties), "jakke", roadLink)

      val changes = service.getChanged(DateTime.parse("2016-11-01T12:00Z"), DateTime.parse("2016-12-31T12:00Z"))
      changes.length should be(1)
    }
  }

  test("Width Of RoadAxis Marking get change does not return floating Width Of RoadAxis Marking"){
    runWithRollback {
      val lrmPositionsIds = Queries.fetchLrmPositionIds(11)

      sqlu"""insert into asset (id,asset_type_id,floating, created_date) VALUES (11,$widthOfRoadAxisMarkingTypeId, 1, TO_DATE('17/12/2016', 'DD/MM/YYYY'))""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(10)}, 388553075, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (11,${lrmPositionsIds(10)})""".execute
      sqlu"""insert into single_choice_value (asset_id, enumerated_value_id, property_id, modified_date, modified_by) values(11, 300153, 300144, timestamp '2016-12-17 19:01:13.000000', null)""".execute
      Queries.updateAssetGeometry(11, Point(5, 0))

      sqlu"""insert into asset (id,asset_type_id,floating) VALUES (2,$widthOfRoadAxisMarkingTypeId,1)""".execute
      sqlu"""insert into lrm_position (id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(1)}, 388553075, null, 0.000, 25.000)""".execute
      sqlu"""insert into asset_link (asset_id,position_id) VALUES (2,${lrmPositionsIds(1)})""".execute
      sqlu"""insert into single_choice_value (asset_id, enumerated_value_id, property_id, modified_date, modified_by) values(2, 300153, 300144, timestamp '2016-12-17 20:01:13.000000', null)""".execute
      Queries.updateAssetGeometry(2, Point(5, 0))

      when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq())

      val changes = service.getChanged(DateTime.now().minusDays(1), DateTime.now().plusDays(1))
      changes.length should be(0)
    }
  }
}

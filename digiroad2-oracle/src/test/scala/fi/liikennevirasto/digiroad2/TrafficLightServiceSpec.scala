package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.pointasset.oracle.TrafficLight
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.joda.time.DateTime
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.jdbc.StaticQuery.interpolation
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession

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
    when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(), Nil))

    runWithRollback {
      val result = service.getByBoundingBox(testUser, BoundingRectangle(Point(374101, 6677437), Point(374102, 6677438))).head
      result.id should equal(600070)
      result.linkId should equal(1611387)
      result.lon should equal(374101.60105163435)
      result.lat should equal(6677437.872017591)
      result.mValue should equal(16.592)
    }
  }

  test("Can fetch by bounding box with floating obstacle") {
    val modifiedLinkId = 1611387
    val newLinkId = 6002
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway

    val changeInfo = Seq(
      ChangeInfo(Some(modifiedLinkId), Some(modifiedLinkId), 12345, 5, Some(0), Some(20.0), Some(0), Some(15.0), 144000000),
      ChangeInfo(Some(modifiedLinkId), Some(newLinkId), 12346, 6, Some(0), Some(0), Some(15.0), Some(20.0), 144000000)
    )

    val roadLinks = Seq(
      RoadLink(modifiedLinkId, List(Point(3.0, 5.0), Point(8.0, 5.0)), 150.0, administrativeClass, functionalClass,
        trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(newLinkId, List(Point(8.0, 5.0), Point(20.0, 5.0)), 100.0, administrativeClass, functionalClass,
        trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    )

    when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((roadLinks, changeInfo))

    runWithRollback {
      val (assetId, oldLinkId, oldStartMeasure, oldFloatingStatus) =
        sql"""   select a.id, lp.link_id, lp.start_measure, a.floating
                  from asset a
                  join asset_link al on al.asset_id = a.id
                  join lrm_position lp on lp.id = al.position_id
                 where a.id = 600070""".as[(Long, Int, Double, Int)].first
      val result = service.getByBoundingBox(testUser, BoundingRectangle(Point(374101, 6677437), Point(374102, 6677438))).head
      result.id should equal(600070)
      result.linkId should equal(6002)
      result.lon should equal(374101.60105163435)
      result.lat should equal(6677437.872017591)
      result.mValue should equal(1.5919999999999987)
      result.floating should be(false)

      val (assetIdAfterCorretion, correctedLinkid, correctedStartMeasure, correctedFloatingStatus) =
        sql"""   select a.id, lp.link_id, lp.start_measure, a.floating
                  from asset a
                  join asset_link al on al.asset_id = a.id
                  join lrm_position lp on lp.id = al.position_id
                 where a.id = 600070""".as[(Long, Int, Double, Int)].first

      val roundedMvalue = BigDecimal(result.mValue).setScale(3, BigDecimal.RoundingMode.HALF_UP).toDouble
      assetIdAfterCorretion should equal(result.id)
      correctedLinkid should equal(result.linkId)
      correctedStartMeasure should equal(roundedMvalue)
      correctedFloatingStatus should equal(0)

      assetIdAfterCorretion should be (assetId)
      correctedLinkid should not be (oldLinkId)
      correctedStartMeasure should not be(oldStartMeasure)
    }
  }

  test("Can fetch by municipality") {
    when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(235)).thenReturn((Seq(
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

  test("Can fetch by municipality with floating traffic light asset") {
    val modifiedLinkId = 1611387
    val newLinkId = 6002
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway

    val changeInfo = Seq(
      ChangeInfo(Some(modifiedLinkId), Some(modifiedLinkId), 12345, 5, Some(0), Some(20.0), Some(0), Some(15.0), 144000000),
      ChangeInfo(Some(modifiedLinkId), Some(newLinkId), 12346, 6, Some(0), Some(0), Some(15.0), Some(20.0), 144000000)
    )

    val roadLinks = Seq(
      RoadLink(modifiedLinkId, List(Point(3.0, 5.0), Point(8.0, 5.0)), 150.0, administrativeClass, functionalClass,
        trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(newLinkId, List(Point(8.0, 5.0), Point(20.0, 5.0)), 100.0, administrativeClass, functionalClass,
        trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    )

    when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(235)).thenReturn((roadLinks, changeInfo))

    runWithRollback {
      val (assetId, oldLinkId, oldStartMeasure, oldFloatingStatus) =
        sql"""   select a.id, lp.link_id, lp.start_measure, a.floating
                  from asset a
                  join asset_link al on al.asset_id = a.id
                  join lrm_position lp on lp.id = al.position_id
                 where a.id = 600070""".as[(Long, Int, Double, Int)].first
      val result = service.getByMunicipality(235).find(_.id == 600070).get

      result.id should equal(600070)
      result.linkId should equal(6002)
      result.lon should equal(374101.60105163435)
      result.lat should equal(6677437.872017591)
      result.mValue should equal(1.5919999999999987)
      result.floating should be(false)

      val (assetIdAfterCorretion, correctedLinkid, correctedStartMeasure, correctedFloatingStatus) =
        sql"""   select a.id, lp.link_id, lp.start_measure, a.floating
                  from asset a
                  join asset_link al on al.asset_id = a.id
                  join lrm_position lp on lp.id = al.position_id
                 where a.id = 600070""".as[(Long, Int, Double, Int)].first

      val roundedMvalue = BigDecimal(result.mValue).setScale(3, BigDecimal.RoundingMode.HALF_UP).toDouble
      assetIdAfterCorretion should equal(result.id)
      correctedLinkid should equal(result.linkId)
      correctedStartMeasure should equal(roundedMvalue)
      correctedFloatingStatus should equal(0)

      assetIdAfterCorretion should be (assetId)
      correctedLinkid should not be (oldLinkId)
      correctedStartMeasure should not be(oldStartMeasure)
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
      val id = service.create(IncomingTrafficLight(2, 0.0, 388553075), "jakke", Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 235)
      val assets = service.getPersistedAssetsByIds(Set(id))

      assets.size should be(1)

      val asset = assets.head

      asset should be(TrafficLight(
        id = id,
        linkId = 388553075,
        lon = 2,
        lat = 0,
        mValue = 2,
        floating = false,
        municipalityCode = 235,
        createdBy = Some("jakke"),
        createdAt = asset.createdAt
      ))
    }
  }

  test("Update traffic light") {
    val linkGeometry = Seq(Point(0.0, 0.0), Point(200.0, 0.0))
    when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(235)).thenReturn((Seq(
      VVHRoadlink(1611387, 235, linkGeometry, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink), Nil))
    when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(91)).thenReturn((Seq(
      VVHRoadlink(123, 91, linkGeometry, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink), Nil))

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

      service.update(id = 600070, IncomingTrafficLight(100, 0, 123), linkGeometry, 91, "test")

      val afterUpdate = service.getByMunicipality(91).find(_.id == 600070).get
      afterUpdate.id should equal(600070)
      afterUpdate.lon should equal(100)
      afterUpdate.lat should equal(0)
      afterUpdate.mValue should equal(100)
      afterUpdate.linkId should equal(123)
      afterUpdate.municipalityCode should equal(91)
      afterUpdate.createdBy should equal(Some("dr2_test_data"))
      afterUpdate.createdAt should equal(beforeUpdate.createdAt)
      afterUpdate.modifiedBy should equal(Some("test"))
      afterUpdate.modifiedAt.isDefined should equal(true)
    }
  }
}


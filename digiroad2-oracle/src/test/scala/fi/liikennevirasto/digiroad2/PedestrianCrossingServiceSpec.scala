package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.pointasset.oracle.PedestrianCrossing
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

class PedestrianCrossingServiceSpec extends FunSuite with Matchers {
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

  val service = new PedestrianCrossingService(mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = f

    override def withDynSession[T](f: => T): T = f
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(service.dataSource)(test)

  test("Can fetch by bounding box") {
    when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(), Nil))

    runWithRollback {
      val result = service.getByBoundingBox(testUser, BoundingRectangle(Point(374466.5, 6677346.5), Point(374467.5, 6677347.5))).head
      result.id should equal(600029)
      result.linkId should equal(1611317)
      result.lon should equal(374467)
      result.lat should equal(6677347)
      result.mValue should equal(103)
    }
  }

  test("Can fetch by bounding box with floating corrected") {

    val modifiedLinkId = 1611317
    val newLinkId = 6002
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway


    val changeInfo = Seq(
      ChangeInfo(Some(modifiedLinkId), Some(modifiedLinkId), 12345, 5, Some(0), Some(150.0), Some(0), Some(100.0), 144000000),
      ChangeInfo(Some(modifiedLinkId), Some(newLinkId), 12346, 6, Some(0), Some(0), Some(100.0), Some(150.0), 144000000)
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
                 where a.id = 600029""".as[(Long, Int, Double, Int)].first
      val result = service.getByBoundingBox(testUser, BoundingRectangle(Point(374466.5, 6677346.5), Point(374467.5, 6677347.5))).head
      result.id should equal(600029)
      result.linkId should equal(6002)
      result.lon should equal(374467)
      result.lat should equal(6677347)
      result.mValue should equal(3)
      result.floating should be(false)

      val (assetIdAfterCorretion, correctedLinkid, correctedStartMeasure, correctedFloatingStatus) =
        sql"""   select a.id, lp.link_id, lp.start_measure, a.floating
                  from asset a
                  join asset_link al on al.asset_id = a.id
                  join lrm_position lp on lp.id = al.position_id
                 where a.id = 600029""".as[(Long, Int, Double, Int)].first

      assetIdAfterCorretion should equal(result.id)
      correctedLinkid should equal(result.linkId)
      correctedStartMeasure should equal(result.mValue)
      correctedFloatingStatus should equal(0)

      assetIdAfterCorretion should be (assetId)
      correctedLinkid should not be (oldLinkId)
      correctedStartMeasure should not be(oldStartMeasure)
    }
  }


  test("Can fetch by municipality") {
    when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(235)).thenReturn((Seq(
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

  test("Can fetch by municipality with floating corrected") {
    val modifiedLinkId = 1611317
    val newLinkId = 6002
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway


    val changeInfo = Seq(
      ChangeInfo(Some(modifiedLinkId), Some(modifiedLinkId), 12345, 5, Some(0), Some(150.0), Some(0), Some(100.0), 144000000),
      ChangeInfo(Some(modifiedLinkId), Some(newLinkId), 12346, 6, Some(0), Some(0), Some(100.0), Some(150.0), 144000000)
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
                 where a.id = 600029""".as[(Long, Int, Double, Int)].first

      val result = service.getByMunicipality(235).find(_.id == 600029).get

      result.id should equal(600029)
      result.linkId should equal(6002)
      result.lon should equal(374467)
      result.lat should equal(6677347)
      result.mValue should equal(3)
      result.floating should be(false)

      val (assetIdAfterCorretion, correctedLinkid, correctedStartMeasure, correctedFloatingStatus) =
        sql"""   select a.id, lp.link_id, lp.start_measure, a.floating
                  from asset a
                  join asset_link al on al.asset_id = a.id
                  join lrm_position lp on lp.id = al.position_id
                 where a.id = 600029""".as[(Long, Int, Double, Int)].first

      assetIdAfterCorretion should equal(result.id)
      correctedLinkid should equal(result.linkId)
      correctedStartMeasure should equal(result.mValue)
      correctedFloatingStatus should equal(0)

      assetIdAfterCorretion should be (assetId)
      correctedLinkid should not be (oldLinkId)
      correctedStartMeasure should not be(oldStartMeasure)
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
      val now = DateTime.now()
      val id = service.create(IncomingPedestrianCrossing(2, 0.0, 388553075), "jakke", Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 235)
      val assets = service.getPersistedAssetsByIds(Set(id))

      assets.size should be(1)

      val asset = assets.head

      asset should be(PedestrianCrossing(
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
}

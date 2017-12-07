package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.pointasset.oracle.{OraclePedestrianCrossingDao, PedestrianCrossing, PedestrianCrossingToBePersisted}
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
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, VVHRoadlink}

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
      OraclePedestrianCrossingDao.update(600029, PedestrianCrossingToBePersisted(1611317, 374406.8,
        6677308.2, 31.550, 235, "Hannu"), linkSource = NormalLinkInterface)
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
      val now = DateTime.now()
      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingPedestrianCrossing(2, 0.0, 388553075), "jakke", roadLink )
      val assets = service.getPersistedAssetsByIds(Set(id))

      assets.size should be(1)

      val asset = assets.head

      asset.vvhTimeStamp should not be(0)

      asset should be(PedestrianCrossing(
        id = id,
        linkId = 388553075,
        lon = 2,
        lat = 0,
        mValue = 2,
        floating = false,
        vvhTimeStamp = asset.vvhTimeStamp,
        municipalityCode = 235,
        createdBy = Some("jakke"),
        createdAt = asset.createdAt,
        linkSource = NormalLinkInterface
      ))
    }
  }
}

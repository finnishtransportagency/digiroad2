package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.{Private, BoundingRectangle, Municipality, TrafficDirection}
import fi.liikennevirasto.digiroad2.pointasset.oracle.Obstacle
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class ObstacleServiceSpec extends FunSuite with Matchers {
  val testUser = User(
    id = 1,
    username = "Hannu",
    configuration = Configuration(authorizedMunicipalities = Set(235)))
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  when(mockVVHClient.fetchVVHRoadlinks(any[BoundingRectangle], any[Set[Int]])).thenReturn(Seq(
    VVHRoadlink(1611317, 235, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Municipality,
      TrafficDirection.BothDirections, FeatureClass.AllOthers)))
  when(mockVVHClient.fetchVVHRoadlink(1611317)).thenReturn(Seq(
    VVHRoadlink(1611317, 235, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Municipality,
      TrafficDirection.BothDirections, FeatureClass.AllOthers)).headOption)

  when(mockVVHClient.fetchVVHRoadlink(1191950690)).thenReturn(Seq(
    VVHRoadlink(1191950690, 235, Seq(Point(373500.349, 6677657.152), Point(373494.182, 6677669.918)), Private,
      TrafficDirection.BothDirections, FeatureClass.AllOthers)).headOption)

  val service = new ObstacleService(mockVVHClient) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(service.dataSource)(test)

  test("Can fetch by bounding box") {
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
    when(mockVVHClient.fetchByMunicipality(235)).thenReturn(Seq(
      VVHRoadlink(388553074, 235, Seq(Point(0.0, 0.0), Point(200.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)))

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
    when(mockVVHClient.fetchByMunicipality(235)).thenReturn(Seq(
      VVHRoadlink(388553074, 235, Seq(Point(0.0, 0.0), Point(200.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)))

    runWithRollback {
      val result = service.getByMunicipality(235).find(_.id == 600046).get
      result.id should equal(600046)

      service.expire(600046, "unit_test")

      service.getByMunicipality(235).find(_.id == 600046) should equal(None)
    }
  }

  test("Create new obstacle") {
    runWithRollback {
      val id = service.create(IncomingObstacle(2.0, 0.0, 388553075, 2), "jakke", Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 235)

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

      service.update(obstacle.id, updated, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 235, "unit_test")
      val updatedObstacle = service.getById(600046).get

      updatedObstacle.obstacleType should equal(2)
      updatedObstacle.id should equal(obstacle.id)
      updatedObstacle.modifiedBy should equal(Some("unit_test"))
      updatedObstacle.modifiedAt shouldBe defined
    }
  }

  test("Asset can be outside link within treshold") {
    runWithRollback {
      val id = service.create(IncomingObstacle(373494.183, 6677669.927, 1191950690, 2), "unit_test", Seq(Point(373500.349, 6677657.152), Point(373494.182, 6677669.918)), 235)

      val asset = service.getById(id).get

      asset.floating should be(false)
    }
  }

  test("should not float") {
    val testUser = User(
      id = 1,
      username = "Hannu",
      configuration = Configuration(authorizedMunicipalities = Set(235)))
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val roadLink = VVHRoadlink(5797521, 853, Seq(Point(240863.911, 6700590.15),
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
      Point(240885.898, 6700689.602)), Municipality,
      TrafficDirection.BothDirections, FeatureClass.AllOthers)
    when(mockVVHClient.fetchVVHRoadlinks(any[BoundingRectangle], any[Set[Int]])).thenReturn(Seq(
      roadLink))
    when(mockVVHClient.fetchVVHRoadlink(5797521)).thenReturn(Seq(
      roadLink).headOption)

    val service = new ObstacleService(mockVVHClient) {
      override def withDynTransaction[T](f: => T): T = f
      override def withDynSession[T](f: => T): T = f
    }

    runWithRollback {
      val id = service.create(IncomingObstacle(240877.69416595, 6700681.8198731, 5797521, 2), "unit_test", roadLink.geometry, 853)

      val asset = service.getById(id).get

      asset.floating should be(false)
    }

  }
}

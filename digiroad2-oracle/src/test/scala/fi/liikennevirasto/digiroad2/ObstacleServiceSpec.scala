package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, Municipality, TrafficDirection}
import fi.liikennevirasto.digiroad2.pointasset.oracle.{PedestrianCrossing, Obstacle}
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.joda.time.DateTime
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
    VVHRoadlink(388553074, 235, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Municipality,
      TrafficDirection.BothDirections, FeatureClass.AllOthers)))
  when(mockVVHClient.fetchVVHRoadlink(388553074)).thenReturn(Seq(
    VVHRoadlink(388553074, 235, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Municipality,
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
      result.mmlId should equal(388553074)
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
      result.mmlId should equal(388553074)
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

      asset should be(
        Obstacle(
          id = id,
          mmlId = 388553075,
          lon = 2,
          lat = 0,
          mValue = 2,
          floating = false,
          municipalityCode = 235,
          obstacleType = 2,
          createdBy = Some("jakke"),
          createdDateTime = asset.createdDateTime))
    }
  }

  test("Update obstacle") {
    runWithRollback {
      val obstacle = service.getById(600046).get
      val updated = IncomingObstacle(obstacle.lon, obstacle.lat, obstacle.mmlId, obstacle.obstacleType)

      service.update(obstacle.id, IncomingObstacle(updated.lon, updated.lat, updated.obstacleType, updated.obstacleType), Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 235, "unit_test")
      val updatedObstacle = service.getById(600046).get

      updatedObstacle.obstacleType should equal(2)
      updatedObstacle.id should equal(obstacle.id)
      updatedObstacle.modifiedBy should equal(Some("unit_test"))
    }
  }

  test("Asset can be outside link within treshold") {
    runWithRollback {
      val id = service.create(NewObstacle(373494.183, 6677669.927, 1191950690, 2), "unit_test", Seq(Point(373500.349, 6677657.152), Point(373494.182, 6677669.918)), 235)

      val asset = service.getById(id).get

      asset.floating should be(false)
    }
  }
}

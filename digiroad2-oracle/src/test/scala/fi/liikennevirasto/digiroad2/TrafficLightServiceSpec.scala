package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.{TrafficDirection, Municipality, BoundingRectangle}
import fi.liikennevirasto.digiroad2.pointasset.oracle.TrafficLight
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.joda.time.DateTime
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, FunSuite}

class TrafficLightServiceSpec  extends FunSuite with Matchers {
  val testUser = User(
    id = 1,
    username = "Hannu",
    configuration = Configuration(authorizedMunicipalities = Set(235)))
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  when(mockVVHClient.fetchVVHRoadlinks(any[BoundingRectangle], any[Set[Int]])).thenReturn(Seq(
    VVHRoadlink(388553074, 235, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Municipality,
      TrafficDirection.BothDirections, FeatureClass.AllOthers)))

  val service = new TrafficLightService(mockVVHClient) {
    override def withDynTransaction[T](f: => T): T = f

    override def withDynSession[T](f: => T): T = f
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(service.dataSource)(test)

  test("Can fetch by bounding box") {
    runWithRollback {
      val result = service.getByBoundingBox(testUser, BoundingRectangle(Point(374101, 6677437), Point(374102, 6677438))).head
      result.id should equal(600070)
      result.mmlId should equal(388553548)
      result.lon should equal(374101.60105163435)
      result.lat should equal(6677437.872017591)
      result.mValue should equal(16.592)
    }
  }

  test("Can fetch by municipality") {
    when(mockVVHClient.fetchByMunicipality(235)).thenReturn(Seq(
      VVHRoadlink(388553548, 235, Seq(Point(0.0, 0.0), Point(200.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)))

    runWithRollback {
      val result = service.getByMunicipality(235).find(_.id == 600070).get

      result.id should equal(600070)
      result.mmlId should equal(388553548)
      result.lon should equal(374101.60105163435)
      result.lat should equal(6677437.872017591)
      result.mValue should equal(16.592)
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
        mmlId = 388553075,
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
    when(mockVVHClient.fetchByMunicipality(235)).thenReturn(Seq(
      VVHRoadlink(388553548, 235, linkGeometry, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)))
    when(mockVVHClient.fetchByMunicipality(91)).thenReturn(Seq(
      VVHRoadlink(123, 91, linkGeometry, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)))

    runWithRollback {
      val beforeUpdate = service.getByMunicipality(235).find(_.id == 600070).get
      beforeUpdate.id should equal(600070)
      beforeUpdate.lon should equal(374101.60105163435)
      beforeUpdate.lat should equal(6677437.872017591)
      beforeUpdate.mValue should equal(16.592)
      beforeUpdate.mmlId should equal(388553548)
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
      afterUpdate.mmlId should equal(123)
      afterUpdate.municipalityCode should equal(91)
      afterUpdate.createdBy should equal(Some("dr2_test_data"))
      afterUpdate.createdAt should equal(beforeUpdate.createdAt)
      afterUpdate.modifiedBy should equal(Some("test"))
      afterUpdate.modifiedAt.isDefined should equal(true)
    }
  }
}


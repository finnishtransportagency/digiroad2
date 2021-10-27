package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, VVHRoadlink}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util.TestTransactions
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class WeightLimitServiceSpec extends FunSuite with Matchers {
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

  val weightLimitService = new TotalWeightLimitService(mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }
  val axleWeightLimitService = new AxleWeightLimitService(mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }
  val bogieWeightLimitService = new BogieWeightLimitService(mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }
  val trailerTruckWeightLimitService = new TrailerTruckWeightLimitService(mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }

  def runWithRollbackWeightLimit(test: => Unit): Unit = TestTransactions.runWithRollback(PostGISDatabase.ds)(test)
  def runWithRollbackAxleWeightLimit(test: => Unit): Unit = TestTransactions.runWithRollback(PostGISDatabase.ds)(test)
  def runWithRollbackBogieWeightLimit(test: => Unit): Unit = TestTransactions.runWithRollback(PostGISDatabase.ds)(test)
  def runWithRollbackTrailerTruckWeightLimit(test: => Unit): Unit = TestTransactions.runWithRollback(PostGISDatabase.ds)(test)

  test("Can fetch by bounding box WeightLimit Asset") {
    when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(List())

    runWithRollbackWeightLimit {
      val result = weightLimitService.getByBoundingBox(testUser, BoundingRectangle(Point(374101, 6677437), Point(374102, 6677438))).head
      result.id should equal(600076)
      result.linkId should equal(1611387)
      result.lon should equal(374101.60105163435)
      result.lat should equal(6677437.872017591)
      result.mValue should equal(16.592)
    }
  }

  test("Can fetch by municipality WeightLimit Asset") {
    when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(235)).thenReturn(Seq(
      VVHRoadlink(1611387, 235, Seq(Point(0.0, 0.0), Point(200.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink))

    runWithRollbackWeightLimit {
      val result = weightLimitService.getByMunicipality(235).find(_.id == 600076).get

      result.id should equal(600076)
      result.linkId should equal(1611387)
      result.lon should equal(374101.60105163435)
      result.lat should equal(6677437.872017591)
      result.mValue should equal(16.592)
    }
  }

  test("Expire Weight Limit") {
    runWithRollbackWeightLimit {
      weightLimitService.getPersistedAssetsByIds(Set(600076)).length should be(1)
    }
    an[UnsupportedOperationException] should be thrownBy weightLimitService.expire(600076, testUser.username)
  }

  test("Create new WeightLimit Asset") {
    val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    an[UnsupportedOperationException] should be thrownBy weightLimitService.create(IncomingWeightLimit(2, 0.0, 388553075, 10, 0, Some(0)), "test", roadLink)
  }

  test("Update Weight Limit") {
    val linkGeometry = Seq(Point(0.0, 0.0), Point(200.0, 0.0))

    when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(235)).thenReturn(Seq(
      VVHRoadlink(1611387, 235, linkGeometry, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink))

    when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(91)).thenReturn(Seq(
      VVHRoadlink(123, 91, linkGeometry, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink))

    runWithRollbackWeightLimit {
      val beforeUpdate = weightLimitService.getByMunicipality(235).find(_.id == 600076).get
      beforeUpdate.id should equal(600076)
      beforeUpdate.lon should equal(374101.60105163435)
      beforeUpdate.lat should equal(6677437.872017591)
      beforeUpdate.mValue should equal(16.592)
      beforeUpdate.linkId should equal(1611387)
      beforeUpdate.municipalityCode should equal(235)
      beforeUpdate.createdBy should equal(Some("dr2_test_data"))
      beforeUpdate.createdAt.isDefined should equal(true)
      beforeUpdate.modifiedBy should equal(None)
      beforeUpdate.modifiedAt should equal(None)

      val roadLink = RoadLink(123, linkGeometry, 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)))
      an[UnsupportedOperationException] should be thrownBy weightLimitService.update(id = 600076, IncomingWeightLimit(100, 0, 123, 20, 0, Some(0)), roadLink, "test")
    }
  }


  test("Can fetch by bounding box AxleWeightLimit Asset") {
    when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(List())

    runWithRollbackAxleWeightLimit {
      val result = axleWeightLimitService.getByBoundingBox(testUser, BoundingRectangle(Point(374101, 6677437), Point(374102, 6677438))).head
      result.id should equal(600077)
      result.linkId should equal(1611387)
      result.lon should equal(374101.60105163435)
      result.lat should equal(6677437.872017591)
      result.mValue should equal(16.592)
    }
  }

  test("Can fetch by municipality AxleWeightLimit Asset") {
    when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(235)).thenReturn(Seq(
      VVHRoadlink(1611387, 235, Seq(Point(0.0, 0.0), Point(200.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink))

    runWithRollbackAxleWeightLimit {
      val result = axleWeightLimitService.getByMunicipality(235).find(_.id == 600077).get

      result.id should equal(600077)
      result.linkId should equal(1611387)
      result.lon should equal(374101.60105163435)
      result.lat should equal(6677437.872017591)
      result.mValue should equal(16.592)
    }
  }

  test("Expire AxleWeightLimit") {
    runWithRollbackAxleWeightLimit {
      axleWeightLimitService.getPersistedAssetsByIds(Set(600077)).length should be(1)
    }
    an[UnsupportedOperationException] should be thrownBy axleWeightLimitService.expire(600077, testUser.username)
  }

  test("Create new AxleWeightLimit Asset") {
    val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    an[UnsupportedOperationException] should be thrownBy axleWeightLimitService.create(IncomingAxleWeightLimit(2, 0.0, 388553075, 10, 0, Some(0)), "test", roadLink)
  }

  test("Update AxleWeight Limit") {
    val linkGeometry = Seq(Point(0.0, 0.0), Point(200.0, 0.0))

    when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(235)).thenReturn(Seq(
      VVHRoadlink(1611387, 235, linkGeometry, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink))

    when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(91)).thenReturn(Seq(
      VVHRoadlink(123, 91, linkGeometry, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink))

    runWithRollbackAxleWeightLimit {
      val beforeUpdate = axleWeightLimitService.getByMunicipality(235).find(_.id == 600077).get
      beforeUpdate.id should equal(600077)
      beforeUpdate.lon should equal(374101.60105163435)
      beforeUpdate.lat should equal(6677437.872017591)
      beforeUpdate.mValue should equal(16.592)
      beforeUpdate.linkId should equal(1611387)
      beforeUpdate.municipalityCode should equal(235)
      beforeUpdate.createdBy should equal(Some("dr2_test_data"))
      beforeUpdate.createdAt.isDefined should equal(true)
      beforeUpdate.modifiedBy should equal(None)
      beforeUpdate.modifiedAt should equal(None)

      val roadLink = RoadLink(123, linkGeometry, 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)))
      an[UnsupportedOperationException] should be thrownBy axleWeightLimitService.update(id = 600077, IncomingAxleWeightLimit(100, 0, 123, 20, 0, Some(0)), roadLink, "test")
    }
  }

  test("Can fetch by bounding box BogieWeightLimit Asset") {
    when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(List())

    runWithRollbackBogieWeightLimit {
      val result = bogieWeightLimitService.getByBoundingBox(testUser, BoundingRectangle(Point(374101, 6677437), Point(374102, 6677438))).head
      result.id should equal(600078)
      result.linkId should equal(1611387)
      result.lon should equal(374101.60105163435)
      result.lat should equal(6677437.872017591)
      result.mValue should equal(16.592)
    }
  }

  test("Can fetch by municipality BogieWeightLimit Asset") {
    when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(235)).thenReturn(Seq(
      VVHRoadlink(1611387, 235, Seq(Point(0.0, 0.0), Point(200.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink))

    runWithRollbackBogieWeightLimit {
      val result = bogieWeightLimitService.getByMunicipality(235).find(_.id == 600078).get

      result.id should equal(600078)
      result.linkId should equal(1611387)
      result.lon should equal(374101.60105163435)
      result.lat should equal(6677437.872017591)
      result.mValue should equal(16.592)
    }
  }

  test("Expire BogieWeightLimit") {
    runWithRollbackBogieWeightLimit {
      bogieWeightLimitService.getPersistedAssetsByIds(Set(600078)).length should be(1)
    }
    an[UnsupportedOperationException] should be thrownBy bogieWeightLimitService.expire(600078, testUser.username)
  }

  test("Create new BogieWeightLimit Asset") {
    val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    an[UnsupportedOperationException] should be thrownBy bogieWeightLimitService.create(IncomingBogieWeightLimit(2, 0.0, 388553075, 10, 0, Some(0)), "test", roadLink)
  }

  test("Update BogieWeight Limit") {
    val linkGeometry = Seq(Point(0.0, 0.0), Point(200.0, 0.0))

    when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(235)).thenReturn(Seq(
      VVHRoadlink(1611387, 235, linkGeometry, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink))

    when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(91)).thenReturn(Seq(
      VVHRoadlink(123, 91, linkGeometry, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink))

    runWithRollbackBogieWeightLimit {
      val beforeUpdate = bogieWeightLimitService.getByMunicipality(235).find(_.id == 600078).get
      beforeUpdate.id should equal(600078)
      beforeUpdate.lon should equal(374101.60105163435)
      beforeUpdate.lat should equal(6677437.872017591)
      beforeUpdate.mValue should equal(16.592)
      beforeUpdate.linkId should equal(1611387)
      beforeUpdate.municipalityCode should equal(235)
      beforeUpdate.createdBy should equal(Some("dr2_test_data"))
      beforeUpdate.createdAt.isDefined should equal(true)
      beforeUpdate.modifiedBy should equal(None)
      beforeUpdate.modifiedAt should equal(None)

      val roadLink = RoadLink(123, linkGeometry, 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)))
      an[UnsupportedOperationException] should be thrownBy bogieWeightLimitService.update(id = 600078, IncomingBogieWeightLimit(100, 0, 123, 20, 0, Some(0)), roadLink, "test")
    }
  }

  test("Can fetch by bounding box TrailerTruckWeightLimit Asset") {
    when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(List())

    runWithRollbackTrailerTruckWeightLimit {
      val result = trailerTruckWeightLimitService.getByBoundingBox(testUser, BoundingRectangle(Point(374101, 6677437), Point(374102, 6677438))).head
      result.id should equal(600079)
      result.linkId should equal(1611387)
      result.lon should equal(374101.60105163435)
      result.lat should equal(6677437.872017591)
      result.mValue should equal(16.592)
    }
  }

  test("Can fetch by municipality TrailerTruckWeightLimit Asset") {
    when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(235)).thenReturn(Seq(
      VVHRoadlink(1611387, 235, Seq(Point(0.0, 0.0), Point(200.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink))

    runWithRollbackTrailerTruckWeightLimit {
      val result = trailerTruckWeightLimitService.getByMunicipality(235).find(_.id == 600079).get

      result.id should equal(600079)
      result.linkId should equal(1611387)
      result.lon should equal(374101.60105163435)
      result.lat should equal(6677437.872017591)
      result.mValue should equal(16.592)
    }
  }

  test("Expire TrailerTruckWeightLimit") {
    runWithRollbackTrailerTruckWeightLimit {
      trailerTruckWeightLimitService.getPersistedAssetsByIds(Set(600079)).length should be(1)
    }
    an[UnsupportedOperationException] should be thrownBy trailerTruckWeightLimitService.expire(600079, testUser.username)
  }

  test("Create new TrailerTruckWeightLimit Asset") {
    val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    an[UnsupportedOperationException] should be thrownBy trailerTruckWeightLimitService.create(IncomingTrailerTruckWeightLimit(2, 0.0, 388553075, 10, 0, Some(0)), "test", roadLink)
  }

  test("Update TrailerTruckWeight Limit") {
    val linkGeometry = Seq(Point(0.0, 0.0), Point(200.0, 0.0))

    when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(235)).thenReturn(Seq(
      VVHRoadlink(1611387, 235, linkGeometry, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink))

    when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(91)).thenReturn(Seq(
      VVHRoadlink(123, 91, linkGeometry, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink))

    runWithRollbackTrailerTruckWeightLimit {
      val beforeUpdate = trailerTruckWeightLimitService.getByMunicipality(235).find(_.id == 600079).get
      beforeUpdate.id should equal(600079)
      beforeUpdate.lon should equal(374101.60105163435)
      beforeUpdate.lat should equal(6677437.872017591)
      beforeUpdate.mValue should equal(16.592)
      beforeUpdate.linkId should equal(1611387)
      beforeUpdate.municipalityCode should equal(235)
      beforeUpdate.createdBy should equal(Some("dr2_test_data"))
      beforeUpdate.createdAt.isDefined should equal(true)
      beforeUpdate.modifiedBy should equal(None)
      beforeUpdate.modifiedAt should equal(None)

      val roadLink = RoadLink(123, linkGeometry, 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)))
      an[UnsupportedOperationException] should be thrownBy trailerTruckWeightLimitService.update(id = 600079, IncomingTrailerTruckWeightLimit(100, 0, 123, 20, 0, Some(0)), roadLink, "test")
    }
  }
}

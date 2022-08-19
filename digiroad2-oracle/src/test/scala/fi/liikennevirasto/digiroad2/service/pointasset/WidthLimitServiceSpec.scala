package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{FeatureClass, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util.{LinkIdGenerator, TestTransactions}
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class WidthLimitServiceSpec extends FunSuite with Matchers {
  def toRoadLink(l: RoadLinkFetched) = {
    RoadLink(l.linkId, l.geometry, GeometryUtils.geometryLength(l.geometry),
      l.administrativeClass, 1, l.trafficDirection, UnknownLinkType, None, None, l.attributes + ("MUNICIPALITYCODE" -> BigInt(l.municipalityCode)))
  }
  val testUser = User(
    id = 1,
    username = "Hannu",
    configuration = Configuration(authorizedMunicipalities = Set(235)))

  val linkId1 = "52d58ce5-39e8-4ab4-8c43-d347a9945ab5:1"
  val linkId2 = "a2c8e119-5739-456a-aa6c-cba0f300cc3c:1"

  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  when(mockRoadLinkService.getRoadLinksFromVVH(any[BoundingRectangle], any[Set[Int]],any[Boolean])).thenReturn(Seq(
    RoadLinkFetched(linkId1, 235, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Municipality,
      TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink))

  val service = new WidthLimitService(mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(PostGISDatabase.ds)(test)

  test("Can fetch by bounding box") {
    when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean],any[Boolean])).thenReturn(List())

    runWithRollback {
      val result = service.getByBoundingBox(testUser, BoundingRectangle(Point(374101, 6677437), Point(374102, 6677438))).head
      result.id should equal(600080)
      result.linkId should equal(linkId2)
      result.lon should equal(374101.60105163435)
      result.lat should equal(6677437.872017591)
      result.mValue should equal(16.592)
      result.reason should equal(WidthLimitReason.HalfPortal)
    }
  }

  test("Can fetch by municipality") {
    val linkId = linkId2
    when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(235)).thenReturn(Seq(
      RoadLinkFetched(linkId, 235, Seq(Point(0.0, 0.0), Point(200.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink))

    runWithRollback {
      val result = service.getByMunicipality(235).find(_.id == 600080).get

      result.id should equal(600080)
      result.linkId should equal(linkId)
      result.lon should equal(374101.60105163435)
      result.lat should equal(6677437.872017591)
      result.mValue should equal(16.592)
      result.reason should equal(WidthLimitReason.HalfPortal)
    }
  }

  test("Expire With Limit") {
    runWithRollback {
      service.getPersistedAssetsByIds(Set(600080)).length should be(1)
    }
    an[UnsupportedOperationException] should be thrownBy service.expire(600080, testUser.username)
  }

  test("Create new") {
    val linkId = LinkIdGenerator.generateRandom()
    val roadLink = RoadLink(linkId, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    an[UnsupportedOperationException] should be thrownBy service.create(IncomingWidthLimit(2, 0.0, linkId, 10, WidthLimitReason.Fence, 0, Some(0)), "test", roadLink)
  }

  test("Update With Limit") {
    val linkGeometry = Seq(Point(0.0, 0.0), Point(200.0, 0.0))
    val linkId = linkId2
    val randomLinkId = LinkIdGenerator.generateRandom()

    when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(235)).thenReturn(Seq(
      RoadLinkFetched(linkId, 235, linkGeometry, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink))

    when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(91)).thenReturn(Seq(
      RoadLinkFetched(randomLinkId, 91, linkGeometry, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)).map(toRoadLink))

    runWithRollback {
      val beforeUpdate = service.getByMunicipality(235).find(_.id == 600080).get
      beforeUpdate.id should equal(600080)
      beforeUpdate.lon should equal(374101.60105163435)
      beforeUpdate.lat should equal(6677437.872017591)
      beforeUpdate.mValue should equal(16.592)
      beforeUpdate.linkId should equal(linkId)
      beforeUpdate.municipalityCode should equal(235)
      beforeUpdate.createdBy should equal(Some("dr2_test_data"))
      beforeUpdate.createdAt.isDefined should equal(true)
      beforeUpdate.modifiedBy should equal(None)
      beforeUpdate.modifiedAt should equal(None)
      beforeUpdate.reason should equal(WidthLimitReason.HalfPortal)

      val roadLink = RoadLink(randomLinkId, linkGeometry, 10, Municipality, 1, TrafficDirection.AgainstDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)))
      an[UnsupportedOperationException] should be thrownBy service.update(id = 600080, IncomingWidthLimit(100, 0, randomLinkId, 20, WidthLimitReason.Abutment, 0, Some(0)), roadLink, "test")
    }
  }
}

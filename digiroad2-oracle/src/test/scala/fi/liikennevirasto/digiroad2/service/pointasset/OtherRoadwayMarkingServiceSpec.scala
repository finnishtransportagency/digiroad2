package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, VVHRoadlink}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}


class otherRoadwayMarkingServiceSpec extends FunSuite with Matchers {

  def toRoadLink(l: VVHRoadlink) = {
    RoadLink(l.linkId, l.geometry, GeometryUtils.geometryLength(l.geometry),
      l.administrativeClass, 1, l.trafficDirection, UnknownLinkType, None, None, l.attributes + ("MUNICIPALITYCODE" -> BigInt(l.municipalityCode)))
  }

  val testUser = User(
    id = 1,
    username = "Hannu",
    configuration = Configuration(authorizedMunicipalities = Set(235)))
  val otherRoadwayMarkingTypeId = OtherRoadwayMarkings.typeId
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

  val service = new OtherRoadwayMarkingService(mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }

  val propertiesL1 = Set(SimplePointAssetProperty("otherRoadwayMarking_regulation_number", List(PropertyValue("1"))))
  val propertiesL2 = Set(SimplePointAssetProperty("otherRoadwayMarking_regulation_number", List(PropertyValue("2"))))

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(service.dataSource)(test)


  test("Create new Other Roadway Marking") {
    runWithRollback {
      val properties = propertiesL1

      val roadLink = RoadLink(388553075, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = service.create(IncomingOtherRoadwayMarking(2.0, 0.0, 388553075, properties), testUser.username, roadLink)

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
      asset.propertyData.find(p => p.publicId == "otherRoadwayMarking_regulation_number").get.values.head.asInstanceOf[PropertyValue].propertyValue should be("1")
      asset.createdBy should be(Some(testUser.username))
      asset.createdAt shouldBe defined
    }
  }
}



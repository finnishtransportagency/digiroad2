package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{FeatureClass, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.dao.RoadLinkDAO
import fi.liikennevirasto.digiroad2.dao.pointasset.Obstacle
import fi.liikennevirasto.digiroad2.linearasset.{NewLimit, RoadLink, SpeedLimitValue, ValidityPeriod}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.linearasset.{ManoeuvreService, NewManoeuvre, SpeedLimitService}
import fi.liikennevirasto.digiroad2.service.pointasset.{IncomingObstacle, ObstacleService}
import fi.liikennevirasto.digiroad2.service.{AssetsOnExpiredLinksService, RoadLinkService}
import fi.liikennevirasto.digiroad2.{DummyEventBus, Point}
import org.joda.time.DateTime
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class ExpiredRoadLinkHandlingProcessSpec extends FunSuite with Matchers {
  def roadLinkDAO = new RoadLinkDAO
  val workListService = new AssetsOnExpiredLinksService
  val mockRoadLinkService: RoadLinkService = MockitoSugar.mock[RoadLinkService]
  val speedLimitService: SpeedLimitService = new SpeedLimitService(new DummyEventBus, mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = f
  }
  val manoeuvreService: ManoeuvreService = new ManoeuvreService(mockRoadLinkService, new DummyEventBus) {
    override def withDynTransaction[T](f: => T): T = f
  }
  val obstacleService = new ObstacleService(mockRoadLinkService)
  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(PostGISDatabase.ds)(test)

  val testUser = "test_user"
  val expiredLinkId = "1438d48d-dde6-43db-8aba-febf3d2220c0:1"
  val geometryString = "LINESTRING ZM(367880.004 6673884.307 21.732 0, 367877.133 6673892.641 22.435 8.815, 367868.672 6673919.942 24.138 37.397, 367858.777 6673950.125 24.72 69.16, 367851.607 6673967.978 24.331 88.399, 367843.6280000001 6673981.799 23.742 104.358, 367834.833 6673991.432 23.121 117.402, 367824.646 6674001.441 22.455 131.683)"
  val geometry = Seq(Point(367880.004, 6673884.307, 21.732), Point(367824.646, 6674001.441, 22.455))

  val testRoadLinkFetched: RoadLinkFetched = RoadLinkFetched(expiredLinkId, municipalityCode = 49, geometry = geometry,
    administrativeClass = Municipality, trafficDirection = TrafficDirection.BothDirections,
    featureClass = FeatureClass.CarRoad_IIIa, modifiedAt = None, attributes = Map(), constructionType = ConstructionType.InUse,
    linkSource = LinkGeomSource.NormalLinkInterface, length = 131.683)

  val testRoadLink: RoadLink = RoadLink(linkId = expiredLinkId, geometry = geometry, length = 131.683,
    administrativeClass = Municipality, functionalClass = 99, trafficDirection = TrafficDirection.BothDirections,
    linkType = UnknownLinkType, modifiedAt = None, modifiedBy = None, attributes = Map("MUNICIPALITYCODE" -> BigInt(49)), constructionType = ConstructionType.InUse,
    linkSource = LinkGeomSource.NormalLinkInterface, lanes = Seq())
  val obstacleValues = Seq(PropertyValue("2"))

  val simpleProperty: SimplePointAssetProperty = SimplePointAssetProperty("esterakennelma", obstacleValues)
  val incomingObstacle: IncomingObstacle = IncomingObstacle(2.0, 0.0, expiredLinkId, Set(simpleProperty))

  test("Delete expired links with no assets"){
    runWithRollback{
      val expiredLinks = roadLinkDAO.fetchExpiredRoadLinks()
      expiredLinks.isEmpty should equal(false)
      ExpiredRoadLinkHandlingProcess.handleExpiredRoadLinks()
      val expiredLinksAfter = roadLinkDAO.fetchExpiredRoadLinks()
      expiredLinksAfter.isEmpty should equal(true)
    }
  }

  test("Delete expired links with floating point assets"){
    runWithRollback {
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(expiredLinkId)).thenReturn(Some(testRoadLinkFetched))

      val createdObstacleId = obstacleService.create(incomingObstacle, testUser, testRoadLink, newTransaction = false)
      val createdObstacle = Obstacle(createdObstacleId, expiredLinkId, 2.0, 0.0, 0.0, floating = true, 0L, 49, Seq(),
        None, None, Some(testUser), Some(DateTime.now()), expired = false, LinkGeomSource.NormalLinkInterface, None)
      obstacleService.updateFloatingAsset(createdObstacle)
      val expiredLinks = roadLinkDAO.fetchExpiredRoadLinks()
      expiredLinks.isEmpty should equal(false)

      ExpiredRoadLinkHandlingProcess.handleExpiredRoadLinks()
      val expiredLinksAfter = roadLinkDAO.fetchExpiredRoadLinks()
      expiredLinksAfter.isEmpty should equal(true)

      val assetsOnWorkList = workListService.getAllWorkListAssets(false)
      assetsOnWorkList.size should equal(0)
    }
  }

  test("Persist links with assets, insert assets on work list"){
    runWithRollback {
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(expiredLinkId)).thenReturn(Some(testRoadLinkFetched))
      when(mockRoadLinkService.enrichFetchedRoadLinks(Seq(testRoadLinkFetched))).thenReturn(Seq(testRoadLink))

      val createdSpeedLimitId = speedLimitService.create(Seq(NewLimit(expiredLinkId, 0.0, 131.683)), SpeedLimitValue(30), "test", (_, _) => Unit).head
      val expiredLinks = roadLinkDAO.fetchExpiredRoadLinks()
      expiredLinks.isEmpty should equal(false)

      ExpiredRoadLinkHandlingProcess.handleExpiredRoadLinks()
      val expiredLinksAfter = roadLinkDAO.fetchExpiredRoadLinks()
      expiredLinksAfter.size should equal(1)

      val assetsOnWorkList = workListService.getAllWorkListAssets(false)
      assetsOnWorkList.size should equal(1)
      assetsOnWorkList.head.id should equal(createdSpeedLimitId)
      assetsOnWorkList.head.linkId should equal(expiredLinkId)
      assetsOnWorkList.head.assetTypeId should equal(SpeedLimitAsset.typeId)
    }
  }

  test("Asset is already on work list, do not insert duplicate"){
    runWithRollback {
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(expiredLinkId)).thenReturn(Some(testRoadLinkFetched))

      val createdSpeedLimitId = speedLimitService.create(Seq(NewLimit(expiredLinkId, 0.0, 131.683)), SpeedLimitValue(30), "test", (_, _) => Unit).head
      val expiredLinks = roadLinkDAO.fetchExpiredRoadLinks()
      expiredLinks.isEmpty should equal(false)

      ExpiredRoadLinkHandlingProcess.handleExpiredRoadLinks(cleanRoadLinkTable = false)
      val expiredLinksAfter = roadLinkDAO.fetchExpiredRoadLinks()
      expiredLinksAfter.isEmpty should equal(false)

      val assetsOnWorkList = workListService.getAllWorkListAssets(false)
      assetsOnWorkList.size should equal(1)
      assetsOnWorkList.head.id should equal(createdSpeedLimitId)
      assetsOnWorkList.head.linkId should equal(expiredLinkId)
      assetsOnWorkList.head.assetTypeId should equal(SpeedLimitAsset.typeId)

      //Run process again
      ExpiredRoadLinkHandlingProcess.handleExpiredRoadLinks(cleanRoadLinkTable = false)
      val expiredLinksAfter2ndRun = roadLinkDAO.fetchExpiredRoadLinks()
      expiredLinksAfter2ndRun.isEmpty should equal(false)

      val assetsOnWorkList2ndRun = workListService.getAllWorkListAssets(false)
      assetsOnWorkList2ndRun.size should equal(1)
      assetsOnWorkList2ndRun.head.id should equal(createdSpeedLimitId)
      assetsOnWorkList2ndRun.head.linkId should equal(expiredLinkId)
      assetsOnWorkList2ndRun.head.assetTypeId should equal(SpeedLimitAsset.typeId)
    }
  }


  test("Persist links with manoeuvres, does not insert manoeuvres into work list") {
    runWithRollback {
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(expiredLinkId)).thenReturn(Some(testRoadLinkFetched))
      when(mockRoadLinkService.enrichFetchedRoadLinks(Seq(testRoadLinkFetched))).thenReturn(Seq(testRoadLink))
      val expiredLinks = roadLinkDAO.fetchExpiredRoadLinks()
      expiredLinks.isEmpty should equal(false)
      val manoeuvreLinkIds = Seq("1d26dcec-ec5d-4c26-ac93-cfefaab26091:1", "5dc2e739-794e-4ba9-b635-c676d9b60e93:1")
      manoeuvreService.createManoeuvre("test", NewManoeuvre(Set.empty[ValidityPeriod], Seq.empty[Int], None, manoeuvreLinkIds, None, false))

      ExpiredRoadLinkHandlingProcess.handleExpiredRoadLinks()
      val expiredLinksAfter = roadLinkDAO.fetchExpiredRoadLinks()
      expiredLinksAfter.size should equal(2)
      expiredLinksAfter.map(_.linkId).toSet should be(manoeuvreLinkIds.toSet)

      val manoeuvreWorkList = manoeuvreService.getManoeuvreWorkList(false)
      manoeuvreWorkList.size should equal(0)
    }
  }
}

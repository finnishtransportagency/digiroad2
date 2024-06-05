package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.asset.SideCode.BothDirections
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.ChangeInfo
import fi.liikennevirasto.digiroad2.client.{FeatureClass, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MunicipalityDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.{AssetLastModification, PostGISLinearAssetDao}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{LinearAssetUtils, LinkIdGenerator, PolygonTools, TestTransactions}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummyEventBus, GeometryUtils, Point}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession


class RoadWidthServiceSpec extends FunSuite with Matchers {
  val RoadWidthAssetTypeId = 120

  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]
  val mockLinearAssetDao = MockitoSugar.mock[PostGISLinearAssetDao]
  val mockDynamicLinearAssetDao = MockitoSugar.mock[DynamicLinearAssetDao]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val linearAssetDao = new PostGISLinearAssetDao()
  val mockMunicipalityDao = MockitoSugar.mock[MunicipalityDao]

  val linkId = LinkIdGenerator.generateRandom()
  val roadLinkWithLinkSource = RoadLink(
    linkId, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
  when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(any[Set[String]], any[Boolean])).thenReturn(Seq(roadLinkWithLinkSource))

  val initChangeSet: ChangeSet = LinearAssetFiller.emptyChangeSet

  val randomLinkId1: String = LinkIdGenerator.generateRandom()
  val randomLinkId2: String = LinkIdGenerator.generateRandom()
  val randomLinkId3: String = LinkIdGenerator.generateRandom()
  val randomLinkId4: String = LinkIdGenerator.generateRandom()
  val randomLinkId5: String = LinkIdGenerator.generateRandom()

  val dynamicLinearAssetDAO = new DynamicLinearAssetDao

  object ServiceWithDao extends RoadWidthService(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: PostGISLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def polygonTools: PolygonTools = mockPolygonTools
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao

    override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
  }

  def runWithRollback(test: => Unit): Unit = assetLock.synchronized {
    TestTransactions.runWithRollback()(test)
  }

  val assetLock = "Used to prevent deadlocks"

  private def createChangeInfo(roadLinks: Seq[RoadLink], timeStamp: Long) = {
    roadLinks.map(rl => ChangeInfo(Some(rl.linkId), Some(rl.linkId), 0L, 1, None, None, None, None, timeStamp))
  }

  private def createService() = {
    val service = new RoadWidthService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
      override def withDynSession[T](f: => T): T = f
    }
    service
  }

  private def createRoadLinks(municipalityCode: Int) = {
    val newLinkId1 = randomLinkId1
    val newLinkId2 = randomLinkId2
    val newLinkId3 = randomLinkId3
    val newLinkId4 = randomLinkId4
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val attributes1 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2), "MTKCLASS" -> 12112)
    val attributes2 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2), "MTKCLASS" -> 12122)
    val attributes3 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2), "MTKCLASS" -> 2)

    val geometry = List(Point(0.0, 0.0), Point(20.0, 0.0))
    val newRoadLink1 = RoadLink(newLinkId1, geometry, GeometryUtils.geometryLength(geometry), administrativeClass,
      functionalClass, trafficDirection, linkType, None, None, attributes1)
    val newRoadLink2 = newRoadLink1.copy(linkId=newLinkId2, attributes = attributes2)
    val newRoadLink3 = newRoadLink1.copy(linkId=newLinkId3, attributes = attributes3)
    val newRoadLink4 = newRoadLink1.copy(linkId=newLinkId4, attributes = attributes3)
    List(newRoadLink1, newRoadLink2, newRoadLink3, newRoadLink4)
  }

  test("get unVerified road width assets") {
    val linkId1 = randomLinkId2
    val linkId2 = randomLinkId3
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val service = createService()

    val roadLinks = Seq(
      RoadLink(linkId1, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))),
      RoadLink(linkId2, List(Point(0.0, 0.0), Point(120.0, 0.0)), 120.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))))

    PostGISDatabase.withDynTransaction {
      when(mockMunicipalityDao.getMunicipalityNameByCode(235)).thenReturn("Kauniainen")
      when(mockRoadLinkService.getRoadLinks(any[BoundingRectangle], any[Set[Int]],any[Boolean])).thenReturn((roadLinks, Nil))
      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(any[Set[String]], any[Boolean])).thenReturn(roadLinks)

      val newAssets1 = service.create(Seq(NewLinearAsset(linkId1, 0.0, 20, NumericValue(2017), 1, 234567, None)), RoadWidthAssetTypeId, AutoGeneratedUsername.dr1Conversion)
      val newAssets2 = service.create(Seq(NewLinearAsset(linkId2, 40.0, 120, NumericValue(4779), 1, 234567, None)), RoadWidthAssetTypeId, "testuser")

      val unVerifiedAssets = service.getUnverifiedLinearAssets(RoadWidthAssetTypeId, Set())
      unVerifiedAssets.keys.head should be ("Kauniainen")
      unVerifiedAssets.flatMap(_._2).keys.head should be("Municipality")
      unVerifiedAssets.flatMap(_._2).values.head should be(newAssets1)
      unVerifiedAssets.flatMap(_._2).values.head should not be newAssets2
      dynamicSession.rollback()
    }
  }

  test("create roadWidth and check if informationSource is Municipality Maintainer "){

    val service = createService()
    val toInsert = Seq(NewLinearAsset(randomLinkId1, 0, 50, NumericValue(4000), BothDirections.value, 0, None), NewLinearAsset(randomLinkId2, 0, 50, NumericValue(3000), BothDirections.value, 0, None))
    runWithRollback {
      val assetsIds = service.create(toInsert, RoadWidth.typeId, "test")
      val assetsCreated = service.getPersistedAssetsByIds(RoadWidth.typeId, assetsIds.toSet)

      assetsCreated.length should be (2)
      assetsCreated.foreach{asset =>
        asset.informationSource should be (Some(MunicipalityMaintenainer))
      }
    }
  }

  test("update roadWidth and check if informationSource is Municipality Maintainer "){
    val propSuggestBox = DynamicProperty("suggest_box", "checkbox", false, List(DynamicPropertyValue(0)))

    val propInsWidth1 = DynamicProperty("width", "integer", true, Seq(DynamicPropertyValue("4000")))
    val propIns1: Seq[DynamicProperty] = List(propInsWidth1, propSuggestBox)
    val propInsWidth2 = DynamicProperty("width", "integer", true, Seq(DynamicPropertyValue("3000")))
    val propIns2: Seq[DynamicProperty] = List(propInsWidth2, propSuggestBox)

    val propUpdWidth = DynamicProperty("width", "integer", true, Seq(DynamicPropertyValue("1500")))
    val propUpd: Seq[DynamicProperty] = List(propSuggestBox, propUpdWidth)

    val roadWidthIns1 = DynamicValue(DynamicAssetValue(propIns1))
    val roadWidthIns2 = DynamicValue(DynamicAssetValue(propIns2))
    val roadWidthUpd = DynamicValue(DynamicAssetValue(propUpd))

    when(mockRoadLinkService.fetchNormalOrComplimentaryRoadLinkByLinkId(any[String])).thenReturn(Some(RoadLinkFetched(randomLinkId1, 235, Seq(Point(0, 0), Point(100, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))

    val service = createService()
    val toInsert = Seq(NewLinearAsset(randomLinkId1, 0, 50, roadWidthIns1, BothDirections.value, 0, None), NewLinearAsset(randomLinkId2, 0, 50, roadWidthIns2, BothDirections.value, 0, None))
    runWithRollback {
      val assetsIds = service.create(toInsert, RoadWidth.typeId, "test")
      val updated = service.update(assetsIds, roadWidthUpd, "userTest")

      val assetsUpdated = service.getPersistedAssetsByIds(RoadWidth.typeId, updated.toSet)

      assetsUpdated.length should be (2)
      assetsUpdated.foreach{asset =>
        asset.informationSource should be (Some(MunicipalityMaintenainer))
        asset.value.head.asInstanceOf[DynamicValue].value.properties.find(_.publicId == "width") should be (roadWidthUpd.value.properties.find(_.publicId == "width"))
      }
    }
  }

}



package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.asset.{DynamicProperty, _}
import fi.liikennevirasto.digiroad2.client.vvh.ChangeInfo
import fi.liikennevirasto.digiroad2.client.{FeatureClass, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.dao._
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{LinkIdGenerator, PolygonTools, TestTransactions}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils, Point}
import org.joda.time.DateTime
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}
class DynamicLinearTestSupporter extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]
  val (linkId1, linkId2, linkId3, linkId4) =
    (LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom())
  
  when(mockRoadLinkService.fetchByLinkId(linkId4)).thenReturn(Some(RoadLinkFetched(linkId4, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq(RoadLinkFetched(linkId4, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockRoadLinkService.fetchNormalOrComplimentaryRoadLinkByLinkId(any[String])).thenReturn(Some(RoadLinkFetched(linkId4, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))

  val roadLinkWithLinkSource = RoadLink(
    linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
  when(mockRoadLinkService.getRoadLinksAndChanges(any[BoundingRectangle], any[Set[Int]],any[Boolean])).thenReturn((List(roadLinkWithLinkSource), Nil))
  when(mockRoadLinkService.getRoadLinksWithComplementaryAndChanges(any[Int])).thenReturn((List(roadLinkWithLinkSource), Nil))
  when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(any[Set[String]], any[Boolean])).thenReturn(Seq(roadLinkWithLinkSource))
  when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(any[String], any[Boolean])).thenReturn(Some(roadLinkWithLinkSource))

  val mockLinearAssetDao = MockitoSugar.mock[PostGISLinearAssetDao]
  when(mockLinearAssetDao.fetchLinearAssetsByLinkIds(30, Seq(linkId1), "mittarajoitus", false))
    .thenReturn(Seq(PersistedLinearAsset(1, linkId1, 1, Some(NumericValue(40000)), 0.4, 9.6, None, None, None, None, false, 30, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None)))
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val linearAssetDao = new PostGISLinearAssetDao()
  val mVLinearAssetDao: DynamicLinearAssetDao = new DynamicLinearAssetDao
  val mockMunicipalityDao = MockitoSugar.mock[MunicipalityDao]
  val mockAssetDao = MockitoSugar.mock[PostGISAssetDao]
  val mockDynamicLinearAssetDao = MockitoSugar.mock[DynamicLinearAssetDao]

  val multiTypePropSeq = DynamicAssetValue(
    Seq(
      DynamicProperty("weight", "integer", required = true, Seq(DynamicPropertyValue("1000"))),
      DynamicProperty("mittarajoitus", "number", required = false, Seq(DynamicPropertyValue("1000"))),
      DynamicProperty("nimi_suomeksiTest", "text", required = false, Seq(DynamicPropertyValue("Dummy Text"))),
      DynamicProperty("suggest_box", "checkbox", required = false, List()),
      DynamicProperty("esteettomyys_liikuntarajoitteiselleTest", "long_text", required = false, Seq(DynamicPropertyValue("Long Dummy Text!!!!!!!!!!!!!!!!!!")))
    ))
  val multiTypePropSeq1 =DynamicAssetValue(
    Seq(
      DynamicProperty("nimi_suomeksiTest", "text", required = false, Seq(DynamicPropertyValue("Dummy Text One"))),
      DynamicProperty("esteettomyys_liikuntarajoitteiselleTest", "long_text", required = false, Seq(DynamicPropertyValue("Long Dummy Text!!!!!!!!!!!!!!!!!!")))
    ))
  val multiTypePropSeq2 =DynamicAssetValue(
    Seq(
      DynamicProperty("nimi_suomeksiTest", "text", required = false, Seq(DynamicPropertyValue("Dummy Text Two"))),
      DynamicProperty("esteettomyys_liikuntarajoitteiselleTest", "long_text", required = false, Seq(DynamicPropertyValue("Long Dummy Text!!!!!!!!!!!!!!!!!!")))
    ))
  val multiTypePropSeq3 =DynamicAssetValue(
    Seq(
      DynamicProperty("nimi_suomeksiTest", "text", required = false, Seq(DynamicPropertyValue("Dummy Text Five"))),
      DynamicProperty("esteettomyys_liikuntarajoitteiselleTest", "long_text", required = false, Seq(DynamicPropertyValue("Long Dummy Text!!!!!!!!!!!!!!!!!!")))
    ))
  val multiTypePropSeq4 =DynamicAssetValue(
    Seq(
      DynamicProperty("mittarajoitus", "number", required = false, Seq(DynamicPropertyValue("1000")))
    ))
  val multiTypePropSeq5 =DynamicAssetValue(
    Seq(
      DynamicProperty("weight", "integer", required = true, Seq(DynamicPropertyValue("1000"))),
      DynamicProperty("nimi_suomeksiTest", "text", required = false, Seq(DynamicPropertyValue("Dummy Text Five"))),
      DynamicProperty("esteettomyys_liikuntarajoitteiselleTest", "long_text", required = false, Seq(DynamicPropertyValue("Long Dummy Text!!!!!!!!!!!!!!!!!!")))
    ))

  val propertyData = DynamicValue(multiTypePropSeq)
  val propertyData1 = DynamicValue(multiTypePropSeq1)
  val propertyData2 = DynamicValue(multiTypePropSeq2)
  val propertyData3 = DynamicValue(multiTypePropSeq3)
  val propertyData4 = DynamicValue(multiTypePropSeq4)
  val propertyData5 = DynamicValue(multiTypePropSeq5)

  object PassThroughService extends LinearAssetOperations {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: PostGISLinearAssetDao = mockLinearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def polygonTools: PolygonTools = mockPolygonTools
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao
    override def assetDao: PostGISAssetDao = mockAssetDao
    def dynamicLinearAssetDao: DynamicLinearAssetDao = mockDynamicLinearAssetDao

    override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
    override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()) = throw new UnsupportedOperationException("Not supported method")
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(PostGISDatabase.ds)(test)
}
class DynamicLinearAssetServiceSpec extends DynamicLinearTestSupporter {

  object ServiceWithDao extends DynamicLinearAssetService(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: PostGISLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def polygonTools: PolygonTools = mockPolygonTools
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao
    override def assetDao: PostGISAssetDao = mockAssetDao
    override def dynamicLinearAssetDao: DynamicLinearAssetDao = mVLinearAssetDao

    override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
    override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()) = throw new UnsupportedOperationException("Not supported method")
  }


  test("Create new linear asset") {
    runWithRollback {
      val (propId1, propId2) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)

      //Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId1, 30, 'text', '0', 'testuser', 'nimi_suomeksiTest', null)""".execute

      //Long Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId2, 30, 'long_text', '0', 'testuser', 'esteettomyys_liikuntarajoitteiselleTest', null)""".execute


      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(linkId4, 0, 40, propertyData, 1, 0, None)), 30, "testuser")
      newAssets.length should be(1)
      val asset = mVLinearAssetDao.fetchDynamicLinearAssetsByIds(Set(newAssets.head)).head
      asset.value should be (Some(propertyData))
      asset.expired should be (false)
      mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(linkId4), newTransaction = false).head.linkSource.value should be (1)
    }
  }

  test("Create new linear asset with verified info") {
    runWithRollback {
      val (propId1, propId2) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)

      //Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId1, 30, 'text', '0', 'testuser', 'nimi_suomeksiTest', null)""".execute

      //Long Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId2, 30, 'long_text', '0', 'testuser', 'esteettomyys_liikuntarajoitteiselleTest', null)""".execute

      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(linkId4, 0, 40, propertyData, 1, 0, None)), 30, "testuser")
      newAssets.length should be(1)
      val asset = mVLinearAssetDao.fetchDynamicLinearAssetsByIds(Set(newAssets.head)).head
      asset.value should be (Some(propertyData))
      asset.expired should be (false)
      asset.verifiedBy.get should be ("testuser")
      asset.verifiedDate.get.toString("yyyy-MM-dd") should be (DateTime.now().toString("yyyy-MM-dd"))
    }
  }

  test("Separate linear asset") {
    val typeId = 140
    runWithRollback {
      val (propId1, propId2) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)

      //Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId1, $typeId, 'text', '0', 'testuser', 'nimi_suomeksiTest', null)""".execute

      //Long Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId2, $typeId, 'long_text', '0', 'testuser', 'esteettomyys_liikuntarajoitteiselleTest', null)""".execute

      val newLimit = NewLinearAsset(linkId = linkId4, startMeasure = 0, endMeasure = 10, value = propertyData1, sideCode = 1, 0, None)
      val assetId = ServiceWithDao.create(Seq(newLimit), typeId, "test").head

      when(mockAssetDao.getAssetTypeId(Seq(assetId))).thenReturn(Seq((assetId, typeId)))

      val createdId = ServiceWithDao.separate(assetId, Some(propertyData2), Some(propertyData3), "unittest", (i, a) => Unit)

      createdId.length should be (2)

      val createdLimit = ServiceWithDao.getPersistedAssetsByIds(typeId, Set(createdId.head)).head
      val createdLimit1 = ServiceWithDao.getPersistedAssetsByIds(typeId, Set(createdId.last)).head
      val oldLimit = ServiceWithDao.getPersistedAssetsByIds(typeId, Set(assetId)).head

      oldLimit.linkId should be (linkId4)
      oldLimit.sideCode should be (SideCode.BothDirections.value)
      oldLimit.expired should be (true)
      oldLimit.modifiedBy should be (None)

      createdLimit.linkId should be (linkId4)
      createdLimit.sideCode should be (SideCode.TowardsDigitizing.value)
      createdLimit.value should be (Some(propertyData2))
      createdLimit.createdBy should be (Some("test"))
      createdLimit.modifiedBy should be (Some("unittest"))

      createdLimit1.linkId should be (linkId4)
      createdLimit1.sideCode should be (SideCode.AgainstDigitizing.value)
      createdLimit1.value should be (Some(propertyData3))
      createdLimit1.createdBy should be (Some("test"))
    }
  }

  test("Separate with empty value towards digitization") {
    val typeId = 140
    runWithRollback {
      val (propId1, propId2) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)

      //Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId1, $typeId, 'text', '0', 'testuser', 'nimi_suomeksiTest', null)""".execute

      //Long Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId2, $typeId, 'long_text', '0', 'testuser', 'esteettomyys_liikuntarajoitteiselleTest', null)""".execute

      val newLimit = NewLinearAsset(linkId4, 0, 10, propertyData1, 1, 0, None)
      val assetId = ServiceWithDao.create(Seq(newLimit), typeId, "test").head

      when(mockAssetDao.getAssetTypeId(Seq(assetId))).thenReturn(Seq((assetId, typeId)))

      val createdId = ServiceWithDao.separate(assetId, None, Some(propertyData3), "unittest", (i, a) => Unit)
      createdId.length should be (1)

      val createdLimit = ServiceWithDao.getPersistedAssetsByIds(typeId, Set(createdId.head)).head
      val oldLimit = ServiceWithDao.getPersistedAssetsByIds(typeId, Set(assetId)).head

      oldLimit.linkId should be (linkId4)
      oldLimit.sideCode should be (SideCode.BothDirections.value)
      oldLimit.expired should be (true)
      oldLimit.modifiedBy should be (None)

      createdLimit.linkId should be (linkId4)
      createdLimit.sideCode should be (SideCode.AgainstDigitizing.value)
      createdLimit.value should be (Some(propertyData3))
      createdLimit.expired should be (false)
      createdLimit.createdBy should be (Some("test"))
      createdLimit.modifiedBy should be (Some("unittest"))    }
  }

  test("Separate with empty value against digitization") {
    val typeId = 140
    runWithRollback {
      val (propId1, propId2) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)

      //Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId1, $typeId, 'text', '0', 'testuser', 'nimi_suomeksiTest', null)""".execute

      //Long Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId2, $typeId, 'long_text', '0', 'testuser', 'esteettomyys_liikuntarajoitteiselleTest', null)""".execute

      val newLimit = NewLinearAsset(linkId4, 0, 10, propertyData1, 1, 0, None)
      val assetId = ServiceWithDao.create(Seq(newLimit), typeId, "test").head

      when(mockAssetDao.getAssetTypeId(Seq(assetId))).thenReturn(Seq((assetId, typeId)))

      val newAssetIdAfterUpdate = ServiceWithDao.separate(assetId, Some(propertyData2), None, "unittest", (i, a) => Unit)
      newAssetIdAfterUpdate.size should be(1)

      val oldLimit = ServiceWithDao.getPersistedAssetsByIds(typeId, Set(newAssetIdAfterUpdate.head)).head

      oldLimit.linkId should be (linkId4)
      oldLimit.sideCode should be (SideCode.TowardsDigitizing.value)
      oldLimit.value should be (Some(propertyData2))
      oldLimit.expired should be (false)
      oldLimit.modifiedBy should be (Some("unittest"))

    }
  }

  test("Split linear asset") {
    val typeId = 140
    runWithRollback {
      val (propId1, propId2) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)

      //Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId1, $typeId, 'text', '0', 'testuser', 'nimi_suomeksiTest', null)""".execute

      //Long Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId2, $typeId, 'long_text', '0', 'testuser', 'esteettomyys_liikuntarajoitteiselleTest', null)""".execute

      val newLimit = NewLinearAsset(linkId4, 0, 10, propertyData1, 1, 0, None)
      val assetId = ServiceWithDao.create(Seq(newLimit), 140, "test").head

      when(mockAssetDao.getAssetTypeId(Seq(assetId))).thenReturn(Seq((assetId, typeId)))

      val ids = ServiceWithDao.split(assetId, 2.0, Some(propertyData2), Some(propertyData3), "unittest", (i, a) => Unit)

      ids.length should be(2)

      val createdLimit = ServiceWithDao.getPersistedAssetsByIds(140, Set(ids.head)).head
      val createdLimit1 = ServiceWithDao.getPersistedAssetsByIds(140, Set(ids.last)).head

      createdLimit.linkId should be (linkId4)
      createdLimit.sideCode should be (SideCode.BothDirections.value)
      createdLimit.value should be (Some(propertyData2))
      createdLimit.modifiedBy should be (Some("unittest"))
      createdLimit.startMeasure should be (2.0)
      createdLimit.endMeasure should be (10.0)

      createdLimit1.linkId should be (linkId4)
      createdLimit1.sideCode should be (SideCode.BothDirections.value)
      createdLimit1.value should be (Some(propertyData3))
      createdLimit1.createdBy should be (Some("test"))
      createdLimit1.startMeasure should be (0.0)
      createdLimit1.endMeasure should be (2.0)
    }
  }

  test("Separation should call municipalityValidation") {
    def failingMunicipalityValidation(code: Int, adminClass: AdministrativeClass): Unit = { throw new IllegalArgumentException }
    runWithRollback {
      val (propId1, propId2) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)

      //Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId1, 140, 'text', '0', 'testuser', 'nimi_suomeksiTest', null)""".execute

      //Long Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId2, 140, 'long_text', '0', 'testuser', 'esteettomyys_liikuntarajoitteiselleTest', null)""".execute

      val newLimit = NewLinearAsset(linkId4, 0, 10, propertyData1, 1, 0, None)
      val assetId = ServiceWithDao.create(Seq(newLimit), 140, "test").head

      when(mockAssetDao.getAssetTypeId(Seq(assetId))).thenReturn(Seq((assetId, 140)))

      intercept[IllegalArgumentException] {
        ServiceWithDao.separate(assetId, Some(propertyData1), Some(propertyData2), "unittest", failingMunicipalityValidation)
      }
    }
  }

  test("get unVerified linear assets") {
    when(mockMunicipalityDao.getMunicipalitiesNameAndIdByCode(any[Set[Int]])).thenReturn(List(MunicipalityInfo(235, 9, "Kauniainen")))
      runWithRollback {
        val (propId1, propId2) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)

        //Text property value
        sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId1, 40, 'text', '0', 'testuser', 'nimi_suomeksiTest', null)""".execute

        //Long Text property value
        sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId2, 40, 'long_text', '0', 'testuser', 'esteettomyys_liikuntarajoitteiselleTest', null)""".execute

      val newAssets1 = ServiceWithDao.create(Seq(NewLinearAsset(linkId1, 0, 30, propertyData5, 1, 0, None)), 40, AutoGeneratedUsername.dr1Conversion)
      val newAssets2 = ServiceWithDao.create(Seq(NewLinearAsset(linkId1, 30, 60, propertyData5, 1, 0, None)), 40, "testuser")

      val unVerifiedAssets = ServiceWithDao.getUnverifiedLinearAssets(40, Set())
      unVerifiedAssets.keys.head should be ("Kauniainen")
      unVerifiedAssets.flatMap(_._2).keys.head should be ("Municipality")
      unVerifiedAssets.flatMap(_._2).values.head should be (newAssets1)
    }
  }

  test("get unVerified linear assets only for specific municipalities") {
    val roadLink = Seq(RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface),
      RoadLink(linkId2, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(92)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface),
      RoadLink(linkId3, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface))
    when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(any[Set[String]], any[Boolean])).thenReturn(roadLink)

    val municipalitiesInfo = List(MunicipalityInfo(91, 9, "Helsinki"), MunicipalityInfo(92, 9, "Vantaa"))
    when(mockMunicipalityDao.getMunicipalitiesNameAndIdByCode(any[Set[Int]])).thenReturn(municipalitiesInfo)

    runWithRollback {
      val (propId1, propId2) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)

      //Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId1, 40, 'text', '0', 'testuser', 'nimi_suomeksiTest', null)""".execute

      //Long Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId2, 40, 'long_text', '0', 'testuser', 'esteettomyys_liikuntarajoitteiselleTest', null)""".execute

      val newAssets1 = ServiceWithDao.create(Seq(NewLinearAsset(linkId1, 0, 30, propertyData5, 1, 0, None)), 40, AutoGeneratedUsername.dr1Conversion)
      val newAssets2 = ServiceWithDao.create(Seq(NewLinearAsset(linkId2, 0, 60, propertyData5, 1, 0, None)), 40, AutoGeneratedUsername.dr1Conversion)
      val newAssets3 = ServiceWithDao.create(Seq(NewLinearAsset(linkId3, 0, 30, propertyData5, 1, 0, None)), 40, AutoGeneratedUsername.dr1Conversion)

      val unVerifiedAssets = ServiceWithDao.getUnverifiedLinearAssets(40, Set(91,92))
      unVerifiedAssets.keys.size should be (2)
      unVerifiedAssets.keys.forall(List("Vantaa", "Helsinki").contains) should be (true)
      unVerifiedAssets.filter(_._1 == "Vantaa").flatMap(_._2).keys.head should be ("Municipality")
      unVerifiedAssets.filter(_._1 == "Vantaa").flatMap(_._2).values.head should be (newAssets2)
      unVerifiedAssets.filter(_._1 == "Helsinki").flatMap(_._2).keys.head should be ("Municipality")
      unVerifiedAssets.filter(_._1 == "Helsinki").flatMap(_._2).values.head should be (newAssets1)
    }
  }

  test("should not get administrative class 'State'") {
    val roadLink = Seq(RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, State,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface))
    when(mockMunicipalityDao.getMunicipalityNameByCode(91)).thenReturn("Helsinki")
    when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(any[Set[String]], any[Boolean])).thenReturn(roadLink)

    runWithRollback {
      val (propId1, propId2) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)

      //Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId1, 40, 'text', '0', 'testuser', 'nimi_suomeksiTest', null)""".execute

      //Long Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId2, 40, 'long_text', '0', 'testuser', 'esteettomyys_liikuntarajoitteiselleTest', null)""".execute

      val newAssets1 = ServiceWithDao.create(Seq(NewLinearAsset(linkId1, 0, 30, propertyData5, 1, 0, None)), 40, AutoGeneratedUsername.dr1Conversion)

      val unVerifiedAssets = ServiceWithDao.getUnverifiedLinearAssets(40, Set(91))
      unVerifiedAssets should be(empty)
    }
  }

  case class TestAssetInfo(newLinearAsset: NewLinearAsset, typeId: Int)

  ignore("Adjust projected asset with creation"){
    val (oldLinkId, newLinkId) = (LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom())
    val careClassValue = DynamicValue(DynamicAssetValue(Seq(
      DynamicProperty("hoitoluokat_talvihoitoluokka", "single_choice", false, Seq(DynamicPropertyValue(20))),
      DynamicProperty("hoitoluokat_viherhoitoluokka", "single_choice", false, Seq(DynamicPropertyValue(3)))
    )))

    val carryingCapacityValue = DynamicValue(DynamicAssetValue(Seq(
      DynamicProperty("kevatkantavuus", "integer", false, Seq(DynamicPropertyValue(0))),
      DynamicProperty("routivuuskerroin", "single_choice", false, Seq(DynamicPropertyValue(40))),
      DynamicProperty("mittauspaiva", "date", false, Seq(DynamicPropertyValue("11.9.2018")))
    )))

    val damagedByThawValue = DynamicValue(DynamicAssetValue(Seq(
      DynamicProperty("kelirikko", "number", false, Seq(DynamicPropertyValue(10))),
      DynamicProperty("spring_thaw_period", "number", false, Seq()),
      DynamicProperty("annual_repetition", "number", false, Seq()),
      DynamicProperty("suggest_box", "checkbox", false, List())
    )))

    val roadWorksValue = DynamicValue(DynamicAssetValue(Seq(
      DynamicProperty("tyon_tunnus", "number", false, Seq(DynamicPropertyValue(99))),
      DynamicProperty("arvioitu_kesto", "date", false, Seq(DynamicPropertyValue(Map(("startDate", "11.9.2018"), ("endDate", "21.9.2018"))))),
      DynamicProperty("suggest_box", "checkbox", false, List())
    )))

    val assetsInfo = Seq(
      TestAssetInfo(NewLinearAsset(oldLinkId, 0, 150, careClassValue, SideCode.AgainstDigitizing.value, 0, None), CareClass.typeId),
      TestAssetInfo(NewLinearAsset(oldLinkId, 0, 150, carryingCapacityValue, SideCode.AgainstDigitizing.value, 0, None), CarryingCapacity.typeId),
      TestAssetInfo(NewLinearAsset(oldLinkId, 0, 150, damagedByThawValue, SideCode.AgainstDigitizing.value, 0, None), DamagedByThaw.typeId),
      TestAssetInfo(NewLinearAsset(oldLinkId, 0, 150, roadWorksValue, SideCode.AgainstDigitizing.value, 0, None), RoadWorksAsset.typeId)
        )

      assetsInfo.zipWithIndex.foreach(adjustProjectedAssetWithCreation(_, oldLinkId, newLinkId))
  }

  def adjustProjectedAssetWithCreation(assetInfoCount: (TestAssetInfo, Int), oldLinkId: String, newLinkId: String) : Unit = {
    val assetInfo = assetInfoCount._1
    val count = assetInfoCount._2 + 1
    val municipalityCode = 444
    val functionalClass = 1
    val geom = List(Point(0, 0), Point(300, 0))
    val len = GeometryUtils.geometryLength(geom)

    val roadLinks = Seq(RoadLink(oldLinkId, geom, len, State, functionalClass, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(newLinkId, geom, len, State, functionalClass, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    )
    val changeInfo = Seq(
      ChangeInfo(Some(oldLinkId), Some(newLinkId), 1204467577, 1, Some(0), Some(150), Some(100), Some(200), 1461970812000L))

    runWithRollback {
      val assetId = ServiceWithDao.create(Seq(assetInfo.newLinearAsset), assetInfo.typeId, "KX1", 0).head

      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChanges(any[Int])).thenReturn((roadLinks, changeInfo))
      ServiceWithDao.getByMunicipality(assetInfo.typeId, municipalityCode)

      withClue("assetName " + AssetTypeInfo.apply(assetInfo.typeId).layerName) {
        verify(mockEventBus, times(1))
          .publish("dynamicAsset:update", ChangeSet(Set.empty[Long], Nil, Seq(SideCodeAdjustment(assetId,newLinkId,SideCode.BothDirections,assetInfo.typeId), SideCodeAdjustment(0,newLinkId,SideCode.BothDirections,assetInfo.typeId)), Set.empty[Long], valueAdjustments = Seq.empty[ValueAdjustment]))

        val captor = ArgumentCaptor.forClass(classOf[Seq[PersistedLinearAsset]])
        verify(mockEventBus, times(count)).publish(org.mockito.ArgumentMatchers.eq("dynamicAsset:saveProjectedAssets"), captor.capture())

        val toBeComparedProperties = assetInfo.newLinearAsset.value.asInstanceOf[DynamicValue].value.properties
        val projectedAssets = captor.getValue.asInstanceOf[Seq[PersistedLinearAsset]]
        projectedAssets.length should be(1)
        val projectedAsset = projectedAssets.head
        projectedAsset.id should be(0)
        projectedAsset.linkId should be(newLinkId)
        projectedAsset.value.get.asInstanceOf[DynamicValue].value.properties.foreach { property =>
          val existingProperty = toBeComparedProperties.find(p => p.publicId == property.publicId)
          existingProperty.get.values.forall { value =>
            toBeComparedProperties.asInstanceOf[Seq[DynamicProperty]].exists(prop => prop.values.contains(value))
          }
        }
      }
    }
  }
}

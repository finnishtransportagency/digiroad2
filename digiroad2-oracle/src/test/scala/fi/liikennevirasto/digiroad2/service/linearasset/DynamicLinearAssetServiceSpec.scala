
package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh._
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao._
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{PolygonTools, TestTransactions}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

class DynamicLinearAssetServiceSpec extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]

  when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
  when(mockVVHRoadLinkClient.fetchByLinkId(388562360l)).thenReturn(Some(VVHRoadlink(388562360l, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(VVHRoadlink(388562360l, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockVVHClient.fetchRoadLinkByLinkId(any[Long])).thenReturn(Some(VVHRoadlink(388562360l, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))

  val roadLinkWithLinkSource = RoadLink(
    1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
  when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(roadLinkWithLinkSource), Nil))
  when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[Int])).thenReturn((List(roadLinkWithLinkSource), Nil))
  when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq(roadLinkWithLinkSource))
  when(mockRoadLinkService.getRoadLinkAndComplementaryFromVVH(any[Long], any[Boolean])).thenReturn(Some(roadLinkWithLinkSource))

  val mockLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]
  when(mockLinearAssetDao.fetchLinearAssetsByLinkIds(30, Seq(1), "mittarajoitus", false))
    .thenReturn(Seq(PersistedLinearAsset(1, 1, 1, Some(NumericValue(40000)), 0.4, 9.6, None, None, None, None, false, 30, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None)))
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val linearAssetDao = new OracleLinearAssetDao(mockVVHClient, mockRoadLinkService)
  val mVLinearAssetDao: DynamicLinearAssetDao = new DynamicLinearAssetDao
  val mockMunicipalityDao = MockitoSugar.mock[MunicipalityDao]
  val mockAssetDao = MockitoSugar.mock[OracleAssetDao]
  val mockDynamicLinearAssetDao = MockitoSugar.mock[DynamicLinearAssetDao]

  val multiTypePropSeq = DynamicAssetValue(
    Seq(
      DynamicProperty("nimi_suomeksiTest", "text", required = false, Seq(DynamicPropertyValue("Dummy Text"))),
      DynamicProperty("esteettomyys_liikuntarajoitteiselleTest", "long_text", required = false, Seq(DynamicPropertyValue("Long Dummy Text!!!!!!!!!!!!!!!!!!"))),
      DynamicProperty("mittarajoitus", "number", required = false, Seq(DynamicPropertyValue("1000")))
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

  val propertyData = DynamicValue(multiTypePropSeq)
  val propertyData1 = DynamicValue(multiTypePropSeq1)
  val propertyData2 = DynamicValue(multiTypePropSeq2)
  val propertyData3 = DynamicValue(multiTypePropSeq3)
  val propertyData4 = DynamicValue(multiTypePropSeq4)

  object PassThroughService extends LinearAssetOperations {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: OracleLinearAssetDao = mockLinearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def vvhClient: VVHClient = mockVVHClient
    override def polygonTools: PolygonTools = mockPolygonTools
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao
    override def assetDao: OracleAssetDao = mockAssetDao
    def dynamicLinearAssetDao: DynamicLinearAssetDao = mockDynamicLinearAssetDao

    override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
    override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()) = throw new UnsupportedOperationException("Not supported method")
  }

  object ServiceWithDao extends DynamicLinearAssetService(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: OracleLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def vvhClient: VVHClient = mockVVHClient
    override def polygonTools: PolygonTools = mockPolygonTools
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao
    override def assetDao: OracleAssetDao = mockAssetDao
    override def dynamicLinearAssetDao: DynamicLinearAssetDao = mVLinearAssetDao

    override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
    override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()) = throw new UnsupportedOperationException("Not supported method")
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(PassThroughService.dataSource)(test)


  test("Create new linear asset") {
    runWithRollback {
      val (propId1, propId2) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)

      //Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId1, 30, 'text', 0, 'testuser', 'nimi_suomeksiTest', null)""".execute

      //Long Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId2, 30, 'long_text', 0, 'testuser', 'esteettomyys_liikuntarajoitteiselleTest', null)""".execute


      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(388562360l, 0, 40, propertyData, 1, 0, None)), 30, "testuser")
      newAssets.length should be(1)
      val asset = mVLinearAssetDao.fetchDynamicLinearAssetsByIds(Set(newAssets.head)).head
      asset.value should be (Some(propertyData))
      asset.expired should be (false)
      mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(388562360l), newTransaction = false).head.linkSource.value should be (1)
    }
  }

  test("Create new linear asset with verified info") {
    runWithRollback {
      val (propId1, propId2) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)

      //Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId1, 30, 'text', 0, 'testuser', 'nimi_suomeksiTest', null)""".execute

      //Long Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId2, 30, 'long_text', 0, 'testuser', 'esteettomyys_liikuntarajoitteiselleTest', null)""".execute

      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(388562360l, 0, 40, propertyData, 1, 0, None)), 30, "testuser")
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
           VALUES ($propId1, $typeId, 'text', 0, 'testuser', 'nimi_suomeksiTest', null)""".execute

      //Long Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId2, $typeId, 'long_text', 0, 'testuser', 'esteettomyys_liikuntarajoitteiselleTest', null)""".execute

      val newLimit = NewLinearAsset(linkId = 388562360, startMeasure = 0, endMeasure = 10, value = propertyData1, sideCode = 1, 0, None)
      val assetId = ServiceWithDao.create(Seq(newLimit), typeId, "test").head

      when(mockAssetDao.getAssetTypeId(Seq(assetId))).thenReturn(Seq((assetId, typeId)))

      val createdId = ServiceWithDao.separate(assetId, Some(propertyData2), Some(propertyData3), "unittest", (i, a) => Unit)
      val createdLimit = ServiceWithDao.getPersistedAssetsByIds(typeId, Set(createdId(1))).head
      val oldLimit = ServiceWithDao.getPersistedAssetsByIds(typeId, Set(createdId.head)).head

      oldLimit.linkId should be (388562360)
      oldLimit.sideCode should be (SideCode.TowardsDigitizing.value)
      oldLimit.value should be (Some(propertyData2))
      oldLimit.modifiedBy should be (Some("unittest"))

      createdLimit.linkId should be (388562360)
      createdLimit.sideCode should be (SideCode.AgainstDigitizing.value)
      createdLimit.value should be (Some(propertyData3))
      createdLimit.createdBy should be (Some("unittest"))
    }
  }

  test("Separate with empty value towards digitization") {
    val typeId = 140
    runWithRollback {
      val (propId1, propId2) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)

      //Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId1, $typeId, 'text', 0, 'testuser', 'nimi_suomeksiTest', null)""".execute

      //Long Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId2, $typeId, 'long_text', 0, 'testuser', 'esteettomyys_liikuntarajoitteiselleTest', null)""".execute

      val newLimit = NewLinearAsset(388562360, 0, 10, propertyData1, 1, 0, None)
      val assetId = ServiceWithDao.create(Seq(newLimit), typeId, "test").head

      when(mockAssetDao.getAssetTypeId(Seq(assetId))).thenReturn(Seq((assetId, typeId)))

      val createdId = ServiceWithDao.separate(assetId, None, Some(propertyData3), "unittest", (i, a) => Unit).filter(_ != assetId).head
      val createdLimit = ServiceWithDao.getPersistedAssetsByIds(typeId, Set(createdId)).head
      val oldLimit = ServiceWithDao.getPersistedAssetsByIds(typeId, Set(assetId)).head

      oldLimit.linkId should be (388562360)
      oldLimit.sideCode should be (SideCode.TowardsDigitizing.value)
      oldLimit.expired should be (true)
      oldLimit.modifiedBy should be (Some("unittest"))

      createdLimit.linkId should be (388562360)
      createdLimit.sideCode should be (SideCode.AgainstDigitizing.value)
      createdLimit.value should be (Some(propertyData3))
      createdLimit.expired should be (false)
      createdLimit.createdBy should be (Some("unittest"))
    }
  }

  test("Separate with empty value against digitization") {
    val typeId = 140
    runWithRollback {
      val (propId1, propId2) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)

      //Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId1, $typeId, 'text', 0, 'testuser', 'nimi_suomeksiTest', null)""".execute

      //Long Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId2, $typeId, 'long_text', 0, 'testuser', 'esteettomyys_liikuntarajoitteiselleTest', null)""".execute

      val newLimit = NewLinearAsset(388562360, 0, 10, propertyData1, 1, 0, None)
      val assetId = ServiceWithDao.create(Seq(newLimit), typeId, "test").head

      when(mockAssetDao.getAssetTypeId(Seq(assetId))).thenReturn(Seq((assetId, typeId)))

      val newAssetIdAfterUpdate = ServiceWithDao.separate(assetId, Some(propertyData2), None, "unittest", (i, a) => Unit)
      newAssetIdAfterUpdate.size should be(1)

      val oldLimit = ServiceWithDao.getPersistedAssetsByIds(typeId, Set(newAssetIdAfterUpdate.head)).head

      oldLimit.linkId should be (388562360)
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
           VALUES ($propId1, $typeId, 'text', 0, 'testuser', 'nimi_suomeksiTest', null)""".execute

      //Long Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId2, $typeId, 'long_text', 0, 'testuser', 'esteettomyys_liikuntarajoitteiselleTest', null)""".execute

      val newLimit = NewLinearAsset(388562360, 0, 10, propertyData1, 1, 0, None)
      val assetId = ServiceWithDao.create(Seq(newLimit), 140, "test").head

      when(mockAssetDao.getAssetTypeId(Seq(assetId))).thenReturn(Seq((assetId, typeId)))

      val ids = ServiceWithDao.split(assetId, 2.0, Some(propertyData2), Some(propertyData3), "unittest", (i, a) => Unit)

      val createdId = ids(1)
      val createdLimit = ServiceWithDao.getPersistedAssetsByIds(140, Set(createdId)).head
      val oldLimit = ServiceWithDao.getPersistedAssetsByIds(140, Set(ids.head)).head

      oldLimit.linkId should be (388562360)
      oldLimit.sideCode should be (SideCode.BothDirections.value)
      oldLimit.value should be (Some(propertyData2))
      oldLimit.modifiedBy should be (Some("unittest"))
      oldLimit.startMeasure should be (2.0)
      oldLimit.endMeasure should be (10.0)

      createdLimit.linkId should be (388562360)
      createdLimit.sideCode should be (SideCode.BothDirections.value)
      createdLimit.value should be (Some(propertyData3))
      createdLimit.createdBy should be (Some("unittest"))
      createdLimit.startMeasure should be (0.0)
      createdLimit.endMeasure should be (2.0)
    }
  }

  test("Separation should call municipalityValidation") {
    def failingMunicipalityValidation(code: Int, adminClass: AdministrativeClass): Unit = { throw new IllegalArgumentException }
    runWithRollback {
      val (propId1, propId2) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)

      //Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId1, 140, 'text', 0, 'testuser', 'nimi_suomeksiTest', null)""".execute

      //Long Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId2, 140, 'long_text', 0, 'testuser', 'esteettomyys_liikuntarajoitteiselleTest', null)""".execute

      val newLimit = NewLinearAsset(388562360, 0, 10, propertyData1, 1, 0, None)
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
           VALUES ($propId1, 40, 'text', 0, 'testuser', 'nimi_suomeksiTest', null)""".execute

        //Long Text property value
        sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId2, 40, 'long_text', 0, 'testuser', 'esteettomyys_liikuntarajoitteiselleTest', null)""".execute

      val newAssets1 = ServiceWithDao.create(Seq(NewLinearAsset(1, 0, 30, propertyData1, 1, 0, None)), 40, "dr1_conversion")
      val newAssets2 = ServiceWithDao.create(Seq(NewLinearAsset(1, 30, 60, propertyData3, 1, 0, None)), 40, "testuser")

      val unVerifiedAssets = ServiceWithDao.getUnverifiedLinearAssets(40, Set())
      unVerifiedAssets.keys.head should be ("Kauniainen")
      unVerifiedAssets.flatMap(_._2).keys.head should be ("Municipality")
      unVerifiedAssets.flatMap(_._2).values.head should be (newAssets1)
    }
  }

  test("get unVerified linear assets only for specific municipalities") {
    val roadLink = Seq(RoadLink(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface),
      RoadLink(2, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(92)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface),
      RoadLink(3, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface))
    when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(any[Set[Long]], any[Boolean])).thenReturn(roadLink)

    val municipalitiesInfo = List(MunicipalityInfo(91, 9, "Helsinki"), MunicipalityInfo(92, 9, "Vantaa"))
    when(mockMunicipalityDao.getMunicipalitiesNameAndIdByCode(any[Set[Int]])).thenReturn(municipalitiesInfo)

    runWithRollback {
      val (propId1, propId2) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)

      //Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId1, 40, 'text', 0, 'testuser', 'nimi_suomeksiTest', null)""".execute

      //Long Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId2, 40, 'long_text', 0, 'testuser', 'esteettomyys_liikuntarajoitteiselleTest', null)""".execute

      val newAssets1 = ServiceWithDao.create(Seq(NewLinearAsset(1, 0, 30, propertyData1, 1, 0, None)), 40, "dr1_conversion")
      val newAssets2 = ServiceWithDao.create(Seq(NewLinearAsset(2, 0, 60, propertyData2, 1, 0, None)), 40, "dr1_conversion")
      val newAssets3 = ServiceWithDao.create(Seq(NewLinearAsset(3, 0, 30, propertyData3, 1, 0, None)), 40, "dr1_conversion")

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
    val roadLink = Seq(RoadLink(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, State,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface))
    when(mockMunicipalityDao.getMunicipalityNameByCode(91)).thenReturn("Helsinki")
    when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(any[Set[Long]], any[Boolean])).thenReturn(roadLink)

    runWithRollback {
      val (propId1, propId2) = (Sequences.nextPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)

      //Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId1, 40, 'text', 0, 'testuser', 'nimi_suomeksiTest', null)""".execute

      //Long Text property value
      sqlu"""INSERT INTO PROPERTY (ID, ASSET_TYPE_ID, PROPERTY_TYPE, REQUIRED, CREATED_BY, PUBLIC_ID, NAME_LOCALIZED_STRING_ID)
           VALUES ($propId2, 40, 'long_text', 0, 'testuser', 'esteettomyys_liikuntarajoitteiselleTest', null)""".execute

      val newAssets1 = ServiceWithDao.create(Seq(NewLinearAsset(1, 0, 30, propertyData1, 1, 0, None)), 40, "dr1_conversion")

      val unVerifiedAssets = ServiceWithDao.getUnverifiedLinearAssets(40, Set(91))
      unVerifiedAssets should be(empty)
    }
  }

  test("Create new linear asset with validity period property") {
    runWithRollback {
      val typeId = 160
      val value = Seq(DynamicPropertyValue(Map("days" -> BigInt(1), "startHour" -> BigInt(0), "endHour" -> BigInt(0), "startMinute" -> BigInt(24), "endMinute" -> BigInt(0), "periodType" -> None)))
      val propertyData  = DynamicValue(DynamicAssetValue(Seq(DynamicProperty("public_validity_period", "time_period", false, value))))

      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(388562360l, 0, 40, propertyData, 1, 0, None)), typeId, "testuser")
      newAssets.length should be(1)
      val asset = ServiceWithDao.getPersistedAssetsByIds(typeId, newAssets.toSet).head
      asset.value.get.equals(propertyData) should be (true)
    }
  }

  test("Create new linear asset with validity periods property values") {
    runWithRollback {
      val typeId = 160
      val value = Seq(DynamicPropertyValue(Map("days" -> BigInt(1), "startHour" -> BigInt(10), "endHour" -> BigInt(14), "startMinute" -> BigInt(0), "endMinute" -> BigInt(0), "periodType" -> None)),
                      DynamicPropertyValue(Map("days" -> BigInt(1), "startHour" -> BigInt(16), "endHour" -> BigInt(20), "startMinute" -> BigInt(30), "endMinute" -> BigInt(30), "periodType" -> None)))

      val propertyData  = DynamicValue(DynamicAssetValue(Seq(DynamicProperty("public_validity_period", "time_period", false, value))))

      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(388562360l, 0, 40, propertyData, 1, 0, None)), typeId, "testuser")
      newAssets.length should be(1)
      val asset = ServiceWithDao.getPersistedAssetsByIds(typeId, newAssets.toSet).head
      asset.value.get.equals(propertyData) should be (true)
    }
  }

}

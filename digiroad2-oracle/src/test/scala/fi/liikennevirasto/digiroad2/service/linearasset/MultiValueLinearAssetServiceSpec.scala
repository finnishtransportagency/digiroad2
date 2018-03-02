
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
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

class MultiValueLinearAssetServiceSpec extends FunSuite with Matchers {
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

  val mockLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]
  when(mockLinearAssetDao.fetchLinearAssetsByLinkIds(30, Seq(1), "mittarajoitus", false))
    .thenReturn(Seq(PersistedLinearAsset(1, 1, 1, Some(NumericValue(40000)), 0.4, 9.6, None, None, None, None, false, 30, 0, None, LinkGeomSource.NormalLinkInterface, None, None)))
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val linearAssetDao = new OracleLinearAssetDao(mockVVHClient, mockRoadLinkService)
  val mVLinearAssetDao: MultiValueLinearAssetDao = new MultiValueLinearAssetDao
  val mockMunicipalityDao = MockitoSugar.mock[MunicipalityDao]
  val mockAssetDao = MockitoSugar.mock[OracleAssetDao]
  val mockMultiValueLinearAssetDao = MockitoSugar.mock[MultiValueLinearAssetDao]

  val multiTypePropSeq = MultiAssetValue(
    Seq(
      MultiTypeProperty("nimi_suomeksiTest", "text", Seq(MultiTypePropertyValue("Dummy Text"))),
      MultiTypeProperty("esteettomyys_liikuntarajoitteiselleTest", "long_text", Seq(MultiTypePropertyValue("Long Dummy Text!!!!!!!!!!!!!!!!!!"))),
      MultiTypeProperty("mittarajoitus", "number", Seq(MultiTypePropertyValue("1000")))
    ))
  val multiTypePropSeq1 =MultiAssetValue(
    Seq(
      MultiTypeProperty("nimi_suomeksiTest", "text", Seq(MultiTypePropertyValue("Dummy Text One"))),
      MultiTypeProperty("esteettomyys_liikuntarajoitteiselleTest", "long_text", Seq(MultiTypePropertyValue("Long Dummy Text!!!!!!!!!!!!!!!!!!")))
    ))
  val multiTypePropSeq2 =MultiAssetValue(
    Seq(
      MultiTypeProperty("nimi_suomeksiTest", "text", Seq(MultiTypePropertyValue("Dummy Text Two"))),
      MultiTypeProperty("esteettomyys_liikuntarajoitteiselleTest", "long_text", Seq(MultiTypePropertyValue("Long Dummy Text!!!!!!!!!!!!!!!!!!")))
    ))
  val multiTypePropSeq3 =MultiAssetValue(
    Seq(
      MultiTypeProperty("nimi_suomeksiTest", "text", Seq(MultiTypePropertyValue("Dummy Text Five"))),
      MultiTypeProperty("esteettomyys_liikuntarajoitteiselleTest", "long_text", Seq(MultiTypePropertyValue("Long Dummy Text!!!!!!!!!!!!!!!!!!")))
    ))
  val multiTypePropSeq4 =MultiAssetValue(
    Seq(
      MultiTypeProperty("mittarajoitus", "number", Seq(MultiTypePropertyValue("1000")))
    ))

  val propertyData = MultiValue(multiTypePropSeq)
  val propertyData1 = MultiValue(multiTypePropSeq1)
  val propertyData2 = MultiValue(multiTypePropSeq2)
  val propertyData3 = MultiValue(multiTypePropSeq3)
  val propertyData4 = MultiValue(multiTypePropSeq4)

  object PassThroughService extends LinearAssetOperations {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: OracleLinearAssetDao = mockLinearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def vvhClient: VVHClient = mockVVHClient
    override def polygonTools: PolygonTools = mockPolygonTools
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao
    override def assetDao: OracleAssetDao = mockAssetDao
    def multiValueLinearAssetDao: MultiValueLinearAssetDao = mockMultiValueLinearAssetDao

    override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
  }

  object ServiceWithDao extends MultiValueLinearAssetService(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: OracleLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def vvhClient: VVHClient = mockVVHClient
    override def polygonTools: PolygonTools = mockPolygonTools
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao
    override def assetDao: OracleAssetDao = mockAssetDao
    override def multiValueLinearAssetDao: MultiValueLinearAssetDao = mVLinearAssetDao

    override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
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
      val asset = mVLinearAssetDao.fetchMultiValueLinearAssetsByIds(30, Set(newAssets.head)).head
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
      val asset = mVLinearAssetDao.fetchMultiValueLinearAssetsByIds(30, Set(newAssets.head)).head
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

      val createdId = ServiceWithDao.separate(assetId, Some(propertyData2), Some(propertyData3), "unittest", (i) => Unit)
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

      val createdId = ServiceWithDao.separate(assetId, None, Some(propertyData3), "unittest", (i) => Unit).filter(_ != assetId).head
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

      val newAssetIdAfterUpdate = ServiceWithDao.separate(assetId, Some(propertyData2), None, "unittest", (i) => Unit)
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

      val ids = ServiceWithDao.split(assetId, 2.0, Some(propertyData2), Some(propertyData3), "unittest", (i) => Unit)

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
    def failingMunicipalityValidation(code: Int): Unit = { throw new IllegalArgumentException }
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
    when(mockMunicipalityDao.getMunicipalityNameByCode(235)).thenReturn("Kauniainen")
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

    when(mockMunicipalityDao.getMunicipalityNameByCode(91)).thenReturn("Helsinki")
    when(mockMunicipalityDao.getMunicipalityNameByCode(92)).thenReturn("Vantaa")
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
}

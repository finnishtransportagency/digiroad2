package fi.liikennevirasto.digiroad2.util

import java.text.SimpleDateFormat

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.{AnimalWarningsType, _}
import fi.liikennevirasto.digiroad2.client.tierekisteri._
import fi.liikennevirasto.digiroad2.client.tierekisteri.importer._
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, VVHClient, VVHRoadLinkClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MunicipalityDao, OracleAssetDao, RoadAddressTEMP, RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.dao.linearasset.{OracleLinearAssetDao, OracleSpeedLimitDao}
import fi.liikennevirasto.digiroad2.linearasset.{DynamicAssetValue, DynamicValue, NumericValue, TextualValue}
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.service.linearasset.LinearAssetTypes
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class TierekisteriImporterOperationsSpec extends FunSuite with Matchers  {

  val mockAssetDao: OracleAssetDao = MockitoSugar.mock[OracleAssetDao]
  val mockRoadAddressService = MockitoSugar.mock[RoadAddressService]
  val mockMunicipalityDao: MunicipalityDao = MockitoSugar.mock[MunicipalityDao]
  val mockTRClient: TierekisteriLightingAssetClient = MockitoSugar.mock[TierekisteriLightingAssetClient]
  val mockTRTrafficSignsLimitClient: TierekisteriTrafficSignAssetClient = MockitoSugar.mock[TierekisteriTrafficSignAssetClient]
  val mockTRTrafficSignsLimitSpeedLimitClient: TierekisteriTrafficSignAssetSpeedLimitClient = MockitoSugar.mock[TierekisteriTrafficSignAssetSpeedLimitClient]
  val mockRoadLinkService: RoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient: VVHClient = MockitoSugar.mock[VVHClient]
  val mockVVHRoadLinkClient: VVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val linearAssetDao = new OracleLinearAssetDao(mockVVHClient, mockRoadLinkService)
  val speedLimitDao = new OracleSpeedLimitDao(mockVVHClient, mockRoadLinkService)
  val oracleAssetDao = new OracleAssetDao
  val mockTRPavedRoadClient: TierekisteriPavedRoadAssetClient = MockitoSugar.mock[TierekisteriPavedRoadAssetClient]
  val mockMassTransitLaneClient: TierekisteriMassTransitLaneAssetClient = MockitoSugar.mock[TierekisteriMassTransitLaneAssetClient]
  val mockTRDamageByThawClient: TierekisteriDamagedByThawAssetClient = MockitoSugar.mock[TierekisteriDamagedByThawAssetClient]
  val mockTREuropeanRoadClient: TierekisteriEuropeanRoadAssetClient = MockitoSugar.mock[TierekisteriEuropeanRoadAssetClient]
  val mockTRSpeedLimitAssetClient: TierekisteriSpeedLimitAssetClient = MockitoSugar.mock[TierekisteriSpeedLimitAssetClient]
  val mockTierekisteriAssetDataClient: TierekisteriAssetDataClient = MockitoSugar.mock[TierekisteriAssetDataClient]
  val dynamicDao = new DynamicLinearAssetDao
  val mockGreenCareClassAssetClient: TierekisteriGreenCareClassAssetClient = MockitoSugar.mock[TierekisteriGreenCareClassAssetClient]
  val mockWinterCareClassAssetClient: TierekisteriWinterCareClassAssetClient = MockitoSugar.mock[TierekisteriWinterCareClassAssetClient]
  val mockTRCarryingCapacityClient: TierekisteriCarryingCapacityAssetClient = MockitoSugar.mock[TierekisteriCarryingCapacityAssetClient]
  val mockTRAnimalWarningsClient: TierekisteriAnimalWarningsAssetClient = MockitoSugar.mock[TierekisteriAnimalWarningsAssetClient]

  lazy val roadWidthImporterOperations: RoadWidthTierekisteriImporter = {
    new RoadWidthTierekisteriImporter()
  }

  lazy val litRoadImporterOperations: LitRoadTierekisteriImporter = {
    new LitRoadTierekisteriImporter()
  }

  lazy val tierekisteriAssetImporterOperations: TestTierekisteriAssetImporterOperations = {
    new TestTierekisteriAssetImporterOperations
  }

  lazy val speedLimitsTierekisteriImporter: TestSpeedLimitsTierekisteriImporter = {
    new TestSpeedLimitsTierekisteriImporter
  }

  lazy val speedLimitAssetTierekisteriImporter: TestSpeedLimitAssetOperations ={
    new TestSpeedLimitAssetOperations
  }

  class TestTierekisteriAssetImporterOperations extends TierekisteriAssetImporterOperations {
    override def typeId: Int = 999
    override def withDynSession[T](f: => T): T = f
    override def withDynTransaction[T](f: => T): T = f
    override def assetName: String = "assetTest"
    override type TierekisteriClientType = TierekisteriAssetDataClient
    override lazy val assetDao: OracleAssetDao = mockAssetDao
    override lazy val municipalityDao: MunicipalityDao = mockMunicipalityDao
    override lazy val roadAddressService: RoadAddressService = mockRoadAddressService
    override val tierekisteriClient: TierekisteriLightingAssetClient = mockTRClient
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient

    //Creating this new methods because is protected visibility on the trait
    def getRoadAddressSectionsTest(trAsset: TierekisteriAssetData): Seq[(AddressSection, TierekisteriAssetData)] = super.getRoadAddressSections(trAsset)
    def expireAssetsTest(linkIds: Seq[Long]): Unit = super.expireAssets(linkIds)
    def calculateStartLrmByAddressTest(startAddress: ViiteRoadAddress, section: AddressSection): Option[Double] = super.calculateStartLrmByAddress(startAddress, section)
    def calculateEndLrmByAddressTest(endAddress: ViiteRoadAddress, section: AddressSection): Option[Double] = super.calculateEndLrmByAddress(endAddress, section)

    def getAllTierekisteriAddressSectionsTest(roadNumber: Long) = super.getAllTierekisteriAddressSections(roadNumber: Long)
    def getAllTierekisteriAddressSectionsTest(roadNumber: Long, roadPart: Long) = super.getAllTierekisteriAddressSections(roadNumber: Long)
    def getAllTierekisteriHistoryAddressSectionTest(roadNumber: Long, lastExecution: DateTime) = super.getAllTierekisteriHistoryAddressSection(roadNumber: Long, lastExecution: DateTime)

    override protected def createAsset(section: AddressSection, trAssetData: TierekisteriAssetData, existingRoadAddresses: Map[(Long, Long, Track), Seq[ViiteRoadAddress]], mappedRoadLinks: Seq[VVHRoadlink], historyMappedRoadLinks: Seq[RoadAddressTEMP]): Unit = {
    override protected def createAsset(section: AddressSection, trAssetData: TierekisteriAssetData, existingRoadAddresses: Map[(Long, Long, Track), Seq[ViiteRoadAddress]], mappedRoadLinks: Seq[VVHRoadlink]): Unit = {

    }
  }

  class TestSpeedLimitsTierekisteriImporter extends StateSpeedLimitTierekisteriImporter {
    override lazy val assetDao: OracleAssetDao = mockAssetDao
    override lazy val municipalityDao: MunicipalityDao = mockMunicipalityDao
    override lazy val roadAddressService: RoadAddressService = mockRoadAddressService
    override val tierekisteriClient = mockTRTrafficSignsLimitSpeedLimitClient
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
    override def withDynTransaction[T](f: => T): T = f

    def testSplitRoadAddressSectionBySigns(trAssets: Seq[TierekisteriAssetData], ra: ViiteRoadAddress, roadSide: RoadSide): Seq[(AddressSection, Option[TierekisteriAssetData])] = {
      super.splitRoadAddressSectionBySigns(trAssets, ra, roadSide)
    }

    def calculateMeasuresTest(roadAddress: ViiteRoadAddress, section: AddressSection) = super.calculateMeasures(roadAddress, section)

    def createSpeedLimitTest(roadAddress: ViiteRoadAddress, addressSection: AddressSection,
                             trAssetOption: Option[TrAssetInfo], roadLinkOption: Option[VVHRoadlink]) = super.createSpeedLimit(roadAddress, addressSection, trAssetOption, roadLinkOption, None)

    override def createUrbanTrafficSign(roadLink: Option[VVHRoadlink], trUrbanAreaAssets: Seq[TierekisteriUrbanAreaData],
                                        addressSection: AddressSection, roadAddress: ViiteRoadAddress,
                                        roadSide: RoadSide): Option[TrAssetInfo] = super.createUrbanTrafficSign(roadLink, trUrbanAreaAssets, addressSection, roadAddress, roadSide)

    def generateOneSideSpeedLimitsTest(roadNumber: Long, roadSide: RoadSide, trAssets : Seq[TierekisteriAssetData], trUrbanAreaAssets: Seq[TierekisteriUrbanAreaData], existingRoadAddresses: Seq[ViiteRoadAddress])
    = super.generateOneSideSpeedLimits(roadNumber: Long, roadSide: RoadSide, trAssets, trUrbanAreaAssets, existingRoadAddresses)
  }

  class TestLitRoadOperations extends LitRoadTierekisteriImporter {
    override lazy val assetDao: OracleAssetDao = mockAssetDao
    override lazy val municipalityDao: MunicipalityDao = mockMunicipalityDao
    override lazy val roadAddressService: RoadAddressService = mockRoadAddressService
    override val tierekisteriClient: TierekisteriLightingAssetClient = mockTRClient
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
    override def withDynTransaction[T](f: => T): T = f
  }

  class TestPavedRoadOperations extends PavedRoadTierekisteriImporter {
    override lazy val assetDao: OracleAssetDao = mockAssetDao
    override lazy val municipalityDao: MunicipalityDao = mockMunicipalityDao
    override lazy val roadAddressService: RoadAddressService = mockRoadAddressService
    override val tierekisteriClient: TierekisteriPavedRoadAssetClient = mockTRPavedRoadClient
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
    override def withDynTransaction[T](f: => T): T = f

    def filterTierekisteriAssetsTest(tierekisteriAssetData: TierekisteriAssetData) = super.filterTierekisteriAssets(tierekisteriAssetData)
  }

  class TestTierekisteriAssetImporterOperationsFilterAll extends TestTierekisteriAssetImporterOperations {
      protected override def filterTierekisteriAssets(tierekisteriAssetData: TierekisteriAssetData) : Boolean = {
        false
      }
  }

  class TestMassTransitLaneOperations extends MassTransitLaneTierekisteriImporter {

    override lazy val assetDao: OracleAssetDao = mockAssetDao
    override lazy val municipalityDao: MunicipalityDao = mockMunicipalityDao
    override lazy val roadAddressService: RoadAddressService = mockRoadAddressService
    override val tierekisteriClient: TierekisteriMassTransitLaneAssetClient = mockMassTransitLaneClient
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
    override def withDynTransaction[T](f: => T): T = f

    def filterTierekisteriAssetsTest(tierekisteriAssetData: TierekisteriAssetData) = super.filterTierekisteriAssets(tierekisteriAssetData)
  }

  class TestDamageByThawOperations extends DamagedByThawTierekisteriImporter {
    override lazy val assetDao: OracleAssetDao = mockAssetDao
    override lazy val municipalityDao: MunicipalityDao = mockMunicipalityDao
    override lazy val roadAddressService: RoadAddressService = mockRoadAddressService
    override val tierekisteriClient: TierekisteriDamagedByThawAssetClient = mockTRDamageByThawClient
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
    override def withDynTransaction[T](f: => T): T = f
  }

  class TestEuropeanRoadOperations extends EuropeanRoadTierekisteriImporter {
    override lazy val assetDao: OracleAssetDao = mockAssetDao
    override lazy val municipalityDao: MunicipalityDao = mockMunicipalityDao
    override lazy val roadAddressService: RoadAddressService = mockRoadAddressService
    override val tierekisteriClient: TierekisteriEuropeanRoadAssetClient = mockTREuropeanRoadClient
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
    override def withDynTransaction[T](f: => T): T = f
  }

  class TestSpeedLimitAssetOperations extends SpeedLimitTierekisteriImporter {
    override lazy val assetDao: OracleAssetDao = mockAssetDao
    override lazy val municipalityDao: MunicipalityDao = mockMunicipalityDao
    override lazy val roadAddressService: RoadAddressService = mockRoadAddressService
    override val tierekisteriClient: TierekisteriSpeedLimitAssetClient = mockTRSpeedLimitAssetClient
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
    override def withDynTransaction[T](f: => T): T = f
  }

  class TestCareClassOperations extends CareClassTierekisteriImporter {
    override lazy val assetDao: OracleAssetDao = mockAssetDao
    override lazy val municipalityDao: MunicipalityDao = mockMunicipalityDao
    override lazy val roadAddressService: RoadAddressService = mockRoadAddressService
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val greenCareTierekisteriClient: TierekisteriGreenCareClassAssetClient = mockGreenCareClassAssetClient
    override lazy val winterCareTierekisteriClient: TierekisteriWinterCareClassAssetClient = mockWinterCareClassAssetClient
    override lazy val vvhClient: VVHClient = mockVVHClient
    override def withDynTransaction[T](f: => T): T = f
  }

  class TestCarryingCapacityOperations extends CarryingCapacityTierekisteriImporter {
    override lazy val assetDao: OracleAssetDao = mockAssetDao
    override lazy val municipalityDao: MunicipalityDao = mockMunicipalityDao
    override lazy val roadAddressService: RoadAddressService = mockRoadAddressService
    override val tierekisteriClient: TierekisteriCarryingCapacityAssetClient = mockTRCarryingCapacityClient
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
    override def withDynTransaction[T](f: => T): T = f
  }

  class TestTrafficSignTierekisteriImporter extends TrafficSignTierekisteriImporter {
    override lazy val assetDao: OracleAssetDao = mockAssetDao
    override lazy val municipalityDao: MunicipalityDao = mockMunicipalityDao
    override lazy val roadAddressService: RoadAddressService = mockRoadAddressService
    override val tierekisteriClient: TierekisteriTrafficSignAssetClient = mockTRTrafficSignsLimitClient
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
    override def withDynTransaction[T](f: => T): T = f
  }

  case class TierekisteriAssetDataTest (roadNumber: Long, startRoadPartNumber: Long, endRoadPartNumber: Long, startAddressMValue: Long,
                                        endAddressMValue: Long, track: Track) extends TierekisteriAssetData

  class TestPointAssetTierekisteriImporterOperations extends PointAssetTierekisteriImporterOperations {
    override def typeId: Int = 999
    override def withDynSession[T](f: => T): T = f
    override def withDynTransaction[T](f: => T): T = f
    override def assetName: String = "assetTest"
    override type TierekisteriClientType = TierekisteriAssetDataClient
    override lazy val assetDao: OracleAssetDao = mockAssetDao
    override lazy val municipalityDao: MunicipalityDao = mockMunicipalityDao
    override lazy val roadAddressService: RoadAddressService = mockRoadAddressService
    override val tierekisteriClient: TierekisteriAssetDataClient = mockTierekisteriAssetDataClient
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient

    var createObject: Seq[(ViiteRoadAddress, VVHRoadlink, Double, TierekisteriAssetData)] = Seq()

    override def createPointAsset(roadAddress: ViiteRoadAddress, vvhRoadlink: VVHRoadlink, mValue: Double, trAssetData: TierekisteriAssetData): Unit = {
      createObject = List.concat(createObject , Seq((roadAddress, vvhRoadlink, mValue, trAssetData)))
    }

    def getCreatedValues: Seq[(ViiteRoadAddress, VVHRoadlink, Double, TierekisteriAssetData)] = {
      createObject
    }

    def createAssetTest(section: AddressSection, trAssetData: TierekisteriAssetData, existingRoadAddresses: Map[(Long, Long, Track), Seq[ViiteRoadAddress]], mappedRoadLinks: Seq[VVHRoadlink]) =
      super.createAsset(section: AddressSection, trAssetData: TierekisteriAssetData, existingRoadAddresses: Map[(Long, Long, Track), Seq[ViiteRoadAddress]], mappedRoadLinks: Seq[VVHRoadlink])
  }

  class TestAnimalWarningsOperations extends AnimalWarningsTierekisteriImporter {
    override lazy val assetDao: OracleAssetDao = mockAssetDao
    override lazy val municipalityDao: MunicipalityDao = mockMunicipalityDao
    override lazy val roadAddressService: RoadAddressService = mockRoadAddressService
    override val tierekisteriClient: TierekisteriAnimalWarningsAssetClient = mockTRAnimalWarningsClient
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
    override def withDynTransaction[T](f: => T): T = f
  }

  test("test createPoint main method") {
    val pointImporterOperations = new TestPointAssetTierekisteriImporterOperations()

    val roadNumber: Int = 4
    val startRoadPartNumber: Int = 203
    val startAddressMValue: Int = 3184
    val endAddressMValue: Int = 3400
    val track: Track = Track.RightSide

    val trAssetData = TierekisteriAssetDataTest(roadNumber, startRoadPartNumber, startRoadPartNumber, startAddressMValue, startAddressMValue, Track.RightSide)
    val section = AddressSection(roadNumber, startRoadPartNumber, Track.RightSide, startAddressMValue, Some(endAddressMValue))
    val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)
    val roadAddress = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001, 1, 11, SideCode.TowardsDigitizing, Seq(), false, None, None, None)

    when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]], any[Boolean])).thenReturn(Seq(vvhRoadLink))

    pointImporterOperations.createAssetTest(section, trAssetData.asInstanceOf[pointImporterOperations.TierekisteriAssetData], Seq(roadAddress).groupBy(ra => (ra.roadNumber, ra.roadPartNumber, ra.track)), Seq(vvhRoadLink))

    pointImporterOperations.getCreatedValues.foreach { case (viiteRoadAddress, roadLink, mValue, tierekisteriAssetData ) =>
      viiteRoadAddress should be (roadAddress)
      roadLink should be (vvhRoadLink)
      mValue should be (1)
      tierekisteriAssetData should be (trAssetData)
    }
  }

  test("assets splited are split properly") {
    val trl = TierekisteriLightingData(4L, 203L, 208L, Track.RightSide, 3184L, 6584L)
    val sections = tierekisteriAssetImporterOperations.getRoadAddressSectionsTest(trl).map(_._1)
    sections.size should be (6)
    sections.head should be (AddressSection(4L, 203L, Track.RightSide, 3184L, None))
    sections.last should be (AddressSection(4L, 208L, Track.RightSide,  0L, Some(6584L)))
    val middleParts = sections.filterNot(s => s.roadPartNumber==203L || s.roadPartNumber==208L)
    middleParts.forall(s => s.track == Track.RightSide) should be (true)
    middleParts.forall(s => s.startAddressMValue == 0L) should be (true)
    middleParts.forall(s => s.endAddressMValue.isEmpty) should be (true)
  }

  test("assets split works on single part") {
    val trl = TierekisteriLightingData(4L, 203L, 203L, Track.RightSide, 3184L, 6584L)
    val sections = tierekisteriAssetImporterOperations.getRoadAddressSectionsTest(trl).map(_._1)
    sections.size should be (1)
    sections.head should be (AddressSection(4L, 203L, Track.RightSide, 3184L, Some(6584L)))
  }

  test("assets split works on two parts") {
    val trl = TierekisteriLightingData(4L, 203L, 204L, Track.RightSide, 3184L, 6584L)
    val sections = tierekisteriAssetImporterOperations.getRoadAddressSectionsTest(trl).map(_._1)
    sections.size should be (2)
    sections.head should be (AddressSection(4L, 203L, Track.RightSide, 3184L, None))
    sections.last should be (AddressSection(4L, 204L, Track.RightSide, 0L, Some(6584L)))
  }

  test("assets get tierekisteri data with address section") {
    val assetValue = 10
    val trl = TierekisteriLightingData(4L, 203L, 203L, Track.RightSide, 3184L, 6584L)
    val sectionTrAssetPair = tierekisteriAssetImporterOperations.getRoadAddressSectionsTest(trl)
    sectionTrAssetPair.size should be (1)
    sectionTrAssetPair.head should be ((AddressSection(4L, 203L, Track.RightSide, 3184L, Some(6584L)), trl))
  }

  test("calculate lrm position from road address when section inside road address and is towards digitizing") {
    val startAddressMValue = 0
    val endAddressMValue = 100
    val startMValue = 0
    val endMValue = 10
    val roadAddress = ViiteRoadAddress(1L, 100, 1, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001, 1, 11, SideCode.TowardsDigitizing, Seq(), false, None, None, None)
    val addressSection = AddressSection(1, 1, Track.Combined, 50, Some(100))
    val resultStartMValue = tierekisteriAssetImporterOperations.calculateStartLrmByAddressTest(roadAddress, addressSection)
    val resultEndMValue = tierekisteriAssetImporterOperations.calculateEndLrmByAddressTest(roadAddress, addressSection)

    resultStartMValue should be (Some(6.0))
    resultEndMValue should be (Some(11.0))
  }

  test("calculate lrm position from road address when section inside road address and is against digitizing") {
    val startAddressMValue = 0
    val endAddressMValue = 100
    val startMValue = 0
    val endMValue = 10
    val roadAddress = ViiteRoadAddress(1L, 100, 1, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001, 1, 11, SideCode.AgainstDigitizing, Seq(), false, None, None, None)
    val addressSection = AddressSection(1, 1, Track.Combined, 50, Some(100))
    val resultStartMValue = tierekisteriAssetImporterOperations.calculateStartLrmByAddressTest(roadAddress, addressSection)
    val resultEndMValue = tierekisteriAssetImporterOperations.calculateEndLrmByAddressTest(roadAddress, addressSection)

    resultEndMValue should be (Some(1.0))
    resultStartMValue should be (Some(6.0))
  }

  test("calculate lrm position from road address when section starts outside the road address and is towards digitizing") {
    val startAddressMValue = 100
    val endAddressMValue = 200
    val startMValue = 0
    val endMValue = 10
    val roadAddress = ViiteRoadAddress(1L, 100, 1, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001, 1, 11, SideCode.TowardsDigitizing, Seq(), false, None, None, None)
    val addressSection = AddressSection(1, 1, Track.Combined, 50, Some(150))
    val resultStartMValue = tierekisteriAssetImporterOperations.calculateStartLrmByAddressTest(roadAddress, addressSection)
    val resultEndMValue = tierekisteriAssetImporterOperations.calculateEndLrmByAddressTest(roadAddress, addressSection)

    resultStartMValue should be (Some(1.0))
    resultEndMValue should be (Some(6.0))
  }

  test("calculate lrm position from road address when section ends outside the road address and is towards digitizing") {
    val startAddressMValue = 100
    val endAddressMValue = 200
    val startMValue = 0
    val endMValue = 10
    val roadAddress = ViiteRoadAddress(1L, 100, 1, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001, 1, 11, SideCode.TowardsDigitizing, Seq(), false, None, None, None)
    val addressSection = AddressSection(1, 1, Track.Combined, 150, Some(250))
    val resultStartMValue = tierekisteriAssetImporterOperations.calculateStartLrmByAddressTest(roadAddress, addressSection)
    val resultEndMValue = tierekisteriAssetImporterOperations.calculateEndLrmByAddressTest(roadAddress, addressSection)

    resultStartMValue should be (Some(6.0))
    resultEndMValue should be (Some(11.0))
  }

  test("calculate lrm position from road address when section starts outside the road address and is against digitizing") {
    val startAddressMValue = 100
    val endAddressMValue = 200
    val startMValue = 0
    val endMValue = 10
    val roadAddress = ViiteRoadAddress(1L, 100, 1, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001, 1, 11, SideCode.AgainstDigitizing, Seq(), false, None, None, None)
    val addressSection = AddressSection(1, 1, Track.Combined, 50, Some(150))
    val resultStartMValue = tierekisteriAssetImporterOperations.calculateStartLrmByAddressTest(roadAddress, addressSection)
    val resultEndMValue = tierekisteriAssetImporterOperations.calculateEndLrmByAddressTest(roadAddress, addressSection)

    resultStartMValue should be (Some(11.0))
    resultEndMValue should be (Some(6.0))
  }

  test("calculate lrm position from road address when section ends outside the road address and is against digitizing") {
    val startAddressMValue = 100
    val endAddressMValue = 200
    val startMValue = 0
    val endMValue = 10
    val roadAddress = ViiteRoadAddress(1L, 100, 1, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001, 1, 11, SideCode.AgainstDigitizing, Seq(), false, None, None, None)
    val addressSection = AddressSection(1, 1, Track.Combined, 150, Some(250))
    val resultStartMValue = tierekisteriAssetImporterOperations.calculateStartLrmByAddressTest(roadAddress, addressSection)
    val resultEndMValue = tierekisteriAssetImporterOperations.calculateEndLrmByAddressTest(roadAddress, addressSection)

    resultStartMValue should be (Some(6.0))
    resultEndMValue should be (Some(1.0))
  }

  test("calculate lrm position from road address when section starts and ends outside the road address and is towards digitizing") {
    val startAddressMValue = 100
    val endAddressMValue = 200
    val startMValue = 0
    val endMValue = 10
    val roadAddress = ViiteRoadAddress(1L, 100, 1, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001, 1, 11, SideCode.TowardsDigitizing, Seq(), false, None, None, None)
    val addressSection = AddressSection(1, 1, Track.Combined, 50, Some(250))
    val resultStartMValue = tierekisteriAssetImporterOperations.calculateStartLrmByAddressTest(roadAddress, addressSection)
    val resultEndMValue = tierekisteriAssetImporterOperations.calculateEndLrmByAddressTest(roadAddress, addressSection)

    resultStartMValue should be (Some(1.0))
    resultEndMValue should be (Some(11.0))
  }

  test("calculate lrm position from road address when section starts and ends outside the road address and is against digitizing") {
    val startAddressMValue = 100
    val endAddressMValue = 200
    val startMValue = 0
    val endMValue = 10
    val roadAddress = ViiteRoadAddress(1L, 100, 1, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001, 1, 11, SideCode.AgainstDigitizing, Seq(), false, None, None, None)
    val addressSection = AddressSection(1, 1, Track.Combined, 50, Some(250))
    val resultStartMValue = tierekisteriAssetImporterOperations.calculateStartLrmByAddressTest(roadAddress, addressSection)
    val resultEndMValue = tierekisteriAssetImporterOperations.calculateEndLrmByAddressTest(roadAddress, addressSection)

    resultStartMValue should be (Some(11.0))
    resultEndMValue should be (Some(1.0))
  }

  test("calculate measures, towards digitizing"){
    TestTransactions.runWithRollback() {

      val testLitRoad = new TestLitRoadOperations
      val roadNumber = 4L
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 200L
      val startAddressMValue = 0L
      val endAddressMValue = 500L

      val starAddress = 0
      val endAddress = 500

      val startSection = 50
      val endSection = 350

      val tr = TierekisteriLightingData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startSection, endSection)
      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001, starAddress, endAddress, SideCode.TowardsDigitizing, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

      when(mockMunicipalityDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressService.getAllRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockTRClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockRoadAddressService.getAllByRoadNumber(any[Long])).thenReturn(Seq(ra))
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]], any[Boolean])).thenReturn(Seq(vvhRoadLink))

      testLitRoad.importAssets()
      val asset = linearAssetDao.fetchLinearAssetsByLinkIds(testLitRoad.typeId, Seq(5001), LinearAssetTypes.numericValuePropertyId).head

      asset.linkId should be (5001)
      asset.value should be (Some(NumericValue(1)))
      asset.startMeasure should be (50)
      asset.endMeasure should be (350)
      asset.sideCode should be (1)
    }
  }

  test("calculate measures, with 2 sections"){
    TestTransactions.runWithRollback() {

      val testLitRoad = new TestLitRoadOperations
      val roadNumber = 4L
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 201L
      val startMValueSection1 = 0L
      val endMValueSection1 = 500L

      val startMValueSection2 = 0L
      val endMValueSection2 = 1000L

      val starAddressSection1 = 50
      val endAddressSection1 = 500

      val starAddressSection2 = 500
      val endAddressSection2= 750

      val startSection = 0
      val endSection = 1000

      val raS1 = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, startMValueSection1, endMValueSection1, None, None, 5001, starAddressSection1, endAddressSection1, SideCode.TowardsDigitizing, Seq(), false, None, None, None)
      val raS2 = ViiteRoadAddress(1L, roadNumber, endRoadPartNumber, Track.RightSide, startMValueSection2, endMValueSection2, None, None, 5001, starAddressSection2, endAddressSection2, SideCode.TowardsDigitizing, Seq(), false, None, None, None)

      val tr = TierekisteriLightingData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startSection, endSection)
      val vvhRoadLink = VVHRoadlink(5001, 235, List(Point(0.0, 0.0), Point(0.0, 1000.0)), State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

      when(mockMunicipalityDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressService.getAllRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockTRClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockRoadAddressService.getAllByRoadNumber(any[Long])).thenReturn(Seq(raS1, raS2))
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]], any[Boolean])).thenReturn(Seq(vvhRoadLink))

      testLitRoad.importAssets()
      val asset = linearAssetDao.fetchLinearAssetsByLinkIds(testLitRoad.typeId, Seq(5001), LinearAssetTypes.numericValuePropertyId)

      asset.length should be (2)
      asset.count(a => a.startMeasure == 50) should be (1)
      asset.count(a => a.endMeasure == 500) should be (1)
      asset.count(a => a.startMeasure == 500) should be (1)
      asset.count(a => a.endMeasure == 750) should be (1)
    }
  }

  test("import assets (litRoad) from TR to OTH") {
     TestTransactions.runWithRollback() {

       val testLitRoad = new TestLitRoadOperations
       val roadNumber = 4L
       val startRoadPartNumber = 200L
       val endRoadPartNumber = 200L
       val startAddressMValue = 0L
       val endAddressMValue = 250L

       val tr = TierekisteriLightingData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue)
       val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001, 1.5, 11.4, SideCode.TowardsDigitizing, Seq(), false, None, None, None)
       val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

      when(mockMunicipalityDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressService.getAllRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockTRClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockRoadAddressService.getAllByRoadNumber(any[Long])).thenReturn(Seq(ra))
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]], any[Boolean])).thenReturn(Seq(vvhRoadLink))

       testLitRoad.importAssets()
       val asset = linearAssetDao.fetchLinearAssetsByLinkIds(testLitRoad.typeId, Seq(5001), LinearAssetTypes.numericValuePropertyId).head

       asset.linkId should be(5001)
       asset.value should be(Some(NumericValue(1)))
     }
   }

  test("update assets (litRoad) from TR to OTH"){
    TestTransactions.runWithRollback() {

      val testLitRoad = new TestLitRoadOperations
      val roadNumber = 4L
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 200L
      val startAddressMValue = 0L
      val endAddressMValue = 250L

      val starAddress = 0
      val endAddress = 500

      val startSection = 50
      val endSection = 150

      val startSectionHist = 100
      val endSectionHist = 150

      val tr = TierekisteriLightingData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startSection, endSection)
      val trHist = TierekisteriLightingData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startSectionHist, endSectionHist)

      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001,starAddress, endAddress, SideCode.TowardsDigitizing, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

      when(mockMunicipalityDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressService.getAllRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockTRClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockTRClient.fetchHistoryAssetData(any[Long], any[Option[DateTime]])).thenReturn(Seq(trHist))
      when(mockTRClient.fetchActiveAssetData(any[Long], any[Long])).thenReturn(Seq(trHist))
      when(mockRoadAddressService.getAllByRoadNumber(any[Long])).thenReturn(Seq(ra))
      when(mockRoadAddressService.getAllByRoadNumberAndParts(any[Long], any[Seq[Long]])).thenReturn(Seq(ra))

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]], any[Boolean])).thenReturn(Seq(vvhRoadLink))

      testLitRoad.importAssets()
      val assetI = linearAssetDao.fetchLinearAssetsByLinkIds(testLitRoad.typeId, Seq(5001), LinearAssetTypes.numericValuePropertyId).head

      testLitRoad.updateAssets(DateTime.now())
      val assetU = linearAssetDao.fetchLinearAssetsByLinkIds(testLitRoad.typeId, Seq(5001), LinearAssetTypes.numericValuePropertyId).filterNot(a => a.id == assetI.id).head

      assetU.startMeasure should not be assetI.startMeasure
      assetU.endMeasure should be (assetI.endMeasure)
    }
  }

  test("import assets (massTransitLane) from TR to OTH"){
    TestTransactions.runWithRollback() {

      val testMassTransitLane = new TestMassTransitLaneOperations
      val roadNumber = 4L
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 200L
      val startAddressMValue = 0L
      val endAddressMValue = 250L
      val trLaneType = TRLaneArrangementType.apply(5)

      val tr = TierekisteriMassTransitLaneData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, trLaneType, RoadSide.Left)
      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001, 1.5, 11.4, SideCode.TowardsDigitizing, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

      when(mockMunicipalityDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressService.getAllRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockMassTransitLaneClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockRoadAddressService.getAllByRoadNumber(any[Long])).thenReturn(Seq(ra))
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]], any[Boolean])).thenReturn(Seq(vvhRoadLink))

      testMassTransitLane.importAssets()
      val asset = linearAssetDao.fetchLinearAssetsByLinkIds(testMassTransitLane.typeId, Seq(5001), LinearAssetTypes.numericValuePropertyId).head

      asset.linkId should be (5001)
      asset.value should be (Some(NumericValue(1)))
      asset.sideCode should be (SideCode.TowardsDigitizing.value)
    }
  }

  test("update assets (massTransitLane) from TR to OTH"){
    TestTransactions.runWithRollback() {

      val testMassTransitLane = new TestMassTransitLaneOperations
      val roadNumber = 4L
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 200L
      val startAddressMValue = 0L
      val endAddressMValue = 250L
      val laneType = TRLaneArrangementType.apply(5)

      val starAddress = 0
      val endAddress = 500

      val startSection = 50
      val endSection = 150

      val starSectionHist = 100
      val endSectionHist = 150

      val tr = TierekisteriMassTransitLaneData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startSection, endSection, laneType, RoadSide.Left)
      val trHist = TierekisteriMassTransitLaneData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, starSectionHist, endSectionHist, laneType, RoadSide.Left)

      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001,starAddress, endAddress, SideCode.TowardsDigitizing, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

      when(mockMunicipalityDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressService.getAllRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockMassTransitLaneClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockMassTransitLaneClient.fetchHistoryAssetData(any[Long], any[Option[DateTime]])).thenReturn(Seq(trHist))
      when(mockMassTransitLaneClient.fetchActiveAssetData(any[Long], any[Long])).thenReturn(Seq(trHist))

      when(mockRoadAddressService.getAllByRoadNumber(any[Long])).thenReturn(Seq(ra))
      when(mockRoadAddressService.getAllByRoadNumberAndParts(any[Long], any[Seq[Long]])).thenReturn(Seq(ra))

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]], any[Boolean])).thenReturn(Seq(vvhRoadLink))

      testMassTransitLane.importAssets()
      val assetI = linearAssetDao.fetchLinearAssetsByLinkIds(testMassTransitLane.typeId, Seq(5001), LinearAssetTypes.numericValuePropertyId).head

      testMassTransitLane.updateAssets(DateTime.now())
      val assetU = linearAssetDao.fetchLinearAssetsByLinkIds(testMassTransitLane.typeId, Seq(5001), LinearAssetTypes.numericValuePropertyId).filterNot(a => a.id == assetI.id).head

      assetU.startMeasure should not be assetI.startMeasure
      assetU.endMeasure should be (assetI.endMeasure)
    }
  }

  test("import assets (pavedRoad) from TR to OTH"){
    TestTransactions.runWithRollback() {

      val testPavedRoad = new TestPavedRoadOperations
      val roadNumber = 4L
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 200L
      val startAddressMValue = 0L
      val endAddressMValue = 250L

      val tr = TierekisteriPavedRoadData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, PavementClass.Cobblestone)
      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001, 1.5, 11.4, SideCode.TowardsDigitizing, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

      when(mockMunicipalityDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressService.getAllRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockTRPavedRoadClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockRoadAddressService.getAllByRoadNumber(any[Long])).thenReturn(Seq(ra))
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]], any[Boolean])).thenReturn(Seq(vvhRoadLink))

      testPavedRoad.importAssets()
      val asset = linearAssetDao.fetchLinearAssetsByLinkIds(testPavedRoad.typeId, Seq(5001), LinearAssetTypes.numericValuePropertyId).head

      asset.linkId should be (5001)
      asset.value should be (Some(NumericValue(1)))
    }
  }

  test("update assets (pavedRoad) from TR to OTH"){
    TestTransactions.runWithRollback() {

      val testPavedRoad = new TestPavedRoadOperations
      val roadNumber = 4L
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 200L
      val startAddressMValue = 0L
      val endAddressMValue = 250L

      val starAddress = 0
      val endAddress = 500

      val startSection = 50
      val endSection = 150

      val starSectionHist = 100
      val endSectionHist = 150

      val tr = TierekisteriPavedRoadData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startSection, endSection, PavementClass.Cobblestone)
      val trHist = TierekisteriPavedRoadData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, starSectionHist, endSectionHist, PavementClass.Cobblestone)

      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001,starAddress, endAddress, SideCode.TowardsDigitizing, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

      when(mockMunicipalityDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressService.getAllRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockTRPavedRoadClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockTRPavedRoadClient.fetchHistoryAssetData(any[Long], any[Option[DateTime]])).thenReturn(Seq(trHist))
      when(mockTRPavedRoadClient.fetchActiveAssetData(any[Long], any[Long])).thenReturn(Seq(trHist))
      when(mockRoadAddressService.getAllByRoadNumber(any[Long])).thenReturn(Seq(ra))
      when(mockRoadAddressService.getAllByRoadNumberAndParts(any[Long], any[Seq[Long]])).thenReturn(Seq(ra))

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]], any[Boolean])).thenReturn(Seq(vvhRoadLink))

      testPavedRoad.importAssets()
      val assetI = linearAssetDao.fetchLinearAssetsByLinkIds(testPavedRoad.typeId, Seq(5001), LinearAssetTypes.numericValuePropertyId).head

      testPavedRoad.updateAssets(DateTime.now())
      val assetU = linearAssetDao.fetchLinearAssetsByLinkIds(testPavedRoad.typeId, Seq(5001), LinearAssetTypes.numericValuePropertyId).filterNot(a => a.id == assetI.id).head

      assetU.startMeasure should not be assetI.startMeasure
      assetU.endMeasure should be (assetI.endMeasure)
    }
  }

  test("Should allow all asset\"") {
    TestTransactions.runWithRollback() {

      val testTierekisteriAsset = new TestTierekisteriAssetImporterOperations
      val roadNumber = 4L
      val roadPartNumber = 1L
      val startSection = 50
      val endSection = 350
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 200L
      val date = DateTime.now()

      val tr = Seq(TierekisteriLightingData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startSection, endSection),
        TierekisteriLightingData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startSection, endSection),
        TierekisteriLightingData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.LeftSide, startSection, endSection))

      when(mockTRClient.fetchActiveAssetData(roadNumber)).thenReturn(tr)
      val filterAsset = testTierekisteriAsset.getAllTierekisteriAddressSectionsTest(roadNumber)
      filterAsset.length should be(3)

      when(mockTRClient.fetchActiveAssetData(roadNumber, roadPartNumber)).thenReturn(tr)
      val assetFetch = testTierekisteriAsset.getAllTierekisteriAddressSectionsTest(roadNumber, roadPartNumber)
      assetFetch.length should be(3)

      when(mockTRClient.fetchHistoryAssetData(roadNumber, Some(date))).thenReturn(tr)
      val assetHist = testTierekisteriAsset.getAllTierekisteriHistoryAddressSectionTest(roadNumber, date)
      assetHist.length should be(3)
    }
  }

  test("Should exclude all assets\"") {
    TestTransactions.runWithRollback() {

      val testTierekisteriAsset = new TestTierekisteriAssetImporterOperationsFilterAll
      val roadNumber = 4L
      val roadPartNumber = 1L
      val startSection = 50
      val endSection = 350
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 200L
      val date = DateTime.now()

      val tr = Seq(TierekisteriLightingData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startSection, endSection),
        TierekisteriLightingData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startSection, endSection),
        TierekisteriLightingData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.LeftSide, startSection, endSection))

      when(mockTRClient.fetchActiveAssetData(roadNumber)).thenReturn(tr)
      val filterAsset = testTierekisteriAsset.getAllTierekisteriAddressSectionsTest(roadNumber)
      filterAsset.length should be(0)

      when(mockTRClient.fetchActiveAssetData(roadNumber, roadPartNumber)).thenReturn(tr)
      val assetFetch = testTierekisteriAsset.getAllTierekisteriAddressSectionsTest(roadNumber, roadPartNumber)
      assetFetch.length should be(0)

      when(mockTRClient.fetchHistoryAssetData(roadNumber, Some(date))).thenReturn(tr)
      val assetHist = testTierekisteriAsset.getAllTierekisteriHistoryAddressSectionTest(roadNumber, date)
      assetHist.length should be(0)
    }
  }

  test("Should not create/update asset (massTransitLane) with TR type Unknown\"") {
    TestTransactions.runWithRollback() {

      val testMassTransitLane = new TestMassTransitLaneOperations
      val roadNumber = 4L
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 200L
      val startSection = 50
      val endSection = 350

      val tr = TierekisteriMassTransitLaneData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startSection, endSection, TRLaneArrangementType.apply(1), RoadSide.Left)
      testMassTransitLane.filterTierekisteriAssetsTest(tr) should be (false)
    }
  }

  test("Should not create/update asset (paved road) with TR type Unknown\"") {
    TestTransactions.runWithRollback() {

      val testPavedRoad = new TestPavedRoadOperations
      val roadNumber = 4L
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 200L
      val startSection = 50
      val endSection = 350

      val tr = TierekisteriPavedRoadData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startSection, endSection, PavementClass.apply(60))
      testPavedRoad.filterTierekisteriAssetsTest(tr) should be (false)
    }
  }

  test("Should create asset with TR type valid") {
    TestTransactions.runWithRollback() {

      val testDamageByThaw = new TestDamageByThawOperations
      val roadNumber = 4L
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 200L
      val startAddressMValue = 0L
      val endAddressMValue = 250L

      val tr = TierekisteriDamagedByThawData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, Some(12000))
      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, 100, endAddressMValue, None, None, 5001, 0, 300, SideCode.TowardsDigitizing, Seq(), false, None, None, None)

      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

      when(mockMunicipalityDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressService.getAllRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockTRDamageByThawClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockRoadAddressService.getAllByRoadNumber(any[Long])).thenReturn(Seq(ra))
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]], any[Boolean])).thenReturn(Seq(vvhRoadLink))

      testDamageByThaw.importAssets()
      val asset = dynamicDao.fetchDynamicLinearAssetsByLinkIds(testDamageByThaw.typeId, Seq(5001))
      asset.length should be(1)
    }
  }

  test("import assets (europeanRoad) from TR to OTH"){
    TestTransactions.runWithRollback() {

      val testEuropeanRoad = new TestEuropeanRoadOperations
      val roadNumber = 4L
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 200L
      val startAddressMValue = 0L
      val endAddressMValue = 250L
      val assetValue = "E35"

      val tr = TierekisteriEuropeanRoadData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, assetValue)
      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001, 1.5, 11.4, SideCode.TowardsDigitizing, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

      when(mockMunicipalityDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressService.getAllRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockTREuropeanRoadClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockRoadAddressService.getAllByRoadNumber(any[Long])).thenReturn(Seq(ra))
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]], any[Boolean])).thenReturn(Seq(vvhRoadLink))

      testEuropeanRoad.importAssets()
      val asset = linearAssetDao.fetchAssetsWithTextualValuesByLinkIds(testEuropeanRoad.typeId, Seq(5001), LinearAssetTypes.europeanRoadPropertyId).head

      asset.linkId should be (5001)
      asset.value should be (Some(TextualValue(assetValue)))
    }
  }

  test("update assets (europeanRoad) from TR to OTH"){
    TestTransactions.runWithRollback() {

      val testEuropeanRoad = new TestEuropeanRoadOperations

      val roadNumber = 4L
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 200L
      val startAddressMValue = 0L
      val endAddressMValue = 250L
      val assetValue = "E35"

      val endAddressMValueHist = 200L
      val assetValueHist = "E35, E38"

      val tr = TierekisteriEuropeanRoadData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, assetValue)
      val trHist = TierekisteriEuropeanRoadData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValueHist, assetValueHist)

      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001, 1.5, 11.4, SideCode.TowardsDigitizing, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

      when(mockMunicipalityDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressService.getAllRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockTREuropeanRoadClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockTREuropeanRoadClient.fetchHistoryAssetData(any[Long], any[Option[DateTime]])).thenReturn(Seq(trHist))
      when(mockTREuropeanRoadClient.fetchActiveAssetData(any[Long], any[Long])).thenReturn(Seq(trHist))

      when(mockRoadAddressService.getAllByRoadNumber(any[Long])).thenReturn(Seq(ra))
      when(mockRoadAddressService.getAllByRoadNumberAndParts(any[Long], any[Seq[Long]])).thenReturn(Seq(ra))

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]], any[Boolean])).thenReturn(Seq(vvhRoadLink))

      testEuropeanRoad.importAssets()
      val assetI = linearAssetDao.fetchAssetsWithTextualValuesByLinkIds(testEuropeanRoad.typeId, Seq(5001), LinearAssetTypes.europeanRoadPropertyId).head

      testEuropeanRoad.updateAssets(DateTime.now())
      val assetU = linearAssetDao.fetchAssetsWithTextualValuesByLinkIds(testEuropeanRoad.typeId, Seq(5001), LinearAssetTypes.europeanRoadPropertyId).filterNot(a => a.id == assetI.id).head

      assetU.startMeasure should be (assetI.startMeasure)
      assetU.endMeasure should not be assetI.endMeasure
      assetU.value should be (Some(TextualValue(assetValueHist)))
    }
  }

  test("Split road address in right side with one sign"){
    val roadNumber = 1
    val roadPart = 1
    val startAddressMValue = 0
    val endAddressMValue = 500
    val starAddress = 0
    val endAddress = 500
    val trAssets = Seq(TierekisteriTrafficSignData(roadNumber, roadPart, roadPart, Track.RightSide, 40, 40, RoadSide.Right, SpeedLimitSign, "80"))
    val roadAddress = ViiteRoadAddress(1L, roadNumber, roadPart, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001,starAddress, endAddress, SideCode.TowardsDigitizing, Seq(), false, None, None, None)

    val sections = speedLimitsTierekisteriImporter.testSplitRoadAddressSectionBySigns(trAssets, roadAddress, RoadSide.Right)

    sections.size should be (2)
    val (firstSection, firstTrAsset) = sections.head
    val (lastSection, lastTrAsset) = sections.last

    firstSection.roadNumber should be (1)
    firstSection.roadPartNumber should be (1)
    firstSection.startAddressMValue should be (0)
    firstSection.endAddressMValue should be (Some(40))
    firstTrAsset should be (None)

    lastSection.roadNumber should be (1)
    lastSection.roadPartNumber should be (1)
    lastSection.startAddressMValue should be (40)
    lastSection.endAddressMValue should be (Some(500))
    lastTrAsset should be (trAssets.headOption)
  }

  test("Split road address in right side with two sign"){
    val roadNumber = 1
    val roadPart = 1
    val startAddressMValue = 0
    val endAddressMValue = 500
    val starAddress = 0
    val endAddress = 500
    val trAssets = Seq(
      TierekisteriTrafficSignData(roadNumber, roadPart, roadPart, Track.RightSide, 40, 40, RoadSide.Right, SpeedLimitSign, "80"),
      TierekisteriTrafficSignData(roadNumber, roadPart, roadPart, Track.RightSide, 70, 70, RoadSide.Right, SpeedLimitSign, "90")
    )
    val roadAddress = ViiteRoadAddress(1L, roadNumber, roadPart, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001,starAddress, endAddress, SideCode.TowardsDigitizing, Seq(), false, None, None, None)

    val sections = speedLimitsTierekisteriImporter.testSplitRoadAddressSectionBySigns(trAssets, roadAddress, RoadSide.Right)

    sections.size should be (3)
    val (firstSection, firstTrAsset) = sections.head
    val (middleSection, middleTrAsset) = sections.tail.head
    val (lastSection, lastTrAsset) = sections.tail.last

    firstSection.roadNumber should be (1)
    firstSection.roadPartNumber should be (1)
    firstSection.startAddressMValue should be (0)
    firstSection.endAddressMValue should be (Some(40))
    firstTrAsset should be (None)

    middleSection.roadNumber should be (1)
    middleSection.roadPartNumber should be (1)
    middleSection.startAddressMValue should be (40)
    middleSection.endAddressMValue should be (Some(70))
    middleTrAsset should be (trAssets.headOption)

    lastSection.roadNumber should be (1)
    lastSection.roadPartNumber should be (1)
    lastSection.startAddressMValue should be (70)
    lastSection.endAddressMValue should be (Some(500))
    lastTrAsset should be (trAssets.lastOption)
  }

  test("Split road address in left side with one sign"){
    val roadNumber = 1
    val roadPart = 1
    val startAddressMValue = 0
    val endAddressMValue = 500
    val starAddress = 0
    val endAddress = 500
    val trAssets = Seq(TierekisteriTrafficSignData(roadNumber, roadPart, roadPart, Track.LeftSide, 40, 40, RoadSide.Left, SpeedLimitSign, "80"))
    val roadAddress = ViiteRoadAddress(1L, roadNumber, roadPart, Track.LeftSide, startAddressMValue, endAddressMValue, None, None, 5001,starAddress, endAddress, SideCode.TowardsDigitizing, Seq(), false, None, None, None)

    val sections = speedLimitsTierekisteriImporter.testSplitRoadAddressSectionBySigns(trAssets, roadAddress, RoadSide.Left)

    sections.size should be (2)
    val (firstSection, firstTrAsset) = sections.head
    val (lastSection, lastTrAsset) = sections.last

    firstSection.roadNumber should be (1)
    firstSection.roadPartNumber should be (1)
    firstSection.startAddressMValue should be (40)
    firstSection.endAddressMValue should be (Some(500))
    firstTrAsset should be (None)

    lastSection.roadNumber should be (1)
    lastSection.roadPartNumber should be (1)
    lastSection.startAddressMValue should be (0)
    lastSection.endAddressMValue should be (Some(40))
    lastTrAsset should be (trAssets.headOption)
  }

  test("Split road address in left side with two sign"){
    val roadNumber = 1
    val roadPart = 1
    val startAddressMValue = 0
    val endAddressMValue = 500
    val starAddress = 0
    val endAddress = 500
    val trAssets = Seq(
      TierekisteriTrafficSignData(roadNumber, roadPart, roadPart, Track.LeftSide, 40, 40, RoadSide.Left, SpeedLimitSign, "80"),
      TierekisteriTrafficSignData(roadNumber, roadPart, roadPart, Track.LeftSide, 70, 70, RoadSide.Left, SpeedLimitSign, "90")
    )
    val roadAddress = ViiteRoadAddress(1L, roadNumber, roadPart, Track.LeftSide, startAddressMValue, endAddressMValue, None, None, 5001,starAddress, endAddress, SideCode.TowardsDigitizing, Seq(), false, None, None, None)

    val sections = speedLimitsTierekisteriImporter.testSplitRoadAddressSectionBySigns(trAssets, roadAddress, RoadSide.Left)

    sections.size should be (3)
    val (firstSection, firstTrAsset) = sections.head
    val (middleSection, middleTrAsset) = sections.tail.head
    val (lastSection, lastTrAsset) = sections.tail.last

    firstSection.roadNumber should be (1)
    firstSection.roadPartNumber should be (1)
    firstSection.startAddressMValue should be (70)
    firstSection.endAddressMValue should be (Some(500))
    firstTrAsset should be (None)

    middleSection.roadNumber should be (1)
    middleSection.roadPartNumber should be (1)
    middleSection.startAddressMValue should be (40)
    middleSection.endAddressMValue should be (Some(70))
    middleTrAsset should be (trAssets.lastOption)

    lastSection.roadNumber should be (1)
    lastSection.roadPartNumber should be (1)
    lastSection.startAddressMValue should be (0)
    lastSection.endAddressMValue should be (Some(40))
    lastTrAsset should be (trAssets.headOption)
  }

  test("import assets (speed limit) from TR to OTH"){
    TestTransactions.runWithRollback() {

      val testSpeedLimit = new TestSpeedLimitAssetOperations
      val roadNumber = 4L
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 200L
      val startAddressMValue = 0L
      val endAddressMValue = 250L
      val assetValue = 100
      val roadSide = RoadSide.Left

      val tr = TierekisteriSpeedLimitData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, assetValue, roadSide)
      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001, 1.5, 11.4, SideCode.AgainstDigitizing, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, None, Map(), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

      when(mockMunicipalityDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressService.getAllRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockTRSpeedLimitAssetClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockRoadAddressService.getAllByRoadNumber(any[Long])).thenReturn(Seq(ra))
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]], any[Boolean])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinksAndComplementary(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.getVVHRoadLinksF(any[Int])).thenReturn(Seq(vvhRoadLink))

      testSpeedLimit.importAssets()
      val asset = speedLimitDao.getCurrentSpeedLimitsByLinkIds(Some(Set(5001))).head

      asset.linkId should be (5001)
      asset.value should be (Some(NumericValue(assetValue)))
    }
  }

  test("update assets (speed limit) from TR to OTH"){
    TestTransactions.runWithRollback() {

      val testSpeedLimit = new TestSpeedLimitAssetOperations

      val roadNumber = 4L
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 200L
      val startAddressMValue = 0L
      val endAddressMValue = 250L
      val assetValue = 100
      val roadSide = RoadSide.Left

      val assetValueHist = 120
      val endAddressMValueHist = 200L

      val tr = TierekisteriSpeedLimitData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, assetValue, roadSide)
      val trHist = TierekisteriSpeedLimitData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValueHist, assetValueHist, roadSide)

      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001, 1.5, 11.4, SideCode.AgainstDigitizing, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, None, Map(), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

      when(mockAssetDao.expireAssetByTypeAndLinkId(any[Long], any[Seq[Long]])).thenCallRealMethod()
      when(mockMunicipalityDao.getMunicipalities).thenReturn(Seq(235))
      when(mockRoadAddressService.getAllRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockTRSpeedLimitAssetClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockTRSpeedLimitAssetClient.fetchHistoryAssetData(any[Long], any[Option[DateTime]])).thenReturn(Seq(trHist))  /*needed for update*/
      when(mockTRSpeedLimitAssetClient.fetchActiveAssetData(any[Long], any[Long])).thenReturn(Seq(trHist))

      when(mockRoadAddressService.getAllByRoadNumber(any[Long])).thenReturn(Seq(ra))
      when(mockRoadAddressService.getAllByRoadNumberAndParts(any[Long], any[Seq[Long]])).thenReturn(Seq(ra))
      when(mockRoadLinkService.getVVHRoadLinksF(any[Int])).thenReturn(Seq(vvhRoadLink))

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]], any[Boolean])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinksAndComplementary(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))

      testSpeedLimit.importAssets()
      val assetI = speedLimitDao.getCurrentSpeedLimitsByLinkIds(Some(Set(5001))).head

      testSpeedLimit.updateAssets(DateTime.now())
      val assetU = speedLimitDao.getCurrentSpeedLimitsByLinkIds(Some(Set(5001))).head

      assetU.startMeasure should not be (assetI.startMeasure)
      assetU.endMeasure should be (assetI.endMeasure)
      assetU.value should be (Some(NumericValue(assetValueHist)))
    }
  }

  test("Create asset with urban area information with value 9 (speed limt = 80) "){
    TestTransactions.runWithRollback() {
      val testTRSpeedLimit = new TestSpeedLimitsTierekisteriImporter

      val roadNumber = 4L
      val roadPart = 200L
      val startAddress = 0L
      val endAddress = 250L
      val assetValue = "9"

      val trUrbanArea = TierekisteriUrbanAreaData(roadNumber, roadPart, roadPart, Track.RightSide, startAddress, endAddress, assetValue)
      val addressSection = AddressSection(roadNumber, roadPart, Track.RightSide, startAddress, Some(endAddress))

      val ra = ViiteRoadAddress(1L, roadNumber, roadPart, Track.RightSide, startAddress, endAddress, None, None, 5001, 1.5, 11.4, SideCode.AgainstDigitizing, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, None, Map(), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

      val predictedMeasures = testTRSpeedLimit.calculateMeasuresTest(ra, addressSection).head
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)

      testTRSpeedLimit.createUrbanTrafficSign(Some(vvhRoadLink),Seq(trUrbanArea), addressSection, ra, RoadSide.Left)

      val asset = linearAssetDao.fetchLinearAssetsByLinkIds(310, Seq(5001), LinearAssetTypes.numericValuePropertyId)
      asset.size should be (1)
      asset.head.linkId should be(5001)
      asset.head.sideCode should be(SideCode.AgainstDigitizing.value)
      asset.head.value should be(Some(NumericValue(80)))
      asset.head.startMeasure should be(predictedMeasures.startMeasure +- 0.01)
      asset.head.endMeasure should be(predictedMeasures.endMeasure +- 0.01)
      asset.head.createdBy should be(Some("batch_process_stateSpeedLimit"))
    }
  }

  test("Create asset with urban area information with value different of 9 (speed limt = 50)"){
    TestTransactions.runWithRollback() {
      val testTRSpeedLimit = new TestSpeedLimitsTierekisteriImporter

      val roadNumber = 4L
      val roadPart = 200L
      val startAddressMValue = 0L
      val endAddressMValue = 250L
      val starAddress= 0L
      val endAddress = 250L
      val assetValue = "2"
      val roadSide = RoadSide.Left

      val trUrbanArea = TierekisteriUrbanAreaData(roadNumber, roadPart, roadPart, Track.RightSide, starAddress, endAddress, assetValue)
      val addressSection = AddressSection(roadNumber, roadPart, Track.RightSide, startAddressMValue, Some(endAddressMValue))

      val ra = ViiteRoadAddress(1L, roadNumber, roadPart, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001, starAddress, endAddress, SideCode.AgainstDigitizing, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, None, Map(), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

      val predictedMeasures = testTRSpeedLimit.calculateMeasuresTest(ra, addressSection).head

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)

      testTRSpeedLimit.createUrbanTrafficSign(Some(vvhRoadLink),Seq(trUrbanArea), addressSection, ra, RoadSide.Right)

      val asset = linearAssetDao.fetchLinearAssetsByLinkIds(310, Seq(5001), LinearAssetTypes.numericValuePropertyId)
      asset.size should be (1)
      asset.head.linkId should be(5001)
      asset.head.sideCode should be(SideCode.AgainstDigitizing.value)
      asset.head.value should be(Some(NumericValue(50)))
      asset.head.startMeasure should be(predictedMeasures.startMeasure +- 0.01)
      asset.head.endMeasure should be(predictedMeasures.endMeasure +- 0.01)
      asset.head.createdBy should be(Some("batch_process_stateSpeedLimit"))
    }
  }

  test("Spliting in two sections and creation of two assets (with and without TrUrbanArea information)") {
    TestTransactions.runWithRollback() {
      val testTRSpeedLimit = new TestSpeedLimitsTierekisteriImporter

      val roadNumber = 4L
      val roadPart = 200L
      val startAddressMValue = 0L
      val endAddressMValue = 250L
      val starAddress= 0L
      val endAddress = 250L
      val assetValue = "2"

      val trUrbanArea = TierekisteriUrbanAreaData(roadNumber, roadPart, roadPart, Track.RightSide, 230, 250, assetValue)
      val addressSection = AddressSection(roadNumber, roadPart, Track.RightSide, startAddressMValue, Some(endAddressMValue))

      val ra = ViiteRoadAddress(1L, roadNumber, roadPart, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001,starAddress, endAddress, SideCode.TowardsDigitizing, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, None, Map(), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      testTRSpeedLimit.createUrbanTrafficSign(Some(vvhRoadLink),Seq(trUrbanArea), addressSection, ra, RoadSide.Right)

      val assets = linearAssetDao.fetchLinearAssetsByLinkIds(310, Seq(5001), LinearAssetTypes.numericValuePropertyId)
      assets.size should be (2)
      assets.foreach{ asset =>
        asset.linkId should be(5001)
        asset.sideCode should be(SideCode.TowardsDigitizing.value)
        asset.createdBy should be(Some("batch_process_stateSpeedLimit"))
      }
      assets.sortBy(_.startMeasure)
      assets.head.value should be (Some(NumericValue(80)))
      assets.last.value should be (Some(NumericValue(50)))

    }
  }

  test("Creation of one assets (without TrUrbanArea information)"){
    TestTransactions.runWithRollback() {
      val testTRSpeedLimit = new TestSpeedLimitsTierekisteriImporter

      val roadNumber = 4L
      val roadPart = 200L
      val addressMValue = 10L
      val assetValue = "2"
      val startAddress = 0L
      val endAddress = 250L
      val startAddressMValue = 0
      val endAddressMValue = 250

      val addressSection = AddressSection(roadNumber, roadPart, Track.RightSide, startAddress, Some(endAddress))

      val ra = ViiteRoadAddress(1L, roadNumber, roadPart, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001, startAddress, endAddress, SideCode.AgainstDigitizing, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, None, Map(), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      testTRSpeedLimit.createUrbanTrafficSign(Some(vvhRoadLink),Seq.empty[TierekisteriUrbanAreaData], addressSection, ra, RoadSide.Right)

      val asset = linearAssetDao.fetchLinearAssetsByLinkIds(310, Seq(5001), LinearAssetTypes.numericValuePropertyId)
      asset.size should be (1)
      asset.head.linkId should be(5001)
      asset.head.sideCode should be(SideCode.AgainstDigitizing.value)
      asset.head.createdBy should be(Some("batch_process_stateSpeedLimit"))
      asset.head.value should be (Some(NumericValue(80)))
    }
  }

  test("Create SpeedLimit using 2 traffic signs from 1 urban area asset, for 3 sections"){
    TestTransactions.runWithRollback() {
      val testTRSpeedLimit = new TestSpeedLimitsTierekisteriImporter

      val roadNumber = 1
      val roadPart = 1
      val startAddressMValue = 0
      val endAddressMValue = 500
      val starAddress = 0
      val endAddress = 500
      val assetValue = "2"
      val roadSide = RoadSide.Left

      val trUrbanArea = Seq(
        TierekisteriUrbanAreaData(roadNumber, roadPart, roadPart, Track.RightSide, 40, 70, assetValue))
      val addressSection = AddressSection(roadNumber, roadPart, Track.RightSide, startAddressMValue, Some(endAddressMValue))

      val ra = ViiteRoadAddress(1L, roadNumber, roadPart, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001,starAddress, endAddress, SideCode.TowardsDigitizing, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, None, Map(), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      testTRSpeedLimit.createUrbanTrafficSign(Some(vvhRoadLink), trUrbanArea, addressSection, ra, RoadSide.Right)
      val assets = linearAssetDao.fetchLinearAssetsByLinkIds(310, Seq(5001), LinearAssetTypes.numericValuePropertyId)

      assets.size should be (3)
      assets.foreach{ asset =>
        asset.linkId should be(5001)
        asset.sideCode should be(2)
        asset.createdBy should be(Some("batch_process_stateSpeedLimit"))
      }
      assets.sortBy(_.startMeasure).map(_.value) should be (Seq(Some(NumericValue(80)), Some(NumericValue(50)), Some(NumericValue(80))))
    }
  }

  test("Create SpeedLimit based on Telematic type without roadLink type info"){
    TestTransactions.runWithRollback() {
      val testTRSpeedLimit = new TestSpeedLimitsTierekisteriImporter

      val roadNumber = 1
      val roadPart = 1

      val ras = Seq(ViiteRoadAddress(1L, roadNumber, roadPart, Track.RightSide, 0, 170, None, None, 5001,0, 170.3, SideCode.TowardsDigitizing, Seq(), false, None, None, None),
      ViiteRoadAddress(1L, roadNumber, roadPart, Track.RightSide, 170, 175, None, None, 5002, 0, 5.8, SideCode.TowardsDigitizing, Seq(), false, None, None, None))

      val vvhRoadLink = Seq(VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, None, Map(), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface),
      VVHRoadlink(5002, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, None, Map(), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface))

      val startMValue = 118
      val trAsset =  Seq(TierekisteriTrafficSignData(roadNumber, roadPart, roadPart, Track.RightSide, startMValue, startMValue, RoadSide.Right, TelematicSpeedLimit,""))
      val mappedLinkType: Map[Long, Seq[(Long, LinkType)]] = Map((5001L, Seq((5001L, Motorway))))
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockRoadAddressService.getAllByRoadNumber(any[Long])).thenReturn(ras)
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]], any[Boolean])).thenReturn(vvhRoadLink)
      when(mockRoadLinkService.getAllLinkType(any[Seq[Long]])).thenReturn(mappedLinkType)
      testTRSpeedLimit.generateOneSideSpeedLimitsTest(roadNumber, RoadSide.Right, trAsset, Seq(), ras)

      val assets = linearAssetDao.fetchLinearAssetsByLinkIds(310, Seq(5001, 5002), LinearAssetTypes.numericValuePropertyId)

      assets.size should be (3)
      assets.foreach{ asset =>
        asset.sideCode should be(2)
        asset.createdBy should be(Some("batch_process_stateSpeedLimit"))
      }
      assets.sortBy(_.linkId).map(_.linkId) should be (Seq(5001, 5001, 5002))
      assets.sortBy(_.linkId).sortBy(_.startMeasure).map(_.value) should be (Seq(Some(NumericValue(80)), Some(NumericValue(120)), Some(NumericValue(120))))
    }
  }

  test("import care class from TR to OTH"){
    TestTransactions.runWithRollback() {

      val testCareClassImporter = new TestCareClassOperations
      val roadNumber = 4L
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 200L
      val shortStartAddressMValue = 50L
      val shortEndAddressMValue = 150L
      val longStartAddressMValue = 0L
      val longEndAddressMValue = 500L
      val middleStartAddressMValue = 100L
      val middleEndAddressMValue = 300L
      val greenAssetValue = 4
      val greenAssetPublicId = "hoitoluokat_viherhoitoluokka"
      val winterAssetPublicId = "hoitoluokat_talvihoitoluokka"
      val winterAssetValue = 1
      val middleWinterAssetValue = 5

      val winterAsset = TierekisteriWinterCareClassAssetData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, shortStartAddressMValue, shortEndAddressMValue, winterAssetValue, winterAssetPublicId)
      val middleWinterAsset = TierekisteriWinterCareClassAssetData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, middleStartAddressMValue, middleEndAddressMValue, middleWinterAssetValue, winterAssetPublicId)
      val greenAsset = TierekisteriGreenCareClassAssetData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, longStartAddressMValue, longEndAddressMValue, greenAssetValue, greenAssetPublicId)
      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, longStartAddressMValue, longEndAddressMValue, None, None, 5002, 1.5, 11.4, SideCode.TowardsDigitizing, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5002, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

      when(mockMunicipalityDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressService.getAllRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockGreenCareClassAssetClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(greenAsset))
      when(mockWinterCareClassAssetClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(winterAsset, middleWinterAsset))
      when(mockRoadAddressService.getAllByRoadNumber(any[Long])).thenReturn(Seq(ra))
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]], any[Boolean])).thenReturn(Seq(vvhRoadLink))

      testCareClassImporter.importAssets()
      val assets = dynamicDao.fetchDynamicLinearAssetsByLinkIds(testCareClassImporter.typeId, Seq(5002)).sortBy(_.id).toArray

      assets.size should be (5)
    }
  }

  test("import assets (carryingCapacity) from TR to OTH"){
    TestTransactions.runWithRollback() {

      val testCarryingCapacity = new TestCarryingCapacityOperations
      val roadNumber = 4L
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 200L
      val startAddressMValue = 0L
      val endAddressMValue = 250L
      val springCapacity = Some("508")
      val factorValue = TRFrostHeavingFactorType.MiddleValue60to80.value
      val measurementDate = Some(new SimpleDateFormat("dd/MM/yyyy").parse("04/06/2018"))

      val tr = TierekisteriCarryingCapacityData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, springCapacity, factorValue, measurementDate)
      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001, 1.5, 11.4, SideCode.TowardsDigitizing, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

      when(mockMunicipalityDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressService.getAllRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockTRCarryingCapacityClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockRoadAddressService.getAllByRoadNumber(any[Long])).thenReturn(Seq(ra))
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]], any[Boolean])).thenReturn(Seq(vvhRoadLink))

      testCarryingCapacity.importAssets()
      val asset = dynamicDao.fetchDynamicLinearAssetsByLinkIds(testCarryingCapacity.typeId, Seq(5001))
      asset.length should be(1)

      asset.head.linkId should be (5001)
      asset.head.value.head match {
        case DynamicValue(multiTypeProps) =>
          multiTypeProps.properties.map(prop =>
            prop.propertyType match {
              case "single_choice" =>
                prop.values.head.value should be (factorValue.toString())
              case "integer" =>
                prop.values.head.value should be (springCapacity.get)
              case "date" =>
                val formatedDate = new SimpleDateFormat("dd/MM/yyyy").parse(prop.values.head.value.toString.replace(".", "/"))
                formatedDate should be (measurementDate.get)
              case _ => None
            }
          )
        case _ => None
      }
    }
  }

  test("update assets (carryingCapacity) from TR to OTH"){
    TestTransactions.runWithRollback() {

      val testCarryingCapacity = new TestCarryingCapacityOperations

      val roadNumber = 4L
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 200L
      val startAddressMValue = 0L
      val endAddressMValue = 250L
      val springCapacity = Some("508")
      val factorValue = TRFrostHeavingFactorType.MiddleValue60to80.value
      val measurementDate = Some(new SimpleDateFormat("dd/MM/yyyy").parse("04/06/2018"))

      val springCapacityHist = Some("271")
      val factorValueHist = TRFrostHeavingFactorType.NoFrostHeaving.value
      val measurementDateHist = Some(new SimpleDateFormat("dd/MM/yyyy").parse("01/06/2018"))

      val tr = TierekisteriCarryingCapacityData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, springCapacity, factorValue, measurementDate)
      val trHist = TierekisteriCarryingCapacityData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, springCapacityHist, factorValueHist, measurementDateHist)
      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001, 1.5, 11.4, SideCode.TowardsDigitizing, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

      when(mockMunicipalityDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressService.getAllRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockRoadAddressService.getAllByRoadNumberAndParts(any[Long], any[Seq[Long]])).thenReturn(Seq(ra))
      when(mockTRCarryingCapacityClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockRoadAddressService.getAllByRoadNumber(any[Long])).thenReturn(Seq(ra))
      when(mockTRCarryingCapacityClient.fetchHistoryAssetData(any[Long], any[Option[DateTime]])).thenReturn(Seq(trHist))
      when(mockTRCarryingCapacityClient.fetchActiveAssetData(any[Long], any[Long])).thenReturn(Seq(trHist))
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]], any[Boolean])).thenReturn(Seq(vvhRoadLink))

      testCarryingCapacity.importAssets()
      val assetI = dynamicDao.fetchDynamicLinearAssetsByLinkIds(testCarryingCapacity.typeId, Seq(5001)).head

      testCarryingCapacity.updateAssets(DateTime.now())
      val assetU = dynamicDao.fetchDynamicLinearAssetsByLinkIds(testCarryingCapacity.typeId, Seq(5001)).filterNot(a => a.id == assetI.id).head

      assetU.linkId should be (5001)
      assetU.value.head match {
        case DynamicValue(multiTypeProps) =>
          multiTypeProps.properties.map(prop =>
            prop.propertyType match {
              case "single_choice" =>
                prop.values.head.value should be (factorValueHist.toString())
              case "integer" =>
                prop.values.head.value should be (springCapacityHist.get)
              case "date" =>
                val formatedDate = new SimpleDateFormat("dd/MM/yyyy").parse(prop.values.head.value.toString.replace(".", "/"))
                formatedDate should be (measurementDateHist.get)
              case _ => None
            }
          )
        case _ => None
      }
    }
  }

  test("import assets (animalWarnings) from TR to OTH"){
    TestTransactions.runWithRollback() {

      val testAnimalWarnings = new TestAnimalWarningsOperations
      val roadNumber = 4L
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 200L
      val startAddressMValue = 0L
      val endAddressMValue = 250L

      val tr = TierekisteriAnimalWarningsData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, AnimalWarningsType.MooseWarningArea)
      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001, 1.5, 11.4, SideCode.TowardsDigitizing, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

      when(mockMunicipalityDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressService.getAllRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockTRAnimalWarningsClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockRoadAddressService.getAllByRoadNumber(any[Long])).thenReturn(Seq(ra))
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]], any[Boolean])).thenReturn(Seq(vvhRoadLink))

      testAnimalWarnings.importAssets()
      val asset = dynamicDao.fetchDynamicLinearAssetsByLinkIds(testAnimalWarnings.typeId, Seq(5001)).head

      asset.linkId should be (5001)
      asset.value should not be empty
    }
  }

  test("update assets (animalWarnings) from TR to OTH"){
    TestTransactions.runWithRollback() {

      val testAnimalWarnings = new TestAnimalWarningsOperations
      val roadNumber = 4L
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 200L
      val startAddressMValue = 0L
      val endAddressMValue = 250L

      val starAddress = 0
      val endAddress = 500

      val startSection = 50
      val endSection = 150

      val starSectionHist = 100
      val endSectionHist = 150

      val tr = TierekisteriAnimalWarningsData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startSection, endSection, AnimalWarningsType.MooseFence)
      val trHist = TierekisteriAnimalWarningsData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, starSectionHist, endSectionHist, AnimalWarningsType.MooseFence)

      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001,starAddress, endAddress, SideCode.TowardsDigitizing, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

      when(mockMunicipalityDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressService.getAllRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockTRAnimalWarningsClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockTRAnimalWarningsClient.fetchHistoryAssetData(any[Long], any[Option[DateTime]])).thenReturn(Seq(trHist))
      when(mockTRAnimalWarningsClient.fetchActiveAssetData(any[Long], any[Long])).thenReturn(Seq(trHist))
      when(mockRoadAddressService.getAllByRoadNumber(any[Long])).thenReturn(Seq(ra))
      when(mockRoadAddressService.getAllByRoadNumberAndParts(any[Long], any[Seq[Long]])).thenReturn(Seq(ra))

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]], any[Boolean])).thenReturn(Seq(vvhRoadLink))

      testAnimalWarnings.importAssets()
      val assetI = linearAssetDao.fetchLinearAssetsByLinkIds(testAnimalWarnings.typeId, Seq(5001), LinearAssetTypes.numericValuePropertyId).head

      testAnimalWarnings.updateAssets(DateTime.now())
      val assetU = linearAssetDao.fetchLinearAssetsByLinkIds(testAnimalWarnings.typeId, Seq(5001), LinearAssetTypes.numericValuePropertyId).filterNot(a => a.id == assetI.id).head

      assetU.startMeasure should not be assetI.startMeasure
      assetU.endMeasure should be (assetI.endMeasure)
    }
  }

  test("traffic sign converter"){
    val traffic  = new TestTrafficSignTierekisteriImporter

    traffic.converter(MaxTonsOnBogieExceeding, "30t") should be ("30000")
    traffic.converter(MaxTonsOnBogieExceeding, "30T") should be ("30000")
    traffic.converter(MaxTonsOnBogieExceeding, "30.t") should be ("30000")
    traffic.converter(MaxTonsOnBogieExceeding, "30.1t") should be ("30100")
    traffic.converter(MaxTonsOnBogieExceeding, "30.1tn") should be ("30100")
    traffic.converter(MaxTonsOnBogieExceeding, "30.1 tn") should be ("30100")
    traffic.converter(MaxTonsOnBogieExceeding, "some text 30.1 tn") should be ("30100")
    traffic.converter(MaxTonsOnBogieExceeding, "some text 30.1 tn some text") should be ("30100")

    traffic.converter(MaxTonsOnBogieExceeding, "30") should be ("30")

    traffic.converter(NoWidthExceeding, "2.2 m") should be ("220")
    traffic.converter(NoWidthExceeding, "2,2 M") should be ("220")
    traffic.converter(NoWidthExceeding, "2.2") should be ("2.2")
    traffic.converter(NoWidthExceeding, "some text 2.2m") should be ("220")
    traffic.converter(NoWidthExceeding, "some text 2.2m some text") should be ("220")

    traffic.converter(SpeedLimitSign, "100km\\h ") should be ("100")
    traffic.converter(SpeedLimitSign, "100km\\h") should be ("100")
    traffic.converter(SpeedLimitSign, "100KM\\H") should be ("100")
    traffic.converter(SpeedLimitSign, "100kmh") should be ("100")
    traffic.converter(SpeedLimitSign, "some text 100kmh") should be ("100")
    traffic.converter(SpeedLimitSign, "some text 100kmh some text") should be ("100")
    traffic.converter(SpeedLimitSign, "100") should be ("100")
  }

  test("Only state roads are valid targets for TR assets") {
    TestTransactions.runWithRollback() {

      val testLitRoad = new TestLitRoadOperations
      val roadNumber = 4L
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 200L
      val startAddressMValue = 0L
      val endAddressMValue = 250L

      val tr = TierekisteriLightingData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue)
      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5001, 1.5, 11.4, SideCode.TowardsDigitizing, Seq(), false, None, None, None)
      val ra2 = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5002, 1.5, 11.4, SideCode.TowardsDigitizing, Seq(), false, None, None, None)
      val ra3 = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, None, None, 5003, 1.5, 11.4, SideCode.TowardsDigitizing, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)
      val vvhRoadLink2 = VVHRoadlink(5002, 235, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)
      val vvhRoadLink3 = VVHRoadlink(5003, 235, Nil, Private, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)


      when(mockMunicipalityDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressService.getAllRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockTRClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockRoadAddressService.getAllByRoadNumber(any[Long])).thenReturn(Seq(ra, ra2, ra3))
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(vvhRoadLink, vvhRoadLink2, vvhRoadLink3))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]], any[Boolean])).thenReturn(Seq(vvhRoadLink, vvhRoadLink2, vvhRoadLink3))

      testLitRoad.importAssets()
      val asset = linearAssetDao.fetchLinearAssetsByLinkIds(testLitRoad.typeId, Seq(5001, 5002, 5003), LinearAssetTypes.numericValuePropertyId)

      asset.size should be (1)

    }
  }
}

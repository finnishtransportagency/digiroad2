package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.{TierekisteriLightingAssetClient, TierekisteriMassTransitLaneAssetClient, TierekisteriTrafficSignData, _}
import fi.liikennevirasto.digiroad2.asset.oracle.OracleAssetDao
import fi.liikennevirasto.digiroad2.linearasset.{TextualValue, NumericValue}
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.roadaddress.oracle.{RoadAddressDAO, RoadAddress => ViiteRoadAddress}
import org.joda.time.DateTime
import org.mockito.Matchers.{any, _}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import org.mockito.Mockito._

class TierekisteriImporterOperationsSpec extends FunSuite with Matchers  {

  val mockAssetDao: OracleAssetDao = MockitoSugar.mock[OracleAssetDao]
  val mockRoadAddressDAO: RoadAddressDAO = MockitoSugar.mock[RoadAddressDAO]
  val mockTRClient: TierekisteriLightingAssetClient = MockitoSugar.mock[TierekisteriLightingAssetClient]
  val mockTRTrafficSignsLimitClient: TierekisteriTrafficSignAssetClient = MockitoSugar.mock[TierekisteriTrafficSignAssetClient]
  val mockRoadLinkService: RoadLinkOTHService = MockitoSugar.mock[RoadLinkOTHService]
  val mockVVHClient: VVHClient = MockitoSugar.mock[VVHClient]
  val mockVVHRoadLinkClient: VVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val linearAssetDao = new OracleLinearAssetDao(mockVVHClient, mockRoadLinkService)
  val oracleAssetDao = new OracleAssetDao
  val mockTrImporter: TierekisteriDataImporter = MockitoSugar.mock[TierekisteriDataImporter]
  val mockTRPavedRoadClient: TierekisteriPavedRoadAssetClient = MockitoSugar.mock[TierekisteriPavedRoadAssetClient]
  val mockMassTransitLaneClient: TierekisteriMassTransitLaneAssetClient = MockitoSugar.mock[TierekisteriMassTransitLaneAssetClient]
  val mockTRDamageByThawClient: TierekisteriDamagedByThawAssetClient = MockitoSugar.mock[TierekisteriDamagedByThawAssetClient]
  val mockTREuropeanRoadClient: TierekisteriEuropeanRoadAssetClient = MockitoSugar.mock[TierekisteriEuropeanRoadAssetClient]
  val mockTRSpeedLimitAssetClient: TierekisteriSpeedLimitAssetClient = MockitoSugar.mock[TierekisteriSpeedLimitAssetClient]

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
    override lazy val roadAddressDao: RoadAddressDAO = mockRoadAddressDAO
    override val tierekisteriClient: TierekisteriLightingAssetClient = mockTRClient
    override lazy val roadLinkService: RoadLinkOTHService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient

    //Creating this new methods because is protected visibility on the trait
    def getRoadAddressSectionsTest(trAsset: TierekisteriAssetData): Seq[(AddressSection, TierekisteriAssetData)] = super.getRoadAddressSections(trAsset)
    def getAllViiteRoadAddressTest(section: AddressSection) = super.getAllViiteRoadAddress(section)
    def getAllViiteRoadAddressTest(roadNumber: Long, roadPart: Long) = super.getAllViiteRoadAddress(roadNumber, roadPart)
    def expireAssetsTest(linkIds: Seq[Long]): Unit = super.expireAssets(linkIds)
    def calculateStartLrmByAddressTest(startAddress: ViiteRoadAddress, section: AddressSection): Option[Double] = super.calculateStartLrmByAddress(startAddress, section)
    def calculateEndLrmByAddressTest(endAddress: ViiteRoadAddress, section: AddressSection): Option[Double] = super.calculateEndLrmByAddress(endAddress, section)

    def getAllTierekisteriAddressSectionsTest(roadNumber: Long) = super.getAllTierekisteriAddressSections(roadNumber: Long)
    def getAllTierekisteriAddressSectionsTest(roadNumber: Long, roadPart: Long) = super.getAllTierekisteriAddressSections(roadNumber: Long)
    def getAllTierekisteriHistoryAddressSectionTest(roadNumber: Long, lastExecution: DateTime) = super.getAllTierekisteriHistoryAddressSection(roadNumber: Long, lastExecution: DateTime)

    override protected def createAsset(section: AddressSection, trAssetData: TierekisteriAssetData): Unit = {

    }
  }

  class TestSpeedLimitsTierekisteriImporter extends SpeedLimitsTierekisteriImporter {
    override lazy val assetDao: OracleAssetDao = mockAssetDao
    override lazy val roadAddressDao: RoadAddressDAO = mockRoadAddressDAO
    override val tierekisteriClient = mockTRTrafficSignsLimitClient
    override lazy val roadLinkService: RoadLinkOTHService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
    override def withDynTransaction[T](f: => T): T = f

    def testSplitRoadAddressSectionBySigns(trAssets: Seq[TierekisteriAssetData], ra: ViiteRoadAddress, roadSide: RoadSide): Seq[(AddressSection, Option[TierekisteriAssetData])] = {
      super.splitRoadAddressSectionBySigns(trAssets, ra, roadSide)
    }

    def calculateMeasuresTest(roadAddress: ViiteRoadAddress, section: AddressSection) = super.calculateMeasures(roadAddress, section)

    def createSpeedLimitTest(roadAddress: ViiteRoadAddress, addressSection: AddressSection,
                             trAssetOption: Option[TierekisteriAssetData], roadLinkOption: Option[VVHRoadlink],
                             trUrbanAreaAssets: Seq[TierekisteriUrbanAreaData], roadSide: RoadSide) = super.createSpeedLimit(roadAddress, addressSection, trAssetOption, roadLinkOption, trUrbanAreaAssets, roadSide)
  }

  class TestLitRoadOperations extends LitRoadTierekisteriImporter {
    override lazy val assetDao: OracleAssetDao = mockAssetDao
    override lazy val roadAddressDao: RoadAddressDAO = mockRoadAddressDAO
    override val tierekisteriClient: TierekisteriLightingAssetClient = mockTRClient
    override lazy val roadLinkService: RoadLinkOTHService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
    override def withDynTransaction[T](f: => T): T = f
  }

  class TestPavedRoadOperations extends PavedRoadTierekisteriImporter {
    override lazy val assetDao: OracleAssetDao = mockAssetDao
    override lazy val roadAddressDao: RoadAddressDAO = mockRoadAddressDAO
    override val tierekisteriClient: TierekisteriPavedRoadAssetClient = mockTRPavedRoadClient
    override lazy val roadLinkService: RoadLinkOTHService = mockRoadLinkService
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
    override lazy val roadAddressDao: RoadAddressDAO = mockRoadAddressDAO
    override val tierekisteriClient: TierekisteriMassTransitLaneAssetClient = mockMassTransitLaneClient
    override lazy val roadLinkService: RoadLinkOTHService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
    override def withDynTransaction[T](f: => T): T = f

    def filterTierekisteriAssetsTest(tierekisteriAssetData: TierekisteriAssetData) = super.filterTierekisteriAssets(tierekisteriAssetData)
  }

  class TestDamageByThawOperations extends DamagedByThawTierekisteriImporter {
    override lazy val assetDao: OracleAssetDao = mockAssetDao
    override lazy val roadAddressDao: RoadAddressDAO = mockRoadAddressDAO
    override val tierekisteriClient: TierekisteriDamagedByThawAssetClient = mockTRDamageByThawClient
    override lazy val roadLinkService: RoadLinkOTHService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
    override def withDynTransaction[T](f: => T): T = f
  }

  class TestEuropeanRoadOperations extends EuropeanRoadTierekisteriImporter {
    override lazy val assetDao: OracleAssetDao = mockAssetDao
    override lazy val roadAddressDao: RoadAddressDAO = mockRoadAddressDAO
    override val tierekisteriClient: TierekisteriEuropeanRoadAssetClient = mockTREuropeanRoadClient
    override lazy val roadLinkService: RoadLinkOTHService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
    override def withDynTransaction[T](f: => T): T = f
  }

  class TestSpeedLimitAssetOperations extends SpeedLimitAssetTierekisteriImporter {
    override lazy val assetDao: OracleAssetDao = mockAssetDao
    override lazy val roadAddressDao: RoadAddressDAO = mockRoadAddressDAO
    override val tierekisteriClient: TierekisteriSpeedLimitAssetClient = mockTRSpeedLimitAssetClient
    override lazy val roadLinkService: RoadLinkOTHService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
    override def withDynTransaction[T](f: => T): T = f
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
    val roadAddress = ViiteRoadAddress(1L, 100, 1, Track.RightSide, 5, startAddressMValue, endAddressMValue, None, None, 1L, 5001, 1, 11, SideCode.TowardsDigitizing, false, Seq(), false, None, None, None)
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
    val roadAddress = ViiteRoadAddress(1L, 100, 1, Track.RightSide, 5, startAddressMValue, endAddressMValue, None, None, 1L, 5001, 1, 11, SideCode.AgainstDigitizing, false, Seq(), false, None, None, None)
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
    val roadAddress = ViiteRoadAddress(1L, 100, 1, Track.RightSide, 5, startAddressMValue, endAddressMValue, None, None, 1L, 5001, 1, 11, SideCode.TowardsDigitizing, false, Seq(), false, None, None, None)
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
    val roadAddress = ViiteRoadAddress(1L, 100, 1, Track.RightSide, 5, startAddressMValue, endAddressMValue, None, None, 1L, 5001, 1, 11, SideCode.TowardsDigitizing, false, Seq(), false, None, None, None)
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
    val roadAddress = ViiteRoadAddress(1L, 100, 1, Track.RightSide, 5, startAddressMValue, endAddressMValue, None, None, 1L, 5001, 1, 11, SideCode.AgainstDigitizing, false, Seq(), false, None, None, None)
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
    val roadAddress = ViiteRoadAddress(1L, 100, 1, Track.RightSide, 5, startAddressMValue, endAddressMValue, None, None, 1L, 5001, 1, 11, SideCode.AgainstDigitizing, false, Seq(), false, None, None, None)
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
    val roadAddress = ViiteRoadAddress(1L, 100, 1, Track.RightSide, 5, startAddressMValue, endAddressMValue, None, None, 1L, 5001, 1, 11, SideCode.TowardsDigitizing, false, Seq(), false, None, None, None)
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
    val roadAddress = ViiteRoadAddress(1L, 100, 1, Track.RightSide, 5, startAddressMValue, endAddressMValue, None, None, 1L, 5001, 1, 11, SideCode.AgainstDigitizing, false, Seq(), false, None, None, None)
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
      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, 5, startAddressMValue, endAddressMValue, None, None, 1L, 5001, starAddress, endAddress, SideCode.TowardsDigitizing, false, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

      when(mockAssetDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressDAO.getRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockTRClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockRoadAddressDAO.withRoadAddressSinglePart(any[Long], any[Long], any[Int], any[Long], any[Option[Long]], any[Option[Int]])(any[String])).thenReturn("")
      when(mockRoadAddressDAO.getRoadAddress(any[String => String].apply)).thenReturn(Seq(ra))
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

      val raS1 = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, 5, startMValueSection1, endMValueSection1, None, None, 1L, 5001, starAddressSection1, endAddressSection1, SideCode.TowardsDigitizing, false, Seq(), false, None, None, None)
      val raS2 = ViiteRoadAddress(1L, roadNumber, endRoadPartNumber, Track.RightSide, 5, startMValueSection2, endMValueSection2, None, None, 1L, 5001, starAddressSection2, endAddressSection2, SideCode.TowardsDigitizing, false, Seq(), false, None, None, None)

      val tr = TierekisteriLightingData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startSection, endSection)
      val vvhRoadLink = VVHRoadlink(5001, 235, List(Point(0.0, 0.0), Point(0.0, 1000.0)), State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

      when(mockAssetDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressDAO.getRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockTRClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockRoadAddressDAO.withRoadAddressSinglePart(any[Long], any[Long], any[Int], any[Long], any[Option[Long]], any[Option[Int]])(any[String])).thenReturn("")
      when(mockRoadAddressDAO.getRoadAddress(any[String => String].apply)).thenReturn(Seq(raS1)).thenReturn(Seq(raS2))
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
       val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, 5, startAddressMValue, endAddressMValue, None, None, 1L, 5001, 1.5, 11.4, SideCode.TowardsDigitizing, false, Seq(), false, None, None, None)
       val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

      when(mockAssetDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressDAO.getRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockTRClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockRoadAddressDAO.withRoadAddressSinglePart(any[Long], any[Long], any[Int], any[Long], any[Option[Long]], any[Option[Int]])(any[String])).thenReturn("")
      when(mockRoadAddressDAO.getRoadAddress(any[String => String].apply)).thenReturn(Seq(ra))
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

      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, 5, startAddressMValue, endAddressMValue, None, None, 1L, 5001,starAddress, endAddress, SideCode.TowardsDigitizing, false, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

      when(mockAssetDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressDAO.getRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockTRClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockTRClient.fetchHistoryAssetData(any[Long], any[Option[DateTime]])).thenReturn(Seq(trHist))
      when(mockTRClient.fetchActiveAssetData(any[Long], any[Long])).thenReturn(Seq(trHist))

      when(mockRoadAddressDAO.withRoadAddressSinglePart(any[Long], any[Long], any[Int], any[Long], any[Option[Long]], any[Option[Int]])(any[String])).thenReturn("")
      when(mockRoadAddressDAO.getRoadAddress(any[String => String].apply)).thenReturn(Seq(ra))

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

      val tr = TierekisteriMassTransitLaneData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, trLaneType)
      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, 5, startAddressMValue, endAddressMValue, None, None, 1L, 5001, 1.5, 11.4, SideCode.TowardsDigitizing, false, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

      when(mockAssetDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressDAO.getRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockMassTransitLaneClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockRoadAddressDAO.withRoadAddressSinglePart(any[Long], any[Long], any[Int], any[Long], any[Option[Long]], any[Option[Int]])(any[String])).thenReturn("")
      when(mockRoadAddressDAO.getRoadAddress(any[String => String].apply)).thenReturn(Seq(ra))
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]], any[Boolean])).thenReturn(Seq(vvhRoadLink))

      testMassTransitLane.importAssets()
      val asset = linearAssetDao.fetchLinearAssetsByLinkIds(testMassTransitLane.typeId, Seq(5001), LinearAssetTypes.numericValuePropertyId).head

      asset.linkId should be (5001)
      asset.value should be (Some(NumericValue(1)))
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

      val tr = TierekisteriMassTransitLaneData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startSection, endSection, laneType)
      val trHist = TierekisteriMassTransitLaneData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, starSectionHist, endSectionHist, laneType)

      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, 5, startAddressMValue, endAddressMValue, None, None, 1L, 5001,starAddress, endAddress, SideCode.TowardsDigitizing, false, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

      when(mockAssetDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressDAO.getRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockMassTransitLaneClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockMassTransitLaneClient.fetchHistoryAssetData(any[Long], any[Option[DateTime]])).thenReturn(Seq(trHist))
      when(mockMassTransitLaneClient.fetchActiveAssetData(any[Long], any[Long])).thenReturn(Seq(trHist))

      when(mockRoadAddressDAO.withRoadAddressSinglePart(any[Long], any[Long], any[Int], any[Long], any[Option[Long]], any[Option[Int]])(any[String])).thenReturn("")
      when(mockRoadAddressDAO.getRoadAddress(any[String => String].apply)).thenReturn(Seq(ra))

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

      val tr = TierekisteriPavedRoadData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, TRPavedRoadType.Cobblestone)
      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, 5, startAddressMValue, endAddressMValue, None, None, 1L, 5001, 1.5, 11.4, SideCode.TowardsDigitizing, false, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

      when(mockAssetDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressDAO.getRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockTRPavedRoadClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockRoadAddressDAO.withRoadAddressSinglePart(any[Long], any[Long], any[Int], any[Long], any[Option[Long]], any[Option[Int]])(any[String])).thenReturn("")
      when(mockRoadAddressDAO.getRoadAddress(any[String => String].apply)).thenReturn(Seq(ra))
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

      val tr = TierekisteriPavedRoadData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startSection, endSection, TRPavedRoadType.Cobblestone)
      val trHist = TierekisteriPavedRoadData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, starSectionHist, endSectionHist, TRPavedRoadType.Cobblestone)

      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, 5, startAddressMValue, endAddressMValue, None, None, 1L, 5001,starAddress, endAddress, SideCode.TowardsDigitizing, false, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

      when(mockAssetDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressDAO.getRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockTRPavedRoadClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockTRPavedRoadClient.fetchHistoryAssetData(any[Long], any[Option[DateTime]])).thenReturn(Seq(trHist))
      when(mockTRPavedRoadClient.fetchActiveAssetData(any[Long], any[Long])).thenReturn(Seq(trHist))

      when(mockRoadAddressDAO.withRoadAddressSinglePart(any[Long], any[Long], any[Int], any[Long], any[Option[Long]], any[Option[Int]])(any[String])).thenReturn("")
      when(mockRoadAddressDAO.getRoadAddress(any[String => String].apply)).thenReturn(Seq(ra))

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

      val tr = TierekisteriMassTransitLaneData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startSection, endSection, TRLaneArrangementType.apply(1))
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

      val tr = TierekisteriPavedRoadData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startSection, endSection, TRPavedRoadType.apply(30))
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

      val tr = TierekisteriDamagedByThawData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue)
      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, 5, 100, endAddressMValue, None, None, 1L, 5001, 0, 300, SideCode.TowardsDigitizing, false, Seq(), false, None, None, None)

      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

      when(mockAssetDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressDAO.getRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockTRDamageByThawClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockRoadAddressDAO.withRoadAddressSinglePart(any[Long], any[Long], any[Int], any[Long], any[Option[Long]], any[Option[Int]])(any[String])).thenReturn("")
      when(mockRoadAddressDAO.getRoadAddress(any[String => String].apply)).thenReturn(Seq(ra))
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]], any[Boolean])).thenReturn(Seq(vvhRoadLink))

      testDamageByThaw.importAssets()
      val asset = linearAssetDao.fetchLinearAssetsByLinkIds(testDamageByThaw.typeId, Seq(5001), LinearAssetTypes.numericValuePropertyId)
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
      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, 5, startAddressMValue, endAddressMValue, None, None, 1L, 5001, 1.5, 11.4, SideCode.TowardsDigitizing, false, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

      when(mockAssetDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressDAO.getRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockTREuropeanRoadClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockRoadAddressDAO.withRoadAddressSinglePart(any[Long], any[Long], any[Int], any[Long], any[Option[Long]], any[Option[Int]])(any[String])).thenReturn("")
      when(mockRoadAddressDAO.getRoadAddress(any[String => String].apply)).thenReturn(Seq(ra))
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

      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, 5, startAddressMValue, endAddressMValue, None, None, 1L, 5001, 1.5, 11.4, SideCode.TowardsDigitizing, false, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

      when(mockAssetDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressDAO.getRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockTREuropeanRoadClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockTREuropeanRoadClient.fetchHistoryAssetData(any[Long], any[Option[DateTime]])).thenReturn(Seq(trHist))
      when(mockTREuropeanRoadClient.fetchActiveAssetData(any[Long], any[Long])).thenReturn(Seq(trHist))

      when(mockRoadAddressDAO.withRoadAddressSinglePart(any[Long], any[Long], any[Int], any[Long], any[Option[Long]], any[Option[Int]])(any[String])).thenReturn("")
      when(mockRoadAddressDAO.getRoadAddress(any[String => String].apply)).thenReturn(Seq(ra))

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
    val trAssets = Seq(TierekisteriTrafficSignData(roadNumber, roadPart, roadPart, Track.RightSide, 40, 40, RoadSide.Right, TRTrafficSignType.SpeedLimit, "80"))
    val roadAddress = ViiteRoadAddress(1L, roadNumber, roadPart, Track.RightSide, 5, startAddressMValue, endAddressMValue, None, None, 1L, 5001,starAddress, endAddress, SideCode.TowardsDigitizing, false, Seq(), false, None, None, None)

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
      TierekisteriTrafficSignData(roadNumber, roadPart, roadPart, Track.RightSide, 40, 40, RoadSide.Right, TRTrafficSignType.SpeedLimit, "80"),
      TierekisteriTrafficSignData(roadNumber, roadPart, roadPart, Track.RightSide, 70, 70, RoadSide.Right, TRTrafficSignType.SpeedLimit, "90")
    )
    val roadAddress = ViiteRoadAddress(1L, roadNumber, roadPart, Track.RightSide, 5, startAddressMValue, endAddressMValue, None, None, 1L, 5001,starAddress, endAddress, SideCode.TowardsDigitizing, false, Seq(), false, None, None, None)

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
    val trAssets = Seq(TierekisteriTrafficSignData(roadNumber, roadPart, roadPart, Track.LeftSide, 40, 40, RoadSide.Left, TRTrafficSignType.SpeedLimit, "80"))
    val roadAddress = ViiteRoadAddress(1L, roadNumber, roadPart, Track.LeftSide, 5, startAddressMValue, endAddressMValue, None, None, 1L, 5001,starAddress, endAddress, SideCode.TowardsDigitizing, false, Seq(), false, None, None, None)

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
      TierekisteriTrafficSignData(roadNumber, roadPart, roadPart, Track.LeftSide, 40, 40, RoadSide.Left, TRTrafficSignType.SpeedLimit, "80"),
      TierekisteriTrafficSignData(roadNumber, roadPart, roadPart, Track.LeftSide, 70, 70, RoadSide.Left, TRTrafficSignType.SpeedLimit, "90")
    )
    val roadAddress = ViiteRoadAddress(1L, roadNumber, roadPart, Track.LeftSide, 5, startAddressMValue, endAddressMValue, None, None, 1L, 5001,starAddress, endAddress, SideCode.TowardsDigitizing, false, Seq(), false, None, None, None)

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
      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, 5, startAddressMValue, endAddressMValue, None, None, 1L, 5001, 1.5, 11.4, SideCode.AgainstDigitizing, false, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, None, Map(), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

      when(mockAssetDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressDAO.getRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockTRSpeedLimitAssetClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockRoadAddressDAO.withRoadAddressSinglePart(any[Long], any[Long], any[Int], any[Long], any[Option[Long]], any[Option[Int]])(any[String])).thenReturn("")
      when(mockRoadAddressDAO.getRoadAddress(any[String => String].apply)).thenReturn(Seq(ra))
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]], any[Boolean])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinksAndComplementary(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.getVVHRoadLinksF(any[Int])).thenReturn(Seq(vvhRoadLink))

      testSpeedLimit.importAssets()
      val asset = linearAssetDao.getCurrentSpeedLimitsByLinkIds(Some(Set(5001))).head

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

      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, 5, startAddressMValue, endAddressMValue, None, None, 1L, 5001, 1.5, 11.4, SideCode.AgainstDigitizing, false, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, None, Map(), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

      when(mockAssetDao.expireAssetByTypeAndLinkId(any[Long], any[Seq[Long]])).thenCallRealMethod()
      when(mockAssetDao.getMunicipalities).thenReturn(Seq(235))
      when(mockRoadAddressDAO.getRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockTRSpeedLimitAssetClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockTRSpeedLimitAssetClient.fetchHistoryAssetData(any[Long], any[Option[DateTime]])).thenReturn(Seq(trHist))  /*needed for update*/
      when(mockTRSpeedLimitAssetClient.fetchActiveAssetData(any[Long], any[Long])).thenReturn(Seq(trHist))

      when(mockRoadAddressDAO.withRoadAddressSinglePart(any[Long], any[Long], any[Int], any[Long], any[Option[Long]], any[Option[Int]])(any[String])).thenReturn("")
      when(mockRoadAddressDAO.getRoadAddress(any[String => String].apply)).thenReturn(Seq(ra))
      when(mockRoadLinkService.getVVHRoadLinksF(any[Int])).thenReturn(Seq(vvhRoadLink))

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]], any[Boolean])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinksAndComplementary(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))

      testSpeedLimit.importAssets()
      val assetI = linearAssetDao.getCurrentSpeedLimitsByLinkIds(Some(Set(5001))).head

      testSpeedLimit.updateAssets(DateTime.now())
      val assetU = linearAssetDao.getCurrentSpeedLimitsByLinkIds(Some(Set(5001)))/*.filterNot(a => a.id == assetI.id)*/.head

      assetU.startMeasure should not be (assetI.startMeasure)
      assetU.endMeasure should be (assetI.endMeasure)
      assetU.value should be (Some(NumericValue(assetValueHist)))
    }
  }

  test("Create SpeedLimit using Urban Area TR information, all Address Section inside of Urban Area measures with value 9(Out Urban Area)"){
    TestTransactions.runWithRollback() {
      val testTRSpeedLimit = new TestSpeedLimitsTierekisteriImporter

      val roadNumber = 4L
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 200L
      val startAddressMValue = 0L
      val endAddressMValue = 250L
      val assetValue = "9"
      val roadSide = RoadSide.Left

      //AddressSection Values
      val addressSectionStartAddressMValue = 0L
      val addressSectionEndAddressMValue = 81L

      val trUrbanArea = TierekisteriUrbanAreaData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, assetValue)
      val addressSection = AddressSection(roadNumber, startRoadPartNumber, Track.RightSide, addressSectionStartAddressMValue, Some(addressSectionEndAddressMValue))

      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, 5, startAddressMValue, endAddressMValue, None, None, 1L, 5001, 1.5, 11.4, SideCode.AgainstDigitizing, false, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, None, Map(), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

      val predictedMeasures = testTRSpeedLimit.calculateMeasuresTest(ra, addressSection).head


      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)


      testTRSpeedLimit.createSpeedLimitTest(ra, addressSection, None, Some(vvhRoadLink), Seq(trUrbanArea), roadSide)

      val asset = linearAssetDao.fetchLinearAssetsByLinkIds(310, Seq(5001), LinearAssetTypes.numericValuePropertyId)
      asset.size should be (1)
      asset.head.linkId should be(5001)
      asset.head.sideCode should be(roadSide.value)
      asset.head.value should be(Some(NumericValue(80)))
      asset.head.startMeasure should be(predictedMeasures.startMeasure +- 0.01)
      asset.head.endMeasure should be(predictedMeasures.endMeasure +- 0.01)
      asset.head.createdBy should be(Some("batch_process_speedLimitState"))
    }
  }

  test("Create SpeedLimit using Urban Area TR information, all Address Section inside of Urban Area measures with value 2(In Urban Area)"){
    TestTransactions.runWithRollback() {
      val testTRSpeedLimit = new TestSpeedLimitsTierekisteriImporter

      val roadNumber = 4L
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 200L
      val startAddressMValue = 0L
      val endAddressMValue = 250L
      val assetValue = "2"
      val roadSide = RoadSide.Left

      //AddressSection Values
      val addressSectionStartAddressMValue = 0L
      val addressSectionEndAddressMValue = 81L

      val trUrbanArea = TierekisteriUrbanAreaData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, assetValue)
      val addressSection = AddressSection(roadNumber, startRoadPartNumber, Track.RightSide, addressSectionStartAddressMValue, Some(addressSectionEndAddressMValue))

      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, 5, startAddressMValue, endAddressMValue, None, None, 1L, 5001, 1.5, 11.4, SideCode.AgainstDigitizing, false, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, None, Map(), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

      val predictedMeasures = testTRSpeedLimit.calculateMeasuresTest(ra, addressSection).head


      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)


      testTRSpeedLimit.createSpeedLimitTest(ra, addressSection, None, Some(vvhRoadLink), Seq(trUrbanArea), roadSide)

      val asset = linearAssetDao.fetchLinearAssetsByLinkIds(310, Seq(5001), LinearAssetTypes.numericValuePropertyId)
      asset.size should be (1)
      asset.head.linkId should be(5001)
      asset.head.sideCode should be(roadSide.value)
      asset.head.value should be(Some(NumericValue(50)))
      asset.head.startMeasure should be(predictedMeasures.startMeasure +- 0.01)
      asset.head.endMeasure should be(predictedMeasures.endMeasure +- 0.01)
      asset.head.createdBy should be(Some("batch_process_speedLimitState"))
    }
  }

  test("Create SpeedLimit using Urban Area TR information, doesn't exist information in Urban Area data from TR, create with value out of Urban Area)") {
    TestTransactions.runWithRollback() {
      val testTRSpeedLimit = new TestSpeedLimitsTierekisteriImporter

      val roadNumber = 4L
      val startRoadPartNumber = 200L
      val startAddressMValue = 0L
      val endAddressMValue = 250L
      val assetValue = "2"
      val roadSide = RoadSide.Left

      //AddressSection Values
      val addressStartRoadPartNumber = 230L
      val addressEndRoadPartNumber = 230L
      val addressSectionStartAddressMValue = 0L
      val addressSectionEndAddressMValue = 81L

      val trUrbanArea = TierekisteriUrbanAreaData(roadNumber, addressStartRoadPartNumber, addressEndRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, assetValue)
      val addressSection = AddressSection(roadNumber, startRoadPartNumber, Track.RightSide, addressSectionStartAddressMValue, Some(addressSectionEndAddressMValue))

      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, 5, startAddressMValue, endAddressMValue, None, None, 1L, 5001, 1.5, 11.4, SideCode.AgainstDigitizing, false, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, None, Map(), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

      val predictedMeasures = testTRSpeedLimit.calculateMeasuresTest(ra, addressSection).head


      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)


      testTRSpeedLimit.createSpeedLimitTest(ra, addressSection, None, Some(vvhRoadLink), Seq(trUrbanArea), roadSide)

      val asset = linearAssetDao.fetchLinearAssetsByLinkIds(310, Seq(5001), LinearAssetTypes.numericValuePropertyId)
      asset.size should be (1)
      asset.head.linkId should be(5001)
      asset.head.sideCode should be(roadSide.value)
      asset.head.value should be(Some(NumericValue(80)))
      asset.head.startMeasure should be(predictedMeasures.startMeasure +- 0.01)
      asset.head.endMeasure should be(predictedMeasures.endMeasure +- 0.01)
      asset.head.createdBy should be(Some("batch_process_speedLimitState"))
    }
  }

  test("Create SpeedLimit using Urban Area TR information, initial part of Address Section without info and the rest with Urban Area Info"){
    TestTransactions.runWithRollback() {
      val testTRSpeedLimit = new TestSpeedLimitsTierekisteriImporter

      val roadNumber = 4L
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 200L
      val startAddressMValue = 10L
      val endAddressMValue = 81L
      val assetValue = "2"
      val roadSide = RoadSide.Left

      //AddressSection Values
      val addressSectionStartAddressMValue = 0L
      val addressSectionEndAddressMValue = 81L

      //RoadAddress Values
      val roadAddressStartAddressMValue = 0L
      val roadAddressEndAddressMValue = 250L

      val trUrbanArea = TierekisteriUrbanAreaData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, assetValue)
      val addressSection = AddressSection(roadNumber, startRoadPartNumber, Track.RightSide, addressSectionStartAddressMValue, Some(addressSectionEndAddressMValue))

      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, 5, roadAddressStartAddressMValue, roadAddressEndAddressMValue, None, None, 1L, 5001, 1.5, 11.4, SideCode.AgainstDigitizing, false, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, None, Map(), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      testTRSpeedLimit.createSpeedLimitTest(ra, addressSection, None, Some(vvhRoadLink), Seq(trUrbanArea), roadSide)

      val assets = linearAssetDao.fetchLinearAssetsByLinkIds(310, Seq(5001), LinearAssetTypes.numericValuePropertyId)
      assets.size should be (2)
      assets.foreach{ asset =>
        asset.linkId should be(5001)
        asset.sideCode should be(roadSide.value)
        asset.createdBy should be(Some("batch_process_speedLimitState"))
      }

      assets.sortBy(_.startMeasure)
      assets.head.value should be(Some(NumericValue(80)))
      assets.last.value should be(Some(NumericValue(50)))
    }
  }

  test("Create SpeedLimit using Urban Area TR information, last part of Address Section without info and the rest with Urban Area Info"){
    TestTransactions.runWithRollback() {
      val testTRSpeedLimit = new TestSpeedLimitsTierekisteriImporter

      val roadNumber = 4L
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 200L
      val startAddressMValue = 0L
      val endAddressMValue = 70L
      val assetValue = "2"
      val roadSide = RoadSide.Left

      //AddressSection Values
      val addressSectionStartAddressMValue = 0L
      val addressSectionEndAddressMValue = 81L

      //RoadAddress Values
      val roadAddressStartAddressMValue = 0L
      val roadAddressEndAddressMValue = 250L

      val trUrbanArea = TierekisteriUrbanAreaData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, assetValue)
      val addressSection = AddressSection(roadNumber, startRoadPartNumber, Track.RightSide, addressSectionStartAddressMValue, Some(addressSectionEndAddressMValue))

      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, 5, roadAddressStartAddressMValue, roadAddressEndAddressMValue, None, None, 1L, 5001, 1.5, 11.4, SideCode.AgainstDigitizing, false, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, None, Map(), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)


      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)


      testTRSpeedLimit.createSpeedLimitTest(ra, addressSection, None, Some(vvhRoadLink), Seq(trUrbanArea), roadSide)

      val assets = linearAssetDao.fetchLinearAssetsByLinkIds(310, Seq(5001), LinearAssetTypes.numericValuePropertyId)
      assets.size should be (2)
      assets.foreach{ asset =>
        asset.linkId should be(5001)
        asset.sideCode should be(roadSide.value)
        asset.createdBy should be(Some("batch_process_speedLimitState"))
      }
      assets.sortBy(_.startMeasure)
      assets.head.value should be(Some(NumericValue(50)))
      assets.last.value should be(Some(NumericValue(80)))
    }
  }

  test("Create SpeedLimit using Urban Area TR information, initial and last part of Address Section without info and the middle with Urban Area Info"){
    TestTransactions.runWithRollback() {
      val testTRSpeedLimit = new TestSpeedLimitsTierekisteriImporter

      val roadNumber = 4L
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 200L
      val startAddressMValue = 10L
      val endAddressMValue = 70L
      val assetValue = "2"
      val roadSide = RoadSide.Left

      //AddressSection Values
      val addressSectionStartAddressMValue = 0L
      val addressSectionEndAddressMValue = 81L

      //RoadAddress Values
      val roadAddressStartAddressMValue = 0L
      val roadAddressEndAddressMValue = 250L

      val trUrbanArea = TierekisteriUrbanAreaData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, assetValue)
      val addressSection = AddressSection(roadNumber, startRoadPartNumber, Track.RightSide, addressSectionStartAddressMValue, Some(addressSectionEndAddressMValue))

      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, 5, roadAddressStartAddressMValue, roadAddressEndAddressMValue, None, None, 1L, 5001, 1.5, 11.4, SideCode.AgainstDigitizing, false, Seq(), false, None, None, None)
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, None, Map(), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)


      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)


      testTRSpeedLimit.createSpeedLimitTest(ra, addressSection, None, Some(vvhRoadLink), Seq(trUrbanArea), roadSide)

      val assets = linearAssetDao.fetchLinearAssetsByLinkIds(310, Seq(5001), LinearAssetTypes.numericValuePropertyId)

      assets.size should be (3)
      assets.foreach{ asset =>
        asset.linkId should be(5001)
        asset.sideCode should be(roadSide.value)
        asset.createdBy should be(Some("batch_process_speedLimitState"))
      }

      assets.sortBy(_.startMeasure)
      assets.head.value should be(Some(NumericValue(80)))
      assets.tail.head.value should be(Some(NumericValue(50)))
      assets.last.value should be(Some(NumericValue(80)))
    }
  }
}

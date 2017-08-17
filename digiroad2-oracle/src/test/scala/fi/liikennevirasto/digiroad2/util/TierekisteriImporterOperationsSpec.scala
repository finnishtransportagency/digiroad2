package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.{TierekisteriLightingAssetClient, TierekisteriMassTransitLaneAssetClient, _}
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
  val mockRoadLinkService: RoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient: VVHClient = MockitoSugar.mock[VVHClient]
  val mockVVHRoadLinkClient: VVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val linearAssetDao = new OracleLinearAssetDao(mockVVHClient, mockRoadLinkService)
  val mockTrImporter: TierekisteriDataImporter = MockitoSugar.mock[TierekisteriDataImporter]
  val mockTRPavedRoadClient: TierekisteriPavedRoadAssetClient = MockitoSugar.mock[TierekisteriPavedRoadAssetClient]
  val mockMassTransitLaneTRClient: TierekisteriMassTransitLaneAssetClient = MockitoSugar.mock[TierekisteriMassTransitLaneAssetClient]
  val mockTRDamageByThawClient: TierekisteriDamagedByThawAssetClient = MockitoSugar.mock[TierekisteriDamagedByThawAssetClient]
  val mockTREuropeanRoadClient: TierekisteriEuropeanRoadAssetClient = MockitoSugar.mock[TierekisteriEuropeanRoadAssetClient]


  lazy val roadWidthImporterOperations: RoadWidthTierekisteriImporter = {
    new RoadWidthTierekisteriImporter()
  }

  lazy val litRoadImporterOperations: LitRoadTierekisteriImporter = {
    new LitRoadTierekisteriImporter()
  }

  lazy val tierekisteriAssetImporterOperations: TestTierekisteriAssetImporterOperations = {
    new TestTierekisteriAssetImporterOperations
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
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient

    //Creating this new methods because is protected visibility on the trait
    def getRoadAddressSectionsTest(trAsset: TierekisteriAssetData): Seq[(AddressSection, TierekisteriAssetData)] = super.getRoadAddressSections(trAsset)
    def getAllViiteRoadAddressTest(section: AddressSection) = super.getAllViiteRoadAddress(section)
    def getAllViiteRoadAddressTest(roadNumber: Long, roadPart: Long, track: Track) = super.getAllViiteRoadAddress(roadNumber, roadPart, track)
    def expireAssetsTest(linkIds: Seq[Long]): Unit = super.expireAssets(linkIds)
    def calculateStartLrmByAddressTest(startAddress: ViiteRoadAddress, section: AddressSection): Option[Double] = super.calculateStartLrmByAddress(startAddress, section)
    def calculateEndLrmByAddressTest(endAddress: ViiteRoadAddress, section: AddressSection): Option[Double] = super.calculateEndLrmByAddress(endAddress, section)

    override protected def createAsset(section: AddressSection, trAssetData: TierekisteriAssetData): Unit = {

    }
  }

  class TestLitRoadOperations extends LitRoadTierekisteriImporter {
    override lazy val assetDao: OracleAssetDao = mockAssetDao
    override lazy val roadAddressDao: RoadAddressDAO = mockRoadAddressDAO
    override val tierekisteriClient: TierekisteriLightingAssetClient = mockTRClient
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
    override def withDynTransaction[T](f: => T): T = f
  }

  class TestPavedRoadOperations extends PavedRoadTierekisteriImporter {
    override lazy val assetDao: OracleAssetDao = mockAssetDao
    override lazy val roadAddressDao: RoadAddressDAO = mockRoadAddressDAO
    override val tierekisteriClient: TierekisteriPavedRoadAssetClient = mockTRPavedRoadClient
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
    override def withDynTransaction[T](f: => T): T = f
  }

  class TestMassTransitLaneOperations extends MassTransitLaneTierekisteriImporter {

    override lazy val assetDao: OracleAssetDao = mockAssetDao
    override lazy val roadAddressDao: RoadAddressDAO = mockRoadAddressDAO
    override val tierekisteriClient: TierekisteriMassTransitLaneAssetClient = mockMassTransitLaneTRClient
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
    override def withDynTransaction[T](f: => T): T = f
  }

  class TestDamageByThawOperations extends DamagedByThawAssetTierekisteriImporter {
    override lazy val assetDao: OracleAssetDao = mockAssetDao
    override lazy val roadAddressDao: RoadAddressDAO = mockRoadAddressDAO
    override val tierekisteriClient: TierekisteriDamagedByThawAssetClient = mockTRDamageByThawClient
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
    override def withDynTransaction[T](f: => T): T = f
  }

  class TestEuropeanRoadOperations extends EuropeanRoadTierekisteriImporter {
    override lazy val assetDao: OracleAssetDao = mockAssetDao
    override lazy val roadAddressDao: RoadAddressDAO = mockRoadAddressDAO
    override val tierekisteriClient: TierekisteriEuropeanRoadAssetClient = mockTREuropeanRoadClient
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
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
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))

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
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))

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
       when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))

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
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))

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
      when(mockMassTransitLaneTRClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockRoadAddressDAO.withRoadAddressSinglePart(any[Long], any[Long], any[Int], any[Long], any[Option[Long]], any[Option[Int]])(any[String])).thenReturn("")
      when(mockRoadAddressDAO.getRoadAddress(any[String => String].apply)).thenReturn(Seq(ra))
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))

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
      when(mockMassTransitLaneTRClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockMassTransitLaneTRClient.fetchHistoryAssetData(any[Long], any[Option[DateTime]])).thenReturn(Seq(trHist))
      when(mockMassTransitLaneTRClient.fetchActiveAssetData(any[Long], any[Long])).thenReturn(Seq(trHist))

      when(mockRoadAddressDAO.withRoadAddressSinglePart(any[Long], any[Long], any[Int], any[Long], any[Option[Long]], any[Option[Int]])(any[String])).thenReturn("")
      when(mockRoadAddressDAO.getRoadAddress(any[String => String].apply)).thenReturn(Seq(ra))

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))

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
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))

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
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))

      testPavedRoad.importAssets()
      val assetI = linearAssetDao.fetchLinearAssetsByLinkIds(testPavedRoad.typeId, Seq(5001), LinearAssetTypes.numericValuePropertyId).head

      testPavedRoad.updateAssets(DateTime.now())
      val assetU = linearAssetDao.fetchLinearAssetsByLinkIds(testPavedRoad.typeId, Seq(5001), LinearAssetTypes.numericValuePropertyId).filterNot(a => a.id == assetI.id).head

      assetU.startMeasure should not be assetI.startMeasure
      assetU.endMeasure should be (assetI.endMeasure)
    }
  }

  test("Should not create asset with TR type Unknown") {
    TestTransactions.runWithRollback() {

      val testPavedRoad = new TestPavedRoadOperations
      val roadNumber = 4L
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 200L
      val startAddressMValue = 0L
      val endAddressMValue = 250L

      val tr = TierekisteriPavedRoadData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue, TRPavedRoadType.Unknown)
      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, 5, 100, endAddressMValue, None, None, 1L, 5001, 0, 300, SideCode.TowardsDigitizing, false, Seq(), false, None, None, None)

      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

      when(mockAssetDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressDAO.getRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockTRPavedRoadClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockRoadAddressDAO.withRoadAddressSinglePart(any[Long], any[Long], any[Int], any[Long], any[Option[Long]], any[Option[Int]])(any[String])).thenReturn("")
      when(mockRoadAddressDAO.getRoadAddress(any[String => String].apply)).thenReturn(Seq(ra))
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))

      testPavedRoad.importAssets()
      val asset = linearAssetDao.fetchLinearAssetsByLinkIds(testPavedRoad.typeId, Seq(5001), LinearAssetTypes.numericValuePropertyId)
      asset.length should be(0)
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
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))

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
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))

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
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))

      testEuropeanRoad.importAssets()
      val assetI = linearAssetDao.fetchAssetsWithTextualValuesByLinkIds(testEuropeanRoad.typeId, Seq(5001), LinearAssetTypes.europeanRoadPropertyId).head

      testEuropeanRoad.updateAssets(DateTime.now())
      val assetU = linearAssetDao.fetchAssetsWithTextualValuesByLinkIds(testEuropeanRoad.typeId, Seq(5001), LinearAssetTypes.europeanRoadPropertyId).filterNot(a => a.id == assetI.id).head

      assetU.startMeasure should be (assetI.startMeasure)
      assetU.endMeasure should not be assetI.endMeasure
      assetU.value should be (Some(TextualValue(assetValueHist)))
    }
  }
}

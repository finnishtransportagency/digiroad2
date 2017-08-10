package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.oracle.OracleAssetDao
import fi.liikennevirasto.digiroad2.linearasset.NumericValue
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

  class TestLitRoadOperations extends LitRoadTierekisteriImporter {
    override lazy val assetDao: OracleAssetDao = mockAssetDao
    override lazy val roadAddressDao: RoadAddressDAO = mockRoadAddressDAO
    override val tierekisteriClient: TierekisteriLightingAssetClient = mockTRClient
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
    override def withDynTransaction[T](f: => T): T = f
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

  test("import assets (litRoad) from TR to OTH"){
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

      asset.linkId should be (5001)
      asset.value should be (Some(NumericValue(1)))
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

      val starSectionHist = 100
      val endSectionHist = 150

      val tr = TierekisteriLightingData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startSection, endSection)
      val trHist = TierekisteriLightingData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, starSectionHist, endSectionHist)

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
}

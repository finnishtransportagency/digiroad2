package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.oracle.OracleAssetDao
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.roadaddress.oracle.{RoadAddressDAO, RoadAddress => ViiteRoadAddress}
import org.joda.time.DateTime
import org.mockito.Matchers.{any, _}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import org.mockito.Mockito._


class TierekisteriImporterOperationsSpec extends FunSuite with Matchers  {

  val mocAssetDao: OracleAssetDao = MockitoSugar.mock[OracleAssetDao]
  val mockRoadAddressDAO: RoadAddressDAO = MockitoSugar.mock[RoadAddressDAO]
  val mockTRClient: TierekisteriLightingAssetClient = MockitoSugar.mock[TierekisteriLightingAssetClient]
  val mockRoadLinkService: RoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient: VVHClient = MockitoSugar.mock[VVHClient]
  val mockVVHRoadLinkClient: VVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val linearAssetDao = new OracleLinearAssetDao(mockVVHClient, mockRoadLinkService)
  val mockTrImporter: TierekisteriDataImporter = MockitoSugar.mock[TierekisteriDataImporter]

  class TestLitRoadOperations extends LitRoadTierekisteriImporter {
    override lazy val assetDao: OracleAssetDao = mocAssetDao
    override lazy val roadAddressDao: RoadAddressDAO = mockRoadAddressDAO
    override val tierekisteriClient: TierekisteriLightingAssetClient = mockTRClient
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
  }

  test("import assets from TR to OTH"){
    TestTransactions.runWithRollback() {

      val testLitRoad = new TestLitRoadOperations
      val roadNumber = 4L
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 200L
      val startAddressMValue = 0L
      val endAddressMValue = 250L

      val tr = TierekisteriLightingData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue)
      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, 5, startAddressMValue, endAddressMValue, None, None, 1L, 5001, 1.5, 11.4, SideCode.TowardsDigitizing, false, Seq())
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

      when(mocAssetDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressDAO.getRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockTRClient.fetchActiveAssetData(any[Long])).thenReturn(Seq(tr))
      when(mockRoadAddressDAO.withRoadAddressSinglePart(any[Long], any[Long], any[Int], any[Long], any[Option[Long]], any[Option[Int]])(any[String])).thenReturn("")
      when(mockRoadAddressDAO.getRoadAddress(any[String => String].apply)).thenReturn(Seq(ra))
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))

      testLitRoad.importAssets()
      val asset = linearAssetDao.fetchLinearAssetsByLinkIds(testLitRoad.typeId, Seq(5001), LinearAssetTypes.numericValuePropertyId)

      asset.length should not be 0
    }
  }

  test("update assets from TR to OTH"){
    TestTransactions.runWithRollback() {

      val testLitRoad = new TestLitRoadOperations
      val roadNumber = 4L
      val startRoadPartNumber = 200L
      val endRoadPartNumber = 200L
      val startAddressMValue = 0L
      val endAddressMValue = 250L

      val tr = TierekisteriLightingData(roadNumber, startRoadPartNumber, endRoadPartNumber, Track.RightSide, startAddressMValue, endAddressMValue)
      val ra = ViiteRoadAddress(1L, roadNumber, startRoadPartNumber, Track.RightSide, 5, startAddressMValue, endAddressMValue, None, None, 1L, 5001, 1.5, 11.4, SideCode.TowardsDigitizing, false, Seq())
      val vvhRoadLink = VVHRoadlink(5001, 235, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)
      val lastUpdate = mockTrImporter.obtainLastExecutionDate(testLitRoad.assetName, testLitRoad.typeId)

      when(mocAssetDao.getMunicipalities).thenReturn(Seq())
      when(mockRoadAddressDAO.getRoadNumbers()).thenReturn(Seq(roadNumber))
      when(mockTRClient.fetchHistoryAssetData(any[Long], any[Option[DateTime]])).thenReturn(Seq(tr))
      when(mockTRClient.fetchActiveAssetData(any[Long], any[Long])).thenReturn(Seq(tr))
      when(mockRoadAddressDAO.withRoadAddressSinglePart(any[Long], any[Long], any[Int], any[Long], any[Option[Long]], any[Option[Int]])(any[String])).thenReturn("")
      when(mockRoadAddressDAO.getRoadAddress(any[String => String].apply)).thenReturn(Seq(ra))
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]])).thenReturn(Seq(vvhRoadLink))

      testLitRoad.updateAssets(lastUpdate)
      val asset = linearAssetDao.fetchLinearAssetsByLinkIds(testLitRoad.typeId, Seq(5001), LinearAssetTypes.numericValuePropertyId)

      asset.length should not be 0
    }
  }
}

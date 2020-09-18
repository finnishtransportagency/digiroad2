package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, VVHClient, VVHRoadLinkClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, OracleAssetDao}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{PolygonTools, TestTransactions}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class LengthOfRoadAxisSpecSupport extends FunSuite with Matchers {
  val mockRoadLinkService: RoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockLengthOfRoadAxisService: LengthOfRoadAxisService = MockitoSugar.mock[LengthOfRoadAxisService]
  val mockVVHClient: VVHClient = MockitoSugar.mock[VVHClient]
  val mockVVHRoadLinkClient: VVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val mockPolygonTools: PolygonTools = MockitoSugar.mock[PolygonTools]

  when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
  when(mockVVHRoadLinkClient.fetchByLinkId(388562360L))
    .thenReturn(Some(VVHRoadlink(388562360L, 235, Seq(Point(0, 0), Point(10, 0)),
      Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]]))
    .thenReturn(Seq(VVHRoadlink(388562360L, 235, Seq(Point(0, 0), Point(10, 0)),
      Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockVVHClient.fetchRoadLinkByLinkId(any[Long]))
    .thenReturn(Some(VVHRoadlink(388562360L, 235, Seq(Point(0, 0), Point(10, 0)),
      Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))

  val roadLinkWithLinkSource: RoadLink = RoadLink(
      1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None,
      Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
  //when(mockLengthOfRoadAxisService.createRaodwayLinear())

  val mockLinearAssetDao: OracleLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]
  val mockEventBus: DigiroadEventBus = MockitoSugar.mock[DigiroadEventBus]
  //val linearAssetDao = new OracleLinearAssetDao(mockVVHClient, mockLengthOfRoadAxisService)
  val linearAssetDao = new OracleLinearAssetDao(mockVVHClient, mockRoadLinkService)
  val mockMunicipalityDao: MunicipalityDao = MockitoSugar.mock[MunicipalityDao]
  val mockAssetDao: OracleAssetDao = MockitoSugar.mock[OracleAssetDao]

  object PassThroughService extends LinearAssetOperations {
    override def withDynTransaction[T](f: => T): T = f
    def lengthOfRoadAxisService: LengthOfRoadAxisService = mockLengthOfRoadAxisService
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: OracleLinearAssetDao = mockLinearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def vvhClient: VVHClient = mockVVHClient
    override def polygonTools: PolygonTools = mockPolygonTools
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao
    override def assetDao: OracleAssetDao = mockAssetDao

    override def getUncheckedLinearAssets(areas: Option[Set[Int]]) =
        throw new UnsupportedOperationException("Not supported method")
    override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass]
        = Set()) = throw new UnsupportedOperationException("Not supported method")


  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(PassThroughService.dataSource)(test)

}

class LengthOfRoadAxisServiceSpec extends LengthOfRoadAxisSpecSupport {


  // create new asset
 test ("create new LengthOfRoadAxis asset"){

 }


  // get new asset
  test ("get new LengthOfRoadAxis asset"){

  }

  // update new asset

  test ("update new LengthOfRoadAxis asset"){

  }

  // delete new asset

  test ("delete new LengthOfRoadAxis asset"){

  }

}



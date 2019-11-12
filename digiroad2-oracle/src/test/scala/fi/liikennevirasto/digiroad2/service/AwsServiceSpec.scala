package fi.liikennevirasto.digiroad2.service

import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.{AssetService, Point}
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.AwsDao
import fi.liikennevirasto.digiroad2.linearasset.{NumericValue, PersistedLinearAsset}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.linearasset._
import fi.liikennevirasto.digiroad2.service.pointasset._
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}

class AwsServiceSpec extends FunSuite with Matchers with BeforeAndAfter {
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockOnOffLinearAssetService = MockitoSugar.mock[OnOffLinearAssetService]
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mocklinearAssetService = MockitoSugar.mock[LinearAssetService]
  val mockObstacleService = MockitoSugar.mock[ObstacleService]
  val mockAssetService = MockitoSugar.mock[AssetService]
  val mockSpeedLimitService = MockitoSugar.mock[SpeedLimitService]
  val mockPavedRoadService = MockitoSugar.mock[PavedRoadService]
  val mockRoadWidthService = MockitoSugar.mock[RoadWidthService]
  val mockManoeuvreService = MockitoSugar.mock[ManoeuvreService]
  val mockPedestrianCrossingService = MockitoSugar.mock[PedestrianCrossingService]
  val mockRailwayCrossingService = MockitoSugar.mock[RailwayCrossingService]
  val mockTrafficLightService = MockitoSugar.mock[TrafficLightService]
  val mockMassTransitLaneService = MockitoSugar.mock[MassTransitLaneService]
  val mockNumberOfLanesService = MockitoSugar.mock[NumberOfLanesService]


  lazy val dataSource = {
    val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/bonecp.properties"))
    new BoneCPDataSource(cfg)
  }

  object ServiceWithDao extends AwsService(mockVVHClient, mockOnOffLinearAssetService, mockRoadLinkService, mocklinearAssetService, mockSpeedLimitService, mockPavedRoadService, mockRoadWidthService, mockManoeuvreService, mockAssetService, mockObstacleService, mockPedestrianCrossingService, mockRailwayCrossingService, mockTrafficLightService, mockMassTransitLaneService, mockNumberOfLanesService){
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
    override def awsDao: AwsDao = new AwsDao
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(dataSource)(test)

//  test("validate and insert Dataset on database") {
//    val geoJson =
//    val dataset = Dataset(Some("0a852cf2-15b3-4f33-bdbe-1af89751bfaf"), Option[Map[Any, Any]], Option[List[List[BigInt]]])
//
//    runWithRollback {
//      ServiceWithDao.validateAndInsertDataset(dataset)
//    }
//
//  }
//
//  test("create new linear asset") {
//    runWithRollback {
//
//    }

//  }

}

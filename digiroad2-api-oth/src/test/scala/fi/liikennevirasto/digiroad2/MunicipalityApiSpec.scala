package fi.liikennevirasto.digiroad2

import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.FeatureClass.AllOthers
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadLinkClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.AwsDao
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, _}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset._
import fi.liikennevirasto.digiroad2.service.pointasset.{HeightLimit => _, WidthLimit => _, _}
import javax.sql.DataSource
import org.json4s.{DefaultFormats, Formats}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession

object sTestTransactions {
  def runWithRollback(ds: DataSource = OracleDatabase.ds)(f: => Unit): Unit = {
    Database.forDataSource(ds).withDynTransaction {
      f
      dynamicSession.rollback()
    }
  }
  def withDynTransaction[T](ds: DataSource = OracleDatabase.ds)(f: => T): T = {
    Database.forDataSource(ds).withDynTransaction {
      f
    }
  }
  def withDynSession[T](ds: DataSource = OracleDatabase.ds)(f: => T): T = {
    Database.forDataSource(ds).withDynSession {
      f
    }
  }
}

class MunicipalityApiSpec extends FunSuite with Matchers with BeforeAndAfter {

  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val obstacleService = new ObstacleService(mockRoadLinkService)
  val speedLimitService = new SpeedLimitService(new DummyEventBus, mockVVHClient, mockRoadLinkService)
  val pavedRoadService = new PavedRoadService(mockRoadLinkService, new DummyEventBus)


  lazy val dataSource = {
    val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/bonecp.properties"))
    new BoneCPDataSource(cfg)
  }

  protected implicit val jsonFormats: Formats = DefaultFormats

  val dataSetId = "ab70d6a9-9616-4cc4-abbe-6272c2344709"
  val roadLinksList: List[List[Long]] = List(List(441062, 441063, 441070, 452512), List(445212))

  val commonLinearProperties: Map[String, String] = Map("name" -> "Mannerheimintie", "pavementClass" -> "1", "speedLimit" -> "100", "sideCode" -> "1", "id" -> "100001", "functionalClass" -> "Katu", "type" -> "Roadlink")
  val commonPointProperties: Map[String, String] = Map("id" -> "100000", "type" -> "obstacle", "class" -> "1")

  val commonLinearGeometry: Geometry = Geometry("LineString", List(List(384594.081, 6674141.478, 105.55299999999988), List(384653.656, 6674029.718, 106.02099999999336), List(384731.654, 6673901.8, 106.37600000000384), List(384919.538, 6673638.735, 106.51600000000326)), Map(("type", "name")))
  val commonPointGeometry: Geometry = Geometry("Point", List(List(385786, 6671390, 0)), Map(("type", "name")))

  val commonPointFeature: Feature = Feature("Feature", commonPointGeometry, commonPointProperties)
  val commonLinearFeature: Feature = Feature("Feature", commonLinearGeometry, commonLinearProperties)

  val commonFeatureCollection: FeatureCollection = FeatureCollection("FeatureCollection", List(commonLinearFeature, commonPointFeature))

  object ServiceWithDao extends MunicipalityApi(mockVVHClient, mockRoadLinkService, speedLimitService, pavedRoadService, obstacleService, new OthSwagger){
    override def awsDao: AwsDao = new AwsDao
  }
  def runWithRollback(test: => Unit): Unit = sTestTransactions.runWithRollback()(test)

  test("number of features doesn't match with the number of list of road links give") {

    val wrongRoadLinksList: List[List[Long]] = List(List(441062, 441063, 441070, 452512))
    val dataSet = Dataset(dataSetId, commonFeatureCollection, wrongRoadLinksList)

    runWithRollback {
      ServiceWithDao.validateAndInsertDataset(dataSet)
      val datasetStatus = ServiceWithDao.awsDao.getDatasetStatus(dataSetId)
      datasetStatus should be(1)
    }
  }

  test("validate if features have id key/value") {
    val newRoadLinks = Seq(RoadLink(5000L, List(Point(0.0, 0.0), Point(100.0, 0.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))))
    when(mockRoadLinkService.getRoadsLinksFromVVH(Set(5000), false)).thenReturn(newRoadLinks)
    val pointProperties: Map[String, String] = Map("type" -> "obstacle", "class" -> "1")
    val pointGeometry: Geometry = Geometry("Point", List(List(385786, 6671390, 0)), Map(("type", "name")))
    val pointFeature: Feature = Feature("Feature", pointGeometry, pointProperties)
    val featureCollection: FeatureCollection = FeatureCollection("FeatureCollection", List(pointFeature))
    val roadLinksList: List[List[Long]] = List(List(5000))

    val dataSet = Dataset(dataSetId, featureCollection, roadLinksList)

    runWithRollback {
      val numberOfFeaturesWithoutId = ServiceWithDao.validateAndInsertDataset(dataSet)
      val datasetStatus = ServiceWithDao.awsDao.getDatasetStatus(dataSetId)

      numberOfFeaturesWithoutId should be (Some(1))
      datasetStatus should be(2)
    }
  }

  test("validate if roadLink exists on VVH") {
    when(mockRoadLinkService.getRoadsLinksFromVVH(Set(5, 10), false)).thenReturn(Seq())

    val roadLinksList: List[List[Long]] = List(List(5),List(10))
    val dataSet = Dataset(dataSetId, commonFeatureCollection, roadLinksList)

    runWithRollback {
      val numberOfFeaturesWithoutId = ServiceWithDao.validateAndInsertDataset(dataSet)
      val datasetStatus = ServiceWithDao.awsDao.getDatasetStatus(dataSetId)
      val featuresStatus = ServiceWithDao.awsDao.getAllFeatureIdAndStatusByDataset(dataSetId)

      numberOfFeaturesWithoutId should be(None)
      datasetStatus should be(2)
      featuresStatus.sortBy(status => status._1) should be (List(("100000","6"), ("100001","6")))
    }
  }

  test("validate if the Geometry Type is one of the allowed") {
    val newRoadLinks = Seq(RoadLink(5000L, List(Point(0.0, 0.0), Point(100.0, 0.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))))
    when(mockRoadLinkService.getRoadsLinksFromVVH(Set(5000), false)).thenReturn(newRoadLinks)

    val roadLinksList: List[List[Long]] = List(List(5000))
    val pointGeometry: Geometry = Geometry("WrongGeometryType", List(List(385786, 6671390, 0)), Map(("type", "name")))
    val pointFeature: Feature = Feature("Feature", pointGeometry, commonPointProperties)
    val featureCollection: FeatureCollection = FeatureCollection("FeatureCollection", List(pointFeature))

    val dataSet = Dataset(dataSetId, featureCollection, roadLinksList)

    runWithRollback {
      val numberOfFeaturesWithoutId = ServiceWithDao.validateAndInsertDataset(dataSet)
      val datasetStatus = ServiceWithDao.awsDao.getDatasetStatus(dataSetId)
      val featuresStatus = ServiceWithDao.awsDao.getAllFeatureIdAndStatusByDataset(dataSetId)

      numberOfFeaturesWithoutId should be(None)
      datasetStatus should be(2)
      featuresStatus should be (List(("100000","3")))
    }
  }

  test("new obstacle with nonvalid value to be created/updated") {
    val newRoadLinks = Seq(RoadLink(5000L, List(Point(0.0, 0.0), Point(100.0, 0.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))))
    when(mockRoadLinkService.getRoadsLinksFromVVH(Set(5000), false)).thenReturn(newRoadLinks)

    val roadLinksList: List[List[Long]] = List(List(5000))
    val pointProperties: Map[String, String] = Map("id" -> "100000", "type" -> "obstacle", "class" -> "10")
    val pointFeature: Feature = Feature("Feature", commonPointGeometry, pointProperties)
    val featureCollection: FeatureCollection = FeatureCollection("FeatureCollection", List(pointFeature))

    val dataSet = Dataset(dataSetId, featureCollection, roadLinksList)

    runWithRollback {
      val numberOfFeaturesWithoutId = ServiceWithDao.validateAndInsertDataset(dataSet)
      val datasetStatus = ServiceWithDao.awsDao.getDatasetStatus(dataSetId)
      val featuresStatus = ServiceWithDao.awsDao.getAllFeatureIdAndStatusByDataset(dataSetId)

      numberOfFeaturesWithoutId should be(None)
      datasetStatus should be(2)
      featuresStatus should be (List(("100000","2")))
    }
  }

  test("new speedlimit with nonvalid value to be created/updated") {
    val newRoadLinks = Seq(RoadLink(5000L, List(Point(0.0, 0.0), Point(100.0, 0.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))))
    when(mockRoadLinkService.getRoadsLinksFromVVH(Set(5000), false)).thenReturn(newRoadLinks)

    val roadLinksList: List[List[Long]] = List(List(5000))
    val linearProperties: Map[String, String] = Map("name" -> "Mannerheimintie", "speedLimit" -> "210", "sideCode" -> "1", "id" -> "200000", "functionalClass" -> "Katu", "type" -> "Roadlink")
    val linearFeature: Feature = Feature("Feature", commonLinearGeometry, linearProperties)
    val featureCollection: FeatureCollection = FeatureCollection("FeatureCollection", List(linearFeature))

    val dataSet = Dataset(dataSetId, featureCollection, roadLinksList)

    runWithRollback {
      val numberOfFeaturesWithoutId = ServiceWithDao.validateAndInsertDataset(dataSet)
      val datasetStatus = ServiceWithDao.awsDao.getDatasetStatus(dataSetId)
      val featuresStatus = ServiceWithDao.awsDao.getAllFeatureIdAndStatusByDataset(dataSetId)

      numberOfFeaturesWithoutId should be(None)
      datasetStatus should be(2)
      featuresStatus should be (List(("200000","2")))
    }
  }

  test("new pavementClass with nonvalid value to be created/updated") {
    val newRoadLinks = Seq(RoadLink(5000L, List(Point(0.0, 0.0), Point(100.0, 0.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))))
    when(mockRoadLinkService.getRoadsLinksFromVVH(Set(5000), false)).thenReturn(newRoadLinks)

    val roadLinksList: List[List[Long]] = List(List(5000))
    val linearProperties: Map[String, String] = Map("name" -> "Mannerheimintie", "pavementClass" -> "100", "sideCode" -> "1", "id" -> "200000", "functionalClass" -> "Katu", "type" -> "Roadlink")
    val linearFeature: Feature = Feature("Feature", commonLinearGeometry, linearProperties)
    val featureCollection: FeatureCollection = FeatureCollection("FeatureCollection", List(linearFeature))

    val dataSet = Dataset(dataSetId, featureCollection, roadLinksList)

    runWithRollback {
      val numberOfFeaturesWithoutId = ServiceWithDao.validateAndInsertDataset(dataSet)
      val datasetStatus = ServiceWithDao.awsDao.getDatasetStatus(dataSetId)
      val featuresStatus = ServiceWithDao.awsDao.getAllFeatureIdAndStatusByDataset(dataSetId)

      numberOfFeaturesWithoutId should be(None)
      datasetStatus should be(2)
      featuresStatus should be (List(("200000","2")))
    }
  }

  test("new speedlimit with valid value to be created") {
    val newRoadLink = RoadLink(5000L, List(Point(0.0, 0.0), Point(100.0, 0.0)), 100.0, Municipality, 1, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val newVVHroadLink = VVHRoadlink(5000L, 235, List(Point(0.0, 0.0), Point(100.0, 0.0)), Municipality, TrafficDirection.BothDirections, AllOthers)
    when(mockRoadLinkService.getRoadsLinksFromVVH(Set(5000), false)).thenReturn(Seq(newRoadLink))
    when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(5000), false)).thenReturn(Seq(newRoadLink))
    when(mockRoadLinkService.fetchVVHRoadlinkAndComplementary(5000)).thenReturn(Some(newVVHroadLink))


    when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
    val timeStamp = new VVHRoadLinkClient("http://localhost:6080").createVVHTimeStamp()
    when(mockVVHRoadLinkClient.createVVHTimeStamp(any[Int])).thenReturn(timeStamp)

    val roadLinksList: List[List[Long]] = List(List(5000))
    val linearProperties: Map[String, String] = Map("name" -> "Mannerheimintie", "speedLimit" -> "100", "sideCode" -> "1", "id" -> "200000", "functionalClass" -> "Katu", "type" -> "Roadlink")
    val linearFeature: Feature = Feature("Feature", commonLinearGeometry, linearProperties)
    val featureCollection: FeatureCollection = FeatureCollection("FeatureCollection", List(linearFeature))

    val dataSet = Dataset(dataSetId, featureCollection, roadLinksList)

    runWithRollback {
      val numberOfFeaturesWithoutId = ServiceWithDao.validateAndInsertDataset(dataSet)
      val datasetStatus = ServiceWithDao.awsDao.getDatasetStatus(dataSetId)
      val featuresStatus = ServiceWithDao.awsDao.getAllFeatureIdAndStatusByDataset(dataSetId)

      numberOfFeaturesWithoutId should be(None)
      datasetStatus should be(0)
      featuresStatus should be (List(("200000","0")))

      ServiceWithDao.updateDataset(dataSet)
      val datasetStatus2 = ServiceWithDao.awsDao.getDatasetStatus(dataSetId)
      val featuresStatus2 = ServiceWithDao.awsDao.getAllFeatureIdAndStatusByDataset(dataSetId)
      val createdSpeedLimit = speedLimitService.getExistingAssetByRoadLink(newRoadLink, false)

      datasetStatus2 should be(3)
      featuresStatus2 should be (List(("200000","1")))
      createdSpeedLimit.head.linkId should be (5000)
      createdSpeedLimit.head.value should be(Some(NumericValue(100)))
      createdSpeedLimit.head.startMeasure should be (0.0)
      createdSpeedLimit.head.endMeasure should be (100.0)
      createdSpeedLimit.head.createdBy should be (Some("AwsUpdater"))
    }
  }

  test("new pavementClass with valid value to be created") {
    val newRoadLink = RoadLink(5000L, List(Point(0.0, 0.0), Point(100.0, 0.0)), 100.0, Municipality, 1, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val newVVHroadLink = VVHRoadlink(5000L, 235, List(Point(0.0, 0.0), Point(100.0, 0.0)), Municipality, TrafficDirection.BothDirections, AllOthers)
    when(mockRoadLinkService.getRoadsLinksFromVVH(Set(5000), false)).thenReturn(Seq(newRoadLink))
    when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(5000), false)).thenReturn(Seq(newRoadLink))
    when(mockRoadLinkService.fetchVVHRoadlinkAndComplementary(5000)).thenReturn(Some(newVVHroadLink))


    when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
    val timeStamp = new VVHRoadLinkClient("http://localhost:6080").createVVHTimeStamp()
    when(mockVVHRoadLinkClient.createVVHTimeStamp(any[Int])).thenReturn(timeStamp)
    when(mockVVHClient.roadLinkData.createVVHTimeStamp(any[Int])).thenReturn(timeStamp)

    val roadLinksList: List[List[Long]] = List(List(5000))
    val linearProperties: Map[String, String] = Map("name" -> "Mannerheimintie", "pavementClass" -> "1", "sideCode" -> "1", "id" -> "200000", "functionalClass" -> "Katu", "type" -> "Roadlink")
    val linearFeature: Feature = Feature("Feature", commonLinearGeometry, linearProperties)
    val featureCollection: FeatureCollection = FeatureCollection("FeatureCollection", List(linearFeature))

    val dataSet = Dataset(dataSetId, featureCollection, roadLinksList)

    runWithRollback {
      val numberOfFeaturesWithoutId = ServiceWithDao.validateAndInsertDataset(dataSet)
      val datasetStatus = ServiceWithDao.awsDao.getDatasetStatus(dataSetId)
      val featuresStatus = ServiceWithDao.awsDao.getAllFeatureIdAndStatusByDataset(dataSetId)

      numberOfFeaturesWithoutId should be(None)
      datasetStatus should be(0)
      featuresStatus should be (List(("200000","0")))

      ServiceWithDao.updateDataset(dataSet)
      val datasetStatus2 = ServiceWithDao.awsDao.getDatasetStatus(dataSetId)
      val featuresStatus2 = ServiceWithDao.awsDao.getAllFeatureIdAndStatusByDataset(dataSetId)
      val createdPavementClass = pavedRoadService.getPersistedAssetsByLinkIds(PavedRoad.typeId, Seq(newRoadLink.linkId), false)

      datasetStatus2 should be(3)
      featuresStatus2 should be (List(("200000","1")))
      createdPavementClass.head.linkId should be (5000)
      createdPavementClass.head.value.toString should be(Some(DynamicValue(DynamicAssetValue(List(DynamicProperty("paallysteluokka","single_choice",false,List(DynamicPropertyValue(1))))))).toString)
      createdPavementClass.head.startMeasure should be (0.0)
      createdPavementClass.head.endMeasure should be (100.0)
      createdPavementClass.head.createdBy should be (Some("AwsUpdater"))
    }
  }

  test("new obstacle with valid value to be created") {
    val newRoadLink = RoadLink(5000L, List(Point(0.0, 0.0), Point(100.0, 0.0)), 100.0, Municipality, 1, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val newVVHroadLink = VVHRoadlink(5000L, 235, List(Point(0.0, 0.0), Point(100.0, 0.0)), Municipality, TrafficDirection.BothDirections, AllOthers)
    when(mockRoadLinkService.getRoadsLinksFromVVH(Set(5000), false)).thenReturn(Seq(newRoadLink))
    when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(5000), false)).thenReturn(Seq(newRoadLink))
    when(mockRoadLinkService.fetchVVHRoadlinkAndComplementary(5000)).thenReturn(Some(newVVHroadLink))
    when(mockRoadLinkService.getRoadLinkFromVVH(5000, false)).thenReturn(Some(newRoadLink))

    when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(235)).thenReturn((Seq(newRoadLink), Seq()))

    when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
    val timeStamp = new VVHRoadLinkClient("http://localhost:6080").createVVHTimeStamp()
    when(mockVVHRoadLinkClient.createVVHTimeStamp(any[Int])).thenReturn(timeStamp)
    when(mockVVHClient.roadLinkData.createVVHTimeStamp(any[Int])).thenReturn(timeStamp)

    val roadLinksList: List[List[Long]] = List(List(5000))
    val featureCollection: FeatureCollection = FeatureCollection("FeatureCollection", List(commonPointFeature))

    val dataSet = Dataset(dataSetId, featureCollection, roadLinksList)

    runWithRollback {
      val numberOfFeaturesWithoutId = ServiceWithDao.validateAndInsertDataset(dataSet)
      val datasetStatus = ServiceWithDao.awsDao.getDatasetStatus(dataSetId)
      val featuresStatus = ServiceWithDao.awsDao.getAllFeatureIdAndStatusByDataset(dataSetId)

      numberOfFeaturesWithoutId should be(None)
      datasetStatus should be(0)
      featuresStatus should be (List(("100000","0")))

      ServiceWithDao.updateDataset(dataSet)
      val datasetStatus2 = ServiceWithDao.awsDao.getDatasetStatus(dataSetId)
      val featuresStatus2 = ServiceWithDao.awsDao.getAllFeatureIdAndStatusByDataset(dataSetId)

      datasetStatus2 should be(3)
      featuresStatus2 should be (List(("100000","1")))
    }
  }
}
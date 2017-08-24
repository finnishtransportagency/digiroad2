package fi.liikennevirasto.digiroad2

import com.vividsolutions.jts.geom.{GeometryFactory, Polygon}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.oracle.{OracleMaintenanceDao, OracleLinearAssetDao}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.{PolygonTools, TestTransactions}
import org.geotools.geometry.jts.GeometryBuilder
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class MaintenanceServiceSpec extends FunSuite with Matchers {
  val maintenanceRoadAssetTypeId: Int = 290
  
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
  when(mockRoadLinkService.getRoadLinkAndComplementaryFromVVH(any[Long], any[Boolean])).thenReturn(Some(roadLinkWithLinkSource))

  val mockLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]
  val mockMaintenanceDao = MockitoSugar.mock[OracleMaintenanceDao]
  when(mockMaintenanceDao.fetchMaintenancesByLinkIds(maintenanceRoadAssetTypeId, Seq(1)))
    .thenReturn(Seq(PersistedLinearAsset(1, 1, 1, Some(NumericValue(40000)), 0.4, 9.6, None, None, None, None, false, maintenanceRoadAssetTypeId, 0, None, LinkGeomSource.NormalLinkInterface)))
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val linearAssetDao = new OracleLinearAssetDao(mockVVHClient, mockRoadLinkService)
  val maintenanceDao = new OracleMaintenanceDao(mockVVHClient, mockRoadLinkService)

  object PassThroughService extends LinearAssetOperations {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: OracleLinearAssetDao = mockLinearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def vvhClient: VVHClient = mockVVHClient
    override def polygonTools: PolygonTools = mockPolygonTools
  }

  object ServiceWithDao extends MaintenanceService(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: OracleLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def vvhClient: VVHClient = mockVVHClient
    override def polygonTools: PolygonTools = mockPolygonTools
    override def maintenanceDAO: OracleMaintenanceDao = maintenanceDao
  }

  val geomFact= new GeometryFactory()
  val geomBuilder = new GeometryBuilder(geomFact)

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(PassThroughService.dataSource)(test)

  test("Create new maintenanceRoad") {
    val prop1 = Properties("huoltotie_kayttooikeus", "single_choice", "1")
    val prop2 = Properties("huoltotie_huoltovastuu", "single_choice", "2")
    val prop3 = Properties("huoltotie_tiehoitokunta", "text", "text")

    val propertiesSeq :Seq[Properties] = List(prop1, prop2, prop3)

    val maintenanceRoad = MaintenanceRoad(propertiesSeq)
    runWithRollback {
      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(388562360l, 0, 20, maintenanceRoad, 1, 0, None)), maintenanceRoadAssetTypeId, "testuser")
      newAssets.length should be(1)

      val asset = maintenanceDao.fetchMaintenancesByLinkIds(maintenanceRoadAssetTypeId, Seq(388562360l)).head
      asset.value should be (Some(maintenanceRoad))
      asset.expired should be (false)
    }
  }

  test("update new maintenanceRoad") {
    val propIns1 = Properties("huoltotie_kayttooikeus", "single_choice", "1")
    val propIns2 = Properties("huoltotie_huoltovastuu", "single_choice", "2")
    val propIns3 = Properties("huoltotie_postinumero", "text", "text prop3")
    val propIns4 = Properties("huoltotie_puh1" , "text", "text prop4")
    val propIns5 = Properties("huoltotie_tiehoitokunta", "text", "text")

    val propIns :Seq[Properties] = List(propIns1, propIns2, propIns3, propIns4, propIns5)
    val maintenanceRoadIns = MaintenanceRoad(propIns)

    val propUpd1 = Properties("huoltotie_kayttooikeus", "single_choice", "4")
    val propUpd2 = Properties("huoltotie_huoltovastuu", "single_choice", "1")
    val propUpd3 = Properties("huoltotie_postinumero", "text",  "text prop3 Update")
    val propUpd4 = Properties("huoltotie_puh1" , "text", "")
    val propUpd5 = Properties("huoltotie_tiehoitokunta", "text", "text")
    val propUpd6 = Properties("huoltotie_puh2" , "text", "text prop puh2")

    val propUpd :Seq[Properties] = List(propUpd1, propUpd2, propUpd3, propUpd4, propUpd5, propUpd6)
    val maintenanceRoadUpd = MaintenanceRoad(propUpd)

    val maintenanceRoadFetch = MaintenanceRoad(propUpd.filterNot(_.publicId == "huoltotie_puh1"))

    runWithRollback {
      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(388562360l, 0, 20, maintenanceRoadIns, 1, 0, None)), maintenanceRoadAssetTypeId, "testuser")
      newAssets.length should be(1)

      val updAssets = ServiceWithDao.update(Seq(newAssets.head), maintenanceRoadUpd, "testuser")
      updAssets.length should be(1)

      val asset = maintenanceDao.fetchMaintenancesByLinkIds(maintenanceRoadAssetTypeId, Seq(388562360l)).filterNot(_.expired).head
      asset.value should be (Some(maintenanceRoadFetch))
      asset.expired should be (false)
    }
  }

  test("Should delete maintenanceRoad asset"){
    val prop1 = Properties("huoltotie_kayttooikeus", "single_choice", "1")
    val prop2 = Properties("huoltotie_huoltovastuu", "single_choice", "2")
    val prop3 = Properties("huoltotie_tiehoitokunta", "text", "text")

    val propertiesSeq :Seq[Properties] = List(prop1, prop2, prop3)

    val maintenanceRoad = MaintenanceRoad(propertiesSeq)
    runWithRollback {
      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(388562360l, 0, 20, maintenanceRoad, 1, 0, None)), maintenanceRoadAssetTypeId, "testuser")
      newAssets.length should be(1)
      var asset = maintenanceDao.fetchMaintenancesByIds(maintenanceRoadAssetTypeId,Set(newAssets.head),false).head
      asset.value should be (Some(maintenanceRoad))
      asset.expired should be (false)

      val assetId : Seq[Long] = List(asset.id)
      val deleted = ServiceWithDao.expire( assetId , "testuser")
      asset = maintenanceDao.fetchMaintenancesByIds(maintenanceRoadAssetTypeId,Set(newAssets.head),false).head
      asset.expired should be (true)
    }
  }

  test("Fetch all Active Maintenance Road By Polygon") {
    val prop1 = Properties("huoltotie_kayttooikeus", "single_choice", "1")
    val prop2 = Properties("huoltotie_huoltovastuu", "single_choice", "2")
    val prop3 = Properties("huoltotie_tiehoitokunta", "text", "text")

    val propertiesSeq :Seq[Properties] = List(prop1, prop2, prop3)

    when(mockPolygonTools.getAreaGeometry(any[Int])).thenReturn(geomBuilder.polygon(24.2, 60.5, 24.8, 60.5, 24.8, 59, 24.2, 59))
    when(mockRoadLinkService.getLinkIdsFromVVHWithComplementaryByPolygons(any[Seq[Polygon]])).thenReturn(Seq(388562360l))

    val maintenanceRoad = MaintenanceRoad(propertiesSeq)
    runWithRollback {
      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(388562360l, 0, 20, maintenanceRoad, 1, 0, None)), maintenanceRoadAssetTypeId, "testuser")
      newAssets.length should be(1)

      val assets = ServiceWithDao.getActiveMaintenanceRoadByPolygon(1)
      assets.map { asset =>
        asset.linkId should be(388562360l)
        asset.startMeasure should be(0)
        asset.endMeasure should be(20)
        asset.value.get.asInstanceOf[MaintenanceRoad].maintenanceRoad.length should be(3)
      }
    }
  }

  test("Fetch Active Maintenance Road By Polygon, with an empty result") {
    when(mockRoadLinkService.getLinkIdsFromVVHWithComplementaryByPolygons(Seq(geomBuilder.polygon(24.2, 60.5, 24.8, 60.5, 24.8, 59, 24.2, 59)))).thenReturn(Seq(388562360l))
    OracleDatabase.withDynTransaction {
      val assets = ServiceWithDao.getActiveMaintenanceRoadByPolygon(1)
      assets.length should be(0)
    }
  }

}

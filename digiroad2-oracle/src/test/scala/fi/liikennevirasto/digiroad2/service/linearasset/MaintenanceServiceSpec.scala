package fi.liikennevirasto.digiroad2.service.linearasset

import com.vividsolutions.jts.geom.{GeometryFactory, Polygon}
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh._
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MunicipalityDao, PostGISAssetDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.{PostGISLinearAssetDao, PostGISMaintenanceDao}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{PolygonTools, TestTransactions}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point}
import org.geotools.geometry.jts.GeometryBuilder
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class MaintenanceServiceSpec extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]

  when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
  when(mockVVHRoadLinkClient.fetchByLinkId(388562360l)).thenReturn(Some(VVHRoadlink(388562360l, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(VVHRoadlink(388562360l, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockVVHClient.fetchRoadLinkByLinkId(any[Long])).thenReturn(Some(VVHRoadlink(388562360l, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(Set(388562361l))).thenReturn(Seq(RoadLink(388562361l, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)))
  when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(Set.empty[Long])).thenReturn(Seq())

  val roadLinkWithLinkSource = Seq(RoadLink(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    ,RoadLink(388562360l, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    ,RoadLink(388562361l, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    ,RoadLink(388562362l, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface))

  when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((roadLinkWithLinkSource, Nil))
  when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[Int])).thenReturn((roadLinkWithLinkSource, Nil))
  when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(any[Set[Long]], any[Boolean])).thenReturn(roadLinkWithLinkSource)
  when(mockPolygonTools.getAreaByGeometry(Seq(any[Point]), Measures(any[Double],any[Double]), None )).thenReturn(1)

  val mockLinearAssetDao = MockitoSugar.mock[PostGISLinearAssetDao]
  val mockMaintenanceDao = MockitoSugar.mock[PostGISMaintenanceDao]
  val mockDynamicLinearAssetDao = MockitoSugar.mock[DynamicLinearAssetDao]
  val mockMunicipalityDao = MockitoSugar.mock[MunicipalityDao]
  val mockAssetDao = MockitoSugar.mock[PostGISAssetDao]
  when(mockDynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(MaintenanceRoadAsset.typeId, Seq(1)))
    .thenReturn(Seq(PersistedLinearAsset(1, 1, 1, Some(NumericValue(40000)), 0.4, 9.6, None, None, None, None, false, MaintenanceRoadAsset.typeId, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None)))
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val linearAssetDao = new PostGISLinearAssetDao(mockVVHClient, mockRoadLinkService)
  val maintenanceDao = new PostGISMaintenanceDao(mockVVHClient, mockRoadLinkService)
  val dynamicLinearAssetDAO = new DynamicLinearAssetDao

  object ServiceWithDao extends MaintenanceService(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: PostGISLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def vvhClient: VVHClient = mockVVHClient
    override def polygonTools: PolygonTools = mockPolygonTools
    override def maintenanceDAO: PostGISMaintenanceDao = maintenanceDao
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao
    override def assetDao: PostGISAssetDao = new PostGISAssetDao
    override def dynamicLinearAssetDao: DynamicLinearAssetDao = dynamicLinearAssetDAO
    override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()) = throw new UnsupportedOperationException("Not supported method")
  }

  object MaintenanceServiceWithDao extends MaintenanceService(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: PostGISLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def vvhClient: VVHClient = mockVVHClient
    override def polygonTools: PolygonTools = mockPolygonTools
    override def maintenanceDAO: PostGISMaintenanceDao = mockMaintenanceDao
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao
    override def assetDao: PostGISAssetDao = mockAssetDao
    override def dynamicLinearAssetDao: DynamicLinearAssetDao = mockDynamicLinearAssetDao
    override def getInaccurateRecords(typeId: Int,municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()) = throw new UnsupportedOperationException("Not supported method")
  }

  val geomFact= new GeometryFactory()
  val geomBuilder = new GeometryBuilder(geomFact)

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  test("Create new maintenanceRoad") {
    val prop1 = DynamicProperty("huoltotie_kayttooikeus", "single_choice", true, Seq(DynamicPropertyValue( "1")))
    val prop2 = DynamicProperty("huoltotie_huoltovastuu", "single_choice", true, Seq(DynamicPropertyValue( "2")))
    val prop3 = DynamicProperty("huoltotie_tiehoitokunta", "text",  false, Seq(DynamicPropertyValue( "text")))
    val prop4 = DynamicProperty("suggest_box", "checkbox",  false, Seq())
    val prop5 = DynamicProperty("huoltotie_tarkistettu", "checkbox",  false, Seq())

    val propertiesSeq: Seq[DynamicProperty] = List(prop1, prop2, prop3, prop4, prop5)

    val maintenanceRoad = DynamicValue(DynamicAssetValue(propertiesSeq))
    runWithRollback {
      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(388562360l, 0, 20, maintenanceRoad, 1, 0, None)), MaintenanceRoadAsset.typeId, "testuser")
      newAssets.length should be(1)

      val asset = dynamicLinearAssetDAO.fetchDynamicLinearAssetsByLinkIds(MaintenanceRoadAsset.typeId, Seq(388562360l)).head
      asset.value should be (Some(maintenanceRoad))
      asset.expired should be (false)
    }
  }

  test("update new maintenanceRoad") {
    val propIns1 = DynamicProperty("huoltotie_kayttooikeus", "single_choice", true, Seq(DynamicPropertyValue( "1")))
    val propIns2 = DynamicProperty("huoltotie_huoltovastuu", "single_choice", true, Seq(DynamicPropertyValue( "2")))
    val propIns3 = DynamicProperty("huoltotie_tiehoitokunta", "text", false, Seq(DynamicPropertyValue( "text")))
    val propIns4 = DynamicProperty("suggest_box", "checkbox",  false, Seq())
    val propIns5 = DynamicProperty("huoltotie_tarkistettu", "checkbox",  false, Seq())

    val propIns :Seq[DynamicProperty] = List(propIns1, propIns2, propIns3, propIns4, propIns5)
    val maintenanceRoadIns = DynamicValue(DynamicAssetValue(propIns))

    val propUpd1 = DynamicProperty("huoltotie_kayttooikeus", "single_choice", true, Seq(DynamicPropertyValue("4")))
    val propUpd2 = DynamicProperty("huoltotie_huoltovastuu", "single_choice", true, Seq(DynamicPropertyValue("1")))
    val propUpd3 = DynamicProperty("huoltotie_tiehoitokunta", "text", false, Seq(DynamicPropertyValue("text")))
    val propUpd4 = DynamicProperty("suggest_box", "checkbox",  false, Seq())
    val propUpd5 = DynamicProperty("huoltotie_tarkistettu", "checkbox",  false, Seq())

    val propUpd :Seq[DynamicProperty] = List(propUpd1, propUpd2, propUpd3, propUpd4, propUpd5)
    val maintenanceRoadUpd = DynamicValue(DynamicAssetValue(propUpd))

    val maintenanceRoadFetch = DynamicValue(DynamicAssetValue(propUpd))

    runWithRollback {
      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(388562360l, 0, 20, maintenanceRoadIns, 1, 0, None)), MaintenanceRoadAsset.typeId, "testuser")
      newAssets.length should be(1)

      val updAssets = ServiceWithDao.update(Seq(newAssets.head), maintenanceRoadUpd, "testuser")
      updAssets.length should be(1)

      val asset = ServiceWithDao.dynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(MaintenanceRoadAsset.typeId, Seq(388562360l)).filterNot(_.expired).head
      asset.value should be (Some(maintenanceRoadFetch))
      asset.expired should be (false)
    }
  }

  test("Should delete maintenanceRoad asset"){
    val prop1 = DynamicProperty("huoltotie_kayttooikeus", "single_choice", true, Seq(DynamicPropertyValue("1")))
    val prop2 = DynamicProperty("huoltotie_huoltovastuu", "single_choice", true, Seq(DynamicPropertyValue("2")))
    val prop3 = DynamicProperty("huoltotie_tiehoitokunta", "text", false, Seq(DynamicPropertyValue("text")))
    val prop4 = DynamicProperty("suggest_box", "checkbox",  false, Seq())
    val prop5 = DynamicProperty("huoltotie_tarkistettu", "checkbox",  false, Seq())

    val propertiesSeq: Seq[DynamicProperty] = List(prop1, prop2, prop3, prop4, prop5)

    val maintenanceRoad = DynamicValue(DynamicAssetValue(propertiesSeq))
    runWithRollback {
      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(388562360l, 0, 20, maintenanceRoad, 1, 0, None)), MaintenanceRoadAsset.typeId, "testuser")
      newAssets.length should be(1)
      var asset = dynamicLinearAssetDAO.fetchDynamicLinearAssetsByIds(Set(newAssets.head)).head
      asset.value should be (Some(maintenanceRoad))
      asset.expired should be (false)

      val assetId : Seq[Long] = List(asset.id)
      ServiceWithDao.expire( assetId , "testuser")
      asset = dynamicLinearAssetDAO.fetchDynamicLinearAssetsByIds(Set(newAssets.head)).head
      asset.expired should be (true)
    }
  }

  test("Fetch all Active Maintenance Road By Polygon") {
    val prop1 = DynamicProperty("huoltotie_kayttooikeus", "single_choice", true, Seq(DynamicPropertyValue("1")))
    val prop2 = DynamicProperty("huoltotie_huoltovastuu", "single_choice", true, Seq(DynamicPropertyValue("2")))
    val prop3 = DynamicProperty("huoltotie_tiehoitokunta", "text", false, Seq(DynamicPropertyValue("text")))

    val propertiesSeq :Seq[DynamicProperty] = List(prop1, prop2, prop3)

    when(mockPolygonTools.getAreaGeometry(any[Int])).thenReturn(geomBuilder.polygon(24.2, 60.5, 24.8, 60.5, 24.8, 59, 24.2, 59))
    when(mockRoadLinkService.getLinkIdsFromVVHWithComplementaryByPolygons(any[Seq[Polygon]])).thenReturn(Seq(388562360l))

    val maintenanceRoad = DynamicAssetValue(propertiesSeq)
    runWithRollback {
      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(388562360l, 0, 20, DynamicValue(maintenanceRoad), 1, 0, None)), MaintenanceRoadAsset.typeId, "testuser")
      newAssets.length should be(1)

      val assets = ServiceWithDao.getActiveMaintenanceRoadByPolygon(1)
      assets.map { asset =>
        asset.linkId should be(388562360l)
        asset.startMeasure should be(0)
        asset.endMeasure should be(20)
        asset.value.get.asInstanceOf[DynamicValue].value.properties.length should be(5)
      }
    }
  }

  test("Fetch Active Maintenance Road By Polygon, with an empty result") {
    when(mockRoadLinkService.getLinkIdsFromVVHWithComplementaryByPolygons(Seq(geomBuilder.polygon(24.2, 60.5, 24.8, 60.5, 24.8, 59, 24.2, 59)))).thenReturn(Seq(388562360l))
    PostGISDatabase.withDynTransaction {
      val assets = ServiceWithDao.getActiveMaintenanceRoadByPolygon(1)
      assets.length should be(0)
    }
  }

  test("Get unchecked maintenanceRoad asset") {
    val prop1 = DynamicProperty("huoltotie_kayttooikeus", "single_choice", true, Seq(DynamicPropertyValue("1")))
    val prop2 = DynamicProperty("huoltotie_huoltovastuu", "single_choice", true, Seq(DynamicPropertyValue("2")))
    val prop3 = DynamicProperty("huoltotie_tarkistettu", "checkbox", false, Seq(DynamicPropertyValue("0")))
    val prop4 = DynamicProperty("huoltotie_tarkistettu", "checkbox", false, Seq(DynamicPropertyValue("1")))

    val maintenanceUnchecked = DynamicAssetValue(List(prop1, prop2, prop3))
    val maintenanceChecked = DynamicAssetValue(List(prop1, prop2, prop4))
    runWithRollback {
      //asset created on area 1
      val uncheckedAsset1 = ServiceWithDao.create(Seq(NewLinearAsset(388562360l, 0, 20, DynamicValue(maintenanceUnchecked), 1, 0, None)), MaintenanceRoadAsset.typeId, "testuser")
      val checkedAsset1 = ServiceWithDao.create(Seq(NewLinearAsset(388562361l, 0, 20, DynamicValue(maintenanceChecked), 1, 0, None)), MaintenanceRoadAsset.typeId, "testuser")
      //asset created on area 2
      when(mockPolygonTools.getAreaByGeometry(Seq(any[Point]), Measures(any[Double],any[Double]), None )).thenReturn(2)
      val uncheckedAsset2 = ServiceWithDao.create(Seq(NewLinearAsset(388562362l, 0, 20, DynamicValue(maintenanceUnchecked), 1, 0, None)), MaintenanceRoadAsset.typeId, "testuser")

      val assetArea1 = ServiceWithDao.getUncheckedLinearAssets(Some(Set(1)))
      assetArea1.flatMap(_._2).flatMap(_._2).toSeq.contains(uncheckedAsset1.head) should be (true)
      assetArea1.flatMap(_._2).flatMap(_._2).toSeq.contains(uncheckedAsset2.head) should be (false)
      assetArea1.flatMap(_._2).flatMap(_._2).toSeq.contains(checkedAsset1.head) should be (false)

      val assetArea = ServiceWithDao.getUncheckedLinearAssets(None)
      assetArea.flatMap(_._2).flatMap(_._2).toSeq.contains(uncheckedAsset1.head) should be (true)
      assetArea.flatMap(_._2).flatMap(_._2).toSeq.contains(uncheckedAsset2.head) should be (true)
      assetArea.flatMap(_._2).flatMap(_._2).toSeq.contains(checkedAsset1.head) should be (false)
    }
  }

  test("Get service roads by bounding box") {

    val prop1 = DynamicProperty("huoltotie_kayttooikeus", "single_choice", true, Seq(DynamicPropertyValue("1")))
    val prop2 = DynamicProperty("huoltotie_huoltovastuu", "single_choice", true, Seq(DynamicPropertyValue("2")))
    val prop3 = DynamicProperty("huoltotie_tiehoitokunta", "text", false, Seq(DynamicPropertyValue("text")))

    val propertiesSeq :Seq[DynamicProperty] = List(prop1, prop2, prop3)

    val changeInfo = Seq(
      ChangeInfo(Some(1), Some(2), 12345, 1, Some(0), Some(100), Some(0), Some(100), 1476468913000L),
      ChangeInfo(Some(3), Some(4), 12345, 2, Some(0), Some(20), Some(100), Some(120), 1476468913000L)
    )

    when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn((roadLinkWithLinkSource, changeInfo))

    val maintenanceRoad = DynamicAssetValue(propertiesSeq)
    runWithRollback {
      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(388562360l, 0, 20, DynamicValue(maintenanceRoad), 1, 0, None)), MaintenanceRoadAsset.typeId, "testuser")
      newAssets.length should be(1)

      val assets = ServiceWithDao.getAllByBoundingBox(BoundingRectangle(Point(1, 2), Point(3, 4)))
      assets.foreach {
        case (asset, _) =>
        asset.linkId should be(388562360l)
        asset.startMeasure should be(0)
        asset.endMeasure should be(20)
        asset.value.get.asInstanceOf[DynamicValue].value.properties.length should be(5)
      }
    }

  }

  test("Get only maintenance assets that have kayttooikeus = 9 "){
    val propKayttooikeus = DynamicProperty("huoltotie_kayttooikeus", "single_choice", true, Seq(DynamicPropertyValue("1")))
    val propHuoltovastuu = DynamicProperty("huoltotie_huoltovastuu", "single_choice", true, Seq(DynamicPropertyValue("2")))
    val propTiehoitokunta = DynamicProperty("huoltotie_tiehoitokunta", "text", false, Seq(DynamicPropertyValue("text")))

    val propertiesSeq :Seq[DynamicProperty] = List(propKayttooikeus, propHuoltovastuu, propTiehoitokunta)

    val propKayttooikeus1 = DynamicProperty("huoltotie_kayttooikeus", "single_choice", true, Seq(DynamicPropertyValue("9")))
    val propHuoltovastuu1 = DynamicProperty("huoltotie_huoltovastuu", "single_choice", true, Seq(DynamicPropertyValue("2")))
    val propTiehoitokunta1 = DynamicProperty("huoltotie_tiehoitokunta", "text", false, Seq(DynamicPropertyValue("text")))

    val propertiesSeq1 :Seq[DynamicProperty] = List(propKayttooikeus1, propHuoltovastuu1, propTiehoitokunta1)

    val maintenanceRoad = DynamicAssetValue(propertiesSeq)
    val maintenanceRoad1 = DynamicAssetValue(propertiesSeq1)

    runWithRollback {
      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(388562360l, 0, 20, DynamicValue(maintenanceRoad), 1, 0, None),
                                                NewLinearAsset(388562361l, 0, 20, DynamicValue(maintenanceRoad1), 1, 0, None)), MaintenanceRoadAsset.typeId, "testuser")
      newAssets.length should be(2)

      val assets = ServiceWithDao.maintenanceDAO.fetchPotentialServiceRoads()
      assets.size should be(1)
      assets.head.linkId should be(388562361l)
      assets.head.startMeasure should be(0.0)
      assets.head.endMeasure should be(20.0)
      assets.head.value.get.asInstanceOf[DynamicValue].value.properties.find(_.publicId == "huoltotie_kayttooikeus").get.values.head.value should be ("9")
    }
  }

  test("Get only maintenance assets that doesn' have kayttooikeus = 9 "){
    val propKayttooikeus = DynamicProperty("huoltotie_kayttooikeus", "single_choice", true, Seq(DynamicPropertyValue("1")))
    val propHuoltovastuu = DynamicProperty("huoltotie_huoltovastuu", "single_choice", true, Seq(DynamicPropertyValue("2")))
    val propTiehoitokunta = DynamicProperty("huoltotie_tiehoitokunta", "text", false, Seq(DynamicPropertyValue("text")))

    val propertiesSeq :Seq[DynamicProperty] = List(propKayttooikeus, propHuoltovastuu, propTiehoitokunta)

    val propKayttooikeus1 = DynamicProperty("huoltotie_kayttooikeus", "single_choice", true, Seq(DynamicPropertyValue("1")))
    val propHuoltovastuu1 = DynamicProperty("huoltotie_huoltovastuu", "single_choice", true, Seq(DynamicPropertyValue("2")))
    val propTiehoitokunta1 = DynamicProperty("huoltotie_tiehoitokunta", "text", false, Seq(DynamicPropertyValue("text")))

    val propertiesSeq1 :Seq[DynamicProperty] = List(propKayttooikeus1, propHuoltovastuu1, propTiehoitokunta1)

    val maintenanceRoad = DynamicAssetValue(propertiesSeq)
    val maintenanceRoad1 = DynamicAssetValue(propertiesSeq1)

    runWithRollback {
      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(388562360l, 0, 20, DynamicValue(maintenanceRoad), 1, 0, None),
          NewLinearAsset(388562361l, 0, 20, DynamicValue(maintenanceRoad1), 1, 0, None)), MaintenanceRoadAsset.typeId, "testuser")
      newAssets.length should be(2)

      val assets = ServiceWithDao.maintenanceDAO.fetchPotentialServiceRoads()
      assets.size should be (0)
    }
  }

  test("test get by zoom level method"){
    val propKayttooikeus = DynamicProperty("huoltotie_kayttooikeus", "single_choice", true, Seq(DynamicPropertyValue("9")))
    val propHuoltovastuu = DynamicProperty("huoltotie_huoltovastuu", "single_choice", true, Seq(DynamicPropertyValue("2")))
    val propTiehoitokunta = DynamicProperty("huoltotie_tiehoitokunta", "text", false, Seq(DynamicPropertyValue("text")))

    val propertiesSeq :Seq[DynamicProperty] = List(propKayttooikeus, propHuoltovastuu, propTiehoitokunta)
    val maintenanceRoad = DynamicAssetValue(propertiesSeq)

    runWithRollback {
      when(mockMaintenanceDao.fetchPotentialServiceRoads()).thenReturn(Seq(
        PersistedLinearAsset(0, 388562361l,1, Some(DynamicValue(maintenanceRoad)), 0.0, 20.0, Some("testuser"), None, None, None, false, 290, 0, None, NormalLinkInterface, None, None, None)
      ))

      val assets = MaintenanceServiceWithDao.getByZoomLevel
      assets.size should be (1)
    }
  }

  test("should not return assets when get by zoom level called"){
    runWithRollback {
      when(mockMaintenanceDao.fetchPotentialServiceRoads()).thenReturn(Seq())

      val assets = MaintenanceServiceWithDao.getByZoomLevel
      assets.size should be (0)
    }
  }
}

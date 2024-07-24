package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.Digiroad2Context._
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset._
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.{MassTransitStopService, PersistedMassTransitStop}
import fi.liikennevirasto.digiroad2.util.LinkIdGenerator
import org.joda.time.DateTime
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, Formats}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite}
import org.scalatra.test.scalatest.ScalatraSuite

import javax.sql.DataSource
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession

object integrationApiTestTransactions {
  def runWithRollback(ds: DataSource = PostGISDatabase.ds)(f: => Unit): Unit = {
    Database.forDataSource(ds).withDynTransaction {
      f
      dynamicSession.rollback()
    }
  }
}

class IntegrationApiSpec extends FunSuite with ScalatraSuite with BeforeAndAfter{
  protected implicit val jsonFormats: Formats = DefaultFormats
  def stopWithLinkId(linkId: String): PersistedMassTransitStop = {
    PersistedMassTransitStop(1L, 2L, linkId, Seq(2, 3), 235, 1.0, 1.0, 1, None, None, None, floating = false, 0, Modification(None, None), Modification(None, None), Seq(), NormalLinkInterface)
  }
  val mockLinearLengthLimitService = MockitoSugar.mock[LinearLengthLimitService]
  val mockMassTransitStopService = MockitoSugar.mock[MassTransitStopService]
  when(mockMassTransitStopService.getByMunicipality(235)).thenReturn(Seq(stopWithLinkId("123"), stopWithLinkId("321")))

  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val testLinearTotalWeightLimitService = new LinearTotalWeightLimitService(mockRoadLinkService, new DummyEventBus)
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val testDynamicLinearAssetService = new DynamicLinearAssetService(mockRoadLinkService, mockEventBus)
  val testBogieWeightLimitService = new LinearBogieWeightLimitService(mockRoadLinkService, mockEventBus)
  val testDamagedByThawService = new DamagedByThawService(mockRoadLinkService, mockEventBus)
  val testParkingProhibitionService = new ParkingProhibitionService(mockRoadLinkService, mockEventBus)

  val (linkId1, linkId2, linkId3, linkId4, linkId5) = (LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom(),
    LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom())

  def runWithRollback(test: => Unit): Unit = integrationApiTestTransactions.runWithRollback(PostGISDatabase.ds)(test)

  private val integrationApi = new IntegrationApi(mockMassTransitStopService, new OthSwagger) {
    override def getLinearAssetService(typeId: Int): LinearAssetOperations = {
      typeId match {
        case MaintenanceRoadAsset.typeId => maintenanceRoadService
        case PavedRoad.typeId => pavedRoadService
        case RoadWidth.typeId => roadWidthService
        case Prohibition.typeId => prohibitionService
        case HazmatTransportProhibition.typeId => hazmatTransportProhibitionService
        case EuropeanRoads.typeId | ExitNumbers.typeId => textValueLinearAssetService
        case CareClass.typeId | CarryingCapacity.typeId | LitRoad.typeId => testDynamicLinearAssetService
        case LengthLimit.typeId => linearLengthLimitService
        case TotalWeightLimit.typeId => testLinearTotalWeightLimitService
        case TrailerTruckWeightLimit.typeId => linearTrailerTruckWeightLimitService
        case AxleWeightLimit.typeId => linearAxleWeightLimitService
        case BogieWeightLimit.typeId => testBogieWeightLimitService
        case MassTransitLane.typeId => massTransitLaneService
        case NumberOfLanes.typeId => numberOfLanesService
        case DamagedByThaw.typeId => testDamagedByThawService
        case RoadWorksAsset.typeId => roadWorkService
        case ParkingProhibition.typeId => testParkingProhibitionService
        case CyclingAndWalking.typeId => cyclingAndWalkingService
        case _ => linearAssetService
      }
    }
  }
  addServlet(integrationApi, "/*")

  test("Get assets requires municipality number") {
    get("/mass_transit_stops") {
      status should equal(400)
    }
    get("/mass_transit_stops?municipality=235") {
      status should equal(200)
    }
  }

  test("Get speed_limits requires municipality number") {
    get("/speed_limits") {
      status should equal(400)
    }
    get("/speed_limits?municipality=588") {
      status should equal(200)
    }
  }

  // run manually if required, will take a long time or will not work reliably on CI
  ignore("Should use cached data on second search") {
    var result = ""
    var timing = 0L
    val startTimeMs = System.currentTimeMillis
    get("/road_link_properties?municipality=235") {
      status should equal(200)
      result = body
      timing =  System.currentTimeMillis - startTimeMs
    }
    // Second request should use cache and be less than half of the time spent (in dev testing, approx 2/5ths)
    get("/road_link_properties?municipality=235") {
      status should equal(200)
      body should equal(result)
      val elapsed = System.currentTimeMillis - startTimeMs - timing
      elapsed shouldBe < (timing / 2)
    }
  }

  test("Returns mml id of the road link that the stop refers to") {
    get("/mass_transit_stops?municipality=235") {
      val linkIds = (((parse(body) \ "features") \ "properties") \ "link_id").extract[Seq[String]]
      linkIds should be(Seq("123", "321"))
    }
  }

  test("encode speed limit") {
    integrationApi.speedLimitsToApi(Seq(PieceWiseLinearAsset(1, linkId2, SideCode.BothDirections, Some(SpeedLimitValue(80)), Seq(), false,
      0, 1, Set(), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections, 0,
        None, NormalLinkInterface, Unknown, Map(), None, None, None))) should be(Seq(Map(
      "id" -> 1,
      "sideCode" -> 1,
      "points" -> Nil,
      "geometryWKT" -> "",
      "value" -> 80,
      "startMeasure" -> 0,
      "endMeasure" -> 1,
      "linkId" -> linkId2,
      "muokattu_viimeksi" -> "",
      "generatedValue" -> false,
      "linkSource" -> 1
    )))
  }

  test("generatedValue returns true if creator is real user and modifier is automatically generated") {
    integrationApi.speedLimitsToApi(Seq(PieceWiseLinearAsset(1, linkId2, SideCode.BothDirections, Some(SpeedLimitValue(80)), Seq(), false,
      0, 1, Set(), Some(AutoGeneratedUsername.generatedInUpdate), None, Some("K123456"), None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections, 0,
      None, NormalLinkInterface, Unknown, Map(), None, None, None))) should be(Seq(Map(
      "id" -> 1,
      "sideCode" -> 1,
      "points" -> Nil,
      "geometryWKT" -> "",
      "value" -> 80,
      "startMeasure" -> 0,
      "endMeasure" -> 1,
      "linkId" -> linkId2,
      "muokattu_viimeksi" -> "",
      "generatedValue" -> true,
      "linkSource" -> 1
    )))
  }

  test("generatedValue returns true if creator is real user and modifier is automatically generated (contains fixed part of auto-generated value)") {
    integrationApi.speedLimitsToApi(Seq(PieceWiseLinearAsset(1, linkId2, SideCode.BothDirections, Some(SpeedLimitValue(80)), Seq(), false,
      0, 1, Set(), Some(AutoGeneratedUsername.splitSpeedLimitPrefix + "1234"), None, Some("K123456"), None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections, 0,
      None, NormalLinkInterface, Unknown, Map(), None, None, None))) should be(Seq(Map(
      "id" -> 1,
      "sideCode" -> 1,
      "points" -> Nil,
      "geometryWKT" -> "",
      "value" -> 80,
      "startMeasure" -> 0,
      "endMeasure" -> 1,
      "linkId" -> linkId2,
      "muokattu_viimeksi" -> "",
      "generatedValue" -> true,
      "linkSource" -> 1
    )))
  }

  test("generatedValue returns true if creator is automatically generated") {
    integrationApi.speedLimitsToApi(Seq(PieceWiseLinearAsset(1, linkId2, SideCode.BothDirections, Some(SpeedLimitValue(80)), Seq(), false,
      0, 1, Set(), None, None, Some(AutoGeneratedUsername.dr1Conversion), None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections, 0,
      None, NormalLinkInterface, Unknown, Map(), None, None, None))) should be(Seq(Map(
      "id" -> 1,
      "sideCode" -> 1,
      "points" -> Nil,
      "geometryWKT" -> "",
      "value" -> 80,
      "startMeasure" -> 0,
      "endMeasure" -> 1,
      "linkId" -> linkId2,
      "muokattu_viimeksi" -> "",
      "generatedValue" -> true,
      "linkSource" -> 1
    )))
  }

  test("generatedValue returns true if creator is automatically generated (contains fixed part of auto-generated value)") {
    integrationApi.speedLimitsToApi(Seq(PieceWiseLinearAsset(1, linkId2, SideCode.BothDirections, Some(SpeedLimitValue(80)), Seq(), false,
      0, 1, Set(), None, None, Some(AutoGeneratedUsername.splitSpeedLimitPrefix + "1234"), None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections, 0,
      None, NormalLinkInterface, Unknown, Map(), None, None, None))) should be(Seq(Map(
      "id" -> 1,
      "sideCode" -> 1,
      "points" -> Nil,
      "geometryWKT" -> "",
      "value" -> 80,
      "startMeasure" -> 0,
      "endMeasure" -> 1,
      "linkId" -> linkId2,
      "muokattu_viimeksi" -> "",
      "generatedValue" -> true,
      "linkSource" -> 1
    )))
  }

  test("generatedValue returns false if creator is automatically generated and modifier is real user") {
    integrationApi.speedLimitsToApi(Seq(PieceWiseLinearAsset(1, linkId2, SideCode.BothDirections, Some(SpeedLimitValue(80)), Seq(), false,
      0, 1, Set(), Some("K123456"), None, Some(AutoGeneratedUsername.dr1Conversion), None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections, 0,
      None, NormalLinkInterface, Unknown, Map(), None, None, None))) should be(Seq(Map(
      "id" -> 1,
      "sideCode" -> 1,
      "points" -> Nil,
      "geometryWKT" -> "",
      "value" -> 80,
      "startMeasure" -> 0,
      "endMeasure" -> 1,
      "linkId" -> linkId2,
      "muokattu_viimeksi" -> "",
      "generatedValue" -> false,
      "linkSource" -> 1
    )))
  }

  test("encode validity period to time domain") {
    integrationApi.toTimeDomain(ValidityPeriod(6, 10, ValidityPeriodDayOfWeek.Weekday))  should be("[[(t2){d5}]*[(h6){h4}]]")
    integrationApi.toTimeDomain(ValidityPeriod(23, 24, ValidityPeriodDayOfWeek.Weekday)) should be("[[(t2){d5}]*[(h23){h1}]]")
    integrationApi.toTimeDomain(ValidityPeriod(21, 7, ValidityPeriodDayOfWeek.Weekday)) should be("[[(t2){d5}]*[(h21){h10}]]")
    integrationApi.toTimeDomain(ValidityPeriod(0, 1, ValidityPeriodDayOfWeek.Weekday)) should be("[[(t2){d5}]*[(h0){h1}]]")
    integrationApi.toTimeDomain(ValidityPeriod(0, 24, ValidityPeriodDayOfWeek.Weekday)) should be("[[(t2){d5}]*[(h0){h24}]]")

    integrationApi.toTimeDomain(ValidityPeriod(6, 10, ValidityPeriodDayOfWeek.Saturday)) should be("[[(t7){d1}]*[(h6){h4}]]")
    integrationApi.toTimeDomain(ValidityPeriod(23, 24, ValidityPeriodDayOfWeek.Saturday)) should be("[[(t7){d1}]*[(h23){h1}]]")
    integrationApi.toTimeDomain(ValidityPeriod(21, 7, ValidityPeriodDayOfWeek.Saturday)) should be("[[(t7){d1}]*[(h21){h10}]]")
    integrationApi.toTimeDomain(ValidityPeriod(0, 1, ValidityPeriodDayOfWeek.Saturday)) should be("[[(t7){d1}]*[(h0){h1}]]")
    integrationApi.toTimeDomain(ValidityPeriod(0, 24, ValidityPeriodDayOfWeek.Saturday)) should be("[[(t7){d1}]*[(h0){h24}]]")

    integrationApi.toTimeDomain(ValidityPeriod(6, 10, ValidityPeriodDayOfWeek.Sunday)) should be("[[(t1){d1}]*[(h6){h4}]]")
    integrationApi.toTimeDomain(ValidityPeriod(23, 24, ValidityPeriodDayOfWeek.Sunday)) should be("[[(t1){d1}]*[(h23){h1}]]")
    integrationApi.toTimeDomain(ValidityPeriod(21, 7, ValidityPeriodDayOfWeek.Sunday)) should be("[[(t1){d1}]*[(h21){h10}]]")
    integrationApi.toTimeDomain(ValidityPeriod(0, 1, ValidityPeriodDayOfWeek.Sunday)) should be("[[(t1){d1}]*[(h0){h1}]]")
    integrationApi.toTimeDomain(ValidityPeriod(0, 24, ValidityPeriodDayOfWeek.Sunday)) should be("[[(t1){d1}]*[(h0){h24}]]")
  }

  test("encode validity period to time domain With Minutes") {
    integrationApi.toTimeDomainWithMinutes(ValidityPeriod(6, 10, ValidityPeriodDayOfWeek.Weekday, 30, 15))  should be("[[(t2){d5}]*[(h6m30){h3m45}]]")
    integrationApi.toTimeDomainWithMinutes(ValidityPeriod(23, 24, ValidityPeriodDayOfWeek.Weekday, 45, 0)) should be("[[(t2){d5}]*[(h23m45){h0m15}]]")
    integrationApi.toTimeDomainWithMinutes(ValidityPeriod(21, 7, ValidityPeriodDayOfWeek.Weekday, 55, 20)) should be("[[(t2){d5}]*[(h21m55){h9m25}]]")
    integrationApi.toTimeDomainWithMinutes(ValidityPeriod(0, 1, ValidityPeriodDayOfWeek.Weekday, 5, 25)) should be("[[(t2){d5}]*[(h0m5){h1m20}]]")
    integrationApi.toTimeDomainWithMinutes(ValidityPeriod(0, 24, ValidityPeriodDayOfWeek.Weekday, 0, 0)) should be("[[(t2){d5}]*[(h0m0){h24m0}]]")

    integrationApi.toTimeDomainWithMinutes(ValidityPeriod(6, 10, ValidityPeriodDayOfWeek.Saturday, 30, 15))  should be("[[(t7){d1}]*[(h6m30){h3m45}]]")
    integrationApi.toTimeDomainWithMinutes(ValidityPeriod(23, 24, ValidityPeriodDayOfWeek.Saturday, 45, 0)) should be("[[(t7){d1}]*[(h23m45){h0m15}]]")
    integrationApi.toTimeDomainWithMinutes(ValidityPeriod(21, 7, ValidityPeriodDayOfWeek.Saturday, 55, 20)) should be("[[(t7){d1}]*[(h21m55){h9m25}]]")
    integrationApi.toTimeDomainWithMinutes(ValidityPeriod(0, 1, ValidityPeriodDayOfWeek.Saturday, 5, 25)) should be("[[(t7){d1}]*[(h0m5){h1m20}]]")
    integrationApi.toTimeDomainWithMinutes(ValidityPeriod(0, 24, ValidityPeriodDayOfWeek.Saturday, 0, 0)) should be("[[(t7){d1}]*[(h0m0){h24m0}]]")

    integrationApi.toTimeDomainWithMinutes(ValidityPeriod(6, 10, ValidityPeriodDayOfWeek.Sunday, 30, 15))  should be("[[(t1){d1}]*[(h6m30){h3m45}]]")
    integrationApi.toTimeDomainWithMinutes(ValidityPeriod(23, 24, ValidityPeriodDayOfWeek.Sunday, 45, 0)) should be("[[(t1){d1}]*[(h23m45){h0m15}]]")
    integrationApi.toTimeDomainWithMinutes(ValidityPeriod(21, 7, ValidityPeriodDayOfWeek.Sunday, 55, 20)) should be("[[(t1){d1}]*[(h21m55){h9m25}]]")
    integrationApi.toTimeDomainWithMinutes(ValidityPeriod(0, 1, ValidityPeriodDayOfWeek.Sunday, 5, 25)) should be("[[(t1){d1}]*[(h0m5){h1m20}]]")
    integrationApi.toTimeDomainWithMinutes(ValidityPeriod(0, 24, ValidityPeriodDayOfWeek.Sunday, 0, 0)) should be("[[(t1){d1}]*[(h0m0){h24m0}]]")
  }

  test("encode manouvre") {
    val manoeuvre = Manoeuvre(1,
        Seq(ManoeuvreElement(1, linkId1, linkId2, ElementTypes.FirstElement),
            ManoeuvreElement(1, linkId2, linkId3, ElementTypes.IntermediateElement),
            ManoeuvreElement(1, linkId3, linkId4, ElementTypes.IntermediateElement),
            ManoeuvreElement(1, linkId4, linkId5, ElementTypes.IntermediateElement),
            ManoeuvreElement(1, linkId5, "", ElementTypes.LastElement)),
        Set.empty,Nil, None, None, "", DateTime.now, "", false)

      val result = integrationApi.manouvresToApi(Seq(manoeuvre))

      result.length should be(1)
      result.head.get("elements") should be(Some(Seq(linkId1,linkId2,linkId3,linkId4,linkId5)))
      result.head.get("sourceLinkId") should equal(Some(linkId1))
      result.head.get("destLinkId") should equal(Some(linkId5))
  }

  test("geometryWKTForLinearAssets provides proper geometry") {
    val (header, returntxt) =
      integrationApi.geometryWKTForLinearAssets(Seq(Point(0.0, 0.0, 0.0), Point(1.0, 0.0, 0.5), Point(4.0, 4.0, 1.5)))
    header should be ("geometryWKT")
    returntxt should be ("LINESTRING ZM (0.0 0.0 0.0 0.0, 1.0 0.0 0.5 1.0, 4.0 4.0 1.5 6.0)")

  }

  test("Validate if sevenRestriction JSON generator return all required keys"){
    val roadLinkFromDb = RoadLink("1b10c3d0-0bcf-4467-aa7b-71c769918d10:1",List(Point(148500.251,6682412.563,4.914999999993597),
      Point(148500.277,6682419.949,4.911999999996624), Point(148498.659,6682436.121,5.232999999992899),
      Point(148500.32,6682450.942,5.095000000001164), Point(148505.67,6682461.014,4.81699999999546),
      Point(148515.38,6682471.428,4.838000000003376), Point(148524.065,6682480.948,4.811000000001513),
      Point(148528.798,6682486.726,4.876000000003842), Point(148536.287,6682495.204,5.297999999995227),
      Point(148545.1,6682504.235,5.349000000001979), Point(148554.27,6682511.474,5.100999999995111),
      Point(148559.451,6682514.659,5.039000000004307)),126.24650466596506,Private,99,TrafficDirection.BothDirections,
      UnknownLinkType,Some("16.12.2016 14:34:56"),Some(AutoGeneratedUsername.automaticAdjustment),
      Map("LAST_EDITED_DATE" -> BigInt(1481891696000L), "MTKHEREFLIP" -> 1, "MTKID" -> 67439354, "VERTICALACCURACY" -> 201,
        "CONSTRUCTIONTYPE" -> 0, "SURFACETYPE" -> 1, "MTKCLASS" -> 12141, "points" -> List(Map("x" -> 148500.251,
          "y" -> 6682412.563, "z" -> 4.914999999993597, "m" -> 0), Map("x" -> 148500.277, "y" -> 6682419.949,
          "z" -> 4.911999999996624, "m" -> 7.385999999998603), Map("x" -> 148498.659, "y" -> 6682436.121,
          "z" -> 5.232999999992899, "m" -> 23.63880000000063), Map("x" -> 148500.32, "y" -> 6682450.942,
          "z" -> 5.095000000001164, "m" -> 38.55259999999544), Map("x" -> 148505.67, "y" -> 6682461.014,
          "z" -> 4.81699999999546, "m" -> 49.957299999994575), Map("x" -> 148515.38, "y" -> 6682471.428,
          "z" -> 4.838000000003376, "m" -> 64.19580000000133), Map("x" -> 148524.065, "y" -> 6682480.948,
          "z" -> 4.811000000001513, "m" -> 77.08220000000438), Map("x" -> 148528.798, "y" -> 6682486.726,
          "z" -> 4.876000000003842, "m" -> 84.55130000000645), Map("x" -> 148536.287, "y" -> 6682495.204,
          "z" -> 5.297999999995227, "m" -> 95.86329999999725), Map("x" -> 148545.1, "y" -> 6682504.235,
          "z" -> 5.349000000001979, "m" -> 108.48179999999411), Map("x" -> 148554.27, "y" -> 6682511.474,
          "z" -> 5.100999999995111, "m" -> 120.16479999999865), Map("x" -> 148559.451, "y" -> 6682514.659,
          "z" -> 5.039000000004307, "m" -> 126.24649999999383)), "geometryWKT" -> "LINESTRING ZM (148500.251 6682412.563 4.914999999993597 0, 148500.277 6682419.949 4.911999999996624 7.385999999998603, 148498.659 6682436.121 5.232999999992899 23.63880000000063, 148500.32 6682450.942 5.095000000001164 38.55259999999544, 148505.67 6682461.014 4.81699999999546 49.957299999994575, 148515.38 6682471.428 4.838000000003376 64.19580000000133, 148524.065 6682480.948 4.811000000001513 77.08220000000438, 148528.798 6682486.726 4.876000000003842 84.55130000000645, 148536.287 6682495.204 5.297999999995227 95.86329999999725, 148545.1 6682504.235 5.349000000001979 108.48179999999411, 148554.27 6682511.474 5.100999999995111 120.16479999999865, 148559.451 6682514.659 5.039000000004307 126.24649999999383)",
        "VERTICALLEVEL" -> 0, "MUNICIPALITYCODE" -> 766, "CREATED_DATE" -> 1446398762000L, "HORIZONTALACCURACY" -> 3000),
      ConstructionType.InUse,NormalLinkInterface,List())
    when(mockRoadLinkService.getRoadLinksWithComplementaryByMunicipalityUsingCache(766)).thenReturn(Seq(roadLinkFromDb))
    val requiredKeys = Set("linkId","linkSource","startMeasure","side_code","muokattu_viimeksi","points","generatedValue","geometryWKT","endMeasure","value","id")
    val jsonResult = integrationApi.sevenRestrictionToApi(30, 766)

    val jsonToValidate = jsonResult.head.filterNot{ case (key, value) => value == None ||  value.toString.trim.isEmpty }

    jsonToValidate.keySet.size should be (requiredKeys.size)
    jsonToValidate.keySet.equals(requiredKeys) should be (true)

  }

  val linkId: String = LinkIdGenerator.generateRandom()
  val roadLink = RoadLink(
    linkId, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

  when(mockRoadLinkService.getRoadLinksWithComplementaryByMunicipalityUsingCache(any[Int])).thenReturn(Seq(roadLink))
  when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(any[Set[String]], any[Boolean])).thenReturn(Seq(roadLink))

  test("care class value is returned from api as integer") {
    val careClassValue = DynamicValue(DynamicAssetValue(Seq(
      DynamicProperty("hoitoluokat_talvihoitoluokka", "single_choice", false, Seq(DynamicPropertyValue(20))),
      DynamicProperty("hoitoluokat_viherhoitoluokka", "single_choice", false, Seq(DynamicPropertyValue(3)))
    )))

    runWithRollback {
      testDynamicLinearAssetService.create(Seq(NewLinearAsset(linkId, 0, 150, careClassValue, SideCode.BothDirections.value, 0, None)),
        CareClass.typeId, "test", 0)
      val linearAssetFromApi = integrationApi.linearAssetsToApi(CareClass.typeId, 235).head
      val (assetSideCode, assetValue) = (linearAssetFromApi.get("side_code").get, linearAssetFromApi.get("value").get)
      assetSideCode.isInstanceOf[Int] should be(true)
      assetValue match {
        case Some(value) => value.isInstanceOf[Int] should be(true)
        case _ => throw new NoSuchElementException("value parameter not found")
      }
    }
  }

  test("bogie weight value is returned as integer") {
    val bogieWeightValue = DynamicValue(DynamicAssetValue(Seq(
      DynamicProperty("bogie_weight_2_axel", "number", false, Seq(DynamicPropertyValue(2000))),
      DynamicProperty("bogie_weight_3_axel", "number", false, Seq(DynamicPropertyValue(3000)))
    )))

    runWithRollback {
      testBogieWeightLimitService.create(Seq(NewLinearAsset(linkId, 0, 150, bogieWeightValue, SideCode.BothDirections.value, 0, None)), BogieWeightLimit.typeId, "test", 0)
      val twoAxelBogieWeightLimit = integrationApi.bogieWeightLimitsToApi(235).head.get("twoAxelValue").get
      twoAxelBogieWeightLimit.isInstanceOf[Int] should be(true)
      val threeAxelBogieWeightLimit = integrationApi.bogieWeightLimitsToApi(235).head.get("threeAxelValue").get
      threeAxelBogieWeightLimit.isInstanceOf[Int] should be(true)
    }
  }

  test("damaged by thaw values are returned as integer") {
    val damagedByThawValues = DynamicValue(DynamicAssetValue(Seq(
      DynamicProperty("annual_repetition", "number", false, Seq(DynamicPropertyValue(1))),
    DynamicProperty("kelirikko", "number", false, Seq(DynamicPropertyValue(10)))
    )))

    runWithRollback {
      testDamagedByThawService.create(Seq(NewLinearAsset(linkId, 0, 150, damagedByThawValues, SideCode.BothDirections.value, 0, None)), DamagedByThaw.typeId, "test", 0)
      val damagedByThawFromApi = integrationApi.damagedByThawToApi(235).head
      val (annualRepetition, value) = (damagedByThawFromApi.get("annual_repetition").get, damagedByThawFromApi.get("value").get)
      annualRepetition.isInstanceOf[Int] should be(true)
      value.isInstanceOf[Int] should be(true)
    }
  }

  test("parking prohibition type is returned as integer") {
    val parkingProhibitionValues = DynamicValue(DynamicAssetValue(Seq(
      DynamicProperty("parking_prohibition", "number", false, Seq(DynamicPropertyValue(1)))
    )))

    runWithRollback {
      testParkingProhibitionService.create(Seq(NewLinearAsset(linkId, 0, 150, parkingProhibitionValues, SideCode.AgainstDigitizing.value, 0, None)), ParkingProhibition.typeId, "test", 0)
      val parkingProhibitionsFromApi = integrationApi.parkingProhibitionsToApi(235).head
      val parkingProhibitionType = parkingProhibitionsFromApi.get("parking_prohibition").get
      parkingProhibitionType.isInstanceOf[Int] should be(true)
    }
  }

  test("certain traffic sign values are returned as integer") {
    val properties = Seq(
      Property(1L, "trafficSigns_type", "", false, Seq(PropertyValue("2"))),
      Property(1L, "old_traffic_code", "", false, Seq(PropertyValue("1"))),
      Property(1L, "trafficSigns_value", "", false, Seq(PropertyValue("50"))),
      Property(1L, "trafficSigns_info", "", false, Seq(PropertyValue("test"))),
      Property(1L, "municipality_id", "", false, Seq(PropertyValue("1", propertyDisplayValue = Some("235")))),
      Property(1L, "main_sign_text", "", false, Seq(PropertyValue("test"))),
      Property(1L, "structure", "", false, Seq(PropertyValue("1"))),
      Property(1L, "condition", "", false, Seq(PropertyValue("1"))),
      Property(1L, "size", "", false, Seq(PropertyValue("10"))),
      Property(1L, "height", "", false, Seq(PropertyValue("10"))),
      Property(1L, "coating_type", "", false, Seq(PropertyValue("1"))),
      Property(1L, "sign_material", "", false, Seq(PropertyValue("2"))),
      Property(1L, "location_specifier", "", false, Seq(PropertyValue("11"))),
      Property(1L, "terrain_coordinates_x", "", false, Seq(PropertyValue("1", propertyDisplayValue = Some("1,23")))),
      Property(1L, "terrain_coordinates_y", "", false, Seq(PropertyValue("2", propertyDisplayValue = Some("4,56")))),
      Property(1L, "lane_type", "", false, Seq(PropertyValue("1"))),
      Property(1L, "lane", "", false, Seq(PropertyValue("1", propertyDisplayValue = Some("1")))),
      Property(1L, "main_sign_text", "", false, Seq(PropertyValue("test"))),
      Property(1L, "life_cycle", "", false, Seq(PropertyValue("1"))),
      Property(1L, "trafficSign_start_date", "", false, Seq(PropertyValue("12.12.2000"))),
      Property(1L, "trafficSign_end_date", "", false, Seq(PropertyValue("24.12.2000"))),
      Property(1L, "type_of_damage", "", false, Seq(PropertyValue("2"))),
      Property(1L, "urgency_of_repair", "", false, Seq(PropertyValue("1"))),
      Property(1L, "lifespan_left", "", false, Seq(PropertyValue("1", propertyDisplayValue = Some("1")))),
      Property(1L, "additional_panel", "", false, Seq(AdditionalPanel(1, "testInfo", "testValue", 1, "testText", 1, 1, 1)))
    )

    val trafficSignForApi = PersistedTrafficSign(1L, "100L", 11.11, 22.22, 33.33, false, 0L, 235, properties, None, None,
      None, None, 1, Some(1), LinkGeomSource.Unknown, false)

    val trafficSignValuesFromApi = integrationApi.trafficSignsToApi(Seq(trafficSignForApi)).head
    trafficSignValuesFromApi.get("typeOfDamage").get.isInstanceOf[Int] should be(true)
    trafficSignValuesFromApi.get("oldTrafficCode").get.isInstanceOf[Option[Int]] should be(true)
    trafficSignValuesFromApi.get("size").get.isInstanceOf[Int] should be(true)
    trafficSignValuesFromApi.get("height").get.isInstanceOf[Int] should be(true)
    trafficSignValuesFromApi.get("lane").get.isInstanceOf[Int] should be(true)
    trafficSignValuesFromApi.get("structure").get.isInstanceOf[Int] should be(true)
    trafficSignValuesFromApi.get("condition").get.isInstanceOf[Int] should be(true)
    trafficSignValuesFromApi.get("coatingType").get.isInstanceOf[Int] should be(true)
    trafficSignValuesFromApi.get("urgencyOfRepair").get.isInstanceOf[Int] should be(true)
    trafficSignValuesFromApi.get("lifespanLeft").get.isInstanceOf[Int] should be(true)
    trafficSignValuesFromApi.get("laneType").get.isInstanceOf[Int] should be(true)
    trafficSignValuesFromApi.get("signMaterial").get.isInstanceOf[Int] should be(true)
    trafficSignValuesFromApi.get("lifeCycle").get.isInstanceOf[Int] should be(true)
    trafficSignValuesFromApi.get("terrainCoordinatesX").get.isInstanceOf[Float] should be(true)
    trafficSignValuesFromApi.get("terrainCoordinatesY").get.isInstanceOf[Float] should be(true)
    val additionalPanel = trafficSignValuesFromApi.get("additionalPanels").get.asInstanceOf[List[Map[String, Any]]].head
    additionalPanel.get("additionalPanelSize").get.isInstanceOf[Int] should be(true)
    additionalPanel.get("additionalPanelCoatingType").get.isInstanceOf[Int] should be(true)
    additionalPanel.get("additionalPanelColor").get.isInstanceOf[Int] should be(true)
  }

  test("traffic sign api conversion returns default value on invalid data") {

    val properties = Seq(
      Property(1L, "trafficSigns_type", "", false, Seq(PropertyValue("a"))),
      Property(1L, "old_traffic_code", "", false, Seq(PropertyValue("b"))),
      Property(1L, "trafficSigns_value", "", false, Seq(PropertyValue("c"))),
      Property(1L, "trafficSigns_info", "", false, Seq(PropertyValue("test"))),
      Property(1L, "municipality_id", "", false, Seq(PropertyValue("235"))),
      Property(1L, "main_sign_text", "", false, Seq(PropertyValue("test"))),
      Property(1L, "structure", "", false, Seq(PropertyValue("d"))),
      Property(1L, "condition", "", false, Seq(PropertyValue("e"))),
      Property(1L, "size", "", false, Seq(PropertyValue("f"))),
      Property(1L, "height", "", false, Seq(PropertyValue("g"))),
      Property(1L, "coating_type", "", false, Seq(PropertyValue("h"))),
      Property(1L, "sign_material", "", false, Seq(PropertyValue("i"))),
      Property(1L, "location_specifier", "", false, Seq(PropertyValue("j"))),
      Property(1L, "terrain_coordinates_x", "", false, Seq(PropertyValue("k"))),
      Property(1L, "terrain_coordinates_y", "", false, Seq(PropertyValue("l"))),
      Property(1L, "lane_type", "", false, Seq(PropertyValue("m"))),
      Property(1L, "lane", "", false, Seq(PropertyValue("n", propertyDisplayValue = Some("o")))),
      Property(1L, "main_sign_text", "", false, Seq(PropertyValue("test"))),
      Property(1L, "life_cycle", "", false, Seq(PropertyValue("p"))),
      Property(1L, "trafficSign_start_date", "", false, Seq(PropertyValue("q"))),
      Property(1L, "trafficSign_end_date", "", false, Seq(PropertyValue("r"))),
      Property(1L, "type_of_damage", "", false, Seq(PropertyValue("s"))),
      Property(1L, "urgency_of_repair", "", false, Seq(PropertyValue("t"))),
      Property(1L, "lifespan_left", "", false, Seq(PropertyValue("u", propertyDisplayValue = Some("w")))),
      Property(1L, "additional_panel", "", false, Seq(AdditionalPanel(1, "testInfo", "testValue", 1, "testText", 1, 1, 1)))
    )

    val trafficSignForApi = PersistedTrafficSign(1L, "100L", 11.11, 22.22, 33.33, false, 0L, 235, properties, None, None,
      None, None, 1, Some(1), LinkGeomSource.Unknown, false)

    val trafficSignValuesFromApi = integrationApi.trafficSignsToApi(Seq(trafficSignForApi)).head
    trafficSignValuesFromApi.get("typeOfDamage").get should be("")
    trafficSignValuesFromApi.get("oldTrafficCode").get should be(None)
    trafficSignValuesFromApi.get("size").get should be("")
    trafficSignValuesFromApi.get("height").get should be("")
    trafficSignValuesFromApi.get("lane").get should be("")
    trafficSignValuesFromApi.get("structure").get should be("")
    trafficSignValuesFromApi.get("condition").get should be("")
    trafficSignValuesFromApi.get("coatingType").get should be("")
    trafficSignValuesFromApi.get("urgencyOfRepair").get should be("")
    trafficSignValuesFromApi.get("lifespanLeft").get should be("")
    trafficSignValuesFromApi.get("laneType").get should be("")
    trafficSignValuesFromApi.get("signMaterial").get should be("")
    trafficSignValuesFromApi.get("lifeCycle").get should be("")
    trafficSignValuesFromApi.get("terrainCoordinatesX").get should be("")
    trafficSignValuesFromApi.get("terrainCoordinatesY").get should be("")
  }
}

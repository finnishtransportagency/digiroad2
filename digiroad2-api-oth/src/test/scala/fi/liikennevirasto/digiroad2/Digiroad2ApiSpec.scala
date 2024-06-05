package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.authentication.SessionApi
import fi.liikennevirasto.digiroad2.client.{FeatureClass, RoadLinkClient, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MassTransitStopDao, MunicipalityDao, RoadLinkDAO}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.service.linearasset._
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.pointasset._
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.{MassTransitStop, MassTransitStopService, MassTransitStopWithProperties}
import fi.liikennevirasto.digiroad2.util.{GeometryTransform, LinkIdGenerator, RoadSide}
import org.joda.time.DateTime
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization.write
import org.mockito.AdditionalAnswers
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, Tag}

import scala.concurrent.Promise

case class LinearAssetFromApi(id: Option[Long], linkId: String, sideCode: Int, value: Option[Int], points: Seq[Point], expired: Boolean = false)
case class DirectionalTrafficSignFromApi(id: Long, linkId: String, lon: Double, lat: Double, mValue: Double, floating: Boolean, timeStamp: Long, municipalityCode: Int,
                                         validityDirection: Int, text: Option[String], bearing: Option[Int], createdBy: Option[String] = None, createdAt: Option[DateTime] = None,
                                         modifiedBy: Option[String] = None, modifiedAt: Option[DateTime] = None, geometry: Seq[Point] = Nil)
case class MassLinearAssetFromApi(geometry: Seq[Point], sideCode: Int, value: Option[Value], administrativeClass: Int)

class Digiroad2ApiSpec extends AuthenticatedApiSpec with BeforeAndAfter {
  case object PointAssetSerializer extends CustomSerializer[PointAssetValue](format =>
    ({
      case jsonObj: JObject =>
        val propertyValue = (jsonObj \ "propertyValue").extract[String]
        val propertyDisplayValue = (jsonObj \ "propertyDisplayValue").extractOpt[String]
        val checked = (jsonObj \ "checked").extract[Boolean]

        PropertyValue(propertyValue, propertyDisplayValue, checked)
    },
      {
        case _ => null
      }))

  protected implicit val jsonFormats: Formats = DefaultFormats + PointAssetSerializer
  val TestPropertyId = "katos"
  val TestPropertyId2 = "pysakin_tyyppi"
  val CreatedTestAssetId = 300004
  val roadLinkGeometry = List(Point(374567.632,6677255.6,0.0), Point(374603.57,6677262.009,0.0), Point(374631.683,6677267.545,0.0), Point(374651.471,6677270.245,0.0), Point(374669.739,6677273.332,0.0), Point(374684.567,6677277.323,0.0))
  val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
  val mockGeometryTransform = MockitoSugar.mock[GeometryTransform]
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val roadAddress = RoadAddress(None, 1, 1, Track.Combined, 1)
  val linkId1: String = LinkIdGenerator.generateRandom()
  val linkId2: String = LinkIdGenerator.generateRandom()
  val linkId3: String = LinkIdGenerator.generateRandom()
  val linkId4: String = "dd8bdb73-b8b4-4c81-a404-1126c4f4e714:1"
  val linkId5: String = "fbb3c1bd-432d-46fd-9588-00dec41042dd:1"
  val linkId6: String = "d06dbdc7-366a-4ee3-a41f-c1d5010859ae:1"
  val linkId7: String = "7cf9e493-151c-4afd-96a4-8a6880cf18e5:1"
  val linkId8: String = "ef043f17-dadc-435e-9e3f-50a19c3c84f3:1"

  when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[String], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn((roadAddress , RoadSide.Right))
  when(mockRoadLinkService.fetchByLinkId(linkId1))
    .thenReturn(Some(RoadLinkFetched(linkId1, 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockRoadLinkService.fetchByLinkId(linkId2))
    .thenReturn(Some(RoadLinkFetched(linkId2, 235, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockRoadLinkService.fetchByLinkId(linkId3))
    .thenReturn(Some(RoadLinkFetched(linkId3, 235, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  private val fetchedRoadlinksForBoundingBox = List(
    RoadLinkFetched(linkId3, 235, Seq(Point(0, 0), Point(0, 10)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
    RoadLinkFetched(linkId4, 235, Seq(Point(0, 0), Point(120, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers))
  
  when(mockRoadLinkService.fetchByLinkId(linkId5))
    .thenReturn(Some(RoadLinkFetched(linkId5, 91,  List(Point(0.0, 0.0), Point(117.318, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockRoadLinkService.fetchByLinkId(linkId6))
    .thenReturn(Some(RoadLinkFetched(linkId6, 235, roadLinkGeometry, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockRoadLinkService.fetchRoadlinksByIds(Set(linkId5, linkId8, linkId7)))
    .thenReturn(Seq(RoadLinkFetched(linkId5, 91,  List(Point(0.0, 0.0), Point(117.318, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
                    RoadLinkFetched(linkId8, 91,  List(Point(117.318, 0.0), Point(127.239, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
                    RoadLinkFetched(linkId7, 91,  List(Point(127.239, 0.0), Point(146.9, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockRoadLinkService.fetchRoadlinksByIds(Set(linkId4)))
    .thenReturn(List(RoadLinkFetched(linkId4, 235, Seq(Point(0, 0), Point(120, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))

  def toRoadLink(l: RoadLinkFetched) = {
    RoadLink(l.linkId, l.geometry, GeometryUtils.geometryLength(l.geometry),
      l.administrativeClass, 1, l.trafficDirection, UnknownLinkType, None, None, l.attributes + ("MUNICIPALITYCODE" -> BigInt(l.municipalityCode)))
  }
  
  when(mockRoadLinkService.getRoadLinkByLinkId(linkId1))
    .thenReturn(Some(toRoadLink(RoadLinkFetched(linkId1, 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers))))
  when(mockRoadLinkService.getRoadLinkByLinkId(linkId2))
    .thenReturn(Some(toRoadLink(RoadLinkFetched(linkId2, 235, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers))))
  when(mockRoadLinkService.getRoadLinkByLinkId(linkId3))
    .thenReturn(Some(toRoadLink(RoadLinkFetched(linkId3, 235, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers))))
  when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId1))
    .thenReturn(Some(toRoadLink(RoadLinkFetched(linkId1, 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers))))
  when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId2))
    .thenReturn(Some(toRoadLink(RoadLinkFetched(linkId2, 235, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers))))
  when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId3))
    .thenReturn(Some(toRoadLink(RoadLinkFetched(linkId3, 235, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers))))
  when(mockRoadLinkService.getRoadLinksWithComplementaryByBoundsAndMunicipalities(any[BoundingRectangle], any[Set[Int]], any[Boolean],any[Boolean]))
    .thenReturn(fetchedRoadlinksForBoundingBox.map(toRoadLink))
  when(mockRoadLinkService.getRoadLinksByBoundsAndMunicipalities(any[BoundingRectangle], any[Set[Int]],any[Boolean]))
    .thenReturn(fetchedRoadlinksForBoundingBox.map(toRoadLink))
  when(mockRoadLinkService.getRoadLinks(any[BoundingRectangle], any[Set[Int]],any[Boolean]))
    .thenReturn((fetchedRoadlinksForBoundingBox.map(toRoadLink), Nil))
  when(mockRoadLinkService.getRoadLinksWithComplementary(any[BoundingRectangle], any[Set[Int]], any[Boolean],any[Boolean]))
    .thenReturn((fetchedRoadlinksForBoundingBox.map(toRoadLink), Nil))
  when(mockRoadLinkService.getRoadLinkByLinkId(linkId5))
    .thenReturn(Some(toRoadLink(RoadLinkFetched(linkId5, 91,  List(Point(0.0, 0.0), Point(117.318, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers))))
  when(mockRoadLinkService.getRoadLinkByLinkId(linkId6))
    .thenReturn(Some(toRoadLink(RoadLinkFetched(linkId6, 235, roadLinkGeometry, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers))))
  when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId6))
    .thenReturn(Some(toRoadLink(RoadLinkFetched(linkId6, 235, roadLinkGeometry, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers))))
  when(mockRoadLinkService.fetchRoadlinksByIds(Set(linkId4)))
    .thenReturn(List(RoadLinkFetched(linkId4, 235, Seq(Point(0, 0), Point(120, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockRoadLinkService.getRoadLinksWithComplementary(any[BoundingRectangle], any[Set[Int]], any[Boolean],any[Boolean])).thenReturn((fetchedRoadlinksForBoundingBox.map(toRoadLink), Nil))
  when(mockRoadLinkService.fetchRoadlinkAndComplementary(linkId5)).thenReturn(Some(RoadLinkFetched(linkId5, 91,  List(Point(0.0, 0.0), Point(117.318, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockRoadLinkService.fetchRoadlinkAndComplementary(linkId2))
    .thenReturn(Some(RoadLinkFetched(linkId2, 235, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockRoadLinkService.fetchRoadlinkAndComplementary(linkId1))
    .thenReturn(Some(RoadLinkFetched(linkId1, 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))

  when(mockRoadLinkService.fetchRoadlinksAndComplementaries(Set(linkId5, linkId8, linkId7))).thenReturn(Seq(RoadLinkFetched(linkId5, 91,  List(Point(0.0, 0.0), Point(117.318, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
    RoadLinkFetched(linkId8, 91,  List(Point(117.318, 0.0), Point(127.239, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
    RoadLinkFetched(linkId7, 91,  List(Point(127.239, 0.0), Point(146.9, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockRoadLinkService.fetchRoadlinkAndComplementary(linkId5)).thenReturn(Some(RoadLinkFetched(linkId5, 91,  List(Point(0.0, 0.0), Point(117.318, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))

  when(mockRoadLinkService.fetchRoadlinkAndComplementary(linkId5)).thenReturn(Some(RoadLinkFetched(linkId5, 91,  List(Point(0.0, 0.0), Point(117.318, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))

  val mockRoadAddressService = MockitoSugar.mock[RoadAddressService]
  when(mockRoadAddressService.roadLinkWithRoadAddress(any[Seq[RoadLink]],any[String])).thenAnswer(AdditionalAnswers.returnsFirstArg())
  when(mockRoadAddressService.linearAssetWithRoadAddress(any[Seq[Seq[PieceWiseLinearAsset]]])).thenAnswer(AdditionalAnswers.returnsFirstArg())

  when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId5)).thenReturn(Some(RoadLink(linkId5, List(Point(0.0, 0.0), Point(117.318, 0.0)), 117.318, Municipality, 1, TrafficDirection.UnknownDirection, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)))))
  when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId2))
    .thenReturn(Some(RoadLink(linkId2, Nil, 0, Municipality, 1, TrafficDirection.UnknownDirection, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))))
  when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId1))
    .thenReturn(Some(RoadLink(linkId1, Nil, 0, Municipality, 1, TrafficDirection.UnknownDirection, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)))))

  when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkId5)).thenReturn(Some(RoadLink(linkId5, List(Point(0.0, 0.0), Point(117.318, 0.0)), 117.318, Municipality, 1, TrafficDirection.UnknownDirection, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)))))
  val testObstacleService = new ObstacleService(mockRoadLinkService)
  val testRailwayCrossingService = new RailwayCrossingService(mockRoadLinkService)
  val testDirectionalTrafficSignService = new DirectionalTrafficSignService(mockRoadLinkService)
  val testSpeedLimitProvider = new SpeedLimitService(new DummyEventBus, mockRoadLinkService)
  val testMassTransitStopService: MassTransitStopService = new MassTransitStopService {
    override def eventbus: DigiroadEventBus = new DummyEventBus
    override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
    override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
    override val massTransitStopDao: MassTransitStopDao = new MassTransitStopDao
    override val municipalityDao: MunicipalityDao = new MunicipalityDao
    override val roadLinkService: RoadLinkService = mockRoadLinkService
    override val geometryTransform: GeometryTransform = mockGeometryTransform
  }
  val testLinearAssetService = new LinearAssetService(mockRoadLinkService, new DummyEventBus)
  val testLinearTotalWeightLimitService = new LinearTotalWeightLimitService(mockRoadLinkService, new DummyEventBus)
  val testServicePointService = new ServicePointService
  val testMaintenanceRoadServiceService = new MaintenanceService(mockRoadLinkService, new DummyEventBus)
  val testLinearMassLimitationService = new LinearMassLimitationService(mockRoadLinkService, new DynamicLinearAssetDao)
  val testPavedRoadService = new PavedRoadService(mockRoadLinkService, new DummyEventBus)
  val testRoadWidthService = new RoadWidthService(mockRoadLinkService, new DummyEventBus)
  val testNumericValueService = new NumericValueLinearAssetService(mockRoadLinkService, new DummyEventBus)
  val testProhibitionService = new ProhibitionService(mockRoadLinkService, new DummyEventBus)
  val testTextValueService = new TextValueLinearAssetService(mockRoadLinkService, new DummyEventBus)
  val testNumberOfLanesService = new NumberOfLanesService(mockRoadLinkService, new DummyEventBus)
  val testMassTransitLaneService = new MassTransitLaneService(mockRoadLinkService, new DummyEventBus)

  addServlet(new Digiroad2Api(mockRoadLinkService, mockRoadAddressService, testSpeedLimitProvider, testObstacleService, testRailwayCrossingService, testDirectionalTrafficSignService, testServicePointService, mockRoadLinkClient, testMassTransitStopService, testLinearAssetService, testLinearMassLimitationService, testMaintenanceRoadServiceService,
    testPavedRoadService, testRoadWidthService, linearTotalWeightLimitService = testLinearTotalWeightLimitService, numberOfLanesService = testNumberOfLanesService, massTransitLaneService = testMassTransitLaneService), "/*")
  addServlet(classOf[SessionApi], "/auth/*")

  test("provide header to indicate session still active", Tag("db")) {
    getWithUserAuth("/massTransitStops?bbox=374702,6677462,374870,6677780") {
      status should equal(200)
      response.getHeader(Digiroad2Context.Digiroad2ServerOriginatedResponseHeader) should be ("true")
    }
  }

  test("get mass transit stops", Tag("db")) {
    getWithUserAuth("/massTransitStops?bbox=374650,6677400,374920,6677820") {
      status should equal(200)
      val stops: List[MassTransitStop] = parse(body).extract[List[MassTransitStop]]
      stops.count(_.validityPeriod == "current") should be(1)
    }
  }

  test("get mass transit stops with bounding box for multiple municipalities", Tag("db")) {
    getWithUserAuth("/massTransitStops?bbox=373305,6676648,375755,6678084", "test2") {
      status should equal(200)
      parse(body).extract[List[MassTransitStop]].filterNot(_.id == 300000).size should be(5)
    }
  }

  test("get asset by id", Tag("db")) {
    getWithUserAuth("/massTransitStops/2") {
      status should equal(200)
      val parsedBody = parse(body)
      (parsedBody \ "id").extract[Int] should be(CreatedTestAssetId)
    }
    getWithUserAuth("/massTransitStops/9999999999999999") {
      status should equal(404)
    }
  }

  test("get enumerated property values", Tag("db")) {
    getWithUserAuth("/enumeratedPropertyValues/10") {
      status should equal(200)
      parse(body).extract[List[EnumeratedPropertyValue]].size should be(16)
    }
  }

  test("get startup parameters", Tag("db")) {
    getWithUserAuth("/startupParameters") {
      status should equal(200)
      val responseJson = parse(body)
      (responseJson \ "zoom").values should equal(3)
      (responseJson \ "lon").values should equal(470092)
      (responseJson \ "lat").values should equal(7504895)
    }
  }

  test("update operation requires authentication") {
    val body = propertiesToJson(SimplePointAssetProperty(TestPropertyId2, Seq(PropertyValue("3"))))
    putJsonWithUserAuth("/assets/" + CreatedTestAssetId, body.getBytes, username = "nonexistent") {
      status should equal(401)
    }
    putJsonWithUserAuth("/assets/" + CreatedTestAssetId, body.getBytes, username = "testviewer") {
      status should equal(401)
    }
  }

  test("validate request parameters when creating a new mass transit stop", Tag("db")) {
    val requestPayload = s"""{"lon": 7478014, "lat": 483655, "linkId": "$linkId2", "bearing": 0}"""
    postJsonWithUserAuth("/massTransitStops", requestPayload.getBytes, username = "silari") {
      status should equal(400)
    }
  }

  test("validate user rights when creating a new mass transit stop", Tag("db")) {
    val requestPayload = s"""{"lon": 0, "lat": 0, "linkId": "$linkId1", "bearing": 0}"""
    postJsonWithUserAuth("/massTransitStops", requestPayload.getBytes) {
      status should equal(401)
    }
  }

  test("mass transit stops cannot be retrieved without bounding box", Tag("db")) {
    getWithUserAuth("/massTransitStops") {
      status should equal(400)
    }
  }

  test("mass transit stops cannot be retrieved with massive bounding box", Tag("db")) {
    getWithUserAuth("/massTransitStops?bbox=324702,6677462,374870,6697780") {
      status should equal(400)
    }
  }

  test("write requests pass only if user is not in viewer role", Tag("db")) {
    postJsonWithUserAuth("/massTransitStops/", Array(), username = "testviewer") {
      status should equal(401)
    }
    postJsonWithUserAuth("/massTransitStops/", Array()) {
      // no asset id given, returning 404 is legal
      status should equal(404)
    }
  }

  test("get available properties for asset type", Tag("db")) {
    getWithUserAuth("/assetTypeProperties/10") {
      status should equal(200)
      val ps = parse(body).extract[List[Property]]
      ps.size should equal(54)
      val p1 = ps.find(_.publicId == TestPropertyId).get
      p1.publicId should be ("katos")
      p1.propertyType should be ("single_choice")
      p1.required should be (false)
      ps.find(_.publicId == "vaikutussuunta") should be ('defined)
    }
  }

  test("get localized property names for language") {
    // propertyID -> value
    getWithUserAuth("/assetPropertyNames/fi") {
      status should equal(200)
      val propertyNames = parse(body).extract[Map[String, String]]
      propertyNames("ensimmainen_voimassaolopaiva") should be("Ensimmäinen voimassaolopäivä")
      propertyNames("matkustajatunnus") should be("Matkustajatunnus")
    }
  }

  test("mass transit stop has properties defined in asset type properties", Tag("db")) {
    val propIds = getWithUserAuth("/assetTypeProperties/10") {
      status should equal(200)
      parse(body).extract[List[Property]].map(p => p.publicId).toSet
    }
    val assetPropNames = getWithUserAuth("/massTransitStops/2") {
      status should equal(200)
      parse(body).extract[MassTransitStopWithProperties].propertyData.map(p => p.publicId).toSet
    }
    propIds should be(assetPropNames)
  }

  test("updating speed limits requires an operator role") {
    putJsonWithUserAuth("/speedlimits", """{"value":{"isSuggested":false,"value":60}, "ids":[200114]}""".getBytes, username = "test") {
      status should equal(401)
    }
  }

  test("creating speed limit requires an operator role") {
    postJsonWithUserAuth("/speedlimits", """{"linkId":1611071, "startMeasure":0.0, "endMeasure":50.0, "value":{"isSuggested":false,"value":40}}""".getBytes, username = "test") {
      status should equal(401)
    }
  }

  case class RoadLinkHelper(linkId: String, points: Seq[Point],
                            administrativeClass: String, functionalClass: Int, trafficDirection: String,
                            modifiedAt: Option[String], modifiedBy: Option[String], linkType: Int)

  test("split speed limits requires an operator role") {
    postJsonWithUserAuth("/speedlimits/200114/split", """{"existingValue":30, "createdValue":40 , "splitMeasure":5 , "value":120}""".getBytes, username = "test") {
      status should equal(401)
    }
  }

  test("get numerical limits with bounding box", Tag("db")) {
    getWithUserAuth("/linearassets?typeId=30&bbox=374037,6677013,374540,6677675&withRoadAddress=true&zoom=10") {
      status should equal(200)
      val parsedBody = parse(body).extract[Seq[Seq[LinearAssetFromApi]]]
      parsedBody.size should be(3)
      parsedBody.flatMap(pb => pb.filter(_.id.isEmpty)).size should be(1)
      parsedBody.flatMap(pb => pb.filter(_.id.isDefined)).size should be(2)
    }
  }

  test("get numerical limits with bounding box should return bad request if typeId missing", Tag("db")) {
    getWithUserAuth("/linearassets?bbox=374037,6677013,374540,6677675&withRoadAddress=true&zoom=10") {
      status should equal(400)
    }
  }

  test("get complementary numerical limits with bounding box should return bad request if typeId missing", Tag("db")) {
    getWithUserAuth("/linearassets/complementary?bbox=374037,6677013,374540,6677675&withRoadAddress=true&zoom=10") {
      status should equal(400)
    }
  }

  test("get complementary numerical limits with bounding box", Tag("db")) {
    getWithUserAuth("/linearassets/complementary?typeId=30&bbox=374037,6677013,374540,6677675&withRoadAddress=true&zoom=10") {
      status should equal(200)
      val parsedBody = parse(body).extract[Seq[Seq[LinearAssetFromApi]]]
      parsedBody.size should be(3)
      parsedBody.flatMap(pb => pb.filter(_.id.isEmpty)).size should be(1)
      parsedBody.flatMap(pb => pb.filter(_.id.isDefined)).size should be(2)
    }
  }

  test("get mass Limitations Assets with bounding box should return bad request if typeId missing", Tag("db")) {
    getWithUserAuth("/linearassets/massLimitation?bbox=374037,6677013,374540,6677675&withRoadAddress=true") {
      status should equal(400)
    }
  }

  test("get mass Limitations Assets with bounding box", Tag("db")) {
    getWithUserAuth("/linearassets/massLimitation?typeId=60&bbox=374037,6677013,374540,6677675&withRoadAddress=false") {
      status should equal(200)
      val parsedBody = parse(body).extract[Seq[Seq[MassLinearAssetFromApi]]]
      parsedBody.size should be(1)
    }
  }

  test("get directional traffic signs with bounding box") {
    getWithUserAuth("/directionalTrafficSigns?bbox=374419,6677198,374467,6677281") {
      status should equal(200)
      val trafficSigns = parse(body).extract[Seq[DirectionalTrafficSignFromApi]]
      trafficSigns.size should be(1)
    }
  }

  private[this] def propertiesToJson(prop: SimplePointAssetProperty): String = {
    val json = write(Seq(prop))
    s"""{"properties":$json}"""
  }
}

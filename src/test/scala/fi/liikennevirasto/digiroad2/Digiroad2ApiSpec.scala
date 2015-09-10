package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.authentication.SessionApi
import fi.liikennevirasto.digiroad2.linearasset.SpeedLimit
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleSpeedLimitProvider
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization.write
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, Tag}

import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

class Digiroad2ApiSpec extends AuthenticatedApiSpec with BeforeAndAfter {
  protected implicit val jsonFormats: Formats = DefaultFormats
  val TestPropertyId = "katos"
  val TestPropertyId2 = "pysakin_tyyppi"
  val CreatedTestAssetId = 300004
  val roadLinkGeometry = List(Point(374567.632,6677255.6,0.0), Point(374603.57,6677262.009,0.0), Point(374631.683,6677267.545,0.0), Point(374651.471,6677270.245,0.0), Point(374669.739,6677273.332,0.0), Point(374684.567,6677277.323,0.0))
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  when(mockVVHClient.fetchVVHRoadlink(1l))
    .thenReturn(Some(VVHRoadlink(1l, 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockVVHClient.fetchVVHRoadlink(2l))
    .thenReturn(Some(VVHRoadlink(2l, 235, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockVVHClient.fetchVVHRoadlink(7478l))
    .thenReturn(Some(VVHRoadlink(7478l, 235, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockVVHClient.fetchVVHRoadlinks(any[BoundingRectangle], any[Set[Int]]))
    .thenReturn(List(
      VVHRoadlink(7478l, 235, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
      VVHRoadlink(388562360, 235, Seq(Point(0, 0), Point(120, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)
    ))
  when(mockVVHClient.fetchVVHRoadlink(362964704))
    .thenReturn(Some(VVHRoadlink(362964704l, 91,  List(Point(0.0, 0.0), Point(117.318, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockVVHClient.fetchVVHRoadlink(1140018963))
    .thenReturn(Some(VVHRoadlink(1140018963, 235, roadLinkGeometry, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockVVHClient.fetchVVHRoadlinks(Set(362964704l, 362955345l, 362955339l)))
    .thenReturn(Seq(VVHRoadlink(362964704l, 91,  List(Point(0.0, 0.0), Point(117.318, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
                    VVHRoadlink(362955345l, 91,  List(Point(117.318, 0.0), Point(127.239, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
                    VVHRoadlink(362955339l, 91,  List(Point(127.239, 0.0), Point(146.9, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))

  val testRoadLinkService = new VVHRoadLinkService(mockVVHClient, new DummyEventBus)
  val testSpeedLimitProvider = new OracleSpeedLimitProvider(new DummyEventBus, testRoadLinkService)
  val testMassTransitStopService: MassTransitStopService = new MassTransitStopService {
    override def roadLinkService: RoadLinkService = testRoadLinkService
    override def eventbus: DigiroadEventBus = new DummyEventBus
    override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
    override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  }
  val testLinearAssetService = new LinearAssetService(testRoadLinkService)

  addServlet(new Digiroad2Api(testRoadLinkService, testSpeedLimitProvider, testMassTransitStopService, testLinearAssetService), "/*")
  addServlet(classOf[SessionApi], "/auth/*")

  // TODO: For this test to mean anything the servlet should probably not use test mode authentication?
  ignore("require authentication", Tag("db")) {
    get("/massTransitStops?bbox=374702,6677462,374870,6677780&municipalityNumber=235") {
      status should equal(401)
    }
    getWithUserAuth("/massTransitStops?bbox=374702,6677462,374870,6677780&municipalityNumber=235", username = "test") {
      status should equal(200)
    }
  }

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

  // TODO: Remove side-effects from other tests so that list of floating stops is always deterministic
  ignore("get floating mass transit stops") {
    getWithUserAuth("/floatingMassTransitStops") {
      val response = parse(body).extract[Map[String, Seq[Long]]]
      status should equal(200)
      response.size should be(1)
      response should be(Map("Kauniainen" -> List(6)))
    }

    getWithOperatorAuth("/floatingMassTransitStops") {
      val response = parse(body).extract[Map[String, Seq[Long]]]
      status should equal(200)
      response.size should be(1)
      response should be(Map("Kauniainen" -> List(6)))
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
      parse(body).extract[List[EnumeratedPropertyValue]].size should be(12)
    }
  }

  test("get startup parameters", Tag("db")) {
    getWithUserAuth("/startupParameters") {
      status should equal(200)
      val responseJson = parse(body)
      (responseJson \ "zoom").values should equal(8)
      (responseJson \ "lon").values should equal(373560)
      (responseJson \ "lat").values should equal(6677676)
    }
  }

  test("update operation requires authentication") {
    val body = propertiesToJson(SimpleProperty(TestPropertyId2, Seq(PropertyValue("3"))))
    putJsonWithUserAuth("/assets/" + CreatedTestAssetId, body.getBytes, username = "nonexistent") {
      status should equal(401)
    }
    putJsonWithUserAuth("/assets/" + CreatedTestAssetId, body.getBytes, username = "testviewer") {
      status should equal(401)
    }
  }

  test("validate request parameters when creating a new mass transit stop", Tag("db")) {
    val requestPayload = """{"lon": 0, "lat": 0, "mmlId": 2, "bearing": 0}"""
    postJsonWithUserAuth("/massTransitStops", requestPayload.getBytes) {
      status should equal(400)
    }
  }

  test("validate user rights when creating a new mass transit stop", Tag("db")) {
    val requestPayload = """{"lon": 0, "lat": 0, "mmlId": 1, "bearing": 0}"""
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
      ps.size should equal(31)
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
      parse(body).extract[List[Property]].map(p => p.publicId).toSet
    }
    val assetPropNames = getWithUserAuth("/massTransitStops/2") {
      parse(body).extract[MassTransitStopWithProperties].propertyData.map(p => p.publicId).toSet
    }
    propIds should be(assetPropNames)
  }

  test("updating speed limits requires an operator role") {
    putJsonWithUserAuth("/speedlimits", """{"value":60, "ids":[200114]}""".getBytes, username = "test") {
      status should equal(401)
    }
  }

  test("creating speed limit requires an operator role") {
    postJsonWithUserAuth("/speedlimits", """{"mmlId":362964704, "startMeasure":0.0, "endMeasure":50.0, "value":40}""".getBytes, username = "test") {
      status should equal(401)
    }
  }

  case class RoadLinkHelper(mmlId: Long, points: Seq[Point],
                            administrativeClass: String, functionalClass: Int, trafficDirection: String,
                            modifiedAt: Option[String], modifiedBy: Option[String], linkType: Int)

  test("split speed limits requires an operator role") {
    postJsonWithUserAuth("/speedlimits/200114/split", """{"existingValue":30, "createdValue":40 , "splitMeasure":5 , "value":120}""".getBytes, username = "test") {
      status should equal(401)
    }
  }

  test("get numerical limits with bounding box", Tag("db")) {
    getWithUserAuth("/numericallimits?typeId=30&bbox=374037,6677013,374540,6677675") {
      status should equal(200)
      val parsedBody = parse(body).extract[List[LinearAssetLink]]
      parsedBody.size should be(2)
    }
  }

  test("get numerical limits with bounding box should return bad request if typeId missing", Tag("db")) {
    getWithUserAuth("/numericallimits?bbox=374037,6677013,374540,6677675") {
      status should equal(400)
    }
  }

  test("updating numerical limits should require an operator role") {
    putJsonWithUserAuth("/numericallimits/11112", """{"value":6000}""".getBytes, username = "test") {
      status should equal(401)
    }
  }

  private[this] def propertiesToJson(prop: SimpleProperty): String = {
    val json = write(Seq(prop))
    s"""{"properties":$json}"""
  }
}

package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset._
import org.json4s.{DefaultFormats, Formats}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite, Tag}
import org.scalatra.test.scalatest.ScalatraSuite
import org.apache.commons.codec.binary.Base64
import org.joda.time.DateTime
import org.json4s.jackson.JsonMethods._
import org.slf4j.LoggerFactory


class IntegrationApiSpec extends FunSuite with ScalatraSuite with BeforeAndAfter{
  protected implicit val jsonFormats: Formats = DefaultFormats
  def stopWithLinkId(linkId: Long): PersistedMassTransitStop = {
    PersistedMassTransitStop(1L, 2L, linkId, Seq(2, 3), 235, 1.0, 1.0, 1, None, None, None, floating = false, 0, Modification(None, None), Modification(None, None), Seq(), NormalLinkInterface)
  }
  val mockMassTransitStopService = MockitoSugar.mock[MassTransitStopService]
  when(mockMassTransitStopService.getByMunicipality(235)).thenReturn(Seq(stopWithLinkId(123L), stopWithLinkId(321L)))
  private val integrationApi = new IntegrationApi(mockMassTransitStopService)
  addServlet(integrationApi, "/*")

  def getWithBasicUserAuth[A](uri: String, username: String, password: String)(f: => A): A = {
    val credentials = username + ":" + password
    val encodedCredentials = Base64.encodeBase64URLSafeString(credentials.getBytes)
    val authorizationToken = "Basic " + encodedCredentials + "="
    get(uri, Seq.empty, Map("Authorization" -> authorizationToken))(f)
  }

  before {
    integrationApi.clearCache()
  }
  after {
    integrationApi.clearCache()
  }

  test("Should require correct authentication", Tag("db")) {
    get("/mass_transit_stops") {
      status should equal(401)
    }
    getWithBasicUserAuth("/mass_transit_stops", "nonexisting", "incorrect") {
      status should equal(401)
    }
  }

  test("Get assets requires municipality number") {
    getWithBasicUserAuth("/mass_transit_stops", "kalpa", "kalpa") {
      status should equal(400)
    }
    getWithBasicUserAuth("/mass_transit_stops?municipality=235", "kalpa", "kalpa") {
      status should equal(200)
    }
  }

  test("Get speed_limits requires municipality number") {
    getWithBasicUserAuth("/speed_limits", "kalpa", "kalpa") {
      status should equal(400)
    }
    getWithBasicUserAuth("/speed_limits?municipality=235", "kalpa", "kalpa") {
      status should equal(200)
    }
  }

  // run manually if required, will take a long time or will not work reliably on CI
  ignore("Should use cached data on second search") {
    var result = ""
    var timing = 0L
    val startTimeMs = System.currentTimeMillis
    getWithBasicUserAuth("/road_link_properties?municipality=235", "kalpa", "kalpa") {
      status should equal(200)
      result = body
      timing =  System.currentTimeMillis - startTimeMs
    }
    // Second request should use cache and be less than half of the time spent (in dev testing, approx 2/5ths)
    getWithBasicUserAuth("/road_link_properties?municipality=235", "kalpa", "kalpa") {
      status should equal(200)
      body should equal(result)
      val elapsed = System.currentTimeMillis - startTimeMs - timing
      elapsed shouldBe < (timing / 2)
    }
  }

  test("Returns mml id of the road link that the stop refers to") {
    getWithBasicUserAuth("/mass_transit_stops?municipality=235", "kalpa", "kalpa") {
      val linkIds = (((parse(body) \ "features") \ "properties") \ "link_id").extract[Seq[Long]]
      linkIds should be(Seq(123L, 321L))
    }
  }

  test("encode speed limit") {
    integrationApi.speedLimitsToApi(Seq(SpeedLimit(1, 2, SideCode.BothDirections, TrafficDirection.BothDirections, Some(NumericValue(80)), Nil, 0, 1, None, None, None, None, 0, None, municipalityCode = None, linkSource = NormalLinkInterface))) should be(Seq(Map(
      "id" -> 1,
      "sideCode" -> 1,
      "points" -> Nil,
      "geometryWKT" -> "",
      "value" -> 80,
      "startMeasure" -> 0,
      "endMeasure" -> 1,
      "linkId" -> 2,
      "muokattu_viimeksi" -> "",
      "generatedValue" -> false,
      "linkSource" -> 1
    )))
  }

  test("generatedValue returns true if creator is real user and modifier is automatically generated") {
    integrationApi.speedLimitsToApi(Seq(SpeedLimit(1, 2, SideCode.BothDirections, TrafficDirection.BothDirections, Some(NumericValue(80)), Nil, 0, 1, Some("dr1conversion"), None, Some("K123456"), None, 0, None, municipalityCode = None, linkSource = NormalLinkInterface))) should be(Seq(Map(
      "id" -> 1,
      "sideCode" -> 1,
      "points" -> Nil,
      "geometryWKT" -> "",
      "value" -> 80,
      "startMeasure" -> 0,
      "endMeasure" -> 1,
      "linkId" -> 2,
      "muokattu_viimeksi" -> "",
      "generatedValue" -> true,
      "linkSource" -> 1
    )))
  }

  test("generatedValue returns true if creator is real user and modifier is automatically generated (contains fixed part of auto-generated value)") {
    integrationApi.speedLimitsToApi(Seq(SpeedLimit(1, 2, SideCode.BothDirections, TrafficDirection.BothDirections, Some(NumericValue(80)), Nil, 0, 1, Some("split_speedlimit_1234"), None, Some("K123456"), None, 0, None, municipalityCode = None, linkSource = NormalLinkInterface))) should be(Seq(Map(
      "id" -> 1,
      "sideCode" -> 1,
      "points" -> Nil,
      "geometryWKT" -> "",
      "value" -> 80,
      "startMeasure" -> 0,
      "endMeasure" -> 1,
      "linkId" -> 2,
      "muokattu_viimeksi" -> "",
      "generatedValue" -> true,
      "linkSource" -> 1
    )))
  }

  test("generatedValue returns true if creator is automatically generated") {
    integrationApi.speedLimitsToApi(Seq(SpeedLimit(1, 2, SideCode.BothDirections, TrafficDirection.BothDirections, Some(NumericValue(80)), Nil, 0, 1, None, None, Some("dr1conversion"), None, 0, None, municipalityCode = None, linkSource = NormalLinkInterface))) should be(Seq(Map(
      "id" -> 1,
      "sideCode" -> 1,
      "points" -> Nil,
      "geometryWKT" -> "",
      "value" -> 80,
      "startMeasure" -> 0,
      "endMeasure" -> 1,
      "linkId" -> 2,
      "muokattu_viimeksi" -> "",
      "generatedValue" -> true,
      "linkSource" -> 1
    )))
  }

  test("generatedValue returns true if creator is automatically generated (contains fixed part of auto-generated value)") {
    integrationApi.speedLimitsToApi(Seq(SpeedLimit(1, 2, SideCode.BothDirections, TrafficDirection.BothDirections, Some(NumericValue(80)), Nil, 0, 1, None, None, Some("split_speedlimit_1234"), None, 0, None, municipalityCode = None, linkSource = NormalLinkInterface))) should be(Seq(Map(
      "id" -> 1,
      "sideCode" -> 1,
      "points" -> Nil,
      "geometryWKT" -> "",
      "value" -> 80,
      "startMeasure" -> 0,
      "endMeasure" -> 1,
      "linkId" -> 2,
      "muokattu_viimeksi" -> "",
      "generatedValue" -> true,
      "linkSource" -> 1
    )))
  }

  test("generatedValue returns false if creator is automatically generated and modifier is real user") {
    integrationApi.speedLimitsToApi(Seq(SpeedLimit(1, 2, SideCode.BothDirections, TrafficDirection.BothDirections, Some(NumericValue(80)), Nil, 0, 1, Some("K123456"), None, Some("dr1conversion"), None, 0, None, municipalityCode = None, linkSource = NormalLinkInterface))) should be(Seq(Map(
      "id" -> 1,
      "sideCode" -> 1,
      "points" -> Nil,
      "geometryWKT" -> "",
      "value" -> 80,
      "startMeasure" -> 0,
      "endMeasure" -> 1,
      "linkId" -> 2,
      "muokattu_viimeksi" -> "",
      "generatedValue" -> false,
      "linkSource" -> 1
    )))
  }

  test("changeType validation returns 'add' if asset is create before the actual date") {
    val changedSpeedLimits = ChangedSpeedLimit(
      speedLimit = SpeedLimit(1, 2, SideCode.BothDirections, TrafficDirection.BothDirections, Some(NumericValue(80)), Nil, 0, 1, Some("modifiedByUser"), None, Some("createdByUser"), Some(DateTime.parse("2017-05-07T12:00Z")), 0, None, municipalityCode = None, linkSource = NormalLinkInterface),
      link = RoadLink(12345, Seq(), 10.0, Municipality, 5, TrafficDirection.UnknownDirection, SingleCarriageway, None, None))

    integrationApi.speedLimitsChangesToApi(DateTime.parse("2017-05-06T12:00Z"), Seq(changedSpeedLimits)) should be(Seq(Map(
      "id" -> 1,
      "sideCode" -> 1,
      "points" -> Nil,
      "geometryWKT" -> "",
      "value" -> 80,
      "startMeasure" -> 0.0,
      "endMeasure" -> 1.0,
      "linkId" -> 2,
      "muokattu_viimeksi" -> "07.05.2017 12:00:00",
      "generatedValue" -> false,
      "changeType" -> "Add",
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
        Seq(ManoeuvreElement(1, 1, 2, ElementTypes.FirstElement),
            ManoeuvreElement(1, 2, 3, ElementTypes.IntermediateElement),
            ManoeuvreElement(1, 3, 4, ElementTypes.IntermediateElement),
            ManoeuvreElement(1, 4, 5, ElementTypes.IntermediateElement),
            ManoeuvreElement(1, 5, 0, ElementTypes.LastElement)),
        Set.empty,Nil, "","","")

      val result = integrationApi.manouvresToApi(Seq(manoeuvre))

      result.length should be(1)
      result.head.get("elements") should be(Some(Seq(1,2,3,4,5)))
      result.head.get("sourceLinkId") should equal(Some(1))
      result.head.get("destLinkId") should equal(Some(5))
  }

  test("geometryWKTForLinearAssets provides proper geometry") {
    val (header, returntxt) =
      integrationApi.geometryWKTForLinearAssets(Seq(Point(0.0, 0.0, 0.0), Point(1.0, 0.0, 0.5), Point(4.0, 4.0, 1.5)))
    header should be ("geometryWKT")
    returntxt should be ("LINESTRING ZM (0.0 0.0 0.0 0.0, 1.0 0.0 0.5 1.0, 4.0 4.0 1.5 6.0)")

  }
}

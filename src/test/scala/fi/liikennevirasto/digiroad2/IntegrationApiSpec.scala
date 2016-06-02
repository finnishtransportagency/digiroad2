package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.{Modification, SideCode, TrafficDirection}
import fi.liikennevirasto.digiroad2.linearasset._
import org.json4s.{DefaultFormats, Formats}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite, Tag}
import org.scalatra.test.scalatest.ScalatraSuite
import org.apache.commons.codec.binary.Base64
import org.json4s.jackson.JsonMethods._


class IntegrationApiSpec extends FunSuite with ScalatraSuite with BeforeAndAfter{
  protected implicit val jsonFormats: Formats = DefaultFormats
  def stopWithLinkId(linkId: Long): PersistedMassTransitStop = {
    PersistedMassTransitStop(1L, 2L, linkId, Seq(2, 3), 235, 1.0, 1.0, 1, None, None, None, false, Modification(None, None), Modification(None, None), Seq())
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

  test("Should use cached data on second search") {
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
    integrationApi.speedLimitsToApi(Seq(SpeedLimit(1, 2, SideCode.BothDirections, TrafficDirection.BothDirections, Some(NumericValue(80)), Nil, 0, 1, None, None, None, None, 0, None))) should be(Seq(Map(
      "id" -> 1,
      "sideCode" -> 1,
      "points" -> Nil,
      "geometryWKT" -> "",
      "value" -> 80,
      "startMeasure" -> 0,
      "endMeasure" -> 1,
      "linkId" -> 2,
      "muokattu_viimeksi" -> ""
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
}

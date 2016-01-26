package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.{TrafficDirection, SideCode, Modification}
import fi.liikennevirasto.digiroad2.linearasset._
import org.json4s.{Formats, DefaultFormats}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Tag, FunSuite}
import org.scalatra.test.scalatest.ScalatraSuite
import org.apache.commons.codec.binary.Base64
import org.json4s.jackson.JsonMethods._


class IntegrationApiSpec extends FunSuite with ScalatraSuite {
  protected implicit val jsonFormats: Formats = DefaultFormats
  def stopWithMmlId(mmlId: Long): PersistedMassTransitStop = {
    PersistedMassTransitStop(1L, 2L, mmlId, Seq(2, 3), 235, 1.0, 1.0, 1, None, None, None, false, Modification(None, None), Modification(None, None), Seq())
  }
  val mockMassTransitStopService = MockitoSugar.mock[MassTransitStopService]
  when(mockMassTransitStopService.getByMunicipality(235)).thenReturn(Seq(stopWithMmlId(123L), stopWithMmlId(321L)))
  private val integrationApi = new IntegrationApi(mockMassTransitStopService)
  addServlet(integrationApi, "/*")

  def getWithBasicUserAuth[A](uri: String, username: String, password: String)(f: => A): A = {
    val credentials = username + ":" + password
    val encodedCredentials = Base64.encodeBase64URLSafeString(credentials.getBytes)
    val authorizationToken = "Basic " + encodedCredentials + "="
    get(uri, Seq.empty, Map("Authorization" -> authorizationToken))(f)
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

  test("Returns mml id of the road link that the stop refers to") {
    getWithBasicUserAuth("/mass_transit_stops?municipality=235", "kalpa", "kalpa") {
      val mmlIds = (((parse(body) \ "features") \ "properties") \ "mml_id").extract[Seq[Long]]
      mmlIds should be(Seq(123L, 321L))
    }
  }

  test("encode speed limit") {
    integrationApi.speedLimitsToApi(Seq(SpeedLimit(1, 2, SideCode.BothDirections, TrafficDirection.BothDirections, Some(NumericValue(80)), Nil, 0, 1, None, None, None, None))) should be(Seq(Map(
      "id" -> 1,
      "sideCode" -> 1,
      "points" -> Nil,
      "value" -> 80,
      "startMeasure" -> 0,
      "endMeasure" -> 1,
      "mmlId" -> 2,
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

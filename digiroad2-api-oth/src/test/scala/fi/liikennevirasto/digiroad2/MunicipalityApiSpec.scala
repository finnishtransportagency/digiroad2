package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode}
import fi.liikennevirasto.digiroad2.linearasset._
import org.apache.commons.codec.binary.Base64
import org.json4s.{DefaultFormats, Formats}
import org.scalatest.{BeforeAndAfter, FunSuite, Tag}
import org.scalatra.test.scalatest.ScalatraSuite
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar


class MunicipalityApiSpec extends FunSuite with ScalatraSuite with BeforeAndAfter with AuthenticatedApiSpec {

  protected implicit val jsonFormats: Formats = DefaultFormats

  val mockLinearAssetService = MockitoSugar.mock[LinearAssetService]
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  when(mockLinearAssetService.getAssetsByMunicipality(any[Int], any[Int])).thenReturn(Seq(PersistedLinearAsset(1, 100, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, 30, 0, None, LinkGeomSource.NormalLinkInterface)))

  private val municipalityApi = new MunicipalityApi(mockLinearAssetService, mockRoadLinkService)
  addServlet(municipalityApi, "/*")

  def getWithBasicUserAuth[A](uri: String, username: String, password: String)(f: => A): A = {
    val credentials = username + ":" + password
    val encodedCredentials = Base64.encodeBase64URLSafeString(credentials.getBytes)
    val authorizationToken = "Basic " + encodedCredentials + "="
    get(uri, Seq.empty, Map("Authorization" -> authorizationToken))(f)
  }

  def getAuthorizationHeader[A](username: String, password: String): Map[String, String]= {
    val credentials = username + ":" + password
    val encodedCredentials = Base64.encodeBase64URLSafeString(credentials.getBytes)
    val authorizationToken = "Basic " + encodedCredentials + "="
    Map("Authorization" -> authorizationToken)
  }

  test("Should require correct authentication", Tag("db")) {
    get("/235/lighting") {
      status should equal(401)
    }
    getWithBasicUserAuth("/235/lighting", "nonexisting", "incorrect") {
      status should equal(401)
    }
    getWithBasicUserAuth("/235/lighting", "kalpa", "kalpa") {
      status should equal(200)
    }
  }

  test("create new asset without link id", Tag("db")) {
    val requestPayload = """{"id": 0, "startMeasure": 0, "createdAt": 2, "geometryTimestamp": 0, "endMeasure": 200, "sideCode": 1}"""

    postJsonWithUserAuth("/235/lighting", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      status should be (422)
    }
  }

  test("create new asset without a valid SideCode", Tag("db")) {
    val requestPayload = """[{"id": 0, "linkId": 1, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 0, "endMeasure": 200, "sideCode": 4, "properties" : [{"value" : 1, "name" : "lighting"}]}]"""

    postJsonWithUserAuth("/235/lighting", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      status should be (422)
    }
  }

  test("create new asset without valid properties", Tag("db")) {
    val requestPayload = """[{"id": 0, "linkId": 1, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 0, "endMeasure": 200, "sideCode": 4, "properties" : []}]"""

    postJsonWithUserAuth("/235/lighting", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      status should be (400)
    }
  }


  test("encode lighting limit") {
    municipalityApi.linearAssetsToApi(Seq(PersistedLinearAsset(1, 2, SideCode.BothDirections.value, Some(NumericValue(1)), 0, 1, None, None, None, None, false, 100, 0, None, linkSource = NormalLinkInterface))) should be(Seq(Map(
      "id" -> 1,
      "properties" -> Seq(Map("value" -> Some(1), "name" -> "lighting")),
      "linkId" -> 2,
      "startMeasure" -> 0,
      "endMeasure" -> 1,
      "sideCode" -> 1,
      "modifiedAt" -> None,
      "createdAt" -> None,
      "geometryTimestamp" -> 0
    )))
  }
}

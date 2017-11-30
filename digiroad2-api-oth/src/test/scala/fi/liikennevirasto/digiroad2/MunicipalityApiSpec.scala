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

  val mockOnOffLinearAssetService = MockitoSugar.mock[OnOffLinearAssetService]
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  when(mockOnOffLinearAssetService.getAssetsByMunicipality(any[Int], any[Int])).thenReturn(Seq(PersistedLinearAsset(1, 100, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, 30, 0, None, LinkGeomSource.NormalLinkInterface, None, None)))
  when(mockRoadLinkService.getRoadLinkGeometry(any[Long])).thenReturn(Option(Seq(Point(0,0), Point(0,500))))
  when(mockOnOffLinearAssetService.getPersistedAssetsByIds(any[Int], any[Set[Long]])).thenReturn(Seq(PersistedLinearAsset(1, 100, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, 30, 1, None, LinkGeomSource.NormalLinkInterface, None, None)))
  when(mockOnOffLinearAssetService.getPersistedAssetsByLinkIds(any[Int], any[Seq[Long]])).thenReturn(Seq(PersistedLinearAsset(1, 100, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, 30, 1, None, LinkGeomSource.NormalLinkInterface, None, None)))
  when(mockOnOffLinearAssetService.updateWithNewMeasures(Seq(any[Long]), any[Value], any[String], any[Option[Measures]], any[Option[Long]], any[Option[Int]])).thenReturn(Seq(3.toLong))
  when(mockOnOffLinearAssetService.updateWithTimeStamp(Seq(any[Long]), any[Value], any[String], any[Option[Long]], any[Option[Int]])).thenReturn(Seq(3.toLong))
  when(mockOnOffLinearAssetService.create(Seq(any[NewLinearAsset]), any[Int], any[String], any[Long])).thenReturn(Seq(1.toLong))
  when(mockOnOffLinearAssetService.getMunicipalityById(any[Long])).thenReturn(Seq(235.toLong))

  private val municipalityApi = new MunicipalityApi(mockOnOffLinearAssetService, mockRoadLinkService)
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
    val requestPayload = """[{"id": 1, "startMeasure": 0, "createdAt": 2, "geometryTimestamp": 0, "endMeasure": 200, "sideCode": 1}]"""

    postJsonWithUserAuth("/235/lighting", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      status should be (422)
    }
  }

  test("create new asset without a valid SideCode", Tag("db")) {
    val requestPayload = """[{"id": 1, "linkId": 1, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 0, "endMeasure": 200, "sideCode": 4, "properties" : [{"value" : 1, "name" : "lighting"}]}]"""

    postJsonWithUserAuth("/235/lighting", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      status should be (422)
    }
  }

  test("create new asset", Tag("db")) {
    val requestPayload = """[{"id": 1, "linkId": 1, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 0, "endMeasure": 200, "sideCode": 1, "properties" : [{"value" : 1, "name" : "lighting"}]}]"""

    postJsonWithUserAuth("/235/lighting", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      status should be (200)
    }
  }

  test("create new asset without valid properties", Tag("db")) {
    val requestPayload = """[{"id": 1, "linkId": 1, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 0, "endMeasure": 200, "sideCode": 4, "properties" : []}]"""

    postJsonWithUserAuth("/235/lighting", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      status should be (400)
    }
  }

  test("asset is not created if the asset is longer than the road"){
    val requestPayload = """[{"id": 1, "linkId": 1, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 2, "endMeasure": 1000, "sideCode": 1, "properties" : [{"value" : 1, "name" : "lighting"}]}]"""
    postJsonWithUserAuth("/235/lighting", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      status should equal(422)
    }
  }

  test("asset is not created if one measure is less than 0"){
    val requestPayload = """[{"id": 1, "linkId": 1, "startMeasure": -1, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 2, "endMeasure": 200, "sideCode": 1, "properties" : [{"value" : 1, "name" : "lighting"}]}]"""
    postJsonWithUserAuth("/235/lighting", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      status should equal(422)
    }
  }

  test("delete asset with wrong authentication", Tag("db")){
    deleteWithUserAuth("/235/lighting/1", getAuthorizationHeader("kalpa", "")) {
      status should be (401)
    }
  }

  test("asset is updated with newer timestamp and differing measures"){
    val requestPayload = """{"id": 1, "linkId": 1, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 1502, "endMeasure": 16, "sideCode": 1, "properties" : [{"value" : 1, "name" : "lighting"}]}"""
    putJsonWithUserAuth("/235/lighting/1", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      status should equal(200)
    }
  }

  test("asset is updated with equal or newer timestamp but same measures"){
    val requestPayload = """{"id": 1, "linkId": 1, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 2, "endMeasure": 10, "sideCode": 1, "properties" : [{"value" : 1, "name" : "lighting"}]}"""
    putJsonWithUserAuth("/235/lighting/1", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      status should equal(200)
    }
  }

  test("asset is not updated if timestamp is older than the existing asset"){
    val requestPayload = """{"id": 1, "linkId": 1, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 0, "endMeasure": 15, "sideCode": 1, "properties" : [{"value" : 1, "name" : "lighting"}]}"""
    putJsonWithUserAuth("/235/lighting/1", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      status should equal(422)
    }
  }

  test("asset is not updated if the asset is longer than the road"){
    val requestPayload = """{"id": 1, "linkId": 1, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 2, "endMeasure": 1000, "sideCode": 1, "properties" : [{"value" : 1, "name" : "lighting"}]}"""
    putJsonWithUserAuth("/235/lighting/1", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      status should equal(422)
    }
  }

  test("asset is not updated if one measure is less than 0"){
    val requestPayload = """{"id": 1, "linkId": 1, "startMeasure": -1, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 2, "endMeasure": 15, "sideCode": 1, "properties" : [{"value" : 1, "name" : "lighting"}]}"""
    putJsonWithUserAuth("/235/lighting/1", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      status should equal(422)
    }
  }

  test("asset is not updated without a valid sidecode"){
    val requestPayload = """{"id": 1, "linkId": 1, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 0, "endMeasure": 15, "sideCode": 11, "properties" : [{"value" : 1, "name" : "lighting"}]}"""
    putJsonWithUserAuth("/235/lighting/1", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      status should equal(422)
    }
  }

  test("encode lighting limit") {
    municipalityApi.linearAssetsToApi(Seq(PersistedLinearAsset(1, 2, SideCode.BothDirections.value, Some(NumericValue(1)), 0, 1, None, None, None, None, false, 100, 0, None, linkSource = NormalLinkInterface, None, None)), 235) should be(Seq(Map(
      "id" -> 1,
      "properties" -> Seq(Map("value" -> Some(1), "name" -> "lighting")),
      "linkId" -> 2,
      "startMeasure" -> 0,
      "endMeasure" -> 1,
      "sideCode" -> 1,
      "modifiedAt" -> None,
      "createdAt" -> None,
      "geometryTimestamp" -> 0,
      "municipalityCode" -> 235,
      "assetType" -> 100
    )))
  }
}

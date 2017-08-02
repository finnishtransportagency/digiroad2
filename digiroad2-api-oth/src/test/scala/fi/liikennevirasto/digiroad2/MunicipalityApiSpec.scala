package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.{SideCode, TrafficDirection}
import fi.liikennevirasto.digiroad2.linearasset.{NumericValue, PersistedLinearAsset, PieceWiseLinearAsset, SpeedLimit}
import org.apache.commons.codec.binary.Base64
import org.json4s.{DefaultFormats, Formats}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite, Tag}
import org.scalatra.test.scalatest.ScalatraSuite

class MunicipalityApiSpec extends FunSuite with ScalatraSuite with BeforeAndAfter{
  protected implicit val jsonFormats: Formats = DefaultFormats

  val mockLinearAssetService = MockitoSugar.mock[LinearAssetService]

  private val municipalityApi = new MunicipalityApi(mockLinearAssetService)
  addServlet(municipalityApi, "/*")

  def getWithBasicUserAuth[A](uri: String, username: String, password: String)(f: => A): A = {
    val credentials = username + ":" + password
    val encodedCredentials = Base64.encodeBase64URLSafeString(credentials.getBytes)
    val authorizationToken = "Basic " + encodedCredentials + "="
    get(uri, Seq.empty, Map("Authorization" -> authorizationToken))(f)
  }


  test("Should require correct authentication", Tag("db")) {
    get("/235/100") {
      status should equal(401)
    }
    getWithBasicUserAuth("/235/lighting", "nonexisting", "incorrect") {
      status should equal(401)
    }
  }

  test("encode lighting limit") {
    municipalityApi.linearAssetsToApi(Seq(PersistedLinearAsset(1, 2, SideCode.BothDirections.value, Some(NumericValue(1)), 0, 1, None, None, None, None, false, 100, 0, None, linkSource = NormalLinkInterface))) should be(Seq(Map(
      "id" -> 1,
      "value" -> 1,
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

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

class MunicipalityApiSpec extends FunSuite with ScalatraSuite with BeforeAndAfter{
  protected implicit val jsonFormats: Formats = DefaultFormats

  val mockLinearAssetService = MockitoSugar.mock[LinearAssetService]
  when(mockLinearAssetService.getAssetsByMunicipality(any[Int], any[Int])).thenReturn(Seq(PersistedLinearAsset(1, 100, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, 30, 0, None, LinkGeomSource.NormalLinkInterface)))

  private val municipalityApi = new MunicipalityApi(mockLinearAssetService)
  addServlet(municipalityApi, "/*")

  def getWithBasicUserAuth[A](uri: String, username: String, password: String)(f: => A): A = {
    val credentials = username + ":" + password
    val encodedCredentials = Base64.encodeBase64URLSafeString(credentials.getBytes)
    val authorizationToken = "Basic " + encodedCredentials + "="
    get(uri, Seq.empty, Map("Authorization" -> authorizationToken))(f)
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

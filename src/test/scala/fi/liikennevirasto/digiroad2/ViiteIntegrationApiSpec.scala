package fi.liikennevirasto.digiroad2


import fi.liikennevirasto.viite.RoadAddressService
import org.json4s.{DefaultFormats, Formats}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite}
import org.scalatra.test.scalatest.ScalatraSuite
import org.apache.commons.codec.binary.Base64


class ViiteIntegrationApiSpec extends FunSuite with ScalatraSuite with BeforeAndAfter{
  protected implicit val jsonFormats: Formats = DefaultFormats

  val mockRoadAddressService = MockitoSugar.mock[RoadAddressService]
  //TODO create the mocks
  //when(mockRoadAddressService.getRoadAddressLinks()).thenReturn(Seq()))

  private val integrationApi = new ViiteIntegrationApi(mockRoadAddressService)
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

  //TODO add tests here

  test("geometryWKTForLinearAssets provides proper geometry") {
    val (header, returntxt) =
      integrationApi.geometryWKTForLinearAssets(Seq(Point(0.0, 0.0, 0.0), Point(1.0, 0.0, 0.5), Point(4.0, 4.0, 1.5)))
    header should be ("geometryWKT")
    returntxt should be ("LINESTRING ZM (0.0 0.0 0.0 0.0, 1.0 0.0 0.5 1.0, 4.0 4.0 1.5 6.0)")
  }
}

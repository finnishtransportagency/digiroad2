package fi.liikennevirasto.digiroad2
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadLinkClient}
import fi.liikennevirasto.digiroad2.dao.AwsDao
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.SpeedLimitService
import fi.liikennevirasto.digiroad2.service.pointasset.{ObstacleService, PavedRoadService}
import org.apache.commons.codec.binary.Base64
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}

class MunicipalityApiRequestSpec extends FunSuite with Matchers with BeforeAndAfter with AuthenticatedApiSpec {

  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockPavedRoadService = MockitoSugar.mock[PavedRoadService]
  val mockObstacleService = MockitoSugar.mock[ObstacleService]
  val mockSpeedLimitService = MockitoSugar.mock[SpeedLimitService]
  val mockAwsDao = MockitoSugar.mock[AwsDao]

  private val municipalityApi = new MunicipalityApi(mockVVHClient, mockRoadLinkService, mockSpeedLimitService, mockPavedRoadService, mockObstacleService, new OthSwagger) {
    override def awsDao: AwsDao = mockAwsDao
  }

  addServlet(municipalityApi, "/*")

  test("invalid json throws internal server error"){
    val payLoad = """[{"dattasettid": "apispecId", "geojson": {"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"LineString","coordinates":[[10,10],[50,50]]},"properties":{"id":"apispecId","name":"Teststreet","type":"Roadlink","sideCode":1,"speedLimit":"50","pavementClass":"1","functionalClass":"Katu"}}]}, "matchedRoadlinks": [[999999], [999999]]}]"""
    put("/" + "assetUpdateFromAWS", payLoad.getBytes, Map("Content-type" -> "application/json")) {
        status should equal(500)
    }
  }

}

package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.service.RoadLinkType.NormalRoadLinkType
import fi.liikennevirasto.digiroad2.asset.ConstructionType.InUse
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.viite.dao.CalibrationPoint
import fi.liikennevirasto.viite.model.{Anomaly, RoadAddressLink}
import fi.liikennevirasto.viite.{RoadAddressService, RoadType}
import org.apache.commons.codec.binary.Base64
import org.json4s.{DefaultFormats, Formats}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite, Tag}
import org.scalatra.test.scalatest.ScalatraSuite


class ViiteIntegrationApiSpec extends FunSuite with ScalatraSuite with BeforeAndAfter{
  protected implicit val jsonFormats: Formats = DefaultFormats

  val mockRoadAddressService = MockitoSugar.mock[RoadAddressService]
  when(mockRoadAddressService.getRoadAddressesLinkByMunicipality(235)).thenReturn(Seq())

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

  test("Should require correct authentication", Tag("db")) {
    get("/road_address") {
      status should equal(401)
    }
    getWithBasicUserAuth("/road_address", "nonexisting", "incorrect") {
      status should equal(401)
    }
  }

  test("Get road address requires municipality number") {
    getWithBasicUserAuth("/road_address", "kalpa", "kalpa") {
      status should equal(400)
    }
    getWithBasicUserAuth("/road_address?municipality=235", "kalpa", "kalpa") {
      status should equal(200)
    }
  }

  test("encode road adress") {
    val geometry = Seq(Point(0.0, 0.0, 0.0), Point(1.0, 0.0, 0.5), Point(4.0, 4.0, 1.5))
    val roadAdressLink = RoadAddressLink(63298,5171208, geometry, GeometryUtils.geometryLength(geometry),Municipality, UnknownLinkType, NormalRoadLinkType,  InUse, NormalLinkInterface, RoadType.MunicipalityStreetRoad,"Vt5", BigInt(0),None,None,Map("linkId" ->5171208, "segmentId" -> 63298 ),5,205,1,0,0,0,6,"2015-01-01","2016-01-01",0.0,0.0,SideCode.TowardsDigitizing,Some(CalibrationPoint(120,1,2)),None, Anomaly.None, 0)
    integrationApi.roadAddressLinksToApi(Seq(roadAdressLink)) should be(Seq(Map(
      "muokattu_viimeksi" -> "",
      "geometryWKT" -> "LINESTRING ZM (0.000 0.000 0.000 0.000, 1.000 0.000 0.500 1.000, 4.000 4.000 1.500 6.000)",
      "id" -> 63298,
      "link_id" -> 5171208,
      "link_source" -> 1,
      "road_number" -> 5,
      "road_part_number" -> 205,
      "track_code" -> 1,
      "side_code" -> 2,
      "start_addr_m" -> 0,
      "end_addr_m" -> 6,
      "ely_code" -> 0,
      "road_type" -> 3,
      "discontinuity" -> 0,
      "start_date" ->  "2015-01-01",
      "end_date" ->  "2016-01-01",
      "calibration_points" -> Map("start" ->  Some(Map("link_id" -> 120, "address_m_value" -> 2, "segment_m_value" -> 1.0)), "end" -> None)
    )))
  }

  test("geometryWKTForLinearAssets provides proper geometry") {
    val (header, returnTxt) =
      integrationApi.geometryWKT(Seq(Point(0.0, 0.0, 0.0), Point(1.0, 0.0, 0.5), Point(4.0, 4.0, 1.5)), 0L, 6L)
    header should be ("geometryWKT")
    returnTxt should be ("LINESTRING ZM (0.000 0.000 0.000 0.000, 1.000 0.000 0.500 1.000, 4.000 4.000 1.500 6.000)")
  }
}

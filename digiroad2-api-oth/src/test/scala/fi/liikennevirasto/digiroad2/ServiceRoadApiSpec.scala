package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.MaintenanceService
import org.scalatest.{BeforeAndAfter, FunSuite, Tag}
import org.scalatra.test.scalatest.ScalatraSuite
import org.apache.commons.codec.binary.Base64
import org.json4s.{DefaultFormats, Formats}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar

class ServiceRoadApiSpec extends FunSuite with ScalatraSuite with BeforeAndAfter{
  protected implicit val jsonFormats: Formats = DefaultFormats

  val roadLink = RoadLink(100, List(Point(0.0, 0.0), Point(1.0, 0.0)), 10.0, Municipality, 5, TrafficDirection.BothDirections, MultipleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(345), "MTKID" -> BigInt(1234), "VERTICALLEVEL" -> BigInt(1)))
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockMaintenanceService = MockitoSugar.mock[MaintenanceService]
  val serviceRoadAPI = new ServiceRoadAPI(mockMaintenanceService, mockRoadLinkService)
  when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq(roadLink))
  when(mockMaintenanceService.getActiveMaintenanceRoadByPolygon(any[Int])).thenReturn(Seq(PersistedLinearAsset(1, 100, 1, Some(MaintenanceRoad(Seq())), 0, 10, None, None, None, None, false, 30, 0, None, LinkGeomSource.NormalLinkInterface, None, None)))

   addServlet(serviceRoadAPI, "/*")

  def getWithBasicUserAuth[A](uri: String, username: String, password: String)(f: => A): A = {
    val credentials = username + ":" + password
    val encodedCredentials = Base64.encodeBase64URLSafeString(credentials.getBytes)
    val authorizationToken = "Basic " + encodedCredentials + "="
    get(uri, Seq.empty, Map("Authorization" -> authorizationToken))(f)
  }

  test("Should require correct authentication", Tag("db")) {
    get("/huoltotiet") {
      status should equal(401)
    }

    getWithBasicUserAuth("/huoltotiet", "nonexisting", "incorrect") {
      status should equal(401)
    }
  }

  test("Get all existing active assets by poligon in database") {
    getWithBasicUserAuth("/huoltotiet/12345", "kalpa", "kalpa") {
      status should equal(200)
    }
  }

}

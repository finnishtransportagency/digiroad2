package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.MaintenanceService
import org.scalatest.{BeforeAndAfter, FunSuite, Tag}
import org.scalatra.test.scalatest.ScalatraSuite
import org.apache.commons.codec.binary.Base64
import org.json4s.{DefaultFormats, Formats}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar

class ServiceRoadApiSpec extends FunSuite with ScalatraSuite with BeforeAndAfter{
  protected implicit val jsonFormats: Formats = DefaultFormats

  val linkId = "100"
  val roadLink = RoadLink(linkId, List(Point(0.0, 0.0), Point(1.0, 0.0)), 10.0, Municipality, 5, TrafficDirection.BothDirections, MultipleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(345), "SOURCEID" -> BigInt(1234), "SURFACERELATION" -> BigInt(1)))
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockMaintenanceService = MockitoSugar.mock[MaintenanceService]
  val serviceRoadAPI = new ServiceRoadAPI(mockMaintenanceService, mockRoadLinkService, new OthSwagger)
  when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(any[Set[String]], any[Boolean])).thenReturn(Seq(roadLink))
  when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(any[Set[String]], any[Boolean])).thenReturn(Seq(roadLink))
  when(mockMaintenanceService.getActiveMaintenanceRoadByPolygon(any[Int])).thenReturn(Seq(PersistedLinearAsset(1, linkId, 1, Some(DynamicValue(DynamicAssetValue(Seq()))), 0, 10, None, None, None, None, false, 30, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None)))

   addServlet(serviceRoadAPI, "/*")

  test("Get all existing active assets by poligon in database") {
    get("/huoltotiet/12345") {
      status should equal(200)
    }
  }

}

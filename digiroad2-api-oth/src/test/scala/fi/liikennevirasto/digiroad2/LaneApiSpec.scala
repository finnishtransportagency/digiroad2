package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.{ConstructionType, LinkGeomSource, Motorway, Municipality, SideCode, State, TrafficDirection}
import fi.liikennevirasto.digiroad2.asset.TrafficDirection.BothDirections
import fi.liikennevirasto.digiroad2.dao.RoadAddress
import fi.liikennevirasto.digiroad2.lane.{LaneProperty, LanePropertyValue, PieceWiseLane}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.util.Track
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.FunSuite
import org.scalatest.mockito.MockitoSugar
import org.scalatra.test.scalatest.ScalatraSuite

class LaneApiSpec extends FunSuite with ScalatraSuite {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockRoadAddressService = MockitoSugar.mock[RoadAddressService]

  private val laneApi = new LaneApi(new OthSwagger, mockRoadLinkService, mockRoadAddressService)
  addServlet(laneApi, "/*")

  val roadLink = RoadLink(
    1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235),
      "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

  val pieceWiseLane = PieceWiseLane(111, 1, 2, false, Seq(Point(0.0, 0.0), Point(1.0, 1.0)), 0, 1, Set(Point(0.0, 0.0), Point(1.0, 1.0)),
    None, None, None, None, 0L, None, State, Seq(LaneProperty("lane_code", Seq(LanePropertyValue(11)))))

  val roadAddress = RoadAddress(0, 0, 0, Track(99), 0, 0, None, None, 0, 0, 0, SideCode(1), Seq(), false, None, None, None)

  when(mockRoadLinkService.getRoadLinksFromVVH(any[Int])).thenReturn(Seq(roadLink))
  when(mockRoadAddressService.getAllByRoadNumber(any())).thenReturn(Seq(roadAddress))
  when(mockRoadAddressService.laneWithRoadAddress(any())).thenReturn(Seq(Seq(pieceWiseLane)))
  when(mockRoadAddressService.experimentalLaneWithRoadAddress(any())).thenReturn(Seq(Seq(pieceWiseLane)))

  //Creates two road links geometrically next to each other
  def createRoadLinks(): Seq[RoadLink] = {
    val roadLink1 = RoadLink(1, Seq(Point(0.0, 0.0), Point(1.0, 1.0)), 1, State, 1, BothDirections, Motorway, None, None, Map())
    val roadLink2 = RoadLink(2, Seq(Point(1.0, 1.0), Point(2.0, 2.0)), 1, State, 1, BothDirections, Motorway, None, None, Map())
    Seq(roadLink1, roadLink2)
  }

  //Creates 11 and 21 lanes on two road links with road address information
  def createLanes(): Seq[PieceWiseLane] = {
    val laneAttributes11 = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(11))))
    val laneAttributes21 = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(21))))

    val attributes1 = Map("ROAD_NUMBER" -> 1L, "ROAD_PART_NUMBER" -> 1L, "TRACK" -> 0, "START_ADDR" -> 0L, "END_ADDR" -> 100L)
    val attributes2 = Map("ROAD_NUMBER" -> 1L, "ROAD_PART_NUMBER" -> 1L, "TRACK" -> 0, "START_ADDR" -> 100L, "END_ADDR" -> 200L)

    val lane11a = PieceWiseLane(111, 1, 2, false, Seq(Point(0.0, 0.0), Point(1.0, 1.0)), 0, 1, Set(Point(0.0, 0.0), Point(1.0, 1.0)),
      None, None, None, None, 0L, None, State, laneAttributes11, attributes1)
    val lane11b = PieceWiseLane(112, 2, 2, false, Seq(Point(1.0, 1.0), Point(2.0, 2.0)), 0, 1, Set(Point(1.0, 1.0), Point(2.0, 2.0)),
      None, None, None, None, 0L, None, State, laneAttributes11, attributes2)

    val lane21a = PieceWiseLane(211, 1, 3, false, Seq(Point(1.0, 1.0), Point(2.0, 2.0)), 0, 1, Set(Point(1.0, 1.0), Point(2.0, 2.0)),
      None, None, None, None, 0L, None, State, laneAttributes21, attributes2)
    val lane21b = PieceWiseLane(212, 2, 3, false, Seq(Point(0.0, 0.0), Point(1.0, 1.0)), 0, 1, Set(Point(0.0, 0.0), Point(1.0, 1.0)),
      None, None, None, None, 0L, None, State, laneAttributes21, attributes1)

    Seq(lane11a, lane11b, lane21a, lane21b)
  }

  test("Two equal ApiLanes should be formed") {
    val roadLinks = createRoadLinks().groupBy(_.linkId).mapValues(_.head)
    val lanes = createLanes()

    val apiRoad = laneApi.lanesToApiFormat(lanes, roadLinks).head
    val apiLanes = apiRoad.roadParts.head.apiLanes

    val apiLane11 = apiLanes.find(_.laneCode == 11).get
    val apiLane21 = apiLanes.find(_.laneCode == 21).get

    apiLanes.size should equal(2)
    apiLane11.startAddressM should equal(0)
    apiLane21.startAddressM should equal(0)
    apiLane11.endAddressM should equal(200)
    apiLane21.endAddressM should equal(200)
  }

  test("Lanes in municipality API requires municipality number") {
    get("/lanes_in_municipality") {
      status should equal(400)
    }
    get("/lanes_in_municipality?municipality=abcd") {
      status should equal(400)
    }
    get("/lanes_in_municipality?municipality=235") {
      status should equal(200)
    }
  }

  test("lanes in road address range Api requires valid parameters"){
    get("/lanes_in_range"){
      status should equal(400)
    }
    get("/lanes_in_range?road_number=a&track=b&start_part=c&start_addrm=d&end_part=e&end_addrm=f"){
      status should equal(400)
    }
    get("/lanes_in_range?road_number=9&track=1&start_part=208&start_addrm=8500&end_part=208&end_addrm=9000"){
      status should equal(200)
    }
  }

}

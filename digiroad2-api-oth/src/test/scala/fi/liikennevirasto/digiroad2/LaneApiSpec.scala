package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.lane.{LaneProperty, LanePropertyValue, PieceWiseLane}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.lane.LaneService
import fi.liikennevirasto.digiroad2.service.{RoadAddressForLink, RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.util.{GeometryTransform, LinkIdGenerator, RoadAddressRange}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.FunSuite
import org.scalatest.mockito.MockitoSugar
import org.scalatra.test.scalatest.ScalatraSuite

class LaneApiSpec extends FunSuite with ScalatraSuite {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockRoadAddressService = MockitoSugar.mock[RoadAddressService]
  val mockGeometryTransform = MockitoSugar.mock[GeometryTransform]

  private val laneServiceMock = new LaneService(mockRoadLinkService, new DummyEventBus, mockRoadAddressService){
    override lazy val geometryTransform: GeometryTransform = mockGeometryTransform
  }
  private val laneApi = new LaneApi(new OthSwagger, mockRoadLinkService, mockRoadAddressService) {
    override lazy val laneService = laneServiceMock
  }
  addServlet(laneApi, "/*")

  val linkId1 = LinkIdGenerator.generateRandom()
  val linkId2 = LinkIdGenerator.generateRandom()
  val linkId3 = LinkIdGenerator.generateRandom()
  val linkId4 = LinkIdGenerator.generateRandom()

  val roadLink1 = RoadLink(
    linkId1, Seq(Point(0.0, 0.0), Point(100.0, 0.0)), 100.0, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235),
      "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

  val pieceWiseLane = PieceWiseLane(111, linkId1, 2, false, Seq(Point(0.0, 0.0), Point(1.0, 1.0)), 0, 1, Set(Point(0.0, 0.0), Point(1.0, 1.0)),
    None, None, None, None, 0L, None, State, Seq(LaneProperty("lane_code", Seq(LanePropertyValue(11))),LaneProperty("lane_type", Seq(LanePropertyValue("1")))))

  val roadAddress = RoadAddressForLink(0, 0, 0, Track(99), 0, 0, None, None, "0", 0, 0, SideCode(1), Seq(), false, None, None, None)

  when(mockRoadLinkService.getRoadLinksByMunicipalityUsingCache(any[Int])).thenReturn(Seq(roadLink1))
  when(mockRoadLinkService.getRoadLinksByLinkIds(any[Set[String]], any[Boolean])).thenReturn(Seq(roadLink1))
  when(mockGeometryTransform.getLinkIdsInRoadAddressRange(any[RoadAddressRange])).thenReturn(Set(linkId1))
  when(mockRoadAddressService.laneWithRoadAddress(any())).thenReturn(Seq(pieceWiseLane))

  // Returns four geometrically connected road links
  def getTestRoadLinks: Seq[RoadLink] = {
    val roadLink2 = RoadLink(
      linkId2, Seq(Point(0.0, 0.0), Point(100.0, 0.0)), 100.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235),
        "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val roadLink3 = RoadLink(
      linkId3, Seq(Point(100.0, 0.0), Point(200.0, 0.0)), 100.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235),
        "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val roadLink4 = RoadLink(
      linkId4, Seq(Point(200.0, 0.0), Point(300.0, 0.0)), 100.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235),
        "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

    Seq(roadLink1, roadLink2, roadLink3, roadLink4)
  }

  // Returns 11, 21 lanes covering four road links and two 12 full length additional lanes covering two of the road links
  def getTestMainLanesAndAdditionalLanesOnContinuingLinks: Seq[PieceWiseLane] = {
    val laneAttributes11 = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(11))), LaneProperty("lane_type", Seq(LanePropertyValue(1))))
    val laneAttributes21 = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(21))), LaneProperty("lane_type", Seq(LanePropertyValue(1))))
    val laneAttributes12 = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(12))), LaneProperty("lane_type", Seq(LanePropertyValue(2))))

    val attributes1 = Map("ROAD_NUMBER" -> 1L, "ROAD_PART_NUMBER" -> 1L, "TRACK" -> 0, "START_ADDR" -> 0L, "END_ADDR" -> 100L, "SIDECODE" -> 2)
    val attributes2 = Map("ROAD_NUMBER" -> 1L, "ROAD_PART_NUMBER" -> 1L, "TRACK" -> 0, "START_ADDR" -> 100L, "END_ADDR" -> 200L, "SIDECODE" -> 2)
    val attributes3 = Map("ROAD_NUMBER" -> 1L, "ROAD_PART_NUMBER" -> 1L, "TRACK" -> 0, "START_ADDR" -> 200L, "END_ADDR" -> 300L, "SIDECODE" -> 2)
    val attributes4 = Map("ROAD_NUMBER" -> 1L, "ROAD_PART_NUMBER" -> 1L, "TRACK" -> 0, "START_ADDR" -> 300L, "END_ADDR" -> 400L, "SIDECODE" -> 2)

    // 11 lanes on all four continuing links
    val lane11a = PieceWiseLane(111, linkId1, 2, false, Seq(Point(0.0, 0.0), Point(100.0, 0.0)), 0, 100, Set(Point(0.0, 0.0), Point(100.0, 0.0)),
      None, None, None, None, 0L, None, State, laneAttributes11, attributes1)
    val lane11b = PieceWiseLane(112, linkId2, 2, false, Seq(Point(100.0, 0.0), Point(200.0, 0.0)), 0, 100, Set(Point(100.0, 0.0), Point(200.0, 0.0)),
      None, None, None, None, 0L, None, State, laneAttributes11, attributes2)
    val lane11c = PieceWiseLane(113, linkId3, 2, false, Seq(Point(200.0, 0.0), Point(300.0, 0.0)), 0, 100, Set(Point(200.0, 0.0), Point(300.0, 0.0)),
      None, None, None, None, 0L, None, State, laneAttributes11, attributes3)
    val lane11d = PieceWiseLane(114, linkId4, 2, false, Seq(Point(300.0, 0.0), Point(400.0, 0.0)), 0, 100, Set(Point(300.0, 0.0), Point(400.0, 0.0)),
      None, None, None, None, 0L, None, State, laneAttributes11, attributes4)

    // 21 lanes on all four continuing links
    val lane21a = PieceWiseLane(211, linkId1, 3, false, Seq(Point(0.0, 0.0), Point(100.0, 0.0)), 0, 100, Set(Point(0.0, 0.0), Point(100.0, 0.0)),
      None, None, None, None, 0L, None, State, laneAttributes21, attributes1)
    val lane21b = PieceWiseLane(212, linkId2, 3, false, Seq(Point(100.0, 0.0), Point(200.0, 0.0)), 0, 100, Set(Point(100.0, 0.0), Point(200.0, 0.0)),
      None, None, None, None, 0L, None, State, laneAttributes21, attributes2)
    val lane21c = PieceWiseLane(213, linkId3, 3, false, Seq(Point(200.0, 0.0), Point(300.0, 0.0)), 0, 100, Set(Point(200.0, 0.0), Point(300.0, 0.0)),
      None, None, None, None, 0L, None, State, laneAttributes21, attributes3)
    val lane21d = PieceWiseLane(214, linkId4, 3, false, Seq(Point(300.0, 0.0), Point(400.0, 0.0)), 0, 100, Set(Point(300.0, 0.0), Point(400.0, 0.0)),
      None, None, None, None, 0L, None, State, laneAttributes21, attributes4)

    // 12 lanes on middle two road links
    val lane12a = PieceWiseLane(121, linkId2, 2, false, Seq(Point(100.0, 0.0), Point(200.0, 0.0)), 0, 100, Set(Point(100.0, 0.0), Point(200.0, 0.0)),
      None, None, None, None, 0L, None, State, laneAttributes12, attributes2)
    val lane12b = PieceWiseLane(122, linkId3, 2, false, Seq(Point(200.0, 0.0), Point(300.0, 0.0)), 0, 100, Set(Point(200.0, 0.0), Point(300.0, 0.0)),
      None, None, None, None, 0L, None, State, laneAttributes12, attributes3)
    Seq(lane11a, lane11b, lane11c, lane11d, lane21a, lane21b, lane21c, lane21d, lane12a, lane12b)
  }

  // Returns 11 and 21 lanes on two road links with road address information
  def create11And21LanesOnTwoContinuingLinks(): Seq[PieceWiseLane] = {
    val laneAttributes11 = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(11))), LaneProperty("lane_type", Seq(LanePropertyValue(1))))
    val laneAttributes21 = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(21))), LaneProperty("lane_type", Seq(LanePropertyValue(1))))

    val attributes1 = Map("ROAD_NUMBER" -> 1L, "ROAD_PART_NUMBER" -> 1L, "TRACK" -> 0, "START_ADDR" -> 0L, "END_ADDR" -> 100L)
    val attributes2 = Map("ROAD_NUMBER" -> 1L, "ROAD_PART_NUMBER" -> 1L, "TRACK" -> 0, "START_ADDR" -> 100L, "END_ADDR" -> 200L)

    val lane11a = PieceWiseLane(111, linkId1, 2, false, Seq(Point(0.0, 0.0), Point(1.0, 1.0)), 0, 1, Set(Point(0.0, 0.0), Point(1.0, 1.0)),
      None, None, None, None, 0L, None, State, laneAttributes11, attributes1)
    val lane11b = PieceWiseLane(112, linkId2, 2, false, Seq(Point(1.0, 1.0), Point(2.0, 2.0)), 0, 1, Set(Point(1.0, 1.0), Point(2.0, 2.0)),
      None, None, None, None, 0L, None, State, laneAttributes11, attributes2)

    val lane21a = PieceWiseLane(211, linkId1, 3, false, Seq(Point(1.0, 1.0), Point(2.0, 2.0)), 0, 1, Set(Point(1.0, 1.0), Point(2.0, 2.0)),
      None, None, None, None, 0L, None, State, laneAttributes21, attributes2)
    val lane21b = PieceWiseLane(212, linkId2, 3, false, Seq(Point(0.0, 0.0), Point(1.0, 1.0)), 0, 1, Set(Point(0.0, 0.0), Point(1.0, 1.0)),
      None, None, None, None, 0L, None, State, laneAttributes21, attributes1)

    Seq(lane11a, lane11b, lane21a, lane21b)
  }

  test("lanes should be grouped into two groups by meaningful attributes") {
    val lanes = create11And21LanesOnTwoContinuingLinks()
    val grouped = laneApi.groupLanesByMeaningfulAttributes(lanes)

    grouped.size should equal(2)
    grouped.flatten.size should equal(lanes.size)
    grouped.head.size should equal(2)
    grouped.last.size should equal(2)
  }

  test("piecewise lanes which share meaningful attributes and are geometrically connected should be homogenized") {
    val lanes = getTestMainLanesAndAdditionalLanesOnContinuingLinks
    val roadLinks = getTestRoadLinks.groupBy(_.linkId).mapValues(_.head)
    val homogenizedLanes = laneApi.homogenizeTwoDigitLanes(lanes, roadLinks)

    val homogenizedLane11 = homogenizedLanes.find(_.laneCode == 11).get
    val homogenizedLane21 = homogenizedLanes.find(_.laneCode == 21).get
    val homogenizedLane12 = homogenizedLanes.find(_.laneCode == 12).get

    homogenizedLanes.size should equal(3)
    homogenizedLane11.startAddressM should equal(0)
    homogenizedLane11.endAddressM should equal(400)

    homogenizedLane21.startAddressM should equal(0)
    homogenizedLane21.endAddressM should equal(400)

    homogenizedLane12.startAddressM should equal(100)
    homogenizedLane12.endAddressM should equal(300)
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
    when(mockGeometryTransform.getLinkIdsInRoadAddressRange(any[RoadAddressRange])).thenReturn(Set.empty[String])

    get("/lanes_in_range") {
      status should equal(400)
    }
    get("/lanes_in_range?road_number=a&track=b&start_part=c&start_addrm=d&end_part=e&end_addrm=f") {
      status should equal(400)
    }
    get("/lanes_in_range?road_number=9&track=1&start_part=208&start_addrm=8500&end_part=208&end_addrm=9000") {
      status should equal(200)
    }
    //TODO Remove after walking and cycling lanes are enabled in laneApi
    get("/lanes_in_range?road_number=70001&track=1&start_part=208&start_addrm=8500&end_part=208&end_addrm=9000") {
      status should equal(400)
    }
  }

  test("Lanes on linearly referenced point API requires correct linkID and mValue params") {
    //Missing both params
    get("/lanes_on_point") {
      status should equal(400)
    }
    //MValue param missing
    get("/lanes_on_point?linkId=linkId=abb902fe-96a2-4423-b619-2af7f06f410a:1&mValue=INVALIDMVALUE") {
      status should equal(400)
    }
    //Correct params
    get("/lanes_on_point?linkId=abb902fe-96a2-4423-b619-2af7f06f410a:1&mValue=50.567") {
      status should equal(200)
    }
  }

}

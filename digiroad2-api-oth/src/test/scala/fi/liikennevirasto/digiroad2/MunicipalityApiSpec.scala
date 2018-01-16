package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{Measures, OnOffLinearAssetService}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.ValidityPeriodDayOfWeek.Weekday
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, _}
import org.apache.commons.codec.binary.Base64
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
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
  val mocklinearAssetService = MockitoSugar.mock[LinearAssetService]
  val mockObstacleService = MockitoSugar.mock[ObstacleService]
  val mockAssetService = MockitoSugar.mock[AssetService]
  val mockSpeedLimitService = MockitoSugar.mock[SpeedLimitService]
  val mockPavingService = MockitoSugar.mock[PavingService]
  val mockRoadWidthService = MockitoSugar.mock[RoadWidthService]
  val mockManoeuvreService = MockitoSugar.mock[ManoeuvreService]

  val roadLink = RoadLink(1000L, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, Municipality, 1, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

  when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq(roadLink))

  when(mocklinearAssetService.getByMunicipalityAndRoadLinks(TotalWeightLimit.typeId, 235)).thenReturn(
    Seq((PieceWiseLinearAsset(1, 1000, SideCode.BothDirections, Some(NumericValue(1)), Seq(Point(0, 5), Point(5, 10)), false, 0, 10, Set(), None, None, None, None, TotalWeightLimit.typeId, TrafficDirection.BothDirections, 0, None, NormalLinkInterface, Municipality), roadLink)))
  when(mocklinearAssetService.getByMunicipalityAndRoadLinks(TrailerTruckWeightLimit.typeId, 235)).thenReturn(
    Seq((PieceWiseLinearAsset(1, 1000, SideCode.BothDirections, Some(NumericValue(1)), Seq(Point(0, 5), Point(5, 10)), false, 0, 10, Set(), None, None, None, None, TrailerTruckWeightLimit.typeId, TrafficDirection.BothDirections, 0, None, NormalLinkInterface, Municipality), roadLink)))
  when(mocklinearAssetService.getByMunicipalityAndRoadLinks(AxleWeightLimit.typeId, 235)).thenReturn(
    Seq((PieceWiseLinearAsset(1, 1000, SideCode.BothDirections, Some(NumericValue(1)), Seq(Point(0, 5), Point(5, 10)), false, 0, 10, Set(), None, None, None, None, AxleWeightLimit.typeId, TrafficDirection.BothDirections, 0, None, NormalLinkInterface, Municipality), roadLink)))
  when(mocklinearAssetService.getByMunicipalityAndRoadLinks(BogieWeightLimit.typeId, 235)).thenReturn(
    Seq((PieceWiseLinearAsset(1, 1000, SideCode.BothDirections, Some(NumericValue(1)), Seq(Point(0, 5), Point(5, 10)), false, 0, 10, Set(), None, None, None, None, BogieWeightLimit.typeId, TrafficDirection.BothDirections, 0, None, NormalLinkInterface, Municipality), roadLink)))
  when(mocklinearAssetService.getByMunicipalityAndRoadLinks(HeightLimit.typeId, 235)).thenReturn(
    Seq((PieceWiseLinearAsset(1, 1000, SideCode.BothDirections, Some(NumericValue(1)), Seq(Point(0, 5), Point(5, 10)), false, 0, 10, Set(), None, None, None, None, HeightLimit.typeId, TrafficDirection.BothDirections, 0, None, NormalLinkInterface, Municipality), roadLink)))
  when(mocklinearAssetService.getByMunicipalityAndRoadLinks(LengthLimit.typeId, 235)).thenReturn(
    Seq((PieceWiseLinearAsset(1, 1000, SideCode.BothDirections, Some(NumericValue(1)), Seq(Point(0, 5), Point(5, 10)), false, 0, 10, Set(), None, None, None, None, LengthLimit.typeId, TrafficDirection.BothDirections, 0, None, NormalLinkInterface, Municipality), roadLink)))
  when(mocklinearAssetService.getByMunicipalityAndRoadLinks(WidthLimit.typeId, 235)).thenReturn(
    Seq((PieceWiseLinearAsset(1, 1000, SideCode.BothDirections, Some(NumericValue(1)), Seq(Point(0, 5), Point(5, 10)), false, 0, 10, Set(), None, None, None, None, WidthLimit.typeId, TrafficDirection.BothDirections, 0, None, NormalLinkInterface, Municipality), roadLink)))
  when(mocklinearAssetService.getPersistedAssetsByIds(TotalWeightLimit.typeId, Set(1L))).thenReturn(Seq(PersistedLinearAsset(1, 1000, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, TotalWeightLimit.typeId, 1, None, LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(TrailerTruckWeightLimit.typeId, Set(1L))).thenReturn(Seq(PersistedLinearAsset(1, 1000, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, TrailerTruckWeightLimit.typeId, 1, None, LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(AxleWeightLimit.typeId, Set(1L))).thenReturn(Seq(PersistedLinearAsset(1, 1000, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, AxleWeightLimit.typeId, 1, None, LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(BogieWeightLimit.typeId, Set(1L))).thenReturn(Seq(PersistedLinearAsset(1, 1000, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, BogieWeightLimit.typeId, 1, None, LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(HeightLimit.typeId, Set(1L))).thenReturn(Seq(PersistedLinearAsset(1, 1000, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, HeightLimit.typeId, 1, None, LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(LengthLimit.typeId, Set(1L))).thenReturn(Seq(PersistedLinearAsset(1, 1000, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, LengthLimit.typeId, 1, None, LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(WidthLimit.typeId, Set(1L))).thenReturn(Seq(PersistedLinearAsset(1, 1000, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, WidthLimit.typeId, 1, None, LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(TotalWeightLimit.typeId, Set(3L))).thenReturn(Seq(PersistedLinearAsset(3, 1000, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, TotalWeightLimit.typeId, 2, None, LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(TrailerTruckWeightLimit.typeId, Set(3L))).thenReturn(Seq(PersistedLinearAsset(3, 1000, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, TrailerTruckWeightLimit.typeId, 2, None, LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(AxleWeightLimit.typeId, Set(3L))).thenReturn(Seq(PersistedLinearAsset(3, 1000, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, AxleWeightLimit.typeId, 2, None, LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(BogieWeightLimit.typeId, Set(3L))).thenReturn(Seq(PersistedLinearAsset(3, 1000, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, BogieWeightLimit.typeId, 2, None, LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(HeightLimit.typeId, Set(3L))).thenReturn(Seq(PersistedLinearAsset(3, 1000, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, HeightLimit.typeId, 2, None, LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(LengthLimit.typeId, Set(3L))).thenReturn(Seq(PersistedLinearAsset(3, 1000, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, LengthLimit.typeId, 2, None, LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(WidthLimit.typeId, Set(3L))).thenReturn(Seq(PersistedLinearAsset(3, 1000, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, WidthLimit.typeId, 2, None, LinkGeomSource.NormalLinkInterface)))

  when(mocklinearAssetService.getPersistedAssetsByIds(NumberOfLanes.typeId, Set(1L))).thenReturn(Seq(PersistedLinearAsset(1, 1000, 1, Some(NumericValue(2)), 0, 10, None, None, None, None, false, NumberOfLanes.typeId, 2, None, LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(NumberOfLanes.typeId, Set(3L))).thenReturn(Seq(PersistedLinearAsset(3, 1000, 1, Some(NumericValue(2)), 0, 10, None, None, None, None, false, NumberOfLanes.typeId, 2, None, LinkGeomSource.NormalLinkInterface)))

  when(mocklinearAssetService.getByMunicipalityAndRoadLinks(NumberOfLanes.typeId, 235)).thenReturn(
    Seq((PieceWiseLinearAsset(1, 1000, SideCode.BothDirections, Some(NumericValue(2)), Seq(Point(0, 5), Point(5, 10)), false, 0, 10, Set(), None, None, None, None, NumberOfLanes.typeId, TrafficDirection.BothDirections, 0, None, NormalLinkInterface, Municipality), roadLink)))

  when(mocklinearAssetService.create(Seq(any[NewLinearAsset]), any[Int], any[String], any[Long])).thenReturn(Seq(1L))
  when(mocklinearAssetService.updateWithNewMeasures(Seq(any[Long]), any[Value], any[String], any[Option[Measures]], any[Option[Long]], any[Option[Int]])).thenReturn(Seq(3L))

  when(mockSpeedLimitService.getByMunicpalityAndRoadLinks(235)).thenReturn(Seq((SpeedLimit(1, 1000, SideCode.BothDirections, TrafficDirection.BothDirections, Some(NumericValue(50)), Seq(Point(0, 5), Point(0, 10)), 0, 10, None, None, None, None, 0, None, false, LinkGeomSource.NormalLinkInterface, Map()), roadLink)))
  when(mockSpeedLimitService.create(Seq(any[NewLinearAsset]), any[Int], any[String], any[Long], any[Int => Unit].apply)).thenReturn(Seq(1L))
  when(mockSpeedLimitService.update(any[Long], Seq(any[NewLinearAsset]), any[String])).thenReturn(Seq(3L))
  when(mockSpeedLimitService.getSpeedLimitAssetsByIds(Set(1))).thenReturn(Seq(SpeedLimit(1, 1000, SideCode.BothDirections, TrafficDirection.BothDirections, Some(NumericValue(50)), Seq(Point(0, 5), Point(0, 10)), 0, 10, None, None, None, None, 1, None, false, LinkGeomSource.NormalLinkInterface, Map())))
  when(mockSpeedLimitService.getSpeedLimitAssetsByIds(Set(3))).thenReturn(Seq(SpeedLimit(3, 1000, SideCode.BothDirections, TrafficDirection.BothDirections, Some(NumericValue(60)), Seq(Point(0, 5), Point(0, 10)), 0, 10, None, None, None, None, 2, None, false, LinkGeomSource.NormalLinkInterface, Map())))

  when(mockOnOffLinearAssetService.getByMunicipalityAndRoadLinks(any[Int], any[Int])).thenReturn(
    Seq((PieceWiseLinearAsset(1, 1000, SideCode.BothDirections, Some(NumericValue(1)), Seq(Point(0, 5), Point(5, 10)), false, 0, 10, Set(), None, None, None, None, TotalWeightLimit.typeId, TrafficDirection.BothDirections, 0, None, NormalLinkInterface, Municipality), roadLink)))
  when(mockOnOffLinearAssetService.getPersistedAssetsByIds(LitRoad.typeId, Set(3L))).thenReturn(Seq(PersistedLinearAsset(3, 1000, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, LitRoad.typeId, 2, None, LinkGeomSource.NormalLinkInterface)))
  when(mockRoadLinkService.getRoadLinkGeometry(any[Long])).thenReturn(Option(Seq(Point(0, 0), Point(0, 500))))
  when(mockOnOffLinearAssetService.getPersistedAssetsByIds(any[Int], any[Set[Long]])).thenReturn(Seq(PersistedLinearAsset(1, 1000, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, 30, 1, None, LinkGeomSource.NormalLinkInterface)))
  when(mockOnOffLinearAssetService.getPersistedAssetsByLinkIds(any[Int], any[Seq[Long]])).thenReturn(Seq(PersistedLinearAsset(1, 1000, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, 30, 1, None, LinkGeomSource.NormalLinkInterface)))
  when(mockOnOffLinearAssetService.updateWithNewMeasures(Seq(any[Long]), any[Value], any[String], any[Option[Measures]], any[Option[Long]], any[Option[Int]])).thenReturn(Seq(3.toLong))
  when(mockOnOffLinearAssetService.updateWithTimeStamp(Seq(any[Long]), any[Value], any[String], any[Option[Long]], any[Option[Int]])).thenReturn(Seq(3.toLong))
  when(mockOnOffLinearAssetService.create(Seq(any[NewLinearAsset]), any[Int], any[String], any[Long])).thenReturn(Seq(1.toLong))
  when(mockOnOffLinearAssetService.getMunicipalityById(any[Int])).thenReturn(Seq(235))
  when(mockAssetService.getMunicipalityById(any[Long])).thenReturn(Seq(235.toLong))

  val manoeuvreElement = Seq(ManoeuvreElement(10L, 1000L, 1001L, ElementTypes.FirstElement),
    ManoeuvreElement(10L, 1001L, 1002L, ElementTypes.IntermediateElement),
    ManoeuvreElement(10L, 1002L, 1003L, ElementTypes.IntermediateElement),
    ManoeuvreElement(10L, 1003L, 0, ElementTypes.LastElement))

  val newRoadLinks = Seq(RoadLink(1000L, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
    RoadLink(1001L, List(Point(0.0, 0.0), Point(15.0, 0.0)), 15.0, Municipality, 1, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
    RoadLink(1002L, List(Point(0.0, 0.0), Point(12.0, 0.0)), 12.0, Municipality, 1, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
    RoadLink(1003L, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))))

  when(mockRoadLinkService.getRoadsLinksFromVVH(any[Set[Long]], any[Boolean])).thenReturn(newRoadLinks)

  when(mockManoeuvreService.getByMunicipalityAndRoadLinks(235)).thenReturn(Seq((Manoeuvre(1, manoeuvreElement, Set.empty, Nil, None, None, "", DateTime.now(), ""), newRoadLinks)))
  when(mockManoeuvreService.createManoeuvre(any[String], any[NewManoeuvre])).thenReturn(10)
  when(mockManoeuvreService.find(any[Long])).thenReturn(Some(Manoeuvre(1, manoeuvreElement, Set.empty, Nil, None, None, "", DateTime.now(), "")))

  private val municipalityApi = new MunicipalityApi(mockOnOffLinearAssetService, mockRoadLinkService, mocklinearAssetService, mockSpeedLimitService, mockPavingService, mockRoadWidthService, mockManoeuvreService, mockAssetService)
  addServlet(municipalityApi, "/*")

  def getWithBasicUserAuth[A](uri: String, username: String, password: String)(f: => A): A = {
    val credentials = username + ":" + password
    val encodedCredentials = Base64.encodeBase64URLSafeString(credentials.getBytes)
    val authorizationToken = "Basic " + encodedCredentials + "="
    get(uri, Seq.empty, Map("Authorization" -> authorizationToken))(f)
  }

  def getAuthorizationHeader[A](username: String, password: String): Map[String, String] = {
    val credentials = username + ":" + password
    val encodedCredentials = Base64.encodeBase64URLSafeString(credentials.getBytes)
    val authorizationToken = "Basic " + encodedCredentials + "="
    Map("Authorization" -> authorizationToken)
  }

  val assetInfo =
    Map(
      "lighting" -> """ "properties": [{"value": 1, "name": "hasLighting"}]""",
      "speed_limit" -> """ "properties": [{"value": 60, "name": "value"}]""",
      "total_weight_limit" -> """ "properties": [{"value": 1000, "name": "value"}]""",
      "trailer_truck_weight_limit" -> """ "properties": [{"value": 1000, "name": "value"}]""",
      "axle_weight_limit" -> """ "properties": [{"value": 1000, "name": "value"}]""",
      "bogie_weight_limit" -> """ "properties": [{"value": 1000, "name": "value"}]""",
      "height_limit" -> """ "properties": [{"value": 1000, "name": "value"}]""",
      "length_limit" -> """ "properties": [{"value": 1000, "name": "value"}]""",
      "width_limit" -> """ "properties": [{"value": 1000, "name": "value"}]""",
      "number_of_lanes" -> """ "properties": [{"value": 2, "name": "value"}]"""
    )

  def testUserAuth(assetURLName: String) = {
    get("/" + assetURLName + "?municipalityCode=235") {
      withClue("assetName " + assetURLName) {
        status should equal(401)
      }
    }
    getWithBasicUserAuth("/" + assetURLName + "?municipalityCode=235", "nonexisting", "incorrect") {
      withClue("assetName " + assetURLName) {
        status should equal(401)
      }
    }
    getWithBasicUserAuth("/" + assetURLName + "?municipalityCode=235", "kalpa", "kalpa") {
      withClue("assetName " + assetURLName) {
        status should equal(200)
      }
    }
  }

  def createLinearAsset(assetInfo: (String, String)) = {
    val (assetURLName, prop) = assetInfo
    val requestPayload = """[{"linkId": 1000, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "endMeasure": 200, "sideCode": 1, """ + prop + """}]"""

    postJsonWithUserAuth("/" + assetURLName, requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      withClue("assetName " + assetURLName) {
        status should be(200)
      }
    }
  }

  def createWithoutLinkId(assetURLName: String) = {
    val requestPayload = """[{"startMeasure": 0, "createdAt": 2, "endMeasure": 200, "sideCode": 1}]"""

    postJsonWithUserAuth("/" + assetURLName, requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      withClue("assetName " + assetURLName) {
        status should be(422)
      }
    }
  }

  def createWithoutValidProperties(assetURLName: String) = {
    val requestPayload =
      """[{"linkId": 1000, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "endMeasure": 200, "sideCode": 4, "properties" : []}]"""

    postJsonWithUserAuth("/" + assetURLName, requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      withClue("assetName " + assetURLName) {
        status should be(400)
      }
    }
  }

  def createWithoutValidSideCode(assetInfo: (String, String)) = {
    val (assetURLName, prop) = assetInfo
    val requestPayload = """[{"linkId": 1000, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "endMeasure": 1000, "sideCode": 1, """ + prop + """}]"""

    postJsonWithUserAuth("/" + assetURLName, requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      withClue("assetName " + assetURLName) {
        status should be(422)
      }
    }
  }

  def assetNotCreatedIfAssetLongerThanRoad(assetInfo: (String, String)) = {
    val (assetURLName, prop) = assetInfo
    val requestPayload = """[{"id": 1, "linkId": 1000, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "endMeasure": 1000, "sideCode": 1, """ + prop + """}]"""
    postJsonWithUserAuth("/" + assetURLName, requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      withClue("assetName " + assetURLName) {
        status should equal(422)
      }
    }
  }

  def assetNotCeatedIfOneMeasureLessZero(assetInfo: (String, String)) = {
    val (assetURLName, prop) = assetInfo
    val requestPayload = """[{"linkId": 1000, "startMeasure": -1, "createdAt": "01.08.2017 14:33:47", "endMeasure": 200, "sideCode": 1, """ + prop + """}]"""
    postJsonWithUserAuth("/" + assetURLName, requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      withClue("assetName " + assetURLName) {
        status should equal(422)
      }
    }
  }

  def assetNotCreatedWithStateAdministrativeClass(assetInfo: (String, String)) = {
    val newRoadLinks = Seq(RoadLink(5000L, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, State, 1, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))))
    when(mockRoadLinkService.getRoadsLinksFromVVH(Set(5000), false)).thenReturn(newRoadLinks)

    val (assetURLName, prop) = assetInfo
    val requestPayload = """[{"linkId": 5000, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "endMeasure": 200, "sideCode": 1, """ + prop + """}]"""
    postJsonWithUserAuth("/" + assetURLName, requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      withClue("assetName " + assetURLName) {
        status should equal(422)
      }
    }
  }

  def deleteAssetWithWrongAuthentication(assetURLName: String) = {
    deleteWithUserAuth("/" + assetURLName + "/1", getAuthorizationHeader("kalpa", "")) {
      withClue("assetName " + assetURLName) {
        status should be(401)
      }
    }
  }

  def updatedWithNewerTimestampAndDifferingMeasures(assetInfo: (String, String)) = {
    val (assetURLName, prop) = assetInfo
    val requestPayload = """{"id": 1, "linkId": 1000, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 1502, "endMeasure": 16, "sideCode": 1, """ + prop + """}"""
    putJsonWithUserAuth("/" + assetURLName + "/1", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      withClue("assetName " + assetURLName) {
        status should equal(200)
      }
    }
  }

  def updatedWithEqualOrNewerTimestampButSameMeasures(assetInfo: (String, String)) = {
    val (assetURLName, prop) = assetInfo
    val requestPayload = """{"id": 1, "linkId": 1000, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 2, "endMeasure": 10, "sideCode": 1, """ + prop + """}"""
    putJsonWithUserAuth("/" + assetURLName + "/1", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      withClue("assetName " + assetURLName) {
        status should equal(200)
      }
    }
  }

  def notUpdatedIfTimestampIsOlderThanExistingAsset(assetInfo: (String, String)) = {
    val (assetURLName, prop) = assetInfo
    val requestPayload = """{"id": 1, "linkId": 1000, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 0, "endMeasure": 15, "sideCode": 1, """ + prop + """}"""
    putJsonWithUserAuth("/" + assetURLName + "/1", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      withClue("assetName " + assetURLName) {
        status should equal(422)
      }
    }
  }

  def notUpdatedIfAssetLongerThanRoad(assetInfo: (String, String)) = {
    val (assetURLName, prop) = assetInfo
    val requestPayload = """{"id": 1, "linkId": 1000, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 2, "endMeasure": 1000, "sideCode": 1, """ + prop + """}"""
    putJsonWithUserAuth("/" + assetURLName + "/1", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      withClue("assetName " + assetURLName) {
        status should equal(422)
      }
    }
  }

  def notUpdatedIfOneMeasureLessThanZero(assetInfo: (String, String)) = {
    val (assetURLName, prop) = assetInfo
    val requestPayload = """{"id": 1, "linkId": 1000, "startMeasure": -1, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 2, "endMeasure": 15, "sideCode": 1, """ + prop + """}"""
    putJsonWithUserAuth("/" + assetURLName + "/1", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      withClue("assetName " + assetURLName) {
        status should equal(422)
      }
    }
  }

  def notUpdatedWithoutValidSidecode(assetInfo: (String, String)) = {
    val (assetURLName, prop) = assetInfo
    val requestPayload = """{"id": 1, "linkId": 1000, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 0, "endMeasure": 15, "sideCode": 11, """ + prop + """}"""
    putJsonWithUserAuth("/" + assetURLName + "/1", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      withClue("assetName " + assetURLName) {
        status should equal(422)
      }
    }
  }

  def assetNotUpdatedWithStateAdministrativeClass(assetInfo: (String, String)) = {
    val newRoadLinks = Seq(RoadLink(5000L, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, State, 1, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))))
    when(mockRoadLinkService.getRoadsLinksFromVVH(Set(5000), false)).thenReturn(newRoadLinks)

    val (assetURLName, prop) = assetInfo
    val requestPayload = """{"id": 1, "linkId": 5000, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 1502, "endMeasure": 16, "sideCode": 1, """ + prop + """}"""
    putJsonWithUserAuth("/" + assetURLName + "/1", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      withClue("assetName " + assetURLName) {
        status should equal(422)
      }
    }
  }

  test("Should require correct authentication for linear", Tag("db")) {
    assetInfo.keySet.foreach(testUserAuth)
  }

  test("create new linear asset", Tag("db")) {
    assetInfo.foreach(createLinearAsset)
  }

  test("create new asset without link id", Tag("db")) {
    assetInfo.keySet.foreach(createWithoutLinkId)
  }

  test("create new asset without a valid SideCode", Tag("db")) {
    assetInfo.foreach(createWithoutValidSideCode)
  }

  test("create new asset without valid properties", Tag("db")) {
    assetInfo.keySet.foreach(createWithoutValidProperties)
  }

  test("asset is not created if the asset is longer than the road"){
    assetInfo.foreach(assetNotCreatedIfAssetLongerThanRoad)
  }

  test("asset is not created if one measure is less than 0"){
    assetInfo.foreach(assetNotCeatedIfOneMeasureLessZero)
  }

  test("asset is not created if linkId has AdministrativeClass State"){
    assetInfo.foreach(assetNotCreatedWithStateAdministrativeClass)
  }

  test("delete asset with wrong authentication", Tag("db")){
    assetInfo.keySet.foreach(deleteAssetWithWrongAuthentication)
  }

  test("asset is updated with newer timestamp and differing measures"){
    assetInfo.foreach(updatedWithNewerTimestampAndDifferingMeasures)
  }

  test("asset is updated with equal or newer timestamp but same measures"){
    assetInfo.foreach(updatedWithEqualOrNewerTimestampButSameMeasures)
  }

  test("asset is not updated if timestamp is older than the existing asset"){
    assetInfo.foreach(notUpdatedIfTimestampIsOlderThanExistingAsset)
  }

  test("asset is not updated if the asset is longer than the road"){
    assetInfo.foreach(notUpdatedIfAssetLongerThanRoad)
  }

  test("asset is not updated if one measure is less than 0"){
    assetInfo.foreach(notUpdatedIfOneMeasureLessThanZero)
  }

  test("asset is not updated without a valid sidecode"){
    assetInfo.foreach(notUpdatedWithoutValidSidecode)
  }

  test("asset is not upade if linkId has AdministrativeClass State"){
    assetInfo.foreach(assetNotUpdatedWithStateAdministrativeClass)
  }

  test("encode lighting limit") {
    municipalityApi.linearAssetsToApi(Seq(PersistedLinearAsset(1, 2, SideCode.BothDirections.value, Some(NumericValue(1)), 0, 1, None, None, None, None, false, 100, 0, None, linkSource = NormalLinkInterface, None, None)), 235) should be(Seq(Map(
      "id" -> 1,
      "properties" -> Seq(Map("value" -> Some(1), "name" -> "hasLighting")),
      "linkId" -> 2000,
      "startMeasure" -> 0,
      "endMeasure" -> 1,
      "sideCode" -> 1,
      "modifiedAt" -> None,
      "createdAt" -> None,
      "geometryTimestamp" -> 0,
      "municipalityCode" -> 235
    )))
  }

  test("encode 7 maximum restrictions asset") {
    val mapAsset = Seq(Map(
      "id" -> 1,
      "properties" -> Seq(Map("value" -> Some(100), "name" -> "value")),
      "linkId" -> 2000,
      "startMeasure" -> 0,
      "endMeasure" -> 1,
      "sideCode" -> 1,
      "modifiedAt" -> None,
      "createdAt" -> None,
      "geometryTimestamp" -> 0,
      "municipalityCode" -> 235
    ))

    withClue("assetName TotalWeightLimit" ) {
      municipalityApi.linearAssetsToApi(Seq((PersistedLinearAsset(1, 2000, SideCode.BothDirections.value, Some(NumericValue(100)), 0, 1, None, None, None, None, false, TotalWeightLimit.typeId , 0, None, linkSource = NormalLinkInterface), roadLink))) should be (mapAsset)}
    withClue("assetName TrailerTruckWeightLimit" ) {
      municipalityApi.linearAssetsToApi(Seq((PersistedLinearAsset(1, 2000, SideCode.BothDirections.value, Some(NumericValue(100)), 0, 1, None, None, None, None, false, TrailerTruckWeightLimit.typeId, 0, None, linkSource = NormalLinkInterface), roadLink))) should be (mapAsset)}
    withClue("assetName AxleWeightLimit" ) {
      municipalityApi.linearAssetsToApi(Seq((PersistedLinearAsset(1, 2000, SideCode.BothDirections.value, Some(NumericValue(100)), 0, 1, None, None, None, None, false, AxleWeightLimit.typeId, 0, None, linkSource = NormalLinkInterface), roadLink))) should be (mapAsset)}
    withClue("assetName BogieWeightLimit" ) {
      municipalityApi.linearAssetsToApi(Seq((PersistedLinearAsset(1, 2000, SideCode.BothDirections.value, Some(NumericValue(100)), 0, 1, None, None, None, None, false, BogieWeightLimit.typeId, 0, None, linkSource = NormalLinkInterface), roadLink))) should be (mapAsset)}
    withClue("assetName HeightLimit" ) {
      municipalityApi.linearAssetsToApi(Seq((PersistedLinearAsset(1, 2000, SideCode.BothDirections.value, Some(NumericValue(100)), 0, 1, None, None, None, None, false, HeightLimit.typeId, 0, None, linkSource = NormalLinkInterface), roadLink))) should be (mapAsset)}
    withClue("assetName LengthLimit" ) {
      municipalityApi.linearAssetsToApi(Seq((PersistedLinearAsset(1, 2000, SideCode.BothDirections.value, Some(NumericValue(100)), 0, 1, None, None, None, None, false, LengthLimit.typeId, 0, None, linkSource = NormalLinkInterface), roadLink))) should be (mapAsset)}
    withClue("assetName WidthLimit" ) {
      municipalityApi.linearAssetsToApi(Seq((PersistedLinearAsset(1, 2000, SideCode.BothDirections.value, Some(NumericValue(100)), 0, 1, None, None, None, None, false, WidthLimit.typeId, 0, None, linkSource = NormalLinkInterface), roadLink))) should be (mapAsset)}
  }

  test("encode speed Limit Asset") {
    municipalityApi.speedLimitAssetsToApi(Seq((SpeedLimit(1, 1000, SideCode.BothDirections, TrafficDirection.BothDirections, Some(NumericValue(60)), Seq(Point(0,5), Point(0,10)), 0, 10, None, None, None, None, 0, None, false, LinkGeomSource.NormalLinkInterface, Map()), roadLink))) should be
    Seq(Map(
      "id" -> 1,
      "properties" -> Seq(Map("value" -> Some(60), "name" -> "value")),
      "linkId" -> 1000,
      "startMeasure" -> 0,
      "endMeasure" -> 10,
      "sideCode" -> 1,
      "modifiedAt" -> None,
      "createdAt" -> None,
      "geometryTimestamp" -> 0,
      "municipalityCode" -> 235
    ))
  }

  test("Should require correct authentication for manoeuvre", Tag("db")) {
    get("/manoeuvre?municipalityCode=235") {
      status should equal(401)
    }
    getWithBasicUserAuth("/manoeuvre?municipalityCode=235", "nonexisting", "incorrect") {
      status should equal(401)
    }
    getWithBasicUserAuth("/manoeuvre?municipalityCode=235", "kalpa", "kalpa") {
      status should equal(200)
    }
  }

  val propManoeuvre = """ "properties":[{"value":1000, "name":"sourceLinkId"},{"value":1003,"name":"destLinkId"},{"value":[1001,1002], "name":"elements"}, {"value":[10,22], "name":"exceptions"},{"value":[{"startHour":12, "startMinute": 30, "endHour": 13, "endMinute": 35, "days": "Weekday"}, {"startHour": 10, "startMinute": 20, "endHour":14, "endMinute": 35,"days": "Weekday"}],"name":"validityPeriods"}]"""

  test("create new Manoeuvre asset", Tag("db")) {
    val requestPayload = """[{"linkId": 1000, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "endMeasure": 10, """ + propManoeuvre + """}]"""

    postJsonWithUserAuth("/manoeuvre", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      status should be(200)
    }
  }

  test("create new Manoeuvre asset without link id", Tag("db")) {
    val requestPayload = """[{ "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "endMeasure": 200, """ + propManoeuvre + """}]"""
    postJsonWithUserAuth("/manoeuvre", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      status should be(422)
    }
  }

  test("create new Manoeuvre asset without valid properties", Tag("db")) {
    val requestPayloadManoeuvre = """[{"id": 1, "linkId": 1000, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "endMeasure": 200}]"""
    postJsonWithUserAuth("/manoeuvre", requestPayloadManoeuvre.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      status should be(422)
    }
  }

  test("Manoeuvre asset is not created if the asset is longer than the road"){
    val requestPayloadManoeuvre = """[{"linkId": 1000, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "endMeasure": 200, """ + propManoeuvre + """  }]"""
    postJsonWithUserAuth("/manoeuvre", requestPayloadManoeuvre.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      status should equal(422)
    }
  }

  test("Manoeuvre asset is not created if one measure is less than 0"){
    val requestPayloadManoeuvre = """[{"id": 1, "linkId": 1000, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "endMeasure": 200, """ + propManoeuvre + """  }]"""
    postJsonWithUserAuth("/manoeuvre", requestPayloadManoeuvre.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      status should equal(422)
    }
  }

  test("Manoeuvre asset is not created if at least one linkId has administrative Class 'State'") {
    val newRoadLinks = Seq(RoadLink(1000L, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
      RoadLink(1001L, List(Point(0.0, 0.0), Point(15.0, 0.0)), 15.0, Municipality, 1, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
      RoadLink(1002L, List(Point(0.0, 0.0), Point(12.0, 0.0)), 12.0, State, 1, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
      RoadLink(1003L, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))))

    when(mockRoadLinkService.getRoadsLinksFromVVH(any[Set[Long]], any[Boolean])).thenReturn(newRoadLinks)
    val requestPayload = """[{"linkId": 1000, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "endMeasure": 10, """ + propManoeuvre + """}]"""
    postJsonWithUserAuth("/manoeuvre", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      status should equal(422)
    }
  }

  test("delete Manoeuvre asset with wrong authentication", Tag("db")){
    deleteWithUserAuth("/manoeuvre/1", getAuthorizationHeader("kalpa", "")){
      status should be(401)
    }
  }
  val propManoeuvreUpd = """ "properties":[{"value":[10,22], "name":"exceptions"},{"value":[{"startHour":12, "startMinute": 30, "endHour": 13, "endMinute": 35, "days": "Weekday"}],"name":"validityPeriods"}]"""

  test("Manoeuvre asset is updated with newer timestamp"){
    val createdAt = DateTime.now.plusDays(1).toString("dd-MM-yyyy HH:mm:ss")
    val requestPayloadManoeuvre = """{"id": 1, "linkId": 1000, "startMeasure": 0, "createdAt": """" + createdAt + """", "geometryTimestamp": """ + DateTime.now.plusDays(1).getMillis + """ , "endMeasure": 200, """ + propManoeuvreUpd + """  }"""
    putJsonWithUserAuth("/manoeuvre/1", requestPayloadManoeuvre.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      status should equal(200)
    }
  }

  test("Manoeuvre asset is not updated if timestamp is older than the existing asset"){
    val requestPayloadManoeuvre = """{"id": 1, "linkId": 1000, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47",  "geometryTimestamp": 1511264405, "endMeasure": 15, """ + propManoeuvreUpd + """}"""
    putJsonWithUserAuth("/manoeuvre/1", requestPayloadManoeuvre.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      status should equal(422)
    }
  }

  test("encode Manoeuvre asset") {
    val formatter = DateTimeFormat.forPattern("dd-MM-yyyy HH:mm:ss")
    val modifiedAt = DateTime.parse("18-11-2017 03:01:03", formatter)
    val manoeuvreAsset = Manoeuvre(1, manoeuvreElement, Set(ValidityPeriod(12, 13, Weekday , 30, 35)), Seq(10,22), Some(modifiedAt), None, "test", DateTime.now, "")

    val manoeuvreMap =  municipalityApi.manoeuvreAssetToApi(manoeuvreAsset, newRoadLinks)
    manoeuvreMap.get("id").get should be (1)
    manoeuvreMap.get("linkId").get should be (1003)
    manoeuvreMap.get("startMeasure").get should be (0)
    manoeuvreMap.get("endMeasure").get should be (10)
    manoeuvreMap.get("modifiedAt").get should be ("18.11.2017 03:01:03")
    manoeuvreMap.get("geometryTimestamp").get should be (1510966863000L)
    manoeuvreMap.get("municipalityCode").get should be (235)
    val properties = manoeuvreMap.get("properties").get.asInstanceOf[Seq[Map[String, Any]]]
    properties.find(_.get("name") == Some("sourceLinkId")).map(_.getOrElse("value", 0)).get should be (1000L)
    properties.find(_.get("name") == Some("destLinkId")).map(_.getOrElse("value", 0)).get should be (1003L)
    properties.find(_.get("name") == Some("additionalInfo")).map(_.getOrElse("value", 0)).get should be ("test")
    properties.find(_.get("name") == Some("exceptions")).map(_.getOrElse("value", Seq())).get should be (List(10,22))
    val validityPeriod = properties.find(_.get("name") == Some("validityPeriods")).map(_.getOrElse("value", Seq())).get.asInstanceOf[Set[Map[String, Any]]].head
    validityPeriod.find(_._1 == "startHour").map(_._2).get should be (12)
    validityPeriod.find(_._1 == "endHour").map(_._2).get should be (13)
    validityPeriod.find(_._1 == "days").map(_._2).get should be ("Weekday")
    validityPeriod.find(_._1 == "startMinute").map(_._2).get should be (30)
    validityPeriod.find(_._1 == "endMinute").map(_._2).get should be (35)
  }
}

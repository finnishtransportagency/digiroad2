package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset._
import org.apache.commons.codec.binary.Base64
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
  val mocklinearAssetService = MockitoSugar.mock[LinearAssetService]
  val mockObstacleService = MockitoSugar.mock[ObstacleService]
  val mockAssetService = MockitoSugar.mock[AssetService]
  val mockSpeedLimitService = MockitoSugar.mock[SpeedLimitService]
  val mockPavingService = MockitoSugar.mock[PavingService]
  val mockRoadWidthService = MockitoSugar.mock[RoadWidthService]
  val mockManoeuvreService = MockitoSugar.mock[ManoeuvreService]
  when(mocklinearAssetService.getAssetsByMunicipality(TotalWeightLimit.typeId, 235)).thenReturn(Seq(PersistedLinearAsset(1, 100, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, TotalWeightLimit.typeId, 0, None, Some(10), LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getAssetsByMunicipality(TrailerTruckWeightLimit.typeId, 235)).thenReturn(Seq(PersistedLinearAsset(1, 100, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, TrailerTruckWeightLimit.typeId, 0, None, Some(10), LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getAssetsByMunicipality(AxleWeightLimit.typeId, 235)).thenReturn(Seq(PersistedLinearAsset(1, 100, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, AxleWeightLimit.typeId, 0, None, Some(10), LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getAssetsByMunicipality(BogieWeightLimit.typeId, 235)).thenReturn(Seq(PersistedLinearAsset(1, 100, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, BogieWeightLimit.typeId, 0, None, Some(10), LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getAssetsByMunicipality(HeightLimit.typeId, 235)).thenReturn(Seq(PersistedLinearAsset(1, 100, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, HeightLimit.typeId, 0, None, Some(10), LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getAssetsByMunicipality(LengthLimit.typeId, 235)).thenReturn(Seq(PersistedLinearAsset(1, 100, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, LengthLimit.typeId, 0, None, Some(10), LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getAssetsByMunicipality(WidthLimit.typeId, 235)).thenReturn(Seq(PersistedLinearAsset(1, 100, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, WidthLimit.typeId, 0, None, Some(10), LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(TotalWeightLimit.typeId, Set(1L))).thenReturn(Seq(PersistedLinearAsset(1, 100, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, TotalWeightLimit.typeId, 1, None, Some(10), LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(TrailerTruckWeightLimit.typeId, Set(1L))).thenReturn(Seq(PersistedLinearAsset(1, 100, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, TrailerTruckWeightLimit.typeId, 1, None, Some(10), LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(AxleWeightLimit.typeId, Set(1L))).thenReturn(Seq(PersistedLinearAsset(1, 100, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, AxleWeightLimit.typeId, 1, None, Some(10), LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(BogieWeightLimit.typeId, Set(1L))).thenReturn(Seq(PersistedLinearAsset(1, 100, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, BogieWeightLimit.typeId, 1, None, Some(10), LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(HeightLimit.typeId, Set(1L))).thenReturn(Seq(PersistedLinearAsset(1, 100, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, HeightLimit.typeId, 1, None, Some(10), LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(LengthLimit.typeId, Set(1L))).thenReturn(Seq(PersistedLinearAsset(1, 100, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, LengthLimit.typeId, 1, None, Some(10), LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(WidthLimit.typeId, Set(1L))).thenReturn(Seq(PersistedLinearAsset(1, 100, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, WidthLimit.typeId, 1, None, Some(10), LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(TotalWeightLimit.typeId, Set(3L))).thenReturn(Seq(PersistedLinearAsset(3, 100, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, TotalWeightLimit.typeId, 2, None, Some(10), LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(TrailerTruckWeightLimit.typeId, Set(3L))).thenReturn(Seq(PersistedLinearAsset(3, 100, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, TrailerTruckWeightLimit.typeId, 2, None, Some(10), LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(AxleWeightLimit.typeId, Set(3L))).thenReturn(Seq(PersistedLinearAsset(3, 100, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, AxleWeightLimit.typeId, 2, None, Some(10), LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(BogieWeightLimit.typeId, Set(3L))).thenReturn(Seq(PersistedLinearAsset(3, 100, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, BogieWeightLimit.typeId, 2, None, Some(10), LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(HeightLimit.typeId, Set(3L))).thenReturn(Seq(PersistedLinearAsset(3, 100, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, HeightLimit.typeId, 2, None, Some(10), LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(LengthLimit.typeId, Set(3L))).thenReturn(Seq(PersistedLinearAsset(3, 100, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, LengthLimit.typeId, 2, None, Some(10), LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(WidthLimit.typeId, Set(3L))).thenReturn(Seq(PersistedLinearAsset(3, 100, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, WidthLimit.typeId, 2, None, Some(10), LinkGeomSource.NormalLinkInterface)))

  when(mocklinearAssetService.getAssetsByMunicipality(NumberOfLanes.typeId, 235)).thenReturn(Seq(PersistedLinearAsset(1, 100, 1, Some(NumericValue(2)), 0, 10, None, None, None, None, false, NumberOfLanes.typeId, 0, None, Some(10), LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(NumberOfLanes.typeId, Set(1L))).thenReturn(Seq(PersistedLinearAsset(1, 100, 1, Some(NumericValue(2)), 0, 10, None, None, None, None, false, NumberOfLanes.typeId, 1, None, Some(10), LinkGeomSource.NormalLinkInterface)))
  when(mocklinearAssetService.getPersistedAssetsByIds(NumberOfLanes.typeId, Set(3L))).thenReturn(Seq(PersistedLinearAsset(3, 100, 1, Some(NumericValue(2)), 0, 10, None, None, None, None, false, NumberOfLanes.typeId, 2, None, Some(10), LinkGeomSource.NormalLinkInterface)))

  when(mocklinearAssetService.create(Seq(any[NewLinearAsset]), any[Int], any[String], any[Long])).thenReturn(Seq(1L))
  when(mocklinearAssetService.updateWithNewMeasures(Seq(any[Long]), any[Value], any[String], any[Option[Measures]], any[Option[Long]], any[Option[Int]])).thenReturn(Seq(3L))

  when(mockSpeedLimitService.get(235)).thenReturn(Seq(SpeedLimit(1, 100, SideCode.BothDirections, TrafficDirection.BothDirections, Some(NumericValue(50)), Seq(Point(0,5), Point(0,10)), 0, 10, None, None, None, None, 0, None, false, municipalityCode = None, LinkGeomSource.NormalLinkInterface, Map())))
  when(mockSpeedLimitService.create(Seq(any[NewLinearAsset]), any[Int], any[String], any[Long], any[Int => Unit].apply)).thenReturn(Seq(1L))
  when(mockSpeedLimitService.update(any[Long], Seq(any[NewLinearAsset]), any[String])).thenReturn(Seq(3L))
  when(mockSpeedLimitService.getSpeedLimitAssetsByIds(Set(1))).thenReturn(Seq(SpeedLimit(1, 100, SideCode.BothDirections, TrafficDirection.BothDirections, Some(NumericValue(50)), Seq(Point(0,5), Point(0,10)), 0, 10, None, None, None, None, 1, None, false, Some(10), LinkGeomSource.NormalLinkInterface, Map())))
  when(mockSpeedLimitService.getSpeedLimitAssetsByIds(Set(3))).thenReturn(Seq(SpeedLimit(3, 100, SideCode.BothDirections, TrafficDirection.BothDirections, Some(NumericValue(60)), Seq(Point(0,5), Point(0,10)), 0, 10, None, None, None, None, 2, None, false, Some(10), LinkGeomSource.NormalLinkInterface, Map())))

  when(mockOnOffLinearAssetService.getAssetsByMunicipality(any[Int], any[Int])).thenReturn(Seq(PersistedLinearAsset(1, 100, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, 30, 0, None, Some(10), LinkGeomSource.NormalLinkInterface)))
  when(mockRoadLinkService.getRoadLinkGeometry(any[Long])).thenReturn(Option(Seq(Point(0, 0), Point(0, 500))))
  when(mockOnOffLinearAssetService.getPersistedAssetsByIds(any[Int], any[Set[Long]])).thenReturn(Seq(PersistedLinearAsset(1, 100, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, 30, 1, None, Some(10), LinkGeomSource.NormalLinkInterface)))
  when(mockOnOffLinearAssetService.getPersistedAssetsByLinkIds(any[Int], any[Seq[Long]])).thenReturn(Seq(PersistedLinearAsset(1, 100, 1, Some(NumericValue(1)), 0, 10, None, None, None, None, false, 30, 1, None, Some(10), LinkGeomSource.NormalLinkInterface)))
  when(mockOnOffLinearAssetService.updateWithNewMeasures(Seq(any[Long]), any[Value], any[String], any[Option[Measures]], any[Option[Long]], any[Option[Int]])).thenReturn(Seq(3.toLong))
  when(mockOnOffLinearAssetService.updateWithTimeStamp(Seq(any[Long]), any[Value], any[String], any[Option[Long]], any[Option[Int]])).thenReturn(Seq(3.toLong))
  when(mockOnOffLinearAssetService.create(Seq(any[NewLinearAsset]), any[Int], any[String], any[Long])).thenReturn(Seq(1.toLong))
  when(mockOnOffLinearAssetService.getMunicipalityById(any[Long])).thenReturn(Seq(235.toLong))
  when(mockAssetService.getMunicipalityById(any[Long])).thenReturn(Seq(235.toLong))

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
      "number_of_lanes" -> """ "properties": [{"value": 2, "name": "value"}]""",
      "manoeuvre" -> """ "properties": [{"value": 1100, "name": "sourceLinkId"},{"value": 1105,"name": "destLinkId"},{"value": 1105,"name": "elements"},{"value": [10, 22],"name": "exceptions"},{"value": [{"startHour": 12,"startMinute": 30,"endHour": 13,"endMinute": 35,"days": 1},{"startHour": 10,"startMinute": 20,"endHour": 14,"endMinute": 35,"days": 2}],"name": "validityPeriods"}],"endMeasure": 50}]"""
    )

  def testUserAuth(assetURLName: String) = {
    get("/235/" + assetURLName) {
      withClue("assetName " + assetURLName ) {status should equal(401)}
    }
    getWithBasicUserAuth("/235/" + assetURLName, "nonexisting", "incorrect") {
      withClue("assetName " + assetURLName ) {status should equal(401)}
    }
    getWithBasicUserAuth("/235/" + assetURLName, "kalpa", "kalpa") {
      withClue("assetName " + assetURLName )  {status should equal(200)}
    }
  }

  def createLinearAsset(assetInfo: (String, String)) = {
    val (assetURLName, prop) = assetInfo
    val requestPayload = """[{"id": 1, "linkId": 1, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 0, "endMeasure": 200, "sideCode": 1, """ + prop + """}]"""

    postJsonWithUserAuth("/235/" + assetURLName, requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      withClue("assetName " + assetURLName )  {status should be(200)}
    }
  }

  def createWithoutLinkId(assetURLName: String) = {
    val requestPayload = """[{"id": 1, "startMeasure": 0, "createdAt": 2, "geometryTimestamp": 0, "endMeasure": 200, "sideCode": 1}]"""

    postJsonWithUserAuth("/235/" + assetURLName, requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      withClue("assetName " + assetURLName )  {status should be (422)}
    }
  }

  def createWithoutValidProperties(assetURLName: String) = {
    val requestPayload =
      """[{"id": 1, "linkId": 1, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 0, "endMeasure": 200, "sideCode": 4, "properties" : []}]"""

    postJsonWithUserAuth("/235/" + assetURLName, requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      withClue("assetName " + assetURLName )  {status should be(400)}
    }
  }

  def createWithoutValidSideCode(assetInfo: (String, String)) = {
    val (assetURLName, prop) = assetInfo
    val requestPayload = """[{"id": 1, "linkId": 1, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 2, "endMeasure": 1000, "sideCode": 1, """ + prop + """}]"""

    postJsonWithUserAuth("/235/" + assetURLName, requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      withClue("assetName " + assetURLName )  {status should be (422)}
    }
  }

  def assetNotCreatedIfAssetLongerThanRoad(assetInfo: (String, String)) = {
    val (assetURLName, prop) = assetInfo
    val requestPayload = """[{"id": 1, "linkId": 1, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 2, "endMeasure": 1000, "sideCode": 1, """ + prop + """}]"""
    postJsonWithUserAuth("/235/" + assetURLName, requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      withClue("assetName " + assetURLName )  {  status should equal(422)}
    }
  }

  def assetNotCeatedIfOneMeasureLessZero(assetInfo: (String, String)) = {
    val (assetURLName, prop) = assetInfo
    val requestPayload = """[{"id": 1, "linkId": 1, "startMeasure": -1, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 2, "endMeasure": 200, "sideCode": 1, """ + prop + """}]"""
    postJsonWithUserAuth("/235/" + assetURLName, requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      withClue("assetName " + assetURLName )  {  status should equal(422)}
    }
  }

  def deleteAssetWithWrongAuthentication(assetURLName: String) = {
    deleteWithUserAuth("/235/" + assetURLName + "/1", getAuthorizationHeader("kalpa", "")) {
      withClue("assetName " + assetURLName )  {status should be (401)}
    }
  }

  def updatedWithNewerTimestampAndDifferingMeasures(assetInfo: (String, String)) = {
    val (assetURLName, prop) = assetInfo
    val requestPayload = """{"id": 1, "linkId": 1, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 1502, "endMeasure": 16, "sideCode": 1, """ + prop + """}"""
    putJsonWithUserAuth("/235/" + assetURLName +"/1", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      withClue("assetName " + assetURLName )  {status should equal(200)}
    }
  }

  def updatedWithEqualOrNewerTimestampButSameMeasures(assetInfo: (String, String)) = {
    val (assetURLName, prop) = assetInfo
    val requestPayload = """{"id": 1, "linkId": 1, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 2, "endMeasure": 10, "sideCode": 1, """ + prop + """}"""
    putJsonWithUserAuth("/235/" + assetURLName +"/1", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      withClue("assetName " + assetURLName )  {status should equal(200)}
    }
  }

  def notUpdatedIfTimestampIsOlderThanExistingAsset(assetInfo: (String, String)) = {
    val (assetURLName, prop) = assetInfo
    val requestPayload = """{"id": 1, "linkId": 1, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 0, "endMeasure": 15, "sideCode": 1, """ + prop + """}"""
    putJsonWithUserAuth("/235/" + assetURLName + "/1", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      withClue("assetName " + assetURLName )  {status should equal(422)}
    }
  }

  def notUpdatedIfAssetLongerThanRoad(assetInfo: (String, String)) = {
    val (assetURLName, prop) = assetInfo
    val requestPayload = """{"id": 1, "linkId": 1, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 2, "endMeasure": 1000, "sideCode": 1, """ + prop + """}"""
    putJsonWithUserAuth("/235/"+ assetURLName + "/1", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      withClue("assetName " + assetURLName )  {status should equal(422)}
    }
  }

  def notUpdatedIfOneMeasureLessThanZero(assetInfo: (String, String)) = {
    val (assetURLName, prop) = assetInfo
    val requestPayload = """{"id": 1, "linkId": 1, "startMeasure": -1, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 2, "endMeasure": 15, "sideCode": 1, """ + prop + """}"""
    putJsonWithUserAuth("/235/" + assetURLName + "/1", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      withClue("assetName " + assetURLName )  {status should equal(422)}
    }
  }

  def notUpdatedWithoutValidSidecode(assetInfo: (String, String)) = {
    val (assetURLName, prop) = assetInfo
    val requestPayload = """{"id": 1, "linkId": 1, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "geometryTimestamp": 0, "endMeasure": 15, "sideCode": 11, """ + prop + """}"""
    putJsonWithUserAuth("/235/" + assetURLName + "/1", requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
      withClue("assetName " + assetURLName )  {status should equal(422)}
    }
  }

//  test("Should require correct authentication for linear", Tag("db")) {
//    assetInfo.keySet.foreach(testUserAuth)
//  }
//
//  test("create new linear asset", Tag("db")) {
//    assetInfo.foreach(createLinearAsset)
//  }
//
//  test("create new asset without link id", Tag("db")) {
//    assetInfo.keySet.foreach(createWithoutLinkId)
//  }
//
//  test("create new asset without a valid SideCode", Tag("db")) {
//    assetInfo.foreach(createWithoutValidSideCode)
//  }
//
//  test("create new asset without valid properties", Tag("db")) {
//    assetInfo.keySet.foreach(createWithoutValidProperties)
//  }
//
//  test("asset is not created if the asset is longer than the road"){
//    assetInfo.foreach(assetNotCreatedIfAssetLongerThanRoad)
//  }
//
//  test("asset is not created if one measure is less than 0"){
//    assetInfo.foreach(assetNotCeatedIfOneMeasureLessZero)
//  }
//
//  test("delete asset with wrong authentication", Tag("db")){
//    assetInfo.keySet.foreach(deleteAssetWithWrongAuthentication)
//  }
//
//  test("asset is updated with newer timestamp and differing measures"){
//    assetInfo.foreach(updatedWithNewerTimestampAndDifferingMeasures)
//  }
//
//  test("asset is updated with equal or newer timestamp but same measures"){
//    assetInfo.foreach(updatedWithEqualOrNewerTimestampButSameMeasures)
//  }
//
//  test("asset is not updated if timestamp is older than the existing asset"){
//    assetInfo.foreach(notUpdatedIfTimestampIsOlderThanExistingAsset)
//  }
//
//  test("asset is not updated if the asset is longer than the road"){
//    assetInfo.foreach(notUpdatedIfAssetLongerThanRoad)
//  }
//
//  test("asset is not updated if one measure is less than 0"){
//    assetInfo.foreach(notUpdatedIfOneMeasureLessThanZero)
//  }
//
//  test("asset is not updated without a valid sidecode"){
//    assetInfo.foreach(notUpdatedWithoutValidSidecode)
//  }
//
//  test("encode lighting limit") {
//    municipalityApi.linearAssetsToApi(Seq(PersistedLinearAsset(1, 2, SideCode.BothDirections.value, Some(NumericValue(1)), 0, 1, None, None, None, None, false, 100, 0, None, linkSource = NormalLinkInterface)), 235) should be(Seq(Map(
//      "id" -> 1,
//      "properties" -> Seq(Map("value" -> Some(1), "name" -> "hasLighting")),
//      "linkId" -> 2,
//      "startMeasure" -> 0,
//      "endMeasure" -> 1,
//      "sideCode" -> 1,
//      "modifiedAt" -> None,
//      "createdAt" -> None,
//      "geometryTimestamp" -> 0,
//      "municipalityCode" -> 235
//    )))
//  }
//
//  test("encode 7 maximum restrictions asset") {
//    val mapAsset = Seq(Map(
//      "id" -> 1,
//      "properties" -> Seq(Map("value" -> Some(100), "name" -> "value")),
//      "linkId" -> 2,
//      "startMeasure" -> 0,
//      "endMeasure" -> 1,
//      "sideCode" -> 1,
//      "modifiedAt" -> None,
//      "createdAt" -> None,
//      "geometryTimestamp" -> 0,
//      "municipalityCode" -> 235
//    ))
//    withClue("assetName TotalWeightLimit" ) {
//      municipalityApi.linearAssetsToApi(Seq(PersistedLinearAsset(1, 2, SideCode.BothDirections.value, Some(NumericValue(100)), 0, 1, None, None, None, None, false, TotalWeightLimit.typeId , 0, None, linkSource = NormalLinkInterface)), 235) should be (mapAsset)}
//    withClue("assetName TrailerTruckWeightLimit" ) {
//      municipalityApi.linearAssetsToApi(Seq(PersistedLinearAsset(1, 2, SideCode.BothDirections.value, Some(NumericValue(100)), 0, 1, None, None, None, None, false, TrailerTruckWeightLimit.typeId, 0, None, linkSource = NormalLinkInterface)), 235) should be (mapAsset)}
//    withClue("assetName AxleWeightLimit" ) {
//      municipalityApi.linearAssetsToApi(Seq(PersistedLinearAsset(1, 2, SideCode.BothDirections.value, Some(NumericValue(100)), 0, 1, None, None, None, None, false, AxleWeightLimit.typeId, 0, None, linkSource = NormalLinkInterface)), 235) should be (mapAsset)}
//    withClue("assetName BogieWeightLimit" ) {
//      municipalityApi.linearAssetsToApi(Seq(PersistedLinearAsset(1, 2, SideCode.BothDirections.value, Some(NumericValue(100)), 0, 1, None, None, None, None, false, BogieWeightLimit.typeId, 0, None, linkSource = NormalLinkInterface)), 235) should be (mapAsset)}
//    withClue("assetName HeightLimit" ) {
//      municipalityApi.linearAssetsToApi(Seq(PersistedLinearAsset(1, 2, SideCode.BothDirections.value, Some(NumericValue(100)), 0, 1, None, None, None, None, false, HeightLimit.typeId, 0, None, linkSource = NormalLinkInterface)), 235) should be (mapAsset)}
//    withClue("assetName LengthLimit" ) {
//      municipalityApi.linearAssetsToApi(Seq(PersistedLinearAsset(1, 2, SideCode.BothDirections.value, Some(NumericValue(100)), 0, 1, None, None, None, None, false, LengthLimit.typeId, 0, None, linkSource = NormalLinkInterface)), 235) should be (mapAsset)}
//    withClue("assetName WidthLimit" ) {
//      municipalityApi.linearAssetsToApi(Seq(PersistedLinearAsset(1, 2, SideCode.BothDirections.value, Some(NumericValue(100)), 0, 1, None, None, None, None, false, WidthLimit.typeId, 0, None, linkSource = NormalLinkInterface)), 235) should be (mapAsset)}
//  }
//
//  test("encode speed Limit Asset") {
//    municipalityApi.speedLimitAssetsToApi(Seq(SpeedLimit(1, 100, SideCode.BothDirections, TrafficDirection.BothDirections, Some(NumericValue(60)), Seq(Point(0,5), Point(0,10)), 0, 10, None, None, None, None, 0, None, false, LinkGeomSource.NormalLinkInterface, Map())) , 235) should be
//    Seq(Map(
//      "id" -> 1,
//      "properties" -> Seq(Map("value" -> Some(60), "name" -> "value")),
//      "linkId" -> 100,
//      "startMeasure" -> 0,
//      "endMeasure" -> 10,
//      "sideCode" -> 1,
//      "modifiedAt" -> None,
//      "createdAt" -> None,
//      "geometryTimestamp" -> 0,
//      "municipalityCode" -> 235
//    ))
//  }

  test("encode speed Limit Asset1") {
//    def createLinearAsset(assetInfo: (String, String)) = {
      val (assetURLName, prop) = "manoeuvre" -> """ "properties": [{"value": 1100, "name": "sourceLinkId"},{"value": 1105,"name": "destLinkId"},{"value": 1105,"name": "elements"},{"value": [10, 22],"name": "exceptions"},{"value": [{"startHour": 12,"startMinute": 30,"endHour": 13,"endMinute": 35,"days": 1},{"startHour": 10,"startMinute": 20,"endHour": 14,"endMinute": 35,"days": 2}],"name": "validityPeriods"}],"endMeasure": 50}]"""

    val requestPayload = """[{"id": 1, "linkId": 1, "startMeasure": 0, "createdAt": "01.08.2017 14:33:47", "endMeasure": 50, """ + prop + """}]"""

      postJsonWithUserAuth("/235/" + assetURLName, requestPayload.getBytes, getAuthorizationHeader("kalpa", "kalpa")) {
        withClue("assetName " + assetURLName) {
          status should be(200)
        }
//      }
    }
  }

}

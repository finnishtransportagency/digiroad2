package fi.liikennevirasto.digiroad2.util.assetUpdater.pointasset

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.{Property, _}
import fi.liikennevirasto.digiroad2.client.{RoadLinkChange, RoadLinkChangeClient}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset._
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, FloatingReason, PersistedPointAsset, Point}
import org.mockito.Mockito.when
import org.scalatest._
import org.scalatest.mockito.MockitoSugar
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.util.TestTransactions
import slick.jdbc.StaticQuery.interpolation

class DirectionalPointAssetUpdaterSpec extends FunSuite with Matchers {

  case class testDirectionalPointAsset(id: Long, lon: Double, lat: Double, municipalityCode: Int, linkId: String,
                                     mValue: Double, floating: Boolean, timeStamp: Long, validityDirection: Int,
                                     bearing: Option[Int], linkSource: LinkGeomSource,
                                     propertyData: Seq[Property] = Seq()) extends PersistedPointAsset {
    override def getValidityDirection: Option[Int] = Some(this.validityDirection)
    override def getBearing: Option[Int] = this.bearing
  }

  val roadLinkChangeClient = new RoadLinkChangeClient
  val source: scala.io.BufferedSource = scala.io.Source.fromFile("digiroad2-oracle/src/test/resources/smallChangeSet.json")
  val changes: Seq[RoadLinkChange] = roadLinkChangeClient.convertToRoadLinkChange(source.mkString)

  val mockEventBus: DigiroadEventBus = MockitoSugar.mock[DigiroadEventBus]
  val mockRoadLinkService: RoadLinkService = MockitoSugar.mock[RoadLinkService]

  val directionalTrafficSignService: DirectionalTrafficSignService = new DirectionalTrafficSignService(mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }

  val trafficLightService: TrafficLightService = new TrafficLightService(mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }

  val trafficSignService: TrafficSignService = new TrafficSignService(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }

  val updater: DirectionalPointAssetUpdater = new DirectionalPointAssetUpdater(directionalTrafficSignService) {
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  test("Link split to multiple new links: assets are moved to correct link") {
    val oldLinkId = "99ade73f-979b-480b-976a-197ad365440a:1"
    val newLinkId1 = "1cb02550-5ce4-4c0a-8bee-c7f5e1f314d1:1"
    val newLinkId2 = "7c6fc2d3-f79f-4e03-a349-80103714a442:1"
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get

    val newLinkInfo1 = change.newLinks.find(_.linkId == newLinkId1).get
    val newLinkInfo2 = change.newLinks.find(_.linkId == newLinkId2).get
    val newLink1 = RoadLink(newLinkId1, newLinkInfo1.geometry, newLinkInfo1.linkLength, Municipality, 1, TrafficDirection.BothDirections,
      Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    val newLink2 = RoadLink(newLinkId2, newLinkInfo2.geometry, newLinkInfo2.linkLength, Municipality, 1, TrafficDirection.BothDirections,
      Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(newLinkId1, false)).thenReturn(Some(newLink1))
    when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(newLinkId2, false)).thenReturn(Some(newLink2))

    val asset1 = testDirectionalPointAsset(1, 367475.5758941994, 6673657.346462522, 49, oldLinkId,
      353.2503573773719, true, 0, SideCode.TowardsDigitizing.value, Some(341), NormalLinkInterface)
    val asset2 = testDirectionalPointAsset(2, 367537.23265942856, 6673473.43531342, 49, oldLinkId,
      159.2856222448274, true, 0, SideCode.AgainstDigitizing.value, Some(341), NormalLinkInterface)
    val corrected1 = updater.correctPersistedAsset(asset1, change)
    val corrected2 = updater.correctPersistedAsset(asset2, change)

    val distanceToOldLocation1 = Point(corrected1.lon, corrected1.lat).distance2DTo(Point(asset1.lon, asset1.lat))
    corrected1.linkId should be(newLinkId1)
    corrected1.floating should be(false)
    corrected1.validityDirection should be(Some(SideCode.TowardsDigitizing.value))
    distanceToOldLocation1 should be < updater.MaxDistanceDiffAllowed

    val distanceToOldLocation2 = Point(corrected2.lon, corrected2.lat).distance2DTo(Point(asset2.lon, asset2.lat))
    corrected2.linkId should be(newLinkId2)
    corrected2.floating should be(false)
    corrected2.validityDirection should be(Some(SideCode.AgainstDigitizing.value))
    distanceToOldLocation2 should be < updater.MaxDistanceDiffAllowed
  }

  test("Link has merged to another link: assets are relocated on new link and validity direction is fixed") {
    val oldLinkId1 = "88449ad1-ded1-4b4a-aa57-ae40571ae18b:1"
    val oldLinkId2 = "291f7e18-a48a-4afc-a84b-8485164288b2:1"
    val newLinkId = "eca24369-a77b-4e6f-875e-57dc85176003:1"
    val change1 = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId1).get
    val change2 = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId2).get

    val newLinkInfo = change1.newLinks.find(_.linkId == newLinkId).get
    val newLink = RoadLink(newLinkId, newLinkInfo.geometry, newLinkInfo.linkLength, Municipality, 1, TrafficDirection.AgainstDigitizing,
      Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(newLinkId, false)).thenReturn(Some(newLink))

    val asset1 = testDirectionalPointAsset(1, 391922.1567866767, 6672845.986092816, 91, oldLinkId1,
      13.736569359264589, true, 0, SideCode.AgainstDigitizing.value, Some(341), NormalLinkInterface)
    val corrected1 = updater.correctPersistedAsset(asset1, change1)
    corrected1.floating should be(false)
    corrected1.linkId should be(newLinkId)
    corrected1.validityDirection should be(Some(SideCode.AgainstDigitizing.value))
    val distanceToOldLocation1 = Point(corrected1.lon, corrected1.lat).distance2DTo(Point(asset1.lon, asset1.lat))
    distanceToOldLocation1 should be < updater.MaxDistanceDiffAllowed

    val asset2 = testDirectionalPointAsset(1, 391942.45320008986, 6672844.827758611, 91, oldLinkId2,
      12.508397774777997, true, 0, SideCode.TowardsDigitizing.value, Some(23), NormalLinkInterface)
    val corrected2 = updater.correctPersistedAsset(asset2, change2)
    corrected2.floating should be(false)
    corrected2.linkId should be(newLinkId)
    corrected2.validityDirection should be(Some(SideCode.AgainstDigitizing.value))
    val distanceToOldLocation2 = Point(corrected2.lon, corrected2.lat).distance2DTo(Point(asset2.lon, asset2.lat))
    distanceToOldLocation2 should be < updater.MaxDistanceDiffAllowed
  }

  test("Asset validity direction does not match new link direction: Asset is marked floating") {
    val oldLinkId = "291f7e18-a48a-4afc-a84b-8485164288b2:1"
    val newLinkId = "eca24369-a77b-4e6f-875e-57dc85176003:1"
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get

    val newLinkInfo = change.newLinks.find(_.linkId == newLinkId).get
    val newLink = RoadLink(newLinkId, newLinkInfo.geometry, newLinkInfo.linkLength, Municipality, 1, TrafficDirection.AgainstDigitizing,
      Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(newLinkId, false)).thenReturn(Some(newLink))

    val asset = testDirectionalPointAsset(1, 391936.8899081593, 6672840.334351871, 91, oldLinkId,
      4.773532861864595, false, 0, SideCode.AgainstDigitizing.value, Some(23), NormalLinkInterface)
    val corrected = updater.correctPersistedAsset(asset, change)

    corrected.floating should be(true)
    corrected.floatingReason should be(Some(FloatingReason.TrafficDirectionNotMatch))
    corrected.lon should be(asset.lon)
    corrected.lat should be(asset.lat)
    corrected.validityDirection should be(asset.getValidityDirection)
    corrected.linkId should not be newLinkId
  }

  test("Asset validity direction does match new link direction that is overridden") {
    val oldLinkId = "291f7e18-a48a-4afc-a84b-8485164288b2:1"
    val newLinkId = "eca24369-a77b-4e6f-875e-57dc85176003:1"
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get

    val newLinkInfo = change.newLinks.find(_.linkId == newLinkId).get
    val newLink = RoadLink(newLinkId, newLinkInfo.geometry, newLinkInfo.linkLength, Municipality, 1, TrafficDirection.TowardsDigitizing,
      Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(newLinkId, false)).thenReturn(Some(newLink))

    val asset = testDirectionalPointAsset(1, 391936.8899081593, 6672840.334351871, 91, oldLinkId,
      4.773532861864595, false, 0, SideCode.AgainstDigitizing.value, Some(23), NormalLinkInterface)
    val corrected = updater.correctPersistedAsset(asset, change)

    newLinkInfo.trafficDirection should not be newLink.trafficDirection
    corrected.floating should be(false)
    corrected.validityDirection should be(Some(SideCode.TowardsDigitizing.value))
    corrected.linkId should be(newLinkId)
  }

  test("New link is longer than old one: Asset is relocated on new link") {
    val oldLinkId = "d2fb5669-b512-4c41-8fc8-c40a1c62f2b8:1"
    val newLinkId = "76271938-fc08-4061-8e23-d2cfdce8f051:1"
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
    val asset = testDirectionalPointAsset(1, 379539.5523067349, 6676588.239922434, 49, oldLinkId,
      0.7993821710321284, true, 0, SideCode.TowardsDigitizing.value, Some(341), NormalLinkInterface)

    val newLinkInfo = change.newLinks.find(_.linkId == newLinkId).get
    val newLink = RoadLink(newLinkId, newLinkInfo.geometry, newLinkInfo.linkLength, Municipality, 1, TrafficDirection.BothDirections,
      Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(newLinkId, false)).thenReturn(Some(newLink))

    val corrected = updater.correctPersistedAsset(asset, change)
    corrected.floating should be(false)
    corrected.linkId should be(newLinkId)
    val distanceToOldLocation = Point(corrected.lon, corrected.lat).distance2DTo(Point(asset.lon, asset.lat))
    distanceToOldLocation should be < updater.MaxDistanceDiffAllowed
  }

  test("New link is shorted than old one: asset is relocated on shorter link") {
    val oldLinkId = "875766ca-83b1-450b-baf1-db76d59176be:1"
    val newLinkId = "6eec9a4a-bcac-4afb-afc8-f4e6d40ec571:1"
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
    val asset = testDirectionalPointAsset(1, 370243.9245965985, 6670363.935476765, 49, oldLinkId,
      35.833489781349485, true, 0, SideCode.TowardsDigitizing.value, Some(289), NormalLinkInterface)

    val newLinkInfo = change.newLinks.find(_.linkId == newLinkId).get
    val newLink = RoadLink(newLinkId, newLinkInfo.geometry, newLinkInfo.linkLength, Municipality, 1, TrafficDirection.BothDirections,
      Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(newLinkId, false)).thenReturn(Some(newLink))

    val corrected = updater.correctPersistedAsset(asset, change)
    val distanceToOldLocation = Point(corrected.lon, corrected.lat).distance2DTo(Point(asset.lon, asset.lat))
    corrected.floating should be(false)
    corrected.linkId should be(newLinkId)
    distanceToOldLocation should be < updater.MaxDistanceDiffAllowed
  }

  test("New link is shorted than old one: asset is too far from new link so it is marked as floating") {
    val oldLinkId = "875766ca-83b1-450b-baf1-db76d59176be:1"
    val newLinkId = "6eec9a4a-bcac-4afb-afc8-f4e6d40ec571:1"
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
    val asset = testDirectionalPointAsset(1, 370235.4591063613, 6670366.945428849, 49, oldLinkId,
      44.83222354244527, false, 0, SideCode.TowardsDigitizing.value, Some(289), NormalLinkInterface)

    val newLinkInfo = change.newLinks.find(_.linkId == newLinkId).get
    val newLink = RoadLink(newLinkId, newLinkInfo.geometry, newLinkInfo.linkLength, Municipality, 1, TrafficDirection.BothDirections,
      Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(newLinkId, false)).thenReturn(Some(newLink))

    val corrected = updater.correctPersistedAsset(asset, change)
    corrected.floating should be(true)
    corrected.floatingReason should be(Some(FloatingReason.DistanceToRoad))
  }

  test("Link version has changed: asset is marked to new link without other adjustments") {
    val oldLinkId = "1438d48d-dde6-43db-8aba-febf3d2220c0:1"
    val newLinkId = "1438d48d-dde6-43db-8aba-febf3d2220c0:2"
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
    val asset = testDirectionalPointAsset(1, 367830.31375169184, 6673995.872282351, 49, oldLinkId,
      123.74459961959604, true, 0, SideCode.TowardsDigitizing.value, Some(317), NormalLinkInterface)

    val newLinkInfo = change.newLinks.find(_.linkId == newLinkId).get
    val newLink = RoadLink(newLinkId, newLinkInfo.geometry, newLinkInfo.linkLength, Municipality, 1, TrafficDirection.BothDirections,
      Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(newLinkId, false)).thenReturn(Some(newLink))

    val corrected = updater.correctPersistedAsset(asset, change)
    corrected.floating should be(false)
    corrected.linkId should not be oldLinkId
    corrected.linkId should be(newLinkId)
    corrected.lon should be(asset.lon)
    corrected.lat should be(asset.lat)
    corrected.bearing should be(asset.getBearing)
    corrected.validityDirection should be(asset.getValidityDirection)
  }

  test("New link has different municipality than asset: asset is marked as floating") {
    val oldLinkId = "1438d48d-dde6-43db-8aba-febf3d2220c0:1"
    val newLinkId = "1438d48d-dde6-43db-8aba-febf3d2220c0:2"
    val assetMunicipality = 235 // New link is at municipality 49
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
    val asset = testDirectionalPointAsset(1, 367830.31375169184, 6673995.872282351, assetMunicipality, oldLinkId,
      123.74459961959604, true, 0, SideCode.TowardsDigitizing.value, Some(317), NormalLinkInterface)

    val newLinkInfo = change.newLinks.find(_.linkId == newLinkId).get
    val newLink = RoadLink(newLinkId, newLinkInfo.geometry, newLinkInfo.linkLength, Municipality, 1, TrafficDirection.BothDirections,
      Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(assetMunicipality)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(newLinkId, false)).thenReturn(Some(newLink))

    val corrected = updater.correctPersistedAsset(asset, change)
    corrected.floating should be(true)
    corrected.floatingReason should be(Some(FloatingReason.DifferentMunicipalityCode))
    corrected.linkId should be(oldLinkId)
  }

  test("Link removed without no replacement: asset is marked as floating") {
    val oldLinkId = "7766bff4-5f02-4c30-af0b-42ad3c0296aa:1"
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
    val asset = testDirectionalPointAsset(1, 378759.66525429586, 6672990.60914197, 49, oldLinkId,
      99.810467297064, true, 0, SideCode.TowardsDigitizing.value, Some(351), NormalLinkInterface)
    val corrected = updater.correctPersistedAsset(asset, change)

    corrected.floating should be(true)
    corrected.floatingReason should be(Some(FloatingReason.NoRoadLinkFound))
  }

  test("New link is too far from asset and no new location can be calculated: asset is marked as floating") {
    val oldLinkId = "88449ad1-ded1-4b4a-aa57-ae40571ae18b:1"
    val newLinkId = "eca24369-a77b-4e6f-875e-57dc85176003:1"
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
    val asset = testDirectionalPointAsset(1, 391927.91316321003, 6672830.079543546, 91, oldLinkId,
      7.114809035180554, false, 0, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

    val newLinkInfo = change.newLinks.find(_.linkId == newLinkId).get
    val newLink = RoadLink(newLinkId, newLinkInfo.geometry, newLinkInfo.linkLength, Municipality, 1, TrafficDirection.BothDirections,
      Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(newLinkId, false)).thenReturn(Some(newLink))

    val corrected = updater.correctPersistedAsset(asset, change)
    corrected.floating should be(true)
    corrected.floatingReason should be(Some(FloatingReason.DistanceToRoad))
  }

  val trafficLightPropertySet1 = Set(
    SimplePointAssetProperty("trafficLight_type", List(PropertyValue("1")), 1),
    SimplePointAssetProperty("trafficLight_relative_position", List(PropertyValue("1")), 1),
    SimplePointAssetProperty("trafficLight_structure", List(PropertyValue("99")), 1),
    SimplePointAssetProperty("trafficLight_height", List(PropertyValue("")), 1),
    SimplePointAssetProperty("trafficLight_sound_signal", List(PropertyValue("99")), 1),
    SimplePointAssetProperty("trafficLight_vehicle_detection", List(PropertyValue("99")), 1),
    SimplePointAssetProperty("trafficLight_push_button", List(PropertyValue("99")), 1),
    SimplePointAssetProperty("trafficLight_info", List(PropertyValue("")), 1),
    SimplePointAssetProperty("trafficLight_lane_type", List(PropertyValue("99")), 1),
    SimplePointAssetProperty("trafficLight_lane", List(PropertyValue("")), 1),
    SimplePointAssetProperty("location_coordinates_x", List(PropertyValue("")), 1),
    SimplePointAssetProperty("location_coordinates_y", List(PropertyValue("")), 1),
    SimplePointAssetProperty("trafficLight_municipality_id", List(PropertyValue("")), 1),
    SimplePointAssetProperty("trafficLight_state", List(PropertyValue("3")), 1),
    SimplePointAssetProperty("suggest_box", List(PropertyValue("0")), 1),
    SimplePointAssetProperty("bearing", List(PropertyValue("")), 1),
    SimplePointAssetProperty("sidecode", List(PropertyValue("2")), 1)
  )

  val trafficLightPropertySet2 = Set(
    SimplePointAssetProperty("trafficLight_type", List(PropertyValue("4.1")), 2),
    SimplePointAssetProperty("trafficLight_relative_position", List(PropertyValue("2")), 2),
    SimplePointAssetProperty("trafficLight_structure", List(PropertyValue("3")), 2),
    SimplePointAssetProperty("trafficLight_height", List(PropertyValue("")), 2),
    SimplePointAssetProperty("trafficLight_sound_signal", List(PropertyValue("1")), 2),
    SimplePointAssetProperty("trafficLight_vehicle_detection", List(PropertyValue("99")), 2),
    SimplePointAssetProperty("trafficLight_push_button", List(PropertyValue("99")), 2),
    SimplePointAssetProperty("trafficLight_info", List(PropertyValue("")), 2),
    SimplePointAssetProperty("trafficLight_lane_type", List(PropertyValue("99")), 2),
    SimplePointAssetProperty("trafficLight_lane", List(PropertyValue("")), 2),
    SimplePointAssetProperty("location_coordinates_x", List(PropertyValue("")), 2),
    SimplePointAssetProperty("location_coordinates_y", List(PropertyValue("")), 2),
    SimplePointAssetProperty("trafficLight_municipality_id", List(PropertyValue("")), 2),
    SimplePointAssetProperty("trafficLight_state", List(PropertyValue("3")), 2),
    SimplePointAssetProperty("suggest_box", List(PropertyValue("0")), 2),
    SimplePointAssetProperty("bearing", List(PropertyValue("")), 2),
    SimplePointAssetProperty("sidecode", List(PropertyValue("2")), 2)
  )

  def createTestAssets(x: Double, y: Double, roadLink: RoadLink, validityDirection: Option[Int], bearing: Option[Int], updateModifiedBy: Boolean = true) = {
    val dtsId = directionalTrafficSignService.create(IncomingDirectionalTrafficSign(x,y, roadLink.linkId, validityDirection.get, bearing, Set()), "testCreator", roadLink, false)
    val tlId = trafficLightService.create(IncomingTrafficLight(x,y, roadLink.linkId, trafficLightPropertySet1 ++ trafficLightPropertySet2, validityDirection), "testCreator", roadLink, false)
    val tsId = trafficSignService.create(IncomingTrafficSign(x,y,roadLink.linkId, Set(SimplePointAssetProperty("trafficSigns_type", List(PropertyValue("1"))),
      SimplePointAssetProperty("trafficSigns_value", List(PropertyValue("80")))), validityDirection.get, bearing),"testCreator", roadLink, false)

    if (updateModifiedBy) {
      sqlu"""UPDATE ASSET
          SET CREATED_DATE = to_timestamp('2021-05-10T10:52:28.783Z', 'YYYY-MM-DD"T"HH24:MI:SS.FF3Z'),
              MODIFIED_BY = 'testModifier', MODIFIED_DATE = to_timestamp('2022-05-10T10:52:28.783Z', 'YYYY-MM-DD"T"HH24:MI:SS.FF3Z')
          WHERE id in (${dtsId}, ${tlId}, ${tsId})
      """.execute
    } else {
      sqlu"""UPDATE ASSET
          SET CREATED_DATE = to_timestamp('2021-05-10T10:52:28.783Z', 'YYYY-MM-DD"T"HH24:MI:SS.FF3Z')
          WHERE id in (${dtsId}, ${tlId}, ${tsId})
      """.execute
    }

    Set(dtsId, tlId, tsId)
  }


  test("update due to adjustmentOperation does not change creation or modification data if it's called by PointAssetUpdater") {

    runWithRollback {
      val oldLinkId = "1438d48d-dde6-43db-8aba-febf3d2220c0:1"
      val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
      val roadLink = RoadLink(oldLinkId, Seq(Point(367880.004, 6673884.307), Point(367824.646, 6674001.441)), 131.683, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(49)))

      val ids = createTestAssets(367880.004, 6673884.307, roadLink, Some(SideCode.TowardsDigitizing.value), Some(317))
      val asset = directionalTrafficSignService.getPersistedAssetsByIds(ids).head
      val corrected = updater.correctPersistedAsset(asset, change)
      val newId = directionalTrafficSignService.adjustmentOperation(asset, corrected, change.newLinks.find(_.linkId == corrected.linkId).get)
      val newAsset = directionalTrafficSignService.getPersistedAssetsByIds(Set(newId)).head
      newAsset.createdBy.get should be("testCreator")
      newAsset.createdAt.get.toString().startsWith("2021-05-10") should be(true)
      newAsset.modifiedBy.get should be("testModifier")
      newAsset.modifiedAt.get.toString().startsWith("2022-05-10") should be(true)

      val asset2 = trafficLightService.getPersistedAssetsByIds(ids).head
      val corrected2 = updater.correctPersistedAsset(asset2, change)
      val newId2 = trafficLightService.adjustmentOperation(asset2, corrected2, change.newLinks.find(_.linkId == corrected2.linkId).get)
      val newAsset2 = trafficLightService.getPersistedAssetsByIds(Set(newId2)).head
      newAsset2.createdBy.get should be("testCreator")
      newAsset2.createdAt.get.toString().startsWith("2021-05-10") should be(true)
      newAsset2.modifiedBy.get should be("testModifier")
      newAsset2.modifiedAt.get.toString().startsWith("2022-05-10") should be(true)

      val asset3 = trafficSignService.getPersistedAssetsByIds(ids).head
      val corrected3 = updater.correctPersistedAsset(asset3, change)
      val newId3 = trafficSignService.adjustmentOperation(asset3, corrected3, change.newLinks.find(_.linkId == corrected3.linkId).get)
      val newAsset3 = trafficSignService.getPersistedAssetsByIds(Set(newId3)).head
      newAsset3.createdBy.get should be("testCreator")
      newAsset3.createdAt.get.toString().startsWith("2021-05-10") should be(true)
      newAsset3.modifiedBy.get should be("testModifier")
      newAsset3.modifiedAt.get.toString().startsWith("2022-05-10") should be(true)
    }
  }

  test("creation due to adjustmentOperation does not change creation or modification data if it's called by PointAssetUpdater") {

    runWithRollback {
      val oldLinkId = "875766ca-83b1-450b-baf1-db76d59176be:1"
      val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
      val roadLink = RoadLink(oldLinkId, Seq(Point(370276.441, 6670348.945), Point(370276.441, 6670367.114)), 45.317, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(49)))
      val ids = createTestAssets(370276.441, 6670348.945, roadLink, Some(SideCode.TowardsDigitizing.value), Some(289))
      val asset = directionalTrafficSignService.getPersistedAssetsByIds(ids).head
      val corrected = updater.correctPersistedAsset(asset, change)
      val newId = directionalTrafficSignService.adjustmentOperation(asset, corrected, change.newLinks.find(_.linkId == corrected.linkId).get)
      val newAsset = directionalTrafficSignService.getPersistedAssetsByIds(Set(newId)).head
      newAsset.createdBy.get should be("testCreator")
      newAsset.createdAt.get.toString().startsWith("2021-05-10") should be(true)
      newAsset.modifiedBy.get should be("testModifier")
      newAsset.modifiedAt.get.toString().startsWith("2022-05-10") should be(true)

      val asset2 = trafficLightService.getPersistedAssetsByIds(ids).head
      val corrected2 = updater.correctPersistedAsset(asset2, change)
      val newId2 = trafficLightService.adjustmentOperation(asset2, corrected2, change.newLinks.find(_.linkId == corrected2.linkId).get)
      val newAsset2 = trafficLightService.getPersistedAssetsByIds(Set(newId2)).head
      newAsset2.createdBy.get should be("testCreator")
      newAsset2.createdAt.get.toString().startsWith("2021-05-10") should be(true)
      newAsset2.modifiedBy.get should be("testModifier")
      newAsset2.modifiedAt.get.toString().startsWith("2022-05-10") should be(true)

      val asset3 = trafficSignService.getPersistedAssetsByIds(ids).head
      val corrected3 = updater.correctPersistedAsset(asset3, change)
      val newId3 = trafficSignService.adjustmentOperation(asset3, corrected3, change.newLinks.find(_.linkId == corrected3.linkId).get)
      val newAsset3 = trafficSignService.getPersistedAssetsByIds(Set(newId3)).head
      newAsset3.createdBy.get should be("testCreator")
      newAsset3.createdAt.get.toString().startsWith("2021-05-10") should be(true)
      newAsset3.modifiedBy.get should be("testModifier")
      newAsset3.modifiedAt.get.toString().startsWith("2022-05-10") should be(true)
    }
  }

  test("creation due to adjustmentOperation does not change creation or modification data if it's called by PointAssetUpdater (modification data is null)") {

    runWithRollback {
      val oldLinkId = "875766ca-83b1-450b-baf1-db76d59176be:1"
      val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
      val roadLink = RoadLink(oldLinkId, Seq(Point(370276.441, 6670348.945), Point(370276.441, 6670367.114)), 45.317, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(49)))
      val ids = createTestAssets(370276.441, 6670348.945, roadLink, Some(SideCode.TowardsDigitizing.value), Some(289), false)
      val asset = directionalTrafficSignService.getPersistedAssetsByIds(ids).head
      val corrected = updater.correctPersistedAsset(asset, change)
      val newId = directionalTrafficSignService.adjustmentOperation(asset, corrected, change.newLinks.find(_.linkId == corrected.linkId).get)
      val newAsset = directionalTrafficSignService.getPersistedAssetsByIds(Set(newId)).head
      newAsset.createdBy.get should be("testCreator")
      newAsset.createdAt.get.toString().startsWith("2021-05-10") should be(true)
      newAsset.modifiedBy should be(None)
      newAsset.modifiedAt should be(None)

      val asset2 = trafficLightService.getPersistedAssetsByIds(ids).head
      val corrected2 = updater.correctPersistedAsset(asset2, change)
      val newId2 = trafficLightService.adjustmentOperation(asset2, corrected2, change.newLinks.find(_.linkId == corrected2.linkId).get)
      val newAsset2 = trafficLightService.getPersistedAssetsByIds(Set(newId2)).head
      newAsset2.createdBy.get should be("testCreator")
      newAsset2.createdAt.get.toString().startsWith("2021-05-10") should be(true)
      newAsset2.modifiedBy should be(None)
      newAsset2.modifiedAt should be(None)

      val asset3 = trafficSignService.getPersistedAssetsByIds(ids).head
      val corrected3 = updater.correctPersistedAsset(asset3, change)
      val newId3 = trafficSignService.adjustmentOperation(asset3, corrected3, change.newLinks.find(_.linkId == corrected3.linkId).get)
      val newAsset3 = trafficSignService.getPersistedAssetsByIds(Set(newId3)).head
      newAsset3.createdBy.get should be("testCreator")
      newAsset3.createdAt.get.toString().startsWith("2021-05-10") should be(true)
      newAsset3.modifiedBy should be(None)
      newAsset3.modifiedAt should be(None)
    }
  }

  test("all traffic light properties are preserved") {
    runWithRollback {
      val oldLinkId = "1438d48d-dde6-43db-8aba-febf3d2220c0:1"
      val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
      val roadLink = RoadLink(oldLinkId, Seq(Point(367880.004, 6673884.307), Point(367824.646, 6674001.441)), 131.683, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(49)))

      val ids = createTestAssets(367880.004, 6673884.307, roadLink, Some(SideCode.TowardsDigitizing.value), Some(317))

      val asset = trafficLightService.getPersistedAssetsByIds(ids).head
      val corrected = updater.correctPersistedAsset(asset, change)
      val newId = trafficLightService.adjustmentOperation(asset, corrected, change.newLinks.find(_.linkId == corrected.linkId).get)
      val newAsset = trafficLightService.getPersistedAssetsByIds(Set(newId)).head

      val oldAssetProperties = asset.propertyData.groupBy(_.groupedId)
      val newAssetProperties = newAsset.propertyData.groupBy(_.groupedId)

      oldAssetProperties.keys.toSeq.sorted should be(newAssetProperties.keys.toSeq.sorted)
      oldAssetProperties.foreach { old =>
        val equivalentNewAssetProperties = newAssetProperties.find(_._1 == old._1).get
        old._2.foreach { oldProperty =>
          val equivalentNewProperty = equivalentNewAssetProperties._2.find(_.publicId == oldProperty.publicId).get
          oldProperty.toJson should be(equivalentNewProperty.toJson)
        }
      }
    }
  }
}

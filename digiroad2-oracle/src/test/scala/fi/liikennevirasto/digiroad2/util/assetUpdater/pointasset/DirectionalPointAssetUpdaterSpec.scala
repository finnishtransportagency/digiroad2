package fi.liikennevirasto.digiroad2.util.assetUpdater.pointasset

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, Property, SideCode}
import fi.liikennevirasto.digiroad2.client.{RoadLinkChange, RoadLinkChangeClient}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.DirectionalTrafficSignService
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, FloatingReason, PersistedPointAsset, Point}
import org.scalatest._
import org.scalatest.mockito.MockitoSugar

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
  val updater: DirectionalPointAssetUpdater = new DirectionalPointAssetUpdater(directionalTrafficSignService)

  test("Link split to multiple new links: assets are moved to correct link") {
    val oldLinkId = "99ade73f-979b-480b-976a-197ad365440a:1"
    val newLinkId1 = "1cb02550-5ce4-4c0a-8bee-c7f5e1f314d1:1"
    val newLinkId2 = "7c6fc2d3-f79f-4e03-a349-80103714a442:1"
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
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

  test("New link is longer than old one: Asset is relocated on new link") {
    val oldLinkId = "d2fb5669-b512-4c41-8fc8-c40a1c62f2b8:1"
    val newLinkId = "76271938-fc08-4061-8e23-d2cfdce8f051:1"
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
    val asset = testDirectionalPointAsset(1, 379539.5523067349, 6676588.239922434, 49, oldLinkId,
      0.7993821710321284, true, 0, SideCode.TowardsDigitizing.value, Some(341), NormalLinkInterface)
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
    val corrected = updater.correctPersistedAsset(asset, change)

    val distanceToOldLocation = Point(corrected.lon, corrected.lat).distance2DTo(Point(asset.lon, asset.lat))
    corrected.floating should be(false)
    corrected.linkId should be(newLinkId)
    distanceToOldLocation should be < updater.MaxDistanceDiffAllowed
  }

  test("New link is shorted than old one: asset is too far from new link so it is marked as floating") {
    val oldLinkId = "875766ca-83b1-450b-baf1-db76d59176be:1"
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
    val asset = testDirectionalPointAsset(1, 370235.4591063613, 6670366.945428849, 49, oldLinkId,
      44.83222354244527, false, 0, SideCode.TowardsDigitizing.value, Some(289), NormalLinkInterface)
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
    val assetMunicipality = 235 // New link is at municipality 49
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
    val asset = testDirectionalPointAsset(1, 367830.31375169184, 6673995.872282351, assetMunicipality, oldLinkId,
      123.74459961959604, true, 0, SideCode.TowardsDigitizing.value, Some(317), NormalLinkInterface)
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
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
    val asset = testDirectionalPointAsset(1, 391927.91316321003, 6672830.079543546, 91, oldLinkId,
      7.114809035180554, false, 0, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)
    val corrected = updater.correctPersistedAsset(asset, change)

    corrected.floating should be(true)
    corrected.floatingReason should be(Some(FloatingReason.DistanceToRoad))
  }
}

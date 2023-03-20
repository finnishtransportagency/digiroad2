package fi.liikennevirasto.digiroad2.util.assetUpdater.pointasset

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, Property}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, FloatingReason, PersistedPointAsset, Point}
import fi.liikennevirasto.digiroad2.client.{RoadLinkChange, RoadLinkChangeClient}
import fi.liikennevirasto.digiroad2.dao.pointasset.PostGISPedestrianCrossingDao
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.{ObstacleService, PedestrianCrossingService, RailwayCrossingService}
import org.scalatest._
import org.scalatest.mockito.MockitoSugar

class PointAssetUpdaterSpec extends FunSuite with Matchers {

  case class testPersistedPointAsset(id: Long, lon: Double, lat: Double, municipalityCode: Int, linkId: String,
                                     mValue: Double, floating: Boolean, timeStamp: Long, linkSource: LinkGeomSource,
                                     propertyData: Seq[Property] = Seq()) extends PersistedPointAsset

  val roadLinkChangeClient = new RoadLinkChangeClient
  val source: scala.io.BufferedSource = scala.io.Source.fromFile("digiroad2-oracle/src/test/resources/smallChangeSet.json")
  val changes: Seq[RoadLinkChange] = roadLinkChangeClient.convertToRoadLinkChange(source.mkString)

  val mockEventBus: DigiroadEventBus = MockitoSugar.mock[DigiroadEventBus]
  val mockRoadLinkService: RoadLinkService = MockitoSugar.mock[RoadLinkService]

  val pedestrianCrossingService: PedestrianCrossingService = new PedestrianCrossingService(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
    override lazy val dao: PostGISPedestrianCrossingDao = new PostGISPedestrianCrossingDao()
  }
  val obstacleService: ObstacleService = new ObstacleService(mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }
  val railwayCrossingService: RailwayCrossingService = new RailwayCrossingService(mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }
  val updater: PointAssetUpdater = new PointAssetUpdater(pedestrianCrossingService)

  test("Link split to multiple new links: assets are moved to correct link") {
    val oldLinkId = "99ade73f-979b-480b-976a-197ad365440a:1"
    val newLinkId1 = "1cb02550-5ce4-4c0a-8bee-c7f5e1f314d1:1"
    val newLinkId2 = "7c6fc2d3-f79f-4e03-a349-80103714a442:1"
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
    val asset1 = testPersistedPointAsset(1, 367457.211214136, 6673711.584371272, 49, oldLinkId,
      410.51770995333163, true, 0, NormalLinkInterface)
    val asset2 = testPersistedPointAsset(2, 367539.3567114221, 6673467.119157674, 49, oldLinkId,
      152.62045380018026, true, 0, NormalLinkInterface)
    val corrected1 = updater.correctPersistedAsset(asset1, change)
    val corrected2 = updater.correctPersistedAsset(asset2, change)

    corrected1.nonEmpty should be(true)
    val update1 = corrected1.get
    val distanceToOldLocation1 = Point(update1.lon, update1.lat).distance2DTo(Point(asset1.lon, asset1.lat))
    update1.linkId should be(newLinkId1)
    update1.floating should be(false)
    distanceToOldLocation1 should be < updater.MaxDistanceDiffAllowed

    corrected2.nonEmpty should be(true)
    val update2 = corrected2.get
    val distanceToOldLocation2 = Point(update2.lon, update2.lat).distance2DTo(Point(asset2.lon, asset2.lat))
    update2.linkId should be(newLinkId2)
    update2.floating should be(false)
    distanceToOldLocation2 should be < updater.MaxDistanceDiffAllowed
  }

  test("Link has merged to another link: asset is relocated on new link") {
    val oldLinkId1 = "88449ad1-ded1-4b4a-aa57-ae40571ae18b:1"
    val oldLinkId2 = "291f7e18-a48a-4afc-a84b-8485164288b2:1"
    val newLinkId = "eca24369-a77b-4e6f-875e-57dc85176003:1"
    val change1 = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId1).get
    val change2 = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId2).get

    val asset1 = testPersistedPointAsset(1, 391925.80082758254, 6672841.181664083, 91, oldLinkId1,
      7.114809035180554, true, 0, NormalLinkInterface)
    val corrected1 = updater.correctPersistedAsset(asset1, change1)
    corrected1.nonEmpty should be(true)
    val update1 = corrected1.get
    update1.floating should be(false)
    update1.linkId should be(newLinkId)
    val distanceToOldLocation1 = Point(update1.lon, update1.lat).distance2DTo(Point(asset1.lon, asset1.lat))
    distanceToOldLocation1 should be < updater.MaxDistanceDiffAllowed

    val asset2 = testPersistedPointAsset(1, 391943.08929429477, 6672846.323852181, 91, oldLinkId2,
      14.134182055427011, true, 0, NormalLinkInterface)
    val corrected2 = updater.correctPersistedAsset(asset2, change2)
    corrected2.nonEmpty should be(true)
    val update2 = corrected2.get
    update2.floating should be(false)
    update2.linkId should be(newLinkId)
    val distanceToOldLocation2 = Point(update2.lon, update2.lat).distance2DTo(Point(asset2.lon, asset2.lat))
    distanceToOldLocation2 should be < updater.MaxDistanceDiffAllowed
  }

  test("New link is longer than old one: Asset is relocated on new link") {
    val oldLinkId = "d2fb5669-b512-4c41-8fc8-c40a1c62f2b8:1"
    val newLinkId = "76271938-fc08-4061-8e23-d2cfdce8f051:1"
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
    val asset = testPersistedPointAsset(1, 379539.5523067349, 6676588.239922434, 49, oldLinkId,
      0.7993821710321284, true, 0, NormalLinkInterface)
    val corrected = updater.correctPersistedAsset(asset, change)
    corrected.nonEmpty should be(true)
    val update = corrected.get
    update.floating should be(false)
    update.linkId should be(newLinkId)
    val distanceToOldLocation = Point(update.lon, update.lat).distance2DTo(Point(asset.lon, asset.lat))
    distanceToOldLocation should be < updater.MaxDistanceDiffAllowed
  }

  test("New link is shorted than old one: asset is relocated on shorter link") {
    val oldLinkId = "875766ca-83b1-450b-baf1-db76d59176be:1"
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
    val asset = testPersistedPointAsset(1, 370243.9245965985, 6670363.935476765, 49, oldLinkId,
      35.833489781349485, true, 0, NormalLinkInterface)
    val corrected = updater.correctPersistedAsset(asset, change)
    corrected.nonEmpty should be(true)

    val update = corrected.get
    val distanceToOldLocation = Point(update.lon, update.lat).distance2DTo(Point(asset.lon, asset.lat))
    update.floating should be(false)
    distanceToOldLocation should be < updater.MaxDistanceDiffAllowed
  }

  test("New link is shorted than old one: asset is too far from new link so it is marked as floating") {
    val oldLinkId = "875766ca-83b1-450b-baf1-db76d59176be:1"
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
    val asset = testPersistedPointAsset(1, 370235.4591063613, 6670366.945428849, 49, oldLinkId,
      44.83222354244527, false, 0, NormalLinkInterface)
    val corrected = updater.correctPersistedAsset(asset, change)
    corrected.nonEmpty should be(true)
    corrected.get.floating should be(true)
    corrected.get.floatingReason should be(Some(FloatingReason.DistanceToRoad))
  }


  test("Link version has changed: asset is marked to new link without other adjustments") {
    val oldLinkId = "41cca8ff-4644-41aa-8de1-2702f1a57f80:1"
    val newLinkId = "41cca8ff-4644-41aa-8de1-2702f1a57f80:2"
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
    val asset = testPersistedPointAsset(1, 361945.5903231929, 6682479.948548384, 49, oldLinkId,
      148.59150863874925, true, 0, NormalLinkInterface)
    val corrected = updater.correctPersistedAsset(asset, change)

    corrected.nonEmpty should be(true)
    corrected.get.floating should be(false)
    corrected.get.linkId should not be oldLinkId
    corrected.get.linkId should be(newLinkId)
    corrected.get.lon should be(asset.lon)
    corrected.get.lat should be(asset.lat)
  }

  test("New link has different municipality than asset: asset is marked as floating") {
    val oldLinkId = "41cca8ff-4644-41aa-8de1-2702f1a57f80:1"
    val assetMunicipality = 235 // New link is at municipality 49
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
    val asset = testPersistedPointAsset(1, 361945.5903231929, 6682479.948548384, assetMunicipality, oldLinkId,
      148.59150863874925, true, 0, NormalLinkInterface)
    val corrected = updater.correctPersistedAsset(asset, change)

    corrected.nonEmpty should be(true)
    corrected.get.floating should be(true)
    corrected.get.floatingReason should be(Some(FloatingReason.DifferentMunicipalityCode))
    corrected.get.linkId should be(oldLinkId)
  }

  test("Link removed without no replacement: asset is marked as floating") {
    val oldLinkId = "7766bff4-5f02-4c30-af0b-42ad3c0296aa:1"
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
    val asset = testPersistedPointAsset(1, 366414.9482441691, 6674451.461887036, 49, oldLinkId,
      14.033238836181871, true, 0, NormalLinkInterface)
    val corrected = updater.correctPersistedAsset(asset, change)

    corrected.nonEmpty should be(true)
    corrected.get.floating should be(true)
    corrected.get.floatingReason should be(Some(FloatingReason.NoRoadLinkFound))
  }

  test("New link is too far from asset and no new location can be calculated: asset is marked as floating") {
    val oldLinkId = "88449ad1-ded1-4b4a-aa57-ae40571ae18b:1"
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
    val asset = testPersistedPointAsset(1, 391927.91316321003, 6672830.079543546, 91, oldLinkId,
      7.114809035180554, false, 0, NormalLinkInterface)
    val corrected = updater.correctPersistedAsset(asset, change)

    corrected.nonEmpty should be(true)
    corrected.get.floating should be(true)
    corrected.get.floatingReason should be(Some(FloatingReason.DistanceToRoad))
  }
}

package fi.liikennevirasto.digiroad2.util.assetUpdater.pointasset

import fi.liikennevirasto.digiroad2.FloatingReason.NoRoadLinkFound
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{RoadLinkChange, RoadLinkChangeClient, RoadLinkChangeType}
import fi.liikennevirasto.digiroad2.dao.pointasset.PostGISPedestrianCrossingDao
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset._
import fi.liikennevirasto.digiroad2.util.assetUpdater.ChangeTypeReport.{Floating, Move}
import org.joda.time.DateTime
import org.scalatest._
import org.scalatest.mockito.MockitoSugar
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.util.TestTransactions
import slick.jdbc.StaticQuery.interpolation

class PointAssetUpdaterSpec extends FunSuite with Matchers {

  case class testPersistedPointAsset(id: Long, lon: Double, lat: Double, municipalityCode: Int, linkId: String,
                                     mValue: Double, floating: Boolean, timeStamp: Long, linkSource: LinkGeomSource,
                                     propertyData: Seq[Property] = Seq(), externalIds: Seq[String] = Seq()) extends PersistedPointAsset

  case class testPedestrianCrossing(override val id: Long, override val linkId: String,
                                    override val lon: Double, override val lat: Double, override val mValue: Double,
                                    override val floating: Boolean, override val timeStamp: Long, override val municipalityCode: Int,
                                    override val propertyData: Seq[Property], override val createdBy: Option[String] = None,
                                    override val createdAt: Option[DateTime] = None, override val modifiedBy: Option[String] = None,
                                    override val modifiedAt: Option[DateTime] = None, override val expired: Boolean = false,
                                    override val linkSource: LinkGeomSource, override val externalIds: Seq[String] = Seq()) extends PersistedPoint

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

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  test("Link split to multiple new links: assets are moved to correct link") {
    runWithRollback {
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

      val distanceToOldLocation1 = Point(corrected1.lon, corrected1.lat).distance2DTo(Point(asset1.lon, asset1.lat))
      corrected1.linkId should be(newLinkId1)
      corrected1.floating should be(false)
      distanceToOldLocation1 should be < updater.MaxDistanceDiffAllowed

      val distanceToOldLocation2 = Point(corrected2.lon, corrected2.lat).distance2DTo(Point(asset2.lon, asset2.lat))
      corrected2.linkId should be(newLinkId2)
      corrected2.floating should be(false)
      distanceToOldLocation2 should be < updater.MaxDistanceDiffAllowed
    }
  }

  test("Link has merged to another link: asset is relocated on new link") {
    runWithRollback {
      val oldLinkId1 = "88449ad1-ded1-4b4a-aa57-ae40571ae18b:1"
      val oldLinkId2 = "291f7e18-a48a-4afc-a84b-8485164288b2:1"
      val newLinkId = "eca24369-a77b-4e6f-875e-57dc85176003:1"
      val change1 = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId1).get
      val change2 = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId2).get

      val asset1 = testPersistedPointAsset(1, 391925.80082758254, 6672841.181664083, 91, oldLinkId1,
        7.114809035180554, true, 0, NormalLinkInterface)
      val corrected1 = updater.correctPersistedAsset(asset1, change1)
      corrected1.floating should be(false)
      corrected1.linkId should be(newLinkId)
      val distanceToOldLocation1 = Point(corrected1.lon, corrected1.lat).distance2DTo(Point(asset1.lon, asset1.lat))
      distanceToOldLocation1 should be < updater.MaxDistanceDiffAllowed

      val asset2 = testPersistedPointAsset(1, 391943.08929429477, 6672846.323852181, 91, oldLinkId2,
        14.134182055427011, true, 0, NormalLinkInterface)
      val corrected2 = updater.correctPersistedAsset(asset2, change2)
      corrected2.floating should be(false)
      corrected2.linkId should be(newLinkId)
      val distanceToOldLocation2 = Point(corrected2.lon, corrected2.lat).distance2DTo(Point(asset2.lon, asset2.lat))
      distanceToOldLocation2 should be < updater.MaxDistanceDiffAllowed
    }
  }

  test("New link is longer than old one: Asset is relocated on new link") {
    runWithRollback {
      val oldLinkId = "d2fb5669-b512-4c41-8fc8-c40a1c62f2b8:1"
      val newLinkId = "76271938-fc08-4061-8e23-d2cfdce8f051:1"
      val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
      val asset = testPersistedPointAsset(1, 379539.5523067349, 6676588.239922434, 49, oldLinkId,
        0.7993821710321284, true, 0, NormalLinkInterface)
      val corrected = updater.correctPersistedAsset(asset, change)

      corrected.floating should be(false)
      corrected.linkId should be(newLinkId)
      val distanceToOldLocation = Point(corrected.lon, corrected.lat).distance2DTo(Point(asset.lon, asset.lat))
      distanceToOldLocation should be < updater.MaxDistanceDiffAllowed
    }
  }

  test("New link is shorted than old one: asset is relocated on shorter link") {
    runWithRollback {
      val oldLinkId = "875766ca-83b1-450b-baf1-db76d59176be:1"
      val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
      val asset = testPersistedPointAsset(1, 370243.9245965985, 6670363.935476765, 49, oldLinkId,
        35.833489781349485, true, 0, NormalLinkInterface)
      val corrected = updater.correctPersistedAsset(asset, change)

      val distanceToOldLocation = Point(corrected.lon, corrected.lat).distance2DTo(Point(asset.lon, asset.lat))
      corrected.floating should be(false)
      distanceToOldLocation should be < updater.MaxDistanceDiffAllowed
    }
  }

  test("New link is shorted than old one: asset is too far from new link so it is marked as floating") {
    runWithRollback {
      val oldLinkId = "875766ca-83b1-450b-baf1-db76d59176be:1"
      val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
      val asset = testPersistedPointAsset(1, 370235.4591063613, 6670366.945428849, 49, oldLinkId,
        44.83222354244527, false, 0, NormalLinkInterface)
      val corrected = updater.correctPersistedAsset(asset, change)

      corrected.floating should be(true)
      corrected.floatingReason should be(Some(FloatingReason.DistanceToRoad))
    }
  }


  test("Link version has changed: asset is marked to new link without other adjustments") {
    runWithRollback {
      val oldLinkId = "1438d48d-dde6-43db-8aba-febf3d2220c0:1"
      val newLinkId = "1438d48d-dde6-43db-8aba-febf3d2220c0:2"
      val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
      val asset = testPersistedPointAsset(1, 367830.31375169184, 6673995.872282351, 49, oldLinkId,
        123.74459961959604, true, 0, NormalLinkInterface)
      val corrected = updater.correctPersistedAsset(asset, change)

      corrected.floating should be(false)
      corrected.linkId should not be oldLinkId
      corrected.linkId should be(newLinkId)
      corrected.lon should be(asset.lon)
      corrected.lat should be(asset.lat)
    }
  }

  test("New link has different municipality than asset: asset is marked as floating") {
    runWithRollback {
      val oldLinkId = "1438d48d-dde6-43db-8aba-febf3d2220c0:1"
      val assetMunicipality = 235 // New link is at municipality 49
      val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
      val asset = testPersistedPointAsset(1, 367830.31375169184, 6673995.872282351, assetMunicipality, oldLinkId,
        123.74459961959604, true, 0, NormalLinkInterface)
      val corrected = updater.correctPersistedAsset(asset, change)

      corrected.floating should be(true)
      corrected.floatingReason should be(Some(FloatingReason.DifferentMunicipalityCode))
      corrected.linkId should be(oldLinkId)
    }
  }

  test("Link removed without no replacement: asset is marked as floating") {
    val oldLinkId = "7766bff4-5f02-4c30-af0b-42ad3c0296aa:1"
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
    val asset = testPersistedPointAsset(1, 366414.9482441691, 6674451.461887036, 49, oldLinkId,
      14.033238836181871, true, 0, NormalLinkInterface)
    val corrected = updater.correctPersistedAsset(asset, change)

    corrected.floating should be(true)
    corrected.floatingReason should be(Some(FloatingReason.NoRoadLinkFound))
  }

  test("New link is too far from asset and no new location can be calculated: asset is marked as floating") {
    runWithRollback {
      val oldLinkId = "88449ad1-ded1-4b4a-aa57-ae40571ae18b:1"
      val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
      val asset = testPersistedPointAsset(1, 391927.91316321003, 6672830.079543546, 91, oldLinkId,
        7.114809035180554, false, 0, NormalLinkInterface)
      val corrected = updater.correctPersistedAsset(asset, change)

      corrected.floating should be(true)
      corrected.floatingReason should be(Some(FloatingReason.DistanceToRoad))
    }
  }

  test("correct change report is formed for floating asset") {
    runWithRollback {
      val oldLinkId = "7766bff4-5f02-4c30-af0b-42ad3c0296aa:1"
      val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
      val property = Property(1, "suggest_box", "checkbox", false, Seq(PropertyValue("0", None, false)))
      val oldAsset = testPedestrianCrossing(1, oldLinkId, 366414.9482441691, 6674451.461887036, 14.033238836181871, false,
        0L, 49, Seq(property), None, None, None, None, false, NormalLinkInterface)
      val assetUpdate = AssetUpdate(1, 366414.9482441691, 6674451.461887036, oldLinkId, 14.033238836181871, None, None, 1680689415467L, true, Some(NoRoadLinkFound))
      val newAsset = testPedestrianCrossing(1, oldLinkId, assetUpdate.lon, assetUpdate.lat, assetUpdate.mValue, true,
        1L, 49, Seq(property), None, None, None, None, false, NormalLinkInterface)
      val reportedChange = updater.reportChange(oldAsset, newAsset, Floating, change, assetUpdate)
      reportedChange.linkId should be(oldLinkId)
      reportedChange.before.get.assetId should be(1)
      reportedChange.after.head.assetId should be(1)
      reportedChange.after.head.floatingReason.get should be(NoRoadLinkFound)
      reportedChange.after.head.values should be(s"""[{"id":1,"publicId":"suggest_box","propertyType":"checkbox","required":false,"values":[{"propertyValue":"0","propertyDisplayValue":null}],"groupedId":0}]""")
    }
  }

  test("correct change report is formed for moved asset") {
    runWithRollback {
      val oldLinkId = "875766ca-83b1-450b-baf1-db76d59176be:1"
      val newLinkId = "6eec9a4a-bcac-4afb-afc8-f4e6d40ec571:1"
      val property = Property(1, "suggest_box", "checkbox", false, Seq(PropertyValue("0", None, false)))
      val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
      val oldAsset = testPedestrianCrossing(1, oldLinkId, 370243.9245965985, 6670363.935476765, 35.83348978134948549, false,
        0L, 49, Seq(property), None, None, None, None, false, NormalLinkInterface)
      val assetUpdate = AssetUpdate(1, 370243.8824352341, 6670361.792711945, newLinkId, 35.2122522338214, None, None, 1L, false, None)
      val newAsset = testPedestrianCrossing(1, newLinkId, assetUpdate.lon, assetUpdate.lat, assetUpdate.mValue, false,
        1L, 49, Seq(property), None, None, None, None, false, NormalLinkInterface)
      val reportedChange = updater.reportChange(oldAsset, newAsset, Move, change, assetUpdate)
      reportedChange.linkId should be(oldLinkId)
      reportedChange.before.get.assetId should be(1)
      reportedChange.after.head.assetId should be(1)
      reportedChange.after.head.floatingReason should be(None)
      reportedChange.after.head.values should be(s"""[{"id":1,"publicId":"suggest_box","propertyType":"checkbox","required":false,"values":[{"propertyValue":"0","propertyDisplayValue":null}],"groupedId":0}]""")
    }
  }

  test("Split. Given a Road Link that is split into 2 new Links; when 1 new Link is deleted; then the Point Asset on the deleted Link should be floating.") {
    runWithRollback {
      val oldLinkID = "086404cc-ffaa-46e5-a0c5-b428a846261c:1"
      val change = changes.find(change => change.changeType == RoadLinkChangeType.Split && change.oldLink.get.linkId == oldLinkID).get
      val asset1 = testPersistedPointAsset(1, 487248.206, 6690189.822, 49, oldLinkID,
        410.51770995333163, true, 0, NormalLinkInterface)
      val corrected1 = updater.correctPersistedAsset(asset1, change)

      corrected1.linkId should be(oldLinkID)
      corrected1.floating should be(true)
    }
  }

  def createTestAssets(x: Double, y: Double, roadLink: RoadLink) = {
    val pcId = pedestrianCrossingService.create(IncomingPedestrianCrossing(x, y, roadLink.linkId, Set()), "testCreator", roadLink)
    val oId = obstacleService.create(IncomingObstacle(x,y, roadLink.linkId, Set(SimplePointAssetProperty("esterakennelma", Seq(PropertyValue("2"))))), "testCreator", roadLink)
    val rcId = railwayCrossingService.create(IncomingRailwayCrossing(x, y, roadLink.linkId,
      Set(SimplePointAssetProperty("tasoristeystunnus", Seq(PropertyValue(""))), SimplePointAssetProperty("turvavarustus", Seq(PropertyValue("1"))), SimplePointAssetProperty("rautatien_tasoristeyksen_nimi", Seq(PropertyValue("test"))))), "testCreator", roadLink)

    sqlu"""UPDATE ASSET
          SET CREATED_DATE = to_timestamp('2021-05-10T10:52:28.783Z', 'YYYY-MM-DD"T"HH24:MI:SS.FF3Z'),
              MODIFIED_BY = 'testModifier', MODIFIED_DATE = to_timestamp('2022-05-10T10:52:28.783Z', 'YYYY-MM-DD"T"HH24:MI:SS.FF3Z'),
              EXTERNAL_IDS = 'testExternalId'
          WHERE id in (${pcId}, ${oId}, ${rcId})
      """.execute

    Set(pcId, oId, rcId)
  }


  test("version change does not change asset id, creation or modification data if it's called by PointAssetUpdater") {
    val services = Seq(pedestrianCrossingService, obstacleService)

    runWithRollback {
      val oldLinkId = "1438d48d-dde6-43db-8aba-febf3d2220c0:1"
      val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
      val roadLink = RoadLink(oldLinkId, Seq(Point(367880.004, 6673884.307), Point(367824.646, 6674001.441)), 131.683, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(49)))

      val ids = createTestAssets(367880.004, 6673884.307, roadLink)
      Seq(pedestrianCrossingService, obstacleService).foreach {service =>
        val asset = service.getPersistedAssetsByIds(ids).head
        val corrected = updater.correctPersistedAsset(asset, change)
        val newId = service.adjustmentOperation(asset, corrected, change.newLinks.find(_.linkId == corrected.linkId).get)
        val newAsset = service.getPersistedAssetsByIds(Set(newId)).head.asInstanceOf[PersistedPoint]
        newAsset.id should be(asset.id)
        newAsset.createdBy.get should be("testCreator")
        newAsset.createdAt.get.toString().startsWith("2021-05-10") should be(true)
        newAsset.modifiedBy.get should be("testModifier")
        newAsset.modifiedAt.get.toString().startsWith("2022-05-10") should be(true)
      }
      val asset = railwayCrossingService.getPersistedAssetsByIds(ids).head
      val corrected = updater.correctPersistedAsset(asset, change)
      val newId = railwayCrossingService.adjustmentOperation(asset, corrected, change.newLinks.find(_.linkId == corrected.linkId).get)
      val newAsset = railwayCrossingService.getPersistedAssetsByIds(Set(newId)).head
      newAsset.id should be(asset.id)
      newAsset.createdBy.get should be("testCreator")
      newAsset.createdAt.get.toString().startsWith("2021-05-10") should be(true)
      newAsset.modifiedBy.get should be("testModifier")
      newAsset.modifiedAt.get.toString().startsWith("2022-05-10") should be(true)
    }
  }

  test("replace does not change asset id, creation or modification data if it's called by PointAssetUpdater") {
    runWithRollback {
      val oldLinkId = "875766ca-83b1-450b-baf1-db76d59176be:1"
      val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
      val roadLink = RoadLink(oldLinkId, Seq(Point(370276.441, 6670348.945), Point(370276.441, 6670367.114)), 45.317, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(49)))
      val ids = createTestAssets(370276.441, 6670348.945, roadLink)

      Seq(pedestrianCrossingService, obstacleService).foreach {service =>
        val asset = service.getPersistedAssetsByIds(ids).head
        val corrected = updater.correctPersistedAsset(asset, change)
        val newId = service.adjustmentOperation(asset, corrected, change.newLinks.find(_.linkId == corrected.linkId).get)
        val newAsset = service.getPersistedAssetsByIds(Set(newId)).head.asInstanceOf[PersistedPoint]
        newAsset.id should be(asset.id)
        newAsset.createdBy.get should be("testCreator")
        newAsset.createdAt.get.toString().startsWith("2021-05-10") should be(true)
        newAsset.modifiedBy.get should be("testModifier")
        newAsset.modifiedAt.get.toString().startsWith("2022-05-10") should be(true)
      }
      val asset = railwayCrossingService.getPersistedAssetsByIds(ids).head
      val corrected = updater.correctPersistedAsset(asset, change)
      val newId = railwayCrossingService.adjustmentOperation(asset, corrected, change.newLinks.find(_.linkId == corrected.linkId).get)
      val newAsset = railwayCrossingService.getPersistedAssetsByIds(Set(newId)).head
      newAsset.id should be(asset.id)
      newAsset.createdBy.get should be("testCreator")
      newAsset.createdAt.get.toString().startsWith("2021-05-10") should be(true)
      newAsset.modifiedBy.get should be("testModifier")
      newAsset.modifiedAt.get.toString().startsWith("2022-05-10") should be(true)
    }
  }

  test("floatingUpdate does not change creation or modification data") {
    runWithRollback {
      val oldLinkId = "7766bff4-5f02-4c30-af0b-42ad3c0296aa:1"
      val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
      val roadLink = RoadLink(oldLinkId, Seq(Point(366408.515, 6674439.018), Point(366425.832, 6674457.102)), 30.928, Unknown, 1, TrafficDirection.BothDirections, CycleOrPedestrianPath
        , None, None, Map("MUNICIPALITYCODE" -> BigInt(49)))
      val ids = createTestAssets(366414.9482441691, 6674451.461887036, roadLink)

      Seq(pedestrianCrossingService, obstacleService).foreach { service =>
        val asset = service.getPersistedAssetsByIds(ids).head
        val corrected = updater.correctPersistedAsset(asset, change)
        val newId = service.floatingUpdate(asset.id, corrected.floating, corrected.floatingReason)
        val newAsset = service.getPersistedAssetsByIds(Set(asset.id)).head.asInstanceOf[PersistedPoint]
        newAsset.floating should be(true)
        newAsset.createdBy.get should be("testCreator")
        newAsset.createdAt.get.toString().startsWith("2021-05-10") should be(true)
        newAsset.modifiedBy.get should be("testModifier")
        newAsset.modifiedAt.get.toString().startsWith("2022-05-10") should be(true)
      }
      val asset = railwayCrossingService.getPersistedAssetsByIds(ids).head
      val corrected = updater.correctPersistedAsset(asset, change)
      val newId = railwayCrossingService.floatingUpdate(asset.id, corrected.floating, corrected.floatingReason)
      val newAsset = railwayCrossingService.getPersistedAssetsByIds(Set(asset.id)).head
      newAsset.floating should be(true)
      newAsset.createdBy.get should be("testCreator")
      newAsset.createdAt.get.toString().startsWith("2021-05-10") should be(true)
      newAsset.modifiedBy.get should be("testModifier")
      newAsset.modifiedAt.get.toString().startsWith("2022-05-10") should be(true)
    }
  }

  test("adjustmentOperation does not change the geometry when saving a version change") {
    val services = Seq(pedestrianCrossingService, obstacleService)

    runWithRollback {
      val oldLinkId = "1438d48d-dde6-43db-8aba-febf3d2220c0:1"
      val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
      val roadLink = RoadLink(oldLinkId, Seq(Point(367880.004, 6673884.307), Point(367824.646, 6674001.441)), 131.683, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(49)))

      val ids = createTestAssets(367865.403, 6673891.022, roadLink)
      Seq(pedestrianCrossingService, obstacleService).foreach { service =>
        val asset = service.getPersistedAssetsByIds(ids).head
        val corrected = updater.correctPersistedAsset(asset, change)
        val newId = service.adjustmentOperation(asset, corrected, change.newLinks.find(_.linkId == corrected.linkId).get)
        val newAsset = service.getPersistedAssetsByIds(Set(newId)).head.asInstanceOf[PersistedPoint]
        newAsset.lon should be(corrected.lon)
        newAsset.lat should be(corrected.lat)
      }
      val asset = railwayCrossingService.getPersistedAssetsByIds(ids).head
      val corrected = updater.correctPersistedAsset(asset, change)
      val newId = railwayCrossingService.adjustmentOperation(asset, corrected, change.newLinks.find(_.linkId == corrected.linkId).get)
      val newAsset = railwayCrossingService.getPersistedAssetsByIds(Set(newId)).head
      newAsset.lon should be(corrected.lon)
      newAsset.lat should be(corrected.lat)
    }
  }

  test("when saving a relocated asset, the update method does not change the geometry") {
    runWithRollback {
      val oldLinkId = "875766ca-83b1-450b-baf1-db76d59176be:1"
      val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
      val roadLink = RoadLink(oldLinkId, Seq(Point(370276.441, 6670348.945), Point(370276.441, 6670367.114)), 45.317, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(49)))
      val ids = createTestAssets(370276.441, 6670349.045, roadLink)

      Seq(pedestrianCrossingService, obstacleService).foreach { service =>
        val asset = service.getPersistedAssetsByIds(ids).head
        val corrected = updater.correctPersistedAsset(asset, change)
        val newId = service.adjustmentOperation(asset, corrected, change.newLinks.find(_.linkId == corrected.linkId).get)
        val newAsset = service.getPersistedAssetsByIds(Set(newId)).head.asInstanceOf[PersistedPoint]
        corrected.lon should not be asset.lon
        corrected.lat should not be asset.lat
        newAsset.lon should be(corrected.lon)
        newAsset.lat should be(corrected.lat)
      }
      val asset = railwayCrossingService.getPersistedAssetsByIds(ids).head
      val corrected = updater.correctPersistedAsset(asset, change)
      val newId = railwayCrossingService.adjustmentOperation(asset, corrected, change.newLinks.find(_.linkId == corrected.linkId).get)
      val newAsset = railwayCrossingService.getPersistedAssetsByIds(Set(newId)).head
      corrected.lon should not be asset.lon
      corrected.lat should not be asset.lat
      newAsset.lon should be(corrected.lon)
      newAsset.lat should be(corrected.lat)
    }
  }

  test("Link is replaced with WinterRoads road link, float asset") {
    runWithRollback {
      val oldLinkId = "3469c252-6c52-4e57-b3cf-0045b2b3e47c:1"
      val newLinkId = "b6882964-2d4b-49e7-8f35-c042fac2f007:1"
      val lon = 493151.52
      val lat = 7356520.289
      val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
      val asset = testPersistedPointAsset(1, lon, lat, 698, oldLinkId,
        13.478, true, 0, NormalLinkInterface)
      val corrected = updater.correctPersistedAsset(asset, change)

      corrected.floating should be(true)
      corrected.linkId should be(oldLinkId)
    }
  }

  test("split with one part removed, the point asset placed between the splits should be placed on the remaining part") {
    runWithRollback {
      val oldLinkId = "b9073cbd-5fb7-4dcb-9a0d-b368c948f1d5:1"
      val newLinkId = "7da294fc-cb5e-48e3-8a2d-47addf586147:1"
      val lon = 286834.99
      val lat = 6752236.407
      val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
      val asset = testPersistedPointAsset(1, lon, lat, 430, oldLinkId,
        29.978, false, 0, NormalLinkInterface)
      val corrected = updater.correctPersistedAsset(asset, change)

      corrected.floating should be(false)
      corrected.linkId should be(newLinkId)
    }
  }

  test("After samuutus the externalIds should remain the same and be a part of the saved asset data") {
    runWithRollback {
      val oldLinkId = "1438d48d-dde6-43db-8aba-febf3d2220c0:1"
      val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
      val roadLink = RoadLink(oldLinkId, Seq(Point(367880.004, 6673884.307), Point(367824.646, 6674001.441)), 131.683, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(49)))

      val ids = createTestAssets(367880.004, 6673884.307, roadLink)
      Seq(pedestrianCrossingService, obstacleService).foreach { service =>
        val asset = service.getPersistedAssetsByIds(ids).head
        val corrected = updater.correctPersistedAsset(asset, change)
        val newId = service.adjustmentOperation(asset, corrected, change.newLinks.find(_.linkId == corrected.linkId).get)
        val newAsset = service.getPersistedAssetsByIds(Set(newId)).head.asInstanceOf[PersistedPoint]
        newAsset.externalIds should be(asset.externalIds)
      }
      val asset = railwayCrossingService.getPersistedAssetsByIds(ids).head
      val corrected = updater.correctPersistedAsset(asset, change)
      val newId = railwayCrossingService.adjustmentOperation(asset, corrected, change.newLinks.find(_.linkId == corrected.linkId).get)
      val newAsset = railwayCrossingService.getPersistedAssetsByIds(Set(newId)).head
      newAsset.externalIds should be(asset.externalIds)
    }
  }

  test("After samuutus the externalIds should remain the same and be a part of the saved FLOATING asset data.") {
    runWithRollback {
      val oldLinkId = "d2fb5669-b512-4c41-8fc8-c40a1c62f2b8:1"
      val newLinkId = "76271938-fc08-4061-8e23-d2cfdce8f051:1"
      val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get

      val testAsset = testPersistedPointAsset(1, 379539.5523067349, 6676588.239922434, 49, oldLinkId,
        0.7993821710321284, true, 0, NormalLinkInterface, Seq(), Seq("externalId"))
      val correctedTestAsset = updater.correctPersistedAsset(testAsset, change)
      val pedestrianCrossing = testPedestrianCrossing(1, oldLinkId, 379539.5523067349, 6676588.239922434, 0.799, true, 0, 49, Seq(), None, None, None, None, false, NormalLinkInterface, Seq("ExternalId"))
      val correctedPedestrianCrossing = updater.correctPersistedAsset(pedestrianCrossing, change)

      correctedTestAsset.externalIds should be(testAsset.externalIds)

      correctedPedestrianCrossing.externalIds should be(pedestrianCrossing.externalIds)
    }
  }

  test("Link gets a new version, asset externalIds should stay with the updated asset") {
    runWithRollback {
      val oldLinkId = "8619f047-cf35-4d2e-a9d7-ae00349e872b:1"
      val lon = 531191.535
      val lat = 7101805.878
      val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
      val asset = testPersistedPointAsset(1, lon, lat, 205, oldLinkId,
        0.5, true, 0, NormalLinkInterface, Seq(), externalIds = Seq("10"))
      val corrected = updater.correctPersistedAsset(asset, change)

      corrected.externalIds should be(asset.externalIds)
    }
  }

  test("ExternalId is included in a report for changed asset") {
    runWithRollback {
      val oldLinkId = "875766ca-83b1-450b-baf1-db76d59176be:1"
      val newLinkId = "6eec9a4a-bcac-4afb-afc8-f4e6d40ec571:1"
      val property = Property(1, "suggest_box", "checkbox", false, Seq(PropertyValue("0", None, false)))
      val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
      val oldAsset = testPedestrianCrossing(1, oldLinkId, 370243.9245965985, 6670363.935476765, 35.83348978134948549, false,
        0L, 49, Seq(property), None, None, None, None, false, NormalLinkInterface, Seq("ExternalId"))
      val assetUpdate = AssetUpdate(1, 370243.8824352341, 6670361.792711945, newLinkId, 35.2122522338214, None, None, 1L, false, None, Seq("ExternalId"))
      val newAsset = testPedestrianCrossing(1, newLinkId, assetUpdate.lon, assetUpdate.lat, assetUpdate.mValue, false,
        1L, 49, Seq(property), None, None, None, None, false, NormalLinkInterface, Seq("ExternalId"))
      val reportedChange = updater.reportChange(oldAsset, newAsset, Move, change, assetUpdate)

      reportedChange.after.head.externalIds should be(reportedChange.before.head.externalIds)
    }
  }

  test("Link version has changed: externalIds stays the same") {
    runWithRollback {
      val oldLinkId = "1438d48d-dde6-43db-8aba-febf3d2220c0:1"
      val newLinkId = "1438d48d-dde6-43db-8aba-febf3d2220c0:2"
      val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
      val asset = testPersistedPointAsset(1, 367834.833, 6673991.432, 49, oldLinkId,
        123.74459961959604, true, 0, NormalLinkInterface, Seq(), Seq("externalIdtest"))
      val corrected = updater.correctPersistedAsset(asset, change)

      corrected.linkId should be(newLinkId)
      corrected.externalIds should be(asset.externalIds)
    }
  }
}

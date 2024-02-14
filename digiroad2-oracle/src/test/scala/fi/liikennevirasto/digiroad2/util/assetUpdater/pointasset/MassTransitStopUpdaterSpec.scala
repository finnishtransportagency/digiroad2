package fi.liikennevirasto.digiroad2.util.assetUpdater.pointasset

import fi.liikennevirasto.digiroad2.FloatingReason.{NoRoadLinkFound, RoadOwnerChanged, TerminalChildless}
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{RoadLinkChange, RoadLinkChangeClient}
import fi.liikennevirasto.digiroad2.dao.{MassTransitStopDao, MunicipalityDao}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.{BusStopType, MassTransitStopOperations, MassTransitStopService, NewMassTransitStop}
import fi.liikennevirasto.digiroad2.util.{GeometryTransform, TestTransactions}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, FloatingReason, PersistedPointAsset, Point}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.util.assetUpdater.ChangeTypeReport.{Floating, Move}
import org.mockito.Mockito.when
import slick.jdbc.StaticQuery.interpolation

class MassTransitStopUpdaterSpec extends FunSuite with Matchers {

  case class testBusStop(id: Long, nationalId: Long, linkId: String, stopTypes: Seq[Int],
                         municipalityCode: Int, lon: Double, lat: Double, mValue: Double,
                         validityDirection: Option[Int], bearing: Option[Int],
                         validityPeriod: Option[String], floating: Boolean = false, timeStamp: Long = 0L,
                         propertyData: Seq[Property] = Seq(), linkSource: LinkGeomSource, terminalId: Option[Long] = None) extends PersistedPointAsset {
    override def getValidityDirection: Option[Int] = this.validityDirection
    override def getBearing: Option[Int] = this.bearing
  }

  val roadLinkChangeClient = new RoadLinkChangeClient
  val source: scala.io.BufferedSource = scala.io.Source.fromFile("digiroad2-oracle/src/test/resources/smallChangeSet.json")
  val changes: Seq[RoadLinkChange] = roadLinkChangeClient.convertToRoadLinkChange(source.mkString)

  val mockEventBus: DigiroadEventBus = MockitoSugar.mock[DigiroadEventBus]
  val mockRoadLinkService: RoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockMunicipalityDao: MunicipalityDao = MockitoSugar.mock[MunicipalityDao]
  val mockGeometryTransform: GeometryTransform = MockitoSugar.mock[GeometryTransform]

  val testMassTransitStopDao = new MassTransitStopDao

  val massTransitStopService: MassTransitStopService = new MassTransitStopService {
    val massTransitStopDao: MassTransitStopDao = testMassTransitStopDao
    val municipalityDao: MunicipalityDao = mockMunicipalityDao
    val roadLinkService: RoadLinkService = mockRoadLinkService
    val geometryTransform: GeometryTransform = mockGeometryTransform

    def eventbus: DigiroadEventBus = mockEventBus

    //get assets without dynSession
    override def getPersistedAssetsByIds(ids: Set[Long]): Seq[PersistedAsset] = {
      val idsStr = ids.toSeq.mkString(",")
      val filter = s"where a.asset_type_id = $typeId and a.id in ($idsStr)"
      fetchPointAssets(withFilter(filter))
    }
  }

  val updater: MassTransitStopUpdater = new MassTransitStopUpdater(massTransitStopService) {
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  test("Link split to multiple new links: assets are moved to correct link") {
    runWithRollback {
      val oldLinkId = "99ade73f-979b-480b-976a-197ad365440a:1"
      val newLinkId1 = "1cb02550-5ce4-4c0a-8bee-c7f5e1f314d1:1"
      val newLinkId2 = "7c6fc2d3-f79f-4e03-a349-80103714a442:1"
      val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get

      val newLinkInfo1 = change.newLinks.find(_.linkId == newLinkId1).get
      val newLinkInfo2 = change.newLinks.find(_.linkId == newLinkId2).get
      val newLink1 = RoadLink(newLinkId1, newLinkInfo1.geometry, newLinkInfo1.linkLength, Municipality, 1, TrafficDirection.BothDirections,
        Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(49)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
      val newLink2 = RoadLink(newLinkId1, newLinkInfo2.geometry, newLinkInfo2.linkLength, Municipality, 1, TrafficDirection.BothDirections,
        Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(49)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(newLinkId1, false)).thenReturn(Some(newLink1))
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(newLinkId2, false)).thenReturn(Some(newLink2))

      val oldLinkInfo = change.oldLink.get
      val oldLink = RoadLink(oldLinkId, oldLinkInfo.geometry, oldLinkInfo.linkLength, oldLinkInfo.adminClass, oldLinkInfo.municipality.get,
        oldLinkInfo.trafficDirection, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(49)))
      val stopId1 = massTransitStopService.create(NewMassTransitStop(367475.5758941994, 6673657.346462522, oldLinkId, 341,
        Seq(SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("2"))))), "test", oldLink, false)
      val stopId2 = massTransitStopService.create(NewMassTransitStop(367537.23265942856, 6673473.43531342, oldLinkId, 341,
        Seq(SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("3"))))), "test", oldLink, false)
      val createdStop1 = massTransitStopService.getPersistedAssetsByIds(Set(stopId1)).head
      val createdStop2 = massTransitStopService.getPersistedAssetsByIds(Set(stopId2)).head

      val corrected1 = updater.correctPersistedAsset(createdStop1, change)
      val corrected2 = updater.correctPersistedAsset(createdStop2, change)

      massTransitStopService.adjustmentOperation(createdStop1, corrected1, change.newLinks.find(_.linkId == newLinkId1).get)
      val adjustedAsset1 = massTransitStopService.getPersistedAssetsByIds(Set(stopId1)).head
      val distanceToOldLocation1 = Point(adjustedAsset1.lon, adjustedAsset1.lat).distance2DTo(Point(createdStop1.lon, createdStop1.lat))
      adjustedAsset1.linkId should be(newLinkId1)
      adjustedAsset1.nationalId should be(createdStop1.nationalId)
      adjustedAsset1.floating should be(false)
      adjustedAsset1.validityDirection should be(Some(SideCode.TowardsDigitizing.value))
      adjustedAsset1.lon should be(corrected1.lon)
      adjustedAsset1.lat should be(corrected1.lat)
      distanceToOldLocation1 should be < updater.MaxDistanceDiffAllowed
      adjustedAsset1.propertyData.filterNot(_.publicId == "muokattu_viimeksi").foreach{ property =>
        val oldProperty = createdStop1.propertyData.find(_.publicId == property.publicId).get
        property should be(oldProperty)
      }

      massTransitStopService.adjustmentOperation(createdStop2, corrected2, change.newLinks.find(_.linkId == newLinkId2).get)
      val adjustedAsset2 = massTransitStopService.getPersistedAssetsByIds(Set(stopId2)).head
      val distanceToOldLocation2 = Point(adjustedAsset2.lon, adjustedAsset2.lat).distance2DTo(Point(createdStop2.lon, createdStop2.lat))
      adjustedAsset2.linkId should be(newLinkId2)
      adjustedAsset2.nationalId should be(createdStop2.nationalId)
      adjustedAsset2.floating should be(false)
      adjustedAsset2.validityDirection should be(Some(SideCode.AgainstDigitizing.value))
      adjustedAsset2.lon should be(corrected2.lon)
      adjustedAsset2.lat should be(corrected2.lat)
      distanceToOldLocation2 should be < updater.MaxDistanceDiffAllowed
      adjustedAsset2.propertyData.filterNot(_.publicId == "muokattu_viimeksi").foreach{ property =>
        val oldProperty = createdStop2.propertyData.find(_.publicId == property.publicId).get
          property should be(oldProperty)
      }
    }
  }

  test("Administrative class has changed, bus stop is marked floating") {
    val oldLinkId = "1438d48d-dde6-43db-8aba-febf3d2220c0:1"
    val newLinkId = "1438d48d-dde6-43db-8aba-febf3d2220c0:2"
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get

    val newLinkInfo = change.newLinks.find(_.linkId == newLinkId).get
    val newAdminClass = State
    val newLink = RoadLink(newLinkId, newLinkInfo.geometry, newLinkInfo.linkLength, newAdminClass, 1, TrafficDirection.BothDirections,
      Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1), "SURFACETYPE" -> BigInt(2)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(newLinkId, false)).thenReturn(Some(newLink))

    val administrativeClassProperty = Property(1, MassTransitStopOperations.MassTransitStopAdminClassPublicId, "text", false, Seq(PropertyValue("2")))
    val asset = testBusStop(1, 11, oldLinkId, Seq(), 49, 367830.31375169184, 6673995.872282351,
      123.74459961959604, Some(SideCode.TowardsDigitizing.value), None, None, false, 0L,  Seq(administrativeClassProperty), NormalLinkInterface)

    val corrected = updater.correctPersistedAsset(asset, change)
    corrected.floating should be(true)
    corrected.floatingReason.get should be(RoadOwnerChanged)
    corrected.linkId should be(oldLinkId)
    corrected.linkId should not be(newLinkId)
  }

  test("With normal version change (no admin class change), bus stop is transferred to new link") {
    val oldLinkId = "1438d48d-dde6-43db-8aba-febf3d2220c0:1"
    val newLinkId = "1438d48d-dde6-43db-8aba-febf3d2220c0:2"
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get

    val newLinkInfo = change.newLinks.find(_.linkId == newLinkId).get
    val newLink = RoadLink(newLinkId, newLinkInfo.geometry, newLinkInfo.linkLength, Municipality, 1, TrafficDirection.BothDirections,
      Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(49)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(newLinkId, false)).thenReturn(Some(newLink))

    val administrativeClassProperty = Property(1, MassTransitStopOperations.MassTransitStopAdminClassPublicId, "text", false, Seq(PropertyValue("2")))
    val asset = testBusStop(1, 11, oldLinkId, Seq(), 49, 367830.31375169184, 6673995.872282351,
      123.74459961959604, Some(SideCode.TowardsDigitizing.value), None, None, false, 0L,  Seq(administrativeClassProperty), NormalLinkInterface)

    val corrected = updater.correctPersistedAsset(asset, change)
    corrected.floating should be(false)
    corrected.floatingReason should be(None)
    corrected.linkId should not be(oldLinkId)
    corrected.linkId should be(newLinkId)
  }

  test("A terminal bus stop with no children is marked floating, the condition does not apply to ordinary stops") {
    runWithRollback {
      val oldLinkId = "1438d48d-dde6-43db-8aba-febf3d2220c0:1"
      val newLinkId = "1438d48d-dde6-43db-8aba-febf3d2220c0:2"
      val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get

      val newLinkInfo = change.newLinks.find(_.linkId == newLinkId).get
      val newLink = RoadLink(newLinkId, newLinkInfo.geometry, newLinkInfo.linkLength, Municipality, 1, TrafficDirection.BothDirections,
        Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(49)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(newLinkId, false)).thenReturn(Some(newLink))

      val terminalStopProperty = Property(200, MassTransitStopOperations.MassTransitStopTypePublicId, "multiple_choice", false, Seq(PropertyValue(BusStopType.Terminal.value.toString)))
      val terminalStop = testBusStop(1, 11, oldLinkId, Seq(), 49, 367830.31375169184, 6673995.872282351,
        123.74459961959604, Some(SideCode.TowardsDigitizing.value), None, None, false, 0L, Seq(terminalStopProperty), NormalLinkInterface)
      val ordinaryStop = testBusStop(1, 11, oldLinkId, Seq(), 49, 367830.31375169184, 6673995.872282351,
        123.74459961959604, Some(SideCode.TowardsDigitizing.value), None, None, false, 0L, Seq(), NormalLinkInterface)

      val corrected1 = updater.correctPersistedAsset(terminalStop, change)
      corrected1.floating should be(true)
      corrected1.floatingReason.get should be(TerminalChildless)
      corrected1.linkId should be(oldLinkId)
      corrected1.linkId should not be (newLinkId)

      val corrected2 = updater.correctPersistedAsset(ordinaryStop, change)
      corrected2.floating should be(false)
      corrected2.floatingReason should be(None)
      corrected2.linkId should not be(oldLinkId)
      corrected2.linkId should be (newLinkId)
    }
  }

  test("A terminal bus stop with children is not marked floating") {
    runWithRollback {
      val oldLinkId1 = "88449ad1-ded1-4b4a-aa57-ae40571ae18b:1"
      val oldLinkId2 = "291f7e18-a48a-4afc-a84b-8485164288b2:1"
      val newLinkId = "eca24369-a77b-4e6f-875e-57dc85176003:1"
      val change1 = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId1).get
      val change2 = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId2).get

      val oldLinkInfo1 = change1.oldLink.get
      val oldLink1 = RoadLink(oldLinkId1, oldLinkInfo1.geometry, oldLinkInfo1.linkLength, oldLinkInfo1.adminClass, oldLinkInfo1.municipality.get,
        oldLinkInfo1.trafficDirection, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)))
      val oldLinkInfo2 = change2.oldLink.get
      val oldLink2 = RoadLink(oldLinkId2, oldLinkInfo2.geometry, oldLinkInfo2.linkLength, oldLinkInfo2.adminClass, oldLinkInfo2.municipality.get,
        oldLinkInfo2.trafficDirection, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)))

      val newLinkInfo = change1.newLinks.find(_.linkId == newLinkId).get
      val newLink = RoadLink(newLinkId, newLinkInfo.geometry, newLinkInfo.linkLength, Municipality, 1, TrafficDirection.BothDirections,
        Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(newLinkId, false)).thenReturn(Some(newLink))

      val terminalStopId = massTransitStopService.create(NewMassTransitStop(391922.1567866767, 6672845.986092816, oldLinkId1, 341,
        Seq(SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("3"))), SimplePointAssetProperty(MassTransitStopOperations.MassTransitStopTypePublicId,
          Seq(PropertyValue(BusStopType.Terminal.value.toString))))), "test", oldLink1, false)
      val childStopId = massTransitStopService.create(NewMassTransitStop(391942.45320008986, 6672844.827758611, oldLinkId2, 23,
        Seq(SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("2"))))), "test", oldLink2, false)
      val terminalStop = massTransitStopService.getPersistedAssetsByIds(Set(terminalStopId)).head

      sqlu"insert into TERMINAL_BUS_STOP_LINK(TERMINAL_ASSET_ID, BUS_STOP_ASSET_ID) values ($terminalStopId, $childStopId)".execute

      val corrected = updater.correctPersistedAsset(terminalStop, change1)
      corrected.linkId should be(newLinkId)
      corrected.floating should be(false)
    }
  }

  test("Link has merged to another link: assets are relocated on new link and validity direction is fixed") {
    runWithRollback {
      val oldLinkId1 = "88449ad1-ded1-4b4a-aa57-ae40571ae18b:1"
      val oldLinkId2 = "291f7e18-a48a-4afc-a84b-8485164288b2:1"
      val newLinkId = "eca24369-a77b-4e6f-875e-57dc85176003:1"
      val change1 = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId1).get
      val change2 = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId2).get

      val newLinkInfo = change1.newLinks.find(_.linkId == newLinkId).get
      val newLink = RoadLink(newLinkId, newLinkInfo.geometry, newLinkInfo.linkLength, Municipality, 1, TrafficDirection.AgainstDigitizing,
        Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(1)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(newLinkId, false)).thenReturn(Some(newLink))

      val oldLinkInfo1 = change1.oldLink.get
      val oldLink1 = RoadLink(oldLinkId1, oldLinkInfo1.geometry, oldLinkInfo1.linkLength, oldLinkInfo1.adminClass, oldLinkInfo1.municipality.get,
        oldLinkInfo1.trafficDirection, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)))
      val oldLinkInfo2 = change2.oldLink.get
      val oldLink2 = RoadLink(oldLinkId2, oldLinkInfo2.geometry, oldLinkInfo2.linkLength, oldLinkInfo2.adminClass, oldLinkInfo2.municipality.get,
        oldLinkInfo2.trafficDirection, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)))

      val stopId1 = massTransitStopService.create(NewMassTransitStop(391922.1567866767, 6672845.986092816, oldLinkId1, 341,
        Seq(SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("3"))))), "testCreator", oldLink1, false)
      val stopId2 = massTransitStopService.create(NewMassTransitStop(391942.45320008986, 6672844.827758611, oldLinkId2, 23,
        Seq(SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("2"))))), "testCreator", oldLink2, false)

      sqlu"""UPDATE ASSET
          SET CREATED_DATE = to_timestamp('2021-05-10T10:52:28.783Z', 'YYYY-MM-DD"T"HH24:MI:SS.FF3Z'),
              MODIFIED_BY = 'testModifier', MODIFIED_DATE = to_timestamp('2022-05-10T10:52:28.783Z', 'YYYY-MM-DD"T"HH24:MI:SS.FF3Z')
          WHERE id = ${stopId1}
      """.execute

      sqlu"""UPDATE ASSET
          SET CREATED_DATE = to_timestamp('2021-05-10T10:52:28.783Z', 'YYYY-MM-DD"T"HH24:MI:SS.FF3Z')
          WHERE id = ${stopId2}
      """.execute

      val createdStop1 = massTransitStopService.getPersistedAssetsByIds(Set(stopId1)).head
      val createdStop2 = massTransitStopService.getPersistedAssetsByIds(Set(stopId2)).head

      val corrected1 = updater.correctPersistedAsset(createdStop1, change1)
      massTransitStopService.adjustmentOperation(createdStop1, corrected1, change1.newLinks.head)
      val adjustedAsset1 = massTransitStopService.getPersistedAssetsByIds(Set(stopId1)).head
      val distanceToOldLocation1 = Point(adjustedAsset1.lon, adjustedAsset1.lat).distance2DTo(Point(createdStop1.lon, createdStop1.lat))
      adjustedAsset1.linkId should be(newLinkId)
      adjustedAsset1.nationalId should be(createdStop1.nationalId)
      adjustedAsset1.floating should be(false)
      adjustedAsset1.validityDirection should be(Some(SideCode.AgainstDigitizing.value))
      adjustedAsset1.created.modifier.get should be("testCreator")
      adjustedAsset1.created.modificationTime.get.toString().startsWith("2021-05-10") should be(true)
      adjustedAsset1.modified.modifier.get should be("testModifier")
      adjustedAsset1.modified.modificationTime.get.toString().startsWith("2022-05-10") should be(true)
      distanceToOldLocation1 should be < updater.MaxDistanceDiffAllowed
      adjustedAsset1.propertyData.filterNot(_.publicId == "muokattu_viimeksi").foreach{ property =>
        val oldProperty = createdStop1.propertyData.find(_.publicId == property.publicId).get
        property should be(oldProperty)
      }

      val corrected2 = updater.correctPersistedAsset(createdStop2, change2)
      massTransitStopService.adjustmentOperation(createdStop2, corrected2, change2.newLinks.head)
      val adjustedAsset2 = massTransitStopService.getPersistedAssetsByIds(Set(stopId2)).head
      val distanceToOldLocation2 = Point(adjustedAsset2.lon, adjustedAsset2.lat).distance2DTo(Point(createdStop2.lon, createdStop2.lat))
      adjustedAsset2.linkId should be(newLinkId)
      adjustedAsset2.nationalId should be(createdStop2.nationalId)
      adjustedAsset2.floating should be(false)
      adjustedAsset2.validityDirection should be(Some(SideCode.AgainstDigitizing.value))
      adjustedAsset2.created.modifier.get should be("testCreator")
      adjustedAsset2.created.modificationTime.get.toString().startsWith("2021-05-10") should be(true)
      adjustedAsset2.modified.modifier should be(None)
      adjustedAsset2.modified.modificationTime should be(None)
      distanceToOldLocation2 should be < updater.MaxDistanceDiffAllowed
      adjustedAsset2.propertyData.filterNot(_.publicId == "muokattu_viimeksi").foreach{ property =>
        val oldProperty = createdStop2.propertyData.find(_.publicId == property.publicId).get
        if (property.publicId == "vaikutussuunta") {
          property should not be(oldProperty)
        } else {
          property should be(oldProperty)
        }
      }
    }
  }

  test("Asset validity direction does not match new link direction: Asset is marked floating") {
    val oldLinkId = "291f7e18-a48a-4afc-a84b-8485164288b2:1"
    val newLinkId = "eca24369-a77b-4e6f-875e-57dc85176003:1"
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
    val asset = testBusStop(1, 11, oldLinkId, Seq(), 91, 391936.8899081593, 6672840.334351871,
      4.773532861864595, Some(SideCode.AgainstDigitizing.value), None, None, false, 0L, Seq(), NormalLinkInterface)

    val newLinkInfo = change.newLinks.find(_.linkId == newLinkId).get
    val newLink = RoadLink(newLinkId, newLinkInfo.geometry, newLinkInfo.linkLength, Municipality, 1, TrafficDirection.AgainstDigitizing,
      Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(newLinkId, false)).thenReturn(Some(newLink))

    val corrected = updater.correctPersistedAsset(asset, change)
    corrected.floating should be(true)
    corrected.floatingReason should be(Some(FloatingReason.TrafficDirectionNotMatch))
    corrected.lon should be(asset.lon)
    corrected.lat should be(asset.lat)
    corrected.validityDirection should be(asset.getValidityDirection)
    corrected.linkId should not be newLinkId
  }

  test("New link is shorted than old one: asset is relocated on shorter link") {
    val oldLinkId = "875766ca-83b1-450b-baf1-db76d59176be:1"
    val newLinkId = "6eec9a4a-bcac-4afb-afc8-f4e6d40ec571:1"
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
    val asset = testBusStop(1, 11, oldLinkId, Seq(), 49, 370243.9245965985, 6670363.935476765,
      35.833489781349485, Some(SideCode.TowardsDigitizing.value), Some(289), None, true, 0L, Seq(), NormalLinkInterface)

    val newLinkInfo = change.newLinks.find(_.linkId == newLinkId).get
    val newLink = RoadLink(newLinkId, newLinkInfo.geometry, newLinkInfo.linkLength, Municipality, 1, TrafficDirection.BothDirections,
      Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(49)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(newLinkId, false)).thenReturn(Some(newLink))

    val corrected = updater.correctPersistedAsset(asset, change)
    val distanceToOldLocation = Point(corrected.lon, corrected.lat).distance2DTo(Point(asset.lon, asset.lat))
    corrected.floating should be(false)
    corrected.linkId should be(newLinkId)
    distanceToOldLocation should be < updater.MaxDistanceDiffAllowed
  }

  test("New link is shorter than old one: asset is too far from new link so it is marked as floating") {
    val oldLinkId = "875766ca-83b1-450b-baf1-db76d59176be:1"
    val newLinkId = "6eec9a4a-bcac-4afb-afc8-f4e6d40ec571:1"
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
    val asset = testBusStop(1, 11, oldLinkId, Seq(), 49, 370235.4591063613, 6670366.945428849,
      44.83222354244527, Some(SideCode.TowardsDigitizing.value), Some(289), None, false, 0L, Seq(), NormalLinkInterface)

    val newLinkInfo = change.newLinks.find(_.linkId == newLinkId).get
    val newLink = RoadLink(newLinkId, newLinkInfo.geometry, newLinkInfo.linkLength, Municipality, 1, TrafficDirection.BothDirections,
      Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(49)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(newLinkId, false)).thenReturn(Some(newLink))

    val corrected = updater.correctPersistedAsset(asset, change)
    corrected.floating should be(true)
    corrected.floatingReason should be(Some(FloatingReason.DistanceToRoad))
  }

  test("New link has different municipality than asset: asset is marked as floating") {
    val oldLinkId = "1438d48d-dde6-43db-8aba-febf3d2220c0:1"
    val newLinkId = "1438d48d-dde6-43db-8aba-febf3d2220c0:2"
    val assetMunicipality = 235 // New link is at municipality 49
    val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
    val asset = testBusStop(1, 11, oldLinkId, Seq(), assetMunicipality, 367830.31375169184, 6673995.872282351,
      123.74459961959604, Some(SideCode.TowardsDigitizing.value), Some(317), None, false, 0L, Seq(), NormalLinkInterface)

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
    val asset = testBusStop(1, 11, oldLinkId, Seq(), 49, 378759.66525429586, 6672990.60914197,
      99.810467297064, Some(SideCode.TowardsDigitizing.value), Some(351), None, false, 0L, Seq(), NormalLinkInterface)
    val corrected = updater.correctPersistedAsset(asset, change)

    corrected.floating should be(true)
    corrected.floatingReason should be(Some(FloatingReason.NoRoadLinkFound))
  }

  test("Correct change report is formed for floating asset") {
    runWithRollback {
      val oldLinkId = "7766bff4-5f02-4c30-af0b-42ad3c0296aa:1"
      val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
      val oldLinkInfo = change.oldLink.get
      val oldLink = RoadLink(oldLinkId, oldLinkInfo.geometry, oldLinkInfo.linkLength, oldLinkInfo.adminClass, oldLinkInfo.municipality.get,
        oldLinkInfo.trafficDirection, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(49)))
      val stopId = massTransitStopService.create(NewMassTransitStop(378759.66525429586, 6672990.60914197, oldLinkId, 351,
        Seq(SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("2"))))), "test", oldLink, false)
      val createdStop = massTransitStopService.getPersistedAssetsByIds(Set(stopId)).head
      val corrected = updater.correctPersistedAsset(createdStop, change)
      val newAsset = massTransitStopService.createOperation(createdStop, corrected)
      val reportedChange = updater.reportChange(createdStop, newAsset, Floating, change, corrected)
      reportedChange.linkId should be(oldLinkId)
      reportedChange.before.get.assetId should be(stopId)
      reportedChange.after.head.assetId should be(stopId)
      reportedChange.after.head.floatingReason.get should be(NoRoadLinkFound)
      reportedChange.after.head.values.nonEmpty should be(true)
    }
  }

  test("Correct change report is formed for moved asset") {
    runWithRollback {
      val oldLinkId = "875766ca-83b1-450b-baf1-db76d59176be:1"
      val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get
      val oldLinkInfo = change.oldLink.get
      val oldLink = RoadLink(oldLinkId, oldLinkInfo.geometry, oldLinkInfo.linkLength, oldLinkInfo.adminClass, oldLinkInfo.municipality.get,
        oldLinkInfo.trafficDirection, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(49)))
      val stopId = massTransitStopService.create(NewMassTransitStop(370243.9245965985, 6670363.935476765, oldLinkId, 3289,
        Seq(SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("2"))))), "test", oldLink, false)
      val createdStop = massTransitStopService.getPersistedAssetsByIds(Set(stopId)).head
      val corrected = updater.correctPersistedAsset(createdStop, change)
      val newAsset = massTransitStopService.createOperation(createdStop, corrected)
      val reportedChange = updater.reportChange(createdStop, newAsset, Move, change, corrected)
      reportedChange.linkId should be(oldLinkId)
      reportedChange.before.get.assetId should be(stopId)
      reportedChange.after.head.assetId should be(stopId)
      reportedChange.after.head.floatingReason should be(None)
      reportedChange.after.head.values.nonEmpty should be(true)
      reportedChange.after.head.values.contains(s"""vaikutussuunta","propertyType":"single_choice","required":false,"values":[{"propertyValue":"2""") should be(true)
    }
  }

  test("Validity direction is changed in the report") {
    runWithRollback {
      val oldLinkId = "291f7e18-a48a-4afc-a84b-8485164288b2:1"
      val newLinkId = "eca24369-a77b-4e6f-875e-57dc85176003:1"
      val change = changes.find(change => change.oldLink.nonEmpty && change.oldLink.get.linkId == oldLinkId).get

      val oldLinkInfo = change.oldLink.get
      val oldLink = RoadLink(oldLinkId, oldLinkInfo.geometry, oldLinkInfo.linkLength, oldLinkInfo.adminClass, oldLinkInfo.municipality.get,
        oldLinkInfo.trafficDirection, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)))

      val stopId = massTransitStopService.create(NewMassTransitStop(391942.45320008986, 6672844.827758611, oldLinkId, 23,
        Seq(SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("2"))))), "test", oldLink, false)
      val createdStop = massTransitStopService.getPersistedAssetsByIds(Set(stopId)).head

      val newLinkInfo = change.newLinks.find(_.linkId == newLinkId).get
      val newLink = RoadLink(newLinkId, newLinkInfo.geometry, newLinkInfo.linkLength, Municipality, 1, TrafficDirection.AgainstDigitizing,
        Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(newLinkId, false)).thenReturn(Some(newLink))

      val corrected = updater.correctPersistedAsset(createdStop, change)

      val newAsset = massTransitStopService.createOperation(createdStop, corrected)
      val reportedChange = updater.reportChange(createdStop, newAsset, Move, change, corrected)
      reportedChange.linkId should be(oldLinkId)
      reportedChange.before.get.assetId should be(stopId)
      reportedChange.after.head.assetId should be(stopId)
      reportedChange.after.head.floatingReason should be(None)
      reportedChange.after.head.linearReference.get.validityDirection.get should be(SideCode.AgainstDigitizing.value)
      reportedChange.after.head.values.nonEmpty should be(true)
      reportedChange.after.head.values.contains(s"""vaikutussuunta","propertyType":"single_choice","required":false,"values":[{"propertyValue":"3""") should be(true)
    }
  }
}

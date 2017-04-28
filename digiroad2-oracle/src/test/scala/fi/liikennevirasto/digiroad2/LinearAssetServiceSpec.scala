
package fi.liikennevirasto.digiroad2

import com.vividsolutions.jts.geom.{Geometry, GeometryFactory, Polygon}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, MValueAdjustment}
import fi.liikennevirasto.digiroad2.linearasset.ValidityPeriodDayOfWeek.{Saturday, Weekday}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.{PolygonTools, TestTransactions}
import org.geotools.geometry.jts.GeometryBuilder
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.mockito.ArgumentCaptor
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

import scala.collection.mutable.ListBuffer

class LinearAssetServiceSpec extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]

  when(mockVVHClient.fetchByLinkId(388562360l)).thenReturn(Some(VVHRoadlink(388562360l, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockVVHClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(VVHRoadlink(388562360l, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))

  val roadLink = RoadLink(
    1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)))
  when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(roadLink), Nil))
  when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[Int])).thenReturn((List(roadLink), Nil))

  val mockLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]
  when(mockLinearAssetDao.fetchLinearAssetsByLinkIds(30, Seq(1), "mittarajoitus"))
    .thenReturn(Seq(PersistedLinearAsset(1, 1, 1, Some(NumericValue(40000)), 0.4, 9.6, None, None, None, None, false, 30, 0, None)))

  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val linearAssetDao = new OracleLinearAssetDao(mockVVHClient)

  object PassThroughService extends LinearAssetOperations {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: OracleLinearAssetDao = mockLinearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def vvhClient: VVHClient = mockVVHClient
    override def polygonTools: PolygonTools = mockPolygonTools
  }

  object ServiceWithDao extends LinearAssetOperations {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: OracleLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def vvhClient: VVHClient = mockVVHClient
    override def polygonTools: PolygonTools = mockPolygonTools
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(PassThroughService.dataSource)(test)

  test("Expire numerical limit") {
    runWithRollback {
      ServiceWithDao.expire(Seq(11111l), "lol")
      val limit = linearAssetDao.fetchLinearAssetsByIds(Set(11111), "mittarajoitus").head
      limit.expired should be (true)
    }
  }

  test("Update numerical limit") {
    runWithRollback {
      //Update Numeric Values By Expiring the Old Asset
      val limitToUpdate = linearAssetDao.fetchLinearAssetsByIds(Set(11111), "mittarajoitus").head
      val newAssetIdCreatedWithUpdate = ServiceWithDao.update(Seq(11111l), NumericValue(2000), "UnitTestsUser")

      //Verify if the new data of the new asset is equal to old asset
      val limitUpdated = linearAssetDao.fetchLinearAssetsByIds(newAssetIdCreatedWithUpdate.toSet, "mittarajoitus").head

      limitUpdated.id should not be (limitToUpdate.id)
      limitUpdated.linkId should be (limitToUpdate.linkId)
      limitUpdated.sideCode should be (limitToUpdate.sideCode)
      limitUpdated.value should be (Some(NumericValue(2000)))
      limitUpdated.startMeasure should be (limitToUpdate.startMeasure)
      limitUpdated.endMeasure should be (limitToUpdate.endMeasure)
      limitUpdated.createdBy should be (limitToUpdate.createdBy)
      limitUpdated.createdDateTime should be (limitToUpdate.createdDateTime)
      limitUpdated.modifiedBy should be (Some("UnitTestsUser"))
      limitUpdated.modifiedDateTime should not be empty
      limitUpdated.expired should be (false)
      limitUpdated.typeId should be (limitToUpdate.typeId)
      limitUpdated.vvhTimeStamp should be (limitToUpdate.vvhTimeStamp)

      //Verify if old asset is expired
      val limitExpired = linearAssetDao.fetchLinearAssetsByIds(Set(11111), "mittarajoitus").head
      limitExpired.expired should be (true)
    }
  }

  test("Update Exit number Text Field") {
    runWithRollback {
      //Update Text Values By Expiring the Old Asset
      val assetToUpdate = linearAssetDao.fetchAssetsWithTextualValuesByIds(Set(600068), "liittymänumero").head
      val newAssetIdCreatedWithUpdate = ServiceWithDao.update(Seq(600068), TextualValue("Value for Test"), "UnitTestsUser")

      //Verify if the new data of the new asset is equal to old asset
      val assetUpdated = linearAssetDao.fetchAssetsWithTextualValuesByIds(newAssetIdCreatedWithUpdate.toSet, "liittymänumero").head

      assetUpdated.id should not be (assetToUpdate.id)
      assetUpdated.linkId should be(assetToUpdate.linkId)
      assetUpdated.sideCode should be(assetToUpdate.sideCode)
      assetUpdated.value should be(Some(TextualValue("Value for Test")))
      assetUpdated.startMeasure should be(assetToUpdate.startMeasure)
      assetUpdated.endMeasure should be(assetToUpdate.endMeasure)
      assetUpdated.createdBy should be(assetToUpdate.createdBy)
      assetUpdated.createdDateTime should be(assetToUpdate.createdDateTime)
      assetUpdated.modifiedBy should be(Some("UnitTestsUser"))
      assetUpdated.modifiedDateTime should not be empty
      assetUpdated.expired should be(false)
      assetUpdated.typeId should be(assetToUpdate.typeId)
      assetUpdated.vvhTimeStamp should be(assetToUpdate.vvhTimeStamp)

      //Verify if old asset is expired
      val assetExpired = linearAssetDao.fetchLinearAssetsByIds(Set(600068), "liittymänumero").head
      assetExpired.expired should be(true)
    }
  }

  test("Update prohibition") {
    when(mockVVHClient.fetchByLinkId(1610349)).thenReturn(Some(VVHRoadlink(1610349, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))

    runWithRollback {
      ServiceWithDao.update(Seq(600020l), Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty))), "lol")
      val limit = linearAssetDao.fetchProhibitionsByLinkIds(190, Seq(1610349)).head

      limit.value should be (Some(Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty, null)))))
      limit.expired should be (false)
    }
  }
  test("Create new linear asset") {
    runWithRollback {
      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(388562360l, 0, 20, NumericValue(1000), 1, 0, None)), 30, "testuser")
      newAssets.length should be(1)
      val asset = linearAssetDao.fetchLinearAssetsByIds(Set(newAssets.head), "mittarajoitus").head
      asset.value should be (Some(NumericValue(1000)))
      asset.expired should be (false)
    }
  }

  test("Create new maintenanceRoad") {
    val prop1 = Properties("huoltotie_kayttooikeus", "single_choice", "1")
    val prop2 = Properties("huoltotie_huoltovastuu", "single_choice", "2")
    val prop3 = Properties("huoltotie_tiehoitokunta", "text", "text")

    val propertiesSeq :Seq[Properties] = List(prop1, prop2, prop3)

    val maintenanceRoad = MaintenanceRoad(propertiesSeq)
    runWithRollback {
      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(388562360l, 0, 20, maintenanceRoad, 1, 0, None)), 290, "testuser")
      newAssets.length should be(1)

      val asset = linearAssetDao.fetchMaintenancesByLinkIds(290, Seq(388562360l)).head
      asset.value should be (Some(maintenanceRoad))
      asset.expired should be (false)
    }
  }

  test("update new maintenanceRoad") {
    val propIns1 = Properties("huoltotie_kayttooikeus", "single_choice", "1")
    val propIns2 = Properties("huoltotie_huoltovastuu", "single_choice", "2")
    val propIns3 = Properties("huoltotie_postinumero", "text", "text prop3")
    val propIns4 = Properties("huoltotie_puh1" , "text", "text prop4")
    val propIns5 = Properties("huoltotie_tiehoitokunta", "text", "text")

    val propIns :Seq[Properties] = List(propIns1, propIns2, propIns3, propIns4, propIns5)
    val maintenanceRoadIns = MaintenanceRoad(propIns)

    val propUpd1 = Properties("huoltotie_kayttooikeus", "single_choice", "4")
    val propUpd2 = Properties("huoltotie_huoltovastuu", "single_choice", "1")
    val propUpd3 = Properties("huoltotie_postinumero", "text",  "text prop3 Update")
    val propUpd4 = Properties("huoltotie_puh1" , "text", "")
    val propUpd5 = Properties("huoltotie_tiehoitokunta", "text", "text")
    val propUpd6 = Properties("huoltotie_puh2" , "text", "text prop puh2")

    val propUpd :Seq[Properties] = List(propUpd1, propUpd2, propUpd3, propUpd4, propUpd5, propUpd6)
    val maintenanceRoadUpd = MaintenanceRoad(propUpd)

    val maintenanceRoadFetch = MaintenanceRoad(propUpd.filterNot(_.publicId == "huoltotie_puh1"))

    runWithRollback {
      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(388562360l, 0, 20, maintenanceRoadIns, 1, 0, None)), 290, "testuser")
      newAssets.length should be(1)

      val updAssets = ServiceWithDao.update(Seq(newAssets.head), maintenanceRoadUpd, "testuser")
      updAssets.length should be(1)

      val asset = linearAssetDao.fetchMaintenancesByLinkIds(290, Seq(388562360l)).filterNot(_.expired).head
      asset.value should be (Some(maintenanceRoadFetch))
      asset.expired should be (false)
    }
  }

  test("Create new prohibition") {
    val prohibition = Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty, null)))
    runWithRollback {
      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(388562360l, 0, 20, prohibition, 1, 0, None)), 190, "testuser")
      newAssets.length should be(1)
      val asset = linearAssetDao.fetchProhibitionsByLinkIds(190, Seq(388562360l)).head
      asset.value should be (Some(prohibition))
      asset.expired should be (false)
    }
  }

  test("Should delete maintenanceRoad asset"){
    val prop1 = Properties("huoltotie_kayttooikeus", "single_choice", "1")
    val prop2 = Properties("huoltotie_huoltovastuu", "single_choice", "2")
    val prop3 = Properties("huoltotie_tiehoitokunta", "text", "text")

    val propertiesSeq :Seq[Properties] = List(prop1, prop2, prop3)

    val maintenanceRoad = MaintenanceRoad(propertiesSeq)
    runWithRollback {
      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(388562360l, 0, 20, maintenanceRoad, 1, 0, None)), 290, "testuser")
      newAssets.length should be(1)
      var asset = linearAssetDao.fetchMaintenancesByIds(290,Set(newAssets.head),false).head
      asset.value should be (Some(maintenanceRoad))
      asset.expired should be (false)

      val assetId : Seq[Long] = List(asset.id)
      val deleted = ServiceWithDao.expire( assetId , "testuser")
      asset = linearAssetDao.fetchMaintenancesByIds(290,Set(newAssets.head),false).head
      asset.expired should be (true)
    }
  }

  test("adjust linear asset to cover whole link when the difference in asset length and link length is less than maximum allowed error") {
    val linearAssets = PassThroughService.getByBoundingBox(30, BoundingRectangle(Point(0.0, 0.0), Point(1.0, 1.0))).head
    linearAssets should have size 1
    linearAssets.map(_.geometry) should be(Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0))))
    linearAssets.map(_.linkId) should be(Seq(1))
    linearAssets.map(_.value) should be(Seq(Some(NumericValue(40000))))
    verify(mockEventBus, times(1))
      .publish("linearAssets:update", ChangeSet(Set.empty[Long], Seq(MValueAdjustment(1, 1, 0.0, 10.0)), Nil, Set.empty[Long]))
  }

  test("Municipality fetch dispatches to dao based on asset type id") {
    when(mockLinearAssetDao.fetchProhibitionsByLinkIds(190, Seq(1l), includeFloating = false)).thenReturn(Nil)
    PassThroughService.getByMunicipality(190, 235)
    verify(mockLinearAssetDao).fetchProhibitionsByLinkIds(190, Seq(1l), includeFloating = false)

    when(mockLinearAssetDao.fetchLinearAssetsByLinkIds(100, Seq(1l), "mittarajoitus")).thenReturn(Nil)
    PassThroughService.getByMunicipality(100, 235)
    verify(mockLinearAssetDao).fetchLinearAssetsByLinkIds(100, Seq(1l), "mittarajoitus")
  }

  test("Separate linear asset") {
    runWithRollback {
      val newLimit = NewLinearAsset(linkId = 388562360, startMeasure = 0, endMeasure = 10, value = NumericValue(1), sideCode = 1, 0, None)
      val assetId = ServiceWithDao.create(Seq(newLimit), 140, "test").head
      val createdId = ServiceWithDao.separate(assetId, Some(NumericValue(2)), Some(NumericValue(3)), "unittest", (i) => Unit)
      val createdLimit = ServiceWithDao.getPersistedAssetsByIds(140, Set(createdId(1))).head
      val oldLimit = ServiceWithDao.getPersistedAssetsByIds(140, Set(createdId.head)).head

      oldLimit.linkId should be (388562360)
      oldLimit.sideCode should be (SideCode.TowardsDigitizing.value)
      oldLimit.value should be (Some(NumericValue(2)))
      oldLimit.modifiedBy should be (Some("unittest"))

      createdLimit.linkId should be (388562360)
      createdLimit.sideCode should be (SideCode.AgainstDigitizing.value)
      createdLimit.value should be (Some(NumericValue(3)))
      createdLimit.createdBy should be (Some("unittest"))
    }
  }

  test("Separate prohibition asset") {
    runWithRollback {
      val newLimit = NewLinearAsset(388562360, 0, 10, Prohibitions(Seq(ProhibitionValue(3, Set.empty, Set.empty))), 1, 0, None)
      val assetId = ServiceWithDao.create(Seq(newLimit), 190, "test").head
      val prohibitionA = Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty, null)))
      val prohibitionB = Prohibitions(Seq(ProhibitionValue(5, Set.empty, Set(1, 2), null)))

      ServiceWithDao.separate(assetId, Some(prohibitionA), Some(prohibitionB), "unittest", (i) => Unit)

      val limits = linearAssetDao.fetchProhibitionsByLinkIds(190, Seq(388562360))
      val oldLimit = limits.find(_.id == assetId).get
      oldLimit.linkId should be (388562360)
      oldLimit.sideCode should be (SideCode.TowardsDigitizing.value)
      oldLimit.value should be (Some(prohibitionA))
      oldLimit.modifiedBy should be (Some("unittest"))

      val createdLimit = limits.find(_.id != assetId).get
      createdLimit.linkId should be (388562360)
      createdLimit.sideCode should be (SideCode.AgainstDigitizing.value)
      createdLimit.value should be (Some(prohibitionB))
      createdLimit.createdBy should be (Some("unittest"))
    }
  }

  test("Separate with empty value towards digitization") {
    runWithRollback {
      val newLimit = NewLinearAsset(388562360, 0, 10, NumericValue(1), 1, 0, None)
      val assetId = ServiceWithDao.create(Seq(newLimit), 140, "test").head
      val createdId = ServiceWithDao.separate(assetId, None, Some(NumericValue(3)), "unittest", (i) => Unit).filter(_ != assetId).head
      val createdLimit = ServiceWithDao.getPersistedAssetsByIds(140, Set(createdId)).head
      val oldLimit = ServiceWithDao.getPersistedAssetsByIds(140, Set(assetId)).head

      oldLimit.linkId should be (388562360)
      oldLimit.sideCode should be (SideCode.TowardsDigitizing.value)
      oldLimit.expired should be (true)
      oldLimit.modifiedBy should be (Some("unittest"))

      createdLimit.linkId should be (388562360)
      createdLimit.sideCode should be (SideCode.AgainstDigitizing.value)
      createdLimit.value should be (Some(NumericValue(3)))
      createdLimit.expired should be (false)
      createdLimit.createdBy should be (Some("unittest"))
    }
  }

  test("Separate with empty value against digitization") {
    runWithRollback {
      val newLimit = NewLinearAsset(388562360, 0, 10, NumericValue(1), 1, 0, None)
      val assetId = ServiceWithDao.create(Seq(newLimit), 140, "test").head

      val newAssetIdAfterUpdate = ServiceWithDao.separate(assetId, Some(NumericValue(2)), None, "unittest", (i) => Unit)
      newAssetIdAfterUpdate.size should be(1)

      val oldLimit = ServiceWithDao.getPersistedAssetsByIds(140, Set(newAssetIdAfterUpdate.head)).head

      oldLimit.linkId should be (388562360)
      oldLimit.sideCode should be (SideCode.TowardsDigitizing.value)
      oldLimit.value should be (Some(NumericValue(2)))
      oldLimit.expired should be (false)
      oldLimit.modifiedBy should be (Some("unittest"))

    }
  }

  test("Split linear asset") {
    runWithRollback {
      val newLimit = NewLinearAsset(388562360, 0, 10, NumericValue(1), 1, 0, None)
      val assetId = ServiceWithDao.create(Seq(newLimit), 140, "test").head

      val ids = ServiceWithDao.split(assetId, 2.0, Some(NumericValue(2)), Some(NumericValue(3)), "unittest", (i) => Unit)

      val createdId = ids(1)
      val createdLimit = ServiceWithDao.getPersistedAssetsByIds(140, Set(createdId)).head
      val oldLimit = ServiceWithDao.getPersistedAssetsByIds(140, Set(ids.head)).head

      oldLimit.linkId should be (388562360)
      oldLimit.sideCode should be (SideCode.BothDirections.value)
      oldLimit.value should be (Some(NumericValue(2)))
      oldLimit.modifiedBy should be (Some("unittest"))
      oldLimit.startMeasure should be (2.0)
      oldLimit.endMeasure should be (10.0)

      createdLimit.linkId should be (388562360)
      createdLimit.sideCode should be (SideCode.BothDirections.value)
      createdLimit.value should be (Some(NumericValue(3)))
      createdLimit.createdBy should be (Some("unittest"))
      createdLimit.startMeasure should be (0.0)
      createdLimit.endMeasure should be (2.0)
    }
  }

  test("Split prohibition") {
    runWithRollback {
      val newProhibition = NewLinearAsset(388562360, 0, 10, Prohibitions(Seq(ProhibitionValue(3, Set.empty, Set.empty))), 1, 0, None)
      val assetId = ServiceWithDao.create(Seq(newProhibition), 190, "test").head
      val prohibitionA = Prohibitions(Seq(ProhibitionValue(4, Set.empty, Set.empty, null)))
      val prohibitionB = Prohibitions(Seq(ProhibitionValue(5, Set.empty, Set(1, 2), null)))

      ServiceWithDao.split(assetId, 6.0, Some(prohibitionA), Some(prohibitionB), "unittest", (i) => Unit)

      val prohibitions = linearAssetDao.fetchProhibitionsByLinkIds(190, Seq(388562360))
      val oldProhibition = prohibitions.find(_.id == assetId).get
      oldProhibition.linkId should be (388562360)
      oldProhibition.sideCode should be (SideCode.BothDirections.value)
      oldProhibition.value should be (Some(prohibitionA))
      oldProhibition.modifiedBy should be (Some("unittest"))
      oldProhibition.startMeasure should be (0.0)
      oldProhibition.endMeasure should be (6.0)

      val createdProhibition = prohibitions.find(_.id != assetId).get
      createdProhibition.linkId should be (388562360)
      createdProhibition.sideCode should be (SideCode.BothDirections.value)
      createdProhibition.value should be (Some(prohibitionB))
      createdProhibition.createdBy should be (Some("unittest"))
      createdProhibition.startMeasure should be (6.0)
      createdProhibition.endMeasure should be (10.0)
    }
  }

  test("Separation should call municipalityValidation") {
    def failingMunicipalityValidation(code: Int): Unit = { throw new IllegalArgumentException }
    runWithRollback {
      val newLimit = NewLinearAsset(388562360, 0, 10, NumericValue(1), 1, 0, None)
      val assetId = ServiceWithDao.create(Seq(newLimit), 140, "test").head
      intercept[IllegalArgumentException] {
        ServiceWithDao.separate(assetId, Some(NumericValue(1)), Some(NumericValue(2)), "unittest", failingMunicipalityValidation)
      }
    }
  }

  // Tests for DROTH-76 Automatics for fixing linear assets after geometry update (using VVH change info data)

  test("Should expire assets from deleted road links through the actor")
  {
    val oldLinkId1 = 5001
    val oldLinkId2 = 5002
    val oldLinkId3 = 5003
    val newLinkId = 6000
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val assetTypeId = 100 // lit roads

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
    val linearAssetService = new LinearAssetService(mockRoadLinkService, mockEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val newRoadLinks = Seq(RoadLink(newLinkId, List(Point(0.0, 0.0), Point(25.0, 0.0)), 25.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))))

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId1), Some(newLinkId), 12345, 1, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId2), Some(newLinkId), 12345, 2, Some(0), Some(10), Some(10), Some(20), 144000000),
      ChangeInfo(Some(oldLinkId3), Some(newLinkId), 12345, 2, Some(0), Some(5), Some(20), Some(25), 144000000))

    OracleDatabase.withDynTransaction {
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES (1, $oldLinkId1, 0.0, 10.0, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values (1,$assetTypeId, TO_TIMESTAMP('2014-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX1')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (1,1)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values (1,1,(select id from property where public_id = 'mittarajoitus'),1)""".execute

      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES (2, $oldLinkId2, 0, 10.0, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values (2,$assetTypeId, TO_TIMESTAMP('2016-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX2')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (2,2)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values (2,2,(select id from property where public_id = 'mittarajoitus'),1)""".execute

      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES (3, $oldLinkId3, 0, 5.0, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values (3,$assetTypeId, TO_TIMESTAMP('2015-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX3')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (3,3)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values (3,3,(select id from property where public_id = 'mittarajoitus'),1)""".execute

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[Int])).thenReturn((newRoadLinks, changeInfo))

      linearAssetService.getByMunicipality(assetTypeId, municipalityCode)

      val captor = ArgumentCaptor.forClass(classOf[ChangeSet])
      verify(mockEventBus, times(1)).publish(org.mockito.Matchers.eq("linearAssets:update"), captor.capture())
      captor.getValue.expiredAssetIds should be (Set(1,2,3))

      dynamicSession.rollback()
    }
  }

  test("Should map linear asset (lit road) of old link to three new road links, asset covers the whole road link") {

    // Divided road link (change types 5 and 6)

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId = 5000
    val newLinkId1 = 6001
    val newLinkId2 = 6002
    val newLinkId3 = 6003
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 100
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val oldRoadLink = RoadLink(oldLinkId, List(Point(0.0, 0.0), Point(25.0, 0.0)), 25.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    val newRoadLinks = Seq(RoadLink(newLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes),
      RoadLink(newLinkId2, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes),
      RoadLink(newLinkId3, List(Point(0.0, 0.0), Point(5.0, 0.0)), 5.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes))

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId), Some(newLinkId1), 12345, 5, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId), Some(newLinkId2), 12346, 6, Some(10), Some(20), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId), Some(newLinkId3), 12347, 6, Some(20), Some(25), Some(0), Some(5), 144000000))

    OracleDatabase.withDynTransaction {
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES (1, $oldLinkId, 0.0, 25.0, 1)""".execute
      sqlu"""insert into asset (id, asset_type_id) values (1,$assetTypeId)""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (1,1)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values (1,1,(select id from property where public_id = 'mittarajoitus'),1)""".execute

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(oldRoadLink), Nil))
      val before = service.getByBoundingBox(assetTypeId, boundingBox).toList

      before.length should be (1)
      before.head.map(_.value should be (Some(NumericValue(1))))
      before.head.map(_.sideCode should be (SideCode.BothDirections))
      before.head.map(_.startMeasure should be (0))
      before.head.map(_.endMeasure should be (25))

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((newRoadLinks, changeInfo))
      val after = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

      after.length should be (3)
//      after.foreach(println)
      after.foreach(_.value should be (Some(NumericValue(1))))
      after.foreach(_.sideCode should be (SideCode.BothDirections))

      val afterByLinkId = after.groupBy(_.linkId)
      val linearAsset1 = afterByLinkId(newLinkId1)
      linearAsset1.length should be (1)
      linearAsset1.head.startMeasure should be (0)
      linearAsset1.head.endMeasure should be (10)
      val linearAsset2 = afterByLinkId(newLinkId2)
      linearAsset2.length should be (1)
      linearAsset2.head.startMeasure should be (0)
      linearAsset2.head.endMeasure should be (10)
      val linearAsset3 = afterByLinkId(newLinkId3)
      linearAsset3.length should be (1)
      linearAsset3.head.startMeasure should be (0)
      linearAsset3.head.endMeasure should be (5)

      linearAsset1.forall(a => a.vvhTimeStamp > 0L) should be (true)
      linearAsset2.forall(a => a.vvhTimeStamp > 0L) should be (true)
      linearAsset3.forall(a => a.vvhTimeStamp > 0L) should be (true)
      dynamicSession.rollback()
    }
  }

  test("Should map linear assets (lit road) of old link to three new road links, asset covers part of road link") {

    // Divided road link (change types 5 and 6)

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId = 5000
    val newLinkId1 = 6001
    val newLinkId2 = 6002
    val newLinkId3 = 6003
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 100
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val oldRoadLink = RoadLink(oldLinkId, List(Point(0.0, 0.0), Point(25.0, 0.0)), 25.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    val newRoadLinks = Seq(RoadLink(newLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes),
      RoadLink(newLinkId2, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes),
      RoadLink(newLinkId3, List(Point(0.0, 0.0), Point(5.0, 0.0)), 5.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes))

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId), Some(newLinkId1), 12345, 5, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId), Some(newLinkId2), 12346, 6, Some(10), Some(20), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId), Some(newLinkId3), 12347, 6, Some(20), Some(25), Some(0), Some(5), 144000000))

    OracleDatabase.withDynTransaction {
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES (1, $oldLinkId, 5.0, 15.0, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id, asset_type_id) values (1,$assetTypeId)""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (1,1)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values (1,1,(select id from property where public_id = 'mittarajoitus'), 1)""".execute

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(oldRoadLink), Nil))
      val before = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

      before.length should be (3)
      before.foreach(_.sideCode should be (SideCode.BothDirections))
      before.foreach(_.linkId should be (oldLinkId))

      val beforeByValue = before.groupBy(_.value)
      beforeByValue(Some(NumericValue(1))).length should be (1)
      beforeByValue(None).length should be (2)

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((newRoadLinks, changeInfo))
      val after = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

      after.length should be (5)
      after.foreach(_.sideCode should be (SideCode.BothDirections))

      val afterByLinkId = after.groupBy(_.linkId)

      val linearAssets1 = afterByLinkId(newLinkId1)
      linearAssets1.length should be (2)
      linearAssets1.filter(_.startMeasure == 0.0).head.value should be (None)
      linearAssets1.filter(_.startMeasure == 5.0).head.value should be (Some(NumericValue(1)))
      val linearAssets2 = afterByLinkId(newLinkId2)
      linearAssets2.length should be (2)
      linearAssets2.filter(_.startMeasure == 0.0).head.value should be (Some(NumericValue(1)))
      linearAssets2.filter(_.startMeasure == 5.0).head.value should be (None)
      val linearAssets3 = afterByLinkId(newLinkId3)
      linearAssets3.length should be (1)
      linearAssets3.filter(_.startMeasure == 0.0).head.value should be (None)

      dynamicSession.rollback()
    }
  }

  test("Should map linear assets (lit road) of three old links to one new link") {

    // Combined road link (change types 1 and 2)

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId1 = 5001
    val oldLinkId2 = 5002
    val oldLinkId3 = 5003
    val newLinkId = 6000
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 100
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val oldRoadLinks = Seq(RoadLink(oldLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes),
      RoadLink(oldLinkId2, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes),
      RoadLink(oldLinkId3, List(Point(0.0, 0.0), Point(5.0, 0.0)), 5.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes))

    val newRoadLink = RoadLink(newLinkId, List(Point(0.0, 0.0), Point(25.0, 0.0)), 25.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId1), Some(newLinkId), 12345, 1, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId2), Some(newLinkId), 12345, 2, Some(0), Some(10), Some(10), Some(20), 144000000),
      ChangeInfo(Some(oldLinkId3), Some(newLinkId), 12345, 2, Some(0), Some(5), Some(20), Some(25), 144000000))

    OracleDatabase.withDynTransaction {
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES (1, $oldLinkId1, 0.0, 10.0, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values (1,$assetTypeId, TO_TIMESTAMP('2014-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX1')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (1,1)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values (1,1,(select id from property where public_id = 'mittarajoitus'),1)""".execute
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES (2, $oldLinkId2, 0, 10.0, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values (2,$assetTypeId, TO_TIMESTAMP('2016-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX2')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (2,2)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values (2,2,(select id from property where public_id = 'mittarajoitus'),1)""".execute
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES (3, $oldLinkId3, 0, 5.0, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values (3,$assetTypeId, TO_TIMESTAMP('2015-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX3')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (3,3)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values (3,3,(select id from property where public_id = 'mittarajoitus'),1)""".execute

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((oldRoadLinks, Nil))
      val before = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

      before.length should be (3)
      before.foreach(_.value should be (Some(NumericValue(1))))
      before.foreach(_.sideCode should be (SideCode.BothDirections))

      val beforeByLinkId = before.groupBy(_.linkId)
      val linearAssets1 = beforeByLinkId(oldLinkId1)
      linearAssets1.length should be (1)
      linearAssets1.head.startMeasure should be (0)
      linearAssets1.head.endMeasure should be (10)
      val linearAssets2 = beforeByLinkId(oldLinkId2)
      linearAssets2.length should be (1)
      linearAssets2.head.startMeasure should be (0)
      linearAssets2.head.endMeasure should be (10)
      val linearAssets3 = beforeByLinkId(oldLinkId3)
      linearAssets3.head.startMeasure should be (0)
      linearAssets3.head.endMeasure should be (5)


      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink), changeInfo))
      val after = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

//      after.foreach(println)
      after.length should be(1)
      after.head.value should be(Some(NumericValue(1)))
      after.head.sideCode should be (SideCode.BothDirections)
      after.head.startMeasure should be (0)
      after.head.endMeasure should be (25)
      after.head.modifiedBy should be(Some("KX2"))

      val formatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")
      val latestModifiedDate = DateTime.parse("2016-02-17 10:03:51.047483", formatter)
      after.head.modifiedDateTime should be(Some(latestModifiedDate))

      dynamicSession.rollback()
    }
  }

  test("Should map linear asset with textual value of old link to three new road links, asset covers the whole road link") {

    // Divided road link (change types 5 and 6)

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId = 5000
    val newLinkId1 = 6001
    val newLinkId2 = 6002
    val newLinkId3 = 6003
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 260 // european road
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val oldRoadLink = RoadLink(oldLinkId, List(Point(0.0, 0.0), Point(25.0, 0.0)), 25.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    val newRoadLinks = Seq(RoadLink(newLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes),
      RoadLink(newLinkId2, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes),
      RoadLink(newLinkId3, List(Point(0.0, 0.0), Point(5.0, 0.0)), 5.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes))

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId), Some(newLinkId1), 12345, 5, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId), Some(newLinkId2), 12346, 6, Some(10), Some(20), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId), Some(newLinkId3), 12347, 6, Some(20), Some(25), Some(0), Some(5), 144000000))

    OracleDatabase.withDynTransaction {
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) values (1, $oldLinkId, 0, 25.000, 1)""".execute
      sqlu"""insert into asset (id, asset_type_id, created_date, created_by) values (1, $assetTypeId, SYSDATE, 'dr2_test_data')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (1, 1)""".execute
      sqlu"""insert into text_property_value(id, asset_id, property_id, value_fi, created_date, created_by) values (1, 1, (select id from property where public_id='eurooppatienumero'), 'E666' || chr(10) || 'E667', sysdate, 'dr2_test_data')""".execute

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(oldRoadLink), Nil))
      val before = service.getByBoundingBox(assetTypeId, boundingBox).toList

      before.length should be (1)
      before.head.map(_.value should be (Some(TextualValue("E666\nE667"))))
      before.head.map(_.sideCode should be (SideCode.BothDirections))
      before.head.map(_.startMeasure should be (0))
      before.head.map(_.endMeasure should be (25))

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((newRoadLinks, changeInfo))
      val after = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

      after.length should be (3)
      after.foreach(_.value should be (Some(TextualValue("E666\nE667"))))
      after.foreach(_.sideCode should be (SideCode.BothDirections))

      val afterByLinkId = after.groupBy(_.linkId)
      val linearAsset1 = afterByLinkId(newLinkId1)
      linearAsset1.length should be (1)
      linearAsset1.head.startMeasure should be (0)
      linearAsset1.head.endMeasure should be (10)
      val linearAsset2 = afterByLinkId(newLinkId2)
      linearAsset2.length should be (1)
      linearAsset2.head.startMeasure should be (0)
      linearAsset2.head.endMeasure should be (10)
      val linearAsset3 = afterByLinkId(newLinkId3)
      linearAsset3.length should be (1)
      linearAsset3.head.startMeasure should be (0)
      linearAsset3.head.endMeasure should be (5)

      dynamicSession.rollback()
    }
  }

  test("Should map winter speed limits of two old links to one new link") {

    // Combined road link (change types 1 and 2)

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId1 = 5001
    val oldLinkId2 = 5002
    val newLinkId = 6000
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 180
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val oldRoadLinks = Seq(RoadLink(oldLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes),
      RoadLink(oldLinkId2, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes))

    val newRoadLink = RoadLink(newLinkId, List(Point(0.0, 0.0), Point(25.0, 0.0)), 25.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId1), Some(newLinkId), 12345, 1, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId2), Some(newLinkId), 12345, 2, Some(0), Some(10), Some(10), Some(20), 144000000))

    OracleDatabase.withDynTransaction {
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES (1, $oldLinkId1, 0.0, 10.0, ${SideCode.AgainstDigitizing.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values (1,$assetTypeId, TO_TIMESTAMP('2014-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX1')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (1,1)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values (1,1,(select id from property where public_id = 'mittarajoitus'),40)""".execute
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES (2, $oldLinkId1, 0, 10.0, ${SideCode.TowardsDigitizing.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values (2,$assetTypeId, TO_TIMESTAMP('2016-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX2')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (2,2)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values (2,2,(select id from property where public_id = 'mittarajoitus'),50)""".execute
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES (3, $oldLinkId2, 0, 5.0, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values (3,$assetTypeId, TO_TIMESTAMP('2015-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX3')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (3,3)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values (3,3,(select id from property where public_id = 'mittarajoitus'),60)""".execute

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((oldRoadLinks, Nil))
      val before = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

      before.length should be (4)
      before.count(_.value.nonEmpty) should be (3)

      val beforeByLinkId = before.groupBy(_.linkId)
      val linearAssets1 = beforeByLinkId(oldLinkId1)
      linearAssets1.length should be (2)
      linearAssets1.head.startMeasure should be (0)
      linearAssets1.head.endMeasure should be (10)
      val linearAssets2 = beforeByLinkId(oldLinkId2)
      linearAssets2.length should be (2)
      linearAssets2.filter(l => l.id > 0).head.startMeasure should be (0)
      linearAssets2.filter(l => l.id > 0).head.endMeasure should be (5)

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink), changeInfo))
      val after = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten
//      after.foreach(println)
      after.length should be(4)
      after.count(_.value.nonEmpty) should be (3)
      after.count(l => l.startMeasure == 0.0 && l.endMeasure == 10.0) should be (2)
      after.count(l => l.startMeasure == 10.0 && l.endMeasure == 15.0 && l.value.get.equals(NumericValue(60))) should be (1)
      after.count(l => l.startMeasure == 15.0 && l.endMeasure == 25.0 && l.value.isEmpty) should be (1)

      dynamicSession.rollback()
    }
  }

  test("Should map hazmat prohibitions of two old links to one new link") {

    // Combined road link (change types 1 and 2)

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId1 = 5001
    val oldLinkId2 = 5002
    val newLinkId = 6000
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 210
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val oldRoadLinks = Seq(RoadLink(oldLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes),
      RoadLink(oldLinkId2, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes))

    val newRoadLink = RoadLink(newLinkId, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId1), Some(newLinkId), 12345, 1, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId2), Some(newLinkId), 12345, 2, Some(0), Some(10), Some(10), Some(20), 144000000))

    OracleDatabase.withDynTransaction {
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES (1, $oldLinkId1, 0.0, 10.0, ${SideCode.AgainstDigitizing.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values (1,$assetTypeId, TO_TIMESTAMP('2014-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX1')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (1,1)""".execute
      sqlu"""insert into prohibition_value (id, asset_id, type) values (1,1,24)""".execute
      sqlu"""insert into prohibition_validity_period (id, prohibition_value_id, type, start_hour, end_hour) values (1,1,1,11,12)""".execute
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES (2, $oldLinkId1, 0, 10.0, ${SideCode.TowardsDigitizing.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values (2,$assetTypeId, TO_TIMESTAMP('2016-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX2')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (2,2)""".execute
      sqlu"""insert into prohibition_value (id, asset_id, type) values (2,2,25)""".execute
      sqlu"""insert into prohibition_validity_period (id, prohibition_value_id, type, start_hour, end_hour) values (2,2,2,12,13)""".execute
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES (3, $oldLinkId2, 0, 5.0, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values (3,$assetTypeId, TO_TIMESTAMP('2015-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX3')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (3,3)""".execute
      sqlu"""insert into prohibition_value (id, asset_id, type) values (3,3,24)""".execute

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((oldRoadLinks, Nil))
      val before = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

      before.length should be (4)
      before.count(_.value.nonEmpty) should be (3)

      val beforeByLinkId = before.groupBy(_.linkId)
      val linearAssets1 = beforeByLinkId(oldLinkId1)
      linearAssets1.length should be (2)
      linearAssets1.head.startMeasure should be (0)
      linearAssets1.head.endMeasure should be (10)
      val linearAssets2 = beforeByLinkId(oldLinkId2)
      linearAssets2.length should be (2)
      linearAssets2.filter(l => l.id > 0).head.startMeasure should be (0)
      linearAssets2.filter(l => l.id > 0).head.endMeasure should be (5)

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink), changeInfo))
      val after = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

//      after.foreach(println)
      after.length should be(4)
      after.count(_.value.nonEmpty) should be (3)

      val linearAssetBothDirections = after.filter(p => (p.sideCode == SideCode.BothDirections) && p.value.nonEmpty).head
      val prohibitionBothDirections = Prohibitions(Seq(ProhibitionValue(24, Set.empty, Set.empty, null)))
      val linearAssetTowardsDigitizing = after.filter(p => p.sideCode == SideCode.TowardsDigitizing).head
      val prohibitionTowardsDigitizing = Prohibitions(Seq(ProhibitionValue(25, Set(ValidityPeriod(12, 13, Saturday)), Set.empty, null)))
      val linearAssetAgainstDigitizing = after.filter(p => p.sideCode == SideCode.AgainstDigitizing).head
      val prohibitionAgainstDigitizing = Prohibitions(Seq(ProhibitionValue(24, Set(ValidityPeriod(11, 12, Weekday)), Set.empty, null)))

      linearAssetBothDirections.value should be (Some(prohibitionBothDirections))
      linearAssetTowardsDigitizing.value should be (Some(prohibitionTowardsDigitizing))
      linearAssetAgainstDigitizing.value should be (Some(prohibitionAgainstDigitizing))

      dynamicSession.rollback()
    }
  }
  test("Should map vehicle prohibition of two old links to one new link") {

    // Combined road link (change types 1 and 2)

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId1 = 5001
    val oldLinkId2 = 5002
    val newLinkId = 6000
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 190
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val oldRoadLinks = Seq(RoadLink(oldLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes),
      RoadLink(oldLinkId2, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes))

    val newRoadLink = RoadLink(newLinkId, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId1), Some(newLinkId), 12345, 1, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(Some(oldLinkId2), Some(newLinkId), 12345, 2, Some(0), Some(10), Some(10), Some(20), 144000000))

    OracleDatabase.withDynTransaction {
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES (1, $oldLinkId1, 0.0, 10.0, ${SideCode.AgainstDigitizing.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values (1,$assetTypeId, TO_TIMESTAMP('2014-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX1')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (1,1)""".execute
      sqlu"""insert into prohibition_value (id, asset_id, type) values (1,1,24)""".execute
      sqlu"""insert into prohibition_validity_period (id, prohibition_value_id, type, start_hour, end_hour) values (1,1,1,11,12)""".execute
      sqlu"""insert into prohibition_exception (id, prohibition_value_id, type) values (600010, 1, 10)""".execute

      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES (2, $oldLinkId1, 0, 10.0, ${SideCode.TowardsDigitizing.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values (2,$assetTypeId, TO_TIMESTAMP('2016-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX2')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (2,2)""".execute
      sqlu"""insert into prohibition_value (id, asset_id, type) values (2,2,25)""".execute
      sqlu"""insert into prohibition_validity_period (id, prohibition_value_id, type, start_hour, end_hour) values (2,2,2,12,13)""".execute
      sqlu"""insert into prohibition_exception (id, prohibition_value_id, type) values (600011, 2, 10)""".execute

      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES (3, $oldLinkId2, 0, 5.0, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values (3,$assetTypeId, TO_TIMESTAMP('2015-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX3')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (3,3)""".execute
      sqlu"""insert into prohibition_value (id, asset_id, type) values (3,3,24)""".execute
      sqlu"""insert into prohibition_exception (id, prohibition_value_id, type) values (600012, 3, 10)""".execute


      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((oldRoadLinks, Nil))
      val before = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

      before.length should be (4)
      before.count(_.value.nonEmpty) should be (3)

      val beforeByLinkId = before.groupBy(_.linkId)
      val linearAssets1 = beforeByLinkId(oldLinkId1)
      linearAssets1.length should be (2)
      linearAssets1.head.startMeasure should be (0)
      linearAssets1.head.endMeasure should be (10)

      val linearAssets2 = beforeByLinkId(oldLinkId2)
      linearAssets2.length should be (2)
      linearAssets2.filter(l => l.id > 0).head.startMeasure should be (0)
      linearAssets2.filter(l => l.id > 0).head.endMeasure should be (5)

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink), changeInfo))
      val after = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten
//      after.foreach(println)
      after.length should be(4)
      after.count(_.value.nonEmpty) should be (3)

      val linearAssetBothDirections = after.filter(p => (p.sideCode == SideCode.BothDirections) && p.value.nonEmpty).head
      val prohibitionBothDirections = Prohibitions(Seq(ProhibitionValue(24, Set.empty, Set(10), null)))
      val linearAssetTowardsDigitizing = after.filter(p => p.sideCode == SideCode.TowardsDigitizing).head
      val prohibitionTowardsDigitizing = Prohibitions(Seq(ProhibitionValue(25, Set(ValidityPeriod(12, 13, Saturday)), Set(10), null)))
      val linearAssetAgainstDigitizing = after.filter(p => p.sideCode == SideCode.AgainstDigitizing).head
      val prohibitionAgainstDigitizing = Prohibitions(Seq(ProhibitionValue(24, Set(ValidityPeriod(11, 12, Weekday)), Set(10), null)))

      linearAssetBothDirections.value should be (Some(prohibitionBothDirections))
      linearAssetTowardsDigitizing.value should be (Some(prohibitionTowardsDigitizing))
      linearAssetAgainstDigitizing.value should be (Some(prohibitionAgainstDigitizing))

      dynamicSession.rollback()
    }
  }

  test("Should not create new assets on update") {
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId1 = 1234
    val oldLinkId2 = 1235
    val assetTypeId = 100
    val vvhTimeStamp = 14440000
    OracleDatabase.withDynTransaction {
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES (1, $oldLinkId1, 0.0, 10.0, ${SideCode.AgainstDigitizing.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values (1,$assetTypeId, TO_TIMESTAMP('2014-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX1')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (1,1)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values (1,1,(select id from property where public_id = 'mittarajoitus'),40)""".execute
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES (2, $oldLinkId1, 0, 10.0, ${SideCode.TowardsDigitizing.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values (2,$assetTypeId, TO_TIMESTAMP('2016-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX2')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (2,2)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values (2,2,(select id from property where public_id = 'mittarajoitus'),50)""".execute
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES (3, $oldLinkId2, 0, 5.0, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values (3,$assetTypeId, TO_TIMESTAMP('2015-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX3')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (3,3)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values (3,3,(select id from property where public_id = 'mittarajoitus'),60)""".execute

      val original = service.getPersistedAssetsByIds(assetTypeId, Set(1L)).head
      val projectedLinearAssets = Seq(original.copy(startMeasure = 0.1, endMeasure = 10.1, sideCode = 1, vvhTimeStamp = vvhTimeStamp))

      service.persistProjectedLinearAssets(projectedLinearAssets)
      val all = service.dao.fetchLinearAssetsByLinkIds(assetTypeId, Seq(oldLinkId1, oldLinkId2), "mittarajoitus")
      all.size should be (3)
      val persisted = service.getPersistedAssetsByIds(assetTypeId, Set(1L))
      persisted.size should be (1)
      val head = persisted.head
      head.id should be (original.id)
      head.vvhTimeStamp should be (vvhTimeStamp)
      head.startMeasure should be (0.1)
      head.endMeasure should be (10.1)
      head.expired should be (false)
      dynamicSession.rollback()
    }
  }

  test("Should not create prohibitions on actor update") {
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId1 = 1234
    val oldLinkId2 = 1235
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 190
    val vvhTimeStamp = 14440000

    OracleDatabase.withDynTransaction {
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES (1, $oldLinkId1, 0.0, 10.0, ${SideCode.AgainstDigitizing.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values (1,$assetTypeId, TO_TIMESTAMP('2014-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX1')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (1,1)""".execute
      sqlu"""insert into prohibition_value (id, asset_id, type) values (1,1,24)""".execute
      sqlu"""insert into prohibition_validity_period (id, prohibition_value_id, type, start_hour, end_hour) values (1,1,1,11,12)""".execute
      sqlu"""insert into prohibition_exception (id, prohibition_value_id, type) values (600010, 1, 10)""".execute

      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES (2, $oldLinkId1, 0, 10.0, ${SideCode.TowardsDigitizing.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values (2,$assetTypeId, TO_TIMESTAMP('2016-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX2')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (2,2)""".execute
      sqlu"""insert into prohibition_value (id, asset_id, type) values (2,2,25)""".execute
      sqlu"""insert into prohibition_validity_period (id, prohibition_value_id, type, start_hour, end_hour) values (2,2,2,12,13)""".execute
      sqlu"""insert into prohibition_exception (id, prohibition_value_id, type) values (600011, 2, 10)""".execute

      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES (3, $oldLinkId2, 0, 5.0, ${SideCode.BothDirections.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values (3,$assetTypeId, TO_TIMESTAMP('2015-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX3')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (3,3)""".execute
      sqlu"""insert into prohibition_value (id, asset_id, type) values (3,3,24)""".execute
      sqlu"""insert into prohibition_exception (id, prohibition_value_id, type) values (600012, 3, 10)""".execute


      val original = service.getPersistedAssetsByIds(assetTypeId, Set(1L)).head
      val projectedProhibitions = Seq(original.copy(startMeasure = 0.1, endMeasure = 10.1, sideCode = 1, vvhTimeStamp = vvhTimeStamp))

      service.persistProjectedLinearAssets(projectedProhibitions)
      val all = service.dao.fetchProhibitionsByIds(assetTypeId, Set(1,2,3), false)
      all.size should be (3)
      val persisted = service.getPersistedAssetsByIds(assetTypeId, Set(1L))
      persisted.size should be (1)
      val head = persisted.head
      head.id should be (original.id)
      head.vvhTimeStamp should be (vvhTimeStamp)
      head.startMeasure should be (0.1)
      head.endMeasure should be (10.1)
      head.expired should be (false)

      dynamicSession.rollback()
    }
  }

  test("Should extend vehicle prohibition on road extension") {

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId1 = 6000
    val newLinkId = 6000
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 190
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val oldRoadLinks = Seq(RoadLink(oldLinkId1, List(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes))

    val newRoadLink = RoadLink(newLinkId, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    val changeInfo = Seq(ChangeInfo(Some(oldLinkId1), Some(newLinkId), 12345, 3, Some(0), Some(10), Some(0), Some(10), 144000000),
      ChangeInfo(None, Some(newLinkId), 12345, 4, None, None, Some(10), Some(20), 144000000))

    OracleDatabase.withDynTransaction {
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES (1, $oldLinkId1, 0.0, 10.0, ${SideCode.AgainstDigitizing.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values (1,$assetTypeId, TO_TIMESTAMP('2014-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX1')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (1,1)""".execute
      sqlu"""insert into prohibition_value (id, asset_id, type) values (1,1,24)""".execute
      sqlu"""insert into prohibition_validity_period (id, prohibition_value_id, type, start_hour, end_hour) values (1,1,1,11,12)""".execute
      sqlu"""insert into prohibition_exception (id, prohibition_value_id, type) values (600010, 1, 10)""".execute

      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code) VALUES (2, $oldLinkId1, 0, 9.0, ${SideCode.TowardsDigitizing.value})""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values (2,$assetTypeId, TO_TIMESTAMP('2016-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX2')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (2,2)""".execute
      sqlu"""insert into prohibition_value (id, asset_id, type) values (2,2,25)""".execute
      sqlu"""insert into prohibition_validity_period (id, prohibition_value_id, type, start_hour, end_hour) values (2,2,2,12,13)""".execute
      sqlu"""insert into prohibition_exception (id, prohibition_value_id, type) values (600011, 2, 10)""".execute



      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((oldRoadLinks, Nil))
      val before = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

      val beforeByLinkId = before.groupBy(_.linkId)
      val linearAssets1 = beforeByLinkId(oldLinkId1)
      linearAssets1.length should be (3)

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink), changeInfo))
      val after = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

//      after.foreach(println)
      after.length should be(3)
      after.count(_.value.nonEmpty) should be (2)

      val linearAssetTowardsDigitizing = after.filter(p => p.sideCode == SideCode.TowardsDigitizing).head
      val prohibitionTowardsDigitizing = Prohibitions(Seq(ProhibitionValue(25, Set(ValidityPeriod(12, 13, Saturday)), Set(10), null)))
      val linearAssetAgainstDigitizing = after.filter(p => p.sideCode == SideCode.AgainstDigitizing).head
      val prohibitionAgainstDigitizing = Prohibitions(Seq(ProhibitionValue(24, Set(ValidityPeriod(11, 12, Weekday)), Set(10), null)))

      linearAssetTowardsDigitizing.value should be (Some(prohibitionTowardsDigitizing))
      linearAssetAgainstDigitizing.value should be (Some(prohibitionAgainstDigitizing))
      linearAssetAgainstDigitizing.startMeasure should be (0.0)
      linearAssetAgainstDigitizing.endMeasure should be (20.0)

      linearAssetTowardsDigitizing.startMeasure should be (0.0)
      linearAssetTowardsDigitizing.endMeasure should be (9.0)

      dynamicSession.rollback()
    }
  }

  test("pseudo vvh timestamp is correctly created") {
    val vvhClient = new VVHClient("")
    val hours = DateTime.now().getHourOfDay
    val yesterday = vvhClient.createVVHTimeStamp(hours + 1)
    val today = vvhClient.createVVHTimeStamp(hours)

    (today % 24*60*60*1000L) should be (0L)
    (yesterday % 24*60*60*1000L) should be (0L)
    today should be > yesterday
    (yesterday + 24*60*60*1000L) should be (today)
  }

  // Tests for DROTH-4: Paving from VVH

  test("If VVH does not supply a change Information then no new asset should be created.") {
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val newLinkId = 5001
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 110
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val newRoadLink = RoadLink(newLinkId, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink), Nil))
      when(mockLinearAssetDao.fetchLinearAssetsByLinkIds(any[Int], any[Seq[Long]], any[String])).thenReturn(List())

      val createdAsset = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

      val createdAssetData = createdAsset.filter(p => (p.linkId == newLinkId && p.value.isDefined))

      createdAssetData.length should be (0)
    }
  }

  test("Should create new paving assets from vvh roadlinks infromation through the actor") {
    val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, mockEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val linkId = 5001
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 110 //paving asset type
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))
    val vvhTimeStamp = 11121

    val newRoadLink = RoadLink(linkId, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    val changeInfoSeq = Seq(
      ChangeInfo(Some(linkId), Some(linkId), 12345, 1, Some(0), Some(10), Some(0), Some(10), vvhTimeStamp)
    )

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink), changeInfoSeq))
      when(mockLinearAssetDao.fetchLinearAssetsByLinkIds(any[Int], any[Seq[Long]], any[String])).thenReturn(List())

      service.getByBoundingBox(assetTypeId, boundingBox)

      val captor = ArgumentCaptor.forClass(classOf[Seq[PersistedLinearAsset]])
      verify(mockEventBus, times(1)).publish(org.mockito.Matchers.eq("linearAssets:saveProjectedLinearAssets"), captor.capture())

      val linearAssets = captor.getValue

      linearAssets.length should be (1)

      val linearAsset = linearAssets.filter(p => (p.linkId == linkId)).head

      linearAsset.typeId should be (assetTypeId)
      linearAsset.vvhTimeStamp should be (vvhTimeStamp)

    }
  }

  test("Should not create new paving assets and return the existing paving assets when VVH doesn't have change information") {

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }
    val newLinkId = 5001
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 110
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val newRoadLink = RoadLink(newLinkId, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    runWithRollback {

      val newAssetId = ServiceWithDao.create(Seq(NewLinearAsset(newLinkId, 0, 20, NumericValue(1), 1, 0, None)), assetTypeId, "testuser")
      val newAsset = ServiceWithDao.getPersistedAssetsByIds(assetTypeId, newAssetId.toSet)

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink), Nil))
      when(mockLinearAssetDao.fetchLinearAssetsByLinkIds(any[Int], any[Seq[Long]], any[String])).thenReturn(newAsset)

      val existingAssets = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

      val existingAssetData = existingAssets.filter(p => (p.linkId == newLinkId && p.value.isDefined)).head

      existingAssets.length should be (1)
      existingAssetData.typeId should be (assetTypeId)
      existingAssetData.vvhTimeStamp should be (0)
      existingAssetData.value should be (Some(NumericValue(1)))
      existingAssetData.id should be (newAsset.head.id)
    }
  }

  test("Should be created only 1 new paving asset when get 3 roadlink change information from vvh and only 1 roadlink have surfacetype equal 2") {

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }
    val newLinkId2 = 5002
    val newLinkId1 = 5001
    val newLinkId0 = 5000
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 110
    val attributes2 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))
    val attributes1 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(1))
    val attributes0 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(0))
    val vvhTimeStamp = 14440000

    val newRoadLink2 = RoadLink(newLinkId2, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes2)
    val newRoadLink1 = RoadLink(newLinkId1, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes1)
    val newRoadLink0 = RoadLink(newLinkId0, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes0)

    val changeInfoSeq = Seq(ChangeInfo(Some(newLinkId2), Some(newLinkId2), 12345, 1, Some(0), Some(10), Some(0), Some(10), vvhTimeStamp),
      ChangeInfo(Some(newLinkId1), Some(newLinkId1), 12345, 1, Some(0), Some(10), Some(0), Some(10), vvhTimeStamp),
      ChangeInfo(Some(newLinkId0), Some(newLinkId0), 12345, 1, Some(0), Some(10), Some(0), Some(10), vvhTimeStamp)
    )

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink2, newRoadLink1, newRoadLink0), changeInfoSeq))
      when(mockLinearAssetDao.fetchLinearAssetsByLinkIds(any[Int], any[Seq[Long]], any[String])).thenReturn(List())

      val existingAssets = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

      val filteredCreatedAssets = existingAssets.filter(p => (p.linkId == newLinkId2 && p.value.isDefined))

      existingAssets.length should be (3)
      filteredCreatedAssets.length should be (1)
      filteredCreatedAssets.head.typeId should be (assetTypeId)
      filteredCreatedAssets.head.value should be (Some(NumericValue(1)))
      filteredCreatedAssets.head.vvhTimeStamp should be (vvhTimeStamp)
    }
  }

  test("should do anything when change information link id doesn't exists on vvh roadlinks"){
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val newLinkId = 5001
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 110
    val vvhTimeStamp = 11121

    val changeInfoSeq = Seq(
      ChangeInfo(Some(newLinkId), Some(newLinkId), 12345, 1, Some(0), Some(10), Some(0), Some(10), vvhTimeStamp)
    )

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(), changeInfoSeq))
      when(mockLinearAssetDao.fetchLinearAssetsByLinkIds(any[Int], any[Seq[Long]], any[String])).thenReturn(List())

      val createdAsset = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten

      createdAsset.length should be (0)

    }
  }

  test("Should not create paving assets when it's requested a different asset type.") {

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }
    val newLinkId = 5001
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val differentAssetTypeId = 250
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))
    val vvhTimeStamp = 11121

    val newRoadLink = RoadLink(newLinkId, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    val changeInfoSeq = Seq(
      ChangeInfo(Some(newLinkId), Some(newLinkId), 12345, 1, Some(0), Some(10), Some(0), Some(10), vvhTimeStamp)
    )

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink), changeInfoSeq))
      when(mockLinearAssetDao.fetchLinearAssetsByLinkIds(any[Int], any[Seq[Long]], any[String])).thenReturn(List())

      val createdAsset = service.getByBoundingBox(differentAssetTypeId, boundingBox).toList.flatten

      val filteredAssets = createdAsset.filter(p => (p.linkId == newLinkId && p.value.isDefined))

      createdAsset.length should be (1)
      createdAsset.head.typeId should be (differentAssetTypeId)

      filteredAssets.length should be (0)
    }
  }

  test ("If neither OTH or VVH have existing assets and changeInfo then nothing should be created and returned") {
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }
    val newLinkId = 5001
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 110
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val newRoadLink = RoadLink(newLinkId, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink), Nil))
      when(mockLinearAssetDao.fetchLinearAssetsByLinkIds(any[Int], any[Seq[Long]], any[String])).thenReturn(List())

      val createdAsset = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten
      val filteredAssets = createdAsset.filter(p => (p.linkId == newLinkId && p.value.isDefined))

      createdAsset.length should be (1)
      filteredAssets.length should be (0)
    }
  }

  test("Should expire the assets if vvh gives change informations and the roadlink surface type is equal to 1") {

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }
    val newLinkId = 5001
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 110
    val attributes = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(1))
    val vvhTimeStamp = 14440000


    val newRoadLink = RoadLink(newLinkId, List(Point(0.0, 0.0), Point(20.0, 0.0)), 20.0, administrativeClass, functionalClass, trafficDirection, linkType, None, None, attributes)
    val changeInfoSeq = Seq(ChangeInfo(Some(newLinkId), Some(newLinkId), 12345, 1, Some(0), Some(10), Some(0), Some(10), vvhTimeStamp))

    runWithRollback {

      val newAssetId = ServiceWithDao.create(Seq(NewLinearAsset(newLinkId, 0, 20, NumericValue(1), 1, 0, None)), assetTypeId, "testuser")
      val newAsset = ServiceWithDao.getPersistedAssetsByIds(assetTypeId, newAssetId.toSet)

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(newRoadLink), changeInfoSeq))
      when(mockLinearAssetDao.fetchLinearAssetsByLinkIds(any[Int], any[Seq[Long]], any[String])).thenReturn(List(newAsset.head))

      val createdAsset = service.getByBoundingBox(assetTypeId, boundingBox).toList.flatten.filter(_.value.isDefined)

      createdAsset.length should be (0)

    }
  }

  test("Expire OTH Assets and create new assets based on VVH RoadLink data") {
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
      override def vvhClient: VVHClient = mockVVHClient
    }
    val assetTypeId = 110
    val municipalityCode = 564
    val newLinkId1 = 5000
    val newLinkId2 = 5001
    val newLinkId3 = 5002
    val newLinkId4 = 5003
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val attributes1 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(0))
    val attributes2 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(1))
    val attributes3 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))


    //RoadLinks from VVH to compare at the end
    val newRoadLink1 = VVHRoadlink(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)
    val newRoadLink2 = VVHRoadlink(newLinkId2, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes2)
    val newRoadLink3 = VVHRoadlink(newLinkId3, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes3)
    val newRoadLink4 = VVHRoadlink(newLinkId4, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes3)


    runWithRollback {
      val newAssetId1 = ServiceWithDao.create(Seq(NewLinearAsset(newLinkId1, 0, 20, NumericValue(1), 1, 0, None)), assetTypeId, "testuser")
      val newAsset1 = ServiceWithDao.getPersistedAssetsByIds(assetTypeId, newAssetId1.toSet)
      val newAssetId2 = ServiceWithDao.create(Seq(NewLinearAsset(newLinkId2, 0, 20, NumericValue(1), 1, 0, None)), assetTypeId, "testuser")
      val newAsset2 = ServiceWithDao.getPersistedAssetsByIds(assetTypeId, newAssetId2.toSet)
      val newAssetId3 = ServiceWithDao.create(Seq(NewLinearAsset(newLinkId3, 0, 20, NumericValue(1), 1, 0, None)), assetTypeId, "testuser")
      val newAsset3 = ServiceWithDao.getPersistedAssetsByIds(assetTypeId, newAssetId3.toSet)
      val newAssetList = List(newAsset1.head, newAsset2.head,newAsset3.head)

      when(mockRoadLinkService.getVVHRoadLinksF(municipalityCode)).thenReturn(List(newRoadLink1, newRoadLink2, newRoadLink3, newRoadLink4))
      when(mockVVHClient.createVVHTimeStamp(any[Int])).thenReturn(12222L)

      service.expireImportRoadLinksVVHtoOTH(assetTypeId)

      val assetListAfterChanges = ServiceWithDao.dao.fetchLinearAssetsByLinkIds(assetTypeId, Seq(newLinkId1, newLinkId2, newLinkId3, newLinkId4), "mittarajoitus")

      assetListAfterChanges.size should be (2)

      //AssetId1 - Expired
      var assetToVerifyNotExist = assetListAfterChanges.find(p => (p.id == newAssetId1(0).toInt) )
      assetToVerifyNotExist.size should be (0)

      //AssetId2 - Expired
      assetToVerifyNotExist = assetListAfterChanges.find(p => (p.id == newAssetId2(0).toInt) )
      assetToVerifyNotExist.size should be (0)

      //AssetId3 - Expired
      assetToVerifyNotExist = assetListAfterChanges.find(p => (p.id == newAssetId3(0).toInt))
      assetToVerifyNotExist.size should be (0)

      //AssetId3 - Update Asset
      val assetToVerifyUpdated = assetListAfterChanges.find(p => (p.id != newAsset3 && p.linkId == newLinkId3)).get
      assetToVerifyUpdated.id should not be (newAssetId3)
      assetToVerifyUpdated.linkId should be (newLinkId3)
      assetToVerifyUpdated.expired should be (false)

      //AssetId4 - Create Asset
      val assetToVerify = assetListAfterChanges.find(p => (p.linkId == newLinkId4)).get
      assetToVerify.linkId should be (newLinkId4)
      assetToVerify.expired should be (false)
    }
  }

  private def createService() = {
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
      override def vvhClient: VVHClient = mockVVHClient
    }
    service
  }

  private def createRoadLinks(municipalityCode: Int) = {
    val newLinkId1 = 5000
    val newLinkId2 = 5001
    val newLinkId3 = 5002
    val newLinkId4 = 5003
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val attributes1 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(0))
    val attributes2 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(1))
    val attributes3 = Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))

    val geometry = List(Point(0.0, 0.0), Point(20.0, 0.0))
    val newRoadLink1 = RoadLink(newLinkId1, geometry, GeometryUtils.geometryLength(geometry), administrativeClass,
      functionalClass, trafficDirection, linkType, None, None, attributes1)
    val newRoadLink2 = newRoadLink1.copy(linkId=newLinkId2, attributes = attributes2)
    val newRoadLink3 = newRoadLink1.copy(linkId=newLinkId3, attributes = attributes3)
    val newRoadLink4 = newRoadLink1.copy(linkId=newLinkId4, attributes = attributes3)
    List(newRoadLink1, newRoadLink2, newRoadLink3, newRoadLink4)
  }

  private def createChangeInfo(roadLinks: Seq[RoadLink], vvhTimeStamp: Long) = {
    roadLinks.map(rl => ChangeInfo(Some(rl.linkId), Some(rl.linkId), 0L, 1, None, None, None, None, vvhTimeStamp))
  }

  test("Paving asset changes: new roadlinks") {
    val municipalityCode = 564
    val roadLinks = createRoadLinks(municipalityCode)
    val service = createService()

    val assetTypeId = 110

    val changeInfo = createChangeInfo(roadLinks, 11L)
    val (expiredIds, updated) = service.getPavingAssetChanges(Seq(), roadLinks, changeInfo, assetTypeId)
    expiredIds should have size (0)
    updated.forall(_.vvhTimeStamp == 11L) should be (true)
    updated.forall(_.value.isDefined) should be (true)
    updated should have size (2)
  }

  test("Paving asset changes: outdated") {
    def createPaving(id: Long, linkId: Long, value: Option[Value], vvhTimeStamp: Long) = {
      PersistedLinearAsset(id, linkId, SideCode.BothDirections.value,
        value, 0.0, 20.0, None, None, None, None, expired = false, 110, vvhTimeStamp, None)
    }
    val municipalityCode = 564
    val roadLinks = createRoadLinks(municipalityCode)
    val service = createService()

    val assetTypeId = 110
    val newLinkId1 = 5000
    val newLinkId2 = 5001
    val newLinkId3 = 5002
    val newLinkId4 = 5003
    val unpaved1 = createPaving(1, newLinkId1, None, 10L)
    val unpaved2 = createPaving(2, newLinkId2, None, 10L)
    val unpaved3 = createPaving(3, newLinkId3, None, 10L)
    val unpaved4 = createPaving(4, newLinkId4, None, 10L)
    val paved1 = createPaving(1, newLinkId1, Some(NumericValue(1)), 10L)
    val paved2 = createPaving(2, newLinkId2, Some(NumericValue(1)), 10L)
    val paved3 = createPaving(3, newLinkId3, Some(NumericValue(1)), 10L)
    val paved4 = createPaving(4, newLinkId4, Some(NumericValue(1)), 10L)

    val changeInfo = createChangeInfo(roadLinks, 11L)
    val (expiredIds, updated) = service.getPavingAssetChanges(Seq(unpaved1, unpaved2, unpaved3, unpaved4), roadLinks, changeInfo, assetTypeId)
    expiredIds should be (Set(2))
    updated.forall(_.vvhTimeStamp == 11L) should be (true)
//    updated.foreach(println)
    updated.forall(_.value.isDefined) should be (true)
    updated.exists(_.id == 1) should be (false)

    val (expiredIds2, updated2) = service.getPavingAssetChanges(Seq(paved1, paved2, paved3, paved4), roadLinks, changeInfo, assetTypeId)
    expiredIds2 should be (Set(2))
    updated2.forall(_.vvhTimeStamp == 11L) should be (true)
    updated2.exists(_.id == 1) should be (false)
  }

  test("Paving asset changes: override not affected") {
    def createPaving(id: Long, linkId: Long, value: Option[Value], vvhTimeStamp: Long) = {
      PersistedLinearAsset(id, linkId, SideCode.BothDirections.value,
        value, 0.0, 20.0, None, None, None, None, expired = false, 110, vvhTimeStamp, None)
    }
    val municipalityCode = 564
    val roadLinks = createRoadLinks(municipalityCode)
    val service = createService()

    val assetTypeId = 110
    val newLinkId1 = 5000
    val newLinkId2 = 5001
    val newLinkId3 = 5002
    val newLinkId4 = 5003
    val unpaved1 = createPaving(1, newLinkId1, None, 12L)
    val unpaved2 = createPaving(2, newLinkId2, None, 12L)
    val unpaved3 = createPaving(3, newLinkId3, None, 12L)
    val unpaved4 = createPaving(4, newLinkId4, None, 12L)
    val paved1 = createPaving(1, newLinkId1, Some(NumericValue(1)), 12L)
    val paved2 = createPaving(2, newLinkId2, Some(NumericValue(1)), 12L)
    val paved3 = createPaving(3, newLinkId3, Some(NumericValue(1)), 12L)
    val paved4 = createPaving(4, newLinkId4, Some(NumericValue(1)), 12L)

    val changeInfo = createChangeInfo(roadLinks, 11L)
    val (expiredIds, updated) = service.getPavingAssetChanges(Seq(unpaved1, unpaved2, unpaved3, unpaved4), roadLinks, changeInfo, assetTypeId)
    expiredIds should have size (0)
    updated should have size (0)

    val (expiredIds2, updated2) = service.getPavingAssetChanges(Seq(paved1, paved2, paved3, paved4), roadLinks, changeInfo, assetTypeId)
    expiredIds should have size (0)
    updated should have size (0)
  }

  test("Paving asset changes: stability test") {
    def createPaving(id: Long, linkId: Long, value: Option[Value], vvhTimeStamp: Long) = {
      PersistedLinearAsset(id, linkId, SideCode.BothDirections.value,
        value, 0.0, 20.0, None, None, None, None, expired = false, 110, vvhTimeStamp, None)
    }
    val municipalityCode = 564
    val roadLinks = createRoadLinks(municipalityCode)
    val service = createService()

    val assetTypeId = 110
    val newLinkId1 = 5000
    val newLinkId2 = 5001
    val newLinkId3 = 5002
    val newLinkId4 = 5003
    val unpaved1 = createPaving(1, newLinkId1, None, 11L)
    val unpaved2 = createPaving(2, newLinkId2, None, 11L)
    val unpaved3 = createPaving(3, newLinkId3, None, 11L)
    val unpaved4 = createPaving(4, newLinkId4, None, 11L)
    val paved1 = createPaving(1, newLinkId1, Some(NumericValue(1)), 11L)
    val paved2 = createPaving(2, newLinkId2, Some(NumericValue(1)), 11L)
    val paved3 = createPaving(3, newLinkId3, Some(NumericValue(1)), 11L)
    val paved4 = createPaving(4, newLinkId4, Some(NumericValue(1)), 11L)

    val changeInfo = createChangeInfo(roadLinks, 11L)
    val (expiredIds, updated) = service.getPavingAssetChanges(Seq(unpaved1, unpaved2, unpaved3, unpaved4), roadLinks, changeInfo, assetTypeId)
    expiredIds should have size (0)
    updated should have size (0)

    val (expiredIds2, updated2) = service.getPavingAssetChanges(Seq(paved1, paved2, paved3, paved4), roadLinks, changeInfo, assetTypeId)
    expiredIds should have size (0)
    updated should have size (0)
  }
  test("Should extend traffic count on segment") {

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val timeStamp = new VVHClient("http://localhost:6080").createVVHTimeStamp(-5)
    when(mockVVHClient.createVVHTimeStamp(any[Int])).thenReturn(timeStamp)
    when(mockRoadLinkService.vvhClient).thenReturn(mockVVHClient)
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId1 = 3056622
    val municipalityCode = 444
    val administrativeClass = State
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 170
    val geom = List(Point(346005.726,6688024.548),Point(346020.228,6688034.371),Point(346046.054,6688061.11),Point(346067.323,6688080.86))
    val len = GeometryUtils.geometryLength(geom)
//      [{"modifiedAt":"19.05.2016 14:16:23","linkId":3056622,"roadNameFi":"Lohjanharjuntie","roadPartNumber":1,"administrativeClass":"State","municipalityCode":444,"roadNumber":1125,"points":[{"x":346005.726,"y":6688024.548,"z":0.0},{"x":346020.228,"y":6688034.371,"z":0.0},{"x":346046.054,"y":6688061.11,"z":0.0},{"x":346067.323,"y":6688080.86,"z":0.0}],"verticalLevel":0,"maxAddressNumberRight":1365,"trafficDirection":"TowardsDigitizing","minAddressNumberRight":1361,"roadNameSe":"Lojoåsvägen","functionalClass":4,"linkType":2,"mmlId":1204467577,"modifiedBy":"k638654"}],

    val roadLinks = Seq(RoadLink(oldLinkId1, geom, len, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))))
    val changeInfo = Seq(
      ChangeInfo(Some(oldLinkId1), Some(oldLinkId1), 1204467577, 7, Some(81.53683273), Some(164.92962409), Some(0), Some(83.715056320000002), 1461970812000L),
      ChangeInfo(Some(oldLinkId1), None, 1204467577, 8, Some(0), Some(81.53683273), None, None, 1461970812000L),
      ChangeInfo(Some(oldLinkId1), None, 1204467577, 8, Some(164.92962409), Some(165.37927110999999), None, None, 1461970812000L))

    OracleDatabase.withDynTransaction {
      sqlu"""DELETE FROM asset_link WHERE position_id in (SELECT id FROM lrm_position where link_id = $oldLinkId1)""".execute
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code, adjusted_timestamp) VALUES (1, $oldLinkId1, 0.0, 83.715, ${SideCode.BothDirections.value}, 1)""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values (1,$assetTypeId, TO_TIMESTAMP('2014-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX1')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (1,1)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) (SELECT 1, 1, id, 4779 FROM PROPERTY WHERE PUBLIC_ID = 'mittarajoitus')""".execute

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((roadLinks, changeInfo))
      val before = service.getByBoundingBox(assetTypeId, boundingBox, Set(municipalityCode))
      before should have size(2)

      val newAsset = NewLinearAsset(oldLinkId1, 2.187, len, NumericValue(4779), 1, 234567, None)
      val id = service.create(Seq(newAsset), assetTypeId, "KX2")

      id should have size (1)
      id.head should not be (0)

      val assets = service.getPersistedAssetsByIds(assetTypeId, Set(1L, id.head))
      assets should have size (2)
      assets.forall(_.vvhTimeStamp > 0L) should be (true)

      val after = service.getByBoundingBox(assetTypeId, boundingBox, Set(municipalityCode))
      after should have size(1)
      after.flatten.forall(_.id != 0) should be (true)
      dynamicSession.rollback()
    }
  }

  test("Should apply pavement on whole segment") {

    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val timeStamp = new VVHClient("http://localhost:6080").createVVHTimeStamp(-5)
    when(mockVVHClient.createVVHTimeStamp(any[Int])).thenReturn(timeStamp)
    when(mockRoadLinkService.vvhClient).thenReturn(mockVVHClient)
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    val oldLinkId1 = 4393233
    val municipalityCode = 444
    val administrativeClass = State
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val assetTypeId = 110
    val geom = List(Point(428906.372,6693954.166),Point(428867.234,6693997.01),Point(428857.131,6694009.293))
    val len = GeometryUtils.geometryLength(geom)
    //      [{"modifiedAt":"19.05.2016 14:16:23","linkId":3056622,"roadNameFi":"Lohjanharjuntie","roadPartNumber":1,"administrativeClass":"State","municipalityCode":444,"roadNumber":1125,"points":[{"x":346005.726,"y":6688024.548,"z":0.0},{"x":346020.228,"y":6688034.371,"z":0.0},{"x":346046.054,"y":6688061.11,"z":0.0},{"x":346067.323,"y":6688080.86,"z":0.0}],"verticalLevel":0,"maxAddressNumberRight":1365,"trafficDirection":"TowardsDigitizing","minAddressNumberRight":1361,"roadNameSe":"Lojoåsvägen","functionalClass":4,"linkType":2,"mmlId":1204467577,"modifiedBy":"k638654"}],

    val roadLinks = Seq(RoadLink(oldLinkId1, geom, len, administrativeClass, functionalClass, trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode), "SURFACETYPE" -> BigInt(2))))
    val changeInfo = Seq(
      ChangeInfo(Some(oldLinkId1), Some(oldLinkId1), 1204467577, 3, Some(0), Some(46.23260977), Some(27.86340569), Some(73.93340102), 1470653580000L),
      ChangeInfo(Some(oldLinkId1), Some(oldLinkId1), 1204467577, 3, None, None, Some(0), Some(27.86340569), 1470653580000L))
    OracleDatabase.withDynTransaction {
      sqlu"""DELETE FROM asset_link WHERE position_id in (SELECT id FROM lrm_position where link_id = $oldLinkId1)""".execute
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code, adjusted_timestamp) VALUES (1, $oldLinkId1, 0.0, 46.233, ${SideCode.BothDirections.value}, 1)""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values (1,$assetTypeId, TO_TIMESTAMP('2014-02-17 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'),'KX1')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (1,1)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) (SELECT 1, 1, id, 1 FROM PROPERTY WHERE PUBLIC_ID = 'mittarajoitus')""".execute

      val assets = service.getPersistedAssetsByIds(assetTypeId, Set(1L))
      assets should have size(1)

      when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((roadLinks, changeInfo))
      val after = service.getByBoundingBox(assetTypeId, boundingBox, Set(municipalityCode))
      after should have size(1)

      dynamicSession.rollback()
    }
  }

  test("Get Municipality Code By Asset Id") {
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    when(mockVVHClient.createVVHTimeStamp(any[Int])).thenCallRealMethod()
    val timeStamp = mockVVHClient.createVVHTimeStamp(-5)
    when(mockVVHClient.createVVHTimeStamp(any[Int])).thenReturn(timeStamp)
    when(mockRoadLinkService.vvhClient).thenReturn(mockVVHClient)
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }

    OracleDatabase.withDynTransaction {
      val (assetId, assetMunicipalityCode) = sql"""select ID, MUNICIPALITY_CODE from asset where asset_type_id = 10 and valid_to > = sysdate and rownum = 1""".as[(Int, Int)].first
      val municipalityCode = service.getMunicipalityCodeByAssetId(assetId)
      municipalityCode should be(assetMunicipalityCode)
    }
  }

  test("Should filter out linear assets on walkways from TN-ITS message") {
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }
    val roadLink1 = RoadLink(100, List(Point(0.0, 0.0), Point(1.0, 0.0)), 10.0, Municipality, 8, TrafficDirection.BothDirections, CycleOrPedestrianPath, None, None, Map("MUNICIPALITYCODE" -> BigInt(345)))
    val roadLink2 = RoadLink(200, List(Point(0.0, 0.0), Point(1.0, 0.0)), 10.0, Municipality, 5, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(345)))
    val roadLink3 = RoadLink(300, List(Point(0.0, 0.0), Point(1.0, 0.0)), 10.0, Municipality, 7, TrafficDirection.BothDirections, TractorRoad, None, None, Map("MUNICIPALITYCODE" -> BigInt(345)))
    val heightLimitAssetId = 70

    OracleDatabase.withDynTransaction {
      sqlu"""insert into lrm_position (id, link_id) VALUES (1, 100)""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date) values (1, ${heightLimitAssetId}, TO_TIMESTAMP('2016-11-01 16:00', 'YYYY-MM-DD HH24:MI'))""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (1, 1)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values (1, 1, (select id from property where public_id = 'mittarajoitus'), 1000)""".execute
      sqlu"""insert into lrm_position (id, link_id) VALUES (2, 200)""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date) values (2,  ${heightLimitAssetId}, TO_TIMESTAMP('2016-11-01 16:00', 'YYYY-MM-DD HH24:MI'))""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (2, 2)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values (2, 2, (select id from property where public_id = 'mittarajoitus'), 1000)""".execute
      sqlu"""insert into lrm_position (id, link_id) VALUES (3, 300)""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date) values (3,  ${heightLimitAssetId}, TO_TIMESTAMP('2016-11-01 16:00', 'YYYY-MM-DD HH24:MI'))""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values (3, 3)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) values (3, 3, (select id from property where public_id = 'mittarajoitus'), 1000)""".execute

      when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3))

      val result = service.getChanged(heightLimitAssetId, DateTime.parse("2016-11-01T12:00Z"), DateTime.parse("2016-11-02T12:00Z"))
      result.length should be(1)
      result.head.link.linkType should not be (TractorRoad)
      result.head.link.linkType should not be (CycleOrPedestrianPath)

      dynamicSession.rollback()
    }
  }

  test("Verify if we have all changes between given date after update a NumericValue Field in OTH") {
    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val service = new LinearAssetService(mockRoadLinkService, new DummyEventBus) {
      override def withDynTransaction[T](f: => T): T = f
    }
    val roadLink1 = RoadLink(1611374, List(Point(0.0, 0.0), Point(1.0, 0.0)), 10.0, Municipality, 8, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(345)))
    val totalWeightLimitAssetId = 30

    OracleDatabase.withDynTransaction {
      when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq(roadLink1))

      //Linear assets that have been changed in OTH between given date values Before Update
      val resultBeforeUpdate = service.getChanged(totalWeightLimitAssetId, DateTime.parse("2016-11-01T12:00Z"), DateTime.now().plusDays(1))

      //Update Numeric Values
      val assetToUpdate = linearAssetDao.fetchLinearAssetsByIds(Set(11111), "mittarajoitus").head
      val newAssetIdCreatedWithUpdate = ServiceWithDao.update(Seq(11111l), NumericValue(2000), "UnitTestsUser")
      val assetUpdated = linearAssetDao.fetchLinearAssetsByIds(newAssetIdCreatedWithUpdate.toSet, "mittarajoitus").head

      //Linear assets that have been changed in OTH between given date values After Update
      val resultAfterUpdate = service.getChanged(totalWeightLimitAssetId, DateTime.parse("2016-11-01T12:00Z"), DateTime.now().plusDays(1))

      val oldAssetInMessage = resultAfterUpdate.find { changedLinearAsset => changedLinearAsset.linearAsset.id == assetToUpdate.id }
      val newAssetInMessage = resultAfterUpdate.find { changedLinearAsset => changedLinearAsset.linearAsset.id == assetUpdated.id }

      resultAfterUpdate.size should be (resultBeforeUpdate.size + 1)
      oldAssetInMessage.size should be (1)
      newAssetInMessage.size should be (1)

      oldAssetInMessage.head.linearAsset.expired should be (true)
      oldAssetInMessage.head.linearAsset.value should be (assetToUpdate.value)

      dynamicSession.rollback()
    }
  }

  test("Fetch all Active Maintenance Road By Polygon") {
    val geomFact= new GeometryFactory()
    val geomBuilder = new GeometryBuilder(geomFact)

    val prop1 = Properties("huoltotie_kayttooikeus", "single_choice", "1")
    val prop2 = Properties("huoltotie_huoltovastuu", "single_choice", "2")
    val prop3 = Properties("huoltotie_tiehoitokunta", "text", "text")

    val propertiesSeq :Seq[Properties] = List(prop1, prop2, prop3)

    when(mockPolygonTools.getAreaGeometry(any[Int])).thenReturn(geomBuilder.polygon(24.2, 60.5, 24.8, 60.5, 24.8, 59, 24.2, 59))
    when(mockPolygonTools.stringifyGeometryForVVHClient(any[Seq[Polygon]])).thenReturn(Seq(""))
    when(mockRoadLinkService.getLinkIdsFromVVHWithPolygons(Seq(""))).thenReturn(Seq(388562360l))

    val maintenanceRoad = MaintenanceRoad(propertiesSeq)
    runWithRollback {
      val newAssets = ServiceWithDao.create(Seq(NewLinearAsset(388562360l, 0, 20, maintenanceRoad, 1, 0, None)), 290, "testuser")
      newAssets.length should be(1)

      val assets = ServiceWithDao.getActiveMaintenanceRoadByPolygon(1, 290)
      assets.map { asset =>
        asset.linkId should be(388562360l)
        asset.startMeasure should be(0)
        asset.endMeasure should be(20)
        asset.value.get.asInstanceOf[MaintenanceRoad].maintenanceRoad.length should be(3)
      }
    }
  }

  test("Fetch Active Maintenance Road By Polygon, with an empty result") {
    val geomFact= new GeometryFactory()
    val geomBuilder = new GeometryBuilder(geomFact)

    when(mockPolygonTools.getAreaGeometry(any[Int])).thenReturn(geomBuilder.polygon(24.2, 60.5, 24.8, 60.5, 24.8, 59, 24.2, 59))
    when(mockPolygonTools.stringifyGeometryForVVHClient(any[Seq[Polygon]])).thenReturn(Seq(""))
    when(mockRoadLinkService.getLinkIdsFromVVHWithPolygons(Seq(""))).thenReturn(Seq(388562360l))
    OracleDatabase.withDynTransaction {
      val assets = ServiceWithDao.getActiveMaintenanceRoadByPolygon(1, 290)
      assets.length should be(0)
    }
  }
}

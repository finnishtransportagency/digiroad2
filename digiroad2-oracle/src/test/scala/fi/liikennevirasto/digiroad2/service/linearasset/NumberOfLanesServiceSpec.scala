package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.ChangeInfo
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, PostGISAssetDao, Sequences}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, SideCodeAdjustment, ValueAdjustment}
import fi.liikennevirasto.digiroad2.linearasset.{PersistedLinearAsset, RoadLink}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.PolygonTools
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.mockito.MockitoSugar
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

class NumberOfLanesServiceSpec extends LinearAssetSpecSupport {
  

  val linearAssetService = new NumberOfLanesService(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
  }

  // Data created in siilinjarvi_verificationService_test_data.sql
  val oldLinkId = "ae2ffef1-353d-4c82-8246-108bb89809e1:5"
  val newLinkId = "4e339ea7-0d1c-4b83-ac34-d8cfeebe4066:6"
  val municipalityCode = 444
  val functionalClass = 1
  val assetTypeId = 170
  val geom = List(Point(0, 0), Point(300, 0))
  val len = GeometryUtils.geometryLength(geom)

  ignore("Adjust projected asset with creation: bi-directional road -> one way road updates one way asset to bi-directional"){
    val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
    val linearAssetService = new NumberOfLanesService(mockRoadLinkService, mockEventBus) {
      override def withDynTransaction[T](f: => T): T = f
      override def withDynSession[T](f: => T): T = f
    }

    val roadLinks = Seq(RoadLink(oldLinkId, geom, len, State, functionalClass, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(newLinkId, geom, len, State, functionalClass, TrafficDirection.TowardsDigitizing, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    )
    val changeInfo = Seq(
      ChangeInfo(Some(oldLinkId), Some(newLinkId), 1204467577, 1, Some(0), Some(150), Some(100), Some(200), 1461970812000L))

    PostGISDatabase.withDynTransaction {
      val (lrm, asset) = (Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)
      sqlu"""DELETE FROM asset_link WHERE position_id in (SELECT id FROM lrm_position where link_id = $oldLinkId)""".execute
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code, adjusted_timestamp) VALUES ($lrm, $oldLinkId, 0.0, 150.0, ${SideCode.TowardsDigitizing.value}, 1)""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values ($asset,$assetTypeId, null ,'KX1')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset,$lrm)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) (SELECT $asset, $asset, id, 4779 FROM PROPERTY WHERE PUBLIC_ID = 'mittarajoitus')""".execute


      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChanges(any[Int])).thenReturn((roadLinks, changeInfo))
      linearAssetService.getByMunicipality(assetTypeId, municipalityCode)

      verify(mockEventBus, times(1))
        .publish("linearAssets:update", ChangeSet(Set.empty[Long], Nil, List(SideCodeAdjustment(0,newLinkId,SideCode.BothDirections,170)), Set.empty[Long], valueAdjustments = Seq.empty[ValueAdjustment]))

      val captor = ArgumentCaptor.forClass(classOf[Seq[PersistedLinearAsset]])
      verify(mockEventBus, times(1)).publish(org.mockito.ArgumentMatchers.eq("linearAssets:saveProjectedLinearAssets"), captor.capture())
      val projectedAssets = captor.getValue.asInstanceOf[Seq[PersistedLinearAsset]]
      projectedAssets.length should be(1)
      projectedAssets.foreach { proj =>
        proj.id should be (0)
        proj.linkId should be (newLinkId)
        proj.sideCode should be (TrafficDirection.BothDirections.value)
      }
      dynamicSession.rollback()
    }
  }

  ignore("Adjust projected asset with creation: bi-directional road -> opposite one way road drops existing asset"){
    val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
    val linearAssetService = new NumberOfLanesService(mockRoadLinkService, mockEventBus) {
      override def withDynTransaction[T](f: => T): T = f
      override def withDynSession[T](f: => T): T = f
    }

    val roadLinks = Seq(RoadLink(oldLinkId, geom, len, State, functionalClass, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(newLinkId, geom, len, State, functionalClass, TrafficDirection.TowardsDigitizing, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    )
    val changeInfo = Seq(
      ChangeInfo(Some(oldLinkId), Some(newLinkId), 1204467577, 1, Some(0), Some(150), Some(100), Some(200), 1461970812000L))

    PostGISDatabase.withDynTransaction {
      val (lrm, asset) = (Sequences.nextLrmPositionPrimaryKeySeqValue, Sequences.nextPrimaryKeySeqValue)
      sqlu"""DELETE FROM asset_link WHERE position_id in (SELECT id FROM lrm_position where link_id = $oldLinkId)""".execute
      sqlu"""insert into lrm_position (id, link_id, start_measure, end_measure, side_code, adjusted_timestamp) VALUES ($lrm, $oldLinkId, 0.0, 150.0, ${SideCode.AgainstDigitizing.value}, 1)""".execute
      sqlu"""insert into asset (id, asset_type_id, modified_date, modified_by) values ($asset,$assetTypeId, null ,'KX1')""".execute
      sqlu"""insert into asset_link (asset_id, position_id) values ($asset,$lrm)""".execute
      sqlu"""insert into number_property_value (id, asset_id, property_id, value) (SELECT $asset, $asset, id, 4779 FROM PROPERTY WHERE PUBLIC_ID = 'mittarajoitus')""".execute


      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChanges(any[Int])).thenReturn((roadLinks, changeInfo))
      linearAssetService.getByMunicipality(assetTypeId, municipalityCode)

      verify(mockEventBus, times(1))
        .publish("linearAssets:update", ChangeSet(Set(0), Nil, Nil, Set.empty[Long], Nil))

      val captor = ArgumentCaptor.forClass(classOf[Seq[PersistedLinearAsset]])
      verify(mockEventBus, times(1)).publish(org.mockito.ArgumentMatchers.eq("linearAssets:saveProjectedLinearAssets"), captor.capture())
      val projectedAssets = captor.getValue.asInstanceOf[Seq[PersistedLinearAsset]]
      projectedAssets.length should be(1)
      projectedAssets.foreach { proj =>
        proj.id should be (0)
        proj.linkId should be (newLinkId)
        proj.sideCode should be (SideCode.AgainstDigitizing.value)
      }
      dynamicSession.rollback()
    }
  }
}

package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.ChangeInfo
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MunicipalityDao, PostGISAssetDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, SideCodeAdjustment}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{LinkIdGenerator, PolygonTools}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}

class MassTransitLaneServiceSpec extends DynamicLinearTestSupporter {

  object mtlServiceWithDao extends MassTransitLaneService(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: PostGISLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def polygonTools: PolygonTools = mockPolygonTools
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao
    override def assetDao: PostGISAssetDao = mockAssetDao
    override def dynamicLinearAssetDao: DynamicLinearAssetDao = mVLinearAssetDao

    override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
    override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()) = throw new UnsupportedOperationException("Not supported method")
  }
  case class TestAssetInfo(newLinearAsset: NewLinearAsset, typeId: Int)

  ignore("Adjust projected mtl asset with creation"){

   val massTransitLaneValue = DynamicValue(DynamicAssetValue(Seq(
     DynamicProperty("public_validity_period", "time_period", false, Seq(DynamicPropertyValue(Map("days" -> 1,
       "startHour" -> 2,
       "endHour" -> 10,
       "startMinute" -> 10,
       "endMinute" -> 20))))
        )))

    adjustProjectedAssetWithCreation(TestAssetInfo(NewLinearAsset("ae2ffef1-353d-4c82-8246-108bb89809e1:5", 0, 150, massTransitLaneValue, SideCode.AgainstDigitizing.value, 0, None), MassTransitLane.typeId))

  }

  test("Create new linear asset with validity period property") {
    runWithRollback {
      val typeId = 160
      val value = Seq(DynamicPropertyValue(Map("days" -> BigInt(1), "startHour" -> BigInt(0), "endHour" -> BigInt(0), "startMinute" -> BigInt(24), "endMinute" -> BigInt(0), "periodType" -> None)))
      val propertyData  = DynamicValue(DynamicAssetValue(Seq(DynamicProperty("public_validity_period", "time_period", false, value))))

      val newAssets = mtlServiceWithDao.create(Seq(NewLinearAsset(LinkIdGenerator.generateRandom(), 0, 40, propertyData, 1, 0, None)), typeId, "testuser")
      newAssets.length should be(1)
      val asset = mtlServiceWithDao.getPersistedAssetsByIds(typeId, newAssets.toSet).head
      asset.value.get.equals(propertyData) should be (true)
    }
  }

  test("Create new linear asset with validity periods property values") {
    runWithRollback {
      val typeId = 160
      val value = Seq(DynamicPropertyValue(Map("days" -> BigInt(1), "startHour" -> BigInt(10), "endHour" -> BigInt(14), "startMinute" -> BigInt(0), "endMinute" -> BigInt(0), "periodType" -> None)),
        DynamicPropertyValue(Map("days" -> BigInt(1), "startHour" -> BigInt(16), "endHour" -> BigInt(20), "startMinute" -> BigInt(30), "endMinute" -> BigInt(30), "periodType" -> None)))

      val propertyData  = DynamicValue(DynamicAssetValue(Seq(DynamicProperty("public_validity_period", "time_period", false, value))))

      val newAssets = mtlServiceWithDao.create(Seq(NewLinearAsset(LinkIdGenerator.generateRandom(), 0, 40, propertyData, 1, 0, None)), typeId, "testuser")
      newAssets.length should be(1)
      val asset = mtlServiceWithDao.getPersistedAssetsByIds(typeId, newAssets.toSet).head
      asset.value.get.equals(propertyData) should be (true)
    }
  }


  def adjustProjectedAssetWithCreation(assetInfoCount: TestAssetInfo) : Unit = {
    val assetInfo = assetInfoCount
    val oldLinkId = "ae2ffef1-353d-4c82-8246-108bb89809e1:5" // Data created in siilinjarvi_verificationService_test_data.sql
    val newLinkId = "4e339ea7-0d1c-4b83-ac34-d8cfeebe4066:6"
    val municipalityCode = 444
    val functionalClass = 1
    val geom = List(Point(0, 0), Point(300, 0))
    val len = GeometryUtils.geometryLength(geom)

    val roadLinks = Seq(RoadLink(oldLinkId, geom, len, State, functionalClass, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(newLinkId, geom, len, State, functionalClass, TrafficDirection.BothDirections, Freeway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    )
    val changeInfo = Seq(
      ChangeInfo(Some(oldLinkId), Some(newLinkId), 1204467577, 1, Some(0), Some(150), Some(100), Some(200), 1461970812000L))

    runWithRollback {
      val assetId = mtlServiceWithDao.create(Seq(assetInfo.newLinearAsset), assetInfo.typeId, "KX1", 0).head

      when(mockRoadLinkService.getRoadLinksWithComplementaryAndChanges(any[Int])).thenReturn((roadLinks, changeInfo))
      mtlServiceWithDao.getByMunicipality(assetInfo.typeId, municipalityCode)

      withClue("assetName " + AssetTypeInfo.apply(assetInfo.typeId).layerName) {
        verify(mockEventBus, times(1))
          .publish("dynamicAsset:update", ChangeSet(Set(),List(),List(),Set(), Nil))

        val captor = ArgumentCaptor.forClass(classOf[Seq[PersistedLinearAsset]])
        verify(mockEventBus, times(1)).publish(org.mockito.ArgumentMatchers.eq("dynamicAsset:saveProjectedAssets"), captor.capture())

        val toBeComparedProperties = assetInfo.newLinearAsset.value.asInstanceOf[DynamicValue].value.properties
        val projectedAssets = captor.getValue.asInstanceOf[Seq[PersistedLinearAsset]]
        projectedAssets.length should be(1)
        val projectedAsset = projectedAssets.head
        projectedAsset.id should be(0)
        projectedAsset.linkId should be(newLinkId)
        projectedAsset.value.get.asInstanceOf[DynamicValue].value.properties.foreach { property =>
          val existingProperty = toBeComparedProperties.find(p => p.publicId == property.publicId)
          existingProperty.get.values.forall { value =>
            toBeComparedProperties.asInstanceOf[Seq[DynamicProperty]].exists(prop => prop.values.contains(value))
          }
        }
      }
    }
  }
}

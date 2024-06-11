package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset._
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
}

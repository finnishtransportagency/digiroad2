package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, AxleWeightLimit, TotalWeightLimit}
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.{InaccurateAssetDAO, MunicipalityDao, OracleAssetDao}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.process.AssetValidatorInfo
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.PolygonTools

class LinearTotalWeightLimitService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends LinearSevenRestrictionsOperations {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def dao: OracleLinearAssetDao = new OracleLinearAssetDao(roadLinkServiceImpl.vvhClient, roadLinkServiceImpl)
  override def municipalityDao: MunicipalityDao = new MunicipalityDao
  override def eventBus: DigiroadEventBus = eventBusImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def polygonTools: PolygonTools = new PolygonTools()
  override def assetDao: OracleAssetDao = new OracleAssetDao

  override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")

  override def update(ids: Seq[Long], value: Value, username: String): Seq[Long] = {
    val outputIds = withDynTransaction {
      updateWithoutTransaction(ids, value, username)
    }

    eventBus.publish("totalWeightLimit:Validator",AssetValidatorInfo(ids.toSet, outputIds.toSet))
    outputIds
  }

  /**
    * Saves new linear assets from UI. Used by Digiroad2Api /linearassets POST endpoint.
    */
  override def create(newLinearAssets: Seq[NewLinearAsset], typeId: Int, username: String, vvhTimeStamp: Long = vvhClient.roadLinkData.createVVHTimeStamp()): Seq[Long] = {
    val newIds = withDynTransaction {
      val roadLink = roadLinkService.getRoadLinksAndComplementariesFromVVH(newLinearAssets.map(_.linkId).toSet, false)
       newLinearAssets.map { newAsset =>
        createWithoutTransaction(typeId, newAsset.linkId, newAsset.value, newAsset.sideCode, Measures(newAsset.startMeasure, newAsset.endMeasure), username, vvhTimeStamp, roadLink.find(_.linkId == newAsset.linkId), verifiedBy = getVerifiedBy(username, typeId))
      }
    }
    eventBus.publish("totalWeightLimit:Validator",AssetValidatorInfo(Set(), Set(), newLinearAssets.map(_.linkId).toSet))
    newIds
  }
}

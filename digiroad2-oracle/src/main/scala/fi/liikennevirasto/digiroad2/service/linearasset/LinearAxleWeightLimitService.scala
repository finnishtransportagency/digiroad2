package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, PostGISAssetDao}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.process.AssetValidatorInfo
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.PolygonTools

class LinearAxleWeightLimitService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends DynamicLinearAssetService(roadLinkServiceImpl, eventBusImpl) {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def dao: PostGISLinearAssetDao = new PostGISLinearAssetDao()
  override def municipalityDao: MunicipalityDao = new MunicipalityDao
  override def eventBus: DigiroadEventBus = eventBusImpl
  override def polygonTools: PolygonTools = new PolygonTools()
  override def assetDao: PostGISAssetDao = new PostGISAssetDao

  override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")

  override def update(ids: Seq[Long], value: Value, username: String, timeStamp: Option[Long] = None, sideCode: Option[Int] = None, measures: Option[Measures] = None): Seq[Long] = {
    val outputIds = withDynTransaction {
      updateWithoutTransaction(ids, value, username, timeStamp, sideCode, measures)
    }

    eventBus.publish("axleWeightLimit:Validator",AssetValidatorInfo((ids ++ outputIds).toSet))
    outputIds
  }

  /**
    * Saves new linear assets from UI. Used by Digiroad2Api /linearassets POST endpoint.
    */
  override def create(newLinearAssets: Seq[NewLinearAsset], typeId: Int, username: String, timeStamp: Long = createTimeStamp()): Seq[Long] = {
    val newIds = withDynTransaction {
      val roadLink = roadLinkService.getRoadLinksAndComplementariesFromDB(newLinearAssets.map(_.linkId).toSet, false)
      newLinearAssets.map { newAsset =>
        createWithoutTransaction(typeId, newAsset.linkId, newAsset.value, newAsset.sideCode, Measures(newAsset.startMeasure, newAsset.endMeasure), username, timeStamp, roadLink.find(_.linkId == newAsset.linkId), verifiedBy = getVerifiedBy(username, typeId))
      }
    }
    eventBus.publish("axleWeightLimit:Validator",AssetValidatorInfo(newIds.toSet, newLinearAssets.map(_.linkId).toSet))
    newIds
  }
}

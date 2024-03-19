package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService

class DamagedByThawService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends DynamicLinearAssetService(roadLinkServiceImpl, eventBusImpl) {

  override def enrichPersistedLinearAssetProperties(persistedLinearAsset: Seq[PersistedLinearAsset]): Seq[PersistedLinearAsset] = {
    val assetIds = persistedLinearAsset.map(_.id)
    if (assetIds.nonEmpty) {
      val properties = dynamicLinearAssetDao.getDatePeriodPropertyValue(assetIds.toSet, persistedLinearAsset.head.typeId)
      enrichWithProperties(properties, persistedLinearAsset)
    } else {
      Seq.empty[PersistedLinearAsset]
    }
  }

  override def getAssetsByMunicipality(typeId: Int, municipality: Int, newTransaction: Boolean = true): Seq[PersistedLinearAsset] = {
    if(newTransaction) withDynTransaction {
      val roadLinks= roadLinkService.getRoadLinksByMunicipality(municipality, newTransaction = false)
      val linkIds = roadLinks.map(_.linkId)
      dynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(typeId, linkIds)
    } else {
      val roadLinks= roadLinkService.getRoadLinksByMunicipality(municipality, newTransaction = false)
      val linkIds = roadLinks.map(_.linkId)
      dynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(typeId, linkIds)
    }
  }

}

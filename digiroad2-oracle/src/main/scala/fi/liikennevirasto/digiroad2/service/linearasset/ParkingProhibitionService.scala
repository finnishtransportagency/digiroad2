package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.linearasset.{AssetFiller, OneWayAssetFiller, PersistedLinearAsset}
import fi.liikennevirasto.digiroad2.service.RoadLinkService

class ParkingProhibitionService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends DynamicLinearAssetService(roadLinkServiceImpl, eventBusImpl) {
  override def assetFiller: AssetFiller = new OneWayAssetFiller

  override def enrichPersistedLinearAssetProperties(persistedLinearAsset: Seq[PersistedLinearAsset]): Seq[PersistedLinearAsset] = {
    val assetIds = persistedLinearAsset.map(_.id)
    if (assetIds.nonEmpty) {
      val properties = dynamicLinearAssetDao.getValidityPeriodPropertyValue(assetIds.toSet, persistedLinearAsset.head.typeId)
      enrichWithProperties(properties, persistedLinearAsset)
    } else {
      Seq.empty[PersistedLinearAsset]
    }
  }
}

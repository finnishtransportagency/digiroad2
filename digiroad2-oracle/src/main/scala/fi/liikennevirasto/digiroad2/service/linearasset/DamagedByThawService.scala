package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService

class DamagedByThawService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends DynamicLinearAssetService(roadLinkServiceImpl, eventBusImpl) {
  override def assetFiller: AssetFiller = new DamagedByThawFiller

  override def enrichPersistedLinearAssetProperties(persistedLinearAsset: Seq[PersistedLinearAsset]): Seq[PersistedLinearAsset] = {
    val assetIds = persistedLinearAsset.map(_.id)
    if (assetIds.nonEmpty) {
      val properties = dynamicLinearAssetDao.getDatePeriodPropertyValue(assetIds.toSet, persistedLinearAsset.head.typeId)
      enrichWithProperties(properties, persistedLinearAsset)
    } else {
      Seq.empty[PersistedLinearAsset]
    }
  }

  override def publish(eventBus: DigiroadEventBus, changeSet: LinearAssetFiller.ChangeSet, projectedAssets: Seq[PersistedLinearAsset]): Unit = {
    eventBus.publish("damagedByThaw:update", changeSet)
    eventBus.publish("dynamicAsset:saveProjectedAssets", projectedAssets.filter(_.id == 0L))
  }
}

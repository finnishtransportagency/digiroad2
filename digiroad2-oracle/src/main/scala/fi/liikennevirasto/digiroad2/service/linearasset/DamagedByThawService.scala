package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService

class DamagedByThawService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends DynamicLinearAssetService(roadLinkServiceImpl, eventBusImpl) {
  override def assetFiller: AssetFiller = new DamagedByThawFiller

  override def enrichPersistedLinearAssetProperties(persistedLinearAsset: Seq[PersistedLinearAsset]) : Seq[PersistedLinearAsset] = {
    val assetIds = persistedLinearAsset.map(_.id)
    if (assetIds.nonEmpty) {
      val properties = dynamicLinearAssetDao.getDatePeriodPropertyValue(assetIds.toSet, persistedLinearAsset.head.typeId)
      persistedLinearAsset.groupBy(_.id).flatMap {
        case (id, assets) =>
          properties.get(id) match {
            case Some(props) => assets.map(a => a.copy(value = a.value match {
              case Some(value) =>
                val multiValue = value.asInstanceOf[DynamicValue]
                //If exist at least one property to enrich with value all null properties value could be filter out
                Some(multiValue.copy(value = DynamicAssetValue(multiValue.value.properties.filter(_.values.nonEmpty ) ++ props)))
              case _ =>
                Some(DynamicValue(DynamicAssetValue(props)))
            }))
            case _ => assets
          }
      }.toSeq
    } else {
      Seq.empty[PersistedLinearAsset]
    }
  }
}

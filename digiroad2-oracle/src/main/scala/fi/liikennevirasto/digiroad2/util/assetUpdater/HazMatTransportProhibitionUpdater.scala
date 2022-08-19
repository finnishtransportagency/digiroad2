package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset.HazmatTransportProhibition
import fi.liikennevirasto.digiroad2.linearasset.{PersistedLinearAsset, Prohibitions}
import fi.liikennevirasto.digiroad2.service.linearasset.{HazmatTransportProhibitionService, LinearAssetTypes, Measures}
import fi.liikennevirasto.digiroad2.util.DataFixture.dynamicLinearAssetService.getLinkSource

class HazMatTransportProhibitionUpdater(service: HazmatTransportProhibitionService) extends ProhibitionUpdater(service) {

  override def persistProjectedLinearAssets(newLinearAssets: Seq[PersistedLinearAsset]): Unit = {
    if (newLinearAssets.nonEmpty)
      logger.info("Saving projected prohibition assets")

    val (toInsert, toUpdate) = newLinearAssets.partition(_.id == 0L)
    val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(newLinearAssets.map(_.linkId).toSet, newTransaction = false)
    if (toUpdate.nonEmpty) {
      val prohibitions = toUpdate.filter(a => Set(HazmatTransportProhibition.typeId).contains(a.typeId))
      val persisted = dao.fetchProhibitionsByIds(HazmatTransportProhibition.typeId, prohibitions.map(_.id).toSet).groupBy(_.id)
      updateProjected(toUpdate, persisted)
      if (newLinearAssets.nonEmpty)
        logger.info("Updated ids/linkids " + toUpdate.map(a => (a.id, a.linkId)))
    }
    toInsert.foreach { linearAsset =>
      val id =
        (linearAsset.createdBy, linearAsset.createdDateTime) match {
          case (Some(createdBy), Some(createdDateTime)) =>
            dao.createLinearAsset(linearAsset.typeId, linearAsset.linkId, linearAsset.expired, linearAsset.sideCode,
              Measures(linearAsset.startMeasure, linearAsset.endMeasure), LinearAssetTypes.VvhGenerated, linearAsset.timeStamp,
              getLinkSource(roadLinks.find(_.linkId == linearAsset.linkId)), true, Some(createdBy), Some(createdDateTime), linearAsset.verifiedBy, linearAsset.verifiedDate)
          case _ =>
            dao.createLinearAsset(linearAsset.typeId, linearAsset.linkId, linearAsset.expired, linearAsset.sideCode,
              Measures(linearAsset.startMeasure, linearAsset.endMeasure), LinearAssetTypes.VvhGenerated, linearAsset.timeStamp, getLinkSource(roadLinks.find(_.linkId == linearAsset.linkId)))
        }
      linearAsset.value match {
        case Some(prohibitions: Prohibitions) =>
          dao.insertProhibitionValue(id, HazmatTransportProhibition.typeId, prohibitions)
        case _ => None
      }
    }
    if (newLinearAssets.nonEmpty)
      logger.info("Added assets for linkids " + toInsert.map(_.linkId))
  }
}
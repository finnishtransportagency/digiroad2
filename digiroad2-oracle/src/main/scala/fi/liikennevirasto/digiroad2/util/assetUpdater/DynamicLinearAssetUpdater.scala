package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset.{DynamicProperty, MmlNls}
import fi.liikennevirasto.digiroad2.dao.DynamicLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.SideCodeAdjustment
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.linearasset.{DynamicLinearAssetService, LinearAssetTypes, Measures}
import fi.liikennevirasto.digiroad2.util.LinearAssetUtils

class DynamicLinearAssetUpdater(service: DynamicLinearAssetService) extends LinearAssetUpdater(service) {

  def dynamicLinearAssetDao: DynamicLinearAssetDao = new DynamicLinearAssetDao

  override def persistProjectedLinearAssets(newLinearAssets: Seq[PersistedLinearAsset]): Unit = {
    val (toInsert, toUpdate) = newLinearAssets.partition(_.id == 0L)
    val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(newLinearAssets.map(_.linkId).toSet, newTransaction = false)

    if (toUpdate.nonEmpty) {
      val persisted = dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(toUpdate.map(_.id).toSet).groupBy(_.id)
      updateProjected(toUpdate, persisted, roadLinks)

      if (newLinearAssets.nonEmpty)
        logger.info("Updated ids/linkids " + toUpdate.map(a => (a.id, a.linkId)))
    }

    toInsert.foreach { linearAsset =>
      val roadLink = roadLinks.find(_.linkId == linearAsset.linkId)

      val id = dao.createLinearAsset(linearAsset.typeId, linearAsset.linkId, linearAsset.expired, linearAsset.sideCode,
        Measures(linearAsset.startMeasure, linearAsset.endMeasure), linearAsset.createdBy.getOrElse(LinearAssetTypes.generatedInUpdate), linearAsset.timeStamp, service.getLinkSource(roadLink), informationSource = Some(MmlNls.value))
      linearAsset.value match {
        case Some(DynamicValue(multiTypeProps)) =>
          val props = setDefaultAndFilterProperties(multiTypeProps, roadLink, linearAsset.typeId)
          service.validateRequiredProperties(linearAsset.typeId, props)
          dynamicLinearAssetDao.updateAssetProperties(id, props, linearAsset.typeId)
        case _ => None
      }
    }
    if (newLinearAssets.nonEmpty)
      logger.info("Added assets for linkids " + toInsert.map(_.linkId))
  }

  protected def updateProjected(toUpdate: Seq[PersistedLinearAsset], persisted: Map[Long, Seq[PersistedLinearAsset]], roadLinks: Seq[RoadLink]) : Unit = {
    def valueChanged(assetToPersist: PersistedLinearAsset, persistedLinearAsset: Option[PersistedLinearAsset]) = {
      !persistedLinearAsset.exists(_.value == assetToPersist.value)
    }

    toUpdate.foreach { linearAsset =>
      val roadLink = roadLinks.find(_.linkId == linearAsset.linkId)
      val persistedLinearAsset = persisted.getOrElse(linearAsset.id, Seq()).headOption
      val id = linearAsset.id
      if (valueChanged(linearAsset, persistedLinearAsset)) {
        linearAsset.value match {
          case Some(DynamicValue(multiTypeProps)) =>
            dynamicLinearAssetDao.updateAssetLastModified(id, LinearAssetTypes.generatedInUpdate) match {
              case Some(id) =>
                val props = setDefaultAndFilterProperties(multiTypeProps, roadLink, linearAsset.typeId)
                service.validateRequiredProperties(linearAsset.typeId, props)
                dynamicLinearAssetDao.updateAssetProperties(id, props, linearAsset.typeId)
              case _ => None
            }
          case _ => None
        }
      }
    }
  }

  protected def setDefaultAndFilterProperties(multiTypeProps: DynamicAssetValue, roadLink: Option[RoadLinkLike], typeId: Int) : Seq[DynamicProperty] = {
    val properties = service.setPropertiesDefaultValues(multiTypeProps.properties, roadLink)
    val defaultValues = dynamicLinearAssetDao.propertyDefaultValues(typeId).filterNot(defaultValue => properties.exists(_.publicId == defaultValue.publicId))
    properties ++ defaultValues.toSet
  }

  override def adjustedSideCode(adjustment: SideCodeAdjustment): Unit = {
    val oldAsset = service.getPersistedAssetsByIds(adjustment.typeId, Set(adjustment.assetId), newTransaction =  false).headOption
      .getOrElse(throw new IllegalStateException("Old asset " + adjustment.assetId + " of type " + adjustment.typeId + " no longer available"))
    val roadLink = roadLinkService.getRoadLinkAndComplementaryFromVVH(oldAsset.linkId, newTransaction = false)
      .getOrElse(throw new IllegalStateException("Road link " + oldAsset.linkId + " no longer available"))
    service.expireAsset(oldAsset.typeId, oldAsset.id, LinearAssetTypes.generatedInUpdate, expired = true, newTransaction = false)
    service.createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, oldAsset.value.get, adjustment.sideCode.value, Measures(oldAsset.startMeasure, oldAsset.endMeasure),
      LinearAssetTypes.generatedInUpdate, LinearAssetUtils.createTimeStamp(), Some(roadLink))
  }

}

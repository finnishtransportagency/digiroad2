package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, VVHClient}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, MValueAdjustment}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetOperations, LinearAssetTypes, Measures}
import fi.liikennevirasto.digiroad2.util.{LinearAssetUtils, PolygonTools}
import org.joda.time.DateTime


class RoadWidthService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends LinearAssetOperations {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def dao: OracleLinearAssetDao = new OracleLinearAssetDao(roadLinkServiceImpl.vvhClient, roadLinkServiceImpl)
  override def eventBus: DigiroadEventBus = eventBusImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def polygonTools: PolygonTools = new PolygonTools()

  override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")

  override protected def getByRoadLinks(typeId: Int, roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo]): Seq[PieceWiseLinearAsset] = {
    val linkIds = roadLinks.map(_.linkId)
    val removedLinkIds = LinearAssetUtils.deletedRoadLinkIds(changes, roadLinks)
    val (expiredAssets, existingAssets) =
      withDynTransaction {
            dao.fetchLinearAssetsByLinkIds(LinearAssetTypes.RoadWidthAssetTypeId, linkIds ++ removedLinkIds, LinearAssetTypes.numericValuePropertyId, true).partition(_.expired == true)
        }

    val (assetsOnChangedLinks, assetsWithoutChangedLinks) = existingAssets.partition(a => LinearAssetUtils.newChangeInfoDetected(a, changes))

    val projectableTargetRoadLinks = roadLinks.filter(rl => rl.linkType.value == UnknownLinkType.value || rl.isCarTrafficRoad)

    val (expiredRoadWidthAssetIds, newRoadWidthAssets) = getRoadWidthAssetChanges(existingAssets, expiredAssets, roadLinks, changes)

    val combinedAssets = assetsOnChangedLinks.filterNot(
      a => expiredRoadWidthAssetIds.contains(a.id) || assetsWithoutChangedLinks.exists(_.id == a.id) || assetsWithoutChangedLinks.exists(_.id == a.id)
    ) ++ newRoadWidthAssets

    val filledNewAssets = fillNewRoadLinksWithPreviousAssetsData(projectableTargetRoadLinks,
      combinedAssets, assetsOnChangedLinks, changes) ++ assetsWithoutChangedLinks

    val newAssets = newRoadWidthAssets.filterNot(a => filledNewAssets.exists(f => f.linkId == a.linkId)) ++ filledNewAssets

    val timing = System.currentTimeMillis
    if (newAssets.nonEmpty) {
      logger.info("Transferred %d assets in %d ms ".format(newAssets.length, System.currentTimeMillis - timing))
    }
    val groupedAssets = (existingAssets.filterNot(a => expiredRoadWidthAssetIds.contains(a.id) || newAssets.exists(_.linkId == a.linkId)) ++ newAssets).groupBy(_.linkId)
    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(roadLinks, groupedAssets, typeId)

    val expiredAssetIds = existingAssets.filter(asset => removedLinkIds.contains(asset.linkId)).map(_.id).toSet ++
      changeSet.expiredAssetIds

    eventBus.publish("roadWidth:update", changeSet.copy(expiredAssetIds = expiredRoadWidthAssetIds.filterNot(_ == 0L)))

    //Remove the asset ids ajusted in the "roadWidth:update" otherwise if the "roadWidth:saveProjectedRoadWidth" is executed after the "roadWidth:update"
    //it will update the mValues to the previous ones
    eventBus.publish("RoadWidth:saveProjectedRoadWidth", newAssets.filterNot(a => changeSet.adjustedMValues.exists(_.assetId == a.id)))

    filledTopology
  }

  def getRoadWidthAssetChanges(existingLinearAssets: Seq[PersistedLinearAsset], expiredAssets: Seq[PersistedLinearAsset],
                         roadLinks: Seq[RoadLink], changeInfos: Seq[ChangeInfo]): (Set[Long], Seq[PersistedLinearAsset]) = {

    val roadLinkAdminClass = roadLinks.filter(road => road.administrativeClass == Municipality || road.administrativeClass == Private)
    val roadWithMTKClass = roadLinkAdminClass.filter(road => MTKClassWidth.values.toSeq.contains(road.extractMTKClass(road.attributes)))

    val lastChanges = changeInfos.filter(_.newId.isDefined).groupBy(_.newId.get).mapValues(c => c.maxBy(_.vvhTimeStamp))

    val lastModified = expiredAssets.groupBy(_.linkId).mapValues(
      _.maxBy{x => x.modifiedDateTime match {
        case Some(modifiedDate) => modifiedDate.getMillis
        case _ => 0
      }
    }).values
    val notByMTKClass = lastModified.filterNot(_.modifiedBy == "vvh_mtkclass_default")

    //Map all existing assets by roadlink and changeinfo
    val changedAssets = lastChanges.map {
      case (linkId, changeInfo) =>
        (roadWithMTKClass.find(road => road.linkId == linkId), changeInfo, existingLinearAssets.filter(_.linkId == linkId) , notByMTKClass.filter(_.linkId == linkId))
    }

    val expiredAssetsIds = changedAssets.flatMap {
      case (Some(roadlink), changeInfo, assets, _) if assets.nonEmpty =>
        assets.filter(asset =>
          asset.createdBy.contains("dr1_conversion") ||
          (asset.vvhTimeStamp < changeInfo.vvhTimeStamp && asset.createdBy.contains("vvh_mtkclass_default"))
        ).map(_.id)
      case _ =>
        List()
    }.toSet[Long]

    val newAssets = changedAssets.flatMap{
      case (Some(roadLink), changeInfo, assets, expired) if assets.isEmpty && expired.isEmpty =>
        Some(PersistedLinearAsset(0L, roadLink.linkId, SideCode.BothDirections.value, Some(NumericValue(roadLink.extractMTKClass(roadLink.attributes).width)),
          0, GeometryUtils.geometryLength(roadLink.geometry), Some("vvh_mtkclass_default"), None, None, None, false, LinearAssetTypes.RoadWidthAssetTypeId, changeInfo.vvhTimeStamp, None, linkSource = roadLink.linkSource, getVerifiedBy("vvh_mtkclass_default", LinearAssetTypes.RoadWidthAssetTypeId), None))
      case (Some(roadLink), changeInfo, assets, expired) if assets.nonEmpty =>
        //if the asset was created by changeInfo and there is a new changeInfo, expire and crete a new asset
        assets.filter(asset => expiredAssetsIds.contains(asset.id)).map { asset =>
        PersistedLinearAsset(0L, roadLink.linkId, SideCode.BothDirections.value, Some(NumericValue(roadLink.extractMTKClass(roadLink.attributes).width)),
          asset.startMeasure, asset.endMeasure, Some("vvh_mtkclass_default"), None, None, None, false, LinearAssetTypes.RoadWidthAssetTypeId, changeInfo.vvhTimeStamp, None, linkSource = roadLink.linkSource, getVerifiedBy("vvh_mtkclass_default", LinearAssetTypes.RoadWidthAssetTypeId), None)}
      case _ =>
        None
    }.toSeq

    (expiredAssetsIds, newAssets)
  }

  override def persistProjectedLinearAssets(newLinearAssets: Seq[PersistedLinearAsset]): Unit ={
    if (newLinearAssets.nonEmpty)
      logger.info("Saving projected road Width assets")

    val (toInsert, toUpdate) = newLinearAssets.partition(_.id == 0L)
    withDynTransaction {
        val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(newLinearAssets.map(_.linkId).toSet, newTransaction = false)
        val persisted = dao.fetchLinearAssetsByIds(toUpdate.map(_.id).toSet, LinearAssetTypes.numericValuePropertyId).groupBy(_.id)
      updateProjected(toUpdate, persisted)

      if (newLinearAssets.nonEmpty)
        logger.info("Updated ids/linkids " + toUpdate.map(a => (a.id, a.linkId)))

      toInsert.foreach{ linearAsset =>
        val id = dao.createLinearAsset(linearAsset.typeId, linearAsset.linkId, linearAsset.expired, linearAsset.sideCode,
          Measures(linearAsset.startMeasure, linearAsset.endMeasure), linearAsset.createdBy.getOrElse(LinearAssetTypes.VvhGenerated), linearAsset.vvhTimeStamp, getLinkSource(roadLinks.find(_.linkId == linearAsset.linkId)), verifiedBy = linearAsset.verifiedBy)
        linearAsset.value match {
          case Some(NumericValue(intValue)) =>
            dao.insertValue(id, LinearAssetTypes.numericValuePropertyId, intValue)
          case _ => None
        }
      }
      if (newLinearAssets.nonEmpty)
        logger.info("Added assets for linkids " + toInsert.map(_.linkId))
    }
  }

  override protected def updateProjected(toUpdate: Seq[PersistedLinearAsset], persisted: Map[Long, Seq[PersistedLinearAsset]]) = {
    def valueChanged(assetToPersist: PersistedLinearAsset, persistedLinearAsset: Option[PersistedLinearAsset]) = {
      !persistedLinearAsset.exists(_.value == assetToPersist.value)
    }
    def mValueChanged(assetToPersist: PersistedLinearAsset, persistedLinearAsset: Option[PersistedLinearAsset]) = {
      !persistedLinearAsset.exists(pl => pl.startMeasure == assetToPersist.startMeasure &&
        pl.endMeasure == assetToPersist.endMeasure &&
        pl.vvhTimeStamp == assetToPersist.vvhTimeStamp)
    }
    def sideCodeChanged(assetToPersist: PersistedLinearAsset, persistedLinearAsset: Option[PersistedLinearAsset]) = {
      !persistedLinearAsset.exists(_.sideCode == assetToPersist.sideCode)
    }
    toUpdate.foreach { linearAsset =>
      val persistedLinearAsset = persisted.getOrElse(linearAsset.id, Seq()).headOption
      val id = linearAsset.id
      if (valueChanged(linearAsset, persistedLinearAsset)) {
        linearAsset.value match {
          case Some(NumericValue(intValue)) =>
            dao.updateValue(id, intValue, LinearAssetTypes.numericValuePropertyId, LinearAssetTypes.VvhGenerated)
          case _ => None
        }
      }
      if (mValueChanged(linearAsset, persistedLinearAsset)) dao.updateMValues(linearAsset.id, (linearAsset.startMeasure, linearAsset.endMeasure), linearAsset.vvhTimeStamp)
      if (sideCodeChanged(linearAsset, persistedLinearAsset)) dao.updateSideCode(linearAsset.id, SideCode(linearAsset.sideCode))
    }
  }

  override protected def updateWithoutTransaction(ids: Seq[Long], value: Value, username: String, measures: Option[Measures] = None, vvhTimeStamp: Option[Long] = None, sideCode: Option[Int] = None): Seq[Long] = {
    if (ids.isEmpty)
      return ids

    ids.flatMap { id =>
      updateValueByExpiration(id, value.asInstanceOf[NumericValue], LinearAssetTypes.numericValuePropertyId, username, measures, vvhTimeStamp, sideCode)
    }
  }

  override protected def createWithoutTransaction(typeId: Int, linkId: Long, value: Value, sideCode: Int, measures: Measures, username: String, vvhTimeStamp: Long, roadLink: Option[RoadLinkLike], fromUpdate: Boolean = false,
                                                  createdByFromUpdate: Option[String] = Some(""),
                                                  createdDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()), verifiedBy: Option[String]): Long = {
    val id = dao.createLinearAsset(typeId, linkId, expired = false, sideCode, measures, username,
      vvhTimeStamp, getLinkSource(roadLink), fromUpdate, createdByFromUpdate, createdDateTimeFromUpdate, verifiedBy)
    value match {
      case NumericValue(intValue) =>
        dao.insertValue(id, LinearAssetTypes.numericValuePropertyId, intValue)
      case _ => None
    }
    id
  }

  override def updateChangeSet(changeSet: ChangeSet) : Unit = {
    withDynTransaction {

      val ids = changeSet.expiredAssetIds.toSeq
      if (ids.nonEmpty)
        logger.info("Expiring ids " + ids.mkString(", "))
      ids.foreach(dao.updateExpiration(_, expired = true, "vvh_mtkclass_default"))
    }
  }

  override def getPersistedAssetsByIds(typeId: Int, ids: Set[Long]): Seq[PersistedLinearAsset] = {
    withDynTransaction {
      dao.fetchLinearAssetsByIds(ids, LinearAssetTypes.getValuePropertyId(typeId))
    }
  }
}

package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, MValueAdjustment}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.linearasset.oracle.{AssetLastModification, OracleLinearAssetDao}
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
    val mappedChanges = LinearAssetUtils.getMappedChanges(changes)

    val removedLinkIds = LinearAssetUtils.deletedRoadLinkIds(mappedChanges, linkIds.toSet)

    val (existingAssets) =
      withDynTransaction {
          dao.fetchLinearAssetsByLinkIds(LinearAssetTypes.RoadWidthAssetTypeId, linkIds ++ removedLinkIds, LinearAssetTypes.numericValuePropertyId)
      }

    val timing = System.currentTimeMillis

    val (assetsOnChangedLinks, assetsWithoutChangedLinks) = existingAssets.partition(a => LinearAssetUtils.newChangeInfoDetected(a, mappedChanges))

    val projectableTargetRoadLinks = roadLinks.filter(rl => rl.linkType.value == UnknownLinkType.value || rl.isCarTrafficRoad)

    val (expiredRoadWidthAssetIds, newRoadWidthAssets) = getRoadWidthAssetChanges(existingAssets, roadLinks, changes, (newAssetIds) => withDynSession {
      dao.fetchExpireAssetLastModificationsByLinkIds(LinearAssetTypes.RoadWidthAssetTypeId, newAssetIds)
    })

    val combinedAssets = assetsOnChangedLinks.filterNot(a => expiredRoadWidthAssetIds.contains(a.id)) ++ newRoadWidthAssets

    val filledNewAssets = fillNewRoadLinksWithPreviousAssetsData(projectableTargetRoadLinks,
      combinedAssets, assetsOnChangedLinks, changes)

    val newAssets = newRoadWidthAssets.filterNot(a => filledNewAssets.exists(f => f.linkId == a.linkId)) ++ filledNewAssets

    if (newAssets.nonEmpty) {
      logger.info("Transferred %d assets in %d ms ".format(newAssets.length, System.currentTimeMillis - timing))
    }
    val groupedAssets = (existingAssets.filterNot(a => expiredRoadWidthAssetIds.contains(a.id) || newAssets.exists(_.linkId == a.linkId)) ++ newAssets ++ assetsWithoutChangedLinks).groupBy(_.linkId)
    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(roadLinks, groupedAssets, typeId)

    val expiredAssetIds = existingAssets.filter(asset => removedLinkIds.contains(asset.linkId)).map(_.id).toSet ++
      changeSet.expiredAssetIds

    eventBus.publish("roadWidth:update", changeSet.copy(expiredAssetIds = expiredRoadWidthAssetIds.filterNot(_ == 0L)))

    //Remove the asset ids ajusted in the "roadWidth:update" otherwise if the "roadWidth:saveProjectedRoadWidth" is executed after the "roadWidth:update"
    //it will update the mValues to the previous ones
    eventBus.publish("RoadWidth:saveProjectedRoadWidth", newAssets.filterNot(a => changeSet.adjustedMValues.exists(_.assetId == a.id)))

    filledTopology
  }

  def getRoadWidthAssetChanges(linearAssets: Seq[PersistedLinearAsset], roadLinks: Seq[RoadLink], changeInfos: Seq[ChangeInfo], fetchModifications: (Seq[Long]) => Seq[AssetLastModification]): (Set[Long], Seq[PersistedLinearAsset]) = {

    val mappedLastChanges = changeInfos.filter(_.newId.isDefined).groupBy(_.newId.get).mapValues(c => c.maxBy(_.vvhTimeStamp))
    val mappedLinearAssets = linearAssets.groupBy(_.linkId)
    val mappedRoadLinks = roadLinks.
      filter(road => road.administrativeClass == Municipality || road.administrativeClass == Private).
      filter(road => MTKClassWidth.values.toSeq.contains(road.extractMTKClass(road.attributes))).
      groupBy(_.linkId).mapValues(_.head)

    //Map all existing assets by roadlink and changeinfo
    val changedAssets = mappedLastChanges.map {
      case (linkId, changeInfo) =>
        (mappedRoadLinks.get(linkId), changeInfo, mappedLinearAssets.getOrElse(linkId, Seq()))
    }

    val expiredAssetsIds = changedAssets.flatMap {
      case (_, changeInfo, assets) =>
        assets.filter(asset =>
          asset.createdBy.contains("dr1_conversion") || (asset.vvhTimeStamp < changeInfo.vvhTimeStamp && asset.createdBy.contains("vvh_mtkclass_default"))
        ).map(_.id)
      case _ =>
        List()
    }.toSet

    val newAssetIds = changedAssets.filter(_._3.isEmpty).map(_._2.newId.get)
    val assetsLastModification = if(newAssetIds.isEmpty) Map[Long, AssetLastModification]() else {
      fetchModifications(newAssetIds.toSeq).groupBy(_.linkId)
    }

    val newAssets = changedAssets.flatMap{
      case (Some(roadLink), changeInfo, assets) if assets.isEmpty =>
        assetsLastModification.get(roadLink.linkId) match {
          case Some(_) =>
            None
          case _ =>
            Some(PersistedLinearAsset(0L, roadLink.linkId, SideCode.BothDirections.value, Some(NumericValue(roadLink.extractMTKClass(roadLink.attributes).width)),
              0, GeometryUtils.geometryLength(roadLink.geometry), Some("vvh_mtkclass_default"), None, None, None, false, LinearAssetTypes.RoadWidthAssetTypeId, changeInfo.vvhTimeStamp, None, linkSource = roadLink.linkSource))
        }
      case (Some(roadLink), changeInfo, assets) =>
        //if the asset was created by changeInfo and there is a new changeInfo, expire and crete a new asset
        assets.filter(asset => expiredAssetsIds.contains(asset.id)).map { asset =>
        PersistedLinearAsset(0L, roadLink.linkId, SideCode.BothDirections.value, Some(NumericValue(roadLink.extractMTKClass(roadLink.attributes).width)),
          asset.startMeasure, asset.endMeasure, Some("vvh_mtkclass_default"), None, None, None, false, LinearAssetTypes.RoadWidthAssetTypeId, changeInfo.vvhTimeStamp, None, linkSource = roadLink.linkSource)}
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
          Measures(linearAsset.startMeasure, linearAsset.endMeasure), linearAsset.createdBy.getOrElse(LinearAssetTypes.VvhGenerated), linearAsset.vvhTimeStamp, getLinkSource(roadLinks.find(_.linkId == linearAsset.linkId)))
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
                                                  createdDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now())): Long = {
    val id = dao.createLinearAsset(typeId, linkId, expired = false, sideCode, measures, username,
      vvhTimeStamp, getLinkSource(roadLink), fromUpdate, createdByFromUpdate, createdDateTimeFromUpdate)
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

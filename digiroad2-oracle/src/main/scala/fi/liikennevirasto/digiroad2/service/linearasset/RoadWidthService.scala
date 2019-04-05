package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, VVHClient}
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, OracleAssetDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.{AssetLastModification, OracleLinearAssetDao}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{LinearAssetUtils, PolygonTools}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils}
import org.joda.time.DateTime

class RoadWidthService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends LinearAssetOperations {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def dao: OracleLinearAssetDao = new OracleLinearAssetDao(roadLinkServiceImpl.vvhClient, roadLinkServiceImpl)
  override def municipalityDao: MunicipalityDao = new MunicipalityDao
  override def eventBus: DigiroadEventBus = eventBusImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def polygonTools: PolygonTools = new PolygonTools()
  override def assetDao: OracleAssetDao = new OracleAssetDao

  override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
  override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()) = throw new UnsupportedOperationException("Not supported method")

  override protected def getByRoadLinks(typeId: Int, roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo]): Seq[PieceWiseLinearAsset] = {

      val linkIds = roadLinks.map(_.linkId)
      val mappedChanges = LinearAssetUtils.getMappedChanges(changes)
      val removedLinkIds = LinearAssetUtils.deletedRoadLinkIds(mappedChanges, linkIds.toSet)
      val existingAssets =
        withDynTransaction {
            dao.fetchLinearAssetsByLinkIds(LinearAssetTypes.RoadWidthAssetTypeId, linkIds ++ removedLinkIds, LinearAssetTypes.numericValuePropertyId)
        }

      val timing = System.currentTimeMillis
      val (assetsOnChangedLinks, assetsWithoutChangedLinks) = existingAssets.partition(a => LinearAssetUtils.newChangeInfoDetected(a, mappedChanges))

      val projectableTargetRoadLinks = roadLinks.filter(rl => rl.linkType.value == UnknownLinkType.value || rl.isCarTrafficRoad())

      val initChangeSet: ChangeSet = ChangeSet(droppedAssetIds = Set.empty[Long],
                                    expiredAssetIds = existingAssets.filter(asset => removedLinkIds.contains(asset.linkId)).map(_.id).toSet.filterNot(_ == 0L),
                                    adjustedMValues = Seq.empty[MValueAdjustment],
                                    adjustedVVHChanges = Seq.empty[VVHChangesAdjustment],
                                    adjustedSideCodes = Seq.empty[SideCodeAdjustment],
        valueAdjustments = Seq.empty[ValueAdjustment])

      val (projectedAssets, changedSetProjected) = fillNewRoadLinksWithPreviousAssetsData(projectableTargetRoadLinks,
        assetsOnChangedLinks, assetsOnChangedLinks, changes, initChangeSet)

      val (newRoadWidthAssets, changedSet) = getRoadWidthAssetChanges(existingAssets, projectedAssets, roadLinks, changes, newAssetIds =>
        withDynTransaction {
          dao.fetchExpireAssetLastModificationsByLinkIds(LinearAssetTypes.RoadWidthAssetTypeId, newAssetIds)
      }, changedSetProjected)

      val newAssets = assetsWithoutChangedLinks ++ projectedAssets.filterNot(a =>
        newRoadWidthAssets.exists(b => b.linkId == a.linkId && b.sideCode == a.sideCode && b.startMeasure == a.startMeasure && b.endMeasure == a.endMeasure)) ++
        newRoadWidthAssets

      if (newAssets.nonEmpty) {
        logger.info("Transferred %d assets in %d ms ".format(newAssets.length, System.currentTimeMillis - timing))
      }

      val groupedAssets = (existingAssets.filterNot(a => changedSet.expiredAssetIds.contains(a.id) || newAssets.exists(_.linkId == a.linkId)) ++ newAssets).groupBy(_.linkId)
      val (filledTopology, changeSet) = assetFiller.fillTopology(roadLinks, groupedAssets, typeId, Some(changedSet))

      eventBus.publish("roadWidth:update", changeSet)
      eventBus.publish("RoadWidth:saveProjectedRoadWidth", newAssets.filter(_.id == 0L))

      filledTopology
    }

  def getRoadWidthAssetChanges(linearAssets: Seq[PersistedLinearAsset], projectedAssets: Seq[PersistedLinearAsset], roadLinks: Seq[RoadLink], changeInfos: Seq[ChangeInfo],
                               fetchModifications: Seq[Long] => Seq[AssetLastModification], changedSet: ChangeSet): (Seq[PersistedLinearAsset], ChangeSet) = {

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

    val expiredAssets = changedAssets.flatMap {
      case (Some(road), changeInfo, assets) =>
        assets.filter(asset => asset.modifiedBy.getOrElse(asset.createdBy.getOrElse("")) == "dr1_conversion" ||
          (asset.vvhTimeStamp < changeInfo.vvhTimeStamp && (asset.modifiedBy.getOrElse(asset.createdBy.getOrElse("")) == "vvh_mtkclass_default" ||
            asset.modifiedBy.getOrElse("") == "vvh_generated" && asset.createdBy.getOrElse("") == "vvh_mtkclass_default"))
        ).map{asset => (asset.id, asset.linkId)}
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
              0, GeometryUtils.geometryLength(roadLink.geometry), Some("vvh_mtkclass_default"), None, None, None, false, LinearAssetTypes.RoadWidthAssetTypeId,
              changeInfo.vvhTimeStamp, None, linkSource = roadLink.linkSource, getVerifiedBy("vvh_mtkclass_default", LinearAssetTypes.RoadWidthAssetTypeId), None, Some(MmlNls)))
        }
      case (Some(roadLink), changeInfo, assets) =>
        //if the asset was created by changeInfo and there is a new changeInfo, expire and crete a new asset
        assets.filter(asset => expiredAssets.map(_._2).contains(asset.linkId)).map { asset =>
          val (startMeasure, endMeasure)= projectedAssets.find(_.id == asset.id) match {
            case Some(projAsset) => (projAsset.startMeasure, projAsset.endMeasure)
            case _ => (asset.startMeasure, asset.endMeasure)
          }

        PersistedLinearAsset(0L, roadLink.linkId, SideCode.BothDirections.value, Some(NumericValue(roadLink.extractMTKClass(roadLink.attributes).width)),
          startMeasure, endMeasure, asset.createdBy, asset.createdDateTime, Some("vvh_mtkclass_default"), None, false, LinearAssetTypes.RoadWidthAssetTypeId,
          changeInfo.vvhTimeStamp, None, linkSource = roadLink.linkSource, getVerifiedBy("vvh_mtkclass_default", LinearAssetTypes.RoadWidthAssetTypeId), None, Some(MmlNls))}
      case _ =>
        None
    }.toSeq

    (newAssets , changedSet.copy( expiredAssetIds = changedSet.expiredAssetIds ++ expiredAssets.map(_._1).filterNot(_ == 0),
                                  adjustedVVHChanges = changedSet.adjustedVVHChanges.filterNot(change => expiredAssets.map(_._1).contains(change.assetId)),
                                  adjustedSideCodes = changedSet.adjustedSideCodes.filterNot(change => expiredAssets.map(_._1).contains(change.assetId))))
  }

  override def persistProjectedLinearAssets(newLinearAssets: Seq[PersistedLinearAsset]): Unit ={
    if (newLinearAssets.nonEmpty)
      logger.info("Saving projected road Width assets")

    val (toInsert, toUpdate) = newLinearAssets.partition(_.id == 0L)
    withDynTransaction {
        val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(newLinearAssets.map(_.linkId).toSet, newTransaction = false)
        if(toUpdate.nonEmpty) {
          val persisted = dao.fetchLinearAssetsByIds(toUpdate.map(_.id).toSet, LinearAssetTypes.numericValuePropertyId).groupBy(_.id)
          updateProjected(toUpdate, persisted)

          if (newLinearAssets.nonEmpty)
            logger.info("Updated ids/linkids " + toUpdate.map(a => (a.id, a.linkId)))
        }
      toInsert.foreach{ linearAsset =>
        val roadlink = roadLinks.find(_.linkId == linearAsset.linkId)
        val id = (linearAsset.createdBy, linearAsset.createdDateTime) match {
          case (Some(createdBy), Some(createdDateTime)) =>
            dao.createLinearAsset(linearAsset.typeId, linearAsset.linkId, linearAsset.expired, linearAsset.sideCode,
              Measures(linearAsset.startMeasure, linearAsset.endMeasure), linearAsset.modifiedBy.getOrElse(LinearAssetTypes.VvhGenerated), linearAsset.vvhTimeStamp,
              getLinkSource(roadlink), fromUpdate = true, Some(createdBy), Some(createdDateTime), linearAsset.verifiedBy, linearAsset.verifiedDate, Some(MmlNls.value), geometry = getGeometry(roadlink))
          case _ =>
            dao.createLinearAsset(linearAsset.typeId, linearAsset.linkId, linearAsset.expired, linearAsset.sideCode,
              Measures(linearAsset.startMeasure, linearAsset.endMeasure), linearAsset.createdBy.getOrElse(LinearAssetTypes.VvhGenerated), linearAsset.vvhTimeStamp,
              getLinkSource(roadlink), verifiedBy = linearAsset.verifiedBy, informationSource = Some(MmlNls.value), geometry = getGeometry(roadlink))
        }
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
    }
  }

  override def updateChangeSet(changeSet: ChangeSet) : Unit = {
    withDynTransaction {
      dao.floatLinearAssets(changeSet.droppedAssetIds)

      if (changeSet.adjustedMValues.nonEmpty)
        logger.info("Saving adjustments for asset/link ids=" + changeSet.adjustedMValues.map(a => "" + a.assetId + "/" + a.linkId).mkString(", "))

      changeSet.adjustedMValues.foreach { adjustment =>
        dao.updateMValues(adjustment.assetId, (adjustment.startMeasure, adjustment.endMeasure))
      }

//      //This changes only should be apply if the asset created_by or modified_by are different of "vvh_mtkclass_default"
      if (changeSet.adjustedVVHChanges.nonEmpty)
        logger.info("Saving adjustments for asset/link ids=" + changeSet.adjustedVVHChanges.map(a => "" + a.assetId + "/" + a.linkId).mkString(", "))

      changeSet.adjustedVVHChanges.foreach { adjustment =>
        dao.updateMValuesChangeInfo(adjustment.assetId, (adjustment.startMeasure, adjustment.endMeasure), adjustment.vvhTimestamp, LinearAssetTypes.VvhGenerated)
      }

      changeSet.adjustedSideCodes.foreach { adjustment =>
        adjustedSideCode(adjustment)
      }

      val ids = changeSet.expiredAssetIds.toSeq
      if (ids.nonEmpty)
        logger.info("Expiring ids " + ids.mkString(", "))
      ids.foreach(dao.updateExpiration(_, expired = true, "vvh_mtkclass_default"))
    }
  }

  override def getPersistedAssetsByIds(typeId: Int, ids: Set[Long], newTransaction: Boolean = true): Seq[PersistedLinearAsset] = {
    if(newTransaction)
      withDynTransaction {
        dao.fetchLinearAssetsByIds(ids, LinearAssetTypes.getValuePropertyId(typeId))
      }
    else
      dao.fetchLinearAssetsByIds(ids, LinearAssetTypes.getValuePropertyId(typeId))
  }

  override def create(newLinearAssets: Seq[NewLinearAsset], typeId: Int, username: String, vvhTimeStamp: Long = vvhClient.roadLinkData.createVVHTimeStamp()): Seq[Long] = {
    withDynTransaction {
      val roadlink = roadLinkService.getRoadLinksAndComplementariesFromVVH(newLinearAssets.map(_.linkId).toSet, false)
      newLinearAssets.map { newAsset =>
        createWithoutTransaction(typeId, newAsset.linkId, newAsset.value, newAsset.sideCode, Measures(newAsset.startMeasure, newAsset.endMeasure), username, vvhTimeStamp, roadlink.find(_.linkId == newAsset.linkId), verifiedBy = getVerifiedBy(username, typeId), informationSource = Some(MunicipalityMaintenainer.value))
      }
    }
  }

  /**
    * Saves updated linear asset from UI. Used by Digiroad2Api /linearassets POST endpoint.
    */
  override def update(ids: Seq[Long], value: Value, username: String, vvhTimeStamp: Option[Long] = None, sideCode: Option[Int] = None, measures: Option[Measures] = None): Seq[Long] = {
    withDynTransaction {
      updateWithoutTransaction(ids, value, username, measures = measures, sideCode = sideCode, informationSource = Some(MunicipalityMaintenainer.value))
    }
  }

  override def split(id: Long, splitMeasure: Double, existingValue: Option[Value], createdValue: Option[Value], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit): Seq[Long] = {
    withDynTransaction {
      val linearAsset = dao.fetchLinearAssetsByIds(Set(id), LinearAssetTypes.numericValuePropertyId).head
      val roadLink = vvhClient.fetchRoadLinkByLinkId(linearAsset.linkId).
        getOrElse(throw new IllegalStateException("Road link no longer available"))
      municipalityValidation(roadLink.municipalityCode, roadLink.administrativeClass)

      val (existingLinkMeasures, createdLinkMeasures) = GeometryUtils.createSplit(splitMeasure, (linearAsset.startMeasure, linearAsset.endMeasure))

      val newIdsToReturn = existingValue match {
        case None => dao.updateExpiration(id, expired = true, username).toSeq
        case Some(value) => updateWithoutTransaction(Seq(id), value, username, measures = Some(Measures(existingLinkMeasures._1, existingLinkMeasures._2)), informationSource = Some(MunicipalityMaintenainer.value))
      }

      val createdIdOption = createdValue.map(createWithoutTransaction(linearAsset.typeId, linearAsset.linkId, _, linearAsset.sideCode, Measures(createdLinkMeasures._1, createdLinkMeasures._2), username, linearAsset.vvhTimeStamp,
        Some(roadLink), informationSource = Some(MunicipalityMaintenainer.value)))

      newIdsToReturn ++ Seq(createdIdOption).flatten
    }
  }


}

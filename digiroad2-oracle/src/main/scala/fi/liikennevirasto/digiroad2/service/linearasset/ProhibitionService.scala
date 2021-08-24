package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.asset.ProhibitionClass._
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, VVHClient}
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, PostGISAssetDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{LinearAssetUtils, PolygonTools}
import org.joda.time.DateTime

class ProhibitionCreationException(val response: Set[String]) extends RuntimeException {}

class ProhibitionService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends LinearAssetOperations {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def dao: PostGISLinearAssetDao = new PostGISLinearAssetDao(roadLinkServiceImpl.vvhClient, roadLinkServiceImpl)
  override def municipalityDao: MunicipalityDao = new MunicipalityDao
  override def eventBus: DigiroadEventBus = eventBusImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def polygonTools: PolygonTools = new PolygonTools()
  override def assetDao: PostGISAssetDao = new PostGISAssetDao
  override def assetFiller: AssetFiller = new OneWayAssetFiller
  override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
  override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()): Map[String, Map[String, Any]] = throw new UnsupportedOperationException("Not supported method")

  /**
    * Returns linear assets by asset type and asset ids. Used by Digiroad2Api /linearassets POST and /linearassets DELETE endpoints.
    */
  override def getPersistedAssetsByIds(typeId: Int, ids: Set[Long], newTransaction: Boolean = true): Seq[PersistedLinearAsset] = {
    if (newTransaction)
      withDynTransaction {
        dao.fetchProhibitionsByIds(typeId, ids)
      }
    else
      dao.fetchProhibitionsByIds(typeId, ids)
  }

  override def getPersistedAssetsByLinkIds(typeId: Int, linkIds: Seq[Long], newTransaction: Boolean = true): Seq[PersistedLinearAsset] = {
    if (newTransaction)
      withDynTransaction {
        dao.fetchProhibitionsByLinkIds(typeId, linkIds)
      }
    else
      dao.fetchProhibitionsByLinkIds(typeId, linkIds)
  }

  override def getAssetsByMunicipality(typeId: Int, municipality: Int): Seq[PersistedLinearAsset] = {
    val (roadLinks, changes) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(municipality)
    val linkIds = roadLinks.map(_.linkId)
    val mappedChanges = LinearAssetUtils.getMappedChanges(changes)
    val removedLinkIds = LinearAssetUtils.deletedRoadLinkIds(mappedChanges, roadLinks.map(_.linkId).toSet)
    withDynTransaction {
      dao.fetchProhibitionsByLinkIds(typeId, linkIds ++ removedLinkIds, includeFloating = false)
    }.filterNot(_.expired)
  }

  override protected def getByRoadLinks(typeId: Int, roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo]): Seq[PieceWiseLinearAsset] = {
    val linkIds = roadLinks.map(_.linkId)
    val mappedChanges = LinearAssetUtils.getMappedChanges(changes)

    val removedLinkIds = LinearAssetUtils.deletedRoadLinkIds(mappedChanges, linkIds.toSet)

    val existingAssets =
      withDynTransaction {
        dao.fetchProhibitionsByLinkIds(typeId, linkIds ++ removedLinkIds, includeFloating = false)
      }.filterNot(_.expired)

    val timing = System.currentTimeMillis

    val (assetsOnChangedLinks, assetsWithoutChangedLinks) = existingAssets.partition(a => LinearAssetUtils.newChangeInfoDetected(a, mappedChanges))
    val projectableTargetRoadLinks = roadLinks.filter(
      rl => rl.linkType.value == UnknownLinkType.value || rl.isCarTrafficRoad)


    val initChangeSet = ChangeSet(droppedAssetIds = Set.empty[Long],
      expiredAssetIds = existingAssets.filter(asset => removedLinkIds.contains(asset.linkId)).map(_.id).toSet.filterNot(_ == 0L),
      adjustedMValues = Seq.empty[MValueAdjustment],
      adjustedVVHChanges = Seq.empty[VVHChangesAdjustment],
      adjustedSideCodes = Seq.empty[SideCodeAdjustment],
      valueAdjustments = Seq.empty[ValueAdjustment])

    val combinedAssets = existingAssets.filterNot(a => assetsWithoutChangedLinks.exists(_.id == a.id))

    val (newAssets, changedSet) = fillNewRoadLinksWithPreviousAssetsData(projectableTargetRoadLinks,
      combinedAssets, assetsOnChangedLinks, changes, initChangeSet, existingAssets)

    if (newAssets.nonEmpty) {
      logger.info("Transferred %d assets in %d ms ".format(newAssets.length, System.currentTimeMillis - timing))
    }
    val groupedAssets = (existingAssets.filterNot(a => newAssets.exists(_.linkId == a.linkId)) ++ newAssets ++ assetsWithoutChangedLinks).groupBy(_.linkId)
    val (filledTopology, changeSet) = assetFiller.fillTopology(roadLinks, groupedAssets, typeId, Some(changedSet))

    //Remove the asset ids adjusted in the "prohibition:saveProjectedProhibition" otherwise if the "prohibition:saveProjectedLinearAssets" is executed after the "linearAssets:update"
    //it will update the mValues to the previous ones
    eventBus.publish("prohibition:update", changeSet)
    eventBus.publish("prohibition:saveProjectedProhibition", newAssets.filter(_.id == 0L))

    filledTopology
  }

  override def persistProjectedLinearAssets(newLinearAssets: Seq[PersistedLinearAsset]): Unit = {
    if (newLinearAssets.nonEmpty)
      logger.info("Saving projected prohibition assets")

    val (toInsert, toUpdate) = newLinearAssets.partition(_.id == 0L)
    withDynTransaction {
      val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(newLinearAssets.map(_.linkId).toSet, newTransaction = false)
      if(toUpdate.nonEmpty) {
        val prohibitions = toUpdate.filter(a => Set(Prohibition.typeId).contains(a.typeId))
        val persisted = dao.fetchProhibitionsByIds(Prohibition.typeId, prohibitions.map(_.id).toSet).groupBy(_.id)
        updateProjected(toUpdate, persisted)
        if (newLinearAssets.nonEmpty)
          logger.info("Updated ids/linkids " + toUpdate.map(a => (a.id, a.linkId)))
      }
      toInsert.foreach { linearAsset =>
        val id =
          (linearAsset.createdBy, linearAsset.createdDateTime) match {
            case (Some(createdBy), Some(createdDateTime)) =>
              dao.createLinearAsset(linearAsset.typeId, linearAsset.linkId, linearAsset.expired, linearAsset.sideCode,
                Measures(linearAsset.startMeasure, linearAsset.endMeasure), LinearAssetTypes.VvhGenerated, linearAsset.vvhTimeStamp,
                getLinkSource(roadLinks.find(_.linkId == linearAsset.linkId)), true, Some(createdBy), Some(createdDateTime), linearAsset.verifiedBy, linearAsset.verifiedDate)
            case _ =>
              dao.createLinearAsset(linearAsset.typeId, linearAsset.linkId, linearAsset.expired, linearAsset.sideCode,
                Measures(linearAsset.startMeasure, linearAsset.endMeasure), LinearAssetTypes.VvhGenerated, linearAsset.vvhTimeStamp, getLinkSource(roadLinks.find(_.linkId == linearAsset.linkId)))
          }
        linearAsset.value match {
          case Some(prohibitions: Prohibitions) =>
            dao.insertProhibitionValue(id, Prohibition.typeId, prohibitions)
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
          case Some(prohibitions: Prohibitions) =>
            dao.updateProhibitionValue(id, linearAsset.typeId, prohibitions, LinearAssetTypes.VvhGenerated)
          case _ => None
        }
      }
    }
  }

  override def updateWithoutTransaction(ids: Seq[Long], value: Value, username: String, vvhTimeStamp: Option[Long] = None, sideCode: Option[Int] = None, measures: Option[Measures] = None, informationSource: Option[Int] = None): Seq[Long] = {
    if (ids.isEmpty)
      return ids

    val assetTypeId = assetDao.getAssetTypeId(ids)
    val assetTypeById = assetTypeId.foldLeft(Map.empty[Long, Int]) { case (m, (id, typeId)) => m + (id -> typeId) }

    ids.flatMap { id =>
      val typeId = assetTypeById(id)
      val oldAsset = dao.fetchProhibitionsByIds(typeId, Set(id)).head
      val newMeasures = measures.getOrElse(Measures(oldAsset.startMeasure, oldAsset.endMeasure))
      val newSideCode = sideCode.getOrElse(oldAsset.sideCode)
      val roadLink = vvhClient.fetchRoadLinkByLinkId(oldAsset.linkId).getOrElse(throw new IllegalStateException("Road link no longer available"))

      value match {
        case prohibitions: Prohibitions =>
          if ((validateMinDistance(newMeasures.startMeasure, oldAsset.startMeasure) || validateMinDistance(newMeasures.endMeasure, oldAsset.endMeasure)) || newSideCode != oldAsset.sideCode) {
            dao.updateExpiration(id)
            Some(createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, prohibitions, newSideCode, newMeasures, username, vvhClient.roadLinkData.createVVHTimeStamp(), Some(roadLink), verifiedBy = getVerifiedBy(username, oldAsset.typeId)))
          }
          else {
            dao.updateVerifiedInfo(Set(id), username)
            dao.updateProhibitionValue(id, typeId, prohibitions, username)
          }
        case _ =>
          Some(id)
      }
    }
  }

  protected def updateValueByExpiration(assetId: Long, prohibitions: Prohibitions, assetTypeId: Int, username: String, measures: Option[Measures], vvhTimeStamp: Option[Long], sideCode: Option[Int]): Option[Long] = {
    //Get Old Asset
    dao.fetchProhibitionsByIds(assetTypeId, Set(assetId)).headOption.map {
      oldAsset =>
        if (oldAsset.value.contains(prohibitions)) {
          dao.updateProhibitionValue(assetId, assetTypeId, prohibitions, username, measures).head
        } else {
          //Expire the old asset
          dao.updateExpiration(assetId)
          val roadLink = roadLinkService.getRoadLinkAndComplementaryFromVVH(oldAsset.linkId, newTransaction = false)
          //Create New Asset
          createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, prohibitions, sideCode.getOrElse(oldAsset.sideCode),
            measures.getOrElse(Measures(oldAsset.startMeasure, oldAsset.endMeasure)), username, vvhTimeStamp.getOrElse(vvhClient.roadLinkData.createVVHTimeStamp()), roadLink, true, oldAsset.createdBy, oldAsset.createdDateTime, getVerifiedBy(username, oldAsset.typeId))
        }
    }
  }

  override def createWithoutTransaction(typeId: Int, linkId: Long, value: Value, sideCode: Int, measures: Measures, username: String, vvhTimeStamp: Long, roadLink: Option[RoadLinkLike], fromUpdate: Boolean = false,
                                                  createdByFromUpdate: Option[String] = Some(""),
                                                  createdDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()), verifiedBy: Option[String] = None, informationSource: Option[Int] = None): Long = {
    val id = dao.createLinearAsset(typeId, linkId, expired = false, sideCode, measures, username,
      vvhTimeStamp, getLinkSource(roadLink), fromUpdate, createdByFromUpdate, createdDateTimeFromUpdate, verifiedBy)
    value match {
      case prohibitions: Prohibitions =>
        dao.insertProhibitionValue(id, typeId, prohibitions)
      case _ => None
    }
    id
  }

  override def split(id: Long, splitMeasure: Double, existingValue: Option[Value], createdValue: Option[Value], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit): Seq[Long] = {
    withDynTransaction {
      val assetTypeId = assetDao.getAssetTypeId(Seq(id))
      val assetTypeById = assetTypeId.foldLeft(Map.empty[Long, Int]) { case (m, (id, typeId)) => m + (id -> typeId) }

      val linearAsset = dao.fetchProhibitionsByIds(assetTypeById(id), Set(id)).head
      val roadLink = vvhClient.fetchRoadLinkByLinkId(linearAsset.linkId).getOrElse(throw new IllegalStateException("Road link no longer available"))
      municipalityValidation(roadLink.municipalityCode, roadLink.administrativeClass)

      val (existingLinkMeasures, createdLinkMeasures) = GeometryUtils.createSplit(splitMeasure, (linearAsset.startMeasure, linearAsset.endMeasure))

      dao.updateExpiration(id)

      val existingId = existingValue.map(createWithoutTransaction(linearAsset.typeId, linearAsset.linkId, _, linearAsset.sideCode, Measures(existingLinkMeasures._1, existingLinkMeasures._2), username, linearAsset.vvhTimeStamp, Some(roadLink)))
      val createdId = createdValue.map(createWithoutTransaction(linearAsset.typeId, linearAsset.linkId, _, linearAsset.sideCode, Measures(createdLinkMeasures._1, createdLinkMeasures._2), username, linearAsset.vvhTimeStamp, Some(roadLink)))
      Seq(existingId, createdId).flatten
    }
  }

  override def separate(id: Long, valueTowardsDigitization: Option[Value], valueAgainstDigitization: Option[Value], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit): Seq[Long] = {
    withDynTransaction {
      val assetTypeId = assetDao.getAssetTypeId(Seq(id))
      val assetTypeById = assetTypeId.foldLeft(Map.empty[Long, Int]) { case (m, (id, typeId)) => m + (id -> typeId) }

      val existing = dao.fetchProhibitionsByIds(assetTypeById(id), Set(id)).head
      val roadLink = vvhClient.fetchRoadLinkByLinkId(existing.linkId).getOrElse(throw new IllegalStateException("Road link no longer available"))
      municipalityValidation(roadLink.municipalityCode, roadLink.administrativeClass)

      dao.updateExpiration(id, expired = true, username)

      val (newId1, newId2) =
        (valueTowardsDigitization.map(createWithoutTransaction(existing.typeId, existing.linkId, _, SideCode.TowardsDigitizing.value, Measures(existing.startMeasure, existing.endMeasure), username, existing.vvhTimeStamp, Some(roadLink))),
          valueAgainstDigitization.map(createWithoutTransaction(existing.typeId, existing.linkId, _, SideCode.AgainstDigitizing.value, Measures(existing.startMeasure, existing.endMeasure), username, existing.vvhTimeStamp, Some(roadLink))))

      Seq(newId1, newId2).flatten
    }
  }

  override def getChanged(typeId: Int, since: DateTime, until: DateTime, withAutoAdjust: Boolean = false, token: Option[String] = None): Seq[ChangedLinearAsset] = {
    val excludedTypes = Seq(PassageThrough, HorseRiding, SnowMobile, RecreationalVehicle, OversizedTransport)
    val prohibitions = withDynTransaction {
      dao.getProhibitionsChangedSince(typeId, since, until, excludedTypes, withAutoAdjust, token)
    }
    val roadLinks = roadLinkService.getRoadLinksByLinkIdsFromVVH(prohibitions.map(_.linkId).toSet).filterNot(_.linkType == CycleOrPedestrianPath).filterNot(_.linkType == TractorRoad)
    mapPersistedAssetChanges(prohibitions, roadLinks)

  }

  override def adjustedSideCode(adjustment: SideCodeAdjustment): Unit = {
    val oldAsset = getPersistedAssetsByIds(adjustment.typeId, Set(adjustment.assetId), newTransaction = false).headOption
      .getOrElse(throw new IllegalStateException("Prohibition: Old asset " + adjustment.assetId + " no longer available"))
    val roadLink = roadLinkService.getRoadLinkAndComplementaryFromVVH(oldAsset.linkId, newTransaction = false)
      .getOrElse(throw new IllegalStateException("Road link " + oldAsset.linkId + " no longer available"))
    expireAsset(oldAsset.typeId, oldAsset.id, LinearAssetTypes.VvhGenerated, expired = true, newTransaction = false)
    createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, oldAsset.value.get, adjustment.sideCode.value, Measures(oldAsset.startMeasure, oldAsset.endMeasure), LinearAssetTypes.VvhGenerated, vvhClient.roadLinkData.createVVHTimeStamp(), Some(roadLink), false, Some(LinearAssetTypes.VvhGenerated), None, oldAsset.verifiedBy, oldAsset.informationSource.map(_.value))
  }

}

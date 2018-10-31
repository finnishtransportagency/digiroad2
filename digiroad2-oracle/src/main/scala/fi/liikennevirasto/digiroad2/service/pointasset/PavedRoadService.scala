package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, VVHClient}
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MunicipalityDao, OracleAssetDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, MValueAdjustment, SideCodeAdjustment, VVHChangesAdjustment}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{DynamicLinearAssetService, LinearAssetTypes, Measures}
import fi.liikennevirasto.digiroad2.util.{LinearAssetUtils, PolygonTools}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils}
import org.joda.time.DateTime

import scala.slick.jdbc.{StaticQuery => Q}

class PavedRoadService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends DynamicLinearAssetService(roadLinkServiceImpl, eventBusImpl) {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def dao: OracleLinearAssetDao = new OracleLinearAssetDao(roadLinkServiceImpl.vvhClient, roadLinkServiceImpl)
  override def municipalityDao: MunicipalityDao = new MunicipalityDao
  override def eventBus: DigiroadEventBus = eventBusImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def polygonTools: PolygonTools = new PolygonTools()
  override def assetDao: OracleAssetDao = new OracleAssetDao
  override def dynamicLinearAssetDao: DynamicLinearAssetDao = new DynamicLinearAssetDao

  val defaultMultiTypePropSeq = DynamicAssetValue(Seq(DynamicProperty("paallysteluokka", "single_choice", required = false, Seq(DynamicPropertyValue("99")))))
  val defaultPropertyData = DynamicValue(defaultMultiTypePropSeq)

  override protected def getByRoadLinks(typeId: Int, roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo]): Seq[PieceWiseLinearAsset] = {
    val linkIds = roadLinks.map(_.linkId)
    val mappedChanges = LinearAssetUtils.getMappedChanges(changes)
    val removedLinkIds = LinearAssetUtils.deletedRoadLinkIds(mappedChanges, linkIds.toSet)
    val existingAssets =
      withDynTransaction {
        dynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(PavedRoad.typeId, linkIds ++ removedLinkIds)
      }.filterNot(_.expired)

    val timing = System.currentTimeMillis

    val (assetsOnChangedLinks, assetsWithoutChangedLinks) = existingAssets.partition(a => LinearAssetUtils.newChangeInfoDetected(a, mappedChanges))

    val projectableTargetRoadLinks = roadLinks.filter(rl => rl.linkType.value == UnknownLinkType.value || rl.isCarTrafficRoad)
    val (expiredIds, newAndUpdatedPavedRoadAssets) = getPavedRoadAssetChanges(existingAssets, roadLinks, changes, typeId)

    val initChangeSet = ChangeSet(droppedAssetIds = Set.empty[Long],
      expiredAssetIds = existingAssets.filter(asset => removedLinkIds.contains(asset.linkId)).map(_.id).toSet ++ expiredIds,
      adjustedMValues = Seq.empty[MValueAdjustment],
      adjustedVVHChanges = newAndUpdatedPavedRoadAssets.filter(_.id != 0).map( a => VVHChangesAdjustment(a.id, a.linkId, a.startMeasure, a.endMeasure, a.vvhTimeStamp)),
      adjustedSideCodes = Seq.empty[SideCodeAdjustment])

    val combinedAssets = existingAssets.filterNot(a => expiredIds.contains(a.id) || newAndUpdatedPavedRoadAssets.exists(_.id == a.id) || assetsWithoutChangedLinks.exists(_.id == a.id)
    ) ++ newAndUpdatedPavedRoadAssets

    val (projectedAssets, changedSet) = fillNewRoadLinksWithPreviousAssetsData(projectableTargetRoadLinks, combinedAssets, assetsOnChangedLinks, changes, initChangeSet)

    val newAssets = newAndUpdatedPavedRoadAssets.filterNot(a => projectedAssets.exists(f => f.linkId == a.linkId)) ++ projectedAssets

    if (newAssets.nonEmpty) {
      logger.info("Transferred %d assets in %d ms ".format(newAssets.length, System.currentTimeMillis - timing))
    }
    val groupedAssets = (existingAssets.filterNot(a => expiredIds.contains(a.id) || newAssets.exists(_.linkId == a.linkId)) ++ newAssets ++ assetsWithoutChangedLinks).groupBy(_.linkId)
    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(roadLinks, groupedAssets, typeId, Some(changedSet))

    publish(eventBus, changeSet, newAssets)
    filledTopology
  }

  override def publish(eventBus: DigiroadEventBus, changeSet: ChangeSet, assets: Seq[PersistedLinearAsset]) {
    eventBus.publish("dynamicAsset:update", changeSet)
    eventBus.publish("pavedRoad:saveProjectedPavedRoad", assets.filter(_.id == 0L))
  }

  def getPavedRoadAssetChanges(existingLinearAssets: Seq[PersistedLinearAsset], roadLinks: Seq[RoadLink],
                            changeInfos: Seq[ChangeInfo], typeId: Long): (Set[Long], Seq[PersistedLinearAsset]) = {

    //Group last vvhchanges by link id
    val lastChanges = changeInfos.filter(_.newId.isDefined).groupBy(_.newId.get).mapValues(c => c.maxBy(_.vvhTimeStamp))

    //Map all existing assets by roadlink and changeinfo
    val changedAssets = lastChanges.map{
      case (linkId, changeInfo) =>
        (roadLinks.find(_.linkId == linkId), changeInfo, existingLinearAssets.filter(_.linkId == linkId))
    }

    /* Note: This uses isNotPaved that excludes "unknown" pavement status. In OTH unknown means
    *  "no pavement" but in case OTH has pavement info with value 1 then VVH "unknown" should not affect OTH.
    *  Additionally, should there be an override that is later fixed we let the asset expire here as no
    *  override is needed anymore.
    */
    val expiredAssetsIds = changedAssets.flatMap {
      case (Some(roadlink), changeInfo, assets) =>
        if (roadlink.isNotPaved && assets.nonEmpty)
          assets.filter(_.vvhTimeStamp < changeInfo.vvhTimeStamp).map(_.id)
        else
          List()
      case _ =>
        List()
    }.toSet[Long]

    /* Note: This will not change anything if asset is stored using value None (null in database)
    *  This is the intended consequence as it enables the UI to write overrides to VVH pavement info */
    val newAndUpdatedAssets = changedAssets.flatMap{
      case (Some(roadlink), changeInfo, assets) =>
        if(roadlink.isPaved)
          if (assets.isEmpty)
            Some(PersistedLinearAsset(0L, roadlink.linkId, SideCode.BothDirections.value, Some(defaultPropertyData), 0,
              GeometryUtils.geometryLength(roadlink.geometry), None, None, None, None, false,
              PavedRoad.typeId, changeInfo.vvhTimeStamp, None, linkSource = roadlink.linkSource, None, None, Some(MmlNls)))
          else
            assets.filterNot(a => expiredAssetsIds.contains(a.id) ||
              (a.value.isEmpty || a.vvhTimeStamp >= changeInfo.vvhTimeStamp)
            ).map(a => a.copy(vvhTimeStamp = changeInfo.vvhTimeStamp, value=a.value,
              startMeasure=0.0, endMeasure=roadlink.length, informationSource = Some(MmlNls)))
        else
          None
      case _ =>
        None
    }.toSeq

    (expiredAssetsIds, newAndUpdatedAssets)
  }

  override protected def updateWithoutTransaction(ids: Seq[Long], value: Value, username: String, vvhTimeStamp: Option[Long] = None, sideCode: Option[Int] = None, measures: Option[Measures] = None, informationSource: Option[Int] = None): Seq[Long] = {
    if (ids.isEmpty)
      return ids

    ids.flatMap { id =>
      value match {
        case DynamicValue(multiTypeProps) =>
          updateValueByExpiration(id, DynamicValue(multiTypeProps), LinearAssetTypes.numericValuePropertyId, username, measures, vvhTimeStamp, sideCode, informationSource = informationSource)
        case _ =>
          Some(id)
      }
    }
  }

  override def create(newLinearAssets: Seq[NewLinearAsset], typeId: Int, username: String, vvhTimeStamp: Long = vvhClient.roadLinkData.createVVHTimeStamp()): Seq[Long] = {
    withDynTransaction {
      val roadlinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(newLinearAssets.map(_.linkId).toSet, false)
      newLinearAssets.flatMap { newAsset =>
        Some(createWithoutTransaction(typeId, newAsset.linkId, newAsset.value, newAsset.sideCode, Measures(newAsset.startMeasure, newAsset.endMeasure), username, vvhTimeStamp, roadlinks.find(_.linkId == newAsset.linkId), informationSource = Some(MunicipalityMaintenainer.value)))
      }
    }
  }

  override protected def updateValueByExpiration(assetId: Long, valueToUpdate: Value, valuePropertyId: String, username: String, measures: Option[Measures], vvhTimeStamp: Option[Long], sideCode: Option[Int], informationSource: Option[Int] = None): Option[Long] = {
    //Get Old Asset
    val oldAsset =
    valueToUpdate match {
      case DynamicValue(multiTypeProps) =>
        enrichPersistedLinearAssetProperties(dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(Set(assetId))).head
      case _ => return None
    }

    val measure = measures.getOrElse(Measures(oldAsset.startMeasure, oldAsset.endMeasure))

    //Expire the old asset
    dao.updateExpiration(assetId, expired = true, username)
    val roadLink = roadLinkService.getRoadLinkAndComplementaryFromVVH(oldAsset.linkId, newTransaction = false)
    if (valueToUpdate.toJson == 0 && measures.nonEmpty){
      Seq(Measures(oldAsset.startMeasure, measure.startMeasure), Measures(measure.endMeasure, oldAsset.endMeasure)).map {
        m =>
          if (m.endMeasure - m.startMeasure > 0.01)
            createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, valueToUpdate, sideCode.getOrElse(oldAsset.sideCode),
              m, username, vvhTimeStamp.getOrElse(vvhClient.roadLinkData.createVVHTimeStamp()), roadLink, true, oldAsset.createdBy, Some(oldAsset.createdDateTime.getOrElse(DateTime.now())), informationSource = informationSource)
      }
      Some(0L)
    }else {
      Some(createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, valueToUpdate, sideCode.getOrElse(oldAsset.sideCode),
        measure, username, vvhTimeStamp.getOrElse(vvhClient.roadLinkData.createVVHTimeStamp()), roadLink, true, oldAsset.createdBy, Some(oldAsset.createdDateTime.getOrElse(DateTime.now())), informationSource = informationSource))
    }
  }

  /**
    * Saves updated linear asset from UI. Used by Digiroad2Api /linearassets POST endpoint.
    */
  def update(ids: Seq[Long], value: Value, username: String): Seq[Long] = {
    withDynTransaction {
      updateWithoutTransaction(ids, value, username, informationSource = Some(MunicipalityMaintenainer.value))
    }
  }

  override def update(ids: Seq[Long], value: Value, username: String, vvhTimeStamp: Option[Long] = None, sideCode: Option[Int] = None, measures: Option[Measures] = None): Seq[Long] = {
    withDynTransaction {
      updateWithoutTransaction(ids, value, username, vvhTimeStamp, sideCode, measures, informationSource = Some(MunicipalityMaintenainer.value))
    }
  }

  override def split(id: Long, splitMeasure: Double, existingValue: Option[Value], createdValue: Option[Value], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit): Seq[Long] = {
    withDynTransaction {
      val linearAsset = enrichPersistedLinearAssetProperties(dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(Set(id))).head
      val roadLink = roadLinkService.getRoadLinkAndComplementaryFromVVH(linearAsset.linkId, newTransaction = false).getOrElse(throw new IllegalStateException("Road link no longer available"))
      municipalityValidation(roadLink.municipalityCode, roadLink.administrativeClass)

      val (existingLinkMeasures, createdLinkMeasures) = GeometryUtils.createSplit(splitMeasure, (linearAsset.startMeasure, linearAsset.endMeasure))

      val newIdsToReturn = existingValue match {
        case None => dao.updateExpiration(id, expired = true, username).toSeq
        case Some(value) => updateWithoutTransaction(Seq(id), value, username, measures = Some(Measures(existingLinkMeasures._1, existingLinkMeasures._2)), informationSource = Some(MunicipalityMaintenainer.value))
      }

      val createdIdOption = createdValue.map(
        createWithoutTransaction(linearAsset.typeId, linearAsset.linkId, _, linearAsset.sideCode, Measures(createdLinkMeasures._1, createdLinkMeasures._2), username, linearAsset.vvhTimeStamp,
          Some(roadLink), informationSource = Some(MunicipalityMaintenainer.value)))

      newIdsToReturn ++ Seq(createdIdOption).flatten
    }
  }
}

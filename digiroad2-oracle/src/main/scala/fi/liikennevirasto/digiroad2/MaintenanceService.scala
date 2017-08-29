package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.{UnknownLinkType, SideCode}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.MValueAdjustment
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.linearasset.oracle.{OracleLinearAssetDao, OracleMaintenanceDao}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Queries
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.{LinearAssetUtils, PolygonTools}
import org.joda.time.DateTime

class MaintenanceService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends LinearAssetOperations {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def dao: OracleLinearAssetDao = new OracleLinearAssetDao(roadLinkServiceImpl.vvhClient, roadLinkServiceImpl)
  override def eventBus: DigiroadEventBus = eventBusImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def polygonTools: PolygonTools = new PolygonTools()
  def maintenanceDAO: OracleMaintenanceDao = new OracleMaintenanceDao(roadLinkServiceImpl.vvhClient, roadLinkServiceImpl)

  val maintenanceRoadAssetTypeId: Int = 290

  /*
 * Creates new Maintenance asset and updates existing. Used by the Digiroad2Context.LinearAssetSaveProjected actor.
 */
  override def persistProjectedLinearAssets(newMaintenanceAssets: Seq[PersistedLinearAsset]): Unit = {
    if (newMaintenanceAssets.nonEmpty)
      logger.info("Saving projected Maintenance assets")

    val (toInsert, toUpdate) = newMaintenanceAssets.partition(_.id == 0L)
    withDynTransaction {
        val persisted = Map.empty[Long, Seq[PersistedLinearAsset]]
        updateProjected(toUpdate, persisted)
      if (newMaintenanceAssets.nonEmpty)
        logger.info("Updated ids/linkids " + toUpdate.map(a => (a.id, a.linkId)))

      toInsert.foreach { maintenanceAsset =>
        val id = maintenanceDAO.createLinearAsset(maintenanceAsset.typeId, maintenanceAsset.linkId, maintenanceAsset.expired, maintenanceAsset.sideCode,
          Measures(maintenanceAsset.startMeasure, maintenanceAsset.endMeasure), maintenanceAsset.createdBy.getOrElse(LinearAssetTypes.VvhGenerated), maintenanceAsset.vvhTimeStamp, getLinkSource(maintenanceAsset.linkId))
        maintenanceAsset.value match {
          case Some(maintenanceRoad) =>
            maintenanceDAO.insertMaintenanceRoadValue(id, maintenanceRoad.asInstanceOf[MaintenanceRoad])
          case None => None
        }
      }
      if (newMaintenanceAssets.nonEmpty)
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
    toUpdate.foreach { maintenanceAsset =>
      val persistedLinearAsset = persisted.getOrElse(maintenanceAsset.id, Seq()).headOption
      val id = maintenanceAsset.id
      if (valueChanged(maintenanceAsset, persistedLinearAsset)) {
        maintenanceAsset.value match {
          case Some(maintenance) =>
            maintenanceDAO.updateMaintenanceRoadValue(id, maintenance.asInstanceOf[MaintenanceRoad], LinearAssetTypes.VvhGenerated)
          case _ => None
        }
        if (mValueChanged(maintenanceAsset, persistedLinearAsset)) dao.updateMValues(maintenanceAsset.id, (maintenanceAsset.startMeasure, maintenanceAsset.endMeasure), maintenanceAsset.vvhTimeStamp)
        if (sideCodeChanged(maintenanceAsset, persistedLinearAsset)) dao.updateSideCode(maintenanceAsset.id, SideCode(maintenanceAsset.sideCode))
      }
    }
  }

  /**
    * Mark VALID_TO field of old asset to sysdate and create a new asset.
    * Copy all the data from old asset except the properties that changed, modifiedBy and modifiedAt.
    */
  override protected def updateValueByExpiration(assetId: Long, valueToUpdate: Value, valuePropertyId: String, username: String, measures: Option[Measures]): Option[Long] = {
    //Get Old Asset
    val oldAsset = maintenanceDAO.fetchMaintenancesByIds(maintenanceRoadAssetTypeId, Set(assetId)).head

    //Expire the old asset
    dao.updateExpiration(assetId, expired = true, username)

    //Create New Asset
    val newAssetIDcreate = createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, valueToUpdate, oldAsset.sideCode,
      measures.getOrElse(Measures(oldAsset.startMeasure, oldAsset.endMeasure)), username, vvhClient.roadLinkData.createVVHTimeStamp(), getLinkSource(oldAsset.linkId), true, oldAsset.createdBy, oldAsset.createdDateTime)

    Some(newAssetIDcreate)
  }

  override protected def updateWithoutTransaction(ids: Seq[Long], value: Value, username: String, measures: Option[Measures] = None): Seq[Long] = {
    if (ids.isEmpty)
      return ids

    ids.flatMap { id =>
      val missingProperties = validateRequiredProperties(value.asInstanceOf[MaintenanceRoad])
      if (missingProperties.nonEmpty)
        throw new MissingMandatoryPropertyException(missingProperties)
      updateValueByExpiration(id, value.asInstanceOf[MaintenanceRoad], maintenanceRoadAssetTypeId.toString(), username, measures)
    }
  }

  override protected def createWithoutTransaction(typeId: Int, linkId: Long, value: Value, sideCode: Int, measures: Measures, username: String, vvhTimeStamp: Long, linkSource: Option[Int], fromUpdate: Boolean = false,
                                       createdByFromUpdate: Option[String] = Some(""),
                                       createdDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now())): Long = {
    val id = maintenanceDAO.createLinearAsset(maintenanceRoadAssetTypeId, linkId, expired = false, sideCode, measures, username,
      vvhTimeStamp, linkSource, fromUpdate, createdByFromUpdate, createdDateTimeFromUpdate)

    val missingProperties = validateRequiredProperties(value.asInstanceOf[MaintenanceRoad])
    if (missingProperties.nonEmpty)
      throw new MissingMandatoryPropertyException(missingProperties)

    maintenanceDAO.insertMaintenanceRoadValue(id, value.asInstanceOf[MaintenanceRoad])
    id
  }

  private def validateRequiredProperties(maintenanceRoad: MaintenanceRoad): Set[String] = {
    val mandatoryProperties: Map[String, String] = maintenanceDAO.getMaintenanceRequiredProperties(maintenanceRoadAssetTypeId)
    val nonEmptyMandatoryProperties: Seq[Properties] = maintenanceRoad.maintenanceRoad.filter { property =>
      mandatoryProperties.contains(property.publicId) && property.value.nonEmpty
    }
    mandatoryProperties.keySet -- nonEmptyMandatoryProperties.map(_.publicId).toSet
  }

  def getActiveMaintenanceRoadByPolygon(areaId: Int): Seq[PersistedLinearAsset] = {
    val polygon = polygonTools.getPolygonByArea(areaId)
    val vVHLinkIds = roadLinkService.getLinkIdsFromVVHWithComplementaryByPolygons(polygon)
    getPersistedAssetsByLinkIds(vVHLinkIds)
  }


  override protected def getByRoadLinks(typeId: Int, roadLinksExist: Seq[RoadLink], changes: Seq[ChangeInfo]): Seq[PieceWiseLinearAsset] = {

    // Filter high functional classes from maintenance roads
    val roadLinks: Seq[RoadLink] = roadLinksExist.filter(_.functionalClass > 4)
    val linkIds = roadLinks.map(_.linkId)
    val removedLinkIds = LinearAssetUtils.deletedRoadLinkIds(changes, roadLinks)
    val existingAssets =
      withDynTransaction {
        maintenanceDAO.fetchMaintenancesByLinkIds(maintenanceRoadAssetTypeId, linkIds ++ removedLinkIds, includeFloating = false).filterNot(_.expired)
      }

    val (assetsOnChangedLinks, assetsWithoutChangedLinks) = existingAssets.partition(a => LinearAssetUtils.newChangeInfoDetected(a, changes))

    val projectableTargetRoadLinks = roadLinks.filter(
      rl => rl.linkType.value == UnknownLinkType.value || rl.isCarTrafficRoad)

    val timing = System.currentTimeMillis

    val (expiredPavingAssetIds, newAndUpdatedPavingAssets) = getPavingAssetChanges(existingAssets, roadLinks, changes, maintenanceRoadAssetTypeId)

    val combinedAssets = existingAssets.filterNot(
      a => expiredPavingAssetIds.contains(a.id) || newAndUpdatedPavingAssets.exists(_.id == a.id) || assetsWithoutChangedLinks.exists(_.id == a.id)
    ) ++ newAndUpdatedPavingAssets

    val filledNewAssets = fillNewRoadLinksWithPreviousAssetsData(projectableTargetRoadLinks,
      combinedAssets, assetsOnChangedLinks, changes) ++ assetsWithoutChangedLinks

    val newAssets = newAndUpdatedPavingAssets.filterNot(a => filledNewAssets.exists(f => f.linkId == a.linkId)) ++ filledNewAssets

    if (newAssets.nonEmpty) {
      logger.info("Transferred %d assets in %d ms ".format(newAssets.length, System.currentTimeMillis - timing))
    }
    val groupedAssets = (existingAssets.filterNot(a => expiredPavingAssetIds.contains(a.id) || newAssets.exists(_.linkId == a.linkId)) ++ newAssets).groupBy(_.linkId)
    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(roadLinks, groupedAssets, maintenanceRoadAssetTypeId)

    val expiredAssetIds = existingAssets.filter(asset => removedLinkIds.contains(asset.linkId)).map(_.id).toSet ++
      changeSet.expiredAssetIds ++ expiredPavingAssetIds

    val mValueAdjustments = newAndUpdatedPavingAssets.filter(_.id != 0).map( a =>
      MValueAdjustment(a.id, a.linkId, a.startMeasure, a.endMeasure)
    )
    eventBus.publish("linearAssets:update", changeSet.copy(expiredAssetIds = expiredAssetIds.filterNot(_ == 0L),
      adjustedMValues = changeSet.adjustedMValues ++ mValueAdjustments))

    //Remove the asset ids ajusted in the "linearAssets:update" otherwise if the "linearAssets:saveProjectedLinearAssets" is executed after the "linearAssets:update"
    //it will update the mValues to the previous ones
    eventBus.publish("linearAssets:saveProjectedLinearAssets", newAssets.filterNot(a => changeSet.adjustedMValues.exists(_.assetId == a.id)))

    filledTopology
  }


  /**
    * Returns Maintenance assets by asset type and asset ids.
    */
  override def getPersistedAssetsByIds(typeId: Int, ids: Set[Long]): Seq[PersistedLinearAsset] = {
    withDynTransaction {
      maintenanceDAO.fetchMaintenancesByIds(maintenanceRoadAssetTypeId, ids)
    }
  }

  def getPersistedAssetsByLinkIds(linkIds: Seq[Long]): Seq[PersistedLinearAsset] = {
    withDynTransaction {
      maintenanceDAO.fetchMaintenancesByLinkIds(maintenanceRoadAssetTypeId, linkIds, includeExpire = false)
    }
  }

  override def getUncheckedLinearAssets(areas: Option[Set[Int]]): Map[String, Map[String ,List[Long]]] ={
    val unchecked = withDynTransaction {
      maintenanceDAO.getUncheckedMaintenanceRoad(areas)

    }.groupBy(_._2).mapValues(x => x.map(_._1))
    Map("Unchecked" -> unchecked )
  }
}

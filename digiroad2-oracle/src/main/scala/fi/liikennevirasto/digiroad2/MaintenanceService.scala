package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, SideCode, UnknownLinkType}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.MValueAdjustment
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.linearasset.oracle.{OracleLinearAssetDao, OracleMaintenanceDao}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Queries
import fi.liikennevirasto.digiroad2.util.{LinearAssetUtils, PolygonTools}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database.dynamicSession

class MaintenanceService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends LinearAssetOperations {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def dao: OracleLinearAssetDao = new OracleLinearAssetDao(roadLinkServiceImpl.vvhClient, roadLinkServiceImpl)
  override def eventBus: DigiroadEventBus = eventBusImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def polygonTools: PolygonTools = new PolygonTools()
  def maintenanceDAO: OracleMaintenanceDao = new OracleMaintenanceDao(roadLinkServiceImpl.vvhClient, roadLinkServiceImpl)

  val maintenanceRoadAssetTypeId: Int = 290

  /*
 * Creates new Maintenance asset and updates existing. Used by the Digiroad2Context.MaintenanceRoadSaveProjected actor.
 */
  override def persistProjectedLinearAssets(newMaintenanceAssets: Seq[PersistedLinearAsset]): Unit = {
    if (newMaintenanceAssets.nonEmpty)
      logger.info("Saving projected Maintenance assets")

    val (toInsert, toUpdate) = newMaintenanceAssets.partition(_.id == 0L)
    withDynTransaction {
        val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(newMaintenanceAssets.map(_.linkId).toSet, newTransaction = false)
        val persisted = maintenanceDAO.fetchMaintenancesByIds(maintenanceRoadAssetTypeId, toUpdate.map(_.id).toSet).groupBy(_.id)
        updateProjected(toUpdate, persisted)
      if (newMaintenanceAssets.nonEmpty)
        logger.info("Updated ids/linkids " + toUpdate.map(a => (a.id, a.linkId)))

      toInsert.foreach { maintenanceAsset =>

        val area = getAssetArea(roadLinks.find(_.linkId == maintenanceAsset.linkId), Measures(maintenanceAsset.startMeasure, maintenanceAsset.endMeasure))
        val id = maintenanceDAO.createLinearAsset(maintenanceAsset.typeId, maintenanceAsset.linkId, maintenanceAsset.expired, maintenanceAsset.sideCode,
          Measures(maintenanceAsset.startMeasure, maintenanceAsset.endMeasure), maintenanceAsset.createdBy.getOrElse(LinearAssetTypes.VvhGenerated), maintenanceAsset.vvhTimeStamp, getLinkSource(roadLinks.find(_.linkId == maintenanceAsset.linkId)), area = area)
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
      }
        if (mValueChanged(maintenanceAsset, persistedLinearAsset)) dao.updateMValues(maintenanceAsset.id, (maintenanceAsset.startMeasure, maintenanceAsset.endMeasure), maintenanceAsset.vvhTimeStamp)
        if (sideCodeChanged(maintenanceAsset, persistedLinearAsset)) dao.updateSideCode(maintenanceAsset.id, SideCode(maintenanceAsset.sideCode))
    }
  }

  /**
    * Mark VALID_TO field of old asset to sysdate and create a new asset.
    * Copy all the data from old asset except the properties that changed, modifiedBy and modifiedAt.
    */
  protected def updateValueByExpiration(assetIds: Seq[Long], valueToUpdate: Value, valuePropertyId: String, username: String, measures: Option[Measures]): Seq[Long] = {
    //Get Old Assets
    val oldAssets = maintenanceDAO.fetchMaintenancesByIds(maintenanceRoadAssetTypeId, assetIds.toSet)
    val roadlinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(oldAssets.map(_.linkId).toSet, false)

    oldAssets.map { oldAsset =>
      //Expire the old asset
      dao.updateExpiration(oldAsset.id, expired = true, username)

    //Create New Asset
       createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, valueToUpdate, oldAsset.sideCode, measures.getOrElse(Measures(oldAsset.startMeasure, oldAsset.endMeasure)),
         username, vvhClient.roadLinkData.createVVHTimeStamp(), roadlinks.find(_.linkId == oldAsset.linkId), true, oldAsset.createdBy, oldAsset.createdDateTime)
    }
  }

  override protected def updateWithoutTransaction(ids: Seq[Long], value: Value, username: String, measures: Option[Measures] = None, vvhTimeStamp: Option[Long]= None, sideCode: Option[Int]= None): Seq[Long] = {
    if (ids.isEmpty)
      return ids

      val missingProperties = validateRequiredProperties(value.asInstanceOf[MaintenanceRoad])
      if (missingProperties.nonEmpty)
        throw new MissingMandatoryPropertyException(missingProperties)

    updateValueByExpiration(ids, value.asInstanceOf[MaintenanceRoad], maintenanceRoadAssetTypeId.toString(), username, measures)
  }


  /**
    * Saves new linear assets from UI. Used by Digiroad2Api /linearassets POST endpoint.
    */
  override def create(newLinearAssets: Seq[NewLinearAsset], typeId: Int, username: String, vvhTimeStamp: Long = vvhClient.roadLinkData.createVVHTimeStamp()): Seq[Long] = {
    val roadlink = roadLinkService.getRoadLinksAndComplementariesFromVVH(newLinearAssets.map(_.linkId).toSet)
    withDynTransaction {
      newLinearAssets.map { newAsset =>
        createWithoutTransaction(typeId, newAsset.linkId, newAsset.value, newAsset.sideCode, Measures(newAsset.startMeasure, newAsset.endMeasure), username, vvhClient.roadLinkData.createVVHTimeStamp(), roadlink.find(_.linkId == newAsset.linkId))
      }
    }
  }

  override def createWithoutTransaction(typeId: Int, linkId: Long, value: Value, sideCode: Int, measures: Measures, username: String, vvhTimeStamp: Long, roadLink: Option[RoadLinkLike], fromUpdate: Boolean = false,
                                        createdByFromUpdate: Option[String] = Some(""),
                                        createdDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()), verifiedBy: Option[String] = None): Long = {

    val area = getAssetArea(roadLink, measures)
    val id = maintenanceDAO.createLinearAsset(maintenanceRoadAssetTypeId, linkId, expired = false, sideCode, measures, username,
      vvhTimeStamp, getLinkSource(roadLink), fromUpdate, createdByFromUpdate, createdDateTimeFromUpdate, area = area)

    val missingProperties = validateRequiredProperties(value.asInstanceOf[MaintenanceRoad])
    if (missingProperties.nonEmpty)
      throw new MissingMandatoryPropertyException(missingProperties)

    maintenanceDAO.insertMaintenanceRoadValue(id, value.asInstanceOf[MaintenanceRoad])
    id
  }

  private def validateRequiredProperties(maintenanceRoad: MaintenanceRoad): Set[String] = {
    val mandatoryProperties: Map[String, String] = maintenanceDAO.getMaintenanceRequiredProperties(maintenanceRoadAssetTypeId)
    val nonEmptyMandatoryProperties: Seq[Properties] = maintenanceRoad.properties.filter { property =>
      mandatoryProperties.contains(property.publicId) && property.value.nonEmpty
    }
    mandatoryProperties.keySet -- nonEmptyMandatoryProperties.map(_.publicId).toSet
  }

  def getActiveMaintenanceRoadByPolygon(areaId: Int): Seq[PersistedLinearAsset] = {
    val polygon = polygonTools.getPolygonByArea(areaId)
    val vVHLinkIds = roadLinkService.getLinkIdsFromVVHWithComplementaryByPolygons(polygon)
    getPersistedAssetsByLinkIds(vVHLinkIds)
  }

  def getAssetArea(roadLink: Option[RoadLinkLike], measures: Measures, area: Option[Seq[Int]] = None): Int = {
    roadLink match {
      case Some(road) => polygonTools.getAreaByGeometry(road.geometry, measures, area)
      case None => throw new NoSuchElementException
    }
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
    val combinedAssets = existingAssets.filterNot(a => assetsWithoutChangedLinks.exists(_.id == a.id))

    val newAssets = fillNewRoadLinksWithPreviousAssetsData(projectableTargetRoadLinks,
      combinedAssets, assetsOnChangedLinks, changes) ++ assetsWithoutChangedLinks

    if (newAssets.nonEmpty) {
      logger.info("Transferred %d assets in %d ms ".format(newAssets.length, System.currentTimeMillis - timing))
    }
    val groupedAssets = (existingAssets.filterNot(a => newAssets.exists(_.linkId == a.linkId)) ++ newAssets).groupBy(_.linkId)
    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(roadLinks, groupedAssets, maintenanceRoadAssetTypeId)

    val expiredAssetIds = existingAssets.filter(asset => removedLinkIds.contains(asset.linkId)).map(_.id).toSet ++
      changeSet.expiredAssetIds

    eventBus.publish("linearAssets:update", changeSet.copy(expiredAssetIds = expiredAssetIds.filterNot(_ == 0L)))

    //Remove the asset ids ajusted in the "maintenanceRoads:update" otherwise if the "maintenanceRoads:saveProjectedLinearAssets" is executed after the "maintenanceRoads:update"
    //it will update the mValues to the previous ones
    eventBus.publish("maintenanceRoads:saveProjectedMaintenanceRoads", newAssets.filterNot(a => changeSet.adjustedMValues.exists(_.assetId == a.id)))

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

  def getAllByBoundingBox(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[(PersistedLinearAsset, RoadLink)] = {
    val (roadLinks, change) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(bounds, municipalities)
    val linkIds = roadLinks.map(_.linkId)
    getPersistedAssetsByLinkIds(linkIds).map { asset => (asset, roadLinks.find(r => r.linkId == asset.linkId).getOrElse(throw new NoSuchElementException)) }
  }

  /**
    * Saves linear asset when linear asset is split to two parts in UI (scissors icon). Used by Digiroad2Api /linearassets/:id POST endpoint.
    */
  override def split(id: Long, splitMeasure: Double, existingValue: Option[Value], createdValue: Option[Value], username: String, municipalityValidation: (Int) => Unit): Seq[Long] = {
    withDynTransaction {
      val linearAsset = maintenanceDAO.fetchMaintenancesByIds(maintenanceRoadAssetTypeId, Set(id)).head
      val roadLink = roadLinkService.getRoadLinkAndComplementaryFromVVH(linearAsset.linkId, false).getOrElse(throw new IllegalStateException("Road link no longer available"))

      Queries.updateAssetModified(id, username).execute

      val (existingLinkMeasures, createdLinkMeasures) = GeometryUtils.createSplit(splitMeasure, (linearAsset.startMeasure, linearAsset.endMeasure))

      val newIdsToReturn = existingValue match {
        case None => dao.updateExpiration(id, expired = true, username).toSeq
        case Some(value) => updateWithoutTransaction(Seq(id), value, username, Some(Measures(existingLinkMeasures._1, existingLinkMeasures._2)))
      }

      val createdIdOption = createdValue.map(createWithoutTransaction(linearAsset.typeId, linearAsset.linkId, _, linearAsset.sideCode, Measures(createdLinkMeasures._1, createdLinkMeasures._2), username, linearAsset.vvhTimeStamp,
        Some(roadLink)))

      newIdsToReturn ++ Seq(createdIdOption).flatten
    }
  }
}

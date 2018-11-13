package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, BoundingRectangle, MaintenanceRoadAsset, SideCode, UnknownLinkType}
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, VVHClient}
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, OracleAssetDao, Queries}
import fi.liikennevirasto.digiroad2.dao.linearasset.{OracleLinearAssetDao, OracleMaintenanceDao}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{LinearAssetUtils, PolygonTools}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, MValueAdjustment, SideCodeAdjustment, VVHChangesAdjustment}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database.dynamicSession

class MaintenanceService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends LinearAssetOperations {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def dao: OracleLinearAssetDao = new OracleLinearAssetDao(roadLinkServiceImpl.vvhClient, roadLinkServiceImpl)
  override def municipalityDao: MunicipalityDao = new MunicipalityDao
  override def eventBus: DigiroadEventBus = eventBusImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def polygonTools: PolygonTools = new PolygonTools()
  override def assetDao: OracleAssetDao = new OracleAssetDao
  def maintenanceDAO: OracleMaintenanceDao = new OracleMaintenanceDao(roadLinkServiceImpl.vvhClient, roadLinkServiceImpl)
  override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()) = throw new UnsupportedOperationException("Not supported method")

  val maintenanceRoadAssetTypeId: Int = 290

  override def getByBoundingBox(typeId: Int, bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[Seq[PieceWiseLinearAsset]] = {
    val (roadLinks, change) = roadLinkService.getRoadLinksAndChangesFromVVH(bounds, municipalities)
    val linearAssets = getByRoadLinks(typeId, roadLinks, change)
    val assetsWithAttributes = enrichMaintenanceRoadAttributes(linearAssets, roadLinks)

    LinearAssetPartitioner.partition(assetsWithAttributes, roadLinks.groupBy(_.linkId).mapValues(_.head))
  }

  override def getComplementaryByBoundingBox(typeId: Int, bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[Seq[PieceWiseLinearAsset]] = {
    val (roadLinks, change) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(bounds, municipalities)
    val linearAssets = getByRoadLinks(typeId, roadLinks, change)
    val assetsWithAttributes = enrichMaintenanceRoadAttributes(linearAssets, roadLinks)
    LinearAssetPartitioner.partition(assetsWithAttributes, roadLinks.groupBy(_.linkId).mapValues(_.head))
  }

  private def addPolygonAreaAttribute(linearAsset: PieceWiseLinearAsset, roadLink: RoadLink): PieceWiseLinearAsset = {
    val area = polygonTools.getAreaByGeometry(roadLink.geometry, Measures(linearAsset.startMeasure, linearAsset.endMeasure), None)
    linearAsset.copy(attributes = linearAsset.attributes ++ Map("area" -> area))
  }

  private def enrichMaintenanceRoadAttributes(linearAssets: Seq[PieceWiseLinearAsset], roadLinks: Seq[RoadLink]): Seq[PieceWiseLinearAsset] = {
    val maintenanceRoadAttributeOperations: Seq[(PieceWiseLinearAsset, RoadLink) => PieceWiseLinearAsset] = Seq(
      addPolygonAreaAttribute
      //In the future if we need to add more attributes just add a method here
    )

    val linkData = roadLinks.map(rl => (rl.linkId, rl)).toMap

    linearAssets.map(linearAsset =>
      maintenanceRoadAttributeOperations.foldLeft(linearAsset) { case (asset, operation) =>
        linkData.get(asset.linkId).map{
          roadLink =>
            operation(asset, roadLink)
        }.getOrElse(asset)
      }
    )
  }

  /*
 * Creates new Maintenance asset and updates existing. Used by the Digiroad2Context.MaintenanceRoadSaveProjected actor.
 */
  override def persistProjectedLinearAssets(newMaintenanceAssets: Seq[PersistedLinearAsset]): Unit = {
    if (newMaintenanceAssets.nonEmpty)
      logger.info("Saving projected Maintenance assets")

    val (toInsert, toUpdate) = newMaintenanceAssets.partition(_.id == 0L)
    withDynTransaction {
        val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(newMaintenanceAssets.map(_.linkId).toSet, newTransaction = false)
      if(toUpdate.nonEmpty) {
        val persisted = maintenanceDAO.fetchMaintenancesByIds(MaintenanceRoadAsset.typeId, toUpdate.map(_.id).toSet).groupBy(_.id)
        updateProjected(toUpdate, persisted)
        if (newMaintenanceAssets.nonEmpty)
          logger.info("Updated ids/linkids " + toUpdate.map(a => (a.id, a.linkId)))
      }
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
    }
  }

  /**
    * Mark VALID_TO field of old asset to sysdate and create a new asset.
    * Copy all the data from old asset except the properties that changed, modifiedBy and modifiedAt.
    */
  protected def updateServiceRoad(assetIds: Seq[Long], valueToUpdate: Value, valuePropertyId: String, username: String, measures: Option[Measures], sideCode: Option[Int] = None): Seq[Long] = {
    //Get Old Assets
    val oldAssets = maintenanceDAO.fetchMaintenancesByIds(MaintenanceRoadAsset.typeId, assetIds.toSet)

    oldAssets.foreach { oldAsset =>
      val newMeasures = measures.getOrElse(Measures(oldAsset.startMeasure, oldAsset.endMeasure))
      val newSideCode = sideCode.getOrElse(oldAsset.sideCode)
      val roadLink = vvhClient.fetchRoadLinkByLinkId(oldAsset.linkId).getOrElse(throw new IllegalStateException("Road link no longer available"))

      if ((validateMinDistance(newMeasures.startMeasure, oldAsset.startMeasure) || validateMinDistance(newMeasures.endMeasure, oldAsset.endMeasure)) || newSideCode != oldAsset.sideCode) {
        dao.updateExpiration(oldAsset.id)
        Some(createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, valueToUpdate, newSideCode, newMeasures, username, vvhClient.roadLinkData.createVVHTimeStamp(), Some(roadLink), fromUpdate = true, createdByFromUpdate = Some(username), verifiedBy = oldAsset.verifiedBy))
      }
      else
        maintenanceDAO.updateValues(oldAsset.id, valueToUpdate.asInstanceOf[MaintenanceRoad], username)
    }
    assetIds
  }

  override protected def updateWithoutTransaction(ids: Seq[Long], value: Value, username: String, vvhTimeStamp: Option[Long] = None, sideCode: Option[Int] = None, measures: Option[Measures] = None,  informationSource: Option[Int] = None): Seq[Long] = {
    if (ids.isEmpty)
      return ids

      val missingProperties = validateRequiredProperties(value.asInstanceOf[MaintenanceRoad])
      if (missingProperties.nonEmpty)
        throw new MissingMandatoryPropertyException(missingProperties)

    updateServiceRoad(ids, value.asInstanceOf[MaintenanceRoad], MaintenanceRoadAsset.typeId.toString(), username, measures, sideCode)
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
                                        createdDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()), verifiedBy: Option[String] = None, informationSource: Option[Int] = None): Long = {

    val area = getAssetArea(roadLink, measures)
    val id = maintenanceDAO.createLinearAsset(MaintenanceRoadAsset.typeId, linkId, expired = false, sideCode, measures, username,
      vvhTimeStamp, getLinkSource(roadLink), fromUpdate, createdByFromUpdate, createdDateTimeFromUpdate, area = area)

    val missingProperties = validateRequiredProperties(value.asInstanceOf[MaintenanceRoad])
    if (missingProperties.nonEmpty)
      throw new MissingMandatoryPropertyException(missingProperties)

    maintenanceDAO.insertMaintenanceRoadValue(id, value.asInstanceOf[MaintenanceRoad])
    id
  }

  def createWithHistory(typeId: Int, linkId: Long, value: Value, sideCode: Int, measures: Measures, username: String, roadLink: Option[RoadLinkLike]): Long = {
    withDynTransaction {
      maintenanceDAO.expireMaintenanceAssetsByLinkids(Seq(linkId), typeId)
      createWithoutTransaction(typeId, linkId, value, sideCode, measures, username, vvhClient.roadLinkData.createVVHTimeStamp(), roadLink)
    }
  }

  private def validateRequiredProperties(maintenanceRoad: MaintenanceRoad): Set[String] = {
    val mandatoryProperties: Map[String, String] = maintenanceDAO.getMaintenanceRequiredProperties(MaintenanceRoadAsset.typeId)
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

  override protected def getByRoadLinks(typeId: Int, roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo]): Seq[PieceWiseLinearAsset] = {

    // Filter high functional classes from maintenance roads
    val roads: Seq[RoadLink] = roadLinks.filter(_.functionalClass > 4)
    val mappedChanges = LinearAssetUtils.getMappedChanges(changes)
    val linkIds = roads.map(_.linkId)
    val removedLinkIds = LinearAssetUtils.deletedRoadLinkIds(mappedChanges, linkIds.toSet)
    val existingAssets =
      withDynTransaction {
        maintenanceDAO.fetchMaintenancesByLinkIds(MaintenanceRoadAsset.typeId, linkIds ++ removedLinkIds, includeFloating = false).filterNot(_.expired)
      }

    val (assetsOnChangedLinks, assetsWithoutChangedLinks) = existingAssets.partition(a => LinearAssetUtils.newChangeInfoDetected(a, mappedChanges))
    val projectableTargetRoadLinks = roads.filter(
      rl => rl.linkType.value == UnknownLinkType.value || rl.isCarTrafficRoad)

    val timing = System.currentTimeMillis
    val combinedAssets = existingAssets.filterNot(a => assetsWithoutChangedLinks.exists(_.id == a.id))

    val initChangeSet = ChangeSet(droppedAssetIds = Set.empty[Long],
                                  expiredAssetIds = existingAssets.filter(asset => removedLinkIds.contains(asset.linkId)).map(_.id).toSet.filterNot( _ == 0L),
                                  adjustedMValues = Seq.empty[MValueAdjustment],
                                  adjustedVVHChanges = Seq.empty[VVHChangesAdjustment],
                                  adjustedSideCodes = Seq.empty[SideCodeAdjustment])

    val (newAssets, changedSet) = fillNewRoadLinksWithPreviousAssetsData(projectableTargetRoadLinks,
      combinedAssets, assetsOnChangedLinks, changes, initChangeSet)

    if (newAssets.nonEmpty) {
      logger.info("Transferred %d assets in %d ms ".format(newAssets.length, System.currentTimeMillis - timing))
    }
    val groupedAssets = (existingAssets.filterNot(a => newAssets.exists(_.linkId == a.linkId)) ++ newAssets ++ assetsWithoutChangedLinks).groupBy(_.linkId)
    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(roads, groupedAssets, MaintenanceRoadAsset.typeId, Some(changedSet))

    eventBus.publish("linearAssets:update", changeSet)
    eventBus.publish("maintenanceRoads:saveProjectedMaintenanceRoads", newAssets.filter(_.id == 0L))

    filledTopology
  }
  /**
    * Returns Maintenance assets by asset type and asset ids.
    */
  override def getPersistedAssetsByIds(typeId: Int, ids: Set[Long], newTransaction: Boolean = true): Seq[PersistedLinearAsset] = {
    if(newTransaction)
      withDynTransaction {
        maintenanceDAO.fetchMaintenancesByIds(MaintenanceRoadAsset.typeId, ids)
      }
    else
      maintenanceDAO.fetchMaintenancesByIds(MaintenanceRoadAsset.typeId, ids)
  }

  def getPersistedAssetsByLinkIds(linkIds: Seq[Long]): Seq[PersistedLinearAsset] = {
    withDynTransaction {
      maintenanceDAO.fetchMaintenancesByLinkIds(MaintenanceRoadAsset.typeId, linkIds, includeExpire = false)
    }
  }

  def getPotencialServiceAssets: Seq[PersistedLinearAsset] = {
    withDynTransaction {
      maintenanceDAO.fetchPotentialServiceRoads()
    }
  }

  def getByZoomLevel :Seq[Seq[PieceWiseLinearAsset]] = {
    val linearAssets  = getPotencialServiceAssets
    val roadLinks = roadLinkService.getRoadLinksByLinkIdsFromVVH(linearAssets.map(_.linkId).toSet)
    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(roadLinks, linearAssets.groupBy(_.linkId),MaintenanceRoadAsset.typeId , Some(ChangeSet(Set.empty, Nil,Nil, Nil,Set.empty)))
    LinearAssetPartitioner.partition(filledTopology.filter(_.value.isDefined), roadLinks.groupBy(_.linkId).mapValues(_.head))
  }

  def getWithComplementaryByZoomLevel :Seq[Seq[PieceWiseLinearAsset]]= {
    val linearAssets  = getPotencialServiceAssets
    val roadLinks = roadLinkService.getRoadLinksAndComplementaryByLinkIdsFromVVH(linearAssets.map(_.linkId).toSet)
    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(roadLinks, linearAssets.groupBy(_.linkId),MaintenanceRoadAsset.typeId , Some(ChangeSet(Set.empty, Nil,Nil, Nil,Set.empty)))
    LinearAssetPartitioner.partition(filledTopology.filter(_.value.isDefined), roadLinks.groupBy(_.linkId).mapValues(_.head))
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
    * Saves linear asset when linear asset is split to two parts in UI (scissors icon).
    */
  override def split(id: Long, splitMeasure: Double, existingValue: Option[Value], createdValue: Option[Value], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit): Seq[Long] = {
    withDynTransaction {
      val linearAsset = maintenanceDAO.fetchMaintenancesByIds(MaintenanceRoadAsset.typeId, Set(id)).head
      val roadLink = roadLinkService.getRoadLinkAndComplementaryFromVVH(linearAsset.linkId, false).getOrElse(throw new IllegalStateException("Road link no longer available"))

      val (existingLinkMeasures, createdLinkMeasures) = GeometryUtils.createSplit(splitMeasure, (linearAsset.startMeasure, linearAsset.endMeasure))
      dao.updateExpiration(id)

      val existingId = existingValue.map(createWithoutTransaction(linearAsset.typeId, linearAsset.linkId, _, linearAsset.sideCode, Measures(existingLinkMeasures._1, existingLinkMeasures._2), username, linearAsset.vvhTimeStamp, Some(roadLink),fromUpdate = true,  createdByFromUpdate = linearAsset.createdBy, createdDateTimeFromUpdate = linearAsset.createdDateTime))
      val createdId = createdValue.map(createWithoutTransaction(linearAsset.typeId, linearAsset.linkId, _, linearAsset.sideCode, Measures(createdLinkMeasures._1, createdLinkMeasures._2), username, linearAsset.vvhTimeStamp, Some(roadLink), fromUpdate= true,  createdByFromUpdate = linearAsset.createdBy, createdDateTimeFromUpdate = linearAsset.createdDateTime))
      Seq(existingId, createdId).flatten
    }
  }
}

package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, VVHClient}
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, PostGISAssetDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.{PostGISLinearAssetDao, PostGISMaintenanceDao}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{LinearAssetUtils, PolygonTools}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database.dynamicSession

class MaintenanceService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends DynamicLinearAssetService(roadLinkServiceImpl, eventBusImpl) {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def dao: PostGISLinearAssetDao = new PostGISLinearAssetDao(roadLinkServiceImpl.vvhClient, roadLinkServiceImpl)
  override def municipalityDao: MunicipalityDao = new MunicipalityDao
  override def eventBus: DigiroadEventBus = eventBusImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def polygonTools: PolygonTools = new PolygonTools()
  override def assetDao: PostGISAssetDao = new PostGISAssetDao
  def maintenanceDAO: PostGISMaintenanceDao = new PostGISMaintenanceDao(roadLinkServiceImpl.vvhClient, roadLinkServiceImpl)
  override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()) = throw new UnsupportedOperationException("Not supported method")

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
        val persisted = dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(toUpdate.map(_.id).toSet).groupBy(_.id)
        updateProjected(toUpdate, persisted)
        if (newMaintenanceAssets.nonEmpty)
          logger.info("Updated ids/linkids " + toUpdate.map(a => (a.id, a.linkId)))
      }
      toInsert.foreach { linearAsset =>
        val roadLink =roadLinks.find(_.linkId == linearAsset.linkId)
        val area = getAssetArea(roadLinks.find(_.linkId == linearAsset.linkId), Measures(linearAsset.startMeasure, linearAsset.endMeasure))
        val id = maintenanceDAO.createLinearAsset(linearAsset.typeId, linearAsset.linkId, linearAsset.expired, linearAsset.sideCode,
          Measures(linearAsset.startMeasure, linearAsset.endMeasure), linearAsset.createdBy.getOrElse(LinearAssetTypes.VvhGenerated), linearAsset.vvhTimeStamp, getLinkSource(roadLink), area = area)
        linearAsset.value match {
        case Some(DynamicValue(multiTypeProps)) =>
        val props = setDefaultAndFilterProperties(multiTypeProps, roadLink, linearAsset.typeId)
        validateRequiredProperties(linearAsset.typeId, props)
        dynamicLinearAssetDao.updateAssetProperties(id, props, linearAsset.typeId)
        case _ => None
        }
      }
      if (newMaintenanceAssets.nonEmpty)
        logger.info("Added assets for linkids " + toInsert.map(_.linkId))
    }
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

    value match {
      case DynamicValue(multiTypeProps) =>
        val properties = setPropertiesDefaultValues(multiTypeProps.properties, roadLink)
        val defaultValues = dynamicLinearAssetDao.propertyDefaultValues(typeId).filterNot(defaultValue => properties.exists(_.publicId == defaultValue.publicId))
        val props = properties ++ defaultValues.toSet
        validateRequiredProperties(typeId, props)
        dynamicLinearAssetDao.updateAssetProperties(id, props, typeId)
      case _ => None
    }
    id
  }

  def createWithHistory(typeId: Int, linkId: Long, value: Value, sideCode: Int, measures: Measures, username: String, roadLink: Option[RoadLinkLike]): Long = {
    withDynTransaction {
      maintenanceDAO.expireMaintenanceAssetsByLinkids(Seq(linkId), typeId)
      createWithoutTransaction(typeId, linkId, value, sideCode, measures, username, vvhClient.roadLinkData.createVVHTimeStamp(), roadLink)
    }
  }

  def getActiveMaintenanceRoadByPolygon(areaId: Int): Seq[PersistedLinearAsset] = {
    val polygon = polygonTools.getPolygonByArea(areaId)
    val vVHLinkIds = roadLinkService.getLinkIdsFromVVHWithComplementaryByPolygons(polygon)
    getPersistedAssetsByLinkIds(MaintenanceRoadAsset.typeId, vVHLinkIds)
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
        dynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(MaintenanceRoadAsset.typeId, linkIds ++ removedLinkIds, includeFloating = false).filterNot(_.expired)
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
                                  adjustedSideCodes = Seq.empty[SideCodeAdjustment],
                                  valueAdjustments = Seq.empty[ValueAdjustment])

    val (newAssets, changedSet) = fillNewRoadLinksWithPreviousAssetsData(projectableTargetRoadLinks,
      combinedAssets, assetsOnChangedLinks, changes, initChangeSet, existingAssets)

    if (newAssets.nonEmpty) {
      logger.info("Transferred %d assets in %d ms ".format(newAssets.length, System.currentTimeMillis - timing))
    }
    val groupedAssets = (existingAssets.filterNot(a => newAssets.exists(_.linkId == a.linkId)) ++ newAssets ++ assetsWithoutChangedLinks).groupBy(_.linkId)
    val (filledTopology, changeSet) = assetFiller.fillTopology(roads, groupedAssets, MaintenanceRoadAsset.typeId, Some(changedSet))

    eventBus.publish("linearAssets:update", changeSet)
    eventBus.publish("maintenanceRoads:saveProjectedMaintenanceRoads", newAssets.filter(_.id == 0L))

    filledTopology
  }

  def getPotencialServiceAssets: Seq[PersistedLinearAsset] = {
    withDynTransaction {
      maintenanceDAO.fetchPotentialServiceRoads()
    }
  }

  def getByZoomLevel :Seq[Seq[PieceWiseLinearAsset]] = {
    val linearAssets  = getPotencialServiceAssets
    val roadLinks = roadLinkService.getRoadLinksByLinkIdsFromVVH(linearAssets.map(_.linkId).toSet)
    val (filledTopology, changeSet) = assetFiller.fillTopology(roadLinks, linearAssets.groupBy(_.linkId),MaintenanceRoadAsset.typeId , Some(ChangeSet(Set.empty, Nil,Nil, Nil,Set.empty, Nil)))
    LinearAssetPartitioner.partition(filledTopology.filter(_.value.isDefined), roadLinks.groupBy(_.linkId).mapValues(_.head))
  }

  def getWithComplementaryByZoomLevel :Seq[Seq[PieceWiseLinearAsset]]= {
    val linearAssets  = getPotencialServiceAssets
    val roadLinks = roadLinkService.getRoadLinksAndComplementaryByLinkIdsFromVVH(linearAssets.map(_.linkId).toSet)
    val (filledTopology, changeSet) = assetFiller.fillTopology(roadLinks, linearAssets.groupBy(_.linkId),MaintenanceRoadAsset.typeId , Some(ChangeSet(Set.empty, Nil,Nil, Nil,Set.empty, Nil)))
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
    getPersistedAssetsByLinkIds(MaintenanceRoadAsset.typeId, linkIds).map { asset => (asset, roadLinks.find(r => r.linkId == asset.linkId).getOrElse(throw new NoSuchElementException)) }
  }

  /**
    * Saves linear asset when linear asset is split to two parts in UI (scissors icon).
    */
  override def split(id: Long, splitMeasure: Double, existingValue: Option[Value], createdValue: Option[Value], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit): Seq[Long] = {
    withDynTransaction {
      val linearAsset = dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(Set(id)).head
      val roadLink = roadLinkService.getRoadLinkAndComplementaryFromVVH(linearAsset.linkId, false).getOrElse(throw new IllegalStateException("Road link no longer available"))

      val (existingLinkMeasures, createdLinkMeasures) = GeometryUtils.createSplit(splitMeasure, (linearAsset.startMeasure, linearAsset.endMeasure))
      dao.updateExpiration(id)

      val existingId = existingValue.map(createWithoutTransaction(linearAsset.typeId, linearAsset.linkId, _, linearAsset.sideCode, Measures(existingLinkMeasures._1, existingLinkMeasures._2), username, linearAsset.vvhTimeStamp, Some(roadLink),fromUpdate = true,  createdByFromUpdate = linearAsset.createdBy, createdDateTimeFromUpdate = linearAsset.createdDateTime))
      val createdId = createdValue.map(createWithoutTransaction(linearAsset.typeId, linearAsset.linkId, _, linearAsset.sideCode, Measures(createdLinkMeasures._1, createdLinkMeasures._2), username, linearAsset.vvhTimeStamp, Some(roadLink), fromUpdate= true,  createdByFromUpdate = linearAsset.createdBy, createdDateTimeFromUpdate = linearAsset.createdDateTime))
      Seq(existingId, createdId).flatten
    }
  }
}

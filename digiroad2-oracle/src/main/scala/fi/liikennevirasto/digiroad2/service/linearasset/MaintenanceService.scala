package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, PostGISAssetDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.{PostGISLinearAssetDao, PostGISMaintenanceDao}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{LogUtils, PolygonTools}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._
import org.joda.time.DateTime
import org.postgresql.util.PSQLException
import slick.driver.JdbcDriver.backend.Database.dynamicSession

class MaintenanceService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends DynamicLinearAssetService(roadLinkServiceImpl, eventBusImpl) {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def dao: PostGISLinearAssetDao = new PostGISLinearAssetDao()
  override def municipalityDao: MunicipalityDao = new MunicipalityDao
  override def eventBus: DigiroadEventBus = eventBusImpl
  override def polygonTools: PolygonTools = new PolygonTools()
  override def assetDao: PostGISAssetDao = new PostGISAssetDao
  def maintenanceDAO: PostGISMaintenanceDao = new PostGISMaintenanceDao()
  override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()) = throw new UnsupportedOperationException("Not supported method")

  override def getByBoundingBox(typeId: Int, bounds: BoundingRectangle, municipalities: Set[Int] = Set(), showSpeedLimitsHistory: Boolean = false,
                                roadLinkFilter: RoadLink => Boolean = _ => true): Seq[Seq[PieceWiseLinearAsset]] = {
    val roadLinks = roadLinkService.getRoadLinksByBoundsAndMunicipalities(bounds, municipalities)
    val linearAssets = getByRoadLinks(typeId, roadLinks)
    val assetsWithAttributes = enrichMaintenanceRoadAttributes(linearAssets, roadLinks)

    LinearAssetPartitioner.partition(assetsWithAttributes, roadLinks.groupBy(_.linkId).mapValues(_.head))
  }

  override def getComplementaryByBoundingBox(typeId: Int, bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[Seq[PieceWiseLinearAsset]] = {
    val roadLinks = roadLinkService.getRoadLinksWithComplementaryByBoundsAndMunicipalities(bounds, municipalities)
    val linearAssets = getByRoadLinks(typeId, roadLinks)
    val assetsWithAttributes = enrichMaintenanceRoadAttributes(linearAssets, roadLinks)
    LinearAssetPartitioner.partition(assetsWithAttributes, roadLinks.groupBy(_.linkId).mapValues(_.head))
  }

  private def addPolygonAreaAttribute(linearAsset: PieceWiseLinearAsset, roadLink: RoadLink): PieceWiseLinearAsset = {
    val area = polygonTools.getAreaByGeometry(roadLink.geometry, Measures(linearAsset.startMeasure, linearAsset.endMeasure), None)
    linearAsset.copy(attributes = linearAsset.attributes ++ Map("area" -> area))
  }

  private def addConstructionTypeAttribute(linearAsset: PieceWiseLinearAsset, roadLink: RoadLink): PieceWiseLinearAsset = {
    linearAsset.copy(attributes = linearAsset.attributes ++ Map("constructionType" -> roadLink.constructionType.value))
  }

  private def enrichMaintenanceRoadAttributes(linearAssets: Seq[PieceWiseLinearAsset], roadLinks: Seq[RoadLink]): Seq[PieceWiseLinearAsset] = {
    val maintenanceRoadAttributeOperations: Seq[(PieceWiseLinearAsset, RoadLink) => PieceWiseLinearAsset] = Seq(
      addPolygonAreaAttribute,
      addConstructionTypeAttribute
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

  /**
    * Saves new linear assets from UI. Used by Digiroad2Api /linearassets POST endpoint.
    */
  override def create(newLinearAssets: Seq[NewLinearAsset], typeId: Int, username: String, timeStamp: Long = createTimeStamp()): Seq[Long] = {
    val roadLink = roadLinkService.getRoadLinksAndComplementariesByLinkIds(newLinearAssets.map(_.linkId).toSet)
    withDynTransaction {
      newLinearAssets.map { newAsset =>
        createWithoutTransaction(typeId, newAsset.linkId, newAsset.value, newAsset.sideCode, Measures(newAsset.startMeasure, newAsset.endMeasure), username, createTimeStamp(), roadLink.find(_.linkId == newAsset.linkId))
      }
    }
  }

  override def createWithoutTransaction(typeId: Int, linkId: String, value: Value, sideCode: Int, measures: Measures, username: String, timeStamp: Long, roadLink: Option[RoadLinkLike], fromUpdate: Boolean = false,
                                        createdByFromUpdate: Option[String] = Some(""),
                                        createdDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()), modifiedByFromUpdate: Option[String] = None, modifiedDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()), verifiedBy: Option[String] = None, informationSource: Option[Int] = None): Long = {

    val area = getAssetArea(roadLink, measures)
    val id = maintenanceDAO.createLinearAsset(MaintenanceRoadAsset.typeId, linkId, expired = false, sideCode, measures, username,
      timeStamp, getLinkSource(roadLink), fromUpdate, createdByFromUpdate, createdDateTimeFromUpdate, modifiedByFromUpdate, modifiedDateTimeFromUpdate, area = area)

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

  def createWithHistory(typeId: Int, linkId: String, value: Value, sideCode: Int, measures: Measures, username: String, roadLink: Option[RoadLinkLike]): Long = {
    withDynTransaction {
      maintenanceDAO.expireMaintenanceAssetsByLinkids(Seq(linkId), typeId)
      createWithoutTransaction(typeId, linkId, value, sideCode, measures, username, createTimeStamp(), roadLink)
    }
  }

  def getActiveMaintenanceRoadByPolygon(areaId: Int): Seq[PersistedLinearAsset] = {
    val polygon = polygonTools.getPolygonByArea(areaId)
    val vVHLinkIds = roadLinkService.getLinkIdsWithComplementaryByPolygons(polygon)
    getPersistedAssetsByLinkIds(MaintenanceRoadAsset.typeId, vVHLinkIds)
  }

  def getAssetArea(roadLink: Option[RoadLinkLike], measures: Measures, area: Option[Seq[Int]] = None): Int = {
    roadLink match {
      case Some(road) => polygonTools.getAreaByGeometry(road.geometry, measures, area)
      case None => throw new NoSuchElementException
    }
  }

  override protected def getByRoadLinks(typeId: Int, roadLinks: Seq[RoadLink], generateUnknownBoolean: Boolean = true, showHistory: Boolean = false,
                                        roadLinkFilter: RoadLink => Boolean = _ => true): Seq[PieceWiseLinearAsset] = {

    // Filter high functional classes from maintenance roads
    val roads: Seq[RoadLink] = roadLinks.filter(_.functionalClass > 4)
    val linkIds = roads.map(_.linkId)

    val existingAssets =
      withDynTransaction {
        dynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(MaintenanceRoadAsset.typeId, linkIds).filterNot(_.expired)
      }
    val linearAssets = assetFiller.toLinearAssetsOnMultipleLinks(existingAssets, roadLinks.map(assetFiller.toRoadLinkForFillTopology))
    if(generateUnknownBoolean) generateUnknowns(roadLinks, linearAssets.groupBy(_.linkId), typeId) else linearAssets
  }

  /**
    * Make sure operations are small and fast
    * Do not try to use methods which also use event bus, publishing will not work
    *
    * @param linksIds
    * @param typeId asset type
    */
  override def adjustLinearAssetsAction(linksIds: Set[String], typeId: Int, newTransaction: Boolean = true,adjustSideCode: Boolean = false): Unit = {
    if (newTransaction) withDynTransaction {action(false)} else action(newTransaction)
    def action(newTransaction: Boolean): Unit = {
      try {
        val roadLinks = roadLinkService.getRoadLinksAndComplementariesByLinkIds(linksIds, newTransaction = newTransaction)
        val existingAssets = dynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(MaintenanceRoadAsset.typeId, roadLinks.map(_.linkId)).filterNot(_.expired)
        val linearAssets = assetFiller.toLinearAssetsOnMultipleLinks(existingAssets, roadLinks.map(assetFiller.toRoadLinkForFillTopology))
        val groupedAssets = linearAssets.groupBy(_.linkId)

        LogUtils.time(logger, s"Check for and adjust possible linearAsset adjustments on ${roadLinks.size} roadLinks. TypeID: ${MaintenanceRoadAsset.typeId}") {
          if (adjustSideCode) adjustLinearAssetsSideCode(roadLinks, groupedAssets, typeId, geometryChanged = false)
          else adjustLinearAssets(roadLinks, groupedAssets, typeId, geometryChanged = false)
        }

      } catch {
        case e: PSQLException => logger.error(s"Database error happened on asset type ${typeId}, on links ${linksIds.mkString(",")} : ${e.getMessage}", e)
        case e: Throwable => logger.error(s"Unknown error happened on asset type ${typeId}, on links ${linksIds.mkString(",")} : ${e.getMessage}", e)
      }
    }
  }

  def getPotencialServiceAssets: Seq[PersistedLinearAsset] = {
    withDynTransaction {
      maintenanceDAO.fetchPotentialServiceRoads()
    }
  }

  def getByZoomLevel :Seq[Seq[PieceWiseLinearAsset]] = {
    val potentialAssets  = getPotencialServiceAssets
    val roadLinks = roadLinkService.getRoadLinksByLinkIds(potentialAssets.map(_.linkId).toSet)
    val linearAssets = assetFiller.toLinearAssetsOnMultipleLinks(potentialAssets, roadLinks.map(assetFiller.toRoadLinkForFillTopology))
    val filledTopology = generateUnknowns(roadLinks, linearAssets.groupBy(_.linkId),MaintenanceRoadAsset.typeId)
    LinearAssetPartitioner.partition(filledTopology.filter(_.value.isDefined), roadLinks.groupBy(_.linkId).mapValues(_.head))
  }

  def getWithComplementaryByZoomLevel :Seq[Seq[PieceWiseLinearAsset]]= {
    val potentialAssets  = getPotencialServiceAssets
    val roadLinks = roadLinkService.getRoadLinksByLinkIds(potentialAssets.map(_.linkId).toSet)
    val linearAssets = assetFiller.toLinearAssetsOnMultipleLinks(potentialAssets, roadLinks.map(assetFiller.toRoadLinkForFillTopology))
    val filledTopology = generateUnknowns(roadLinks, linearAssets.groupBy(_.linkId),MaintenanceRoadAsset.typeId)
    LinearAssetPartitioner.partition(filledTopology.filter(_.value.isDefined), roadLinks.groupBy(_.linkId).mapValues(_.head))
  }

  override def getUncheckedLinearAssets(areas: Option[Set[Int]]): Map[String, Map[String ,List[Long]]] ={
    val unchecked = withDynTransaction {
      maintenanceDAO.getUncheckedMaintenanceRoad(areas)
    }.groupBy(_._2).mapValues(x => x.map(_._1))
    Map("Unchecked" -> unchecked )
  }

  def getAllByBoundingBox(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[(PersistedLinearAsset, RoadLink)] = {
    val roadLinks = roadLinkService.getRoadLinksWithComplementaryByBoundsAndMunicipalities(bounds, municipalities)
    val linkIds = roadLinks.map(_.linkId)
    getPersistedAssetsByLinkIds(MaintenanceRoadAsset.typeId, linkIds).map { asset => (asset, roadLinks.find(r => r.linkId == asset.linkId).getOrElse(throw new NoSuchElementException)) }
  }

  /**
    * Saves linear asset when linear asset is split to two parts in UI (scissors icon).
    */
  override def split(id: Long, splitMeasure: Double, existingValue: Option[Value], createdValue: Option[Value], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit,adjust:Boolean = true): Seq[Long] = {
   val ids= withDynTransaction {
      val linearAsset = dynamicLinearAssetDao.fetchDynamicLinearAssetsByIds(Set(id)).head
      val roadLink = roadLinkService.getRoadLinkAndComplementaryByLinkId(linearAsset.linkId, false).getOrElse(throw new IllegalStateException("Road link no longer available"))

      val (existingLinkMeasures, createdLinkMeasures) = GeometryUtils.createSplit(splitMeasure, (linearAsset.startMeasure, linearAsset.endMeasure))
      dao.updateExpiration(id)

      val existingId = existingValue.map(createWithoutTransaction(linearAsset.typeId, linearAsset.linkId, _, linearAsset.sideCode, Measures(existingLinkMeasures._1, existingLinkMeasures._2), username, linearAsset.timeStamp, Some(roadLink),fromUpdate = true,  createdByFromUpdate = linearAsset.createdBy, createdDateTimeFromUpdate = linearAsset.createdDateTime))
      val createdId = createdValue.map(createWithoutTransaction(linearAsset.typeId, linearAsset.linkId, _, linearAsset.sideCode, Measures(createdLinkMeasures._1, createdLinkMeasures._2), username, linearAsset.timeStamp, Some(roadLink), fromUpdate= true,  createdByFromUpdate = linearAsset.createdBy, createdDateTimeFromUpdate = linearAsset.createdDateTime))
      Seq(existingId, createdId).flatten
    }
    if (adjust) adjustAssets(ids)else ids
  }
}

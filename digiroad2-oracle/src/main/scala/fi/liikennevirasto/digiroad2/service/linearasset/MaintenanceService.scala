package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, RoadLinkClient}
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
  override def dao: PostGISLinearAssetDao = new PostGISLinearAssetDao(roadLinkServiceImpl.roadLinkClient, roadLinkServiceImpl)
  override def municipalityDao: MunicipalityDao = new MunicipalityDao
  override def eventBus: DigiroadEventBus = eventBusImpl
  override def roadLinkClient: RoadLinkClient = roadLinkServiceImpl.roadLinkClient
  override def polygonTools: PolygonTools = new PolygonTools()
  override def assetDao: PostGISAssetDao = new PostGISAssetDao
  def maintenanceDAO: PostGISMaintenanceDao = new PostGISMaintenanceDao(roadLinkServiceImpl.roadLinkClient, roadLinkServiceImpl)
  override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()) = throw new UnsupportedOperationException("Not supported method")

  override def getByBoundingBox(typeId: Int, bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[Seq[PieceWiseLinearAsset]] = {
    val roadLinks = roadLinkService.getRoadLinksFromVVH(bounds, municipalities)
    val linearAssets = getByRoadLinks(typeId, roadLinks)
    val assetsWithAttributes = enrichMaintenanceRoadAttributes(linearAssets, roadLinks)

    LinearAssetPartitioner.partition(assetsWithAttributes, roadLinks.groupBy(_.linkId).mapValues(_.head))
  }

  override def getComplementaryByBoundingBox(typeId: Int, bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[Seq[PieceWiseLinearAsset]] = {
    val roadLinks = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(bounds, municipalities)._1
    val linearAssets = getByRoadLinks(typeId, roadLinks)
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

  /**
    * Saves new linear assets from UI. Used by Digiroad2Api /linearassets POST endpoint.
    */
  override def create(newLinearAssets: Seq[NewLinearAsset], typeId: Int, username: String, vvhTimeStamp: Long = roadLinkClient.roadLinkData.createVVHTimeStamp()): Seq[Long] = {
    val roadLink = roadLinkService.getRoadLinksAndComplementariesFromVVH(newLinearAssets.map(_.linkId).toSet)
    withDynTransaction {
      newLinearAssets.map { newAsset =>
        createWithoutTransaction(typeId, newAsset.linkId, newAsset.value, newAsset.sideCode, Measures(newAsset.startMeasure, newAsset.endMeasure), username, roadLinkClient.roadLinkData.createVVHTimeStamp(), roadLink.find(_.linkId == newAsset.linkId))
      }
    }
  }

  override def createWithoutTransaction(typeId: Int, linkId: String, value: Value, sideCode: Int, measures: Measures, username: String, vvhTimeStamp: Long, roadLink: Option[RoadLinkLike], fromUpdate: Boolean = false,
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

  def createWithHistory(typeId: Int, linkId: String, value: Value, sideCode: Int, measures: Measures, username: String, roadLink: Option[RoadLinkLike]): Long = {
    withDynTransaction {
      maintenanceDAO.expireMaintenanceAssetsByLinkids(Seq(linkId), typeId)
      createWithoutTransaction(typeId, linkId, value, sideCode, measures, username, roadLinkClient.roadLinkData.createVVHTimeStamp(), roadLink)
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

  override protected def getByRoadLinks(typeId: Int, roadLinks: Seq[RoadLink]): Seq[PieceWiseLinearAsset] = {

    // Filter high functional classes from maintenance roads
    val roads: Seq[RoadLink] = roadLinks.filter(_.functionalClass > 4)
    val linkIds = roads.map(_.linkId)

    val existingAssets =
      withDynTransaction {
        dynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(MaintenanceRoadAsset.typeId, linkIds).filterNot(_.expired)
      }

    val groupedAssets = existingAssets.groupBy(_.linkId)
    val filledTopology = assetFiller.fillRoadLinksWithoutAsset(roadLinks, groupedAssets, typeId)

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
    val roadLinks = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(bounds, municipalities)._1
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

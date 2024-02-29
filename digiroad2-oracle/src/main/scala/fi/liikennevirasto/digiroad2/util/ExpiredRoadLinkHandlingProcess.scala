package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.{AssetTypeInfo, Lanes}
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.dao.lane.LaneDao
import fi.liikennevirasto.digiroad2.dao.linearasset.manoeuvre.ManoeuvreDao
import fi.liikennevirasto.digiroad2.dao.linearasset.{AssetLinkWithMeasures, PostGISLinearAssetDao}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.{AssetOnExpiredLink, AssetsOnExpiredLinksService, RoadLinkService, RoadLinkWithExpiredDate}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummyEventBus, DummySerializer, GeometryUtils}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

object ExpiredRoadLinkHandlingProcess {

  lazy val roadLinkClient: RoadLinkClient = new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
  lazy val dummyEventBus: DigiroadEventBus = new DummyEventBus
  lazy val dummySerializer: VVHSerializer = new DummySerializer
  lazy val roadLinkService: RoadLinkService = new RoadLinkService(roadLinkClient, dummyEventBus, dummySerializer)
  lazy val postGISLinearAssetDao: PostGISLinearAssetDao = new PostGISLinearAssetDao
  lazy val assetsOnExpiredLinksService: AssetsOnExpiredLinksService = new AssetsOnExpiredLinksService
  lazy val laneDao: LaneDao = new LaneDao
  lazy val manoeuvreDao: ManoeuvreDao = new ManoeuvreDao
  lazy val assetTypeIds: Set[Int] = AssetTypeInfo.values.map(_.typeId)

  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  val logger = LoggerFactory.getLogger(getClass)

  /**
    * Get all assets (except manoeuvres) which are still on expired road links
    * Enrich assets with geometry and roadLink expired date
    * @param expiredRoadLinks All expired road links
    * @return Enriched assets which are on expired road links
    */
  def getAllExistingAssetsOnExpiredLinks(expiredRoadLinks: Seq[RoadLinkWithExpiredDate]): Seq[AssetOnExpiredLink] = {
    def enrichAssetLink(assetLink: AssetLinkWithMeasures, roadLinkWithExpiredDate: RoadLinkWithExpiredDate): AssetOnExpiredLink = {
      val geometry = if(AssetTypeInfo.apply(assetLink.assetTypeId).geometryType == "linear") {
        GeometryUtils.truncateGeometry3D(roadLinkWithExpiredDate.roadLink.geometry, assetLink.startMeasure, assetLink.endMeasure)
      } else {
        val pointGeom = GeometryUtils.calculatePointFromLinearReference(roadLinkWithExpiredDate.roadLink.geometry, assetLink.startMeasure)
        if(pointGeom.nonEmpty) {
          Seq(pointGeom.get)
        } else {
          logger.error(s"Could not calculate point on linkID: ${roadLinkWithExpiredDate.roadLink.linkId}, assetID: ${assetLink.id}")
          Seq()
        }
      }
      val roadLinkExpiredDate = roadLinkWithExpiredDate.expiredDate
      AssetOnExpiredLink(assetLink.id, assetLink.assetTypeId, assetLink.linkId, assetLink.sideCode, assetLink.startMeasure, assetLink.endMeasure, geometry, roadLinkExpiredDate)
    }

    expiredRoadLinks.flatMap(roadLinkWithExpiredDate => {
      val roadLink = roadLinkWithExpiredDate.roadLink
      val assetsOnExpiredLink = postGISLinearAssetDao.fetchAssetsWithPositionByLinkIds(assetTypeIds, Seq(roadLink.linkId), includeFloating = false, includeExpired = false)
      val lanesOnExpiredLink = laneDao.fetchAllLanesByLinkIds(Seq(roadLink.linkId)).map(lane =>
        AssetLinkWithMeasures(lane.id, Lanes.typeId, lane.linkId, lane.sideCode, lane.startMeasure, lane.endMeasure))

      val assetLinks = assetsOnExpiredLink ++ lanesOnExpiredLink
      assetLinks.map(enrichAssetLink(_, roadLinkWithExpiredDate))
    })
  }


  def process(cleanRoadLinkTable: Boolean = true): Unit = {
    withDynTransaction{
      logger.info("Starting to process expired road links")
      handleExpiredRoadLinks(cleanRoadLinkTable)
      logger.info("Finished processing expired road links")
    }
  }

  def handleExpiredRoadLinks(cleanRoadLinkTable:Boolean = true): Unit = {
    val expiredRoadLinksWithExpireDates = roadLinkService.getAllExpiredRoadLinksWithExpiredDates()
    logger.info(s"Expired road links count: ${expiredRoadLinksWithExpireDates.size}")
    val expiredRoadLinks = expiredRoadLinksWithExpireDates.map(_.roadLink)
    val assetsOnExpiredLinks = getAllExistingAssetsOnExpiredLinks(expiredRoadLinksWithExpireDates)
    val emptyExpiredLinks = expiredRoadLinks.filter(rl => {
      val linkIdsWithExistingAssets = assetsOnExpiredLinks.map(_.linkId)
      !linkIdsWithExistingAssets.contains(rl.linkId)
    }).map(_.linkId).toSet

    logger.info(s"Empty expired links count: ${emptyExpiredLinks.size}")
    logger.info(s"Assets on expired links count: ${assetsOnExpiredLinks.size}")
    
    if (cleanRoadLinkTable && emptyExpiredLinks.nonEmpty) {
      LogUtils.time(logger, "Delete and expire road links and properties") {
        roadLinkService.deleteRoadLinksAndPropertiesByLinkIds(emptyExpiredLinks)
      }
    }
    
    if (assetsOnExpiredLinks.nonEmpty) {
      LogUtils.time(logger, s"Insert ${assetsOnExpiredLinks.size} assets to worklist") {
        assetsOnExpiredLinksService.insertAssets(assetsOnExpiredLinks)
      }
    }
  }
}

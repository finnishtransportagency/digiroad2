package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.{AssetTypeInfo, Lanes}
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.dao.lane.LaneDao
import fi.liikennevirasto.digiroad2.dao.linearasset.manoeuvre.ManoeuvreDao
import fi.liikennevirasto.digiroad2.dao.linearasset.{AssetLinkWithMeasures, PostGISLinearAssetDao}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.{AssetOnExpiredLink, AssetsOnExpiredLinksService, RoadLinkService}
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
  def getAllExistingAssetsOnExpiredLinks(expiredRoadLinks: Seq[(RoadLink, DateTime)]): Seq[AssetOnExpiredLink] = {
    def enrichAssetLink(assetLink: AssetLinkWithMeasures, roadLinkWithExpiredDate: (RoadLink, DateTime)): AssetOnExpiredLink = {
      val geometry = if(AssetTypeInfo.apply(assetLink.assetTypeId).geometryType == "linear") {
        GeometryUtils.truncateGeometry3D(roadLinkWithExpiredDate._1.geometry, assetLink.startMeasure, assetLink.endMeasure)
      } else {
        Seq(GeometryUtils.calculatePointFromLinearReference(roadLinkWithExpiredDate._1.geometry, assetLink.startMeasure).get)
      }
      val roadLinkExpiredDate = roadLinkWithExpiredDate._2
      AssetOnExpiredLink(assetLink.id, assetLink.assetTypeId, assetLink.linkId, assetLink.sideCode, assetLink.startMeasure, assetLink.endMeasure, geometry, roadLinkExpiredDate)
    }

    expiredRoadLinks.flatMap(roadLinkWithExpiredDate => {
      val roadLink = roadLinkWithExpiredDate._1
      val assetsOnExpiredLink = postGISLinearAssetDao.fetchAssetsWithPositionByLinkIds(assetTypeIds, Seq(roadLink.linkId), includeFloating = false, includeExpired = false)
      val lanesOnExpiredLink = laneDao.fetchAllLanesByLinkIds(Seq(roadLink.linkId)).map(lane =>
        AssetLinkWithMeasures(lane.id, Lanes.typeId, lane.linkId, lane.sideCode, lane.startMeasure, lane.endMeasure))

      val assetLinks = assetsOnExpiredLink ++ lanesOnExpiredLink
      assetLinks.map(enrichAssetLink(_, roadLinkWithExpiredDate))
    })
  }


  def process(): Unit = {
    withDynTransaction{
      handleExpiredRoadLinks()
    }
  }

  def handleExpiredRoadLinks(): Unit = {
      val expiredRoadLinksWithExpireDates = roadLinkService.getAllExpiredRoadLinksWithExpiredDates()
      val expiredRoadLinks = expiredRoadLinksWithExpireDates.map(_._1)
      val assetsOnExpiredLinks = getAllExistingAssetsOnExpiredLinks(expiredRoadLinksWithExpireDates)
      val emptyExpiredLinks = expiredRoadLinks.filter(rl => {
        val linkIdsWithExistingAssets = assetsOnExpiredLinks.map(_.linkId)
        !linkIdsWithExistingAssets.contains(rl.linkId)
      }).map(_.linkId).toSet

    if(emptyExpiredLinks.nonEmpty) {
      roadLinkService.deleteRoadLinksAndPropertiesByLinkIds(emptyExpiredLinks)
    }
    if(assetsOnExpiredLinks.nonEmpty){
      assetsOnExpiredLinksService.insertAssets(assetsOnExpiredLinks)
    }
  }
}

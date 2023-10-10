package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.{AssetTypeInfo, Lanes, Manoeuvres}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummyEventBus, DummySerializer}
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.dao.lane.LaneDao
import fi.liikennevirasto.digiroad2.dao.linearasset.{AssetLink, PostGISLinearAssetDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.manoeuvre.ManoeuvreDao
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService

object ExpiredRoadLinkHandlingProcess {

  lazy val roadLinkClient: RoadLinkClient = new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
  lazy val dummyEventBus: DigiroadEventBus = new DummyEventBus
  lazy val dummySerializer: VVHSerializer = new DummySerializer
  lazy val roadLinkService: RoadLinkService = new RoadLinkService(roadLinkClient, dummyEventBus, dummySerializer)
  lazy val postGISLinearAssetDao: PostGISLinearAssetDao = new PostGISLinearAssetDao
  lazy val laneDao: LaneDao = new LaneDao
  lazy val manoeuvreDao: ManoeuvreDao = new ManoeuvreDao
  lazy val assetTypeIds: Set[Int] = AssetTypeInfo.values.map(_.typeId)

  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

  def getAllExistingAssetsOnExpiredLinks(expiredRoadLinks: Seq[RoadLink]): Seq[AssetLink] = {
    expiredRoadLinks.flatMap(roadLink => {
      val assetsOnExpiredLink = postGISLinearAssetDao.fetchAssetsByLinkIds(assetTypeIds, Seq(roadLink.linkId), includeFloating = false, includeExpired = false)
      val lanesOnExpiredLink = laneDao.fetchAllLanesByLinkIds(Seq(roadLink.linkId)).map(lane => AssetLink(lane.id, lane.linkId, Lanes.typeId))
      val manoeuvresOnLink = manoeuvreDao.getByRoadLinks(Seq(roadLink.linkId))
        .flatMap(manoeuvre => manoeuvre.elements
        .flatMap(element => Seq(AssetLink(element.manoeuvreId, element.sourceLinkId, Manoeuvres.typeId), AssetLink(element.manoeuvreId, element.destLinkId, Manoeuvres.typeId))))

      assetsOnExpiredLink ++ lanesOnExpiredLink ++ manoeuvresOnLink
    })
  }


  def process(): Unit = {
    withDynTransaction {
      val expiredRoadLinks = roadLinkService.getAllExpiredRoadLinks()
      val expiredLinksWithExistingAssets = getAllExistingAssetsOnExpiredLinks(expiredRoadLinks)
      val emptyExpiredLinks = expiredRoadLinks.filter(rl => {
        val linkIdsWithExistingAssets = expiredLinksWithExistingAssets.map(_.linkId)
        !linkIdsWithExistingAssets.contains(rl.linkId)
      }).map(_.linkId).toSet

      roadLinkService.deleteRoadLinksAndPropertiesByLinkIds(emptyExpiredLinks)

    }
  }
}

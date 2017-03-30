package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.PointAssetFiller.AssetAdjustment
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.pointasset.oracle.{OraclePedestrianCrossingDao, PedestrianCrossing, PedestrianCrossingToBePersisted}
import fi.liikennevirasto.digiroad2.user.User
import org.slf4j.LoggerFactory

case class IncomingPedestrianCrossing(lon: Double, lat: Double, linkId: Long) extends IncomingPointAsset

class PedestrianCrossingService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingPedestrianCrossing
  type PersistedAsset = PedestrianCrossing

  override def typeId: Int = 200

  override def fetchPointAssets(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[PedestrianCrossing] = OraclePedestrianCrossingDao.fetchByFilter(queryFilter)

  override def setFloating(persistedAsset: PedestrianCrossing, floating: Boolean) = {
    persistedAsset.copy(floating = floating)
  }

  override def create(asset: IncomingPedestrianCrossing, username: String, geometry: Seq[Point], municipality: Int, administrativeClass: Option[AdministrativeClass] = None): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat, 0), geometry)
    withDynTransaction {
      OraclePedestrianCrossingDao.create(PedestrianCrossingToBePersisted(asset.linkId, asset.lon, asset.lat, mValue, municipality, username), username)
    }
  }

  override def update(id: Long, updatedAsset: IncomingAsset, geometry: Seq[Point], municipality: Int, username: String): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat, 0), geometry)
    withDynTransaction {
      OraclePedestrianCrossingDao.update(id, PedestrianCrossingToBePersisted(updatedAsset.linkId, updatedAsset.lon, updatedAsset.lat, mValue, municipality, username))
    }
    id
  }

  override def getByBoundingBox(user: User, bounds: BoundingRectangle) : Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksAndChangesFromVVH(bounds)
    super.getByBoundingBox(user, bounds, roadLinks, changeInfo, floatingAdjustment)
  }

  private def floatingAdjustment(roadLinks: Seq[RoadLink], changeInfo: Seq[ChangeInfo], assetBeforeUpdate: AssetBeforeUpdate) = {
    if (assetBeforeUpdate.persistedFloating || assetBeforeUpdate.asset.floating) {
      PointAssetFiller.correctedPersistedAsset(assetBeforeUpdate.asset, roadLinks, changeInfo) match {
        case Some(asset) =>
          OraclePedestrianCrossingDao.update(asset.assetId, PedestrianCrossingToBePersisted(asset.linkId,
            asset.lon, asset.lat, asset.mValue, assetBeforeUpdate.asset.municipalityCode, "vvh_generated"))
          AssetBeforeUpdate(createPersistedAsset(assetBeforeUpdate.asset, asset), asset.floating, Some(FloatingReason.Unknown))

        case None =>
          if (assetBeforeUpdate.persistedFloating && !assetBeforeUpdate.asset.floating) {
            val logger = LoggerFactory.getLogger(getClass)
            val floatingReasonMessage = floatingReason(assetBeforeUpdate.asset, roadLinks.find(_.linkId == assetBeforeUpdate.asset.linkId))
            logger.info("Floating asset %d, reason: %s".format(assetBeforeUpdate.asset.id, floatingReasonMessage))
          }
          AssetBeforeUpdate(setFloating(assetBeforeUpdate.asset,  assetBeforeUpdate.persistedFloating), assetBeforeUpdate.asset.floating, assetBeforeUpdate.floatingReason)
      }
    }
    else
      AssetBeforeUpdate(setFloating(assetBeforeUpdate.asset, assetBeforeUpdate.persistedFloating), assetBeforeUpdate.asset.floating, assetBeforeUpdate.floatingReason)
  }


  override def getByMunicipality(municipalityCode: Int): Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksAndChangesFromVVH(municipalityCode)
    val mapRoadLinks = roadLinks.map(l => l.linkId -> l).toMap
    getByMunicipality(municipalityCode, mapRoadLinks, roadLinks, changeInfo, floatingCorrection)
  }

  private def floatingCorrection[T](changeInfo: Seq[ChangeInfo], roadLinks: Seq[RoadLink],
                                    persistedStop: PersistedAsset, floating: Boolean, assetFloatingReason: Option[FloatingReason],
                                    conversion: (PersistedAsset, Boolean) => T) = {
    if (floating) {
      PointAssetFiller.correctedPersistedAsset(persistedStop, roadLinks, changeInfo) match {
        case Some(asset) =>
          OraclePedestrianCrossingDao.update(asset.assetId, PedestrianCrossingToBePersisted(asset.linkId,
            asset.lon, asset.lat, asset.mValue, persistedStop.municipalityCode, "vvh_generated"))
          val persistedAsset = createPersistedAsset(persistedStop, asset)
          (conversion(persistedAsset, persistedAsset.floating), assetFloatingReason)

        case None => (conversion(persistedStop, floating), assetFloatingReason)
      }
    }
    else
      (conversion(persistedStop, floating), assetFloatingReason)
  }

  private def createPersistedAsset[T](persistedStop: PersistedAsset, asset: AssetAdjustment) = {

    new PersistedAsset(asset.assetId, asset.linkId, asset.lon, asset.lat,
      asset.mValue, asset.floating, persistedStop.vvhTimeStamp, persistedStop.municipalityCode, persistedStop.createdBy,
      persistedStop.createdAt, persistedStop.modifiedBy, persistedStop.modifiedAt)
  }
}


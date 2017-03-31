package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.PointAssetFiller.AssetAdjustment
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, BoundingRectangle}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.pointasset.oracle.{OracleTrafficLightDao, TrafficLight, TrafficLightToBePersisted}
import fi.liikennevirasto.digiroad2.user.User
import org.slf4j.LoggerFactory

case class IncomingTrafficLight(lon: Double, lat: Double, linkId: Long) extends IncomingPointAsset

class TrafficLightService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingTrafficLight
  type PersistedAsset = TrafficLight

  override def typeId: Int = 280

  override def update(id: Long, updatedAsset: IncomingAsset, geometry: Seq[Point], municipality: Int, username: String): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat, 0), geometry)
    withDynTransaction {
      OracleTrafficLightDao.update(id, TrafficLightToBePersisted(updatedAsset.linkId, updatedAsset.lon, updatedAsset.lat, mValue, municipality, username))
    }
    id
  }

  override def setFloating(persistedAsset: TrafficLight, floating: Boolean) = {
    persistedAsset.copy(floating = floating)
  }

  override def fetchPointAssets(queryFilter: (String) => String, roadLinks: Seq[RoadLinkLike]): Seq[TrafficLight] = {
    OracleTrafficLightDao.fetchByFilter(queryFilter)
  }

  override def create(asset: IncomingAsset, username: String, geometry: Seq[Point], municipality: Int, administrativeClass: Option[AdministrativeClass] = None): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat, 0), geometry)
    withDynTransaction {
      OracleTrafficLightDao.create(TrafficLightToBePersisted(asset.linkId, asset.lon, asset.lat, mValue, municipality, username), username)
    }
  }

  override def getByBoundingBox(user: User, bounds: BoundingRectangle) : Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksAndChangesFromVVH(bounds)
    super.getByBoundingBox(user, bounds, roadLinks, changeInfo, floatingAdjustment)
  }

  private def floatingAdjustment(roadLinks: Seq[RoadLink], changeInfo: Seq[ChangeInfo], assetBeforeUpdate: AssetBeforeUpdate) = {
    if (assetBeforeUpdate.persistedFloating || assetBeforeUpdate.asset.floating) {
      PointAssetFiller.correctedPersistedAsset(assetBeforeUpdate.asset, roadLinks, changeInfo) match {
        case Some(adjustment) =>
          OracleTrafficLightDao.update(adjustment.assetId, TrafficLightToBePersisted(adjustment.linkId,
            adjustment.lon, adjustment.lat, adjustment.mValue, assetBeforeUpdate.asset.municipalityCode, "vvh_generated"), Some(adjustment.vvhTimeStamp))

          AssetBeforeUpdate(createPersistedAsset(assetBeforeUpdate.asset, adjustment), adjustment.floating, Some(FloatingReason.Unknown))

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
        case Some(adjustment) =>
          OracleTrafficLightDao.update(adjustment.assetId, TrafficLightToBePersisted(adjustment.linkId,
            adjustment.lon, adjustment.lat, adjustment.mValue, persistedStop.municipalityCode, "vvh_generated"), Some(adjustment.vvhTimeStamp))

          val persistedAsset = createPersistedAsset(persistedStop, adjustment)
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

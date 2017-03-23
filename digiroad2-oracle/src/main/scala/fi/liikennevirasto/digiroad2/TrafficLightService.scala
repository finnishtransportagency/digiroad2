package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, BoundingRectangle}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.pointasset.oracle.{Obstacle, OracleTrafficLightDao, TrafficLight, TrafficLightToBePersisted}
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

  override def getByBoundingBox(user: User, bounds: BoundingRectangle): Seq[PersistedAsset] = {
    case class AssetBeforeUpdate(asset: PersistedAsset, persistedFloating: Boolean, floatingReason: Option[FloatingReason])
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksAndChangesFromVVH(bounds)

    withDynTransaction {
      val boundingBoxFilter = OracleDatabase.boundingBoxFilter(bounds, "a.geometry")
      val filter = s"where a.asset_type_id = $typeId and $boundingBoxFilter"
      val persistedAssets: Seq[PersistedAsset] = fetchPointAssets(withFilter(filter), roadLinks)

      val assetsBeforeUpdate: Seq[AssetBeforeUpdate] = persistedAssets.filter { persistedAsset =>
        user.isAuthorizedToRead(persistedAsset.municipalityCode)
      }.map { (persistedAsset: PersistedAsset) =>
        val (floating, assetFloatingReason) = super.isFloating(persistedAsset, roadLinks.find(_.linkId == persistedAsset.linkId))

        if (floating || persistedAsset.floating) {
          PointAssetFiller.correctedPersistedAsset(persistedAsset, roadLinks, changeInfo) match {
            case Some(trafficLight) =>
              OracleTrafficLightDao.update(trafficLight.assetId, TrafficLightToBePersisted(trafficLight.linkId,
                trafficLight.lon, trafficLight.lat, trafficLight.mValue, persistedAsset.municipalityCode, "vvh_generated"))

              AssetBeforeUpdate(new PersistedAsset(trafficLight.assetId, trafficLight.linkId, trafficLight.lon, trafficLight.lat,
                trafficLight.mValue, trafficLight.floating, persistedAsset.municipalityCode, persistedAsset.createdBy,
                persistedAsset.createdAt, persistedAsset.modifiedBy, persistedAsset.modifiedAt), trafficLight.floating, Some(FloatingReason.Unknown))

            case None =>
              if (floating && !persistedAsset.floating) {
                val logger = LoggerFactory.getLogger(getClass)
                val floatingReasonMessage = floatingReason(persistedAsset, roadLinks.find(_.linkId == persistedAsset.linkId))
                logger.info("Floating asset %d, reason: %s".format(persistedAsset.id, floatingReasonMessage))
              }
              AssetBeforeUpdate(setFloating(persistedAsset, floating), persistedAsset.floating, assetFloatingReason)
          }
        }
        else
          AssetBeforeUpdate(setFloating(persistedAsset, floating), persistedAsset.floating, assetFloatingReason)
      }
      assetsBeforeUpdate.foreach { asset =>
        if (asset.asset.floating != asset.persistedFloating) {
          updateFloating(asset.asset.id, asset.asset.floating, asset.floatingReason)
        }
      }
      assetsBeforeUpdate.map(_.asset)
    }
  }

  override def getByMunicipality(municipalityCode: Int): Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksAndChangesFromVVH(municipalityCode)

    def linkIdToRoadLink(linkId: Long): Option[RoadLinkLike] =
      roadLinks.map(l => l.linkId -> l).toMap.get(linkId)

    withDynTransaction {
      fetchPointAssets(withMunicipality(municipalityCode))
        .map(withFloatingUpdate(convertPersistedAsset(setFloating, linkIdToRoadLink, changeInfo, roadLinks)))
        .toList
    }
  }

  def convertPersistedAsset[T](conversion: (PersistedAsset, Boolean) => T,
                               linkIdToRoadLink: (Long) => Option[RoadLinkLike],
                               changeInfo: Seq[ChangeInfo], roadLinks: Seq[RoadLink])
                              (persistedStop: PersistedAsset): (T, Option[FloatingReason]) = {

    val (floating, assetFloatingReason) = isFloating(persistedStop, linkIdToRoadLink(persistedStop.linkId))
    if (floating) {
      PointAssetFiller.correctedPersistedAsset(persistedStop, roadLinks, changeInfo) match {
        case Some(trafficLight) =>
          OracleTrafficLightDao.update(trafficLight.assetId, TrafficLightToBePersisted(trafficLight.linkId,
            trafficLight.lon, trafficLight.lat, trafficLight.mValue, persistedStop.municipalityCode, "vvh_generated"))

          val persistedAsset = new PersistedAsset(trafficLight.assetId, trafficLight.linkId, trafficLight.lon, trafficLight.lat,
            trafficLight.mValue, trafficLight.floating, persistedStop.municipalityCode, persistedStop.createdBy,
            persistedStop.createdAt, persistedStop.modifiedBy, persistedStop.modifiedAt)

          (conversion(persistedAsset, persistedAsset.floating), assetFloatingReason)

        case None => (conversion(persistedStop, floating), assetFloatingReason)
      }
    }
    else
      (conversion(persistedStop, floating), assetFloatingReason)
  }
}

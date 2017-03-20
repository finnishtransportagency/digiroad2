package fi.liikennevirasto.digiroad2

import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.pointasset.oracle.{Obstacle, OraclePedestrianCrossingDao, PedestrianCrossing, PedestrianCrossingToBePersisted}
import fi.liikennevirasto.digiroad2.user.User
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery
import slick.jdbc.StaticQuery.interpolation

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

        if (floating && !persistedAsset.floating) {
          PointAssetFiller.correctedPersistedAsset(persistedAsset, roadLinks, changeInfo) match{
            case Some(pedestrian) =>
              OraclePedestrianCrossingDao.update(pedestrian.assetId, PedestrianCrossingToBePersisted(pedestrian.linkId,
                pedestrian.lon, pedestrian.lat, pedestrian.mValue, persistedAsset.municipalityCode, "vvh_generated"))

              AssetBeforeUpdate(new PersistedAsset(pedestrian.assetId, pedestrian.linkId, pedestrian.lon, pedestrian.lat,
                pedestrian.mValue, pedestrian.floating, persistedAsset.municipalityCode, persistedAsset.createdBy,
                persistedAsset.createdAt, persistedAsset.modifiedBy, persistedAsset.modifiedAt), pedestrian.floating, assetFloatingReason)

            case None => {
              val logger = LoggerFactory.getLogger(getClass)
              val floatingReasonMessage = floatingReason(persistedAsset, roadLinks.find(_.linkId == persistedAsset.linkId))
              logger.info("Floating asset %d, reason: %s".format(persistedAsset.id, floatingReasonMessage))
              AssetBeforeUpdate(setFloating(persistedAsset, floating), persistedAsset.floating, assetFloatingReason)
            }
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
      val persistedAsset = PointAssetFiller.correctedPersistedAsset(persistedStop, roadLinks, changeInfo) match {
        case Some(pedestrian) =>
          OraclePedestrianCrossingDao.update(pedestrian.assetId, PedestrianCrossingToBePersisted(pedestrian.linkId,
            pedestrian.lon, pedestrian.lat, pedestrian.mValue, persistedStop.municipalityCode, "vvh_generated"))

          new PersistedAsset(pedestrian.assetId, pedestrian.linkId, pedestrian.lon, pedestrian.lat,
            pedestrian.mValue, pedestrian.floating, persistedStop.municipalityCode, persistedStop.createdBy,
            persistedStop.createdAt, persistedStop.modifiedBy, persistedStop.modifiedAt)

        case None =>
          val logger = LoggerFactory.getLogger(getClass)
          val floatingReasonMessage = floatingReason(persistedStop, roadLinks.find(_.linkId == persistedStop.linkId))
          logger.info("Floating asset %d, reason: %s".format(persistedStop.id, floatingReasonMessage))
          persistedStop
      }
      (conversion(persistedAsset, persistedAsset.floating), assetFloatingReason)
    }
    else
      (conversion(persistedStop, floating), assetFloatingReason)
  }
}


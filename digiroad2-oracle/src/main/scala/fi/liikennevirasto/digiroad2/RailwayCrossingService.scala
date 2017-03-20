package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, BoundingRectangle}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.pointasset.oracle.{Obstacle, _}
import fi.liikennevirasto.digiroad2.user.User
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

case class IncomingRailwayCrossing(lon: Double, lat: Double, linkId: Long, safetyEquipment: Int, name: Option[String]) extends IncomingPointAsset

class RailwayCrossingService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingRailwayCrossing
  type PersistedAsset = RailwayCrossing

  override def typeId: Int = 230

  override def fetchPointAssets(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[RailwayCrossing] = OracleRailwayCrossingDao.fetchByFilter(queryFilter)

  override def setFloating(persistedAsset: RailwayCrossing, floating: Boolean) = {
    persistedAsset.copy(floating = floating)
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
          PointAssetFiller.correctedPersistedAsset(persistedAsset, roadLinks, changeInfo) match {
            case Some(railway) =>
              val updatedAsset = IncomingRailwayCrossing(railway.lon, railway.lat, railway.linkId, persistedAsset.safetyEquipment, persistedAsset.name)
              OracleRailwayCrossingDao.update(railway.assetId, updatedAsset, railway.mValue, persistedAsset.municipalityCode, "vvh_generated")

              AssetBeforeUpdate(new PersistedAsset(railway.assetId, railway.linkId, railway.lon, railway.lat,
                railway.mValue, railway.floating, persistedAsset.municipalityCode, persistedAsset.safetyEquipment, persistedAsset.name,
                persistedAsset.createdBy, persistedAsset.createdAt, persistedAsset.modifiedBy, persistedAsset.modifiedAt),
                railway.floating, Some(FloatingReason.Unknown))

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
        case Some(railway) =>
          val updatedAsset = IncomingRailwayCrossing(railway.lon, railway.lat, railway.linkId, persistedStop.safetyEquipment, persistedStop.name)
          OracleRailwayCrossingDao.update(railway.assetId, updatedAsset, railway.mValue, persistedStop.municipalityCode, "vvh_generated")

          new PersistedAsset(railway.assetId, railway.linkId, railway.lon, railway.lat,
            railway.mValue, railway.floating, persistedStop.municipalityCode, persistedStop.safetyEquipment, persistedStop.name,
            persistedStop.createdBy, persistedStop.createdAt, persistedStop.modifiedBy, persistedStop.modifiedAt)

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

  override def create(asset: IncomingRailwayCrossing, username: String, geometry: Seq[Point], municipality: Int, administrativeClass: Option[AdministrativeClass] = None): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat, 0), geometry)
    withDynTransaction {
      OracleRailwayCrossingDao.create(asset, mValue, municipality, username)
    }
  }

  override def update(id: Long, updatedAsset: IncomingRailwayCrossing, geometry: Seq[Point], municipality: Int, username: String): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat, 0), geometry)
    withDynTransaction {
      OracleRailwayCrossingDao.update(id, updatedAsset, mValue, municipality, username)
    }
    id
  }
}



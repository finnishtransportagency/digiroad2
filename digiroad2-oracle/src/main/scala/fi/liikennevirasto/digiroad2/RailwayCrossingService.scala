package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, BoundingRectangle}
import fi.liikennevirasto.digiroad2.linearasset.RoadLinkLike
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

    withDynSession {
      val boundingBoxFilter = OracleDatabase.boundingBoxFilter(bounds, "a.geometry")
      val filter = s"where a.asset_type_id = $typeId and $boundingBoxFilter"
      val persistedAssets: Seq[PersistedAsset] = fetchPointAssets(withFilter(filter), roadLinks)

      val assetsBeforeUpdate: Seq[AssetBeforeUpdate] = persistedAssets.filter { persistedAsset =>
        user.isAuthorizedToRead(persistedAsset.municipalityCode)
      }.map { (persistedAsset: PersistedAsset) =>
        val (floating, assetFloatingReason) = super.isFloating(persistedAsset, roadLinks.find(_.linkId == persistedAsset.linkId))
        if (floating && !persistedAsset.floating) {

          PointAssetFiller.correctedPersistedAsset(persistedAsset, roadLinks, changeInfo) match{

            case Some(railway) =>
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

    withDynSession {
      fetchPointAssets(withMunicipality(municipalityCode))
        .map { (persistedAsset: PersistedAsset) =>
          val (floating, assetFloatingReason) = super.isFloating(persistedAsset, linkIdToRoadLink(persistedAsset.linkId))
          val pointAsset = setFloating(persistedAsset, floating)

          if (persistedAsset.floating != pointAsset.floating) {
            PointAssetFiller.correctedPersistedAsset(persistedAsset, roadLinks, changeInfo) match {
              case Some(railway) =>
                new PersistedAsset(railway.assetId, railway.linkId, railway.lon, railway.lat,
                  railway.mValue, railway.floating, persistedAsset.municipalityCode, persistedAsset.safetyEquipment, persistedAsset.name,
                  persistedAsset.createdBy, persistedAsset.createdAt, persistedAsset.modifiedBy, persistedAsset.modifiedAt)

              case None => {
                val logger = LoggerFactory.getLogger(getClass)
                val floatingReasonMessage = floatingReason(persistedAsset, roadLinks.find(_.linkId == persistedAsset.linkId))
                logger.info("Floating asset %d, reason: %s".format(persistedAsset.id, floatingReasonMessage))
                updateFloating(pointAsset.id, pointAsset.floating, assetFloatingReason)
                pointAsset
              }
            }
          }else{
            pointAsset
          }
        }
        .toList
    }
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



package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.PointAssetFiller.AssetAdjustment
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, BoundingRectangle}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.pointasset.oracle.{OracleRailwayCrossingDao, RailwayCrossing}
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

  override def getByBoundingBox(user: User, bounds: BoundingRectangle) : Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksAndChangesFromVVH(bounds)
    super.getByBoundingBox(user, bounds, roadLinks, changeInfo, floatingAdjustment)
  }

  private def floatingAdjustment(roadLinks: Seq[RoadLink], changeInfo: Seq[ChangeInfo], assetBeforeUpdate: AssetBeforeUpdate) = {
    val hasChangeInfo = changeInfo.filter(changeInfos => changeInfos.oldId.getOrElse(0L) == assetBeforeUpdate.asset.linkId && changeInfos.vvhTimeStamp > assetBeforeUpdate.asset.vvhTimeStamp).headOption match {
      case Some(inf) => true
      case _ => false
    }

    if (hasChangeInfo) {
      PointAssetFiller.correctedPersistedAsset(assetBeforeUpdate.asset, roadLinks, changeInfo) match {
        case Some(adjustment) =>
          val updatedAsset = IncomingRailwayCrossing(adjustment.lon, adjustment.lat, adjustment.linkId, assetBeforeUpdate.asset.safetyEquipment, assetBeforeUpdate.asset.name)
          OracleRailwayCrossingDao.update(adjustment.assetId, updatedAsset, adjustment.mValue, assetBeforeUpdate.asset.municipalityCode, "vvh_generated", Some(adjustment.vvhTimeStamp))
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
          val updatedAsset = IncomingRailwayCrossing(adjustment.lon, adjustment.lat, adjustment.linkId, persistedStop.safetyEquipment, persistedStop.name)
          OracleRailwayCrossingDao.update(adjustment.assetId, updatedAsset, adjustment.mValue, persistedStop.municipalityCode, "vvh_generated",  Some(adjustment.vvhTimeStamp))

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
      asset.mValue, asset.floating, persistedStop.vvhTimeStamp, persistedStop.municipalityCode, persistedStop.safetyEquipment, persistedStop.name,
      persistedStop.createdBy, persistedStop.createdAt, persistedStop.modifiedBy, persistedStop.modifiedAt)
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



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
    super.getByBoundingBox(user, bounds, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation))
  }

  private def createOperation(asset: PersistedAsset, adjustment: AssetAdjustment): PersistedAsset = {
    createPersistedAsset(asset, adjustment)
  }

  private def adjustmentOperation(assetBeforeUpdate: AssetBeforeUpdate, adjustment: AssetAdjustment): Long = {
    val updatedAsset = IncomingRailwayCrossing(adjustment.lon, adjustment.lat, adjustment.linkId, assetBeforeUpdate.asset.safetyEquipment, assetBeforeUpdate.asset.name)
    OracleRailwayCrossingDao.update(adjustment.assetId, updatedAsset, adjustment.mValue, assetBeforeUpdate.asset.municipalityCode, "vvh_generated", Some(adjustment.vvhTimeStamp))
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



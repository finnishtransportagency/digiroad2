package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.PointAssetFiller.AssetAdjustment
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, LinkGeomSource}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.pointasset.oracle.{OracleRailwayCrossingDao, RailwayCrossing}
import fi.liikennevirasto.digiroad2.user.User

case class IncomingRailwayCrossing(lon: Double, lat: Double, linkId: Long, safetyEquipment: Int, name: Option[String]) extends IncomingPointAsset

class RailwayCrossingService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingRailwayCrossing
  type PersistedAsset = RailwayCrossing

  override def typeId: Int = 230

  override def setAssetPosition(asset: IncomingRailwayCrossing, geometry: Seq[Point], mValue: Double): IncomingRailwayCrossing = {
    GeometryUtils.calculatePointFromLinearReference(geometry, mValue) match {
      case Some(point) =>
        asset.copy(lon = point.x, lat = point.y)
      case _ =>
        asset
    }
  }

  override def fetchPointAssets(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[RailwayCrossing] = OracleRailwayCrossingDao.fetchByFilter(queryFilter)

  override def setFloating(persistedAsset: RailwayCrossing, floating: Boolean) = {
    persistedAsset.copy(floating = floating)
  }

  override def getByBoundingBox(user: User, bounds: BoundingRectangle) : Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(bounds)
    super.getByBoundingBox(user, bounds, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation))
  }

  private def createOperation(asset: PersistedAsset, adjustment: AssetAdjustment): PersistedAsset = {
    createPersistedAsset(asset, adjustment)
  }

  private def adjustmentOperation(persistedAsset: PersistedAsset, adjustment: AssetAdjustment, roadLink: RoadLink): Long = {
    val updated = IncomingRailwayCrossing(adjustment.lon, adjustment.lat, adjustment.linkId, persistedAsset.safetyEquipment, persistedAsset.name)
    updateWithoutTransaction(adjustment.assetId, updated, roadLink.geometry, persistedAsset.municipalityCode, "vvh_generated", persistedAsset.linkSource)
  }

  override def getByMunicipality(municipalityCode: Int): Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(municipalityCode)
    val mapRoadLinks = roadLinks.map(l => l.linkId -> l).toMap
    getByMunicipality(municipalityCode, mapRoadLinks, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation))
  }

  private def createPersistedAsset[T](persistedStop: PersistedAsset, asset: AssetAdjustment) = {

    new PersistedAsset(asset.assetId, asset.linkId, asset.lon, asset.lat,
      asset.mValue, asset.floating, persistedStop.vvhTimeStamp, persistedStop.municipalityCode, persistedStop.safetyEquipment, persistedStop.name,
      persistedStop.createdBy, persistedStop.createdAt, persistedStop.modifiedBy, persistedStop.modifiedAt, persistedStop.linkSource)
  }

  override def create(asset: IncomingRailwayCrossing, username: String, roadLink: RoadLink): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat), roadLink.geometry)
    withDynTransaction {
      OracleRailwayCrossingDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, roadLink.municipalityCode, username, VVHClient.createVVHTimeStamp(), roadLink.linkSource)
    }
  }

  override def update(id: Long, updatedAsset: IncomingRailwayCrossing, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource): Long = {
    withDynTransaction {
      updateWithoutTransaction(id, updatedAsset, geometry, municipality, username, linkSource)
    }
  }

  def updateWithoutTransaction(id: Long, updatedAsset: IncomingRailwayCrossing, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource): Long = {
    val oldAsset = getPersistedAssetsByIdsWithoutTransaction(Set(id)).headOption
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat), geometry)
    oldAsset match {
      case Some(old) if  old.lat != updatedAsset.lat || old.lon != updatedAsset.lon=>
        expireWihoutTransaction(id, username)
        OracleRailwayCrossingDao.create(setAssetPosition(updatedAsset, geometry, mValue), mValue, municipality, username, VVHClient.createVVHTimeStamp(), linkSource, old.createdBy, old.createdAt)
      case _ =>
        OracleRailwayCrossingDao.update(id, setAssetPosition(updatedAsset, geometry, mValue), mValue, municipality, username, Some(VVHClient.createVVHTimeStamp()), linkSource)
        id

    }
  }
}



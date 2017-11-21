package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.PointAssetFiller.AssetAdjustment
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.pointasset.oracle.{OraclePedestrianCrossingDao, PedestrianCrossing}
import fi.liikennevirasto.digiroad2.user.User

case class IncomingPedestrianCrossing(lon: Double, lat: Double, linkId: Long) extends IncomingPointAsset

class PedestrianCrossingService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingPedestrianCrossing
  type PersistedAsset = PedestrianCrossing

  override def typeId: Int = 200

  override def fetchPointAssets(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[PedestrianCrossing] = OraclePedestrianCrossingDao.fetchByFilter(queryFilter)

  override def setFloating(persistedAsset: PedestrianCrossing, floating: Boolean) = {
    persistedAsset.copy(floating = floating)
  }

  override def setAssetPosition(asset: IncomingPedestrianCrossing, geometry: Seq[Point], mValue: Double): IncomingPedestrianCrossing = {
      GeometryUtils.calculatePointFromLinearReference(geometry, mValue) match {
        case Some(point) =>
          asset.copy(lon = point.x, lat = point.y)
        case _ =>
          asset
      }
  }

  override def create(asset: IncomingPedestrianCrossing, username: String, roadLink: RoadLink): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat, 0), roadLink.geometry)
    withDynTransaction {
      OraclePedestrianCrossingDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, username, roadLink.municipalityCode, VVHClient.createVVHTimeStamp(), roadLink.linkSource)
    }
  }

  override def update(id: Long, updatedAsset: IncomingPedestrianCrossing, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource): Long = {
    withDynTransaction {
      updateWithoutTransaction(id, updatedAsset, geometry, municipality, username, linkSource)
    }
  }

  def updateWithoutTransaction(id: Long, updatedAsset: IncomingPedestrianCrossing, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource): Long = {
    val oldAsset = getPersistedAssetsByIdsWithoutTransaction(Set(id)).headOption
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat, 0), geometry)
    oldAsset match {
      case Some(old) if  old.lat != updatedAsset.lat || old.lon != updatedAsset.lon =>
        expireWihoutTransaction(id, username)
        OraclePedestrianCrossingDao.create(setAssetPosition(updatedAsset, geometry, mValue), mValue, username, municipality, VVHClient.createVVHTimeStamp(), linkSource)
      case _ =>
        OraclePedestrianCrossingDao.update(id, setAssetPosition(updatedAsset, geometry, mValue), mValue, username, municipality, Some(VVHClient.createVVHTimeStamp()), linkSource)
        id
    }
  }

  override def getByBoundingBox(user: User, bounds: BoundingRectangle) : Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(bounds)
    super.getByBoundingBox(user, bounds, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation))
  }

  private def createOperation(asset: PersistedAsset, adjustment: AssetAdjustment): PersistedAsset = {
    createPersistedAsset(asset, adjustment)
  }

  private def adjustmentOperation(persistedAsset: PersistedAsset, adjustment: AssetAdjustment, roadLink: RoadLink): Long = {
    val updated = IncomingPedestrianCrossing(adjustment.lon, adjustment.lat, adjustment.linkId)
    updateWithoutTransaction(adjustment.assetId, updated, roadLink.geometry, persistedAsset.municipalityCode, "vvh_generated", persistedAsset.linkSource)
  }

  override def getByMunicipality(municipalityCode: Int): Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(municipalityCode)
    val mapRoadLinks = roadLinks.map(l => l.linkId -> l).toMap
    getByMunicipality(municipalityCode, mapRoadLinks, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation))
  }

  private def createPersistedAsset[T](persistedStop: PersistedAsset, asset: AssetAdjustment) = {

    new PersistedAsset(asset.assetId, asset.linkId, asset.lon, asset.lat,
      asset.mValue, asset.floating, persistedStop.vvhTimeStamp, persistedStop.municipalityCode, persistedStop.createdBy,
      persistedStop.createdAt, persistedStop.modifiedBy, persistedStop.modifiedAt, persistedStop.linkSource)
  }
}


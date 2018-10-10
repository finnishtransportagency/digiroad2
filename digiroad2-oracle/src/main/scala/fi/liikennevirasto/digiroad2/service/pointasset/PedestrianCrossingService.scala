package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.PointAssetFiller.AssetAdjustment
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.asset.Asset._
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.pointasset.{OraclePedestrianCrossingDao, PedestrianCrossing}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.User
import org.joda.time.DateTime

case class IncomingPedestrianCrossing(lon: Double, lat: Double, linkId: Long) extends IncomingPointAsset
case class IncomingPedestrianCrossingAsset(linkId: Long, mValue: Long) extends IncomePointAsset

class PedestrianCrossingService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingPedestrianCrossing
  type PersistedAsset = PedestrianCrossing

  override def typeId: Int = 200

  override def fetchPointAssets(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[PedestrianCrossing] = OraclePedestrianCrossingDao.fetchByFilter(queryFilter)

  override def setFloating(persistedAsset: PedestrianCrossing, floating: Boolean) : PedestrianCrossing = {
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

  override def getPersistedAssetsByIdsWithoutTransaction(ids: Set[Long]): Seq[PersistedAsset] = {
    super.getPersistedAssetsByIdsWithoutTransaction(ids).filterNot(_.expired)
  }

  override def getPersistedAssetsByLinkIdWithoutTransaction(linkId: Long): Seq[PersistedAsset] = {
    super.getPersistedAssetsByLinkIdWithoutTransaction(linkId: Long).filterNot(_.expired)
  }

  override def getPersistedAssetsByLinkIdsWithoutTransaction(linkIds: Set[Long]): Seq[PersistedAsset] = {
    super.getPersistedAssetsByLinkIdsWithoutTransaction(linkIds).filterNot(_.expired)
    }

  override def create(asset: IncomingPedestrianCrossing, username: String, roadLink: RoadLink): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat), roadLink.geometry)
    withDynTransaction {
      OraclePedestrianCrossingDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, username, roadLink.municipalityCode, VVHClient.createVVHTimeStamp(), roadLink.linkSource)
    }
  }

  override def update(id: Long, updatedAsset: IncomingPedestrianCrossing, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource): Long = {
    withDynTransaction {
      updateWithoutTransaction(id, updatedAsset, geometry, municipality, username, linkSource, None, None)
    }
  }

  def updateWithoutTransaction(id: Long, updatedAsset: IncomingPedestrianCrossing, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource, mValue : Option[Double], vvhTimeStamp: Option[Long]): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat), geometry)
    getPersistedAssetsByIdsWithoutTransaction(Set(id)).headOption.getOrElse(throw new NoSuchElementException("Asset not found")) match {
      case old if  old.lat != updatedAsset.lat || old.lon != updatedAsset.lon =>
        expireWithoutTransaction(id)
        OraclePedestrianCrossingDao.create(setAssetPosition(updatedAsset, geometry, mValue), mValue, username, municipality, vvhTimeStamp.getOrElse(VVHClient.createVVHTimeStamp()), linkSource, old.createdBy, old.createdAt)
      case _ =>
        OraclePedestrianCrossingDao.update(id, setAssetPosition(updatedAsset, geometry, mValue), mValue, username, municipality, Some(vvhTimeStamp.getOrElse(VVHClient.createVVHTimeStamp())), linkSource)
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
    updateWithoutTransaction(adjustment.assetId, updated, roadLink.geometry, persistedAsset.municipalityCode, "vvh_generated",
                             persistedAsset.linkSource, Some(adjustment.mValue), Some(adjustment.vvhTimeStamp))
  }

  override def getByMunicipality(municipalityCode: Int): Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(municipalityCode)
    val mapRoadLinks = roadLinks.map(l => l.linkId -> l).toMap
    getByMunicipality(municipalityCode, mapRoadLinks, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation)).filterNot(_.expired)
  }

  private def createPersistedAsset[T](persistedStop: PersistedAsset, asset: AssetAdjustment) = {

    new PersistedAsset(asset.assetId, asset.linkId, asset.lon, asset.lat,
      asset.mValue, asset.floating, persistedStop.vvhTimeStamp, persistedStop.municipalityCode, persistedStop.createdBy,
      persistedStop.createdAt, persistedStop.modifiedBy, persistedStop.modifiedAt, linkSource = persistedStop.linkSource)
  }

  override def toIncomingAsset(asset: IncomePointAsset, link: RoadLink) : Option[IncomingPedestrianCrossing] = {
    GeometryUtils.calculatePointFromLinearReference(link.geometry, asset.mValue).map {
      point =>  IncomingPedestrianCrossing(point.x, point.y, link.linkId)
    }
  }

  override def getChanged(sinceDate: DateTime, untilDate: DateTime): Seq[ChangedPointAsset] = {
    val querySinceDate = s"to_date('${DateTimeSimplifiedFormat.print(sinceDate)}', 'YYYYMMDDHH24MI')"
    val queryUntilDate = s"to_date('${DateTimeSimplifiedFormat.print(untilDate)}', 'YYYYMMDDHH24MI')"

    val filter = s"where a.asset_type_id = $typeId and floating = 0 and (" +
      s"(a.valid_to > $querySinceDate and a.valid_to <= $queryUntilDate) or " +
      s"(a.modified_date > $querySinceDate and a.modified_date <= $queryUntilDate) or "+
      s"(a.created_date > $querySinceDate and a.created_date <= $queryUntilDate)) "

    val assets = withDynSession {
      fetchPointAssets(withFilter(filter))
    }

    val roadLinks = roadLinkService.getRoadLinksByLinkIdsFromVVH(assets.map(_.linkId).toSet)

    assets.map { asset =>
      ChangedPointAsset(asset, roadLinks.find(_.linkId == asset.linkId).getOrElse(throw new IllegalStateException("Road link no longer available")))    }
  }
}


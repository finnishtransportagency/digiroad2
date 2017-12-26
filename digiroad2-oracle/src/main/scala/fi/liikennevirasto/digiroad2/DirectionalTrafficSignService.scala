package fi.liikennevirasto.digiroad2


import fi.liikennevirasto.digiroad2.asset.LinkGeomSource
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.pointasset.oracle._

case class IncomingDirectionalTrafficSign(lon: Double, lat: Double, linkId: Long, validityDirection: Int, text: Option[String], bearing: Option[Int]) extends IncomingPointAsset


class DirectionalTrafficSignService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingDirectionalTrafficSign
  type PersistedAsset = DirectionalTrafficSign

  override def typeId: Int = 240

  override def setAssetPosition(asset: IncomingDirectionalTrafficSign, geometry: Seq[Point], mValue: Double): IncomingDirectionalTrafficSign = {
    GeometryUtils.calculatePointFromLinearReference(geometry, mValue) match {
      case Some(point) =>
        asset.copy(lon = point.x, lat = point.y)
      case _ =>
        asset
    }
  }

  override def fetchPointAssets(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[DirectionalTrafficSign] = {
    val assets = OracleDirectionalTrafficSignDao.fetchByFilter(queryFilter)
    assets.map { asset =>
      asset.copy(geometry = roadLinks.find(_.linkId == asset.linkId).map(_.geometry).getOrElse(Nil))}
  }

  override def setFloating(persistedAsset: DirectionalTrafficSign, floating: Boolean) = {
    persistedAsset.copy(floating = floating)
  }

  override def create(asset: IncomingDirectionalTrafficSign, username: String, roadLink: RoadLink): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat), roadLink.geometry)
    withDynTransaction {
      OracleDirectionalTrafficSignDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, roadLink.municipalityCode ,username)
    }
  }

  override def update(id: Long, updatedAsset: IncomingDirectionalTrafficSign, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource): Long = {
    withDynTransaction {
      updateWithoutTransaction(id, updatedAsset, geometry, municipality, username, linkSource)
    }
  }

  def updateWithoutTransaction(id: Long, updatedAsset: IncomingDirectionalTrafficSign, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat), geometry)
    getPersistedAssetsByIdsWithoutTransaction(Set(id)).headOption.getOrElse(throw new NoSuchElementException("Asset not found")) match {
      case old if old.bearing != updatedAsset.bearing || ( old.lat != updatedAsset.lat || old.lon != updatedAsset.lon) =>
        expireWithoutTransaction(id)
        OracleDirectionalTrafficSignDao.create(setAssetPosition(updatedAsset, geometry, mValue), mValue, municipality, username,old.createdBy, old.createdAt)
      case _ =>
        OracleDirectionalTrafficSignDao.update(id, setAssetPosition(updatedAsset, geometry, mValue), mValue, municipality, username)
    }
  }
}



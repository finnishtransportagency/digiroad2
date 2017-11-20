package fi.liikennevirasto.digiroad2


import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, LinkGeomSource}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.pointasset.oracle._

case class IncomingDirectionalTrafficSign(lon: Double, lat: Double, linkId: Long, validityDirection: Int, text: Option[String], bearing: Option[Int]) extends IncomingPointAsset


class DirectionalTrafficSignService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingDirectionalTrafficSign
  type PersistedAsset = DirectionalTrafficSign

  private def setAssetPosition(asset: IncomingAsset, geometry: Seq[Point], mValue: Double): IncomingAsset = {
    GeometryUtils.calculatePointFromLinearReference(geometry, mValue) match {
      case Some(point) =>
        asset.copy(lon = point.x, lat = point.y)
      case _ =>
        asset
    }
  }

  override def typeId: Int = 240

  override def fetchPointAssets(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[DirectionalTrafficSign] = {
    val assets = OracleDirectionalTrafficSignDao.fetchByFilter(queryFilter)
    assets.map { asset =>
      asset.copy(geometry = roadLinks.find(_.linkId == asset.linkId).map(_.geometry).getOrElse(Nil))}
  }

  override def setFloating(persistedAsset: DirectionalTrafficSign, floating: Boolean) = {
    persistedAsset.copy(floating = floating)
  }

  override def create(asset: IncomingDirectionalTrafficSign, username: String, roadLink: RoadLink): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat, 0), roadLink.geometry)
    withDynTransaction {
      OracleDirectionalTrafficSignDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, roadLink.municipalityCode ,username)
    }
  }

  override def update(id: Long, updatedAsset: IncomingDirectionalTrafficSign, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource): Long = {
    val oldAsset = getById(id)
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat, 0), geometry)
    withDynTransaction {
      oldAsset match {
        case Some(old) if old.bearing != updatedAsset.bearing || ( old.lat != updatedAsset.lat || old.lon != updatedAsset.lon) =>
          updateExpiration(id, expired = true, username)
          OracleDirectionalTrafficSignDao.create(setAssetPosition(updatedAsset, geometry, mValue), mValue, municipality ,username)
        case _ =>
          OracleDirectionalTrafficSignDao.update(id, setAssetPosition(updatedAsset, geometry, mValue), mValue, municipality, username)
          id
      }
    }
  }
}



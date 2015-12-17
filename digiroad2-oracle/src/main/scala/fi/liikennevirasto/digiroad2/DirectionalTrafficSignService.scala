package fi.liikennevirasto.digiroad2


import fi.liikennevirasto.digiroad2.pointasset.oracle._

case class IncomingDirectionalTrafficSign(lon: Double, lat: Double, mmlId: Long, validityDirection: Int, text: Option[String]) extends IncomingPointAsset


class DirectionalTrafficSignService(val vvhClient: VVHClient) extends PointAssetOperations {
  type IncomingAsset = IncomingDirectionalTrafficSign
  type PersistedAsset = DirectionalTrafficSign

  override def typeId: Int = 240

  override def fetchPointAssets(queryFilter: String => String, roadLinks: Seq[VVHRoadlink]): Seq[DirectionalTrafficSign] = {
    val assets = OracleDirectionalTrafficSignDao.fetchByFilter(queryFilter)
    assets.map { asset =>
      asset.copy(geometry = roadLinks.find(_.mmlId == asset.mmlId).map(_.geometry).getOrElse(Nil))}
  }

  override def setFloating(persistedAsset: DirectionalTrafficSign, floating: Boolean) = {
    persistedAsset.copy(floating = floating)
  }

  override def create(asset: IncomingDirectionalTrafficSign, username: String, geometry: Seq[Point], municipality: Int): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat, 0), geometry)
    withDynTransaction {
      OracleDirectionalTrafficSignDao.create(asset, mValue, municipality ,username)
    }
  }
  override def update(id: Long, updatedAsset: IncomingDirectionalTrafficSign, geometry: Seq[Point], municipality: Int, username: String): Long = 0
}



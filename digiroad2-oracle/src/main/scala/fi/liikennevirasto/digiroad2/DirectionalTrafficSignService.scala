package fi.liikennevirasto.digiroad2


import fi.liikennevirasto.digiroad2.pointasset.oracle._

case class IncomingDirectionalTrafficSign(lon: Double, lat: Double, mmlId: Long, validityDirection: Int, text: Option[String]) extends IncomingPointAsset


class DirectionalTrafficSignService(val vvhClient: VVHClient) extends PointAssetOperations {
  type IncomingAsset = IncomingDirectionalTrafficSign
  type PersistedAsset = DirectionalTrafficSign

  override def typeId: Int = 240

  override def fetchPointAssets(queryFilter: String => String): Seq[DirectionalTrafficSign] = {
    OracleDirectionalTrafficSignDao.fetchByFilter(queryFilter)
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



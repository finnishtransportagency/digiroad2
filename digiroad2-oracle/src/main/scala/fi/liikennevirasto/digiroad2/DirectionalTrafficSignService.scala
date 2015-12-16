package fi.liikennevirasto.digiroad2


import fi.liikennevirasto.digiroad2.pointasset.oracle._
import org.joda.time.DateTime

case class IncomingDirectionalTrafficSign(lon: Double, lat: Double, mmlId: Long, text: Option[String]) extends IncomingPointAsset


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

  override def create(asset: IncomingDirectionalTrafficSign, username: String, geometry: Seq[Point], municipality: Int): Long = 0
  override def update(id: Long, updatedAsset: IncomingDirectionalTrafficSign, geometry: Seq[Point], municipality: Int, username: String): Long = 0
}



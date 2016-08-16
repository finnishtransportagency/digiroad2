package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.pointasset.oracle.{TrafficLightToBePersisted, OracleTrafficLightDao, TrafficLight}

case class IncomingTrafficLight(lon: Double, lat: Double, linkId: Long) extends IncomingPointAsset

class TrafficLightService(val vvhClient: VVHClient) extends PointAssetOperations {
  type IncomingAsset = IncomingTrafficLight
  type PersistedAsset = TrafficLight

  override def typeId: Int = 280

  override def update(id: Long, updatedAsset: IncomingAsset, geometry: Seq[Point], municipality: Int, username: String): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat, 0), geometry)
    withDynTransaction {
      OracleTrafficLightDao.update(id, TrafficLightToBePersisted(updatedAsset.linkId, updatedAsset.lon, updatedAsset.lat, mValue, municipality, username))
    }
    id
  }

  override def setFloating(persistedAsset: TrafficLight, floating: Boolean) = {
    persistedAsset.copy(floating = floating)
  }

  override def fetchPointAssets(queryFilter: (String) => String, roadLinks: Seq[VVHRoadlink]): Seq[TrafficLight] = {
    OracleTrafficLightDao.fetchByFilter(queryFilter)
  }

  override def create(asset: IncomingAsset, username: String, geometry: Seq[Point], municipality: Int, roadlink: Option[VVHRoadlink] = None): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat, 0), geometry)
    withDynTransaction {
      OracleTrafficLightDao.create(TrafficLightToBePersisted(asset.linkId, asset.lon, asset.lat, mValue, municipality, username), username)
    }
  }
}

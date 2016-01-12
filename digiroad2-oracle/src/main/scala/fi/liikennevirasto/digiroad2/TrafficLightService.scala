package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.pointasset.oracle.{PedestrianCrossing, TrafficLight}

case class IncomingTrafficLight(lon: Double, lat: Double, mmlId: Long) extends IncomingPointAsset

class TrafficLightService(val vvhClient: VVHClient) extends PointAssetOperations {
  type IncomingAsset = IncomingTrafficLight
  type PersistedAsset = TrafficLight

  override def typeId: Int = 280

  override def update(id: Long, updatedAsset: IncomingAsset, geometry: Seq[Point], municipality: Int, username: String): Long = {
    // TODO: implementation
    0
  }

  override def setFloating(persistedAsset: TrafficLight, floating: Boolean) = {
    persistedAsset.copy(floating = floating)
  }

  override def fetchPointAssets(queryFilter: (String) => String, roadLinks: Seq[VVHRoadlink]): Seq[TrafficLight] = {
    // TODO: implementation
    Seq.empty
  }

  override def create(asset: IncomingAsset, username: String, geometry: Seq[Point], municipality: Int): Long = {
    // TODO: implementation
    0
  }
}

package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.pointasset.oracle._
import org.joda.time.DateTime

case class IncomingRailwayCrossing(lon: Double, lat: Double, mmlId: Long, safetyEquipment: Int, name: String) extends IncomingPointAsset

class RailwayCrossingService(val vvhClient: VVHClient) extends PointAssetOperations {
  type IncomingAsset = IncomingRailwayCrossing
  type PersistedAsset = RailwayCrossing

  override def typeId: Int = 230

  override def fetchPointAssets(queryFilter: String => String): Seq[RailwayCrossing] = OracleRailwayCrossingDao.fetchByFilter(queryFilter)

  override def setFloating(persistedAsset: RailwayCrossing, floating: Boolean) = {
    persistedAsset.copy(floating = floating)
  }

  override def create(asset: IncomingRailwayCrossing, username: String, geometry: Seq[Point], municipality: Int): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat, 0), geometry)
    withDynTransaction {
      OracleRailwayCrossingDao.create(RailwayCrossingToBePersisted(asset.mmlId, asset.lon, asset.lat, mValue, municipality, username, asset.safetyEquipment, asset.name), username)
    }
  }

  override def update(id: Long, updatedAsset: IncomingRailwayCrossing, geometry: Seq[Point], municipality: Int, username: String): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat, 0), geometry)
    withDynTransaction {
      OracleRailwayCrossingDao.update(id, RailwayCrossingToBePersisted(updatedAsset.mmlId, updatedAsset.lon, updatedAsset.lat, mValue, municipality, username, updatedAsset.safetyEquipment, updatedAsset.name))
    }
    id
  }
}



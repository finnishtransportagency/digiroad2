package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.pointasset.{OracleTrailerTruckWeightLimitDao, OracleBogieWeightLimitDao, OracleAxleWeightLimitDao, OracleWeightLimitDao}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import org.joda.time.DateTime

case class IncomingWeightLimit(lon: Double, lat: Double, linkId: Long, limit: Double, validityDirection: Int, bearing: Option[Int]) extends IncomingPointAsset
case class IncomingAxleWeightLimit(lon: Double, lat: Double, linkId: Long, limit: Double, validityDirection: Int, bearing: Option[Int]) extends IncomingPointAsset
case class IncomingBogieWeightLimit(lon: Double, lat: Double, linkId: Long, limit: Double, validityDirection: Int, bearing: Option[Int]) extends IncomingPointAsset
case class IncomingTrailerTruckWeightLimit(lon: Double, lat: Double, linkId: Long, limit: Double, validityDirection: Int, bearing: Option[Int]) extends IncomingPointAsset

case class WeightLimit(id: Long, linkId: Long,
                       lon: Double, lat: Double,
                       mValue: Double, floating: Boolean,
                       vvhTimeStamp: Long,
                       municipalityCode: Int,
                       createdBy: Option[String] = None,
                       createdAt: Option[DateTime] = None,
                       modifiedBy: Option[String] = None,
                       modifiedAt: Option[DateTime] = None,
                       linkSource: LinkGeomSource,
                       limit: Double) extends PersistedPointAsset


class WeightLimitService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingWeightLimit
  type PersistedAsset = WeightLimit

  override def typeId: Int = TrWeightLimit.typeId

  override def setAssetPosition(asset: IncomingWeightLimit, geometry: Seq[Point], mValue: Double) = throw new UnsupportedOperationException("Not Supported Method")

  override def update(id: Long, updatedAsset: IncomingWeightLimit, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource) = throw new UnsupportedOperationException("Not Supported Method")

  override def setFloating(persistedAsset: WeightLimit, floating: Boolean) = {
    persistedAsset
  }

  override def fetchPointAssets(queryFilter: (String) => String, roadLinks: Seq[RoadLinkLike]): Seq[WeightLimit] = {
    OracleWeightLimitDao.fetchByFilter(queryFilter)
  }

  override def create(asset: IncomingWeightLimit, username: String, roadLink: RoadLink) = throw new UnsupportedOperationException("Not Supported Method")

  override  def expire(id: Long, username: String): Long = throw new UnsupportedOperationException("Not Supported Method")

  override def toIncomingAsset(asset: IncomePointAsset, link: RoadLink) = throw new UnsupportedOperationException("Not Supported Method")
}


class AxleWeightLimitService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingAxleWeightLimit
  type PersistedAsset = WeightLimit

  override def typeId: Int = TrAxleWeightLimit.typeId

  override def setAssetPosition(asset: IncomingAxleWeightLimit, geometry: Seq[Point], mValue: Double) = throw new UnsupportedOperationException("Not Supported Method")

  override def update(id: Long, updatedAsset: IncomingAxleWeightLimit, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource) = throw new UnsupportedOperationException("Not Supported Method")

  override def setFloating(persistedAsset: WeightLimit, floating: Boolean) = {
    persistedAsset
  }

  override def fetchPointAssets(queryFilter: (String) => String, roadLinks: Seq[RoadLinkLike]): Seq[WeightLimit] = {
    OracleAxleWeightLimitDao.fetchByFilter(queryFilter)
  }

  override def create(asset: IncomingAxleWeightLimit, username: String, roadLink: RoadLink) = throw new UnsupportedOperationException("Not Supported Method")

  override  def expire(id: Long, username: String): Long = throw new UnsupportedOperationException("Not Supported Method")

  override def toIncomingAsset(asset: IncomePointAsset, link: RoadLink) = throw new UnsupportedOperationException("Not Supported Method")
}


class BogieWeightLimitService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingBogieWeightLimit
  type PersistedAsset = WeightLimit

  override def typeId: Int = TrBogieWeightLimit.typeId

  override def setAssetPosition(asset: IncomingBogieWeightLimit, geometry: Seq[Point], mValue: Double) = throw new UnsupportedOperationException("Not Supported Method")

  override def update(id: Long, updatedAsset: IncomingBogieWeightLimit, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource) = throw new UnsupportedOperationException("Not Supported Method")

  override def setFloating(persistedAsset: WeightLimit, floating: Boolean) = {
    persistedAsset
  }

  override def fetchPointAssets(queryFilter: (String) => String, roadLinks: Seq[RoadLinkLike]): Seq[WeightLimit] = {
    OracleBogieWeightLimitDao.fetchByFilter(queryFilter)
  }

  override def create(asset: IncomingBogieWeightLimit, username: String, roadLink: RoadLink) = throw new UnsupportedOperationException("Not Supported Method")

  override  def expire(id: Long, username: String): Long = throw new UnsupportedOperationException("Not Supported Method")

  override def toIncomingAsset(asset: IncomePointAsset, link: RoadLink) = throw new UnsupportedOperationException("Not Supported Method")
}


class TrailerTruckWeightLimitService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingTrailerTruckWeightLimit
  type PersistedAsset = WeightLimit

  override def typeId: Int = TrTrailerTruckWeightLimit.typeId

  override def setAssetPosition(asset: IncomingTrailerTruckWeightLimit, geometry: Seq[Point], mValue: Double) = throw new UnsupportedOperationException("Not Supported Method")

  override def update(id: Long, updatedAsset: IncomingTrailerTruckWeightLimit, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource) = throw new UnsupportedOperationException("Not Supported Method")

  override def setFloating(persistedAsset: WeightLimit, floating: Boolean) = {
    persistedAsset
  }

  override def fetchPointAssets(queryFilter: (String) => String, roadLinks: Seq[RoadLinkLike]): Seq[WeightLimit] = {
    OracleTrailerTruckWeightLimitDao.fetchByFilter(queryFilter)
  }

  override def create(asset: IncomingTrailerTruckWeightLimit, username: String, roadLink: RoadLink): Long = throw new UnsupportedOperationException("Not Supported Method")

  override  def expire(id: Long, username: String): Long = throw new UnsupportedOperationException("Not Supported Method")

  override def toIncomingAsset(asset: IncomePointAsset, link: RoadLink) = throw new UnsupportedOperationException("Not Supported Method")
}



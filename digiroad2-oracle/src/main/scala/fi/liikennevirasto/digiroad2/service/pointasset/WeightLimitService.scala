package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.{IncomingPointAsset, _}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.pointasset.{OracleAxleWeightLimitDao, OracleBogieWeightLimitDao, OracleTrailerTruckWeightLimitDao, OracleWeightLimitDao}
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


trait WeightLimitService extends PointAssetOperations {
  type IncomingAsset = IncomingPointAsset
  type PersistedAsset = WeightLimit

  override def typeId: Int

  override def setAssetPosition(asset: IncomingAsset, geometry: Seq[Point], mValue: Double) = throw new UnsupportedOperationException("Not Supported Method")

  override def update(id: Long, updatedAsset: IncomingAsset, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource) = throw new UnsupportedOperationException("Not Supported Method")

  override def setFloating(persistedAsset: WeightLimit, floating: Boolean) = {
    persistedAsset
  }

  override def fetchPointAssets(queryFilter: (String) => String, roadLinks: Seq[RoadLinkLike]): Seq[WeightLimit] = {
    OracleWeightLimitDao.fetchByFilter(queryFilter)
  }

  override def create(asset: IncomingAsset, username: String, roadLink: RoadLink) = throw new UnsupportedOperationException("Not Supported Method")

  override  def expire(id: Long, username: String): Long = throw new UnsupportedOperationException("Not Supported Method")

  override def toIncomingAsset(asset: IncomePointAsset, link: RoadLink) : Option[IncomingAsset] = {  throw new UnsupportedOperationException("Not Supported Method") }
}

class TotalWeightLimitService(val roadLinkService: RoadLinkService) extends WeightLimitService {

  override def typeId: Int = TrWeightLimit.typeId

  override def fetchPointAssets(queryFilter: (String) => String, roadLinks: Seq[RoadLinkLike]): Seq[WeightLimit] = {
    OracleWeightLimitDao.fetchByFilter(queryFilter)
  }
}

class AxleWeightLimitService(val roadLinkService: RoadLinkService) extends WeightLimitService {

  override def typeId: Int = TrAxleWeightLimit.typeId

  override def fetchPointAssets(queryFilter: (String) => String, roadLinks: Seq[RoadLinkLike]): Seq[WeightLimit] = {
    OracleAxleWeightLimitDao.fetchByFilter(queryFilter)
  }
}

class BogieWeightLimitService(val roadLinkService: RoadLinkService) extends WeightLimitService {

  override def typeId: Int = TrBogieWeightLimit.typeId

  override def fetchPointAssets(queryFilter: (String) => String, roadLinks: Seq[RoadLinkLike]): Seq[WeightLimit] = {
    OracleBogieWeightLimitDao.fetchByFilter(queryFilter)
  }
}

class TrailerTruckWeightLimitService(val roadLinkService: RoadLinkService) extends WeightLimitService {

  override def typeId: Int = TrTrailerTruckWeightLimit.typeId

  override def fetchPointAssets(queryFilter: (String) => String, roadLinks: Seq[RoadLinkLike]): Seq[WeightLimit] = {
    OracleTrailerTruckWeightLimitDao.fetchByFilter(queryFilter)
  }
}



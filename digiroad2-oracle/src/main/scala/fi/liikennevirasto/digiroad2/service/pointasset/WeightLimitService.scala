package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource
import fi.liikennevirasto.digiroad2.dao.pointasset.OracleWeightLimitDao
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import org.joda.time.DateTime

case class IncomingWeightLimit(lon: Double, lat: Double, linkId: Long) extends IncomingPointAsset
case class IncomingAxelWeightLimit(lon: Double, lat: Double, linkId: Long) extends IncomingPointAsset
case class IncomingBogieWeightLimit(lon: Double, lat: Double, linkId: Long) extends IncomingPointAsset
case class IncomingTrailerTruckWeightLimit(lon: Double, lat: Double, linkId: Long) extends IncomingPointAsset

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

  override def typeId: Int = 320

  override def setAssetPosition(asset: IncomingWeightLimit, geometry: Seq[Point], mValue: Double) = throw new UnsupportedOperationException("Not Supported Method")

  override def update(id: Long, updatedAsset: IncomingWeightLimit, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource) = throw new UnsupportedOperationException("Not Supported Method")

  override def setFloating(persistedAsset: WeightLimit, floating: Boolean) = throw new UnsupportedOperationException("Not Supported Method")

  override def fetchPointAssets(queryFilter: (String) => String, roadLinks: Seq[RoadLinkLike]): Seq[WeightLimit] = {
    OracleWeightLimitDao.fetchByFilter(queryFilter)
  }

  override def create(asset: IncomingWeightLimit, username: String, roadLink: RoadLink) = throw new UnsupportedOperationException("Not Supported Method")

  override def toIncomingAsset(asset: IncomePointAsset, link: RoadLink): Option[IncomingWeightLimit] = {
    GeometryUtils.calculatePointFromLinearReference(link.geometry, asset.mValue).map {
      point => IncomingWeightLimit(point.x, point.y, link.linkId)
    }
  }
}


class AxelWeightLimitService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingAxelWeightLimit
  type PersistedAsset = WeightLimit

  override def typeId: Int = 340

  override def setAssetPosition(asset: IncomingAxelWeightLimit, geometry: Seq[Point], mValue: Double) = throw new UnsupportedOperationException("Not Supported Method")

  override def update(id: Long, updatedAsset: IncomingAxelWeightLimit, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource) = throw new UnsupportedOperationException("Not Supported Method")

  override def setFloating(persistedAsset: WeightLimit, floating: Boolean) = throw new UnsupportedOperationException("Not Supported Method")

  override def fetchPointAssets(queryFilter: (String) => String, roadLinks: Seq[RoadLinkLike]): Seq[WeightLimit] = {
    OracleWeightLimitDao.fetchByFilter(queryFilter)
  }

  override def create(asset: IncomingAxelWeightLimit, username: String, roadLink: RoadLink) = throw new UnsupportedOperationException("Not Supported Method")

  override def toIncomingAsset(asset: IncomePointAsset, link: RoadLink): Option[IncomingAxelWeightLimit] = {
    GeometryUtils.calculatePointFromLinearReference(link.geometry, asset.mValue).map {
      point => IncomingAxelWeightLimit(point.x, point.y, link.linkId)
    }
  }
}


class BogieWeightLimitService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingBogieWeightLimit
  type PersistedAsset = WeightLimit

  override def typeId: Int = 350

  override def setAssetPosition(asset: IncomingBogieWeightLimit, geometry: Seq[Point], mValue: Double) = throw new UnsupportedOperationException("Not Supported Method")

  override def update(id: Long, updatedAsset: IncomingBogieWeightLimit, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource) = throw new UnsupportedOperationException("Not Supported Method")

  override def setFloating(persistedAsset: WeightLimit, floating: Boolean) = throw new UnsupportedOperationException("Not Supported Method")

  override def fetchPointAssets(queryFilter: (String) => String, roadLinks: Seq[RoadLinkLike]): Seq[WeightLimit] = {
    OracleWeightLimitDao.fetchByFilter(queryFilter)
  }

  override def create(asset: IncomingBogieWeightLimit, username: String, roadLink: RoadLink) = throw new UnsupportedOperationException("Not Supported Method")

  override def toIncomingAsset(asset: IncomePointAsset, link: RoadLink): Option[IncomingBogieWeightLimit] = {
    GeometryUtils.calculatePointFromLinearReference(link.geometry, asset.mValue).map {
      point => IncomingBogieWeightLimit(point.x, point.y, link.linkId)
    }
  }
}


class TrailerTruckWeightLimitService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingTrailerTruckWeightLimit
  type PersistedAsset = WeightLimit

  override def typeId: Int = 330

  override def setAssetPosition(asset: IncomingTrailerTruckWeightLimit, geometry: Seq[Point], mValue: Double) = throw new UnsupportedOperationException("Not Supported Method")

  override def update(id: Long, updatedAsset: IncomingTrailerTruckWeightLimit, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource) = throw new UnsupportedOperationException("Not Supported Method")

  override def setFloating(persistedAsset: WeightLimit, floating: Boolean) = throw new UnsupportedOperationException("Not Supported Method")

  override def fetchPointAssets(queryFilter: (String) => String, roadLinks: Seq[RoadLinkLike]): Seq[WeightLimit] = {
    OracleWeightLimitDao.fetchByFilter(queryFilter)
  }

  override def create(asset: IncomingTrailerTruckWeightLimit, username: String, roadLink: RoadLink): Long = throw new UnsupportedOperationException("Not Supported Method")

  override def toIncomingAsset(asset: IncomePointAsset, link: RoadLink): Option[IncomingTrailerTruckWeightLimit] = {
    GeometryUtils.calculatePointFromLinearReference(link.geometry, asset.mValue).map {
      point => IncomingTrailerTruckWeightLimit(point.x, point.y, link.linkId)
    }
  }
}



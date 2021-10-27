package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, Property, TrHeightLimit}
import fi.liikennevirasto.digiroad2.dao.pointasset.PostGISHeightLimitDao
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import org.joda.time.DateTime

case class IncomingHeightLimit(lon: Double, lat: Double, linkId: Long, limit: Double, validityDirection: Int, bearing: Option[Int]) extends IncomingPointAsset

case class HeightLimit(id: Long, linkId: Long,
                       lon: Double, lat: Double,
                       mValue: Double, floating: Boolean,
                       vvhTimeStamp: Long,
                       municipalityCode: Int,
                       createdBy: Option[String] = None,
                       createdAt: Option[DateTime] = None,
                       modifiedBy: Option[String] = None,
                       modifiedAt: Option[DateTime] = None,
                       linkSource: LinkGeomSource,
                       limit: Double,
                       propertyData: Seq[Property] = Seq()) extends PersistedPointAsset

class HeightLimitService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingHeightLimit
  type PersistedAsset = HeightLimit

  override def typeId: Int = TrHeightLimit.typeId

  override def setAssetPosition(asset: IncomingHeightLimit, geometry: Seq[Point], mValue: Double) = throw new UnsupportedOperationException("Not Supported Method")
  override def fetchPointAssetsWithExpiredLimited(queryFilter: String => String, token: Option[String]): Seq[HeightLimit] = throw new UnsupportedOperationException("Not Supported Method")
  override def update(id: Long, updatedAsset: IncomingHeightLimit, roadLink: RoadLink, username: String) = throw new UnsupportedOperationException("Not Supported Method")

  override def setFloating(persistedAsset: HeightLimit, floating: Boolean) = {
    persistedAsset
  }

  override def fetchPointAssets(queryFilter: (String) => String, roadLinks: Seq[RoadLinkLike]): Seq[HeightLimit] = {
    PostGISHeightLimitDao.fetchByFilter(queryFilter)
  }

  override def create(asset: IncomingHeightLimit, username: String, roadLink: RoadLink, newTransaction: Boolean) = throw new UnsupportedOperationException("Not Supported Method")

  override  def expire(id: Long, username: String): Long = throw new UnsupportedOperationException("Not Supported Method")

  override def toIncomingAsset(asset: IncomePointAsset, link: RoadLink) = throw new UnsupportedOperationException("Not Supported Method")

  override def getChanged(sinceDate: DateTime, untilDate: DateTime, token: Option[String] = None): Seq[ChangedPointAsset] = { throw new UnsupportedOperationException("Not Supported Method") }

  override def fetchPointAssetsWithExpired(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[HeightLimit] =  { throw new UnsupportedOperationException("Not Supported Method") }
}



package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource
import fi.liikennevirasto.digiroad2.dao.pointasset.OracleHeightLimitDao
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import org.joda.time.DateTime

case class IncomingHeightLimit(lon: Double, lat: Double, linkId: Long, limit: Double) extends IncomingPointAsset

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
                       limit: Double) extends PersistedPointAsset

class HeightLimitService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingHeightLimit
  type PersistedAsset = HeightLimit

  override def typeId: Int = 360

  override def setAssetPosition(asset: IncomingHeightLimit, geometry: Seq[Point], mValue: Double) = throw new UnsupportedOperationException("Not Supported Method")

  override def update(id: Long, updatedAsset: IncomingHeightLimit, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource) = throw new UnsupportedOperationException("Not Supported Method")

  override def setFloating(persistedAsset: HeightLimit, floating: Boolean) = {
    persistedAsset
  }

  override def fetchPointAssets(queryFilter: (String) => String, roadLinks: Seq[RoadLinkLike]): Seq[HeightLimit] = {
    OracleHeightLimitDao.fetchByFilter(queryFilter)
  }

  override def create(asset: IncomingHeightLimit, username: String, roadLink: RoadLink) = throw new UnsupportedOperationException("Not Supported Method")

  override  def expire(id: Long, username: String): Long = throw new UnsupportedOperationException("Not Supported Method")

  override def toIncomingAsset(asset: IncomePointAsset, link: RoadLink): Option[IncomingHeightLimit] = {
    GeometryUtils.calculatePointFromLinearReference(link.geometry, asset.mValue).map {

      //TODO remove hard coded zero, probably we have to cast IncomePointAsset to IncomingHeightLimit and then get the value
      point => IncomingHeightLimit(point.x, point.y, link.linkId, 0)
    }
  }
}



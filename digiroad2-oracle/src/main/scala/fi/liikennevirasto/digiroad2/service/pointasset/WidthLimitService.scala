package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.dao.pointasset.OracleWidthLimitDao
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.{TrWidthLimit, LinkGeomSource}
import org.joda.time.DateTime

sealed trait WidthLimitReason {
  def value: Int
}
object WidthLimitReason {
  val values = Set(Bridge, FullPortal, HalfPortal, Railing, Lamp, Fence, Abutment, TrafficLightPost, Other, Unknown)

  def apply(intValue: Int): WidthLimitReason = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object Bridge extends WidthLimitReason { def value = 1 }
  case object FullPortal extends WidthLimitReason { def value = 2 }
  case object HalfPortal extends WidthLimitReason { def value = 3 }
  case object Railing extends WidthLimitReason { def value = 4 }
  case object Lamp extends WidthLimitReason { def value = 5 }
  case object Fence extends WidthLimitReason { def value = 6 }
  case object Abutment extends WidthLimitReason { def value = 7 }
  case object TrafficLightPost extends WidthLimitReason { def value = 8 }
  case object Other extends WidthLimitReason { def value = 9 }
  case object Unknown extends WidthLimitReason { def value = 99 }
}

case class IncomingWidthLimit(lon: Double, lat: Double, linkId: Long, limit: Double, reason: WidthLimitReason, validityDirection: Int, bearing: Option[Int]) extends IncomingPointAsset

case class WidthLimit(id: Long, linkId: Long,
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
                      reason: WidthLimitReason) extends PersistedPointAsset

class WidthLimitService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingWidthLimit
  type PersistedAsset = WidthLimit

  override def typeId: Int = TrWidthLimit.typeId

  override def setAssetPosition(asset: IncomingWidthLimit, geometry: Seq[Point], mValue: Double) = throw new UnsupportedOperationException("Not Supported Method")

  override def update(id: Long, updatedAsset: IncomingWidthLimit, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource) = throw new UnsupportedOperationException("Not Supported Method")

  override def setFloating(persistedAsset: WidthLimit, floating: Boolean) = {
    persistedAsset
  }

  override def fetchPointAssets(queryFilter: (String) => String, roadLinks: Seq[RoadLinkLike]): Seq[WidthLimit] = {
    OracleWidthLimitDao.fetchByFilter(queryFilter)
  }

  override def create(asset: IncomingWidthLimit, username: String, roadLink: RoadLink) = throw new UnsupportedOperationException("Not Supported Method")

  override  def expire(id: Long, username: String): Long = throw new UnsupportedOperationException("Not Supported Method")

  override def toIncomingAsset(asset: IncomePointAsset, link: RoadLink) = throw new UnsupportedOperationException("Not Supported Method")
}



package fi.liikennevirasto.digiroad2.dao.pointasset

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource
import org.joda.time.DateTime


sealed trait WidthLimitReason {
  def value: Int
}
object WidthLimitReason {
  val values = Set(BridgeReason, FullPortalReason, HalfPortalReason, RailingReason, LampReason, FenceReason, AbutmentReason, TrafficLightPostReason, OtherReason, Unknown)

  def apply(intValue: Int): WidthLimitReason = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object BridgeReason extends WidthLimitReason { def value = 1 }
  case object FullPortalReason extends WidthLimitReason { def value = 2 }
  case object HalfPortalReason extends WidthLimitReason { def value = 3 }
  case object RailingReason extends WidthLimitReason { def value = 4 }
  case object LampReason extends WidthLimitReason { def value = 5 }
  case object FenceReason extends WidthLimitReason { def value = 6 }
  case object AbutmentReason extends WidthLimitReason { def value = 7 }
  case object TrafficLightPostReason extends WidthLimitReason { def value = 8 }
  case object OtherReason extends WidthLimitReason { def value = 9 }
  case object Unknown extends WidthLimitReason { def value = 99 }
}

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
                       reason: WidthLimitReason)

object OracleWidthLimitDao {

}

package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.client.vvh.RoadLinkFetched
import fi.liikennevirasto.digiroad2.util.Track
import org.joda.time.DateTime

case class RoadAddress(id: Long, roadNumber: Long, roadPartNumber: Long, track: Track, startAddrMValue: Long, endAddrMValue: Long, startDate: Option[DateTime] = None,
                       endDate: Option[DateTime] = None, linkId: Long,
                       startMValue: Double, endMValue: Double, sideCode: SideCode, geom: Seq[Point],
                       expired: Boolean, createdBy: Option[String], createdDate: Option[DateTime], modifiedDate: Option[DateTime]) {
  def addressMValueToLRM(addrMValue: Long): Option[Double] = {
    if (addrMValue < startAddrMValue || addrMValue > endAddrMValue)
      None
    else
    // Linear approximation: addrM = a*mValue + b <=> mValue = (addrM - b) / a
      sideCode match {
        case TowardsDigitizing => Some((addrMValue - startAddrMValue) * lrmLength / addressLength + startMValue)
        case AgainstDigitizing => Some(endMValue - (addrMValue - startAddrMValue) * lrmLength / addressLength)
        case _ => None
      }
  }

  private val addressLength: Long = endAddrMValue - startAddrMValue
  private val lrmLength: Double = Math.abs(endMValue - startMValue)

  def addrAt(a: Double) = {
    val coefficient = (endAddrMValue - startAddrMValue) / (endMValue - startMValue)
    sideCode match {
      case SideCode.AgainstDigitizing =>
        endAddrMValue - Math.round((a - startMValue) * coefficient)
      case SideCode.TowardsDigitizing =>
        startAddrMValue + Math.round((a - startMValue) * coefficient)
      case _ => throw new IllegalArgumentException(s"Bad sidecode $sideCode on road address $id (link $linkId)")
    }
  }
}

//TODO - Remove after new service NLS is used
case class RoadAddressTEMP(linkId: Long, road: Long, roadPart: Long, track: Track, startAddressM: Long, endAddressM: Long,
                           startMValue: Double, endMValue: Double, geom: Seq[Point] = Seq(), sideCode: Option[SideCode] = None,
                           municipalityCode: Option[Int] = None, createdDate: Option[String] = None) {

  private val addressLength: Long = endAddressM - startAddressM
  private val lrmLength: Double = Math.abs(endAddressM - startAddressM)

  def addressMValueToLRM(addrMValue: Long, vvhRoadLink: RoadLinkFetched): Option[Double] = {
    if (addrMValue < startAddressM || addrMValue > endAddressM)
      None
    else
    // Linear approximation: addrM = a*mValue + b <=> mValue = (addrM - b) / a
      sideCode.getOrElse(SideCode.Unknown) match {
        case TowardsDigitizing => Some((addrMValue - startAddressM) * lrmLength / addressLength + 0)
        case AgainstDigitizing => Some(vvhRoadLink.length - (addrMValue - startAddressM) * lrmLength / addressLength)
        case _ => None
      }
  }
}

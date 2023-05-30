package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.client.RoadLinkFetched
import fi.liikennevirasto.digiroad2.{Point, Track}

//TODO - Remove after new service NLS is used
case class RoadAddressTEMP(linkId: String, road: Long, roadPart: Long, track: Track, startAddressM: Long, endAddressM: Long,
                           startMValue: Double, endMValue: Double, geom: Seq[Point] = Seq(), sideCode: Option[SideCode] = None,
                           municipalityCode: Option[Int] = None, createdDate: Option[String] = None) {

  private val addressLength: Long = endAddressM - startAddressM
  private val lrmLength: Double = Math.abs(endAddressM - startAddressM)

  def addressMValueToLRM(addrMValue: Long, roadLinkFetched: RoadLinkFetched): Option[Double] = {
    if (addrMValue < startAddressM || addrMValue > endAddressM)
      None
    else
    // Linear approximation: addrM = a*mValue + b <=> mValue = (addrM - b) / a
      sideCode.getOrElse(SideCode.Unknown) match {
        case TowardsDigitizing => Some((addrMValue - startAddressM) * lrmLength / addressLength + 0)
        case AgainstDigitizing => Some(roadLinkFetched.length - (addrMValue - startAddressM) * lrmLength / addressLength)
        case _ => None
      }
  }
}
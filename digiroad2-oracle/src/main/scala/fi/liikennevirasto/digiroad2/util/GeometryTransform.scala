package fi.liikennevirasto.digiroad2.util


import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.client.VKMClient
import fi.liikennevirasto.digiroad2.client.viite.ViiteClientException
import fi.liikennevirasto.digiroad2.service.{RoadAddressForLink, RoadAddressService}
import fi.liikennevirasto.digiroad2.{Point, RoadAddress, RoadAddressException, Track}
import org.slf4j.{Logger, LoggerFactory}


/**
  * Road Side (fi: "puoli") tells the relation of an object and the track. For example, the side
  * where the bus stop is.
  */
sealed trait RoadSide {
  def value: Int
}
object RoadSide {
  val values = Set(Right, Left, Between, End, Middle, Across, Unknown)

  def apply(intValue: Int): RoadSide = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  def switch(side: RoadSide) = {
    side match {
      case Right => Left
      case Left => Right
      case _ => side
    }
  }

  case object Right extends RoadSide { def value = 1 }
  case object Left extends RoadSide { def value = 2 }
  case object Between extends RoadSide { def value = 3 }
  case object End extends RoadSide { def value = 7 }
  case object Middle extends RoadSide { def value = 8 }
  case object Across extends RoadSide { def value = 9 }
  case object Unknown extends RoadSide { def value = 0 }
}

/**
  * A class to transform ETRS89-FI coordinates to road network addresses
  */

class GeometryTransform(roadAddressService: RoadAddressService) {
  // see page 16: http://www.liikennevirasto.fi/documents/20473/143621/tieosoitej%C3%A4rjestelm%C3%A4.pdf/

  lazy val vkmClient: VKMClient = {
    new VKMClient()
  }

  val logger: Logger = LoggerFactory.getLogger(getClass)

  def getLinkIdsInRoadAddressRange(roadAddressRange: RoadAddressRange): Set[String] = {
    val startAndEndLinkIdsForAllSegments = vkmClient.fetchStartAndEndLinkIdForAddrRange(roadAddressRange)
    if (startAndEndLinkIdsForAllSegments.isEmpty) {
      throw new RoadAddressException(s"Could not fetch start and end link id for RoadAddressRange: $roadAddressRange")
    } else {
      startAndEndLinkIdsForAllSegments.flatMap(linkIds => {
        val startLinkId = linkIds._1
        val endLinkId = linkIds._2
        vkmClient.fetchLinkIdsBetweenTwoRoadLinks(startLinkId, endLinkId, roadAddressRange.roadNumber)
      })
    }
  }

  def resolveAddressAndLocation(coord: Point, heading: Int, mValue: Double, linkId: String, assetSideCode: Int, municipalityCode: Option[Int] = None, road: Option[Int] = None): (RoadAddress, RoadSide) = {

    def againstDigitizing(addr: RoadAddressForLink) = {
      val addressLength: Long = addr.endAddrMValue - addr.startAddrMValue
      val lrmLength: Double = Math.abs(addr.endMValue - addr.startMValue)
      val newMValue = (addr.endAddrMValue - ((mValue-addr.startMValue) * addressLength / lrmLength)).toInt
      RoadAddress(Some(municipalityCode.toString), addr.roadNumber.toInt, addr.roadPartNumber.toInt, addr.track, newMValue)
    }

    def towardsDigitizing (addr: RoadAddressForLink) = {
      val addressLength: Long = addr.endAddrMValue - addr.startAddrMValue
      val lrmLength: Double = Math.abs(addr.endMValue - addr.startMValue)
      val newMValue = (((mValue-addr.startMValue) * addressLength) / lrmLength + addr.startAddrMValue).toInt
      RoadAddress(Some(municipalityCode.toString), addr.roadNumber.toInt, addr.roadPartNumber.toInt, addr.track, newMValue)
    }

    val roadAddress =
      try {
        roadAddressService.getByLrmPosition(linkId, mValue)
      } catch {
        case vce: ViiteClientException =>
          logger.error(s"Viite error with message ${vce.getMessage}")
          None
      }

    //If there is no roadAddress in VIITE or VIITE query fails try to find it in VKM
    if(roadAddress.isEmpty && road.isDefined)
      return vkmClient.resolveAddressAndLocation(coord, heading, SideCode.apply(assetSideCode), road)

    val roadSide = roadAddress match {
      case Some(addrSide) if addrSide.sideCode.value == assetSideCode => RoadSide.Right //TowardsDigitizing //
      case Some(addrSide) if addrSide.sideCode.value != assetSideCode => RoadSide.Left //AgainstDigitizing //
      case _ => RoadSide.Unknown
    }

    val address = roadAddress match {
      case Some(addr) if addr.track.eq(Track.Unknown) => throw new RoadAddressException("Invalid value for Track: %d".format(addr.track.value))
      case Some(addr) if addr.sideCode.value != assetSideCode =>
        if (assetSideCode == SideCode.AgainstDigitizing.value) towardsDigitizing(addr) else againstDigitizing(addr)
      case Some(addr) =>
        if (assetSideCode == SideCode.TowardsDigitizing.value) towardsDigitizing(addr) else againstDigitizing(addr)
      case None => throw new RoadAddressException("No road address found")
    }

    (address, roadSide )
  }
}


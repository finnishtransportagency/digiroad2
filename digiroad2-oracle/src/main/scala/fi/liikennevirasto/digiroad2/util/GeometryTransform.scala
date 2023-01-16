package fi.liikennevirasto.digiroad2.util


import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.client.VKMClient
import fi.liikennevirasto.digiroad2.service.{RoadAddressForLink, RoadAddressService}
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.client.viite.ViiteClientException
import org.slf4j.{Logger, LoggerFactory}
/**
  * A road consists of 1-2 tracks (fi: "ajorata"). 2 tracks are separated by a fence or grass for example.
  * Left and Right are relative to the advancing direction (direction of growing m values)
  */
sealed trait Track {
  def value: Int
}
object Track {
  val values = Set(Combined, RightSide, LeftSide, Unknown)

  def apply(intValue: Int): Track = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  /**
    * Switch left to right and vice versa
    * @param track Track value to switch
    * @return
    */
  def switch(track: Track) = {
    track match {
      case RightSide => LeftSide
      case LeftSide => RightSide
      case _ => track
    }
  }

  case object Combined extends Track { def value = 0 }
  case object RightSide extends Track { def value = 1 }
  case object LeftSide extends Track { def value = 2 }
  case object Unknown extends Track { def value = 99 }
}

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

case class RoadAddress(municipalityCode: Option[String], road: Int, roadPart: Int, track: Track, addrM: Int)
object RoadAddress {
  lazy val routeRoadNumbers: Seq[Int] = 1 to 39
  lazy val mainRoadRoadNumbers: Seq[Int] =  40 to 99
  lazy val connectingRoadRoadNumbers: Seq[Int] = 1000 to 19999
  lazy val roundAboutAndDropletRoadNumbers: Seq[Int] = 20001 to 39999 //road part 1
  lazy val roundAboutFreeRightSideRoadNumbers: Seq[Int] = 20001 to 39999 //road part 2-5
  lazy val roundAboutDriveThroughLineRoadNumbers: Seq[Int] = 20001 to 39999 //road part 6-7
  lazy val rampsAndJunctionsRoadNumbers: Seq[Int] = 20001 to 39999 // road part > 11
  lazy val serviceAreaRoadNumbers: Seq[Int] = 20001 to 39999 // road part 995-999
  lazy val serviceAccessRoadNumbers: Seq[Int] = 30000 to 39999 // road part 9
  lazy val streetRoadNumbers: Seq[Int] = 40001 to 49999
  lazy val privateRoadRoadNumbers: Seq[Int] = 50001 to 59999
  lazy val winterRoadRoadNumbers: Seq[Int] = 60001 to 61999
  lazy val tractorRoadRoadNumbers: Seq[Int] = 62001 to 62999
  lazy val walkingCyclingRoadNumbers: Seq[Int] = 70001 to 99999

  lazy val carTrafficRoadNumbers: Seq[Int] = 1 to 62999

  def isCarTrafficRoadAddress(roadNumber: Long): Boolean = {
    carTrafficRoadNumbers.contains(roadNumber)
  }

}
class RoadAddressException(response: String) extends RuntimeException(response)
class RoadPartReservedException(response: String) extends RoadAddressException(response)

/**
  * A class to transform ETRS89-FI coordinates to road network addresses
  */

class GeometryTransform(roadAddressService: RoadAddressService) {
  // see page 16: http://www.liikennevirasto.fi/documents/20473/143621/tieosoitej%C3%A4rjestelm%C3%A4.pdf/

  lazy val vkmClient: VKMClient = {
    new VKMClient()
  }

  val logger: Logger = LoggerFactory.getLogger(getClass)

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

  /**
    * @param roadLinkLength Length of road link
    * @param addrSideCode SideCode of the road address for link, i.e. which way road address grows relative to digitizing direction
    * @param addrStartMValue Road address distance from road link's start point (Point determined by road address growth direction NOT digitizing direction)
    * @param addrEndMValue Road address distance from road link's end point (Point determined by road address growth direction NOT digitizing direction)
    * @param assetStartMValue Start M-Value on road link for cut asset
    * @param assetEndMValue End M-Value on road link for cut asset
    * @return Road address start and end distance on cut asset
    */
  def getAddressMValuesForCutAssets(roadLinkLength: Double, addrSideCode: SideCode, addrStartMValue: Int, addrEndMValue: Int,
                                    assetStartMValue: Double, assetEndMValue: Double): (Int, Int) = {

    // Coefficient is needed for determining address m-value on specific asset measure, because
    // road address length is not equal to roadlink length
    val coefficient = (addrEndMValue - addrStartMValue) / roadLinkLength

    addrSideCode match {
      case TowardsDigitizing =>
        val startAddrMValue = addrStartMValue + Math.round(assetStartMValue * coefficient).toInt
        val endAddrMValue = addrStartMValue + Math.round(assetEndMValue * coefficient).toInt
        (startAddrMValue, endAddrMValue)
      case AgainstDigitizing =>
        val startAddrMValue = addrEndMValue - Math.round(assetEndMValue * coefficient).toInt
        val endAddrMValue = addrEndMValue - Math.round(assetStartMValue * coefficient).toInt
        (startAddrMValue, endAddrMValue)
      case _ => throw new RoadAddressException("Invalid road address side code")
    }
  }
}


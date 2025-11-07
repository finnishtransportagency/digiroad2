package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}


class RoadAddressException(response: String) extends RuntimeException(response)
class MissingRoadAddressInformation(response: String) extends RuntimeException(response)
class RoadPartReservedException(response: String) extends RoadAddressException(response)
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

case class RoadAddress(municipalityCode: Option[String], road: Int, roadPart: Int, track: Track, addrM: Int)

object RoadAddress {
  lazy val routeRoadNumbers: Seq[Int] = 1 to 39
  lazy val mainRoadRoadNumbers: Seq[Int] = 40 to 99
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
  lazy val roadPartNumberRange: Seq[Int] = 1 to 1000

  def isCarTrafficRoadAddress(roadNumber: Long): Boolean = {
    carTrafficRoadNumbers.contains(roadNumber)
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
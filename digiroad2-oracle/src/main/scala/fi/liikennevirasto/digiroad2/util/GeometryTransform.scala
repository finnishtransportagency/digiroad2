package fi.liikennevirasto.digiroad2.util


import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.client.{PointAssetForConversion, MassQueryParamsCoord, MassQueryResolve, RoadAddressBoundToAsset, VKMClient}
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
case class RoadAddressRange(roadNumber: Long, track: Option[Track], startRoadPartNumber: Long, endRoadPartNumber: Long, startAddrMValue: Long, endAddrMValue: Long)
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

    determinateRoadSide(mValue, assetSideCode, municipalityCode, roadAddress)
  }
  
  private def againstDigitizing(addr: RoadAddressForLink, mValue: Double, municipalityCode: Option[Int]): RoadAddress = {
    val addressLength: Long = addr.endAddrMValue - addr.startAddrMValue
    val lrmLength: Double = Math.abs(addr.endMValue - addr.startMValue)
    val newMValue = (addr.endAddrMValue - ((mValue - addr.startMValue) * addressLength / lrmLength)).toInt
    RoadAddress(Some(municipalityCode.toString), addr.roadNumber.toInt, addr.roadPartNumber.toInt, addr.track, newMValue)
  }

  private def towardsDigitizing(addr: RoadAddressForLink, mValue: Double, municipalityCode: Option[Int]): RoadAddress = {
    val addressLength: Long = addr.endAddrMValue - addr.startAddrMValue
    val lrmLength: Double = Math.abs(addr.endMValue - addr.startMValue)
    val newMValue = (((mValue - addr.startMValue) * addressLength) / lrmLength + addr.startAddrMValue).toInt
    RoadAddress(Some(municipalityCode.toString), addr.roadNumber.toInt, addr.roadPartNumber.toInt, addr.track, newMValue)
  }

  private def determinateRoadSide(mValue: Double, assetSideCode: Int, municipalityCode: Option[Int], roadAddress: Option[RoadAddressForLink]): (RoadAddress, RoadSide) = {
    val roadSide = roadAddress match {
      case Some(addrSide) if addrSide.sideCode.value == assetSideCode => RoadSide.Right //TowardsDigitizing //
      case Some(addrSide) if addrSide.sideCode.value != assetSideCode => RoadSide.Left //AgainstDigitizing //
      case _ => RoadSide.Unknown
    }

    val address = roadAddress match {
      case Some(addr) if addr.track.eq(Track.Unknown) => throw new RoadAddressException("Invalid value for Track: %d".format(addr.track.value))
      case Some(addr) if addr.sideCode.value != assetSideCode =>
        if (assetSideCode == SideCode.AgainstDigitizing.value) towardsDigitizing(addr, mValue, municipalityCode) else againstDigitizing(addr, mValue, municipalityCode)
      case Some(addr) =>
        if (assetSideCode == SideCode.TowardsDigitizing.value) towardsDigitizing(addr, mValue, municipalityCode) else againstDigitizing(addr, mValue, municipalityCode)
      case None => throw new RoadAddressException("No road address found")
    }

    (address, roadSide)
  }
  def resolveMultipleAddressAndLocations(assets: Seq[PointAssetForConversion]): Seq[RoadAddressBoundToAsset] = {
    val roadAddress = roadAddressService.getAllByLinkIds(assets.map(_.linkId))
    val foundFromViite = assets.map(mapRoadAddressToAsset(roadAddress, _)).filter(_.isDefined).map(_.get)
    val allReadyMapped = foundFromViite.map(_.asset)
    val stillMissing = assets.filterNot(a => allReadyMapped.contains(a.id))
    logger.info(s"still missing road address: ${stillMissing.size}")
    val foundFromVKM = vkmClient.resolveAddressAndLocations(stillMissing.filter(_.road.isDefined).map(a => MassQueryResolve(a.id, a.coord, a.heading, SideCode.apply(a.sideCode.get), a.road)))
    foundFromVKM ++ foundFromViite
  }
  private def mapRoadAddressToAsset(roadAddress: Seq[RoadAddressForLink], asset: PointAssetForConversion): Option[RoadAddressBoundToAsset] = {
    // copy pasted from Viite code base Search API get by LRM
    // will return the road addresses with the start and end measure in between mValue or start measure equal or greater than mValue
    def selectRoadAddress(ra: RoadAddressForLink, asset: PointAssetForConversion): Boolean = {
      val isBetween = ra.startMValue < asset.mValue && asset.mValue < ra.endMValue
      ra.linkId == asset.linkId && (ra.startMValue >= asset.mValue || isBetween)
    }
    roadAddress.find(selectRoadAddress(_,asset)) match {
      case Some(selected) =>
        val (roadAddress, side) = determinateRoadSide(asset.mValue, asset.sideCode.get, asset.municipalityCode, Some(selected))
        Some(RoadAddressBoundToAsset(asset.id, roadAddress, side))
      case None => None
    }
  }
}


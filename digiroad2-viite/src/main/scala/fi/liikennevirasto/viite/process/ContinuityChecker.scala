package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao.Discontinuity._
import fi.liikennevirasto.viite.dao.{RoadAddress, RoadAddressDAO}

/**
  * Created by venholat on 15.9.2016.
  */

trait AddressError {
  val roadNumber: Long
  val roadPartNumber: Long
  val startMAddr: Option[Long]
  val endMAddr: Option[Long]
  val linkId: Option[Long]
}

case class MissingSegment(roadNumber: Long, roadPartNumber: Long, startMAddr: Option[Long], endMAddr: Option[Long], linkId: Option[Long]) extends AddressError
case class MissingLink(roadNumber: Long, roadPartNumber: Long, startMAddr: Option[Long], endMAddr: Option[Long], linkId: Option[Long]) extends AddressError

class ContinuityChecker(roadLinkService: RoadLinkService) {

  def checkRoadPart(roadNumber: Int, roadPartNumber: Int, checkMissingLinks: Boolean = false) = {
    val roadAddressList = RoadAddressDAO.fetchByRoadPart(roadNumber, roadPartNumber, true)
    assert(roadAddressList.groupBy(ra => (ra.roadNumber, ra.roadPartNumber)).keySet.size == 1, "Mixed roadparts present!")
    val missingSegments = checkAddressesHaveNoGaps(roadAddressList)
    // TODO: Combine these checks, maybe?
    if (checkMissingLinks)
      new FloatingChecker(roadLinkService).checkRoadNetwork().map(ra =>
        MissingLink(ra.roadNumber, ra.roadPartNumber, Some(ra.startAddrMValue), Some(ra.endAddrMValue), Some(ra.linkId)))
    else
      missingSegments
  }

  def checkRoad(roadNumber: Int) = {
    var current = Option(0)
    while (current.nonEmpty) {
      current = RoadAddressDAO.fetchNextRoadPartNumber(roadNumber, current.get)
      current.foreach(partNumber => checkRoadPart(roadNumber, partNumber))
    }
  }

  def checkRoadNetwork() = {
    var current = Option(0)
    while (current.nonEmpty) {
      current = RoadAddressDAO.fetchNextRoadNumber(current.get)
      current.foreach(checkRoad)
    }
  }

  def checkAddressesHaveNoGaps(addresses: Seq[RoadAddress]): Seq[MissingSegment] = {
    val addressMap = addresses.groupBy(_.startAddrMValue)
    val missingFirst = !addressMap.contains(0L) match {
      case true => Seq(MissingSegment(addresses.head.roadNumber, addresses.head.roadPartNumber,
        Some(0), Some(addresses.map(_.startAddrMValue).min), None))
      case _ => Seq()
    }
    missingFirst ++ addresses.filter(addressHasGapAfter(addressMap)).map(ra =>
      MissingSegment(ra.roadNumber, ra.roadPartNumber, Some(ra.startAddrMValue), None, None))
  }

  private def addressHasGapAfter(addressMap: Map[Long, Seq[RoadAddress]])(address: RoadAddress) = {
    // Test for end of road part
    def nothingAfter(address: RoadAddress, addressMap: Map[Long, Seq[RoadAddress]]) = {
      !addressMap.keySet.exists(_ >= address.endAddrMValue)
    }
    // Test if road part continues with combined tracks
    def combinedTrackFollows(address: RoadAddress, next: Seq[RoadAddress]) = {
      next.exists(na => na.track == Track.Combined)
    }
    // Test if same track continues (different tracks may have different start/end points)
    def sameTrackFollows(address: RoadAddress, next: Seq[RoadAddress]) = {
      next.exists(na => na.track == address.track)
    }
    // Test if combined track is now split into two => they both must be found
    def splitTrackFollows(address: RoadAddress, next: Seq[RoadAddress]) = {
        address.track == Track.Combined &&
          (next.exists(na => na.track == Track.LeftSide) &&
            next.exists(na => na.track == Track.RightSide))
    }

    val nextAddressItems = addressMap.getOrElse(address.endAddrMValue, Seq())
    !nothingAfter(address, addressMap) &&
      !(combinedTrackFollows(address, nextAddressItems) ||
        sameTrackFollows(address, nextAddressItems) ||
        splitTrackFollows(address, nextAddressItems))
  }

  private def checkLinksExist(addresses: Seq[RoadAddress]): Seq[MissingLink] = {
    Seq()
  }
}

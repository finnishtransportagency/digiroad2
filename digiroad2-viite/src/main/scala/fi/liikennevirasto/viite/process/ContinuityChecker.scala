package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.RoadLinkService
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao.Discontinuity._
import fi.liikennevirasto.viite.dao.{RoadAddress, RoadAddressDAO}

/**
  * Created by venholat on 15.9.2016.
  */

trait AddressError {
  val roadNumber: Int
  val roadPartNumber: Int
  val startMAddr: Option[Long]
  val endMAddr: Option[Long]
  val linkId: Option[Long]
}

case class MissingSegment(roadNumber: Int, roadPartNumber: Int, startMAddr: Option[Long], endMAddr: Option[Long], linkId: Option[Long]) extends AddressError
case class MissingLink(roadNumber: Int, roadPartNumber: Int, startMAddr: Option[Long], endMAddr: Option[Long], linkId: Option[Long]) extends AddressError

class ContinuityChecker(roadLinkService: RoadLinkService) {

  def checkRoadPart(roadNumber: Int, roadPartNumber: Int) = {
    val roadAddressList = RoadAddressDAO.fetchByRoadPart(roadNumber, roadPartNumber)
    assert(roadAddressList.groupBy(ra => (ra.roadNumber, ra.roadPartNumber)).keySet.size == 1, "Mixed roadparts present!")
    checkAddressesHaveNoGaps(roadAddressList)
    checkLinksExist(roadAddressList)
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

  private def checkAddressesHaveNoGaps(addresses: Seq[RoadAddress]) = {
    val addressMap = addresses.groupBy(_.startAddrMValue)
    val missingFirst = !addressMap.contains(0L)
    addresses.filter(addressHasGapAfter(addressMap))
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
    nothingAfter(address, addressMap) ||
      combinedTrackFollows(address, nextAddressItems) ||
      sameTrackFollows(address, nextAddressItems) ||
      splitTrackFollows(address, nextAddressItems)
  }

  private def nextSegment(addressMap: Map[Long, Seq[RoadAddress]])(address: RoadAddress) = {

  }

  private def checkLinksExist(addresses: Seq[RoadAddress]) = {

  }
}

package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao.{CalibrationPoint, RoadAddress}

object LinkRoadAddressCalculator {
  /**
    * Recalculate road address mapping to links for one road. This should only be run after ContinuityChecker has been
    * successfully run to the road address list.
    *
    * @param addressList RoadAddress objects for one road or road part
    */
  def recalculate(addressList: Seq[RoadAddress]) = {
    if (!addressList.forall(ra => ra.roadNumber == addressList.head.roadNumber))
      throw new InvalidAddressDataException("Multiple road numbers present in source data")
    val calibrationPoints = addressList.flatMap(ra => ra.calibrationPoints).distinct.groupBy(_.linkId)
    val (trackZero, others) = addressList.partition(ra => ra.track == Track.Combined)
    val (trackOne, trackTwo) = others.partition(ra => ra.track == Track.RightSide)
    recalculateTrack(trackZero) ++
      recalculateTrack(trackOne) ++
      recalculateTrack(trackTwo)
  }

  private def recalculateTrack(addressList: Seq[RoadAddress]) = {
    val groupedList = addressList.groupBy(_.roadPartNumber)
    groupedList.mapValues {
      case (addresses) =>
        val calibrationPoints: Seq[CalibrationPoint] = addressList.flatMap(_.calibrationPoints).distinct.sortBy(_.addressMValue)
        val sortedAddresses = addresses.sortBy(_.startAddrMValue)
        segmentize(sortedAddresses, calibrationPoints, Seq())
    }.values.flatten
  }

  private def segmentize(addresses: Seq[RoadAddress], calibrationPoints: Seq[CalibrationPoint], processed: Seq[RoadAddress]): Seq[RoadAddress] = {
    //TODO: Pushback if calibration point is in the middle of the segment?
    if (addresses.isEmpty)
      return processed
    if (calibrationPoints.isEmpty || calibrationPoints.tail.isEmpty)
      throw new InvalidAddressDataException("Ran out of calibration points")
    val startCP = calibrationPoints.head
    val endCP = calibrationPoints.tail.head
    val first = addresses.head
    // Test if this link is calibrated on both ends. Special case, then.
    if (startCP.linkId == endCP.linkId) {
      return segmentize(addresses.tail, calibrationPoints.tail.tail, processed) ++
        Seq(first.copy(startAddrMValue = startCP.addressMValue, endAddrMValue = endCP.addressMValue))
    }
    val cutPoint = addresses.tail.indexWhere(_.calibrationPoints.nonEmpty) + 2 // Adding one for .tail, one for 0 starting
    val (segments, others) = addresses.splitAt(cutPoint)
    val newGeom = segments.scanLeft((0.0, 0.0))({ case (runningLen, address) => (runningLen._2, runningLen._2 + linkLength(address))}).tail
    val coefficient = (endCP.addressMValue - startCP.addressMValue) / newGeom.last._2
    segmentize(others, calibrationPoints.tail.tail, processed ++
      segments.zip(newGeom).map {
        case (ra, (cumStart, cumEnd)) =>
          ra.copy(startAddrMValue = Math.round(coefficient * cumStart) + startCP.addressMValue,
            endAddrMValue = Math.round(coefficient * cumEnd) + startCP.addressMValue)

      }
    )
  }

  private def linkLength(roadAddress: RoadAddress) = {
    roadAddress.endMValue - roadAddress.startMValue
  }
}

class InvalidAddressDataException(string: String) extends RuntimeException;

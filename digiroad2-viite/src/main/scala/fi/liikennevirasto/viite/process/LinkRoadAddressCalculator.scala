package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao.{CalibrationPoint, RoadAddress}

trait LinkRoadAddressCalculator {
  def recalculate(addressList: Seq[RoadAddress]): Seq[RoadAddress]
}
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
    addressList.groupBy(_.roadPartNumber).flatMap{ case (_, seq) => recalculatePart(seq) }.toSeq
  }

  /**
    * Recalculate road address mapping to links for one road. This should only be run after ContinuityChecker has been
    * successfully run to the road address list.
    *
    * @param addressList RoadAddress objects for one road or road part
    */
  private def recalculatePart(addressList: Seq[RoadAddress]): Seq[RoadAddress] = {
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
        val sortedAddresses = addresses.sortBy(_.startAddrMValue)
        segmentize(sortedAddresses, Seq())
    }.values.flatten.toSeq
  }

  private def segmentize(addresses: Seq[RoadAddress], processed: Seq[RoadAddress]): Seq[RoadAddress] = {
    if (addresses.isEmpty)
      return processed
    val calibrationPointsS = addresses.flatMap(ra =>
      ra.calibrationPoints._1).sortBy(_.addressMValue)
    val calibrationPointsE = addresses.flatMap(ra =>
      ra.calibrationPoints._2).sortBy(_.addressMValue)
    if (calibrationPointsS.isEmpty || calibrationPointsE.isEmpty)
      throw new InvalidAddressDataException("Ran out of calibration points")
    val startCP = calibrationPointsS.head
    val endCP = calibrationPointsE.head
    if (calibrationPointsS.tail.exists(_.addressMValue < endCP.addressMValue)) {
      throw new InvalidAddressDataException("Starting calibration point without an ending one")
    }
    // Test if this link is calibrated on both ends. Special case, then.
    val cutPoint = addresses.indexWhere(_.calibrationPoints._2.contains(endCP)) + 1
    val (segments, others) = addresses.splitAt(cutPoint)
    segmentize(others, processed ++ adjustGeometry(segments, startCP, endCP))
  }

  private def linkLength(roadAddress: RoadAddress) = {
    roadAddress.endMValue - roadAddress.startMValue
  }

  private def adjustGeometry(segments: Seq[RoadAddress], startingCP: CalibrationPoint, endingCP: CalibrationPoint) = {
    val newGeom = segments.scanLeft((0.0, 0.0))({ case (runningLen, address) => (runningLen._2, runningLen._2 + linkLength(address))}).tail
    val coefficient = (endingCP.addressMValue - startingCP.addressMValue) / newGeom.last._2
    segments.zip(newGeom).map {
      case (ra, (cumStart, cumEnd)) =>
        ra.copy(startAddrMValue = Math.round(coefficient * cumStart) + startingCP.addressMValue, endAddrMValue = Math.round(coefficient * cumEnd) + startingCP.addressMValue)

    }
  }
}

class InvalidAddressDataException(string: String) extends RuntimeException {
  override def getMessage: String = string
};

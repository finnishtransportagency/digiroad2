package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao.{BaseRoadAddress, CalibrationPoint, ProjectLink, RoadAddress}

trait LinkRoadAddressCalculator {
  def recalculate[T <: BaseRoadAddress](addressList: Seq[T]): Seq[T]
}
object LinkRoadAddressCalculator {
  /**
    * Recalculate road address mapping to links for one road. This should only be run after ContinuityChecker has been
    * successfully run to the road address list.
    *
    * @param addressList RoadAddress objects for one road or road part
    */
  def recalculate[T <: BaseRoadAddress](addressList: Seq[T]): Seq[T] = {
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
  private def recalculatePart[T <: BaseRoadAddress](addressList: Seq[T]): Seq[T] = {
    val (trackZero, others) = addressList.partition(ra => ra.track == Track.Combined)
    val (trackOne, trackTwo) = others.partition(ra => ra.track == Track.RightSide)
    recalculateTrack(trackZero) ++
      recalculateTrack(trackOne) ++
      recalculateTrack(trackTwo)
  }
  private def recalculateTrack[T <: BaseRoadAddress](addressList: Seq[T]): Seq[T] = {
    val groupedList = addressList.groupBy(_.roadPartNumber)
    groupedList.mapValues {
      case (addresses) =>
        val sortedAddresses = addresses.sortBy(_.startAddrMValue)
        segmentize(sortedAddresses, Seq())
    }.values.flatten.toSeq
  }

  private def segmentize[T <: BaseRoadAddress](addresses: Seq[T], processed: Seq[T]): Seq[T] = {
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

  private def linkLength(roadAddress: BaseRoadAddress) = {
    roadAddress.endMValue - roadAddress.startMValue
  }

  private def adjustGeometry[T <: BaseRoadAddress](segments: Seq[T], startingCP: CalibrationPoint, endingCP: CalibrationPoint): Seq[T] = {
    val newGeom = segments.scanLeft((0.0, 0.0))({ case (runningLen, address) => (runningLen._2, runningLen._2 + linkLength(address))}).tail
    val coefficient = (endingCP.addressMValue - startingCP.addressMValue) / newGeom.last._2
    segments.zip(newGeom).map {
      case (t, (cumStart, cumEnd)) =>
        (t match {
          case ra: RoadAddress => ra.copy(startAddrMValue = Math.round(coefficient * cumStart) + startingCP.addressMValue, endAddrMValue = Math.round(coefficient * cumEnd) + startingCP.addressMValue)
          case pl: ProjectLink => pl.copy(startAddrMValue = Math.round(coefficient * cumStart) + startingCP.addressMValue, endAddrMValue = Math.round(coefficient * cumEnd) + startingCP.addressMValue)
          case _ => t
        }).asInstanceOf[T]
    }
  }
}

class InvalidAddressDataException(string: String) extends RuntimeException {
  override def getMessage: String = string
};

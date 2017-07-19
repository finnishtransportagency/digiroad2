package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao.{CalibrationPoint, ProjectLink}

/**
  *
  */
trait ProjectLinkCalculator {

  def recalculate (projectLinkList: Seq[ProjectLink]): Seq[ProjectLink]

}

object ProjectLinkCalculator {

  def recalculate(projectLinkList: Seq[ProjectLink]) = {
    if (!projectLinkList.forall(ra => ra.roadNumber == projectLinkList.head.roadNumber))
      throw new InvalidAddressDataException("Multiple road numbers present in source data")
    projectLinkList.groupBy(_.roadPartNumber).flatMap{ case (_, seq) => recalculatePart(seq) }.toSeq
  }

  private def recalculatePart(projectLinkList: Seq[ProjectLink]): Seq[ProjectLink] = {
    val (trackZero, others) = projectLinkList.partition(ra => ra.track == Track.Combined)
    val (trackOne, trackTwo) = others.partition(ra => ra.track == Track.RightSide)
    recalculateTrack(trackZero) ++
      recalculateTrack(trackOne) ++
      recalculateTrack(trackTwo)
  }
  private def recalculateTrack(projectLinkList: Seq[ProjectLink]) = {
    val groupedList = projectLinkList.groupBy(_.roadPartNumber)
    groupedList.mapValues {
      case (projectLinks) =>
        val sortedProjectLinks = projectLinks.sortBy(_.startAddrMValue)
        segmentize(sortedProjectLinks, Seq())
    }.values.flatten.toSeq
  }

  private def segmentize(projectLinks: Seq[ProjectLink], processed: Seq[ProjectLink]): Seq[ProjectLink] = {
    if (projectLinks.isEmpty)
      return processed
    val calibrationPointsS = projectLinks.flatMap(ra =>
      ra.calibrationPoints._1).sortBy(_.addressMValue)
    val calibrationPointsE = projectLinks.flatMap(ra =>
      ra.calibrationPoints._2).sortBy(_.addressMValue)
    if (calibrationPointsS.isEmpty || calibrationPointsE.isEmpty)
      throw new InvalidAddressDataException("Ran out of calibration points")
    val startCP = calibrationPointsS.head
    val endCP = calibrationPointsE.head
    if (calibrationPointsS.tail.exists(_.addressMValue < endCP.addressMValue)) {
      throw new InvalidAddressDataException("Starting calibration point without an ending one")
    }
    // Test if this link is calibrated on both ends. Special case, then.
    val cutPoint = projectLinks.indexWhere(_.calibrationPoints._2.contains(endCP)) + 1
    val (segments, others) = projectLinks.splitAt(cutPoint)
    segmentize(others, processed ++ adjustGeometry(segments, startCP, endCP))
  }


  private def linkLength(projectLink: ProjectLink) = {
    projectLink.endMValue - projectLink.startMValue
  }

  private def adjustGeometry(segments: Seq[ProjectLink], startingCP: CalibrationPoint, endingCP: CalibrationPoint) = {
    val newGeom = segments.scanLeft((0.0, 0.0))({ case (runningLen, projectLink) => (runningLen._2, runningLen._2 + linkLength(projectLink))}).tail
    val coefficient = (endingCP.addressMValue - startingCP.addressMValue) / newGeom.last._2
    segments.zip(newGeom).map {
      case (ra, (cumStart, cumEnd)) =>
        ra.copy(startAddrMValue = Math.round(coefficient * cumStart) + startingCP.addressMValue, endAddrMValue = Math.round(coefficient * cumEnd) + startingCP.addressMValue)
    }
  }

}

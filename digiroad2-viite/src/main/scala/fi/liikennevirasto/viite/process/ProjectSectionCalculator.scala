package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.viite.RoadType
import fi.liikennevirasto.viite.dao._
import org.slf4j.LoggerFactory

object ProjectSectionCalculator {

  val logger = LoggerFactory.getLogger(getClass)

  /**
    * Order list of geometrically continuous Project Links so that the chain is traversable from end to end
    * in this sequence and side code
    * @param list Project Links (already partitioned; connected)
    * @return Ordered list of project links
    */
  def orderProjectLinksTopologyByGeometry(list:Seq[ProjectLink]): Seq[ProjectLink] = {
    // Calculates the new end point from geometry that is not the oldEndPoint
    def getNewEndPoint(geometry: Seq[Point], oldEndPoint: Point): Point = {
      val s = Seq(geometry.head, geometry.last)
      s.maxBy(p => (oldEndPoint - p).length())
    }
    def setSideCode(growing: SideCode, point: Point, newLink: ProjectLink): Seq[ProjectLink] = {
      if (GeometryUtils.areAdjacent(newLink.geometry.head, point))
        Seq(newLink.copy(sideCode = growing))
      else
        Seq(newLink.copy(sideCode = switchSideCode(growing)))
    }
    // Finds either a project link that continues from current head; or end; or returns the whole bunch back
    def recursiveFindAndExtend(sortedList: Seq[ProjectLink], unprocessed: Seq[ProjectLink], head: Point, end: Point) : Seq[ProjectLink] = {
      val headExtend = unprocessed.find(l => GeometryUtils.areAdjacent(l.geometry, head))
      if (headExtend.nonEmpty) {
        val ext = headExtend.get
        recursiveFindAndExtend(setSideCode(AgainstDigitizing, head, ext)++sortedList, unprocessed.filterNot(p => p.linkId == ext.linkId), getNewEndPoint(ext.geometry, head), end)
      } else {
        val tailExtend = unprocessed.find(l => GeometryUtils.areAdjacent(l.geometry, end))
        if (tailExtend.nonEmpty) {
          val ext = tailExtend.get
          recursiveFindAndExtend(sortedList++setSideCode(TowardsDigitizing, end, ext), unprocessed.filterNot(p => p.linkId == ext.linkId), head, getNewEndPoint(ext.geometry, end))
        } else {
          sortedList ++ unprocessed
        }
      }
    }
    // TODO: When tracks that cross themselves are accepted the logic comes here. Now just return the list unchanged.
    if (GeometryUtils.isNonLinear(list))
      return list

    val head = list.head
    val (headPoint, endPoint) = head.sideCode match {
      case AgainstDigitizing => (head.geometry.last, head.geometry.head)
      case _ => (head.geometry.head, head.geometry.last)
    }
    recursiveFindAndExtend(Seq(list.head), list.tail, headPoint, endPoint)
  }

  /**
    * Calculates the address M values for the given set of project links and assigns them calibration points where applicable
    * @param newProjectLinks List of new addressed links in project
    * @param oldProjectLinks Other links in project, used as a guidance (TODO)
    * @return Sequence of project links with address values and calibration points.
    */
  def determineMValues(newProjectLinks: Seq[ProjectLink], oldProjectLinks: Seq [ProjectLink]): Seq[ProjectLink] = {
    // Sort: smallest endAddrMValue is first but zero does not count.
    def addressSort(projectLink1: ProjectLink, projectLink2: ProjectLink): Boolean = {
      // Test if exactly one already was in project -> prefer them for starting points.
      if (oldProjectLinks.exists(lip => lip.linkId == projectLink1.linkId) ^ oldProjectLinks.exists(lip => lip.linkId == projectLink2.linkId)) {
        oldProjectLinks.exists(lip => lip.linkId == projectLink1.linkId)
      } else {
        if (projectLink1.endAddrMValue != projectLink2.endAddrMValue) {
          if (projectLink1.endAddrMValue == 0)
            false
          else {
            projectLink1.endAddrMValue < projectLink2.endAddrMValue || projectLink2.endAddrMValue == 0
          }
        } else {
          projectLink1.linkId < projectLink2.linkId
        }
      }
    }
    def makeStartCP(projectLink: ProjectLink) = {
      Some(CalibrationPoint(projectLink.linkId, if (projectLink.sideCode == TowardsDigitizing) 0.0 else projectLink.geometryLength, projectLink.startAddrMValue))
    }
    def makeEndCP(projectLink: ProjectLink) = {
      Some(CalibrationPoint(projectLink.linkId, if (projectLink.sideCode == AgainstDigitizing) 0.0 else projectLink.geometryLength, projectLink.endAddrMValue))
    }
    def assignCalibrationPoints(ready: Seq[ProjectLink], unprocessed: Seq[ProjectLink], reverse: Boolean = false): Seq[ProjectLink] = {
      if (reverse) {
        assignCalibrationPoints(ready.reverse, unprocessed.reverse)
      } else {
        // If first one
        if (ready.isEmpty) {
          val link = unprocessed.head
          // If there is only one link in section we put two calibration points in it
          if (unprocessed.size == 1)
            Seq(link.copy(calibrationPoints = (makeStartCP(link), makeEndCP(link))))
          else
            assignCalibrationPoints(Seq(link.copy(calibrationPoints = (makeStartCP(link), None))), unprocessed.tail)
          // If last one
        } else if (unprocessed.tail.isEmpty) {
          val link = unprocessed.head
          ready ++ Seq(link.copy(calibrationPoints = (None, makeEndCP(link))))
        } else {
          // a middle one, add to sequence and continue
          assignCalibrationPoints(ready ++ Seq(unprocessed.head.copy(calibrationPoints = (None, None))), unprocessed.tail)
        }
      }
    }
    // Set address values to section
    def assignLinkValues(section: CombinedSection, startMValue: Long, endMValue: Long): Seq[ProjectLink] = {
      val coeff = (endMValue - startMValue).toDouble / section.right.links.map(_.geometryLength).sum
      val runningLength = section.right.links.scanLeft(startMValue.toDouble){case (value, plink) =>
        value + coeff*plink.geometryLength
      }
      // Calculate address values to right track
      val rightLinks = section.right.links.zip(runningLength.zip(runningLength.tail)).map { case (plink, (start, end)) =>
        plink.copy(startAddrMValue = Math.min(Math.round(start), Math.round(end)),
          endAddrMValue = Math.max(Math.round(start), Math.round(end)),
          startMValue = 0.0, endMValue = plink.geometryLength)
      }
      // Check if this is a combined track
      if (section.right == section.left)
        assignCalibrationPoints(Seq(), rightLinks)
      else {
        val coeffL = (endMValue - startMValue).toDouble / section.left.links.map(_.geometryLength).sum
        val runningLengthL = section.left.links.scanLeft(startMValue.toDouble){case (value, plink) =>
          value + coeffL*plink.geometryLength
        }
        // Calculate left track values
        assignCalibrationPoints(Seq(), rightLinks) ++
          assignCalibrationPoints(Seq(),
            section.left.links.zip(runningLengthL.zip(runningLengthL.tail)).map { case (plink, (start, end)) =>
              plink.copy(startAddrMValue = Math.min(Math.round(start), Math.round(end)),
                endAddrMValue = Math.max(Math.round(start), Math.round(end)),
                startMValue = 0.0, endMValue = plink.geometryLength)
            })
      }
    }
    val groupedProjectLinks = newProjectLinks.groupBy(record => (record.roadNumber, record.roadPartNumber))
    groupedProjectLinks.flatMap(gpl => {
      val linkPartitions = SectionPartitioner.partition(gpl._2).map(_.sortWith(addressSort)).map(orderProjectLinksTopologyByGeometry)
      try {
        val sections = linkPartitions.map(lp =>
          TrackSection(lp.head.roadNumber, lp.head.roadPartNumber, lp.head.track, lp.map(_.geometryLength).sum, lp)
        )
        val existingSections = SectionPartitioner.partition(oldProjectLinks).map(_.sortWith(addressSort)).map(lp =>
          TrackSection(lp.head.roadNumber, lp.head.roadPartNumber, lp.head.track, lp.map(_.geometryLength).sum, lp)
        )
        val ordSections = orderTrackSectionsByGeometry(sections ++ existingSections)
        val mValues = calculateSectionAddressValues(ordSections)
        ordSections.zip(mValues.zip(mValues.tail)).flatMap { case (section, (start, end)) =>
          assignLinkValues(section, start.toLong, end.toLong)
        }
      } catch {
        case ex: InvalidAddressDataException =>
          logger.info(s"Can't calculate road/road part ${gpl._1._1}/${gpl._1._2}: " + ex.getMessage)
          gpl._2
      }
    }).toSeq
  }

  private def calculateSectionAddressValues(sections: Seq[CombinedSection]): Seq[Double] = {
    // Return fixed value for this section if it has one
    def fixedValue(param: (CombinedSection, Option[CombinedSection])): Option[Double] = {
      val (currentSection, nextSection) = param
      (currentSection.linkStatus, nextSection.map(_.linkStatus)) match {
        case (LinkStatus.UnChanged, _) => Some(currentSection.endAddrM)
        case (_, Some(LinkStatus.UnChanged)) => nextSection.map(_.startAddrM)
        case (_, _) => None
      }
    }
    // Create equally long sequence with optionality
    val following = sections.tail.map(cs => Some(cs)) ++ Seq(None)
    sections.zip(following).scanLeft(0.0) { case (mValue, sec) =>
      fixedValue(sec) match {
        case None => mValue + sec._1.geometryLength
        case Some(fixedValue) => fixedValue
      }
    }
  }


  /**
    * Turn track sections into an ordered sequence of combined sections so that they can be addressed in that order.
    * Combined section has two tracks (left and right) of project links which are the same if it is a combined track.
    * Right one is the one where we assign the direction should they ever disagree.
    * @param sections
    * @return
    */
  def orderTrackSectionsByGeometry(sections: Seq[TrackSection]): Seq[CombinedSection] = {
    def reverseCombinedSection(section: CombinedSection): CombinedSection = {
      section.copy(left = reverseSection(section.left), right = reverseSection(section.right))
    }

    def reverseSection(section: TrackSection): TrackSection = {
      section.copy(links =
        section.links.map(l => l.copy(sideCode = switchSideCode(l.sideCode))).reverse)
    }

    def closest(section: TrackSection, candidates: Seq[TrackSection]): TrackSection = {
      candidates.minBy(ts =>
        Math.min(
          (section.startGeometry - ts.startGeometry).length() + (section.endGeometry - ts.endGeometry).length(),
          (section.startGeometry - ts.endGeometry).length() + (section.endGeometry - ts.startGeometry).length()
        ))
    }

    // TODO: Ramps work differently, may have only right tracks? Or combined?
    def combineTracks(tracks: Seq[TrackSection]): Seq[CombinedSection] = {
      val (combined, others) = tracks.partition(_.track == Track.Combined)
      if (others.isEmpty)
        return combined.map(ts => CombinedSection(ts.startGeometry, ts.endGeometry, ts.geometryLength, ts, ts))
      val (left, right) = others.partition(_.track == Track.LeftSide)
      if (left.size != right.size)
        throw new InvalidAddressDataException(s"Non-matching left/right tracks on road ${others.head.roadNumber} part ${others.head.roadPartNumber}")
      val pick = right.head
      val pair = closest(pick, left)
      val pickVector = pick.endGeometry - pick.startGeometry
      val pairVector = pair.endGeometry - pair.startGeometry
      // Test if tracks are going opposite directions and if they touch each other but don't have same direction
      val (oppositeDirections, mustReverse) = (pickVector.dot(pairVector) < 0,
        GeometryUtils.areAdjacent(pick.startGeometry, pair.endGeometry) ||
        GeometryUtils.areAdjacent(pick.endGeometry, pair.startGeometry))
      // If tracks are not headed the same direction (less than 90 degree angle) and are not connected then reverse
      val combo = if (oppositeDirections || mustReverse)
        CombinedSection(pick.startGeometry, pick.endGeometry, (pick.geometryLength + pair.geometryLength)/2.0, reverseSection(pair), pick)
      else
        CombinedSection(pick.startGeometry, pick.endGeometry, (pick.geometryLength + pair.geometryLength)/2.0, pair, pick)
      Seq(combo) ++ combineTracks(combined ++ right.tail ++ left.filterNot(_ == pair))
    }

    def recursiveFindAndExtendCombined(sortedList: Seq[CombinedSection],
                                       unprocessed: Seq[CombinedSection]): Seq[CombinedSection] = {
      if (unprocessed.isEmpty)
        return sortedList
      val head = sortedList.head
      val last = sortedList.last
      val (headPoint, endPoint) = (
        head.sideCode match {
          case AgainstDigitizing => head.endGeometry
          case _ => head.startGeometry
        },
        last.sideCode match {
          case AgainstDigitizing => last.startGeometry
          case _ => last.endGeometry
        }
      )
      val sorted = unprocessed.sortBy { ts =>
        Math.min(
          Math.min((headPoint - ts.addressEndGeometry).length(),
            (endPoint - ts.addressStartGeometry).length()),
          Math.min((endPoint - ts.addressEndGeometry).length(),
            (headPoint - ts.addressStartGeometry).length()))
      }
      val next = Seq(sorted.head, reverseCombinedSection(sorted.head)).minBy(ts =>
        Math.min((headPoint - ts.addressEndGeometry).length(),
        (endPoint - ts.addressStartGeometry).length()))

      if ((next.addressStartGeometry - endPoint).length() <
        (next.addressEndGeometry - headPoint).length())
        recursiveFindAndExtendCombined(sortedList ++ Seq(next), sorted.tail)
      else
        recursiveFindAndExtendCombined(Seq(next) ++ sortedList, sorted.tail)
    }

    val combined = combineTracks(sections)
    recursiveFindAndExtendCombined(Seq(combined.head), combined.tail)
  }

  def switchSideCode(sideCode: SideCode): SideCode = {
    // Switch between against and towards 2 -> 3, 3 -> 2
    SideCode.apply(5-sideCode.value)
  }

}
case class RoadAddressSection(roadNumber: Long, roadPartNumberStart: Long, roadPartNumberEnd: Long, track: Track,
                              startMAddr: Long, endMAddr: Long, discontinuity: Discontinuity, roadType: RoadType) {
  def includes(ra: RoadAddress): Boolean = {
    // within the road number and parts included
    ra.roadNumber == roadNumber && ra.roadPartNumber >= roadPartNumberStart && ra.roadPartNumber <= roadPartNumberEnd &&
      // and on the same track
      ra.track == track &&
      // and not starting before this section start or after this section ends
      !(ra.startAddrMValue < startMAddr && ra.roadPartNumber == roadPartNumberStart ||
        ra.startAddrMValue > endMAddr && ra.roadPartNumber == roadPartNumberEnd) &&
      // and not ending after this section ends or before this section starts
      !(ra.endAddrMValue > endMAddr && ra.roadPartNumber == roadPartNumberEnd ||
        ra.endAddrMValue < startMAddr && ra.roadPartNumber == roadPartNumberStart)
  }
}
case class RoadLinkLength(linkId: Long, geometryLength: Double)
case class TrackSection(roadNumber: Long, roadPartNumber: Long, track: Track,
                        geometryLength: Double, links: Seq[ProjectLink]) {
  lazy val startGeometry: Point = links.head.sideCode match {
    case AgainstDigitizing => links.head.geometry.last
    case _ => links.head.geometry.head
  }
  lazy val endGeometry: Point = links.last.sideCode match {
    case AgainstDigitizing => links.last.geometry.head
    case _ => links.last.geometry.last
  }
}
case class CombinedSection(startGeometry: Point, endGeometry: Point, geometryLength: Double, left: TrackSection, right: TrackSection) {
  lazy val sideCode: SideCode = {
    if (GeometryUtils.areAdjacent(startGeometry, right.links.head.geometry.head))
      right.links.head.sideCode
    else
      SideCode.apply(5-right.links.head.sideCode.value)
  }
  lazy val addressStartGeometry: Point = sideCode match {
    case AgainstDigitizing => endGeometry
    case _ => startGeometry
  }

  lazy val addressEndGeometry: Point = sideCode match {
    case AgainstDigitizing => startGeometry
    case _ => endGeometry
  }

  lazy val linkStatus: LinkStatus = right.links.head.status

  lazy val startAddrM: Long = right.links.map(_.startAddrMValue).min

  lazy val endAddrM: Long = right.links.map(_.endAddrMValue).max
}


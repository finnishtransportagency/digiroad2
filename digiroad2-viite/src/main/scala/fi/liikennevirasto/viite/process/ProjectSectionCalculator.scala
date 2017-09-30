package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.util.{RoadAddressException, Track}
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point, Vector3d}
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
    * Recalculates the AddressMValues for project links. LinkStatus.New will get reassigned values and all
    * others will have the transfer/unchanged rules applied for them.
    * Terminated links will not be recalculated
    * @param projectLinks List of addressed links in project
    * @return Sequence of project links with address values and calibration points.
    */
  def assignMValues(projectLinks: Seq[ProjectLink], userGivenCalibrationPoints: Seq[CalibrationPoint] = Seq()): Seq[ProjectLink] = {
    logger.info(s"Starting MValue assignment for ${projectLinks.size} links")
    val (terminated, others) = projectLinks.partition(_.status == LinkStatus.Terminated)
    val (newLinks, nonTerminatedLinks) = others.partition(l => l.status == LinkStatus.New)
    try {
      assignMValues(newLinks, nonTerminatedLinks, userGivenCalibrationPoints) ++ terminated
    } finally {
      logger.info(s"Finished MValue assignment for ${projectLinks.size} links")
    }
  }

  def findStartingPoints(newLinks: Seq[ProjectLink], oldLinks: Seq[ProjectLink],
                                calibrationPoints: Seq[CalibrationPoint]): (Point, Point) = {
    val rightStartPoint = findStartingPoint(newLinks.filter(_.track != Track.LeftSide), oldLinks.filter(_.track != Track.LeftSide),
      calibrationPoints)
    // Get left track non-connected points and find the closest to right track starting point
    val leftLinks = newLinks.filter(_.track != Track.RightSide) ++ oldLinks.filter(_.track != Track.RightSide)
    val leftLinkEnds = leftLinks.flatMap(pl => Seq(pl.startingPoint, pl.endPoint))
    val leftPoints = leftLinkEnds.filterNot(p1 => leftLinkEnds.count(p2 => GeometryUtils.areAdjacent(p1, p2)) > 1)
    if (leftPoints.isEmpty)
      throw new InvalidAddressDataException("Missing left track starting points")
    val leftStartPoint = leftPoints.minBy(lp => (lp - rightStartPoint).length())
    (rightStartPoint, leftStartPoint)

  }
  /**
    * Find a starting point for this road part
    * @param newLinks Status = New links that need to have an address
    * @param oldLinks Other links that already existed before the project
    * @return Starting point
    */
  private def findStartingPoint(newLinks: Seq[ProjectLink], oldLinks: Seq[ProjectLink],
                                calibrationPoints: Seq[CalibrationPoint]): Point = {
    def calibrationPointToPoint(calibrationPoint: CalibrationPoint): Option[Point] = {
      val link = oldLinks.find(_.linkId == calibrationPoint.linkId).orElse(newLinks.find(_.linkId == calibrationPoint.linkId))
      link.flatMap(pl => GeometryUtils.calculatePointFromLinearReference(pl.geometry, calibrationPoint.segmentMValue))
    }
    def addressSort(projectLink1: ProjectLink, projectLink2: ProjectLink): Boolean = {
      // Test if exactly one already was in project -> prefer them for starting points.
      if (oldLinks.exists(lip => lip.linkId == projectLink1.linkId) ^ oldLinks.exists(lip => lip.linkId == projectLink2.linkId)) {
        oldLinks.exists(lip => lip.linkId == projectLink1.linkId)
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
    // Pick the one with calibration point set to zero: or any old link with lowest address: or new links by direction
    calibrationPoints.find(_.addressMValue == 0).flatMap(calibrationPointToPoint).getOrElse(
      oldLinks.filter(_.status == LinkStatus.UnChanged).sortWith(addressSort).headOption.map(_.startingPoint).getOrElse {
        val remainLinks = oldLinks ++ newLinks
        if (remainLinks.isEmpty)
          throw new InvalidAddressDataException("Missing right track starting project links")
        val points = remainLinks.map(pl => (pl.startingPoint, pl.endPoint))
        val direction = points.map(p => p._2 - p._1).fold(Vector3d(0, 0, 0)) { case (v1, v2) => v1 + v2 }.normalize2D()
        // Approximate estimate of the mid point: averaged over count, not link length
        val midPoint = points.map(p => p._1 + (p._2 - p._1).scale(0.5)).foldLeft(Vector3d(0,0,0)){case (x, p) =>
          (p - Point(0,0)).scale(1.0/points.size) + x}

        points.flatMap{ case (start, end) =>
          val stSeq = if (points.exists(p => GeometryUtils.areAdjacent(p._2, start)))
            Seq()
          else
            Seq(start -> direction.dot(start.toVector - midPoint))
          val endSeq = if (points.exists(p => GeometryUtils.areAdjacent(p._1, end)))
            Seq()
          else
            Seq(end -> direction.dot(end.toVector - midPoint))
          stSeq ++ endSeq
        }.minBy(_._2)._1
      }

    )
  }

  /**
    * Calculates the address M values for the given set of project links and assigns them calibration points where applicable
    *
    * @param newProjectLinks List of new addressed links in project
    * @param oldProjectLinks Other links in project, used as a guidance
    * @return Sequence of project links with address values and calibration points.
    */
  private def assignMValues(newProjectLinks: Seq[ProjectLink], oldProjectLinks: Seq[ProjectLink],
                            userCalibrationPoints: Seq[CalibrationPoint]): Seq[ProjectLink] = {
    // TODO: use user given calibration points (US-564, US-666, US-639)
    // Sort: smallest endAddrMValue is first but zero does not count.
    def makeStartCP(projectLink: ProjectLink) = {
      Some(CalibrationPoint(projectLink.linkId, if (projectLink.sideCode == TowardsDigitizing) 0.0 else projectLink.geometryLength, projectLink.startAddrMValue))
    }
    def makeEndCP(projectLink: ProjectLink) = {
      Some(CalibrationPoint(projectLink.linkId, if (projectLink.sideCode == AgainstDigitizing) 0.0 else projectLink.geometryLength, projectLink.endAddrMValue))
    }
    def assignCalibrationPoints(ready: Seq[ProjectLink], unprocessed: Seq[ProjectLink]): Seq[ProjectLink] = {
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

    def eliminateExpiredCalibrationPoints(roadPartLinks: Seq[ProjectLink]): Seq[ProjectLink] = {
      val tracks = roadPartLinks.groupBy(_.track)
      tracks.mapValues{ links =>
        links.map{ l =>
          val calibrationPoints =
            l.calibrationPoints match {
              case (None, None) => l.calibrationPoints
              case (Some(st), None) =>
                if (links.exists(_.endAddrMValue == st.addressMValue))
                  (None, None)
                else
                  l.calibrationPoints
              case (None, Some(en)) =>
                if (links.exists(_.startAddrMValue == en.addressMValue))
                  (None, None)
                else
                  l.calibrationPoints
              case (Some(st), Some(en)) =>
                (
                  if (links.exists(_.endAddrMValue == st.addressMValue))
                    None
                  else
                    Some(st),
                  if (links.exists(_.startAddrMValue == en.addressMValue))
                    None
                  else
                    Some(en)
                )
            }
          l.copy(calibrationPoints = calibrationPoints)
        }
      }.values.flatten.toSeq
    }
    val groupedProjectLinks = newProjectLinks.groupBy(record => (record.roadNumber, record.roadPartNumber))
    val groupedOldLinks = oldProjectLinks.groupBy(record => (record.roadNumber, record.roadPartNumber))
    val group = (groupedProjectLinks.keySet ++ groupedOldLinks.keySet).map(k =>
      k -> (groupedProjectLinks.getOrElse(k, Seq()), groupedOldLinks.getOrElse(k, Seq())))
    group.flatMap { case (part, (projectLinks, oldLinks)) => {
      try {
        val (right, left) = TrackSectionOrder.orderProjectLinksTopologyByGeometry(
          findStartingPoints(projectLinks, oldLinks, userCalibrationPoints), projectLinks++oldLinks)
        val ordSections = TrackSectionOrder.createCombinedSections(right, left)

        val links = calculateSectionAddressValues(ordSections).flatMap { sec =>
          if (sec.right == sec.left)
            assignCalibrationPoints(Seq(), sec.right.links)
          else {
            assignCalibrationPoints(Seq(), sec.right.links) ++
              assignCalibrationPoints(Seq(), sec.left.links)
          }
        }
        eliminateExpiredCalibrationPoints(links)
      } catch {
        case ex: InvalidAddressDataException =>
          logger.info(s"Can't calculate road/road part ${part._1}/${part._2}: " + ex.getMessage)
          projectLinks ++ oldLinks
      }
    }
    }.toSeq
  }

  private def calculateSectionAddressValues(sections: Seq[CombinedSection]): Seq[CombinedSection] = {
    def getContinuousTrack(seq: Seq[ProjectLink]): (Seq[ProjectLink], Seq[ProjectLink]) = {
      val track = seq.headOption.map(_.track).getOrElse(Track.Unknown)
      seq.span(_.track == track)
    }
    def getFixedAddress(rightLink: ProjectLink, leftLink: ProjectLink): Option[(Long, Long)] = {
      if (rightLink.status == LinkStatus.UnChanged) {
        Some((rightLink.startAddrMValue, rightLink.endAddrMValue))
      } else {
        if (leftLink.status == LinkStatus.UnChanged)
          Some((leftLink.startAddrMValue, leftLink.endAddrMValue))
        else
          None
      }
    }

    def assignValues(seq: Seq[ProjectLink], st: Long, en: Long, factor: TrackAddressingFactors): Seq[ProjectLink] = {
      val coEff = (en - st - factor.unChangedLength - factor.transferLength) / factor.newLength
      ProjectSectionMValueCalculator.assignLinkValues(seq, Some(st.toDouble), coEff)
    }
    def adjustTwoTracks(right: Seq[ProjectLink], left: Seq[ProjectLink], startM: Option[Long], endM: Option[Long]) = {
      val (rst, lst, ren, len) = (right.head.startAddrMValue, left.head.startAddrMValue, right.last.endAddrMValue,
        left.last.endAddrMValue)
      val st = startM.getOrElse(if (rst > lst) Math.ceil(0.5*(rst+lst)).round else Math.floor(0.5*(rst+lst)).round)
      val en = endM.getOrElse(if (ren > len) Math.ceil(0.5*(ren+len)).round else Math.floor(0.5*(ren+len)).round)
      (assignValues(right, st, en, ProjectSectionMValueCalculator.calculateAddressingFactors(right)),
        assignValues(left, st, en, ProjectSectionMValueCalculator.calculateAddressingFactors(left)))
    }
    def adjustTracksToMatch(rightLinks: Seq[ProjectLink], leftLinks: Seq[ProjectLink], fixedStart: Option[Long]): (Seq[ProjectLink], Seq[ProjectLink]) = {
      if (rightLinks.isEmpty && leftLinks.isEmpty)
        (Seq(), Seq())
      else {
        val (right, rOthers) = getContinuousTrack(rightLinks)
        val (left, lOthers) = getContinuousTrack(leftLinks)
        if (right.nonEmpty && left.nonEmpty) {
          val st = getFixedAddress(right.head, left.head).map(_._1)
          val en = getFixedAddress(right.last, left.last).map(_._2)
          val (r, l) = adjustTwoTracks(right, left, st, en)
          val (ro, lo) = adjustTracksToMatch(rOthers, lOthers, Some(r.last.endAddrMValue))
          (r ++ ro, l ++ lo)
        } else {
          throw new RoadAddressException(s"Mismatching tracks, R ${right.size}, L ${left.size}")
        }
      }
    }

    val rightLinks = ProjectSectionMValueCalculator.calculateMValuesForTrack(sections.flatMap(_.right.links))
    val leftLinks = ProjectSectionMValueCalculator.calculateMValuesForTrack(sections.flatMap(_.left.links))
    val (right, left) = adjustTracksToMatch(rightLinks.sortBy(_.startAddrMValue), leftLinks.sortBy(_.startAddrMValue), None)
    TrackSectionOrder.createCombinedSections(right, left)
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

  private def averageTracks(rightTrack: TrackSection, leftTrack: TrackSection): (TrackSection, TrackSection) = {
    def needsReversal(left: TrackSection, right: TrackSection): Boolean = {
      GeometryUtils.areAdjacent(left.startGeometry, right.endGeometry) ||
        GeometryUtils.areAdjacent(left.endGeometry, right.startGeometry)
    }
    val alignedLeft = if (needsReversal(leftTrack, rightTrack)) leftTrack.reverse else leftTrack
    val (startM, endM) = (average(rightTrack.startAddrM, alignedLeft.startAddrM), average(rightTrack.endAddrM, alignedLeft.endAddrM))

    (rightTrack.toAddressValues(startM, endM), leftTrack.toAddressValues(startM, endM))
  }
  private def average(rightAddrValue: Long, leftAddrValue: Long): Long = {
    if (rightAddrValue >= leftAddrValue)
      (1L + rightAddrValue + leftAddrValue) / 2L
    else
      (rightAddrValue + leftAddrValue - 1L) / 2L
  }


}
case class RoadAddressSection(roadNumber: Long, roadPartNumberStart: Long, roadPartNumberEnd: Long, track: Track,
                              startMAddr: Long, endMAddr: Long, discontinuity: Discontinuity, roadType: RoadType) {
  def includes(ra: BaseRoadAddress): Boolean = {
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
  def reverse = TrackSection(roadNumber, roadPartNumber, track, geometryLength,
    links.map(l => l.copy(sideCode = SideCode.switch(l.sideCode))).reverse)

  lazy val startGeometry: Point = links.head.sideCode match {
    case AgainstDigitizing => links.head.geometry.last
    case _ => links.head.geometry.head
  }
  lazy val endGeometry: Point = links.last.sideCode match {
    case AgainstDigitizing => links.last.geometry.head
    case _ => links.last.geometry.last
  }
  lazy val startAddrM: Long = links.map(_.startAddrMValue).min
  lazy val endAddrM: Long = links.map(_.endAddrMValue).max
  def toAddressValues(start: Long, end: Long): TrackSection = {
    val runningLength = links.scanLeft(0.0){ case (d, pl) => d + pl.geometryLength }
    val coeff = (end - start) / runningLength.last
    val updatedLinks = links.zip(runningLength.zip(runningLength.tail)).map { case (pl, (st, en)) =>
      pl.copy(startAddrMValue = Math.round(start + st*coeff), endAddrMValue = Math.round(start + en*coeff))
    }
    this.copy(links = updatedLinks)
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

  lazy val linkStatusCodes: Set[LinkStatus] = (right.links.map(_.status) ++ left.links.map(_.status)).toSet
}


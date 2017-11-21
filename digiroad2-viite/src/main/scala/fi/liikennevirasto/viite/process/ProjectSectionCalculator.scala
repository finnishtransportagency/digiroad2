package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.util.{RoadAddressException, Track}
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point, Vector3d}
import fi.liikennevirasto.viite.RoadType
import fi.liikennevirasto.viite.dao.CalibrationPointDAO.UserDefinedCalibrationPoint
import fi.liikennevirasto.viite.dao._
import org.slf4j.LoggerFactory

object ProjectSectionCalculator {

  private val logger = LoggerFactory.getLogger(getClass)

  /**
    * NOTE! Should be called from project service only at recalculate method - other places are usually wrong places
    * and may miss user given calibration points etc.
    * Recalculates the AddressMValues for project links. LinkStatus.New will get reassigned values and all
    * others will have the transfer/unchanged rules applied for them.
    * Terminated links will not be recalculated
    *
    * @param projectLinks List of addressed links in project
    * @return Sequence of project links with address values and calibration points.
    */
  def assignMValues(projectLinks: Seq[ProjectLink], userGivenCalibrationPoints: Seq[UserDefinedCalibrationPoint] = Seq()): Seq[ProjectLink] = {
    logger.info(s"Starting MValue assignment for ${projectLinks.size} links")
    val (terminated, others) = projectLinks.partition(_.status == LinkStatus.Terminated)
    val (newLinks, nonTerminatedLinks) = others.partition(l => l.status == LinkStatus.New)
    try {
      if (TrackSectionOrder.isRoundabout(others)) {
        logger.info(s"Roundabout addressing scheme")
        assignMValuesForRoundabout(newLinks, nonTerminatedLinks, userGivenCalibrationPoints) ++ terminated
      } else {
        logger.info(s"Normal addressing scheme")
        assignMValues(newLinks, nonTerminatedLinks, userGivenCalibrationPoints) ++ terminated
      }
    } finally {
      logger.info(s"Finished MValue assignment for ${projectLinks.size} links")
    }
  }

  private def assignMValuesForRoundabout(newProjectLinks: Seq[ProjectLink], oldProjectLinks: Seq[ProjectLink],
    userCalibrationPoints: Seq[UserDefinedCalibrationPoint]): Seq[ProjectLink] = {
    def toCalibrationPoints(linkId: Long, st: Option[UserDefinedCalibrationPoint], en: Option[UserDefinedCalibrationPoint]) = {
      (st.map(cp => CalibrationPoint(linkId, cp.segmentMValue, cp.addressMValue)),
      en.map(cp => CalibrationPoint(linkId, cp.segmentMValue, cp.addressMValue)))
    }
    val startingLink = oldProjectLinks.sortBy(_.startAddrMValue).headOption.orElse(
      newProjectLinks.find(pl => pl.endAddrMValue != 0 && pl.startAddrMValue == 0)).orElse(
      newProjectLinks.headOption).toSeq
    val rest = (newProjectLinks ++ oldProjectLinks).filterNot(startingLink.contains)
    val mValued = TrackSectionOrder.mValueRoundabout(startingLink ++ rest)
    if (userCalibrationPoints.nonEmpty) {
      val withCalibration = mValued.map(pl =>
        userCalibrationPoints.filter(_.projectLinkId == pl.id) match {
          case s if s.size == 2 =>
            val (st, en) = (s.minBy(_.addressMValue), s.maxBy(_.addressMValue))
            pl.copy(startAddrMValue = st.addressMValue, endAddrMValue = en.addressMValue, calibrationPoints = toCalibrationPoints(pl.linkId, Some(st), Some(en)))
          case s if s.size == 1 && s.head.segmentMValue == 0.0 =>
            pl.copy(startAddrMValue = s.head.addressMValue, calibrationPoints = toCalibrationPoints(pl.linkId, Some(s.head), None))
          case s if s.size == 1 && s.head.segmentMValue != 0.0 =>
            pl.copy(endAddrMValue = s.head.addressMValue, calibrationPoints = toCalibrationPoints(pl.linkId, None, Some(s.head)))
          case _ =>
            pl.copy(calibrationPoints = (None, None))
        }
      )
      val factors = ProjectSectionMValueCalculator.calculateAddressingFactors(withCalibration)
      val coEff = (withCalibration.map(_.endAddrMValue).max - factors.unChangedLength - factors.transferLength) / factors.newLength
      ProjectSectionMValueCalculator.assignLinkValues(withCalibration, None, coEff)
    } else {
      mValued
    }
  }
  def findStartingPoints(newLinks: Seq[ProjectLink], oldLinks: Seq[ProjectLink],
                                calibrationPoints: Seq[UserDefinedCalibrationPoint]): (Point, Point) = {
    val rightStartPoint = findStartingPoint(newLinks.filter(_.track != Track.LeftSide), oldLinks.filter(_.track != Track.LeftSide),
      calibrationPoints)
    if ((oldLinks ++ newLinks).exists(l => GeometryUtils.areAdjacent(l.geometry, rightStartPoint) && l.track == Track.Combined))
      (rightStartPoint, rightStartPoint)
    else {
      // Get left track non-connected points and find the closest to right track starting point
      val leftLinks = newLinks.filter(_.track != Track.RightSide) ++ oldLinks.filter(_.track != Track.RightSide)
      val leftPoints = TrackSectionOrder.findOnceConnectedLinks(leftLinks).keys
      if (leftPoints.isEmpty)
        throw new InvalidAddressDataException("Missing left track starting points")
      val leftStartPoint = leftPoints.minBy(lp => (lp - rightStartPoint).length())
      (rightStartPoint, leftStartPoint)
    }
  }
  /**
    * Find a starting point for this road part
    *
    * @param newLinks Status = New links that need to have an address
    * @param oldLinks Other links that already existed before the project
    * @param calibrationPoints The calibration points set by user as fixed addresses
    * @return Starting point
    */
  private def findStartingPoint(newLinks: Seq[ProjectLink], oldLinks: Seq[ProjectLink],
                                calibrationPoints: Seq[UserDefinedCalibrationPoint]): Point = {
    def calibrationPointToPoint(calibrationPoint: UserDefinedCalibrationPoint): Option[Point] = {
      val link = oldLinks.find(_.id == calibrationPoint.projectLinkId).orElse(newLinks.find(_.id == calibrationPoint.projectLinkId))
      link.flatMap(pl => GeometryUtils.calculatePointFromLinearReference(pl.geometry, calibrationPoint.segmentMValue))
    }
    // Pick the one with calibration point set to zero: or any old link with lowest address: or new links by direction
    calibrationPoints.find(_.addressMValue == 0).flatMap(calibrationPointToPoint).getOrElse(
      oldLinks.filter(_.status == LinkStatus.UnChanged).sortBy(_.startAddrMValue).headOption.map(_.startingPoint).getOrElse {
        val remainLinks = oldLinks ++ newLinks
        if (remainLinks.isEmpty)
          throw new InvalidAddressDataException("Missing right track starting project links")
        val points = remainLinks.map(pl => (pl.startingPoint, pl.endPoint))
        val direction = points.map(p => p._2 - p._1).fold(Vector3d(0, 0, 0)) { case (v1, v2) => v1 + v2 }.normalize2D()
        // Approximate estimate of the mid point: averaged over count, not link length
        val midPoint = points.map(p => p._1 + (p._2 - p._1).scale(0.5)).foldLeft(Vector3d(0,0,0)){case (x, p) =>
          (p - Point(0,0)).scale(1.0/points.size) + x}
        TrackSectionOrder.findOnceConnectedLinks(remainLinks).keys.minBy(p => direction.dot(p.toVector - midPoint))
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
                            userCalibrationPoints: Seq[UserDefinedCalibrationPoint]): Seq[ProjectLink] = {
    // TODO: use user given start calibration points (US-564, US-666, US-639)
    // Sort: smallest endAddrMValue is first but zero does not count.
    def makeStartCP(projectLink: ProjectLink) = {
      Some(CalibrationPoint(projectLink.linkId, if (projectLink.sideCode == TowardsDigitizing) 0.0 else projectLink.geometryLength, projectLink.startAddrMValue))
    }
    def makeEndCP(projectLink: ProjectLink, userDefinedCalibrationPoint: Option[UserDefinedCalibrationPoint]) = {
      Some(CalibrationPoint(projectLink.linkId, if (projectLink.sideCode == AgainstDigitizing) 0.0 else projectLink.geometryLength,
        userDefinedCalibrationPoint.map(_.addressMValue).getOrElse(projectLink.endAddrMValue)))
    }
    def makeLink(link: ProjectLink, userDefinedCalibrationPoint: Option[UserDefinedCalibrationPoint],
                 startCP: Boolean, endCP: Boolean) = {
      val sCP = if (startCP) makeStartCP(link) else None
      val eCP = if (endCP) makeEndCP(link, userDefinedCalibrationPoint) else None
      link.copy(calibrationPoints = (sCP, eCP), endAddrMValue = eCP.map(_.addressMValue).getOrElse(link.endAddrMValue))
    }
    def assignCalibrationPoints(ready: Seq[ProjectLink], unprocessed: Seq[ProjectLink],
                                calibrationPoints: Map[Long, UserDefinedCalibrationPoint]): Seq[ProjectLink] = {
      // If first one
      if (ready.isEmpty) {
        val link = unprocessed.head
        // If there is only one link in section we put two calibration points in it
        if (unprocessed.size == 1)
          Seq(makeLink(link, calibrationPoints.get(link.id), true, true))
        else
          assignCalibrationPoints(Seq(makeLink(link, calibrationPoints.get(link.id), true, false)), unprocessed.tail, calibrationPoints)
        // If last one
      } else if (unprocessed.tail.isEmpty) {
        ready ++ Seq(makeLink(unprocessed.head, calibrationPoints.get(unprocessed.head.id), false, true))
      } else {
        // a middle one, add to sequence and continue
        assignCalibrationPoints(ready ++
          Seq(makeLink(unprocessed.head, calibrationPoints.get(unprocessed.head.id), false, false)), unprocessed.tail, calibrationPoints)
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
    group.flatMap { case (part, (projectLinks, oldLinks)) =>
      try {
        val (right, left) = TrackSectionOrder.orderProjectLinksTopologyByGeometry(
          findStartingPoints(projectLinks, oldLinks, userCalibrationPoints), projectLinks++oldLinks)
        val ordSections = TrackSectionOrder.createCombinedSections(right, left)

        // TODO: userCalibrationPoints to Long -> Seq[UserDefinedCalibrationPoint] in method params
        val calMap = userCalibrationPoints.map(c => c.projectLinkId -> c).toMap
        val links = calculateSectionAddressValues(ordSections, calMap).flatMap { sec =>
          if (sec.right == sec.left)
            assignCalibrationPoints(Seq(), sec.right.links, calMap)
          else {
            assignCalibrationPoints(Seq(), sec.right.links, calMap) ++
              assignCalibrationPoints(Seq(), sec.left.links, calMap)
          }
        }
        eliminateExpiredCalibrationPoints(links)
      } catch {
        case ex: InvalidAddressDataException =>
          logger.info(s"Can't calculate road/road part ${part._1}/${part._2}: " + ex.getMessage)
          projectLinks ++ oldLinks
        case ex: NoSuchElementException =>
          logger.info("Delta calculation failed: " + ex.getMessage, ex)
          projectLinks ++ oldLinks
        case ex: NullPointerException =>
          logger.info("Delta calculation failed (NPE)", ex)
          projectLinks ++ oldLinks
        case ex: Throwable =>
          logger.info("Delta calculation not possible: " + ex.getMessage)
          projectLinks ++ oldLinks
      }
    }.toSeq
  }

  private def calculateSectionAddressValues(sections: Seq[CombinedSection],
                                            userDefinedCalibrationPoint: Map[Long,UserDefinedCalibrationPoint]): Seq[CombinedSection] = {
    def getContinuousTrack(seq: Seq[ProjectLink]): (Seq[ProjectLink], Seq[ProjectLink]) = {
      val track = seq.headOption.map(_.track).getOrElse(Track.Unknown)
      seq.span(_.track == track)
    }
    def getFixedAddress(rightLink: ProjectLink, leftLink: ProjectLink,
                        maybeDefinedCalibrationPoint: Option[UserDefinedCalibrationPoint] = None): Option[(Long, Long)] = {
      if (rightLink.status == LinkStatus.UnChanged) {
        Some((rightLink.startAddrMValue, rightLink.endAddrMValue))
      } else {
        if (leftLink.status == LinkStatus.UnChanged)
          Some((leftLink.startAddrMValue, leftLink.endAddrMValue))
        else {
          maybeDefinedCalibrationPoint.map(c => (c.addressMValue, c.addressMValue)).orElse(None)
        }
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
          val en = getFixedAddress(right.last, left.last,
            userDefinedCalibrationPoint.get(right.last.id).orElse(userDefinedCalibrationPoint.get(left.last.id))).map(_._2)
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


  def switchSideCode(sideCode: SideCode): SideCode = {
    // Switch between against and towards 2 -> 3, 3 -> 2
    SideCode.apply(5-sideCode.value)
  }

}
case class RoadAddressSection(roadNumber: Long, roadPartNumberStart: Long, roadPartNumberEnd: Long, track: Track,
                              startMAddr: Long, endMAddr: Long, discontinuity: Discontinuity, roadType: RoadType, ely: Long, reversed: Boolean) {
  def includes(ra: BaseRoadAddress): Boolean = {
    // within the road number and parts included
    ra.roadNumber == roadNumber && ra.roadPartNumber >= roadPartNumberStart && ra.roadPartNumber <= roadPartNumberEnd &&
      // and on the same track
      ra.track == track &&
      // and by reversed direction
      ra.reversed == reversed &&
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


package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.linearasset.PolyLine
import fi.liikennevirasto.digiroad2.util.{RoadAddressException, Track}
import fi.liikennevirasto.digiroad2.{GeometryUtils, Matrix, Point, Vector3d}
import fi.liikennevirasto.viite.MaxDistanceForConnectedLinks
import fi.liikennevirasto.viite.dao.LinkStatus._
import fi.liikennevirasto.viite.dao.{BaseRoadAddress, CalibrationPoint, ProjectLink}


object TrackSectionOrder {
  private val RightVector = Vector3d(-1.0, 0.0, 0.0)
  private val ForwardVector = Vector3d(0.0, 1.0, 0.0)

  def RotationMatrix(tangent: Vector3d): Matrix = {
    if (Math.abs(tangent.x) <= fi.liikennevirasto.viite.Epsilon)
      Matrix(Seq(Seq(0.0, -1.0), Seq(1.0, 0.0)))
    else {
      val k = tangent.y / tangent.x
      val coeff = 1/Math.sqrt(k*k + 1)
      Matrix(Seq(Seq(coeff, -k*coeff), Seq(k*coeff, coeff)))
    }
  }

  def findOnceConnectedLinks[T <: BaseRoadAddress](seq: Iterable[T]): Map[Point, T] = {
    val pointMap = seq.flatMap(l => {
      val (p1, p2) = GeometryUtils.geometryEndpoints(l.geometry)
      Seq(p1 -> l, p2 -> l)
    }).groupBy(_._1).mapValues(_.map(_._2).toSeq.distinct)
    pointMap.keys.map{ p =>
      val links = pointMap.filterKeys(m => GeometryUtils.areAdjacent(p, m, MaxDistanceForConnectedLinks)).values.flatten
      p -> links
    }.toMap.filter(_._2.size == 1).mapValues(_.head)
  }

  def isRoundabout[T <: BaseRoadAddress](seq: Iterable[T]): Boolean = {
    seq.nonEmpty && seq.map(_.track).toSet.size == 1 && findOnceConnectedLinks(seq).isEmpty && seq.forall(pl =>
      seq.count(pl2 =>
        GeometryUtils.areAdjacent(pl.geometry, pl2.geometry, MaxDistanceForConnectedLinks)) == 3) // the link itself and two connected
  }

  /**
    * A sequence of points is turning counterclockwise if every segment between them is turning left looking from the
    * center of the points
    * @param seq
    * @return
    */
  def isCounterClockwise(seq: Seq[Point]): Boolean = {
    val midPoint = seq.tail.fold(seq.head){ case (p1, p2) => p1.copy(x = p1.x + p2.x, y=p1.y + p2.y, z=0)}.toVector.scale(1.0/seq.size)
    // Create vectors from midpoint to p and from p to next
    val extended = seq ++ Seq(seq.head) // Take the last point to be used as the ending point as well
    val vectors = extended.map(p => p.toVector - midPoint).zip(seq.zip(extended.tail).map{ case (p1, p2) =>
      p2 - p1})
    vectors.forall{ case (x, y) =>
      // Using cross product: Right hand rule -> z is positive if y is turning left in relative direction of x
      x.cross(y).z > 0.0 }
  }

  def mValueRoundabout(seq: Seq[ProjectLink]): Seq[ProjectLink] = {
    /* None = No change. Therefore for calibration points
       None            = No change
       Some(None)      = Clear if there was any
       Some(Some(...)) = Set to this value
      */
    def adjust(pl: ProjectLink, sideCode: Option[SideCode] = None, startAddrMValue: Option[Long] = None,
               endAddrMValue: Option[Long] = None, startCalibrationPoint: Option[Option[CalibrationPoint]] = None,
               endCalibrationPoint: Option[Option[CalibrationPoint]] = None) = {
      pl.copy(sideCode = sideCode.getOrElse(pl.sideCode),
        startAddrMValue = startAddrMValue.getOrElse(pl.startAddrMValue),
        endAddrMValue = endAddrMValue.getOrElse(pl.endAddrMValue),
        calibrationPoints = (startCalibrationPoint.getOrElse(pl.calibrationPoints._1), endCalibrationPoint.getOrElse(pl.calibrationPoints._2))
      )
    }
    def firstPoint(pl: ProjectLink) = {
      pl.sideCode match {
        case TowardsDigitizing => pl.geometry.head
        case AgainstDigitizing => pl.geometry.last
        case _ => throw new InvalidGeometryException("SideCode was not decided")
      }
    }
    def recursive(currentPoint: Point, ready: Seq[ProjectLink], unprocessed: Seq[ProjectLink]): Seq[ProjectLink] = {
      if (unprocessed.isEmpty) {
        // Put calibration point at the end
        val last = ready.last
        ready.init ++ Seq(adjust(last, startCalibrationPoint = Some(None), endCalibrationPoint = Some(Some(
          CalibrationPoint(last.linkId, if (last.sideCode == AgainstDigitizing) 0.0 else last.geometryLength, last.endAddrMValue)))))
      }
      else {
        val hit = unprocessed.find(pl => GeometryUtils.areAdjacent(pl.geometry, currentPoint, MaxDistanceForConnectedLinks))
          .getOrElse(throw new InvalidGeometryException("Roundabout was not connected"))
        val nextPoint = getOppositeEnd(hit.geometry, currentPoint)
        val sideCode = if (hit.geometry.last == nextPoint) SideCode.TowardsDigitizing else SideCode.AgainstDigitizing
        val prevAddrM = ready.last.endAddrMValue
        val endAddrM = hit.status match {
          case NotHandled | UnChanged | Transfer | Numbering =>
            ready.last.endAddrMValue + (hit.endAddrMValue - hit.startAddrMValue)
          case New =>
            prevAddrM + Math.round(hit.geometryLength)
          case _ =>
            hit.endAddrMValue
        }
        recursive(nextPoint, ready ++ Seq(adjust(hit, sideCode = Some(sideCode), startAddrMValue = Some(prevAddrM),
          endAddrMValue = Some(endAddrM), startCalibrationPoint = Some(None), endCalibrationPoint = Some(None))),
          unprocessed.filter(_ != hit))
      }
    }
    val firstLink = seq.head // First link is defined by end user and must be always first
    // Put calibration point at the beginning
    val ordered = recursive(firstLink.geometry.last, Seq(adjust(firstLink, sideCode = Some(TowardsDigitizing),
      startAddrMValue = Some(0L), endAddrMValue =
        Some(if (firstLink.status == New)
          Math.round(firstLink.geometryLength)
        else
          firstLink.endAddrMValue - firstLink.startAddrMValue),
      startCalibrationPoint = Some(Some(CalibrationPoint(firstLink.linkId, 0.0, 0L))),
      endCalibrationPoint = Some(None))), seq.tail)
    if (isCounterClockwise(ordered.map(firstPoint)))
      ordered
    else {
      val reOrdered = recursive(firstLink.geometry.head,

        Seq(adjust(firstLink, sideCode = Some(AgainstDigitizing),
          startAddrMValue = Some(0L), endAddrMValue =
            Some(if (firstLink.status == New)
              Math.round(firstLink.geometryLength)
            else
              firstLink.endAddrMValue - firstLink.startAddrMValue),
          startCalibrationPoint = Some(Some(CalibrationPoint(firstLink.linkId, firstLink.geometryLength, 0L))),
          endCalibrationPoint = Some(None))),
        seq.tail)
      if (isCounterClockwise(reOrdered.map(firstPoint)))
        reOrdered
      else
        throw new InvalidGeometryException("Roundabout was not round")
    }
  }

  private def getOppositeEnd(geometry: Seq[Point], point: Point): Point = {
    val (st, en) = GeometryUtils.geometryEndpoints(geometry)
    if ((st - point).length() < (en - point).length()) en else st
  }

  def orderProjectLinksTopologyByGeometry(startingPoints: (Point, Point), list: Seq[ProjectLink]): (Seq[ProjectLink], Seq[ProjectLink]) = {

    def pickMostAligned(rotationMatrix: Matrix, vector: Vector3d, candidates: Seq[ProjectLink]): ProjectLink = {
      candidates.minBy(pl => (rotationMatrix * GeometryUtils.firstSegmentDirection(pl.geometry).normalize2D()) ⋅ vector)
    }

    def pickRightMost(lastLink: ProjectLink, candidates: Seq[ProjectLink]): ProjectLink = {
      pickMostAligned(RotationMatrix(GeometryUtils.lastSegmentDirection(lastLink.geometry)), RightVector, candidates)
    }

    def pickForwardPointing(lastLink: ProjectLink, candidates: Seq[ProjectLink]): ProjectLink = {
      pickMostAligned(RotationMatrix(GeometryUtils.lastSegmentDirection(lastLink.geometry)), ForwardVector, candidates)
    }

    def recursiveFindAndExtend(currentPoint: Point, ready: Seq[ProjectLink], unprocessed: Seq[ProjectLink]): Seq[ProjectLink] = {
      if (unprocessed.isEmpty)
        ready
      else {
        val connected = unprocessed.filter(pl => GeometryUtils.minimumDistance(currentPoint,
          GeometryUtils.geometryEndpoints(pl.geometry)) < MaxDistanceForConnectedLinks)
        val (nextPoint, nextLink) = connected.size match {
          case 0 =>
            val subsetB = findOnceConnectedLinks(unprocessed)
            val (closestPoint, link) = subsetB.minBy(b => (currentPoint - b._1).length())
            (getOppositeEnd(link.geometry, closestPoint), link)
          case 1 =>
            (getOppositeEnd(connected.head.geometry, currentPoint), connected.head)
          case 2 =>
            if (findOnceConnectedLinks(unprocessed).exists(b =>
              (currentPoint - b._1).length() <= fi.liikennevirasto.viite.MaxJumpForSection)) {
              findOnceConnectedLinks(unprocessed).filter(b =>
                (currentPoint - b._1).length() <= fi.liikennevirasto.viite.MaxJumpForSection)
                .minBy(b => (currentPoint - b._1).length())
            } else {
              val l = pickRightMost(ready.last, connected)
              (getOppositeEnd(l.geometry, currentPoint), l)
            }
          case _ =>
            val l = pickForwardPointing(ready.last, connected)
            (getOppositeEnd(l.geometry, currentPoint), l)
        }
        // Check if link direction needs to be turned and choose next point
        val sideCode = if (nextLink.geometry.last == nextPoint) SideCode.TowardsDigitizing else SideCode.AgainstDigitizing
        recursiveFindAndExtend(nextPoint, ready ++ Seq(nextLink.copy(sideCode = sideCode)), unprocessed.filterNot(pl => pl == nextLink))
      }
    }

    val track01 = list.filter(_.track != Track.LeftSide)
    val track02 = list.filter(_.track != Track.RightSide)

    (recursiveFindAndExtend(startingPoints._1, Seq(), track01), recursiveFindAndExtend(startingPoints._2, Seq(), track02))
  }

  def createCombinedSections(rightLinks: Seq[ProjectLink], leftLinks: Seq[ProjectLink]): Seq[CombinedSection] = {
    def fromProjectLinks(s: Seq[ProjectLink]): TrackSection = {
      val pl = s.head
      TrackSection(pl.roadNumber, pl.roadPartNumber, pl.track, s.map(_.geometryLength).sum, s)
    }
    def groupIntoSections(seq: Seq[ProjectLink]): (Seq[TrackSection]) = {
      if (seq.isEmpty)
        throw new InvalidAddressDataException("Missing track")
      val changePoints = seq.zip(seq.tail).filter{ case (pl1, pl2) => pl1.track != pl2.track}
      seq.foldLeft(Seq(Seq[ProjectLink]())) { case (tracks, pl) =>
        if (changePoints.exists(_._2 == pl)) {
          Seq(Seq(pl)) ++ tracks
        } else {
          Seq(tracks.head ++ Seq(pl)) ++ tracks.tail
        }
      }.reverse.map(fromProjectLinks)
    }

    def combineSections(rightSection: Seq[TrackSection], leftSection: Seq[TrackSection]): Seq[CombinedSection] = {
      rightSection.map { r =>
        r.track match {
          case Track.Combined =>
            // Average address values for track lengths:
            val l = leftSection.filter(_.track == Track.Combined).minBy(l =>
              Math.min(
                Math.min(l.startGeometry.distance2DTo(r.startGeometry), l.startGeometry.distance2DTo(r.endGeometry)),
                Math.min(l.endGeometry.distance2DTo(r.startGeometry), l.endGeometry.distance2DTo(r.endGeometry))))
            CombinedSection(r.startGeometry, r.endGeometry, r.geometryLength, l, r)
          case Track.RightSide =>
            val l = leftSection.filter(_.track == Track.LeftSide).minBy(l =>
              Math.min(
                Math.min(l.startGeometry.distance2DTo(r.startGeometry), l.startGeometry.distance2DTo(r.endGeometry)),
              Math.min(l.endGeometry.distance2DTo(r.startGeometry), l.endGeometry.distance2DTo(r.endGeometry))))
            CombinedSection(r.startGeometry, r.endGeometry, .5*(r.geometryLength + l.geometryLength),
              l, r)
          case _ => throw new RoadAddressException(s"Incorrect track code ${r.track}")
        }
      }

    }
    combineSections(groupIntoSections(rightLinks), groupIntoSections(leftLinks))
  }
}

class InvalidGeometryException(s: String) extends RuntimeException {
}

package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.util.{RoadAddressException, Track}
import fi.liikennevirasto.digiroad2.{GeometryUtils, Matrix, Point, Vector3d}
import fi.liikennevirasto.viite.dao.ProjectLink


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

  def findOnceConnectedLinks(seq: Seq[ProjectLink]): Map[Point, ProjectLink] = {
    val pointMap = seq.flatMap(l => {
      val (p1, p2) = GeometryUtils.geometryEndpoints(l.geometry)
      Seq(p1 -> l, p2 -> l)
    }).groupBy(_._1).mapValues(_.map(_._2).distinct)
    pointMap.filter(_._2.size == 1).mapValues(_.head)
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

    def getOppositeEnd(geometry: Seq[Point], point: Point): Point = {
      val (st, en) = GeometryUtils.geometryEndpoints(geometry)
      if ((st - point).length() > (en - point).length()) en else st
    }

    def recursiveFindAndExtend(currentPoint: Point, ready: Seq[ProjectLink], unprocessed: Seq[ProjectLink]): Seq[ProjectLink] = {
      if (unprocessed.isEmpty)
        ready
      else {
        val connected = unprocessed.filter(pl => GeometryUtils.minimumDistance(currentPoint,
          GeometryUtils.geometryEndpoints(pl.geometry)) < fi.liikennevirasto.viite.MaxDistanceForConnectedLinks)
        val (nextPoint, nextLink) = connected.size match {
          case 0 =>
            val subsetB = findOnceConnectedLinks(unprocessed)
            subsetB.minBy(b => (currentPoint - b._1).length())
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
        val sideCode = if (nextLink.geometry.head == nextPoint) SideCode.TowardsDigitizing else SideCode.AgainstDigitizing
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

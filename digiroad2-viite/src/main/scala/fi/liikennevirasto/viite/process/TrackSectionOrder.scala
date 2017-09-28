package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.{GeometryUtils, Matrix, Point, Vector3d}
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.util.{RoadAddressException, Track}
import fi.liikennevirasto.digiroad2.util.Track.RightSide
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

  def orderProjectLinksTopologyByGeometry(startingPoint: Point, list: Seq[ProjectLink]): (Seq[ProjectLink], Seq[ProjectLink]) = {

    def findOnceConnectedLinks(seq: Seq[ProjectLink]): Seq[ProjectLink] = {
      // Using 3 because case b==j is included
      seq.filter(b => seq.count(j =>
        GeometryUtils.areAdjacent(b.geometry, j.geometry, fi.liikennevirasto.viite.MaxDistanceForConnectedLinks)) < 3)
    }
    def distancePointToLink(p: Point, l: ProjectLink) = {
      GeometryUtils.minimumDistance(p, GeometryUtils.geometryEndpoints(l.geometry))
    }
    def pickMostAligned(rotationMatrix: Matrix, vector: Vector3d, candidates: Seq[ProjectLink]): ProjectLink = {
      candidates.minBy(pl => (rotationMatrix* GeometryUtils.firstSegmentDirection(pl.geometry).normalize2D()).dot(vector))
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
          GeometryUtils.geometryEndpoints(pl.geometry)) < fi.liikennevirasto.viite.MaxDistanceForConnectedLinks)
        val nextLink = connected.size match {
          case 0 =>
            val subsetB = findOnceConnectedLinks(unprocessed)
            subsetB.minBy(b => distancePointToLink(currentPoint, b))
          case 1 =>
            connected.head
          case 2 =>
            if (findOnceConnectedLinks(unprocessed).exists(b =>
              distancePointToLink(currentPoint, b) <= fi.liikennevirasto.viite.MaxJumpForSection)) {
              findOnceConnectedLinks(unprocessed).filter(b =>
                distancePointToLink(currentPoint, b) <= fi.liikennevirasto.viite.MaxJumpForSection)
                .minBy(b => distancePointToLink(currentPoint, b))
            } else {
              pickRightMost(ready.last, connected)
            }
          case _ =>
            pickForwardPointing(ready.last, connected)
        }
        // Check if link direction needs to be turned and choose next point
        val nextGeo = nextLink.geometry
        val (sideCode, nextPoint) = if ((nextGeo.head - currentPoint).length() > (nextGeo.last - currentPoint).length())
          (SideCode.AgainstDigitizing, nextGeo.head)
        else
          (SideCode.TowardsDigitizing, nextGeo.last)
        recursiveFindAndExtend(nextPoint, ready ++ Seq(nextLink.copy(sideCode = sideCode)), unprocessed.filterNot(pl => pl == nextLink))
      }
    }

    val track01 = list.filter(_.track != Track.LeftSide)
    val track02 = list.filter(_.track != Track.RightSide)

    (recursiveFindAndExtend(startingPoint, Seq(), track01), recursiveFindAndExtend(startingPoint, Seq(), track02))
  }

  def toSection(pl: ProjectLink) = {
    TrackSection(pl.roadNumber, pl.roadPartNumber, pl.track, pl.endMValue, Seq(pl))
  }

  def createCombinedSections(rightLinks: Seq[ProjectLink], leftLinks: Seq[ProjectLink]): Seq[CombinedSection] = {
    def fromProjectLinks(s: Seq[ProjectLink]): TrackSection = {
      val pl = s.head
      TrackSection(pl.roadNumber, pl.roadPartNumber, pl.track, s.map(_.geometryLength).sum, s)
    }
    def groupIntoSections(seq: Seq[ProjectLink]): (Seq[TrackSection]) = {
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
            println(s"Combining ${r.startAddrM} - ${r.endAddrM} with l& ${l.startAddrM} - ${l.endAddrM}")
            CombinedSection(r.startGeometry, r.endGeometry, .5*(r.geometryLength + l.geometryLength),
              l, r)
          case _ => throw new RoadAddressException(s"Incorrect track code ${r.track}")
        }
      }

    }
    combineSections(groupIntoSections(rightLinks), groupIntoSections(leftLinks))
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

package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.{GeometryUtils, Matrix, Point, Vector3d}
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.digiroad2.util.Track.RightSide
import fi.liikennevirasto.viite.dao.ProjectLink


object TrackSectionOrder {

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
    def pickRightMost(lastLink: ProjectLink, candidates: Seq[ProjectLink]): ProjectLink = {
      val rotationMatrix = RotationMatrix(GeometryUtils.lastSegmentDirection(lastLink.geometry))
      val rightVector = Vector3d(-1.0, 0.0, 0.0)
      candidates.minBy(pl => (rotationMatrix* GeometryUtils.firstSegmentDirection(pl.geometry).normalize2D()).dot(rightVector))
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
          case 2 if findOnceConnectedLinks(unprocessed).exists(b =>
            distancePointToLink(currentPoint, b) <= fi.liikennevirasto.viite.MaxJumpForSection) =>
            findOnceConnectedLinks(unprocessed).filter(b =>
              distancePointToLink(currentPoint, b) <= fi.liikennevirasto.viite.MaxJumpForSection)
              .minBy(b => distancePointToLink(currentPoint, b))
          case _ =>
            pickRightMost(ready.last, connected)
        }
        // Check if link direction needs to be turned and choose next point
        val nextGeo = nextLink.geometry
        val (sideCode, nextPoint) = if ((nextGeo.head - currentPoint).length() > (nextGeo.last - currentPoint).length())
          (SideCode.AgainstDigitizing, nextGeo.head)
        else
          (SideCode.TowardsDigitizing, nextGeo.last)
        recursiveFindAndExtend(nextPoint, ready ++ Seq(nextLink.copy(sideCode = sideCode)), unprocessed.filter(pl => pl == nextLink))
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

    def combineSections(rightSection: Seq[TrackSection], leftSection: Seq[TrackSection]) = {
      rightSection.zip(leftSection).map {
        case (r, _) if r.track == Track.Combined =>
          CombinedSection(r.startGeometry, r.endGeometry, r.geometryLength, r, r)
        case (r, l)  =>
          CombinedSection(r.startGeometry, r.endGeometry, (r.geometryLength + l.geometryLength)*.5, l, r)
      }

    }
    combineSections(groupIntoSections(rightLinks), groupIntoSections(leftLinks))
  }
}

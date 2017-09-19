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

  def orderTrackSectionsByGeometry(rightLinks: Seq[ProjectLink], leftLinks: Seq[ProjectLink]): Seq[CombinedSection] = {
    def groupIntoSection(section: TrackSection, seq: Seq[ProjectLink]): (Seq[TrackSection]) = {
      if (seq.isEmpty)
        Seq(section)
      else {
        if (section.track == seq.head)
          groupIntoSection(section.copy(links = section.links ++ seq.headOption,
            geometryLength = section.geometryLength + (seq.head.endMValue - seq.head.startMValue)), seq.tail)
        else {
          val pl = seq.head
          Seq(section) ++ groupIntoSection(toSection(pl), seq.tail)
        }
      }
    }

    def combineSections(rightSection: Seq[TrackSection], leftSection: Seq[TrackSection]) = {

    }
    // Collect in reverse order because it's neat
    val segmentedRight = rightLinks.tail.foldLeft(Seq(Seq(rightLinks.head))){
      case (seg, pl) =>
        if (seg.head.head.track == pl.track)
          Seq(Seq(pl) ++ seg.head) ++ seg.tail
        else
          Seq(Seq(pl)) ++ seg.tail
    }.map(_.reverse).reverse
    val segmentedLeft = leftLinks.tail.foldLeft(Seq(Seq(leftLinks.head))){
      case (seg, pl) =>
        if (seg.head.head.track == pl.track)
          Seq(Seq(pl) ++ seg.head) ++ seg.tail
        else
          Seq(Seq(pl)) ++ seg.tail
    }.map(_.reverse).reverse
    val sectionRight = groupIntoSection(toSection(rightLinks.head), rightLinks.tail)
    val sectionLeft = groupIntoSection(toSection(leftLinks.head),leftLinks.tail)


  }
}

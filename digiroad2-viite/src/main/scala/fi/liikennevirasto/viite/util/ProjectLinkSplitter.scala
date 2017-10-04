package fi.liikennevirasto.viite.util

import fi.liikennevirasto.digiroad2.linearasset.RoadLinkLike
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.viite._
import fi.liikennevirasto.viite.dao.{Discontinuity, LinkStatus, ProjectLink}
import fi.liikennevirasto.viite.{MaxDistanceForConnectedLinks, MaxSuravageToleranceToGeometry, RoadType}
import fi.liikennevirasto.viite.dao.{Discontinuity, LinkStatus, ProjectLink, RoadAddress}
import fi.liikennevirasto.viite.model.RoadAddressLink

/**
  * Split suravage link together with project link template
  */
object ProjectLinkSplitter {
  def split(suravage: ProjectLink, templateLink: ProjectLink, split: SplitOptions): Seq[ProjectLink] = {
    val suravageM = GeometryUtils.calculateLinearReferenceFromPoint(split.splitPoint, suravage.geometry)
    val templateM = GeometryUtils.calculateLinearReferenceFromPoint(split.splitPoint, templateLink.geometry)
    val (splitA, splitB, splitT) = (split.statusA, split.statusB) match {
      case (LinkStatus.UnChanged, LinkStatus.Terminated) => {
        val splitAddressM = Math.round(templateM / templateLink.geometryLength *
          (templateLink.endAddrMValue - templateLink.startAddrMValue))
        (suravage.copy(roadNumber = split.roadNumber,
          roadPartNumber = split.roadPartNumber,
          track = Track.apply(split.trackCode),
          discontinuity = Discontinuity.apply(split.discontinuity),
//          ely = split.ely,
          roadType = RoadType.apply(split.roadType),
          startMValue = 0.0,
          endMValue = suravageM,
          startAddrMValue = templateLink.startAddrMValue,
          endAddrMValue = splitAddressM,
          status = LinkStatus.UnChanged
        ), suravage.copy(roadNumber = split.roadNumber,
          roadPartNumber = split.roadPartNumber,
          track = Track.apply(split.trackCode),
          discontinuity = Discontinuity.apply(split.discontinuity),
//          ely = split.ely,
          roadType = RoadType.apply(split.roadType),
          startMValue = suravageM,
          endMValue = suravage.geometryLength,
          startAddrMValue = splitAddressM,
          endAddrMValue = templateLink.endAddrMValue,
          status = LinkStatus.New
        ), templateLink.copy(
          startMValue = templateM,
          endMValue = templateLink.geometryLength,
          startAddrMValue = splitAddressM,
          status = LinkStatus.Terminated
        ))
      }
    }
    Seq(splitA,splitB,splitT)
  }

  def findMatchingGeometrySegment(suravage: RoadLinkLike, template: RoadAddress): Option[Seq[Point]] = {
    def findMatchingSegment(suravageGeom: Seq[Point], templateGeom: Seq[Point]): Option[Seq[Point]] = {
      if (GeometryUtils.areAdjacent(suravageGeom.head, templateGeom.head, MaxDistanceForConnectedLinks)) {
        val boundaries = geometryToBoundaries(suravageGeom)
        val templateSegments = GeometryUtils.geometryToSegments(templateGeom)
        val exitPoint = templateSegments.flatMap { seg =>
          findIntersection(seg, boundaries._1, Some(Epsilon), Some(Epsilon))
            .orElse(findIntersection(seg, boundaries._2, Some(Epsilon), Some(Epsilon)))
        }.headOption
        if (exitPoint.nonEmpty) {
          // Exits the tunnel -> find point
          exitPoint.map(ep => GeometryUtils.truncateGeometry2D(templateGeom, 0.0,
            GeometryUtils.calculateLinearReferenceFromPoint(ep, templateGeom)))
        } else {
          Some(GeometryUtils.truncateGeometry2D(templateGeom, 0.0, Math.max(template.geometry.length, suravage.length)))
        }
      } else
        None
    }
    findMatchingSegment(suravage.geometry, template.geometry).orElse(
      findMatchingSegment(suravage.geometry.reverse, template.geometry.reverse).map(_.reverse))
  }

  def findIntersection(geometry1: Seq[Point], geometry2: Seq[Point], maxDistance1: Option[Double] = None,
                       maxDistance2: Option[Double] = None): Option[Point] = {
    val segments1 = geometry1.zip(geometry1.tail)
    val segments2 = geometry2.zip(geometry2.tail)
    val s = segments1.flatMap( s1 => segments2.flatMap{ s2 =>
      intersectionPoint(s1, s2).filter(p =>
        maxDistance1.forall(d => GeometryUtils.minimumDistance(p, s1) < d) &&
          maxDistance2.forall(d => GeometryUtils.minimumDistance(p, s2) < d))})
    s.headOption
  }

  def intersectionPoint(segment1: (Point, Point), segment2: (Point, Point)): Option[Point] = {

    def switchXY(p: Point) = {
      Point(p.y, p.x, p.z)
    }
    val ((p1,p2),(p3,p4)) = (segment1, segment2)
    val v1 = p2-p1
    val v2 = p4-p3
    if (Math.abs(v1.x) < 0.001) {
      if (Math.abs(v2.x) < 0.001) {
        // Both are vertical or near vertical -> swap x and y and recalculate
        return intersectionPoint((switchXY(p1),switchXY(p2)), (switchXY(p3), switchXY(p4))).map(switchXY)
      } else {
        val dx = (p1 - p3).x
        val normV2 = v2.normalize2D()
        return Some(p3 + normV2.scale(dx/normV2.x))
      }
    } else if (Math.abs(v2.x) < 0.001) {
      // second parameter is near vertical, switch places and rerun
      return intersectionPoint((switchXY(p3), switchXY(p4)), (switchXY(p1),switchXY(p2))).map(switchXY)
    }

    val a = v1.y / v1.x
    val b = p1.y - a * p1.x
    val c = v2.y / v2.x
    val d = p3.y - c * p3.x
    if (Math.abs(a-c) < 1E-4 && Math.abs(d-b) > 1E-4) {
      // Differing y is great but coefficients a and c are almost same -> Towards infinities
      None
    } else {
      val x = (d - b) / (a - c)
      val y = a * x + b
      if (x.isNaN || x.isInfinity || y.isNaN || y.isInfinity)
        None
      else
        Some(Point(x, y))
    }
  }

  def geometryToBoundaries(suravageGeometry: Seq[Point]): (Seq[Point], Seq[Point]) = {
    def connectSegments(unConnected: Seq[(Point, Point)]): Seq[Point] = {
      unConnected.scanLeft(unConnected.head){ case (previous, current) =>
        val intersection = intersectionPoint(previous, current)
        (intersection.getOrElse(current._1), current._2)
      }.map(_._1) ++ Seq(unConnected.last._2)
    }
    val (leftUnConnected, rightUnConnected) = suravageGeometry.zip(suravageGeometry.tail).map{ case (p1, p2) =>
      val vecL = (p2-p1).normalize2D().rotateLeft().scale(MaxSuravageToleranceToGeometry)
      ((p1 + vecL, p2 + vecL), (p1 - vecL, p2 - vecL))
    }.unzip
    (connectSegments(leftUnConnected).tail, connectSegments(rightUnConnected).tail)
  }
}

case class SplitOptions(splitPoint: Point, statusA: LinkStatus, statusB: LinkStatus,
                        roadNumber: Long, roadPartNumber: Long, trackCode: Int, discontinuity: Int, ely: Long,
                        roadLinkSource: Int, roadType: Int)


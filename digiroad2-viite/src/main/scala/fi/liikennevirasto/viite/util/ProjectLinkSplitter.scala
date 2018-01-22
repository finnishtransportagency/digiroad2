package fi.liikennevirasto.viite.util

import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode}
import fi.liikennevirasto.digiroad2.linearasset.PolyLine
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.viite.dao.{Discontinuity, LinkStatus, ProjectCoordinates, ProjectLink}
import fi.liikennevirasto.viite.{MaxDistanceForConnectedLinks, MaxSuravageToleranceToGeometry, RoadType, _}

/**
  * Split suravage link together with project link template
  */
object ProjectLinkSplitter {
  private def isDirectionReversed(link1: PolyLine, link2: PolyLine) = {
    GeometryUtils.areAdjacent(link1.geometry.head, link2.geometry.last, MaxDistanceForConnectedLinks) ||
      GeometryUtils.areAdjacent(link1.geometry.last, link2.geometry.head, MaxDistanceForConnectedLinks)
  }

  // Test if connected from template geometry end (towards digitization end)
  private def isTailConnected(suravage: PolyLine, template: PolyLine) = {
    GeometryUtils.areAdjacent(suravage.geometry.last, template.geometry.last, MaxDistanceForConnectedLinks) ||
      isDirectionReversed(suravage, template) &&
        GeometryUtils.areAdjacent(suravage.geometry.head, template.geometry.last, MaxDistanceForConnectedLinks)
  }

  private def suravageWithOptions(suravage: ProjectLink, templateLink: ProjectLink, split: SplitOptions, suravageM: Double,
                                  splitAddressM: Long, templateM: Double, isReversed: Boolean, keptGeom: Seq[Point]) = {
    def equals(geom1: Seq[Point], geom2: Seq[Point]) = {
      GeometryUtils.withinTolerance(GeometryUtils.geometryEndpoints(geom1), GeometryUtils.geometryEndpoints(geom2), MaxDistanceDiffAllowed) ||
        GeometryUtils.withinTolerance(GeometryUtils.geometryEndpoints(geom1).swap, GeometryUtils.geometryEndpoints(geom2), MaxDistanceDiffAllowed)
    }
    val splitGeometries = {
      val endingGeom = GeometryUtils.truncateGeometry2D(suravage.geometry, suravageM, suravage.geometryLength)
      val startingGeom = GeometryUtils.truncateGeometry2D(suravage.geometry, 0.0, suravageM)
      if (equals(endingGeom, keptGeom)){
        (endingGeom, startingGeom)
      } else if (equals(startingGeom, keptGeom)){
        (startingGeom, endingGeom)
      } else {
        throw new SplittingException("Suunnitelmalinkin katkaisukohta ei kohtaa olemassaolevan tieosoitteen kanssa.")
      }
    }
    val (startMA, endMA, startMB, endMB) = (GeometryUtils.calculateLinearReferenceFromPoint(splitGeometries._1.head, suravage.geometry),
      GeometryUtils.calculateLinearReferenceFromPoint(splitGeometries._1.last, suravage.geometry),
      GeometryUtils.calculateLinearReferenceFromPoint(splitGeometries._2.head, suravage.geometry),
      GeometryUtils.calculateLinearReferenceFromPoint(splitGeometries._2.last, suravage.geometry))
    val (splitAddressesA, splitAddressesB) =
      (Seq(templateLink.addrAt(GeometryUtils.calculateLinearReferenceFromPoint(splitGeometries._1.head, templateLink.geometry)),
        templateLink.addrAt(GeometryUtils.calculateLinearReferenceFromPoint(splitGeometries._1.last, templateLink.geometry))),
        Seq(templateLink.addrAt(GeometryUtils.calculateLinearReferenceFromPoint(splitGeometries._2.head, templateLink.geometry)),
          templateLink.addrAt(GeometryUtils.calculateLinearReferenceFromPoint(splitGeometries._2.last, templateLink.geometry))))
    val bigEndAddr = (splitAddressesA ++ splitAddressesB).max
    (
      suravage.copy(roadNumber = split.roadNumber,
        roadPartNumber = split.roadPartNumber,
        track = split.trackCode,
        discontinuity = if (bigEndAddr == splitAddressesA.max) split.discontinuity else Discontinuity.Continuous,
        roadType = split.roadType,
        startMValue = GeometryUtils.calculateLinearReferenceFromPoint(splitGeometries._1.head, suravage.geometry),
        endMValue = GeometryUtils.calculateLinearReferenceFromPoint(splitGeometries._1.last, suravage.geometry),
        startAddrMValue = splitAddressesA.min,
        endAddrMValue = splitAddressesA.max,
        status = split.statusA,
        sideCode = templateLink.sideCode,
        roadAddressId = templateLink.roadAddressId,
        connectedLinkId = Some(templateLink.linkId),
        geometry = splitGeometries._1,
        geometryLength = GeometryUtils.geometryLength(splitGeometries._1),
        ely = templateLink.ely
      ),
      suravage.copy(roadNumber = split.roadNumber,
        roadPartNumber = split.roadPartNumber,
        track = split.trackCode,
        discontinuity = if (bigEndAddr == splitAddressesB.max) split.discontinuity else Discontinuity.Continuous,
        roadType = split.roadType,
        startMValue = GeometryUtils.calculateLinearReferenceFromPoint(splitGeometries._2.head, suravage.geometry),
        endMValue = GeometryUtils.calculateLinearReferenceFromPoint(splitGeometries._2.last, suravage.geometry),
        startAddrMValue = splitAddressesB.min,
        endAddrMValue = splitAddressesB.max,
        status = split.statusB,
        sideCode = templateLink.sideCode,
        roadAddressId = templateLink.roadAddressId,
        connectedLinkId = Some(templateLink.linkId),
        geometry = splitGeometries._2,
        geometryLength = GeometryUtils.geometryLength(splitGeometries._2),
        ely = templateLink.ely
      )
    )
  }

  /**
    * ***Recursive***
    * Connect all the project link templates that are adjacents of the terminated project link,
    * so they can be shown in the user interface after the split and/or when the split is being saved
    * @param splitA The "transfered" part of the splited project link template
    * @param splitB The "new" part of the splited project link template
    * @param terminatedLink The terminated part of the the splited project link template
    * @param templateLink The project link template
    * @param disconnectedProjectLinks All the links not relevant for the going on spliting
    * @return Returns all the project links splited and a merged project link with the all terminated geometry and address values
    */
  private def connectTerminatedProjectLinks(splitA: ProjectLink, splitB: ProjectLink, terminatedLink: ProjectLink, templateLink: ProjectLink, disconnectedProjectLinks: Seq[ProjectLink]) = {
    def concat(geo1: Seq[Point], geo2: Seq[Point]) = {
      //check first here where is adjacent to know if it's needed to prepend or append
      GeometryUtils.areAdjacent(geo1.last, geo2.head) match {
        case true => geo1 ++ geo2
        case false => geo2 ++ geo1
      }
    }
    def copy(pProjectLink: ProjectLink, nProjectLink: ProjectLink, result: SplitResult) = {
      result.copy(
        terminatedProjectLinks = result.terminatedProjectLinks ++ Seq(pProjectLink),
        terminatedProjectLink = result.terminatedProjectLink.copy(
          endAddrMValue = Math.max(result.terminatedProjectLink.endAddrMValue, nProjectLink.endAddrMValue),
          startAddrMValue = Math.min(result.terminatedProjectLink.startAddrMValue, nProjectLink.startAddrMValue),
          geometry = concat(nProjectLink.geometry, result.terminatedProjectLink.geometry)
        )
      )
    }
    def merge(pProjectLink: ProjectLink, nProjectLinks: Seq[ProjectLink], result: SplitResult) : SplitResult = {
      nProjectLinks.headOption match {
        case Some(nProjectLink) =>
          GeometryUtils.areAdjacent(pProjectLink.geometry, nProjectLink.geometry) match {
            case true => merge(nProjectLink.copy(status = LinkStatus.Terminated), nProjectLinks.tail, copy(pProjectLink, nProjectLink, result))
            case false => result.copy(terminatedProjectLinks = result.terminatedProjectLinks ++ Seq(pProjectLink))
          }
        case _ => result.copy(terminatedProjectLinks = result.terminatedProjectLinks ++ Seq(pProjectLink))
      }
    }
    val sortedDisconnectedLinks = disconnectedProjectLinks.
      filter(pl => pl.linkId == terminatedLink.linkId && pl.endMValue >= templateLink.startMValue).
      sortBy(_.startMValue)

    merge(terminatedLink, sortedDisconnectedLinks, SplitResult(splitA, splitB, Seq(), terminatedLink))
  }

  def split(suravage: ProjectLink, templateLink: ProjectLink, splitOptions: SplitOptions): Seq[ProjectLink] =
    split(suravage, templateLink, Seq(), splitOptions).toSeqWithAllTerminated

  def split(suravage: ProjectLink, templateLink: ProjectLink, disconnectedProjectLinks: Seq[ProjectLink], split: SplitOptions): SplitResult = {
    def movedFromStart(suravageM: Double, templateM: Double, splitAddressM: Long, isReversed: Boolean) = {
      val keptGeom = GeometryUtils.truncateGeometry2D(templateLink.geometry, 0.0, templateM)
      val termGeom = GeometryUtils.truncateGeometry2D(templateLink.geometry, templateM, templateLink.geometryLength)
      val (splitA, splitB) = suravageWithOptions(suravage, templateLink, split, suravageM, splitAddressM, templateM, isReversed, keptGeom)
      val splitT = templateLink.copy(
        startMValue = templateM,
        geometryLength = templateLink.endMValue - templateM,
        startAddrMValue = Math.min(splitAddressM, templateLink.addrAt(templateLink.endMValue)),
        endAddrMValue = Math.max(splitAddressM, templateLink.addrAt(templateLink.endMValue)),
        status = LinkStatus.Terminated,
        geometry = termGeom,
        connectedLinkId = Some(suravage.linkId)
      )
      (splitA, splitB, splitT)
    }
    def movedFromEnd(suravageM: Double, templateM: Double, splitAddressM: Long, isReversed: Boolean) = {
      val termGeom = GeometryUtils.truncateGeometry2D(templateLink.geometry, 0.0, templateM)
      val keptGeom = GeometryUtils.truncateGeometry2D(templateLink.geometry, templateM, templateLink.geometryLength)
      val (splitA, splitB) = suravageWithOptions(suravage, templateLink, split, suravageM, splitAddressM, templateM, isReversed, keptGeom)
      val splitT = templateLink.copy(
        endMValue = templateM,
        geometryLength = templateM,
        startAddrMValue = Math.min(splitAddressM, templateLink.addrAt(templateLink.startMValue)),
        endAddrMValue = Math.max(splitAddressM, templateLink.addrAt(templateLink.startMValue)),
        status = LinkStatus.Terminated,
        geometry = termGeom,
        connectedLinkId = Some(suravage.linkId))
      (splitA, splitB, splitT)
    }
    def switchDigitization(splits: (ProjectLink, ProjectLink, ProjectLink)) = {
      val (splitA, splitB, splitT) = splits
      (
        splitB.copy(
          sideCode = SideCode.switch(splitT.sideCode)),
        splitA.copy(
          sideCode = SideCode.switch(splitT.sideCode)
        ),
        splitT)
    }
    //Discontinuity of splits should be the given only for the part with the biggest M Address Value
    //The rest of them should be 5 (Continuous)
    val suravageM = GeometryUtils.calculateLinearReferenceFromPoint(split.splitPoint, suravage.geometry)
    val templateM = GeometryUtils.calculateLinearReferenceFromPoint(split.splitPoint, templateLink.geometry)
    val splitAddressM = templateLink.addrAt(templateM)
    val isReversed = isDirectionReversed(suravage, templateLink)
    val splits =
      if (isTailConnected(suravage, templateLink))
        movedFromEnd(suravageM, templateM, splitAddressM, isReversed)
      else
        movedFromStart(suravageM, templateM, splitAddressM, isReversed)

    val normalizedSplits = if (isReversed) switchDigitization(splits) else splits

    connectTerminatedProjectLinks(normalizedSplits._1, normalizedSplits._2, normalizedSplits._3, templateLink, disconnectedProjectLinks)
  }

  def findMatchingGeometrySegment(suravage: PolyLine, template: PolyLine): Option[Seq[Point]] = {
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
          if (suravageGeom.forall(p => GeometryUtils.minimumDistance(p, templateGeom) <= MaxSuravageToleranceToGeometry) ||
            templateGeom.forall(p => GeometryUtils.minimumDistance(p, suravageGeom) <= MaxSuravageToleranceToGeometry))
            Some(GeometryUtils.truncateGeometry2D(templateGeom, 0.0,
              Math.max(GeometryUtils.geometryLength(template.geometry), GeometryUtils.geometryLength(suravage.geometry))))
          else
            None
        }
      } else if (GeometryUtils.areAdjacent(suravageGeom.last, templateGeom.head, MaxDistanceForConnectedLinks)) {
        findMatchingSegment(suravageGeom.reverse, templateGeom)
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
    val s = segments1.flatMap(s1 => segments2.flatMap { s2 =>
      intersectionPoint(s1, s2).filter(p =>
        maxDistance1.forall(d => GeometryUtils.minimumDistance(p, s1) < d) &&
          maxDistance2.forall(d => GeometryUtils.minimumDistance(p, s2) < d))
    })
    s.headOption
  }

  def intersectionPoint(segment1: (Point, Point), segment2: (Point, Point)): Option[Point] = {

    def switchXY(p: Point) = {
      Point(p.y, p.x, p.z)
    }
    val ((segment1Start, segment1End), (segment2Start, segment2End)) = (segment1, segment2)
    val vector1 = segment1End - segment1Start
    val vector2 = segment2End - segment2Start
    if (Math.abs(vector1.x) < 0.001) {
      if (Math.abs(vector2.x) < 0.001) {
        // Both are vertical or near vertical -> swap x and y and recalculate
        return intersectionPoint((switchXY(segment1Start), switchXY(segment1End)), (switchXY(segment2Start), switchXY(segment2End))).map(switchXY)
      } else {
        val dx = (segment1Start - segment2Start).x
        val normV2 = vector2.normalize2D()
        return Some(segment2Start + normV2.scale(dx / normV2.x))
      }
    } else if (Math.abs(vector2.x) < 0.001) {
      // second parameter is near vertical, switch places and rerun
      return intersectionPoint((switchXY(segment2Start), switchXY(segment2End)), (switchXY(segment1Start), switchXY(segment1End))).map(switchXY)
    }
    // calculate lines as y = ax + b and y = cx + d
    val a = vector1.y / vector1.x
    val b = segment1Start.y - a * segment1Start.x
    val c = vector2.y / vector2.x
    val d = segment2Start.y - c * segment2Start.x
    if (Math.abs(a - c) < 1E-4 && Math.abs(d - b) > 1E-4) {
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
      unConnected.scanLeft(unConnected.head) { case (previous, current) =>
        val intersection = intersectionPoint(previous, current)
        (intersection.getOrElse(current._1), current._2)
      }.map(_._1) ++ Seq(unConnected.last._2)
    }
    val (leftUnConnected, rightUnConnected) = suravageGeometry.zip(suravageGeometry.tail).map { case (p1, p2) =>
      val vecL = (p2 - p1).normalize2D().rotateLeft().scale(MaxSuravageToleranceToGeometry)
      ((p1 + vecL, p2 + vecL), (p1 - vecL, p2 - vecL))
    }.unzip
    (connectSegments(leftUnConnected).tail, connectSegments(rightUnConnected).tail)
  }
}

case class SplitOptions(splitPoint: Point, statusA: LinkStatus, statusB: LinkStatus,
                        roadNumber: Long, roadPartNumber: Long, trackCode: Track, discontinuity: Discontinuity, ely: Long,
                        roadLinkSource: LinkGeomSource, roadType: RoadType, projectId: Long, coordinates: ProjectCoordinates)

case class SplitResult(splitA: ProjectLink, splitB: ProjectLink, terminatedProjectLinks: Seq[ProjectLink], terminatedProjectLink: ProjectLink)
{
  private def isShorterProjectLinks(pl: ProjectLink) = Math.abs(pl.endMValue - pl.startMValue) >= fi.liikennevirasto.viite.MinAllowedRoadAddressLength
  def toSeqWithAllTerminated: Seq[ProjectLink] = (Seq(splitA, splitB) ++ terminatedProjectLinks).filter(isShorterProjectLinks)
  def toSeqWithMergeTerminated: Seq[ProjectLink] = Seq(splitA, splitB, terminatedProjectLink).filter(isShorterProjectLinks)
}


package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.linearasset.GraphPartitioner
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.RoadType
import fi.liikennevirasto.viite.dao.{CalibrationPoint, Discontinuity, ProjectLink, RoadAddress}
import fi.liikennevirasto.viite.process.SectionPartitioner.{clusterLinks, linksFromCluster}
import fi.liikennevirasto.digiroad2.linearasset.GraphPartitioner

object ProjectSectionCalculator {
  /**
    * Order list of geometrically continuous Project Links so that the chain is traversable from end to end
    * in this sequence and side code
    * @param list Project Links (already partitioned; connected)
    * @return Ordered list of project links
    */
  def orderProjectLinksTopologyByGeometry(list:Seq[ProjectLink]): Seq[ProjectLink] = {
    def getNewEndPoint(geometry: Seq[Point], oldEndPoint: Point): Point = {
      val s = Seq(geometry.head, geometry.last)
      s.maxBy(p => (oldEndPoint - p).length())
    }

    def isInvertSideCode(geom1 : Seq[Point], geom2 : Seq[Point]): Boolean = {
      GeometryUtils.areAdjacent(geom1.head, geom2.head) || GeometryUtils.areAdjacent(geom1.last, geom2.last)
    }

    def recursiveFindAndExtend(sortedList: Seq[ProjectLink], unprocessed: Seq[ProjectLink], head: Point, end: Point) : Seq[ProjectLink] = {
      println("***")
      println(s"Head = ${head.x}, ${head.y}; end = ${end.x}, ${end.y}")
      println("-")
      sortedList.foreach(println)
      println("-.-")
      unprocessed.foreach(println)
      println("***")
      val headExtend = unprocessed.find(l => GeometryUtils.areAdjacent(l.geometry, head))
      if (headExtend.nonEmpty) {
        val ext = headExtend.get
        println(s"${ext.geometry} match head ${head}")
        recursiveFindAndExtend(Seq(ext)++sortedList, unprocessed.filterNot(p => p.linkId == ext.linkId), getNewEndPoint(ext.geometry, head), end)
      } else {
        val tailExtend = unprocessed.find(l => GeometryUtils.areAdjacent(l.geometry, end))
        if (tailExtend.nonEmpty) {
          val ext = tailExtend.get
          println(s"${ext.geometry} match last ${end}")
          recursiveFindAndExtend(sortedList++Seq(ext), unprocessed.filterNot(p => p.linkId == ext.linkId), head, getNewEndPoint(ext.geometry, end))
        } else {
          sortedList
        }
      }
    }
    val head = list.head
    val (headPoint, endPoint) = head.sideCode match {
      case AgainstDigitizing => (head.geometry.last, head.geometry.head)
      case _ => (head.geometry.head, head.geometry.last)
    }
    recursiveFindAndExtend(Seq(list.head), list.tail, headPoint, endPoint)
  }

  def determineMValues(projectLinks: Seq[ProjectLink], geometryLengthList: Map[RoadPart, Seq[RoadLinkLength]], linksInProject: Seq [ProjectLink]): Seq[ProjectLink] = {
    def makeStartCP(projectLink: ProjectLink) = {
      Some(CalibrationPoint(projectLink.linkId, if (projectLink.sideCode == TowardsDigitizing) 0.0 else projectLink.geometryLength, projectLink.startAddrMValue))
    }
    def makeEndCP(projectLink: ProjectLink) = {
      Some(CalibrationPoint(projectLink.linkId, if (projectLink.sideCode == AgainstDigitizing) 0.0 else projectLink.geometryLength, projectLink.endAddrMValue))
    }
    def assignCalibrationPoints(ready: Seq[ProjectLink], unprocessed: Seq[ProjectLink], reverse: Boolean = false): Seq[ProjectLink] = {
      if (reverse) {
        println("Reverse!")
        assignCalibrationPoints(ready.reverse, unprocessed.reverse, reverse = false)
      } else {
        // If first one
        if (ready.isEmpty) {
          val link = unprocessed.head
          // If only one
          if (unprocessed.size == 1)
            Seq(link.copy(calibrationPoints = (makeStartCP(link), makeEndCP(link))))
          else
            assignCalibrationPoints(Seq(link.copy(calibrationPoints = (makeStartCP(link), None))), unprocessed.tail, reverse)
          // If last one
        } else if (unprocessed.tail.isEmpty) {
          val link = unprocessed.head
          ready ++ Seq(link.copy(calibrationPoints = (None, makeEndCP(link))))
        } else {
          assignCalibrationPoints(ready ++ Seq(unprocessed.head), unprocessed.tail, reverse)
        }
      }
    }
    def assignLinkValues(section: CombinedSection, startMValue: Long, endMValue: Long, reverse: Boolean = false): Seq[ProjectLink] = {
      if (section.right.links.head.sideCode == AgainstDigitizing && startMValue < endMValue) {
        return assignLinkValues(section, endMValue, startMValue, reverse = true)
      }
      val coeff = (endMValue - startMValue).toDouble / section.right.links.map(_.geometryLength).sum
      val runningLength = section.right.links.scanLeft(startMValue.toDouble){case (value, plink) =>
        value + coeff*plink.geometryLength
      }
      val rightLinks = section.right.links.zip(runningLength.zip(runningLength.tail)).map { case (plink, (start, end)) =>
        plink.copy(startAddrMValue = Math.min(start.toLong, end.toLong), endAddrMValue = Math.max(start.toLong, end.toLong),
          startMValue = 0.0, endMValue = plink.geometryLength)
      }
      if (section.right == section.left)
        assignCalibrationPoints(Seq(), rightLinks, reverse)
      else {
        val coeffL = (endMValue - startMValue).toDouble / section.left.links.map(_.geometryLength).sum
        val runningLengthL = section.left.links.scanLeft(startMValue.toDouble){case (value, plink) =>
          value + coeffL*plink.geometryLength
        }
        assignCalibrationPoints(Seq(), rightLinks, reverse) ++
          assignCalibrationPoints(Seq(),
            section.left.links.zip(runningLengthL.zip(runningLengthL.tail)).map { case (plink, (start, end)) =>
              plink.copy(startAddrMValue = Math.min(start.toLong, end.toLong), endAddrMValue = Math.max(start.toLong, end.toLong),
                startMValue = 0.0, endMValue = plink.geometryLength)
            }, reverse)
      }
    }
    projectLinks.foreach(println)
    geometryLengthList.foreach(println)
    linksInProject.foreach(println)
    val groupedProjectLinks = projectLinks.groupBy(record => (record.roadNumber, record.roadPartNumber))
    groupedProjectLinks.flatMap(gpl => {
      val roadPartId = RoadPart(gpl._1._1, gpl._1._2)
      if(geometryLengthList.keySet.contains(roadPartId)){
        val linkPartitions = SectionPartitioner.partition(gpl._2).map(orderProjectLinksTopologyByGeometry)

        linkPartitions.foreach { lp =>
          println("Partition")
          lp.foreach(pl => println(s"${pl.linkId}, ${pl.geometry.head}-${pl.geometry.last}"))
        }
        val sections = linkPartitions.map(lp =>
          TrackSection(lp.head.roadNumber, lp.head.roadPartNumber, lp.head.track, lp.map(_.geometryLength).sum, lp)
        )
        val ordSections = orderTrackSectionsByGeometry(sections)
        val mValues = ordSections.scanLeft(0.0) { case (mValue, sec) =>
          mValue + sec.geometryLength
        }
        ordSections.zip(mValues.zip(mValues.tail)).flatMap { case (section, (start, end)) => assignLinkValues(section, start.toLong, end.toLong) }

      } else {
        orderProjectLinksTopologyByGeometry(gpl._2)
      }
    }).toSeq
  }

  def orderTrackSectionsByGeometry(sections: Seq[TrackSection]): Seq[CombinedSection] = {
    def reverseSection(section: TrackSection): TrackSection = {
      section.copy(links =
        section.links.map(l => l.copy(geometry = l.geometry.reverse, sideCode = switchSideCode(l.sideCode))))
    }

    def closest(section: TrackSection, candidates: Seq[TrackSection]): TrackSection = {
      candidates.minBy(ts =>
        Math.min(
          (section.startGeometry - ts.startGeometry).length() + (section.endGeometry - ts.endGeometry).length(),
          (section.startGeometry - ts.endGeometry).length() + (section.endGeometry - ts.startGeometry).length()
        ))
    }

    def combineTracks(tracks: Seq[TrackSection]): Seq[CombinedSection] = {
      val (combined, others) = tracks.partition(_.track == Track.Combined)
      if (others.isEmpty)
        return combined.map(ts => CombinedSection(ts.startGeometry, ts.endGeometry, ts.geometryLength, ts, ts))
      val (left, right) = others.partition(_.track == Track.LeftSide)
      if (left.size != right.size)
        throw new InvalidAddressDataException("Non-matching left/right tracks")
      val pick = right.head
      val pair = closest(pick, left)
      val combo = if ((pick.startGeometry - pair.startGeometry).length() > (pick.startGeometry - pair.endGeometry).length())
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
        Math.min((headPoint - ts.addressEndGeometry).length(),
          (endPoint - ts.addressStartGeometry).length())
      }
      val next = sorted.head
      if ((next.addressStartGeometry - endPoint).length() <
        (next.addressEndGeometry - headPoint).length())
        recursiveFindAndExtendCombined(sortedList ++ Seq(next), sorted.tail)
      else
        recursiveFindAndExtendCombined(Seq(next) ++ sortedList, sorted.tail)
    }

    val combined = combineTracks(sections)
    combined.foreach{ cs =>
      println()
      println(s"${cs.right.roadNumber} ${cs.right.roadPartNumber}, ${cs.right.track}: ${cs.startGeometry}-${cs.endGeometry} ${cs.sideCode}")
      if (cs.right.track != Track.Combined)
        println(s"${cs.left.roadNumber} ${cs.left.roadPartNumber}, ${cs.left.track}: ${cs.startGeometry}-${cs.endGeometry}")
      println((cs.right.links ++ cs.left.links).distinct.map(_.linkId).mkString(", "))
    }
    recursiveFindAndExtendCombined(Seq(combined.head), combined.tail)
  }

  def orderProjectLinks(unorderedProjectLinks: Seq[ProjectLink]): Seq[ProjectLink] = {
    def isExtending(link: ProjectLink, ext: ProjectLink) = {
      link.roadNumber == ext.roadNumber && link.roadPartNumber == ext.roadPartNumber &&
        link.track == ext.track
    }
    def continueSeqWith(seq: Seq[ProjectLink], plink: ProjectLink): Seq[ProjectLink] = {
      if (GeometryUtils.areAdjacent(plink.geometry, seq.head.geometry)) {
        Seq(plink) ++ seq
      } else {
        seq ++ Seq(plink)
      }
    }
    def extendChainByGeometry(ordered: Seq[ProjectLink], unordered: Seq[ProjectLink], sideCode: SideCode): Seq[ProjectLink] = {
      // First link gets the assigned side code
      if (ordered.isEmpty)
        return extendChainByGeometry(Seq(unordered.head.copy(sideCode=sideCode)), unordered.tail, sideCode)
      if (unordered.isEmpty) {
        return ordered
      }
      // Find a road address link that continues from current last link
      unordered.find { ral =>
        Seq(ordered.head, ordered.last).exists(
          un => un.linkId != ral.linkId && GeometryUtils.areAdjacent(un.geometry, ral.geometry)
        )
      } match {
        case Some(link) =>
          val seq = continueSeqWith(ordered, link)
          extendChainByGeometry(seq, unordered.filterNot(link.equals), link.sideCode)
        case _ => throw new InvalidAddressDataException("Non-contiguous road target geometry")
      }
    }
    def extendChainByAddress(ordered: Seq[ProjectLink], unordered: Seq[ProjectLink]): Seq[ProjectLink] = {
      if (ordered.isEmpty)
        return extendChainByAddress(Seq(unordered.head), unordered.tail)
      if (unordered.isEmpty)
        return ordered
      val (next, rest) = unordered.partition(u => isExtending(ordered.last, u))
      if (next.nonEmpty)
        extendChainByAddress(ordered ++ next, rest)
      else {
        val (previous, rest) = unordered.partition(u => isExtending(u, ordered.head))
        if (previous.isEmpty)
          throw new IllegalArgumentException("Non-contiguous road addressing")
        else
          extendChainByAddress(previous ++ ordered, rest)
      }
    }

    val orderedByAddress =  extendChainByAddress(Seq(unorderedProjectLinks.head), unorderedProjectLinks.tail)
    val orderedByGeometry = extendChainByGeometry(Seq(), orderedByAddress, orderedByAddress.head.sideCode)
    orderedByGeometry
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
}


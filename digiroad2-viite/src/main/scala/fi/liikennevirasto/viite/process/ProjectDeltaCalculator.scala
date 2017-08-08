package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.linearasset.GraphPartitioner
import fi.liikennevirasto.digiroad2.util.{RoadAddressException, Track}
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.viite.RoadType
import fi.liikennevirasto.viite.dao.{ProjectLink, _}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

/**
  * Calculate the effective change between the project and the current road address data
  */
object ProjectDeltaCalculator {

  val logger = LoggerFactory.getLogger(getClass)
  val checker = new ContinuityChecker(null) // We don't need road link service here
  def delta(projectId: Long): Delta = {
    val projectOpt = ProjectDAO.getRoadAddressProjectById(projectId)
    if (projectOpt.isEmpty)
      throw new IllegalArgumentException("Project not found")
    val project = projectOpt.get
    val projectLinks = ProjectDAO.getProjectLinks(projectId).groupBy(l => RoadPart(l.roadNumber,l.roadPartNumber))
    val currentAddresses = projectLinks.filter(_._2.exists(_.status != LinkStatus.New)).keySet.map(r => r -> RoadAddressDAO.fetchByRoadPart(r.roadNumber, r.roadPartNumber, true)).toMap
    val terminations = findTerminations(projectLinks, currentAddresses)
    val newCreations = findNewCreations(projectLinks)
    if (terminations.size != currentAddresses.values.flatten.size)
      throw new RoadAddressException(s"Road address count did not match: ${terminations.size} terminated, " +
        s"(and ${newCreations.size } created), ${currentAddresses.values.flatten.size} addresses found")
    Delta(project.startDate, terminations.sortBy(t => (t.discontinuity.value, t.roadType.value)), newCreations.sortBy(t => (t.discontinuity.value, t.roadType.value)))
  }

  private def findTerminations(projectLinks: Map[RoadPart, Seq[ProjectLink]], currentAddresses: Map[RoadPart, Seq[RoadAddress]]) = {
    val terminations = projectLinks.map{case (part, pLinks) => part -> (pLinks, currentAddresses.getOrElse(part, Seq()))}.mapValues{ case (pll, ra) =>
      ra.filter(r => pll.exists(pl => pl.linkId == r.linkId && pl.status == LinkStatus.Terminated))
    }
    terminations.filterNot(t => t._2.isEmpty).values.foreach(validateTerminations)
    terminations.values.flatten.toSeq
  }

  private def findNewCreations(projectLinks: Map[RoadPart, Seq[ProjectLink]]) = {
    projectLinks.values.flatten.filter(_.status == LinkStatus.New).toSeq
  }

  private def validateTerminations(roadAddresses: Seq[RoadAddress]) = {
    if (roadAddresses.groupBy(ra => (ra.roadNumber, ra.roadPartNumber)).keySet.size != 1)
      throw new RoadAddressException("Multiple or no road parts present in one termination set")
    val missingSegments = checker.checkAddressesHaveNoGaps(roadAddresses)
    if (missingSegments.nonEmpty)
      throw new RoadAddressException(s"Termination has gaps in between: ${missingSegments.mkString("\n")}") //TODO: terminate only part of the road part later
  }

  def projectLinkPartition(projectLinks: Seq[ProjectLink]): Seq[RoadAddressSection] = {
    val grouped = projectLinks.groupBy(projectLink => (projectLink.roadNumber, projectLink.roadPartNumber, projectLink.track, projectLink.roadType))
    grouped.mapValues(v => combine(v.sortBy(_.startAddrMValue))).values.flatten.map(ra =>
      RoadAddressSection(ra.roadNumber, ra.roadPartNumber, ra.roadPartNumber,
        ra.track, ra.startAddrMValue, ra.endAddrMValue, ra.discontinuity, ra.roadType)
    ).toSeq
  }

  private def combineTwo[T <: BaseRoadAddress](r1: T, r2: T): Seq[T] = {
    if (r1.endAddrMValue == r2.startAddrMValue && r1.discontinuity == Discontinuity.Continuous)
      r1 match {
        case x: RoadAddress => Seq(x.copy(endAddrMValue = r2.endAddrMValue, discontinuity = r2.discontinuity).asInstanceOf[T])
        case x: ProjectLink => Seq(x.copy(endAddrMValue = r2.endAddrMValue, discontinuity = r2.discontinuity).asInstanceOf[T])
      }
    else
      Seq(r2, r1)
  }

  private def combine[T <: BaseRoadAddress](roadAddressSeq: Seq[T], result: Seq[T] = Seq()): Seq[T] = {
    if (roadAddressSeq.isEmpty)
      result.reverse
    else if (result.isEmpty)
      combine(roadAddressSeq.tail, Seq(roadAddressSeq.head))
    else
      combine(roadAddressSeq.tail, combineTwo(result.head, roadAddressSeq.head) ++ result.tail)
  }

  def partition(roadAddresses: Seq[RoadAddress]): Seq[RoadAddressSection] = {
    val grouped = roadAddresses.groupBy(ra => (ra.roadNumber, ra.roadPartNumber, ra.track))
    grouped.mapValues(v => combine(v.sortBy(_.startAddrMValue))).values.flatten.map(ra =>
      RoadAddressSection(ra.roadNumber, ra.roadPartNumber, ra.roadPartNumber,
        ra.track, ra.startAddrMValue, ra.endAddrMValue, ra.discontinuity, ra.roadType)
    ).toSeq
  }

  /**
    * Order list of geometrically continuous Project Links so that the chain is traversable from end to end
    * in this sequence and side code
    * @param list Project Links (already partitioned; connected)
    * @return Ordered list of project links
    */
  def orderProjectLinksTopologyByGeometry(list:Seq[ProjectLink]): Seq[ProjectLink] = {

    def isInvertSideCode(geom1 : Seq[Point], geom2 : Seq[Point]): Boolean = {
      GeometryUtils.areAdjacent(geom1.head, geom2.head) || GeometryUtils.areAdjacent(geom1.last, geom2.last)
    }

    def recursiveFindAndExtend(sortedList: Seq[ProjectLink], unprocessed: Seq[ProjectLink]) : Seq[ProjectLink] = {
      println("***")
      sortedList.foreach(println)
      println("-")
      unprocessed.foreach(println)
      println("***")
      val head = sortedList.head
      val last = sortedList.last
      val (headPoint, endPoint) = (
        head.sideCode match {
          case SideCode.AgainstDigitizing => head.geometry.last
          case _ => head.geometry.head
        },
        last.sideCode match {
          case SideCode.AgainstDigitizing => last.geometry.head
          case _ => last.geometry.last
        }
      )
      val headExtend = unprocessed.find(l => GeometryUtils.areAdjacent(l.geometry, headPoint))
      if (headExtend.nonEmpty) {
        val ext = headExtend.get
        println(s"${ext.geometry} match head ${headPoint}")
        recursiveFindAndExtend(Seq(ext)++sortedList, unprocessed.filterNot(p => p.linkId == ext.linkId))
      } else {
        val tailExtend = unprocessed.find(l => GeometryUtils.areAdjacent(l.geometry, endPoint))
        if (tailExtend.nonEmpty) {
          val ext = tailExtend.get
          println(s"${ext.geometry} match last ${endPoint}")
          recursiveFindAndExtend(sortedList++Seq(ext), unprocessed.filterNot(p => p.linkId == ext.linkId))
        } else {
          sortedList
        }
      }
    }
    recursiveFindAndExtend(Seq(list.head), list.tail)
  }

  def determineMValues(projectLinks: Seq[ProjectLink], geometryLengthList: Map[RoadPart, Seq[RoadLinkLength]], linksInProject: Seq [ProjectLink]): Seq[ProjectLink] = {
    def makeStartCP(projectLink: ProjectLink) = {
      Some(CalibrationPoint(projectLink.linkId, if (projectLink.sideCode == SideCode.TowardsDigitizing) 0.0 else projectLink.geometryLength, projectLink.startAddrMValue))
    }
    def makeEndCP(projectLink: ProjectLink) = {
      Some(CalibrationPoint(projectLink.linkId, if (projectLink.sideCode == SideCode.AgainstDigitizing) 0.0 else projectLink.geometryLength, projectLink.endAddrMValue))
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
      if (section.right.links.head.sideCode == SideCode.AgainstDigitizing && startMValue < endMValue) {
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
        val linkPartitions = DeltaPartitioner.partition(gpl._2).map(orderProjectLinksTopologyByGeometry)

        linkPartitions.foreach { lp =>
          println("Partition")
          lp.foreach(pl => println(s"${pl.linkId}, ${pl.geometry.head}-${pl.geometry.last}"))
        }
        val sections = linkPartitions.map(lp =>
          TrackSection(lp.head.roadNumber, lp.head.roadPartNumber, lp.head.track, lp.head.geometry.head, lp.last.geometry.last,
            lp.map(_.geometryLength).sum, lp)
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
    def listEndPoints(sortedList: Seq[CombinedSection]) = {
      (sortedList.head.startGeometry, sortedList.last.endGeometry)
    }

    def reverseSection(section: TrackSection): TrackSection = {
      section.copy(startGeometry = section.endGeometry, endGeometry = section.startGeometry, links =
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
          case SideCode.AgainstDigitizing => head.endGeometry
          case _ => head.startGeometry
        },
        last.sideCode match {
          case SideCode.AgainstDigitizing => last.startGeometry
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

  def isSideCodeChange(geom1: Seq[Point], geom2: Seq[Point]): Boolean = {
    GeometryUtils.areAdjacent(geom1.last, geom2.last) ||
      GeometryUtils.areAdjacent(geom1.head, geom2.head)
  }

  def switchSideCode(sideCode: SideCode): SideCode = {
    // Switch between against and towards 2 -> 3, 3 -> 2
    SideCode.apply(5-sideCode.value)
  }
}

case class Delta(startDate: DateTime, terminations: Seq[RoadAddress], newRoads: Seq[ProjectLink] = Seq.empty[ProjectLink])
case class RoadPart(roadNumber: Long, roadPartNumber: Long)
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
case class TrackSection(roadNumber: Long, roadPartNumber: Long, track: Track, startGeometry: Point, endGeometry: Point,
                        geometryLength: Double, links: Seq[ProjectLink])
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

object DeltaPartitioner extends GraphPartitioner {

  def partition[T <: ProjectLink](links: Seq[T]): Seq[Seq[T]] = {
    val linkGroups = links.groupBy { link => (
      link.roadNumber, link.roadPartNumber, link.track
    )
    }
    val clusters = for (linkGroup <- linkGroups.values.toSeq;
                        cluster <- clusterLinks(linkGroup)) yield cluster
    clusters.map(linksFromCluster)
  }
}

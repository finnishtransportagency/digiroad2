package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.util.{RoadAddressException, Track}
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

  def trackCodeChangeLengths(linkList: Seq[ProjectLink], otherProjectLinks: Seq[ProjectLink]): Seq[ProjectLink] = {

    def checkAdj(link1: ProjectLink, link2: ProjectLink): Boolean = {
      (GeometryUtils.areAdjacent(link1.geom.head, link2.geom.last) || GeometryUtils.areAdjacent(link1.geom.last, link2.geom.head))
    }

    def findLastAdj(list1: Seq[ProjectLink], list2: Seq[ProjectLink]): Seq[ProjectLink] = {
      if (list1.length == 0 || list2.length == 0) Nil
      else
        checkAdj(list1.head, list2.head) match {
          case true => Seq(list1.head) ++ findLastAdj(list1.tail, list2)
          case _ => findLastAdj(list1, list2.tail)
        }
    }

    def calculateMValues(track1: Seq[ProjectLink], track2: Seq[ProjectLink], start: Long): (Double, Double) = {
      val linkLength1 = track1.foldLeft(0.0) { (sum, length) => {
        sum + length.geometryLength
      }
      }
      val linkLength2 = track2.foldLeft(0.0) { (sum, length) => {
        sum + length.geometryLength
      }
      }
      (Math.round(start), Math.round((linkLength1 + linkLength2) / 2 + start))
    }

    def recalculateMidlePoints(list: Seq[ProjectLink], start: Double): Seq[ProjectLink] = {
      var startM = start
      list.map(l => {
        val end = startM + l.geometryLength
        val rec = l.copy(startAddrMValue = Math.round(startM), endAddrMValue = Math.round(end))
        startM = end
        rec
      })
    }

    def updateMValues(links: Seq[ProjectLink], values: (Double, Double)): Seq[ProjectLink] = {
      val sorted = links.sortBy(_.startAddrMValue)
      if (links.size == 1) {
        Seq(links.head.copy(startAddrMValue = values._1.toLong, endAddrMValue = values._2.toLong))
      } else {
        recalculateMidlePoints(sorted, values._1).map(l => {
          if (l.linkId == sorted.last.linkId) {
            l.copy(endAddrMValue = values._2.toLong, calibrationPoints = (None, None))
          } else if (l.linkId == sorted.head.linkId) {
            l.copy(startAddrMValue = values._1.toLong, calibrationPoints = (None, None))
          } else {
            l.copy(calibrationPoints = (None, None))
          }
        })
      }
    }

    def invertList(list1: Seq[ProjectLink], list2: Seq[ProjectLink]): Seq[ProjectLink] = {
      if (list1.size == 1) {
        list1.map(_ => list1.head.copy(startAddrMValue = list2.head.startAddrMValue, endAddrMValue = list2.head.endAddrMValue))
      } else {
        if (GeometryUtils.areAdjacent(list1.head.geom.head, list2.last.geom.head) && list1.head.startAddrMValue != list2.head.startAddrMValue) {
          list1.map(l => {
            if (l.linkId == list1.head.linkId) l.copy(startAddrMValue = list2.head.startAddrMValue) else l
          })
        }
        else if (GeometryUtils.areAdjacent(list1.last.geom.head, list2.head.geom.head) && list1.last.endAddrMValue != list2.last.endAddrMValue) {
          list1.map(l => {
            if (l.linkId == list1.last.linkId) l.copy(endAddrMValue = list2.last.endAddrMValue) else l
          })
        } else {
          list1
        }
      }
    }

    val rightLinks = linkList.filter(_.track == Track.RightSide)
    val leftLinks = linkList.filter(_.track == Track.LeftSide)
    val otherLinks = linkList.filterNot(l => (rightLinks ++ leftLinks).exists(_.linkId == l.linkId)).sortBy(_.startAddrMValue)
    val firstCombRight = findLastAdj(otherLinks, rightLinks)
    val firstCombLeft = findLastAdj(otherLinks, leftLinks)

    if (rightLinks.nonEmpty && leftLinks.nonEmpty) {
      val startValue = firstCombRight.find(fcr => firstCombLeft.exists(_.linkId == fcr.linkId)) match {
        case Some(prj) => prj.endAddrMValue
        case None => 0
      }
      val mValues = calculateMValues(rightLinks, leftLinks, startValue)
      if (startValue > 0 && (rightLinks.size == 1 || leftLinks.size == 1)) {
        firstCombRight.last.copy(startAddrMValue = mValues._2.toLong)
      }
      val changedLinks = (updateMValues(invertList(leftLinks, rightLinks), mValues) ++ updateMValues(invertList(rightLinks, leftLinks), mValues))
      linkList.map(l => {
        if (firstCombRight.length == 2 && l.linkId == firstCombRight.last.linkId) {
          l.copy(startAddrMValue = mValues._2.toLong)
        } else if (firstCombLeft.length == 2 && l.linkId == firstCombLeft.last.linkId) {
          l.copy(startAddrMValue = mValues._2.toLong)
        } else {
          changedLinks.find(_.linkId == l.linkId) match {
            case Some(link) => link
            case None => l
          }
        }
      })
    } else {
      linkList
    }
  }

  def getSplitStart(combinedLinks: Seq[ProjectLink], otherLinks: Seq[ProjectLink]): Double = {
    otherLinks.map(link => {
      combinedLinks.find(comb => {
        GeometryUtils.areAdjacent(comb.geom.last, link.geom.head)
      })
    }) match {
      case list if list.size > 0 => list.head.get.startMValue
      case _ => 0
    }
  }

  def orderProjectLinksTopologyByGeometry(list:Seq[ProjectLink]): Seq[ProjectLink] = {

    def linkTopologyByGeomEndPoints(sortedList: Seq[ProjectLink], unprocessed: Seq[ProjectLink]) : Seq[ProjectLink] = {
      unprocessed.find(l => GeometryUtils.areAdjacent(l.geom, sortedList.last.geom)) match {
        case Some(prj) =>
          val changedPrj = invertSideCode(prj.geom, sortedList.last.geom) match {
            case true => prj.copy(sideCode = if(sortedList.last.sideCode == SideCode.TowardsDigitizing) SideCode.AgainstDigitizing else SideCode.TowardsDigitizing)
            case _ => prj.copy(sideCode = sortedList.last.sideCode)
          }
          val unp = unprocessed.filterNot(u => u.linkId == changedPrj.linkId)
          linkTopologyByGeomEndPoints(sortedList ++ Seq(changedPrj), unp)
        case _ => sortedList
      }
    }

    def invertSideCode(geom1 : Seq[Point], geom2 : Seq[Point]): Boolean = {
      GeometryUtils.areAdjacent(geom1.head, geom2.head) || GeometryUtils.areAdjacent(geom1.last, geom2.last)
    }


    val linksAtEnd = list.filterNot(e =>  linkAdjacentInBothEnds(e.linkId, e.geom, list.filterNot(l =>l.linkId ==e.linkId).map(_.geom)))
    //TODO which one of the possible ones (ones at the borders) should be the first? discover by sideCode? How?
    val rest = list.filterNot(_.linkId == linksAtEnd.head.linkId )
    val possibleFirst = rest.exists(p => GeometryUtils.areAdjacent(p.geom.head, linksAtEnd.head.geom.head) || GeometryUtils.areAdjacent(p.geom.last, linksAtEnd.head.geom.head)) match {
      case true => linksAtEnd.head.copy(sideCode = SideCode.AgainstDigitizing)
      case _ => linksAtEnd.head
    }
    linkTopologyByGeomEndPoints(Seq(possibleFirst), rest)
  }

  def linkAdjacentInBothEnds(linkId: Long, geometry1: Seq[Point], geometries: Seq[Seq[Point]]): Boolean = {
    val epsilon = 0.01
    val geometry1EndPoints = GeometryUtils.geometryEndpoints(geometry1)
    val isLeftAdjacent = geometries.exists { g =>
      val geometry2Endpoints = GeometryUtils.geometryEndpoints(g)
      geometry1EndPoints._1.distance2DTo(geometry2Endpoints._1) < epsilon ||
        geometry1EndPoints._1.distance2DTo(geometry2Endpoints._2) < epsilon
    }
    val isRightAdjacent = geometries.exists { g =>
      val geometry2Endpoints = GeometryUtils.geometryEndpoints(g)
      geometry1EndPoints._2.distance2DTo(geometry2Endpoints._1) < epsilon ||
        geometry1EndPoints._2.distance2DTo(geometry2Endpoints._2) < epsilon
    }
    isLeftAdjacent && isRightAdjacent
  }

  def determineMValues(projectLinks: Seq[ProjectLink], geometryLengthList: Map[RoadPartBasis, Seq[RoadPartLengths]], linksInProject: Seq [ProjectLink]): Seq[ProjectLink] = {
    val groupedProjectLinks = projectLinks.groupBy(record => (record.roadNumber, record.roadPartNumber))
    groupedProjectLinks.flatMap(gpl => {
      var lastEndM = 0.0
      val roadPartId = RoadPart(gpl._1._1, gpl._1._2)
      if(geometryLengthList.keySet.contains(roadPartId)){
        val links = orderProjectLinksTopologyByGeometry(gpl._2)
        val linksToCheck= links.map(l => {
          val lengths = geometryLengthList.get(roadPartId).get
          val foundGeomLength = lengths.find(_.linkId == l.linkId).get
          val endValue = lastEndM + foundGeomLength.geometryLength
          val updatedProjectLink = l.copy(startMValue = 0.0, endMValue = foundGeomLength.geometryLength, startAddrMValue = Math.round(lastEndM), endAddrMValue = Math.round(endValue))
          lastEndM = endValue
          updatedProjectLink
        }).reverse
        trackCodeChangeLengths(linksToCheck, linksInProject)
      } else {
        orderProjectLinksTopologyByGeometry(gpl._2)
      }
    }).toSeq
  }

  def orderProjectLinks(unorderedProjectLinks: Seq[ProjectLink]): Seq[ProjectLink] = {
    def extending(link: ProjectLink, ext: ProjectLink) = {
      link.roadNumber == ext.roadNumber && link.roadPartNumber == ext.roadPartNumber &&
        link.track == ext.track
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
        (unordered++ordered).exists(
          un => un.linkId != ral.linkId && GeometryUtils.areAdjacent(un.geom, ral.geom)
        )
      } match {
        case Some(link) =>
          val sideCode = if (isSideCodeChange(link.geom, ordered.last.geom))
            switchSideCode(ordered.last.sideCode)
          else
            ordered.last.sideCode
          extendChainByGeometry(ordered ++ Seq(link.copy(sideCode=sideCode)), unordered.filterNot(link.equals), sideCode)
        case _ => throw new InvalidAddressDataException("Non-contiguous road target geometry")
      }
    }
    def extendChainByAddress(ordered: Seq[ProjectLink], unordered: Seq[ProjectLink]): Seq[ProjectLink] = {
      if (ordered.isEmpty)
        return extendChainByAddress(Seq(unordered.head), unordered.tail)
      if (unordered.isEmpty)
        return ordered
      val (next, rest) = unordered.partition(u => extending(ordered.last, u))
      if (next.nonEmpty)
        extendChainByAddress(ordered ++ next, rest)
      else {
        val (previous, rest) = unordered.partition(u => extending(u, ordered.head))
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
package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.util.{RoadAddressException, Track}
import fi.liikennevirasto.viite.RoadType
import fi.liikennevirasto.viite.dao._
import org.joda.time.DateTime

/**
  * Calculate the effective change between the project and the current road address data
  */
object ProjectDeltaCalculator {
  val checker = new ContinuityChecker(null) // We don't need road link service here
  def delta(projectId: Long): Delta = {
    val projectOpt = ProjectDAO.getRoadAddressProjectById(projectId)
    if (projectOpt.isEmpty)
      throw new IllegalArgumentException("Project not found")
    val project = projectOpt.get
    val projectLinks = ProjectDAO.getProjectLinks(projectId).groupBy(l => RoadPart(l.roadNumber,l.roadPartNumber))
    val currentAddresses = projectLinks.keySet.map(r => r -> RoadAddressDAO.fetchByRoadPart(r.roadNumber, r.roadPartNumber, true)).toMap
    val terminations = findTerminations(projectLinks, currentAddresses)
    if (terminations.size != currentAddresses.values.flatten.size)
      throw new RoadAddressException(s"Road address count did not match: ${terminations.size} terminated, ${currentAddresses.values.flatten.size} addresses found")
    // TODO: Find transfers, etc etc

    Delta(project.startDate, terminations.sortBy(t => (t.discontinuity.value, t.roadType.value)))
  }

  private def findTerminations(projectLinks: Map[RoadPart, Seq[ProjectLink]], currentAddresses: Map[RoadPart, Seq[RoadAddress]]) = {
    val terminations = projectLinks.map{case (part, pLinks) => part -> (pLinks, currentAddresses.getOrElse(part, Seq()))}.mapValues{ case (pll, ra) =>
      ra.filter(r => pll.exists(pl => pl.linkId == r.linkId && pl.status == LinkStatus.Terminated))
    }
    terminations.filterNot(t => t._2.isEmpty).values.foreach(validateTerminations)
    terminations.values.flatten.toSeq
  }

  private def validateTerminations(roadAddresses: Seq[RoadAddress]) = {
    if (roadAddresses.groupBy(ra => (ra.roadNumber, ra.roadPartNumber)).keySet.size != 1)
      throw new RoadAddressException("Multiple or no road parts present in one termination set")
    val missingSegments = checker.checkAddressesHaveNoGaps(roadAddresses)
    if (missingSegments.nonEmpty)
      throw new RoadAddressException(s"Termination has gaps in between: ${missingSegments.mkString("\n")}") //TODO: terminate only part of the road part later
  }

  // TODO: When other than terminations are handled we partition also project links: section should include road type
  def projectLinkPartition(projectLinks: Seq[ProjectLink]): Seq[RoadAddressSection] = ???

  def partition(roadAddresses: Seq[RoadAddress]): Seq[RoadAddressSection] = {
    def combineTwo(r1: RoadAddress, r2: RoadAddress): Seq[RoadAddress] = {
      if (r1.endAddrMValue == r2.startAddrMValue && r1.discontinuity == Discontinuity.Continuous)
        Seq(r1.copy(endAddrMValue = r2.endAddrMValue, discontinuity = r2.discontinuity))
      else
        Seq(r2, r1)
    }
    def combine(roadAddressSeq: Seq[RoadAddress], result: Seq[RoadAddress] = Seq()): Seq[RoadAddress] = {
      if (roadAddressSeq.isEmpty)
        result.reverse
      else if (result.isEmpty)
        combine(roadAddressSeq.tail, Seq(roadAddressSeq.head))
      else
        combine(roadAddressSeq.tail, combineTwo(result.head, roadAddressSeq.head) ++ result.tail)
    }
    val grouped = roadAddresses.groupBy(ra => (ra.roadNumber, ra.roadPartNumber, ra.track))
    grouped.mapValues(v => combine(v.sortBy(_.startAddrMValue))).values.flatten.map(ra =>
      RoadAddressSection(ra.roadNumber, ra.roadPartNumber, ra.roadPartNumber,
      ra.track, ra.startAddrMValue, ra.endAddrMValue, ra.discontinuity, ra.roadType)
    ).toSeq
  }
}

case class Delta(startDate: DateTime, terminations: Seq[RoadAddress])
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

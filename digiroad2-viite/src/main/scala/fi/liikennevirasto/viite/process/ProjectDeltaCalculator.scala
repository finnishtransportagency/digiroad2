package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.util.{RoadAddressException, Track}
import fi.liikennevirasto.viite.dao.{ProjectLink, _}
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
    val projectLinksFetched = ProjectDAO.getProjectLinks(projectId)
    val projectLinks = projectLinksFetched.groupBy(l => RoadPart(l.roadNumber,l.roadPartNumber))
    val currentAddresses = projectLinks.filter(_._2.exists(_.status != LinkStatus.New)).keySet.map(r =>
      r -> RoadAddressDAO.fetchByLinkId(projectLinksFetched.map(pl => pl.linkId).toSet, includeFloating = true)).toMap
    val terminations = findTerminations(projectLinks, currentAddresses)
    val newCreations = findNewCreations(projectLinks)
    val unChanged = findUnChanged(projectLinks, currentAddresses)
    val transferred = Transferred(findTransferredOld(projectLinks, currentAddresses), findTransferredNew(projectLinks))
    val numbering = ReNumeration(findNumberingOld(projectLinks, currentAddresses), findNumberingNew(projectLinks))

    Delta(project.startDate, terminations, newCreations, unChanged, transferred, numbering)
  }

  private def findTerminations(projectLinks: Map[RoadPart, Seq[ProjectLink]], currentAddresses: Map[RoadPart, Seq[RoadAddress]]) = {
    val terminations = projectLinks.map{case (part, pLinks) => part -> (pLinks, currentAddresses.getOrElse(part, Seq()))}.mapValues{ case (pll, ra) =>
      ra.filter(r => pll.exists(pl => pl.linkId == r.linkId && pl.status == LinkStatus.Terminated))
    }
    terminations.filterNot(t => t._2.isEmpty).values.foreach(validateTerminations)
    terminations.values.flatten.toSeq
  }

  private def findUnChanged(projectLinks: Map[RoadPart, Seq[ProjectLink]], currentAddresses: Map[RoadPart, Seq[RoadAddress]]) = {
    projectLinks.map{case (part, pLinks) => part -> (pLinks, currentAddresses.getOrElse(part, Seq()))}.mapValues{ case (pll, ra) =>
      ra.filter(r => pll.exists(pl => pl.linkId == r.linkId && pl.status == LinkStatus.UnChanged))
    }.values.flatten.toSeq
  }

  private def findTransferredOld(projectLinks: Map[RoadPart, Seq[ProjectLink]], currentAddresses: Map[RoadPart, Seq[RoadAddress]]) = {
    projectLinks.map{case (part, pLinks) => part -> (pLinks, currentAddresses.getOrElse(part, Seq()))}.mapValues{ case (pll, ra) =>
      ra.filter(r => pll.exists(pl => pl.linkId == r.linkId && pl.status == LinkStatus.Transfer))
    }.values.flatten.toSeq
  }

  private def findTransferredNew(projectLinks: Map[RoadPart, Seq[ProjectLink]]) = {
    projectLinks.values.flatten.filter(_.status == LinkStatus.Transfer).toSeq
  }

  private def findNumberingOld(projectLinks: Map[RoadPart, Seq[ProjectLink]], currentAddresses: Map[RoadPart, Seq[RoadAddress]]) = {
    projectLinks.map{case (part, pLinks) => part -> (pLinks, currentAddresses.getOrElse(part, Seq()))}.mapValues{ case (pll, ra) =>
      ra.filter(r => pll.exists(pl => pl.linkId == r.linkId && pl.status == LinkStatus.Numbering))
    }.values.flatten.toSeq
  }

  private def findNumberingNew(projectLinks: Map[RoadPart, Seq[ProjectLink]]) = {
    projectLinks.values.flatten.filter(_.status == LinkStatus.Numbering).toSeq
  }

  private def findNewCreations(projectLinks: Map[RoadPart, Seq[ProjectLink]]) = {
    projectLinks.values.flatten.filter(_.status == LinkStatus.New).toSeq
  }

  private def validateTerminations(roadAddresses: Seq[RoadAddress]) = {
    if (roadAddresses.groupBy(ra => (ra.roadNumber, ra.roadPartNumber)).keySet.size != 1)
      throw new RoadAddressException("Multiple or no road parts present in one termination set")
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

  def getRoadAddressSectionMap(roadGroups: Seq[RoadAddressSection], projectGroups: Seq[RoadAddressSection], roadAddresses: Seq[RoadAddress], projectLinks: Seq[ProjectLink] ): Seq[Map[RoadAddressSection, RoadAddressSection]]  = {
      roadGroups.map { sec =>
      val linkId = roadAddresses.find(ra => sec.includes(ra)).map(_.linkId).get
      val targetGroup = projectGroups.find(_.includes(projectLinks.find(_.linkId == linkId).get))
      Map(sec -> targetGroup.get)
    }
  }

  def getProjectLinkSectionMap(projectGroups: Seq[RoadAddressSection], roadGroups: Seq[RoadAddressSection], roadAddresses: Seq[RoadAddress], projectLinks: Seq[ProjectLink]  ): Seq[Map[RoadAddressSection, RoadAddressSection]] = {
      projectGroups.map { sec =>
      val linkId = projectLinks.find(ra => sec.includes(ra)).map(_.linkId).get
      val sourceGroup = roadGroups.find(_.includes(roadAddresses.find(_.linkId == linkId).get))
      Map(sourceGroup.get -> sec)
    }
  }

  /**
    * Partition the transfers into a mapping of RoadAddressSection -> RoadAddressSection.
    * It is impossible to tell afterwards the exact mapping unless done at this point
    * @param roadAddresses Road Addresses that were the source
    * @param projectLinks Project Links that have the transfer address values
    * @return Map between the sections old -> new
    */
  def partition(roadAddresses: Seq[RoadAddress], projectLinks: Seq[ProjectLink]): Seq[Map[RoadAddressSection, RoadAddressSection]] = {
    val groupedAddresses = roadAddresses.sortBy(_.startAddrMValue).groupBy(ra => (ra.roadNumber, ra.roadPartNumber, ra.track))
    val addressesGroups = groupedAddresses.mapValues(v => combine(v.sortBy(_.startAddrMValue))).values.flatten.map(ra =>
      RoadAddressSection(ra.roadNumber, ra.roadPartNumber, ra.roadPartNumber,
        ra.track, ra.startAddrMValue, ra.endAddrMValue, ra.discontinuity, ra.roadType)
    ).toSeq

    val groupedProjectLinks = projectLinks.sortBy(_.startAddrMValue).groupBy(pl => (pl.roadNumber, pl.roadPartNumber, pl.track))
    val projectLinksGroups = groupedProjectLinks.mapValues(v => combine(v.sortBy(_.startAddrMValue))).values.flatten.map(pl =>
      RoadAddressSection(pl.roadNumber, pl.roadPartNumber, pl.roadPartNumber,
        pl.track, pl.startAddrMValue, pl.endAddrMValue, pl.discontinuity, pl.roadType)
    ).toSeq

    if (groupedAddresses.size >= groupedProjectLinks.size)
      getRoadAddressSectionMap(addressesGroups, projectLinksGroups, roadAddresses, projectLinks)
    else
      getProjectLinkSectionMap(projectLinksGroups, addressesGroups, roadAddresses, projectLinks)
  }
}

case class Delta(startDate: DateTime, terminations: Seq[RoadAddress], newRoads: Seq[ProjectLink],
                 unChanged: Seq[RoadAddress], transferred: Transferred, numbering : ReNumeration)

case class RoadPart(roadNumber: Long, roadPartNumber: Long)

case class Transferred(oldLinks: Seq[RoadAddress], newLinks: Seq[ProjectLink])

case class ReNumeration(oldLinks: Seq[RoadAddress], newLinks: Seq[ProjectLink])

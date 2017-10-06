package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.util.RoadAddressException
import java.text.DecimalFormat

import fi.liikennevirasto.digiroad2.GeometryUtils
import fi.liikennevirasto.digiroad2.util.Track.RightSide
import fi.liikennevirasto.digiroad2.util.{RoadAddressException, Track}
import fi.liikennevirasto.viite.dao.{ProjectLink, _}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

/**
  * Calculate the effective change between the project and the current road address data
  */
object ProjectDeltaCalculator {

  val MaxAllowedMValueError = 0.001
  val checker = new ContinuityChecker(null) // We don't need road link service here
  lazy private val logger = LoggerFactory.getLogger(getClass)

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
      .mapValues(v => combine(v.sortBy(_.startAddrMValue))).values.flatten.map(ra =>
      RoadAddressSection(ra.roadNumber, ra.roadPartNumber, ra.roadPartNumber,
        ra.track, ra.startAddrMValue, ra.endAddrMValue, ra.discontinuity, ra.roadType)
    ).toSeq
    val paired = grouped.groupBy(section => (section.roadNumber, section.roadPartNumberStart, section.track))

    paired.flatMap { case (key, target) =>
      val matches = matchingTracks(paired, key)
      if (matches.nonEmpty)
        adjustTrack((target, matches.get))
      else
        target
    }.toSeq
  }

  private def combineTwo[T <: BaseRoadAddress, R <: BaseRoadAddress](tr1: (T,R), tr2: (T,R)): Seq[(T,R)] = {
    val (t1,r1) = tr1
    val (t2,r2) = tr2
    if (r1.endAddrMValue == r2.startAddrMValue && r1.discontinuity == Discontinuity.Continuous &&
      t1.endAddrMValue == t2.startAddrMValue && t1.discontinuity == Discontinuity.Continuous)
      Seq((
        t1 match {
          case x: RoadAddress => x.copy(endAddrMValue = t2.endAddrMValue, discontinuity = t2.discontinuity).asInstanceOf[T]
          case x: ProjectLink => x.copy(endAddrMValue = t2.endAddrMValue, discontinuity = t2.discontinuity).asInstanceOf[T]
      },
        r1 match {
          case x: RoadAddress => x.copy(endAddrMValue = r2.endAddrMValue, discontinuity = r2.discontinuity).asInstanceOf[R]
          case x: ProjectLink => x.copy(endAddrMValue = r2.endAddrMValue, discontinuity = r2.discontinuity).asInstanceOf[R]
      }))
    else {
      Seq(tr2, tr1)
    }
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


  private def combinePair[T <: BaseRoadAddress, R <: BaseRoadAddress](combinedSeq: Seq[(T,R)], result: Seq[(T,R)] = Seq()): Seq[(T,R)] = {
    if (combinedSeq.isEmpty)
      result.reverse
    else if (result.isEmpty)
      combinePair(combinedSeq.tail, Seq(combinedSeq.head))
    else
      combinePair(combinedSeq.tail, combineTwo(result.head, combinedSeq.head) ++ result.tail)
  }

  def adjustAddrValues(addrMValues: Long, mValue: Long, track: Track): Long = {
    val fusedValues = addrMValues%2 match {
      case 0 => addrMValues/2
      case _ =>
        if(track == RightSide ^ (mValue * 2 < addrMValues)){
          (addrMValues+1)/2
        } else {
          addrMValues/2
        }
    }
    fusedValues.toLong
  }

  def partition(roadAddresses: Seq[RoadAddress]): Seq[RoadAddressSection] = {
    val grouped = roadAddresses.groupBy(ra => (ra.roadNumber, ra.roadPartNumber, ra.track)).mapValues(v => combine(v.sortBy(_.startAddrMValue))).values.flatten.map(ra =>
      RoadAddressSection(ra.roadNumber, ra.roadPartNumber, ra.roadPartNumber,
        ra.track, ra.startAddrMValue, ra.endAddrMValue, ra.discontinuity, ra.roadType)
    ).toSeq

    val adjustedAddressMValues: Seq[RoadAddressSection] = grouped.groupBy(s => (s.roadNumber, s.roadPartNumberStart, s.roadPartNumberEnd)).flatMap {
      group =>
        group._2.sortBy(_.track.value).map{
          e =>
            e.copy(startMAddr = adjustAddrValues(group._2.map(_.startMAddr).sum, e.startMAddr, e.track),
              endMAddr = adjustAddrValues(group._2.map(_.endMAddr).sum, e.endMAddr, e.track))
        }
    }.toSeq
    adjustedAddressMValues
  }

  def pair(roadAddress: Seq[RoadAddress], projectLink: Map[Long, Seq[ProjectLink]]): Seq[(RoadAddress,ProjectLink)] = {
    roadAddress.foldLeft(List.empty[(RoadAddress,ProjectLink)]) { case (p, a) =>
      val options = projectLink.getOrElse(a.id, Seq())
      options.size match {
        case 1 =>
          p :+ (a, options.head)
        case 0 =>
          logger.error(s"Unmatched road address ${a.id}: ${a.roadNumber}/${a.roadPartNumber}/${a.track.value}/${a.startAddrMValue}-${a.endAddrMValue}")
          p
        case _ =>
          logger.info(s"${options.size} matches for road address ${a.id}: ${a.roadNumber}/${a.roadPartNumber}/${a.track.value}/${a.startAddrMValue}-${a.endAddrMValue}")
          p
      }
    }
  }

  private def reverse(track: Track): Track = {
    track match {
      case Track.RightSide => Track.LeftSide
      case Track.LeftSide => Track.RightSide
      case _ => track
    }
  }

  private def matchingTracks(map: Map[(Long,Long,Track,Long,Long,Track), (Seq[RoadAddressSection],Seq[RoadAddressSection])],
                             key: (Long,Long,Track,Long,Long,Track)): Option[(Seq[RoadAddressSection],Seq[RoadAddressSection])] = {
    map.get((key._1, key._2, reverse(key._3), key._4, key._5, reverse(key._6)))
  }

  private def matchingTracks(map: Map[(Long,Long,Track), Seq[RoadAddressSection]],
                             key: (Long,Long,Track)): Option[Seq[RoadAddressSection]] = {
    map.get((key._1, key._2, reverse(key._3)))
  }

  private def adjustTrack(group: (Seq[RoadAddressSection], Seq[RoadAddressSection])): Seq[RoadAddressSection] = {
    group._1.zip(group._2).map {
      case (e1, e2) =>
        e1.copy(startMAddr = adjustAddrValues(e1.startMAddr + e2.startMAddr, e1.startMAddr, e1.track),
          endMAddr = adjustAddrValues(e1.endMAddr + e2.endMAddr, e1.endMAddr, e1.track))
    }
  }

  /**
    * Partition the transfers into a mapping of RoadAddressSection -> RoadAddressSection.
    * It is impossible to tell afterwards the exact mapping unless done at this point
    * @param roadAddresses Road Addresses that were the source
    * @param projectLinks Project Links that have the transfer address values
    * @return Map between the sections old -> new
    */
  def partition(roadAddresses: Seq[RoadAddress], projectLinks: Seq[ProjectLink]): Map[RoadAddressSection, RoadAddressSection] = {
    def toRoadAddressSection(o: Seq[BaseRoadAddress]): Seq[RoadAddressSection] = {
      o.sortBy(_.startAddrMValue).map(ra =>
        RoadAddressSection(ra.roadNumber, ra.roadPartNumber, ra.roadPartNumber,
          ra.track, ra.startAddrMValue, ra.endAddrMValue, ra.discontinuity, ra.roadType))
    }
    val paired = pair(roadAddresses, projectLinks.groupBy(_.roadAddressId))
      .groupBy(x => (x._1.roadNumber, x._1.roadPartNumber, x._1.track, x._2.roadNumber, x._2.roadPartNumber, x._2.track))
      .mapValues(v => combinePair(v.sortBy(_._1.startAddrMValue)))
      .mapValues(v => {
        val x = v.unzip
        toRoadAddressSection(x._1) -> toRoadAddressSection(x._2)
      })

    paired.flatMap { case (key, (src, target)) =>
      val matches = matchingTracks(paired, key)
      if (matches.nonEmpty)
        adjustTrack((src, matches.get._1)).zip(adjustTrack((target, matches.get._2)))
      else
        src.zip(target)
    }
  }
}

case class Delta(startDate: DateTime, terminations: Seq[RoadAddress], newRoads: Seq[ProjectLink],
                 unChanged: Seq[RoadAddress], transferred: Transferred, numbering : ReNumeration)

case class RoadPart(roadNumber: Long, roadPartNumber: Long)

case class Transferred(oldLinks: Seq[RoadAddress], newLinks: Seq[ProjectLink])

case class ReNumeration(oldLinks: Seq[RoadAddress], newLinks: Seq[ProjectLink])

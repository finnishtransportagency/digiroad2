package fi.liikennevirasto.viite

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle}
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao.Discontinuity._
import fi.liikennevirasto.viite.dao.LinkStatus._
import fi.liikennevirasto.viite.dao._
import fi.liikennevirasto.viite.process.TrackSectionOrder

object ProjectValidator {

  sealed trait ValidationErrorTrait {
    def value: Int
    def message: String
    def notification: Boolean
    def alterMessage(newMsg:String): ValidationErrorTrait
  }

  case class ValidationError(value: Int, message: String, notification: Boolean) extends ValidationErrorTrait {
    def alterMessage(newMsg: String): ValidationError = {
      this.copy(message = newMsg)
    }
  }

  object ValidationErrorList {
    val values = Set(MinorDiscontinuityFound, MajorDiscontinuityFound, InsufficientTrackCoverage, DiscontinuousAddressScheme,
      SharedLinkIdsExist, NoContinuityCodesAtEnd, UnsuccessfulRecalculation, MissingEndOfRoad, HasNotHandledLinks, ConnectedDiscontinuousLink,
      IncompatibleDiscontinuityCodes, EndOfRoadNotOnLastPart, ElyCodeChangeDetected, DiscontinuityOnRamp,
      RoadNotEndingInElyBorder, RoadContinuesInAnotherEly, ErrorInValidationOfUnchangedLinks)

    //Viite-942
    case object MissingEndOfRoad extends ValidationError (0 ,MissingEndOfRoadMessage, false)
    //Viite-453
    //There must be a minor discontinuity if the jump is longer than 0.1 m (10 cm) between road links
    case object MinorDiscontinuityFound extends ValidationError (1, MinorDiscontinuityFoundMessage, true)
    //Viite-453
    //There must be a major discontinuity if the jump is longer than 50 meters
    case object MajorDiscontinuityFound extends ValidationError (2, MajorDiscontinuityFoundMessage, false)
    //Viite-453
    //For every track 1 there must exist track 2 that covers the same address span and vice versa
    case object InsufficientTrackCoverage extends ValidationError (3, InsufficientTrackCoverageMessage, false)
    //Viite-453
    //There must be a continuous road addressing scheme so that all values from 0 to the highest number are covered
    case object DiscontinuousAddressScheme extends ValidationError (4, DiscontinuousAddressSchemeMessage, false)
    //Viite-453
    //There are no link ids shared between the project and the current road address + lrm_position tables at the project date (start_date, end_date)
    case object SharedLinkIdsExist extends ValidationError (5, SharedLinkIdsExistMessage, false)
    //Viite-453
    //Continuity codes are given for end of road
    case object NoContinuityCodesAtEnd extends ValidationError (6, NoContinuityCodesAtEndMessage, false)
    //Viite-453
    //Recalculation of M values and delta calculation are both unsuccessful for every road part in project
    case object UnsuccessfulRecalculation extends ValidationError (7, UnsuccessfulRecalculationMessage, false)

    case object HasNotHandledLinks extends ValidationError(8, "", false)

    case object ConnectedDiscontinuousLink extends ValidationError (9, ConnectedDiscontinuousMessage, false)

    case object IncompatibleDiscontinuityCodes extends ValidationError (10, DifferingDiscontinuityCodesForTracks, false)

    case object EndOfRoadNotOnLastPart extends ValidationError (11, EndOfRoadNotOnLastPartMessage, false)

    case object ElyCodeChangeDetected extends ValidationError (12, ElyCodeChangeNotPresent, false)

    case object DiscontinuityOnRamp extends ValidationError (13, RampDiscontinuityFoundMessage, true)

    case object RoadNotEndingInElyBorder extends ValidationError (14, RoadNotEndingInElyBorderMessage, true)

    case object RoadContinuesInAnotherEly extends ValidationError (15, RoadContinuesInAnotherElyMessage, true)

    //Viite-473
    // Unchanged project links cannot have any other operation (transfer, termination) previously on the same number and part
    case object ErrorInValidationOfUnchangedLinks extends ValidationError (16, ErrorInValidationOfUnchangedLinksMessage, false)

    def apply(intValue: Int): ValidationError = {
      values.find(_.value == intValue).get
    }
  }

  case class ValidationErrorDetails(projectId: Long, validationError: ValidationError,
                                    affectedLinkIds: Seq[Long], coordinates: Seq[ProjectCoordinates],
                                    optionalInformation: Option[String])

  def validateProject(project: RoadAddressProject, projectLinks: Seq[ProjectLink]): Seq[ValidationErrorDetails] = {

    def checkProjectContinuity: Seq[ValidationErrorDetails] =
      projectLinks.filter(_.status != Terminated).groupBy(pl => (pl.roadNumber, pl.roadPartNumber)).flatMap {
        case ((road, _), seq) =>
          if (road < RampsMinBound || road > RampsMaxBound) {
            checkOrdinaryRoadContinuityCodes(project, seq)
          } else {
            checkRampContinuityCodes(project, seq)
          }
        case _ => Seq()
      }.toSeq

    def checkProjectCoverage = {
      Seq.empty[ValidationErrorDetails]
    }

    def checkProjectContinuousSchema = {
      Seq.empty[ValidationErrorDetails]
    }

    def checkProjectSharedLinks = {
      Seq.empty[ValidationErrorDetails]
    }

    def checkForContinuityCodes = {
      Seq.empty[ValidationErrorDetails]
    }

    def checkForUnsuccessfulRecalculation = {
      Seq.empty[ValidationErrorDetails]
    }

    def checkForNotHandledLinks = {
      val notHandled = projectLinks.filter(_.status == LinkStatus.NotHandled)
      notHandled.groupBy(link => (link.roadNumber, link.roadPartNumber)).foldLeft(Seq.empty[ValidationErrorDetails])((errorDetails, road) =>
        errorDetails :+ ValidationErrorDetails(project.id, ValidationErrorList.HasNotHandledLinks,
          Seq(road._2.size), road._2.map(l => ProjectCoordinates(l.geometry.head.x, l.geometry.head.y, 12)),
          Some(HasNotHandledLinksMessage.format(road._2.size, road._1._1, road._1._2)))
      )
    }

    def checkForInvalidUnchangedLinks = {
      val roadNumberAndParts = projectLinks.groupBy(pl => (pl.roadNumber, pl.roadPartNumber)).keySet
      val invalidUnchangedLinks = roadNumberAndParts.flatMap(rn => ProjectDAO.getInvalidUnchangedOperationProjectLinks(rn._1, rn._2)).toSeq
      invalidUnchangedLinks.map(projectLink =>
        ValidationErrorDetails(project.id, ValidationErrorList.ErrorInValidationOfUnchangedLinks,
          Seq(projectLink.linkId), Seq(ProjectCoordinates(projectLink.geometry.head.x, projectLink.geometry.head.y, 12)),
          Some("TIE : %d, OSA: %d, AET: %d".format(projectLink.roadNumber, projectLink.roadPartNumber, projectLink.startAddrMValue))))
    }

    def checkProjectElyCodes:Seq[ValidationErrorDetails] = {
      val grouped = projectLinks.groupBy(pl => (pl.roadNumber, pl.roadPartNumber)).map(group => group._1 -> group._2.sortBy(_.endAddrMValue))
      project.ely.get match {
        case 3 => {
            firstElyBorderCheck(project, grouped)
        }
        case _ => {
            secondElyBorderCheck(project, grouped)
        }
      }
    }

    checkProjectContinuity ++ checkProjectCoverage ++ checkProjectContinuousSchema ++ checkProjectSharedLinks ++
      checkForContinuityCodes ++ checkForUnsuccessfulRecalculation ++ checkForNotHandledLinks ++ checkForInvalidUnchangedLinks
  }

  def checkRemovedEndOfRoadParts(project: RoadAddressProject): Seq[ValidationErrorDetails] = {
    // Pick only parts that have no length anymore and had end of road given before
    project.reservedParts.filter(rrp => rrp.addressLength.nonEmpty && rrp.newLength.getOrElse(0L) == 0L &&
      rrp.discontinuity.contains(EndOfRoad))

      // There is no EndOfRoad in this project for this road
      .filterNot(rrp => project.reservedParts.exists(np =>
      np.roadNumber == rrp.roadNumber && np.roadPartNumber < rrp.roadPartNumber &&
        np.newLength.getOrElse(0L) > 0L && np.newDiscontinuity.contains(EndOfRoad)))
      .map { rrp =>
        ValidationErrorDetails(project.id, ValidationErrorList.MissingEndOfRoad, Seq(),
          Seq(), Some(s"TIE ${rrp.roadNumber} OSA ${project.reservedParts.filter(p => p.roadNumber == rrp.roadNumber &&
          p.newLength.getOrElse(0L) > 0L).map(_.roadPartNumber).max}"))
      }
  }

  /**
    * Check for non-ramp and roundabout roads:
    * 1) If inside a part there is a gap between links > .1 meters, discontinuity 4 (minor) is required
    * 2) If inside a part there is no gap, discontinuity 5 (cont) is required
    * 3) End of road part, discontinuity 2 or 3 (major, ely change) is required if there is a gap
    * 4) If a part that contained end of road discontinuity is terminated / renumbered / transferred,
    *    there must be a new end of road link for that road at the last part
    * 5) If the next road part has differing ely code then there must be a discontinuity code 3 at the end
    * @param project
    * @param seq
    * @return
    */
  def checkOrdinaryRoadContinuityCodes(project: RoadAddressProject, seq: Seq[ProjectLink]): Seq[ValidationErrorDetails] = {
    def error(validationError: ValidationError)(pl: Seq[ProjectLink]) = {
      val (linkIds, points) = pl.map(pl => (pl.linkId, GeometryUtils.midPointGeometry(pl.geometry))).unzip
      if (linkIds.nonEmpty)
        Some(ValidationErrorDetails(project.id, validationError, linkIds,
          points.map(p => ProjectCoordinates(p.x, p.y, 12)), None))
      else
        None
    }
    def checkConnectedAreContinuous = {
      error(ValidationErrorList.ConnectedDiscontinuousLink)(seq.filterNot(pl =>
        // Check that pl is continuous or after it there is no connected project link
        pl.discontinuity == Continuous ||
          !seq.exists(pl2 => pl2.startAddrMValue == pl.endAddrMValue && trackMatch(pl2.track, pl.track) && connected(pl2, pl))
      ))
    }
    def checkNotConnectedHaveMinorDiscontinuity = {
      val possibleDiscontinuous = seq.filterNot{pl =>
        // Check that pl has discontinuity or after it the project links are connected (except last, where forall is true for empty list)
        pl.discontinuity == MinorDiscontinuity ||
          seq.filter(pl2 => pl2.startAddrMValue == pl.endAddrMValue && trackMatch(pl2.track, pl.track)).forall(pl2 => connected(pl2, pl))
      }
      val adjacentRoadAddresses = possibleDiscontinuous.filterNot(pd => {
        val roadsDiscontinuity = RoadAddressDAO.fetchByRoadPart(pd.roadNumber, pd.roadPartNumber)
        val sameDiscontinuity = roadsDiscontinuity.map(_.discontinuity).distinct.contains(pd.discontinuity)
        val overlapping = roadsDiscontinuity.exists(p => GeometryUtils.overlaps((p.startMValue, p.endMValue), (pd.startMValue, pd.endMValue)))
        sameDiscontinuity && overlapping
      })
      error(ValidationErrorList.MinorDiscontinuityFound)(adjacentRoadAddresses)
    }
    def checkRoadPartEnd(lastProjectLinks: Seq[ProjectLink]): Option[ValidationErrorDetails] = {
      if (lastProjectLinks.exists(_.discontinuity != lastProjectLinks.head.discontinuity))
        error(ValidationErrorList.IncompatibleDiscontinuityCodes)(lastProjectLinks)
      else {
        val (road, part) = (lastProjectLinks.head.roadNumber, lastProjectLinks.head.roadPartNumber)
        val discontinuity = lastProjectLinks.head.discontinuity
        val ely = lastProjectLinks.head.ely
        val projectNextRoadParts = project.reservedParts.filter(rp =>
          rp.roadNumber == road && rp.roadPartNumber > part)

        val nextProjectPart = projectNextRoadParts.filter(_.newLength.getOrElse(0L) > 0L)
          .map(_.roadPartNumber).sorted.headOption
        val nextAddressPart = RoadAddressDAO.getValidRoadParts(road.toInt, project.startDate)
          .filterNot(p => p == part || (!projectNextRoadParts.isEmpty && projectNextRoadParts.exists(_.roadPartNumber == p))).sorted.headOption
        if (nextProjectPart.isEmpty && nextAddressPart.isEmpty) {
          if (discontinuity != EndOfRoad)
            return error(ValidationErrorList.MissingEndOfRoad)(lastProjectLinks)
        } else {
          val nextLinks =
            if (nextProjectPart.nonEmpty && (nextAddressPart.isEmpty || nextProjectPart.get < nextAddressPart.get))
              ProjectDAO.fetchByProjectRoadPart(road, nextProjectPart.get, project.id).filter(_.startAddrMValue == 0L)
            else
              RoadAddressDAO.fetchByRoadPart(road, nextAddressPart.get, includeFloating = true, includeExpired = false, includeHistory = false)
                .filter(_.startAddrMValue == 0L)
          //TODO to be done/changed in a more detailed story
//          if (nextLinks.exists(_.ely != ely) && discontinuity != ChangingELYCode)
//            return error(ValidationError.ElyCodeChangeDetected)(lastProjectLinks)
          val isConnected = lastProjectLinks.forall(lpl => nextLinks.exists(nl => trackMatch(nl.track, lpl.track) &&
            connected(lpl, nl)))
          val isDisConnected = !lastProjectLinks.exists(lpl => nextLinks.exists(nl => trackMatch(nl.track, lpl.track) &&
            connected(lpl, nl)))
          if (isDisConnected) {
            discontinuity match {
              case Continuous | MinorDiscontinuity =>
                return error(ValidationErrorList.MajorDiscontinuityFound)(lastProjectLinks)
              case EndOfRoad =>
                return error(ValidationErrorList.EndOfRoadNotOnLastPart)(lastProjectLinks)
              case _ => // no error, continue
            }
          }
          if (isConnected) {
            discontinuity match {
              case MinorDiscontinuity | Discontinuous =>
                return error(ValidationErrorList.ConnectedDiscontinuousLink)(lastProjectLinks)
              case EndOfRoad =>
                return error(ValidationErrorList.EndOfRoadNotOnLastPart)(lastProjectLinks)
              case _ => // no error, continue
            }
          }
        }
        None
      }
    }
    // Checks inside road part (not including last links' checks)
    checkConnectedAreContinuous.toSeq ++ checkNotConnectedHaveMinorDiscontinuity.toSeq ++
      checkRoadPartEnd(seq.filter(_.endAddrMValue == seq.maxBy(_.endAddrMValue).endAddrMValue)).toSeq
  }

  /**
    * Check for ramp and roundabout roads:
    * 1) If inside a part there is a gap between links > .1 meters, discontinuity 4 (minor) is required
    * 2) If inside a part there is no gap, discontinuity 5 (cont) is required
    * 3) End of road part, discontinuity 2 or 3 (major, ely change) is required if there is a gap
    * 4) If a part that contained end of road discontinuity is terminated / renumbered / transferred,
    *    there must be a new end of road link for that road at the last part
    * 5) If the next road part has differing ely code then there must be a discontinuity code 3 at the end
    * @param project
    * @param seq
    * @return
    */
  def checkRampContinuityCodes(project: RoadAddressProject, seq: Seq[ProjectLink]): Seq[ValidationErrorDetails] = {
    def endPoint(b: BaseRoadAddress) = {
      b.sideCode match {
        case TowardsDigitizing => b.geometry.last
        case AgainstDigitizing => b.geometry.head
        case _ => Point(0.0, 0.0)
      }
    }
    def error(validationError: ValidationError)(pl: Seq[ProjectLink]) = {
      val (linkIds, points) = pl.map(pl => (pl.linkId, GeometryUtils.midPointGeometry(pl.geometry))).unzip
      if (linkIds.nonEmpty)
        Some(ValidationErrorDetails(project.id, validationError, linkIds,
          points.map(p => ProjectCoordinates(p.x, p.y, 12)), None))
      else
        None
    }
    def checkDiscontinuityBetweenLinks = {
      error(ValidationErrorList.DiscontinuityOnRamp)(seq.filter{pl =>
        // Check that pl has no discontinuity unless on last link and after it the possible project link is connected
        val nextLink = seq.find(pl2 => pl2.startAddrMValue == pl.endAddrMValue)
        (nextLink.nonEmpty && pl.discontinuity != Continuous) ||
          nextLink.exists(pl2 => !connected(pl2, pl))
      })
    }
    def checkRoadPartEnd(lastProjectLinks: Seq[ProjectLink]): Option[ValidationErrorDetails] = {
      val (road, part) = (lastProjectLinks.head.roadNumber, lastProjectLinks.head.roadPartNumber)
      val discontinuity = lastProjectLinks.head.discontinuity
      val ely = lastProjectLinks.head.ely
      val projectNextRoadParts = project.reservedParts.filter(rp =>
        rp.roadNumber == road && rp.roadPartNumber > part)

      val nextProjectPart = projectNextRoadParts.filter(_.newLength.getOrElse(0L) > 0L)
        .map(_.roadPartNumber).sorted.headOption
      val nextAddressPart = RoadAddressDAO.getValidRoadParts(road.toInt, project.startDate)
        .filterNot(p => projectNextRoadParts.exists(_.roadPartNumber == p)).sorted.headOption
      if (nextProjectPart.isEmpty && nextAddressPart.isEmpty) {
        if (discontinuity != EndOfRoad)
          return error(ValidationErrorList.MissingEndOfRoad)(lastProjectLinks)
      } else {
        val nextLinks =
          if (nextProjectPart.nonEmpty && (nextAddressPart.isEmpty || nextProjectPart.get < nextAddressPart.get))
            ProjectDAO.fetchByProjectRoadPart(road, nextProjectPart.get, project.id).filter(_.startAddrMValue == 0L)
          else
            RoadAddressDAO.fetchByRoadPart(road, nextAddressPart.get, includeFloating = true, includeExpired = false, includeHistory = false)
              .filter(_.startAddrMValue == 0L)
        //TODO to be done/changed in a more detailed story
//        if (nextLinks.exists(_.ely != ely) && discontinuity != ChangingELYCode)
//          return error(ValidationError.ElyCodeChangeDetected)(lastProjectLinks)
        val isConnected = lastProjectLinks.forall(lpl => nextLinks.exists(nl => connected(lpl, nl)))
        val isDisConnected = !lastProjectLinks.exists(lpl => nextLinks.exists(nl => connected(lpl, nl)))
        if (isDisConnected) {
          discontinuity match {
            case MinorDiscontinuity =>
              // This code means that this road part (of a ramp) should be connected to a roundabout
              val endPoints = lastProjectLinks.map(endPoint).map(p => (p.x, p.y)).unzip
              val boundingBox = BoundingRectangle(Point(endPoints._1.min,
                endPoints._2.min),Point(endPoints._1.max, endPoints._2.max))
              // Fetch all ramps and roundabouts roads and parts this is connected to (or these, if ramp has multiple links)
              val roadParts = RoadAddressDAO.fetchRoadAddressesByBoundingBox(boundingBox, false, false,
                Seq((RampsMinBound, RampsMaxBound))).filter(ra =>
                lastProjectLinks.exists(pl => connected(pl, ra))).groupBy(ra => (ra.roadNumber, ra.roadPartNumber))

              // Check all the fetched road parts to see if any of them is a roundabout
              if (!roadParts.keys.exists (rp => TrackSectionOrder.isRoundabout(
                RoadAddressDAO.fetchByRoadPart(rp._1, rp._2, includeFloating = true))))
                return error(ValidationErrorList.DiscontinuityOnRamp)(lastProjectLinks)
            case Continuous =>
              return error(ValidationErrorList.MajorDiscontinuityFound)(lastProjectLinks)
            case EndOfRoad =>
              return error(ValidationErrorList.EndOfRoadNotOnLastPart)(lastProjectLinks)
            case _ => // no error, continue
          }
        }
        if (isConnected) {
          discontinuity match {
            case MinorDiscontinuity | Discontinuous =>
              return error(ValidationErrorList.ConnectedDiscontinuousLink)(lastProjectLinks)
            case EndOfRoad =>
              return error(ValidationErrorList.EndOfRoadNotOnLastPart)(lastProjectLinks)
            case _ => // no error, continue
          }
        }
      }
      None
    }
    // Checks inside road part (not including last links' checks)
    checkDiscontinuityBetweenLinks.toSeq ++
      checkRoadPartEnd(seq.filter(_.endAddrMValue == seq.maxBy(_.endAddrMValue).endAddrMValue)).toSeq
  }

  private def connected(pl1: BaseRoadAddress, pl2: BaseRoadAddress) = {
    GeometryUtils.areAdjacent(pl1.geometry, pl2.geometry, fi.liikennevirasto.viite.MaxDistanceForConnectedLinks)
  }

  private def trackMatch(track1: Track, track2: Track) = {
    track1 == track2 || track1 == Track.Combined || track2 == Track.Combined
  }

  private def firstElyBorderCheck(project: RoadAddressProject, groupedProjectLinks: Map[(Long, Long), Seq[ProjectLink]]) = {
    def error(validationError: ValidationError)(pl: Seq[BaseRoadAddress]):Option[ValidationErrorDetails] = {
      val (linkIds, points) = pl.map(pl => (pl.linkId, GeometryUtils.midPointGeometry(pl.geometry))).unzip
      if (linkIds.nonEmpty)
        Some(ValidationErrorDetails(project.id, validationError, linkIds,
          points.map(p => ProjectCoordinates(p.x, p.y, 12)), None))
      else
        None
    }
    def checkValidation(roads: Seq[BaseRoadAddress]) = {
      if(roads.forall(_.ely == 3))
        error(ValidationErrorList.RoadNotEndingInElyBorder)(roads)
      else
        Option.empty[ValidationErrorDetails]
    }
    val validationProblems = groupedProjectLinks.flatMap(group => {
      //Possibility to search for road addresses
      val roadNumber = group._1._1
      val roadPartNumber = group._1._2

      val addresses = RoadAddressDAO.fetchByRoadPart(roadNumber, roadPartNumber)
      val invalidAddresses = checkValidation(addresses)
      val invalidProjectLinks = checkValidation(group._2)

      invalidProjectLinks ++ invalidAddresses
    })
     validationProblems.toSeq
  }

  private def secondElyBorderCheck(project: RoadAddressProject, groupedProjectLinks: Map[(Long, Long), Seq[ProjectLink]]) = {
    def error(validationError: ValidationError)(pl: Seq[BaseRoadAddress]):Option[ValidationErrorDetails] = {
      val grouppedByEly = pl.groupBy(_.ely)
      val (gLinkIds, gPoints, gEly) = grouppedByEly.flatMap(g => {
        val links = g._2
        val zoomPoint = GeometryUtils.midPointGeometry(links.minBy(_.endAddrMValue).geometry)
        links.map(l => (l.linkId, zoomPoint, l.ely))
      }).unzip3
      if(gLinkIds.nonEmpty)
        Some(ValidationErrorDetails(project.id, validationError.alterMessage(validationError.message.format(gEly.mkString(", "))), gLinkIds.toSeq,
          gPoints.map(p => ProjectCoordinates(p.x, p.y, 12)).toSeq, None))
      else
        Option.empty[ValidationErrorDetails]
    }
    def checkValidation(roads: Seq[BaseRoadAddress]) = {
      val (sameEly, diffEly) = roads.partition(_.ely != project.ely.get)
      if(diffEly.nonEmpty){
        error(ValidationErrorList.RoadContinuesInAnotherEly)(diffEly)
      } else {
        Option.empty[ValidationErrorDetails]
      }
    }
    val validationProblems = groupedProjectLinks.flatMap(group => {
      //Possibility to search for road addresses
      val roadNumber = group._1._1
      val roadPartNumber = group._1._2
      val projectLink = group._2

      val addresses = RoadAddressDAO.fetchByRoadPart(roadNumber, roadPartNumber)
      val invalidAddresses = checkValidation(addresses)
      val invalidProjectLinks = checkValidation(projectLink)

    invalidAddresses ++ invalidProjectLinks

    })
    validationProblems.toSeq
  }
}

class ProjectValidationException(s: String) extends RuntimeException {
  override def getMessage: String = s
}

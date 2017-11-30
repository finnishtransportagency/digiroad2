package fi.liikennevirasto.viite

import fi.liikennevirasto.viite.dao.ProjectCoordinates

object ProjectValidatorService {

  sealed trait ValidationError {
    def value: Int
    def message: String
  }

  object ValidationError {
    val values = Set(minorDiscontinuityFound, majorDiscontinuityFound, insufficientTrackCoverage, discontinuousAddressScheme,
      sharedLinkIdsExist, noContinuityCodesAtEnd, unsuccessfulRecalculation)
    //There must be a minor discontinuity if the jump is longer than 0.1 m (10 cm) between road links
    case object minorDiscontinuityFound extends ValidationError {def value = 0; def message = ""}
    //There must be a major discontinuity if the jump is longer than 50 meters
    case object majorDiscontinuityFound extends ValidationError {def value = 1; def message = ""}
    //For every track 1 there must exist track 2 that covers the same address span and vice versa
    case object insufficientTrackCoverage extends ValidationError {def value = 2; def message = ""}
    //There must be a continuous road addressing scheme so that all values from 0 to the highest number are covered
    case object discontinuousAddressScheme extends ValidationError {def value = 3; def message = ""}
    //There are no link ids shared between the project and the current road address + lrm_position tables at the project date (start_date, end_date)
    case object sharedLinkIdsExist extends ValidationError {def value = 4; def message = ""}
    //Continuity codes are given for end of road
    case object noContinuityCodesAtEnd extends ValidationError {def value = 5; def message = ""}
    //Recalculation of M values and delta calculation are both successful for every road part in project
    case object unsuccessfulRecalculation extends ValidationError {def value = 6; def message = ""}
    def apply(intValue: Int): ValidationError = {
      values.find(_.value == intValue).get
    }
  }

  case class validationErrorDetails(projectId: Long, validationError: ValidationError,
                                    affectedLinkIds: Set[Long], coordinates: Map[Long,ProjectCoordinates],
                                    optionalInformation: Option[String])

}

package fi.liikennevirasto.digiroad2.process.assetValidator

import fi.liikennevirasto.digiroad2.dao.LaneValidatorDao

object LaneValidators extends Validators {
  override def forSamuutus: Seq[ValidatorFunction] = Seq(laneFitIntoLink, lanesIsNotTooShort, lanesDoesNotOverlap, incoherentLaneCode)
  override def forTopology: Seq[ValidatorFunction] = Seq(laneFitIntoLink, lanesIsNotTooShort, lanesDoesNotOverlap, incoherentLaneCode)
  //4. lanes
  //a)
  private def laneFitIntoLink(assetType: Int, linkIds: Set[String]): returnResult = {
    Validators.returnValidationResult(0,LaneValidatorDao.laneWhichDoesNotFallInFullyToLink(linkIds))
  }
  //b)
  private def lanesDoesNotOverlap(assetType: Int, linkIds: Set[String]): returnResult = {
    Validators.returnValidationResult(0,LaneValidatorDao.overFlappingLanes(linkIds))
  }
  //c)
  private def lanesIsNotTooShort(assetType: Int, linkIds: Set[String]): returnResult = {
    Validators.returnValidationResult(0,LaneValidatorDao.laneTooShort(linkIds))
  }
  //d)
  private def incoherentLaneCode(assetType: Int, linkIds: Set[String]): returnResult = {
    Validators.returnValidationResult(0,LaneValidatorDao.incoherentLane(linkIds))
  }
}

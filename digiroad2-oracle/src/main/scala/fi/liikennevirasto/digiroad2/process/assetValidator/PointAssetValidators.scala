package fi.liikennevirasto.digiroad2.process.assetValidator

import fi.liikennevirasto.digiroad2.dao.PointAssetValidatorDao

object PointAssetValidators extends Validators{
  override val forSamuutus: Seq[ValidatorFunction] = Seq(pointLikeAssetFitIntoLink)
  override val forTopology: Seq[ValidatorFunction] = Seq(pointLikeAssetFitIntoLink)

  //Pistemäinen asset ei asetu linkille

  private def pointLikeAssetFitIntoLink(assetType: Int, linkIds: Set[String]): returnResult = {
    Validators.returnValidationResult(0,PointAssetValidatorDao.assetWhichDoesNotFallInToLink(assetType,linkIds))
  }
}
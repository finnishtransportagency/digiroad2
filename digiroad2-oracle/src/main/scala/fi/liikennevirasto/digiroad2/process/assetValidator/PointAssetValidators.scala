package fi.liikennevirasto.digiroad2.process.assetValidator

import fi.liikennevirasto.digiroad2.dao.PointAssetValidatorDao

object PointAssetValidators extends Validators{
  override def forSamuutus: Seq[ValidatorFunction] = Seq(pointLikeAssetFitIntoLink)
  override def forTopology: Seq[ValidatorFunction] = Seq(pointLikeAssetFitIntoLink)

  //Pistemäinen asset ei asetu linkille

  private def pointLikeAssetFitIntoLink(assetType: Int, linkIds: Set[String]): returnResult = {
    Validators.returnValidationResult(0,PointAssetValidatorDao.assetWhichDoesNotFallInToLink(assetType,linkIds))
  }
}
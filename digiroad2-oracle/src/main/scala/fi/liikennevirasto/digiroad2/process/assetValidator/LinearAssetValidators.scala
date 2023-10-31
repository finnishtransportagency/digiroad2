package fi.liikennevirasto.digiroad2.process.assetValidator

import fi.liikennevirasto.digiroad2.asset.AssetTypeInfo
import fi.liikennevirasto.digiroad2.dao.ValidatorLinearDao

object LinearAssetValidators extends Validators{
  override val forSamuutus: Seq[ValidatorFunction] = Seq(assetFitIntoLink, assetDoesNotOverlap, assetPartsFullLenthIsSameAsLink)
  override val forTopology: Seq[ValidatorFunction] = Seq(assetFitIntoLink, assetDoesNotOverlap, assetsIsNotTooShort, assetPartsFullLenthIsSameAsLink)
  //1
  //a)
  private def assetFitIntoLink(assetType: Int, linkIds: Set[String]): returnResult = {
    Validators.returnValidationResult(0,ValidatorLinearDao.assetWhichDoesNotFallInFullyToLink(assetType,linkIds))
  }
  //b)
  private def assetDoesNotOverlap(assetType: Int, linkIds: Set[String]): returnResult = {
    Validators.returnValidationResult(0,ValidatorLinearDao.overFlappingAssets(assetType,linkIds))
  }
  //c)
  private def assetsIsNotTooShort(assetType: Int, linkIds: Set[String]): returnResult = {
    Validators.returnValidationResult(0,ValidatorLinearDao.assetTooShort(assetType,linkIds))
  }
  //2.
  // a)
  private def assetPartsFullLenthIsSameAsLink(assetType: Int, linkIds: Set[String]): returnResult = {
    if (AssetTypeInfo.roadLinkLongAssets.contains(assetType)) {
      Validators.returnValidationResult(0,ValidatorLinearDao.assetDoesNotFillLink(assetType,linkIds))
    } else None
  }
}
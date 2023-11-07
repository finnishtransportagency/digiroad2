package fi.liikennevirasto.digiroad2.process.assetValidator

import fi.liikennevirasto.digiroad2.asset.AssetTypeInfo
import fi.liikennevirasto.digiroad2.dao.ValidatorLinearDao
import fi.liikennevirasto.digiroad2.util.LogUtils
import org.slf4j.LoggerFactory

object LinearAssetValidators extends Validators{
  private val logger = LoggerFactory.getLogger(getClass)
  override def forSamuutus: Seq[ValidatorFunction] = Seq(assetFitIntoLink,  assetDoesNotOverlap, assetPartsFullLenthIsSameAsLink)
  override def forTopology: Seq[ValidatorFunction] = Seq(assetFitIntoLink, assetDoesNotOverlap, assetsIsNotTooShort, assetPartsFullLenthIsSameAsLink)
  
  private def assetFitIntoLink(assetType: Int, linkIds: Set[String]): returnResult = {
    LogUtils.time(logger,s"assetFitIntoLink, asset type: $assetType"){
      Validators.returnValidationResult("doesNotFallInFullyToLink",ValidatorLinearDao.assetWhichDoesNotFallInFullyToLink(assetType,linkIds))
    }
  }
  
  private def assetDoesNotOverlap(assetType: Int, linkIds: Set[String]): returnResult = {
    LogUtils.time(logger,s"assetDoesNotOverlap, asset type: $assetType"){
      Validators.returnValidationResult("overFlappingAsset",ValidatorLinearDao.overFlappingAssets(assetType,linkIds))
    }
  }
  
  private def assetsIsNotTooShort(assetType: Int, linkIds: Set[String]): returnResult = {
    LogUtils.time(logger,s"assetsIsNotTooShort, asset type: $assetType"){
      Validators.returnValidationResult("assetTooShort",ValidatorLinearDao.assetTooShort(assetType,linkIds))
    }
  }
  
  private def assetPartsFullLenthIsSameAsLink(assetType: Int, linkIds: Set[String]): returnResult = {
    LogUtils.time(logger,s"assetPartsFullLenthIsSameAsLink, asset type: $assetType"){
      if (AssetTypeInfo.roadLinkLongAssets.contains(assetType)) {
        Validators.returnValidationResult("assetDoesNotFillLink", ValidatorLinearDao.assetDoesNotFillLink(assetType, linkIds))
      } else None
    }
    
  }
}
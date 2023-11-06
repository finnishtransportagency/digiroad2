package fi.liikennevirasto.digiroad2.process.assetValidator

import fi.liikennevirasto.digiroad2.dao.PointAssetValidatorDao
import fi.liikennevirasto.digiroad2.util.LogUtils
import org.slf4j.LoggerFactory

object PointAssetValidators extends Validators{
  private val logger = LoggerFactory.getLogger(getClass)
  override def forSamuutus: Seq[ValidatorFunction] = Seq(pointLikeAssetFitIntoLink)
  override def forTopology: Seq[ValidatorFunction] = Seq(pointLikeAssetFitIntoLink)

  private def pointLikeAssetFitIntoLink(assetType: Int, linkIds: Set[String]): returnResult = {
    LogUtils.time(logger,s"pointLikeAssetFitIntoLink, asset type: $assetType"){
      Validators.returnValidationResult("pointLikeAssetDoesNotFallInToLink",PointAssetValidatorDao.assetWhichDoesNotFallInToLink(assetType,linkIds))
    }
    
  }
}
package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._

object PavedRoadFiller extends AssetFiller {

  //RoadLinks should contain only 1 pavement asset. If multiple are present, redundant assets should be expired
  override def adjustAssets(roadLink: RoadLinkForFillTopology, linearAssets: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val (filteredAssets, updatedChangeSet) = (linearAssets.size > 1) match {
      case true =>
        val (redundantAssets, validAssets) = linearAssets.partition(asset => isRedundantUnknownPavementAsset(asset, roadLink))
        (validAssets, changeSet.copy(expiredAssetIds = redundantAssets.map(_.id).toSet))
      case false => (linearAssets, changeSet)
    }
    super.adjustAssets(roadLink, filteredAssets, updatedChangeSet)
  }

  /** *
   * Filters out Pavement assets that do not fill the whole RoadLink and have an Unknown value
   *
   * @param asset
   * @param roadLink
   * @return
   */
  def isRedundantUnknownPavementAsset(asset: PieceWiseLinearAsset, roadLink: RoadLinkForFillTopology): Boolean = {
    val assetLength = asset.endMeasure - asset.startMeasure
    val linkLength = roadLink.length
    val pavementClass = PavementClass.extractPavementClass(asset.value).getOrElse(DynamicPropertyValue(""))
    (assetLength - linkLength).abs > MaxAllowedMValueError &&
      PavementClass.applyFromDynamicPropertyValue(pavementClass.value) == PavementClass.Unknown match {
      case true =>
        logger.info(s"LinearAsset ${asset.id} removed from process due to having Unknown PavementClass and not filling whole RoadLink")
        true
      case _ =>
        false
    }
  }
}
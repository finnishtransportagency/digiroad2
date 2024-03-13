package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._

object PavedRoadFiller extends AssetFiller {

  private def expireReplacedPavementAssets(assetsIdsToExpire: Set[Long], changeSet: ChangeSet) = {
    if (assetsIdsToExpire.nonEmpty) {
      logger.info(s"LinearAssets ${assetsIdsToExpire.mkString(", ")} expired due to Unknown PavementClass and being replaced by asset with known PavementClass")
    }
    changeSet.copy(expiredAssetIds = changeSet.expiredAssetIds ++ assetsIdsToExpire)
  }

  /**
   * Filter out assets with Unknown or Missing PavementClass if any other PavementClass is present.
   * If all assets have Unknown PavementClass, do nothing.
   * @param roadLink  which we are processing
   * @param linearAssets  assets on link
   * @param changeSet record of changes for final saving stage
   *  @return assets and changeSet
   */
  override def adjustAssets(roadLink: RoadLinkForFillTopology, linearAssets: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val(replaceablePavementAssets, retainablePavementAssets) = linearAssets.partition(asset => PavementClass.isReplaceablePavementClass(asset.value))
    val replaceablePavementAssetIds = replaceablePavementAssets.map(_.id).toSet
    val (filteredAssets, updatedChangeSet) = replaceablePavementAssetIds.size match {
      case size if size == linearAssets.size => (linearAssets, changeSet)
      case _ =>
        (retainablePavementAssets, expireReplacedPavementAssets(replaceablePavementAssetIds, changeSet))
    }
    super.adjustAssets(roadLink, filteredAssets, updatedChangeSet)
  }
}
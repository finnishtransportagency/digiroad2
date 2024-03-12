package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._

object PavedRoadFiller extends AssetFiller {

  private def expireUnknownPavementAssets(assetsToExpire: Seq[PieceWiseLinearAsset], changeSet: ChangeSet) = {
    val expiredAssetIds = assetsToExpire.map(_.id).toSet
    if (expiredAssetIds.nonEmpty) {
      logger.info(s"LinearAssets ${expiredAssetIds.mkString(", ")} expired due to Unknown PavementClass and being replaced by asset with known PavementClass")
    }
    changeSet.copy(expiredAssetIds = changeSet.expiredAssetIds ++ assetsToExpire.map(_.id).toSet)
  }

  /**
   * Filter out assets with Unknown PavementClass if any other PavementClass is present.
   * If all assets have Unknown PavementClass, do nothing.
   * @param roadLink  which we are processing
   * @param linearAssets  assets on link
   * @param changeSet record of changes for final saving stage
   *  @return assets and changeSet
   */
  override def adjustAssets(roadLink: RoadLinkForFillTopology, linearAssets: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val(unknownPavementAssets, knownPavementAssets) = linearAssets.partition(asset => PavementClass.hasUnknownPavementClass(asset.value))
    val (filteredAssets, updatedChangeSet) = unknownPavementAssets.size match {
      case size if size == linearAssets.size => (linearAssets, changeSet)
      case _ =>
        (knownPavementAssets, expireUnknownPavementAssets(unknownPavementAssets, changeSet))
    }
    super.adjustAssets(roadLink, filteredAssets, updatedChangeSet)
  }
}
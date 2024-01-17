package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._

object PavedRoadFiller extends AssetFiller {

  //RoadLinks should contain only 1 pavement asset. If multiple are present, redundant assets should be ignored from the process
  override def adjustAssets(roadLink: RoadLinkForFillTopology, linearAssets: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
    val filteredAssets = (linearAssets.size > 1) match {
      case true => linearAssets.filterNot(asset => isRedundantUnknownPavementAsset(asset, roadLink))
      case false => linearAssets
    }
    super.adjustAssets(roadLink, filteredAssets, changeSet)
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
    val pavementClass = extractPavementClass(asset.value).getOrElse(DynamicPropertyValue(""))
    (assetLength - linkLength).abs > MaxAllowedMValueError &&
      PavementClass.applyFromDynamicPropertyValue(pavementClass.value) == PavementClass.Unknown match {
      case true =>
        logger.info(s"LinearAsset ${asset.id} removed from process due to having Unknown PavementClass and not filling whole RoadLink")
        true
      case _ =>
        false
    }
  }

  def extractPavementClass(assetValue: Option[Value]): Option[DynamicPropertyValue] = {
    assetValue.collect {
      case dynamicValue: DynamicValue =>
        dynamicValue.value.properties.collectFirst {
          case property if property.publicId == "paallysteluokka" && property.values.size > 0 =>
            property.values.head
        }
    }.flatten
  }
}
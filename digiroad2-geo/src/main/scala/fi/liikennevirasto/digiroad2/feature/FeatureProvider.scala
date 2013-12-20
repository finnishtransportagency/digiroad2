package fi.liikennevirasto.digiroad2.feature

import fi.liikennevirasto.digiroad2.mtk.MtkRoadLink

trait FeatureProvider {
  def getAssetTypes: Seq[AssetType]
  def getAssets(assetTypeId: Long, municipalityNumber: Option[Long] = None, assetId: Option[Long] = None): Seq[Asset]
  def updateAssetProperty(assetId: Long, propertyId: String, propertyValues: Seq[PropertyValue])
  def deleteAssetProperty(assetId: Long, propertyId: String)
  def getEnumeratedPropertyValues(assetTypeId: Long): Seq[EnumeratedPropertyValue]
  def updateAssetLocation(asset: Asset): Asset
  def getRoadLinks(municipalityNumber: Option[Int] = None): Seq[RoadLink]
  def getImage(imageId: Long): Array[Byte]
  def updateRoadLinks(roadlinks: Seq[MtkRoadLink]): Unit
}

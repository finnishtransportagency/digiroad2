package fi.liikennevirasto.digiroad2.feature

trait FeatureProvider {
  def getAssetTypes: Seq[AssetType]
  def getAssets(assetTypeId: Long, municipalityNumber: Option[Long] = None, assetId: Option[Long] = None): Seq[Asset]
  def updateAssetProperty(assetId: Long, propertyId: Long, propertyValues: Seq[PropertyValue])
  def deleteAssetProperty(assetId: Long, propertyId: Long)
  def getEnumeratedPropertyValues(assetTypeId: Long): Seq[EnumeratedPropertyValue]
  def updateAssetLocation(asset: Asset): Asset
  def getRoadLinks(municipalityNumber: Option[Int] = None): Seq[RoadLink]
  def getImage(imageId: Long): Array[Byte]
}

package fi.liikennevirasto.digiroad2.feature

class NoOpFeatureProvider extends FeatureProvider {
  def updateAssetProperty(assetId: Long, propertyId: String, propertyValues: Seq[PropertyValue]) {}
  def deleteAssetProperty(assetId: Long, propertyId: String) {}
  def getRoadLinks(municipalityNumber: Option[Int]): Seq[RoadLink] = List()
  def getAssets(assetTypeId: Long, municipalityNumber: Option[Long], assetId: Option[Long]): Seq[Asset] = List()
  def getAssetTypes = List()
  def getEnumeratedPropertyValues(assetTypeId: Long): Seq[EnumeratedPropertyValue] = List()
  def updateAssetLocation(asset: Asset): Asset = asset
  def getImage(imageId: Long): Array[Byte] = new Array[Byte](0)
}
package fi.liikennevirasto.digiroad2.feature

trait FeatureProvider {
  def getBusStops(municipalityNumber: Option[Int] = None): Seq[BusStop]
  def getAssetTypes: Seq[AssetType]
  def getAssets(assetTypeId: Long, municipalityNumber: Option[Long] = None, assetId: Option[Long] = None): Seq[Asset]
  def updateAssetProperty(assetId: Long, propertyId: String, propertyValues: Seq[PropertyValue])
  def deleteAssetProperty(assetId: Long, propertyId: String)
  def getEnumeratedPropertyValues(assetTypeId: Long): Seq[EnumeratedPropertyValue]
  def updateBusStop(busStop: BusStop): BusStop
  def updateAssetLocation(asset: Asset): Asset
  def getRoadLinks(municipalityNumber: Option[Int] = None): Seq[RoadLink]
}

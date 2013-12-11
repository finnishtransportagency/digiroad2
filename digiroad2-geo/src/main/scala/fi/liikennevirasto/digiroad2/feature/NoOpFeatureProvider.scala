package fi.liikennevirasto.digiroad2.feature

class NoOpFeatureProvider extends FeatureProvider {
  def getBusStops(municipalityNumber: Option[Int]): Seq[BusStop] = { List() }
  def updateAssetProperty(assetId: Long, propertyId: Long, propertyValues: Seq[PropertyValue]) {}
  def deleteAssetProperty(assetId: Long, propertyId: Long) {}
  def updateBusStop(busStop: BusStop): BusStop = busStop
  def getRoadLinks(municipalityNumber: Option[Int]): Seq[RoadLink] = List()
  def getAssets(assetTypeId: Long, municipalityNumber: Option[Long], assetId: Option[Long]): Seq[Asset] = List()
  def getAssetTypes = List()
  def getEnumeratedPropertyValues(assetTypeId: Long): Seq[EnumeratedPropertyValue] = List()
  def updateAssetLocation(asset: Asset): Asset = asset
}
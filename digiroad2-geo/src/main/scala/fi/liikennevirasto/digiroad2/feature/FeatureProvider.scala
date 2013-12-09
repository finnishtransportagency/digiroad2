package fi.liikennevirasto.digiroad2.feature

trait FeatureProvider {
  def getBusStops(municipalityNumber: Option[Int] = None): Seq[BusStop]
  def getAssetTypes: Seq[AssetType]
  def getAssets(assetTypeId: Long, municipalityNumber: Option[Long] = None): Seq[Asset]
  def updateAssetProperty(assetId: Long, propertyId: Long, propertyValues: Seq[PropertyValue])
  def updateBusStop(busStop: BusStop): BusStop
  def getRoadLinks(municipalityNumber: Option[Int] = None): Seq[RoadLink]
}
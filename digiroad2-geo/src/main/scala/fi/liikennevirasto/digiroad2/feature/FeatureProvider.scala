package fi.liikennevirasto.digiroad2.feature

trait FeatureProvider {
  def getBusStops(municipalityNumber: Option[Int] = None): Seq[BusStop]
  def getAssets(assetTypeId: Long, municipalityNumber: Option[Long] = None): Seq[Asset] // TODO return Assets
  def updateBusStop(busStop: BusStop): BusStop
  def getRoadLinks(municipalityNumber: Option[Int] = None): Seq[RoadLink]
}
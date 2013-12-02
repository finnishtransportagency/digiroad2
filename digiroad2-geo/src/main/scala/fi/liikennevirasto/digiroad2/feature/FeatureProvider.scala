package fi.liikennevirasto.digiroad2.feature

trait FeatureProvider {
  def getBusStops(municipalityNumber: Option[Int] = None): Seq[BusStop]
  def updateBusStop(busStop: BusStop): BusStop
  def getRoadLinks(municipalityNumber: Option[Int] = None): Seq[RoadLink]
}
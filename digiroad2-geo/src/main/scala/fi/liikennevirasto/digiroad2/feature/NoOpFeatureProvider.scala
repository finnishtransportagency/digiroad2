package fi.liikennevirasto.digiroad2.feature

class NoOpFeatureProvider extends FeatureProvider {
  def getBusStops(municipalityNumber: Option[Int]): Seq[BusStop] = { List() }
  def updateBusStop(busStop: BusStop): BusStop = busStop
  def getRoadLinks(municipalityNumber: Option[Int]): Seq[RoadLink] = List()
}
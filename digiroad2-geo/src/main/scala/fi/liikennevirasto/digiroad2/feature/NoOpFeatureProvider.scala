package fi.liikennevirasto.digiroad2.feature

class NoOpFeatureProvider extends FeatureProvider {
  def getBusStops(): Seq[BusStop] = { List() }
  def updateBusStop(busStop: BusStop): BusStop = busStop
  def getRoadLinks(): Seq[RoadLink] = List()
}
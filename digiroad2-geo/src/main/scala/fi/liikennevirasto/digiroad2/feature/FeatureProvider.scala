package fi.liikennevirasto.digiroad2.feature

trait FeatureProvider {
  def getBusStops(): Seq[BusStop]
  def updateBusStop(busStop: BusStop): BusStop
  def getRoadLinks(): Seq[RoadLink]
}
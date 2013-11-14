package fi.liikennevirasto.digiroad2.feature

class NoOpFeatureProvider extends FeatureProvider {
  def getBusStops(): Seq[BusStop] = { List() }
}
package fi.liikennevirasto.digiroad2.feature

trait FeatureProvider {
  def getBusStops(): Seq[BusStop]
}
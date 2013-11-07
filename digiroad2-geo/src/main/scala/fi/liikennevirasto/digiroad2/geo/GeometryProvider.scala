package fi.liikennevirasto.digiroad2.geo

trait GeometryProvider {
  def loadGeometry(dbGeometry: Object): Geometry
}
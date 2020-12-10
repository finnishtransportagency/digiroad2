package fi.liikennevirasto.digiroad2

  case class FeatureCollection(`type`: String, features: List[Feature], crs: Option[Map[String, Any]] = None)
  case class Feature(`type`: String, geometry: Geometry, properties: Map[String, String])
  case class Geometry(`type`: String, coordinates: List[List[Double]])
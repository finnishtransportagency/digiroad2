package fi.liikennevirasto.digiroad2.client.vvh
import com.vividsolutions.jts.geom.Polygon
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, LinkGeomSource}

sealed case class FeatureCollection(`type`: String, features: List[Feature], crs: Option[Map[String, Any]] = None)
sealed case class Feature(`type`: String, geometry: Geometry, properties: Map[String, String])
sealed case class Geometry(`type`: String, coordinates: List[List[Double]])

trait MtkOperation extends LinkOperationsAbstract{
  type LinkType
  type Content = FeatureCollection
  type IdType = String
  protected val linkGeomSource: LinkGeomSource
  protected def restApiEndPoint: String
  protected def serviceName: String
  protected val disableGeometry: Boolean
  protected def mapFields(content:Content, url: String): Either[List[Map[String, Any]], LinkOperationError]  = ???
  protected def defaultOutFields(): String = ???
  protected def extractFeature(feature: Map[String, Any]): LinkType = ???

  override protected def queryByMunicipalitiesAndBounds(bounds: BoundingRectangle, roadNumbers: Seq[(Int, Int)],
                                                        municipalities: Set[Int] = Set(),
                                                        includeAllPublicRoads: Boolean = false): Seq[RoadlinkFetched] = ???

  override protected def queryByMunicipalitiesAndBounds(bounds: BoundingRectangle, municipalities: Set[Int],
                                                        filter: Option[String]): Seq[LinkType] = ???

  override protected def queryByMunicipality(municipality: Int, filter: Option[String] = None): Seq[LinkType] = ???

  override protected def queryByPolygons(polygon: Polygon): Seq[LinkType] = ???

  override protected def queryLinksIdByPolygons(polygon: Polygon): Seq[Long] = ???

  override protected def queryByLinkIds[T](linkIds: Set[String],
                                           fieldSelection: Option[String],
                                           fetchGeometry: Boolean,
                                           resultTransition: (Map[String, Any], List[List[Double]]) => T,
                                           filter: Set[Long] => String): Seq[T] = ???

  override protected def queryByMunicipalitiesAndBounds(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[LinkType] = {
    queryByMunicipalitiesAndBounds(bounds, municipalities, None)
  }
}

package fi.liikennevirasto.digiroad2.util


import java.util.Properties
import com.vividsolutions.jts.geom.{Coordinate, GeometryFactory, Polygon}
import org.scalatest.{FunSuite, Matchers}
import org.geotools.geometry.jts.GeometryBuilder
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.{Point, VVHClient}
import com.vividsolutions.jts.io.WKTReader


class PolygonToolsSpec extends FunSuite with Matchers {
  val polygonTools= new PolygonTools()
  val geomFact= new GeometryFactory()
  val geomBuilder = new GeometryBuilder(geomFact)
  val wKTParser = new WKTReader()
  val vvhClient = new VVHClient(properties.getProperty("digiroad2.VVHRestApiEndPoint"))

  lazy val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }
  test("Polygon to string test") {
    val poly1=geomBuilder.polygon(24.2,60.5, 24.8,60.5, 24.8,59, 24.2,59)
    val polyString = vvhClient.stringifyGeometry(poly1)
    polyString should be ("{rings:[[[24.2,60.5],[24.8,60.5],[24.8,59.0],[24.2,59.0]]]}")
  }

  test("Polygon & BoundingBox intersection test") {
    val boundingBox= BoundingRectangle(Point(24,60), Point(25,61))
    val poly1=geomBuilder.polygon(24.2,60.5, 24.8,60.5, 24.8,59, 24.2,59)
    val interceptedPolygon= polygonTools.geometryInterceptorToBoundingBox(poly1,boundingBox).head
    interceptedPolygon.getCoordinates.contains(new Coordinate(24.2,60.5)) should be (true)
    interceptedPolygon.getCoordinates.contains(new Coordinate(24.2,60)) should be (true)
    interceptedPolygon.getCoordinates.contains(new Coordinate(24.8,60)) should be (true)
    interceptedPolygon.getCoordinates.contains(new Coordinate(24.8,60.5)) should be (true)
  }

  test("Multipolygon & BoundingBox intersection test") {
    val boundingBox= BoundingRectangle(Point(0,0), Point(100,100))
    val poly1= wKTParser.read("MULTIPOLYGON(((20 80,40 80,40 60,20 60, 20 80)),((70 80, 90 80, 90 60, 70 60, 70 80)))")
    val interceptedPolygonListSize= polygonTools.geometryInterceptorToBoundingBox(poly1,boundingBox).length
    interceptedPolygonListSize should be (2)
  }

  test("Polygon & BoundingBox intersection no-common points test") {
    val boundingBox= BoundingRectangle(Point(240,600), Point(250,610))
    val poly1=geomBuilder.polygon(24.2,60.5, 24.8,60.5, 24.8,59, 24.2,59)
    val interceptedPolygon= polygonTools.geometryInterceptorToBoundingBox(poly1,boundingBox).head
    interceptedPolygon.isEmpty should be (true)
  }

  test("Polygon & BoundingBox intersects common line, but has no common area") {
    val bounds= BoundingRectangle(Point(564000, 6930000),Point(566000, 6931000))
    val poly1=geomBuilder.polygon(564000, 6930000, 568000, 6930000, 568000, 6920000, 564000, 6920000)
    val interceptedPolygon= polygonTools.geometryInterceptorToBoundingBox(poly1,bounds)
    interceptedPolygon.isEmpty should be (true)
  }
}

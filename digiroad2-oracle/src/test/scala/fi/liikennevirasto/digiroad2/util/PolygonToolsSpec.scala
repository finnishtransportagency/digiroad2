package fi.liikennevirasto.digiroad2.util


import com.vividsolutions.jts.geom.{Coordinate, GeometryFactory}
import org.scalatest.{FunSuite, Matchers}
import org.geotools.geometry.jts.GeometryBuilder
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.Point


class PolygonToolsSpec extends FunSuite with Matchers {
  val polygonTools= new PolygonTools()
  val geomFact= new GeometryFactory()
  val geomBuilder = new GeometryBuilder(geomFact)

  test("Simple polygon to string test") {
    val poly1=geomBuilder.polygon(24.2,60.5, 24.8,60.5, 24.8,59, 24.2,59)
    val polyString =polygonTools.stringifyPolygonForVVHClient(poly1)
    polyString should be ("{rings:[[[24.2,60.5],[24.8,60.5],[24.8,59.0],[24.2,59.0]]}")
  }

  test("Simple empty polygon test") {
    val poly1=geomBuilder.polygon()
    val polyString =polygonTools.stringifyPolygonForVVHClient(poly1)
    polyString should be ("")
  }

  test("Polygon & BoundingBox intersection test") {
    val boundingbox= BoundingRectangle(Point(24,60), Point(25,61))
    val poly1=geomBuilder.polygon(24.2,60.5, 24.8,60.5, 24.8,59, 24.2,59)
    val interceptedPolygon= polygonTools.polygonInterceptorToBoundingBox(poly1,boundingbox)
    interceptedPolygon.getCoordinates.contains(new Coordinate(24.2,60.5)) should be (true)
    interceptedPolygon.getCoordinates.contains(new Coordinate(24.2,60)) should be (true)
    interceptedPolygon.getCoordinates.contains(new Coordinate(24.8,60)) should be (true)
    interceptedPolygon.getCoordinates.contains(new Coordinate(24.8,60.5)) should be (true)
  }

  test("Polygon & BoundingBox intersection no-common points test") {
    val boundingbox= BoundingRectangle(Point(240,600), Point(250,610))
    val poly1=geomBuilder.polygon(24.2,60.5, 24.8,60.5, 24.8,59, 24.2,59)
    val interceptedPolygon= polygonTools.polygonInterceptorToBoundingBox(poly1,boundingbox)
    interceptedPolygon.isEmpty should be (true)
  }

  test("Polygon & BoundingBox intersects common line, but has no common area") {
    val bounds= BoundingRectangle(Point(564000, 6930000),Point(566000, 6931000))
    val poly1=geomBuilder.polygon(564000, 6930000, 568000, 6930000, 568000, 6920000, 564000, 6920000)
    val interceptedPolygon= polygonTools.polygonInterceptorToBoundingBox(poly1,bounds)
    interceptedPolygon.isEmpty should be (true)
  }


  test("Polygon & BoundingBox intersects common Point, but has no common area") {
    val bounds= BoundingRectangle(Point(564000, 6930000),Point(566000, 6931000))
    val poly1=geomBuilder.polygon(564000, 6930000, 568000, 6920000, 568000, 6920000, 564000, 6920000)
    val interceptedPolygon= polygonTools.polygonInterceptorToBoundingBox(poly1,bounds)
    interceptedPolygon.isEmpty should be (true)
  }

}

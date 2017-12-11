package fi.liikennevirasto.digiroad2.util


import java.util.Properties

import com.vividsolutions.jts.geom.{Coordinate, GeometryFactory, Polygon}
import org.scalatest.{FunSuite, Matchers}
import org.geotools.geometry.jts.GeometryBuilder
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.Point
import com.vividsolutions.jts.io.WKTReader
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.service.linearasset.Measures


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
    val polyString = vvhClient.roadLinkData.stringifyPolygonGeometry(poly1)
    polyString should be ("{rings:[[[24.2,60.5],[24.8,60.5],[24.8,59.0],[24.2,59.0]]]}")
  }

  test("Polygon & BoundingBox intersection test") {
    val boundingBox= BoundingRectangle(Point(24,60), Point(25,61))
    val poly1=geomBuilder.polygon(24.2,60.5, 24.8,60.5, 24.8,59, 24.2,59)
    val interceptedPolygon= polygonTools.geometryInterceptorToBoundingBox(Seq(poly1),boundingBox).head
    interceptedPolygon.getCoordinates.contains(new Coordinate(24.2,60.5)) should be (true)
    interceptedPolygon.getCoordinates.contains(new Coordinate(24.2,60)) should be (true)
    interceptedPolygon.getCoordinates.contains(new Coordinate(24.8,60)) should be (true)
    interceptedPolygon.getCoordinates.contains(new Coordinate(24.8,60.5)) should be (true)
  }

  test("Multipolygon & BoundingBox intersection test") {
    val boundingBox= BoundingRectangle(Point(0,0), Point(100,100))
    val poly1= wKTParser.read("MULTIPOLYGON(((20 80,40 80,40 60,20 60, 20 80)),((70 80, 90 80, 90 60, 70 60, 70 80)))")
    val interceptedPolygonListSize= polygonTools.geometryInterceptorToBoundingBox(Seq(poly1),boundingBox).length
    interceptedPolygonListSize should be (2)
  }

  test("Polygon & BoundingBox intersection no-common points test") {
    val boundingBox= BoundingRectangle(Point(240,600), Point(250,610))
    val poly1=geomBuilder.polygon(24.2,60.5, 24.8,60.5, 24.8,59, 24.2,59)
    val interceptedPolygon= polygonTools.geometryInterceptorToBoundingBox(Seq(poly1),boundingBox)
    interceptedPolygon.isEmpty should be (true)
  }

  test("Polygon & BoundingBox intersects common line, but has no common area") {
    val bounds= BoundingRectangle(Point(564000, 6930000),Point(566000, 6931000))
    val poly1=geomBuilder.polygon(564000, 6930000, 568000, 6930000, 568000, 6920000, 564000, 6920000)
    val interceptedPolygon= polygonTools.geometryInterceptorToBoundingBox(Seq(poly1),bounds)
    interceptedPolygon.isEmpty should be (true)
  }

  test("Polygons & BoundingBox intersects with one common area") {
    val bounds= BoundingRectangle(Point(5, 25),Point(15, 5))
    val poly1=geomBuilder.polygon(10, 10, 20, 10, 20, 20, 10, 20)
    val poly2=geomBuilder.polygon(30, 10, 40, 10, 40, 20, 30, 20)
    val interceptedPolygon= polygonTools.geometryInterceptorToBoundingBox(Seq(poly1, poly2),bounds)
    interceptedPolygon.size should be (1)
  }

  test("Polygons & BoundingBox intersects with both common area") {
    val bounds= BoundingRectangle(Point(15, 25),Point(35, 5))
    val poly1=geomBuilder.polygon(10, 10, 20, 10, 20, 20, 10, 20)
    val poly2=geomBuilder.polygon(30, 10, 40, 10, 40, 20, 30, 20)
    val interceptedPolygon= polygonTools.geometryInterceptorToBoundingBox(Seq(poly1, poly2),bounds)
    interceptedPolygon.size should be (2)
  }

  test("Polygons & BoundingBox intersects without common area") {
    val bounds= BoundingRectangle(Point(5, 5),Point(0, 5))
    val poly1=geomBuilder.polygon(10, 10, 20, 10, 20, 20, 10, 20)
    val poly2=geomBuilder.polygon(30, 10, 40, 10, 40, 20, 30, 20)
    val interceptedPolygon= polygonTools.geometryInterceptorToBoundingBox(Seq(poly1, poly2),bounds)
    interceptedPolygon.isEmpty should be (true)
  }

  test("asset geometry exist in polygon areas") {
    val result = polygonTools.getAreaByGeometry(Seq(Point(550000, 6806000) ,Point(560000, 6806000)), Measures(0, 10000), None)
    result should be (6)
  }

  test("asset geometry intersect polygon areas") {
    val result1 = polygonTools.getAreaByGeometry(Seq(Point(281000,6690000), Point(374000,6690000)), Measures(0, 430000), Some(Seq(1)))
    val result2 = polygonTools.getAreaByGeometry(Seq(Point(281000,6690000), Point(374000,6690000)), Measures(0, 430000), Some(Seq(2)))
    result1 should be (1)
    result2 should be (2)
  }

  test("asset geometry not found in polygon areas") {
    val thrown = intercept[IllegalArgumentException] {
      polygonTools.getAreaByGeometry(Seq(Point(282000, 6623686) ,Point(287000, 6623686)), Measures(0, 5000), None)
    }
    thrown.getMessage should be ("Geometry not found in polygon areas")

  }


}

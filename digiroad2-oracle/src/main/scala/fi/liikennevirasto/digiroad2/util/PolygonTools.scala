package fi.liikennevirasto.digiroad2.util

import java.util.Properties

import com.vividsolutions.jts.geom._
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.user.UserProvider
import org.geotools.geometry.jts.GeometryBuilder
import fi.liikennevirasto.digiroad2.Point
import com.vividsolutions.jts.io.WKTReader
import scala.collection.mutable.ListBuffer

/**
  * Tools related to polygons
  */
class PolygonTools {
  val geomFact = new GeometryFactory()
  val geomBuilder = new GeometryBuilder(geomFact)
  lazy val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }
  lazy val userProvider: UserProvider = {
    Class.forName(properties.getProperty("digiroad2.userProvider")).newInstance().asInstanceOf[UserProvider]
  }

  /**
    *
    * @param geometry jts Geometry
    * @param boundingBox  BoundingRectangle
    * @return returns Sequence of JTS Polygons that are with in bounding box
    */
  def geometryInterceptorToBoundingBox(geometry: Geometry, boundingBox: BoundingRectangle): Seq[Polygon] = {
    val leftBottomP = boundingBox.leftBottom
    val rightTopP = boundingBox.rightTop
    val leftTopP = Point(leftBottomP.x, rightTopP.y)
    val rightBottom = Point(rightTopP.x, leftBottomP.y)
    val BoundingBoxAsPoly = geomBuilder.polygon(leftTopP.x, leftTopP.y, rightTopP.x, rightTopP.y, rightBottom.x, rightBottom.y, leftBottomP.x, leftBottomP.y)
    val intersectionGeometry = geometry.intersection(BoundingBoxAsPoly)
    if (intersectionGeometry.getGeometryType.toLowerCase.startsWith("polygon")) {
      Seq(intersectionGeometry.asInstanceOf[Polygon])
    } else if (intersectionGeometry.isEmpty) {
      Seq.empty[Polygon]
    } else if (intersectionGeometry.getGeometryType.toLowerCase.contains("multipolygon")) {
      multiPolygonToPolygonSeq(intersectionGeometry.asInstanceOf[MultiPolygon])
    } else
      Seq.empty[Polygon]
  }
  /**
    *
    * @param geometrySeq sequence of polygons to be converted to strings
    * @return sequence of strings compatible with VVH polygon query
    */
  def stringifyGeometryForVVHClient(geometrySeq: Seq[Polygon]): Seq[String] = {
    var stringPolygonList = ListBuffer.empty[String]
    for (geometry <- geometrySeq) {
      var polygonString: String = "{rings:[["
      if (geometry.getCoordinates.length > 0) {
        for (point <- geometry.getCoordinates.dropRight(1)) {
          // drop removes duplicates
          polygonString += "[" + point.x + "," + point.y + "],"
        }
        polygonString = polygonString.dropRight(1) + "]]}"
        stringPolygonList += polygonString
      }
    }
    stringPolygonList
  }

  def getAreaGeometry(areaId: Int): Geometry = {
    val wKTParser = new WKTReader()
    val areaChoose= new getServiceArea()
    wKTParser.read(areaChoose.getArea(areaId))
  }

  private def multiPolygonToPolygonSeq (multiPoly: MultiPolygon): Seq[Polygon] ={
    var geomCounter=multiPoly.getNumGeometries
    var  listPolygons= ListBuffer.empty[Polygon]
    while (geomCounter>0)
    {
      val poly=multiPoly.getGeometryN(geomCounter-1)
      if (poly.getGeometryType=="Polygon") {
        listPolygons += poly.asInstanceOf[Polygon]
      }
      geomCounter-=1
    }
    listPolygons
  }
}
package fi.liikennevirasto.digiroad2.util
import com.vividsolutions.jts.geom._
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import org.geotools.geometry.jts.GeometryBuilder
import fi.liikennevirasto.digiroad2.Point

/**
  * Tools related to polygons
  */
class PolygonTools {
  val geomFact = new GeometryFactory()
  val geomBuilder = new GeometryBuilder(geomFact)
  /**
    *
    * @param polygon     jts.geom Polygon
    * @param boundingBox BoundingRectangle
    * @return Returns polygon which have common area with BoundingRectangle and given polygon.
    *         returns empty polygon if there is no common area.
    */
  def polygonInterceptorToBoundingBox(polygon: Polygon, boundingBox: BoundingRectangle): Polygon = {
    val leftBottomP = boundingBox.leftBottom
    val rightTopP = boundingBox.rightTop
    val leftTopP = Point(leftBottomP.x, rightTopP.y)
    val rightBottom = Point(rightTopP.x, leftBottomP.y)
    val BoundingBoxAsPoly = geomBuilder.polygon(leftTopP.x, leftTopP.y, rightTopP.x, rightTopP.y, rightBottom.x, rightBottom.y, leftBottomP.x, leftBottomP.y)
    val intersectionGeometry=polygon.intersection(BoundingBoxAsPoly)
    if (intersectionGeometry.getGeometryType.toLowerCase.contains("polygon"))
    {//checks that result is polygon
      geomFact.createPolygon(intersectionGeometry.getCoordinates)
    }
    else
    { // when intersection has common points, but no common area we return empty polygon
      geomBuilder.polygon()
    }
  }

  /**
    *
    * @param polygon polygon to be converted
    * @return returns string in format that VVH accepts in URL
    */
  def stringifyPolygonForVVHClient(polygon: Polygon): String = {
    var polygonString:String = "{rings:[["
    if (polygon.getCoordinates.length>0)
    {
      for (point <- polygon.getCoordinates.dropRight(1)) { // drop removes duplicates
        polygonString+= "[" + point.x + "," + point.y + "],"
      }
      polygonString= polygonString.dropRight(1) + "]}"
      polygonString
    } else
      ""
  }

  def getAreaPolygonFromDatabase(areaId : Int): Polygon = {
  //TODO Conversion method to DB polygon data now returns empty polygon
    geomBuilder.polygon()
  }
}
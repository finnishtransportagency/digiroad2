package fi.liikennevirasto.digiroad2.util

import java.util.Properties

import com.vividsolutions.jts.geom._
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.user.UserProvider
import org.geotools.geometry.jts.GeometryBuilder
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import com.vividsolutions.jts.io.WKTReader
import fi.liikennevirasto.digiroad2.service.linearasset.Measures

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
    * @param geometries jts Geometries
    * @param boundingBox  BoundingRectangle
    * @return returns Sequence of JTS Polygons that are with in bounding box
    */
  def geometryInterceptorToBoundingBox(geometries: Seq[Geometry], boundingBox: BoundingRectangle): Seq[Polygon] = {
    val leftBottomP = boundingBox.leftBottom
    val rightTopP = boundingBox.rightTop
    val leftTopP = Point(leftBottomP.x, rightTopP.y)
    val rightBottom = Point(rightTopP.x, leftBottomP.y)
    val BoundingBoxAsPoly = geomBuilder.polygon(leftTopP.x, leftTopP.y, rightTopP.x, rightTopP.y, rightBottom.x, rightBottom.y, leftBottomP.x, leftBottomP.y)

    geometries.flatMap{
      geometry =>
      val intersectionGeometry = geometry.intersection(BoundingBoxAsPoly)
      if (intersectionGeometry.isInstanceOf[Polygon]) {
        polygonToPolygonSeq(intersectionGeometry.asInstanceOf[Polygon])
      } else if (intersectionGeometry.isInstanceOf[MultiPolygon]) {
        multiPolygonToPolygonSeq(intersectionGeometry.asInstanceOf[MultiPolygon])
      } else
        Seq.empty[Polygon]
    }
  }

  def getPolygonByArea(areaId: Int): Seq[Polygon] = {
    val geometry = getAreaGeometry(areaId)

    val polygon = geometry match {
      case _ if geometry.getGeometryType.toLowerCase.startsWith("polygon") =>
        Seq(geometry.asInstanceOf[Polygon])
      case _ if geometry.getGeometryType.toLowerCase.startsWith("multipolygon") =>
        multiPolygonToPolygonSeq(geometry.asInstanceOf[MultiPolygon])
      case _ => Seq.empty[Polygon]
    }
    polygon
  }

  def getAreaByGeometry(geometry: Seq[Point], measure: Measures, areaOpt: Option[Seq[Int]]): Int  = {
    val assetGeom = GeometryUtils.truncateGeometry2D(geometry, measure.startMeasure, measure.endMeasure)
    val lineStringGeom = geomBuilder.lineString(assetGeom.flatMap(p => Seq(p.x, p.y)):_*)

    val area = areaOpt match {
      case Some(areaVal) => areaVal
      case _ => Seq(1,2,3,4,5,6,7,8,9,10,11,12)
    }

   area.find{ area => getPolygonByArea(area).exists(poly => poly.intersects(lineStringGeom))
   }.getOrElse(throw new IllegalArgumentException("Geometry not found in polygon areas"))
  }

  def getAreasGeometries(areadIds: Set[Int]): Seq[Geometry] ={
    areadIds.map(getAreaGeometry).toSeq
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

  private def polygonToPolygonSeq(polygon: Polygon) : Seq[Polygon] = {
    def isPolygonEmpty(polygon: Polygon) = {
      polygon.getNumPoints() > 0
    }

    if(isPolygonEmpty(polygon))
      Seq(polygon)
    else
      Seq.empty[Polygon]
  }
}
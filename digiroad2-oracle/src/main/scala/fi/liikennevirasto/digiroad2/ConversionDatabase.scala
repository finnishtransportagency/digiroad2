package fi.liikennevirasto.digiroad2

import net.postgis.jdbc.PGgeometry
import net.postgis.jdbc.geometry.GeometryBuilder
import org.postgresql.util.PGobject
import slick.jdbc.{GetResult, PositionedResult}
import scala.collection.mutable.ListBuffer

object ConversionDatabase {
  implicit object GetPointSeq extends GetResult[Seq[Point]] {
    def apply(rs: PositionedResult) = toPoints(rs.nextObject())
  }

  private def toPoints(bytes: Object): Seq[Point] = {
    val geometry = bytes.asInstanceOf[PGobject]
    if (geometry == null) Nil else{
      val geom = GeometryBuilder.geomFromString(geometry.getValue)
      if(geom.numPoints()==1 ) {
        val point = geom.getFirstPoint
        List(Point(point.x, point.y))
      }
      else {
        val listOfPoint=ListBuffer[Point]()
        for (i <- 0 until geom.numPoints() ){
          val point =geom.getPoint(i)
          listOfPoint += Point(point.x,point.y)
        }
        listOfPoint.toList
      }
    }
  }
}

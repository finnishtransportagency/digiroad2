package fi.liikennevirasto.digiroad2

import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import org.postgis.PGgeometry
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
      val geom = PGgeometry.geomFromString(geometry.getValue)
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

  lazy val dataSource = {
    val cfg = new BoneCPConfig(PostGISDatabase.loadProperties("/conversion.bonecp.properties"))
    new BoneCPDataSource(cfg)
  }
}

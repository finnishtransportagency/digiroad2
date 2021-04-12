package fi.liikennevirasto.digiroad2

import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.postgis.PGgeometry
import org.postgresql.util.PGobject
import slick.jdbc.{GetResult, PositionedResult}

object ConversionDatabase {
  implicit object GetPointSeq extends GetResult[Seq[Point]] {
    def apply(rs: PositionedResult) = toPoints(rs.nextObject())
  }

  private def toPoints(bytes: Object): Seq[Point] = {
    val geometry = bytes.asInstanceOf[PGobject]
    if (geometry == null) Nil
    else if(geometry.getValue.nonEmpty) {
      val geom = PGgeometry.geomFromString(geometry.getValue)
      val point = geom.getFirstPoint
      List(Point(point.x, point.y))
    }
    else {
      val geom = PGgeometry.geomFromString(geometry.getValue)
      val listOfPoint=List[Point]()
      for (i <- 0 until geom.numPoints() ){
        val point =geom.getPoint(i)
        Point(point.x,point.y) :: listOfPoint
      }
      listOfPoint.reverse
    }
  }

  lazy val dataSource = {
    val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/conversion.bonecp.properties"))
    new BoneCPDataSource(cfg)
  }
}

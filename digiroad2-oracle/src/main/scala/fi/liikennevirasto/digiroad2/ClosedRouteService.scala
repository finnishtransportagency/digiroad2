package fi.liikennevirasto.digiroad2

import _root_.oracle.spatial.geometry.JGeometry
import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase

import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.jdbc.StaticQuery.interpolation
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession
import scala.slick.jdbc.{GetResult, PositionedResult}

object ClosedRouteService {
  implicit object GetPointSeq extends GetResult[Seq[Point]] {
    def apply(rs: PositionedResult) = toPoints(rs.nextBytes())
  }

  private def toPoints(bytes: Array[Byte]): Seq[Point] = {
    val geometry = JGeometry.load(bytes)
    if (geometry == null) Nil
    else {
      geometry.getOrdinatesArray.grouped(2).map { point ⇒
        Point(point(0), point(1))
      }.toList
    }
  }

  lazy val dataSource = {
    val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/conversion.bonecp.properties"))
    new BoneCPDataSource(cfg)
  }

  def getByMunicipality(municipalityNumber: Int): Seq[(Long, Long, Point)] = {
    Database.forDataSource(dataSource).withDynTransaction {
      val query = sql"""
         select s.segm_id, s.tielinkki_id, to_2d(sdo_lrs.dynamic_segment(t.shape, s.alkum, s.loppum))
           from segments s
           join tielinkki_ctas t on s.tielinkki_id = t.dr1_id
           where t.kunta_nro = $municipalityNumber and s.tyyppi = 16
        """
      query.as[(Long, Long, Seq[Point])].iterator().map {
        case (id, roadLinkId, geometry) => (id, roadLinkId, geometry.head)
      }.toSeq
    }
  }
}

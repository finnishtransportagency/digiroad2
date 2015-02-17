package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase

import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.jdbc.StaticQuery.interpolation
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession
import scala.slick.jdbc.{StaticQuery => Q}

object ManoeuvreService {
  def getByBoundingBox(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[Long] = {
    Database.forDataSource(ConversionDatabase.dataSource).withDynTransaction {
      val municipalityFilter = if (municipalities.nonEmpty) "kunta_nro in (" + municipalities.mkString(",") + ") and" else ""
      val boundingBoxFilter = OracleDatabase.boundingBoxFilter(bounds)
      sql"""
        select k.kaan_id
        from kaantymismaarays k
        join tielinkki_ctas tl on k.tl_dr1_id = tl.dr1_id
        where #$municipalityFilter #$boundingBoxFilter
      """.as[Long].list
    }
  }
}

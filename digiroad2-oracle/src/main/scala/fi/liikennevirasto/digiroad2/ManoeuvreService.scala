package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase

import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.jdbc.StaticQuery.interpolation
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession
import scala.slick.jdbc.{StaticQuery => Q}

case class Manoeuvre(id: Long, sourceRoadLinkId: Long, destRoadLinkId: Long, sourceMmlId: Long, destMmlId: Long)

object ManoeuvreService {
  def getByBoundingBox(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[Manoeuvre] = {
    Database.forDataSource(ConversionDatabase.dataSource).withDynTransaction {
      val municipalityFilter = if (municipalities.nonEmpty) "kunta_nro in (" + municipalities.mkString(",") + ") and" else ""
      val boundingBoxFilter = OracleDatabase.boundingBoxFilter(bounds)
      val manoeuvres = sql"""
        select k.kaan_id, k.tl_dr1_id, tl.mml_id, elem_jarjestyslaji
        from kaantymismaarays k
        join tielinkki_ctas tl on k.tl_dr1_id = tl.dr1_id
        where #$municipalityFilter #$boundingBoxFilter
      """.as[(Long, Long, Long, Int)].list

      val manoeuvresById: Map[Long, Seq[(Long, Long, Long, Int)]] = manoeuvres.groupBy(_._1)
      manoeuvresById.filter { case(id, links) =>
        links.size == 2 && links.exists(_._4 == 1) && links.exists(_._4 == 3)
      }.map { case(id, links) =>
        val source: (Long, Long, Long, Int) = links.find(_._4 == 1).get
        val dest: (Long, Long, Long, Int) = links.find(_._4 == 3).get
        Manoeuvre(id, source._2, dest._2, source._3, dest._3)
      }.toSeq
    }
  }
}

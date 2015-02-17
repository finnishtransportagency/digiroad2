package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase

import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.jdbc.StaticQuery.interpolation
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession
import scala.slick.jdbc.{StaticQuery => Q}

case class Manoeuvre(id: Long, sourceRoadLinkId: Long, destRoadLinkId: Long)

object ManoeuvreService {
  def getByBoundingBox(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[Manoeuvre] = {
    Database.forDataSource(ConversionDatabase.dataSource).withDynTransaction {
      val municipalityFilter = if (municipalities.nonEmpty) "kunta_nro in (" + municipalities.mkString(",") + ") and" else ""
      val boundingBoxFilter = OracleDatabase.boundingBoxFilter(bounds)
      val manoeuvres = sql"""
        select k.kaan_id, k.tl_dr1_id, elem_jarjestyslaji
        from kaantymismaarays k
        join tielinkki_ctas tl on k.tl_dr1_id = tl.dr1_id
        where #$municipalityFilter #$boundingBoxFilter
      """.as[(Long, Long, Int)].list

      val manoeuvresById: Map[Long, Seq[(Long, Long, Int)]] = manoeuvres.groupBy(_._1)
      manoeuvresById.filter { case(id, links) =>
        links.size == 2 && !links.exists{ case(_, _, linkType) => linkType == 2 }
      }.map { case(id, links) =>
        Manoeuvre(id, links.find(_._3 == 1).get._2, links.find(_._3 == 3).get._2)
      }.toSeq
    }
  }
}

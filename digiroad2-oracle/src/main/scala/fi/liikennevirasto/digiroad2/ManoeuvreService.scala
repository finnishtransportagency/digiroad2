package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, BoundingRectangle}
import fi.liikennevirasto.digiroad2.asset.oracle.Queries._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.oracle.collections.OracleArray
import org.joda.time.DateTime
import scala.collection.JavaConversions._

import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.jdbc.StaticQuery.interpolation
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession
import scala.slick.jdbc.{StaticQuery => Q}

case class Manoeuvre(id: Long, sourceRoadLinkId: Long, destRoadLinkId: Long, sourceMmlId: Long, destMmlId: Long)

object ManoeuvreService {
  def getByMunicipality(municipalityNumber: Int): Seq[Manoeuvre] = {
    val filter = s"kunta_nro = $municipalityNumber and k.elem_jarjestyslaji = 1"
    query(filter)
  }

  private def query(filter: String): Seq[Manoeuvre] = {
    Database.forDataSource(ConversionDatabase.dataSource).withDynTransaction {
      val manoeuvres = sql"""
        select k.kaan_id, k.tl_dr1_id, tl.mml_id, k.elem_jarjestyslaji
        from kaantymismaarays k
        join tielinkki_ctas tl on k.tl_dr1_id = tl.dr1_id
        where k.kaan_id in (
          select distinct(k.kaan_id)
          from kaantymismaarays k
          join tielinkki_ctas tl on k.tl_dr1_id = tl.dr1_id
          where #$filter)
      """.as[(Long, Long, Long, Int)].list

      val manoeuvresById: Map[Long, Seq[(Long, Long, Long, Int)]] = manoeuvres.groupBy(_._1)
      manoeuvresById.filter { case (id, links) =>
        links.size == 2 && links.exists(_._4 == 1) && links.exists(_._4 == 3)
      }.map { case (id, links) =>
        val source: (Long, Long, Long, Int) = links.find(_._4 == 1).get
        val dest: (Long, Long, Long, Int) = links.find(_._4 == 3).get
        Manoeuvre(id, source._2, dest._2, source._3, dest._3)
      }.toSeq
    }
  }

  def getByBoundingBox(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[Manoeuvre] = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      val roadLinks = RoadLinkService.getRoadLinks(bounds, municipalities)
      val roadLinkIds = roadLinks.map(_._1).toList

      val manoeuvres = OracleArray.fetchManoeuvresByRoadLinkIds(roadLinkIds, bonecpToInternalConnection(dynamicSession.conn))

      val manoeuvresById: Map[Long, Seq[(Long, Int, Long, Int, DateTime, String)]] = manoeuvres.toList.groupBy(_._1)
      manoeuvresById.filter { case (id, links) =>
        links.size == 2 && links.exists(_._4 == 1) && links.exists(_._4 == 3)
      }.map { case (id, links) =>
        val source: (Long, Int, Long, Int, DateTime, String) = links.find(_._4 == 1).get
        val dest: (Long, Int, Long, Int, DateTime, String) = links.find(_._4 == 3).get
        val sourceMmlId = roadLinks.find(_._1 == source._3).get._2
        val destMmlId = roadLinks.find(_._1 == dest._3).get._2

        Manoeuvre(id, source._3, dest._3, sourceMmlId, destMmlId)
      }.toSeq
    }
  }
}

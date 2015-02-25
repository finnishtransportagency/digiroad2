package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.asset.oracle.Queries._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.oracle.collections.OracleArray
import org.joda.time.DateTime

import scala.collection.JavaConversions._
import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession
import scala.slick.jdbc.{StaticQuery => Q}

case class Manoeuvre(id: Long, sourceRoadLinkId: Long, destRoadLinkId: Long, sourceMmlId: Long, destMmlId: Long)

object ManoeuvreService {
  def getByMunicipality(municipalityNumber: Int): Seq[Manoeuvre] = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      val roadLinks = RoadLinkService.getByMunicipalityWithProperties(municipalityNumber)
        .map(link => (link("id").asInstanceOf[Long], link("mmlId").asInstanceOf[Long]))
        .toMap

      getByRoadlinks(roadLinks)
    }
  }

  def getByBoundingBox(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[Manoeuvre] = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      val roadLinks = RoadLinkService.getRoadLinks(bounds, municipalities)
        .map(link => (link._1, link._2))
        .toMap

      getByRoadlinks(roadLinks)
    }
  }

  private def getByRoadlinks(roadLinks: Map[Long,Long]): Seq[Manoeuvre] = {
    val roadLinkIds = roadLinks.keys.toList

    val manoeuvres = OracleArray.fetchManoeuvresByRoadLinkIds(roadLinkIds, bonecpToInternalConnection(dynamicSession.conn))

    val manoeuvresById: Map[Long, Seq[(Long, Int, Long, Int, DateTime, String)]] = manoeuvres.toList.groupBy(_._1)
    manoeuvresById.filter { case (id, links) =>
      links.size == 2 && links.exists(_._4 == 1) && links.exists(_._4 == 3)
    }.map { case (id, links) =>
      val source: (Long, Int, Long, Int, DateTime, String) = links.find(_._4 == 1).get
      val dest: (Long, Int, Long, Int, DateTime, String) = links.find(_._4 == 3).get
      val sourceMmlId = roadLinks.getOrElse(source._3, RoadLinkService.getRoadLink(source._3)._2)
      val destMmlId = roadLinks.getOrElse(dest._3, RoadLinkService.getRoadLink(source._3)._2)

      Manoeuvre(id, source._3, dest._3, sourceMmlId, destMmlId)
    }.toSeq
  }

}

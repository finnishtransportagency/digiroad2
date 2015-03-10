package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.asset.oracle.AssetPropertyConfiguration
import fi.liikennevirasto.digiroad2.asset.oracle.Queries._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.oracle.collections.OracleArray
import org.joda.time.DateTime

import scala.collection.JavaConversions._
import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession
import scala.slick.jdbc.{StaticQuery => Q}
import scala.slick.jdbc.StaticQuery.interpolation

case class Manoeuvre(id: Long, sourceRoadLinkId: Long, destRoadLinkId: Long, sourceMmlId: Long, destMmlId: Long, exceptions: Seq[Int], modifiedBy: String, modifiedDateTime: String)

object ManoeuvreService {
  def getSourceRoadLinkIdById(id: Long): Long = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      sql"""
             select road_link_id
             from manoeuvre
             where id = $id and element_type = 1
          """.as[Long].first
    }
  }

  def deleteManoeuvre(username: String, id: Long) = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      sqlu"""
             update manoeuvre
             set valid_to = sysdate, modified_date = sysdate, modified_by = $username
             where id = $id
          """.execute()
    }
  }

  private def addManoeuvreExceptions(manoeuvreId: Long, exceptions: Seq[Int]) {
    if (exceptions.nonEmpty) {
      val query = s"insert all " +
        exceptions.map { exception => s"into manoeuvre_exceptions (manoeuvre_id, exception_type) values ($manoeuvreId, $exception) "}.mkString +
        s"select * from dual"
      Q.updateNA(query).execute()
    }
  }

  val FirstElement = 1
  val LastElement = 3

  def createManoeuvre(userName: String, sourceRoadLinkId: Long, destRoadLinkId: Long, exceptions: Seq[Int]): Long = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      val manoeuvreId = sql"select manoeuvre_id_seq.nextval from dual".as[Long].first()

      sqlu"""
             insert into manoeuvre(id, type, road_link_id, element_type, modified_date, modified_by)
             values ($manoeuvreId, 2, $sourceRoadLinkId, $FirstElement, sysdate, $userName)
          """.execute()

      sqlu"""
             insert into manoeuvre(id, type, road_link_id, element_type, modified_date, modified_by)
             values ($manoeuvreId, 2, $destRoadLinkId, $LastElement, sysdate, $userName)
          """.execute()

      addManoeuvreExceptions(manoeuvreId, exceptions)
      manoeuvreId
    }
  }

  def setManoeuvreExceptions(username: String, manoeuvreId: Long, exceptions: Seq[Int]) = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      sqlu"""
           delete from manoeuvre_exceptions where manoeuvre_id = $manoeuvreId
          """.execute()

      addManoeuvreExceptions(manoeuvreId, exceptions)

      sqlu"""
           update manoeuvre
           set modified_date = sysdate, modified_by = $username
           where id = $manoeuvreId
          """.execute()
    }
  }

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
    val manoeuvresById = fetchManoeuvresByRoadLinkIds(roadLinks.keys.toSeq)
    val manoeuvreExceptionsById = fetchManoeuvreExceptionsByIds(manoeuvresById.keys.toSeq)

    manoeuvresById.filter { case (id, links) =>
      links.size == 2 && links.exists(_._4 == FirstElement) && links.exists(_._4 == LastElement)
    }.map { case (id, links) =>
      val (_, _, sourceRoadLinkId, _, modifiedDate, modifiedBy) = links.find(_._4 == FirstElement).get
      val (_, _, destRoadLinkId, _, _, _) = links.find(_._4 == LastElement).get
      val sourceMmlId = roadLinks.getOrElse(sourceRoadLinkId, RoadLinkService.getRoadLink(sourceRoadLinkId)._2)
      val destMmlId = roadLinks.getOrElse(destRoadLinkId, RoadLinkService.getRoadLink(destRoadLinkId)._2)
      val modifiedTimeStamp = AssetPropertyConfiguration.DateTimePropertyFormat.print(modifiedDate)

      Manoeuvre(id, sourceRoadLinkId, destRoadLinkId, sourceMmlId, destMmlId, manoeuvreExceptionsById.getOrElse(id, Seq()), modifiedTimeStamp, modifiedBy)
    }.toSeq
  }

  private def fetchManoeuvresByRoadLinkIds(roadLinkIds: Seq[Long]): Map[Long, Seq[(Long, Int, Long, Int, DateTime, String)]] = {
    val manoeuvres = OracleArray.fetchManoeuvresByRoadLinkIds(roadLinkIds, bonecpToInternalConnection(dynamicSession.conn))
    val manoeuvresById = manoeuvres.toList.groupBy(_._1)
    manoeuvresById
  }

  private def fetchManoeuvreExceptionsByIds(manoeuvreIds: Seq[Long]): Map[Long, Seq[Int]] = {
    val manoeuvreExceptions = OracleArray.fetchManoeuvreExceptionsByIds(manoeuvreIds, bonecpToInternalConnection(dynamicSession.conn))
    val manoeuvreExceptionsById: Map[Long, Seq[Int]] = manoeuvreExceptions.toList.groupBy(_._1).mapValues(_.map(_._2))
    manoeuvreExceptionsById
  }
}

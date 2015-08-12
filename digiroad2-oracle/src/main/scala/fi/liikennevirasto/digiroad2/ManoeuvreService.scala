package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.asset.oracle.AssetPropertyConfiguration
import fi.liikennevirasto.digiroad2.asset.oracle.Queries._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.oracle.collections.OracleArray
import org.joda.time.DateTime

import scala.collection.JavaConversions._
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.{StaticQuery => Q}
import slick.jdbc.StaticQuery.interpolation

case class Manoeuvre(id: Long, sourceRoadLinkId: Long, destRoadLinkId: Long, sourceMmlId: Long, destMmlId: Long, exceptions: Seq[Int], modifiedDateTime: String, modifiedBy: String, additionalInfo: String)
case class NewManoeuvre(sourceRoadLinkId: Long, destRoadLinkId: Long, exceptions: Seq[Int], additionalInfo: Option[String])
case class ManoeuvreUpdates(exceptions: Option[Seq[Int]], additionalInfo: Option[String])

object ManoeuvreService {

  val FirstElement = 1
  val LastElement = 3

  def getSourceRoadLinkIdById(id: Long): Long = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      sql"""
             select road_link_id
             from manoeuvre_element
             where manoeuvre_id = $id and element_type = 1
          """.as[Long].first
    }
  }

  def getByMunicipality(municipalityNumber: Int): Seq[Manoeuvre] = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      val roadLinks = RoadLinkService.getIdsAndMmlIdsByMunicipality(municipalityNumber).toMap
      getByRoadlinks(roadLinks)
    }
  }

  def getByBoundingBox(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[Manoeuvre] = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      val roadLinks = RoadLinkService.getRoadLinks(bounds, municipalities)
        .map(link => (link.id, link.mmlId))
        .toMap

      getByRoadlinks(roadLinks)
    }
  }

  def deleteManoeuvre(username: String, id: Long) = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      sqlu"""
             update manoeuvre
             set valid_to = sysdate, modified_date = sysdate, modified_by = $username
             where id = $id
          """.execute
    }
  }

  def createManoeuvre(userName: String, manoeuvre: NewManoeuvre): Long = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      val manoeuvreId = sql"select manoeuvre_id_seq.nextval from dual".as[Long].first
      val additionalInfo = manoeuvre.additionalInfo.getOrElse("")
      sqlu"""
             insert into manoeuvre(id, type, modified_date, modified_by, additional_info)
             values ($manoeuvreId, 2, sysdate, $userName, $additionalInfo)
          """.execute

      val sourceRoadLinkId = manoeuvre.sourceRoadLinkId
      sqlu"""
             insert into manoeuvre_element(manoeuvre_id, road_link_id, element_type)
             values ($manoeuvreId, $sourceRoadLinkId, $FirstElement)
          """.execute

      val destRoadLinkId = manoeuvre.destRoadLinkId
      sqlu"""
             insert into manoeuvre_element(manoeuvre_id, road_link_id, element_type)
             values ($manoeuvreId, $destRoadLinkId, $LastElement)
          """.execute

      addManoeuvreExceptions(manoeuvreId, manoeuvre.exceptions)
      manoeuvreId
    }
  }

  def updateManoeuvre(userName: String, manoeuvreId: Long, manoeuvreUpdates: ManoeuvreUpdates) = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      manoeuvreUpdates.additionalInfo.map(setManoeuvreAdditionalInfo(manoeuvreId))
      manoeuvreUpdates.exceptions.map(setManoeuvreExceptions(manoeuvreId))
      updateModifiedData(userName, manoeuvreId)
    }
  }

  private def addManoeuvreExceptions(manoeuvreId: Long, exceptions: Seq[Int]) {
    if (exceptions.nonEmpty) {
      val query = s"insert all " +
        exceptions.map { exception => s"into manoeuvre_exceptions (manoeuvre_id, exception_type) values ($manoeuvreId, $exception) "}.mkString +
        s"select * from dual"
      Q.updateNA(query).execute
    }
  }

  private def getByRoadlinks(roadLinks: Map[Long,Long]): Seq[Manoeuvre] = {
    val manoeuvresById = fetchManoeuvresByRoadLinkIds(roadLinks.keys.toSeq)
    val manoeuvreExceptionsById = fetchManoeuvreExceptionsByIds(manoeuvresById.keys.toSeq)

    manoeuvresById.filter { case (id, links) =>
      links.size == 2 && links.exists(_._4 == FirstElement) && links.exists(_._4 == LastElement)
    }.map { case (id, links) =>
      val (_, _, sourceRoadLinkId, _, modifiedDate, modifiedBy, additionalInfo) = links.find(_._4 == FirstElement).get
      val (_, _, destRoadLinkId, _, _, _, _) = links.find(_._4 == LastElement).get
      val sourceMmlId = roadLinks.getOrElse(sourceRoadLinkId, RoadLinkService.getRoadLinkMmlId(sourceRoadLinkId))
      val destMmlId = roadLinks.getOrElse(destRoadLinkId, RoadLinkService.getRoadLinkMmlId(destRoadLinkId))
      val modifiedTimeStamp = AssetPropertyConfiguration.DateTimePropertyFormat.print(modifiedDate)

      Manoeuvre(id, sourceRoadLinkId, destRoadLinkId, sourceMmlId, destMmlId, manoeuvreExceptionsById.getOrElse(id, Seq()), modifiedTimeStamp, modifiedBy, additionalInfo)
    }.toSeq
  }

  private def fetchManoeuvresByRoadLinkIds(roadLinkIds: Seq[Long]): Map[Long, Seq[(Long, Int, Long, Int, DateTime, String, String)]] = {
    val manoeuvres = OracleArray.fetchManoeuvresByRoadLinkIds(roadLinkIds, bonecpToInternalConnection(dynamicSession.conn))
    val manoeuvresById = manoeuvres.toList.groupBy(_._1)
    manoeuvresById
  }

  private def fetchManoeuvreExceptionsByIds(manoeuvreIds: Seq[Long]): Map[Long, Seq[Int]] = {
    val manoeuvreExceptions = OracleArray.fetchManoeuvreExceptionsByIds(manoeuvreIds, bonecpToInternalConnection(dynamicSession.conn))
    val manoeuvreExceptionsById: Map[Long, Seq[Int]] = manoeuvreExceptions.toList.groupBy(_._1).mapValues(_.map(_._2))
    manoeuvreExceptionsById
  }

  private def setManoeuvreExceptions(manoeuvreId: Long)(exceptions: Seq[Int]) = {
    sqlu"""
           delete from manoeuvre_exceptions where manoeuvre_id = $manoeuvreId
        """.execute
    addManoeuvreExceptions(manoeuvreId, exceptions)
  }

  private def updateModifiedData(username: String, manoeuvreId: Long) {
    sqlu"""
           update manoeuvre
           set modified_date = sysdate, modified_by = $username
           where id = $manoeuvreId
        """.execute
  }

  private def setManoeuvreAdditionalInfo(manoeuvreId: Long)(additionalInfo: String) = {
    sqlu"""
           update manoeuvre
           set additional_info = $additionalInfo
           where id = $manoeuvreId
        """.execute
  }
}

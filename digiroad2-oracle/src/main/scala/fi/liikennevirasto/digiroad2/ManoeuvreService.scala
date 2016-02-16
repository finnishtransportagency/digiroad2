package fi.liikennevirasto.digiroad2

import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.asset.Asset._
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.linearasset.{ValidityPeriodDayOfWeek, ValidityPeriod, RoadLink}
import fi.liikennevirasto.digiroad2.oracle.{MassQuery, OracleDatabase}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

case class Manoeuvre(id: Long, sourceLinkId: Long, destLinkId: Long, validityPeriods: Set[ValidityPeriod], exceptions: Seq[Int], modifiedDateTime: String, modifiedBy: String, additionalInfo: String)
case class NewManoeuvre(validityPeriods: Set[ValidityPeriod], exceptions: Seq[Int], additionalInfo: Option[String], sourceLinkId: Long, destLinkId: Long)
case class ManoeuvreUpdates(validityPeriods: Option[Set[ValidityPeriod]], exceptions: Option[Seq[Int]], additionalInfo: Option[String])

class ManoeuvreService(roadLinkService: RoadLinkService) {

  val FirstElement = 1
  val LastElement = 3

  def getSourceRoadLinkIdById(id: Long): Long = {
    OracleDatabase.withDynTransaction {
      sql"""
             select link_id
             from manoeuvre_element
             where manoeuvre_id = $id and element_type = 1
          """.as[Long].first
    }
  }

  def getByMunicipality(municipalityNumber: Int): Seq[Manoeuvre] = {
    val roadLinks = roadLinkService.getRoadLinksFromVVH(municipalityNumber)
    getByRoadLinks(roadLinks)
  }

  def getByBoundingBox(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[Manoeuvre] = {
    val roadLinks = roadLinkService.getRoadLinksFromVVH(bounds, municipalities)
    getByRoadLinks(roadLinks)
  }

  def deleteManoeuvre(username: String, id: Long) = {
    OracleDatabase.withDynTransaction {
      sqlu"""
             update manoeuvre
             set valid_to = sysdate, modified_date = sysdate, modified_by = $username
             where id = $id
          """.execute
    }
  }

  def createManoeuvre(userName: String, manoeuvre: NewManoeuvre): Long = {
    OracleDatabase.withDynTransaction {
      val manoeuvreId = sql"select manoeuvre_id_seq.nextval from dual".as[Long].first
      val additionalInfo = manoeuvre.additionalInfo.getOrElse("")
      sqlu"""
             insert into manoeuvre(id, type, modified_date, modified_by, additional_info)
             values ($manoeuvreId, 2, sysdate, $userName, $additionalInfo)
          """.execute

      val sourceLinkId = manoeuvre.sourceLinkId
      sqlu"""
             insert into manoeuvre_element(manoeuvre_id, element_type, link_id)
             values ($manoeuvreId, $FirstElement, $sourceLinkId)
          """.execute

      val destLinkId = manoeuvre.destLinkId
      sqlu"""
             insert into manoeuvre_element(manoeuvre_id, element_type, link_id)
             values ($manoeuvreId, $LastElement, $destLinkId)
          """.execute

      addManoeuvreExceptions(manoeuvreId, manoeuvre.exceptions)
      addManoeuvreValidityPeriods(manoeuvreId, manoeuvre.validityPeriods)
      manoeuvreId
    }
  }

  def updateManoeuvre(userName: String, manoeuvreId: Long, manoeuvreUpdates: ManoeuvreUpdates) = {
    OracleDatabase.withDynTransaction {
      manoeuvreUpdates.additionalInfo.foreach(setManoeuvreAdditionalInfo(manoeuvreId))
      manoeuvreUpdates.exceptions.foreach(setManoeuvreExceptions(manoeuvreId))
      manoeuvreUpdates.validityPeriods.foreach(setManoeuvreValidityPeriods(manoeuvreId))
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

  private def addManoeuvreValidityPeriods(manoeuvreId: Long, validityPeriods: Set[ValidityPeriod]) {
    validityPeriods.foreach { case ValidityPeriod(startHour, endHour, days) =>
      sqlu"""
        insert into manoeuvre_validity_period (id, manoeuvre_id, start_hour, end_hour, type)
        values (primary_key_seq.nextval, $manoeuvreId, $startHour, $endHour, ${days.value})
      """.execute
    }
  }

  private def getByRoadLinks(roadLinks: Seq[RoadLink]): Seq[Manoeuvre] = {
    val (manoeuvresById, manoeuvreExceptionsById, manoeuvreValidityPeriodsById) = OracleDatabase.withDynTransaction {
      val manoeuvresById = fetchManoeuvresByLinkIds(roadLinks.map(_.linkId))
      val manoeuvreExceptionsById = fetchManoeuvreExceptionsByIds(manoeuvresById.keys.toSeq)
      val manoeuvreValidityPeriodsById = fetchManoeuvreValidityPeriodsByIds(manoeuvresById.keys.toSet)
      (manoeuvresById, manoeuvreExceptionsById, manoeuvreValidityPeriodsById)
    }

    manoeuvresById
      .filter(hasOnlyOneSourceAndDestination)
      .map(manoeuvreRowsToManoeuvre(manoeuvreExceptionsById, manoeuvreValidityPeriodsById))
      .filter(isValidManoeuvre(roadLinks))
      .toSeq
  }

  private def hasOnlyOneSourceAndDestination(manoeuvreRowsForId: (Long, Seq[PersistedManoeuvreRow])): Boolean = {
    val (_, manoeuvreRows) = manoeuvreRowsForId
    manoeuvreRows.size == 2 && manoeuvreRows.exists(_.elementType == FirstElement) && manoeuvreRows.exists(_.elementType == LastElement)
  }

  private def manoeuvreRowsToManoeuvre(manoeuvreExceptionsById: Map[Long, Seq[Int]],
                                       manoeuvreValidityPeriodsById: Map[Long, Set[ValidityPeriod]])
                                      (manoeuvreRowsForId: (Long, Seq[PersistedManoeuvreRow])): Manoeuvre = {
    val (id, manoeuvreRows) = manoeuvreRowsForId
    val manoeuvreSource = manoeuvreRows.find(_.elementType == FirstElement).get
    val manoeuvreDestination = manoeuvreRows.find(_.elementType == LastElement).get
    val modifiedTimeStamp = DateTimePropertyFormat.print(manoeuvreSource.modifiedDate)

    Manoeuvre(id, manoeuvreSource.linkId, manoeuvreDestination.linkId,
      manoeuvreValidityPeriodsById.getOrElse(id, Set.empty), manoeuvreExceptionsById.getOrElse(id, Seq()),
      modifiedTimeStamp, manoeuvreSource.modifiedBy, manoeuvreSource.additionalInfo)
  }

  private def isValidManoeuvre(roadLinks: Seq[RoadLink])(manoeuvre: Manoeuvre): Boolean = {
    val destRoadLinkOption = roadLinks.find(_.linkId == manoeuvre.destLinkId).orElse(roadLinkService.getRoadLinkFromVVH(manoeuvre.destLinkId))
    val sourceRoadLinkOption = roadLinks.find(_.linkId == manoeuvre.sourceLinkId).orElse(roadLinkService.getRoadLinkFromVVH(manoeuvre.sourceLinkId))

    (sourceRoadLinkOption, destRoadLinkOption) match {
      case (Some(sourceRoadLink), Some(destRoadLink)) => {
        GeometryUtils.areAdjacent(sourceRoadLink.geometry, destRoadLink.geometry) &&
          sourceRoadLink.isCarTrafficRoad &&
          destRoadLink.isCarTrafficRoad
      }
      case _ => false
    }
  }

  case class PersistedManoeuvreRow(id: Long, linkId: Long, elementType: Int, modifiedDate: DateTime, modifiedBy: String, additionalInfo: String)

  private def fetchManoeuvresByLinkIds(linkIds: Seq[Long]): Map[Long, Seq[PersistedManoeuvreRow]] = {
    val manoeuvres = MassQuery.withIds(linkIds.toSet) { idTableName =>
      sql"""SELECT m.id, e.link_id, e.element_type, m.modified_date, m.modified_by, m.additional_info
            FROM MANOEUVRE m
            JOIN MANOEUVRE_ELEMENT e ON m.id = e.manoeuvre_id
            WHERE m.id in (SELECT k.manoeuvre_id
                            FROM MANOEUVRE_ELEMENT k
                            join #$idTableName i on i.id = k.link_id
                            where valid_to is null)""".as[(Long, Long, Int, DateTime, String, String)].list
    }
    manoeuvres.map { manoeuvreRow =>
      PersistedManoeuvreRow(manoeuvreRow._1, manoeuvreRow._2, manoeuvreRow._3, manoeuvreRow._4, manoeuvreRow._5, manoeuvreRow._6)
    }.groupBy(_.id)
  }

  private def fetchManoeuvreExceptionsByIds(manoeuvreIds: Seq[Long]): Map[Long, Seq[Int]] = {
    val manoeuvreExceptions = MassQuery.withIds(manoeuvreIds.toSet) { idTableName =>
      sql"""SELECT m.manoeuvre_id, m.exception_type
            FROM MANOEUVRE_EXCEPTIONS m
            JOIN #$idTableName i on m.manoeuvre_id = i.id""".as[(Long, Int)].list
    }
    val manoeuvreExceptionsById: Map[Long, Seq[Int]] = manoeuvreExceptions.toList.groupBy(_._1).mapValues(_.map(_._2))
    manoeuvreExceptionsById
  }

  private def fetchManoeuvreValidityPeriodsByIds(manoeuvreIds: Set[Long]):  Map[Long, Set[ValidityPeriod]] = {
    val manoeuvreValidityPeriods = MassQuery.withIds(manoeuvreIds) { idTableName =>
      sql"""SELECT m.manoeuvre_id, m.type, m.start_hour, m.end_hour
            FROM MANOEUVRE_VALIDITY_PERIOD m
            JOIN #$idTableName i on m.manoeuvre_id = i.id""".as[(Long, Int, Int, Int)].list

    }

    manoeuvreValidityPeriods.groupBy(_._1).mapValues { periods =>
      periods.map { case (_, dayOfWeek, startHour, endHour) =>
        ValidityPeriod(startHour, endHour, ValidityPeriodDayOfWeek(dayOfWeek))
      }.toSet
    }
  }

  private def setManoeuvreExceptions(manoeuvreId: Long)(exceptions: Seq[Int]) = {
    sqlu"""
           delete from manoeuvre_exceptions where manoeuvre_id = $manoeuvreId
        """.execute
    addManoeuvreExceptions(manoeuvreId, exceptions)
  }

  private def setManoeuvreValidityPeriods(manoeuvreId: Long)(validityPeriods: Set[ValidityPeriod]) = {
    sqlu"""
           delete from manoeuvre_validity_period where manoeuvre_id = $manoeuvreId
        """.execute
    addManoeuvreValidityPeriods(manoeuvreId, validityPeriods)
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

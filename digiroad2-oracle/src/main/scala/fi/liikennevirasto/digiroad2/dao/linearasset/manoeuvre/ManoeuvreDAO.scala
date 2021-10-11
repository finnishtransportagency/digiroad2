package fi.liikennevirasto.digiroad2.dao.linearasset.manoeuvre

import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.asset.TrafficSigns
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.linearasset.{ValidityPeriod, ValidityPeriodDayOfWeek}
import fi.liikennevirasto.digiroad2.postgis.MassQuery
import fi.liikennevirasto.digiroad2.service.linearasset.{ElementTypes, Manoeuvre, ManoeuvreElement, NewManoeuvre}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}
/**
  * Created by venholat on 3.5.2016.
  */
case class PersistedManoeuvreRow(id: Long, linkId: Long, destLinkId: Long, elementType: Int, modifiedDate: Option[DateTime],
                                 modifiedBy: Option[String], additionalInfo: String, createdDate: DateTime, createdBy: String, isSuggested: Boolean)

class ManoeuvreDao(val vvhClient: VVHClient) {

  def find(id: Long): Option[Manoeuvre] = {
    val manoeuvresById = Map(id -> fetchManoeuvreById(id))
    if (manoeuvresById.get(id).get.nonEmpty) {
      val manoeuvreExceptionsById = fetchManoeuvreExceptionsByIds(Seq(id))
      val manoeuvreValidityPeriodsById = fetchManoeuvreValidityPeriodsByIds(Set(id))
      manoeuvresById.map(manoeuvreRowsToManoeuvre(manoeuvreExceptionsById, manoeuvreValidityPeriodsById))
        .headOption
    } else {
      None
    }
  }


  def getSourceRoadLinkIdById(id: Long): Long = {
    sql"""
             select link_id
             from manoeuvre_element
             where manoeuvre_id = $id and element_type = 1
          """.as[Long].first
  }

  def deleteManoeuvre(username: String, id: Long) = {
    sqlu"""
             update manoeuvre
             set valid_to = current_timestamp, modified_date = current_timestamp, modified_by = $username
             where id = $id
          """.execute
    id
  }

  def deleteManoeuvreByTrafficSign(trafficSignId: Long) = {
    val username = "automatic_trafficSign_deleted"
    sqlu"""
             update manoeuvre
             set valid_to = current_timestamp, modified_date = current_timestamp, modified_by = $username
             where traffic_sign_id = $trafficSignId
          """.execute
    trafficSignId
  }

  def deleteManoeuvreByTrafficSign(queryFilter: String => String, username: Option[String]) : Unit = {
    val modified = username match { case Some(user) => s", modified_by = '$user',  modified_date = current_timestamp" case _ => ""}
    val query = s"""
          update manoeuvre
             set valid_to = current_timestamp $modified
             where traffic_sign_id in (
             select a.id
              from asset a
              where a.asset_type_id = ${TrafficSigns.typeId}
          """
    Q.updateNA(queryFilter(query) + ")").execute
  }

  def expireManoeuvre(id: Long) = {
    sqlu"""
             update manoeuvre
             set valid_to = current_timestamp
             where id = $id
          """.execute
  }

  def createManoeuvre(userName: String, manoeuvre: NewManoeuvre): Long = {
    val manoeuvreId = sql"select nextval('manoeuvre_id_seq')".as[Long].first
    val additionalInfo = manoeuvre.additionalInfo.getOrElse("")
    sqlu"""
             insert into manoeuvre(id, type, created_date, created_by, additional_info, traffic_sign_id, suggested)
             values ($manoeuvreId, 2, current_timestamp, $userName, $additionalInfo, ${manoeuvre.trafficSignId}, ${manoeuvre.isSuggested})
          """.execute

    val linkPairs = manoeuvre.linkIds.zip(manoeuvre.linkIds.tail)
    val startingElement = linkPairs.head
    sqlu"""
             insert into manoeuvre_element(manoeuvre_id, element_type, link_id, dest_link_id)
             values ($manoeuvreId, ${ElementTypes.FirstElement}, ${startingElement._1}, ${startingElement._2})
          """.execute

    val destLinkId = manoeuvre.linkIds.last
    sqlu"""
             insert into manoeuvre_element(manoeuvre_id, element_type, link_id)
             values ($manoeuvreId, ${ElementTypes.LastElement}, $destLinkId)
          """.execute

    val intermediateLinkIds = linkPairs.tail
    intermediateLinkIds.foreach(pair =>
      sqlu"""
             insert into manoeuvre_element(manoeuvre_id, element_type, link_id, dest_link_id)
             values ($manoeuvreId, ${ElementTypes.IntermediateElement}, ${pair._1}, ${pair._2})
          """.execute
    )
    addManoeuvreExceptions(manoeuvreId, manoeuvre.exceptions)
    addManoeuvreValidityPeriods(manoeuvreId, manoeuvre.validityPeriods)
    manoeuvreId
  }

  def createManoeuvreForUpdate(userName: String, oldManoeuvreRow: PersistedManoeuvreRow, additionalInfoOpt: Option[String], modifiedDateOpt: Option[DateTime]): Long = {
    val manoeuvreId = sql"select nextval('manoeuvre_id_seq')".as[Long].first
    val additionalInfo = additionalInfoOpt match {
      case Some(additionalValue) => additionalValue
      case _ => oldManoeuvreRow.additionalInfo
    }

    modifiedDateOpt match {
      case Some(modifiedDate) =>
        sqlu"""
            insert into manoeuvre(id, type, created_by, created_date, additional_info, modified_by, modified_date)
            values ($manoeuvreId, 2, ${oldManoeuvreRow.createdBy}, ${oldManoeuvreRow.createdDate}, $additionalInfo, $userName, $modifiedDate)
          """.execute
      case _ =>
        sqlu"""
            insert into manoeuvre(id, type, created_by, created_date, additional_info, modified_by, modified_date)
            values ($manoeuvreId, 2, ${oldManoeuvreRow.createdBy}, ${oldManoeuvreRow.createdDate}, $additionalInfo, $userName, current_timestamp)
          """.execute
    }

    sqlu"""
             insert into manoeuvre_element(manoeuvre_id, element_type, link_id, dest_link_id)
             select $manoeuvreId, element_type, link_id, dest_link_id
             from manoeuvre_element where manoeuvre_Id = ${oldManoeuvreRow.id}
          """.execute

    sqlu"""
             insert into manoeuvre_exceptions(manoeuvre_id, exception_type)
             select $manoeuvreId, exception_type
             from manoeuvre_exceptions where manoeuvre_Id = ${oldManoeuvreRow.id}
          """.execute

    sqlu"""
             insert into manoeuvre_validity_period(id, manoeuvre_id, type, start_hour, end_hour, start_minute, end_minute)
             select nextval('primary_key_seq'), $manoeuvreId, type, start_hour, end_hour, start_minute, end_minute
             from manoeuvre_validity_period where manoeuvre_Id = ${oldManoeuvreRow.id}
          """.execute

    manoeuvreId
  }

  def addManoeuvreExceptions(manoeuvreId: Long, exceptions: Seq[Int]) {
    if (exceptions.nonEmpty) {
      val query = s"insert into manoeuvre_exceptions (manoeuvre_id, exception_type) values" +
        exceptions.map { exception => s"($manoeuvreId, $exception),"}.mkString.dropRight(1)
      Q.updateNA(query).execute
    }
  }

  def addManoeuvreValidityPeriods(manoeuvreId: Long, validityPeriods: Set[ValidityPeriod]) {
    validityPeriods.foreach { case ValidityPeriod(startHour, endHour, days, startMinute, endMinute) =>
      sqlu"""
        insert into manoeuvre_validity_period (id, manoeuvre_id, start_hour, end_hour, type, start_minute, end_minute)
        values (nextval('primary_key_seq'), $manoeuvreId, $startHour, $endHour, ${days.value}, $startMinute, $endMinute)
      """.execute
    }
  }

  private def manoeuvreRowsToManoeuvre(manoeuvreExceptionsById: Map[Long, Seq[Int]],
                               manoeuvreValidityPeriodsById: Map[Long, Set[ValidityPeriod]])
                              (manoeuvreRowsForId: (Long, Seq[PersistedManoeuvreRow])): Manoeuvre = {
    val (id, manoeuvreRows) = manoeuvreRowsForId
    val manoeuvreRow = manoeuvreRows.head
    Manoeuvre(id, manoeuvreElementsFromRows(manoeuvreRowsForId),
      manoeuvreValidityPeriodsById.getOrElse(id, Set.empty), manoeuvreExceptionsById.getOrElse(id, Seq()), manoeuvreRow.modifiedDate,
      manoeuvreRow.modifiedBy, manoeuvreRow.additionalInfo, manoeuvreRow.createdDate, manoeuvreRow.createdBy, manoeuvreRow.isSuggested)
  }

  private def manoeuvreElementsFromRows(manoeuvreRowsForId: (Long, Seq[PersistedManoeuvreRow])): Seq[ManoeuvreElement] = {
    val (id, manoeuvreRows) = manoeuvreRowsForId
    manoeuvreRows.map(row => ManoeuvreElement(id, row.linkId, row.destLinkId, row.elementType))
  }

  private def manoeuvreRowsToManoeuvre(manoeuvreRows: Seq[PersistedManoeuvreRow], manoeuvreExceptions: Seq[Int],
                               manoeuvreValidityPeriods: Set[ValidityPeriod])
  : Manoeuvre = {
    val manoeuvreRow = manoeuvreRows.head

    val elements = manoeuvreRows.map(row => ManoeuvreElement(row.id, row.linkId, row.destLinkId, row.elementType))
    Manoeuvre(manoeuvreRow.id, elements, manoeuvreValidityPeriods, manoeuvreExceptions, manoeuvreRow.modifiedDate, manoeuvreRow.modifiedBy,
      manoeuvreRow.additionalInfo, manoeuvreRow.createdDate, manoeuvreRow.createdBy, manoeuvreRow.isSuggested)
  }

  private def fetchManoeuvresByLinkIds(linkIds: Seq[Long]): Map[Long, Seq[PersistedManoeuvreRow]] = {
    val manoeuvres = MassQuery.withIds(linkIds.toSet) { idTableName =>
      sql"""SELECT m.id, e.link_id, e.dest_link_id, e.element_type, m.modified_date, m.modified_by, m.additional_info, m.created_date, m.created_by, m.suggested
            FROM MANOEUVRE m
            JOIN MANOEUVRE_ELEMENT e ON m.id = e.manoeuvre_id
            WHERE (valid_to is null OR valid_to > current_timestamp) AND
                EXISTS (SELECT k.manoeuvre_id
                               FROM MANOEUVRE_ELEMENT k
                               join #$idTableName i on i.id = k.link_id
                               where
                                   k.manoeuvre_id = m.id)
        """.as[(Long, Long, Long, Int, Option[DateTime], Option[String], String, DateTime, String, Boolean)].list
    }
    manoeuvres.map { manoeuvreRow =>
      PersistedManoeuvreRow(manoeuvreRow._1, manoeuvreRow._2, manoeuvreRow._3, manoeuvreRow._4, manoeuvreRow._5, manoeuvreRow._6,
        manoeuvreRow._7, manoeuvreRow._8, manoeuvreRow._9, manoeuvreRow._10 )
    }.groupBy(_.id)
  }

  private def fetchManoeuvresByElementTypeLinkIds(elementType: Long, linkIds: Seq[Long]): Map[Long, Seq[PersistedManoeuvreRow]] = {
    val manoeuvres = MassQuery.withIds(linkIds.toSet) { idTableName =>
      sql"""SELECT m.id, e.link_id, e.dest_link_id, e.element_type, m.modified_date, m.modified_by, m.additional_info, m.created_date, m.created_by, m.suggested
            FROM MANOEUVRE m
            JOIN MANOEUVRE_ELEMENT e ON m.id = e.manoeuvre_id
            WHERE (valid_to is null OR valid_to > current_timestamp) AND
                EXISTS (SELECT k.manoeuvre_id
                               FROM MANOEUVRE_ELEMENT k
                               join #$idTableName i on i.id = k.link_id
                               where
                                   k.manoeuvre_id = m.id and k.element_type = $elementType)
        """.as[(Long, Long, Long, Int, Option[DateTime], Option[String], String, DateTime, String, Boolean)].list
    }
    manoeuvres.map { manoeuvreRow =>
      PersistedManoeuvreRow(manoeuvreRow._1, manoeuvreRow._2, manoeuvreRow._3, manoeuvreRow._4, manoeuvreRow._5, manoeuvreRow._6,
        manoeuvreRow._7, manoeuvreRow._8, manoeuvreRow._9, manoeuvreRow._10)
    }.groupBy(_.id)
  }

  def fetchManoeuvreById(id: Long): Seq[PersistedManoeuvreRow] = {
    val manoeuvre =
      sql"""SELECT m.id, e.link_id, e.dest_link_id, e.element_type, m.modified_date, m.modified_by, m.additional_info, m.created_date, m.created_by, m.suggested
            FROM MANOEUVRE m
            JOIN MANOEUVRE_ELEMENT e ON m.id = e.manoeuvre_id
            WHERE m.id = $id and (valid_to > current_timestamp OR valid_to is null)"""
        .as[(Long, Long, Long, Int, Option[DateTime], Option[String], String, DateTime, String, Boolean)].list
    manoeuvre.map(row =>
      PersistedManoeuvreRow(row._1, row._2, row._3, row._4, row._5, row._6, row._7, row._8, row._9, row._10))
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
      sql"""SELECT m.manoeuvre_id, m.type, m.start_hour, m.end_hour, m.start_minute, m.end_minute
            FROM MANOEUVRE_VALIDITY_PERIOD m
            JOIN #$idTableName i on m.manoeuvre_id = i.id""".as[(Long, Int, Int, Int, Int, Int)].list

    }

    manoeuvreValidityPeriods.groupBy(_._1).mapValues { periods =>
      periods.map { case (_, dayOfWeek, startHour, endHour, startMinute, endMinute) =>
        ValidityPeriod(startHour, endHour, ValidityPeriodDayOfWeek(dayOfWeek), startMinute, endMinute)
      }.toSet
    }
  }

  def setManoeuvreExceptions(manoeuvreId: Long)(exceptions: Seq[Int]) = {
    sqlu"""
           delete from manoeuvre_exceptions where manoeuvre_id = $manoeuvreId
        """.execute
    addManoeuvreExceptions(manoeuvreId, exceptions)
  }

  def setManoeuvreValidityPeriods(manoeuvreId: Long)(validityPeriods: Set[ValidityPeriod]) = {
    sqlu"""
           delete from manoeuvre_validity_period where manoeuvre_id = $manoeuvreId
        """.execute
    addManoeuvreValidityPeriods(manoeuvreId, validityPeriods)
  }

  private def updateModifiedData(username: String, manoeuvreId: Long, modifiedDate: Option[DateTime]) {
    modifiedDate match {
      case Some(date) =>
        sqlu"""
           update manoeuvre
           set modified_date = $date
           , modified_by = $username
           where id = $manoeuvreId
        """.execute
      case _ =>
        sqlu"""
           update manoeuvre
           set modified_date = current_timestamp
           , modified_by = $username
           where id = $manoeuvreId
        """.execute
    }
  }

  def setManoeuvreAdditionalInfo(manoeuvreId: Long)(additionalInfo: String) = {
    sqlu"""
           update manoeuvre
           set additional_info = $additionalInfo
           where id = $manoeuvreId
        """.execute
  }

  def getByRoadLinks(roadLinkIds: Seq[Long]): Seq[Manoeuvre] = {
    getByManoeuvresId(fetchManoeuvresByLinkIds(roadLinkIds))
  }

  def getByElementTypeRoadLinks(elementType: Long)(roadLinkIds: Seq[Long]): Seq[Manoeuvre] = {
    getByManoeuvresId(fetchManoeuvresByElementTypeLinkIds(elementType, roadLinkIds))
  }

  def getByManoeuvresId(manoeuvresById: Map[Long, Seq[PersistedManoeuvreRow]]): Seq[Manoeuvre] = {
    val manoeuvreExceptionsById = fetchManoeuvreExceptionsByIds(manoeuvresById.keys.toSeq)
    val manoeuvreValidityPeriodsById = fetchManoeuvreValidityPeriodsByIds(manoeuvresById.keys.toSet)
    manoeuvresById
      .map(manoeuvreRowsToManoeuvre(manoeuvreExceptionsById, manoeuvreValidityPeriodsById))
      .toSeq
  }

  def countExistings(sourceId: Long, destId: Long, elementType: Int): Long = {
    sql"""
         select COUNT(*)
         from manoeuvre_element me
         join MANOEUVRE m on m.ID = me.MANOEUVRE_ID
         where me.LINK_ID = $sourceId and me.element_type = $elementType and me.DEST_LINK_ID = $destId and m.VALID_TO is null
      """.as[Long].first
  }
}

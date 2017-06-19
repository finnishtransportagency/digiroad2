package fi.liikennevirasto.digiroad2.roadlinkservice.oracle

import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

object RoadLinkServiceDAO {
  private val FunctionalClass = "functional_class"
  private val TrafficDirection = "traffic_direction"
  private val LinkType = "link_type"
  private val AdministrativeClass = "administrative_class"
  private val VVHAdministrativeClass = "vvh_administrative_class"

  def updateExistingLinkPropertyRow(table: String, column: String, linkId: Long, username: Option[String], existingValue: Int, value: Int, mmlId: Option[Long]) = {
    if (existingValue != value) {
      sqlu"""update #$table
                 set #$column = $value,
                     mml_id = $mmlId,
                     modified_date = current_timestamp,
                     modified_by = $username
                 where link_id = $linkId""".execute
    }
  }

  def insertNewLinkProperty(table: String, column: String, linkId: Long, username: Option[String], value: Int, mml_id: Option[Long]) = {
    sqlu"""insert into #$table (id, link_id, #$column, modified_by, mml_id )
                   select primary_key_seq.nextval, $linkId, $value, $username, $mml_id
                   from dual
                   where not exists (select * from #$table where link_id = $linkId)""".execute
  }

  def insertNewLinkPropertyWithVVHColumn(table: String, column: String, vvhColumn: String, linkId: Long, username: Option[String], value: Int, mml_id: Option[Long], optionalVVHValue: Option[Int]) = {
    sqlu"""insert into #$table (id, link_id, #$column, modified_by, mml_id, vvh_administrative_class )
                   select primary_key_seq.nextval, $linkId, $value, $username, $mml_id, $optionalVVHValue
                   from dual
                   where not exists (select * from #$table where link_id = $linkId)""".execute
  }

  def updateExistingLinkPropertyRowWithVVHColumn(table: String, column: String, vvhColumn: String, linkId: Long, username: Option[String], existingValue: Int, value: Int, mmlId: Option[Long], optionalVVHValue: Option[Int]) = {
    if (existingValue != value) {
      sqlu"""update #$table
                 set #$column = $value,
                     mml_id = $mmlId,
                     modified_date = current_timestamp,
                     modified_by = $username,
                     #$vvhColumn = $optionalVVHValue
                 where link_id = $linkId""".execute
    }
  }

  def getLinkProperty(table: String, column: String, linkId: Long) = {
    sql"""select #$column from #$table where link_id = $linkId""".as[Int].firstOption
  }

  def expireExistingLinkPropertyRow(table: String, linkId: Long, username: Option[String]) = {
    sqlu"""update #$table
                 set valid_to = current_timestamp,
                     modified_date = current_timestamp,
                     modified_by = $username
                 where link_id = $linkId""".execute
  }

  def updateFunctionalClass(linkId: Long, username: Option[String], existingValue: Int, value: Int) = {
    updateExistingLinkPropertyRow(FunctionalClass, FunctionalClass, linkId, username, existingValue, value, None)
  }

  def deleteExistingLinkPropertyRow(table: String, column: String, linkId: Long) = {
      sqlu"""delete from #$table
                 where link_id = $linkId""".execute
  }

  def deleteTrafficDirection(linkId: Long) = {
    deleteExistingLinkPropertyRow(TrafficDirection, TrafficDirection, linkId)
  }

  def updateLinkType(linkId: Long, username: Option[String], existingValue: Int, value: Int) = {
    updateExistingLinkPropertyRow(LinkType, LinkType, linkId, username, existingValue, value, None)
  }

  def insertFunctionalClass(linkId: Long, username: Option[String], value: Int) = {
    insertNewLinkProperty(FunctionalClass, FunctionalClass, linkId, username, value, None)
  }

  def insertTrafficDirection(linkId: Long, username: Option[String], value: Int) = {
    insertNewLinkProperty(TrafficDirection, TrafficDirection, linkId, username, value, None)
  }

  def insertLinkType(linkId: Long, username: Option[String], value: Int) = {
    insertNewLinkProperty(LinkType, LinkType, linkId, username, value, None)
  }

  def getFunctionalClassValue(linkId: Long) = {
    getLinkProperty(FunctionalClass, FunctionalClass, linkId)
  }

  def getTrafficDirectionValue(linkId: Long) = {
    getLinkProperty(TrafficDirection, TrafficDirection, linkId)
  }

  def getLinkTypeValue(linkId: Long) = {
    getLinkProperty(LinkType, LinkType, linkId)
  }

  def updateAdministrativeClass(linkId: Long, username: Option[String], existingValue: Int, value: Int, mmlId: Option[Long], optionalVVHValue: Option[Int]) = {
    updateExistingLinkPropertyRowWithVVHColumn(AdministrativeClass, AdministrativeClass, VVHAdministrativeClass, linkId, username, existingValue, value, mmlId, optionalVVHValue)
  }

  def insertAdministrativeClass(linkId: Long, username: Option[String], value: Int, mmlId: Option[Long], optionalVVHValue: Option[Int]) = {
    insertNewLinkPropertyWithVVHColumn(AdministrativeClass, AdministrativeClass, VVHAdministrativeClass, linkId, username, value, mmlId, optionalVVHValue)
  }

  def getAdministrativeClassValue(linkId: Long) = {
    getLinkProperty(AdministrativeClass, AdministrativeClass, linkId)
  }

  def expireAdministrativeClass(linkId: Long, username: Option[String]) = {
    expireExistingLinkPropertyRow(AdministrativeClass, linkId, username)
  }

}

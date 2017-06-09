package fi.liikennevirasto.digiroad2.roadlinkservice.oracle

import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

object RoadLinkServiceDAO {
  private val FunctionalClass = "functional_class"
  private val TrafficDirection = "traffic_direction"
  private val LinkType = "link_type"
  private val AdministrativeClass = "administrative_class"

  def updateExistingLinkPropertyRow(table: String, column: String, linkId: Long, username: Option[String], existingValue: Int, value: Int) = {
    if (existingValue != value) {
      sqlu"""update #$table
                 set #$column = $value,
                     modified_date = current_timestamp,
                     modified_by = $username
                 where link_id = $linkId""".execute
    }
  }

  def insertNewLinkProperty(table: String, column: String, linkId: Long, username: Option[String], value: Int) = {
    sqlu"""insert into #$table (id, link_id, #$column, modified_by)
                   select primary_key_seq.nextval, $linkId, $value, $username
                   from dual
                   where not exists (select * from #$table where link_id = $linkId)""".execute
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
    updateExistingLinkPropertyRow(FunctionalClass, FunctionalClass, linkId, username, existingValue, value)
  }

  def deleteExistingLinkPropertyRow(table: String, column: String, linkId: Long) = {
      sqlu"""delete from #$table
                 where link_id = $linkId""".execute
  }

  def deleteTrafficDirection(linkId: Long) = {
    deleteExistingLinkPropertyRow(TrafficDirection, TrafficDirection, linkId)
  }

  def updateLinkType(linkId: Long, username: Option[String], existingValue: Int, value: Int) = {
    updateExistingLinkPropertyRow(LinkType, LinkType, linkId, username, existingValue, value)
  }

  def insertFunctionalClass(linkId: Long, username: Option[String], value: Int) = {
    insertNewLinkProperty(FunctionalClass, FunctionalClass, linkId, username, value)
  }

  def insertTrafficDirection(linkId: Long, username: Option[String], value: Int) = {
    insertNewLinkProperty(TrafficDirection, TrafficDirection, linkId, username, value)
  }

  def insertLinkType(linkId: Long, username: Option[String], value: Int) = {
    insertNewLinkProperty(LinkType, LinkType, linkId, username, value)
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

  def updateAdministrativeClass(linkId: Long, username: Option[String], existingValue: Int, value: Int) = {
    updateExistingLinkPropertyRow(AdministrativeClass, AdministrativeClass, linkId, username, existingValue, value)
  }

  def insertAdministrativeClass(linkId: Long, username: Option[String], value: Int) = {
    insertNewLinkProperty(AdministrativeClass, AdministrativeClass, linkId, username, value)
  }

  def getAdministrativeClassValue(linkId: Long) = {
    getLinkProperty(AdministrativeClass, AdministrativeClass, linkId)
  }

  def expireAdministrativeClass(linkId: Long, username: Option[String]) = {
    expireExistingLinkPropertyRow(AdministrativeClass, linkId, username)
  }

}

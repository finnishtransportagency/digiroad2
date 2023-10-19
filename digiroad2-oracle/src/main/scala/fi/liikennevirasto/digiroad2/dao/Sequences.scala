package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.dao.Queries._
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession

object Sequences {
  def nextPrimaryKeySeqValue = {
    nextPrimaryKeyId.as[Long].first
  }

  def nextPrimaryKeySeqValues(len: Int): Seq[Long] = {
    nextPrimaryKeyIds(len).as[Long].list
  }

  def nextLrmPositionPrimaryKeySeqValue = {
    nextLrmPositionPrimaryKeyId.as[Long].first
  }

  def nextGroupedIdSeqValue = {
    nextGroupedId.as[Long].first
  }

  def nextLaneHistoryEventOrderNumberValue: Long = {
    nextLaneHistoryEventOrderNumber.as[Long].first
  }

  def nextLaneHistoryEventOrderNumberValues(len: Int): Seq[Long] = {
    nextLaneHistoryEventOrderNumbers(len).as[Long].list
  }
}

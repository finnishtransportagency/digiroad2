package fi.liikennevirasto.digiroad2.asset.oracle

import fi.liikennevirasto.digiroad2.asset.oracle.Queries._
import scala.slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession

object Sequences {
  def nextPrimaryKeySeqValue = {
    nextPrimaryKeyId.as[Long].first
  }

  def nextLrmPositionPrimaryKeySeqValue = {
    nextLrmPositionPrimaryKeyId.as[Long].first
  }
}

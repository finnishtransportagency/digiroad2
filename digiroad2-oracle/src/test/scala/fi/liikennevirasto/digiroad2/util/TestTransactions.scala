package fi.liikennevirasto.digiroad2.util

import javax.sql.DataSource

import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession

object TestTransactions {
  def runWithRollback(ds: DataSource = OracleDatabase.ds)(f: => Unit): Unit = {
    Database.forDataSource(ds).withDynTransaction {
      f
      dynamicSession.rollback()
    }
  }
}

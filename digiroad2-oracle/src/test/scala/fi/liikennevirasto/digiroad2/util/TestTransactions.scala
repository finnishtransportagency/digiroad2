package fi.liikennevirasto.digiroad2.util

import javax.sql.DataSource

import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession

object TestTransactions {
  def runWithRollback(ds: DataSource = PostGISDatabase.ds)(f: => Unit): Unit = {
    Database.forDataSource(ds).withDynTransaction {
      f
      dynamicSession.rollback()
    }
  }
  def withDynTransaction[T](ds: DataSource = PostGISDatabase.ds)(f: => T): T = {
    Database.forDataSource(ds).withDynTransaction {
      f
    }
  }
  def withDynSession[T](ds: DataSource = PostGISDatabase.ds)(f: => T): T = {
    Database.forDataSource(ds).withDynSession {
      f
    }
  }
}

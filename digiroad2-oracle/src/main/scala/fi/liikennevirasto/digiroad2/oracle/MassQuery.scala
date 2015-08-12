package fi.liikennevirasto.digiroad2.oracle

import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

object MassQuery {
  def withIds[T](ids: Set[Long])(f: String => T): T = {
    val insertMmlIdPS = dynamicSession.prepareStatement("insert into temp_id (id) values (?)")
    ids.foreach { id =>
      insertMmlIdPS.setLong(1, id)
      insertMmlIdPS.addBatch()
    }
    insertMmlIdPS.executeBatch()
    f("temp_id")
  }
}
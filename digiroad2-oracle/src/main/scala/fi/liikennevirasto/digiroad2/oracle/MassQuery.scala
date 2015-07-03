package fi.liikennevirasto.digiroad2.oracle

import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession
import scala.slick.jdbc.StaticQuery.interpolation

object MassQuery {
  def withIds[T](ids: Set[Long])(f: String => T): T = {
    val insertMmlIdPS = dynamicSession.prepareStatement("insert into temporary_id (id) values (?)")
    ids.foreach { id =>
      insertMmlIdPS.setLong(1, id)
      insertMmlIdPS.addBatch()
    }
    insertMmlIdPS.executeBatch()
    val ret = f("temporary_id")
    sqlu"""delete from temporary_id""".execute()
    ret
  }
}
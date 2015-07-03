package fi.liikennevirasto.digiroad2.oracle

import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession
import scala.slick.jdbc.StaticQuery.interpolation

object MassQuery {
  def withIds[T](ids: Set[Long])(f: String => T): T = {
    val insertMmlIdPS = dynamicSession.prepareStatement("insert into temp_id (id) values (?)")
    ids.foreach { id =>
      insertMmlIdPS.setLong(1, id)
      insertMmlIdPS.addBatch()
    }
    insertMmlIdPS.executeBatch()
    val ret = f("temp_id")
    sqlu"""delete from temp_id""".execute()
    ret
  }
}
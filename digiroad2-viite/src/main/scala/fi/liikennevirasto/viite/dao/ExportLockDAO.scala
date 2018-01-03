package fi.liikennevirasto.viite.dao

import slick.jdbc.StaticQuery.interpolation

object ExportLockDAO {

  def insert: Unit = {
    sqlu"""INSERT INTO EXPORT_LOCK VALUES (1, SYSDATE)""".execute
  }

  def delete: Unit = {
    sqlu"""DELETE FROM EXPORT_LOCK"""
  }

}

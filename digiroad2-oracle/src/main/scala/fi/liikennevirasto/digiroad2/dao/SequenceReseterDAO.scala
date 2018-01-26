package fi.liikennevirasto.digiroad2.dao

import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery
import slick.jdbc.StaticQuery.interpolation

class SequenceReseterDAO {

  def resetSequenceToNumber(seqName: String, seqNumber: Long): Unit = {
    sqlu"""DROP SEQUENCE #$seqName """.execute
    sqlu"""CREATE SEQUENCE #$seqName START WITH #$seqNumber CACHE 20 INCREMENT BY 1""".execute
  }
}


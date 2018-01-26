package fi.liikennevirasto.digiroad2.dao

import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
/**
  * Created by alapeijario on 25.1.2018.
  */
class SequenceReseterDAO {

  def resetSequenceToNumber(seqName: String, seqNumber: Long): Unit = {
    sql""" DROP SEQUENCE $seqName"""
    sql"""CREATE SEQUENCE $seqName START WITH $seqNumber CACHE 20 INCREMENT BY 1"""
  }
}


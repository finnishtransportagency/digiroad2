package fi.liikennevirasto.digiroad2.dao

import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

class MunicipalityDao {

  def getMunicipalities: Seq[Int] = {
    sql"""
      select id from municipality
    """.as[Int].list
  }

  def getMunicipalityNameByCode(code: Int): String = {
    sql"""
      select name_fi from municipality where id = $code
    """.as[String].first
  }

  def getMunicipalityById(id: Long): Seq[Long] = {
    sql"""select id from municipality where id = $id """.as[Long].list
  }

}

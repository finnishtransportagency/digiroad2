package fi.liikennevirasto.digiroad2.dao

import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

class MunicipalityDao {

  def getMunicipalities: Seq[Int] = {
    sql"""
      select id from municipality
    """.as[Int].list
  }

  def getMunicipalityNameByCode(id: Int): String = {
    sql"""
      select name_fi from municipality where id = $id""".as[String].first
  }

  def getMunicipalityById(id: Int): Seq[Int] = {
    sql"""select id from municipality where id = $id """.as[Int].list
  }

  def getMunicipalitiesNameByCode(codes: Set[Int]): Seq[String] = {
    val filter = if (codes.nonEmpty) {"where id in " + codes.mkString(",") } else ""

    sql"""
      select name_fi from municipality
      #$filter
    """.as[String].list
  }

  def getMunicipalitiesNameandIdByCode(codes: Set[Int]): List[(Int, String)] = {
    val filter = if (codes.nonEmpty) {"where id in (" + codes.mkString(",") + ")" } else ""

    sql"""
      select id, name_fi from municipality
      #$filter
    """.as[(Int, String)].list
  }
}

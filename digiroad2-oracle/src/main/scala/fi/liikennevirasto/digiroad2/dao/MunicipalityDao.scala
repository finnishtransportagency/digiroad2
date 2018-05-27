package fi.liikennevirasto.digiroad2.dao

import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
case class MunicipalityInfo(id: Int, ely: Int, name: String)

class MunicipalityDao {

  def getMunicipalities: Seq[Int] = {
    sql"""
      select id from municipality
    """.as[Int].list
  }

  def getMunicipalitiesInfo: Seq[(Int, String)] = {
    sql"""
      select id, name_fi from municipality
    """.as[(Int, String)].list
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

  def getMunicipalitiesNameAndIdByCode(codes: Set[Int]): List[MunicipalityInfo] = {
    val filter = if (codes.nonEmpty) {"where id in (" + codes.mkString(",") + ")" } else ""

    sql"""
      select id, ely_nro, name_fi from municipality
      #$filter
    """.as[(Int, Int, String)].list
      .map{ case(id, ely, name) =>
        MunicipalityInfo(id, ely, name)}
  }
}

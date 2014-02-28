package fi.liikennevirasto.digiroad2.municipality.oracle

import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.jdbc.StaticQuery
import Database.dynamicSession
import StaticQuery.interpolation
import fi.liikennevirasto.digiroad2.municipality.MunicipalityProvider
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._

class OracleMunicipalityProvider extends MunicipalityProvider {

  def getMunicipalities(elyNumber: Int): Seq[Int] = {
    Database.forDataSource(ds).withDynSession {
      sql"""select municipality_id from ely where id = """.as[Int].list
    }
  }
}

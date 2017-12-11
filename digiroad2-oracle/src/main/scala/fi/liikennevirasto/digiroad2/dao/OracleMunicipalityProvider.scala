package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.municipality.MunicipalityProvider
import slick.jdbc.{SetParameter, StaticQuery}

class OracleMunicipalityProvider extends MunicipalityProvider {

  def seqParam[Int](implicit pconv: SetParameter[Int]): SetParameter[Set[Int]] = SetParameter {
    case (seq, pp) =>
      for (a <- seq) {
        pconv.apply(a, pp)
      }
  }

  implicit val parameterSetOfInts: SetParameter[Set[Int]] = seqParam[Int]

  def getMunicipalities(elyNumbers: Set[Int]): Seq[Int] = {
    OracleDatabase.withDynSession {
      val q = "select m.id from municipality m where m.ELY_NRO in (" + elyNumbers.toArray.map(_ => "?").mkString(",") + ")"
      StaticQuery.query[Set[Int], Int](q).apply(elyNumbers).list
    }
  }

}

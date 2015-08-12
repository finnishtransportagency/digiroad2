package fi.liikennevirasto.digiroad2.municipality.oracle

import slick.driver.JdbcDriver.backend.Database
import slick.jdbc.{PositionedParameters, SetParameter, StaticQuery}
import Database.dynamicSession
import StaticQuery.interpolation
import fi.liikennevirasto.digiroad2.municipality.MunicipalityProvider
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._

class OracleMunicipalityProvider extends MunicipalityProvider {

  def seqParam[Int](implicit pconv: SetParameter[Int]): SetParameter[Set[Int]] = SetParameter {
    case (seq, pp) =>
      for (a <- seq) {
        pconv.apply(a, pp)
      }
  }

  implicit val parameterSetOfInts: SetParameter[Set[Int]] = seqParam[Int]

  def getMunicipalities(elyNumbers: Set[Int]): Seq[Int] = {
    Database.forDataSource(ds).withDynSession {
      val q = "select municipality_id from ely where id in (" + elyNumbers.toArray.map(_ => "?").mkString(",") + ")"
      StaticQuery.query[Set[Int], Int](q).apply(elyNumbers).list
    }
  }

  Set(941, 170, 683, 614, 698, 417, 320, 751, 261, 766, 60, 65, 583, 742, 148, 732, 498, 736, 76, 318, 890, 758, 240, 851, 438, 241, 35, 295, 43, 845, 976, 771, 478, 854, 273, 47, 62)
  Set(683, 614, 698, 320, 751, 261, 583, 742, 148, 732, 498, 890, 758, 240, 851, 241, 845, 976, 854, 273, 47)
}

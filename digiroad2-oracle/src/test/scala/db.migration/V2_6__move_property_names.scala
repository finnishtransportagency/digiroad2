package db.migration

import com.googlecode.flyway.core.api.migration.jdbc.JdbcMigration
import java.sql.Connection

import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import slick.jdbc.{GetResult, PositionedParameters, PositionedResult, SetParameter, StaticQuery => Q}
import Q.interpolation
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import fi.liikennevirasto.digiroad2.asset.LocalizedString
import fi.liikennevirasto.digiroad2.dao.LocalizationDao

class V2_6__move_property_names extends JdbcMigration {
  def migrate(connection: Connection) {
    OracleDatabase.withDynTransaction {
      val originalProps = sql"""
          select name_fi from property
        """.as[String].list
      originalProps.foreach {
        p =>
          val ls = LocalizationDao.insertLocalizedString(LocalizedString(Map(LocalizedString.LangFi -> p)))
          sqlu"""
          update property set name_localized_string_id = ${ls.id.get} where name_fi = ${p}
        """.execute
      }
      sqlu"""
        alter table property drop column name_fi
      """.execute
      sqlu"""
        alter table property drop column name_sv
      """.execute
    }
  }
}


package db.migration

import com.googlecode.flyway.core.api.migration.jdbc.JdbcMigration
import java.sql.Connection
import slick.jdbc.{StaticQuery => Q, PositionedResult, GetResult, PositionedParameters, SetParameter}
import fi.liikennevirasto.digiroad2.asset.oracle.{LocalizationDao, OracleSpatialAssetDao, Queries}
import Queries._
import Q.interpolation
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import fi.liikennevirasto.digiroad2.asset.LocalizedString

class V2_6__move_property_names extends JdbcMigration {
  def migrate(connection: Connection) {
    Database.forDataSource(ds).withDynTransaction {
      val originalProps = sql"""
          select name_fi from property
        """.as[String].list()
      originalProps.foreach {
        p =>
          val ls = LocalizationDao.insertLocalizedString(LocalizedString(Map(LocalizedString.LangFi -> p)))
          sqlu"""
          update property set name_localized_string_id = ${ls.id.get} where name_fi = ${p}
        """.execute()
      }
      sqlu"""
        alter table property drop column name_fi
      """.execute()
      sqlu"""
        alter table property drop column name_sv
      """.execute()
    }
  }
}


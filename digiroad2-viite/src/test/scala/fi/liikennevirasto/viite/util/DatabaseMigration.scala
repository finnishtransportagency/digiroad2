package fi.liikennevirasto.viite.util

import com.googlecode.flyway.core.Flyway
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase.ds

object DatabaseMigration {
  def main(args: Array[String]) : Unit = {
    val flyway = new Flyway()
    flyway.setDataSource(ds)
    flyway.setLocations("db.migration")
    flyway.setTable("viite_schema_version")
    flyway.migrate()
  }
}

package fi.liikennevirasto.viite.util

import com.googlecode.flyway.core.Flyway
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase.ds

object DatabaseMigration {
  def main(args: Array[String]) : Unit = {
    val flyway = new Flyway()
    flyway.setDataSource(ds)
    flyway.setTable("schema_viite_version")
    flyway.setLocations("db.viite.migration")
    flyway.migrate()
  }
}

package fi.liikennevirasto.digiroad2.util

import com.googlecode.flyway.core.Flyway
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._

object DatabaseMigration {
  def main(args: Array[String]) : Unit = {
    val flyway = new Flyway()
    flyway.setDataSource(ds)
    flyway.setLocations("db.migration")
    flyway.migrate()
    // TODO: Remove temp_id table recreate once temp_id table stays performant over several deployments
    SqlScriptRunner.runScript("recreate_temp_id_table.sql")
  }
}

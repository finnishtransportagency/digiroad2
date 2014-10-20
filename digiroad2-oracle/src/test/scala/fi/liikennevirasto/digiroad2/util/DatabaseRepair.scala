package fi.liikennevirasto.digiroad2.util

import com.googlecode.flyway.core.Flyway
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._

object DatabaseRepair {
  def main(args: Array[String]): Unit = {
    println("*** RUNNING FLYWAY REPAIR ***")
    println(ds)
    val flyway = new Flyway()
    flyway.setDataSource(ds)
    flyway.setLocations("db.migration")
    flyway.repair()
  }
}

package db.migration

import com.googlecode.flyway.core.api.migration.jdbc.JdbcMigration
import java.sql.Connection
import fi.liikennevirasto.digiroad2.util.BusStopIconImageData

class V2_4__insert_virtual_bus_stop_image extends JdbcMigration {
  def migrate(connection: Connection) {
    BusStopIconImageData.insertImages("db_migration_v2.4", "Pysäkin tyyppi", Map("5" -> "/virtuaalipysakki.png"))
  }
}

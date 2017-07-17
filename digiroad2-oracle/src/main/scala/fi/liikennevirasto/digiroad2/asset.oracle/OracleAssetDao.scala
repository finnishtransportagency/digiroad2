package fi.liikennevirasto.digiroad2.asset.oracle

import org.joda.time.DateTime
import slick.jdbc.StaticQuery.interpolation

class OracleAssetDao {
  def getLastExecutionDate(typeId: Int, createddBy: String): DateTime = {
    sql"""
      select MAX(a.created_date)
      from asset a
      where a.created_by = $createddBy
        and a.type_type_id = $typeId
    """.as[DateTime].first
  }
}


package fi.liikennevirasto.digiroad2.pointasset.oracle

import fi.liikennevirasto.digiroad2.PointAsset
import fi.liikennevirasto.digiroad2.oracle.MassQuery
import slick.jdbc.StaticQuery.interpolation
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession

trait OraclePointAssetDao {
  def getByMmldIds(mmlIds: Seq[Long]): Seq[PointAsset] = {
    val assets = MassQuery.withIds(mmlIds.toSet) { idTableName =>
      sql"""
        select a.id, pos.mml_id
        from asset a
        join asset_link al on a.id = al.asset_id
        join lrm_position pos on al.position_id = pos.id
        join  #$idTableName i on i.id = pos.mml_id
        where a.asset_type_id = 200 and floating = 0
       """.as[(Long, Long)].list
    }
    assets.map { a => PointAsset(a._1, a._2) }
  }

}

object OraclePointAssetDao extends OraclePointAssetDao

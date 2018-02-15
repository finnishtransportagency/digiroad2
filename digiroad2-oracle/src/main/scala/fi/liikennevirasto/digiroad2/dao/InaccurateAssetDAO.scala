package fi.liikennevirasto.digiroad2.dao

import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

class InaccurateAssetDAO {

  def createInaccurateAsset(assetId: Long, assetTypeId: Long) = {
    sqlu"""
        insert into inaccurate_asset (asset_id, asset_type_id)
        values ($assetId, $assetTypeId)
      """.execute
  }

  def getInaccurateAssetById(assetId: Long) = {
    sql"""select asset_id, asset_type_id from inaccurate_asset where asset_id= $assetId""".as[(Long, Long)].list
  }

  def getInaccurateAssetByTypeId(assetTypeId: Long) = {
    sql"""select asset_id, asset_type_id from inaccurate_asset where asset_type_id= $assetTypeId""".as[(Long, Long)].list
  }

  def deleteInaccurateAssetById(assetId: Long) = {
    sqlu"""delete from inaccurate_asset where asset_id= $assetId""".execute
  }

  def deleteAllInaccurateAssets(assetTypeId: Option[Long] = None) = {
    val where: String = assetTypeId match {
      case Some(typeId) => " where asset_type_id= " + typeId + ""
      case _ => ""
    }
    sqlu"""delete from inaccurate_asset #$where""".execute
  }
}


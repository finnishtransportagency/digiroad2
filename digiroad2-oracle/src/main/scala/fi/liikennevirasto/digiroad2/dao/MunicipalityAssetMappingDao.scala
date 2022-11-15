package fi.liikennevirasto.digiroad2.dao

import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.{GetResult, PositionedResult}
import slick.jdbc.StaticQuery.interpolation

case class MunicipalityAssetMapping(assetId: Long, municipalityAssetId: String, municipality: Int)

class MunicipalityAssetMappingDao {

  implicit val getMunicipalityAssetMapping = new GetResult[MunicipalityAssetMapping] {
    def apply(r: PositionedResult): MunicipalityAssetMapping = {
      val assetId = r.nextLong()
      val municipalityAssetId = r.nextString()
      val municipality = r.nextInt()

      MunicipalityAssetMapping(assetId, municipalityAssetId, municipality)
    }
  }

  def getByAssetId(assetId: Long): Option[MunicipalityAssetMapping] = {
    sql"""
        select asset_id, municipality_asset_id, municipality_code
        from municipality_asset_id_mapping
        where asset_id = $assetId
    """.as[MunicipalityAssetMapping].firstOption
  }

  def replaceAssetId(oldAssetId: Long, newAssetId: Long): Long = {
    sqlu"""
        update municipality_asset_id_mapping
        set asset_id = $newAssetId
        where asset_id = $oldAssetId
       """.execute
    newAssetId
  }
}

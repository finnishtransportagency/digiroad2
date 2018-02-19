package fi.liikennevirasto.digiroad2.dao

import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

class InaccurateAssetDAO {

  def createInaccurateAsset(assetId: Long, assetTypeId: Long, municipalityCode: Long, areaCode: Long, administrativeClass: Long) = {
    sqlu"""
        insert into inaccurate_asset (asset_id, asset_type_id, municipality_code, area_code, administrative_class)
        values ($assetId, $assetTypeId, $municipalityCode, $areaCode, $administrativeClass)
      """.execute
  }

  def getInaccurateAssetById(assetId: Long) = {
    sql"""select asset_id, asset_type_id from inaccurate_asset where asset_id= $assetId""".as[(Long, Long)].list
  }

  def getInaccurateAssetByTypeId(assetTypeId: Long, municipalities: Option[Set[Int]] = None, areas: Option[Set[Int]] = None,
                                 adminClassAllow: Option[Set[Int]] = None): List[Long] = {
    val optionalMunicipalities = municipalities.map(_.mkString(","))
    val optionalAreas = areas.map(_.mkString(","))
    val optionaladminClassAllow = adminClassAllow.map(_.mkString(","))

    val withAuthorizedMunicipalities = optionalMunicipalities match {
      case Some(municipality) => s" and municipality_code in ($municipality)"
      case _ => s" "
    }

    val withAuthorizedAreas = optionalAreas match {
      case Some(area) => s" and area_code in ($area)"
      case _ => s" "
    }

    val withAdminClassRestrictions = optionaladminClassAllow match {
      case Some(adminClass) => s" and administrative_class in ($adminClass)"
      case _ => s" "
    }

    sql"""
       select asset_id
       from inaccurate_asset
       where asset_type_id= $assetTypeId #$withAuthorizedMunicipalities #$withAuthorizedAreas #$withAdminClassRestrictions
     """.as[Long].list
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


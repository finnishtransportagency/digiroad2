package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.asset.AdministrativeClass
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

class InaccurateAssetDAO {

  def createInaccurateAsset(assetId: Long, typeId: Int, municipalityCode: Long, areaCode: Int, administrativeClass: Long) = {
    sqlu"""
        insert into inaccurate_asset (asset_id, asset_type_id, municipality_code, area_code, administrative_class)
        values ($assetId, $typeId, $municipalityCode, $areaCode, $administrativeClass)
      """.execute
  }

  def getInaccurateAssetById(assetId: Long) = {
    sql"""select asset_id, asset_type_id from inaccurate_asset where asset_id= $assetId""".as[(Long, Long)].list
  }

  def getInaccurateAssetByTypeId(typeId: Int, municipalities: Option[Set[Int]] = None, areas: Option[Set[Int]] = None,
                                 adminClassAllow: Option[Set[Int]] = None): List[(Long, String, Int, String)] = {

    val optionalMunicipalities = municipalities.map(_.mkString(","))
    val optionalAreas = areas.map(_.mkString(","))
    val optionalAdminClassAllow = adminClassAllow.map(_.mkString(","))

    val withAuthorizedMunicipalities = optionalMunicipalities match {
      case Some(municipality) => s" and ia.municipality_code in ($municipality)"
      case _ => s" "
    }

    val withAuthorizedAreas = optionalAreas match {
      case Some(area) => s" and ia.area_code in ($area)"
      case _ => s" "
    }

    val withAdminClassRestrictions = optionalAdminClassAllow match {
      case Some(adminClass) => s" and ia.administrative_class in ($adminClass)"
      case _ => s" "
    }

    sql"""
       select ia.asset_id, m.name_fi, ia.administrative_class, TO_CHAR(ia.area_code)
       from inaccurate_asset ia,
            municipality m
       where ia.asset_type_id= $typeId #$withAuthorizedMunicipalities #$withAuthorizedAreas #$withAdminClassRestrictions
       and ia.municipality_code = m.id
     """.as[(Long, String, Int, String)].list
  }

  def deleteInaccurateAssetById(assetId: Long) = {
    sqlu"""delete from inaccurate_asset where asset_id= $assetId""".execute
  }

  def deleteAllInaccurateAssets(typeId: Option[Int] = None) = {
    val where: String = typeId match {
      case Some(id) => " where asset_type_id= " + id + ""
      case _ => ""
    }
    sqlu"""delete from inaccurate_asset #$where""".execute
  }
}


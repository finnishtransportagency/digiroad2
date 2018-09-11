package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.asset.AdministrativeClass
import fi.liikennevirasto.digiroad2.linearasset.InaccurateLinearAsset
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

class InaccurateAssetDAO {

  def createInaccurateAsset(assetId: Long, typeId: Int, municipalityCode: Int, administrativeClass: AdministrativeClass) = {
    sqlu"""
        insert into inaccurate_asset (asset_id, asset_type_id, municipality_code, administrative_class)
        values ($assetId, $typeId, $municipalityCode, ${administrativeClass.value})
      """.execute
  }

  def createInaccurateLink(linkId: Long, typeId: Int, municipalityCode: Int, administrativeClass: AdministrativeClass) = {
    sqlu"""
        insert into inaccurate_asset (link_id, asset_type_id, municipality_code, administrative_class)
        values ($linkId, $typeId, $municipalityCode, ${administrativeClass.value})
      """.execute
  }

  def getInaccurateAssetById(assetId: Long): Option[Long] = {
    sql"""select asset_id from inaccurate_asset where asset_id= $assetId""".as[Long].firstOption
  }

  def existsOnInaccurateWithLinkId(linkId: Long): Option[Long] = {
    sql"""select linkId from inaccurate_asset where link_id= $linkId""".as[Long].firstOption
  }

  def getInaccurateAssetByTypeId(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()): List[(Long, String, Int)] = {

    val withAuthorizedMunicipalities =
      if (municipalities.nonEmpty) s" and ia.municipality_code in (${municipalities.mkString(",")})"  else s""

    val withAdminClassRestrictions =
      if(adminClass.nonEmpty) s" and ia.administrative_class in (${adminClass.map(_.value).mkString(",")})" else s""

    sql"""
       select ia.asset_id, m.name_fi, ia.administrative_class
       from inaccurate_asset ia
       left join municipality m on ia.municipality_code = m.id
       where ia.asset_type_id = $typeId #$withAuthorizedMunicipalities #$withAdminClassRestrictions
     """.as[(Long, String, Int)].list
  }

  def getInaccurateAsset(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()): Seq[InaccurateLinearAsset] = {

    val withAuthorizedMunicipalities =
      if (municipalities.nonEmpty) s" and ia.municipality_code in (${municipalities.mkString(",")})"  else s""

    val withAdminClassRestrictions =
      if(adminClass.nonEmpty) s" and ia.administrative_class in (${adminClass.map(_.value).mkString(",")})" else s""

    val inacurates = sql"""
       select ia.asset_id, m.name_fi, ia.administrative_class, ia.link_id
       from inaccurate_asset ia
       left join municipality m on ia.municipality_code = m.id
       where ia.asset_type_id = $typeId #$withAuthorizedMunicipalities #$withAdminClassRestrictions
     """.as[(Option[Long], String, Int, Option[Long])].list

    inacurates.map{ case(asseId, municipality, administrativeClass, linkId ) =>
      InaccurateLinearAsset(asseId, municipality, AdministrativeClass(administrativeClass).toString, linkId)
    }
  }

  def getInaccurateByTypeId(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()): List[(Long, String, Int, Long)] = {

    val withAuthorizedMunicipalities =
      if (municipalities.nonEmpty) s" and ia.municipality_code in (${municipalities.mkString(",")})"  else s""

    val withAdminClassRestrictions =
      if(adminClass.nonEmpty) s" and ia.administrative_class in (${adminClass.map(_.value).mkString(",")})" else s""

    sql"""
       select ia.asset_id, m.name_fi, ia.administrative_class, ia.link_id
       from inaccurate_asset ia
       left join municipality m on ia.municipality_code = m.id
       where ia.asset_type_id = $typeId #$withAuthorizedMunicipalities #$withAdminClassRestrictions
     """.as[(Long, String, Int, Long)].list
  }


  def deleteInaccurateAssetById(assetId: Long) = {
    sqlu"""delete from inaccurate_asset where asset_id= $assetId""".execute
  }

  def deleteInaccurateLinkId(linkId: Long) = {
    sqlu"""delete from inaccurate_asset where link_id= $linkId""".execute
  }

  def deleteAllInaccurateAssets(typeId: Int) = {
    sqlu"""delete from inaccurate_asset
          where asset_type_id = $typeId""".execute
  }

  def deleteInaccurateAssetByIds(assetIds: Set[Long]): Unit = {
    sqlu"""delete from inaccurate_asset where asset_id in (#${assetIds.mkString(",")})""".execute
  }

  def deleteInaccurateAssetByLinkIds(linkIds: Set[Long], typeId: Int): Unit = {
    sqlu"""delete from inaccurate_asset where link_id in (#${linkIds.mkString(",")}) and asset_type_id = $typeId""".execute
  }
}


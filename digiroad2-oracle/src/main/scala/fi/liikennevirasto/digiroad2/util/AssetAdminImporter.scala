package fi.liikennevirasto.digiroad2.util
import scala.slick.driver.JdbcDriver.backend.{Database, DatabaseDef, Session}
import scala.slick.jdbc.{StaticQuery => Q, _}
import Database.dynamicSession
import Q.interpolation

object AssetAdminImporter {
  var counter = 0
  def toAdminUpdateSql(values: (Long, Option[(String, String)])) = {
    counter = counter + 1
    if(counter % 50 == 0) println(s"$counter items processed")
    val (assetId, adminCode) = values
    val adminCodeValue = adminCode.map(x => x._1).map(x => if(x == null) null else s"'$x'").getOrElse("'ei tiedossa'")
    val adminValue = adminCode.map(x => x._2).map(x => if(x == null) 99 else x.toInt).getOrElse(99)
    (getSqlForTextProperty(assetId, adminCodeValue, "Ylläpitäjän tunnus"), getSqlForAdministrator(assetId, adminValue))
  }

  def toNameUpdateSql(values: (Long, Option[(String, String)])) = {
    counter = counter + 1
    if(counter % 50 == 0) println(s"$counter items processed")
    val (assetId, names) = values
    val (nameFi, nameSv) = names.getOrElse((null, null))
    def getName(name: String) = if(name == null) null else s"'$name'"
    (getSqlForTextProperty(assetId, getName(nameFi), "Nimi suomeksi"), getSqlForTextProperty(assetId, getName(nameSv), "Nimi ruotsiksi"))
  }

  def toFunctionalClassUpdate(roadlinkId: Long, functionalClass: Int) = {
    counter = counter + 1
    if(counter % 10000 == 0) println(s"$counter items processed")
    getSqlForFunctionalClass(roadlinkId, functionalClass)
  }

  private def getSqlForTextProperty(assetId: Long, value: String, propertyName: String) = {
    s"""
      |update text_property_value set value_fi = $value, modified_by = 'dr1conversion', modified_date = CURRENT_TIMESTAMP
      |   where property_id = (select id from property where name_fi = '$propertyName') and asset_id = $assetId;
      |insert into text_property_value(id, property_id, asset_id, value_fi, created_by)
      |   SELECT primary_key_seq.nextval, (select id from property where NAME_FI = '$propertyName'), $assetId, $value, 'dr1conversion' from dual
      |   WHERE NOT EXISTS (select 1 from text_property_value
      |                     where property_id = (select id from property where name_fi = '$propertyName') and asset_id = $assetId);
    """.stripMargin
  }

  private def getSqlForAdministrator(assetId: Long, value: Int) = {
    s"""
      |update single_choice_value set enumerated_value_id = (select id from enumerated_value where value = $value and property_id = (select id from property where NAME_FI = 'Tietojen ylläpitäjä')), modified_by = 'dr1conversion', modified_date = CURRENT_TIMESTAMP
      |          where property_id = (select id from property where NAME_FI = 'Tietojen ylläpitäjä') and asset_id = $assetId;
      |insert into single_choice_value(property_id, asset_id, enumerated_value_id, modified_by, modified_date)
      |         SELECT (select id from property where NAME_FI = 'Tietojen ylläpitäjä'), $assetId, (select id from enumerated_value where value = $value and property_id = (select id from property where NAME_FI = 'Tietojen ylläpitäjä')), 'dr1conversion', CURRENT_TIMESTAMP from dual
      |         WHERE NOT EXISTS (select 1 from single_choice_value
      |                           where property_id = (select id from property where name_fi = 'Tietojen ylläpitäjä') and asset_id = $assetId);
    """.stripMargin
  }

  private def getSqlForFunctionalClass(roadlinkId: Long, value: Int) = {
    s"""update asset set functional_class = $value where id = $roadlinkId;"""
  }

  def getAdminCodesFromDr1(dataSet: AssetDataImporter.ImportDataSet, externalId: Long) = {
    dataSet.database().withDynSession {
      try {
        val external = externalId.toString
        sql"""
          select yllaptunnus, administrator_type from lineaarilokaatio where pysakki_id = $external
        """.as[(String, String)].firstOption
      } catch {
        case e: Exception =>
          println(s"failed with id $externalId")
          throw e
      }
    }
  }

  def getFunctionalClassesFromDr1(dataSet: AssetDataImporter.ImportDataSet, roadlinkId: Long) = {
    dataSet.database().withDynSession {
      try {
        val external = roadlinkId.toString
        sql"""
          select objectid, functionalroadclass from tielinkki where objectid = $external
        """.as[(Long, Int)].first
      } catch {
        case e: Exception =>
          println(s"failed with id $roadlinkId")
          throw e
      }
    }
  }

  def getNamesFromDr1(dataSet: AssetDataImporter.ImportDataSet, externalId: Long) = {
    dataSet.database().withDynSession {
      try {
        val external = externalId.toString
        sql"""
          select nimi_fi, nimi_sv from lineaarilokaatio where pysakki_id = $external
        """.as[(String, String)].firstOption
      } catch {
        case e: Exception =>
          println(s"failed with id $externalId")
          throw e
      }
    }
  }
}

class AssetAdminImporter extends AssetDataImporter {

  def getAssetIdsQuery: StaticQuery0[(Long, Long)] = {
    sql"""
        select id, external_id from asset
    """.as[(Long, Long)]
  }

  def getAssetIds(toSqlFunc: ((Long, Option[(String, String)])) => (String, String),
                  dataFromDr1: (AssetDataImporter.ImportDataSet, Long) => Option[(String, String)]) = {
    getAssetIdsQuery
      .mapResult((x: (Long, Long)) => (x._1, dataFromDr1(AssetDataImporter.Conversion, x._2)))
      .mapResult(toSqlFunc)
  }

  def getRoadLinkQuery: StaticQuery0[(Long)] = {
    sql"""
        select id from road_link
    """.as[(Long)]
  }

  def getFunctionalClasses(toSqlFunc: ((Long, Int) => String),
                           dataFromDr1: (AssetDataImporter.ImportDataSet, Long) => (Long, Int)) = {
    getRoadLinkQuery
      .mapResult((x: (Long)) => dataFromDr1(AssetDataImporter.Conversion, x))
      .mapResult(x => toSqlFunc(x._1, x._2))
  }
}

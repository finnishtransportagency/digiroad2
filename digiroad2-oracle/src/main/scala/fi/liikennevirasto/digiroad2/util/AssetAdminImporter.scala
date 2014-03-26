package fi.liikennevirasto.digiroad2.util
import scala.slick.driver.JdbcDriver.backend.{Database, DatabaseDef, Session}
import scala.slick.jdbc.{StaticQuery => Q, _}
import Database.dynamicSession
import Q.interpolation

object AssetAdminImporter {
  var counter = 0
  def toUpdateSql(values: (Long, Option[(String, String)])) = {
    counter = counter + 1
    if(counter % 50 == 0) println(s"$counter items processed")
    val (assetId, adminCode) = values
    val adminCodeValue = adminCode.map(x => x._1).map(x => if(x == null) null else s"'$x'").getOrElse("'ei tiedossa'")
    val adminValue = adminCode.map(x => x._2).map(x => if(x == null) 99 else x.toInt).getOrElse(99)
    (getSqlForAdminCode(assetId, adminCodeValue), getSqlForAdministrator(assetId, adminValue))
  }

  private def getSqlForAdminCode(assetId: Long, value: String) = {
    s"""
      |update text_property_value set value_fi = $value, modified_by = 'dr1conversion', modified_date = CURRENT_TIMESTAMP
      |   where property_id = (select id from property where name_fi = 'Ylläpitäjän tunnus') and asset_id = $assetId;
      |insert into text_property_value(id, property_id, asset_id, value_fi, created_by)
      |   SELECT primary_key_seq.nextval, (select id from property where NAME_FI = 'Ylläpitäjän tunnus'), $assetId, $value, 'dr1conversion' from dual
      |   WHERE NOT EXISTS (select 1 from text_property_value
      |                     where property_id = (select id from property where name_fi = 'Ylläpitäjän tunnus') and asset_id = $assetId);
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
}

class AssetAdminImporter extends AssetDataImporter {

  def getAssetIds(toSqlFunc: ((Long, Option[(String, String)])) => (String, String)) = {
    sql"""
        select id, external_id from asset
      """.as[(Long, Long)]
      .mapResult((x: (Long, Long)) => (x._1, getAdminCodeFromDr1(AssetDataImporter.Conversion, x._2)))
      .mapResult(toSqlFunc)
  }

  def getAdminCodeFromDr1(dataSet: AssetDataImporter.ImportDataSet, externalId: Long) = {
    dataSet.database().withDynSession {
      try {
        sql"""
          select segm_pysakki_yllap_tunnus, segm_omistaja_tyyppi from segmentti where segm_pysakki_valtak_tunnus = $externalId
      """.as[(String, String)].firstOption
      } catch {
        case e: Exception =>
          println(s"failed with id $externalId")
          throw e
      }
    }
  }
}

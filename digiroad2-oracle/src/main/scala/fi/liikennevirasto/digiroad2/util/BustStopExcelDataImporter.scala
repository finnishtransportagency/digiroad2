package fi.liikennevirasto.digiroad2.util

import javax.sql.DataSource
import com.jolbox.bonecp.BoneCPDataSource
import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.jdbc.{StaticQuery => Q, GetResult}
import Database.dynamicSession
import Q.interpolation
import org.apache.commons.lang3.StringUtils.{trimToEmpty, isBlank}

class BustStopExcelDataImporter {
  val Updater = "excel_data_migration"
  lazy val convDs: DataSource = initConversionDataSource
  lazy val excelDs: DataSource = initExcelDataSource

  case class ExcelBusStopData(externalId: Long, stopNameFi: String, stopNameSv: String, direction: String, reachability: String, accessibility: String, internalId: String, equipments: String)

  implicit val getExcelBusStopData = GetResult[ExcelBusStopData](r => ExcelBusStopData(r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<))

  private[this] def initExcelDataSource: DataSource = {
    Class.forName("oracle.jdbc.driver.OracleDriver")
    val ds = new BoneCPDataSource()
    ds.setJdbcUrl("jdbc:oracle:thin:@livispr01n1l-vip:1521/drkonv")
    ds.setUsername("dr2sample2")
    ds.setPassword("dr2sample2")
    ds
  }

  private[this] def initConversionDataSource: DataSource = {
    Class.forName("oracle.jdbc.driver.OracleDriver")
    val ds = new BoneCPDataSource()
    ds.setJdbcUrl("")
    ds.setUsername("")
    ds.setPassword("")
    ds
  }

  def importDataFromExcel(): List[ExcelBusStopData] = {
    Database.forDataSource(excelDs).withDynTransaction {
      sql"""
        select valtak_tunnus, pysakin_nimi, pysakin_nimi_se, SUUNTA_KANSALAISEN_NAKOK, PYSAKIN_SAAVUTETTAVUUS, ESTEETTOMYYS_TIEDOT, YLLAPITAJAN_SISAINEN_ID, VARUSTEET_MUOKKAUSSARAKE from excel_unique
      """.as[ExcelBusStopData].list
    }
  }

  def insertExcelData(ed: List[ExcelBusStopData]) {
    Database.forDataSource(convDs).withDynTransaction {
      ed.foreach{ row =>
        val assetIds = sql"""
          select id from asset where external_id = ${row.externalId}
        """.as[Long].list

        if (assetIds.nonEmpty) {
          assetIds.foreach { assetId =>
            println("UPDATING ASSET: " + assetId + " WITH EXTERNAL ID: " + row.externalId)

            sqlu"""
              update asset set modified_by = ${Updater}, modified_date = CURRENT_TIMESTAMP where id = ${assetId}
            """.execute

            insertTextPropertyValue(assetId, "Nimi suomeksi", row.stopNameFi)

            insertTextPropertyValue(assetId, "Nimi ruotsiksi", row.stopNameSv)

            insertTextPropertyValue(assetId, "Liikennöintisuunta", row.direction)

            val equipments = trimToEmpty(row.equipments)
            if (equipments.toLowerCase.contains("penkk") || equipments.toLowerCase.contains("penkillinen")) setSingleChoicePropertyToYes(assetId, "Varusteet (Penkki)")
            if (equipments.toLowerCase.contains("katos")) setSingleChoicePropertyToYes(assetId, "Varusteet (Katos)")
            if (equipments.toLowerCase.contains("mainoskatos")) setSingleChoicePropertyToYes(assetId, "Varusteet (Mainoskatos)")
            if (equipments.toLowerCase.contains("aikataulu")) setSingleChoicePropertyToYes(assetId, "Varusteet (Aikataulu)")
            if (equipments.toLowerCase.contains("pyöräteline")) setSingleChoicePropertyToYes(assetId, "Varusteet (Pyöräteline)")

            if (trimToEmpty(row.reachability).contains("pysäkointi")) setSingleChoicePropertyToYes(assetId, "Saattomahdollisuus henkilöautolla")

            insertTextPropertyValue(assetId, "Liityntäpysäköinnin lisätiedot", row.reachability)

            insertTextPropertyValue(assetId, "Esteettömyys liikuntarajoitteiselle", row.accessibility)

            insertTextPropertyValue(assetId, "Ylläpitäjän tunnus", row.internalId)

            insertTextPropertyValue(assetId, "Lisätiedot", equipments)
          }
        } else {
          println("NO ASSET FOUND FOR EXTERNAL ID: " + row.externalId)
        }
      }
    }
  }

  def insertTextPropertyValue(assetId: Long, propertyName: String, value: String) {
    if (isBlank(value)) return

    val propertyId = sql"""
      select id from text_property_value
      where property_id = (select id from property where name_fi = ${propertyName})
      and asset_id = ${assetId}
    """.as[Long].firstOption

    propertyId match {
      case None => {
        println("  CREATING PROPERTY VALUE: '" + propertyName + "' WITH VALUE: '" + value + "'")
        sqlu"""
          insert into text_property_value(id, property_id, asset_id, value_fi, created_by)
          values (primary_key_seq.nextval, (select id from property where NAME_FI = ${propertyName}), ${assetId}, ${value}, ${Updater})
        """.execute
      }
      case _ => {
        println("  UPDATING PROPERTY VALUE: '" + propertyName + "' WITH VALUE: '" + value + "'")
        sqlu"""
          update text_property_value set value_fi = ${value}, modified_by = ${Updater}, modified_date = CURRENT_TIMESTAMP
          where id = ${propertyId}
        """.execute
      }
    }
  }

  def setSingleChoicePropertyToYes(assetId: Long, propertyName: String) {
    val Yes = 2
    val propertyId = sql"""select id from property where name_fi = ${propertyName}""".as[Long].first
    val existingProperty = sql"""select property_id from single_choice_value where property_id = ${propertyId} and asset_id = ${assetId}""".as[Long].firstOption

    println("  SETTING SINGLE CHOICE VALUE: '" + propertyName + "' TO: 'Kyllä'")
    existingProperty match {
      case None => {
        sqlu"""
          insert into single_choice_value(property_id, asset_id, enumerated_value_id, modified_by, modified_date)
          values (${propertyId}, ${assetId}, (select id from enumerated_value where value = ${Yes} and property_id = ${propertyId}), ${Updater}, CURRENT_TIMESTAMP)
        """.execute
      }
      case _ => {
        sqlu"""
          update single_choice_value set enumerated_value_id = (select id from enumerated_value where value = ${Yes} and property_id = ${propertyId}), modified_by = ${Updater}, modified_date = CURRENT_TIMESTAMP
          where property_id = $propertyId and asset_id = ${assetId}
        """.execute
      }
    }
  }
}

object BustStopExcelDataImporter extends App {
  val importer = new BustStopExcelDataImporter()
  importer.insertExcelData(importer.importDataFromExcel())
}

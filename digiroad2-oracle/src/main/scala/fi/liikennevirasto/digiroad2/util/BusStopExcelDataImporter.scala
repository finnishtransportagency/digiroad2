package fi.liikennevirasto.digiroad2.util

import javax.sql.DataSource
import com.jolbox.bonecp.BoneCPDataSource
import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.jdbc.{StaticQuery => Q, GetResult}
import Database.dynamicSession
import Q.interpolation
import org.apache.commons.lang3.StringUtils.{trimToEmpty, isBlank}
import com.github.tototoshi.csv._
import java.io.{InputStreamReader}

class BusStopExcelDataImporter {
  val Updater = "excel_data_migration"
  val No = 1
  val Yes = 2
  lazy val convDs: DataSource = initConversionDataSource
  lazy val excelDs: DataSource = initExcelDataSource

  case class ExcelBusStopData(externalId: Long, stopNameFi: String, stopNameSv: String, direction: String, reachability: String, accessibility: String, internalId: String, equipments: String, xPosition: String, yPosition: String, passengerId: Option[String] = None)

  implicit val getExcelBusStopData = GetResult[ExcelBusStopData](r => ExcelBusStopData(r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<, r.<<))

  implicit object SemicolonSeparatedValues extends DefaultCSVFormat {
    override val delimiter = ';'
  }

  private[this] def initExcelDataSource: DataSource = {
    Class.forName("oracle.jdbc.driver.OracleDriver")
    val ds = new BoneCPDataSource()
    ds.setJdbcUrl("")
    ds.setUsername("")
    ds.setPassword("")
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

  def readExcelDataFromDb(): List[ExcelBusStopData] = {
    Database.forDataSource(excelDs).withDynTransaction {
      sql"""
        select valtak_tunnus, pysakin_nimi, pysakin_nimi_se, SUUNTA_KANSALAISEN_NAKOK, PYSAKIN_SAAVUTETTAVUUS, ESTEETTOMYYS_TIEDOT, YLLAPITAJAN_SISAINEN_ID, VARUSTEET_MUOKKAUSSARAKE, X_ETRS_TM35FIN, Y_ETRS_TM35FIN from excel_unique
      """.as[ExcelBusStopData].list
    }
  }

  def readExcelDataFromCsvFile(): List[ExcelBusStopData] = {
    val reader = CSVReader.open(new InputStreamReader(getClass.getResourceAsStream("/pysakkitiedot.csv")))
    reader.allWithHeaders().map { row =>
      new ExcelBusStopData(row("Valtakunnallinen ID").toLong, row("Pysäkin nimi"), row("Pysäkin nimi SE"), row("Suunta kansalaisen näkökulmasta"),
        row("Pysäkin saavutettavuus"), row("Esteettömyys-tiedot"), row("Ylläpitäjän sisäinen pysäkki-ID"), row("Pysäkin varusteet"),
        row("X_ETRS-TM35FIN"), row("Y_ETRS-TM35FIN"), row.get("Pysäkin tunnus matkustajalle"))
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
            if (equipments.toLowerCase.contains("penkk") || equipments.toLowerCase.contains("penkillinen")) setSingleChoiceProperty(assetId, "Penkki", Yes)
            if (equipments.toLowerCase.contains("katos") || equipments.toLowerCase.contains("omniselter")) setSingleChoiceProperty(assetId, "Katos", Yes)
            if (equipments.toLowerCase.contains("mainoskatos")) setSingleChoiceProperty(assetId, "Mainoskatos", Yes)
            if (equipments.toLowerCase.contains("aikataulu")) setSingleChoiceProperty(assetId, "Aikataulu", Yes)
            if (equipments.toLowerCase.contains("ei aikataulu")) setSingleChoiceProperty(assetId, "Aikataulu", No)
            if (equipments.toLowerCase.contains("pyöräteline")) setSingleChoiceProperty(assetId, "Pyöräteline", Yes)
            if (equipments.toLowerCase.contains("ei katos")) setSingleChoiceProperty(assetId, "Katos", No)

            if (trimToEmpty(row.reachability).contains("pysäkointi")) setSingleChoiceProperty(assetId, "Saattomahdollisuus henkilöautolla", Yes)

            insertTextPropertyValue(assetId, "Liityntäpysäköinnin lisätiedot", row.reachability)

            insertTextPropertyValue(assetId, "Esteettömyys liikuntarajoitteiselle", row.accessibility)

            insertTextPropertyValue(assetId, "Ylläpitäjän tunnus", row.internalId)

            insertTextPropertyValue(assetId, "Lisätiedot", equipments)

            insertTextPropertyValue(assetId, "Maastokoordinaatti X", row.xPosition)

            insertTextPropertyValue(assetId, "Maastokoordinaatti Y", row.yPosition)

            insertTextPropertyValue(assetId, "Matkustajatunnus", row.passengerId.getOrElse(null))
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
      where property_id = (select p.id from property p, localized_string ls where ls.id = p.name_localized_string_id and ls.value_fi = ${propertyName})
      and asset_id = ${assetId}
    """.as[Long].firstOption

    propertyId match {
      case None => {
        println("  CREATING PROPERTY VALUE: '" + propertyName + "' WITH VALUE: '" + value + "'")
        sqlu"""
          insert into text_property_value(id, property_id, asset_id, value_fi, created_by)
          values (primary_key_seq.nextval, (select p.id from property p, localized_string ls where ls.id = p.name_localized_string_id and ls.value_fi = ${propertyName}), ${assetId}, ${value}, ${Updater})
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

  def setSingleChoiceProperty(assetId: Long, propertyName: String, value: Int) {

    val propertyId = sql"""select p.id from property p, localized_string ls where ls.id = p.name_localized_string_id and ls.value_fi = ${propertyName}""".as[Long].first
    val existingProperty = sql"""select property_id from single_choice_value where property_id = ${propertyId} and asset_id = ${assetId}""".as[Long].firstOption

    println("  SETTING SINGLE CHOICE VALUE: '" + propertyName + "' TO: " + value)
    existingProperty match {
      case None => {
        sqlu"""
          insert into single_choice_value(property_id, asset_id, enumerated_value_id, modified_by, modified_date)
          values (${propertyId}, ${assetId}, (select id from enumerated_value where value = ${value} and property_id = ${propertyId}), ${Updater}, CURRENT_TIMESTAMP)
        """.execute
      }
      case _ => {
        sqlu"""
          update single_choice_value set enumerated_value_id = (select id from enumerated_value where value = ${value} and property_id = ${propertyId}), modified_by = ${Updater}, modified_date = CURRENT_TIMESTAMP
          where property_id = $propertyId and asset_id = ${assetId}
        """.execute
      }
    }
  }
}

object BusStopExcelDataImporter extends App {
  val importer = new BusStopExcelDataImporter()
  importer.insertExcelData(importer.readExcelDataFromCsvFile())
}

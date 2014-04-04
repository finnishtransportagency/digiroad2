package fi.liikennevirasto.digiroad2.util

import javax.sql.DataSource
import com.jolbox.bonecp.BoneCPDataSource
import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.jdbc.{StaticQuery => Q}
import Database.dynamicSession
import Q.interpolation
import org.apache.commons.lang3.StringUtils.{trimToEmpty, isBlank}
import com.github.tototoshi.csv._
import java.io.{InputStreamReader}
import scala.language.implicitConversions

sealed trait Status { def dbValue: Int }

class BusStopExcelDataImporter {
  val Updater = "excel_data_migration"
  lazy val convDs: DataSource = initConversionDataSource
  case object Yes extends Status { val dbValue = 1 }
  case object No extends Status { val dbValue = 2 }
  case object Unknown extends Status { val dbValue = 99 }

  case class ExcelBusStopData(externalId: Long, stopNameFi: String, stopNameSv: String, direction: String, reachability: String, accessibility: String, internalId: String, equipments: String, xPosition: String, yPosition: String,
                              passengerId: String, timetable: Option[Status], shelter: Option[Status], addShelter: Option[Status], bench: Option[Status], bicyclePark: Option[Status], electricTimetable: Option[Status], lightning: Option[Status])

  implicit def GetStatus(status: String) = {
    status match {
      case "1" => Some(No)
      case "2" => Some(Yes)
      case "3" => Some(Unknown)
      case _ => None
    }
  }

  implicit object SemicolonSeparatedValues extends DefaultCSVFormat {
    override val delimiter = ';'
  }

  private[this] def initConversionDataSource: DataSource = {
    Class.forName("oracle.jdbc.driver.OracleDriver")
    val ds = new BoneCPDataSource()
    ds.setJdbcUrl("")
    ds.setUsername("")
    ds.setPassword("")
    ds
  }

  def readExcelDataFromCsvFile(): List[ExcelBusStopData] = {
    val reader = CSVReader.open(new InputStreamReader(getClass.getResourceAsStream("/pysakkitiedot.csv")))
    reader.allWithHeaders().map { row =>
      new ExcelBusStopData(row("Valtakunnallinen ID").toLong, row("Pysäkin nimi"), row("Pysäkin nimi SE"), row("Suunta kansalaisen näkökulmasta"),
        row("Pysäkin saavutettavuus"), row("Esteettömyys-tiedot"), row("Ylläpitäjän sisäinen pysäkki-ID"), row("Pysäkin varusteet"),
        row("X_ETRS-TM35FIN"), row("Y_ETRS-TM35FIN"), row("Pysäkin tunnus matkustajalle"), row("Aikataulu"), row("Katos"), row("Mainoskatos"), row("Penkki"),
        row("Pyöräteline"), row("Sähköinen aikataulunäyttö"), row("Valaistus"))
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

            setSingleChoiceProperty(assetId, "Aikataulu", row.timetable)
            setSingleChoiceProperty(assetId, "Katos", row.shelter)
            setSingleChoiceProperty(assetId, "Mainoskatos", row.addShelter)
            setSingleChoiceProperty(assetId, "Penkki", row.bench)
            setSingleChoiceProperty(assetId, "Pyöräteline", row.bicyclePark)
            setSingleChoiceProperty(assetId, "Sähköinen aikataulunäyttö", row.electricTimetable)
            setSingleChoiceProperty(assetId, "Valaistus", row.bicyclePark)

            if (trimToEmpty(row.reachability).contains("pysäkointi")) setSingleChoiceProperty(assetId, "Saattomahdollisuus henkilöautolla", Some(Yes))

            insertTextPropertyValue(assetId, "Liityntäpysäköinnin lisätiedot", row.reachability)

            insertTextPropertyValue(assetId, "Esteettömyys liikuntarajoitteiselle", row.accessibility)

            insertTextPropertyValue(assetId, "Ylläpitäjän tunnus", row.internalId)

            insertTextPropertyValue(assetId, "Lisätiedot", row.equipments)

            insertTextPropertyValue(assetId, "Maastokoordinaatti X", row.xPosition)

            insertTextPropertyValue(assetId, "Maastokoordinaatti Y", row.yPosition)

            insertTextPropertyValue(assetId, "Matkustajatunnus", row.passengerId)
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

  def setSingleChoiceProperty(assetId: Long, propertyName: String, status: Option[Status]) {
    status match { case None => return }

    val propertyId = sql"""select p.id from property p, localized_string ls where ls.id = p.name_localized_string_id and ls.value_fi = ${propertyName}""".as[Long].first
    val existingProperty = sql"""select property_id from single_choice_value where property_id = ${propertyId} and asset_id = ${assetId}""".as[Long].firstOption

    println("  SETTING SINGLE CHOICE VALUE: '" + propertyName + "' TO: " + status.get.dbValue)
    existingProperty match {
      case None => {
        sqlu"""
          insert into single_choice_value(property_id, asset_id, enumerated_value_id, modified_by, modified_date)
          values (${propertyId}, ${assetId}, (select id from enumerated_value where value = ${status.get.dbValue} and property_id = ${propertyId}), ${Updater}, CURRENT_TIMESTAMP)
        """.execute
      }
      case _ => {
        sqlu"""
          update single_choice_value set enumerated_value_id = (select id from enumerated_value where value = ${status.get.dbValue} and property_id = ${propertyId}), modified_by = ${Updater}, modified_date = CURRENT_TIMESTAMP
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

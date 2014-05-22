package fi.liikennevirasto.digiroad2.util

import javax.sql.DataSource
import com.jolbox.bonecp.BoneCPDataSource
import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.jdbc.{StaticQuery => Q}
import Database.dynamicSession
import Q.interpolation
import org.apache.commons.lang3.StringUtils.{trimToEmpty, isBlank}
import com.github.tototoshi.csv._
import java.io.InputStreamReader
import scala.language.implicitConversions
import org.slf4j.LoggerFactory

sealed trait Status { def dbValue: Int }

class BusStopExcelDataImporter {
  val Updater = "excel_data_migration"
  //val logger = LoggerFactory.getLogger(getClass)
  lazy val convDs: DataSource = initConversionDataSource
  case object No extends Status { val dbValue = 1 }
  case object Yes extends Status { val dbValue = 2 }
  case object Unknown extends Status { val dbValue = 99 }

  case class ExcelBusStopData(externalId: Long, stopNameFi: String, stopNameSv: String, direction: String, reachability: String, accessibility: String,
                              internalId: String, equipments: String, xPosition: String, yPosition: String, passengerId: String, timetable: Option[Status],
                              shelter: Option[Status], addShelter: Option[Status], bench: Option[Status], bicyclePark: Option[Status], electricTimetable: Option[Status], lighting: Option[Status])

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

  def readExcelDataFromCsvFile(fileName: String): List[ExcelBusStopData] = {
    val reader = CSVReader.open(new InputStreamReader(getClass.getResourceAsStream("/" + fileName)))
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

            insertTextPropertyValue(assetId, "nimi_suomeksi", row.stopNameFi)
            insertTextPropertyValue(assetId, "nimi_ruotsiksi", row.stopNameSv)

            insertTextPropertyValue(assetId, "liikennointisuunta", row.direction)

            setSingleChoiceProperty(assetId, "aikataulu", row.timetable)
            setSingleChoiceProperty(assetId, "katos", row.shelter)
            setSingleChoiceProperty(assetId, "mainoskatos", row.addShelter)
            setSingleChoiceProperty(assetId, "penkki", row.bench)
            setSingleChoiceProperty(assetId, "pyorateline", row.bicyclePark)
            setSingleChoiceProperty(assetId, "sahkoinen_aikataulunaytto", row.electricTimetable)
            setSingleChoiceProperty(assetId, "valaistus", row.lighting)

            if (trimToEmpty(row.reachability).contains("liityntäpysäkointi")) setSingleChoiceProperty(assetId, "saattomahdollisuus_henkiloautolla", Some(Yes))
            if (trimToEmpty(row.reachability).contains("ei liityntäpysäkointi")) setSingleChoiceProperty(assetId, "saattomahdollisuus_henkiloautolla", Some(No))

            insertTextPropertyValue(assetId, "liityntapysakoinnin_lisatiedot", row.reachability)

            insertTextPropertyValue(assetId, "esteettomyys_liikuntarajoitteiselle", row.accessibility)

            insertTextPropertyValue(assetId, "yllapitajan_tunnus", row.internalId)

            insertTextPropertyValue(assetId, "lisatiedot", row.equipments)

            insertTextPropertyValue(assetId, "maastokoordinaatti_x", row.xPosition)
            insertTextPropertyValue(assetId, "maastokoordinaatti_y", row.yPosition)

            insertTextPropertyValue(assetId, "matkustajatunnus", row.passengerId)
          }
        } else {
          println("NO ASSET FOUND FOR EXTERNAL ID: " + row.externalId)
        }
      }
    }
  }

  def insertTextPropertyValue(assetId: Long, propertyPublicId: String, value: String) {
    if (isBlank(value)) return

    val propertyId = sql"""
      select id from text_property_value
      where property_id = (select p.id from property p where p.public_id = ${propertyPublicId})
      and asset_id = ${assetId}
    """.as[Long].firstOption

    propertyId match {
      case None => {
        println("  CREATING PROPERTY VALUE: '" + propertyPublicId + "' WITH VALUE: '" + value + "'")
        sqlu"""
          insert into text_property_value(id, property_id, asset_id, value_fi, created_by)
          values (primary_key_seq.nextval, (select p.id from property p where p.public_id = ${propertyPublicId}), ${assetId}, ${value}, ${Updater})
        """.execute
      }
      case _ => {
        println("  UPDATING PROPERTY VALUE: '" + propertyPublicId + "' WITH VALUE: '" + value + "'")
        sqlu"""
          update text_property_value set value_fi = ${value}, modified_by = ${Updater}, modified_date = CURRENT_TIMESTAMP
          where id = ${propertyId}
        """.execute
      }
    }
  }

  def setSingleChoiceProperty(assetId: Long, propertyName: String, status: Option[Status]) {
    status match {
      case None =>  {
        println("  NOT UPDATING SINGLE CHOICE VALUE: " + propertyName)
      }
      case _ => {
        println("  SETTING SINGLE CHOICE VALUE: '" + propertyName + "' TO: " + status.get.dbValue)

        val propertyId = sql"""select p.id from property p where public_id = ${propertyName}""".as[Long].first
        val existingProperty = sql"""select property_id from single_choice_value where property_id = ${propertyId} and asset_id = ${assetId}""".as[Long].firstOption

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
  }
}

object BusStopExcelDataImporter extends App {
  val importer = new BusStopExcelDataImporter()
  importer.insertExcelData(importer.readExcelDataFromCsvFile("pysakkitiedot.csv"))
}

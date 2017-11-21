package fi.liikennevirasto.digiroad2.util

import javax.sql.DataSource
import com.jolbox.bonecp.BoneCPDataSource
import slick.driver.JdbcDriver.backend.Database
import slick.jdbc.{StaticQuery => Q}
import Database.dynamicSession
import Q.interpolation
import org.apache.commons.lang3.StringUtils.{trimToEmpty, isBlank}
import com.github.tototoshi.csv._
import java.io.{Reader, InputStreamReader}
import scala.language.implicitConversions
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import org.slf4j.LoggerFactory

sealed trait Status { def dbValue: Int }

class MassTransitStopExcelDataImporter(dataSource: DataSource) {
  val Updater = "excel_data_migration"
  val logger = LoggerFactory.getLogger(getClass)
  case object No extends Status { val dbValue = 1 }
  case object Yes extends Status { val dbValue = 2 }
  case object Unknown extends Status { val dbValue = 99 }

  case class ExcelBusStopData(externalId: Long, stopNameFi: String, stopNameSv: String, direction: String, reachability: String, accessibility: String,
                              internalId: String, equipments: String, xPosition: String, yPosition: String, passengerId: String, timetable: Option[Status],
                              shelter: Option[Status], addShelter: Option[Status], bench: Option[Status], bicyclePark: Option[Status], electricTimetable: Option[Status], lighting: Option[Status], originalRow: Map[String, String])
  case class ImportResult(updatedStops: List[Updated], notFoundStops: List[NotFound])
  abstract class Result
  case class Updated(externalId: Long) extends Result
  case class NotFound(externalId: Long, row: String) extends Result

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

  def this() {
    this(ds)
  }

  def updateAssetDataFromCsvFile(csvFile: Reader): ImportResult = {
    Database.forDataSource(dataSource).withDynTransaction {
      val results = parseStopDataFromCsvFile(csvFile).map { stopData =>
        updateAssetData(stopData)
      }
      val (updated, notFound) = results.partition {
        case Updated(_) => true
        case _ => false
      }
      ImportResult(updated.asInstanceOf, notFound.asInstanceOf)
    }
  }

  def parseStopDataFromCsvFile(fileName: String): ImportResult = {
    updateAssetDataFromCsvFile(new InputStreamReader(getClass.getResourceAsStream("/" + fileName)))
  }

  // FIXME return csv file row number in exception if parsing fails
  private def parseStopDataFromCsvFile(csvFile: Reader): List[ExcelBusStopData] = {
    val reader = CSVReader.open(csvFile)
    reader.allWithHeaders.map { row =>
      new ExcelBusStopData(row("Valtakunnallinen ID").toLong, row("Pysäkin nimi"), row("Pysäkin nimi SE"), row("Suunta kansalaisen näkökulmasta"),
        row("Pysäkin saavutettavuus"), row("Esteettömyys-tiedot"), row("Ylläpitäjän sisäinen pysäkki-ID"), row("Pysäkin varusteet"),
        row("X_ETRS-TM35FIN"), row("Y_ETRS-TM35FIN"), row("Pysäkin tunnus matkustajalle"), row("Aikataulu"), row("Katos"), row("Mainoskatos"), row("Penkki"),
        row("Pyöräteline"), row("Sähköinen aikataulunäyttö"), row("Valaistus"), row)
    }
  }

  // FIXME print the failed row if exception occurs
  private def updateAssetData(stopData: ExcelBusStopData): Result = {
    val assetIdOpt = sql"""
      select id from asset where external_id = ${stopData.externalId}
    """.as[Long].firstOption

    assetIdOpt match {
      case None => {
        logger.warn("NO ASSET FOUND FOR EXTERNAL ID: " + stopData.externalId)
        NotFound(stopData.externalId, stopData.originalRow.view map { case (key, value) => key + ": '" + value + "'"} mkString(", "))
      }
      case Some(assetId) => {
        logger.info("UPDATING ASSET: " + assetId + " WITH EXTERNAL ID: " + stopData.externalId)

        sqlu"""
          update asset set modified_by = ${Updater}, modified_date = SYSDATE where id = ${assetId}
        """.execute

        insertTextPropertyValue(assetId, "nimi_suomeksi", stopData.stopNameFi)
        insertTextPropertyValue(assetId, "nimi_ruotsiksi", stopData.stopNameSv)

        insertTextPropertyValue(assetId, "liikennointisuunta", stopData.direction)

        setSingleChoiceProperty(assetId, "aikataulu", stopData.timetable)
        setSingleChoiceProperty(assetId, "katos", stopData.shelter)
        setSingleChoiceProperty(assetId, "mainoskatos", stopData.addShelter)
        setSingleChoiceProperty(assetId, "penkki", stopData.bench)
        setSingleChoiceProperty(assetId, "pyorateline", stopData.bicyclePark)
        setSingleChoiceProperty(assetId, "sahkoinen_aikataulunaytto", stopData.electricTimetable)
        setSingleChoiceProperty(assetId, "valaistus", stopData.lighting)

        if (trimToEmpty(stopData.reachability).contains("liityntäpysäkointi")) setSingleChoiceProperty(assetId, "saattomahdollisuus_henkiloautolla", Some(Yes))
        if (trimToEmpty(stopData.reachability).contains("ei liityntäpysäkointi")) setSingleChoiceProperty(assetId, "saattomahdollisuus_henkiloautolla", Some(No))

        insertTextPropertyValue(assetId, "liityntapysakoinnin_lisatiedot", stopData.reachability)

        insertTextPropertyValue(assetId, "esteettomyys_liikuntarajoitteiselle", stopData.accessibility)

        insertTextPropertyValue(assetId, "yllapitajan_tunnus", stopData.internalId)

        insertTextPropertyValue(assetId, "lisatiedot", stopData.equipments)

        insertTextPropertyValue(assetId, "maastokoordinaatti_x", stopData.xPosition)
        insertTextPropertyValue(assetId, "maastokoordinaatti_y", stopData.yPosition)

        insertTextPropertyValue(assetId, "matkustajatunnus", stopData.passengerId)
        Updated(stopData.externalId)
      }
    }
  }

  private def insertTextPropertyValue(assetId: Long, propertyPublicId: String, value: String) {
    if (isBlank(value)) return

    val propertyId = sql"""
      select id from text_property_value
      where property_id = (select p.id from property p where p.public_id = ${propertyPublicId})
      and asset_id = ${assetId}
    """.as[Long].firstOption

    propertyId match {
      case None => {
        logger.info("  CREATING PROPERTY VALUE: '" + propertyPublicId + "' WITH VALUE: '" + value + "'")
        sqlu"""
          insert into text_property_value(id, property_id, asset_id, value_fi, created_by)
          values (primary_key_seq.nextval, (select p.id from property p where p.public_id = ${propertyPublicId}), ${assetId}, ${value}, ${Updater})
        """.execute
      }
      case _ => {
        logger.info("  UPDATING PROPERTY VALUE: '" + propertyPublicId + "' WITH VALUE: '" + value + "'")
        sqlu"""
          update text_property_value set value_fi = ${value}, modified_by = ${Updater}, modified_date = SYSDATE
          where id = ${propertyId}
        """.execute
      }
    }
  }

  private def setSingleChoiceProperty(assetId: Long, propertyName: String, status: Option[Status]) {
    status match {
      case None =>  {
        logger.info("  NOT UPDATING SINGLE CHOICE VALUE: " + propertyName)
      }
      case _ => {
        logger.info("  SETTING SINGLE CHOICE VALUE: '" + propertyName + "' TO: " + status.get.dbValue)

        val propertyId = sql"""select p.id from property p where public_id = ${propertyName}""".as[Long].first
        val existingProperty = sql"""select property_id from single_choice_value where property_id = ${propertyId} and asset_id = ${assetId}""".as[Long].firstOption

        existingProperty match {
          case None => {
            sqlu"""
              insert into single_choice_value(property_id, asset_id, enumerated_value_id, created_by, created_date)
              values (${propertyId}, ${assetId}, (select id from enumerated_value where value = ${status.get.dbValue} and property_id = ${propertyId}), ${Updater}, SYSDATE)
            """.execute
          }
          case _ => {
            sqlu"""
              update single_choice_value set enumerated_value_id = (select id from enumerated_value where value = ${status.get.dbValue} and property_id = ${propertyId}), modified_by = ${Updater}, modified_date = SYSDATE
              where property_id = $propertyId and asset_id = ${assetId}
            """.execute
          }
        }
      }
    }
  }
}

object MassTransitStopExcelDataImporter extends App {
  val importer = new MassTransitStopExcelDataImporter(initDataSource)
  importer.parseStopDataFromCsvFile("pysakkitiedot.csv")

  private[this] def initDataSource: DataSource = {
    Class.forName("oracle.jdbc.driver.OracleDriver")
    val ds = new BoneCPDataSource()
    ds.setJdbcUrl("")
    ds.setUsername("")
    ds.setPassword("")
    ds
  }
}


package fi.liikennevirasto.digiroad2

import java.util.Properties
import fi.liikennevirasto.digiroad2.dao._
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime
import fi.liikennevirasto.digiroad2.asset.ServicePointsClass.{Unknown => _, _}
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.util.Digiroad2Properties

sealed trait Status {
  def value : Int
  def description: String
  def descriptionFi: String
}

object Status {
  val values : Set[Status] = Set(InProgress, OK, NotOK, Abend)

  def apply(value: Int) : Status = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  case object InProgress extends Status {def value = 1; def description = "In progress ..."; def descriptionFi = "Lataus kesken"}
  case object OK extends Status {def value = 2; def description = "All records was treated "; def descriptionFi = "Kaikki kohteet käsitelty"}
  case object NotOK extends Status {def value = 3; def description = "Process Executed but some record fails"; def descriptionFi = "Kaikki kohteet käsitelty, joitakin virhetilanteita"}
  case object Abend extends Status {def value = 4; def description = "Process fail"; def descriptionFi = "Lataus epäonnistunut"}
  case object Unknown extends Status {def value = 99; def description = "Unknown Status Type"; def descriptionFi = "Tuntematon tilakoodi"}
}

case class ImportStatusInfo(id: Long, status: Int, statusDescription: String, fileName: String, createdBy: Option[String], createdDate: Option[DateTime], jobName: String, content: Option[String])

class RoadLinkNotFoundException(linkId: String) extends RuntimeException

case class IncompleteRow(missingParameters: List[String], csvRow: String)
case class MalformedRow(malformedParameters: List[String], csvRow: String)
case class ExcludedRow(affectedRows: String, csvRow: String)
case class AssetProperty(columnName: String, value: Any)

trait ImportResult {
  val incompleteRows: List[IncompleteRow]
  val malformedRows: List[MalformedRow]
  val excludedRows: List[ExcludedRow]
}

trait CsvDataImporterOperations {
  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  def roadLinkService: RoadLinkService
  def roadLinkClient: RoadLinkClient
  def eventBus: DigiroadEventBus

  lazy val roadAddressService: RoadAddressService = {
    new RoadAddressService()
  }

  type MalformedParameters = List[String]
  type ParsedProperties = List[AssetProperty]
  type ParsedRow = (MalformedParameters, ParsedProperties)
  type ImportResultData <: ImportResult

  def mappingContent(result: ImportResultData): String  = {
    val excludedResult = result.excludedRows.map{rows => s"<li> ${rows.affectedRows} -> ${rows.csvRow} </li>"}
    val incompleteResult = result.incompleteRows.map{ rows=> s"<li> ${rows.missingParameters.mkString(";")} -> ${rows.csvRow} </li>"}
    val malformedResult = result.malformedRows.map{ rows => s"<li> ${rows.malformedParameters.mkString(";")}  -> ${rows.csvRow} </li>"}

    s"<ul> excludedLinks: ${excludedResult.mkString.replaceAll("[(|)]{1}","")} </ul>" +
    s"<ul> incompleteRows: ${incompleteResult.mkString.replaceAll("[(|)]{1}","")} </ul>" +
    s"<ul> malformedRows: ${malformedResult.mkString.replaceAll("[(|)]{1}","")} </ul>"
  }

  val importLogDao: ImportLogDAO = new ImportLogDAO

    def getImportById(id: Long) : Option[ImportStatusInfo]  = {
      PostGISDatabase.withDynTransaction {
        importLogDao.get(id)
      }
    }

    def getByUser(username: String) : Seq[ImportStatusInfo]  = {
      PostGISDatabase.withDynTransaction {
        importLogDao.getByUser(username)
      }
    }

    def getById(id: Long) : Option[ImportStatusInfo]  = {
      PostGISDatabase.withDynTransaction {
        importLogDao.get(id)
      }
    }

    def getByIds(ids: Set[Long]) : Seq[ImportStatusInfo]  = {
      PostGISDatabase.withDynTransaction {
        importLogDao.getByIds(ids)
      }
    }

    def update(id: Long, status: Status, content: Option[String] = None) : Long  = {
      PostGISDatabase.withDynTransaction {
        importLogDao.update(id, status, content)
      }
    }

    def rowToString(csvRowWithHeaders: Map[String, Any]): String = {
      csvRowWithHeaders.view map { case (key, value) => key + ": '" + value + "'" } mkString ", "
    }
}

class CsvDataImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends CsvDataImporterOperations {
  override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def roadLinkClient: RoadLinkClient = roadLinkServiceImpl.roadLinkClient
  override def eventBus: DigiroadEventBus = eventBusImpl
}

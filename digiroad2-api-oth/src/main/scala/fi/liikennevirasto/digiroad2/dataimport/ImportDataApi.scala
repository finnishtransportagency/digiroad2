package fi.liikennevirasto.digiroad2.dataimport

import java.io.InputStreamReader

import fi.liikennevirasto.digiroad2.Digiroad2Context.properties
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, _}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.authentication.RequestHeaderAuthentication
import fi.liikennevirasto.digiroad2.middleware.{AdministrativeValues, CsvDataImporterInfo}
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.digiroad2.util.MassTransitStopExcelDataImporter
import javax.servlet.ServletException
import org.joda.time.DateTime
import org.json4s.JsonAST.JInt
import org.json4s.{CustomSerializer, DefaultFormats, Extraction, Formats, JInt, JString}
import org.scalatra._
import org.scalatra.servlet.{FileItem, FileUploadSupport, MultipartConfig}
import org.scalatra.json.JacksonJsonSupport

class ImportDataApi extends ScalatraServlet with FileUploadSupport with JacksonJsonSupport with RequestHeaderAuthentication {

  case object DateTimeSerializer extends CustomSerializer[DateTime](format => ( {
    case _ => throw new NotImplementedError("DateTime deserialization")
  }, {
    case d: DateTime => JString(d.toString(DateTimePropertyFormat))
  }))

  protected implicit val jsonFormats: Formats = DefaultFormats + DateTimeSerializer
  private val CSV_LOG_PATH = "/tmp/csv_data_import_logs/"
  private val roadLinkCsvImporter = new RoadLinkCsvImporter
  private val trafficSignCsvImporter = new TrafficSignCsvImporter
  private val maintenanceRoadCsvImporter = new MaintenanceRoadCsvImporter
  private val massTransitStopCsvImporter = new MassTransitStopCsvImporter

  lazy val csvDataImporter = new CsvDataImporter
  private final val threeMegabytes: Long = 3*1024*1024
  lazy val user = userProvider.getCurrentUser()
  lazy val eventbus: DigiroadEventBus = {
    Class.forName(properties.getProperty("digiroad2.eventBus")).newInstance().asInstanceOf[DigiroadEventBus]
  }

  lazy val userProvider: UserProvider = {
    Class.forName(properties.getProperty("digiroad2.userProvider")).newInstance().asInstanceOf[UserProvider]
  }

  before() {
    contentType = formats("json")
    configureMultipartHandling(MultipartConfig(maxFileSize = Some(threeMegabytes)))
    try {
      authenticateForApi(request)(userProvider)
    } catch {
      case ise: IllegalStateException => halt(Unauthorized("Authentication error: " + ise.getMessage))
    }
    response.setHeader("Digiroad2-Server-Originated-Response", "true")
  }

  post("/maintenanceRoads") {
    if (!user.isOperator()) {
      halt(Forbidden("Vain operaattori voi suorittaa Excel-ajon"))
    }
   importMaintenanceRoads(fileParams("csv-file"))
  }

  post("/trafficSigns") {
    val municipalitiesToExpire = request.getParameterValues("municipalityNumbers") match {
      case null => Set.empty[Int]
      case municipalities => municipalities.map(_.toInt).toSet
    }

    if (!(user.isOperator() || user.isMunicipalityMaintainer())) {
      halt(Forbidden("Vain operaattori tai kuntaylläpitäjä voi suorittaa Excel-ajon"))
    }

    if (user.isMunicipalityMaintainer() && municipalitiesToExpire.diff(user.configuration.authorizedMunicipalities).nonEmpty) {
      halt(Forbidden(s"Puuttuvat muokkausoikeukset jossain listalla olevassa kunnassa: ${municipalitiesToExpire.mkString(",")}"))
    }

   importTrafficSigns(fileParams("csv-file"), municipalitiesToExpire)
  }

  post("/roadlinks") {
    if (!user.isOperator()) {
      halt(Forbidden("Vain operaattori voi suorittaa Excel-ajon"))
    }

    importRoadLinks(fileParams("csv-file"))
  }

  post("/massTransitStop") {
    if (!user.isOperator()) {
      halt(Forbidden("Vain operaattori voi suorittaa Excel-ajon"))
    }
    val administrativeClassLimitations: Set[AdministrativeClass] = Set(
      params.get("limit-import-to-roads").map(_ => State),
      params.get("limit-import-to-streets").map(_ => Municipality),
      params.get("limit-import-to-private-roads").map(_ => Private)
    ).flatten

    importMassTransitStop(fileParams("csv-file"), administrativeClassLimitations)
  }

  get("/log/:id") {
    params.getAs[Long]("id").flatMap(id =>  csvDataImporter.getById(id)).getOrElse("Logia ei löytynyt.")
  }

  //TODO check if this is necessary
  override def isSizeConstraintException(e: Exception) = e match {
    case se: ServletException if se.getMessage.contains("exceeds max filesize") ||
      se.getMessage.startsWith("Request exceeds maxRequestSize") => true
    case _ => false
  }

  //TODO check if this exist
  post("/csv") {
    if (!user.isOperator()) {
      halt(Forbidden("Vain operaattori voi suorittaa Excel-ajon"))
    }
    val csvStream = new InputStreamReader(fileParams("csv-file").getInputStream)
    new MassTransitStopExcelDataImporter().updateAssetDataFromCsvFile(csvStream)
  }

  def importTrafficSigns(csvFileItem: FileItem, municipalitiesToExpire: Set[Int]): Unit = {
    val csvFileInputStream = csvFileItem.getInputStream
    val fileName = csvFileItem.getName
    if (csvFileInputStream.available() == 0) halt(BadRequest("Ei valittua CSV-tiedostoa. Valitse tiedosto ja yritä uudestaan.")) else None

    eventbus.publish("importCSVData", CsvDataImporterInfo(TrafficSigns.layerName, fileName, csvFileInputStream))

  }

  def importRoadLinks(csvFileItem: FileItem ): Nothing = {
    val csvFileInputStream = csvFileItem.getInputStream
    val fileName = csvFileItem.getName
    if (csvFileInputStream.available() == 0) halt(BadRequest("Ei valittua CSV-tiedostoa. Valitse tiedosto ja yritä uudestaan.")) else None


  }

  def importMaintenanceRoads(csvFileItem: FileItem): Unit = {
    val csvFileInputStream = csvFileItem.getInputStream
    val fileName = csvFileItem.getName
    if (csvFileInputStream.available() == 0) halt(BadRequest("Ei valittua CSV-tiedostoa. Valitse tiedosto ja yritä uudestaan.")) else None

    val logId =  maintenanceRoadCsvImporter.create(user.username,  maintenanceRoadCsvImporter.MAINTENANCE_ROAD_LOG, fileName)

    eventbus.publish("importCSVData", CsvDataImporterInfo("maintenanceRoads", logId, csvFileInputStream))
  }

  def importMassTransitStop(csvFileItem: FileItem, administrativeClassLimitations: Set[AdministrativeClass]) : Unit = {
    val csvFileInputStream = csvFileItem.getInputStream
    val fileName = csvFileItem.getName

    val logId = massTransitStopCsvImporter.create(user.username, massTransitStopCsvImporter.BUS_STOP_LOG, fileName)

    eventbus.publish("importCSVData", CsvDataImporterInfo("massTransitStop", logId, csvFileInputStream, Some(administrativeClassLimitations.asInstanceOf[AdministrativeValues])))
  }
}

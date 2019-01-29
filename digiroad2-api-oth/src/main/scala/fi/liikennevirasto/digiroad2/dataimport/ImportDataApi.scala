package fi.liikennevirasto.digiroad2.dataimport

import java.io.{InputStream, InputStreamReader}

import fi.liikennevirasto.digiroad2.Digiroad2Context
import fi.liikennevirasto.digiroad2.Digiroad2Context.{Digiroad2ServerOriginatedResponseHeader, userProvider}
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, Municipality, Private, State}
import fi.liikennevirasto.digiroad2.authentication.RequestHeaderAuthentication
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dataimport.CsvImporter.ImportResult
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.MassTransitStopService
import fi.liikennevirasto.digiroad2.service.{ImportLogService, Status}
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.digiroad2.util.MassTransitStopExcelDataImporter
import javax.servlet.ServletException
import org.json4s.{DefaultFormats, Extraction, Formats}
import org.scalatra._
import org.scalatra.servlet.{FileItem, FileUploadSupport, MultipartConfig}
import org.scalatra.json.JacksonJsonSupport

class ImportDataApi extends ScalatraServlet with FileUploadSupport with JacksonJsonSupport with RequestHeaderAuthentication {

  protected implicit val jsonFormats: Formats = DefaultFormats
  private val CSV_LOG_PATH = "/tmp/csv_data_import_logs/"
  private val roadLinkCsvImporter = new RoadLinkCsvImporter
  private val trafficSignCsvImporter = new TrafficSignCsvImporter
  private val maintenanceRoadCsvImporter = new MaintenanceRoadCsvImporter

  private final val threeMegabytes: Long = 3*1024*1024
  private val csvImporter = new CsvImporter {
    override val massTransitStopService: MassTransitStopService = Digiroad2Context.massTransitStopService
    override val userProvider: UserProvider = Digiroad2Context.userProvider
    override val vvhClient: VVHClient = Digiroad2Context.vvhClient
  }
  private val user = userProvider.getCurrentUser()

  lazy val importLogService = new ImportLogService

  before() {
    contentType = formats("json")
    configureMultipartHandling(MultipartConfig(maxFileSize = Some(threeMegabytes)))
    try {
      authenticateForApi(request)(userProvider)
    } catch {
      case ise: IllegalStateException => halt(Unauthorized("Authentication error: " + ise.getMessage))
    }
    response.setHeader(Digiroad2ServerOriginatedResponseHeader, "true")
  }

  post("/maintenanceRoads") {
    if (!user.isOperator()) {
      halt(Forbidden("Vain operaattori voi suorittaa Excel-ajon"))
    }
    importMaintenanceRoads(fileParams("csv-file"))
  }

  post("/trafficsigns") {
    val csvFileItem= fileParams("csv-file")

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

    importTrafficSigns(csvFileItem, municipalitiesToExpire)
  }

  post("/roadlinks") {
    if (!user.isOperator()) {
      halt(Forbidden("Vain operaattori voi suorittaa Excel-ajon"))
    }
    importRoadLinks( fileParams("csv-file"))
  }

  get("/log/:id") {
    params.getAs[Long]("id").flatMap(id =>  importLogService.getById(id)).getOrElse("Logia ei löytynyt.")
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

  get("/log/:id") {
    params.getAs[Long]("id").flatMap(id => importLogService.getById(id)).getOrElse("Logia ei löytynyt.")
  }

  private def fork(f: => Unit): Unit = {
    new Thread(new Runnable() {
      override def run(): Unit = {
        f
      }
    }).start()
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

    val id = importLogService.create(user.username, importLogService.BUS_STOP_LOG, "")

    val csvFileInputStream = fileParams("csv-file").getInputStream
    fork {
      // Current user is stored in a thread-local variable (feel free to provide better solution)
      userProvider.setCurrentUser(user)
      try {
        val result = csvImporter.importAssets(csvFileInputStream, administrativeClassLimitations)
        val response = result match {
          case ImportResult(Nil, Nil, Nil, Nil) => "CSV tiedosto käsitelty."
          case ImportResult(Nil, Nil, Nil, excludedAssets) => "CSV tiedosto käsitelty. Seuraavat päivitykset on jätetty huomioimatta:\n" + pretty(Extraction.decompose(excludedAssets))
          case _ => pretty(Extraction.decompose(result))
        }
        importLogService.update(id, Status.OK, Some(response) )
      } catch {
        case e: Exception =>
          importLogService.update(id, Status.Abend, Some( "Latauksessa tapahtui odottamaton virhe: " + e.toString))
          throw e

      } finally {
        csvFileInputStream.close()
      }
    }
    redirect(url("/log/" + id))
  }

  def importTrafficSigns(csvFileItem: FileItem, municipalitiesToExpire: Set[Int]): Nothing = {
    val csvFileInputStream = csvFileItem.getInputStream
    val fileName = csvFileItem.getName
    if (csvFileInputStream.available() == 0) halt(BadRequest("Ei valittua CSV-tiedostoa. Valitse tiedosto ja yritä uudestaan.")) else None

    val id = importLogService.create(user.username, importLogService.TRAFFIC_SIGN_LOG, fileName)
    try {
      val result = trafficSignCsvImporter.importTrafficSigns(csvFileInputStream, municipalitiesToExpire)
      val response = result match {
        case trafficSignCsvImporter.ImportResult(Nil, Nil, Nil, Nil, _) => "CSV tiedosto käsitelty." //succesfully processed
        case trafficSignCsvImporter.ImportResult(Nil, excludedLinks, Nil, Nil, _) => "CSV tiedosto käsitelty. Seuraavat päivitykset on jätetty huomioimatta:\n" + pretty(Extraction.decompose(excludedLinks)) //following links have been excluded
        case _ => pretty(Extraction.decompose(result.copy(createdData = Nil)))
      }
      importLogService.update(id, Status.OK)
    } catch {
      case e: Exception =>
        importLogService.update(id, Status.Abend, Some("Latauksessa tapahtui odottamaton virhe: " + e.toString)) //error when saving log
        throw e
    } finally {
      csvFileInputStream.close()
    }
    redirect(url("/log/" + id + "/" +  importLogService.TRAFFIC_SIGN_LOG))
  }

  def importRoadLinks(csvFileItem: FileItem ): Nothing = {
    val csvFileInputStream = csvFileItem.getInputStream
    val fileName = csvFileItem.getName
    if (csvFileInputStream.available() == 0) halt(BadRequest("Ei valittua CSV-tiedostoa. Valitse tiedosto ja yritä uudestaan.")) else None

    val id =  importLogService.create(user.username,  importLogService.ROAD_LINK_LOG, fileName)
    try {
      val result = roadLinkCsvImporter.importLinkAttribute(csvFileInputStream)
      val response = result match {
        case roadLinkCsvImporter.ImportResult(Nil, Nil, Nil, Nil) => "CSV tiedosto käsitelty." //succesfully processed
        case roadLinkCsvImporter.ImportResult(Nil, Nil, Nil, excludedLinks) => "CSV tiedosto käsitelty. Seuraavat päivitykset on jätetty huomioimatta:\n" + pretty(Extraction.decompose(excludedLinks)) //following links have been excluded
        case _ => pretty(Extraction.decompose(result))
      }
      importLogService.update(id, Status.OK, Some(response))
    } catch {
      case e: Exception => {
        importLogService.update(id, Status.Abend, Some("Latauksessa tapahtui odottamaton virhe: " + e.toString())) //error when saving log
        throw e
      }
    } finally {
      csvFileInputStream.close()
    }
    redirect(url("/log/" + id + "/" +  importLogService.ROAD_LINK_LOG))
  }

  def importMaintenanceRoads(csvFileItem: FileItem): Nothing = {
    val csvFileInputStream = csvFileItem.getInputStream
    val fileName = csvFileItem.getName
    if (csvFileInputStream.available() == 0) halt(BadRequest("Ei valittua CSV-tiedostoa. Valitse tiedosto ja yritä uudestaan.")) else None

    val id =  importLogService.create(user.username,  importLogService.MAINTENANCE_ROAD_LOG, fileName)
    try {
      val result = maintenanceRoadCsvImporter.importMaintenanceRoads(csvFileInputStream)
      val response = result match {
        case maintenanceRoadCsvImporter.ImportResult(Nil, Nil, Nil) => "CSV tiedosto käsitelty." //succesfully processed
        case maintenanceRoadCsvImporter.ImportResult(Nil, excludedLinks, Nil) => "CSV tiedosto käsitelty. Seuraavat päivitykset on jätetty huomioimatta:\n" + pretty(Extraction.decompose(excludedLinks)) //following links have been excluded
        case _ => pretty(Extraction.decompose(result))
      }
      importLogService.update(id, Status.OK, Some(response))
    } catch {
      case e: Exception =>
        importLogService.update(id, Status.Abend, Some("Latauksessa tapahtui odottamaton virhe: " + e.toString)) //error when saving log
        throw e
    } finally {
      csvFileInputStream.close()
    }
    redirect(url("/log/" + id + "/" +  importLogService.MAINTENANCE_ROAD_LOG))
  }


}

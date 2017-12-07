package fi.liikennevirasto.digiroad2.dataimport

import fi.liikennevirasto.digiroad2.dataimport.CsvImporter.ImportResult
import fi.liikennevirasto.digiroad2.{Digiroad2Context, MassTransitStopService, RoadLinkService}
import fi.liikennevirasto.digiroad2.user.UserProvider
import org.scalatra._
import fi.liikennevirasto.digiroad2.authentication.RequestHeaderAuthentication
import fi.liikennevirasto.digiroad2.Digiroad2Context._
import org.scalatra.servlet.{FileUploadSupport, MultipartConfig}
import javax.servlet.ServletException

import fi.liikennevirasto.digiroad2.util.MassTransitStopExcelDataImporter
import java.io.InputStreamReader

import org.scalatra.json.JacksonJsonSupport
import org.json4s.{DefaultFormats, Formats}
import org.slf4j.LoggerFactory

import scala.io.Source
import org.json4s.Extraction
import java.io.File
import java.io.FileNotFoundException
import java.io.BufferedWriter
import java.io.FileWriter

import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, Municipality, Private, State}
import javax.naming.OperationNotSupportedException

import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.oracle.ImportLogService

class MassTransitStopImportApi extends ScalatraServlet with CorsSupport with RequestHeaderAuthentication with FileUploadSupport with JacksonJsonSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats
  private final val threeMegabytes: Long = 3*1024*1024
  private val importLogger = LoggerFactory.getLogger(getClass)
  private val CSV_LOG_PATH = "/tmp/csv_import_logs/"
  private val  BUS_STOP_LOG = "bus stop import"
  private val csvImporter = new CsvImporter {
    override val massTransitStopService: MassTransitStopService = Digiroad2Context.massTransitStopService
    override val userProvider: UserProvider = Digiroad2Context.userProvider
    override val vvhClient: VVHClient = Digiroad2Context.vvhClient
  }

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

  // Jetty 8.1.3 incorrectly throws ServletException instead of IllegalStateException. http://www.scalatra.org/2.2/guides/formats/upload.html
  override def isSizeConstraintException(e: Exception) = e match {
    case se: ServletException if se.getMessage.contains("exceeds max filesize") ||
      se.getMessage.startsWith("Request exceeds maxRequestSize") => true
    case _ => false
  }

  post("/csv") {
    if (!userProvider.getCurrentUser().isOperator()) {
      halt(Forbidden("Vain operaattori voi suorittaa Excel-ajon"))
    }
    val csvStream = new InputStreamReader(fileParams("csv-file").getInputStream)
    new MassTransitStopExcelDataImporter().updateAssetDataFromCsvFile(csvStream)
  }

  get("/log/:id") {
    params.getAs[Long]("id").flatMap(id => ImportLogService.get(id, BUS_STOP_LOG)).getOrElse("Logia ei löytynyt.")
  }

  private def fork(f: => Unit): Unit = {
    new Thread(new Runnable() {
      override def run(): Unit = {
        f
      }
    }).start()
  }

  post("/validationCsv") {
    if (!userProvider.getCurrentUser().isOperator()) {
      halt(Forbidden("Vain operaattori voi suorittaa Excel-ajon"))
    }
    val administrativeClassLimitations: Set[AdministrativeClass] = Set(
      params.get("limit-import-to-roads").map(_ => State),
      params.get("limit-import-to-streets").map(_ => Municipality),
      params.get("limit-import-to-private-roads").map(_ => Private)
    ).flatten
    val id = ImportLogService.save("Pysäkkien lataus on käynnissä. Päivitä sivu hetken kuluttua uudestaan.", BUS_STOP_LOG)
    val user = userProvider.getCurrentUser()
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
        ImportLogService.save(id, response, BUS_STOP_LOG )
      } catch {
        case e: Exception => {
          ImportLogService.save(id, "Latauksessa tapahtui odottamaton virhe: " + e.toString(), BUS_STOP_LOG )
          throw e
        }
      } finally {
        csvFileInputStream.close()
      }
    }
    redirect(url("/log/" + id))
  }
}

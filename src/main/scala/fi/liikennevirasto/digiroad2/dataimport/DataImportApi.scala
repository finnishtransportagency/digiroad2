package fi.liikennevirasto.digiroad2.dataimport

import fi.liikennevirasto.digiroad2.dataimport.CsvImporter.ImportResult
import org.scalatra._
import fi.liikennevirasto.digiroad2.authentication.RequestHeaderAuthentication
import fi.liikennevirasto.digiroad2.Digiroad2Context._
import org.scalatra.servlet.{MultipartConfig, FileUploadSupport}
import javax.servlet.ServletException
import fi.liikennevirasto.digiroad2.util.BusStopExcelDataImporter
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

class DataImportApi extends ScalatraServlet with CorsSupport with RequestHeaderAuthentication with FileUploadSupport with JacksonJsonSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats
  private final val threeMegabytes: Long = 3*1024*1024
  private val importLogger = LoggerFactory.getLogger(getClass)
  private val CSV_LOG_PATH = "/tmp/csv_import_logs/"

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
    if (!userProvider.getCurrentUser().configuration.roles.contains("operator")) {
      halt(Forbidden("Vain operaattori voi suorittaa Excel-ajon"))
    }
    val csvStream = new InputStreamReader(fileParams("csv-file").getInputStream)
    new BusStopExcelDataImporter().updateAssetDataFromCsvFile(csvStream)
  }

  get("/log/:filename") {
    try {
      Source.fromFile(CSV_LOG_PATH + params.get("filename").get).mkString
    } catch {
      case e: FileNotFoundException => "Logia ei löytynyt."
    }
  }

  private def writeToPath(path: String, str: String): Unit = {
    val w = new BufferedWriter(new FileWriter(path))
    try {
      w.write(str)
    } finally {
      w.close()
    }
  }

  private def fork(f: => Unit): Unit = {
    new Thread(new Runnable() {
      override def run(): Unit = {
        f
      }
    }).start()
  }

  post("/validationCsv") {
    if (!userProvider.getCurrentUser().configuration.roles.contains("operator")) {
      halt(Forbidden("Vain operaattori voi suorittaa Excel-ajon"))
    }
    val limitImportToStreets = params.get("limit-import-to-streets").flatMap(stringToBoolean(_)).getOrElse(false)
    val timestamp = System.currentTimeMillis()
    val path = CSV_LOG_PATH + timestamp + ".log"
    val directory = new File(CSV_LOG_PATH)
    if (!directory.exists) {
      directory.mkdir()
    }
    writeToPath(path, "Pysäkkien lataus on käynnissä. Päivitä sivu hetken kuluttua uudestaan.")
    val user = userProvider.getCurrentUser()
    val csvFileInputStream = fileParams("csv-file").getInputStream
    fork {
      // Current user is stored in a thread-local variable (feel free to provide better solution)
      userProvider.setCurrentUser(user)
      try {
        val result = CsvImporter.importAssets(csvFileInputStream, assetProvider, limitImportToStreets)
        val response = result match {
          case ImportResult(Nil, Nil, Nil, Nil) => "CSV tiedosto käsitelty."
          case ImportResult(Nil, Nil, Nil, excludedAssets) => "CSV tiedosto käsitelty. Seuraavat päivitykset on jätetty huomioimatta: " + excludedAssets
          case _ => pretty(Extraction.decompose(result))
        }
        writeToPath(path, response)
      } catch {
        case e: Exception => {
          writeToPath(path, "Latauksessa tapahtui odottamaton virhe: " + e.toString())
          throw e
        }
      } finally {
        csvFileInputStream.close()
      }
    }
    redirect(url("/log/" + timestamp + ".log"))
  }
}

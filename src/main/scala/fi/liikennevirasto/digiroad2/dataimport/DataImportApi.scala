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

class DataImportApi extends ScalatraServlet with CorsSupport with RequestHeaderAuthentication with FileUploadSupport with JacksonJsonSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats
  private final val threeMegabytes: Long = 3*1024*1024

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

  post("/validationCsv") {
    if (!userProvider.getCurrentUser().configuration.roles.contains("operator")) {
      halt(Forbidden("Vain operaattori voi suorittaa Excel-ajon"))
    }
    val result = CsvImporter.importAssets(fileParams("csv-file").getInputStream, assetProvider)
    result match {
      case ImportResult(Nil, Nil, Nil) => "CSV tiedosto käsitelty."
      case _ => halt(BadRequest(result))
    }
  }
}

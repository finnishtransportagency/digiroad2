package fi.liikennevirasto.digiroad2.dataimport

import fi.liikennevirasto.digiroad2.Digiroad2Context.{Digiroad2ServerOriginatedResponseHeader, userProvider}
import fi.liikennevirasto.digiroad2.authentication.RequestHeaderAuthentication
import fi.liikennevirasto.digiroad2.dataimport.DataCsvImporter.RoadLinkCsvImporter.ImportResult
import fi.liikennevirasto.digiroad2.Digiroad2Context
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.digiroad2.oracle.ImportLogService
import org.json4s.{DefaultFormats, Extraction, Formats}
import org.scalatra._
import org.scalatra.servlet.FileUploadSupport
import org.scalatra.json.JacksonJsonSupport

class ImportDataApi extends ScalatraServlet with FileUploadSupport with JacksonJsonSupport with RequestHeaderAuthentication {

  protected implicit val jsonFormats: Formats = DefaultFormats
  private val CSV_LOG_PATH = "/tmp/csv_data_import_logs/"
  private val  ROAD_LINK_LOG = "road link import"
  private val csvImporter = new RoadLinkCsvImporter {
    override val userProvider: UserProvider = Digiroad2Context.userProvider
    override val vvhClient: VVHClient = Digiroad2Context.vvhClient
  }

  before() {
    contentType = formats("json")
    try {
      authenticateForApi(request)(userProvider)
    } catch {
      case ise: IllegalStateException => halt(Unauthorized("Authentication error: " + ise.getMessage))
    }
    response.setHeader(Digiroad2ServerOriginatedResponseHeader, "true")
  }

  get("/log/:id") {
    params.getAs[Long]("id").flatMap(id => ImportLogService.get(id, ROAD_LINK_LOG)).getOrElse("Logia ei löytynyt.")
  }

  post("/csv") {
    if (!userProvider.getCurrentUser().isOperator()) {
      halt(Forbidden("Vain operaattori voi suorittaa Excel-ajon"))
    }
    val id = ImportLogService.save("Kohteiden lataus on käynnissä. Päivitä sivu hetken kuluttua uudestaan.", ROAD_LINK_LOG)
    val csvFileInputStream = fileParams("csv-file").getInputStream
    try {
      val result = csvImporter.importLinkAttribute(csvFileInputStream)
      val response = result match {
        case ImportResult(Nil, Nil, Nil, Nil) => "CSV tiedosto käsitelty." //succesfully processed
        case ImportResult(Nil, Nil, Nil, excludedLinks) => "CSV tiedosto käsitelty. Seuraavat päivitykset on jätetty huomioimatta:\n" + pretty(Extraction.decompose(excludedLinks)) //following links have been excluded
        case _ => pretty(Extraction.decompose(result))
      }
      ImportLogService.save(id, response, ROAD_LINK_LOG)
    } catch {
      case e: Exception => {
        ImportLogService.save(id, "Latauksessa tapahtui odottamaton virhe: " + e.toString(), ROAD_LINK_LOG) //error when saving log
        throw e
      }
    } finally {
      csvFileInputStream.close()
    }
    redirect(url("/log/" + id))
  }
}

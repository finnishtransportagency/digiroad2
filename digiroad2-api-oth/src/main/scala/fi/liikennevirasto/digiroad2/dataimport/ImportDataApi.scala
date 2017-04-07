package fi.liikennevirasto.digiroad2.dataimport

import fi.liikennevirasto.digiroad2.Digiroad2Context.{Digiroad2ServerOriginatedResponseHeader, userProvider}
import fi.liikennevirasto.digiroad2.authentication.RequestHeaderAuthentication
import fi.liikennevirasto.digiroad2.dataimport.RoadLinkCsvImporter.CsvImporter.{ExcludedLink, ImportResult}
import fi.liikennevirasto.digiroad2.{Digiroad2Context, RoadLinkService, VVHClient}
import fi.liikennevirasto.digiroad2.user.UserProvider
import org.slf4j.LoggerFactory
import org.json4s.{DefaultFormats, Extraction, Formats}
import org.scalatra._
import org.scalatra.servlet.FileUploadSupport
import org.scalatra.json.JacksonJsonSupport

class ImportDataApi extends ScalatraServlet with FileUploadSupport with JacksonJsonSupport with RequestHeaderAuthentication {

  protected implicit val jsonFormats: Formats = DefaultFormats
  private val importLogger = LoggerFactory.getLogger(getClass)
  private val CSV_LOG_PATH = "/tmp/csv_data_import_logs/"
  private val csvImporter = new RoadLinkCsvImporter {
    override val roadLinkService: RoadLinkService = Digiroad2Context.roadLinkService
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

  post("/csv") {
    //        TODO:
    //    create csv importer
    //      Error handling
    //      If link id is not found in VVH complementary links, list missing IDs and update others
    //      If update fails in OTH (override can't be added or updated), log failing IDs and update others
    //      If update fails in VVH, list failing IDs and update others

    if (!userProvider.getCurrentUser().isOperator()) {
      halt(Forbidden("Vain operaattori voi suorittaa Excel-ajon"))
    }
    val excludedLinks = List(ExcludedLink("123", "4"))
    val unSuccessfulDummyResult = ImportResult(Nil, Nil, Nil, excludedLinks)
    val successfulDummyResult = ImportResult(Nil, Nil, Nil, Nil)

    val response = successfulDummyResult match {
      case ImportResult(Nil, Nil, Nil, Nil) => "CSV tiedosto käsitelty."
      case ImportResult(Nil, Nil, Nil, excludedLinks) => "CSV tiedosto käsitelty. Seuraavat päivitykset on jätetty huomioimatta:\n" + pretty(Extraction.decompose(excludedLinks))
      case _ => pretty(Extraction.decompose(successfulDummyResult))
    }
    println(response)

  }

}

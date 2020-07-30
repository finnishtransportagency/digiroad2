package fi.liikennevirasto.digiroad2.dataexport

import fi.liikennevirasto.digiroad2.asset.DateParser
import fi.liikennevirasto.digiroad2.asset.DateParser.DateTimePropertyFormat
import fi.liikennevirasto.digiroad2.authentication.RequestHeaderAuthentication
import fi.liikennevirasto.digiroad2.csvDataExporter.AssetReportCsvExporter
import fi.liikennevirasto.digiroad2.dao.ExportReportDAO
import fi.liikennevirasto.digiroad2.middleware.CsvDataExporterInfo
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.{CsvDataExporter, Digiroad2Context, DigiroadEventBus}
import fi.liikennevirasto.digiroad2.user.UserProvider
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.json4s.{CustomSerializer, DefaultFormats, Formats, JString}
import org.scalatra.{Forbidden, ScalatraServlet, Unauthorized}
import org.scalatra.json.JacksonJsonSupport


class ExportDataApi( roadLinkService: RoadLinkService, userProvider: UserProvider = Digiroad2Context.userProvider, val eventBus: DigiroadEventBus = Digiroad2Context.eventbus)
extends ScalatraServlet with JacksonJsonSupport with RequestHeaderAuthentication {

  case object DateTimeSerializer extends CustomSerializer[DateTime](format => ( {
    case _ => throw new NotImplementedError("DateTime deserialization")
  }, {
    case d: DateTime => JString(d.toString(DateTimePropertyFormat))
  }))

  protected implicit val jsonFormats: Formats = DefaultFormats + DateTimeSerializer

  lazy val csvDataExporter = new CsvDataExporter( eventBus)
  lazy val assetReportCsvExporter =  new AssetReportCsvExporter(roadLinkService, eventBus, userProvider)
  val importLogDao: ExportReportDAO = new ExportReportDAO

  before() {
    contentType = formats("json")
    try {
      authenticateForApi(request)(userProvider)
    } catch {
      case ise: IllegalStateException => halt(Unauthorized("Authentication error: " + ise.getMessage))
    }
    response.setHeader("Digiroad2-Server-Originated-Response", "true")
  }

  get("/downloadCsv/:id") {
    val id = params("id")

    if (id.trim.isEmpty) {
      ""
    } else {
      val data = csvDataExporter.getExportById(id.toLong)

      data match {
        case  Some(info) => info
        case _ =>
      }
    }

  }

  get("/log") {
    csvDataExporter.getByUser(userProvider.getCurrentUser().username)
  }

  get("/logs/:id") {
    val id = params("id").toLong
    csvDataExporter.getById(id).getOrElse("Logia ei löytynyt.")
  }

  get("/logs/:ids") {
    val ids = params("ids").split(',').map(_.toLong).toSet
    csvDataExporter.getByIds(ids)
  }

  post("/generateCsvReport/:municipalities/:assetTypes") {
    val municipalitiesParam = params("municipalities")
    val assetTypesParam = params("assetTypes")

    validateOperation()

    val user = userProvider.getCurrentUser()
    val filename = "export_".concat(DateParser.dateToString(DateTime.now, DateTimeFormat.forPattern("ddMMyyyy_HHmmss")) )
                            .concat(".csv")


    val municipalities = assetReportCsvExporter.decodeMunicipalitiesToProcess( municipalitiesParam.split(",").map(_.toInt).toList )
    val assetTypes = assetReportCsvExporter.decodeAssetsToProcess( assetTypesParam.split(",").map(_.toInt).toList )

    val logId = csvDataExporter.insertData( user.username, filename, assetTypes.mkString(","), municipalities.mkString(",") )

    eventBus.publish("exportCSVData", CsvDataExporterInfo(assetTypes, municipalities, filename, user, logId) )

    csvDataExporter.getById(logId)
  }

  def validateOperation(): Unit = {
    if(!(userProvider.getCurrentUser().isOperator())) {
      halt(Forbidden("Vain operaattori voi suorittaa tämän toiminnon"))
    }
  }

}

package fi.liikennevirasto.digiroad2.csvDataImporter

import java.io.{InputStream, InputStreamReader}

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import fi.liikennevirasto.digiroad2.{AssetProperty, CsvDataImporterOperations, DigiroadEventBus, ExcludedRow, ImportResult, IncompleteRow, MalformedRow, Status}
import fi.liikennevirasto.digiroad2.asset.{MaintenanceRoadAsset, SideCode}
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.linearasset.{MaintenanceRoad, Properties}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{MaintenanceService, Measures}
import org.apache.commons.lang3.StringUtils.isBlank

class MaintenanceRoadCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends CsvDataImporterOperations {
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def eventBus: DigiroadEventBus = eventBusImpl

  case class ImportMaintenanceRoadResult(incompleteRows: List[IncompleteRow] = Nil,
                                         malformedRows: List[MalformedRow] = Nil,
                                         excludedRows: List[ExcludedRow] = Nil) extends ImportResult

  type ImportResultData = ImportMaintenanceRoadResult
  case class CsvAssetRow(properties: Seq[AssetProperty])

  lazy val maintenanceService: MaintenanceService = new MaintenanceService(roadLinkService, eventBusImpl)

  private val intFieldMappings = Map(
    "new_ko" -> "rightOfUse",
    "or_access" -> "maintenanceResponsibility",
    "linkid" -> "linkid"
  )

  val mappings : Map[String, String] = intFieldMappings

  private val mandatoryFields = List("linkid", "new_ko", "or_access")

  val MandatoryParameters: Set[String] = mappings.keySet ++ mandatoryFields

  private def findMissingParameters(csvRowWithHeaders: Map[String, String]): List[String] = {
    MandatoryParameters.diff(csvRowWithHeaders.keys.toSet).toList
  }

  private def verifyValueType(parameterName: String, parameterValue: String): ParsedRow = {
    parameterValue.forall(_.isDigit) match {
      case true => (Nil, List(AssetProperty(columnName = intFieldMappings(parameterName), value = parameterValue.toInt)))
      case false => (List(parameterName), Nil)
    }
  }

  private def assetRowToProperties(csvRowWithHeaders: Map[String, String]): ParsedRow = {
    csvRowWithHeaders.foldLeft(Nil: MalformedParameters, Nil: ParsedProperties) { (result, parameter) =>
      val (key, value) = parameter

      if (isBlank(value)) {
        if (mandatoryFields.contains(key))
          result.copy(_1 = List(key) ::: result._1, _2 = result._2)
        else
          result
      } else {
        if (intFieldMappings.contains(key)) {
          val (malformedParameters, properties) = verifyValueType(key, value)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else
          result
      }
    }
  }

  def getPropertyValue(maintenanceRoadAttributes: CsvAssetRow, propertyName: String): Any = {
    maintenanceRoadAttributes.properties.find(prop => prop.columnName == propertyName).map(_.value).get
  }

  def createMaintenanceRoads(maintenanceRoadAttributes: CsvAssetRow, username: String): Unit = {
    val linkId = getPropertyValue(maintenanceRoadAttributes, "linkid").asInstanceOf[Integer].toLong
    val newKoProperty = Properties("huoltotie_kayttooikeus", "single_choice", getPropertyValue(maintenanceRoadAttributes, "rightOfUse").toString)
    val orAccessProperty = Properties("huoltotie_huoltovastuu", "single_choice", getPropertyValue(maintenanceRoadAttributes, "maintenanceResponsibility").toString)

    roadLinkService.getRoadLinksAndComplementariesFromVVH(Set(linkId)).map { roadlink =>
      val values = MaintenanceRoad(Seq(newKoProperty, orAccessProperty))

      maintenanceService.createWithHistory(MaintenanceRoadAsset.typeId, linkId, values,
        SideCode.BothDirections.value, Measures(0, roadlink.length), username, Some(roadlink))
    }
  }

  def importAssets(inputStream: InputStream, fileName: String, username: String, logId: Long) {
    try {
      val result = processing(inputStream, username)
      result match {
        case ImportMaintenanceRoadResult(Nil, Nil, Nil) => update(logId, Status.OK)
        case _ =>
          val content = mappingContent(result)
          update(logId, Status.NotOK, Some(content))
      }
    } catch {
      case e: Exception =>
        update(logId, Status.Abend, Some("Latauksessa tapahtui odottamaton virhe: " + e.toString)) //error when saving log
    } finally {
      inputStream.close()
    }
  }


  def processing(inputStream: InputStream, username: String): ImportResultData = {
    val streamReader = new InputStreamReader(inputStream, "UTF-8")
    val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
      override val delimiter: Char = ';'
    })
    csvReader.allWithHeaders().foldLeft(ImportMaintenanceRoadResult()) { (result, row) =>
      val csvRow = row.map(r => (r._1.toLowerCase, r._2))
      val missingParameters = findMissingParameters(csvRow)
      val (malformedParameters, properties) = assetRowToProperties(csvRow)

      if (missingParameters.nonEmpty || malformedParameters.nonEmpty) {
        result.copy(
          incompleteRows = missingParameters match {
            case Nil => result.incompleteRows
            case parameters => IncompleteRow(missingParameters = parameters, csvRow = rowToString(csvRow)) :: result.incompleteRows
          },
          malformedRows = malformedParameters match {
            case Nil => result.malformedRows
            case parameters => MalformedRow(malformedParameters = parameters, csvRow = rowToString(csvRow)) :: result.malformedRows
          })

      } else {
        val parsedRow = CsvAssetRow(properties = properties)
        createMaintenanceRoads(parsedRow, username)
        result
      }
    }
  }
}

package fi.liikennevirasto.digiroad2.csvDataImporter

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import fi.liikennevirasto.digiroad2.asset.{DynamicProperty, DynamicPropertyValue, MaintenanceRoadAsset, SideCode}
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.linearasset.{DynamicAssetValue, DynamicValue}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.linearasset.{MaintenanceService, Measures}
import fi.liikennevirasto.digiroad2.service.{EditingRestrictionsService, RoadLinkService}
import fi.liikennevirasto.digiroad2.user.User
import fi.liikennevirasto.digiroad2._
import org.apache.commons.lang3.StringUtils.isBlank

import java.io.{InputStream, InputStreamReader}

class MaintenanceRoadCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends CsvDataImporterOperations {
  override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def roadLinkClient: RoadLinkClient = roadLinkServiceImpl.roadLinkClient
  override def eventBus: DigiroadEventBus = eventBusImpl


  def editingRestrictionsService = new EditingRestrictionsService

  case class NotImportedData(reason: String, csvRow: String)

  case class ImportMaintenanceRoadResult(incompleteRows: List[IncompleteRow] = Nil,
                                         malformedRows: List[MalformedRow] = Nil,
                                         excludedRows: List[ExcludedRow] = Nil,
                                         notImportedData: List[NotImportedData] = Nil) extends ImportResult

  type ImportResultData = ImportMaintenanceRoadResult
  case class CsvAssetRow(properties: Seq[AssetProperty])

  lazy val maintenanceService: MaintenanceService = new MaintenanceService(roadLinkService, eventBusImpl)

  private val intFieldMappings = Map(
    "new_ko" -> "rightOfUse",
    "or_access" -> "maintenanceResponsibility"
  )

  val mappings : Map[String, String] = intFieldMappings

  private val mandatoryFields = List("linkid", "new_ko", "or_access")

  val MandatoryParameters: Set[String] = mappings.keySet ++ mandatoryFields

  override def mappingContent(result: ImportResultData): String = {
    val excludedResult = result.excludedRows.map { rows => "<li>" + rows.affectedRows -> rows.csvRow + "</li>" }
    val incompleteResult = result.incompleteRows.map { rows => "<li>" + rows.missingParameters.mkString(";") -> rows.csvRow + "</li>" }
    val malformedResult = result.malformedRows.map { rows => "<li>" + rows.malformedParameters.mkString(";") -> rows.csvRow + "</li>" }
    val notImportedData = result.notImportedData.map { rows => "<li>" + rows.reason -> rows.csvRow + "</li>" }

    s"<ul> excludedLinks: ${excludedResult.mkString.replaceAll("[(|)]{1}", "")} </ul>" +
      s"<ul> incompleteRows: ${incompleteResult.mkString.replaceAll("[(|)]{1}", "")} </ul>" +
      s"<ul> malformedRows: ${malformedResult.mkString.replaceAll("[(|)]{1}", "")} </ul>" +
      s"<ul>notImportedData: ${notImportedData.mkString.replaceAll("[(|)]{1}", "")}</ul>"
  }

  private def findMissingParameters(csvRowWithHeaders: Map[String, String]): List[String] = {
    MandatoryParameters.diff(csvRowWithHeaders.keys.toSet).toList
  }

  private def verifyValueType(parameterName: String, parameterValue: String): ParsedRow = {
    if (intFieldMappings.contains(parameterName) && parameterValue.forall(_.isDigit)) {
      (Nil, List(AssetProperty(columnName = intFieldMappings(parameterName), value = parameterValue.toInt)))
    } else {
      (List(parameterName), Nil)
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
        } else if (key == "linkid" ) {
          result.copy(_1 = result._1, _2 = AssetProperty(columnName = key, value = value) :: result._2)
        } else {
          result
        }
      }
    }
  }

  def getPropertyValue(maintenanceRoadAttributes: CsvAssetRow, propertyName: String): Any = {
    maintenanceRoadAttributes.properties.find(prop => prop.columnName == propertyName).map(_.value).get
  }

  def createMaintenanceRoads(maintenanceRoadAttributes: CsvAssetRow, username: String): Unit = {
    val linkId = getPropertyValue(maintenanceRoadAttributes, "linkid").asInstanceOf[String]
    val newKoProperty = DynamicProperty("huoltotie_kayttooikeus", "single_choice", false, Seq(DynamicPropertyValue(getPropertyValue(maintenanceRoadAttributes, "rightOfUse"))))
    val orAccessProperty = DynamicProperty("huoltotie_huoltovastuu", "single_choice", false, Seq(DynamicPropertyValue(getPropertyValue(maintenanceRoadAttributes, "maintenanceResponsibility"))))

    roadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(linkId)).map { roadLink =>
      val values = DynamicValue(DynamicAssetValue(Seq(newKoProperty, orAccessProperty)))

      maintenanceService.createWithHistory(MaintenanceRoadAsset.typeId, linkId, values,
        SideCode.BothDirections.value, Measures(0, roadLink.length), username, Some(roadLink))
    }
  }

  def importAssets(inputStream: InputStream, fileName: String, user: User, logId: Long) {
    try {
      val result = processing(inputStream, user)
      result match {
        case ImportMaintenanceRoadResult(Nil, Nil, Nil, Nil) => update(logId, Status.OK)
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

  def getPropertyValueOption(pointAssetAttributes: ParsedProperties, propertyName: String): Option[Any] = {
    pointAssetAttributes.find(prop => prop.columnName == propertyName).map(_.value)
  }

  def verifyData(parsedRow: ParsedProperties, user: User): List[String] = {
    val optLinkId = getPropertyValueOption(parsedRow, "linkid").asInstanceOf[Option[String]]

    (optLinkId) match {
      case Some(linkId) =>
        val roadLink = roadLinkService.getRoadLinkByLinkId(linkId)
        roadLink.isEmpty match {
          case false =>
            if (editingRestrictionsService.isEditingRestricted(MaintenanceRoadAsset.typeId, roadLink.get.municipalityCode, roadLink.get.administrativeClass, true)) {
              List("Asset type editing is restricted within municipality or admininistrative class.")
            } else {
              Nil
            }
          case _ =>
            Nil
        }
      case _ =>
        Nil
    }
  }

  def processing(inputStream: InputStream, user: User): ImportResultData = {
    val streamReader = new InputStreamReader(inputStream, "UTF-8")
    val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
      override val delimiter: Char = ';'
    })
    csvReader.allWithHeaders().foldLeft(ImportMaintenanceRoadResult()) { (result, row) =>
      val csvRow = row.map(r => (r._1.toLowerCase, r._2))
      val missingParameters = findMissingParameters(csvRow)
      val (malformedParameters, properties) = assetRowToProperties(csvRow)
      val notImportedParameters = verifyData(properties, user)

      if (missingParameters.nonEmpty || malformedParameters.nonEmpty || notImportedParameters.nonEmpty) {
        result.copy(
          incompleteRows = missingParameters match {
            case Nil => result.incompleteRows
            case parameters => IncompleteRow(missingParameters = parameters, csvRow = rowToString(csvRow)) :: result.incompleteRows
          },
          malformedRows = malformedParameters match {
            case Nil => result.malformedRows
            case parameters => MalformedRow(malformedParameters = parameters, csvRow = rowToString(csvRow)) :: result.malformedRows
          },
          notImportedData = notImportedParameters match {
            case Nil => result.notImportedData
            case parameters => NotImportedData(reason = parameters.head, csvRow = rowToString(row)) :: result.notImportedData
          }
        )

      } else {
        val parsedRow = CsvAssetRow(properties = properties)
        createMaintenanceRoads(parsedRow, user.username)
        result
      }
    }
  }
}

package fi.liikennevirasto.digiroad2.csvDataImporter

import java.io.{InputStream, InputStreamReader}
import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.{DateParser, SideCode}
import fi.liikennevirasto.digiroad2.lane._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.lane.LaneService
import fi.liikennevirasto.digiroad2.user.User
import fi.liikennevirasto.digiroad2.util.{LaneUtils, Track}
import org.apache.commons.lang3.StringUtils.isBlank
import scala.util.{Success, Try}


class LanesCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends CsvDataImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) {
  case class NotImportedData(reason: String, csvRow: String)
  case class ImportResultLaneAsset(incompleteRows: List[IncompleteRow] = Nil,
                                   malformedRows: List[MalformedRow] = Nil,
                                   excludedRows: List[ExcludedRow] = Nil,
                                   notImportedData: List[NotImportedData] = Nil,
                                   createdData: List[ParsedProperties] = Nil)  extends ImportResult
  type ImportResultData = ImportResultLaneAsset
  type ParsedCsv = (MalformedParameters, List[ParsedProperties])
  def laneUtils = LaneUtils()
  lazy val laneService: LaneService = new LaneService(roadLinkServiceImpl, eventBusImpl)

  private val csvImportUser = "csv_importer"

  private val nonMandatoryFieldsMapping: Map[String, String] = Map(
    "id" -> "id",
    "tietyyppi" -> "road type",
    "s_tietyyppi" -> "name road type",
    "s_katyyppi" -> "name lane type"
  )

  private val intValueFieldsMapping: Map[String, String] = Map(
    "tie" -> "road number",
    "ajorata" -> "track",
    "osa" -> "road part",
    "aet" -> "initial distance",
    "let" -> "end distance"
  )


  private val laneNumberFieldMapping: Map[String, String] = Map("kaista" -> "lane")
  private val laneTypeFieldMapping: Map[String, String] = Map("katyyppi" -> "lane type")
  private val dateFieldMapping: Map[String, String] = Map("alkupvm" -> "start date")

  val mandatoryFieldsMapping: Map[String, String] = laneNumberFieldMapping ++ intValueFieldsMapping ++ laneTypeFieldMapping

  private def findMissingParameters(csvRowWithHeaders: Map[String, String]): List[String] = {
    mandatoryFieldsMapping.keySet.diff(csvRowWithHeaders.keys.toSet).toList
  }

  def getPropertyValue(pointAssetAttributes: ParsedProperties, propertyName: String): String = {
    pointAssetAttributes.find(prop => prop.columnName == propertyName).map(_.value).get.asInstanceOf[String]
  }

  def getPropertyValueOption(pointAssetAttributes: ParsedProperties, propertyName: String): Option[String] = {
    pointAssetAttributes.find(prop => prop.columnName == propertyName).map(_.value).asInstanceOf[Option[String]]
  }

  def verifyDateType(parameterName: String, parameterValue: String): ParsedRow = {
    val formattedValue = parameterValue.trim.replaceAll("[/-]", ".")

    val isDateParserOk = Try(DateParser.stringToDate(formattedValue, DateParser.DatePropertyFormat))

    if (isDateParserOk.isSuccess) {
      (Nil, List(AssetProperty(columnName = dateFieldMapping(parameterName), value = formattedValue)))
    } else {
      (List(parameterName), Nil)
    }

  }

  def verifyIntType(parameterName: String, parameterValue: String): ParsedRow = {
    if (parameterValue.forall(_.isDigit)) {
      (Nil, List(AssetProperty(columnName = intValueFieldsMapping(parameterName), value = parameterValue)))
    } else {
      (List(parameterName), Nil)
    }
  }

  def verifyLaneNumber(parameterName: String, parameterValue: String): ParsedRow = {
    val trimmedValue = parameterValue.trim

    Try(trimmedValue.toInt) match {
                case Success(value) if (LaneNumber.isValidLaneNumber(value)) =>
                  (Nil, List(AssetProperty(columnName = laneNumberFieldMapping(parameterName), value = trimmedValue)))

                case _ =>
                  (List(parameterName), Nil)
    }
  }

  def verifyLaneType(parameterName: String, parameterValue: String): ParsedRow = {
    if (parameterValue.forall(_.isDigit) && LaneType.apply(parameterValue.toInt) != LaneType.Unknown) {
      (Nil, List(AssetProperty(columnName = laneTypeFieldMapping(parameterName), value = parameterValue)))
    } else {
      (List(parameterName), Nil)
    }
  }

  def assetRowToAttributes(csvRowWithHeaders: Map[String, String]): ParsedRow = {
    csvRowWithHeaders.foldLeft(Nil: MalformedParameters, Nil: ParsedProperties) {
      (result, parameter) =>
        val (key, value) = parameter

        if (isBlank(value)) {
          if (mandatoryFieldsMapping.contains(key))
            result.copy(_1 = List(key) ::: result._1, _2 = result._2)
          else if (nonMandatoryFieldsMapping.contains(key))
            result.copy(_2 = AssetProperty(columnName = nonMandatoryFieldsMapping(key), value = value) :: result._2)
          else
            result
        } else {
          if (intValueFieldsMapping.contains(key)) {
            val (malformedParameters, properties) = verifyIntType(key, value)
            result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
          } else if (laneNumberFieldMapping.contains(key)) {
            val (malformedParameters, properties) = verifyLaneNumber(key, value)
            result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
          } else if (laneTypeFieldMapping.contains(key)) {
            val (malformedParameters, properties) = verifyLaneType(key, value)
            result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
          }else if(mandatoryFieldsMapping.contains(key)) {
            result.copy(_2 = AssetProperty(columnName = mandatoryFieldsMapping(key), value = value) :: result._2)
          } else if (nonMandatoryFieldsMapping.contains(key)) {
            result.copy(_2 = AssetProperty(columnName = nonMandatoryFieldsMapping(key), value = value) :: result._2)
          } else if ( dateFieldMapping.contains(key) ){
            val (malformedParameters, properties) = verifyDateType(key, value)
            result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
          }else {
            result
          }
        }
    }
  }

  def verifyData(parsedRow: ParsedProperties): ParsedCsv = {
    val optTrack = getPropertyValueOption(parsedRow, "track")
    val optLane = getPropertyValueOption(parsedRow, "lane")

    (optTrack, optLane) match {
      case (Some(track), Some(lane)) =>
        (Track.apply(track.toInt), lane.charAt(0).getNumericValue) match {
          case (Track.RightSide, 2) | (Track.LeftSide, 1)  => (List(s"Wrong lane number for the track given"), List())
          case (_, _) => (List(), List(parsedRow))
        }
      case _ =>
        (Nil, Nil)
    }
  }

  override def mappingContent(result: ImportResultData): String = {
    val excludedResult = result.excludedRows.map { rows => s"<li> ${rows.affectedRows} -> ${rows.csvRow} </li>" }
    val incompleteResult = result.incompleteRows.map { rows => s"<li> ${rows.missingParameters.mkString(";")} -> ${rows.csvRow} </li>" }
    val malformedResult = result.malformedRows.map { rows => s"<li> ${rows.malformedParameters.mkString(";")}  -> ${rows.csvRow} </li>" }
    val notImportedData = result.notImportedData.map { rows => "<li>" + rows.reason -> rows.csvRow + "</li>" }

    s"<ul> excludedLinks: ${excludedResult.mkString.replaceAll("[(|)]{1}", "")} </ul>" +
    s"<ul> incompleteRows: ${incompleteResult.mkString.replaceAll("[(|)]{1}", "")} </ul>" +
    s"<ul> malformedRows: ${malformedResult.mkString.replaceAll("[(|)]{1}", "")} </ul>" +
    s"<ul> notImportedData: ${notImportedData.mkString.replaceAll("[(|)]{1}", "")}</ul>"
  }

  def createAsset(laneAssetProperties: Seq[ParsedProperties], user: User, result: ImportResultData): ImportResultData = {
    laneAssetProperties.groupBy(lane => getPropertyValue(lane, "road number")).foreach { lane =>
      lane._2.foreach { props =>
        val roadPartNumber = getPropertyValue(props, "road part").toLong
        val laneCode = getPropertyValue(props, "lane")

        val initialDistance = getPropertyValue(props, "initial distance").toLong
        val endDistance = getPropertyValue(props, "end distance").toLong
        val track = getPropertyValue(props, "track").toInt
        val laneType = getPropertyValue(props, "lane type").toInt

        val startDate = getPropertyValueOption(props, "start date").getOrElse("")

        val sideCode = track match {
          case 1 | 2 => SideCode.BothDirections
          case _ => if (laneCode.charAt(0).getNumericValue == 1) SideCode.TowardsDigitizing else SideCode.AgainstDigitizing
        }

        val properties = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(laneCode))),
                        LaneProperty("lane_type", Seq(LanePropertyValue(laneType))),
                        LaneProperty("start_date", Seq(LanePropertyValue(startDate)))                          )

        //id, start measure, end measure and municipalityCode doesnt matter
        val incomingLane = NewIncomeLane(0, 0, 0, 0, isExpired = false, isDeleted = false, properties)
        val laneRoadAddressInfo = LaneRoadAddressInfo(lane._1.toLong, roadPartNumber, initialDistance, roadPartNumber, endDistance, track)
        laneUtils.processNewLanesByRoadAddress(Set(incomingLane), laneRoadAddressInfo, sideCode.value, user.username, false)
      }
    }
    result
  }

  def importAssets(inputStream: InputStream, fileName: String, user: User, logId: Long): Unit = {
    try {
      val result = processing(inputStream, user)
      result match {
        case ImportResultLaneAsset(Nil, Nil, Nil, Nil, _) => update(logId, Status.OK)
        case _ =>
          val content = mappingContent(result)
          update(logId, Status.NotOK, Some(content))
      }
    } catch {
      case e: Exception =>
        update(logId, Status.Abend, Some("Lähettäessä tapahtui odottamaton virhe: " + e.toString))
    } finally {
      inputStream.close()
    }
  }

  def processing(inputStream: InputStream, user: User): ImportResultData = {
    val streamReader = new InputStreamReader(inputStream, "UTF-8")
    val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
      override val delimiter: Char = ';'
    })

    withDynTransaction {
      val result = csvReader.allWithHeaders().foldLeft(ImportResultLaneAsset()) {
        (result, row) =>
          val csvRow = row.map(r => (r._1.toLowerCase(), r._2))
          val missingParameters = findMissingParameters(csvRow)
          val (malformedParameters, properties) = assetRowToAttributes(csvRow)
          val (notImportedParameters, parsedRow) = verifyData(properties)

          if (missingParameters.nonEmpty || malformedParameters.nonEmpty || notImportedParameters.nonEmpty) {
            result.copy(
              incompleteRows = missingParameters match {
                case Nil => result.incompleteRows
                case parameters =>
                  IncompleteRow(missingParameters = parameters, csvRow = rowToString(csvRow)) :: result.incompleteRows
              },
              malformedRows = malformedParameters match {
                case Nil => result.malformedRows
                case parameters =>
                  MalformedRow(malformedParameters = parameters, csvRow = rowToString(csvRow)) :: result.malformedRows
              },
              notImportedData = notImportedParameters match {
                case Nil => result.notImportedData
                case parameters =>
                  NotImportedData(reason = parameters.head, csvRow = rowToString(csvRow)) :: result.notImportedData
              })
          } else {
            result.copy(createdData = parsedRow ++ result.createdData)
          }
      }

      // Expire all Lanes in State Roads IF exists some data to create new lanes
      if (result.createdData.nonEmpty) {
        laneService.expireAllLanesInStateRoad(csvImportUser)
      }

      // Create the new lanes
      createAsset(result.createdData, user, result)
    }
  }
}
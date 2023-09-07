package fi.liikennevirasto.digiroad2.csvDataImporter

import java.io.{InputStream, InputStreamReader}
import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import fi.liikennevirasto.digiroad2.asset.{DateParser, PropertyValue, SimplePointAssetProperty}
import fi.liikennevirasto.digiroad2.dao.Sequences
import fi.liikennevirasto.digiroad2.lane.{LaneNumber, LaneType}
import fi.liikennevirasto.digiroad2.{AssetProperty, CsvDataImporterOperations, ExcludedRow, GeometryUtils, ImportResult, IncompleteRow, MalformedRow, Point, Status}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.user.User
import org.apache.commons.lang3.StringUtils.isBlank

import scala.util.Try

trait PointAssetCsvImporter extends CsvDataImporterOperations {
  case class CsvAssetRowAndRoadLink(properties: ParsedProperties, roadLink: Seq[RoadLink])

  case class NotImportedData(reason: String, csvRow: String)
  case class ImportResultPointAsset(incompleteRows: List[IncompleteRow] = Nil,
                                    malformedRows: List[MalformedRow] = Nil,
                                    excludedRows: List[ExcludedRow] = Nil,
                                    notImportedData: List[NotImportedData] = Nil,
                                    createdData: List[CsvAssetRowAndRoadLink] = Nil) extends ImportResult

  type ParsedCsv = (MalformedParameters, Seq[CsvAssetRowAndRoadLink])
  type ImportResultData = ImportResultPointAsset

  case class CsvPointAsset(lon: Double, lat: Double, linkId: String, propertyData: Set[SimplePointAssetProperty], validityDirection: Int, bearing: Option[Int], mValue: Double, roadLink: RoadLink, isFloating: Boolean)

  final val MinimumDistanceFromRoadLink: Double = 3.0

  val coordinateMappings = Map(
    "koordinaatti x" -> "lon",
    "koordinaatti y" -> "lat"
  )

  private val suggestBoxPublicId = "suggest_box"

  val longValueFieldsMapping: Map[String, String] = coordinateMappings
  val codeValueFieldsMapping: Map[String, String] = Map()
  val stringValueFieldsMapping: Map[String, String] = Map()
  val intValueFieldsMapping: Map[String, String] = Map()
  val dateFieldsMapping: Map[String, String] = Map()

  val specificFieldsMapping: Map[String, String] = Map()
  val nonMandatoryFieldsMapping: Map[String, String] = Map()

  def mandatoryFields: Set[String] = coordinateMappings.keySet
  def mandatoryFieldsMapping: Map[String, String] = coordinateMappings

  def checkMinimumDistanceFromRoadLink(pointPosition: Point, linkGeometry: Seq[Point]): Boolean = {
    GeometryUtils.minimumDistance(pointPosition, linkGeometry) >= MinimumDistanceFromRoadLink
  }

  def findMissingParameters(csvRoadWithHeaders: Map[String, String]): List[String] = {
    mandatoryFieldsMapping.keySet.diff(csvRoadWithHeaders.keys.toSet).toList
  }

  def verifyDoubleType(parameterName: String, parameterValue: String): ParsedRow = {
    if (parameterValue.matches("[0-9.]*")) {
      (Nil, List(AssetProperty(columnName = longValueFieldsMapping(parameterName), value = BigDecimal(parameterValue))))
    } else {
      (List(parameterName), Nil)
    }
  }

  def verifyDateType(parameterName: String, parameterValue: String): ParsedRow = {
    try {
      DateParser.DatePropertyFormat.parseDateTime(parameterValue)
      (Nil, List(AssetProperty(columnName = dateFieldsMapping(parameterName), value = parameterValue)))
    } catch {
      case _: Throwable => (List(parameterName), Nil)
    }
  }

  def verifyIntType(parameterName: String, parameterValue: String): ParsedRow = {
    if (parameterValue.forall(_.isDigit)) {
      (Nil, List(AssetProperty(columnName = intValueFieldsMapping(parameterName), value = parameterValue)))
    } else {
      (List(parameterName), Nil)
    }
  }

  def verifyStringType(parameterName: String, parameterValue: String): ParsedRow = {
    if(parameterValue.trim.forall(_.isLetter)) {
      (Nil, List(AssetProperty(columnName = stringValueFieldsMapping(parameterName), value = parameterValue)))
    } else {
      (List(parameterName), Nil)
    }
  }

  def singleChoiceToProperty(parameterName: String, assetSingleChoice: String, singleChoiceAcceptableValues: Set[Int], singleChoiceMapping: Map[String, String]): ParsedRow = {
    tryToInt(assetSingleChoice) match {
      case Some(value) if singleChoiceAcceptableValues.contains(value) =>
        (Nil, List(AssetProperty(columnName = singleChoiceMapping(parameterName), value = value)))
      case _ =>
        (List(s"Invalid value for $parameterName"), Nil)
    }
  }

  def tryToInt(propertyValue: String ) : Option[Int] = {
    Try(propertyValue.toInt).toOption
  }

  def tryToDouble(propertyValue: String ) : Option[Double] = {
    Try(propertyValue.toDouble).toOption
  }

  def getPropertyValue(pointAssetAttributes: ParsedProperties, propertyName: String): Any = {
    pointAssetAttributes.find(prop => prop.columnName == propertyName).map(_.value).get
  }

  def getPropertyValueOption(pointAssetAttributes: ParsedProperties, propertyName: String): Option[Any] = {
    pointAssetAttributes.find(prop => prop.columnName == propertyName).map(_.value)
  }

  def extractPropertyValues(listPublicIds: Seq[String], listFieldNames: Seq[String], attributes: ParsedProperties, withGroupedId: Boolean): Seq[Option[SimplePointAssetProperty]] = {
    val groupedId = if (withGroupedId) Sequences.nextGroupedIdSeqValue else 0
    (listPublicIds, listFieldNames).zipped.map { (publicId, fieldName) =>
      val propertyInfo = getPropertyValueOption(attributes, fieldName)
      if (propertyInfo.get != null && propertyInfo.nonEmpty)
        Some(SimplePointAssetProperty(publicId, Seq(PropertyValue(propertyInfo.get.toString)), groupedId))
      else
        None
    } ++ Set(Some(SimplePointAssetProperty(suggestBoxPublicId, Seq(PropertyValue("0")), groupedId))) //not possible to insert suggested lights through csv
  }

  def getCoordinatesFromProperties(csvProperties: ParsedProperties): Point = {
    val lon = getPropertyValue(csvProperties, "lon").asInstanceOf[BigDecimal].toLong
    val lat = getPropertyValue(csvProperties, "lat").asInstanceOf[BigDecimal].toLong
    Point(lon, lat)
  }

  def verifyData(parsedRow: ParsedProperties, user: User): ParsedCsv = {
    val optLon = getPropertyValueOption(parsedRow, "lon").asInstanceOf[Option[BigDecimal]]
    val optLat = getPropertyValueOption(parsedRow, "lat").asInstanceOf[Option[BigDecimal]]

    (optLon, optLat) match {
      case (Some(lon), Some(lat)) =>
        val roadLinks = roadLinkService.getClosestRoadlinkForCarTraffic(user, Point(lon.toLong, lat.toLong))
        roadLinks.isEmpty match {
          case true => (List(s"No Rights for Municipality or nonexistent road links near asset position"), Seq())
          case false => (List(), Seq(CsvAssetRowAndRoadLink(parsedRow, roadLinks)))
        }
      case _ =>
        (Nil, Nil)
    }
  }

  def csvLaneValidator(optLaneType: Option[Int], optLaneNumber: Option[String]): (Boolean, List[String]) = {
    (optLaneType, optLaneNumber) match {
      case (_, Some(lane)) if lane.trim.nonEmpty && !lane.matches("^([1-3][1-9])$") =>
        (false, List("Invalid lane"))
      case (Some(laneType), Some(laneNumber)) if laneType != LaneType.Unknown.value && laneNumber.trim.nonEmpty =>
        val isMainTypeAndWrongLaneNumber = laneType == LaneType.Main.value && !LaneNumber.isMainLane(laneNumber.toInt)
        val isNotMainTypeAndIsMainLaneNumber = laneType != LaneType.Main.value && LaneNumber.isMainLane(laneNumber.toInt)

        if ( isMainTypeAndWrongLaneNumber || isNotMainTypeAndIsMainLaneNumber) {
          (false, List("Invalid lane and lane type match") )
        } else {
          (true, Nil)
        }

      case (_,_) => (true, Nil)
    }
  }

  override def mappingContent(result: ImportResultData): String  = {
    val excludedResult = result.excludedRows.map{rows => "<li>" + rows.affectedRows ->  rows.csvRow + "</li>"}
    val incompleteResult = result.incompleteRows.map{ rows=> "<li>" + rows.missingParameters.mkString(";") -> rows.csvRow + "</li>"}
    val malformedResult = result.malformedRows.map{ rows => "<li>" + rows.malformedParameters.mkString(";") -> rows.csvRow + "</li>"}
    val notImportedData = result.notImportedData.map{ rows => "<li>" + rows.reason -> rows.csvRow + "</li>"}

    s"<ul> excludedLinks: ${excludedResult.mkString.replaceAll("[(|)]{1}","")} </ul>" +
    s"<ul> incompleteRows: ${incompleteResult.mkString.replaceAll("[(|)]{1}","")} </ul>" +
    s"<ul> malformedRows: ${malformedResult.mkString.replaceAll("[(|)]{1}","")} </ul>" +
    s"<ul>notImportedData: ${notImportedData.mkString.replaceAll("[(|)]{1}","")}</ul>"
  }

  def assetRowToProperties(csvRowWithHeaders: Map[String, String]): ParsedRow = {
    csvRowWithHeaders.foldLeft(Nil: MalformedParameters, Nil: ParsedProperties) {
      (result, parameter) =>
        val (key, value) = parameter

        if (isBlank(value.toString)) {
          if (mandatoryFields.contains(key))
            result.copy(_1 = List(key) ::: result._1, _2 = result._2)
          else if (nonMandatoryFieldsMapping.contains(key))
            result.copy(_2 = AssetProperty(columnName = nonMandatoryFieldsMapping(key), value = value) :: result._2)
          else
            result
        } else {
          if (longValueFieldsMapping.contains(key)) {
            val (malformedParameters, properties) = verifyDoubleType(key, value.toString)
            result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
          } else if (intValueFieldsMapping.contains(key)) {
            val (malformedParameters, properties) = verifyIntType(key, value.toString)
            result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
          } else if (stringValueFieldsMapping.contains(key)) {
            val (malformedParameters, properties) = verifyStringType(key, value.toString)
            result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
          } else if(mandatoryFieldsMapping.contains(key))
            result.copy(_2 = AssetProperty(columnName = mandatoryFieldsMapping(key), value = value) :: result._2)
          else if (nonMandatoryFieldsMapping.contains(key))
            result.copy(_2 = AssetProperty(columnName = nonMandatoryFieldsMapping(key), value = value) :: result._2)
          else
           result
        }
    }
  }

  def createAsset(pointAssetAttributes: Seq[CsvAssetRowAndRoadLink], user: User, result: ImportResultData): ImportResultData

  def importAssets(inputStream: InputStream, fileName: String, user: User, logId: Long): Unit = {
    try {
      val result = processing(inputStream, user)
      result match {
        case ImportResultPointAsset(Nil, Nil, Nil, Nil, _) => update(logId, Status.OK)
        case _ =>
          val content = mappingContent(result)
          update(logId, Status.NotOK, Some(content))
      }
    } catch {
      case e: Exception =>
        update(logId, Status.Abend, Some("Problems creating point asset: " + e.toString))
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
      val result = csvReader.allWithHeaders().foldLeft(ImportResultPointAsset()) {
        (result, row) =>
          val csvRow = row.map(r => (r._1.toLowerCase(), r._2))
          val missingParameters = findMissingParameters(csvRow)
          val (malformedParameters, properties) = assetRowToProperties(csvRow)
          val (notImportedParameters, parsedRowAndRoadLink) = verifyData(properties, user)

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
            result.copy(
              createdData = parsedRowAndRoadLink match {
                case Nil => result.createdData
                case parameters =>
                  CsvAssetRowAndRoadLink(properties = parameters.head.properties, roadLink = parameters.head.roadLink) :: result.createdData
              })
          }
      }
      createAsset(result.createdData, user, result)
    }
  }

}


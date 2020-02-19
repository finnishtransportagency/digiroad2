package fi.liikennevirasto.digiroad2.csvDataImporter

import java.io.{InputStream, InputStreamReader}
import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import fi.liikennevirasto.digiroad2.{AssetProperty, CsvDataImporter, DigiroadEventBus, ExcludedRow, ImportResult, IncompleteRow, MalformedRow, Status}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.User
import org.apache.commons.lang3.StringUtils.isBlank

class LanesCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends CsvDataImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) {
  case class NotImportedData(reason: String, csvRow: String)
  case class ImportResultLaneAsset(incompleteRows: List[IncompleteRow] = Nil,
                                   malformedRows: List[MalformedRow] = Nil,
                                   excludedRows: List[ExcludedRow] = Nil,
                                   notImportedData: List[NotImportedData] = Nil,
                                   createdData: List[ParsedProperties] = Nil)  extends ImportResult
  type ImportResultData = ImportResultLaneAsset
  type ParsedCsv = (MalformedParameters, Seq[ParsedProperties])

  private val nonMandatoryFieldsMapping: Map[String, String] = Map(
    "id" -> "id",
    "tietyyppi" -> "road type",
    "s_tietyyppi" -> "name road type",
    "alkupvm" -> "date",
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

  private val mandatoryFieldsMapping: Map[String, String] = laneNumberFieldMapping ++ intValueFieldsMapping ++ laneTypeFieldMapping

  private def findMissingParameters(csvRowWithHeaders: Map[String, String]): List[String] = {
    mandatoryFieldsMapping.keySet.diff(csvRowWithHeaders.keys.toSet).toList
  }

  def verifyIntType(parameterName: String, parameterValue: String): ParsedRow = {
    if (parameterValue.forall(_.isDigit)) {
      (Nil, List(AssetProperty(columnName = intValueFieldsMapping(parameterName), value = parameterValue)))
    } else {
      (List(parameterName), Nil)
    }
  }

  def verifyLaneNumber(parameterName: String, parameterValue: String): ParsedRow = {
    if (parameterValue.forall(_.isDigit) && parameterValue.length == 2 && Seq(1, 2, 3).contains(parameterValue.charAt(0).getNumericValue)) {
      (Nil, List(AssetProperty(columnName = laneNumberFieldMapping(parameterName), value = parameterValue)))
    } else {
      (List(parameterName), Nil)
    }
  }

  //put this in the right file
  val laneTypes = (1 to 11) ++ (20 to 22)

  def verifyLaneType(parameterName: String, parameterValue: String): ParsedRow = {
    if (parameterValue.forall(_.isDigit) && laneTypes.contains(parameterValue.toInt)) {
      (Nil, List(AssetProperty(columnName = laneTypeFieldMapping(parameterName), value = parameterValue)))
    } else {
      (List(parameterName), Nil)
    }
  }

  def assetRowToAttributes(csvRowWithHeaders: Map[String, String]): ParsedRow = {
    csvRowWithHeaders.foldLeft(Nil: MalformedParameters, Nil: ParsedProperties) {
      (result, parameter) =>
        val (key, value) = parameter

        if (isBlank(value.toString)) {
          if (mandatoryFieldsMapping.contains(key))
            result.copy(_1 = List(key) ::: result._1, _2 = result._2)
          else if (nonMandatoryFieldsMapping.contains(key))
            result.copy(_2 = AssetProperty(columnName = nonMandatoryFieldsMapping(key), value = value) :: result._2)
          else
            result
        } else {
          if (intValueFieldsMapping.contains(key)) {
            val (malformedParameters, properties) = verifyIntType(key, value.toString)
            result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
          } else if (laneNumberFieldMapping.contains(key)) {
            val (malformedParameters, properties) = verifyLaneNumber(key, value.toString)
            result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
          } else if (laneTypeFieldMapping.contains(key)) {
            val (malformedParameters, properties) = verifyLaneType(key, value.toString)
            result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
          }else if(mandatoryFieldsMapping.contains(key))
            result.copy(_2 = AssetProperty(columnName = mandatoryFieldsMapping(key), value = value) :: result._2)
          else if (nonMandatoryFieldsMapping.contains(key))
            result.copy(_2 = AssetProperty(columnName = nonMandatoryFieldsMapping(key), value = value) :: result._2)
          else
            result
        }
    }
  }

//  def verifyData(parsedRow: ParsedProperties, user: User): ParsedCsv = {
//    val optLon = getPropertyValueOption(parsedRow, "lon").asInstanceOf[Option[BigDecimal]]
//    val optLat = getPropertyValueOption(parsedRow, "lat").asInstanceOf[Option[BigDecimal]]
//
//    (optLon, optLat) match {
//      case (Some(lon), Some(lat)) =>
//        val roadLinks = roadLinkService.getClosestRoadlinkForCarTrafficFromVVH(user, Point(lon.toLong, lat.toLong))
//        roadLinks.isEmpty match {
//          case true => (List(s"No Rights for Municipality or nonexistent road links near asset position"), Seq())
//          case false => (List(), Seq(parsedRow))
//        }
//      case _ =>
//        (Nil, Nil)
//    }
//  }

  def createAsset(laneAssetProperties: Seq[ParsedProperties], user: User, result: ImportResultData): ImportResultData = {
    val lanesByRoadNumber = laneAssetProperties.groupBy(lane => lane.find(_.columnName == "road number").get.value)

    result
//    val incomingServicePoint = pointAssetAttributes.map { servicePointAttribute =>
//      val csvProperties = servicePointAttribute.properties
//      val nearbyLinks = servicePointAttribute.roadLink
//
//      val position = getCoordinatesFromProperties(csvProperties)
//
//      val roadLink = roadLinkService.enrichRoadLinksFromVVH(nearbyLinks)
//      val nearestRoadLink = roadLink.filter(_.administrativeClass != State).minBy(r => GeometryUtils.minimumDistance(position, r.geometry))
//
//      val serviceType = getPropertyValue(csvProperties, "type").asInstanceOf[String]
//      val typeExtension = getPropertyValueOption(csvProperties, "type extension").map(_.toString)
//      val name = getPropertyValueOption(csvProperties, "name").map(_.toString)
//      val additionalInfo = getPropertyValueOption(csvProperties, "additional info").map(_.toString)
//      val isAuthorityData = getPropertyValue(csvProperties, "is authority data").asInstanceOf[String]
//      val parkingPlaceCount = getPropertyValueOption(csvProperties, "parking place count").map(_.toString.toInt)
//
//      val validatedServiceType = serviceTypeConverter(serviceType)
//      val validatedTypeExtension = ServicePointsClass.getTypeExtensionValue(typeExtension.get, validatedServiceType)
//      val validatedAuthorityData = authorityDataConverter(isAuthorityData)
//
//      val incomingService = IncomingService(validatedServiceType, name, additionalInfo, validatedTypeExtension, parkingPlaceCount, validatedAuthorityData)
//
//      val servicePointInfo =
//        if(validatedServiceType == ServicePointsClass.Unknown.value)
//          Seq(NotImportedData(reason = s"Service Point type $serviceType does not exist.", csvRow = rowToString(csvProperties.flatMap{x => Map(x.columnName -> x.value)}.toMap)))
//        else
//          Seq()
//
//      CsvServicePoint(position, incomingService, nearestRoadLink, servicePointInfo)
//    }
//
//    val (validServicePoints, nonValidServicePoints) = incomingServicePoint.partition(servicePoint => servicePoint.importInformation.isEmpty)
//    val notImportedInfo = nonValidServicePoints.flatMap(_.importInformation)
//    val groupedServicePoints = validServicePoints.groupBy(_.position)
//
//    val incomingServicePoints = groupedServicePoints.map { servicePoint =>
//      (IncomingServicePoint(servicePoint._1.x, servicePoint._1.y, servicePoint._2.map(_.incomingService).toSet, Set()), servicePoint._2.map(_.roadLink).head.municipalityCode)
//    }
//
//    incomingServicePoints.foreach { incomingAsset =>
//      try {
//        servicePointService.create(incomingAsset._1, incomingAsset._2, user.username, false)
//      } catch {
//        case e: ServicePointException => result.copy(notImportedData = List(NotImportedData(reason = e.getMessage, csvRow = "")) ++ result.notImportedData)
//      }
//    }
//
//    result.copy(notImportedData = notImportedInfo.toList ++ result.notImportedData)
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

          if (missingParameters.nonEmpty || malformedParameters.nonEmpty/* || notImportedParameters.nonEmpty*/) {
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
              }/*,
              notImportedData = notImportedParameters match {
                case Nil => result.notImportedData
                case parameters =>
                  NotImportedData(reason = parameters.head, csvRow = rowToString(csvRow)) :: result.notImportedData
              }*/)
          } else {
            result.copy(createdData = properties :: result.createdData)
          }
      }
//      val (notImportedParameters, parsedRow) = verifyData(properties, user) //need to verifyDataBetween rows or just the row?
      createAsset(result.createdData, user, result)
    }
  }
}
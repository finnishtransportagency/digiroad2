package fi.liikennevirasto.digiroad2.csvDataImporter

import java.io.{InputStream, InputStreamReader}

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.{IncomingWidthOfRoadAxisMarking, TrafficSignService, WidthOfRoadAxisMarkingService}
import fi.liikennevirasto.digiroad2.user.User
import org.apache.commons.lang3.StringUtils.isBlank

class WidthOfRoadAxisMarkingCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends PointAssetCsvImporter {
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def eventBus: DigiroadEventBus = eventBusImpl

  private val regulationNumberPublicId = "regulation_number"
  private val laneNumberPublicId = "lane_number"
  private val laneTypePublicId = "lane_type"
  private val conditionPublicId = "condition"
  private val materialPublicId = "material"
  private val lengthPublicId = "length"
  private val widthPublicId = "width"
  private val raisedPublicId = "raised"
  private val milledPublicId = "milled"
  private val additionalInfoPublicId = "additional_info"
  private val statePublicId = "state"
  private val startDatePublicId = "marking_start_date"
  private val endDatePublicId = "marking_end_date"

  lazy val widthOfRoadAxisMarkingService: WidthOfRoadAxisMarkingService = new WidthOfRoadAxisMarkingService(roadLinkService)

  override val dateFieldsMapping = Map(
    "alkupaivamaara" -> "startDate",
    "loppupaivamaara" -> "endDate"
  )

  private val singleChoiceAcceptableValues = Map(
    "kunto" -> (Condition.values.map(_.value), Condition.getDefault.value),
    "materiaali" -> (MaterialOfMarking.values.map(_.value), MaterialOfMarking.getDefault.value),
    "profiilimerkintä" -> (YesOrNoField.values.map(_.value), YesOrNoField.getDefault.value),
    "jyrsitty" -> (Milled.values.map(_.value), Milled.getDefault.value),
    "tila" -> (PointAssetState.values.map(_.value), PointAssetState.getDefault.value)
  )

  private val singleChoiceMapping = Map(
    "kunto" -> "condition",
    "materiaali" -> "material",
    "profiilimerkintä" -> "raised",
    "jyrsitty" -> "milled",
    "tila" -> "state"
  )

  override val nonMandatoryFieldsMapping = Map(
    "lisatieto" -> "additionalInfo",
    "kunnan id" -> "municipalityId",
    "tien nimi" -> "roadName",
    "kaistanro" -> "laneNumber",
    "kaistan tyyppi" -> "laneType",
    "pituus" -> "length",
    "leveys" -> "width"
  )

  override val intValueFieldsMapping = Map(
    "suuntima" -> "bearing"
  )

  private val codeValueFieldMapping = Map(
    "asetus num" -> "regulationNumber"
  )

  private val codeValueFieldMappingAcceptableValues = Map(
    "liikennevalo tyyppi" -> WidthOfRoadAxisRegulationNumbers
  )


  val mappings : Map[String, String] = longValueFieldsMapping ++ nonMandatoryFieldsMapping ++ codeValueFieldMapping ++ singleChoiceMapping ++
                                       dateFieldsMapping ++ intValueFieldsMapping

  override def mandatoryFields: Set[String] = longValueFieldsMapping.keySet ++ codeValueFieldMapping.keySet

  override def findMissingParameters(csvRoadWithHeaders: Map[String, String]): List[String] = {
    val state = csvRoadWithHeaders.get("tila")

    state match {
      case Some(value) if value.nonEmpty && (PointAssetState.apply(value.toInt) == PointAssetState.TemporarilyInUse || PointAssetState.apply(value.toInt) == PointAssetState.TemporarilyOutOfService) =>
        val requiredFields = mandatoryFields ++ Set("loppupaivamaara")
        requiredFields.diff(csvRoadWithHeaders.keys.toSet).toList

      case _ => mandatoryFields.diff(csvRoadWithHeaders.keys.toSet).toList
    }
  }

  override def assetRowToProperties(csvRowWithHeaders: Map[String, String]): ParsedRow = {
    csvRowWithHeaders.foldLeft(Nil: MalformedParameters, Nil: ParsedProperties) { (result, parameter) =>
      val (key, value) = parameter

      if (isBlank(value.toString)) {
        if (mandatoryFields.contains(key)) {
          result.copy(_1 = List(key) ::: result._1, _2 = result._2)
        } else if (singleChoiceMapping.contains(key)) {
          val defaultValue = singleChoiceAcceptableValues(key) match { case (_, default) => default }
          result.copy(_2 = AssetProperty(columnName = singleChoiceMapping(key), value = defaultValue) :: result._2)
        } else if (dateFieldsMapping.contains(key)) {
          result.copy(_2 = AssetProperty(columnName = dateFieldsMapping(key), value = value) :: result._2)
        } else if (intValueFieldsMapping.contains(key)) {
          result.copy(_2 = AssetProperty(columnName = intValueFieldsMapping(key), value = value) :: result._2)
        } else if (nonMandatoryFieldsMapping.contains(key)) {
          result.copy(_2 = AssetProperty(columnName = nonMandatoryFieldsMapping(key), value = value) :: result._2)
        } else
          result
      } else {
        if (longValueFieldsMapping.contains(key)) {
          val (malformedParameters, properties) = verifyDoubleType(key, value.toString)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (codeValueFieldMapping.contains(key)) {
          val (malformedParameters, properties) = verifyRegulationNumberValue(key, value.toString)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (dateFieldsMapping.contains(key)){
          val (malformedParameters, properties) = verifyDateType(key, value.toString)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (singleChoiceMapping.contains(key)) {
          val acceptableValues = singleChoiceAcceptableValues(key) match { case (values, _) => values}
          val (malformedParameters, properties) = singleChoiceToProperty(key, value, acceptableValues, singleChoiceMapping)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (intValueFieldsMapping.contains(key)) {
          val (malformedParameters, properties) = verifyIntType(key, value.toString)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (nonMandatoryFieldsMapping.contains(key)) {
          result.copy(_2 = AssetProperty(columnName = nonMandatoryFieldsMapping(key), value = value) :: result._2)
        } else
          result
      }
    }
  }

  override def verifyData(parsedRow: ParsedProperties, user: User): ParsedCsv = {
    def hasRoadName: Boolean = {
      val optRoadName = getPropertyValueOption(parsedRow, "roadName").asInstanceOf[Option[String]]
      optRoadName match {
        case Some(roadName) if !roadName.trim.isEmpty => true
        case _ => false
      }
    }

    def datesValidator(startDate: String, endDate: String) = {
      try {
        val startDateFormat = DateParser.DatePropertyFormat.parseDateTime(startDate)
        val endDateFormat = DateParser.DatePropertyFormat.parseDateTime(endDate)

        val isDatesOk = endDateFormat.isAfter(startDateFormat) || endDateFormat.isEqual(startDateFormat)
        val errorMsg = if (isDatesOk) Nil else List("The end date value is equal/previous to the start date value.")

        (isDatesOk, errorMsg)
      } catch {
        case _: Throwable => (false, List("Invalid dates formats"))
      }
    }

    val optRegulationNumber = getPropertyValueOption(parsedRow, "regulationNumber").asInstanceOf[Option[String]]
    val isRegulationNumberOk = optRegulationNumber match {
      case Some(regulationNumber) =>
        val regulationNumberType = WidthOfRoadAxisRegulationNumbers.apply(regulationNumber.toInt)
        regulationNumberType != WidthOfRoadAxisRegulationNumbers.Unknown

      case _ => false
    }

    //Validate if we get a valid regulation number
    if (!isRegulationNumberOk)
      return (List("Not a valid regulation number!"), Seq() )

    /* start direction validations */
    val optMarkingDirection = getPropertyValueOption(parsedRow, "bearing").asInstanceOf[Option[String]]
    val (directionValidator, directionValidatorErrorMsg) = optMarkingDirection match {
      case Some(direction) if direction.trim.nonEmpty => (true, Nil)
      case _ if hasRoadName => (true, Nil)
      case _ => (false, List("Invalid Marking direction"))
    }
    /* end direction validations */

    /* start date validations */
    val temporaryDevices = Seq(PointAssetState.TemporarilyInUse.value, PointAssetState.TemporarilyOutOfService.value)
    val optLifeCycle = getPropertyValueOption(parsedRow, "state").asInstanceOf[Option[Int]].getOrElse(PointAssetState.Unknown.value)
    val optStartDate = getPropertyValueOption(parsedRow, "startDate").asInstanceOf[Option[String]].map(_.trim).filterNot(_.isEmpty)
    val optEndDate = getPropertyValueOption(parsedRow, "endDate").asInstanceOf[Option[String]].map(_.trim).filterNot(_.isEmpty)

    val (isValidDate, datesErrorMsg) =
      (optStartDate, optEndDate) match {
        case (Some(startDate), Some(endDate)) =>
          datesValidator(startDate, endDate)
        case _ if !isBlank(optLifeCycle.toString) && temporaryDevices.contains(optLifeCycle) && isBlank(optEndDate.toString) =>
          (false, List("Invalid End Date, mandatory if state field have temporary value"))
        case _ =>
          (true, Nil)
      }
    /* end date validations */

    /* start lane type validations */
    val optLaneType = getPropertyValueOption(parsedRow, "laneType").asInstanceOf[Option[Int]]
    val optLaneNumber = getPropertyValueOption(parsedRow, "laneNumber").asInstanceOf[Option[String]]

    val (lanesValidator, lanesValidatorErrorMsg) = csvLaneValidator(optLaneType, optLaneNumber)
    /* end lane type validations */

    val allErrors = datesErrorMsg ++ directionValidatorErrorMsg ++ lanesValidatorErrorMsg

    if(isValidDate && directionValidator && lanesValidator) {
      val optLon = getPropertyValueOption(parsedRow, "lon").asInstanceOf[Option[BigDecimal]]
      val optLat = getPropertyValueOption(parsedRow, "lat").asInstanceOf[Option[BigDecimal]]

      (optLon, optLat) match {
        case (Some(lon), Some(lat)) =>
          val roadLinks = roadLinkService.getClosestRoadlinkForCarTrafficFromVVH(user, Point(lon.toLong, lat.toLong), forCarTraffic = false)
          if (roadLinks.isEmpty) {
            (List(s"No Rights for Municipality or nonexistent road links near asset position"), Seq())
          } else {
            (List(), Seq(CsvAssetRowAndRoadLink(parsedRow, roadLinks)))
          }
        case _ =>
          (Nil, Nil)
      }
    }
    else (allErrors, Seq())
  }

  override def createAsset(widthOfRoadAxisMarkingAttributes: Seq[CsvAssetRowAndRoadLink], user: User, result: ImportResultData ): ImportResultData = {
    val markings = widthOfRoadAxisMarkingAttributes.map { markingAttribute =>
      val props = markingAttribute.properties
      val nearbyLinks = markingAttribute.roadLink
      val optBearing = tryToInt(getPropertyValue(props, "bearing").toString)
      val point = getCoordinatesFromProperties(props)
      val (assetBearing, assetValidityDirection) = widthOfRoadAxisMarkingService.recalculateBearing(optBearing)

      var possibleRoadLinks = roadLinkService.filterRoadLinkByBearing(assetBearing, assetValidityDirection, point, nearbyLinks)

      if (possibleRoadLinks.size > 1) {
        getPropertyValue(props, "roadName") match {
          case name: String =>
            val possibleRoadLinksByName = nearbyLinks.filter(_.roadNameIdentifier == Option(name))
            if (possibleRoadLinksByName.size == 1)
              possibleRoadLinks = possibleRoadLinksByName
          case _ =>
        }
      }

      val roadLinks = possibleRoadLinks.filter(_.administrativeClass != State)
      val roadLink = if (roadLinks.nonEmpty) {
        roadLinks.minBy(r => GeometryUtils.minimumDistance(point, r.geometry))
      } else
        nearbyLinks.minBy(r => GeometryUtils.minimumDistance(point, r.geometry))

      val validityDirection = if(assetBearing.isEmpty) {
        widthOfRoadAxisMarkingService.getValidityDirection(point, roadLink, assetBearing)
      } else assetValidityDirection.get

      val mValue = GeometryUtils.calculateLinearReferenceFromPoint(point, roadLink.geometry)

      (props, CsvPointAsset(point.x, point.y, roadLink.linkId, generateBaseProperties(props), validityDirection, assetBearing, mValue, roadLink, (roadLinks.isEmpty || roadLinks.size > 1) && assetBearing.isEmpty))
    }

    var notImportedDataExceptions: List[NotImportedData] = List()
    markings.map { case (csvRow, marking) =>
      val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(marking.lon, marking.lat), marking.roadLink.geometry)
//      val bearing = if (marking.bearing.isEmpty && !marking.isFloating)
//        Some(GeometryUtils.calculateBearing(marking.roadLink.geometry, Some(mValue)))
//      else
//        marking.bearing
      try {
        widthOfRoadAxisMarkingService.createFromCoordinates(IncomingWidthOfRoadAxisMarking(marking.lon, marking.lat, marking.roadLink.linkId, marking.propertyData), marking.roadLink, user.username, marking.isFloating)
      } catch {
        case ex: NoSuchElementException => notImportedDataExceptions = notImportedDataExceptions :+ NotImportedData(reason = ex.getMessage, csvRow = rowToString(csvRow.flatMap { x => Map(x.columnName -> x.value) }.toMap))
      }
    }

    result.copy(notImportedData = result.notImportedData ++ notImportedDataExceptions)
  }

  def verifyRegulationNumberValue(parameterName: String, parameterValue: String): ParsedRow = {
    if (parameterValue.nonEmpty && codeValueFieldMappingAcceptableValues(parameterName).values.map(_.value).contains(parameterValue.toInt)) {
      (Nil, List(AssetProperty(columnName = codeValueFieldMapping(parameterName), value = parameterValue)))
    } else {
      (List(s"Invalid value for $parameterName"), Nil)
    }
  }

  private def generateBaseProperties(markingAttributes: ParsedProperties): Set[SimplePointAssetProperty] = {
    val listPublicIds = Seq(regulationNumberPublicId, laneNumberPublicId, laneTypePublicId, conditionPublicId, materialPublicId,
      lengthPublicId, widthPublicId, raisedPublicId, milledPublicId, additionalInfoPublicId, statePublicId, startDatePublicId, endDatePublicId
    )

    val listFieldNames = Seq("regulationNumber", "laneNumber", "laneType", "condition", "material", "length", "width",
      "raised", "milled", "additionalInfo", "state", "startDate", "endDate")

    extractPropertyValues(listPublicIds, listFieldNames, markingAttributes, withGroupedId = false).toSet.flatten
  }
}
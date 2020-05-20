package fi.liikennevirasto.digiroad2.csvDataImporter

import java.io.{InputStream, InputStreamReader}

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import fi.liikennevirasto.digiroad2
import fi.liikennevirasto.digiroad2.TrafficSignTypeGroup.AdditionalPanels
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.lane.{LaneNumber, LaneType}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.{IncomingTrafficSign, TrafficSignService}
import fi.liikennevirasto.digiroad2.user.User
import org.apache.commons.lang3.StringUtils.isBlank

class TrafficSignCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends PointAssetCsvImporter {
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def eventBus: DigiroadEventBus = eventBusImpl

  private val typePublicId = "trafficSigns_type"
  private val valuePublicId = "trafficSigns_value"
  private val infoPublicId = "trafficSigns_info"
  private val startDatePublicId = "trafficSign_start_date"
  private val endDatePublicId = "trafficSign_end_date"
  private val municipalityPublicId = "municipality_id"
  private val mainSignTextPublicId = "main_sign_text"
  private val structurePublicId = "structure"
  private val conditionPublicId = "condition"
  private val sizePublicId = "size"
  private val heightPublicId = "height"
  private val coatingTypePublicId = "coating_type"
  private val signMaterialPublicId = "sign_material"
  private val locationSpecifierPublicId = "location_specifier"
  private val terrainCoordinatesXPublicId = "terrain_coordinates_x"
  private val terrainCoordinatesYPublicId = "terrain_coordinates_y"
  private val laneTypePublicId = "lane_type"
  private val lanePublicId = "lane"
  private val lifeCyclePublicId = "life_cycle"
  private val typeOfDamagePublicId = "type_of_damage"
  private val urgencyOfRepairPublicId = "urgency_of_repair"
  private val lifespanLeftPublicId = "lifespan_left"
  private val oldTrafficCodePublicId = "old_traffic_code"
  private val oppositeSideSignPublicId = "opposite_side_sign"
  private val additionalPanelPublicId = "additional_panel"

  lazy val trafficSignService: TrafficSignService = new TrafficSignService(roadLinkService, eventBusImpl)

  val minAdditionalPanels = 1
  val maxAdditionalPanels = 3

  private val longValueFieldMappings = coordinateMappings

  //mandatory for the sign 142 (RoadWorks)
  override val dateFieldsMapping = Map(
    "alkupaivamaara" -> "startDate",
    "loppupaivamaara" -> "endDate"
  )

  private val singleChoiceAcceptableValues = Map(
    "rakenne" -> (PointAssetStructure.values.map(_.value), PointAssetStructure.getDefault),
    "kunto" -> (Condition.values.map(_.value), Condition.getDefault),
    "koko" -> (Size.values.map(_.value), Size.getDefault),
    "kalvon tyyppi" -> (CoatingType.values.map(_.value), CoatingType.getDefault),
    "merkin materiaali" -> (SignMaterial.values.map(_.value), SignMaterial.getDefault),
    "sijaintitarkenne" -> (LocationSpecifier.values.map(_.value), LocationSpecifier.getDefault),
    "kaistan tyyppi" -> (LaneType.values.map(_.value), LaneType.getDefault),
    "tila" -> (PointAssetState.values.map(_.value), PointAssetState.getDefault),
    "vauriotyyppi" -> (TypeOfDamage.values.map(_.value), TypeOfDamage.getDefault),
    "korjauksen kiireellisyys" -> (UrgencyOfRepair.values.map(_.value), UrgencyOfRepair.getDefault)
  )

  private val singleChoiceMapping = Map(
    "rakenne" -> "structure",
    "kunto" -> "condition",
    "koko" -> "size",
    "kalvon tyyppi" -> "coatingType",
    "merkin materiaali" -> "signMaterial",
    "sijaintitarkenne" -> "locationSpecifier",
    "kaistan tyyppi" -> "laneType",
    "tila" -> "lifeCycle",
    "vauriotyyppi" -> "typeOfDamage",
    "korjauksen kiireellisyys" -> "urgencyOfRepair"
  )

  private val multiChoiceAcceptableValues = Seq(0, 1)

  private val multiChoiceMapping = Map(
    "liikenteenvastainen" -> "oppositeSideSign",
    "lisaa vanhan lain mukainen koodi" -> "oldTrafficCode"
  )

  private val nonMandatoryMappings = Map(
    "lisatieto" -> "additionalInfo",
    "kunnan id" -> "municipalityId",
    "paamerkin teksti" -> "mainSignText",
    "tien nimi" -> "roadName",
    "kaksipuolinen merkki" -> "twoSided"
  )

  override val intValueFieldsMapping = Map(
    "arvo" -> "value",
    "korkeus" -> "height",
    "kaista" -> "lane",
    "arvioitu kayttoika" -> "lifespanLeft",
    "liikennevirran suunta" -> "trafficDirection",
    "suuntima" -> "bearing"
  )

  private val additionalPanelMapping = Map(
    "lisakilpi 1" -> "additionalPanelType1",
    "lisakilpi arvo 1" -> "additionalPanelValue1",
    "lisakilpi lisatieto 1" -> "additionalPanelInfo1",
    "lisakilpi teksti 1"-> "additionalPanelText1",
    "lisakilpi koko 1" -> "additionalPanelSize1",
    "lisakilpi kalvon tyyppi 1" -> "additionalPanelCoatingType1",
    "lisakilpi lisakilven vari 1" -> "additionalPanelColor1",
    "lisakilpi 2" -> "additionalPanelType2",
    "lisakilpi arvo 2" -> "additionalPanelValue2",
    "lisakilpi lisatieto 2" -> "additionalPanelInfo2",
    "lisakilpi teksti 2"-> "additionalPanelText2",
    "lisakilpi koko 2" -> "additionalPanelSize2",
    "lisakilpi kalvon tyyppi 2" -> "additionalPanelCoatingType2",
    "lisakilpi lisakilven vari 2" -> "additionalPanelColor2",
    "lisakilpi 3" -> "additionalPanelType3",
    "lisakilpi arvo 3" -> "additionalPanelValue3",
    "lisakilpi lisatieto 3" -> "additionalPanelInfo3",
    "lisakilpi teksti 3"-> "additionalPanelText3",
    "lisakilpi koko 3" -> "additionalPanelSize3",
    "lisakilpi kalvon tyyppi 3" -> "additionalPanelCoatingType3",
    "lisakilpi lisakilven vari 3" -> "additionalPanelColor3"
  )

  private val codeValueFieldMappings = Map(
    "liikennemerkin tyyppi" -> "trafficSignType"
  )
  val mappings : Map[String, String] = longValueFieldMappings ++ nonMandatoryMappings ++ codeValueFieldMappings ++ singleChoiceMapping ++ multiChoiceMapping ++
                                       additionalPanelMapping ++ dateFieldsMapping ++ intValueFieldsMapping

  override def mandatoryFields: Set[String] = longValueFieldMappings.keySet ++ codeValueFieldMappings.keySet

  val mandatoryParameters: Set[String] = mappings.keySet ++ mandatoryFields

  private def verifyValueCode(parameterName: String, parameterValue: String): ParsedRow = {
    if(TrafficSignType.applyNewLawCode(parameterValue).source.contains("CSVimport")){
      (Nil, List(AssetProperty(columnName = codeValueFieldMappings(parameterName), value = parameterValue)))
    }else{
      (List(parameterName), Nil)
    }
  }

  private def multiChoiceToProperty(parameterName: String, assetMultiChoice: String): ParsedRow = {
    tryToInt(assetMultiChoice) match {
      case Some(value) if multiChoiceAcceptableValues.contains(value) =>
        (Nil, List(AssetProperty(columnName = multiChoiceMapping(parameterName), value = value)))
      case _ =>
        (List(s"Invalid value for $parameterName"), Nil)
    }
  }

  override def findMissingParameters(csvRoadWithHeaders: Map[String, String]): List[String] = {
    val code = csvRoadWithHeaders.get("liikennemerkin tyyppi")
    code match {
      case Some(value) if value.nonEmpty && TrafficSignType.applyNewLawCode(value) == RoadWorks =>
        mandatoryFieldsMapping.keySet.diff(csvRoadWithHeaders.keys.toSet).toList ++ dateFieldsMapping.keySet.diff(csvRoadWithHeaders.keys.toSet).toList
      case _ => mandatoryFieldsMapping.keySet.diff(csvRoadWithHeaders.keys.toSet).toList
    }
  }

  override def assetRowToProperties(csvRowWithHeaders: Map[String, String]): ParsedRow = {
    csvRowWithHeaders.foldLeft(Nil: MalformedParameters, Nil: ParsedProperties) { (result, parameter) =>
      val (key, value) = parameter

      if (isBlank(value.toString)) {
        if (mandatoryFields.contains(key)) {
          result.copy(_1 = List(key) ::: result._1, _2 = result._2)
        } else if (multiChoiceMapping.contains(key)) {
          result.copy(_2 = AssetProperty(columnName = multiChoiceMapping(key), value = trafficSignService.getDefaultMultiChoiceValue) :: result._2)
        } else if (singleChoiceMapping.contains(key)) {
          val defaultValue = singleChoiceAcceptableValues(key) match { case (_, default) => default }
          result.copy(_2 = AssetProperty(columnName = singleChoiceMapping(key), value = defaultValue) :: result._2)
        } else if (additionalPanelMapping.contains(key)) {
          result.copy(_2 = AssetProperty(columnName = additionalPanelMapping(key), value = value) :: result._2)
        } else if (dateFieldsMapping.contains(key)) {
          result.copy(_2 = AssetProperty(columnName = dateFieldsMapping(key), value = value) :: result._2)
        } else if (intValueFieldsMapping.contains(key)) {
          result.copy(_2 = AssetProperty(columnName = intValueFieldsMapping(key), value = value) :: result._2)
        } else if (nonMandatoryMappings.contains(key)) {
          result.copy(_2 = AssetProperty(columnName = nonMandatoryMappings(key), value = value) :: result._2)
        } else
          result
      } else {
        if (longValueFieldMappings.contains(key)) {
          val (malformedParameters, properties) = verifyDoubleType(key, value.toString)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (codeValueFieldMappings.contains(key)) {
          val (malformedParameters, properties) = verifyValueCode(key, value.toString)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (dateFieldsMapping.contains(key)){
          val (malformedParameters, properties) = verifyDateType(key, value.toString)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (singleChoiceMapping.contains(key)) {
          val acceptableValues = singleChoiceAcceptableValues(key) match { case (values, _) => values}
          val (malformedParameters, properties) = singleChoiceToProperty(key, value, acceptableValues, singleChoiceMapping)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (multiChoiceMapping.contains(key)) {
          val (malformedParameters, properties) = multiChoiceToProperty(key, value)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (intValueFieldsMapping.contains(key)) {
          val (malformedParameters, properties) = verifyIntType(key, value.toString)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (additionalPanelMapping.contains(key)) {
          result.copy(_2 = AssetProperty(columnName = additionalPanelMapping(key), value = value) :: result._2)
        } else if (nonMandatoryMappings.contains(key)) {
          result.copy(_2 = AssetProperty(columnName = nonMandatoryMappings(key), value = value) :: result._2)
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


    val optTrafficSignType = getPropertyValueOption(parsedRow, "trafficSignType").asInstanceOf[Option[String]]

    /* start date validations */
    val temporaryDevices = Seq(4,5)
    val optLifeCycle = getPropertyValueOption(parsedRow, "lifeCycle").asInstanceOf[Option[Int]].getOrElse(PointAssetState.Unknown.value)
    val optStartDate = getPropertyValueOption(parsedRow, "startDate").asInstanceOf[Option[String]]
    val optEndDate = getPropertyValueOption(parsedRow, "endDate").asInstanceOf[Option[String]]

    val (isValidDate, datesErrorMsg) = optTrafficSignType match {
      case Some(signType) if (!isBlank(optLifeCycle.toString) && temporaryDevices.contains(optLifeCycle)) || TrafficSignType.applyNewLawCode(signType) == RoadWorks =>
        (optStartDate, optEndDate) match {
          case (Some(startDate), Some(endDate)) =>
            try {
              val startDateFormat = DateParser.DatePropertyFormat.parseDateTime(startDate)
              val endDateFormat = DateParser.DatePropertyFormat.parseDateTime(endDate)

              val isDatesOk = endDateFormat.isAfter(startDateFormat) || endDateFormat.isEqual(startDateFormat)

              (isDatesOk, Nil)
            } catch {
              case _: Throwable => (false, List("Invalid dates formats"))
            }
          case (_, _) => (false, List("Invalid dates"))
        }
      case _ => (true, Nil)
    }
    /* end date validations */

    /* start direction validations */
    val optTrafficDirection = getPropertyValueOption(parsedRow, "bearing").asInstanceOf[Option[String]]
    val (directionValidator, directionValidatorErrorMsg) = optTrafficDirection match {
            case Some(direction) if direction.trim.nonEmpty => (true, Nil)
            case _ if hasRoadName => (true, Nil)
            case _ => (false, List("Invalid traffic sign direction"))
          }
    /* end direction validations */

    /* start additional panels type validations */
    val additionalPanelsNumbers = (minAdditionalPanels to maxAdditionalPanels)
    val (additionalPanelsValidator: Boolean, additionalPanelsErrorMsg: List[String]) = optTrafficSignType match {
      case Some(sType) if sType.trim.nonEmpty =>
          val result = additionalPanelsNumbers.map { index =>
              getPropertyValueOption(parsedRow, "additionalPanelType" + index).asInstanceOf[Option[String]] match {
                case Some(panelType) if panelType.trim.nonEmpty && TrafficSignType.applyNewLawCode(panelType).group.value != AdditionalPanels.value =>
                  (false, List("Invalid additional panel type on lisakilpi " + index))
                case _ => (true, Nil)
              }
          }

        if (result.count(_._1 == false) < 1)
          (true, Nil)
        else
          (false, result.flatMap(_._2))


      case _ => (true, Nil)
    }
    /* end additional panels type validations */

    /* start lane type validations */
    val optLaneType = getPropertyValueOption(parsedRow, "laneType").asInstanceOf[Option[Int]]
    val optLaneNumber = getPropertyValueOption(parsedRow, "lane").asInstanceOf[Option[String]]

    val (lanesValidator, lanesValidatorErrorMsg) = csvLaneValidator(optLaneType, optLaneNumber)
    /* end lane type validations */

    val allErrors = datesErrorMsg ++ directionValidatorErrorMsg ++ additionalPanelsErrorMsg ++ lanesValidatorErrorMsg

    if(isValidDate && directionValidator && additionalPanelsValidator && lanesValidator) super.verifyData(parsedRow, user)
    else (allErrors, Seq())

  }


  private def generateBaseProperties(trafficSignAttributes: ParsedProperties) : Set[SimplePointAssetProperty] = {
    val valueProperty = tryToInt(getPropertyValue(trafficSignAttributes, "value").toString).map { value =>
      SimplePointAssetProperty(valuePublicId, Seq(PropertyValue(value.toString)))}

    val typeProperty = SimplePointAssetProperty(typePublicId, Seq(PropertyValue(TrafficSignType.applyNewLawCode(getPropertyValue(trafficSignAttributes, "trafficSignType").toString).OTHvalue.toString)))

    val listPublicIds = Seq(infoPublicId, startDatePublicId, endDatePublicId, municipalityPublicId, mainSignTextPublicId, structurePublicId, conditionPublicId, sizePublicId,
                            heightPublicId, coatingTypePublicId, signMaterialPublicId, locationSpecifierPublicId, terrainCoordinatesXPublicId, terrainCoordinatesYPublicId,
                            laneTypePublicId, lanePublicId, lifeCyclePublicId, typeOfDamagePublicId, urgencyOfRepairPublicId, lifespanLeftPublicId, oldTrafficCodePublicId,
                            oppositeSideSignPublicId
                            )

    val listFieldNames = Seq("additionalInfo", "startDate", "endDate", "municipalityId", "mainSignText", "structure", "condition", "size", "height", "coatingType", "signMaterial",
                              "locationSpecifier", "lon", "lat", "laneType", "lane", "lifeCycle", "typeOfDamage", "urgencyOfRepair", "lifespanLeft",
                              "oldTrafficCode", "oppositeSideSign"
                            )

    val propertiesValues = extractPropertyValues(listPublicIds, listFieldNames, trafficSignAttributes, withGroupedId = false)

    (Set(Some(typeProperty), valueProperty) ++ propertiesValues ++ generateBasePanelProperties(trafficSignAttributes)).flatten
  }

  private def generateBasePanelProperties(trafficSignAttributes: ParsedProperties): Set[Option[SimplePointAssetProperty]] = {

    def getSingleChoiceValue(target: String): Int = {
      getPropertyValueOption(trafficSignAttributes, target) match {
        case Some(targetValue) if !isBlank(targetValue.toString) => targetValue.toString.toInt
        case _ => trafficSignService.getDefaultSingleChoiceValue
      }
    }
    var res: Seq[AdditionalPanel] = Seq()

    (minAdditionalPanels to maxAdditionalPanels).foreach(index => {
      getPropertyValueOption(trafficSignAttributes, "additionalPanelType" + index) match {
        case Some(pType) if pType.toString.trim.nonEmpty => {
          res = res ++ Seq(
            AdditionalPanel(
              TrafficSignType.applyNewLawCode(pType.toString).OTHvalue,
              getPropertyValueOption(trafficSignAttributes, "additionalPanelInfo" + index).get.toString,
              getPropertyValueOption(trafficSignAttributes, "additionalPanelValue"+ index).get.toString,
              index,
              getPropertyValueOption(trafficSignAttributes, "additionalPanelText" + index).get.toString,
              getSingleChoiceValue("additionalPanelSize" + index),
              getSingleChoiceValue("additionalPanelCoatingType" + index),
              getSingleChoiceValue("additionalPanelColor" + index)
            ))}
        case _ =>
      }})
    Set(Some(SimplePointAssetProperty(additionalPanelPublicId, res)))
  }

  override def createAsset(trafficSignAttributes: Seq[CsvAssetRowAndRoadLink], user: User, result: ImportResultData ): ImportResultData = {

    val signs = trafficSignAttributes.map { trafficSignAttribute =>
      val props = trafficSignAttribute.properties
      val nearbyLinks = trafficSignAttribute.roadLink
      val optBearing = tryToInt(getPropertyValue(props, "bearing").toString)
      val twoSided = getPropertyValue(props, "twoSided") match {
        case "1" => true
        case _ => false
      }
      val point = getCoordinatesFromProperties(props)
      val (assetBearing, assetValidityDirection) = trafficSignService.recalculateBearing(optBearing)

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
        trafficSignService.getValidityDirection(point, roadLink, assetBearing, twoSided)
      } else assetValidityDirection.get

      val mValue = GeometryUtils.calculateLinearReferenceFromPoint(point, roadLink.geometry)

      (props, CsvPointAsset(point.x, point.y, roadLink.linkId, generateBaseProperties(props), validityDirection, assetBearing, mValue, roadLink, (roadLinks.isEmpty || roadLinks.size > 1) && assetBearing.isEmpty))
    }

    var notImportedDataExceptions: List[NotImportedData] = List()
    signs.foreach { case (csvRow, sign) =>
      val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(sign.lon, sign.lat), sign.roadLink.geometry)
      val bearing = if(sign.bearing.isEmpty && !sign.isFloating)
        Some(GeometryUtils.calculateBearing(sign.roadLink.geometry, Some(mValue)))
      else
        sign.bearing
      try {
        trafficSignService.createFromCoordinates(IncomingTrafficSign(sign.lon, sign.lat, sign.roadLink.linkId, sign.propertyData, sign.validityDirection, bearing), sign.roadLink, user.username, sign.isFloating)
      } catch {
        case ex: NoSuchElementException => notImportedDataExceptions = notImportedDataExceptions :+ NotImportedData(reason = ex.getMessage, csvRow = rowToString(csvRow.flatMap{x => Map(x.columnName -> x.value)}.toMap))
      }
    }
    result.copy(notImportedData = notImportedDataExceptions ++ result.notImportedData)
  }

  def importAssets(inputStream: InputStream, fileName: String, user: User, logId: Long, municipalitiesToExpire: Set[Int]) : Unit = {
    try {
      val result = processing(inputStream, municipalitiesToExpire, user)
      result match {
        case ImportResultPointAsset(Nil, Nil, Nil, Nil, _) => update(logId, Status.OK)
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

  def processing(inputStream: InputStream, municipalitiesToExpire: Set[Int], user: User): ImportResultData = {
    val streamReader = new InputStreamReader(inputStream, "UTF-8")
    val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
      override val delimiter: Char = ';'
    })
    withDynTransaction{
      trafficSignService.expireAssetsByMunicipalities(municipalitiesToExpire)
      val result = csvReader.allWithHeaders().foldLeft(ImportResultPointAsset()) { (result, row) =>
        val csvRow = row.map(r => (r._1.toLowerCase, r._2))
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
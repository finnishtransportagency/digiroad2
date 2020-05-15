package fi.liikennevirasto.digiroad2.csvDataImporter

import java.io.{InputStream, InputStreamReader}

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import fi.liikennevirasto.digiroad2.TrafficSignTypeGroup.AdditionalPanels
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.{AdditionalPanelInfo, IncomingTrafficSign, TrafficSignService}
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
  private val suggestBoxPublicId = "suggest_box"
  private val additionalPanelPublicId = "additional_panel"

  lazy val trafficSignService: TrafficSignService = new TrafficSignService(roadLinkService, eventBusImpl)

  private val longValueFieldMappings = coordinateMappings

  //mandatory for the sign 142 (RoadWorks)
  override val dateFieldsMapping = Map(
    "alkupaivamaara" -> "startDate",
    "loppupaivamaara" -> "endDate"
  )

  private val singleChoiceAcceptableValues = Map(
    "rakenne" -> Seq(1, 2, 3, 4, 5, 6, 999),
    "kunto" -> Seq(1, 2, 3, 4, 5, 999),
    "koko" -> Seq(1, 2, 3, 999),
    "kalvon tyyppi" -> Seq(1, 2, 3, 999),
    "merkin materiaali" -> Seq(1, 2, 3, 999),
    "sijaintitarkenne" -> Seq(1, 2, 3, 4, 5, 6, 999),
    "kaistan tyyppi" -> Seq(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 20, 21, 22, 999),
    "tila" -> Seq(1, 2, 3, 4, 5, 99, 999),
    "vauriotyyppi" -> Seq(1, 2, 3, 4, 999),
    "korjauksen kiireellisyys" -> Seq(1, 2, 3, 4, 999)
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
          result.copy(_2 = AssetProperty(columnName = singleChoiceMapping(key), value = trafficSignService.getDefaultSingleChoiceValue) :: result._2)
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
          val (malformedParameters, properties) = singleChoiceToProperty(key, value, singleChoiceAcceptableValues, singleChoiceMapping)
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

    var errorMessages: (List[String], Seq[CsvAssetRowAndRoadLink]) = (List(), Seq())
    val optTrafficSignType = getPropertyValueOption(parsedRow, "trafficSignType").asInstanceOf[Option[String]]

    /* start date validations */
    val temporaryDevices = Seq(4,5)
    val optLifeCycle = getPropertyValueOption(parsedRow, "lifeCycle").asInstanceOf[Option[Int]].get
    val optStartDate = getPropertyValueOption(parsedRow, "startDate").asInstanceOf[Option[String]]
    val optEndDate = getPropertyValueOption(parsedRow, "endDate").asInstanceOf[Option[String]]
    val isValidDate = optTrafficSignType match {
      case Some(signType) if (!isBlank(optLifeCycle.toString) && temporaryDevices.contains(optLifeCycle)) || TrafficSignType.applyNewLawCode(signType) == RoadWorks =>
        (optStartDate, optEndDate) match {
          case (Some(startDate), Some(endDate)) =>
            try {
              val startDateFormat = dateFormatter.parseDateTime(startDate)
              val endDateFormat = dateFormatter.parseDateTime(endDate)
              endDateFormat.isAfter(startDateFormat) || endDateFormat.isEqual(startDateFormat)
            } catch {
              case _: Throwable => false
            }
          case (_, _) => false
        }
      case _ => true
    }

    if (!isValidDate) {
      errorMessages = (errorMessages._1 ++ List("Invalid dates"), Seq())
    }
    /* end date validations */

    /* start direction validations */
    val optTrafficDirection = getPropertyValueOption(parsedRow, "bearing").asInstanceOf[Option[String]]
    val directionValidator = optTrafficDirection match {
      case Some(direction) if direction.trim.nonEmpty => true
      case Some(_) if hasRoadName => true
      case None if hasRoadName => true
      case _ =>
        errorMessages = (errorMessages._1 ++ List("Invalid traffic sign direction"), Seq())
        false
    }
    /* end direction validations */

    /* start additional panels type validations */
    val minAdditionalPanels = 1
    val maxAdditionalPanels = 3
    val additionalPanelsValidator = optTrafficSignType match {
      case Some(sType) if sType.trim.nonEmpty =>
        val panelVerification = (minAdditionalPanels to maxAdditionalPanels).map { index =>
          getPropertyValueOption(parsedRow, "additionalPanelType" + index).asInstanceOf[Option[String]] match {
            case Some(panelType) if panelType.trim.nonEmpty && TrafficSignType.applyNewLawCode(panelType).group.value != AdditionalPanels.value =>
              errorMessages = (errorMessages._1 ++ List("Invalid additional panel type on lisakilpi " + index), Seq())
              false
            case _ => true
          }
        }
        panelVerification.count(_ == false) < 1
      case _ => true
    }
    /* end additional panels type validations */

    /* start lane type validations */
    val optLaneType = getPropertyValueOption(parsedRow, "laneType").asInstanceOf[Option[Int]]
    val optLane = getPropertyValueOption(parsedRow, "lane").asInstanceOf[Option[String]]
    val lanesValidator = (optLaneType, optLane) match {
      case (_, Some(lane)) if lane.trim.nonEmpty && !lane.matches("^([1-3][1-9])$") =>
        errorMessages = (errorMessages._1 ++ List("Invalid lane"), Seq())
        false
      case (Some(laneType), Some(lane)) if laneType != 999 && lane.trim.nonEmpty =>
        if (laneType == 1 && lane.trim.charAt(1) == '1') true
        else if (laneType != 1 && lane.trim.charAt(1) != '1')  true
        else {
          errorMessages = (errorMessages._1 ++ List("Invalid lane and lane type match"), Seq())
          false
        }
      case (_,_) => true
    }
    /* end lane type validations */

    if(isValidDate && directionValidator && additionalPanelsValidator && lanesValidator) super.verifyData(parsedRow, user)
    else errorMessages
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

    val propertiesValues = extractPropertyValues(listPublicIds, listFieldNames, trafficSignAttributes)
    //not possible to insert suggested signs through csv
    val suggestBox = Set(Some(SimplePointAssetProperty(suggestBoxPublicId, Seq(PropertyValue("0")))))

    (Set(Some(typeProperty), valueProperty) ++ propertiesValues ++ suggestBox ++ generateBasePanelProperties(trafficSignAttributes)).flatten
  }

  private def generateBasePanelProperties(trafficSignAttributes: ParsedProperties): Set[Option[SimplePointAssetProperty]] = {

    def getSingleChoiceValue(target: String): Int = {
      getPropertyValueOption(trafficSignAttributes, target) match {
        case Some(targetValue) if !isBlank(targetValue.toString) => targetValue.toString.toInt
        case _ => trafficSignService.getDefaultSingleChoiceValue
      }
    }
    var res: Seq[AdditionalPanel] = Seq()
    val minAdditionalPanels = 1
    val maxAdditionalPanels = 3
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
              getSingleChoiceValue("additionalPanelSize" + index).toInt,
              getSingleChoiceValue("additionalPanelCoatingType" + index).toInt,
              getSingleChoiceValue("additionalPanelColor" + index).toInt
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
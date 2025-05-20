package fi.liikennevirasto.digiroad2.csvDataImporter

import fi.liikennevirasto.digiroad2.asset.{PointAssetState, PointAssetStructure, SimplePointAssetProperty, State, TrafficLights}
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.dao.pointasset.TrafficLight
import fi.liikennevirasto.digiroad2.lane.LaneType
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.{AssetProperty, DigiroadEventBus, GeometryUtils, NotImportedData, Point, TrafficLightPushButton, TrafficLightRelativePosition, TrafficLightSoundSignal, TrafficLightType, TrafficLightVehicleDetection}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.{IncomingTrafficLight, TrafficLightService}
import fi.liikennevirasto.digiroad2.user.User
import org.apache.commons.lang3.StringUtils.isBlank

class TrafficLightsCsvImporter(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends PointAssetCsvImporter {
  override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def roadLinkClient: RoadLinkClient = roadLinkServiceImpl.roadLinkClient
  override def eventBus: DigiroadEventBus = eventBusImpl

  private val typePublicId = "trafficLight_type"
  private val infoPublicId = "trafficLight_info"
  private val municipalityPublicId = "trafficLight_municipality_id"
  private val structurePublicId = "trafficLight_structure"
  private val heightPublicId = "trafficLight_height"
  private val soundSignalPublicId = "trafficLight_sound_signal"
  private val vehicleDetectionPublicId = "trafficLight_vehicle_detection"
  private val pushButtonPublicId = "trafficLight_push_button"
  private val positionPublicId = "trafficLight_relative_position"
  private val locationCoordinatesXPublicId = "location_coordinates_x"
  private val locationCoordinatesYPublicId = "location_coordinates_y"
  private val laneTypePublicId = "trafficLight_lane_type"
  private val laneNumberPublicId = "trafficLight_lane"
  private val statePublicId = "trafficLight_state"
  private val bearingPublicId = "bearing"
  private val sidecodePublicId = "sidecode"

  private val longValueFieldMappings = coordinateMappings

  private val typeMapping = Map("liikennevalo tyyppi" -> "trafficLightType")

  private val typeMappingAcceptableValues = Map(
    "liikennevalo tyyppi" -> TrafficLightType
  )

  private val singleChoiceMapping = Map(
    "rakennelma" -> "trafficLightStructure",
    "aanimerkki" -> "trafficLightSoundSignal",
    "tunnistus" -> "trafficLightVehicleDetection",
    "painonappi" -> "trafficLightPushButton",
    "sijainti" -> "trafficLightPosition",
    "kaistan tyyppi" -> "trafficLightLaneType",
    "tila" -> "trafficLightState"
  )

  private val nonMandatoryMappings = Map(
    "lisatieto" -> "trafficLightInfo",
    "kunta id" -> "trafficLightMunicipalityId"
  )

  override val intValueFieldsMapping = Map(
    "korkeus" -> "trafficLightHeight",
    "kaista" -> "trafficLightLaneNumber",
    "suuntima" -> "trafficLightBearing"
  )

  private val singleChoiceAcceptableValues = Map(
    "rakennelma" -> (PointAssetStructure.values.map(_.value), PointAssetStructure.getDefault.value),
    "aanimerkki" -> (TrafficLightSoundSignal.values.map(_.value), TrafficLightSoundSignal.getDefault.value),
    "tunnistus" -> (TrafficLightVehicleDetection.values.map(_.value), TrafficLightVehicleDetection.getDefault.value),
    "painonappi" -> (TrafficLightPushButton.values.map(_.value), TrafficLightPushButton.getDefault.value),
    "sijainti" -> (TrafficLightRelativePosition.values.map(_.value), TrafficLightRelativePosition.getDefault.value),
    "kaistan tyyppi" -> (LaneType.values.map(_.value), LaneType.getDefault.value),
    "tila" -> (PointAssetState.values.map(_.value), PointAssetState.getDefault.value)
  )

  val mappings : Map[String, String] = longValueFieldMappings ++ nonMandatoryMappings ++ singleChoiceMapping ++ intValueFieldsMapping ++ typeMapping

  lazy val trafficLightsService: TrafficLightService = new TrafficLightService(roadLinkService)

  override def mandatoryFields: Set[String] = longValueFieldMappings.keySet ++ typeMapping.keySet

  override def verifyData(parsedRow: ParsedProperties, user: User): ParsedCsv = {
    /* start lane type validations */
    val optLaneType = getPropertyValueOption(parsedRow, "trafficLightLaneType").asInstanceOf[Option[Int]]
    val optLaneNumber = getPropertyValueOption(parsedRow, "trafficLightLaneNumber").asInstanceOf[Option[String]]

    val (_, lanesValidatorErrorMsg) = csvLaneValidator(optLaneType, optLaneNumber)
    /* end lane type validations */

    val optLon = getPropertyValueOption(parsedRow, "lon").asInstanceOf[Option[BigDecimal]]
    val optLat = getPropertyValueOption(parsedRow, "lat").asInstanceOf[Option[BigDecimal]]

    val (authorizationErrorMsg, parsedRowAndRoadLink) = (optLon, optLat) match {
      case (Some(lon), Some(lat)) =>
        val roadLinks = roadLinkService.getClosestRoadlinkForCarTraffic(user, Point(lon.toLong, lat.toLong), forCarTraffic = false)
        if (roadLinks.isEmpty) {
          (List(s"No Rights for Municipality or nonexistent road links near asset position"), Seq())
        } else if (assetHasEditingRestrictions(TrafficLights.typeId, roadLinks)) {
          (List("Asset type editing is restricted within municipality or admininistrative class."), Seq())
        } else {
          (List(), Seq(CsvAssetRowAndRoadLink(parsedRow, roadLinks)))
        }
      case _ =>
        (Nil, Nil)
    }
    val allErrorMsg = lanesValidatorErrorMsg ++ authorizationErrorMsg

    (allErrorMsg, parsedRowAndRoadLink)
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
        } else if (typeMapping.contains(key)) {
          val (malformedParameters, properties) = verifyTrafficLightType(key, value.toString)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (singleChoiceMapping.contains(key)) {
          val acceptableValues = singleChoiceAcceptableValues(key) match { case (values, _) => values}
          val (malformedParameters, properties) = singleChoiceToProperty(key, value, acceptableValues, singleChoiceMapping)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (intValueFieldsMapping.contains(key)) {
          val (malformedParameters, properties) = verifyIntType(key, value.toString)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (nonMandatoryMappings.contains(key)) {
          result.copy(_2 = AssetProperty(columnName = nonMandatoryMappings(key), value = value) :: result._2)
        } else
          result
      }
    }
  }

  override def createAsset(pointAssetAttributes: Seq[CsvAssetRowAndRoadLink], user: User, result: ImportResultPointAsset): ImportResultPointAsset = {
    val trafficLights = pointAssetAttributes.map { trafficLightAttribute =>
      val csvProperties = trafficLightAttribute.properties
      val nearbyLinks = trafficLightAttribute.roadLink
      val optBearing = tryToInt(getPropertyValue(csvProperties, "trafficLightBearing").toString)
      val position = getCoordinatesFromProperties(csvProperties)

      val assetValidityDirection = if(optBearing.nonEmpty) Some(trafficLightsService.getAssetValidityDirection(optBearing.get))
      else None

      val possibleRoadLinks = roadLinkService.filterRoadLinkByBearing(optBearing, assetValidityDirection, position, nearbyLinks)

      val roadLinks = possibleRoadLinks.filter(_.administrativeClass != State)
      val roadLink = if (roadLinks.nonEmpty) {
        roadLinks.minBy(r => GeometryUtils.minimumDistance(position, r.geometry))
      } else
        nearbyLinks.minBy(r => GeometryUtils.minimumDistance(position, r.geometry))

      val sideCode = trafficLightsService.getSideCode(position, roadLink, optBearing)
      val mValue = GeometryUtils.calculateLinearReferenceFromPoint(position, roadLink.geometry)
      val bearingToUseForAsset = Some(GeometryUtils.calculateBearing(roadLink.geometry, Some(mValue)))

      val sidecodeProperty = AssetProperty("trafficLightSidecode", sideCode)

      val finalProperties = (bearingToUseForAsset match {
        case Some(bearing) =>
          val bearingProperty = AssetProperty("trafficLightBearing", bearing)
          csvProperties.filterNot(_.columnName == "trafficLightBearing") ++ Seq(bearingProperty)

        case _ =>
          csvProperties
      }) ++ Seq(sidecodeProperty)

      (csvProperties, CsvPointAsset(position.x, position.y, roadLink.linkId, generateBaseProperties(finalProperties), sideCode, bearingToUseForAsset, mValue, roadLink, (roadLinks.isEmpty || roadLinks.size > 1) && bearingToUseForAsset.isEmpty))
    }

    val notImportedData: Seq[NotImportedData] = trafficLights.map { case (csvRow, trafficLight) =>
      val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(trafficLight.lon, trafficLight.lat), trafficLight.roadLink.geometry)
      val bearing = if(trafficLight.bearing.isEmpty && !trafficLight.isFloating)
        Some(GeometryUtils.calculateBearing(trafficLight.roadLink.geometry, Some(mValue)))
      else
        trafficLight.bearing
      try {
        val trafficLightsSuitableForMerge = trafficLightsService.fetchSuitableForMergeByRadius(centralPoint = Point(trafficLight.lon, trafficLight.lat), radius = 5)
        if (trafficLightsSuitableForMerge.isEmpty)
          trafficLightsService.createFromCoordinates(IncomingTrafficLight(trafficLight.lon, trafficLight.lat, trafficLight.roadLink.linkId, trafficLight.propertyData, Some(trafficLight.validityDirection), bearing), trafficLight.roadLink, user.username, trafficLight.isFloating)
        else
          mergeAndUpdateTrafficLight(trafficLightsSuitableForMerge.head, toBeMerged = trafficLight, user)
      } catch {
        case ex: NoSuchElementException => NotImportedData(reason = ex.getMessage, csvRow = rowToString(csvRow.flatMap{x => Map(x.columnName -> x.value)}.toMap))
      }
    }.collect { case element: NotImportedData => element }

    result.copy(notImportedData = result.notImportedData ++ notImportedData)
  }

  private def generateBaseProperties(trafficLightAttributes: ParsedProperties) : Set[SimplePointAssetProperty] = {
    val listPublicIds = Seq(typePublicId, infoPublicId, municipalityPublicId, structurePublicId, heightPublicId, soundSignalPublicId, vehicleDetectionPublicId,
                            pushButtonPublicId, positionPublicId, locationCoordinatesXPublicId, locationCoordinatesYPublicId, laneTypePublicId, laneNumberPublicId, statePublicId, bearingPublicId, sidecodePublicId)

    val listFieldNames = Seq("trafficLightType", "trafficLightInfo", "trafficLightMunicipalityId", "trafficLightStructure", "trafficLightHeight", "trafficLightSoundSignal", "trafficLightVehicleDetection",
                             "trafficLightPushButton", "trafficLightPosition", "lon", "lat", "trafficLightLaneType", "trafficLightLaneNumber", "trafficLightState", "trafficLightBearing", "trafficLightSidecode")

    extractPropertyValues(listPublicIds, listFieldNames, trafficLightAttributes, withGroupedId = true).toSet.flatten
  }

  def mergeAndUpdateTrafficLight(baseTrafficLight: TrafficLight, toBeMerged: CsvPointAsset, user: User): Long = {
    val propertyData = baseTrafficLight.propertyData.map(property => SimplePointAssetProperty(property.publicId, property.values, property.groupedId)).toSet ++ toBeMerged.propertyData
    trafficLightsService.updateWithoutTransaction(baseTrafficLight.id, IncomingTrafficLight(baseTrafficLight.lon, baseTrafficLight.lat, baseTrafficLight.linkId, propertyData), toBeMerged.roadLink, user.username, Some(baseTrafficLight.mValue), None)
  }

  def verifyTrafficLightType(parameterName: String, assetTypeChoice: String): ParsedRow = {
    tryToDouble(assetTypeChoice) match {
      case Some(value) if typeMappingAcceptableValues(parameterName).values.map(_.value).contains(value) =>
        (Nil, List(AssetProperty(columnName = typeMapping(parameterName), value = value)))
      case _ =>
        (List(s"Invalid value for $parameterName"), Nil)
    }
  }
}
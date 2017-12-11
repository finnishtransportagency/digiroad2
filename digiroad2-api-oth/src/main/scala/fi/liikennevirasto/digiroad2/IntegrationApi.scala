package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.Digiroad2Context._
import fi.liikennevirasto.digiroad2.asset.Asset._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.VVHRoadNodes
import fi.liikennevirasto.digiroad2.dao.pointasset._
import fi.liikennevirasto.digiroad2.linearasset.ValidityPeriodDayOfWeek.{Saturday, Sunday}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.pointasset.oracle._
import fi.liikennevirasto.digiroad2.service.linearasset.{ChangedSpeedLimit, LinearAssetOperations, LinearAssetTypes, Manoeuvre}
import fi.liikennevirasto.digiroad2.service.pointasset.PersistedMassTransitStop
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.{MassTransitStopOperations, MassTransitStopService, PersistedMassTransitStop}
import org.joda.time.DateTime
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.{BadRequest, ScalatraServlet}
import org.slf4j.LoggerFactory

class IntegrationApi(val massTransitStopService: MassTransitStopService) extends ScalatraServlet with JacksonJsonSupport with AuthenticationSupport {
  val logger = LoggerFactory.getLogger(getClass)
  protected implicit val jsonFormats: Formats = DefaultFormats

  case class AssetTimeStamps(created: Modification, modified: Modification) extends TimeStamps

  def clearCache() = {
    roadLinkService.clearCache()
  }

  before() {
    basicAuth
  }

  def extractModificationTime(timeStamps: TimeStamps): (String, String) = {
    "muokattu_viimeksi" ->
      timeStamps.modified.modificationTime.map(DateTimePropertyFormat.print(_))
        .getOrElse(timeStamps.created.modificationTime.map(DateTimePropertyFormat.print(_))
          .getOrElse(""))
  }

  def extractModifier(massTransitStop: PersistedMassTransitStop): (String, String) = {
    "muokannut_viimeksi" ->  massTransitStop.modified.modifier
      .getOrElse(massTransitStop.created.modifier
        .getOrElse(""))
  }
  private def toGeoJSON(input: Iterable[PersistedMassTransitStop]): Map[String, Any] = {
    def extractPropertyValue(key: String, properties: Seq[Property], transformation: (Seq[String] => Any)): (String, Any) = {
      val values: Seq[String] = properties.filter { property => property.publicId == key }.flatMap { property =>
        property.values.map { value =>
          value.propertyValue
        }
      }
      key -> transformation(values)
    }
    def propertyValuesToIntList(values: Seq[String]): Seq[Int] = { values.map(_.toInt) }
    def propertyValuesToString(values: Seq[String]): String = { values.mkString }
    def firstPropertyValueToInt(values: Seq[String]): Int = {
      try {
        values.headOption.map(_.toInt).get
      } catch {
        case e: Exception => 99
      }
    }
    def extractBearing(massTransitStop: PersistedMassTransitStop): (String, Option[Int]) = { "suuntima" -> GeometryUtils.calculateActualBearing(massTransitStop.validityDirection.getOrElse(0), massTransitStop.bearing) }
    def extractExternalId(massTransitStop: PersistedMassTransitStop): (String, Long) = { "valtakunnallinen_id" -> massTransitStop.nationalId }
    def extractFloating(massTransitStop: PersistedMassTransitStop): (String, Boolean) = { "kelluvuus" -> massTransitStop.floating }
    def extractLinkId(massTransitStop: PersistedMassTransitStop): (String, Option[Long]) = { "link_id" -> Some(massTransitStop.linkId) }
    def extractMvalue(massTransitStop: PersistedMassTransitStop): (String, Option[Double]) = { "m_value" -> Some(massTransitStop.mValue) }
    def extractLinkSource(massTransitStop: PersistedMassTransitStop) : (String, Option[Int]) = { "linkSource" -> Some(massTransitStop.linkSource.value) }
    Map(
      "type" -> "FeatureCollection",
      "features" -> input.map {
        case (massTransitStop: PersistedMassTransitStop) => Map(
          "type" -> "Feature",
          "id" -> massTransitStop.id,
          "geometry" -> Map("type" -> "Point", "coordinates" -> List(massTransitStop.lon, massTransitStop.lat)),
          "properties" -> Map(
            extractModifier(massTransitStop),
            latestModificationTime(massTransitStop.created.modificationTime, massTransitStop.modified.modificationTime),
            lastModifiedBy(massTransitStop.created.modifier, massTransitStop.modified.modifier),
            extractBearing(massTransitStop),
            extractExternalId(massTransitStop),
            extractFloating(massTransitStop),
            extractLinkId(massTransitStop),
            extractMvalue(massTransitStop),
            extractLinkSource(massTransitStop),
            extractPropertyValue("pysakin_tyyppi", massTransitStop.propertyData, propertyValuesToIntList),
            extractPropertyValue("nimi_suomeksi", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("nimi_ruotsiksi", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("osoite_suomeksi", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("osoite_ruotsiksi", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("tietojen_yllapitaja", massTransitStop.propertyData, firstPropertyValueToInt),
            extractPropertyValue("yllapitajan_tunnus", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("yllapitajan_koodi", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("matkustajatunnus", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("maastokoordinaatti_x", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("maastokoordinaatti_y", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("maastokoordinaatti_z", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("liikennointisuunta", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("vaikutussuunta", massTransitStop.propertyData, firstPropertyValueToInt),
            extractPropertyValue("ensimmainen_voimassaolopaiva", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("viimeinen_voimassaolopaiva", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("aikataulu", massTransitStop.propertyData, firstPropertyValueToInt),
            extractPropertyValue("katos", massTransitStop.propertyData, firstPropertyValueToInt),
            extractPropertyValue("mainoskatos", massTransitStop.propertyData, firstPropertyValueToInt),
            extractPropertyValue("penkki", massTransitStop.propertyData, firstPropertyValueToInt),
            extractPropertyValue("sahkoinen_aikataulunaytto", massTransitStop.propertyData, firstPropertyValueToInt),
            extractPropertyValue("valaistus", massTransitStop.propertyData, firstPropertyValueToInt),
            extractPropertyValue("esteettomyys_liikuntarajoitteiselle", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("saattomahdollisuus_henkiloautolla", massTransitStop.propertyData, firstPropertyValueToInt),
            extractPropertyValue("liityntapysakointipaikkojen_maara", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("liityntapysakoinnin_lisatiedot", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("pysakin_omistaja", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("palauteosoite", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("lisatiedot", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("pyorateline", massTransitStop.propertyData, firstPropertyValueToInt),
            extractPropertyValue("laiturinumero", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("liitetty_terminaaliin", massTransitStop.propertyData, propertyValuesToString)))
      })
  }

  private def getMassTransitStopsByMunicipality(municipalityNumber: Int): Iterable[PersistedMassTransitStop] = {
    massTransitStopService.getByMunicipality(municipalityNumber)
  }

  def speedLimitsToApi(speedLimits: Seq[SpeedLimit]): Seq[Map[String, Any]] = {
    speedLimits.map { speedLimit =>
      Map("id" -> speedLimit.id,
        "sideCode" -> speedLimit.sideCode.value,
        "points" -> speedLimit.geometry,
        geometryWKTForLinearAssets(speedLimit.geometry),
        "value" -> speedLimit.value.fold(0)(_.value),
        "startMeasure" -> speedLimit.startMeasure,
        "endMeasure" -> speedLimit.endMeasure,
        "linkId" -> speedLimit.linkId,
        latestModificationTime(speedLimit.createdDateTime, speedLimit.modifiedDateTime),
        lastModifiedBy(speedLimit.createdBy, speedLimit.modifiedBy),
        "linkSource" -> speedLimit.linkSource.value
      )
    }
  }

  def speedLimitsChangesToApi(since: DateTime, speedLimits: Seq[ChangedSpeedLimit]) = {
    speedLimits.map { case ChangedSpeedLimit(speedLimit, link) =>
      Map("id" -> speedLimit.id,
        "sideCode" -> speedLimit.sideCode.value,
        "points" -> speedLimit.geometry,
        geometryWKTForLinearAssets(speedLimit.geometry),
        "value" -> speedLimit.value.fold(0)(_.value),
        "startMeasure" -> speedLimit.startMeasure,
        "endMeasure" -> speedLimit.endMeasure,
        "linkId" -> speedLimit.linkId,
        latestModificationTime(speedLimit.createdDateTime, speedLimit.modifiedDateTime),
        lastModifiedBy(speedLimit.createdBy, speedLimit.modifiedBy),
        "changeType" -> extractChangeType(since, speedLimit.expired, speedLimit.createdDateTime),
        "linkSource" -> speedLimit.linkSource.value)
    }
  }

  private def roadLinkPropertiesToApi(roadLinks: Seq[RoadLink]): Seq[Map[String, Any]] = {
    roadLinks.map{ roadLink =>
      Map("linkId" -> roadLink.linkId,
        "mmlId" -> roadLink.attributes.get("MTKID"),
        "administrativeClass" -> roadLink.administrativeClass.value,
        "functionalClass" -> roadLink.functionalClass,
        "trafficDirection" -> roadLink.trafficDirection.value,
        "linkType" -> roadLink.linkType.value,
        "modifiedAt" -> roadLink.modifiedAt,
        lastModifiedBy(None, roadLink.modifiedBy),
        "startNode" -> roadLink.attributes.get("STARTNODE"),
        "endNode" -> roadLink.attributes.get("ENDNODE"),
        "cust_owner" -> roadLink.attributes.get("CUST_OWNER"),
        "linkSource" -> roadLink.linkSource.value) ++ roadLink.attributes.filterNot(_._1 == "MTKID")
                                                                         .filterNot(_._1 == "ROADNUMBER")
                                                                         .filterNot(_._1 == "ROADPARTNUMBER")
                                                                         .filterNot(_._1 == "STARTNODE")
                                                                         .filterNot(_._1 == "ENDNODE")
                                                                         .filterNot(_._1 == "CUST_OWNER")
                                                                         .filterNot(_._1 == "MTKCLASS" && roadLink.linkSource.value == LinkGeomSource.ComplimentaryLinkInterface.value)

    }
  }

  def toTimeDomain(validityPeriod: ValidityPeriod): String = {
    val daySpec = validityPeriod.days match {
      case Saturday => "(t7){d1}"
      case Sunday => "(t1){d1}"
      case _ => "(t2){d5}"
    }
    s"[[$daySpec]*[(h${validityPeriod.startHour}){h${validityPeriod.duration()}}]]"
  }

  def toTimeDomainWithMinutes(validityPeriod: ValidityPeriod): String = {
    val daySpec = validityPeriod.days match {
      case Saturday => "(t7){d1}"
      case Sunday => "(t1){d1}"
      case _ => "(t2){d5}"
    }
    s"[[$daySpec]*[(h${validityPeriod.startHour}m${validityPeriod.startMinute}){h${validityPeriod.preciseDuration()._1}m${validityPeriod.preciseDuration()._2}}]]"
  }

  def valueToApi(value: Option[Value]) = {
    value match {
      case Some(Prohibitions(x)) => x.map { prohibitionValue =>
        val exceptions = prohibitionValue.exceptions.toList match {
          case Nil => Map()
          case items => Map("exceptions" -> items)
        }
        val validityPeriods = prohibitionValue.validityPeriods.toList match {
          case Nil => Map()
          case _ => Map("validityPeriods" -> prohibitionValue.validityPeriods.map(toTimeDomain))
        }
        Map("typeId" -> prohibitionValue.typeId) ++ validityPeriods ++ exceptions
      }
      case Some(TextualValue(x)) => x.split("\n").toSeq
      case _ => value.map(_.toJson)
    }
  }

  def linearAssetsToApi(typeId: Int, municipalityNumber: Int): Seq[Map[String, Any]] = {
    def isUnknown(asset:PieceWiseLinearAsset) = asset.id == 0
    def getLinearAssetService(typeId: Int): LinearAssetOperations = {
      typeId match {
        case LinearAssetTypes.MaintenanceRoadAssetTypeId => maintenanceRoadService
        case LinearAssetTypes.PavingAssetTypeId => pavingService
        case LinearAssetTypes.RoadWidthAssetTypeId => roadWidthService
        case _ => linearAssetService
      }
    }

    val linearAssets: Seq[PieceWiseLinearAsset] = getLinearAssetService(typeId).getByMunicipality(typeId, municipalityNumber).filterNot(isUnknown)

    linearAssets.map { asset =>
      Map("id" -> asset.id,
        "points" -> asset.geometry,
        geometryWKTForLinearAssets(asset.geometry),
        "value" -> valueToApi(asset.value),
        "side_code" -> asset.sideCode.value,
        "linkId" -> asset.linkId,
        "startMeasure" -> asset.startMeasure,
        "endMeasure" -> asset.endMeasure,
        latestModificationTime(asset.createdDateTime, asset.modifiedDateTime),
        lastModifiedBy(asset.createdBy, asset.modifiedBy),
        "linkSource" -> asset.linkSource.value
      )
    }
  }

  def pedestrianCrossingsToApi(crossings: Seq[PedestrianCrossing]): Seq[Map[String, Any]] = {
    crossings.filterNot(_.floating).map { pedestrianCrossing =>
      Map("id" -> pedestrianCrossing.id,
        "point" -> Point(pedestrianCrossing.lon, pedestrianCrossing.lat),
        geometryWKTForPoints(pedestrianCrossing.lon, pedestrianCrossing.lat),
        "linkId" -> pedestrianCrossing.linkId,
        "m_value" -> pedestrianCrossing.mValue,
        latestModificationTime(pedestrianCrossing.createdAt, pedestrianCrossing.modifiedAt),
        lastModifiedBy(pedestrianCrossing.createdBy, pedestrianCrossing.modifiedBy),
        "linkSource" -> pedestrianCrossing.linkSource.value)
    }
  }

  def trafficLightsToApi(trafficLights: Seq[TrafficLight]): Seq[Map[String, Any]] = {
    trafficLights.filterNot(_.floating).map { trafficLight =>
      Map("id" -> trafficLight.id,
        "point" -> Point(trafficLight.lon, trafficLight.lat),
        geometryWKTForPoints(trafficLight.lon, trafficLight.lat),
        "linkId" -> trafficLight.linkId,
        "m_value" -> trafficLight.mValue,
        latestModificationTime(trafficLight.createdAt, trafficLight.modifiedAt),
        lastModifiedBy(trafficLight.createdBy, trafficLight.modifiedBy),
        "linkSource" -> trafficLight.linkSource.value)
    }
  }

  def directionalTrafficSignsToApi(directionalTrafficSign: Seq[DirectionalTrafficSign]): Seq[Map[String, Any]] = {
    directionalTrafficSign.filterNot(_.floating).map { directionalTrafficSign =>
      Map("id" -> directionalTrafficSign.id,
        "point" -> Point(directionalTrafficSign.lon, directionalTrafficSign.lat),
        geometryWKTForPoints(directionalTrafficSign.lon, directionalTrafficSign.lat),
        "linkId" -> directionalTrafficSign.linkId,
        "m_value" -> directionalTrafficSign.mValue,
        "bearing" -> GeometryUtils.calculateActualBearing( directionalTrafficSign.validityDirection,directionalTrafficSign.bearing),
        "side_code" -> directionalTrafficSign.validityDirection,
        "text" -> directionalTrafficSign.text.map(_.split("\n").toSeq),
        latestModificationTime(directionalTrafficSign.createdAt, directionalTrafficSign.modifiedAt),
        lastModifiedBy(directionalTrafficSign.createdBy, directionalTrafficSign.modifiedBy),
        "linkSource" -> directionalTrafficSign.linkSource.value)
    }
  }

  def latestModificationTime(createdDateTime: Option[DateTime], modifiedDateTime: Option[DateTime]): (String, String) = {
    "muokattu_viimeksi" ->
      modifiedDateTime
        .orElse(createdDateTime)
        .map(DateTimePropertyFormat.print)
        .getOrElse("")
  }

  def lastModifiedBy(createdBy: Option[String], modifiedBy: Option[String]): (String, Boolean) = {

    val autoGeneratedValues = List("dr1conversion", "dr1_conversion", nonFixedUsers(modifiedBy), nonFixedUsers(createdBy), "automatic_correction", "excel_data_migration", "automatic_generation", "vvh_generated", "vvh_modified")

    modifiedBy match {
      case None => "generatedValue" -> false
      case Some(value) if (value != null) =>
        return "generatedValue" -> autoGeneratedValues.exists(agv => agv.equals(value))
    }
    createdBy match {
      case None => "generatedValue" -> false
      case Some(value) =>
        "generatedValue" -> autoGeneratedValues.exists(agv => agv.equals(value))
    }
  }

  def nonFixedUsers(userOption: Option[String])={
    val nonFixedValues = List("split_speedlimit_", "batch_process_")
    userOption match {
      case Some(user) if(nonFixedValues.exists(nfv => user.startsWith(nfv))) =>
        user
      case _ =>
        ""
    }
  }

  def geometryWKTForLinearAssets(geometry: Seq[Point]): (String, String) =
  {
    if (geometry.nonEmpty)
    {
      val segments = geometry.zip(geometry.tail)
      val runningSum = segments.scanLeft(0.0)((current, points) => current + points._1.distance2DTo(points._2))
      val mValuedGeometry = geometry.zip(runningSum.toList)
      val wktString = mValuedGeometry.map {
        case (p, newM) => p.x +" " + p.y + " " + p.z + " " + newM
      }.mkString(", ")
      "geometryWKT" -> ("LINESTRING ZM (" + wktString + ")")
    }
    else
      "geometryWKT" -> ""
  }

  def geometryWKTForPoints(lon: Double, lat: Double): (String, String) = {
    val geometryWKT = "POINT (" + lon + " " + lat + ")"
    "geometryWKT" -> geometryWKT
  }

  def railwayCrossingsToApi(crossings: Seq[RailwayCrossing]): Seq[Map[String, Any]] = {
    crossings.filterNot(_.floating).map { railwayCrossing =>
      Map("id" -> railwayCrossing.id,
        "point" -> Point(railwayCrossing.lon, railwayCrossing.lat),
        geometryWKTForPoints(railwayCrossing.lon, railwayCrossing.lat),
        "linkId" -> railwayCrossing.linkId,
        "m_value" -> railwayCrossing.mValue,
        "safetyEquipment" -> railwayCrossing.safetyEquipment,
        "name" -> railwayCrossing.name,
        latestModificationTime(railwayCrossing.createdAt, railwayCrossing.modifiedAt),
        lastModifiedBy(railwayCrossing.createdBy, railwayCrossing.modifiedBy),
        "linkSource" -> railwayCrossing.linkSource.value)
    }
  }

  def obstaclesToApi(obstacles: Seq[Obstacle]): Seq[Map[String, Any]] = {
    obstacles.filterNot(_.floating).map { obstacle =>
      Map("id" -> obstacle.id,
        "point" -> Point(obstacle.lon, obstacle.lat),
        geometryWKTForPoints(obstacle.lon, obstacle.lat),
        "linkId" -> obstacle.linkId,
        "m_value" -> obstacle.mValue,
        "obstacle_type" -> obstacle.obstacleType,
        latestModificationTime(obstacle.createdAt, obstacle.modifiedAt),
        lastModifiedBy(obstacle.createdBy, obstacle.modifiedBy),
        "linkSource" -> obstacle.linkSource.value)
    }
  }

  def manouvresToApi(manoeuvres: Seq[Manoeuvre]): Seq[Map[String, Any]] = {
    manoeuvres.map { manoeuvre =>
      Map("id" -> manoeuvre.id,
        //DROTH-177: add intermediate links -> check the element structure
        "elements" -> manoeuvre.elements.map(_.sourceLinkId),
        "sourceLinkId" -> manoeuvre.elements.head.sourceLinkId,
        "destLinkId" -> manoeuvre.elements.last.sourceLinkId,
        "exceptions" -> manoeuvre.exceptions,
        "validityPeriods" -> manoeuvre.validityPeriods.map(toTimeDomain),
        "validityPeriodMinutes" -> manoeuvre.validityPeriods.map(toTimeDomainWithMinutes),
        "additionalInfo" -> manoeuvre.additionalInfo,
        "modifiedDateTime" -> manoeuvre.modifiedDateTime.getOrElse(manoeuvre.createdDateTime),
        lastModifiedBy(Some(manoeuvre.createdBy), manoeuvre.modifiedBy))
    }
  }

  def servicePointsToApi(servicePoints: Set[ServicePoint]) = {
    servicePoints.map { asset =>
      Map("id" -> asset.id,
        "point" -> Point(asset.lon, asset.lat),
        geometryWKTForPoints(asset.lon, asset.lat),
        "services" -> asset.services,
        latestModificationTime(asset.createdAt, asset.modifiedAt),
        lastModifiedBy(asset.createdBy, asset.modifiedBy))
    }
  }

  def roadNodesToApi(roadNodes: Seq[VVHRoadNodes]) = {
    roadNodes.map { roadNode =>
      Map("nodeId" -> roadNode.nodeId,
          "nodeType" -> roadNode.formOfNode.value,
          "point" -> Map("x" -> roadNode.geometry.x, "y" -> roadNode.geometry.y),
          "subtype" -> roadNode.subtype,
          geometryWKTForPoints(roadNode.geometry.x, roadNode.geometry.y)
      )
    }
  }

  private def extractChangeType(since: DateTime, expired: Boolean, createdDateTime: Option[DateTime]) = {
    if (expired) {
      "Remove"
    } else if (createdDateTime.exists(_.isAfter(since))) {
      "Add"
    } else {
      "Modify"
    }
  }

  get("/changes/:assetType") {
    contentType = formats("json")
     val since = DateTime.parse(params.get("since").getOrElse(halt(BadRequest("Missing mandatory 'since' parameter"))))
     val until = params.get("until") match {
       case Some(dateValue) => DateTime.parse(dateValue)
       case _ => DateTime.now()
     }

     val assetType = params("assetType")
     assetType match {
       case "speed_limits" => speedLimitsChangesToApi(since, speedLimitService.getChanged(since, until))
       case _ => BadRequest("Invalid asset type")
     }
  }

  get("/:assetType") {
    contentType = formats("json")
    params.get("municipality").map { municipality =>
      val municipalityNumber = municipality.toInt
      val assetType = params("assetType")
      assetType match {
        case "mass_transit_stops" => toGeoJSON(getMassTransitStopsByMunicipality(municipalityNumber))
        case "speed_limits" => speedLimitsToApi(speedLimitService.get(municipalityNumber))
        case "total_weight_limits" => linearAssetsToApi(30, municipalityNumber)
        case "trailer_truck_weight_limits" => linearAssetsToApi(40, municipalityNumber)
        case "axle_weight_limits" => linearAssetsToApi(50, municipalityNumber)
        case "bogie_weight_limits" => linearAssetsToApi(60, municipalityNumber)
        case "height_limits" => linearAssetsToApi(70, municipalityNumber)
        case "length_limits" => linearAssetsToApi(80, municipalityNumber)
        case "width_limits" => linearAssetsToApi(90, municipalityNumber)
        case "obstacles" => obstaclesToApi(obstacleService.getByMunicipality(municipalityNumber))
        case "traffic_lights" => trafficLightsToApi(trafficLightService.getByMunicipality(municipalityNumber))
        case "pedestrian_crossings" => pedestrianCrossingsToApi(pedestrianCrossingService.getByMunicipality(municipalityNumber))
        case "directional_traffic_signs" => directionalTrafficSignsToApi(directionalTrafficSignService.getByMunicipality(municipalityNumber))
        case "railway_crossings" => railwayCrossingsToApi(railwayCrossingService.getByMunicipality(municipalityNumber))
        case "vehicle_prohibitions" => linearAssetsToApi(190, municipalityNumber)
        case "hazardous_material_transport_prohibitions" => linearAssetsToApi(210, municipalityNumber)
        case "number_of_lanes" => linearAssetsToApi(140, municipalityNumber)
        case "mass_transit_lanes" => linearAssetsToApi(160, municipalityNumber)
        case "roads_affected_by_thawing" => linearAssetsToApi(130, municipalityNumber)
        case "widths" => linearAssetsToApi(120, municipalityNumber)
        case "paved_roads" => linearAssetsToApi(110, municipalityNumber)
        case "lit_roads" => linearAssetsToApi(100, municipalityNumber)
        case "speed_limits_during_winter" => linearAssetsToApi(180, municipalityNumber)
        case "traffic_volumes" => linearAssetsToApi(170, municipalityNumber)
        case "congestion_tendencies" => linearAssetsToApi(150, municipalityNumber)
        case "european_roads" => linearAssetsToApi(260, municipalityNumber)
        case "exit_numbers" => linearAssetsToApi(270, municipalityNumber)
        case "road_link_properties" => roadLinkPropertiesToApi(roadLinkService.withRoadAddress(roadLinkService.getRoadLinksAndComplementaryLinksFromVVHByMunicipality(municipalityNumber)))
        case "manoeuvres" => manouvresToApi(manoeuvreService.getByMunicipality(municipalityNumber))
        case "service_points" => servicePointsToApi(servicePointService.getByMunicipality(municipalityNumber))
        case "road_nodes" => roadNodesToApi(roadLinkService.getRoadNodesFromVVHByMunicipality(municipalityNumber))
        case _ => BadRequest("Invalid asset type")
      }
    } getOrElse {
      BadRequest("Missing mandatory 'municipality' parameter")
    }
  }
}

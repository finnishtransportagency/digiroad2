package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.Digiroad2Context._
import fi.liikennevirasto.digiroad2.asset.DateParser._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.asset.{WidthLimit => WidthLimitInfo, HeightLimit => HeightLimitInfo, _}
import fi.liikennevirasto.digiroad2.client.vvh.VVHRoadNodes
import fi.liikennevirasto.digiroad2.dao.pointasset._
import fi.liikennevirasto.digiroad2.linearasset.ValidityPeriodDayOfWeek.{Saturday, Sunday}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.linearasset.{ChangedSpeedLimit, LinearAssetOperations, Manoeuvre}
import fi.liikennevirasto.digiroad2.service.pointasset.{HeightLimit, _}
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.{MassTransitStopService, PersistedMassTransitStop}
import org.joda.time.DateTime
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger.{Swagger, SwaggerSupport}
import org.scalatra.{BadRequest, ScalatraServlet}
import org.slf4j.LoggerFactory

class IntegrationApi(val massTransitStopService: MassTransitStopService, implicit val swagger: Swagger) extends ScalatraServlet with JacksonJsonSupport with AuthenticationSupport with SwaggerSupport {
  val logger = LoggerFactory.getLogger(getClass)
  protected val applicationDescription = "Integration API "
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
    def extractPropertyValue(key: String, properties: Seq[Property], transformation: (Seq[String] => Any), mapName: Option[String] = None): (String, Any) = {
      val values: Seq[String] = properties.filter { property => property.publicId == key }.flatMap { property =>
        property.values.map { value =>
          value.propertyValue
        }
      }
      mapName.getOrElse(key) -> transformation(values)
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
    def extractLinkId(massTransitStop: PersistedMassTransitStop): (String, Option[Long]) = { if(Some(massTransitStop.linkId).contains(0)) "link_id" -> None else "link_id" -> Some(massTransitStop.linkId) }
    def extractMvalue(massTransitStop: PersistedMassTransitStop): (String, Option[Double]) = {if(Some(massTransitStop.mValue).contains(0)) "m_value" -> None else "m_value" -> Some(massTransitStop.mValue) }
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
            extractPropertyValue("liitetty_terminaaliin_ulkoinen_tunnus", massTransitStop.propertyData, propertyValuesToString, Some("liitetty_terminaaliin")),
            extractPropertyValue("alternative_link_id", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("vyohyketieto", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("tarkenne", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("palvelun_lisätieto", massTransitStop.propertyData, propertyValuesToString),
            extractPropertyValue("viranomaisdataa", massTransitStop.propertyData, propertyValuesToString)
          ))
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
        "accessRightID" -> roadLink.attributes.get("ACCESS_RIGHT_ID"),
        "privateRoadAssociation" -> roadLink.attributes.get("PRIVATE_ROAD_ASSOCIATION"),
        "additionalInfo" -> roadLink.attributes.get("ADDITIONAL_INFO"),
        "linkSource" -> roadLink.linkSource.value) ++ roadLink.attributes.filterNot(_._1 == "MTKID")
                                                                                              .filterNot(_._1 == "ROADNUMBER")
                                                                                              .filterNot(_._1 == "ROADPARTNUMBER")
                                                                                              .filterNot(_._1 == "STARTNODE")
                                                                                              .filterNot(_._1 == "ENDNODE")
                                                                                              .filterNot(_._1 == "CUST_OWNER")
                                                                                              .filterNot(_._1 == "MTKCLASS" && roadLink.linkSource.value == LinkGeomSource.ComplimentaryLinkInterface.value)
                                                                                              .filterNot(_._1 == "ACCESS_RIGHT_ID")
                                                                                              .filterNot(_._1 == "PRIVATE_ROAD_ASSOCIATION")
                                                                                              .filterNot(_._1 == "ADDITIONAL_INFO")
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

  def toTimeDomain(validityPeriod: ValidityPeriodValue): String = {
    //Weekday: 0 ; Saturday: 1, ; Sunday: 2
    val daySpec = validityPeriod.days match {
      case 1 => "(t7){d1}"
      case 2 => "(t1){d1}"
      case _ => "(t2){d5}"
    }
    s"[[$daySpec]*[(h${validityPeriod.startHour}){h${ValidityPeriodValue.duration(validityPeriod.startHour, validityPeriod.startMinute, validityPeriod.endHour, validityPeriod.endMinute)}}]]"
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
      case Some(DynamicValue(x)) => x.properties.flatMap { dynamicTypeProperty => dynamicTypeProperty.values.map { v => v.value} }.headOption
      case _ => value.map(_.toJson)
    }
  }

  def getLinearAssetService(typeId: Int): LinearAssetOperations = {
    typeId match {
      case MaintenanceRoadAsset.typeId => maintenanceRoadService
      case PavedRoad.typeId => pavedRoadService
      case RoadWidth.typeId => roadWidthService
      case Prohibition.typeId => prohibitionService
      case HazmatTransportProhibition.typeId => hazmatTransportProhibitionService
      case EuropeanRoads.typeId | ExitNumbers.typeId => textValueLinearAssetService
      case CareClass.typeId | CarryingCapacity.typeId | AnimalWarnings.typeId =>  dynamicLinearAssetService
      case HeightLimitInfo.typeId => linearHeightLimitService
      case LengthLimit.typeId => linearLengthLimitService
      case WidthLimitInfo.typeId => linearWidthLimitService
      case TotalWeightLimit.typeId => linearTotalWeightLimitService
      case TrailerTruckWeightLimit.typeId => linearTrailerTruckWeightLimitService
      case AxleWeightLimit.typeId => linearAxleWeightLimitService
      case BogieWeightLimit.typeId => linearBogieWeightLimitService
      case MassTransitLane.typeId => massTransitLaneService
      case NumberOfLanes.typeId => numberOfLanesService
      case DamagedByThaw.typeId => damagedByThawService
      case RoadWorksAsset.typeId => roadWorkService
      case ParkingProhibition.typeId => parkingProhibitionService
      case _ => linearAssetService
    }
  }

  def linearAssetsToApi(typeId: Int, municipalityNumber: Int): Seq[Map[String, Any]] = {
    def isUnknown(asset:PieceWiseLinearAsset) = asset.id == 0

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

  def defaultMultiValueLinearAssetsMap(linearAsset: PieceWiseLinearAsset): Map[String, Any] = {
    Map("id" -> linearAsset.id,
      "points" -> linearAsset.geometry,
      geometryWKTForLinearAssets(linearAsset.geometry),
      "side_code" -> linearAsset.sideCode.value,
      "linkId" -> linearAsset.linkId,
      "startMeasure" -> linearAsset.startMeasure,
      "endMeasure" -> linearAsset.endMeasure,
      latestModificationTime(linearAsset.createdDateTime, linearAsset.modifiedDateTime),
      lastModifiedBy(linearAsset.createdBy, linearAsset.modifiedBy),
      "linkSource" -> linearAsset.linkSource.value
    )
  }

  protected def getMultiValueLinearAssetByMunicipality(typeId: Int, municipalityNumber: Int): Seq[PieceWiseLinearAsset] = {
    def isUnknown(asset: PieceWiseLinearAsset) = asset.id == 0
    getLinearAssetService(typeId).getByMunicipality(typeId, municipalityNumber).filterNot(isUnknown)
  }

  def massTransitLanesToApi( municipalityNumber: Int): Seq[Map[String, Any]] = {
    val massTransitLanes = getMultiValueLinearAssetByMunicipality(MassTransitLane.typeId, municipalityNumber)

    massTransitLanes.map { massTransitLane =>
      val dynamicMultiValueLinearAssetsMap =
        Map("validityPeriods" -> (massTransitLane.value match {
          case Some(DynamicValue(x)) => x.properties.flatMap(_.values.map(_.value).map(_.asInstanceOf[Map[String, Any]]).map(ValidityPeriodValue.fromMap).map {
            a => toTimeDomain(a)
          })
          case _ => None
        }))

      defaultMultiValueLinearAssetsMap(massTransitLane) ++ dynamicMultiValueLinearAssetsMap
    }
  }

  def parkingProhibitionsToApi(municipalityNumber: Int): Seq[Map[String, Any]] = {
    val parkingProhibitions = getMultiValueLinearAssetByMunicipality(ParkingProhibition.typeId, municipalityNumber)

     parkingProhibitions.map { parkingProhibition =>
     val dynamicMultiValueLinearAssetMap = parkingProhibition.value match {
       case Some(DynamicValue(value)) =>
         Map(
           "parking_prohibition" -> value.properties.find(_.publicId ==  "parking_prohibition").get.values.head.value,
           "parking_validity_period" -> value.properties.find(_.publicId == "parking_validity_period").get.values.map(_.value).map { timePeriod =>
             timePeriod.asInstanceOf[Map[String, Any]]
           }.map(ValidityPeriodValue.fromMap).map(a => toTimeDomain(a))
         )
       case _ => Map()
     }

      defaultMultiValueLinearAssetsMap(parkingProhibition) ++ dynamicMultiValueLinearAssetMap
    }
  }

  def damagedByThawToApi( municipalityNumber: Int): Seq[Map[String, Any]] = {
    val roadsDamagedByThaw = getMultiValueLinearAssetByMunicipality(DamagedByThaw.typeId, municipalityNumber)

    roadsDamagedByThaw.map { roadDamagedByThaw =>
      val dynamicMultiValueLinearAssetsMap =
        roadDamagedByThaw.value.map(_.asInstanceOf[DynamicValue]) match {
          case Some(value) => val roadDamagedByThawProps = value.value.properties
            Map("spring_thaw_period" -> roadDamagedByThawProps.find(_.publicId == "spring_thaw_period").map(_.values.map(x => DatePeriodValue.fromMap(x.value.asInstanceOf[Map[String, String]])).map {
              period => Map("startDate" -> period.startDate, "endDate" -> period.endDate )
            }),
              "annual_repetition" -> roadDamagedByThawProps.find(_.publicId == "annual_repetition").map(_.values.map(_.value.toString.toInt)),
              "value" -> roadDamagedByThawProps.find(_.publicId == "kelirikko").map(_.values.map(_.value.toString.toInt)
              ))
          case _ => Map()
        }
      defaultMultiValueLinearAssetsMap(roadDamagedByThaw) ++ dynamicMultiValueLinearAssetsMap
    }
  }

  def roadWorksToApi(municipalityNumber: Int): Seq[Map[String, Any]] = {
    val roadWorks = getMultiValueLinearAssetByMunicipality(RoadWorksAsset.typeId, municipalityNumber)

    roadWorks.map { roadWork =>
      val dynamicMultiValueLinearAssetsMap = roadWork.value.map(_.asInstanceOf[DynamicValue]) match {
        case Some(value) =>
          val roadWorkProps = value.value.properties
          val workId = roadWorkProps.find(_.publicId == "tyon_tunnus") match {
            case Some(property) => property.values.head.value
            case _ => ""
          }

          Map(
            "estimated_duration" -> roadWorkProps.find(_.publicId == "arvioitu_kesto").map(_.values.map(x => DatePeriodValue.fromMap(x.value.asInstanceOf[Map[String, String]])).map {
              period => Map("startDate" -> period.startDate, "endDate" -> period.endDate)
            }),
            "work_id" -> roadWorkProps.find(_.publicId == "tyon_tunnus").map(_.values.map(_.value.toString)
            ))
        case _ => Map()
      }

      defaultMultiValueLinearAssetsMap(roadWork) ++ dynamicMultiValueLinearAssetsMap
    }
  }

  private def bogieWeightLimitsToApi(municipalityNumber: Int): Seq[Map[String, Any]] = {
    val bogieWeightLimits = getMultiValueLinearAssetByMunicipality(BogieWeightLimit.typeId, municipalityNumber)

    bogieWeightLimits.map { bogieWeightLimit =>
      val dynamicMultiValueLinearAssetsMap = bogieWeightLimit.value match {
        case Some(DynamicValue(value)) =>
          value.properties.flatMap { bogieWeightAxel =>
            bogieWeightAxel.publicId match {
              case "bogie_weight_2_axel" =>
                bogieWeightAxel.values.map { v =>
                  "twoAxelValue" -> v.value
                }
              case "bogie_weight_3_axel" =>
                bogieWeightAxel.values.map { v =>
                  "threeAxelValue" -> v.value
                }
              case _ => None
            }
          }
        case _ => Map()
      }

      defaultMultiValueLinearAssetsMap(bogieWeightLimit) ++ dynamicMultiValueLinearAssetsMap
    }
  }

  def carryingCapacitiesToApi(municipalityNumber: Int): Seq[Map[String, Any]] = {
    val carryingCapacities = getMultiValueLinearAssetByMunicipality(CarryingCapacity.typeId, municipalityNumber)

    carryingCapacities.map { carryingCapacity =>
      val dynamicMultiValueLinearAssetsMap = carryingCapacity.value match {
        case Some(DynamicValue(x)) =>
          x.properties.flatMap { multiTypeProperty =>
            multiTypeProperty.publicId match {
              case "routivuuskerroin" =>
                multiTypeProperty.values.map { v =>
                  "frostHeavingFactor" -> v.value
                }
              case "kevatkantavuus" =>
                multiTypeProperty.values.map { v =>
                  "springCarryingCapacity" -> v.value
                }
              case "mittauspaiva" =>
                multiTypeProperty.values.map { v =>
                  "dateOfMeasurement" -> v.value
                }
              case _ => None
            }
          }
        case _ => Map()
      }

      defaultMultiValueLinearAssetsMap(carryingCapacity) ++ dynamicMultiValueLinearAssetsMap
    }
  }

  def linearAssetsToApiWithInformationSource(assets: Seq[PieceWiseLinearAsset]): Seq[Map[String, Any]] = {
    def isUnknown(asset:PieceWiseLinearAsset) = asset.id == 0

    assets.filterNot(isUnknown).map { asset =>
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
        "linkSource" -> asset.linkSource.value,
        "informationSource" -> getInformationSource(asset.informationSource)
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


  def getInformationSource(informationSource: Option[InformationSource]): String = {
    informationSource match{
      case Some(info) => info.value.toString
      case _ => ""
    }
  }

  def lastModifiedBy(createdBy: Option[String], modifiedBy: Option[String]): (String, Boolean) = {

    val autoGeneratedValues = List("dr1conversion", "dr1_conversion", nonFixedUsers(modifiedBy), nonFixedUsers(createdBy), "automatic_correction", "excel_data_migration", "automatic_generation", "vvh_generated", "vvh_modified")

    modifiedBy match {
      case None => "generatedValue" -> false
      case Some(value) if value != null =>
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
      case Some(user) if nonFixedValues.exists(nfv => user.startsWith(nfv)) =>
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
        "railwayCrossingId" -> railwayCrossing.code,
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

  def trWeightLimitationsToApi(weightLimits: Seq[WeightLimit]): Seq[Map[String, Any]] = {
    weightLimits.filterNot(_.floating).map { weightLimit =>
      Map("id" -> weightLimit.id,
        "linkId" -> weightLimit.linkId,
        "point" -> Point(weightLimit.lon, weightLimit.lat),
        geometryWKTForPoints(weightLimit.lon, weightLimit.lat),
        "m_value" -> weightLimit.mValue,
        "value" -> weightLimit.limit,
        latestModificationTime(weightLimit.createdAt, weightLimit.modifiedAt),
        lastModifiedBy(weightLimit.createdBy, weightLimit.modifiedBy),
        "linkSource" -> weightLimit.linkSource.value)
    }
  }

  def trHeightLimitsToApi(heightLimits: Seq[HeightLimit]): Seq[Map[String, Any]] = {
    heightLimits.filterNot(_.floating).map { heightLimit =>
      Map("id" -> heightLimit.id,
        "linkId" -> heightLimit.linkId,
        "point" -> Point(heightLimit.lon, heightLimit.lat),
        geometryWKTForPoints(heightLimit.lon, heightLimit.lat),
        "m_value" -> heightLimit.mValue,
        "value" -> heightLimit.limit,
        latestModificationTime(heightLimit.createdAt, heightLimit.modifiedAt),
        lastModifiedBy(heightLimit.createdBy, heightLimit.modifiedBy),
        "linkSource" -> heightLimit.linkSource.value)
    }
  }

  def trWidthLimitsToApi(widthLimits: Seq[WidthLimit]): Seq[Map[String, Any]] = {
    widthLimits.filterNot(_.floating).map { widthLimit =>
      Map("id" -> widthLimit.id,
        "linkId" -> widthLimit.linkId,
        "point" -> Point(widthLimit.lon, widthLimit.lat),
        geometryWKTForPoints(widthLimit.lon, widthLimit.lat),
        "m_value" -> widthLimit.mValue,
        "value" -> widthLimit.limit,
        "reason" -> widthLimit.reason.value,
        latestModificationTime(widthLimit.createdAt, widthLimit.modifiedAt),
        lastModifiedBy(widthLimit.createdBy, widthLimit.modifiedBy),
        "linkSource" -> widthLimit.linkSource.value)
    }
  }
  def trafficSignsToApi(trafficSigns: Seq[PersistedTrafficSign]): Seq[Map[String, Any]] = {
    trafficSigns.filterNot(_.floating).map{ trafficSign =>
     Map("id" -> trafficSign.id,
          "point" -> Point(trafficSign.lon, trafficSign.lat),
          geometryWKTForPoints(trafficSign.lon, trafficSign.lat),
          "linkId" -> trafficSign.linkId,
          "m_value" -> trafficSign.mValue,
          latestModificationTime(trafficSign.createdAt, trafficSign.modifiedAt),
          lastModifiedBy(trafficSign.createdBy, trafficSign.modifiedBy),
          "linkSource" -> trafficSign.linkSource.value,
          "value" ->trafficSignService.getProperty(trafficSign, "trafficSigns_value").map(_.propertyDisplayValue.getOrElse("")),
          "type" -> TrafficSignType.applyOTHValue(trafficSignService.getProperty(trafficSign, "trafficSigns_type").get.propertyValue.toInt).TRvalue,
          "trafficDirection" -> SideCode.toTrafficDirection(SideCode(trafficSign.validityDirection)).value,
          "additionalInformation" -> trafficSignService.getProperty(trafficSign, "trafficSigns_info").map(_.propertyDisplayValue.getOrElse("")),
          "additionalPanels" -> mapAdditionalPanels(trafficSignService.getAllProperties(trafficSign, "additional_panel").map(_.asInstanceOf[AdditionalPanel]))
     )
    }
  }

  private def mapAdditionalPanels(panels: Seq[AdditionalPanel]): Seq[Map[String, Any]] = {
    panels.map{panel =>
      Map(
        "type" -> TrafficSignType.applyOTHValue(panel.panelType).TRvalue,
        "value" -> panel.panelValue,
        "information" -> panel.panelInfo
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

  //Description of Api entry point to get assets changes by asset type and between two dates
  val getChangesOfAssetsByType =
    (apiOperation[Long]("getChangesOfAssetsByType")
      .parameters(
        queryParam[String]("since").description("Initial date of the interval between two dates to obtain modifications for a particular asset."),
        queryParam[String]("until").description("The end date of the interval between two dates to obtain modifications for an asset.").optional,
        queryParam[String]("withAdjust").description("With the field withAdjust, we allow or not the presense of records modified by vvh_generated and not modified yet on the response. The value is True by default.").optional,
        pathParam[String]("assetType").description("Asset type name to get the changes")
      )
      tags "Integration API (Kalpa API)"
      summary "List all changes per assets type between two specific dates."
      authorizations "Contact your service provider for more information"
      description "Example URL: api/integration/changes/bogie_weight_limits?since=2018-04-12T04:00Z&until=2018-04-16T15:00Z"
      )

  get("/changes/:assetType", operation(getChangesOfAssetsByType)) {
    contentType = formats("json")
     val since = DateTime.parse(params.get("since").getOrElse(halt(BadRequest("Missing mandatory 'since' parameter"))))
     val until = params.get("until") match {
       case Some(dateValue) => DateTime.parse(dateValue)
       case _ => DateTime.now()
     }

    val withAdjust = params.get("withAdjust") match{
      case Some(value)=> value.toBoolean
      case _ => true
    }

     val assetType = params("assetType")
     assetType match {
       case "speed_limits" => speedLimitsChangesToApi(since, speedLimitService.getChanged(since, until, withAdjust))
       case _ => BadRequest("Invalid asset type")
     }
  }

  def getTrafficSignGroup(groupName: String):TrafficSignTypeGroup = {
    groupName match {
      case "speed_limits" => TrafficSignTypeGroup.SpeedLimits
      case "pedestrian_crossings" => TrafficSignTypeGroup.RegulatorySigns
      case "maximum_restrictions" => TrafficSignTypeGroup.MaximumRestrictions
      case "warnings" => TrafficSignTypeGroup.GeneralWarningSigns
      case "prohibitory_signs" => TrafficSignTypeGroup.ProhibitionsAndRestrictions
      case "mandatory_signs" => TrafficSignTypeGroup.MandatorySigns
      case "regulatory_signs" => TrafficSignTypeGroup.RegulatorySigns
      case "additional_panels" => TrafficSignTypeGroup.AdditionalPanels
      case _ => TrafficSignTypeGroup.Unknown
    }
  }

  //Description of Api entry point to get all assets by asset type and municipality
  val getAssetsByTypeMunicipality =
    (apiOperation[Long]("getAssetsByTypeMunicipality")
      .parameters(
        queryParam[Int]("municipality").description("Municipality Code where we will execute the search by specific asset type"),
        pathParam[String]("assetType").description("Asset type name to get all assets")
      )
      tags "Integration API (Kalpa API)"
      summary "List all valid assets on a specific municipality."
      authorizations "Contact your service provider for more information"
      description "Example URL: /api/integration/animal_warnings?municipality=749"
      )

  get("/:assetType", operation(getAssetsByTypeMunicipality)) {
    contentType = formats("json")+ "; charset=utf-8"
    params.get("municipality").map { municipality =>
      val municipalityNumber = municipality.toInt
      val assetType = params("assetType")
      assetType match {
        case "mass_transit_stops" => toGeoJSON(getMassTransitStopsByMunicipality(municipalityNumber))
        case "speed_limits" => speedLimitsToApi(speedLimitService.get(municipalityNumber))
        case "total_weight_limits" => linearAssetsToApi(TotalWeightLimit.typeId, municipalityNumber)
        case "trailer_truck_weight_limits" => linearAssetsToApi(TrailerTruckWeightLimit.typeId, municipalityNumber)
        case "axle_weight_limits" => linearAssetsToApi(AxleWeightLimit.typeId, municipalityNumber)
        case "bogie_weight_limits" => bogieWeightLimitsToApi(municipalityNumber)
        case "height_limits" => linearAssetsToApi(HeightLimitInfo.typeId, municipalityNumber)
        case "length_limits" => linearAssetsToApi(LengthLimit.typeId, municipalityNumber)
        case "width_limits" => linearAssetsToApi(WidthLimitInfo.typeId, municipalityNumber)
        case "obstacles" => obstaclesToApi(obstacleService.getByMunicipality(municipalityNumber))
        case "traffic_lights" => trafficLightsToApi(trafficLightService.getByMunicipality(municipalityNumber))
        case "pedestrian_crossings" => pedestrianCrossingsToApi(pedestrianCrossingService.getByMunicipality(municipalityNumber))
        case "directional_traffic_signs" => directionalTrafficSignsToApi(directionalTrafficSignService.getByMunicipality(municipalityNumber))
        case "railway_crossings" => railwayCrossingsToApi(railwayCrossingService.getByMunicipality(municipalityNumber))
        case "vehicle_prohibitions" => linearAssetsToApi(Prohibition.typeId, municipalityNumber)
        case "hazardous_material_transport_prohibitions" => linearAssetsToApi(HazmatTransportProhibition.typeId, municipalityNumber)
        case "number_of_lanes" => linearAssetsToApi(NumberOfLanes.typeId, municipalityNumber)
        case "mass_transit_lanes" => massTransitLanesToApi(municipalityNumber)
        case "roads_affected_by_thawing" => damagedByThawToApi(municipalityNumber)
        case "widths" => linearAssetsToApiWithInformationSource(roadWidthService.getByMunicipality(RoadWidth.typeId, municipalityNumber))
        case "paved_roads" => linearAssetsToApiWithInformationSource(pavedRoadService.getByMunicipality(PavedRoad.typeId, municipalityNumber))
        case "lit_roads" => linearAssetsToApi(LitRoad.typeId, municipalityNumber)
        case "speed_limits_during_winter" => linearAssetsToApi(WinterSpeedLimit.typeId, municipalityNumber)
        case "traffic_volumes" => linearAssetsToApi(TrafficVolume.typeId, municipalityNumber)
        case "european_roads" => linearAssetsToApi(EuropeanRoads.typeId, municipalityNumber)
        case "exit_numbers" => linearAssetsToApi(ExitNumbers.typeId, municipalityNumber)
        case "road_link_properties" => roadLinkPropertiesToApi(roadAddressService.roadLinkWithRoadAddress(roadLinkService.getRoadLinksAndComplementaryLinksFromVVHByMunicipality(municipalityNumber)))
        case "manoeuvres" => manouvresToApi(manoeuvreService.getByMunicipality(municipalityNumber))
        case "service_points" => servicePointsToApi(servicePointService.getByMunicipality(municipalityNumber))
        case "road_nodes" => roadNodesToApi(roadLinkService.getRoadNodesFromVVHByMunicipality(municipalityNumber))
        case "tr_total_weight_limits" => trWeightLimitationsToApi(weightLimitService.getByMunicipality(municipalityNumber))
        case "tr_trailer_truck_weight_limits" => trWeightLimitationsToApi(trailerTruckWeightLimitService.getByMunicipality(municipalityNumber))
        case "tr_axle_weight_limits" => trWeightLimitationsToApi(axleWeightLimitService.getByMunicipality(municipalityNumber))
        case "tr_bogie_weight_limits" => trWeightLimitationsToApi(bogieWeightLimitService.getByMunicipality(municipalityNumber))
        case "tr_height_limits" => trHeightLimitsToApi(heightLimitService.getByMunicipality(municipalityNumber))
        case "tr_width_limits" => trWidthLimitsToApi(widthLimitService.getByMunicipality(municipalityNumber))
        case "carrying_capacity" => carryingCapacitiesToApi(municipalityNumber)
        case "care_classes" =>  linearAssetsToApi(CareClass.typeId, municipalityNumber)
        case "traffic_signs" => trafficSignsToApi(trafficSignService.getByMunicipality(municipalityNumber))
        case "animal_warnings" => linearAssetsToApi(AnimalWarnings.typeId, municipalityNumber)
        case "road_works_asset" => roadWorksToApi(municipalityNumber)
        case "parking_prohibitions" => parkingProhibitionsToApi(municipalityNumber)
        case _ => BadRequest("Invalid asset type")
      }
    } getOrElse {
      BadRequest("Missing mandatory 'municipality' parameter")
    }
  }

  get("/traffic_signs/:group_name"){
    contentType = formats("json")
    params.get("municipality").map { municipality =>
      val groupName = getTrafficSignGroup(params("group_name"))
      groupName match {
        case TrafficSignTypeGroup.Unknown => BadRequest("Invalid group type")
        case _ => trafficSignsToApi(trafficSignService.getByMunicipalityAndGroup(municipality.toInt, groupName))
      }
    }
  }
}

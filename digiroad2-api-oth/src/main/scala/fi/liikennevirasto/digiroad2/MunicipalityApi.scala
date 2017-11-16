package fi.liikennevirasto.digiroad2

import java.text.SimpleDateFormat
import fi.liikennevirasto.digiroad2.Digiroad2Context._
import fi.liikennevirasto.digiroad2.asset.Asset.DateTimePropertyFormat
import fi.liikennevirasto.digiroad2.asset.{AssetTypeInfo, Manoeuvres, _}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.pointasset.oracle._
import org.joda.time.DateTime
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.json4s._

case class NewAssetValues(linkId: Long, startMeasure: Double, endMeasure: Option[Double], properties: Seq[AssetProperties], sideCode: Option[Int], geometryTimestamp: Option[Long])
case class NewManoeuvreValues(linkId: Long, startMeasure: Double, endMeasure: Option[Double], properties: Seq[ManoeuvreProperties], sideCode: Option[Int], geometryTimestamp: Option[Long])

class MunicipalityApi(val onOffLinearAssetService: OnOffLinearAssetService,
                      val roadLinkService: RoadLinkService,
                      val linearAssetService: LinearAssetService,
                      val speedLimitService: SpeedLimitService,
                      val pavingService: PavingService,
                      val roadWidthService: RoadWidthService,
                      val manoeuvreService: ManoeuvreService,
                      val assetService: AssetService
                     ) extends ScalatraServlet with JacksonJsonSupport with AuthenticationSupport {

  override def baseAuth: String = "municipality."
  override val realm: String = "Municipality API"

  val Maximum7Restrictions = Set(TotalWeightLimit.typeId, TrailerTruckWeightLimit.typeId, AxleWeightLimit.typeId, BogieWeightLimit.typeId,
    HeightLimit.typeId, LengthLimit.typeId, WidthLimit.typeId)

  case object DateTimeSerializer extends CustomSerializer[DateTime](format => ( {
    case _ => throw new NotImplementedError("DateTime deserialization")
  }, {
    case d: DateTime => JString(d.toString(DateTimePropertyFormat))
  }))

  case object SideCodeSerializer extends CustomSerializer[SideCode](format => ( {
    null
  }, {
    case s: SideCode => JInt(s.value)
  }))

  case object LinkGeomSourceSerializer extends CustomSerializer[LinkGeomSource](format => ({
    case JInt(lg) => LinkGeomSource.apply(lg.toInt)
  }, {
    case lg: LinkGeomSource => JInt(lg.value)
  }))

  case object TrafficDirectionSerializer extends CustomSerializer[TrafficDirection](format => ( {
    case JString(direction) => TrafficDirection(direction)
  }, {
    case t: TrafficDirection => JString(t.toString)
  }))

  protected implicit val jsonFormats: Formats = DefaultFormats + DateTimeSerializer + LinkGeomSourceSerializer + SideCodeSerializer + TrafficDirectionSerializer

  case class AssetTimeStamps(created: Modification, modified: Modification) extends TimeStamps

  before() {
    basicAuth
  }

  private def verifyLinearServiceToUse(typeId: Int): LinearAssetOperations = {
    typeId match {
      case LitRoad.typeId | MassTransitLane.typeId  => onOffLinearAssetService
      case PavedRoad.typeId => pavingService
      case RoadWidth.typeId => roadWidthService
      case _ => linearAssetService
    }
  }

  private def verifyPointServiceToUse(typeId: Int): PointAssetOperations = {
    typeId match {
      case Obstacles.typeId  => obstacleService
      case PedestrianCrossings.typeId   => pedestrianCrossingService
      case RailwayCrossings.typeId  => railwayCrossingService
      case TrafficLights.typeId  => trafficLightService
    }
  }

  private def extractNewAssets(typeId: Int, value: JValue) = {
    AssetTypeInfo.apply(typeId).geometryType match {
      case "linear" => value.extractOpt[Seq[NewAssetValues]].getOrElse(Nil).map(x => NewLinearAsset(x.linkId, x.startMeasure, x.endMeasure.getOrElse(0), NumericValue(x.properties.map(_.value).head.toInt), x.sideCode.getOrElse(SideCode.BothDirections.value) , x.geometryTimestamp.getOrElse(vvhClient.roadLinkData.createVVHTimeStamp()), None))
      case _ => Seq.empty[NewLinearAsset]
    }
  }

  private def extractLinearAssets(typeId: Int, value: JValue) = {
    value.extractOpt[NewAssetValues].map { v =>
      NewLinearAsset(v.linkId, v.startMeasure, v.endMeasure.getOrElse(0), NumericValue(v.properties.map(_.value).head.toInt), v.sideCode.getOrElse(SideCode.BothDirections.value), v.geometryTimestamp.getOrElse(vvhClient.roadLinkData.createVVHTimeStamp()), None)
    }.get
  }

  def expireLinearAsset(assetTypeId: Int, assetId: Long, username: String, expired: Boolean): String = {
    if (linearAssetService.getPersistedAssetsByIds(assetTypeId, Set(assetId.toLong)).isEmpty)
      halt(UnprocessableEntity("Asset not found."))

    linearAssetService.expireAsset(assetTypeId, assetId, user.username, expired = true).getOrElse("").toString
  }

  def expirePointAsset(assetTypeId: Int, assetId: Long, username: String): Long = {
    getPointAssetById(assetTypeId, assetId)

    val service = verifyPointServiceToUse(assetTypeId)
    service.expire(assetId, user.username) match {
      case 1 => assetId
      case _ => halt(BadRequest("Asset not deleted"))
    }
  }

  def getLinearAssetsByMunicipality(municipalityCode: Int, assetTypeId: Int): Seq[PersistedLinearAsset] ={
    val usedService = verifyLinearServiceToUse(assetTypeId)
    usedService.getAssetsByMunicipality(assetTypeId, municipalityCode).filterNot(_.id == 0).filterNot(_.expired)
  }

  def getPointAssetsByMunicipality(municipalityCode: Int, assetTypeId: Int): Seq[PersistedPointAsset] ={
    val service = verifyPointServiceToUse(assetTypeId)
    service.getByMunicipality(municipalityCode)
  }

  def getPointAssetById(assetTypeId: Int, assetId: Long): PersistedPointAsset ={
    val service = verifyPointServiceToUse(assetTypeId)
    service.getById(assetId) match {
      case Some(asset) => asset.asInstanceOf[PersistedPointAsset]
      case _ => halt(BadRequest("Asset not found."))
    }
  }

  def getLinearAssets(assetTypeId: Int, assetId: Int): Seq[PersistedLinearAsset] ={
    val usedService = verifyLinearServiceToUse(assetTypeId)
    val linearAssets = usedService.getPersistedAssetsByIds(assetTypeId, Set(assetId.toLong)).filterNot(_.expired)

    linearAssets.map { linearAsset =>
      val roadLink = roadLinkService.getRoadLinkAndComplementaryFromVVH(linearAsset.linkId).getOrElse(halt(NotFound("Roadlink not found")))
      linearAsset.copy(municipalityCode = Some(roadLink.municipalityCode))
    }

    if (linearAssets.isEmpty)
      halt(BadRequest("Asset not found."))

    linearAssets
  }

  def getSpeedLimitAssetsByMunicipality(municipalityCode: Int): Seq[SpeedLimit] ={
    speedLimitService.get(municipalityCode).filterNot(asset => asset.id == 0 || asset.expired)
  }

  def getSpeedLimitAssets(assetId: Int): Seq[SpeedLimit] = {
    val speedLimits = speedLimitService.get(Seq(assetId.toLong)).filterNot(_.expired)

    if (speedLimits.isEmpty)
      halt(BadRequest("Asset not found."))

    speedLimits
  }

  def getManoeuvreAssets(assetId: Int): Seq[Manoeuvre] = {
    manoeuvreService.find(assetId) match {
      case Some(value) => Seq(value)
      case _ =>  halt(BadRequest("Asset not found."))
    }
  }

  def updateLinearAsset(assetTypeId: Int, assetId: Int, parsedBody: JValue, linkId: Long): PersistedLinearAsset = {
    val usedService = verifyLinearServiceToUse(assetTypeId)
    val oldAsset = usedService.getPersistedAssetsByIds(assetTypeId, Set(assetId.toLong)).filterNot(_.expired).headOption.
      getOrElse(halt(UnprocessableEntity("Asset not found.")))
    val newAsset = extractLinearAssets(assetTypeId, parsedBody)
    validateMeasuresOnLinears(Set(newAsset.startMeasure, newAsset.endMeasure), assetTypeId, linkId)
    validateSideCodes(Seq(newAsset))
    validateTimeststamp(newAsset.vvhTimeStamp, oldAsset.vvhTimeStamp)

    val updatedId = usedService.updateWithNewMeasures(Seq(oldAsset.id), newAsset.value, user.username, Some(Measures(newAsset.startMeasure, newAsset.endMeasure)), Some(newAsset.vvhTimeStamp), Some(newAsset.sideCode))
    usedService.getPersistedAssetsByIds(assetTypeId, updatedId.toSet).filterNot(_.expired).headOption match {
      case Some(linearAssetUpdated) => linearAssetUpdated
      case _ => halt(BadRequest("Asset not Updated"))
    }
  }

  def updatePointAssets(parsedBody: JValue, typeId: Int, assetId: Int): PersistedPointAsset = {
    val service = verifyPointServiceToUse(typeId)
    val asset = typeId match {
      case Obstacles.typeId =>
        parsedBody.extractOpt[NewAssetValues].map(x =>IncomingObstacleAsset( x.linkId, x.startMeasure.toLong, x.properties.map(_.value).head.toInt))
      case PedestrianCrossings.typeId =>
        parsedBody.extractOpt[NewAssetValues].map(x =>IncomingPedestrianCrossingAsset( x.linkId, x.startMeasure.toLong))
      case RailwayCrossings.typeId =>
        parsedBody.extractOpt[NewAssetValues].map(x =>
          IncomingRailwayCrossingtAsset(
            x.linkId,
            x.startMeasure.toLong,
            x.properties.find(_.name == "safetyEquipment").map { safetyEquipment => safetyEquipment.value.toInt }.get,
            x.properties.find(_.name == "name").map { name => name.value }
          ))
      case TrafficLights.typeId =>
        parsedBody.extractOpt[NewAssetValues].map(x =>IncomingPedestrianCrossingAsset( x.linkId, x.startMeasure.toLong))
    }

    asset.map { value =>
      validateMeasures(Set(value.mValue), typeId, value.linkId)
      roadLinkService.getRoadLinkAndComplementaryFromVVH(value.linkId) match {
        case Some(link) => service.toIncomingAsset(value, link).map {
          pointAsset =>
            service.update(assetId, pointAsset, link.geometry, link.municipalityCode, user.username, link.linkSource)
        }
        case None => halt(NotFound(s"Roadlink with ${value.linkId} does not exist"))
      }
    }
    getPointAssetById(typeId, assetId)
  }

  def updateSpeedLimitAsset(assetId: Long, parsedBody: JValue, linkId: Int): SpeedLimit = {
    val oldAsset = speedLimitService.getSpeedLimitAssetsByIds(Set(assetId)).filterNot(_.expired).headOption
      .getOrElse(halt(UnprocessableEntity("Asset not found.")))

    val newAsset = extractLinearAssets(SpeedLimitAsset.typeId, parsedBody)
    validateMeasuresOnLinears(Set(newAsset.startMeasure, newAsset.endMeasure), SpeedLimitAsset.typeId, linkId)
    validateSideCodes(Seq(newAsset))
    validateTimeststamp(newAsset.vvhTimeStamp, oldAsset.vvhTimeStamp)

    val updatedId = speedLimitService.update(assetId, Seq(newAsset), user.username)
    speedLimitService.getSpeedLimitAssetsByIds(updatedId.toSet).filterNot(_.expired).headOption match {
      case Some(speedLimitUpdated) => speedLimitUpdated
      case _ => halt(BadRequest("Asset not Updated"))
    }
  }

  def updateManoeuvreAssets(assetId: Long, parsedBody: JValue, linkId: Int): Seq[Manoeuvre] = {
    val oldAsset = manoeuvreService.find(assetId).getOrElse(halt(UnprocessableEntity("Asset not found.")))
    parsedBody.extractOpt[NewManoeuvreValues].foreach { manoeuvre =>

      validateMeasuresOnLinears(Set(manoeuvre.startMeasure, manoeuvre.endMeasure.get), Manoeuvres.typeId, linkId)

      validateTimeststamp(manoeuvre.geometryTimestamp.getOrElse(halt(NotFound("geometryTimestamp not found"))), convertStringToDate(oldAsset.modifiedDateTime))

      if (manoeuvre.properties.map(_.name).contains("sourceLinkId") || manoeuvre.properties.map(_.name).contains("elements") || manoeuvre.properties.map(_.name).contains("destLinkId"))
        halt(Found("Not allow update sourceLinkId, destLinkId or elements"))

      if (!manoeuvre.properties.map(_.name).contains("exceptions") && !manoeuvre.properties.map(_.name).contains("validityPeriods") && !manoeuvre.properties.map(_.name).contains("additionalInfo"))
        halt(NotFound("Not found properties to be updated"))

      val validityPeriods = toValidityPeriod(manoeuvre.properties.find(_.name == "validityPeriods"))
      val exceptions = manoeuvre.properties.find(_.name == "exceptions").map(_.value.asInstanceOf[List[BigInt]].map(_.toInt))
      val additionalInfo = manoeuvre.properties.find(_.name == "additionalInfo").map(_.value.toString)

      val manoeuvreUpdates = ManoeuvreUpdates(validityPeriods, exceptions, additionalInfo)
      manoeuvreService.updateManoeuvre(user.username, assetId, manoeuvreUpdates, Some(new DateTime(manoeuvre.geometryTimestamp.get)))
    }
    manoeuvreService.find(assetId).toSeq
  }

  def createLinearAssets(assetTypeId: Int, parsedBody: JValue, linkId: Seq[Long]): Seq[PersistedLinearAsset] ={
    val usedService = verifyLinearServiceToUse(assetTypeId)
    val newLinearAssets = extractNewAssets(assetTypeId, parsedBody)
    validateSideCodes(newLinearAssets)
    newLinearAssets.foreach{
      newAsset => validateMeasuresOnLinears(Set(newAsset.startMeasure, newAsset.endMeasure), assetTypeId, newAsset.linkId)
    }
    val assetsIds = usedService.create(newLinearAssets, assetTypeId, user.username)
    Some(usedService.getPersistedAssetsByIds(assetTypeId, assetsIds.toSet).filterNot(_.expired)) match {
      case Some(value) => value
      case _ => halt(NotFound("Asset not found"))
    }
  }

  def createSpeedLimitAssets(assetTypeId: Int, parsedBody: JValue): Seq[SpeedLimit] ={
    val newSpeedLimitAssets = extractNewAssets(assetTypeId, parsedBody)
    validateSideCodes(newSpeedLimitAssets)
    newSpeedLimitAssets.foreach{
      newAsset => validateMeasuresOnLinears(Set(newAsset.startMeasure, newAsset.endMeasure), assetTypeId, newAsset.linkId)
    }
    val assetsIds = speedLimitService.create(newSpeedLimitAssets, assetTypeId, user.username, 0, _ => Unit)
    speedLimitService.getSpeedLimitAssetsByIds(assetsIds.toSet).filterNot(_.expired)
  }

  def createManoeuvreAssets(assetTypeId: Int, parsedBody: JValue): Seq[Manoeuvre] = {
    val manoeuvreIds = parsedBody.extractOpt[Seq[NewManoeuvreValues]].map { x =>
      x.foreach { newAsset =>
        validateMeasuresOnLinears(Set(newAsset.startMeasure, newAsset.endMeasure.get), assetTypeId, newAsset.linkId)
      }

      x.map {asset =>
        manoeuvreService.createManoeuvre(user.username,
          NewManoeuvre(
            toValidityPeriod(asset.properties.find(_.name == "validityPeriods")).getOrElse(Seq()).toSet,
            asset.properties.find(_.name == "exceptions").map(_.value.asInstanceOf[List[BigInt]].map(_.toInt)).getOrElse(Seq()),
            asset.properties.find(_.name == "additionalInfo").map(_.value.toString),
            Seq(asset.properties.find(_.name == "sourceLinkId").map(_.value.asInstanceOf[BigInt].toLong).getOrElse(halt(NotFound("sourceLinkId not found")))) ++
            (asset.properties.find(_.name == "elements").map(_.value) match {
              case Some(value) => value.asInstanceOf[Seq[BigInt]].map(_.toLong)
              case _ => Seq() }) ++
            Seq(asset.properties.find(_.name == "destLinkId").map(_.value.asInstanceOf[BigInt].toLong).getOrElse(halt(NotFound("destLinkId not found"))))
          )
        )
      }
    }.get
    manoeuvreIds.flatMap(manoeuvre => manoeuvreService.find(manoeuvre))
  }

  def toValidityPeriod(validityOpt: Option[ManoeuvreProperties]): Option[Set[ValidityPeriod]] = {
    validityOpt match {
      case Some(validityPeriod) => Some(validityPeriod.value.asInstanceOf[List[Map[String, Any]]].map { a =>
        ValidityPeriod(a.find(a => a._1 == "startHour").map(_._2).getOrElse(halt(NotFound("startHour not found"))).asInstanceOf[BigInt].toInt,
          a.find(a => a._1 == "endHour").map(_._2).getOrElse(halt(NotFound("endHour not found"))).asInstanceOf[BigInt].toInt,
          ValidityPeriodDayOfWeek(a.find(a => a._1 == "days").map(_._2).getOrElse(halt(NotFound("days not found"))).asInstanceOf[String]),
          a.find(a => a._1 == "startMinute").map(_._2).getOrElse(halt(NotFound("startMinute not found"))).asInstanceOf[BigInt].toInt,
          a.find(a => a._1 == "endMinute").map(_._2).getOrElse(halt(NotFound("endMinute not found"))).asInstanceOf[BigInt].toInt)
      }.toSet)
      case _ => None
    }
  }

  def createPointAssets(assets: JValue, typeId: Int): Seq[PersistedPointAsset] = {

    val service = verifyPointServiceToUse(typeId)
    val assets = typeId match {
      case Obstacles.typeId =>
        parsedBody.extractOpt[Seq[NewAssetValues]].getOrElse(Nil).map(x =>IncomingObstacleAsset( x.linkId, x.startMeasure.toLong, x.properties.map(_.value).head.toInt))
      case PedestrianCrossings.typeId =>
        parsedBody.extractOpt[Seq[NewAssetValues]].getOrElse(Nil).map(x =>IncomingPedestrianCrossingAsset( x.linkId, x.startMeasure.toLong))
      case RailwayCrossings.typeId =>
        parsedBody.extractOpt[Seq[NewAssetValues]].getOrElse(Nil).map(x =>
          IncomingRailwayCrossingtAsset(
            x.linkId,
            x.startMeasure.toLong,
              x.properties.find(_.name == "safetyEquipment").map { safetyEquipment => safetyEquipment.value.toInt }.get,
              x.properties.find(_.name == "name").map { name => name.value }
          ))
      case TrafficLights.typeId =>
        parsedBody.extractOpt[Seq[NewAssetValues]].getOrElse(Nil).map(x =>IncomingPedestrianCrossingAsset( x.linkId, x.startMeasure.toLong))
    }

    val links = roadLinkService.getRoadLinksAndComplementariesFromVVH(assets.map(_.linkId).toSet)
    val insertedAssets = assets.foldLeft(Seq.empty[Long]){ (result, asset) =>
      links.find(_.linkId == asset.linkId) match {
        case Some(link) =>
          service.toIncomingAsset(asset, link) match {
            case Some(pointAsset) => result ++ Seq(service.create(pointAsset, user.username, link))
            case _ => result
          }
        case _ => result
      }
    }
    service.getPersistedAssetsByIds(insertedAssets.toSet)
  }

  def extractPointProperties(pointAsset: PersistedPointAsset, typeId: Int): Any = {
    typeId match {
      case Obstacles.typeId  => Seq(Map("value" ->  pointAsset.asInstanceOf[Obstacle].obstacleType, "name" -> getAssetName(typeId)))
      case PedestrianCrossings.typeId  => Seq(Map("value" -> "1" , "name" -> getAssetName(typeId)))
      case RailwayCrossings.typeId  => Seq(
        Map( "name" -> "name",
          "value" ->  pointAsset.asInstanceOf[RailwayCrossing].name
        ),
        Map("name" -> "safetyEquipment",
          "value" -> pointAsset.asInstanceOf[RailwayCrossing].safetyEquipment)
      )
      case TrafficLights.typeId  => Seq(Map("value" -> "1" , "name" -> getAssetName(typeId)))
    }
  }

  def extractManoeuvreProperties(manoeuvre: Manoeuvre): Any = {
      val exceptions = if (manoeuvre.exceptions.nonEmpty) {
        Seq(Map("name" -> "exceptions", "value" -> manoeuvre.exceptions))
      } else Seq()

      val additionalInfo = if (manoeuvre.additionalInfo != null) {
        Seq(Map("name" -> "additionalInfo", "value" -> manoeuvre.additionalInfo))
      } else Seq()

      val validityPeriods = if (manoeuvre.validityPeriods.nonEmpty) {
        Seq(Map("name" -> "validityPeriods", "value" -> manoeuvre.validityPeriods.map { validity =>
          Map("startHour" -> validity.startHour,
            "endHour" -> validity.endHour,
            "days" -> validity.days.toString,
            "startMinute" -> validity.startMinute,
            "endMinute" -> validity.endMinute)
        }))
      } else Seq()

      val optElements = manoeuvre.elements.filter(_.elementType == ElementTypes.IntermediateElement).map(_.sourceLinkId)

      val elementsLinkId = if (optElements.nonEmpty) {
        Set(Map("name" -> "elements", "value" -> optElements))
      } else Seq()

      Seq(Map("name" -> "sourceLinkId", "value" -> manoeuvre.elements.find(_.elementType == ElementTypes.FirstElement).map(_.sourceLinkId)
      ),
        Map("name" -> "destLinkId", "value" -> manoeuvre.elements.find(_.elementType == ElementTypes.LastElement).map(_.sourceLinkId)
        )) ++ exceptions ++ additionalInfo ++ validityPeriods ++ elementsLinkId
  }

  def extractModifiedAt(pointAsset: PersistedPointAsset, typeId: Int): Any = {
    typeId match {
      case Obstacles.typeId  => pointAsset.asInstanceOf[Obstacle].modifiedAt.orElse( pointAsset.asInstanceOf[Obstacle].createdAt).map(DateTimePropertyFormat.print).getOrElse("")
      case PedestrianCrossings.typeId  =>  pointAsset.asInstanceOf[PedestrianCrossing].modifiedAt.orElse( pointAsset.asInstanceOf[PedestrianCrossing].createdAt).map(DateTimePropertyFormat.print).getOrElse("")
      case RailwayCrossings.typeId  =>  pointAsset.asInstanceOf[RailwayCrossing].modifiedAt.orElse( pointAsset.asInstanceOf[RailwayCrossing].createdAt).map(DateTimePropertyFormat.print).getOrElse("")
      case TrafficLights.typeId  =>  pointAsset.asInstanceOf[TrafficLight].modifiedAt.orElse( pointAsset.asInstanceOf[TrafficLight].createdAt).map(DateTimePropertyFormat.print).getOrElse("")
    }
  }

  def extractCreatedAt(pointAsset: PersistedPointAsset, typeId: Int): Any = {
    typeId match {
      case Obstacles.typeId  => pointAsset.asInstanceOf[Obstacle].createdAt.map(DateTimePropertyFormat.print).getOrElse("")
      case PedestrianCrossings.typeId  => pointAsset.asInstanceOf[PedestrianCrossing].createdAt.map(DateTimePropertyFormat.print).getOrElse("")
      case RailwayCrossings.typeId  => pointAsset.asInstanceOf[RailwayCrossing].createdAt.map(DateTimePropertyFormat.print).getOrElse("")
      case TrafficLights.typeId  => pointAsset.asInstanceOf[TrafficLight].createdAt.map(DateTimePropertyFormat.print).getOrElse("")
    }
  }

  def pointAssetToApi(pointAssets: PersistedPointAsset, typeId: Int) : Map[String, Any] = {
    Map("id" -> pointAssets.id,
      "properties" -> extractPointProperties(pointAssets, typeId),
      "linkId" -> pointAssets.linkId,
      "startMeasure" -> pointAssets.mValue,
      "modifiedAt" -> extractModifiedAt(pointAssets, typeId),
      "createdAt" -> extractCreatedAt(pointAssets, typeId),
      "geometryTimestamp" -> pointAssets.vvhTimeStamp,
      "municipalityCode" -> pointAssets.municipalityCode
    )
  }

  def pointAssetsToApi(pointAssets: Seq[PersistedPointAsset], typeId: Int) : Seq[Map[String, Any]] = {
    pointAssets.map { asset =>
      pointAssetToApi(asset, typeId) : Map[String, Any]
    }
  }

  def linearAssetToApi(linearAsset: PersistedLinearAsset): Map[String, Any] = {
    Map("id" -> linearAsset.id,
      "properties" -> Seq(Map("value" -> linearAsset.value.map(_.toJson), "name" -> getAssetName(linearAsset.typeId))),
      "linkId" -> linearAsset.linkId,
      "startMeasure" -> linearAsset.startMeasure,
      "endMeasure" -> linearAsset.endMeasure,
      "sideCode" -> linearAsset.sideCode,
      "modifiedAt" -> linearAsset.modifiedDateTime,
      "createdAt" -> linearAsset.createdDateTime,
      "geometryTimestamp" -> linearAsset.vvhTimeStamp,
      "municipalityCode" -> linearAsset.municipalityCode
    )
  }

  def linearAssetsToApi(linearAssets: Seq[PersistedLinearAsset]): Seq[Map[String, Any]] = {
    linearAssets.map { linearAsset =>
      linearAssetToApi(linearAsset)
    }
  }

  def speedLimitAssetToApi(speedLimit: SpeedLimit): Map[String, Any] = {
    Map("id" -> speedLimit.id,
      "properties" -> Seq(Map("value" -> speedLimit.value.map(_.toJson), "name" -> getAssetName( SpeedLimitAsset.typeId))),
      "linkId" -> speedLimit.linkId,
      "startMeasure" -> speedLimit.startMeasure,
      "endMeasure" -> speedLimit.endMeasure,
      "sideCode" -> speedLimit.sideCode,
      "modifiedAt" -> speedLimit.modifiedDateTime,
      "createdAt" -> speedLimit.createdDateTime,
      "geometryTimestamp" -> speedLimit.vvhTimeStamp,
      "municipalityCode" -> speedLimit.municipalityCode
    )
  }

  def speedLimitAssetsToApi(speedLimits: Seq[SpeedLimit]): Seq[Map[String, Any]] = {
    speedLimits.map { speedLimit =>
      speedLimitAssetToApi(speedLimit)
    }
  }

  private def manoeuvreAssetToApi(manoeuvre: Manoeuvre, roadLinks: Seq[RoadLink]): Map[String, Any] = {
    val geomtry = roadLinks.find(_.linkId == manoeuvre.elements.find(_.elementType == ElementTypes.LastElement).map(_.sourceLinkId))
      .getOrElse(halt(NotFound("Roadlink not found"))).geometry

    val municipalityCode = roadLinks.find(_.linkId == manoeuvre.elements.find(_.elementType == ElementTypes.FirstElement).map(_.sourceLinkId))
      .getOrElse(halt(NotFound("Roadlink not found"))).municipalityCode
    Map("id" -> manoeuvre.id,
      "properties" -> extractManoeuvreProperties(manoeuvre),
      "linkId" -> manoeuvre.elements.find(_.elementType == ElementTypes.FirstElement).map(_.sourceLinkId),
      "startMeasure" -> 0,
      "endMeasure" -> GeometryUtils.geometryLength(geomtry),
      "modifiedAt" -> manoeuvre.modifiedDateTime,
      "geometryTimestamp" -> convertStringToDate(manoeuvre.modifiedDateTime),
      "municipalityCode" -> municipalityCode
    )
  }

  private def manoeuvreAssetsToApi(manoeuvres: Seq[Manoeuvre]): Seq[Map[String, Any]] = {

    manoeuvres.map { manoeuvre =>
      manoeuvreAssetToApi(manoeuvre, getRoadLinksForManoeuvres(manoeuvres))
    }
  }

  def getAssetTypeId(assetType: String): Int = {
    assetType match {
      case "lighting" => LitRoad.typeId
      case "obstacle" => Obstacles.typeId
      case "pedestrian_crossing"   =>  PedestrianCrossings.typeId
      case "railway_crossing"  => RailwayCrossings.typeId
      case "traffic_light"  =>  TrafficLights.typeId
      case "speed_limit" => SpeedLimitAsset.typeId
      case "total_weight_limit" => TotalWeightLimit.typeId
      case "trailer_truck_weight_limit" => TrailerTruckWeightLimit.typeId
      case "axle_weight_limit" => AxleWeightLimit.typeId
      case "bogie_weight_limit" => BogieWeightLimit.typeId
      case "height_limit" => HeightLimit.typeId
      case "length_limit" => LengthLimit.typeId
      case "width_limit" => WidthLimit.typeId
      case "number_of_lanes" => RoadWidth.typeId
      case "pavement"  => PavedRoad.typeId
      case "road_width" => RoadWidth.typeId
      case "public_transport_lane" => MassTransitLane.typeId
      case "manoeuvre" => Manoeuvres.typeId
      case _ => halt(NotFound("Asset type not found"))
    }
  }

  def getAssetName(assetTypeId: Int): String = {
    assetTypeId match {
      case LitRoad.typeId   => "hasLighting"
      case PedestrianCrossings.typeId   => "hasPedestrianCrossing"
      case Obstacles.typeId => "obstacleType"
      case TrafficLights.typeId => "hasTrafficLight"
      case SpeedLimitAsset.typeId => "value"
      case TotalWeightLimit.typeId => "value"
      case TrailerTruckWeightLimit.typeId => "value"
      case AxleWeightLimit.typeId => "value"
      case BogieWeightLimit.typeId => "value"
      case HeightLimit.typeId => "value"
      case LengthLimit.typeId => "value"
      case WidthLimit.typeId => "value"
      case RoadWidth.typeId => "value"
      case PavedRoad.typeId => "hasPavement"
      case MassTransitLane.typeId => "hasLane"
      case _ => "asset"
    }
  }

  private def convertStringToDate(strDate: String): Long = {
    new SimpleDateFormat("dd.MM.yyyy hh:mm:ss").parse(strDate).getTime
  }

  private def getRoadLinksForManoeuvres(manoeuvres: Seq[Manoeuvre]) : Seq[RoadLink] = {
    val roadLinks = roadLinkService.getRoadsLinksFromVVH((manoeuvres.flatMap(_.elements.find(_.elementType == ElementTypes.LastElement).map(_.sourceLinkId)) ++
      manoeuvres.flatMap(_.elements.find(_.elementType == ElementTypes.FirstElement).map(_.sourceLinkId))).toSet)

    if (roadLinks.size < 2)
      halt(NotFound("Roadlink not found"))

    roadLinks
  }

  def extractPropertyValue(key: String, properties: Seq[AssetProperties], transformation: ( (String, Seq[String])=> Any)):  Any = {
    val values = properties.filter { property => property.name == key }.map { property =>
      property.value
    }
    transformation(key, values)
  }
  def propertyValuesToString(key: String, values: Seq[String]): String = { values.mkString }

  def extractSafetyEquipmentProperty(key: String, properties: Seq[AssetProperties], transformation: ( (String, Seq[String])=> Any)):  Any = {
    extractPropertyValue(key, properties, transformation)
  }

  def extractNameProperty(key: String, properties: Seq[AssetProperties], transformation: ( (String, Seq[String])=> Any)):  Any = {
    extractPropertyValue(key, properties, transformation)
  }

  def propertyValueToInt(key: String, values: Seq[String]): Seq[Int] = {
    try {
      values.map(_.toInt)
    } catch {
      case e: Exception => halt(BadRequest(s"The property values for the property with name $key are not valid."))
    }
  }

  def validateAssetProperties(assetTypeId: Int, properties: Seq[Seq[AssetProperties]]):Unit = {

    properties.foreach { prop =>
      validateNameInProperty(assetTypeId, prop)

      assetTypeId match {
        case LitRoad.typeId => extractPropertyValue(getAssetName(assetTypeId), prop, propertyValueToInt).asInstanceOf[Seq[Int]].foreach{ value =>
          if (!Seq(0, 1).contains(value))
            halt(BadRequest(s"The property values for the property with name hasLighting are not valid."))
        }
        case PavedRoad.typeId => extractPropertyValue(getAssetName(assetTypeId), prop, propertyValueToInt).asInstanceOf[Seq[Int]].foreach{ value =>
          if (!Seq(0, 1).contains(value))
            halt(BadRequest(s"The property values for the property with name hasPavement are not valid."))
        }
        case MassTransitLane.typeId => extractPropertyValue(getAssetName(assetTypeId), prop, propertyValueToInt).asInstanceOf[Seq[Int]].foreach{ value =>
          if (!Seq(0, 1).contains(value))
            halt(BadRequest(s"The property values for the property with name hasLane are not valid."))
        }
        case SpeedLimitAsset.typeId => extractPropertyValue(getAssetName(assetTypeId), prop, propertyValueToInt).asInstanceOf[Seq[Int]].foreach { value =>
          if (!Seq(30, 40, 50, 60, 70, 80, 90, 100, 120).contains(value))
            halt(BadRequest(s"The property values for the property with name speed limit are not valid."))
        }
        case Obstacles.typeId => extractPropertyValue(getAssetName(assetTypeId), prop, propertyValueToInt).asInstanceOf[Seq[Int]].foreach {
          value => if (!Seq(1,2).contains(value))
            halt(BadRequest(s"The property values for the property with name obstacleType are not valid."))
        }
        case PedestrianCrossings.typeId  => extractPropertyValue(getAssetName(assetTypeId), prop, propertyValueToInt).asInstanceOf[Seq[Int]].foreach { value =>
          if (!Seq(0,1).contains(value))
            halt(BadRequest(s"The property values for the property with name hasPedestrianCrossing are not valid."))
        }
        case RailwayCrossings.typeId  =>
          extractSafetyEquipmentProperty("safetyEquipment", prop, propertyValueToInt).asInstanceOf[Seq[Int]].foreach { value =>
            if (!Seq(1,2,3,4,5).contains(value))
              halt(BadRequest(s"The property values for the property with name safetyEquipment is not valid."))
          }
          if ( extractNameProperty("name", prop, propertyValuesToString).asInstanceOf[String].isEmpty)
            halt(BadRequest(s"The property values for the property name is not valid."))
        case TrafficLights.typeId  => extractPropertyValue(getAssetName(assetTypeId), prop, propertyValueToInt).asInstanceOf[Seq[Int]].foreach { value =>
          if (!Seq(0,1).contains(value))
            halt(BadRequest(s"The property values for the property with name hasTrafficLight are not valid."))
        }
        case assetType7restrictions if Maximum7Restrictions.contains(assetType7restrictions) =>
          extractPropertyValue(getAssetName(assetTypeId), prop, propertyValueToInt).asInstanceOf[Seq[Int]].foreach { value =>
            if(!value.toString.forall(_.isDigit) || value <= 0)
              halt(BadRequest(s"The property values for the property with name " + AssetTypeInfo.apply(assetTypeId).label + "are not valid."))
          }
        case RoadWidth.typeId => extractPropertyValue(getAssetName(assetTypeId), prop, propertyValueToInt).asInstanceOf[Seq[Int]].foreach { value =>
          if(!value.toString.forall(_.isDigit) || value <= 0)
            halt(BadRequest(s"The property values for the property with name " + AssetTypeInfo.apply(assetTypeId).label + "are not valid."))
        }
        case NumberOfLanes.typeId => extractPropertyValue(getAssetName(assetTypeId), prop, propertyValueToInt).asInstanceOf[Seq[Int]].foreach { value =>
          if(!value.toString.forall(_.isDigit) || value <= 0)
            halt(BadRequest(s"The property values for the property with name " + AssetTypeInfo.apply(assetTypeId).label + "are not valid."))
        }
        case _ => ("", None)
      }
    }
  }

  def validateNameInProperty(assetTypeId: Int, properties: Seq[AssetProperties]): Unit = {
    if (extractNameProperty(getAssetName(assetTypeId), properties, propertyValuesToString).asInstanceOf[String].isEmpty)
      halt(BadRequest(s"The property name doesn't exist or is not valid for this type of asset."))
  }

  def validateSideCodes(assets: Seq[NewLinearAsset]) : Unit = {
    assets.map( _.sideCode )
      .foreach( sc =>
        if( SideCode.apply(sc) == SideCode.Unknown)
          halt(UnprocessableEntity("Side code doesn't have a valid code."))
      )
  }

  def validateMeasuresOnLinears(measure: Set[Double], typeId: Int, linkId: Long): Unit = {
    validateMeasures(measure, typeId, linkId)
    if(measure.head == measure.last)halt(UnprocessableEntity("The start and end measure should not be equal for a linear asset."))
  }

  def validateMeasures(measure: Set[Double], typeId: Int, linkId: Long): Unit = {
    val roadGeometry = roadLinkService.getRoadLinkGeometry(linkId).getOrElse(halt(UnprocessableEntity("Link id is not valid or doesn't exist.")))
    val roadLength = GeometryUtils.geometryLength(roadGeometry)
    measure.foreach( m => if(m < 0 || m > roadLength) halt(UnprocessableEntity("The measure can not be less than 0 and greater than the length of the road. ")))
  }

  def validateTimeststamp(newAssetVvhTimeStamp: Long, oldAssetVvhTimeStamp: Long) = {
    if(newAssetVvhTimeStamp < oldAssetVvhTimeStamp)
      halt(UnprocessableEntity("The geometryTimestamp of the existing asset is newer than the given asset. Asset was not updated."))
  }

  get("/:assetType/:assetId") {
    contentType = formats("json")
    val assetId = params("assetId").toInt
    val assetTypeId = getAssetTypeId(params("assetType"))

    AssetTypeInfo.apply(assetTypeId).geometryType match {
      case "linear" if assetTypeId == SpeedLimitAsset.typeId => speedLimitAssetToApi(getSpeedLimitAssets(assetId).head)
      case "linear" if assetTypeId == Manoeuvres.typeId =>
        val manoeuvres = getManoeuvreAssets(assetId).head
                          manoeuvreAssetToApi(manoeuvres, getRoadLinksForManoeuvres(Seq(manoeuvres)))
      case "linear" => linearAssetToApi(getLinearAssets(assetTypeId, assetId).head)
      case "point" => pointAssetToApi(getPointAssetById(assetTypeId, assetId), assetTypeId)
      case _ =>
    }
  }

  get("/:assetType") {
    contentType = formats("json")

    val municipalityCode = params.get("municipalityCode").getOrElse(halt(BadRequest("Missing municipality code."))).toInt

    if (assetService.getMunicipalityById(municipalityCode).isEmpty)
      halt(NotFound("Municipality code not found."))

    val assetTypeId = getAssetTypeId(params("assetType"))

    AssetTypeInfo.apply(assetTypeId).geometryType match {
      case "linear" if assetTypeId == SpeedLimitAsset.typeId => speedLimitAssetsToApi(getSpeedLimitAssetsByMunicipality(municipalityCode))
      case "linear" if assetTypeId == Manoeuvres.typeId => manoeuvreAssetsToApi(manoeuvreService.getByMunicipality(municipalityCode))
      case "linear" => linearAssetsToApi(getLinearAssetsByMunicipality(municipalityCode, assetTypeId))
      case "point" => pointAssetsToApi(getPointAssetsByMunicipality(municipalityCode, assetTypeId), assetTypeId)
      case _ =>
    }
  }

  post("/:assetType") {
    contentType = formats("json")
    val assetTypeId = getAssetTypeId(params("assetType"))

    val body = parsedBody.extractOpt[Seq[JObject]].getOrElse(Nil)
    val linkIds = body.map(bd => (bd \ "linkId").extractOrElse[Long](halt(UnprocessableEntity("Missing mandatory 'linkId' parameter"))))
    linkIds.map(linkId => roadLinkService.getRoadLinkGeometry(linkId).getOrElse(halt(UnprocessableEntity(s"Link id: $linkId is not valid or doesn't exist."))))
    body.map(bd => (bd \ "startMeasure").extractOrElse[Double](halt(BadRequest("Missing mandatory 'startMeasure' parameter"))))

    if (body.exists(bd => bd \ "geometryTimestamp" != JNothing))
      halt(BadRequest("Not allow 'geometryTimestamp' parameter"))

    val properties = params("assetType") match {
      case "manoeuvre" =>
        if (body.exists(bd => bd \ "sideCode" != JNothing))
          halt(BadRequest("Not allow SideCode on Manoeuvre asset"))
        body.map(bd => (bd \ "properties").extractOrElse[Seq[ManoeuvreProperties]](halt(BadRequest("Missing asset properties"))))
      case _ =>
        val properties = body.map(bd => (bd \ "properties").extractOrElse[Seq[AssetProperties]](halt(BadRequest("Missing asset properties"))))
        validateAssetProperties(assetTypeId, properties)
        properties
    }

    if (properties.forall(_.isEmpty))
      halt(BadRequest("Missing asset properties values"))

    AssetTypeInfo.apply(assetTypeId).geometryType match {
      case "linear" if assetTypeId == SpeedLimitAsset.typeId => speedLimitAssetsToApi(createSpeedLimitAssets(assetTypeId, parsedBody))
      case "linear" if assetTypeId == Manoeuvres.typeId => manoeuvreAssetsToApi(createManoeuvreAssets(assetTypeId,  parsedBody))
      case "linear" => linearAssetsToApi(createLinearAssets(assetTypeId, parsedBody, linkIds))
      case "point" => pointAssetsToApi(createPointAssets(parsedBody, assetTypeId), assetTypeId)
      case _ =>
    }
  }

  put("/:assetType/:assetId") {
    contentType = formats("json")

    val assetTypeId = getAssetTypeId(params("assetType"))
    val linkId = (parsedBody \ "linkId").extractOrElse[Int](halt(BadRequest("Missing mandatory 'linkId' parameter")))
    (parsedBody \ "startMeasure").extractOrElse[Double](halt(BadRequest("Missing mandatory 'startMeasure' parameter")))
    val geometryTimestamp = (parsedBody \ "geometryTimestamp").extractOrElse[Long](halt(BadRequest("Missing mandatory 'geometryTimestamp' parameter")))
    val assetId = params("assetId").toInt

    val properties = params("assetType") match {
      case "manoeuvre" =>
        (parsedBody \ "properties").extractOrElse[Seq[ManoeuvreProperties]](halt(BadRequest("Missing asset properties")))
      case _ =>
        val properties = (parsedBody \ "properties").extractOrElse[Seq[AssetProperties]](halt(BadRequest("Missing asset properties")))
        validateAssetProperties(assetTypeId,Seq(properties))
        properties
    }

    if (properties.isEmpty)
      halt(BadRequest("Missing asset properties values"))

    AssetTypeInfo.apply(assetTypeId).geometryType match {
      case "linear" if assetTypeId ==  SpeedLimitAsset.typeId => speedLimitAssetToApi(updateSpeedLimitAsset(assetId, parsedBody, linkId))
      case "linear" if assetTypeId == Manoeuvres.typeId => manoeuvreAssetsToApi(updateManoeuvreAssets(assetId, parsedBody, linkId))
      case "linear" => linearAssetToApi(updateLinearAsset(assetTypeId, assetId, parsedBody, linkId))
      case "point" => pointAssetToApi(updatePointAssets(parsedBody, assetTypeId, assetId), assetTypeId)
      case _ =>
    }
  }

  delete("/:municipalityCode/:assetType/:assetId"){

    if(!params.contains("municipalityCode"))
      halt(BadRequest("Missing municipality code."))

    val municipalityCode = params("municipalityCode").toInt
    if(linearAssetService.getMunicipalityById(municipalityCode).isEmpty)
      halt(NotFound("Municipality code not found."))

    val assetType = getAssetTypeId(params("assetType"))
    val assetId = params("assetId").toLong

    AssetTypeInfo.apply(assetType).geometryType match {
      case "linear" if assetType == Manoeuvres.typeId => manoeuvreService.deleteManoeuvre(user.username, assetId)
      case "linear" => expireLinearAsset(assetType, assetId, user.username, expired = true)
      case "point" => expirePointAsset(assetType, assetId, user.username)
      case _ =>

    }
  }
}


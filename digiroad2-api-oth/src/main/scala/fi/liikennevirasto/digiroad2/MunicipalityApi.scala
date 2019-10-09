package fi.liikennevirasto.digiroad2

import java.sql.SQLException

import fi.liikennevirasto.digiroad2.Digiroad2Context._
import fi.liikennevirasto.digiroad2.asset.DateParser.DateTimePropertyFormat
import fi.liikennevirasto.digiroad2.asset.{AssetTypeInfo, Manoeuvres, _}
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.AwsDao
import fi.liikennevirasto.digiroad2.dao.pointasset.{Obstacle, PedestrianCrossing, RailwayCrossing, TrafficLight}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.{AssetPropertyService, AwsService, Dataset, RoadLinkService}
import fi.liikennevirasto.digiroad2.service.linearasset._
import fi.liikennevirasto.digiroad2.service.pointasset.{HeightLimit => _, WidthLimit => _, _}
import org.joda.time.DateTime
import org.json4s.JsonAST.JObject
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.json4s._
import org.scalatra.swagger.{ResponseMessage, Swagger, SwaggerSupport}
import fi.liikennevirasto.digiroad2.service.AwsService

case class NewAssetValues(linkId: Long, startMeasure: Double, endMeasure: Option[Double], properties: Seq[AssetProperties], sideCode: Option[Int], geometryTimestamp: Option[Long])
case class NewManoeuvreValues(linkId: Long, startMeasure: Option[Double], endMeasure: Option[Double], properties: Seq[ManoeuvreProperties], sideCode: Option[Int], geometryTimestamp: Option[Long])

case class ExampleItemResponseOnGet(linkId: Long, municipalityCode: Int, startMeasure: Int, endMeasure: Int, sideCode: Int, id: Long, properties: List[TypeProperty], createdAt: DateTime, geometryTimestamp: Int)
case class ExampleItemResponseOnCreate(linkId: Long, assetType: Int, municipalityCode: Int, startMeasure: Int, endMeasure: Int, sideCode: Int, id: Long, properties: List[TypeProperty], createdAt: DateTime)
case class ExampleItemRequestOnCreate(linkId: Long, startMeasure: Int, endMeasure: Int, sideCode: Int, properties: List[TypeProperty])
case class ExampleItemResponseOnUpdate(linkId: Long, assetType: Int, municipalityCode: Int, startMeasure: Int, endMeasure: Int, sideCode: Int, id: Long, properties: List[TypeProperty], geometryTimestamp: Int, createdAt: DateTime,  modifiedAt: DateTime)
case class ExampleItemRequestOnUpdate(linkId: Long, startMeasure: Int, endMeasure: Int, sideCode: Int, geometryTimestamp: Int, properties: List[TypeProperty])
case class TypeProperty(name: String, value: Int)


class MunicipalityApi(val onOffLinearAssetService: OnOffLinearAssetService,
                      val roadLinkService: RoadLinkService,
                      val linearAssetService: LinearAssetService,
                      val speedLimitService: SpeedLimitService,
                      val pavedRoadService: PavedRoadService,
                      val roadWidthService: RoadWidthService,
                      val manoeuvreService: ManoeuvreService,
                      val assetService: AssetService,
                      val obstacleService: ObstacleService,
                      val pedestrianCrossingService: PedestrianCrossingService,
                      val railwayCrossingService: RailwayCrossingService,
                      val trafficLightService: TrafficLightService,
                      val massTransitLaneService: MassTransitLaneService,
                      val numberOfLanesService: NumberOfLanesService,
                      implicit val swagger: Swagger
                     ) extends ScalatraServlet with JacksonJsonSupport with AuthenticationSupport with SwaggerSupport {

  def AwsService = new AwsService(vvhClient,onOffLinearAssetService, roadLinkService, linearAssetService, speedLimitService, pavedRoadService, roadWidthService, manoeuvreService,
    assetService, obstacleService, pedestrianCrossingService, railwayCrossingService, trafficLightService, massTransitLaneService, numberOfLanesService)
  def awsDao = new AwsDao

  override def baseAuth: String = "municipality."
  override val realm: String = "Municipality API"
  protected val applicationDescription = "Municipality API "

  val assetPropertyService: AssetPropertyService = Digiroad2Context.assetPropertyService

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
      case LitRoad.typeId => onOffLinearAssetService
      case PavedRoad.typeId => pavedRoadService
      case RoadWidth.typeId => roadWidthService
      case MassTransitLane.typeId => massTransitLaneService
      case NumberOfLanes.typeId => numberOfLanesService
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

  private def getValue(typeId: Int, x : NewAssetValues) : Value = {
    if (isDynamic(typeId)) {
      val propertiesDB = assetPropertyService.getProperties(typeId).head
      DynamicValue(DynamicAssetValue(Seq(DynamicProperty(propertiesDB.publicId, propertiesDB.propertyType, propertiesDB.required, Seq(DynamicPropertyValue(x.properties.map(_.value).head))))))
    } else
      NumericValue(x.properties.map(_.value).head.toInt)
  }

  private def extractNewLinearAssets(typeId: Int, value: JValue) = {
    AssetTypeInfo.apply(typeId).geometryType match {
      case "linear" => value.extractOpt[Seq[NewAssetValues]].getOrElse(Nil).map(x => NewLinearAsset(x.linkId, x.startMeasure, x.endMeasure.getOrElse(0), getValue(typeId, x), x.sideCode.getOrElse(SideCode.BothDirections.value), x.geometryTimestamp.getOrElse(vvhClient.roadLinkData.createVVHTimeStamp()), None))
      case _ => Seq.empty[NewLinearAsset]
    }
  }

  private def extractLinearAsset(typeId: Int, value: JValue) = {
    value.extractOpt[NewAssetValues].map { v =>
      NewLinearAsset(v.linkId, v.startMeasure, v.endMeasure.getOrElse(0), getValue(typeId, v), v.sideCode.getOrElse(SideCode.BothDirections.value), v.geometryTimestamp.getOrElse(vvhClient.roadLinkData.createVVHTimeStamp()), None)
    }.get
  }

  def expireLinearAsset(assetTypeId: Int, assetId: Long, username: String, expired: Boolean): String = {
    if (linearAssetService.getPersistedAssetsByIds(assetTypeId, Set(assetId.toLong)).isEmpty)
      halt(NotFound("Asset not found."))

    linearAssetService.expireAsset(assetTypeId, assetId, user.username, expired = true).getOrElse("").toString
  }

  def expirePointAsset(assetTypeId: Int, assetId: Long, username: String): Long = {
    getPointAssetById(assetTypeId, assetId)

    val service = verifyPointServiceToUse(assetTypeId)
    service.expire(assetId, user.username) match {
      case 1 => assetId
      case _ => halt(InternalServerError("Asset not deleted"))
    }
  }

  def getLinearAssetsByMunicipality(municipalityCode: Int, assetTypeId: Int): Seq[(PieceWiseLinearAsset, RoadLink)] ={
    val usedService = verifyLinearServiceToUse(assetTypeId)
    usedService.getByMunicipalityAndRoadLinks(assetTypeId, municipalityCode).filterNot{
      case (linearAsset, _) => linearAsset.id == 0 || linearAsset.expired}
  }

  def getPointAssetsByMunicipality(municipalityCode: Int, assetTypeId: Int): Seq[PersistedPointAsset] ={
    val service = verifyPointServiceToUse(assetTypeId)
    service.getByMunicipality(municipalityCode)
  }

  def getPointAssetById(assetTypeId: Int, assetId: Long): PersistedPointAsset ={
    val service = verifyPointServiceToUse(assetTypeId)
    service.getById(assetId) match {
      case Some(asset) => asset.asInstanceOf[PersistedPointAsset]
      case _ => halt(NotFound("Asset not found."))
    }
  }

  def getLinearAssetsAndRoadLinks(assetTypeId: Int, assetId: Set[Long]): Seq[(PersistedLinearAsset, RoadLink)] ={
    val usedService = verifyLinearServiceToUse(assetTypeId)
    val linearAssets = usedService.getPersistedAssetsByIds(assetTypeId, assetId).filterNot(_.expired)
    val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(linearAssets.map(_.linkId).toSet)

    linearAssets.map { linearAsset => (linearAsset, roadLinks.find(_.linkId == linearAsset.linkId).getOrElse(halt(NotFound("Roadlink not found"))))}
  }

  def getSpeedLimitAssetsByMunicipality(municipalityCode: Int): Seq[(SpeedLimit, RoadLink)] ={
    speedLimitService.getByMunicpalityAndRoadLinks(municipalityCode).filterNot{ case (speedLimit, _) => speedLimit.id == 0 || speedLimit.expired}
  }

  def getSpeedLimitsAndRoadLinks(assetId: Set[Long]): Seq[(SpeedLimit, RoadLink)] = {
    val speedLimits = speedLimitService.getSpeedLimitAssetsByIds(assetId)

    val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(speedLimits.map(_.linkId).toSet)
    speedLimits.map { speedLimits => (speedLimits, roadLinks.find(_.linkId == speedLimits.linkId).getOrElse(halt(NotFound("Roadlink not found")))) }
  }

  def getManoeuvreAndRoadLinks(assetId: Seq[Long]) : Seq[(Manoeuvre, Seq[RoadLink])] = {
    val manoeuvres = assetId.flatMap(manoeuvreService.find)
    val linkIds = manoeuvres.flatMap(_.elements.find (_.elementType == ElementTypes.LastElement).map (_.sourceLinkId) ) ++
      manoeuvres.flatMap(_.elements.find (_.elementType == ElementTypes.FirstElement).map (_.sourceLinkId))

    val roadLinks = roadLinkService.getRoadsLinksFromVVH(linkIds.toSet)
    manoeuvres.map { manoeuvre => (manoeuvre,
      roadLinks.filter(road => road.linkId == manoeuvre.elements.find(_.elementType == ElementTypes.LastElement).map(_.sourceLinkId).get ||
        road.linkId == manoeuvre.elements.find(_.elementType == ElementTypes.FirstElement).map(_.sourceLinkId).get)
    )}
  }

  def updateLinearAsset(assetTypeId: Int, assetId: Int, parsedBody: JValue, linkId: Long): Seq[(PersistedLinearAsset, RoadLink)] = {
    val usedService = verifyLinearServiceToUse(assetTypeId)
    val oldAsset = usedService.getPersistedAssetsByIds(assetTypeId, Set(assetId.toLong)).filterNot(_.expired).headOption.
      getOrElse(halt(NotFound("Asset not found.")))
    val newAsset = extractLinearAsset(assetTypeId, parsedBody)
    validateMeasuresOnAssets(Set(newAsset.startMeasure, newAsset.endMeasure), linkId)
    validateSideCodes(Seq(newAsset))
    validateTimeststamp(newAsset.vvhTimeStamp, oldAsset.vvhTimeStamp)

    val updatedId = usedService.update(Seq(oldAsset.id), newAsset.value, user.username, Some(newAsset.vvhTimeStamp), Some(newAsset.sideCode), Some(Measures(newAsset.startMeasure, newAsset.endMeasure)))
    updatedId match {
      case Seq(0L) => Seq.empty
      case _ => getLinearAssetsAndRoadLinks(assetTypeId, updatedId.toSet)
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
            x.properties.find(_.name == "name").map { name => name.value },
            x.properties.find(_.name == "railwayCrossingId").map { code => code.value }.get
          ))
      case TrafficLights.typeId =>
        parsedBody.extractOpt[NewAssetValues].map(x =>IncomingPedestrianCrossingAsset( x.linkId, x.startMeasure.toLong))
    }

    val pointAsset = asset.getOrElse(throw new NoSuchElementException(""))
    validateMeasures(Set(pointAsset.mValue), pointAsset.linkId)
    val link = roadLinkService.getRoadLinkAndComplementaryFromVVH(pointAsset.linkId).getOrElse(throw new NoSuchElementException(s"Roadlink with ${pointAsset.linkId} does not exist"))
    val incomingAsset = service.toIncomingAsset(pointAsset, link)
    val updatedAsset = service.update(assetId, incomingAsset.get, link, user.username)
    getPointAssetById(typeId, updatedAsset)
  }

  def updateSpeedLimitAsset(assetId: Long, parsedBody: JValue, linkId: Long): (SpeedLimit, RoadLink) = {
    val oldAsset = speedLimitService.getSpeedLimitAssetsByIds(Set(assetId)).filterNot(_.expired).headOption
      .getOrElse(halt(NotFound("Asset not found.")))

    val newAsset = extractLinearAsset(SpeedLimitAsset.typeId, parsedBody)
    validateMeasuresOnAssets(Set(newAsset.startMeasure, newAsset.endMeasure), linkId)
    validateSideCodes(Seq(newAsset))
    validateTimeststamp(newAsset.vvhTimeStamp, oldAsset.vvhTimeStamp)

    val updatedId = speedLimitService.update(assetId, Seq(newAsset), user.username)
    getSpeedLimitsAndRoadLinks(updatedId.toSet).headOption.getOrElse(halt(InternalServerError("Asset not Updated")))
  }

  def updateManoeuvreAssets(assetId: Long, parsedBody: JValue): (Manoeuvre, Seq[RoadLink]) = {
    val oldAsset = manoeuvreService.find(assetId).getOrElse(halt(NotFound("Asset not found.")))
    parsedBody.extractOpt[NewManoeuvreValues].map { manoeuvre =>
      validateManoeuvrePropForUpdate(manoeuvre)

      validateTimeststamp(manoeuvre.geometryTimestamp.getOrElse(halt(NotFound("geometryTimestamp not found"))), oldAsset.modifiedDateTime.getOrElse(oldAsset.createdDateTime).getMillis())

      val validityPeriods = convertValidityPeriod(manoeuvre.properties.find(_.name == "validityPeriods"))
      val exceptions = manoeuvre.properties.find(_.name == "exceptions").map(_.value.asInstanceOf[List[BigInt]].map(_.toInt))
      val additionalInfo = manoeuvre.properties.find(_.name == "additionalInfo").map(_.value.toString)

      val manoeuvreUpdates = ManoeuvreUpdates(validityPeriods, exceptions, additionalInfo)
      val updatedId = manoeuvreService.updateManoeuvre(user.username, assetId, manoeuvreUpdates, Some(new DateTime(manoeuvre.geometryTimestamp.get)))
      getManoeuvreAndRoadLinks(Seq(updatedId.toInt)).headOption.getOrElse(halt(InternalServerError("Asset not Updated")))
    }.get
  }

  def createLinearAssets(assetTypeId: Int, parsedBody: JValue, linkId: Seq[Long]): Seq[(PersistedLinearAsset, RoadLink)] ={
    val usedService = verifyLinearServiceToUse(assetTypeId)
    val newLinearAssets = extractNewLinearAssets(assetTypeId, parsedBody)
    validateSideCodes(newLinearAssets)
    newLinearAssets.foreach{
      newAsset => validateMeasuresOnAssets(Set(newAsset.startMeasure, newAsset.endMeasure), newAsset.linkId)
    }
    val assetsIds = usedService.create(newLinearAssets, assetTypeId, user.username)
    getLinearAssetsAndRoadLinks(assetTypeId, assetsIds.toSet)
  }

  def createSpeedLimitAssets(assetTypeId: Int, parsedBody: JValue): Seq[(SpeedLimit, RoadLink)] ={
    val newSpeedLimitAssets = extractNewLinearAssets(assetTypeId, parsedBody)
    validateSideCodes(newSpeedLimitAssets)
    newSpeedLimitAssets.foreach{
      newAsset => validateMeasuresOnAssets(Set(newAsset.startMeasure, newAsset.endMeasure), newAsset.linkId)
    }
    val assetsIds = speedLimitService.createMultiple(newSpeedLimitAssets, assetTypeId, user.username, 0, (_, _) => Unit)
    getSpeedLimitsAndRoadLinks(assetsIds.toSet)
  }

  private def getLinkIds(manoeuvres: Seq[NewManoeuvreValues]): Seq[Long] = {
    manoeuvres.map(_.properties.find(_.name == "sourceLinkId").map(_.value.asInstanceOf[BigInt].toLong).getOrElse(halt(NotFound("sourceLinkId not found"))))++
      manoeuvres.flatMap(_.properties.find(_.name == "elements").map(_.value.asInstanceOf[Seq[BigInt]].map(_.toLong)).getOrElse(Seq())) ++
      manoeuvres.map(_.properties.find(_.name == "destLinkId").map(_.value.asInstanceOf[BigInt].toLong).getOrElse(halt(NotFound("destLinkId not found"))))
  }

  def createManoeuvreAssets(parsedBody: JValue): Seq[(Manoeuvre, Seq[RoadLink])] = {
    val manoeuvreIds = parsedBody.extractOpt[Seq[NewManoeuvreValues]].map { manoeuvres =>

      val roadLinks = roadLinkService.getRoadsLinksFromVVH(getLinkIds(manoeuvres).toSet)
      validateManoeuvrePropForCreate(manoeuvres, roadLinks)

      manoeuvres.map {manoeuvre =>
        val linkIds = getLinkIds(Seq(manoeuvre))
        manoeuvreService.createManoeuvre(user.username,
          NewManoeuvre(
            convertValidityPeriod(manoeuvre.properties.find(_.name == "validityPeriods")).getOrElse(Seq()).toSet,
            manoeuvre.properties.find(_.name == "exceptions").map(_.value.asInstanceOf[List[BigInt]].map(_.toInt)).getOrElse(Seq()),
            manoeuvre.properties.find(_.name == "additionalInfo").map(_.value.toString), linkIds, None),
         roadLinks.filter(road => linkIds.contains(road.linkId))
        )
      }
    }.get

    getManoeuvreAndRoadLinks(manoeuvreIds)
  }

  def convertValidityPeriod(validityOpt: Option[ManoeuvreProperties]): Option[Set[ValidityPeriod]] = {
    validityOpt match {
      case Some(validityPeriod) => Some(validityPeriod.value.asInstanceOf[List[Map[String, Any]]].map { a =>
        ValidityPeriod(a.find(a => a._1 == "startHour").map(_._2).get.asInstanceOf[BigInt].toInt,
          a.find(a => a._1 == "endHour").map(_._2).get.asInstanceOf[BigInt].toInt,
          ValidityPeriodDayOfWeek(a.find(a => a._1 == "days").map(_._2).get.asInstanceOf[String]),
          a.find(a => a._1 == "startMinute").map(_._2).get.asInstanceOf[BigInt].toInt,
          a.find(a => a._1 == "endMinute").map(_._2).get.asInstanceOf[BigInt].toInt)
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
              x.properties.find(_.name == "name").map { name => name.value },
              x.properties.find(_.name == "railwayCrossingId").map { code => code.value }.get
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
      case Obstacles.typeId  => Seq(Map("value" ->  pointAsset.asInstanceOf[Obstacle].obstacleType, "name" -> getAssetNameProp(typeId).head))
      case PedestrianCrossings.typeId  => Seq(Map("value" -> "1" , "name" -> getAssetNameProp(typeId).head))
      case RailwayCrossings.typeId  => Seq(
        Map( "name" -> "name",
          "value" ->  pointAsset.asInstanceOf[RailwayCrossing].name),
        Map("name" -> "safetyEquipment",
          "value" -> pointAsset.asInstanceOf[RailwayCrossing].safetyEquipment),
        Map("name" -> "railwayCrossingId",
          "value" -> pointAsset.asInstanceOf[RailwayCrossing].code)
      )
      case TrafficLights.typeId  => Seq(Map("value" -> "1" , "name" -> getAssetNameProp(typeId).head))
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

      val elements = manoeuvre.elements.filter(_.elementType == ElementTypes.IntermediateElement).map(_.sourceLinkId)

      Seq(Map("name" -> "sourceLinkId", "value" -> manoeuvre.elements.find(_.elementType == ElementTypes.FirstElement).map(_.sourceLinkId).get
      ),Map("name" -> "elements", "value" -> elements),
        Map("name" -> "destLinkId", "value" -> manoeuvre.elements.find(_.elementType == ElementTypes.LastElement).map(_.sourceLinkId).get
        )) ++ exceptions ++ additionalInfo ++ validityPeriods
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

  def linearPieceWiseAssetToApi(linearAssetsAndRoadLink: (PieceWiseLinearAsset, RoadLink)): Map[String, Any] = {
    val (linearAsset, roadLink) = linearAssetsAndRoadLink
    Map("id" -> linearAsset.id,
      "properties" -> Seq(Map("value" -> linearAsset.value.map(_.toJson), "name" -> getAssetNameProp(linearAsset.typeId).head)),
      "linkId" -> linearAsset.linkId,
      "startMeasure" -> linearAsset.startMeasure,
      "endMeasure" -> linearAsset.endMeasure,
      "sideCode" -> linearAsset.sideCode,
      "modifiedAt" -> linearAsset.modifiedDateTime,
      "createdAt" -> linearAsset.createdDateTime,
      "geometryTimestamp" -> linearAsset.vvhTimeStamp,
      "municipalityCode" -> roadLink.municipalityCode
    )
  }

  def linearPieceWiseAssetsToApi(linearAssetsAndRoadLink: Seq[(PieceWiseLinearAsset, RoadLink)]): Seq[Map[String, Any]] = {
    linearAssetsAndRoadLink.map ( linearAssets => linearPieceWiseAssetToApi(linearAssets))
  }

  def linearAssetToApi(linearAssetsAndRoadLink: (PersistedLinearAsset, RoadLink)): Map[String, Any] = {
    val (linearAsset, roadLink) = linearAssetsAndRoadLink
    Map("id" -> linearAsset.id,
      "properties" -> Seq(Map("value" -> linearAsset.value.map(_.toJson), "name" -> getAssetNameProp(linearAsset.typeId).head)),
      "linkId" -> linearAsset.linkId,
      "startMeasure" -> linearAsset.startMeasure,
      "endMeasure" -> linearAsset.endMeasure,
      "sideCode" -> linearAsset.sideCode,
      "modifiedAt" -> linearAsset.modifiedDateTime,
      "createdAt" -> linearAsset.createdDateTime,
      "geometryTimestamp" -> linearAsset.vvhTimeStamp,
      "municipalityCode" -> roadLink.municipalityCode
    )
  }

  def linearAssetsToApi(linearAssetsAndRoadLink: Seq[(PersistedLinearAsset, RoadLink)]): Seq[Map[String, Any]] = {
    linearAssetsAndRoadLink.map ( linearAssets => linearAssetToApi(linearAssets))
  }

  def dynamicPieceWiseAssetToApi(linearAssetsAndRoadLink: (PieceWiseLinearAsset, RoadLink)): Map[String, Any] = {
    val (linearAsset, roadLink) = linearAssetsAndRoadLink
    Map("id" -> linearAsset.id,
      "properties" -> Seq(Map("value" -> (linearAsset.value match {
        case Some(DynamicValue(x)) => x.properties.flatMap { dynamicTypeProperty =>
          dynamicTypeProperty.values.map(_.value)}.headOption
        case _ => Some(PavementClass.Unknown.value)})
        , "name" -> getAssetNameProp(linearAsset.typeId).head)),
      "linkId" -> linearAsset.linkId,
      "startMeasure" -> linearAsset.startMeasure,
      "endMeasure" -> linearAsset.endMeasure,
      "sideCode" -> linearAsset.sideCode,
      "modifiedAt" -> linearAsset.modifiedDateTime,
      "createdAt" -> linearAsset.createdDateTime,
      "geometryTimestamp" -> linearAsset.vvhTimeStamp,
      "municipalityCode" -> roadLink.municipalityCode
    )
  }

  def dynamicPieceWiseAssetsToApi(linearAssetsAndRoadLink: Seq[(PieceWiseLinearAsset, RoadLink)]): Seq[Map[String, Any]] = {
    linearAssetsAndRoadLink.map ( linearAssets => dynamicPieceWiseAssetToApi(linearAssets))
  }

  def dynamicAssetToApi(linearAssetsAndRoadLink: (PersistedLinearAsset, RoadLink)): Map[String, Any] = {
    val (linearAsset, roadLink) = linearAssetsAndRoadLink
    Map("id" -> linearAsset.id,
      "properties" ->
        Seq(Map("value" -> (linearAsset.value match {
          case Some(DynamicValue(x)) => x.properties.flatMap { dynamicTypeProperty =>
                dynamicTypeProperty.values.map(_.value)}.headOption
          case _ => Some(PavementClass.Unknown.value)})
    , "name" -> getAssetNameProp(linearAsset.typeId).head)),
      "linkId" -> linearAsset.linkId,
      "startMeasure" -> linearAsset.startMeasure,
      "endMeasure" -> linearAsset.endMeasure,
      "sideCode" -> linearAsset.sideCode,
      "modifiedAt" -> linearAsset.modifiedDateTime,
      "createdAt" -> linearAsset.createdDateTime,
      "geometryTimestamp" -> linearAsset.vvhTimeStamp,
      "municipalityCode" -> roadLink.municipalityCode
    )
  }

  def dynamicAssetsToApi(linearAssetsAndRoadLink: Seq[(PersistedLinearAsset, RoadLink)]): Seq[Map[String, Any]] = {
    linearAssetsAndRoadLink.map ( linearAssets => dynamicAssetToApi(linearAssets))
  }

  def speedLimitAssetToApi(speedLimitsAndRoadLink: (SpeedLimit, RoadLink)): Map[String, Any] = {
    val (speedLimit, roadLink) = speedLimitsAndRoadLink
    Map("id" -> speedLimit.id,
      "properties" -> Seq(Map("value" -> speedLimit.value.map(_.toJson), "name" -> getAssetNameProp( SpeedLimitAsset.typeId).head)),
      "linkId" -> speedLimit.linkId,
      "startMeasure" -> speedLimit.startMeasure,
      "endMeasure" -> speedLimit.endMeasure,
      "sideCode" -> speedLimit.sideCode,
      "modifiedAt" -> speedLimit.modifiedDateTime,
      "createdAt" -> speedLimit.createdDateTime,
      "geometryTimestamp" -> speedLimit.vvhTimeStamp,
      "municipalityCode" -> roadLink.municipalityCode
    )
  }

  def speedLimitAssetsToApi(speedLimitsAndRoadLink: Seq[(SpeedLimit, RoadLink)]): Seq[Map[String, Any]] = {
    speedLimitsAndRoadLink.map ( speedLimitsAndRoadLink => speedLimitAssetToApi(speedLimitsAndRoadLink))
  }

  def manoeuvreAssetToApi(manoeuvreAndRoadLinks: (Manoeuvre, Seq[RoadLink])): Map[String, Any] = {
    val (manoeuvre, roadLinks) = manoeuvreAndRoadLinks
    val geomtry = roadLinks.find(_.linkId == manoeuvre.elements.find(_.elementType == ElementTypes.LastElement).map(_.sourceLinkId).get)
      .getOrElse(halt(NotFound("Roadlink not found (destLink)"))).geometry

    val municipalityCode = roadLinks.find(_.linkId == manoeuvre.elements.find(_.elementType == ElementTypes.FirstElement).map(_.sourceLinkId).get)
      .getOrElse(halt(NotFound("Roadlink not found (sourceLink)"))).municipalityCode

    Map("id" -> manoeuvre.id,
      "properties" -> extractManoeuvreProperties(manoeuvre),
      "linkId" -> manoeuvre.elements.find(_.elementType == ElementTypes.LastElement).map(_.sourceLinkId).get,
      "startMeasure" -> 0,
      "endMeasure" -> GeometryUtils.geometryLength(geomtry),
      "modifiedAt" -> manoeuvre.modifiedDateTime.map(DateTimePropertyFormat.print).getOrElse(""),
      "createdAt" -> manoeuvre.createdDateTime.toString("dd.MM.yyyy HH:mm:ss"),
      "geometryTimestamp" -> manoeuvre.modifiedDateTime.getOrElse(manoeuvre.createdDateTime).getMillis,
      "municipalityCode" -> municipalityCode
    )
  }

  def manoeuvreAssetsToApi(manoeuvresAndRoadLinks: Seq[(Manoeuvre, Seq[RoadLink])]): Seq[Map[String, Any]] = {
    manoeuvresAndRoadLinks.map { manoeuvreAndRoadLinks =>
      manoeuvreAssetToApi(manoeuvreAndRoadLinks)
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
      case "number_of_lanes" => NumberOfLanes.typeId
      case "pavement"  => PavedRoad.typeId
      case "road_width" => RoadWidth.typeId
      case "public_transport_lane" => MassTransitLane.typeId
//      case "manoeuvre" => Manoeuvres.typeId
      case _ => halt(NotFound("Asset type not found"))
    }
  }

  def getAssetNameProp(assetTypeId: Int): Seq[String] = {
    assetTypeId match {
      case LitRoad.typeId   => Seq("hasLighting")
      case PedestrianCrossings.typeId   => Seq("hasPedestrianCrossing")
      case Obstacles.typeId => Seq("obstacleType")
      case TrafficLights.typeId => Seq("hasTrafficLight")
      case SpeedLimitAsset.typeId => Seq("value")
      case TotalWeightLimit.typeId => Seq("value")
      case TrailerTruckWeightLimit.typeId => Seq("value")
      case AxleWeightLimit.typeId => Seq("value")
      case BogieWeightLimit.typeId => Seq("value")
      case HeightLimit.typeId => Seq("value")
      case LengthLimit.typeId => Seq("value")
      case WidthLimit.typeId => Seq("value")
      case RoadWidth.typeId => Seq("value")
      case PavedRoad.typeId => Seq("value")
      case NumberOfLanes.typeId => Seq("value")
      case MassTransitLane.typeId => Seq("hasLane")
      case RailwayCrossings.typeId  => Seq("safetyEquipment", "name", "railwayCrossingId")
      case _ => Seq("asset")
    }
  }

  private def linkIdValidation(linkIds: Set[Long]): Seq[RoadLink] = {
    val roadLinks = roadLinkService.getRoadsLinksFromVVH(linkIds)
    linkIds.foreach { linkId =>
      roadLinks.find(road => road.linkId == linkId && road.administrativeClass != State).
        getOrElse(halt(UnprocessableEntity(s"Link id: $linkId is not valid, doesn't exist or have an administrative class 'State'.")))
    }
    roadLinks
  }

  private def linkIdValidation(linkIds: Set[Long], roadLinks: Seq[RoadLink]) : Unit  = {
    linkIds.foreach { linkId =>
      roadLinks.find(road => road.linkId == linkId && road.administrativeClass != State).
        getOrElse(halt(UnprocessableEntity(s"Link id: $linkId is not valid, doesn't exist or have an administrative class 'State'.")))
    }
  }

  private def validateJsonField(fieldsNotAllowed: Seq[String], body: Seq[JObject] ): Unit = {
    val notAllowed = StringBuilder.newBuilder

    fieldsNotAllowed.foreach { field =>
      if (body.exists(bd => bd \ field != JNothing))
        notAllowed.append(field)
    }
    if(notAllowed.nonEmpty)
      halt(BadRequest("Not allow " + notAllowed + " on this type of asset"))
  }

  def extractPropertyValue(key: String, properties: Seq[AssetProperties], transformation: (String, Seq[String]) => Any):  Any = {
    val values = properties.filter { property => property.name == key }.map(_.value)
    transformation(key, values)
  }

  def propertyValuesToString(key: String, values: Seq[String]): String = { values.mkString }

  def extractProperty(key: String, properties: Seq[AssetProperties], transformation: (String, Seq[String]) => Any):  Any = {
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

    if (properties.forall(_.isEmpty))
      halt(BadRequest("Missing asset properties values"))

    properties.foreach { prop =>
      validateNameInProperty(assetTypeId, prop)

      assetTypeId match {
        case LitRoad.typeId => extractPropertyValue(getAssetNameProp(assetTypeId).head, prop, propertyValueToInt).asInstanceOf[Seq[Int]].foreach{ value =>
          if (!Seq(0, 1).contains(value))
            halt(BadRequest(s"The property values for the property with name hasLighting are not valid."))
        }

        case PavedRoad.typeId => extractPropertyValue(getAssetNameProp(assetTypeId).head, prop, propertyValueToInt).asInstanceOf[Seq[Int]].foreach{ value =>
          if (!Seq(1, 2, 10, 20, 30, 40, 50).contains(value))
            halt(BadRequest(s"The property values for the property with name value are not valid."))
        }
        case MassTransitLane.typeId => extractPropertyValue(getAssetNameProp(assetTypeId).head, prop, propertyValueToInt).asInstanceOf[Seq[Int]].foreach{ value =>
          if (!Seq(0, 1).contains(value))
            halt(BadRequest(s"The property values for the property with name hasLane are not valid."))
        }
        case SpeedLimitAsset.typeId => extractPropertyValue(getAssetNameProp(assetTypeId).head, prop, propertyValueToInt).asInstanceOf[Seq[Int]].foreach { value =>
          if (!Seq(30, 40, 50, 60, 70, 80, 90, 100, 120).contains(value))
            halt(BadRequest(s"The property values for the property with name speed limit are not valid."))
        }
        case Obstacles.typeId => extractPropertyValue(getAssetNameProp(assetTypeId).head, prop, propertyValueToInt).asInstanceOf[Seq[Int]].foreach {
          value => if (!Seq(1,2).contains(value))
            halt(BadRequest(s"The property values for the property with name obstacleType are not valid."))
        }
        case PedestrianCrossings.typeId  => extractPropertyValue(getAssetNameProp(assetTypeId).head, prop, propertyValueToInt).asInstanceOf[Seq[Int]].foreach { value =>
          if (!Seq(0,1).contains(value))
            halt(BadRequest(s"The property values for the property with name hasPedestrianCrossing are not valid."))
        }
        case RailwayCrossings.typeId  =>
            extractPropertyValue("safetyEquipment", prop, propertyValueToInt).asInstanceOf[Seq[Int]].foreach { value =>
            if (!Seq(1,2,3,4,5).contains(value))
              halt(BadRequest(s"The property values for the property with name safetyEquipment is not valid."))
          }
        case TrafficLights.typeId  => extractPropertyValue(getAssetNameProp(assetTypeId).head, prop, propertyValueToInt).asInstanceOf[Seq[Int]].foreach { value =>
          if (!Seq(0,1).contains(value))
            halt(BadRequest(s"The property values for the property with name hasTrafficLight are not valid."))
        }
        case assetType7restrictions if Maximum7Restrictions.contains(assetType7restrictions) =>
          extractPropertyValue(getAssetNameProp(assetTypeId).head, prop, propertyValueToInt).asInstanceOf[Seq[Int]].foreach { value =>
            if(!value.toString.forall(_.isDigit) || value <= 0)
              halt(BadRequest(s"The property values for " + AssetTypeInfo.apply(assetTypeId).label + " asset type are not valid."))
          }
        case RoadWidth.typeId => extractPropertyValue(getAssetNameProp(assetTypeId).head, prop, propertyValueToInt).asInstanceOf[Seq[Int]].foreach { value =>
          if(!value.toString.forall(_.isDigit) || value <= 0)
            halt(BadRequest(s"The property values for "  + AssetTypeInfo.apply(assetTypeId).label + " asset type are not valid."))
        }
        case NumberOfLanes.typeId => extractPropertyValue(getAssetNameProp(assetTypeId).head, prop, propertyValueToInt).asInstanceOf[Seq[Int]].foreach { value =>
          if(!value.toString.forall(_.isDigit) || value <= 0)
            halt(BadRequest(s"The property values for "  + AssetTypeInfo.apply(assetTypeId).label + " asset type are not valid."))
        }
        case _ => ("", None)
      }
    }
  }

  private def validateManoeuvrePropForCreate(manoeuvres: Seq[NewManoeuvreValues], roadLinks: Seq[RoadLink]):Unit = {


    linkIdValidation(getLinkIds(manoeuvres).toSet, roadLinks)
    //validate all LinkIds
    manoeuvres.foreach { manoeuvre =>
      val destLinkId = manoeuvre.properties.find(_.name == "destLinkId").map(_.value.asInstanceOf[BigInt].toLong).getOrElse(halt(NotFound("destLinkId not found")))

      manoeuvre.startMeasure match {
        case Some(measure) =>
          if (measure < 0)
            halt(UnprocessableEntity("StartMeasure should be greater than 0"))
        case _ => None
      }

      manoeuvre.endMeasure match {
        case Some(measure) =>
          if (measure > GeometryUtils.geometryLength(roadLinks.find(_.linkId == destLinkId).get.geometry))
            halt(UnprocessableEntity("endMeasure is greater than destLinkId "))
        case _ => None
      }
    }
    validateNotMandatoryManoeuvreProp(manoeuvres)
  }

  private def validateManoeuvrePropForUpdate(manoeuvre: NewManoeuvreValues):Unit = {

    if (manoeuvre.properties.map(_.name).contains("sourceLinkId") || manoeuvre.properties.map(_.name).contains("elements") || manoeuvre.properties.map(_.name).contains("destLinkId"))
      halt(UnprocessableEntity("Not allow update sourceLinkId, destLinkId or elements"))

    if (!manoeuvre.properties.map(_.name).contains("exceptions") && !manoeuvre.properties.map(_.name).contains("validityPeriods") && !manoeuvre.properties.map(_.name).contains("additionalInfo"))
      halt(NotFound("Not found properties to be updated"))

    validateNotMandatoryManoeuvreProp(Seq(manoeuvre))
  }

  private def validateNotMandatoryManoeuvreProp(manoeuvres: Seq[NewManoeuvreValues]) : Unit = {
    def checkValidityPeriodFields(fieldName: String, validityPeriodProp: Map[String,Any]) : Unit = {

      if(fieldName == "days") {
        val prop = validityPeriodProp.find(a => a._1 == fieldName).map(_._2).getOrElse(halt(NotFound(fieldName + " not found"))).asInstanceOf[String]
        if (!Seq("Sunday", "Weekday", "Saturday").contains(prop))
          halt(BadRequest(fieldName + " incorrect format.. Should be \"Sunday\", \"Weekday\", \"Saturday\" "))
      }else {
        val prop = validityPeriodProp.find(a => a._1 == fieldName).map(_._2).getOrElse(halt(NotFound(fieldName + " not found"))).asInstanceOf[BigInt].toString
        if (!prop.forall(_.isDigit))
          halt(BadRequest(fieldName + " incorrect format.. Should be a number"))
        if (fieldName.contains("Hour") && prop.toLong < 0 && prop.toLong > 23)
          halt(BadRequest(fieldName + " incorrect format.. Hour should be between 1 and 24"))
        if (fieldName.contains("Minute") && (prop.toLong < 0 && prop.toLong > 55 || prop.toLong > 0 && prop.toLong % 5 != 0))
          halt(BadRequest(fieldName + " incorrect format.. Minute should be 0, 5, 10, ... or 55"))
      }
    }

    manoeuvres.foreach { manoeuvre =>
      manoeuvre.properties.find(_.name == "validityPeriods") match {
        case Some(validityPeriod) =>
          val validyPeriodValue = validityPeriod.value.asInstanceOf[List[Map[String, Any]]]

          if(validyPeriodValue.flatten.map(_._1).toSet != Set("startHour", "endHour", "days", "startMinute", "endMinute"))
            halt(BadRequest(s"The property names for the property validityPeriods are not valid."))

          validyPeriodValue.foreach { prop =>
          Set("startHour", "endHour", "days", "startMinute", "endMinute").foreach(checkValidityPeriodFields(_, prop))
        }
        case _ => None
      }

      manoeuvre.properties.find(_.name == "exceptions").foreach(_.value.asInstanceOf[List[BigInt]].map(_.toInt).foreach { value =>
        if (!Seq(4, 5, 6, 7, 8, 9, 10, 13, 14, 15, 19, 21, 22, 27).contains(value))
          halt(BadRequest(s"The property values for the property with name exceptions are not valid."))
      })
    }
  }

  def validateNameInProperty(assetTypeId: Int, properties: Seq[AssetProperties]): Unit = {
    getAssetNameProp(assetTypeId).foreach { prop =>
      if (extractProperty(prop, properties, propertyValuesToString).asInstanceOf[String].isEmpty)
        halt(BadRequest(s"The property name " + prop + " doesn't exist or is not valid for this type of asset."))
    }
  }

  def validateSideCodes(assets: Seq[NewLinearAsset]) : Unit = {
    assets.map( _.sideCode )
      .foreach( sc =>
        if( SideCode.apply(sc) == SideCode.Unknown)
          halt(UnprocessableEntity("Side code doesn't have a valid code."))
      )
  }

  def validateMeasuresOnAssets(measure: Set[Double], linkId: Long): Unit = {
    validateMeasures(measure, linkId)
    if(measure.head == measure.last)halt(UnprocessableEntity("The start and end measure should not be equal for a linear asset."))
  }

  def validateMeasures(measure: Set[Double], linkId: Long): Unit = {
    val roadGeometry = roadLinkService.getRoadLinkGeometry(linkId).getOrElse(halt(UnprocessableEntity("Link id is not valid or doesn't exist.")))
    val roadLength = GeometryUtils.geometryLength(roadGeometry)
    measure.foreach( m => if(m < 0 || m > roadLength) halt(UnprocessableEntity("The measure can not be less than 0 and greater than the length of the road. ")))
  }

  def validateTimeststamp(newAssetVvhTimeStamp: Long, oldAssetVvhTimeStamp: Long): Unit = {
    if(newAssetVvhTimeStamp < oldAssetVvhTimeStamp)
      halt(UnprocessableEntity("The geometryTimestamp of the existing asset is newer than the given asset. Asset was not updated."))
  }

  //Description of Api entry point to get assets by municipality
  val getAssetsByMunicipalityCode =
    (apiOperation[List[ExampleItemResponseOnGet]]("getAssetsByMunicipalityCode")
      .parameters(
        queryParam[Int]("municipalityCode").description("Municipality Code where we will execute the search by specific asset type"),
        pathParam[String]("assetType").description("Asset type name to get in a specific municipality")
      )
      .responseMessages(
        ResponseMessage(401, "The authentication parameters are missing or are incorrect"),
        ResponseMessage(403, "The authenticated user doesn't have permissions to do this operation on that municipality."),
        ResponseMessage(404, "Municipality code or asset type not found.")
      )
      tags "Municipality API"
      summary "Lists all assets of an asset type on the municipality code."
      authorizations "Contact your service provider for more information"
      description
      "Example URL: /api/municipality/lighting?municipalityCode=235"
      )

  get("/:assetType", operation(getAssetsByMunicipalityCode)) {
    contentType = formats("json")

    val municipalityCode = params.get("municipalityCode").getOrElse(halt(BadRequest("Missing municipality code."))).toInt

    if (assetService.getMunicipalityById(municipalityCode).isEmpty)
      halt(NotFound("Municipality code not found."))
    val assetTypeName = params("assetType")

    assetTypeName match {
      case "manoeuvre" =>
        manoeuvreAssetsToApi(manoeuvreService.getByMunicipalityAndRoadLinks(municipalityCode))
      case "speed_limit" =>
        speedLimitAssetsToApi(getSpeedLimitAssetsByMunicipality(municipalityCode))
      case _ =>
        val assetTypeId = getAssetTypeId(assetTypeName)
        AssetTypeInfo.apply(assetTypeId).geometryType match {
          case "linear" =>
            if(isDynamic(assetTypeId)) {
              dynamicPieceWiseAssetsToApi(getLinearAssetsByMunicipality(municipalityCode, assetTypeId))
            } else {
              linearPieceWiseAssetsToApi(getLinearAssetsByMunicipality(municipalityCode, assetTypeId))
            }
          case "point" => pointAssetsToApi(getPointAssetsByMunicipality(municipalityCode, assetTypeId), assetTypeId)
          case _ =>
        }
    }
  }

  private def isDynamic(assetId: Int): Boolean = {
    val dynamicAssetType = Seq(PavedRoad.typeId, MassTransitLane.typeId)

    dynamicAssetType.contains(assetId)
  }

  //Description of Api entry point to get assets by id
  val getAssetById =
    (apiOperation[ExampleItemResponseOnGet]("getAssetById")
      .parameters(
        pathParam[String]("assetId").description("Id of the asset to be fetch"),
        pathParam[String]("assetType").description("Asset type name to get")
      )
      .responseMessages(
        ResponseMessage(401, "The authentication parameters are missing or are incorrect"),
        ResponseMessage(403, "The authenticated user doesn't have permissions to do this operation on that municipality."),
        ResponseMessage(404, "Municipality code or asset type not found.")
      )
      tags "Municipality API"
      summary "Returns information about a single asset ."
      authorizations "Contact your service provider for more information"
      description
      "Example URL: /api/municipality/lighting/400018"
      )

  get("/:assetType/:assetId", operation(getAssetById)) {
    contentType = formats("json")
    val assetId = params("assetId").toInt
    val assetTypeName = params("assetType")

    assetTypeName match {
      case "manoeuvre" =>
        manoeuvreAssetToApi(getManoeuvreAndRoadLinks(Seq(assetId)).headOption.getOrElse(halt(NotFound("Asset not found"))))
      case "speed_limit" =>
        speedLimitAssetToApi(getSpeedLimitsAndRoadLinks(Set(assetId)).headOption.getOrElse(halt(NotFound("Asset not found"))))
      case _ =>
        val assetTypeId = getAssetTypeId(assetTypeName)
        AssetTypeInfo.apply(assetTypeId).geometryType match {
          case "linear" =>
            if(isDynamic(assetTypeId)) {
              dynamicAssetToApi(getLinearAssetsAndRoadLinks(assetTypeId, Set(assetId)).headOption.getOrElse(halt(NotFound("Asset not found"))))
            } else {
              linearAssetToApi(getLinearAssetsAndRoadLinks(assetTypeId, Set(assetId)).headOption.getOrElse(halt(NotFound("Asset not found"))))
            }
          case "point" => pointAssetToApi(getPointAssetById(assetTypeId, assetId), assetTypeId)
          case _ =>
        }
    }
  }

  //Description of Api entry point to create assets on specific municipality
  val createAssetOnMunicipality =
    (apiOperation[List[ExampleItemResponseOnCreate]]("createAssetOnMunicipality")
      .parameters(
        pathParam[String]("assetType").description("Asset type name"),
        bodyParam[List[ExampleItemRequestOnCreate]]("NewAsset")
          .description(
            "Parameter: linkId / Type: number / Mandatory: yes / Description: The road link identifier\n" +
              "Parameter: startMeasure / Type: number / Mandatory: yes / Description: The start measure in meters, starting counting from the beginning of the road. \n" +
              "Parameter: endMeasure / Type: number / Mandatory: no (for point assets), yes (for linear assets) / Description: The end measure in meters, starting counting from the beginning of the road. \n" +
              "Parameter: sideCode / Type: number / Mandatory: yes / Description: The side code of the asset (see: Side Codes) Note: Side code not allowed on manoeuvre. \n" +
              "Parameter: name / Type: string / Mandatory: yes / Description: The name that identifies the property (Ex: lighting) \n" +
              "Parameter: value / Type: Any / Mandatory: yes / Description: The value or values of the specified property. The type depends on the asset."
          )
          .required
      )
      .responseMessages(
        ResponseMessage(401, "The authentication parameters are missing or are incorrect"),
        ResponseMessage(403, "The authenticated user doesn't have permissions to do this operation on that municipality."),
        ResponseMessage(404, "Municipality code or asset type not found."),
        ResponseMessage(422, "Some parameters were incorrect.")
      )
      tags "Municipality API"
      summary "Creates an asset of an asset type on the municipality code."
      authorizations "Contact your service provider for more information"
      description "Example URL: /api/municipality/lighting"
      )

  post("/:assetType", operation(createAssetOnMunicipality)) {
    contentType = formats("json")
    val assetTypeName = params("assetType")

    val body = parsedBody.extractOpt[Seq[JObject]].getOrElse(halt(BadRequest("Incorrect Json format, expected a Json Array")))
    body.map(bd => (bd \ "startMeasure").extractOrElse[Double](halt(BadRequest("Missing mandatory 'startMeasure' parameter"))))
    val linkIds = body.map(bd => (bd \ "linkId").extractOrElse[Long](halt(UnprocessableEntity("Missing mandatory 'linkId' parameter"))))
    validateJsonField(Seq("geometryTimestamp"), body)

    assetTypeName match {
      case "manoeuvre" =>
        validateJsonField(Seq("sideCode"), body)
        body.map(bd => (bd \ "endMeasure").extractOrElse[Double](halt(BadRequest("Missing mandatory 'endMeasure' parameter"))))

        val properties = body.map(bd => (bd \ "properties").extractOrElse[Seq[ManoeuvreProperties]](halt(UnprocessableEntity("Missing asset properties"))))
        if(properties.forall(_.isEmpty)) halt(UnprocessableEntity("Missing asset properties values"))

        val asset = createManoeuvreAssets(parsedBody)
        if(asset.nonEmpty) manoeuvreAssetsToApi(asset) else halt(InternalServerError("Asset not Updated"))

      case _ =>
        val assetTypeId = getAssetTypeId(assetTypeName)
        val properties = body.map(bd => (bd \ "properties").extractOrElse[Seq[AssetProperties]](halt(BadRequest("Missing asset properties"))))
        validateAssetProperties(assetTypeId,properties)
        linkIdValidation(linkIds.toSet)

        AssetTypeInfo.apply(assetTypeId).geometryType match {
          case "linear" =>
            body.map(bd => (bd \ "endMeasure").extractOrElse[Double](halt(BadRequest("Missing mandatory 'endMeasure' parameter"))))

            if (assetTypeId == SpeedLimitAsset.typeId) {
              val asset = createSpeedLimitAssets(assetTypeId, parsedBody)
              if (asset.nonEmpty) speedLimitAssetsToApi(asset) else halt(InternalServerError("Asset not Updated"))
            } else {
              val asset = createLinearAssets(assetTypeId, parsedBody, linkIds)

              if(isDynamic(assetTypeId)) {
                if (asset.nonEmpty) dynamicAssetsToApi(asset) else halt(InternalServerError("Asset not Updated"))
              } else {
                if (asset.nonEmpty) linearAssetsToApi(asset) else halt(InternalServerError("Asset not Updated"))
              }
            }

          case "point" =>
            validateJsonField(Seq("endMeasure"), body)
            pointAssetsToApi(createPointAssets(parsedBody, assetTypeId), assetTypeId)
          case _ =>
        }
    }
  }

  //Description of Api entry point to update assets on specific municipality by Id
  val updateAssetOnMunicipalityById =
    (apiOperation[ExampleItemResponseOnUpdate]("updateAssetOnMunicipalityById")
      .parameters(
        pathParam[String]("assetId").description("Id of the asset to be updated"),
        pathParam[String]("assetType").description("Asset type name"),
        bodyParam[ExampleItemRequestOnUpdate]("UpdateAsset")
          .description(
            "Parameter: linkId / Type: number / Mandatory: yes / Description: The road link identifier\n" +
              "Parameter: startMeasure / Type: number / Mandatory: yes / Description: The start measure in meters, starting counting from the beginning of the road. \n" +
              "Parameter: endMeasure / Type: number / Mandatory: no (for point assets), yes (ifor linear assets) / Description: The end measure in meters, starting counting from the beginning of the road. \n" +
              "Parameter: sideCode / Type: number / Mandatory: yes / Description: The side code of the asset (see: Side Codes) Note: Side code not allowed on manoeuvre. \n" +
              "Parameter: geometryTimestamp / Type: number / Mandatory: yes / Description: The unix time stamp of the road link geometry. \n" +
              "Parameter: name / Type: string / Mandatory: yes / Description: The name that identifies the property (Ex: lighting) \n" +
              "Parameter: value / Type: Any / Mandatory: yes / Description: The value or values of the specified property. The type depends on the asset."
          )
          .required
      )
      .responseMessages(
        ResponseMessage(401, "The authentication parameters are missing or are incorrect"),
        ResponseMessage(403, "The authenticated user doesn't have permissions to do this operation on that municipality."),
        ResponseMessage(404, "Municipality code or asset type not found."),
        ResponseMessage(422, "Some parameters were incorrect.")
      )
      tags "Municipality API"
      summary "Updates asset by municipality code, asset type and asset id."
      authorizations "Contact your service provider for more information"
      description "Example URL: /api/municipality/lighting/630280"
      )

  put("/:assetType/:assetId", operation(updateAssetOnMunicipalityById)) {
    contentType = formats("json")

    val assetType = params("assetType")
    val geometryTimestamp = (parsedBody \ "geometryTimestamp").extractOrElse[Long](halt(BadRequest("Missing mandatory 'geometryTimestamp' parameter")))
    val assetId = params("assetId").toInt

    assetType match {
      case "manoeuvre" =>
        val properties = (parsedBody \ "properties").extractOrElse[Seq[ManoeuvreProperties]](halt(BadRequest("Missing asset properties")))
        if (properties.isEmpty) halt(BadRequest("Missing asset properties values"))

        manoeuvreAssetToApi(updateManoeuvreAssets(assetId, parsedBody))
      case _ =>
        val assetTypeId = getAssetTypeId(assetType)
        val linkId = (parsedBody \ "linkId").extractOrElse[Long](halt(BadRequest("Missing mandatory 'linkId' parameter")))
        (parsedBody \ "startMeasure").extractOrElse[Double](halt(BadRequest("Missing mandatory 'startMeasure' parameter")))

        linkIdValidation(Set(linkId))

        val properties = (parsedBody \ "properties").extractOrElse[Seq[AssetProperties]](halt(BadRequest("Missing asset properties")))
        validateAssetProperties(assetTypeId,Seq(properties))

        AssetTypeInfo.apply(assetTypeId).geometryType match {
          case "linear" =>
            (parsedBody \ "sideCode").extractOrElse[Int](halt(BadRequest("Missing mandatory 'sideCode' parameter")))
            if (assetTypeId == SpeedLimitAsset.typeId) speedLimitAssetToApi(updateSpeedLimitAsset(assetId, parsedBody, linkId))
            else{
              val asset = updateLinearAsset(assetTypeId, assetId, parsedBody, linkId)
              if(isDynamic(assetTypeId)) {
                if (asset.nonEmpty) dynamicAssetToApi(asset.head) else halt(InternalServerError("Asset not Updated"))
              } else {
                if (asset.nonEmpty) linearAssetToApi(asset.head) else halt(InternalServerError("Asset not Updated"))
              }
            }
          case "point" => pointAssetToApi(updatePointAssets(parsedBody, assetTypeId, assetId), assetTypeId)
          case _ =>
        }
    }
  }

  //Description of Api entry point to delete assets according asset type name and Id
  val deleteAssetOnMunicipalityById =
    (apiOperation[Long]("deleteAssetOnMunicipalityById")
      .parameters(
        pathParam[String]("assetId").description("Id of the asset to be deleted"),
        pathParam[String]("assetTypeName").description("Asset type name")
      )
      .responseMessages(
        ResponseMessage(401, "The authentication parameters are missing or are incorrect"),
        ResponseMessage(403, "The authenticated user doesn't have permissions to do this operation on that municipality."),
        ResponseMessage(404, "Municipality code or asset type not found.")
      )
      tags "Municipality API"
      summary "Deletes asset by municipality code, asset type and asset id."
      authorizations "Contact your service provider for more information"
      description "Example URL: /api/municipality/lighting/630286"
      )

  delete("/:assetTypeName/:assetId", operation(deleteAssetOnMunicipalityById)){

    val assetTypeName: String = params("assetTypeName")
    val assetTypeId: Int = getAssetTypeId(assetTypeName)
    val assetId = params("assetId").toLong

    assetTypeName match {
      case "manoeuvre" =>
        val (asset, roadLinks) = getManoeuvreAndRoadLinks(Seq(assetId)).headOption.getOrElse(halt(NotFound("Asset not found")))
        linkIdValidation(roadLinks.map(_.linkId).toSet)
        manoeuvreService.deleteManoeuvre(user.username, assetId)

      case "speed_limit" =>
        val (asset, roadLink) = getSpeedLimitsAndRoadLinks(Set(assetId)).headOption.getOrElse(halt(NotFound("Asset not found")))
        linkIdValidation(Set(roadLink.linkId))
        linearAssetService.expireAsset(assetTypeId, assetId, user.username, expired = true).getOrElse("")

      case _ =>
        AssetTypeInfo.apply(assetTypeId).geometryType match {
          case "linear" =>
            val (asset, roadLink) = getLinearAssetsAndRoadLinks(assetTypeId, Set(assetId)).headOption.getOrElse(halt(NotFound("Asset not found")))
            linkIdValidation(Set(roadLink.linkId))
            linearAssetService.expireAsset(assetTypeId, assetId, user.username, expired = true).getOrElse("")
          case "point" =>
            val asset = getPointAssetById(assetTypeId, assetId)
            linkIdValidation(Set(asset.linkId))
            expirePointAsset(assetTypeId, assetId, user.username)
          case _ =>
        }
    }
  }

  put("/assetUpdateFromAWS"){
    try {
      val jsonDatasets: List[List[Array[Option[Any]]]] = parsedBody.extract[List[List[Array[Option[Any]]]]]

      val listDatasets = jsonDatasets.map(data =>
        Dataset(data.head(0).asInstanceOf[Option[String]], data.head(1).asInstanceOf[Option[Map[Any, Any]]], data.head(2).asInstanceOf[Option[List[List[BigInt]]]])
      )

      listDatasets.foreach(dataset =>
        AwsService.validateAndInsertDataset(dataset)
      )

       listDatasets.foreach(dataset =>
        AwsService.updateDataset(dataset)
      )

      var response = Map[String, String]()
      listDatasets.foreach(dataset =>
        response += (dataset.datasetId.get -> AwsService.getDatasetStatusById(dataset.datasetId.get))
      )

      response
    } catch {
      case _: ClassCastException => halt(BadRequest("Json has wrong format"))
      case _: SQLException => halt(BadRequest("DatasetId or featureId already received or not correctly defined"))
      case _: NumberFormatException => halt(BadRequest("FeatureId not correctly defined"))
      case _: Throwable => halt(BadRequest("Could not process Datasets. Verify information provided"))
    }

  }
}

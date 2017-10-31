package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.Digiroad2Context._
import fi.liikennevirasto.digiroad2.asset.Asset.DateTimePropertyFormat
import fi.liikennevirasto.digiroad2.asset.{AssetTypeInfo, _}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.user.UserProvider
import org.joda.time.DateTime
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.json4s._

case class NewNumericOrTextualValueAsset(linkId: Long, startMeasure: Double, endMeasure: Double, properties: Seq[AssetProperties], sideCode: Int, geometryTimestamp: Long)

class MunicipalityApi(val onOffLinearAssetService: OnOffLinearAssetService,
                      val roadLinkService: RoadLinkService,
                      val linearAssetService: LinearAssetService,
                      val assetService: AssetService
                      ) extends ScalatraServlet with JacksonJsonSupport with AuthenticationSupport {

  override def baseAuth: String = "municipality."
  override val realm: String = "Municipality API"

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
      case LitRoad.typeId  => onOffLinearAssetService
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

  private def extractNewLinearAssets(typeId: Int, value: JValue) = {
    typeId match {
      case _ => value.extractOpt[Seq[NewNumericOrTextualValueAsset]].getOrElse(Nil).map(x => NewLinearAsset(x.linkId, x.startMeasure, x.endMeasure, NumericValue(x.properties.map(_.value).head.toInt), x.sideCode , x.geometryTimestamp, None))
    }
  }

  def expireLinearAsset(assetTypeId: Int, assetId: Long, username: String, expired: Boolean): Option[Long] = {
    val usedService = verifyLinearServiceToUse(assetTypeId)
    usedService.expireAsset(assetTypeId, assetId, user.username, expired = true)
  }

  def getLinearAssetsByMunicipality(municipalityCode: Int, assetTypeId: Int): Seq[PersistedLinearAsset] ={
    val usedService = verifyLinearServiceToUse(assetTypeId)
    usedService.getAssetsByMunicipality(assetTypeId, municipalityCode).filterNot(_.id == 0).filterNot(_.expired)
  }

  def getLinearAssets(assetTypeId: Int, assetId: Int): Seq[PersistedLinearAsset] ={
    val usedService = verifyLinearServiceToUse(assetTypeId)
    usedService.getPersistedAssetsByIds(assetTypeId, Set(assetId.toLong)).filterNot(_.expired)
  }

  def updateLinearAssets(assetTypeId: Int, assetId: Int, parsedBody: JValue, linkId: Int): Seq[PersistedLinearAsset] ={
    val usedService = verifyLinearServiceToUse(assetTypeId)
    val assetById = usedService.getPersistedAssetsByIds(assetTypeId, Set(assetId.toLong)).filterNot(_.expired)
    if(assetById.isEmpty) halt(UnprocessableEntity("Asset not found."))
    val newAsset = extractNewLinearAssets(assetTypeId, parsedBody).head
    validateMeasures(Set(newAsset.startMeasure, newAsset.endMeasure), assetTypeId, linkId.toLong)
    validateSideCodes(Seq(newAsset))
    val oldAsset = usedService.getPersistedAssetsByIds(assetTypeId, Set(assetId.toLong)).head
    val newPersistedAsset = newAsset.vvhTimeStamp >= oldAsset.vvhTimeStamp match {
      case true =>
        usedService.updateWithNewMeasures(Seq(oldAsset.id), newAsset.value, user.username, Some(Measures(newAsset.startMeasure, newAsset.endMeasure)), Some(newAsset.vvhTimeStamp), Some(newAsset.sideCode))
      case _ => halt(UnprocessableEntity("The geometryTimestamp of the existing asset is newer than the given asset. Asset was not updated."))
    }
    newPersistedAsset.headOption match {
      case Some(asset) => Some(usedService.getPersistedAssetsByIds(assetTypeId, newPersistedAsset.toSet).filterNot(_.expired)) match {
        case Some(value) => value
        case _ => halt(NotFound("Asset not found"))
      }
      case _ => Seq.empty[PersistedLinearAsset]
    }
  }

  def createLinearAssets(municipalityCode: Int, assetTypeId: Int, parsedBody: JValue, linkId: Int, geometryTimestamp: Long): Seq[PersistedLinearAsset] ={
    val usedService = verifyLinearServiceToUse(assetTypeId)
    val newLinearAssets = extractNewLinearAssets(assetTypeId, parsedBody)
    validateSideCodes(newLinearAssets)
    newLinearAssets.foreach{
      newAsset => validateMeasures(Set(newAsset.startMeasure, newAsset.endMeasure), assetTypeId, linkId.toLong)
    }
    val assetsIds = usedService.create(newLinearAssets, assetTypeId, user.username, geometryTimestamp)
    Some(usedService.getPersistedAssetsByIds(assetTypeId, assetsIds.toSet).filterNot(_.expired)) match {
      case Some(value) => value
      case _ => halt(NotFound("Asset not found"))
    }
  }

//  def getPointAssetsByMunicipality(municipalityCode: Int, assetTypeId: Int): Unit ={
//    val usedService = verifyPointServiceToUse(assetTypeId)
//    usedService.getByMunicipality(municipalityCode)
//  }
//
//  def getPointAssets(assetTypeId: Int, assetId: Int): Unit ={
//    val usedService = verifyPointServiceToUse(assetTypeId)
//    usedService.getById(assetId.toLong)
//  }
//
//  def updatePointAssets(assetTypeId: Int, assetId: Int): Unit ={
//    val usedService = verifyPointServiceToUse(assetTypeId)
//
//  }
//
//  def createPointAssets(): Unit ={
//
//  }

  def linearAssetsToApi(linearAssets: Seq[PersistedLinearAsset], municipalityCode: Long): Seq[Map[String, Any]] = {
    linearAssets.map { asset =>
      Map("id" -> asset.id,
        "properties" -> Seq(Map("value" -> asset.value.map(_.toJson), "name" -> getAssetName(asset.typeId))),
        "linkId" -> asset.linkId,
        "startMeasure" -> asset.startMeasure,
        "endMeasure" -> asset.endMeasure,
        "sideCode" -> asset.sideCode,
        "modifiedAt" -> asset.modifiedDateTime,
        "createdAt" -> asset.createdDateTime,
        "geometryTimestamp" -> asset.vvhTimeStamp,
        "municipalityCode" -> municipalityCode
      )
    }
  }

  def getAssetTypeId(assetType: String): Int = {
    assetType match {
      case "lighting" => LitRoad.typeId
      case _ => halt(NotFound("Asset type not found"))
    }
  }

  def getAssetName(assetTypeId: Int): String = {
    assetTypeId match {
      case LitRoad.typeId   => "hasLighting"
      case _ => "asset"
    }
  }

  def extractPropertyValue(key: String, properties: Seq[AssetProperties], transformation: ( (String, Seq[String])=> Any)): (String, Any) = {
    val values = properties.filter { property => property.name == key }.map { property =>
      property.value
    }
    key -> transformation(key, values)
  }
  def propertyValuesToIntList(key: String, values: Seq[String]): Seq[Int] = { values.map(_.toInt) }
  def propertyValuesToString(key: String, values: Seq[String]): String = { values.mkString }
  def firstPropertyValueToInt(key: String, values: Seq[String]): Int = {
    try {
      values.headOption.map(_.toInt).get
    } catch {
      case e: Exception => halt(BadRequest(s"The property values for the property with name $key are not valid."))
    }
  }

  def validateAssetPropertyValue(assetTypeId: Int, properties:Seq[AssetProperties]):Unit = {
    assetTypeId match {
      case LitRoad.typeId  =>
        val value = extractPropertyValue(getAssetName(assetTypeId), properties, firstPropertyValueToInt)
        if(!Seq(0,1).contains(value._2))
          halt(BadRequest(s"The property values for the property with name lighting are not valid."))
      case _ => ("", None)
    }
  }

  def validateSideCodes(assets: Seq[NewLinearAsset]) : Unit = {
    assets.map( _.sideCode )
      .foreach( sc =>
        if( SideCode.apply(sc) == SideCode.Unknown)
          halt(UnprocessableEntity("Side code doesn't have a valid code."))
      )
  }

  def validateMeasures(measure: Set[Double], typeId: Int, linkId: Long): Unit = {
    val roadGeometry = roadLinkService.getRoadLinkGeometry(linkId).getOrElse(halt(UnprocessableEntity("Link id is not valid or doesn't exist.")))
    val roadLength = GeometryUtils.geometryLength(roadGeometry)
    measure.foreach( m => if(m < 0 || m > roadLength) halt(UnprocessableEntity("The measure can not be less than 0 and greater than the length of the road. ")))
    if(measure.head == measure.last)halt(UnprocessableEntity("The start and end measure should not be equal for a linear asset."))
  }

  get("/:municipalityCode/:assetType") {
    contentType = formats("json")

    if(!params.contains("municipalityCode"))
      halt(BadRequest("Missing municipality code."))
    val municipalityCode = params("municipalityCode").toInt
    val assetTypeId = getAssetTypeId(params("assetType"))

    assetService.getGeometryType(assetTypeId) match {
      case "linear" => linearAssetsToApi(getLinearAssetsByMunicipality(municipalityCode, assetTypeId), municipalityCode)
      //case "point" =>
      case _ =>
    }
  }

  get("/:municipalityCode/:assetType/:assetId") {
    contentType = formats("json")
    val assetId = params("assetId").toInt
    val assetTypeId = getAssetTypeId(params("assetType"))

    if(!params.contains("municipalityCode"))
      halt(BadRequest("Missing municipality code."))

    val municipalityCode = params("municipalityCode").toInt
    if(assetService.getMunicipalityById(municipalityCode).isEmpty)
      halt(NotFound("Municipality code not found."))

    assetService.getGeometryType(assetTypeId) match {
      case "linear" => linearAssetsToApi(getLinearAssets(assetTypeId, assetId), municipalityCode)
      //case "point" =>
      case _ =>
    }
  }

  post("/:municipalityCode/:assetType"){
    contentType = formats("json")
    val assetTypeId = getAssetTypeId(params("assetType"))

    if(!params.contains("municipalityCode"))
      halt(BadRequest("Missing municipality code."))

    val municipalityCode = params("municipalityCode").toInt
    if(assetService.getMunicipalityById(municipalityCode).isEmpty)
      halt(NotFound("Municipality code not found."))

    val linkId = (parsedBody \ "linkId").extractOrElse[Int](halt(UnprocessableEntity("Missing mandatory 'linkId' parameter")))
    val startMeasure = (parsedBody \ "startMeasure").extractOrElse[Double](halt(BadRequest("Missing mandatory 'startMeasure' parameter")))
    val geometryTimestamp = (parsedBody \ "geometryTimestamp").extractOrElse[Long](halt(BadRequest("Missing mandatory 'geometryTimestamp' parameter")))
    val properties = (parsedBody \ "properties").extractOrElse[Seq[AssetProperties]](halt(BadRequest("Missing asset properties")))

    if(properties.isEmpty)
      halt(BadRequest("Missing asset properties values"))

    validateAssetPropertyValue(assetTypeId, properties)

    assetService.getGeometryType(assetTypeId) match {
      case "linear" => linearAssetsToApi(createLinearAssets(municipalityCode, assetTypeId,  parsedBody, linkId, geometryTimestamp), municipalityCode)
      //case "point" =>
      case _ =>
    }
  }

  put("/:municipalityCode/:assetType/:assetId") {
    contentType = formats("json")

    if (!params.contains("municipalityCode"))
      halt(BadRequest("Missing municipality code."))

    val municipalityCode = params("municipalityCode").toInt
    if (assetService.getMunicipalityById(municipalityCode).isEmpty)
      halt(NotFound("Municipality code not found."))

    val assetTypeId = getAssetTypeId(params("assetType"))
    val linkId = (parsedBody \ "linkId").extractOrElse[Int](halt(BadRequest("Missing mandatory 'linkId' parameter")))
    (parsedBody \ "startMeasure").extractOrElse[Double](halt(BadRequest("Missing mandatory 'startMeasure' parameter")))
    val geometryTimestamp = (parsedBody \ "geometryTimestamp").extractOrElse[Long](halt(BadRequest("Missing mandatory 'geometryTimestamp' parameter")))
    val properties = (parsedBody \ "properties").extractOrElse[Seq[AssetProperties]](halt(BadRequest("Missing asset properties")))
    val assetId = params("assetId").toInt


    if (properties.isEmpty)
      halt(BadRequest("Missing asset properties values"))

    validateAssetPropertyValue(assetTypeId, properties)

    assetService.getGeometryType(assetTypeId) match {
      case "linear" => linearAssetsToApi(updateLinearAssets(assetTypeId, assetId, parsedBody, linkId), municipalityCode)
      //case "point" =>
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

    assetService.getGeometryType(assetType) match {
      case "linear" => expireLinearAsset(assetType, assetId, user.username, expired = true)
      //case "point" =>
      case _ =>

    }
  }
}
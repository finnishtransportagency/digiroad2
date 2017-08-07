package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.Asset.DateTimePropertyFormat
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.user.UserProvider
import org.joda.time.DateTime
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.json4s._

case class NewNumericOrTextualValueAsset(linkId: Long, startMeasure: Double, endMeasure: Double, properties: Seq[AssetProperties], sideCode: Int, geometryTimestamp: Long)

class MunicipalityApi(val linearAssetService: LinearAssetService, val roadLinkService: RoadLinkService) extends ScalatraServlet with JacksonJsonSupport with AuthenticationSupport {

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

  private def extractNewLinearAssets(typeId: Int, value: JValue) = {
    typeId match {
      case _ => value.extractOpt[Seq[NewNumericOrTextualValueAsset]].getOrElse(Nil).map(x => NewLinearAsset(x.linkId, x.startMeasure, x.endMeasure, NumericValue(x.properties.map(_.value).head.toInt), x.sideCode, x.geometryTimestamp, None))
    }
  }

  def linearAssetsToApi(linearAssets: Seq[PersistedLinearAsset]): Seq[Map[String, Any]] = {
    linearAssets.map { asset =>
      Map("id" -> asset.id,
        "properties" -> Seq(Map("value" -> asset.value.map(_.toJson), "name" -> getAssetName(asset.typeId))),
        "linkId" -> asset.linkId,
        "startMeasure" -> asset.startMeasure,
        "endMeasure" -> asset.endMeasure,
        "sideCode" -> asset.sideCode,
        "modifiedAt" -> asset.modifiedDateTime,
        "createdAt" -> asset.createdDateTime,
        "geometryTimestamp" -> asset.vvhTimeStamp
      )
    }
  }

  def getAssetTypeId(assetType: String): Int = {
    assetType match {
      case "lighting" => 100
      case _ => halt(NotFound("Asset type not found"))
    }
  }

  def getAssetName(assetTypeId: Int): String = {
    assetTypeId match {
      case 100 => "lighting"
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

  def validateAssetProperties(assetTypeId: Int, properties:Seq[AssetProperties]):Unit = {
    assetTypeId match {
      case 100 =>
        val value = extractPropertyValue("lighting", properties, firstPropertyValueToInt)
        if(value._2 != 1)
          halt(BadRequest(s"The property values for the property with name lighting are not valid."))
      case _ => ("", None)
    }
  }

  def validateSideCodes(assets: Seq[NewLinearAsset]) : Unit = {
    assets.map( _.sideCode )
      .foreach( sc =>
        if( !SideCode.values.map(_.value).contains(sc))
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
    val municipalityCode = params("municipalityCode").toInt
    val assetTypeId = getAssetTypeId(params("assetType"))
    linearAssetsToApi(linearAssetService.getAssetsByMunicipality(assetTypeId, municipalityCode).filterNot(_.id == 0))
  }

  get("/:municipalityCode/:assetType/:assetId") {
    contentType = formats("json")
    val assetId = params("assetId").toInt
    val assetTypeId = getAssetTypeId(params("assetType"))
    linearAssetsToApi(linearAssetService.getPersistedAssetsByIds(assetTypeId, Set(assetId)).filterNot(_.id == 0))
  }

  post("/:municipalityCode/:assetType"){
    contentType = formats("json")

    if(!params.contains("municipalityCode"))
      halt(BadRequest("Municipality code not found."))

    val assetTypeId = getAssetTypeId(params("assetType"))
    val municipalityCode = params("municipalityCode").toInt

    val linkId = (parsedBody \ "linkId").extractOrElse[Int](halt(UnprocessableEntity("Missing mandatory 'linkId' parameter")))
    val startMeasure = (parsedBody \ "startMeasure").extractOrElse[Double](halt(BadRequest("Missing mandatory 'startMeasure' parameter")))
    val geometryTimestamp = (parsedBody \ "geometryTimestamp").extractOrElse[Long](halt(BadRequest("Missing mandatory 'geometryTimestamp' parameter")))
    val properties = (parsedBody \ "properties").extractOrElse[Seq[AssetProperties]](halt(BadRequest("Missing asset properties")))

    if(properties.isEmpty)
      halt(BadRequest("Missing asset properties values"))

    validateAssetProperties(assetTypeId, properties)
    val newLinearAssets = extractNewLinearAssets(assetTypeId, parsedBody)
    validateSideCodes(newLinearAssets)
    newLinearAssets.foreach{
      newAsset => validateMeasures(Set(newAsset.startMeasure, newAsset.endMeasure), assetTypeId, linkId.toLong)
    }
    val assetsIds = linearAssetService.create(newLinearAssets, assetTypeId, user.username, geometryTimestamp)
    linearAssetsToApi(linearAssetService.getPersistedAssetsByIds(assetTypeId, assetsIds.toSet))
  }

  put("/:municipalityCode/:assetType/:assetId"){
    contentType = formats("json")

    if(!params.contains("municipalityCode"))
      halt(BadRequest("Municipality code not found."))

    val assetTypeId = getAssetTypeId(params("assetType"))
    val linkId = (parsedBody \ "linkId").extractOrElse[Int](halt(BadRequest("Missing mandatory 'linkId' parameter")))
    (parsedBody \ "startMeasure").extractOrElse[Double](halt(BadRequest("Missing mandatory 'startMeasure' parameter")))
    (parsedBody \ "geometryTimestamp").extractOrElse[Long](halt(BadRequest("Missing mandatory 'geometryTimestamp' parameter")))
    val properties = (parsedBody \ "properties").extractOrElse[Seq[AssetProperties]](halt(BadRequest("Criar mensagem ")))

    val assetById = linearAssetService.getPersistedAssetsByIds(assetTypeId, Set(params("assetId").toLong))
    if(assetById.isEmpty) halt(UnprocessableEntity("Asset not found."))
    val newAsset = extractNewLinearAssets(assetTypeId, parsedBody).head

    validateMeasures(Set(newAsset.startMeasure, newAsset.endMeasure), assetTypeId, linkId.toLong)
    validateAssetProperties(assetTypeId, properties)
    validateSideCodes(Seq(newAsset))

    val oldAsset = linearAssetService.getPersistedAssetsByLinkIds(assetTypeId, Seq(assetById.head.linkId)).head

    val newPersistedAsset = newAsset.vvhTimeStamp >= oldAsset.vvhTimeStamp match {
      case true => oldAsset.startMeasure != newAsset.startMeasure || oldAsset.endMeasure != newAsset.endMeasure match {
        case true => linearAssetService.updateWithNewMeasures(Seq(oldAsset.id), newAsset.value, user.username, Some(Measures(newAsset.startMeasure, newAsset.endMeasure)), Some(newAsset.vvhTimeStamp)).head
        case _ =>  linearAssetService.updateWithTimeStamp(Seq(oldAsset.id), newAsset.value, user.username, Some(newAsset.vvhTimeStamp)).head
      }
      case _ => halt(UnprocessableEntity("The geometryTimestamp of the existing asset is newer than the given asset."))
    }
    linearAssetsToApi(linearAssetService.getPersistedAssetsByIds(assetTypeId, Set(newPersistedAsset)))
  }

  delete("/:municipalityCode/:assetType/:assetId"){

    if(!params.contains("municipalityCode"))
      halt(BadRequest("Municipality code not found."))

    val assetType = getAssetTypeId(params("assetType"))
    val assetId = params("assetId").toLong

    val asset = linearAssetService.getPersistedAssetsByIds(assetType, Set(assetId))
    if(asset.isEmpty)
      halt(UnprocessableEntity("Asset not found."))

    linearAssetService.expireAsset(assetType, assetId, user.username, expired = true)

  }
}
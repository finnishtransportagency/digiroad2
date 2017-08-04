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

class MunicipalityApi(val linearAssetService: LinearAssetService) extends ScalatraServlet with JacksonJsonSupport with AuthenticationSupport {

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

  private def extractLinearAssetValue(value: JValue): Option[Value] = {
    val numericValue = value.extractOpt[Int]
    val textualParameter = value.extractOpt[String]

    numericValue
      .map(NumericValue)
      .orElse(textualParameter.map(TextualValue))
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

  def validateSideCodes(assets: Seq[NewLinearAsset]) = {
    assets.map( _.sideCode )
      .foreach( sc =>
        if( !SideCode.values.map(_.value).contains(sc))
          halt(UnprocessableEntity("Side code doesn't have a valid code."))
      )
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
    val assetTypeId = getAssetTypeId(params("assetType"))
    if(params.contains("municipalityCode"))halt(BadRequest("Missing mandatory 'linkId' parameter"))
    val municipalityCode = params("municipalityCode").toInt

    val linkId = (parsedBody \ "linkId").extractOrElse[Int](halt(BadRequest("Missing mandatory 'linkId' parameter")))
    val startMeasure = (parsedBody \ "startMeasure").extractOrElse[Double](halt(BadRequest("Missing mandatory 'startMeasure' parameter")))
    val geometryTimestamp = (parsedBody \ "geometryTimestamp").extractOrElse[Long](halt(BadRequest("Missing mandatory 'geometryTimestamp' parameter")))

    val newLinearAssets = extractNewLinearAssets(assetTypeId, parsedBody)
    validateSideCodes(newLinearAssets)
    linearAssetService.create(newLinearAssets, assetTypeId, user.username, geometryTimestamp)
  }

  put("/:municipalityCode/:assetType/:assetId"){
    contentType = formats("json")
    val linkId = (parsedBody \ "linkId").extractOrElse[Int](halt(BadRequest("Missing mandatory 'linkId' parameter")))
    val startMeasure = (parsedBody \ "startMeasure").extractOrElse[Double](halt(BadRequest("Missing mandatory 'startMeasure' parameter")))
    val geometryTimestamp = (parsedBody \ "geometryTimestamp").extractOrElse[Long](halt(BadRequest("Missing mandatory 'geometryTimestamp' parameter")))
    val assetTypeId = getAssetTypeId(params("assetType"))
    val assetId = params("assetId").toLong

  }

  delete("/:municipalityCode/:assetType/:assetId"){
    val municipalityCode = params("municipalityCode").toInt
    val assetType = params("assetType")
    val assetId = params("assetId").toInt
  }
}
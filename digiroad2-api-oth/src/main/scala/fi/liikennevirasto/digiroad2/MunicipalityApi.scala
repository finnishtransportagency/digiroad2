package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.{Modification, TimeStamps}
import fi.liikennevirasto.digiroad2.linearasset._
import org.json4s.{DefaultFormats, Formats, JValue}
import org.scalatra.{BadRequest, ScalatraServlet}
import org.scalatra.json.JacksonJsonSupport

class MunicipalityApi(val linearAssetService: LinearAssetService) extends ScalatraServlet with JacksonJsonSupport with AuthenticationSupport {

  override def baseAuth: String = "municipality."
  override val realm: String = "Municipality API"

  protected implicit val jsonFormats: Formats = DefaultFormats

  case class AssetTimeStamps(created: Modification, modified: Modification) extends TimeStamps
  case class NewNumericValueAsset(linkId: Long, startMeasure: Double, endMeasure: Double, value: Int, sideCode: Int)

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

  private def extractNewLinearAssets(typeId: Int, value: JValue): NewLinearAsset = {
//    typeId match {
//      case _ =>
//        value.extractOpt[Seq[NewNumericValueAsset]].get/*OrElse(Nil).map(x => NewLinearAsset(x.linkId, x.startMeasure, x.endMeasure, NumericValue(x.value), x.sideCode, 0, None))*/
//    }
    val linkId = (value \ "linkId").extract[Long]
    val startMeasure = (value \ "startMeasure").extract[Double]
    val geometryTimestamp = (value \ "geometryTimestamp").extract[Long]
    val endMeasure = (value \ "endMeasure").extract[Double]
    val assetValue = (value \ "value").extract[Int]
    val sideCode = (value \ "sideCode").extract[Int]

    NewLinearAsset(linkId, startMeasure, endMeasure, NumericValue(assetValue), sideCode, geometryTimestamp, None)

  }

  def linearAssetsToApi(linearAssets: Seq[PersistedLinearAsset]): Seq[Map[String, Any]] = {
    linearAssets.map { asset =>
      Map("id" -> asset.id,
        "value" -> asset.value.get.toJson,
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
      case _ => halt(BadRequest("Invalid asset type"))
    }
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
    val linkId = (parsedBody \ "linkId").extractOrElse[Int](halt(BadRequest("Missing mandatory 'linkId' parameter")))
    val startMeasure = (parsedBody \ "startMeasure").extractOrElse[Double](halt(BadRequest("Missing mandatory 'startMeasure' parameter")))
    val geometryTimestamp = (parsedBody \ "geometryTimestamp").extractOrElse[Long](halt(BadRequest("Missing mandatory 'geometryTimestamp' parameter")))
    val assetTypeId = getAssetTypeId(params("assetType"))
    val municipalityCode = params("municipalityCode").toInt
    val newLinearAssets = extractNewLinearAssets(assetTypeId, parsedBody)
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
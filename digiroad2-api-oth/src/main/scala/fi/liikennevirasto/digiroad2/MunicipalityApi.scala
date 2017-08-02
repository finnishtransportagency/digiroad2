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

  private def extractNewLinearAssets(typeId: Int, value: JValue) = {
    typeId match {
      case _ =>
        value.extractOpt[Seq[NewNumericValueAsset]].getOrElse(Nil).map(x => NewLinearAsset(x.linkId, x.startMeasure, x.endMeasure, NumericValue(x.value), x.sideCode, 0, None))
    }
  }

  def linearAssetsToApi(typeId: Int, municipalityNumber: Int): Seq[Map[String, Any]] = {
    def isUnknown(asset: PieceWiseLinearAsset) = asset.id == 0
    val linearAssets: Seq[PieceWiseLinearAsset] = linearAssetService.getByMunicipality(typeId, municipalityNumber).filterNot(isUnknown)
    linearAssets.map { asset =>
      Map("id" -> asset.id,
        "value" -> asset.value,
        "linkId" -> asset.linkId,
        "startMeasure" -> asset.startMeasure,
        "endMeasure" -> asset.endMeasure,
        "side_code" -> asset.sideCode.value,
        "modifiedAt" -> asset.modifiedDateTime,
        "createdAt" -> asset.createdDateTime,
        "geometryTimestamp" -> asset.vvhTimeStamp
      )
    }
  }

  def linearAssetsToApi(typeId: Int, municipalityNumber: Int, assetId: Int): Seq[Map[String, Any]] = {
    def isUnknown(asset: PieceWiseLinearAsset) = asset.id == 0
    val linearAssets: Seq[PieceWiseLinearAsset] = linearAssetService.getByMunicipality(typeId, municipalityNumber).filterNot(isUnknown)
    linearAssets.map { asset =>
      Map("id" -> asset.id,
        "value" -> asset.value,
        "linkId" -> asset.linkId,
        "startMeasure" -> asset.startMeasure,
        "endMeasure" -> asset.endMeasure,
        "side_code" -> asset.sideCode.value,
        "modifiedAt" -> asset.modifiedDateTime,
        "createdAt" -> asset.createdDateTime,
        "geometryTimestamp" -> asset.vvhTimeStamp
      )
    }
  }

  get("/:municipalityCode/:assetType") {
    contentType = formats("json")
    val municipalityCode = params("municipalityCode").toInt
    val assetType = params("assetType")
    assetType match {
      case "lighting" => linearAssetsToApi(100, municipalityCode)
      case _ => BadRequest("Invalid asset type")
    }
  }

  get("/:municipalityCode/:assetType/:assetId") {
    contentType = formats("json")
    val municipalityCode = params("municipalityCode").toInt
    val assetType = params("assetType")
    val assetId = params("assetId").toInt
    assetType match {
      case "lighting" => linearAssetsToApi(100, municipalityCode, assetId)
      case _ => BadRequest("Invalid asset type")
    }
  }

  post("/:municipalityCode/:assetType"){
    contentType = formats("json")
    val linkId = (parsedBody \ "linkId").extractOrElse[Int](halt(BadRequest("Missing mandatory 'linkId' parameter")))
    val startMeasure = (parsedBody \ "startMeasure").extractOrElse[Double](halt(BadRequest("Missing mandatory 'startMeasure' parameter")))
    val geometryTimestamp = (parsedBody \ "geometryTimestamp").extractOrElse[Long](halt(BadRequest("Missing mandatory 'geometryTimestamp' parameter")))

    val assetType = params("assetType")
    val municipalityCode = params("municipalityCode").toInt

    val newLinearAssets = assetType match {
      case "lighting" => extractNewLinearAssets(100, parsedBody)
      case _ => BadRequest("Invalid asset type")
    }
    println("-----breakpoint-----------")
    //linearassetservice.create(newLinearAssets, municipalityCode)

  }

  put("/:municipalityCode/:assetType/:assetId"){
    val linkId = (parsedBody \ "linkId").extractOrElse[Int](halt(BadRequest("Missing mandatory 'linkId' parameter")))
    val startMeasure = (parsedBody \ "startMeasure").extractOrElse[Double](halt(BadRequest("Missing mandatory 'startMeasure' parameter")))
    val geometryTimestamp = (parsedBody \ "geometryTimestamp").extractOrElse[Long](halt(BadRequest("Missing mandatory 'geometryTimestamp' parameter")))

    val assetType = params("assetType")
    val municipalityCode = params("municipalityCode").toInt

    val newLinearAssets = assetType match {
      case "lighting" => extractNewLinearAssets(100, parsedBody)
      case _ => BadRequest("Invalid asset type")
    }

  }

  delete("/:municipalityCode/:assetType/:assetId"){
    val municipalityCode = params("municipalityCode").toInt
    val assetType = params("assetType")
    val assetId = params("assetId").toInt

    val typeId = assetType match {
      case "lighting" => linearAssetsToApi(100, municipalityCode)
      case _ => BadRequest("Invalid asset type")
    }

    //TODO
    //linearservice.delete(municipalityCode, typeId, assetId)

  }


}
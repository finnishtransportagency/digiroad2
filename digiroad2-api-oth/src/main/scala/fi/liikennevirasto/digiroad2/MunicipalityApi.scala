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

case class NewNumericOrTextualValueAsset(linkId: Long, startMeasure: Double, endMeasure: Double, properties: Seq[AssetProperties], sideCode: Int)

class MunicipalityApi(val onOffLinearAssetService: OnOffLinearAssetService, val roadLinkService: RoadLinkService) extends ScalatraServlet with JacksonJsonSupport with AuthenticationSupport {

  override def baseAuth: String = "municipality."
  override val realm: String = "Municipality API"
  val lighting: Int = 100

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
      case _ => value.extractOpt[Seq[NewNumericOrTextualValueAsset]].getOrElse(Nil).map(x => NewLinearAsset(x.linkId, x.startMeasure, x.endMeasure, NumericValue(x.properties.map(_.value).head.toInt), x.sideCode ,0, None))
    }
  }

  private def extractLinearAssets(typeId: Int, value: JValue) = {
    typeId match {
      case `lighting` => value.extractOpt[NewNumericOrTextualValueAsset] match {
        case Some(v) => NewLinearAsset(v.linkId, v.startMeasure, v.endMeasure, NumericValue(v.properties.map(_.value).head.toInt), v.sideCode, 0, None)
      }
    }
  }

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
        "municipalityCode" -> municipalityCode,
        "assetType" -> asset.typeId
      )
    }
  }

  def getAssetTypeId(assetType: String): Int = {
    assetType match {
      case "lighting" => lighting
      case _ => halt(NotFound("Asset type not found"))
    }
  }

  def getAssetName(assetTypeId: Int): String = {
    assetTypeId match {
      case `lighting` => "lighting"
      case _ => "asset"
    }
  }

  def extractPropertyValue(key: String, properties: Seq[AssetProperties], transformation: ( (String, Seq[String])=> Any)): Any = {
    val values = properties.filter { property => property.name == key }.map { property =>
      property.value
    }
    transformation(key, values)
  }
  def propertyValuesToIntList(key: String, values: Seq[String]): Seq[Int] = { values.map(_.toInt) }
  def propertyValuesToString(key: String, values: Seq[String]): String = { values.mkString }
  def firstPropertyValueToInt(key: String, values: Seq[String]): Seq[Int] = {
    try {
      values.map(_.toInt)
    } catch {
      case e: Exception => halt(BadRequest(s"The property values for the property with name $key are not valid."))
    }
  }

  def validateAssetPropertyValue(assetTypeId: Int, properties:Seq[Seq[AssetProperties]]):Unit = {
    properties.foreach { prop =>
      assetTypeId match {
        case `lighting` =>
          extractPropertyValue("lighting", prop, firstPropertyValueToInt).asInstanceOf[Seq[Int]].foreach{ value =>
            if (!Seq(0, 1).contains(value))
              halt(BadRequest(s"The property values for the property with name hasLighting are not valid."))
          }
        case _ => ("", None)
      }
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
    if(onOffLinearAssetService.getMunicipalityById(municipalityCode).isEmpty)
      halt(NotFound("Municipality code not found."))

    val assetTypeId = getAssetTypeId(params("assetType"))
    linearAssetsToApi(onOffLinearAssetService.getAssetsByMunicipality(assetTypeId, municipalityCode).filterNot(_.id == 0).filterNot(_.expired), municipalityCode)
  }

  get("/:municipalityCode/:assetType/:assetId") {
    contentType = formats("json")
    val assetId = params("assetId").toInt
    val assetTypeId = getAssetTypeId(params("assetType"))

    if(!params.contains("municipalityCode"))
      halt(BadRequest("Missing municipality code."))

    val municipalityCode = params("municipalityCode").toInt
    if(onOffLinearAssetService.getMunicipalityById(municipalityCode).isEmpty)
      halt(NotFound("Municipality code not found."))

    linearAssetsToApi(onOffLinearAssetService.getPersistedAssetsByIds(assetTypeId, Set(assetId)).filterNot(_.expired), municipalityCode).headOption match {
      case Some(value) => value
      case _ => halt(NotFound("Asset not found"))
    }
  }

  post("/:municipalityCode/:assetType"){
    contentType = formats("json")
    val assetTypeId = getAssetTypeId(params("assetType"))

    if(!params.contains("municipalityCode"))
      halt(BadRequest("Missing municipality code."))

    val municipalityCode = params("municipalityCode").toInt
    if(onOffLinearAssetService.getMunicipalityById(municipalityCode).isEmpty)
      halt(NotFound("Municipality code not found."))

    val body = parsedBody.extractOpt[Seq[JObject]].getOrElse(Nil)
    val linkIds = body.map(bd => (bd\ "linkId").extractOrElse[Long](halt(UnprocessableEntity("Missing mandatory 'linkId' parameter"))))
    linkIds.map(linkId => roadLinkService.getRoadLinkGeometry(linkId).getOrElse(halt(UnprocessableEntity(s"Link id: $linkId is not valid or doesn't exist."))))
    body.map(bd => (bd\ "startMeasure").extractOrElse[Double](halt(BadRequest("Missing mandatory 'startMeasure' parameter"))))
    val properties = body.map(bd => (bd\ "properties").extractOrElse[Seq[AssetProperties]](halt(BadRequest("Missing asset properties"))))

    if(properties.isEmpty)
      halt(BadRequest("Missing asset properties values"))

    validateAssetPropertyValue(assetTypeId, properties)
    val newLinearAssets = extractNewLinearAssets(assetTypeId, parsedBody)
    validateSideCodes(newLinearAssets)
    newLinearAssets.foreach{
      newAsset => validateMeasures(Set(newAsset.startMeasure, newAsset.endMeasure), assetTypeId, newAsset.linkId)

    }
    val assetsIds = onOffLinearAssetService.create(newLinearAssets, assetTypeId, user.username)
    val assets = onOffLinearAssetService.getPersistedAssetsByIds(assetTypeId, assetsIds.toSet).filterNot(_.expired)
    if(assets.isEmpty)
      halt(NotFound("Asset not found"))
    linearAssetsToApi(assets, municipalityCode)
  }

  put("/:municipalityCode/:assetType/:assetId"){
    contentType = formats("json")

    if(!params.contains("municipalityCode"))
      halt(BadRequest("Missing municipality code."))

    val municipalityCode = params("municipalityCode").toInt
    if(onOffLinearAssetService.getMunicipalityById(municipalityCode).isEmpty)
      halt(NotFound("Municipality code not found."))

    val assetTypeId = getAssetTypeId(params("assetType"))
    val linkId = (parsedBody \ "linkId").extractOrElse[Int](halt(BadRequest("Missing mandatory 'linkId' parameter")))
    (parsedBody \ "startMeasure").extractOrElse[Double](halt(BadRequest("Missing mandatory 'startMeasure' parameter")))
    (parsedBody \ "geometryTimestamp").extractOrElse[Long](halt(BadRequest("Missing mandatory 'geometryTimestamp' parameter")))
    val properties = (parsedBody \ "properties").extractOrElse[Seq[AssetProperties]](halt(BadRequest("Missing asset properties")))

    if(properties.isEmpty)
      halt(BadRequest("Missing asset properties values"))

    val assetById = onOffLinearAssetService.getPersistedAssetsByIds(assetTypeId, Set(params("assetId").toLong)).filterNot(_.expired)
    if(assetById.isEmpty) halt(UnprocessableEntity("Asset not found."))
    val newAsset = extractLinearAssets(assetTypeId, parsedBody)

    validateMeasures(Set(newAsset.startMeasure, newAsset.endMeasure), assetTypeId, linkId.toLong)
    validateAssetPropertyValue(assetTypeId, Seq(properties))
    validateSideCodes(Seq(newAsset))

    val oldAsset = onOffLinearAssetService.getPersistedAssetsByIds(assetTypeId, Set(params("assetId").toLong)).head

    val newPersistedAsset = newAsset.vvhTimeStamp >= oldAsset.vvhTimeStamp match {
       case true =>
          onOffLinearAssetService.updateWithNewMeasures(Seq(oldAsset.id), newAsset.value, user.username, Some(Measures(newAsset.startMeasure, newAsset.endMeasure)), Some(newAsset.vvhTimeStamp), Some(newAsset.sideCode))
       case _ => halt(UnprocessableEntity("The geometryTimestamp of the existing asset is newer than the given asset. Asset was not updated."))
    }
    newPersistedAsset.headOption match {
      case Some(asset) => linearAssetsToApi(onOffLinearAssetService.getPersistedAssetsByIds(assetTypeId, newPersistedAsset.toSet).filterNot(_.expired), municipalityCode).headOption match {
        case Some(value) => value
        case _ => halt(NotFound("Asset not found"))
      }
      case _ => None
    }
  }

  delete("/:municipalityCode/:assetType/:assetId"){

    if(!params.contains("municipalityCode"))
      halt(BadRequest("Missing municipality code."))

    val municipalityCode = params("municipalityCode").toInt
    if(onOffLinearAssetService.getMunicipalityById(municipalityCode).isEmpty)
      halt(NotFound("Municipality code not found."))

    val assetType = getAssetTypeId(params("assetType"))
    val assetId = params("assetId").toLong

    val asset = onOffLinearAssetService.getPersistedAssetsByIds(assetType, Set(assetId)).filterNot(_.expired)
    if(asset.isEmpty)
      halt(UnprocessableEntity("Asset not found."))

    onOffLinearAssetService.expireAsset(assetType, assetId, user.username, expired = true)

  }
}
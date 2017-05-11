package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.linearasset.{MaintenanceRoad, PersistedLinearAsset, Properties}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport

class ServiceRoadAPI(val linearAssetService: LinearAssetOperations, val roadLinkService: RoadLinkService) extends ScalatraServlet with JacksonJsonSupport with AuthenticationSupport {

  override def baseAuth: String = "serviceRoad."
  override val realm: String = "Service Road API"

  private val Kayttooikeus = "huoltotie_kayttooikeus"
  private val Huoltovastuu = "huoltotie_huoltovastuu"
  private val Tiehoitokunta = "huoltotie_tiehoitokunta"
  private val Nimi = "huoltotie_nimi"
  private val Osoite = "huoltotie_osoite"
  private val Postinumero = "huoltotie_postinumero"
  private val Postitoimipaikka = "huoltotie_postitoimipaikka"
  private val Puh1 = "huoltotie_puh1"
  private val Puh2 = "huoltotie_puh2"
  private val Lisatieto = "huoltotie_lisatieto"

  protected implicit def jsonFormats: Formats = DefaultFormats
  val typeId = LinearAssetTypes.MaintenanceRoadAssetTypeId

  private val liviLinkID = "linkId"
  private val liviAssetID = "id"
  private val liviGeometry = "geometry"
  private val liviStartMeasure = "startMeasure"
  private val liviEndMeasure = "endMeasure"
  private val liviModifiedAt = "modifiedAt"
  private val liviGeneratedValue = "generatedValue"
  private val liviKayttooikeus = "kayttooikeus"
  private val liviHuoltovastuu = "huoltovastuu"
  private val liviTiehoitokunta = "tiehoitokunta"
  private val liviNimi = "nimi"
  private val liviOsoite = "osoite"
  private val liviPostinumero = "postinumero"
  private val liviPostitoimipaikka = "postitoimipaikka"
  private val liviPuh1 = "puh1"
  private val liviPuh2 = "puh2"
  private val liviLisatieto = "lisatieto"

  before() {
    basicAuth
  }

  get("/huoltotiet/:areaId"){
    contentType = formats("json")
    var areaId = params("areaId")
    val maintenanceAsset = linearAssetService.getActiveMaintenanceRoadByPolygon(areaId.toInt, typeId)
    createJson(maintenanceAsset)
  }

  private def createJson(maintenanceAsset: Seq[PersistedLinearAsset] ) = {
    val linkIdMap = maintenanceAsset.groupBy(_.linkId).mapValues(_.map(_.id))
    val roadLinks = roadLinkService.getRoadLinksByLinkIdsFromVVH(linkIdMap.keySet)

    maintenanceAsset.map { asset =>
      val geometry = roadLinks.find(_.linkId == asset.linkId) match {
        case Some(rl) => Some(GeometryUtils.truncateGeometry3D(rl.geometry, asset.startMeasure, asset.endMeasure))
        case _ => None
      }
      createMap(asset, asset.value.getOrElse(MaintenanceRoad(Seq())).asInstanceOf[MaintenanceRoad].maintenanceRoad, geometry)
    }
  }

  private def createMap(asset: PersistedLinearAsset, maintenanceRoad: Seq[Properties], geometry: Option[Seq[Point]]): Map[String, Any] = {
    Map(
      liviLinkID -> asset.linkId,
      liviAssetID -> asset.id,
      liviGeometry -> geometry,
      liviStartMeasure -> asset.startMeasure,
      liviEndMeasure -> asset.endMeasure,
      liviModifiedAt ->  convertToDate(if(asset.modifiedDateTime.isEmpty) asset.modifiedDateTime else asset.createdDateTime),
      liviGeneratedValue -> (if(asset.modifiedBy.nonEmpty) getModifiedByValue(asset.modifiedBy) else getModifiedByValue(asset.createdBy)),
      liviKayttooikeus -> getFieldValueInt(maintenanceRoad, Kayttooikeus),
      liviHuoltovastuu -> getFieldValueInt(maintenanceRoad, Huoltovastuu),
      liviTiehoitokunta -> getFieldValue(maintenanceRoad, Tiehoitokunta),
      liviNimi -> getFieldValue(maintenanceRoad, Nimi),
      liviOsoite -> getFieldValue(maintenanceRoad, Osoite),
      liviPostinumero -> getFieldValue(maintenanceRoad, Postinumero),
      liviPostitoimipaikka -> getFieldValue(maintenanceRoad, Postitoimipaikka),
      liviPuh1 -> getFieldValue(maintenanceRoad, Puh1),
      liviPuh2 -> getFieldValue(maintenanceRoad, Puh2),
      liviLisatieto -> getFieldValue(maintenanceRoad, Lisatieto)
    )
  }

  private def getFieldValue(properties: Seq[Properties], publicId: String): Option[String] = {
    properties.find(p => p.publicId == publicId).map(_.value)
  }

  private def getFieldValueInt(properties: Seq[Properties], publicId: String): Option[Int] = {
    properties.find(p => p.publicId == publicId).map(_.value.toInt)
  }

  private def convertToDate(value: Option[DateTime]): Option[String] = {
    value match {
      case Some(date) =>  Some(DateTimeFormat.forPattern("dd.MM.yyyy HH:mm:ss").print(date))
      case _ => None
    }
  }

  def getModifiedByValue(modifiedValue: Option[String]) : Int = {
    modifiedValue match {
      case Some(value) if value == "vvh_generated" => 1
      case _ => 0
    }
  }
}

package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.linearasset.{MaintenanceRoad, PersistedLinearAsset, Properties}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport

class ServiceRoadAPI(val linearAssetService: LinearAssetOperations, val roadLinkService: RoadLinkService) extends ScalatraServlet with JacksonJsonSupport with AuthenticationSupport {
  private val dateFormat = "yyyy-MM-dd"

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

  private val trLinkID = "linkId"
  private val trAssetID = "assetId"
  private val trGeometry = "geometry"
  private val trStartMeasure = "startMeasure"
  private val trEndMeasure = "endMeasure"
  private val trModifiedAt = "modifiedDate"
  private val trModifiedBy = "modifiedBy"
  private val trKayttooikeus = "kayttooikeus"
  private val trHuoltovastuu = "huoltovastuu"
  private val trTiehoitokunta = "tiehoitokunta"
  private val trNimi = "nimi"
  private val trOsoite = "osoite"
  private val trPostinumero = "postinumero"
  private val trPostitoimipaikka = "postitoimipaikka"
  private val trPuh1 = "puh1"
  private val trPuh2 = "puh2"
  private val trLisatieto = "lisatieto"

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
      trLinkID -> asset.linkId,
      trAssetID -> asset.id,
      trGeometry -> geometry,
      trStartMeasure -> asset.startMeasure,
      trEndMeasure -> asset.endMeasure,
      trModifiedAt ->  convertToDate(asset.modifiedDateTime),
      trModifiedBy -> asset.modifiedBy,
      trKayttooikeus -> getFieldValue(maintenanceRoad, Kayttooikeus),
      trHuoltovastuu -> getFieldValue(maintenanceRoad, Huoltovastuu),
      trTiehoitokunta -> getFieldValue(maintenanceRoad, Tiehoitokunta),
      trNimi -> getFieldValue(maintenanceRoad, Nimi),
      trOsoite -> getFieldValue(maintenanceRoad, Osoite),
      trPostinumero -> getFieldValue(maintenanceRoad, Postinumero),
      trPostitoimipaikka -> getFieldValue(maintenanceRoad, Postitoimipaikka),
      trPuh1 -> getFieldValue(maintenanceRoad, Puh1),
      trPuh2 -> getFieldValue(maintenanceRoad, Puh2),
      trLisatieto -> getFieldValue(maintenanceRoad, Lisatieto)
    )
  }

  private def getFieldValue(properties: Seq[Properties], publicId: String): Option[String] = {
    properties.find(p => p.publicId == publicId).map(_.value)
  }

  private def convertToDate(value: Option[DateTime]): Option[String] = {
    value match {
      case Some(date) =>  Some(DateTimeFormat.forPattern("yyyy-MM-dd").print(date))
      case _ => None
    }
  }
}

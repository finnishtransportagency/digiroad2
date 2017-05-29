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

  protected implicit def jsonFormats: Formats = DefaultFormats

  before() {
    basicAuth
  }

  get("/huoltotiet/:areaId"){
    contentType = formats("json")
    var areaId = params("areaId")
    val maintenanceAsset = linearAssetService.getActiveMaintenanceRoadByPolygon(areaId.toInt, LinearAssetTypes.MaintenanceRoadAssetTypeId)
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
      "linkId" -> asset.linkId,
      "id" -> asset.id,
      "geometry" -> geometry,
      "startMeasure" -> asset.startMeasure,
      "endMeasure" -> asset.endMeasure,
      "modifiedAt" ->  convertToDate(if(asset.modifiedDateTime.nonEmpty) asset.modifiedDateTime else asset.createdDateTime),
      "generatedValue" -> (if(asset.modifiedBy.nonEmpty) getModifiedByValue(asset.modifiedBy) else getModifiedByValue(asset.createdBy)),
      "kayttooikeus" -> getFieldValueInt(maintenanceRoad, "huoltotie_kayttooikeus"),
      "huoltovastuu" -> getFieldValueInt(maintenanceRoad, "huoltotie_huoltovastuu"),
      "tiehoitokunta" -> getFieldValue(maintenanceRoad, "huoltotie_tiehoitokunta"),
      "nimi" -> getFieldValue(maintenanceRoad, "huoltotie_nimi"),
      "osoite" -> getFieldValue(maintenanceRoad, "huoltotie_osoite"),
      "postinumero" -> getFieldValue(maintenanceRoad, "huoltotie_postinumero"),
      "postitoimipaikka" -> getFieldValue(maintenanceRoad, "huoltotie_postitoimipaikka"),
      "puh1" -> getFieldValue(maintenanceRoad, "huoltotie_puh1"),
      "puh2" -> getFieldValue(maintenanceRoad, "huoltotie_puh2"),
      "lisatieto" -> getFieldValue(maintenanceRoad, "huoltotie_lisatieto")
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

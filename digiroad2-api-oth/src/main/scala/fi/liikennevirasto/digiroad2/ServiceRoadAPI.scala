package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.linearasset.{MaintenanceRoad, PersistedLinearAsset, Properties, RoadLink}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.{BadRequest, ScalatraServlet}
import org.scalatra.json.JacksonJsonSupport

class ServiceRoadAPI(val maintenanceService: MaintenanceService, val roadLinkService: RoadLinkService) extends ScalatraServlet with JacksonJsonSupport with AuthenticationSupport {

  override def baseAuth: String = "serviceRoad."
  override val realm: String = "Service Road API"

  val MAX_BOUNDING_BOX = 100000000

  protected implicit def jsonFormats: Formats = DefaultFormats

  before() {
    basicAuth
  }

  get("/huoltotiet"){
    contentType = formats("json")
    val bbox = params.get("boundingBox").map(constructBoundingRectangle).getOrElse(halt(BadRequest("Bounding box was missing")))
    validateBoundingBox(bbox)


  }

  get("/huoltotiet/:areaId"){
    contentType = formats("json")
    var areaId = params("areaId")
    val maintenanceAsset = maintenanceService.getActiveMaintenanceRoadByPolygon(areaId.toInt)
    createJson(maintenanceAsset)
  }

  private def createJson(maintenanceAsset: Seq[PersistedLinearAsset]) = {
    val linkIdMap = maintenanceAsset.groupBy(_.linkId).mapValues(_.map(_.id))
    val roadLinks = roadLinkService.getRoadLinksByLinkIdsFromVVH(linkIdMap.keySet)

    maintenanceAsset.map { asset =>
      val geometry = roadLinks.find(_.linkId == asset.linkId) match {
        case Some(rl) => Some(GeometryUtils.truncateGeometry3D(rl.geometry, asset.startMeasure, asset.endMeasure))
        case _ => None
      }
      createMap(asset, asset.value.getOrElse(MaintenanceRoad(Seq())).asInstanceOf[MaintenanceRoad].properties, geometry)
    }
  }

  private def createGeoJson(maintenanceAsset: Seq[PersistedLinearAsset]) = {
    val linkIdMap = maintenanceAsset.groupBy(_.linkId).mapValues(_.map(_.id))
    val roadLinks = roadLinkService.getRoadLinksByLinkIdsFromVVH(linkIdMap.keySet)

    maintenanceAsset.flatMap { asset =>
      roadLinks.find(_.linkId == asset.linkId).map{
        roadlink =>
          val geometry = GeometryUtils.truncateGeometry2D(roadlink.geometry, asset.startMeasure, asset.endMeasure)
          Map(
            "type" -> "Feature",
            "geometry" -> getLineStringGeometry(geometry),
            "properties" -> getProperties(asset, asset.value.getOrElse(MaintenanceRoad(Seq())).asInstanceOf[MaintenanceRoad].properties, roadlink)
          )
      }
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

  private def getLineStringGeometry(geometry: Seq[Point]) = {
    Map(
      "type" -> "LineString",
      "coordinates" -> geometry.map(p => Seq(p.x, p.y))
    )
  }

  private def getProperties(asset: PersistedLinearAsset, properties: Seq[Properties], roadLink: RoadLink) ={
    Map (
      "id" -> asset.id,
      //TODO check if the area is really needed
      //"areaId" -> asset.a,
      "linkId" -> asset.linkId,
      "mmlId" -> roadLink.attributes.get("MTKID"),
      "verticalLevel" -> roadLink.verticalLevel,
      "startMeasure" -> asset.startMeasure,
      "endMeasure" -> asset.endMeasure,
      "modifiedAt" -> convertToDate(if(asset.modifiedDateTime.nonEmpty) asset.modifiedDateTime else asset.createdDateTime),
      "modifiedBy" -> (if(asset.modifiedBy.nonEmpty) asset.modifiedBy else asset.createdBy),
      "access" -> getFieldValueInt(properties, "huoltotie_kayttooikeus"),
      //"accessDesc" -> "",
      "maintainer" -> getFieldValueInt(properties, "huoltotie_huoltovastuu"),
      //"maintainerDesc" -> "",
      "maintainerName" -> getFieldValue(properties, "huoltotie_tiehoitokunta"),
      "person" -> getFieldValue(properties, "huoltotie_nimi"),
      "address" -> getFieldValue(properties, "huoltotie_osoite"),
      "zipCode" -> getFieldValue(properties, "huoltotie_postinumero"),
      "city" -> getFieldValue(properties, "huoltotie_postitoimipaikka"),
      "phone1" -> getFieldValue(properties, "huoltotie_puh1"),
      "phone2" -> getFieldValue(properties, "huoltotie_puh2"),
      "addInfo" -> getFieldValue(properties, "huoltotie_lisatieto")
    )
  }

  private def convertToDate(value: Option[DateTime]): Option[String] = {
    value match {
      case Some(date) =>  Some(DateTimeFormat.forPattern("dd.MM.yyyy HH:mm:ss").print(date))
      case _ => None
    }
  }

  private def validateBoundingBox(bbox: BoundingRectangle): Unit = {
    val leftBottom = bbox.leftBottom
    val rightTop = bbox.rightTop
    val width = Math.abs(rightTop.x - leftBottom.x).toLong
    val height = Math.abs(rightTop.y - leftBottom.y).toLong
    if ((width * height) > MAX_BOUNDING_BOX) {
      halt(BadRequest("Bounding box was too big: " + bbox))
    }
  }

  private def constructBoundingRectangle(bbox: String) = {
    val bboxList = bbox.split(",").map(_.toDouble)
    BoundingRectangle(Point(bboxList(0), bboxList(1)), Point(bboxList(2), bboxList(3)))
  }

  def getModifiedByValue(modifiedValue: Option[String]) : Int = {
    modifiedValue match {
      case Some(value) if value == "vvh_generated" => 1
      case _ => 0
    }
  }
}

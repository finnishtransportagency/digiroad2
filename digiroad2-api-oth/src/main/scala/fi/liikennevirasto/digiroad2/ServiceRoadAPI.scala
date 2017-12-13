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
    createGeoJson(maintenanceService.getAllByBoundingBox(bbox))
  }

  get("/huoltotiet/:areaId"){
    contentType = formats("json")
    var areaId = params("areaId")
    val maintenanceAssets = maintenanceService.getActiveMaintenanceRoadByPolygon(areaId.toInt)
    val linkIdMap = maintenanceAssets.groupBy(_.linkId).mapValues(_.map(_.id))
    val roadLinks = roadLinkService.getRoadLinksByLinkIdsFromVVH(linkIdMap.keySet)

    createGeoJson(maintenanceAssets.flatMap{
      case maintenanceAsset =>
        roadLinks.find(_.linkId == maintenanceAsset.linkId).map {
          case roadLink =>
            (maintenanceAsset, roadLink)
        }
    })
  }

  private def createGeoJson(maintenanceAsset: Seq[(PersistedLinearAsset, RoadLink)]) = {
    maintenanceAsset.map {
      case (asset, roadlink) =>
        val geometry = GeometryUtils.truncateGeometry3D(roadlink.geometry, asset.startMeasure, asset.endMeasure)
        Map(
          "type" -> "Feature",
          "geometry" -> getLineStringGeometry(geometry),
          "properties" -> getProperties(asset, asset.value.getOrElse(MaintenanceRoad(Seq())).asInstanceOf[MaintenanceRoad].properties, roadlink)
        )
    }
  }

  private def getProperties(asset: PersistedLinearAsset, properties: Seq[Properties], roadLink: RoadLink) ={
    val maintainerId = getFieldValueInt(properties, "huoltotie_huoltovastuu")
    val accessId = getFieldValueInt(properties, "huoltotie_kayttooikeus")
    Map (
      "id" -> asset.id,
      "linkId" -> asset.linkId,
      "mmlId" -> roadLink.attributes.get("MTKID"),
      "verticalLevel" -> roadLink.verticalLevel,
      "startMeasure" -> asset.startMeasure,
      "endMeasure" -> asset.endMeasure,
      "modifiedAt" -> convertToDate(if(asset.modifiedDateTime.nonEmpty) asset.modifiedDateTime else asset.createdDateTime),
      "modifiedBy" -> (if(asset.modifiedBy.nonEmpty) asset.modifiedBy else asset.createdBy),
      "access" -> accessId,
      "accessDesc" -> getAccessDescription(accessId),
      "maintainer" -> maintainerId,
      "maintainerDesc" -> getMaintainerDescription(maintainerId),
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

  private def getFieldValue(properties: Seq[Properties], publicId: String): Option[String] = {
    properties.find(p => p.publicId == publicId).map(_.value)
  }

  private def getFieldValueInt(properties: Seq[Properties], publicId: String): Option[Int] = {
    properties.find(p => p.publicId == publicId).map(_.value.toInt)
  }

  private def getLineStringGeometry(geometry: Seq[Point]) = {
    Map(
      "type" -> "LineString",
      "coordinates" -> geometry.map(p => Seq(p.x, p.y, p.z))
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

  private def getModifiedByValue(modifiedValue: Option[String]) : Int = {
    modifiedValue match {
      case Some(value) if value == "vvh_generated" => 1
      case _ => 0
    }
  }

  private def getAccessDescription(accessValue: Option[Int]): Option[String] ={
    accessValue.map {
      case 1 => "Tieoikeus"
      case 2 => "Tiekunnan osakkuus"
      case 3 => "LiVin hallinnoimalla maa-alueella"
      case 4 => "Kevyen liikenteen väylä"
      case _ => "Tuntematon"
    }
  }

  private def getMaintainerDescription(maintainerValue: Option[Int]): Option[String] ={
    maintainerValue.map {
      case 1 => "LiVi"
      case 2 => "Muu"
      case _ => "Ei tietoa"
    }
  }
}

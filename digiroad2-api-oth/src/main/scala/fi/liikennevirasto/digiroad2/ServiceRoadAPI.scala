package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, DynamicProperty}
import fi.liikennevirasto.digiroad2.linearasset.{DynamicAssetValue, DynamicValue, PersistedLinearAsset, RoadLink}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.MaintenanceService
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.{BadRequest, ScalatraServlet}
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger.{Swagger, SwaggerSupport}

case class serviceRoadApiResponseOnGetExample(`type`: String, geometry: geometryFields, properties: propertiesFields)
case class geometryFields(`type`: String, coordinates: Seq[Seq[(Double, Double, Double)]])
case class propertiesFields(
                             id: Long,
                             areaId: Int,
                             linkId: Int,
                             mmlId: Int,
                             verticalLevel: Int,
                             startMeasure: Double,
                             endMeasure: Double,
                             modifiedAt: DateTime,
                             modifiedBy: String,
                             access: Int,
                             accessDesc: String,
                             maintainer: Int,
                             maintainerDesc: String,
                             maintainerName: String,
                             person: String,
                             address: String,
                             zipCode: String,
                             city: String,
                             phone1: String,
                             phone2: String,
                             addInfo: String
                           )

class ServiceRoadAPI(val maintenanceService: MaintenanceService, val roadLinkService: RoadLinkService, implicit val swagger: Swagger) extends ScalatraServlet with JacksonJsonSupport with SwaggerSupport {

  protected val applicationDescription = "Service Road API "

  val MAX_BOUNDING_BOX = 100000000

  protected implicit def jsonFormats: Formats = DefaultFormats

  val getServiceRoadByBoundingBox =
    (apiOperation[List[serviceRoadApiResponseOnGetExample]]("getServiceRoadByBoundingBox")
      tags "Service Road API (Huoltotie API)"
      summary "Returns all Service Road assets inside bounding box. Can be used to get all assets on the UI map area."
      parameter queryParam[String]("boundingBox").description("The bounding box is used to search assets inside ít, is defined with coordinates of top left and bottom right corner.")
      description
      "Bounding box is defined with coordinates of top left and bottom right corner \n" +
        "URL: /digiroad/api/livi/huoltotiet/?boundingBox={x1},{y1},{x2},{y2} \n" +
        "Example: \n https://extranet.liikennevirasto.fi/digiroad/api/livi/huoltotiet?boundingBox=399559.02383961395,6856819.997802734,401097.02383961395,6858507.997802734"
      )


  get("/huoltotiet", operation(getServiceRoadByBoundingBox)){
    contentType = formats("json")
    val bbox = params.get("boundingBox").map(constructBoundingRectangle).getOrElse(halt(BadRequest("Bounding box was missing")))
    validateBoundingBox(bbox)
    createGeoJson(maintenanceService.getAllByBoundingBox(bbox))
  }

  val getServiceRoadByAreaId =
    (apiOperation[List[serviceRoadApiResponseOnGetExample]]("getServiceRoadByAreaId")
      tags "Service Road API (Huoltotie API)"
      summary "Returns all Huoltotie assets inside service area."
      parameter pathParam[String]("areaId").description("Area id refers to the area where the search is going to be done.")
      description
      "Service areas are a polygonal area defined in OTH. \n" +
        "ServiceAreaId is an integer between 1-12. \n" +
        "URL: /digiroad/api/livi/huoltotiet/{serviceAreaId} \n" +
        "Example: \n https://extranet.liikennevirasto.fi/digiroad/api/livi/huoltotiet/10"
      )



  get("/huoltotiet/:areaId", operation(getServiceRoadByAreaId)){
    contentType = formats("json")
    var areaId = params("areaId")
    val maintenanceAssets = maintenanceService.getActiveMaintenanceRoadByPolygon(areaId.toInt)
    val linkIdMap = maintenanceAssets.groupBy(_.linkId).mapValues(_.map(_.id))
    val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(linkIdMap.keySet)

    createGeoJson(maintenanceAssets.flatMap{
       maintenanceAsset =>
        roadLinks.find(_.linkId == maintenanceAsset.linkId).map {
          roadLink =>
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
          "properties" -> getProperties(asset, asset.value.getOrElse(DynamicValue(DynamicAssetValue(Seq()))).asInstanceOf[DynamicValue].value.properties, roadlink)
        )
    }
  }

  private def getProperties(asset: PersistedLinearAsset, properties: Seq[DynamicProperty], roadLink: RoadLink) ={
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

  private def getFieldValue(properties: Seq[DynamicProperty], publicId: String): Option[String] = {
    properties.find(p => p.publicId == publicId).flatMap(_.values.headOption.map(_.value.toString))
  }

  private def getFieldValueInt(properties: Seq[DynamicProperty], publicId: String): Option[Int] = {
    properties.find(p => p.publicId == publicId).flatMap(_.values.headOption.map(_.value.toString.toInt))
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

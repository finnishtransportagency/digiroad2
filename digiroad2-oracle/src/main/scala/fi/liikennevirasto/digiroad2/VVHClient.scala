package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset._
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.json4s._
import org.json4s.jackson.JsonMethods._
import java.net.URLEncoder

sealed trait FeatureClass
object FeatureClass {
  case object TractorRoad extends FeatureClass
  case object DrivePath extends FeatureClass
  case object CycleOrPedestrianPath extends FeatureClass
  case object AllOthers extends FeatureClass
}

case class VVHRoadlink(mmlId: Long, municipalityCode: Int, geometry: Seq[Point],
                      administrativeClass: AdministrativeClass, trafficDirection: TrafficDirection,
                      featureClass: FeatureClass)

class VVHClient(hostname: String) {
  protected implicit val jsonFormats: Formats = DefaultFormats

  private def layerDefinition(municipalities: Set[Int]): String = {
    val definitionStart = "[{"
    val layerSelection = """"layerId":0,"""
    val municipalityFilter =
      if(municipalities.isEmpty) {
        ""
      } else {
        val municipalityQuery = municipalities.tail.foldLeft("MUNICIPALITYCODE=" + municipalities.head){ (acc, m) => acc + " or MUNICIPALITYCODE=" + m }
        s""""where":"$municipalityQuery","""
      }
    val fieldSelection = s""""outFields":"MTKID,MUNICIPALITYCODE,MTKCLASS,ADMINCLASS,DIRECTIONTYPE,CONSTRUCTIONTYPE""""
    val definitionEnd = "}]"
    definitionStart + layerSelection + municipalityFilter + fieldSelection + definitionEnd
  }

  def fetchVVHRoadlinks(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[VVHRoadlink] = {
    val definition = layerDefinition(municipalities)
    val encodedLayerDefinition = URLEncoder.encode(definition, "UTF-8")
    val url = "http://" + hostname + "/arcgis/rest/services/VVH_OTH/Roadlink_data/FeatureServer/query?" +
      s"layerDefs=$encodedLayerDefinition&geometry=" + bounds.leftBottom.x + "," + bounds.leftBottom.y + "," + bounds.rightTop.x + "," + bounds.rightTop.y +
      "&geometryType=esriGeometryEnvelope&spatialRel=esriSpatialRelIntersects&returnGeometry=true&geometryPrecision=3&f=pjson"

    fetchVVHFeatures(url).map(extractVVHFeature)
  }

  def fetchByMunicipality(municipality: Int): Seq[VVHRoadlink] = {
    val definition = layerDefinition(Set(municipality))
    val encodedLayerDefinition = URLEncoder.encode(definition, "UTF-8")
    val url = "http://" + hostname + "/arcgis/rest/services/VVH_OTH/Roadlink_data/FeatureServer/query?" +
      s"layerDefs=$encodedLayerDefinition&returnGeometry=true&geometryPrecision=3&f=pjson"

    fetchVVHFeatures(url).map(extractVVHFeature)
  }

  def fetchVVHRoadlink(mmlId: Long): Option[VVHRoadlink] = fetchVVHRoadlinks(Seq(mmlId)).headOption

  def fetchVVHRoadlinks(mmlIds: Seq[Long]): Seq[VVHRoadlink] = {
    val mmlIdsStr = mmlIds.mkString(",")
    val layerDefs = URLEncoder.encode(s"""{"0":"MTKID IN ($mmlIdsStr)"}""", "UTF-8")
    val url = "http://" + hostname + "/arcgis/rest/services/VVH_OTH/Roadlink_data/FeatureServer/query?" +
      s"layerDefs=$layerDefs&returnGeometry=true&geometryPrecision=3&f=pjson"

    fetchVVHFeatures(url).map(extractVVHFeature)
  }

  private def fetchVVHFeatures(url: String): List[Map[String, Any]] = {
    val request = new HttpGet(url)
    val client = HttpClientBuilder.create().build()
    val response = client.execute(request)
    val content = parse(StreamInput(response.getEntity.getContent)).values.asInstanceOf[Map[String, Any]]
    val layers = content("layers").asInstanceOf[List[Map[String, Any]]]
    layers
      .find(map => { map.contains("features") })
      .get("features").asInstanceOf[List[Map[String, Any]]]
      .filter(roadLinkInUse)
  }

  private def roadLinkInUse(feature: Map[String, Any]): Boolean = {
    val attributes = feature("attributes").asInstanceOf[Map[String, Any]]
    attributes("CONSTRUCTIONTYPE").asInstanceOf[BigInt] == BigInt(0)
  }

  private def extractVVHFeature(feature: Map[String, Any]): VVHRoadlink = {
    val geometry = feature("geometry").asInstanceOf[Map[String, Any]]
    val paths = geometry("paths").asInstanceOf[List[List[List[Double]]]]
    val path: List[List[Double]] = paths.head
    val linkGeometry: Seq[Point] = path.map(point => {
      Point(point(0), point(1))
    })
    val attributes = feature("attributes").asInstanceOf[Map[String, Any]]
    val mmlId = attributes("MTKID").asInstanceOf[BigInt].longValue()
    val municipalityCode = attributes("MUNICIPALITYCODE").asInstanceOf[BigInt].toInt
    val featureClassCode = attributes("MTKCLASS").asInstanceOf[BigInt].intValue()
    val featureClass = featureClassCodeToFeatureClass.getOrElse(featureClassCode, FeatureClass.AllOthers)
    VVHRoadlink(mmlId, municipalityCode, linkGeometry, extractAdministrativeClass(attributes),
      extractTrafficDirection(attributes), featureClass)
  }

  private val featureClassCodeToFeatureClass: Map[Int, FeatureClass] = Map(
    12316 -> FeatureClass.TractorRoad,
    12141 -> FeatureClass.DrivePath,
    12314 -> FeatureClass.CycleOrPedestrianPath
  )

  private def extractAdministrativeClass(attributes: Map[String, Any]): AdministrativeClass = {
    Option(attributes("ADMINCLASS").asInstanceOf[BigInt])
      .map(_.toInt)
      .map(AdministrativeClass.apply)
      .get
  }

  private val vvhTrafficDirectionToTrafficDirection: Map[Int, TrafficDirection] = Map(
    0 -> BothDirections,
    1 -> TowardsDigitizing,
    2 -> AgainstDigitizing)

  private def extractTrafficDirection(attributes: Map[String, Any]): TrafficDirection = {
    Option(attributes("DIRECTIONTYPE").asInstanceOf[BigInt])
      .map(_.toInt)
      .map(vvhTrafficDirectionToTrafficDirection.getOrElse(_, UnknownDirection))
      .getOrElse(UnknownDirection)
  }
}



package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset._
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.json4s._
import org.json4s.jackson.JsonMethods._
import java.net.URLEncoder

class VVHClient(hostname: String) {
  protected implicit val jsonFormats: Formats = DefaultFormats

  def fetchVVHRoadlinks(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[(Long, Int, Seq[Point], AdministrativeClass, TrafficDirection)] = {
    val municipalityFilter = {
      if(municipalities.isEmpty) {
        "0"
      } else {
        val municipalityQuery = municipalities.tail.foldLeft("Kuntatunnus=" + municipalities.head){ (acc, m) => acc + " or Kuntatunnus=" + m }
        URLEncoder.encode(s"""{"0":"$municipalityQuery"}""", "UTF-8")
      }
    }

    val url = "http://" + hostname + "/arcgis/rest/services/VVH_OTH/Basic_data/FeatureServer/query?" +
      s"layerDefs=$municipalityFilter&geometry=" + bounds.leftBottom.x + "," + bounds.leftBottom.y + "," + bounds.rightTop.x + "," + bounds.rightTop.y +
      "&geometryType=esriGeometryEnvelope&spatialRel=esriSpatialRelIntersects&returnGeometry=true&geometryPrecision=3&f=pjson"

    val featureMap: Map[String, Any] = fetchVVHFeatureMap(url)

    val features = featureMap("features").asInstanceOf[List[Map[String, Any]]]
    features.map(feature => {
      extractVVHFeature(feature)
    })
  }

  def fetchByMunicipality(municipality: Int): Seq[(Long, Int, Seq[Point])] = {
    val municipalityFilter = URLEncoder.encode(s"""{"0":"Kuntatunnus=$municipality"}""", "UTF-8")
    val url = "http://" + hostname + "/arcgis/rest/services/VVH_OTH/Basic_data/FeatureServer/query?" +
      s"layerDefs=$municipalityFilter&returnGeometry=true&geometryPrecision=3&f=pjson"

    val featureMap: Map[String, Any] = fetchVVHFeatureMap(url)

    val features = featureMap("features").asInstanceOf[List[Map[String, Any]]]
    features.map(feature => {
      extractVVHFeature(feature)
    }).map { x => (x._1, x._2, x._3) }
  }

  def fetchVVHRoadlink(mmlId: Long): Option[(Int, Seq[Point], AdministrativeClass)] = {
    val layerDefs = URLEncoder.encode(s"""{"0":"MTK_ID=$mmlId"}""", "UTF-8")
    val url = "http://" + hostname + "/arcgis/rest/services/VVH_OTH/Basic_data/FeatureServer/query?" +
      s"layerDefs=$layerDefs&returnGeometry=true&geometryPrecision=3&f=pjson"

    val featureMap: Map[String, Any] = fetchVVHFeatureMap(url)

    val features = featureMap("features").asInstanceOf[List[Map[String, Any]]]
    features.headOption.map(feature => {
      extractVVHFeature(feature)
    }).map{ x => (x._2, x._3, x._4)}
  }

  private def fetchVVHFeatureMap(url: String): Map[String, Any] = {
    val request = new HttpGet(url)
    val client = HttpClientBuilder.create().build()
    val response = client.execute(request)
    val content = parse(StreamInput(response.getEntity.getContent)).values.asInstanceOf[Map[String, Any]]
    val layers = content("layers").asInstanceOf[List[Map[String, Any]]]
    val featureMap: Map[String, Any] = layers.find(map => {
      map.contains("features")
    }).get
    featureMap
  }
  
  private def extractVVHFeature(feature: Map[String, Any]): (Long, Int, Seq[Point], AdministrativeClass, TrafficDirection) = {
    val geometry = feature("geometry").asInstanceOf[Map[String, Any]]
    val paths = geometry("paths").asInstanceOf[List[List[List[Double]]]]
    val path: List[List[Double]] = paths.head
    val linkGeometry: Seq[Point] = path.map(point => {
      Point(point(0), point(1))
    })
    val attributes = feature("attributes").asInstanceOf[Map[String, Any]]
    val mmlId = attributes("MTK_ID").asInstanceOf[BigInt].longValue()
    val municipalityCode = attributes("KUNTATUNNUS").asInstanceOf[String].toInt
    (mmlId, municipalityCode, linkGeometry,
      extractAdministrativeClass(attributes), extractTrafficDirection(attributes))
  }
  
  private val vvhAdministrativeClassToAdministrativeClass: Map[Int, AdministrativeClass] = Map(
    12155 -> State,
    12156 -> Municipality,
    12157 -> Private)

  private def extractAdministrativeClass(attributes: Map[String, Any]): AdministrativeClass = {
    Option(attributes("HALLINNOLLINENLUOKKA").asInstanceOf[BigInt])
      .map(_.toInt)
      .map(vvhAdministrativeClassToAdministrativeClass.getOrElse(_, Unknown))
      .getOrElse(Unknown)
  }

  private val vvhTrafficDirectionToTrafficDirection: Map[Int, TrafficDirection] = Map(
    0 -> BothDirections,
    1 -> TowardsDigitizing,
    2 -> AgainstDigitizing)

  private def extractTrafficDirection(attributes: Map[String, Any]): TrafficDirection = {
    Option(attributes("YKSISUUNTAISUUS").asInstanceOf[BigInt])
      .map(_.toInt)
      .map(vvhTrafficDirectionToTrafficDirection.getOrElse(_, UnknownDirection))
      .getOrElse(UnknownDirection)
  }
}

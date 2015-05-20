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
  case object AllOthers extends FeatureClass
}

case class VVHRoadlink(mmlId: Long, municipalityCode: Int, geometry: Seq[Point],
                      administrativeClass: AdministrativeClass,trafficDirection: TrafficDirection,
                      featureClass: FeatureClass)

class VVHClient(hostname: String) {
  protected implicit val jsonFormats: Formats = DefaultFormats

  def fetchVVHRoadlinks(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[VVHRoadlink] = {
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
    features.map(extractVVHFeature)
  }

  def fetchByMunicipality(municipality: Int): Seq[VVHRoadlink] = {
    val municipalityFilter = URLEncoder.encode(s"""{"0":"Kuntatunnus=$municipality"}""", "UTF-8")
    val url = "http://" + hostname + "/arcgis/rest/services/VVH_OTH/Basic_data/FeatureServer/query?" +
      s"layerDefs=$municipalityFilter&returnGeometry=true&geometryPrecision=3&f=pjson"

    val featureMap: Map[String, Any] = fetchVVHFeatureMap(url)

    val features = featureMap("features").asInstanceOf[List[Map[String, Any]]]
    features.map(extractVVHFeature)
  }

  def fetchVVHRoadlink(mmlId: Long): Option[VVHRoadlink] = {
    val layerDefs = URLEncoder.encode(s"""{"0":"MTK_ID=$mmlId"}""", "UTF-8")
    val url = "http://" + hostname + "/arcgis/rest/services/VVH_OTH/Basic_data/FeatureServer/query?" +
      s"layerDefs=$layerDefs&returnGeometry=true&geometryPrecision=3&f=pjson"

    val featureMap: Map[String, Any] = fetchVVHFeatureMap(url)

    val features = featureMap("features").asInstanceOf[List[Map[String, Any]]]
    features.headOption.map(extractVVHFeature)
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
  
  private def extractVVHFeature(feature: Map[String, Any]): VVHRoadlink = {
    val geometry = feature("geometry").asInstanceOf[Map[String, Any]]
    val paths = geometry("paths").asInstanceOf[List[List[List[Double]]]]
    val path: List[List[Double]] = paths.head
    val linkGeometry: Seq[Point] = path.map(point => {
      Point(point(0), point(1))
    })
    val attributes = feature("attributes").asInstanceOf[Map[String, Any]]
    val mmlId = attributes("MTK_ID").asInstanceOf[BigInt].longValue()
    val municipalityCode = attributes("KUNTATUNNUS").asInstanceOf[String].toInt
    val featureClassCode = attributes("KOHDELUOKKA").asInstanceOf[BigInt].intValue()
    val featureClass = featureClassCodeToFeatureClass.getOrElse(featureClassCode, FeatureClass.AllOthers)
    VVHRoadlink(mmlId, municipalityCode, linkGeometry,
      extractAdministrativeClass(attributes), extractTrafficDirection(attributes), featureClass)
  }

  private val featureClassCodeToFeatureClass: Map[Int, FeatureClass] = Map(
    12316 -> FeatureClass.TractorRoad,
    12141 -> FeatureClass.DrivePath
  )
  
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



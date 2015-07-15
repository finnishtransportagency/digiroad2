package fi.liikennevirasto.digiroad2

import com.newrelic.api.agent.Trace
import fi.liikennevirasto.digiroad2.asset._
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime
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
                      featureClass: FeatureClass, modifiedAt: Option[DateTime] = None, attributes: Map[String, Any] = Map())

class VVHClient(hostname: String) {
  class VVHClientException(response: String) extends RuntimeException(response)
  protected implicit val jsonFormats: Formats = DefaultFormats

  private def withFilter[T](attributeName: String, ids: Set[T]): String = {
    val filter =
      if (ids.isEmpty) {
        ""
      } else {
        val query = ids.mkString(",")
        s""""where":"$attributeName IN ($query)","""
      }
    filter
  }

  private def withMunicipalityFilter(municipalities: Set[Int]): String = {
    withFilter("MUNICIPALITYCODE", municipalities)
  }

  private def withMmlIdFilter(mmlIds: Set[Long]): String = {
    withFilter("MTKID", mmlIds)
  }

  private def layerDefinition(filter: String): String = {
    val definitionStart = "[{"
    val layerSelection = """"layerId":0,"""
    val fieldSelection = s""""outFields":"MTKID,MUNICIPALITYCODE,MTKCLASS,ADMINCLASS,DIRECTIONTYPE,CONSTRUCTIONTYPE,ROADNAME_FI,ROADNAME_SM,ROADNAME_SE,MINANLEFT,MAXANLEFT,MINANRIGHT,MAXANRIGHT,LAST_EDITED_DATE""""
    val definitionEnd = "}]"
    val definition = definitionStart + layerSelection + filter + fieldSelection + definitionEnd
    URLEncoder.encode(definition, "UTF-8")
  }
  private def queryParameters(): String = "returnGeometry=true&returnZ=true&returnM=true&geometryPrecision=3&f=pjson"

  def fetchVVHRoadlinks(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[VVHRoadlink] = {
    val definition = layerDefinition(withMunicipalityFilter(municipalities))
    val url = "http://" + hostname + "/arcgis/rest/services/VVH_OTH/Roadlink_data/FeatureServer/query?" +
      s"layerDefs=$definition&geometry=" + bounds.leftBottom.x + "," + bounds.leftBottom.y + "," + bounds.rightTop.x + "," + bounds.rightTop.y +
      "&geometryType=esriGeometryEnvelope&spatialRel=esriSpatialRelIntersects&" + queryParameters

    fetchVVHFeatures(url) match {
      case Left(features) => features.map(extractVVHFeature)
      case Right(error) => throw new VVHClientException(error.toString)
    }
  }

  def fetchByMunicipality(municipality: Int): Seq[VVHRoadlink] = {
    val definition = layerDefinition(withMunicipalityFilter(Set(municipality)))
    val url = "http://" + hostname + "/arcgis/rest/services/VVH_OTH/Roadlink_data/FeatureServer/query?" +
      s"layerDefs=$definition&$queryParameters"

    fetchVVHFeatures(url) match {
      case Left(features) => features.map(extractVVHFeature)
      case Right(error) => throw new VVHClientException(error.toString)
    }
  }

  def fetchVVHRoadlink(mmlId: Long): Option[VVHRoadlink] = fetchVVHRoadlinks(Set(mmlId)).headOption

  def fetchVVHRoadlinks(mmlIds: Set[Long]): Seq[VVHRoadlink] = {
    val definition = layerDefinition(withMmlIdFilter(mmlIds))
    val url = "http://" + hostname + "/arcgis/rest/services/VVH_OTH/Roadlink_data/FeatureServer/query?" +
      s"layerDefs=$definition&$queryParameters"

    fetchVVHFeatures(url) match {
      case Left(features) => features.map(extractVVHFeature)
      case Right(error) => throw new VVHClientException(error.toString)
    }
  }

  case class VVHError(content: Map[String, Any], url: String)

  private def fetchVVHFeatures(url: String): Either[List[Map[String, Any]], VVHError] = {
    val request = new HttpGet(url)
    val client = HttpClientBuilder.create().build()
    val response = client.execute(request)
    try {
      val content: Map[String, Any] = parse(StreamInput(response.getEntity.getContent)).values.asInstanceOf[Map[String, Any]]
      val optionalLayers = content.get("layers").map(_.asInstanceOf[List[Map[String, Any]]])
      val optionalFeatureLayer = optionalLayers.flatMap { layers => layers.find { layer => layer.contains("features") } }
      val optionalFeatures = optionalFeatureLayer.flatMap { featureLayer => featureLayer.get("features").map(_.asInstanceOf[List[Map[String, Any]]]) }
      optionalFeatures.map(_.filter(roadLinkInUse)).map(Left(_)).getOrElse(Right(VVHError(content, url)))
    } finally {
      response.close()
    }
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
    val linkGeometryForApi = Map( "points" -> path.map(point => Map("x" -> point(0), "y" -> point(1), "z" -> point(2), "m" -> point(3))))
    val attributes = feature("attributes").asInstanceOf[Map[String, Any]]
    val mmlId = attributes("MTKID").asInstanceOf[BigInt].longValue()
    val municipalityCode = attributes("MUNICIPALITYCODE").asInstanceOf[BigInt].toInt
    val featureClassCode = attributes("MTKCLASS").asInstanceOf[BigInt].intValue()
    val featureClass = featureClassCodeToFeatureClass.getOrElse(featureClassCode, FeatureClass.AllOthers)
    VVHRoadlink(mmlId, municipalityCode, linkGeometry, extractAdministrativeClass(attributes),
      extractTrafficDirection(attributes), featureClass, extractModifiedAt(attributes), extractAttributes(attributes) ++ linkGeometryForApi)
  }

  private def extractAttributes(attributesMap: Map[String, Any]): Map[String, Any] = {
    attributesMap.filterKeys{ x => Set(
      "CONSTRUCTIONTYPE",
      "ROADNAME_FI",
      "ROADNAME_SM",
      "ROADNAME_SE",
      "MINANLEFT",
      "MAXANLEFT",
      "MINANRIGHT",
      "MAXANRIGHT",
      "MUNICIPALITYCODE").contains(x) }
  }

  private val featureClassCodeToFeatureClass: Map[Int, FeatureClass] = Map(
    12316 -> FeatureClass.TractorRoad,
    12141 -> FeatureClass.DrivePath,
    12314 -> FeatureClass.CycleOrPedestrianPath
  )

  private def extractModifiedAt(attributes: Map[String, Any]): Option[DateTime] = {
    Option(attributes("LAST_EDITED_DATE").asInstanceOf[BigInt])
      .map(modifiedTime => new DateTime(modifiedTime.longValue()))
  }

  private def extractAdministrativeClass(attributes: Map[String, Any]): AdministrativeClass = {
    Option(attributes("ADMINCLASS").asInstanceOf[BigInt])
      .map(_.toInt)
      .map(AdministrativeClass.apply)
      .getOrElse(Unknown)
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



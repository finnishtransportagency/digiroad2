package fi.liikennevirasto.digiroad2

import java.net.URLEncoder

import fi.liikennevirasto.digiroad2.asset._
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime
import org.json4s._
import org.json4s.jackson.JsonMethods._

sealed trait FeatureClass
object FeatureClass {
  case object TractorRoad extends FeatureClass
  case object DrivePath extends FeatureClass
  case object CycleOrPedestrianPath extends FeatureClass
  case object AllOthers extends FeatureClass
}

case class VVHRoadlink(linkId: Long, municipalityCode: Int, geometry: Seq[Point],
                       administrativeClass: AdministrativeClass, trafficDirection: TrafficDirection,
                       featureClass: FeatureClass, modifiedAt: Option[DateTime] = None, attributes: Map[String, Any] = Map())

class VVHClient(vvhRestApiEndPoint: String) {
  class VVHClientException(response: String) extends RuntimeException(response)
  protected implicit val jsonFormats: Formats = DefaultFormats

  private val serviceName = "Roadlink_data"

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

  private def withLinkIdFilter(linkIds: Set[Long]): String = {
    withFilter("LINKID", linkIds)
  }

  private def withMmlIdFilter(mmlIds: Set[Long]): String = {
    withFilter("MTKID", mmlIds)
  }

  private def layerDefinition(filter: String, customFieldSelection: Option[String] = None): String = {
    val definitionStart = "[{"
    val layerSelection = """"layerId":0,"""
    val fieldSelection = customFieldSelection match {
      case Some(fs) => s""""outFields":"""" + fs + """,CONSTRUCTIONTYPE""""
      case _ => s""""outFields":"MTKID,LINKID,MTKHEREFLIP,MUNICIPALITYCODE,VERTICALLEVEL,HORIZONTALACCURACY,VERTICALACCURACY,MTKCLASS,ADMINCLASS,DIRECTIONTYPE,CONSTRUCTIONTYPE,ROADNAME_FI,ROADNAME_SM,ROADNAME_SE,FROM_LEFT,TO_LEFT,FROM_RIGHT,TO_RIGHT,LAST_EDITED_DATE,ROADNUMBER,ROADPARTNUMBER""""
    }
    val definitionEnd = "}]"
    val definition = definitionStart + layerSelection + filter + fieldSelection + definitionEnd
    URLEncoder.encode(definition, "UTF-8")
  }

  private def queryParameters(fetchGeometry: Boolean = true): String = {
    if (fetchGeometry) "returnGeometry=true&returnZ=true&returnM=true&geometryPrecision=3&f=pjson"
    else "f=pjson"
  }

  def fetchVVHRoadlinks(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[VVHRoadlink] = {
    val definition = layerDefinition(withMunicipalityFilter(municipalities))
    val url = vvhRestApiEndPoint + serviceName + "/FeatureServer/query?" +
      s"layerDefs=$definition&geometry=" + bounds.leftBottom.x + "," + bounds.leftBottom.y + "," + bounds.rightTop.x + "," + bounds.rightTop.y +
      "&geometryType=esriGeometryEnvelope&spatialRel=esriSpatialRelIntersects&" + queryParameters()

    fetchVVHFeatures(url) match {
      case Left(features) => features.map(extractVVHFeature)
      case Right(error) => throw new VVHClientException(error.toString)
    }
  }

  def fetchByMunicipality(municipality: Int): Seq[VVHRoadlink] = {
    val definition = layerDefinition(withMunicipalityFilter(Set(municipality)))
    val url = vvhRestApiEndPoint + serviceName + "/FeatureServer/query?" +
      s"layerDefs=$definition&${queryParameters()}"

    fetchVVHFeatures(url) match {
      case Left(features) => features.map(extractVVHFeature)
      case Right(error) => throw new VVHClientException(error.toString)
    }
  }

  def fetchVVHRoadlink(linkId: Long): Option[VVHRoadlink] = fetchVVHRoadlinks(Set(linkId)).headOption

  def fetchVVHRoadlinks(linkIds: Set[Long]): Seq[VVHRoadlink] = {
    fetchVVHRoadlinks(linkIds, None, true, roadLinkFromFeature, withLinkIdFilter)
  }

  def fetchVVHRoadlinkByMmlId(mmlId: Long): Option[VVHRoadlink] = fetchVVHRoadlinksByMmlIds(Set(mmlId)).headOption

  def fetchVVHRoadlinksByMmlIds(mmlIds: Set[Long]): Seq[VVHRoadlink] = {
    fetchVVHRoadlinks(mmlIds, None, true, roadLinkFromFeature, withMmlIdFilter)
  }

  def fetchVVHRoadlinks[T](linkIds: Set[Long],
                           fieldSelection: Option[String],
                           fetchGeometry: Boolean,
                           resultTransition: (Map[String, Any], List[List[Double]]) => T): Seq[T] =
    fetchVVHRoadlinks(linkIds, fieldSelection, fetchGeometry, resultTransition, withLinkIdFilter)

  def fetchVVHRoadlinks[T](linkIds: Set[Long],
                           fieldSelection: Option[String],
                           fetchGeometry: Boolean,
                           resultTransition: (Map[String, Any], List[List[Double]]) => T,
                           filter: Set[Long] => String): Seq[T] = {
    val batchSize = 1000
    val idGroups: List[Set[Long]] = linkIds.grouped(batchSize).toList
    idGroups.par.flatMap { ids =>
      val definition = layerDefinition(filter(ids), fieldSelection)
      val url = vvhRestApiEndPoint + serviceName + "/FeatureServer/query?" +
        s"layerDefs=$definition&${queryParameters(fetchGeometry)}"
      fetchVVHFeatures(url) match {
        case Left(features) => features.map { feature =>
          val attributes = extractFeatureAttributes(feature)
          val geometry = if (fetchGeometry) extractFeatureGeometry(feature) else Nil
          resultTransition(attributes, geometry)
        }
        case Right(error) => throw new VVHClientException(error.toString)
      }
    }.toList
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

  private def extractFeatureAttributes(feature: Map[String, Any]): Map[String, Any] = {
    feature("attributes").asInstanceOf[Map[String, Any]]
  }

  private def extractFeatureGeometry(feature: Map[String, Any]): List[List[Double]] = {
    val geometry = feature("geometry").asInstanceOf[Map[String, Any]]
    val paths = geometry("paths").asInstanceOf[List[List[List[Double]]]]
    paths.head
  }

  private def roadLinkFromFeature(attributes: Map[String, Any], path: List[List[Double]]): VVHRoadlink = {
    val linkGeometry: Seq[Point] = path.map(point => {
      Point(point(0), point(1))
    })
    val linkGeometryForApi = Map("points" -> path.map(point => Map("x" -> point(0), "y" -> point(1), "z" -> point(2), "m" -> point(3))))
    val linkId = attributes("LINKID").asInstanceOf[BigInt].longValue()
    val municipalityCode = attributes("MUNICIPALITYCODE").asInstanceOf[BigInt].toInt
    val featureClassCode = attributes("MTKCLASS").asInstanceOf[BigInt].intValue()
    val featureClass = featureClassCodeToFeatureClass.getOrElse(featureClassCode, FeatureClass.AllOthers)

    VVHRoadlink(linkId, municipalityCode, linkGeometry, extractAdministrativeClass(attributes),
      extractTrafficDirection(attributes), featureClass, extractModifiedAt(attributes), extractAttributes(attributes) ++ linkGeometryForApi)
  }

  private def extractVVHFeature(feature: Map[String, Any]): VVHRoadlink = {
    val attributes = extractFeatureAttributes(feature)
    val path = extractFeatureGeometry(feature)
    roadLinkFromFeature(attributes, path)
  }

  private def extractAttributes(attributesMap: Map[String, Any]): Map[String, Any] = {
    attributesMap.filterKeys{ x => Set(
      "MTKID",
      "HORIZONTALACCURACY",
      "VERTICALACCURACY",
      "VERTICALLEVEL",
      "CONSTRUCTIONTYPE",
      "ROADNAME_FI",
      "ROADNAME_SM",
      "ROADNAME_SE",
      "ROADNUMBER",
      "ROADPARTNUMBER",
      "FROM_LEFT",
      "TO_LEFT",
      "FROM_RIGHT",
      "TO_RIGHT",
      "MUNICIPALITYCODE",
      "MTKHEREFLIP").contains(x)
    }.filter { case (_, value) =>
      value != null
    }
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
    0 -> TrafficDirection.BothDirections,
    1 -> TrafficDirection.TowardsDigitizing,
    2 -> TrafficDirection.AgainstDigitizing)

  private def extractTrafficDirection(attributes: Map[String, Any]): TrafficDirection = {
    Option(attributes("DIRECTIONTYPE").asInstanceOf[BigInt])
      .map(_.toInt)
      .map(vvhTrafficDirectionToTrafficDirection.getOrElse(_, TrafficDirection.UnknownDirection))
      .getOrElse(TrafficDirection.UnknownDirection)
  }
}



package fi.liikennevirasto.digiroad2

import java.net.URLEncoder

import fi.liikennevirasto.digiroad2.asset._
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.{DateTime, DateTimeZone}
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

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

case class ChangeInfo(oldId: Option[Long], newId: Option[Long], mmlId: Long, changeType: Int,
                      oldStartMeasure: Option[Double], oldEndMeasure: Option[Double], newStartMeasure: Option[Double],
                      newEndMeasure: Option[Double], vvhTimeStamp: Long = 0L)

/**
  * Numerical values for change types from VVH ChangeInfo Api
  */
sealed trait ChangeType {
  def value: Int
}
object ChangeType {
  val values = Set(Unknown, CombinedModifiedPart, CombinedRemovedPart, LenghtenedCommonPart, LengthenedNewPart, DividedModifiedPart, DividedNewPart, ShortenedCommonPart, ShortenedRemovedPart, Removed, New, ReplacedCommonPart, ReplacedNewPart, ReplacedRemovedPart)

  def apply(intValue: Int): ChangeType = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object Unknown extends ChangeType { def value = 0 }
  case object CombinedModifiedPart extends ChangeType { def value = 1 }
  case object CombinedRemovedPart extends ChangeType { def value = 2 }
  case object LenghtenedCommonPart extends ChangeType { def value = 3 }
  case object LengthenedNewPart extends ChangeType { def value = 4 }
  case object DividedModifiedPart extends ChangeType { def value = 5 }
  case object DividedNewPart extends ChangeType { def value = 6 }
  case object ShortenedCommonPart extends ChangeType { def value = 7 }
  case object ShortenedRemovedPart extends ChangeType { def value = 8 }
  case object Removed extends ChangeType { def value = 11 }
  case object New extends ChangeType { def value = 12 }
  case object ReplacedCommonPart extends ChangeType { def value = 13 }
  case object ReplacedNewPart extends ChangeType { def value = 14 }
  case object ReplacedRemovedPart extends ChangeType { def value = 16 }

  /**
    * Return true if this is a replacement where segment or part of it replaces another, older one
    * All changes should be of form (old_id, new_id, old_start, old_end, new_start, new_end) with non-null values
    *
    * @param changeInfo changeInfo object to check
    * @return true, if this is a replacement
    */
  def isReplacementChange(changeInfo: ChangeInfo) = { // Where asset geo location should be replaced with another
    ChangeType.apply(changeInfo.changeType) match {
      case CombinedModifiedPart => true
      case CombinedRemovedPart => true
      case LenghtenedCommonPart => true
      case DividedModifiedPart => true
      case DividedNewPart => true
      case ShortenedCommonPart => true
      case ReplacedCommonPart => true
      case Unknown => false
      case LengthenedNewPart => false
      case ShortenedRemovedPart => false
      case Removed => false
      case New => false
      case ReplacedNewPart => false
      case ReplacedRemovedPart => false
    }
  }

  /**
    * Return true if this is an extension where segment or part of it has no previous entry
    * All changes should be of form (new_id, new_start, new_end) with non-null values and old_* fields must be null
    *
    * @param changeInfo changeInfo object to check
    * @return true, if this is an extension
    */
  def isExtensionChange(changeInfo: ChangeInfo) = { // Where asset geo location is a new extension (non-existing)
    ChangeType.apply(changeInfo.changeType) match {
      case LengthenedNewPart => true
      case ReplacedNewPart => true
      case _ => false
    }
  }

  /**
    * Return true if this is a removed segment or a piece of it. Only old id and m-values should be populated.
    *
    * @param changeInfo changeInfo object to check
    * @return true, if this is a removed segment
    */
  def isRemovalChange(changeInfo: ChangeInfo) = { // Where asset should be removed completely or partially
    ChangeType.apply(changeInfo.changeType) match {
      case Removed => true
      case ReplacedRemovedPart => true
      case ShortenedRemovedPart => true
      case _ => false
    }
  }

  /**
    * Return true if this is a new segment. Only new id and m-values should be populated.
    *
    * @param changeInfo changeInfo object to check
    * @return true, if this is a new segment
    */
  def isCreationChange(changeInfo: ChangeInfo) = { // Where asset geo location should be replaced with another
    ChangeType.apply(changeInfo.changeType) match {
      case New => true
      case _ => false
    }
  }

  def isUnknownChange(changeInfo: ChangeInfo) = {
    ChangeType.Unknown.value == changeInfo.changeType
  }
}

class VVHClient(vvhRestApiEndPoint: String) {
  class VVHClientException(response: String) extends RuntimeException(response)
  protected implicit val jsonFormats: Formats = DefaultFormats

  private val roadLinkDataService = "Roadlink_data"
  private val roadLinkDataHistoryService = "Roadlink_data_history"


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

  private def withLimitFilter(attributeName: String, low: Int, high: Int, includeAllPublicRoads: Boolean = false): String = {
    val filter =
      if (low < 0 || high < 0 || low > high) {
        ""
      } else {
        if (includeAllPublicRoads) {
          s""""where":"( ADMINCLASS = 1 OR $attributeName >= $low and $attributeName <= $high )","""
        } else {
          s""""where":"( $attributeName >= $low and $attributeName <= $high )","""
        }
      }
    filter
  }

  private def withMunicipalityFilter(municipalities: Set[Int]): String = {
    withFilter("MUNICIPALITYCODE", municipalities)
  }

  private def withRoadNumberFilter(roadNumbers: (Int, Int), includeAllPublicRoads: Boolean): String = {
    withLimitFilter("ROADNUMBER", roadNumbers._1, roadNumbers._2, includeAllPublicRoads)
  }

  private def withRoadNumbersFilter(roadNumbers: Seq[(Int, Int)], includeAllPublicRoads: Boolean, filter: String = ""): String = {
    if (roadNumbers.isEmpty)
      return s""""where":"($filter)","""
    if (includeAllPublicRoads)
      return withRoadNumbersFilter(roadNumbers, false, "ADMINCLASS = 1")
    val limit = roadNumbers.head
    val filterAdd = s"""(ROADNUMBER >= ${limit._1} and ROADNUMBER <= ${limit._2})"""
    if (filter == "")
      withRoadNumbersFilter(roadNumbers.tail, includeAllPublicRoads, filterAdd)
    else
      withRoadNumbersFilter(roadNumbers.tail, includeAllPublicRoads, s"""$filter OR $filterAdd""")
  }

  private def withLinkIdFilter(linkIds: Set[Long]): String = {
    withFilter("LINKID", linkIds)
  }

  private def withMmlIdFilter(mmlIds: Set[Long]): String = {
    withFilter("MTKID", mmlIds)
  }

  private def combineFiltersWithAnd(filter1: String, filter2: String) = {
    filter1.isEmpty match {
      case true => filter2
      case _ => "%s AND %s".format(filter1.dropRight(2), filter2.replace("\"where\":\"", ""))
    }
  }

  private def layerDefinition(filter: String, customFieldSelection: Option[String] = None): String = {
    val definitionStart = "[{"
    val layerSelection = """"layerId":0,"""
    val fieldSelection = customFieldSelection match {
      case Some(fs) => s""""outFields":"""" + fs + """,CONSTRUCTIONTYPE""""
      case _ => s""""outFields":"MTKID,LINKID,MTKHEREFLIP,MUNICIPALITYCODE,VERTICALLEVEL,HORIZONTALACCURACY,VERTICALACCURACY,MTKCLASS,ADMINCLASS,DIRECTIONTYPE,CONSTRUCTIONTYPE,ROADNAME_FI,ROADNAME_SM,ROADNAME_SE,FROM_LEFT,TO_LEFT,FROM_RIGHT,TO_RIGHT,LAST_EDITED_DATE,ROADNUMBER,ROADPARTNUMBER,VALIDFROM,GEOMETRY_EDITED_DATE,CREATED_DATE,SURFACETYPE""""
    }
    val definitionEnd = "}]"
    val definition = definitionStart + layerSelection + filter + fieldSelection + definitionEnd
    URLEncoder.encode(definition, "UTF-8")
  }

  private def historyLayerDefinition(filter: String, customFieldSelection: Option[String] = None): String = {
    val definitionStart = "[{"
    val layerSelection = """"layerId":0,"""
    val fieldSelection = customFieldSelection match {
      case Some(fs) => s""""outFields":"""" + fs + """,CONSTRUCTIONTYPE""""
      case _ => s""""outFields":"MTKID,LINKID,MTKHEREFLIP,MUNICIPALITYCODE,VERTICALLEVEL,HORIZONTALACCURACY,VERTICALACCURACY,MTKCLASS,ADMINCLASS,DIRECTIONTYPE,CONSTRUCTIONTYPE,ROADNAME_FI,ROADNAME_SM,ROADNAME_SE,FROM_LEFT,TO_LEFT,FROM_RIGHT,TO_RIGHT,LAST_EDITED_DATE,ROADNUMBER,ROADPARTNUMBER,VALIDFROM,GEOMETRY_EDITED_DATE,SURFACETYPE,END_DATE""""
    }
    val definitionEnd = "}]"
    val definition = definitionStart + layerSelection + filter + fieldSelection + definitionEnd
    URLEncoder.encode(definition, "UTF-8")
  }

  private def historyLayerDefinition(filter: String, customFieldSelection: Option[String] = None): String = {
    val definitionStart = "[{"
    val layerSelection = """"layerId":0,"""
    val fieldSelection = customFieldSelection match {
      case Some(fs) => s""""outFields":"""" + fs + """,CONSTRUCTIONTYPE""""
      case _ => s""""outFields":"MTKID,LINKID,MTKHEREFLIP,MUNICIPALITYCODE,VERTICALLEVEL,HORIZONTALACCURACY,VERTICALACCURACY,MTKCLASS,ADMINCLASS,DIRECTIONTYPE,CONSTRUCTIONTYPE,ROADNAME_FI,ROADNAME_SM,ROADNAME_SE,FROM_LEFT,TO_LEFT,FROM_RIGHT,TO_RIGHT,LAST_EDITED_DATE,ROADNUMBER,ROADPARTNUMBER,VALIDFROM,GEOMETRY_EDITED_DATE,SURFACETYPE,END_DATE""""
    }
    val definitionEnd = "}]"
    val definition = definitionStart + layerSelection + filter + fieldSelection + definitionEnd
    URLEncoder.encode(definition, "UTF-8")
  }

  private def queryParameters(fetchGeometry: Boolean = true): String = {
    if (fetchGeometry) "returnGeometry=true&returnZ=true&returnM=true&geometryPrecision=3&f=pjson"
    else "f=pjson"
  }

  /**
    * Returns VVH road links in bounding box area. Municipalities are optional.
    * Used by VVHClient.fetchVVHRoadlinksF, RoadLinkService.getVVHRoadLinks(bounds, municipalities), RoadLinkService.getVVHRoadLinks(bounds),
    * PointAssetService.getByBoundingBox and ServicePointImporter.importServicePoints.
    */
  def fetchVVHRoadlinksWithRoadNumbers(bounds: BoundingRectangle, roadNumbers: Seq[(Int, Int)], municipalities: Set[Int] = Set(), includeAllPublicRoads: Boolean = false): Seq[VVHRoadlink] = {
    val roadNumberFilters = withRoadNumbersFilter(roadNumbers, includeAllPublicRoads, "")
    val definition = layerDefinition(combineFiltersWithAnd(withMunicipalityFilter(municipalities), roadNumberFilters))
    val url = vvhRestApiEndPoint + roadLinkDataService + "/FeatureServer/query?" +
      s"layerDefs=$definition&geometry=" + bounds.leftBottom.x + "," + bounds.leftBottom.y + "," + bounds.rightTop.x + "," + bounds.rightTop.y +
      "&geometryType=esriGeometryEnvelope&spatialRel=esriSpatialRelIntersects&" + queryParameters()

    fetchVVHFeatures(url) match {
      case Left(features) => features.map(extractVVHFeature)
      case Right(error) => throw new VVHClientException(error.toString)
    }
  }

  /**
    * Returns VVH road links in bounding box area. Municipalities are optional.
    * Used by VVHClient.fetchVVHRoadlinksF, RoadLinkService.getVVHRoadLinks(bounds, municipalities), RoadLinkService.getVVHRoadLinks(bounds),
    * PointAssetService.getByBoundingBox and ServicePointImporter.importServicePoints.
    */
  def fetchVVHRoadlinksWithLinkIds(bounds: BoundingRectangle, linkIds: Set[Long], municipalities: Set[Int] = Set()): Seq[VVHRoadlink] = {
    val definition = layerDefinition(withLinkIdFilter(linkIds))
    val url = vvhRestApiEndPoint + roadLinkDataService + "/FeatureServer/query?" +
      s"layerDefs=$definition&geometry=" + bounds.leftBottom.x + "," + bounds.leftBottom.y + "," + bounds.rightTop.x + "," + bounds.rightTop.y +
      "&geometryType=esriGeometryEnvelope&spatialRel=esriSpatialRelIntersects&" + queryParameters()

    fetchVVHFeatures(url) match {
      case Left(features) => features.map(extractVVHFeature)
      case Right(error) => throw new VVHClientException(error.toString)
    }
  }
  /**
    * Returns VVH road links in bounding box area. Municipalities are optional.
    * Used by VVHClient.fetchVVHRoadlinksF, RoadLinkService.getVVHRoadLinks(bounds, municipalities), RoadLinkService.getVVHRoadLinks(bounds),
    * PointAssetService.getByBoundingBox and ServicePointImporter.importServicePoints.
    */
  def fetchVVHRoadlinks(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[VVHRoadlink] = {
    val definition = layerDefinition(withMunicipalityFilter(municipalities))
    val url = vvhRestApiEndPoint + roadLinkDataService + "/FeatureServer/query?" +
      s"layerDefs=$definition&geometry=" + bounds.leftBottom.x + "," + bounds.leftBottom.y + "," + bounds.rightTop.x + "," + bounds.rightTop.y +
      "&geometryType=esriGeometryEnvelope&spatialRel=esriSpatialRelIntersects&" + queryParameters()

    fetchVVHFeatures(url) match {
      case Left(features) => features.map(extractVVHFeature)
      case Right(error) => throw new VVHClientException(error.toString)
    }
  }

  /**
    * Returns VVH road links. Uses Scala Future for concurrent operations.
    * Used by RoadLinkService.getRoadLinksAndChangesFromVVH(bounds, municipalities).
    */
  def fetchVVHRoadlinksF(bounds: BoundingRectangle, municipalities: Set[Int]): Future[Seq[VVHRoadlink]] = {
    Future(fetchVVHRoadlinks(bounds, municipalities))
  }

  /**
    * Returns VVH road links. Uses Scala Future for concurrent operations.
    * Used by RoadLinkService.getRoadLinksAndChangesFromVVH(bounds, municipalities).
    */
  def fetchVVHRoadlinksWithRoadNumbersF(bounds: BoundingRectangle, municipalities: Set[Int], roadNumbers: Seq[(Int, Int)],
                                        includeAllPublicRoads: Boolean = false): Future[Seq[VVHRoadlink]] = {
    Future(fetchVVHRoadlinksWithRoadNumbers(bounds, roadNumbers, municipalities, includeAllPublicRoads))
  }

  /**
    * Returns VVH road links. Uses Scala Future for concurrent operations.
    * Used by RoadLinkService.getRoadLinksAndChangesFromVVH(municipality).
    */
  def fetchVVHRoadlinksF(municipality: Int): Future[Seq[VVHRoadlink]] = {
    Future(fetchByMunicipality(municipality))
  }

  /**
    * Returns VVH change data. Uses Scala Future for concurrent operations.
    * Used by RoadLinkService.getRoadLinksAndChangesFromVVH(bounds, municipalities)
    */
  def fetchChangesF(bounds: BoundingRectangle, municipalities: Set[Int]): Future[Seq[ChangeInfo]] = {
    val municipalityFilter = withMunicipalityFilter(municipalities)
    val definition = layerDefinition(municipalityFilter, Some("OLD_ID,NEW_ID,MTKID,CHANGETYPE,OLD_START,OLD_END,NEW_START,NEW_END,CREATED_DATE"))
    val url = vvhRestApiEndPoint + "/Roadlink_ChangeInfo/FeatureServer/query?" +
      s"layerDefs=$definition&geometry=" + bounds.leftBottom.x + "," + bounds.leftBottom.y + "," + bounds.rightTop.x + "," + bounds.rightTop.y +
      "&geometryType=esriGeometryEnvelope&spatialRel=esriSpatialRelIntersects&" + queryParameters(false)

    Future {
      fetchVVHFeatures(url) match {
        case Left(features) => features.map(extractVVHChangeInfo)
        case Right(error) => throw new VVHClientException(error.toString)
      }
    }
  }

  /**
    * Returns VVH change data. Uses Scala Future for concurrent operations.
    * Used by RoadLinkService.getRoadLinksAndChangesFromVVH(bounds, municipalities)
    */
  def fetchChangesWithRoadNumbersF(bounds: BoundingRectangle, municipalities: Set[Int], roadNumbers: Seq[(Int, Int)]): Future[Seq[ChangeInfo]] = {
    val municipalityFilter = withMunicipalityFilter(municipalities)
    //val roadNumberFilter = withRoadNumberFilter(roadNumbers)
    val definition = layerDefinition(municipalityFilter, Some("OLD_ID,NEW_ID,MTKID,CHANGETYPE,OLD_START,OLD_END,NEW_START,NEW_END,CREATED_DATE"))
//    val definition = layerDefinition(combineFilters(municipalityFilter, roadNumberFilter), Some("OLD_ID,NEW_ID,MTKID,CHANGETYPE,OLD_START,OLD_END,NEW_START,NEW_END,CREATED_DATE"))
    val url = vvhRestApiEndPoint + "/Roadlink_ChangeInfo/FeatureServer/query?" +
      s"layerDefs=$definition&geometry=" + bounds.leftBottom.x + "," + bounds.leftBottom.y + "," + bounds.rightTop.x + "," + bounds.rightTop.y +
      "&geometryType=esriGeometryEnvelope&spatialRel=esriSpatialRelIntersects&" + queryParameters(false)

    Future {
      fetchVVHFeatures(url) match {
        case Left(features) => features.map(extractVVHChangeInfo)
        case Right(error) => throw new VVHClientException(error.toString)
      }
    }
  }

  /**
    * Returns VVH change data. Uses Scala Future for concurrent operations.
    * Used by RoadLinkService.getRoadLinksAndChangesFromVVH(municipality).
    */
  def fetchChangesF(municipality: Int): Future[Seq[ChangeInfo]] = {
    val definition = layerDefinition(withMunicipalityFilter(Set(municipality)), Some("OLD_ID,NEW_ID,MTKID,CHANGETYPE,OLD_START,OLD_END,NEW_START,NEW_END,CREATED_DATE"))

    val url = vvhRestApiEndPoint + "/Roadlink_ChangeInfo/FeatureServer/query?" +
      s"layerDefs=$definition&" + queryParameters(true)

    Future {
      fetchVVHFeatures(url) match {
        case Left(features) => features.map(extractVVHChangeInfo)
        case Right(error) => throw new VVHClientException(error.toString)
      }
    }
  }


  private def extractVVHChangeInfo(feature: Map[String, Any]) = {
    val attributes = extractFeatureAttributes(feature)

    val oldId = Option(attributes("OLD_ID").asInstanceOf[BigInt]).map(_.longValue())
    val newId = Option(attributes("NEW_ID").asInstanceOf[BigInt]).map(_.longValue())
    val mmlId = attributes("MTKID").asInstanceOf[BigInt].longValue()
    val changeType = attributes("CHANGETYPE").asInstanceOf[BigInt].intValue()
    val vvhTimeStamp = Option(attributes("CREATED_DATE").asInstanceOf[BigInt]).map(_.longValue()).getOrElse(0L)
    val oldStartMeasure = extractMeasure(attributes("OLD_START"))
    val oldEndMeasure = extractMeasure(attributes("OLD_END"))
    val newStartMeasure = extractMeasure(attributes("NEW_START"))
    val newEndMeasure = extractMeasure(attributes("NEW_END"))

    ChangeInfo(oldId, newId, mmlId, changeType, oldStartMeasure, oldEndMeasure, newStartMeasure, newEndMeasure, vvhTimeStamp)
  }

  /**
    * Returns VVH road links by municipality.
    * Used by VVHClient.fetchVVHRoadlinksF(municipality), RoadLinkService.fetchVVHRoadlinks(municipalityCode) and AssetDataImporter.adjustToNewDigitization.
    */
  def fetchByMunicipality(municipality: Int): Seq[VVHRoadlink] = {
    val definition = layerDefinition(withMunicipalityFilter(Set(municipality)))
    val url = vvhRestApiEndPoint + roadLinkDataService + "/FeatureServer/query?" +
      s"layerDefs=$definition&${queryParameters()}"

    fetchVVHFeatures(url) match {
      case Left(features) => features.map(extractVVHFeature)
      case Right(error) => throw new VVHClientException(error.toString)
    }
  }

  /**
    * Returns VVH road links by municipality.
    * Used by RoadAddressService.getMissingRoadAddresses
    */
  def fetchByMunicipality(municipality: Int, roadNumbers: Seq[(Int, Int)]): Seq[VVHRoadlink] = {
    val roadNumberFilters = withRoadNumbersFilter(roadNumbers, true, "")
    val definition = layerDefinition(combineFiltersWithAnd(withMunicipalityFilter(Set(municipality)), roadNumberFilters))
    val url = vvhRestApiEndPoint + roadLinkDataService + "/FeatureServer/query?" +
      s"layerDefs=$definition&${queryParameters()}"

    fetchVVHFeatures(url) match {
      case Left(features) => features.map(extractVVHFeature)
      case Right(error) => throw new VVHClientException(error.toString)
    }
  }

  /**
    * Returns VVH road link by link id.
    * Used by Digiroad2Api.createMassTransitStop, Digiroad2Api.validateUserRights, Digiroad2Api /manoeuvres DELETE endpoint, Digiroad2Api /manoeuvres PUT endpoint,
    * CsvImporter.updateAssetByExternalIdLimitedByRoadType, RoadLinkService,getRoadLinkMiddlePointByLinkId, RoadLinkService.updateLinkProperties, RoadLinkService.getRoadLinkGeometry,
    * RoadLinkService.updateAutoGeneratedProperties, LinearAssetService.split, LinearAssetService.separate, MassTransitStopService.fetchRoadLink, PointAssetOperations.getById
    * and OracleLinearAssetDao.createSpeedLimit.
    */
  def fetchVVHRoadlink(linkId: Long): Option[VVHRoadlink] = fetchVVHRoadlinks(Set(linkId)).headOption

  /**
    * Returns VVH road links by link ids.
    * Used by VVHClient.fetchVVHRoadlink, RoadLinkService.fetchVVHRoadlinks, SpeedLimitService.purgeUnknown, PointAssetOperations.getFloatingAssets,
    * OracleLinearAssetDao.getLinksWithLengthFromVVH, OracleLinearAssetDao.getSpeedLimitLinksById AssetDataImporter.importEuropeanRoads and AssetDataImporter.importProhibitions
    */
  def fetchVVHRoadlinks(linkIds: Set[Long]): Seq[VVHRoadlink] = {
    fetchVVHRoadlinks(linkIds, None, true, roadLinkFromFeature, withLinkIdFilter)
  }

  /**
    * Returns VVH road link by mml id.
    * Used by RoadLinkService.getRoadLinkMiddlePointByMmlId
    */
  def fetchVVHRoadlinkByMmlId(mmlId: Long): Option[VVHRoadlink] = fetchVVHRoadlinksByMmlIds(Set(mmlId)).headOption

  /**
    * Returns VVH road links by mml ids.
    * Used by VVHClient.fetchVVHRoadlinkByMmlId and LinkIdImporter.updateTable
    */
  def fetchVVHRoadlinksByMmlIds(mmlIds: Set[Long]): Seq[VVHRoadlink] = {
    fetchVVHRoadlinks(mmlIds, None, true, roadLinkFromFeature, withMmlIdFilter)
  }

  /**
    * Returns VVH road links.
    * Used by RoadLinkService.fetchVVHRoadlinks (called from CsvGenerator)
    */
  def fetchVVHRoadlinks[T](linkIds: Set[Long],
                           fieldSelection: Option[String],
                           fetchGeometry: Boolean,
                           resultTransition: (Map[String, Any], List[List[Double]]) => T): Seq[T] =
    fetchVVHRoadlinks(linkIds, fieldSelection, fetchGeometry, resultTransition, withLinkIdFilter)

  /**
    * Returns VVH road links.
    * Used by VVHClient.fetchVVHRoadlinks(linkIds), VVHClient.fetchVVHRoadlinksByMmlIds and VVHClient.fetchVVHRoadlinks
    */
  def fetchVVHRoadlinks[T](linkIds: Set[Long],
                           fieldSelection: Option[String],
                           fetchGeometry: Boolean,
                           resultTransition: (Map[String, Any], List[List[Double]]) => T,
                           filter: Set[Long] => String): Seq[T] = {
    val batchSize = 1000
    val idGroups: List[Set[Long]] = linkIds.grouped(batchSize).toList
    idGroups.par.flatMap { ids =>
      val definition = layerDefinition(filter(ids), fieldSelection)
      val url = vvhRestApiEndPoint + roadLinkDataService + "/FeatureServer/query?" +
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
    attributes.getOrElse("CONSTRUCTIONTYPE", BigInt(0)).asInstanceOf[BigInt] == BigInt(0)
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
      Point(point(0), point(1), extractMeasure(point(2)).get)
    })
    val linkGeometryForApi = Map("points" -> path.map(point => Map("x" -> point(0), "y" -> point(1), "z" -> point(2), "m" -> point(3))))
    val linkGeometryWKTForApi = Map("geometryWKT" -> ("LINESTRING ZM (" + path.map(point => point(0) + " " + point(1) + " " + point(2) + " " + point(3)).mkString(", ") + ")"))
    val linkId = attributes("LINKID").asInstanceOf[BigInt].longValue()
    val municipalityCode = attributes("MUNICIPALITYCODE").asInstanceOf[BigInt].toInt
    val featureClassCode = attributes("MTKCLASS").asInstanceOf[BigInt].intValue()
    val featureClass = featureClassCodeToFeatureClass.getOrElse(featureClassCode, FeatureClass.AllOthers)

    VVHRoadlink(linkId, municipalityCode, linkGeometry, extractAdministrativeClass(attributes),
      extractTrafficDirection(attributes), featureClass, extractModifiedAt(attributes), extractAttributes(attributes) ++ linkGeometryForApi ++ linkGeometryWKTForApi)
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
      "MTKHEREFLIP",
      "VALIDFROM",
      "GEOMETRY_EDITED_DATE",
      "END_DATE",
      "CREATED_DATE",
      "LAST_EDITED_DATE",
      "SURFACETYPE").contains(x)
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
    def compareDateMillisOptions(a: Option[Long], b: Option[Long]): Option[Long] = {
      (a, b) match {
        case (Some(firstModifiedAt), Some(secondModifiedAt)) =>
          if (firstModifiedAt > secondModifiedAt)
            Some(firstModifiedAt)
          else
            Some(secondModifiedAt)
        case (Some(firstModifiedAt), None) => Some(firstModifiedAt)
        case (None, Some(secondModifiedAt)) => Some(secondModifiedAt)
        case (None, None) => None
      }
    }

    val lastEditedDate = Option(attributes("LAST_EDITED_DATE").asInstanceOf[BigInt]).map(_.toLong)
    val geometryEditedDate = Option(attributes("GEOMETRY_EDITED_DATE").asInstanceOf[BigInt]).map(_.toLong)
    val validFromDate = Option(attributes("VALIDFROM").asInstanceOf[BigInt]).map(_.toLong)

    val latestDate = compareDateMillisOptions(lastEditedDate, geometryEditedDate)
    latestDate.orElse(validFromDate).map(modifiedTime => new DateTime(modifiedTime))
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

  /**
    * Extract double value from VVH data. Used for change info start and end measures.
    */
  private def extractMeasure(value: Any): Option[Double] = {
    value match {
      case null => None
      case _ => Some(value.toString.toDouble)
    }
  }

  /**
    * Creates a pseudo VVH Timestamp for new assets and speed limits. Turns clock back to 0:00 on the same day
    * if less than offsetHours have passed since or 0:00 on previous day if not.
    *
    * @param offsetHours Number of hours since midnight to return current day as a VVH timestamp (UNIX time in ms)
    */
  def createVVHTimeStamp(offsetHours: Int): Long = {
    val oneHourInMs = 60 * 60 * 1000L
    val utcTime = DateTime.now().minusHours(offsetHours).getMillis
    val curr = utcTime + DateTimeZone.getDefault.getOffset(utcTime)
    curr - (curr % (24L*oneHourInMs))
  }

  /**
    * Returns VVH road links. Uses Scala Future for concurrent operations.
    * Used by RoadLinkService.getRoadLinksAndChangesFromVVH(bounds, municipalities).
    */
  def fetchVVHRoadlinkHistoryF(bounds: BoundingRectangle, municipalities: Set[Int]): Future[Seq[VVHRoadlink]] = {
    Future(fetchVVHRoadlinkHistory(bounds, municipalities))
  }

  /**
    * Returns VVH road link history data in bounding box area. Municipalities are optional.
    * Used by VVHClient.fetchVVHRoadlinksF, RoadLinkService.getVVHRoadLinks(bounds, municipalities), RoadLinkService.getVVHRoadLinks(bounds),
    * PointAssetService.getByBoundingBox and ServicePointImporter.importServicePoints.
    */
  def fetchVVHRoadlinkHistory(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[VVHRoadlink] = {
    val definition = historyLayerDefinition(withMunicipalityFilter(municipalities))
    val url = vvhRestApiEndPoint + roadLinkDataHistoryService + "/FeatureServer/query?" +
      s"layerDefs=$definition&geometry=" + bounds.leftBottom.x + "," + bounds.leftBottom.y + "," + bounds.rightTop.x + "," + bounds.rightTop.y +
      "&geometryType=esriGeometryEnvelope&spatialRel=esriSpatialRelIntersects&" + queryParameters()

    fetchVVHFeatures(url) match {
      case Left(features) => features.map(extractVVHFeature)
      case Right(error) => throw new VVHClientException(error.toString)
    }
  }
}



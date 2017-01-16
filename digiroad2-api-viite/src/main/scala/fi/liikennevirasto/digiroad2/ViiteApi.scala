package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset._
import scala.util.parsing.json._
import fi.liikennevirasto.digiroad2.authentication.RequestHeaderAuthentication
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.viite.RoadAddressService
import fi.liikennevirasto.viite.dao.CalibrationPoint
import fi.liikennevirasto.viite.model.{RoadAddressLink, RoadAddressLinkPartitioner}
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.{NotFound, _}
import org.slf4j.LoggerFactory

/**
  * Created by venholat on 25.8.2016.
  */
class ViiteApi(val roadLinkService: RoadLinkService, val vVHClient: VVHClient,
               val roadAddressService: RoadAddressService,
               val userProvider: UserProvider = Digiroad2Context.userProvider
               )
  extends ScalatraServlet
    with JacksonJsonSupport
    with CorsSupport
    with RequestHeaderAuthentication
    with GZipSupport {

  class Contains(r: Range) { def unapply(i: Int): Boolean = r contains i }

  val DrawMainRoadPartsOnly = 1
  val DrawRoadPartsOnly = 2
  val DrawPublicRoads = 3
  val DrawAllRoads = 4

  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

  val logger = LoggerFactory.getLogger(getClass)
  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    contentType = formats("json") + "; charset=utf-8"
    try {
      authenticateForApi(request)(userProvider)
      if (request.isWrite && !userProvider.getCurrentUser().hasViiteWriteAccess) {
        halt(Unauthorized("No write permissions"))
      }
    } catch {
      case ise: IllegalStateException => halt(Unauthorized("Authentication error: " + ise.getMessage))
    }
    response.setHeader(Digiroad2Context.Digiroad2ServerOriginatedResponseHeader, "true")
  }

  get("/startupParameters") {
    val (east, north, zoom) = {
      val config = userProvider.getCurrentUser().configuration
      (config.east.map(_.toDouble), config.north.map(_.toDouble), config.zoom.map(_.toInt))
    }
    StartupParameters(east.getOrElse(390000), north.getOrElse(6900000), zoom.getOrElse(2))
  }


  get("/roadlinks") {
    response.setHeader("Access-Control-Allow-Headers", "*")

    val user = userProvider.getCurrentUser()
    val municipalities: Set[Int] = if (user.isOperator) Set() else user.configuration.authorizedMunicipalities

    val zoomLevel = chooseDrawType(params.getOrElse("zoom", "5"))

    params.get("bbox")
      .map(getRoadLinksFromVVH(municipalities, zoomLevel))
      .getOrElse(BadRequest("Missing mandatory 'bbox' parameter"))
  }

  get("/roadlinks/:linkId") {
    val linkId = params("linkId").toLong
    val roadLinks = roadAddressService.getRoadAddressLink(linkId)
    if (roadLinks.nonEmpty) {
      val roadLink = roadLinks.tail.foldLeft(roadLinks.head) { case (a, b) =>
        a.copy(startAddressM = Math.min(a.startAddressM, b.startAddressM), endAddressM = Math.max(a.endAddressM, b.endAddressM),
          startMValue = Math.min(a.startMValue, b.endMValue))
      }
      Map("middlePoint" -> GeometryUtils.calculatePointFromLinearReference(roadLink.geometry,
        roadLink.length / 2.0)) ++ roadAddressLinkToApi(roadLink)
    } else {
      NotFound("Road link with link ID " + linkId + " not found")
    }
  }

    get("/roadlinks/adjacent") {
      val linkid = params("linkid").toLong
      val roadNumber = params("roadNumber").toLong
      val roadPartNumber = params("roadPartNumber").toLong
      val trackCode = params("trackCode").toLong
      roadAddressService.getFloatingAdjacent(linkid, roadNumber, roadPartNumber, trackCode).map(roadAddressLinkToApi)
    }

  get("/roadlinks/multiSourceAdjacents") {
      val roadData = JSON.parseFull(params.get("roadData").get).get.asInstanceOf[Seq[Map[String,Double]]]
      if (roadData.isEmpty){
        Set.empty
      } else {
        val adjacents:Seq[RoadAddressLink] = {
          roadData.flatMap(rd => {
            roadAddressService.getFloatingAdjacent(rd.get("linkId").get.toLong,
              rd.get("roadNumber").get.toLong, rd.get("roadPartNumber").get.toLong, rd.get("trackCode").get.toLong)
          })
        }
        val linkIds: Seq[Long] = roadData.map(rd => rd.get("linkId").get.toLong)
        val result = adjacents.filter(adj => {
          !linkIds.contains(adj.linkId)
        })
        result.map(roadAddressLinkToApi)
      }
  }

  private def getRoadLinksFromVVH(municipalities: Set[Int], zoomLevel: Int)(bbox: String): Seq[Seq[Map[String, Any]]] = {
    val boundingRectangle = constructBoundingRectangle(bbox)
    val viiteRoadLinks = zoomLevel match {
      //TODO: When well-performing solution for main parts and road parts is ready
      case DrawMainRoadPartsOnly =>
//        roadAddressService.getCoarseRoadParts(boundingRectangle, Seq((1, 99)), municipalities)
        Seq()
      case DrawRoadPartsOnly =>
//        roadAddressService.getRoadParts(boundingRectangle, Seq((1, 19999)), municipalities)
        Seq()
      case DrawPublicRoads => roadAddressService.getRoadAddressLinks(boundingRectangle, Seq((1, 19999), (40000,49999)), municipalities, publicRoads = false)
      case DrawAllRoads => roadAddressService.getRoadAddressLinks(boundingRectangle, Seq(), municipalities, everything = true)
      case _ => roadAddressService.getRoadAddressLinks(boundingRectangle, Seq((1, 19999)), municipalities)
    }

    val partitionedRoadLinks = RoadAddressLinkPartitioner.partition(viiteRoadLinks)
    partitionedRoadLinks.map {
      _.map(roadAddressLinkToApi)
    }
  }

  private def chooseDrawType(zoomLevel: String) = {
    val C1 = new Contains(-10 to 3)
    val C2 = new Contains(4 to 5)
    val C3 = new Contains(6 to 10)
    val C4 = new Contains(10 to 16)
    try {
      val level: Int = zoomLevel.toInt
      level match {
        case C1() => DrawMainRoadPartsOnly
        case C2() => DrawRoadPartsOnly
        case C3() => DrawPublicRoads
        case C4() => DrawAllRoads
        case _ => DrawMainRoadPartsOnly
      }
    } catch {
      case ex: NumberFormatException => DrawMainRoadPartsOnly
    }
  }

  private[this] def constructBoundingRectangle(bbox: String) = {
    val BBOXList = bbox.split(",").map(_.toDouble)
    BoundingRectangle(Point(BBOXList(0), BBOXList(1)), Point(BBOXList(2), BBOXList(3)))
  }

  def roadAddressLinkToApi(roadAddressLink: RoadAddressLink): Map[String, Any] = {
    Map(
      "segmentId" -> roadAddressLink.id,
      "id" -> roadAddressLink.id,
      "linkId" -> roadAddressLink.linkId,
      "mmlId" -> roadAddressLink.attributes.get("MTKID"),
      "points" -> roadAddressLink.geometry,
      "calibrationPoints" -> Seq(calibrationPoint(roadAddressLink.geometry, roadAddressLink.startCalibrationPoint),
        calibrationPoint(roadAddressLink.geometry, roadAddressLink.endCalibrationPoint)),
      "administrativeClass" -> roadAddressLink.administrativeClass.toString,
      "roadClass" -> roadAddressService.roadClass(roadAddressLink),
      "roadType" -> roadAddressLink.roadType.displayValue,
      "modifiedAt" -> roadAddressLink.modifiedAt,
      "modifiedBy" -> roadAddressLink.modifiedBy,
      "municipalityCode" -> roadAddressLink.attributes.get("MUNICIPALITYCODE"),
      "roadNameFi" -> roadAddressLink.attributes.get("ROADNAME_FI"),
      "roadNameSe" -> roadAddressLink.attributes.get("ROADNAME_SE"),
      "roadNameSm" -> roadAddressLink.attributes.get("ROADNAME_SM"),
      "roadNumber" -> roadAddressLink.roadNumber,
      "roadPartNumber" -> roadAddressLink.roadPartNumber,
      "elyCode" -> roadAddressLink.elyCode,
      "trackCode" -> roadAddressLink.trackCode,
      "startAddressM" -> roadAddressLink.startAddressM,
      "endAddressM" -> roadAddressLink.endAddressM,
      "discontinuity" -> roadAddressLink.discontinuity,
      "endDate" -> roadAddressLink.endDate,
      "anomaly" -> roadAddressLink.anomaly.value,
      "roadLinkType" -> roadAddressLink.roadLinkType.value
    )
  }

  private def calibrationPoint(geometry: Seq[Point], calibrationPoint: Option[CalibrationPoint]) = {
    calibrationPoint match {
      case Some(point) =>
        Option(Seq(("point", GeometryUtils.calculatePointFromLinearReference(geometry, point.segmentMValue)), ("value", point.addressMValue)).toMap)
      case _ => None
    }
  }

  case class StartupParameters(lon: Double, lat: Double, zoom: Int)

  get("/user/roles") {
    userProvider.getCurrentUser().configuration.roles
  }

}


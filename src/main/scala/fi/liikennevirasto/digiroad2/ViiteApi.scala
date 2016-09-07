package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.authentication.RequestHeaderAuthentication
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkPartitioner}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.RoadAddressService
import fi.liikennevirasto.viite.dao.{RoadAddress, RoadAddressDAO}
import fi.liikennevirasto.viite.model.{CalibrationPoint, RoadAddressLink, RoadAddressLinkPartitioner}
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.{NotFound, _}
import org.scalatra.json.JacksonJsonSupport

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

  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

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

    params.get("bbox")
      .map(getRoadLinksFromVVH(municipalities))
      .getOrElse(BadRequest("Missing mandatory 'bbox' parameter"))
  }

  get("/roadlinks/:linkId") {
    val linkId = params("linkId").toLong
    roadLinkService.getRoadLinkMiddlePointByLinkId(linkId).map {
      case (id, middlePoint) => Map("id" -> id, "middlePoint" -> middlePoint)
    }.getOrElse(NotFound("Road link with link ID " + linkId + " not found"))
  }

  private def getRoadLinksFromVVH(municipalities: Set[Int])(bbox: String): Seq[Seq[Map[String, Any]]] = {
    val boundingRectangle = constructBoundingRectangle(bbox)
    val roadLinks = roadLinkService.getViiteRoadLinksFromVVH(boundingRectangle, (1, 19999), municipalities)
    val addresses = withDynSession {
      RoadAddressDAO.fetchByLinkId(roadLinks.map(_.linkId).toSet).map(ra => ra.linkId -> ra).toMap
    }
    println("Found " + addresses.size + " road address links")
    val viiteRoadLinks = roadLinks.map { rl =>
      val ra = addresses.get(rl.linkId)
      ra match {
        case Some(addr) =>
          buildRoadAddressLink(rl, Option(addr), None, None)
        case _ => buildRoadAddressLink(rl, None, None, None)
      }
    }
    val partitionedRoadLinks = RoadAddressLinkPartitioner.partition(viiteRoadLinks)
    partitionedRoadLinks.map {
      _.map(roadAddressLinkToApi)
    }
  }

  private[this] def constructBoundingRectangle(bbox: String) = {
    val BBOXList = bbox.split(",").map(_.toDouble)
    BoundingRectangle(Point(BBOXList(0), BBOXList(1)), Point(BBOXList(2), BBOXList(3)))
  }

  def roadLinkToApi(roadLink: RoadLink): Map[String, Any] = {
    Map(
      "linkId" -> roadLink.linkId,
      "mmlId" -> roadLink.attributes.get("MTKID"),
      "points" -> roadLink.geometry,
      "administrativeClass" -> roadLink.administrativeClass.toString,
      "linkType" -> roadLink.linkType.value,
      "functionalClass" -> roadLink.functionalClass,
      "trafficDirection" -> roadLink.trafficDirection.toString,
      "modifiedAt" -> roadLink.modifiedAt,
      "modifiedBy" -> roadLink.modifiedBy,
      "municipalityCode" -> roadLink.attributes.get("MUNICIPALITYCODE"),
      "verticalLevel" -> roadLink.attributes.get("VERTICALLEVEL"),
      "roadNameFi" -> roadLink.attributes.get("ROADNAME_FI"),
      "roadNameSe" -> roadLink.attributes.get("ROADNAME_SE"),
      "roadNameSm" -> roadLink.attributes.get("ROADNAME_SM"),
      "minAddressNumberRight" -> roadLink.attributes.get("FROM_RIGHT"),
      "maxAddressNumberRight" -> roadLink.attributes.get("TO_RIGHT"),
      "minAddressNumberLeft" -> roadLink.attributes.get("FROM_LEFT"),
      "maxAddressNumberLeft" -> roadLink.attributes.get("TO_LEFT"),
      "roadPartNumber" -> roadLink.attributes.get("ROADPARTNUMBER"),
      "roadNumber" -> roadLink.attributes.get("ROADNUMBER"))
  }

  def roadAddressLinkToApi(roadLink: RoadAddressLink): Map[String, Any] = {
    Map(
      "linkId" -> roadLink.linkId,
      "mmlId" -> roadLink.attributes.get("MTKID"),
      "points" -> roadLink.geometry,
      "administrativeClass" -> roadLink.administrativeClass.toString,
      "roadClass" -> roadAddressService.roadClass(roadLink),
      "linkType" -> roadLink.linkType.value,
      "functionalClass" -> roadLink.functionalClass,
      "trafficDirection" -> roadLink.trafficDirection.toString,
      "modifiedAt" -> roadLink.modifiedAt,
      "modifiedBy" -> roadLink.modifiedBy,
      "municipalityCode" -> roadLink.attributes.get("MUNICIPALITYCODE"),
      "verticalLevel" -> roadLink.attributes.get("VERTICALLEVEL"),
      "roadNameFi" -> roadLink.attributes.get("ROADNAME_FI"),
      "roadNameSe" -> roadLink.attributes.get("ROADNAME_SE"),
      "roadNameSm" -> roadLink.attributes.get("ROADNAME_SM"),
      "minAddressNumberRight" -> roadLink.attributes.get("FROM_RIGHT"),
      "maxAddressNumberRight" -> roadLink.attributes.get("TO_RIGHT"),
      "minAddressNumberLeft" -> roadLink.attributes.get("FROM_LEFT"),
      "maxAddressNumberLeft" -> roadLink.attributes.get("TO_LEFT"),
      "roadPartNumber" -> roadLink.roadPartNumber,
      "roadNumber" -> roadLink.roadNumber)
  }

  case class StartupParameters(lon: Double, lat: Double, zoom: Int)

  get("/user/roles") {
    userProvider.getCurrentUser().configuration.roles
  }

  def buildRoadAddressLink(rl: RoadLink, roadAddr: Option[RoadAddress],
                           startCalibrationPoint: Option[CalibrationPoint],
                           endCalibrationPoint: Option[CalibrationPoint]): RoadAddressLink =
    roadAddr match {
      case Some(ra) => new RoadAddressLink(rl.linkId, rl.geometry,
        rl.length,  rl.administrativeClass,
        rl.functionalClass,  rl.trafficDirection,
        rl.linkType,  rl.modifiedAt,  rl.modifiedBy,
        rl.attributes, ra.roadNumber, ra.roadPartNumber, ra.track.value, ra.discontinuity.value,
        ra.startAddrMValue, ra.endAddrMValue, ra.startMValue, ra.endMValue, toSideCode(ra.startMValue, ra.endMValue, ra.track),
        startCalibrationPoint, endCalibrationPoint)
      case _ => new RoadAddressLink(rl.linkId, rl.geometry,
        rl.length,  rl.administrativeClass,
        rl.functionalClass,  rl.trafficDirection,
        rl.linkType,  rl.modifiedAt,  rl.modifiedBy,
        rl.attributes, 0, 0, 0, 0,
        0, 0, 0, 0, SideCode.Unknown,
        startCalibrationPoint, endCalibrationPoint)
    }

  private def toSideCode(startMValue: Double, endMValue: Double, track: Track) = {
    track match {
      case Track.Combined => SideCode.BothDirections
      case Track.LeftSide => if (startMValue < endMValue) {
        SideCode.TowardsDigitizing
      } else {
        SideCode.AgainstDigitizing
      }
      case Track.RightSide => if (startMValue > endMValue) {
        SideCode.TowardsDigitizing
      } else {
        SideCode.AgainstDigitizing
      }
      case _ => SideCode.Unknown
    }
  }
}


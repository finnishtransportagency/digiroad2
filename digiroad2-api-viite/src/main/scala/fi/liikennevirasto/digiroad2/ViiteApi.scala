package fi.liikennevirasto.digiroad2

import java.text.SimpleDateFormat

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.authentication.RequestHeaderAuthentication
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.digiroad2.util.RoadAddressException
import fi.liikennevirasto.viite.dao._
import fi.liikennevirasto.viite.model.{ProjectAddressLink, RoadAddressLink, RoadAddressLinkPartitioner}
import fi.liikennevirasto.viite.{ChangeProject, ProjectService, ReservedRoadPart, RoadAddressService}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.json4s._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.{NotFound, _}
import org.slf4j.LoggerFactory

import scala.util.parsing.json._
import scala.util.{Left, Right}

/**
  * Created by venholat on 25.8.2016.
  */

case class NewAddressDataExtracted(sourceIds: Set[Long], targetIds: Set[Long], roadAddress: Seq[RoadAddressCreator])


case class RoadAddressProjectExtractor(id: Long, status: Long, name: String, startDate: String, additionalInfo: String,roadPartList: List[ReservedRoadPart])

case class RoadAddressProjectLinkUpdate(linkIds: Seq[Long], projectId: Long, newStatus: Int)
class ViiteApi(val roadLinkService: RoadLinkService, val vVHClient: VVHClient,
               val roadAddressService: RoadAddressService,
               val projectService: ProjectService,
               val userProvider: UserProvider = Digiroad2Context.userProvider,
               val deploy_date: String = Digiroad2Context.deploy_date
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
  protected implicit val jsonFormats: Formats = DefaultFormats + DiscontinuitySerializer
  JSON.globalNumberParser = {
    in =>
      try in.toLong catch { case _: NumberFormatException => in.toDouble }
  }

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
    StartupParameters(east.getOrElse(390000), north.getOrElse(6900000), zoom.getOrElse(2), deploy_date)
  }


  get("/roadlinks") {
    response.setHeader("Access-Control-Allow-Headers", "*")

    val user = userProvider.getCurrentUser()
    val municipalities: Set[Int] = if (user.isViiteUser()) Set() else user.configuration.authorizedMunicipalities

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
    val data = JSON.parseFull(params.getOrElse("roadData", "{}")).get.asInstanceOf[Map[String,Any]]
    val chainLinks = data("selectedLinks").asInstanceOf[Seq[Long]].toSet
    val linkId = data("linkId").asInstanceOf[Long]
    val roadNumber = data("roadNumber").asInstanceOf[Long]
    val roadPartNumber = data("roadPartNumber").asInstanceOf[Long]
    val trackCode = data("trackCode").asInstanceOf[Long].toInt

    roadAddressService.getFloatingAdjacent(chainLinks, linkId, roadNumber, roadPartNumber, trackCode).map(roadAddressLinkToApi)
  }

  get("/roadlinks/adjacent/target") {
    val data = JSON.parseFull(params.getOrElse("roadData", "{}")).get.asInstanceOf[Map[String,Any]]
    val chainLinks = data("selectedLinks").asInstanceOf[Seq[Long]].toSet
    val linkId = data("linkId").asInstanceOf[Long]

    roadAddressService.getAdjacent(chainLinks, linkId).map(roadAddressLinkToApi)
  }
  get("/roadlinks/multiSourceAdjacents") {
    val roadData = JSON.parseFull(params.getOrElse("roadData", "[]")).get.asInstanceOf[Seq[Map[String,Any]]]
    if (roadData.isEmpty){
      Set.empty
    } else {
      val adjacents: Seq[RoadAddressLink] = {
        roadData.flatMap(rd => {
          val chainLinks = rd("selectedLinks").asInstanceOf[Seq[Long]].toSet
          val linkId = rd("linkId").asInstanceOf[Long]
          val roadNumber = rd("roadNumber").asInstanceOf[Long]
          val roadPartNumber = rd("roadPartNumber").asInstanceOf[Long]
          val trackCode = rd("trackCode").asInstanceOf[Long].toInt
          roadAddressService.getFloatingAdjacent(chainLinks, linkId,
            roadNumber, roadPartNumber, trackCode)
        })
      }
      val linkIds: Seq[Long] = roadData.map(rd => rd("linkId").asInstanceOf[Long])
      val result = adjacents.filter(adj => {
        !linkIds.contains(adj.linkId)
      }).distinct
      result.map(roadAddressLinkToApi)
    }
  }

  get("/roadlinks/checkproject/") {
    val linkId = params("projectId").toLong
    projectService.getProjectStatusFromTR(linkId)
  }

  get("/roadlinks/transferRoadLink") {
    val (sources, targets) = roadlinksData()
    val user = userProvider.getCurrentUser()
    try{
      val result = roadAddressService.getRoadAddressLinksAfterCalculation(sources, targets, user)
      result.map(roadAddressLinkToApi)
    }
    catch {
      case e: IllegalArgumentException =>
        logger.warn("Invalid transfer attempted: " + e.getMessage, e)
        BadRequest("Invalid transfer attempted: " + e.getMessage)
      case e: Exception =>
        logger.warn(e.getMessage, e)
        InternalServerError("An unexpected error occurred while processing this action.")
    }
  }

  put("/roadlinks/roadaddress") {
    val data = parsedBody.extract[NewAddressDataExtracted]
    val roadAddressData = data.roadAddress
    val sourceIds = data.sourceIds
    val targetIds = data.targetIds
    val user = userProvider.getCurrentUser()
    val formatter = DateTimeFormat.forPattern("dd.MM.yyyy")

    val roadAddresses = roadAddressService.getRoadAddressesAfterCalculation(sourceIds.toSeq.map(_.toString), targetIds.toSeq.map(_.toString), user)
    try {
      val transferredRoadAddresses = roadAddressService.transferFloatingToGap(sourceIds, targetIds, roadAddresses, user.username)
      transferredRoadAddresses
    }
    catch {
      case e: RoadAddressException =>
        logger.warn(e.getMessage)
        InternalServerError("An unexpected error occurred while processing this action.")
    }
  }

  post("/roadlinks/roadaddress/project/create"){
    val project = parsedBody.extract[RoadAddressProjectExtractor]
    val user = userProvider.getCurrentUser()
    val formatter = DateTimeFormat.forPattern("dd.MM.yyyy")
    val roadAddressProject= RoadAddressProject(project.id, ProjectState.apply(project.status), project.name,
      user.username, DateTime.now(), "-", formatter.parseDateTime(project.startDate), DateTime.now(), project.additionalInfo, project.roadPartList, None)
    try {
      val (projectSaved, addr, info, success) = projectService.createRoadLinkProject(roadAddressProject)
      Map("project" -> projectToApi(projectSaved), "projectAddresses" -> addr, "formInfo" -> info,
        "success" -> success)
    } catch {
      case ex: IllegalArgumentException => BadRequest(s"A project with id ${project.id} has already been created")
    }
  }

  post("/roadlinks/roadaddress/project/sendToTR") {
    (parsedBody \ "projectID").extractOpt[Long].map(projectService.publishProject)
      .getOrElse(BadRequest(s"Invalid arguments"))
  }


  put("/roadlinks/roadaddress/project/save"){
    val project = parsedBody.extract[RoadAddressProjectExtractor]
    val user = userProvider.getCurrentUser()
    val formatter = DateTimeFormat.forPattern("dd.MM.yyyy")
    val roadAddressProject= RoadAddressProject(project.id, ProjectState.apply(project.status), project.name,
      user.username, DateTime.now(), "-", formatter.parseDateTime(project.startDate), DateTime.now(), project.additionalInfo, project.roadPartList, None)
    try {
      val (projectSaved, addr, _, success) = projectService.saveRoadLinkProject(roadAddressProject)
      val info = projectService.getProjectsWithReservedRoadParts(projectSaved.id)._2
      val trPreview= projectService.getChangeProject(project.id)
      Map("project" -> projectToApi(projectSaved), "projectAddresses" -> addr, "formInfo" -> info, "trPreview"->trPreview,
        "success" -> success)
    } catch {
      case ex: IllegalArgumentException =>
        NotFound(s"Project id ${project.id} not found")
    }
  }

  get("/roadlinks/roadaddress/project/all") {
    projectService.getRoadAddressAllProjects().map(roadAddressProjectToApi)
  }

  get("/roadlinks/roadaddress/project/all/projectId/:id") {
    val projectId = params("id").toLong
    val (projects, projectLinks) = projectService.getProjectsWithReservedRoadParts(projectId)
    val project = Seq(projects).map(roadAddressProjectToApi)
    val projectsWithLinkId = project.head
    val publishable = projectService.projectLinkPublishable(projectId) && projectLinks.nonEmpty
    Map("project" -> projectsWithLinkId,"linkId" -> projectLinks.headOption.map(_.startingLinkId), "projectLinks" -> projectLinks, "publishable" -> publishable)
  }

  get("/roadlinks/roadaddress/project/validatereservedlink/"){
    val roadNumber = params("roadNumber").toLong
    val startPart = params("startPart").toLong
    val endPart = params("endPart").toLong
    val projDate = params("projDate").toString
    val formatter = DateTimeFormat.forPattern("dd.MM.yyyy")
    val errorMessageOpt=projectService.checkRoadAddressNumberAndSEParts(roadNumber, startPart, endPart)
    if (errorMessageOpt.isEmpty) {
        projectService.checkReservability(roadNumber, startPart, endPart) match {
        case Left(err) => Map("success"-> err, "roadparts" -> Seq.empty)
        case Right(reservedRoadParts) => {
          projectService.projDateValidation(reservedRoadParts, formatter.parseDateTime(projDate)) match {
            case Some(errMsg) => Map("success"-> errMsg)
            case None => Map("success" -> "ok", "roadparts" -> reservedRoadParts.map(reservedRoadPartToApi))
          }
        }
      }
    } else
      Map("success"-> errorMessageOpt.get)
  }

  get("/project/roadlinks"){
    response.setHeader("Access-Control-Allow-Headers", "*")

    val user = userProvider.getCurrentUser()

    val zoomLevel = chooseDrawType(params.getOrElse("zoom", "5"))
    val projectId: Long = params.get("id") match {
      case Some (s) if s != "" && s.toLong != 0 => s.toLong
      case _ =>

        0L
    }
    if (projectId == 0)
      BadRequest("Missing mandatory 'id' parameter")
    else
      params.get("bbox")
        .map(getProjectLinks(projectId, zoomLevel))
        .getOrElse(BadRequest("Missing mandatory 'bbox' parameter"))
  }

  put("/project/roadlinks"){
    val user = userProvider.getCurrentUser()

    val modification = parsedBody.extract[RoadAddressProjectLinkUpdate]
    val updated = projectService.updateProjectLinkStatus(modification.projectId, modification.linkIds.toSet,
      LinkStatus.apply(modification.newStatus), user.username)
    Map("projectId" -> modification.projectId, "publishable" -> (updated &&
      projectService.projectLinkPublishable(modification.projectId)))
  }

  get("/project/getchangetable/:projectId") {
    val projectId = params("projectId").toLong
    projectService.getChangeProject(projectId).map(project =>
        Map(
          "id" -> project.id,
          "ely" -> project.ely,
          "user" -> project.user,
          "name" -> project.name,
          "changeDate" -> project.changeDate,
          "changeInfoSeq"-> project.changeInfoSeq.map(changeInfo=>
            Map("changetype"->changeInfo.changeType.value, "roadType"->changeInfo.roadType.value,
              "discontinuity"->changeInfo.discontinuity.description, "source"->changeInfo.source,
              "target"->changeInfo.target)))
    ).getOrElse(PreconditionFailed())
  }

    def changeinfoSeq(changeinfo:Seq[RoadAddressChangeInfo]) :(String, Any) = ???

  post("/project/publish"){
    val user = userProvider.getCurrentUser()
    val projectId = params.get("projectId")

    projectId.map(_.toLong).map(projectService.publishProject).map(_.errorMessage).map {
      case Some(s) => PreconditionFailed(s)
      case _ => Map("status" -> "ok")
    }.getOrElse(BadRequest("Missing mandatory 'projectId' parameter"))
  }

  private def roadlinksData(): (Seq[String], Seq[String]) = {
    val data = JSON.parseFull(params.get("data").get).get.asInstanceOf[Map[String,Any]]
    val sources = data("sourceLinkIds").asInstanceOf[Seq[String]]
    val targets = data("targetLinkIds").asInstanceOf[Seq[String]]
    (sources, targets)
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

  private def getProjectLinks(projectId: Long, zoomLevel: Int)(bbox: String): Seq[Seq[Map[String, Any]]] = {
    val boundingRectangle = constructBoundingRectangle(bbox)
    val viiteRoadLinks = zoomLevel match {
      case DrawMainRoadPartsOnly =>
        Seq()
      case DrawRoadPartsOnly =>
        Seq()
      case DrawPublicRoads => projectService.getProjectRoadLinks(projectId, boundingRectangle, Seq((1, 19999), (40000,49999)), Set(), publicRoads = false)
      case DrawAllRoads => projectService.getProjectRoadLinks(projectId, boundingRectangle, Seq(), Set(), everything = true)
      case _ => projectService.getProjectRoadLinks(projectId, boundingRectangle, Seq((1, 19999)), Set())
    }

    val partitionedRoadLinks = RoadAddressLinkPartitioner.partition(viiteRoadLinks)
    partitionedRoadLinks.map {
      _.map(projectAddressLinkToApi)
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
      "startDate" -> roadAddressLink.startDate,
      "endDate" -> roadAddressLink.endDate,
      "anomaly" -> roadAddressLink.anomaly.value,
      "roadLinkType" -> roadAddressLink.roadLinkType.value,
      "constructionType" ->roadAddressLink.constructionType.value,
      "startMValue" -> roadAddressLink.startMValue,
      "endMValue" -> roadAddressLink.endMValue,
      "sideCode" -> roadAddressLink.sideCode.value,
      "linkType" -> roadAddressLink.linkType.value,
      "roadLinkSource" ->  roadAddressLink.roadLinkSource.value,
      "newGeometry" -> roadAddressLink.newGeometry
    )
  }

  def projectAddressLinkToApi(projectAddressLink: ProjectAddressLink): Map[String, Any] = {
    Map(
      "segmentId" -> projectAddressLink.id,
      "id" -> projectAddressLink.id,
      "linkId" -> projectAddressLink.linkId,
      "mmlId" -> projectAddressLink.attributes.get("MTKID"),
      "points" -> projectAddressLink.geometry,
      "calibrationPoints" -> Seq(calibrationPoint(projectAddressLink.geometry, projectAddressLink.startCalibrationPoint),
        calibrationPoint(projectAddressLink.geometry, projectAddressLink.endCalibrationPoint)),
      "administrativeClass" -> projectAddressLink.administrativeClass.toString,
      "roadClass" -> roadAddressService.roadClass(projectAddressLink),
      "roadType" -> projectAddressLink.roadType.displayValue,
      "modifiedAt" -> projectAddressLink.modifiedAt,
      "modifiedBy" -> projectAddressLink.modifiedBy,
      "municipalityCode" -> projectAddressLink.attributes.get("MUNICIPALITYCODE"),
      "roadNameFi" -> projectAddressLink.attributes.get("ROADNAME_FI"),
      "roadNameSe" -> projectAddressLink.attributes.get("ROADNAME_SE"),
      "roadNameSm" -> projectAddressLink.attributes.get("ROADNAME_SM"),
      "roadNumber" -> projectAddressLink.roadNumber,
      "roadPartNumber" -> projectAddressLink.roadPartNumber,
      "elyCode" -> projectAddressLink.elyCode,
      "trackCode" -> projectAddressLink.trackCode,
      "startAddressM" -> projectAddressLink.startAddressM,
      "endAddressM" -> projectAddressLink.endAddressM,
      "discontinuity" -> projectAddressLink.discontinuity,
      "anomaly" -> projectAddressLink.anomaly.value,
      "roadLinkType" -> projectAddressLink.roadLinkType.value,
      "constructionType" ->projectAddressLink.constructionType.value,
      "startMValue" -> projectAddressLink.startMValue,
      "endMValue" -> projectAddressLink.endMValue,
      "sideCode" -> projectAddressLink.sideCode.value,
      "linkType" -> projectAddressLink.linkType.value,
      "roadLinkSource" ->  projectAddressLink.roadLinkSource.value,
      "status" -> projectAddressLink.status.value
    )
  }

  def roadAddressProjectToApi(roadAddressProject: RoadAddressProject): Map[String, Any] = {
    Map(
      "id" -> roadAddressProject.id,
      "statusCode" -> roadAddressProject.status.value,
      "statusDescription" -> roadAddressProject.status.description,
      "name" -> roadAddressProject.name,
      "createdBy" -> roadAddressProject.createdBy,
      "createdDate" -> { if (roadAddressProject.createdDate==null){null} else {roadAddressProject.createdDate.toString}},
      "dateModified" -> { if (roadAddressProject.dateModified==null){null} else {formatToString(roadAddressProject.dateModified.toString)}},
      "startDate" -> { if (roadAddressProject.startDate==null){null} else {formatToString(roadAddressProject.startDate.toString)}},
      "modifiedBy" -> roadAddressProject.modifiedBy,
      "additionalInfo" -> roadAddressProject.additionalInfo,
      "statusInfo" -> roadAddressProject.statusInfo
    )
  }

  def projectToApi(roadAddressProject: RoadAddressProject) : Map[String, Any] = {
    val formatter = DateTimeFormat.forPattern("dd.MM.yyyy")
    Map(
      "id" -> roadAddressProject.id,
      "dateModified" -> roadAddressProject.dateModified.toString(formatter),
      "startDate" -> roadAddressProject.startDate.toString(formatter),
      "additionalInfo" -> roadAddressProject.additionalInfo,
      "createdBy" -> roadAddressProject.createdBy,
      "modifiedBy" -> roadAddressProject.modifiedBy,
      "name" -> roadAddressProject.name,
      "status" -> roadAddressProject.status,
      "statusInfo" -> roadAddressProject.statusInfo
    )
  }

  def reservedRoadPartToApi(reservedRoadPart: ReservedRoadPart) : Map[String, Any] = {
    Map("roadNumber" -> reservedRoadPart.roadNumber,
      "roadPartNumber" -> reservedRoadPart.roadPartNumber,
      "roadPartId" -> reservedRoadPart.roadPartId,
      "ely" -> reservedRoadPart.ely,
      "length" -> reservedRoadPart.length,
      "discontinuity" -> reservedRoadPart.discontinuity.description
    )
  }

  def formatToString(entryDate: String): String = {
    val date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(entryDate)
    val formattedDate = new SimpleDateFormat("dd.MM.yyyy").format(date)
    formattedDate
  }

  private def calibrationPoint(geometry: Seq[Point], calibrationPoint: Option[CalibrationPoint]) = {
    calibrationPoint match {
      case Some(point) =>
        Option(Seq(("point", GeometryUtils.calculatePointFromLinearReference(geometry, point.segmentMValue)), ("value", point.addressMValue)).toMap)
      case _ => None
    }
  }

  case class StartupParameters(lon: Double, lat: Double, zoom: Int, deploy_date: String)

  get("/user/roles") {
    userProvider.getCurrentUser().configuration.roles
  }

}

case object DiscontinuitySerializer extends CustomSerializer[Discontinuity](format => ( {
  case s: JString => Discontinuity.apply(s.values)
  case i: JInt => Discontinuity.apply(i.values.intValue)
}, {
  case s: Discontinuity => JString(s.description)
}))

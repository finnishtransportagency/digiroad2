package fi.liikennevirasto.digiroad2

import java.text.SimpleDateFormat

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.authentication.RequestHeaderAuthentication
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.{User, UserProvider}
import fi.liikennevirasto.digiroad2.util.{DigiroadSerializers, RoadAddressException, RoadPartReservedException}
import fi.liikennevirasto.viite._
import fi.liikennevirasto.viite.dao._
import fi.liikennevirasto.viite.model._
import fi.liikennevirasto.viite.util.SplitOptions
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

case class NewAddressDataExtracted(sourceIds: Set[Long], targetIds: Set[Long])

case class RevertSplitExtractor(projectId: Option[Long], linkId: Option[Long], coordinates: ProjectCoordinates)
case class RevertRoadLinksExtractor(projectId: Long, roadNumber: Long, roadPartNumber: Long, links: List[LinkToRevert], coordinates: ProjectCoordinates)

case class ProjectRoadAddressInfo(projectId: Long, roadNumber: Long, roadPartNumber: Long)

case class RoadAddressProjectExtractor(id: Long, projectEly: Option[Long], status: Long, name: String, startDate: String,
                                       additionalInfo: String, roadPartList: List[RoadPartExtractor], resolution: Int)

case class RoadAddressProjectLinksExtractor(linkIds: Seq[Long], linkStatus: Int, projectId: Long, roadNumber: Long, roadPartNumber: Long, trackCode: Int, discontinuity: Int, roadEly: Long, roadLinkSource: Int, roadType: Int, userDefinedEndAddressM: Option[Int], coordinates: ProjectCoordinates)

case class RoadPartExtractor(roadNumber: Long, roadPartNumber: Long, ely: Long)

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

  class Contains(r: Range) {
    def unapply(i: Int): Boolean = r contains i
  }

  val DrawMainRoadPartsOnly = 1
  val DrawRoadPartsOnly = 2
  val DrawPublicRoads = 3
  val DrawAllRoads = 4

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

  val logger = LoggerFactory.getLogger(getClass)
  protected implicit val jsonFormats: Formats = DigiroadSerializers.jsonFormats
  JSON.globalNumberParser = {
    in =>
      try in.toLong catch {
        case _: NumberFormatException => in.toDouble
      }
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
      .map(getRoadAddressLinks(municipalities, zoomLevel))
      .getOrElse(BadRequest("Missing mandatory 'bbox' parameter"))
  }

  get("/roadlinks/:linkId") {
    val linkId = params("linkId").toLong
    val roadLinks = roadAddressService.getRoadAddressLink(linkId) ++ roadAddressService.getSuravageRoadLinkAddressesByLinkIds(Set(linkId))
    val projectLinks = projectService.getProjectRoadLinksByLinkIds(Set(linkId))
    foldSegments(roadLinks).orElse(foldSegments(projectLinks)).map(midPoint).getOrElse(
      Map("success" -> false, "reason" -> ("Link " + linkId + " not found")))
  }

  get("/roadlinks/project/prefillfromvvh/:linkId") {
    val linkId = params("linkId").toLong
    projectService.fetchPreFillFromVVH(linkId) match {
      case Right(preFillInfo) => {
        Map("success" -> true, "roadNumber" -> preFillInfo.RoadNumber, "roadPartNumber" -> preFillInfo.RoadPart)
      }
      case Left(failuremessage) => {
        Map("success" -> false, "reason" -> failuremessage)
      }
    }
  }

  get("/roadlinks/adjacent") {
    val data = JSON.parseFull(params.getOrElse("roadData", "{}")).get.asInstanceOf[Map[String, Any]]
    val chainLinks = data("selectedLinks").asInstanceOf[Seq[Long]].toSet
    val linkId = data("linkId").asInstanceOf[Long]
    val roadNumber = data("roadNumber").asInstanceOf[Long]
    val roadPartNumber = data("roadPartNumber").asInstanceOf[Long]
    val trackCode = data("trackCode").asInstanceOf[Long].toInt

    roadAddressService.getFloatingAdjacent(chainLinks, linkId, roadNumber, roadPartNumber, trackCode).map(roadAddressLinkToApi)
  }

  get("/roadlinks/adjacent/target") {
    val data = JSON.parseFull(params.getOrElse("roadData", "{}")).get.asInstanceOf[Map[String, Any]]
    val chainLinks = data("selectedLinks").asInstanceOf[Seq[Long]].toSet
    val linkId = data("linkId").asInstanceOf[Long]

    roadAddressService.getAdjacent(chainLinks, linkId).map(roadAddressLinkToApi)
  }

  get("/roadlinks/multiSourceAdjacents") {
    val roadData = JSON.parseFull(params.getOrElse("roadData", "[]")).get.asInstanceOf[Seq[Map[String, Any]]]
    if (roadData.isEmpty) {
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
    try {
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
    val sourceIds = data.sourceIds
    val targetIds = data.targetIds
    val user = userProvider.getCurrentUser()

    val roadAddresses = roadAddressService.getRoadAddressesAfterCalculation(sourceIds.toSeq.map(_.toString), targetIds.toSeq.map(_.toString), user)
    try {
      val transferredRoadAddresses = roadAddressService.transferFloatingToGap(sourceIds, targetIds, roadAddresses, user.username)
      transferredRoadAddresses
    }
    catch {
      case e: RoadAddressException =>
        logger.warn(e.getMessage)
        InternalServerError("An unexpected error occurred while processing this action.")
      case e: MappingException =>
        logger.warn("Exception treating road links", e)
        BadRequest("Missing mandatory ProjectLink parameter")
    }
  }

  post("/roadlinks/roadaddress/project") {
    val project = parsedBody.extract[RoadAddressProjectExtractor]
    val user = userProvider.getCurrentUser()
    val roadAddressProject = ProjectConverter.toRoadAddressProject(project, user)
    try {
      if (project.id != 0) //we check if project is new. If it is then we check project for being in writable state
        projectWritable(roadAddressProject.id)
      val projectSaved = projectService.createRoadLinkProject(roadAddressProject, project.resolution)
      val fetched = projectService.getRoadAddressSingleProject(projectSaved.id).get
      val firstAddress: Map[String, Any] =
        fetched.reservedParts.find(_.startingLinkId.nonEmpty).map(p => "projectAddresses" -> p.startingLinkId.get).toMap
      Map("project" -> roadAddressProjectToApi(fetched), "formInfo" ->
        fetched.reservedParts.map(reservedRoadPartToApi), "success" -> true) ++ firstAddress
    } catch {
      case ex: IllegalArgumentException => BadRequest(s"A project with id ${project.id} has already been created")
      case e: MappingException =>
        logger.warn("Exception treating road links", e)
        BadRequest("Missing mandatory ProjectLink parameter")
      case ex: RuntimeException => Map("success" -> false, "errorMessage" -> ex.getMessage)
      case ex: RoadPartReservedException => Map("success" -> false, "errorMessage" -> ex.getMessage)

    }
  }

  put("/roadlinks/roadaddress/project") {
    val project = parsedBody.extract[RoadAddressProjectExtractor]
    val user = userProvider.getCurrentUser()
    val roadAddressProject = ProjectConverter.toRoadAddressProject(project, user)
    try {
      if (project.id != 0) //we check if project is new. If it is then we check project for being in writable state
        projectWritable(roadAddressProject.id)
      val projectSaved = projectService.saveProject(roadAddressProject, project.resolution)
      val firstLink = projectService.getFirstProjectLink(projectSaved)
      Map("project" -> roadAddressProjectToApi(projectSaved), "projectAddresses" -> firstLink, "formInfo" ->
        projectSaved.reservedParts.map(reservedRoadPartToApi),
        "success" -> true)
    } catch {
      case e: IllegalStateException => Map("success" -> false, "errorMessage" -> "Projekti ei ole enää muokattavissa")
      case ex: IllegalArgumentException => NotFound(s"Project id ${project.id} not found")
      case e: MappingException =>
        logger.warn("Exception treating road links", e)
        BadRequest("Missing mandatory ProjectLink parameter")
      case ex: RuntimeException => Map("success" -> false, "errorMessage" -> ex.getMessage)
      case ex: RoadPartReservedException => Map("success" -> false, "errorMessage" -> ex.getMessage)
    }
  }

  post("/roadlinks/roadaddress/project/sendToTR") {
    val projectId = (parsedBody \ "projectID").extract[Long]

    val writableProjectService = projectWritable(projectId)
    val sendStatus = writableProjectService.publishProject(projectId)
    if (sendStatus.validationSuccess && sendStatus.sendSuccess)
      Map("sendSuccess" -> true)
    else
      Map("sendSuccess" -> false, "errorMessage" -> sendStatus.errorMessage.getOrElse(""))
  }


  put("/project/reverse") {
    val user = userProvider.getCurrentUser()
    try {
      //check for validity
      val roadInfo = parsedBody.extract[RevertRoadLinksExtractor]
      val writableProjectService = projectWritable(roadInfo.projectId)
      writableProjectService.changeDirection(roadInfo.projectId, roadInfo.roadNumber, roadInfo.roadPartNumber, roadInfo.links, roadInfo.coordinates, user.username) match {
        case Some(errorMessage) =>
          Map("success" -> false, "errorMessage" -> errorMessage)
        case None => Map("success" -> true)
      }
    } catch {
      case e: IllegalStateException => Map("success" -> false, "errorMessage" -> e.getMessage)
      case ex: RuntimeException => Map("success" -> false, "errorMessage" -> ex.getMessage)
      case e: MappingException =>
        logger.warn("Exception treating road links", e)
        BadRequest("Missing mandatory ProjectLink parameter")
    }
  }

  get("/roadlinks/roadaddress/project/all") {
    projectService.getRoadAddressAllProjects().map(roadAddressProjectToApi)
  }

  get("/roadlinks/roadaddress/project/all/projectId/:id") {
    val projectId = params("id").toLong
    val project = projectService.getRoadAddressSingleProject(projectId, Seq(LinkStatus.Numbering)).get
    val projectMap = roadAddressProjectToApi(project)
    val parts = project.reservedParts.map(reservedRoadPartToApi)
    val publishable = projectService.projectLinkPublishable(projectId)
    Map("project" -> projectMap, "linkId" -> project.reservedParts.find(_.startingLinkId.nonEmpty).flatMap(_.startingLinkId),
      "projectLinks" -> parts, "publishable" -> publishable)
  }

  get("/roadlinks/roadaddress/project/validatereservedlink/") {
    val roadNumber = params("roadNumber").toLong
    val startPart = params("startPart").toLong
    val endPart = params("endPart").toLong
    val projDate = params("projDate").toString
    val formatter = DateTimeFormat.forPattern("dd.MM.yyyy")
    val errorMessageOpt = projectService.checkRoadPartsExist(roadNumber, startPart, endPart)
    if (errorMessageOpt.isEmpty) {
      projectService.checkRoadPartsReservable(roadNumber, startPart, endPart) match {
        case Left(err) => Map("success" -> err, "roadparts" -> Seq.empty)
        case Right(reservedRoadParts) => {
          if (reservedRoadParts.isEmpty) {
            Map("success" -> s"Puuttuvan tielinkkidatan takia kyseistä tieosaa ei pystytä varaamaan.")
          } else {
            projectService.validateProjectDate(reservedRoadParts, formatter.parseDateTime(projDate)) match {
              case Some(errMsg) => Map("success" -> errMsg)
              case None => Map("success" -> "ok", "roadparts" -> reservedRoadParts.map(reservedRoadPartToApi))
            }
          }
        }
      }
    } else
      Map("success" -> errorMessageOpt.get)
  }

  put("/roadlinks/roadaddress/project/revertchangesroadlink") {
    try {
      val linksToRevert = parsedBody.extract[RevertRoadLinksExtractor]
      if (linksToRevert.links.nonEmpty) {
        val writableProject = projectWritable(linksToRevert.projectId)
        val user = userProvider.getCurrentUser().username
        writableProject.revertLinks(linksToRevert.projectId, linksToRevert.roadNumber, linksToRevert.roadPartNumber, linksToRevert.links, linksToRevert.coordinates, user) match {
          case None => Map("success" -> true)
          case Some(s) => Map("success" -> false, "errorMessage" -> s)
        }
      }
    } catch {
      case e: IllegalStateException => Map("success" -> false, "errorMessage" -> "Projekti ei ole enää muokattavissa")
      case e: MappingException =>
        logger.warn("Exception treating road links", e)
        BadRequest("Missing mandatory ProjectLink parameter")
      case e: Exception => {
        logger.error(e.toString, e)
        InternalServerError(e.toString)
      }
    }
  }

  post("/roadlinks/roadaddress/project/links") {
    val user = userProvider.getCurrentUser()
    try {
      val links = parsedBody.extract[RoadAddressProjectLinksExtractor]
      logger.debug(s"Creating new links: ${links.linkIds.mkString(",")}")
      val writableProject = projectWritable(links.projectId)
      writableProject.createProjectLinks(links.linkIds, links.projectId, links.roadNumber, links.roadPartNumber,
        links.trackCode, links.discontinuity, links.roadType, links.roadLinkSource, links.roadEly, links.coordinates, user.username)
    } catch {
      case e: IllegalStateException => Map("success" -> false, "errorMessage" -> "Projekti ei ole enää muokattavissa")
      case e: MappingException =>
        logger.warn("Exception treating road links", e)
        BadRequest("Missing mandatory ProjectLink parameter")
      case e: Exception => {
        logger.error(e.toString, e)
        InternalServerError(e.toString)
      }
    }
  }

  put("/roadlinks/roadaddress/project/links") {
    val user = userProvider.getCurrentUser()
    try {
      val links = parsedBody.extract[RoadAddressProjectLinksExtractor]
      val writableProject = projectWritable(links.projectId)
      writableProject.updateProjectLinks(links.projectId, links.linkIds.toSet, LinkStatus.apply(links.linkStatus),
        user.username, links.coordinates, links.roadNumber, links.roadPartNumber, links.trackCode, links.userDefinedEndAddressM,
        links.roadType, links.discontinuity, Some(links.roadEly)) match {
        case Some(errorMessage) => Map("success" -> false, "errormessage" -> errorMessage)
        case None => Map("success" -> true, "id" -> links.projectId, "publishable" -> (projectService.projectLinkPublishable(links.projectId)))
      }
    } catch {
      case e: IllegalStateException => Map("success" -> false, "errorMessage" -> "Projekti ei ole enää muokattavissa")
      case e: MappingException =>
        logger.warn("Exception treating road links", e)
        BadRequest("Missing mandatory ProjectLink parameter")
      case e: Exception => {
        logger.error(e.toString, e)
        InternalServerError(e.toString)
      }
    }
  }

  get("/project/roadlinks") {
    response.setHeader("Access-Control-Allow-Headers", "*")

    val user = userProvider.getCurrentUser()

    val zoomLevel = chooseDrawType(params.getOrElse("zoom", "5"))
    val projectId: Long = params.get("id") match {
      case Some(s) if s != "" && s.toLong != 0 => s.toLong
      case _ => 0L
    }
    if (projectId == 0)
      BadRequest("Missing mandatory 'id' parameter")
    else
      params.get("bbox")
        .map(getProjectLinks(projectId, zoomLevel))
        .getOrElse(BadRequest("Missing mandatory 'bbox' parameter"))
  }

  delete("/project/trid/:projectId") {
    val user = userProvider.getCurrentUser()
    val projectId = params("projectId").toLong
    val oError = projectService.removeRotatingTRId(projectId)
    oError match {
      case Some(error) =>
        Map("success" -> "false", "message" -> error)
      case None =>
        Map("success" -> "true", "message" -> "")
    }
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
        "changeInfoSeq" -> project.changeInfoSeq.map(changeInfo =>
          Map("changetype" -> changeInfo.changeType.value, "roadType" -> changeInfo.roadType.value,
            "discontinuity" -> changeInfo.discontinuity.value, "source" -> changeInfo.source,
            "target" -> changeInfo.target, "reversed"-> changeInfo.reversed)))
    ).getOrElse(None)
  }


  post("/project/publish") {
    val user = userProvider.getCurrentUser()
    try {
      val projectId = params("projectId").toLong

      val writableProject = projectWritable(projectId)
      val publishResult = writableProject.publishProject(projectId)
      if (publishResult.sendSuccess && publishResult.validationSuccess)
        Map("status" -> "ok")
      PreconditionFailed(publishResult.errorMessage.getOrElse("Unknown error"))
    }
    catch {
      case e: IllegalStateException => Map("success" -> false, "errorMessage" -> "Projekti ei ole enää muokattavissa")
      case e: MappingException =>
        logger.warn("Exception treating road links", e)
        BadRequest("Missing mandatory ProjectLink parameter")
    }
  }

  put("/project/split/:linkID") {
    val user = userProvider.getCurrentUser()
    params.get("linkID").map(_.toLong) match {
      case Some(link) =>
        try {
          val options = parsedBody.extract[SplitOptions]
          val writableProject = projectWritable(options.projectId)
          val splitError = writableProject.splitSuravageLink(link, user.username, options)
          Map("success" -> splitError.isEmpty, "reason" -> splitError.orNull)
        } catch {
          case e: IllegalStateException => Map("success" -> false, "errorMessage" -> e.getMessage)
          case _: NumberFormatException => BadRequest("Missing mandatory data")
        }
      case _ => BadRequest("Missing Linkid from url")
    }
  }

  delete("/project/split") {
    val user = userProvider.getCurrentUser()
    try{
      val data = parsedBody.extract[RevertSplitExtractor]
      val projectId = data.projectId
      val linkId = data.linkId
      val coordinates = data.coordinates
      (projectId, linkId) match {
        case (Some(project), Some(link)) =>
          val error = projectService.revertSplit(project, link, coordinates, user.username)
          Map("success" -> error.isEmpty, "message" -> error)
        case _ => BadRequest("Missing mandatory 'projectId' or 'linkId' parameter from URI: /project/split/:projectId/:linkId")
      }
    } catch {
      case _: NumberFormatException => BadRequest("'projectId' or 'linkId' parameter given could not be parsed as an integer number")
    }
  }

  private def roadlinksData(): (Seq[String], Seq[String]) = {
    val data = JSON.parseFull(params.get("data").get).get.asInstanceOf[Map[String, Any]]
    val sources = data("sourceLinkIds").asInstanceOf[Seq[String]]
    val targets = data("targetLinkIds").asInstanceOf[Seq[String]]
    (sources, targets)
  }

  private def getRoadAddressLinks(municipalities: Set[Int], zoomLevel: Int)(bbox: String): Seq[Seq[Map[String, Any]]] = {
    val boundingRectangle = constructBoundingRectangle(bbox)
    val startTime = System.currentTimeMillis()
    val viiteRoadLinks = zoomLevel match {
      //TODO: When well-performing solution for main parts and road parts is ready
      case DrawMainRoadPartsOnly =>
        //        roadAddressService.getCoarseRoadParts(boundingRectangle, Seq((1, 99)), municipalities)
        Seq()
      case DrawRoadPartsOnly =>
        //        roadAddressService.getRoadParts(boundingRectangle, Seq((1, 19999)), municipalities)
        Seq()
      case DrawPublicRoads => roadAddressService.getRoadAddressLinksByLinkId(boundingRectangle, Seq((1, 19999), (40000, 49999)), municipalities)
      case DrawAllRoads => roadAddressService.getRoadAddressLinksWithSuravage(boundingRectangle, roadNumberLimits = Seq(), municipalities, everything = true)
      case _ => roadAddressService.getRoadAddressLinksWithSuravage(boundingRectangle, roadNumberLimits = Seq((1, 19999)), municipalities)
    }
    logger.info(s"End fetching data from service (zoom level $zoomLevel) in ${(System.currentTimeMillis() - startTime) * 0.001}s")
    val partitionedRoadLinks = RoadAddressLinkPartitioner.partition(viiteRoadLinks)
    partitionedRoadLinks.map {
      _.map(roadAddressLinkToApi)
    }
  }

  private def getProjectLinks(projectId: Long, zoomLevel: Int)(bbox: String): Seq[Seq[Map[String, Any]]] = {
    val boundingRectangle = constructBoundingRectangle(bbox)
    val startTime = System.currentTimeMillis()
    val viiteRoadLinks = zoomLevel match {
      case DrawMainRoadPartsOnly =>
        Seq()
      case DrawRoadPartsOnly =>
        Seq()
      case DrawPublicRoads => projectService.getProjectLinksWithSuravage(roadAddressService, projectId, boundingRectangle, Seq((1, 19999), (40000, 49999)), Set())
      case DrawAllRoads => projectService.getProjectLinksWithSuravage(roadAddressService, projectId, boundingRectangle, Seq(), Set(), everything = true)
      case _ => projectService.getProjectLinksWithSuravage(roadAddressService, projectId, boundingRectangle, Seq((1, 19999)), Set())
    }
    logger.info(s"End fetching data for id=$projectId project service (zoom level $zoomLevel) in ${(System.currentTimeMillis() - startTime) * 0.001}s")

    val partitionedRoadLinks = ProjectLinkPartitioner.partition(viiteRoadLinks.filter(_.length >= MinAllowedRoadAddressLength))
    partitionedRoadLinks.map {
      _.map(projectAddressLinkToApi)
    }
  }

  private def chooseDrawType(zoomLevel: String) = {
    val C1 = new Contains(-10 to 3)
    val C2 = new Contains(4 to 5)
    val C3 = new Contains(6 to 10)
    val C4 = new Contains(11 to 16)
    try {
      val level: Int = Math.round(zoomLevel.toDouble).toInt
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

  private def roadAddressLinkLikeToApi(roadAddressLink: RoadAddressLinkLike): Map[String, Any] = {
    Map(
      "success" -> true,
      "segmentId" -> roadAddressLink.id,
      "id" -> roadAddressLink.id,
      "linkId" -> roadAddressLink.linkId,
      "mmlId" -> roadAddressLink.attributes.get("MTKID"),
      "points" -> roadAddressLink.geometry,
      "calibrationCode" -> CalibrationCode.getFromAddressLinkLike(roadAddressLink).value,
      "calibrationPoints" -> Seq(calibrationPoint(roadAddressLink.geometry, roadAddressLink.startCalibrationPoint),
        calibrationPoint(roadAddressLink.geometry, roadAddressLink.endCalibrationPoint)),
      "administrativeClass" -> roadAddressLink.administrativeClass.toString,
      "roadClass" -> roadAddressService.roadClass(roadAddressLink.roadNumber),
      "roadTypeId" -> roadAddressLink.roadType.value,
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
      "anomaly" -> roadAddressLink.anomaly.value,
      "roadLinkType" -> roadAddressLink.roadLinkType.value,
      "constructionType" -> roadAddressLink.constructionType.value,
      "startMValue" -> roadAddressLink.startMValue,
      "endMValue" -> roadAddressLink.endMValue,
      "sideCode" -> roadAddressLink.sideCode.value,
      "linkType" -> roadAddressLink.linkType.value,
      "roadLinkSource" -> roadAddressLink.roadLinkSource.value
    )
  }

  def roadAddressLinkToApi(roadAddressLink: RoadAddressLink): Map[String, Any] = {
    roadAddressLinkLikeToApi(roadAddressLink) ++
      Map(
        "startDate" -> roadAddressLink.startDate,
        "endDate" -> roadAddressLink.endDate,
        "newGeometry" -> roadAddressLink.newGeometry
      )
  }

  def projectAddressLinkToApi(projectAddressLink: ProjectAddressLink): Map[String, Any] = {
    roadAddressLinkLikeToApi(projectAddressLink) ++
      (if (projectAddressLink.isSplit)
        Map(
          "status" -> projectAddressLink.status.value,
          "connectedLinkId" -> projectAddressLink.connectedLinkId,
          "originalGeometry" -> projectAddressLink.originalGeometry,
          "reversed" -> projectAddressLink.reversed
        )
      else
        Map(
          "status" -> projectAddressLink.status.value,
          "reversed" -> projectAddressLink.reversed
        ))
  }

  def roadAddressProjectToApi(roadAddressProject: RoadAddressProject): Map[String, Any] = {
    Map(
      "id" -> roadAddressProject.id,
      "name" -> roadAddressProject.name,
      "createdBy" -> roadAddressProject.createdBy,
      "createdDate" -> formatToString(roadAddressProject.createdDate.toString),
      "dateModified" -> formatToString(roadAddressProject.dateModified.toString),
      "startDate" -> formatToString(roadAddressProject.startDate.toString),
      "modifiedBy" -> roadAddressProject.modifiedBy,
      "additionalInfo" -> roadAddressProject.additionalInfo,
      "status" -> roadAddressProject.status,
      "statusCode" -> roadAddressProject.status.value,
      "statusDescription" -> roadAddressProject.status.description,
      "statusInfo" -> roadAddressProject.statusInfo,
      "ely" -> roadAddressProject.ely.getOrElse(-1),
      "coordX" -> roadAddressProject.coordinates.get.x,
      "coordY" -> roadAddressProject.coordinates.get.y,
      "zoomLevel" -> roadAddressProject.coordinates.get.zoom
    )
  }

  def reservedRoadPartToApi(reservedRoadPart: ReservedRoadPart): Map[String, Any] = {
    Map("roadNumber" -> reservedRoadPart.roadNumber,
      "roadPartNumber" -> reservedRoadPart.roadPartNumber,
      "roadPartId" -> reservedRoadPart.id,
      "ely" -> reservedRoadPart.ely,
      "roadLength" -> reservedRoadPart.roadLength,
      "addressLength" -> reservedRoadPart.addressLength,
      "discontinuity" -> reservedRoadPart.discontinuity.description,
      "linkId" -> reservedRoadPart.startingLinkId,
      "isDirty" -> reservedRoadPart.isDirty
    )
  }

  // Fold segments on same link together
  // TODO: add here start / end dates unique values?
  private def foldSegments[T <: RoadAddressLinkLike](links: Seq[T]): Option[T] = {
    if (links.nonEmpty)
      Some(links.tail.foldLeft(links.head) {
        case (a: RoadAddressLink, b) =>
          a.copy(startAddressM = Math.min(a.startAddressM, b.startAddressM), endAddressM = Math.max(a.endAddressM, b.endAddressM),
            startMValue = Math.min(a.startMValue, b.endMValue)).asInstanceOf[T]
        case (a: ProjectAddressLink, b) =>
          a.copy(startAddressM = Math.min(a.startAddressM, b.startAddressM), endAddressM = Math.max(a.endAddressM, b.endAddressM),
            startMValue = Math.min(a.startMValue, b.endMValue)).asInstanceOf[T]
      })
    else
      None
  }

  private def midPoint(link: RoadAddressLinkLike) = {
    Map("middlePoint" -> GeometryUtils.calculatePointFromLinearReference(link.geometry,
      link.length / 2.0)) ++ (link match {
      case l: RoadAddressLink => roadAddressLinkToApi(l)
      case l: ProjectAddressLink => projectAddressLinkToApi(l)
    })
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

  @throws(classOf[Exception])
  private def projectWritable(projectId: Long): ProjectService = {
    val writable = projectService.isWritableState(projectId)
    if (!writable)
      throw new IllegalStateException("Projekti ei ole enään muokattavissa") //project is not in modifiable state
    projectService
  }

  case class StartupParameters(lon: Double, lat: Double, zoom: Int, deploy_date: String)

  get("/user/roles") {
    userProvider.getCurrentUser().configuration.roles
  }

}

case class ProjectFormLine(startingLinkId: Long, projectId: Long, roadNumber: Long, roadPartNumber: Long, roadLength: Long, ely: Long, discontinuity: String, isDirty: Boolean = false)

object ProjectConverter {
  def toRoadAddressProject(project: RoadAddressProjectExtractor, user: User): RoadAddressProject = {
    val formatter = DateTimeFormat.forPattern("dd.MM.yyyy")
    RoadAddressProject(project.id, ProjectState.apply(project.status),
      if (project.name.length > 32) project.name.substring(0, 32).trim else project.name.trim,
      user.username, DateTime.now(), "-", formatter.parseDateTime(project.startDate), DateTime.now(), project.additionalInfo, project.roadPartList.map(toReservedRoadPart), Option(project.additionalInfo), project.projectEly, Some(ProjectCoordinates(0,0,0)))
  }

  def toReservedRoadPart(rp: RoadPartExtractor): ReservedRoadPart = {
    ReservedRoadPart(0L, rp.roadNumber, rp.roadPartNumber,
      0.0, 0L, Discontinuity.Continuous, rp.ely, None, None, None, false)
  }
}

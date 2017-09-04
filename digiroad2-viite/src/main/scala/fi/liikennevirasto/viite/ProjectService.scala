package fi.liikennevirasto.viite

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.SuravageLinkInterface
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, BothDirections, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, LinkGeomSource, SideCode}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Sequences
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.{RoadAddressException, Track}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils, RoadLinkService, VVHRoadlink}
import fi.liikennevirasto.viite.dao.ProjectState._
import fi.liikennevirasto.viite.dao.{ProjectDAO, RoadAddressDAO, _}
import fi.liikennevirasto.viite.model.{ProjectAddressLink, RoadAddressLink, RoadAddressLinkLike}
import fi.liikennevirasto.viite.process._
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal
case class PreFillInfo(RoadNumber:BigInt, RoadPart:BigInt)

case class LinkToRevert(linkId: Long, status: Long)

class ProjectService(roadAddressService: RoadAddressService, roadLinkService: RoadLinkService, eventbus: DigiroadEventBus, frozenTimeVVHAPIServiceEnabled: Boolean = false) {
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

  private val logger = LoggerFactory.getLogger(getClass)
  val allowedSideCodes = List(SideCode.TowardsDigitizing, SideCode.AgainstDigitizing)

  /**
    *
    * @param roadNumber    Road's number (long)
    * @param roadStartPart Starting part (long)
    * @param roadEndPart   Ending part (long)
    * @return Optional error message, None if no error
    */
  def checkRoadPartsExist(roadNumber: Long, roadStartPart: Long, roadEndPart: Long): Option[String] = {
    withDynTransaction {
      if (!RoadAddressDAO.roadPartExists(roadNumber, roadStartPart)) {
        if (!RoadAddressDAO.roadNumberExists(roadNumber)) {
          Some("Tienumeroa ei ole olemassa, tarkista tiedot")
        }
        else //roadnumber exists, but starting roadpart not
          Some("Tiellä ei ole olemassa valittua alkuosaa, tarkista tiedot")
      } else if (!RoadAddressDAO.roadPartExists(roadNumber, roadEndPart)) { // ending part check
        Some("Tiellä ei ole olemassa valittua loppuosaa, tarkista tiedot")
      } else
        None
    }
  }

  /**
    * Checks that new road address is not already reserved (currently only checks road address table)
    *
    * @param roadNumber road number
    * @param roadPart road part number
    * @param project  road address project needed for id and error message
    * @return
    */
  def checkNewRoadPartAvailableForProject(roadNumber: Long, roadPart: Long, project: RoadAddressProject): Option[String] = {
    val projectLinks = RoadAddressDAO.isNewRoadPartUsed(roadNumber, roadPart, project.id)
    if (projectLinks.isEmpty) {
      None
    } else {
      val fmt = DateTimeFormat.forPattern("dd.MM.yyyy")
      Some(s"TIE $roadNumber OSA $roadPart on jo olemassa projektin alkupäivänä ${project.startDate.toString(fmt)}, tarkista tiedot") //message to user if address is already in use
    }
  }

  private def createProject(roadAddressProject: RoadAddressProject): RoadAddressProject = {
    val id = Sequences.nextViitePrimaryKeySeqValue
    val project = roadAddressProject.copy(id = id)
    ProjectDAO.createRoadAddressProject(project)
    project
  }

  private def projectFound(roadAddressProject: RoadAddressProject): Option[RoadAddressProject] = {
    val newRoadAddressProject=0
    if (roadAddressProject.id==newRoadAddressProject) return None
    withDynTransaction {
      return ProjectDAO.getRoadAddressProjectById(roadAddressProject.id)
    }
  }

  def fetchPreFillFromVVH(linkId: Long): Either[String,PreFillInfo] = {
    parsePreFillData(roadLinkService.fetchVVHRoadlinks(Set(linkId),frozenTimeVVHAPIServiceEnabled))
  }

  def parsePreFillData(vvhRoadLinks: Seq[VVHRoadlink]): Either[String, PreFillInfo] = {
    if (vvhRoadLinks.isEmpty) {
      Left("Link could not be found in VVH")    }
    else {
      val vvhLink = vvhRoadLinks.head
      (vvhLink.attributes.get("ROADNUMBER"), vvhLink.attributes.get("ROADPARTNUMBER")) match {
        case (Some(roadNumber:BigInt), Some(roadPartNumber:BigInt)) => {
          Right(PreFillInfo(roadNumber,roadPartNumber))
        }
        case _ => Left("Link does not contain valid prefill info")
      }
    }
  }

  def checkRoadPartsReservable(roadNumber: Long, startPart: Long, endPart: Long): Either[String, Seq[ReservedRoadPart]] = {
    withDynTransaction {
      (startPart to endPart).foreach(part =>
        ProjectDAO.roadPartReservedByProject(roadNumber, part) match {
          case Some(name) => return Left(s"TIE $roadNumber OSA $part on jo varattuna projektissa $name, tarkista tiedot")
          case _ =>
        })
      Right((startPart to endPart).flatMap( part => getAddressPartInfo(roadNumber, part))
      )
    }
  }

  def validateProjectDate(reservedParts: Seq[ReservedRoadPart], date: DateTime): Option[String] = {
    reservedParts.foreach( part => {
      if(part.startDate.nonEmpty && part.startDate.get.isAfter(date))
        return Option(s"Tieosalla TIE ${part.roadNumber} OSA ${part.roadPartNumber} alkupäivämäärä " +
          s"${part.startDate.get.toString("dd.MM.yyyy")} on myöhempi kuin tieosoiteprojektin alkupäivämäärä " +
          s"${date.toString("dd.MM.yyyy")}, tarkista tiedot.")
      if(part.endDate.nonEmpty && part.endDate.get.isAfter(date))
        return Option(s"Tieosalla TIE ${part.roadNumber} OSA ${part.roadPartNumber} loppupäivämäärä " +
          s"${part.endDate.get.toString("dd.MM.yyyy")} on myöhempi kuin tieosoiteprojektin alkupäivämäärä " +
          s"${date.toString("dd.MM.yyyy")}, tarkista tiedot.")
    })
    None
  }

  private def getAddressPartInfo(roadnumber: Long, roadpart: Long): Option[ReservedRoadPart] = {
    RoadAddressDAO.getRoadPartInfo(roadnumber, roadpart) match {
      case Some((roadpartid, linkid, lenght, discontinuity, startDate, endDate)) => {
        val enrichment = false
        val roadLink = roadLinkService.getViiteRoadLinksByLinkIdsFromVVH(Set(linkid), enrichment,frozenTimeVVHAPIServiceEnabled)
        val ely: Option[Long] = roadLink.headOption.map(rl => MunicipalityDAO.getMunicipalityRoadMaintainers.getOrElse(rl.municipalityCode, 0))
        ely match {
          case Some(value) if value != 0 =>
            Some(ReservedRoadPart(roadpartid, roadnumber, roadpart, lenght, Discontinuity.apply(discontinuity.toInt), value, startDate, endDate))
          case _ => None
        }
      }
      case None =>
        None
    }
  }

  /**
    * Used when adding road address that does not have previous address
    */
  def addNewLinksToProject(projectAddressLinks: Seq[ProjectAddressLink], roadAddressProjectID: Long, newRoadNumber: Long,
                           newRoadPartNumber: Long, newTrackCode: Long, newDiscontinuity: Long, newRoadType: Long = RoadType.Unknown.value): Option[String] = {
    def newProjectLink(projectAddressLink: ProjectAddressLink, project: RoadAddressProject, sideCode: SideCode): ProjectLink = {
      toProjectLink(projectAddressLink, NewRoadAddress, Track.apply(newTrackCode.toInt), project, sideCode)
    }

    def existingProjectLink(projectAddressLink: ProjectAddressLink, project: RoadAddressProject, sideCode: SideCode): ProjectLink = {
      toProjectLink(projectAddressLink, projectAddressLink.id, Track.apply(projectAddressLink.trackCode.toInt), project, sideCode)
    }

    def toProjectLink(projectAddressLink: ProjectAddressLink, id: Long, track: Track, project: RoadAddressProject,
                      sideCode: SideCode): ProjectLink = {
      ProjectLink(id, newRoadNumber, newRoadPartNumber, track,
        Discontinuity.apply(newDiscontinuity.toInt), projectAddressLink.startAddressM,
        projectAddressLink.endAddressM, Some(project.startDate), None, Some(project.createdBy), -1,
        projectAddressLink.linkId, projectAddressLink.startMValue, projectAddressLink.endMValue, sideCode,
        (projectAddressLink.startCalibrationPoint, projectAddressLink.endCalibrationPoint), floating = false,
        projectAddressLink.geometry, roadAddressProjectID, LinkStatus.New, RoadType.apply(newRoadType.toInt),
        projectAddressLink.roadLinkSource, projectAddressLink.length)
    }

    def matchSideCodes(newLink: ProjectAddressLink, existingLink: ProjectAddressLink): SideCode = {
      val (startP, endP) = existingLink.sideCode match {
        case AgainstDigitizing => GeometryUtils.geometryEndpoints(existingLink.geometry).swap
        case _ => GeometryUtils.geometryEndpoints(existingLink.geometry)
      }
      if (GeometryUtils.areAdjacent(newLink.geometry.head, endP) ||
        GeometryUtils.areAdjacent(newLink.geometry.last, startP))
        SideCode.TowardsDigitizing
      else
        SideCode.AgainstDigitizing
    }

    // TODO: Move validations to a validator object class and generalize
    def checkAvailable(number: Long, part: Long, currentProject: RoadAddressProject) = {
      val projectLinks = RoadAddressDAO.isNewRoadPartUsed(number, part, currentProject.id)
      if (projectLinks.isEmpty) {
        None
      } else {
        val fmt = DateTimeFormat.forPattern("dd.MM.yyyy")
        throw new ProjectValidationException(
          s"TIE $number OSA $part on jo olemassa projektin alkupäivänä ${currentProject.startDate.toString(fmt)}, tarkista tiedot") //message to user if address is already in use
      }
    }

    def checkNotReserved(number: Long, part: Long, currentProject: RoadAddressProject) = {
      val project = ProjectDAO.roadPartReservedByProject(number, part, currentProject.id, withProjectId = true)
      if (project.nonEmpty) {
        throw new ProjectValidationException(s"TIE $number OSA $part on jo varattuna projektissa ${project.get}, tarkista tiedot")
      }
    }

    def fetchProject(id: Long): RoadAddressProject = {
      ProjectDAO.getRoadAddressProjectById(roadAddressProjectID).getOrElse(
        throw new ProjectValidationException("Projektikoodilla ei löytynyt projektia"))
    }

    try {
      withDynTransaction {
        val linksInProject = getLinksByProjectLinkId(ProjectDAO.fetchByProjectNewRoadPart(newRoadNumber, newRoadPartNumber,
          roadAddressProjectID, false).map(l => l.linkId).toSet, roadAddressProjectID, false)
        //Deleting all existent roads for same road_number and road_part_number, in order to recalculate the full road if it is already in project
        if (linksInProject.nonEmpty) {
          ProjectDAO.removeProjectLinksByProjectAndRoadNumber(roadAddressProjectID, newRoadNumber, newRoadPartNumber)
        }
        val randomSideCode =
          linksInProject.map(l => l -> projectAddressLinks.find(n => GeometryUtils.areAdjacent(l.geometry, n.geometry))).toMap.find { case (l, n) => n.nonEmpty }.map {
            case (l, Some(n)) =>
              matchSideCodes(n, l)
            case _ => SideCode.TowardsDigitizing
          }.getOrElse(SideCode.TowardsDigitizing)
        val project = fetchProject(roadAddressProjectID)
        checkAvailable(newRoadNumber, newRoadPartNumber, project)
        checkNotReserved(newRoadNumber, newRoadPartNumber, project)
        val newProjectLinks = projectAddressLinks.map(projectLink => {
          projectLink.linkId ->
            newProjectLink(projectLink, project, randomSideCode)
        }).toMap
        if (GeometryUtils.isNonLinear(newProjectLinks.values.toSeq))
          throw new ProjectValidationException("Valittu tiegeometria sisältää haarautumia ja pitää käsitellä osina. Tallennusta ei voi tehdä.")
        val existingLinks = linksInProject.map(projectLink => {
          projectLink.linkId ->
            existingProjectLink(projectLink, project, randomSideCode)
        }).toMap
        val combinedLinks = (newProjectLinks.keySet ++ existingLinks.keySet).toSeq.distinct.map(
          linkId => newProjectLinks.getOrElse(linkId, existingLinks(linkId))
        )
        //Determine geometries for the mValues and addressMValues
        // TODO: check if this should be called with params (newProjectLinks, existingLinks)
        val linksWithMValues = ProjectSectionCalculator.determineMValues(combinedLinks,
          Seq())
        ProjectDAO.removeProjectLinksByLinkId(roadAddressProjectID, combinedLinks.map(c => c.linkId).toSet)
        ProjectDAO.create(linksWithMValues)
        None
      }
    } catch {
      case ex: ProjectValidationException => Some(ex.getMessage)
    }
  }

  def revertLinks(projectId: Long, roadNumber: Long, roadPartNumber: Long, links: List[LinkToRevert]): Option[String] = {

    try {
      withDynTransaction{
        links.foreach(link =>{
          if(link.status == LinkStatus.New.value){
            ProjectDAO.removeProjectLinksByLinkId(projectId, links.map(link=> link.linkId).toSet)
            val remainingLinks = ProjectDAO.projectLinksExist(projectId, roadNumber, roadPartNumber)
            if (remainingLinks.nonEmpty){
              val projectLinks = ProjectDAO.fetchByProjectNewRoadPart(roadNumber, roadPartNumber, projectId)
              val projectAddressLinksGeom = getLinksByProjectLinkId(projectLinks.map(_.linkId).toSet, projectId, false).map(pal =>
                pal.linkId -> pal.geometry).toMap
              val adjLinks = projectLinks.map(pl => pl.copy(geometry = projectAddressLinksGeom(pl.linkId),
                geometryLength = GeometryUtils.geometryLength(projectAddressLinksGeom(pl.linkId)),
                calibrationPoints = (None, None), startAddrMValue = 0L, endAddrMValue = 0L
              ))
              ProjectSectionCalculator.determineMValues(adjLinks, Seq.empty[ProjectLink]).foreach(
                adjLink => ProjectDAO.updateAddrMValues(adjLink))
            }
          }
          else if (link.status == LinkStatus.Terminated.value || link.status == LinkStatus.Transfer.value ){
            val roadLink = RoadAddressDAO.fetchByLinkId(Set(link.linkId),  false, false)
            ProjectDAO.updateProjectLinkValues(projectId, roadLink.head)
          }
        })
        None
      }
    }
    catch{
      case NonFatal(e) =>
        logger.info("Error reverting the changes on roadlink", e)
        Some("Virhe tapahtui muutosten palauttamisen yhteydessä")
    }
  }

  def changeDirection(projectId : Long, roadNumber : Long, roadPartNumber : Long): Option[String] = {
    RoadAddressLinkBuilder.municipalityRoadMaintainerMapping // make sure it is populated outside of this TX
    try {
      withDynTransaction {
        val projectLinkIds = ProjectDAO.projectLinksExist(projectId, roadNumber, roadPartNumber)
        if (!projectLinkIds.contains(projectLinkIds.head)){
          return Some("Linkit kuuluvat useampaan projektiin")
        }
        // TODO: Check that status != UnChanged, throw exception if check fails
        ProjectDAO.flipProjectLinksSideCodes(projectId, roadNumber, roadPartNumber)
        val projectLinks = ProjectDAO.getProjectLinks(projectId)
        val projectAddressLinksGeom = getLinksByProjectLinkId(projectLinks.map(_.linkId).toSet, projectId, false).map(pal =>
          pal.linkId -> pal.geometry).toMap
        val adjLinks = projectLinks.map(pl => pl.copy(geometry = projectAddressLinksGeom(pl.linkId),
          geometryLength = GeometryUtils.geometryLength(projectAddressLinksGeom(pl.linkId)),
          calibrationPoints = (None, None), startAddrMValue = 0L, endAddrMValue = 0L
        ))
        ProjectSectionCalculator.determineMValues(adjLinks, Seq.empty[ProjectLink]).foreach(
          link => ProjectDAO.updateAddrMValues(link))
        None
      }
    } catch{
      case NonFatal(e) =>
        logger.info("Direction change failed", e)
        Some("Päivitys ei onnistunut")
    }
  }

  /**
    * Adds reserved road links (from road parts) to a road address project. Reservability is check before this.
    *
    * @param project
    * @return
    */
  private def addLinksToProject(project: RoadAddressProject): Option[String] = {
    def toProjectLink(roadTypeMap: Map[Long, RoadType])(roadAddress: RoadAddress): ProjectLink = {
      ProjectLink(id=NewRoadAddress, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track,
        roadAddress.discontinuity, roadAddress.startAddrMValue, roadAddress.endAddrMValue, roadAddress.startDate,
        roadAddress.endDate, modifiedBy=Option(project.createdBy), 0L, roadAddress.linkId, roadAddress.startMValue, roadAddress.endMValue,
        roadAddress.sideCode, roadAddress.calibrationPoints, floating=false, roadAddress.geometry, project.id,
        LinkStatus.NotHandled, roadTypeMap.getOrElse(roadAddress.linkId, RoadType.Unknown),roadAddress.linkGeomSource, GeometryUtils.geometryLength(roadAddress.geometry))
    }
    //TODO: Check that there are no floating road addresses present when starting
    val projectLinks = ProjectDAO.getProjectLinks(project.id)
    validateReservations(project.reservedParts, project.ely, project.id, projectLinks).orElse {
      val addresses = project.reservedParts.flatMap { roadaddress =>
        val addressesOnPart = RoadAddressDAO.fetchByRoadPart(roadaddress.roadNumber, roadaddress.roadPartNumber, false)
        val mapping = roadLinkService.getViiteRoadLinksByLinkIdsFromVVH(addressesOnPart.map(_.linkId).toSet, false,frozenTimeVVHAPIServiceEnabled)
          .map(rl => rl.linkId -> RoadAddressLinkBuilder.getRoadType(rl.administrativeClass, rl.linkType)).toMap
        addressesOnPart.map(toProjectLink(mapping))
      }
      val linksOnRemovedParts = projectLinks.filterNot(pl => project.reservedParts.exists(_.holds(pl)))
      val newProjectLinks = addresses.filterNot {
        ad => projectLinks.exists(pl => pl.roadNumber == ad.roadNumber && pl.roadPartNumber == ad.roadPartNumber)
      }
      if (linksOnRemovedParts.nonEmpty) {
        ProjectDAO.removeProjectLinksById(linksOnRemovedParts.map(_.id).toSet)
      }
      ProjectDAO.create(newProjectLinks)
      if (project.ely.isEmpty)
        ProjectDAO.updateProjectEly(project.id, project.reservedParts.head.ely)
      None
    }
  }

  private def validateReservations(reservedRoadParts: Seq[ReservedRoadPart], projectEly: Option[Long], projectId: Long, projectLinks: List[ProjectLink]): Option[String] = {
    val errors = reservedRoadParts.flatMap{ra =>
      val roadPartExistsInAddresses = RoadAddressDAO.roadPartExists(ra.roadNumber, ra.roadPartNumber) ||
        ProjectDAO.projectLinksExist(projectId, ra.roadNumber, ra.roadPartNumber).nonEmpty
      val projectLink = projectLinks.find(p => {
        ra.roadNumber == p.roadNumber && ra.roadPartNumber == p.roadPartNumber &&
          ra.discontinuity == p.discontinuity && ra.startDate == p.startDate &&
          ra.endDate == p.endDate
      })
      if ((!roadPartExistsInAddresses) && !existsInSuravageOrNew(projectLink)) {
        Some(s"TIE ${ra.roadNumber} OSA: ${ra.roadPartNumber}")
      } else
        None
    }
    val elyErrors = reservedRoadParts.flatMap(roadAddress =>
      if (projectEly.filterNot(l => l == -1L).getOrElse(roadAddress.ely) != roadAddress.ely) {
        Some(s"TIE ${roadAddress.roadNumber} OSA: ${roadAddress.roadPartNumber} (ELY != ${projectEly.get})")
      } else None)
    if (errors.nonEmpty)
      Some(s"Seuraavia tieosia ei löytynyt tietokannasta: ${errors.mkString(", ")}")
    else {
      if (elyErrors.nonEmpty)
        Some(s"Seuraavat tieosat ovat eri ELY-numerolla kuin projektin muut osat: ${elyErrors.mkString(", ")}")
      else {
        val ely = reservedRoadParts.map(_.ely)
        if (ely.distinct.size > 1) {
          Some(s"Tieosat ovat eri ELYistä")
        } else {
          None
        }
      }
    }
  }

  private def existsInSuravageOrNew(projectLink: Option[ProjectLink]): Boolean = {
    if (projectLink.isEmpty) {
      false
    } else {
      val link = projectLink.get
      if (link.linkGeomSource != LinkGeomSource.SuravageLinkInterface) {
        link.status == LinkStatus.New
      } else{
        if(roadLinkService.fetchSuravageLinksByLinkIdsFromVVH(Set(link.linkId)).isEmpty) {
          false
        } else true
      }
    }
  }

  private def createFormOfReservedLinksToSavedRoadParts(project: RoadAddressProject): (Seq[ProjectFormLine], Option[ProjectLink]) = {
    val createdAddresses = ProjectDAO.getProjectLinks(project.id)
    val groupedAddresses = createdAddresses.groupBy { address =>
      (address.roadNumber, address.roadPartNumber)
    }.toSeq.sortBy(_._1._2)(Ordering[Long])
    val reservedRoadPartsToUI = groupedAddresses.map(addressGroup => {
      val lastAddress = addressGroup._2.last
      val lastAddressM = lastAddress.endAddrMValue
      val roadLink = if(lastAddress.linkGeomSource == LinkGeomSource.SuravageLinkInterface) {
        roadLinkService.getSuravageRoadLinksByLinkIdsFromVVH(Set(lastAddress.linkId), false)
      } else {
        roadLinkService.getViiteRoadLinksByLinkIdsFromVVH(Set(addressGroup._2.last.linkId), false, frozenTimeVVHAPIServiceEnabled)
      }
      val addressFormLine = ProjectFormLine(lastAddress.linkId, project.id, addressGroup._1._1,
        addressGroup._1._2, lastAddressM, MunicipalityDAO.getMunicipalityRoadMaintainers.getOrElse(roadLink.head.municipalityCode, -1),
        lastAddress.discontinuity.description)
      //TODO:case class RoadAddressProjectFormLine(projectId: Long, roadNumber: Long, roadPartNumber: Long, RoadLength: Long, ely : Long, discontinuity: String)
      addressFormLine
    })
    val addresses = createdAddresses.headOption
    (reservedRoadPartsToUI, addresses)
  }

  private def createNewRoadLinkProject(roadAddressProject: RoadAddressProject) = {
    withDynTransaction {
      val project = createProject(roadAddressProject)
      if (project.reservedParts.isEmpty) //check if new project has links
      {
        val (forminfo, createdlink) = createFormOfReservedLinksToSavedRoadParts(project)
        (project, None, forminfo, "ok")
      } else {
        //project with links success field contains errors if any, else "ok"
        val errorMessage = addLinksToProject(project)

        val (forminfo, createdlink) = createFormOfReservedLinksToSavedRoadParts(project)

        (project, createdlink, forminfo, errorMessage.getOrElse("ok"))
      }
    }
  }

  def saveRoadLinkProject(roadAddressProject: RoadAddressProject): (RoadAddressProject, Option[ProjectLink], Seq[ProjectFormLine], String) = {
    if (projectFound(roadAddressProject).isEmpty)
      throw new IllegalArgumentException("Project not found")
    withDynTransaction {
      if (roadAddressProject.reservedParts.isEmpty) { //roadaddresses to update is empty
        ProjectDAO.updateRoadAddressProject(roadAddressProject)
        ProjectDAO.removeProjectLinksByProject(roadAddressProject.id)
        val (forminfo, createdlink) = createFormOfReservedLinksToSavedRoadParts(roadAddressProject)
        (roadAddressProject, createdlink, forminfo, "ok")
      } else {
        //list contains road addresses that we need to add
        val errorMessage = addLinksToProject(roadAddressProject)
        if (errorMessage.isEmpty) {
          //adding links succeeeded
          ProjectDAO.updateRoadAddressProject(roadAddressProject)
          val (forminfo, createdlink) = createFormOfReservedLinksToSavedRoadParts(roadAddressProject)
          (roadAddressProject, createdlink, forminfo, "ok")
        } else {
          //adding links failed
          val (forminfo, createdlink) = createFormOfReservedLinksToSavedRoadParts(roadAddressProject)
          (roadAddressProject, createdlink, forminfo, errorMessage.get)
        }
      }
    }
  }

  def createRoadLinkProject(roadAddressProject: RoadAddressProject): (RoadAddressProject, Option[ProjectLink], Seq[ProjectFormLine], String) = {
    if (roadAddressProject.id != 0)
      throw new IllegalArgumentException(s"Road address project to create has an id ${roadAddressProject.id}")
    createNewRoadLinkProject(roadAddressProject)
  }

  def getRoadAddressSingleProject(projectId: Long): Seq[RoadAddressProject] = {
    withDynTransaction {
      ProjectDAO.getRoadAddressProjects(projectId)
    }
  }

  def getRoadAddressAllProjects(): Seq[RoadAddressProject] = {
    withDynTransaction {
      ProjectDAO.getRoadAddressProjects()
    }
  }

  def getProjectsWithReservedRoadParts(projectId: Long): (RoadAddressProject, Seq[ProjectFormLine]) = {
    withDynTransaction {
      val project: RoadAddressProject = ProjectDAO.getRoadAddressProjects(projectId).head
      val createdAddresses = ProjectDAO.getProjectLinks(project.id)
      val groupedAddresses = createdAddresses.groupBy { address =>
        (address.roadNumber, address.roadPartNumber)
      }.toSeq.sortBy(_._1._2)(Ordering[Long])
      val formInfo: Seq[ProjectFormLine] = groupedAddresses.map(addressGroup => {
        val endAddressM = addressGroup._2.last.endAddrMValue
        val roadLink = if(addressGroup._2.head.linkGeomSource == LinkGeomSource.SuravageLinkInterface){
          roadLinkService.getSuravageRoadLinksByLinkIdsFromVVH(Set(addressGroup._2.head.linkId), false)
        } else {
          roadLinkService.getViiteRoadLinksByLinkIdsFromVVH(Set(addressGroup._2.head.linkId), false, frozenTimeVVHAPIServiceEnabled)
        }
        val isRoadPartDirty = addressGroup._2.exists(_.status != LinkStatus.NotHandled)
        val addressFormLine = ProjectFormLine(addressGroup._2.head.linkId, project.id,
          addressGroup._2.head.roadNumber, addressGroup._2.head.roadPartNumber, endAddressM,
          MunicipalityDAO.getMunicipalityRoadMaintainers.getOrElse(roadLink.head.municipalityCode, -1),
          addressGroup._2.last.discontinuity.description, isRoadPartDirty)
        addressFormLine
      })
      (project, formInfo)
    }
  }

  def getProjectLinksWithSuravage(roadAddressService: RoadAddressService,projectId:Long, boundingRectangle: BoundingRectangle, roadNumberLimits: Seq[(Int, Int)], municipalities: Set[Int], everything: Boolean = false, publicRoads: Boolean=false): Seq[ProjectAddressLink] ={
    val combinedFuture=  for{
      fProjectLink <-  Future(getProjectRoadLinks(projectId, boundingRectangle, roadNumberLimits, municipalities, everything, frozenTimeVVHAPIServiceEnabled))
      fSuravage <- Future(roadAddressService.getSuravageRoadLinkAddresses(boundingRectangle, Set()))
    } yield (fProjectLink, fSuravage)
    val (projectLinkList,suravageList) =Await.result(combinedFuture, Duration.Inf)
    val projectSuravageLinkIds = projectLinkList.filter(_.roadLinkSource == SuravageLinkInterface).map(_.linkId).toSet
    roadAddressLinkToProjectAddressLink(suravageList.filterNot(s => projectSuravageLinkIds.contains(s.linkId))) ++
      projectLinkList
  }

  def getChangeProject(projectId:Long): Option[ChangeProject] = {
    val changeProjectData = withDynTransaction {
      try {
        val delta = ProjectDeltaCalculator.delta(projectId)
        //TODO would be better to give roadType to ProjectLinks table instead of calling vvh here
        val linkIds = (delta.terminations ++ delta.unChanged).map(_.linkId).toSet
        val vvhRoadLinks = roadLinkService.getViiteRoadLinksByLinkIdsFromVVH(linkIds, false, frozenTimeVVHAPIServiceEnabled)
          .map(rl => rl.linkId -> rl).toMap
        val filledTerminations = withFetchedDataFromVVH(delta.terminations, vvhRoadLinks, RoadType)
        val filledUnChanged = withFetchedDataFromVVH(delta.unChanged, vvhRoadLinks, RoadType)

        if (setProjectDeltaToDB(delta.copy(terminations = filledTerminations, unChanged = filledUnChanged), projectId)) {
          val roadAddressChanges = RoadAddressChangesDAO.fetchRoadAddressChanges(Set(projectId))
          Some(ViiteTierekisteriClient.convertToChangeProject(roadAddressChanges))
        } else {
          None
        }
      } catch {
        case NonFatal(e) =>
          logger.info(s"Change info not available for project $projectId: " + e.getMessage)
          None
      }
    }
    changeProjectData
  }

  def enrichTerminations(terminations: Seq[RoadAddress], roadlinks: Seq[RoadLink]): Seq[RoadAddress] = {
    val withRoadType = terminations.par.map{
      t =>
        val relatedRoadLink = roadlinks.find(rl => rl.linkId == t.linkId)
        relatedRoadLink match {
          case None => t
          case Some(rl) =>
            val roadType = RoadAddressLinkBuilder.getRoadType(rl.administrativeClass, rl.linkType)
            t.copy(roadType = roadType)
        }
    }
    withRoadType.toList
  }

  def getRoadAddressChangesAndSendToTR(projectId: Set[Long]) = {
    val roadAddressChanges = RoadAddressChangesDAO.fetchRoadAddressChanges(projectId)
    ViiteTierekisteriClient.sendChanges(roadAddressChanges)
  }

  def getProjectRoadLinksByLinkIds(linkIdsToGet : Set[Long], newTransaction : Boolean = true): Seq[ProjectAddressLink] = {

    if(linkIdsToGet.isEmpty)
      return Seq()

    val fetchVVHStartTime = System.currentTimeMillis()
    val complementedRoadLinks = roadLinkService.getViiteRoadLinksByLinkIdsFromVVH(linkIdsToGet, newTransaction,frozenTimeVVHAPIServiceEnabled)
    val fetchVVHEndTime = System.currentTimeMillis()
    logger.info("End fetch vvh road links in %.3f sec".format((fetchVVHEndTime - fetchVVHStartTime) * 0.001))

    val projectRoadLinks = complementedRoadLinks
      .map { rl =>
        val ra = Seq()
        val missed =  Seq()
        rl.linkId -> roadAddressService.buildRoadAddressLink(rl, ra, missed)
      }.toMap

    val filledProjectLinks = RoadAddressFiller.fillTopology(complementedRoadLinks, projectRoadLinks)

    filledProjectLinks._1.map(toProjectAddressLink)

  }

  def getProjectSuravageRoadLinksByLinkIds(linkIdsToGet : Set[Long]): Seq[ProjectAddressLink] = {
    if(linkIdsToGet.isEmpty)
      Seq()
    else {
      val fetchVVHStartTime = System.currentTimeMillis()
      val suravageRoadLinks = roadAddressService.getSuravageRoadLinkAddressesByLinkIds(linkIdsToGet)
      val fetchVVHEndTime = System.currentTimeMillis()
      logger.info("End fetch vvh road links in %.3f sec".format((fetchVVHEndTime - fetchVVHStartTime) * 0.001))
      suravageRoadLinks.map(toProjectAddressLink)
    }
  }

  def getLinksByProjectLinkId(linkIdsToGet : Set[Long], projectId: Long, newTransaction : Boolean = true): Seq[ProjectAddressLink] = {

    if(linkIdsToGet.isEmpty)
      return Seq()

    val fetchVVHStartTime = System.currentTimeMillis()
    val complementedRoadLinks = roadLinkService.getViiteRoadLinksByLinkIdsFromVVH(linkIdsToGet, newTransaction, frozenTimeVVHAPIServiceEnabled)
    val fetchVVHEndTime = System.currentTimeMillis()
    logger.info("End fetch vvh road links in %.3f sec".format((fetchVVHEndTime - fetchVVHStartTime) * 0.001))
    val fetchProjectLinks = ProjectDAO.getProjectLinks(projectId).groupBy(_.linkId)

    val projectRoadLinks = complementedRoadLinks.map {
      rl =>
        val pl = fetchProjectLinks.getOrElse(rl.linkId, Seq())
        rl.linkId -> buildProjectRoadLink(rl, pl)
    }.toMap.mapValues(_.get)

    RoadAddressFiller.fillProjectTopology(complementedRoadLinks, projectRoadLinks)
  }

  def getProjectRoadLinks(projectId: Long, boundingRectangle: BoundingRectangle, roadNumberLimits: Seq[(Int, Int)], municipalities: Set[Int],
                          everything: Boolean = false, publicRoads: Boolean = false): Seq[ProjectAddressLink] = {
    def complementaryLinkFilter(roadNumberLimits: Seq[(Int, Int)], municipalities: Set[Int],
                                everything: Boolean = false, publicRoads: Boolean = false)(roadAddressLink: RoadAddressLink) = {
      everything || publicRoads || roadNumberLimits.exists {
        case (start, stop) => roadAddressLink.roadNumber >= start && roadAddressLink.roadNumber <= stop
      }
    }

    val fetchRoadAddressesByBoundingBoxF = Future(withDynTransaction {
      val (floating, addresses) = RoadAddressDAO.fetchRoadAddressesByBoundingBox(boundingRectangle, fetchOnlyFloating = false).partition(_.floating)
      (floating.groupBy(_.linkId), addresses.groupBy(_.linkId))
    })
    val fetchProjectLinksF = Future(withDynTransaction {
      ProjectDAO.getProjectLinks(projectId).groupBy(_.linkId)
    })
    val fetchVVHStartTime = System.currentTimeMillis()
    val (complementedRoadLinks, suravageLinks) = fetchRoadLinksWithComplementaryAndSuravage(boundingRectangle, roadNumberLimits, municipalities, everything, publicRoads)
    val complementaryLinkIds = complementedRoadLinks.filter(_.linkSource == LinkGeomSource.ComplimentaryLinkInterface).map(_.linkId)
    val linkIds = complementedRoadLinks.map(_.linkId).toSet ++ suravageLinks.map(_.linkId).toSet
    val fetchVVHEndTime = System.currentTimeMillis()
    logger.info("End fetch vvh road links in %.3f sec".format((fetchVVHEndTime - fetchVVHStartTime) * 0.001))

    val fetchMissingRoadAddressStartTime = System.currentTimeMillis()
    val ((floating, addresses), projectLinks) = Await.result(fetchRoadAddressesByBoundingBoxF.zip(fetchProjectLinksF), Duration.Inf)
    // TODO: When floating handling is enabled we need this - but ignoring the result otherwise here
    val missingLinkIds = linkIds -- floating.keySet -- addresses.keySet -- projectLinks.keySet

    val missedRL = withDynTransaction {
      RoadAddressDAO.getMissingRoadAddresses(missingLinkIds)
    }.groupBy(_.linkId)
    val fetchMissingRoadAddressEndTime = System.currentTimeMillis()
    logger.info("End fetch missing and floating road address in %.3f sec".format((fetchMissingRoadAddressEndTime - fetchMissingRoadAddressStartTime) * 0.001))

    val buildStartTime = System.currentTimeMillis()

    val projectRoadLinks = (complementedRoadLinks.map {
      rl =>
        val pl = projectLinks.getOrElse(rl.linkId, Seq())
        rl.linkId -> buildProjectRoadLink(rl, pl)
    } ++
      suravageLinks.map {
        sl =>
          val pl = projectLinks.getOrElse(sl.linkId, Seq())
          sl.linkId -> buildProjectRoadLink(sl, pl)
      }).filterNot { case (_, optPAL) => optPAL.isEmpty}.toMap.mapValues(_.get)

    val filledProjectLinks = RoadAddressFiller.fillProjectTopology(complementedRoadLinks ++ suravageLinks, projectRoadLinks)

    val nonProjectRoadLinks = complementedRoadLinks.filterNot(rl => projectRoadLinks.keySet.contains(rl.linkId))

    val viiteRoadLinks = nonProjectRoadLinks
      .map { rl =>
        val ra = addresses.getOrElse(rl.linkId, Seq())
        val missed = missedRL.getOrElse(rl.linkId, Seq())
        rl.linkId -> roadAddressService.buildRoadAddressLink(rl, ra, missed)
      }.toMap

    val buildEndTime = System.currentTimeMillis()
    logger.info("End building road address in %.3f sec".format((buildEndTime - buildStartTime) * 0.001))

    val (filledTopology, _) = RoadAddressFiller.fillTopology(nonProjectRoadLinks, viiteRoadLinks)

    val returningTopology = filledTopology.filter(link => !complementaryLinkIds.contains(link.linkId) ||
      complementaryLinkFilter(roadNumberLimits, municipalities, everything, publicRoads)(link))
    returningTopology.map(toProjectAddressLink) ++ filledProjectLinks

  }

  def roadAddressLinkToProjectAddressLink(roadAddresses: Seq[RoadAddressLink]): Seq[ProjectAddressLink]= {
    roadAddresses.map(toProjectAddressLink)
  }
  def updateProjectLinkStatus(projectId: Long, linkIds: Set[Long], linkStatus: LinkStatus, userName: String): Boolean = {
    withDynTransaction{
      val projectLinks = ProjectDAO.getProjectLinks(projectId)
      val changed = projectLinks.filter(pl => linkIds.contains(pl.linkId)).map(_.id).toSet
      ProjectDAO.updateProjectLinkStatus(changed, linkStatus, userName)
      try {
        val delta = ProjectDeltaCalculator.delta(projectId)
        setProjectDeltaToDB(delta,projectId)
        true
      } catch {
        case ex: RoadAddressException =>
          logger.info("Delta calculation not possible: " + ex.getMessage)
          false
      }
    }
  }

  def projectLinkPublishable(projectId: Long): Boolean = {
    // TODO: add other checks after transfers etc. are enabled
    withDynSession{
      ProjectDAO.getProjectLinks(projectId, Some(LinkStatus.NotHandled)).isEmpty
    }
  }

  /**
    * Publish project with id projectId
    *
    * @param projectId Project to publish
    * @return optional error message, empty if no error
    */
  def publishProject(projectId: Long): PublishResult = {
    // TODO: Check that project actually is finished: projectLinkPublishable(projectId)
    // TODO: Run post-change tests for the roads that have been edited and throw an exception to roll back if not acceptable
    withDynTransaction {
      try {
        val delta=ProjectDeltaCalculator.delta(projectId)
        if(!setProjectDeltaToDB(delta,projectId)) {return PublishResult(false, false, Some("Muutostaulun luonti epäonnistui. Tarkasta ely"))}
        val trProjectStateMessage = getRoadAddressChangesAndSendToTR(Set(projectId))
        trProjectStateMessage.status match {
          case it if 200 until 300 contains it => {
            setProjectStatusToSend2TR(projectId)
            PublishResult(true, true, Some(trProjectStateMessage.reason))
          }
          case _ => {
            //rollback
            PublishResult(true, false, Some(trProjectStateMessage.reason))
          }
        }
      } catch{
        case NonFatal(e) =>  PublishResult(false, false, None)
      }
    }
  }

  private def setProjectDeltaToDB(projectDelta:Delta, projectId:Long):Boolean= {
    RoadAddressChangesDAO.clearRoadChangeTable(projectId)
    val batchInsertionResult = RoadAddressChangesDAO.insertDeltaToRoadChangeTable(projectDelta, projectId)
    batchInsertionResult
  }

  private def toProjectAddressLink(ral: RoadAddressLinkLike): ProjectAddressLink = {
    ProjectAddressLink(ral.id, ral.linkId, ral.geometry, ral.length, ral.administrativeClass, ral.linkType, ral.roadLinkType,
      ral.constructionType, ral.roadLinkSource, ral.roadType, ral.roadName, ral.municipalityCode, ral.modifiedAt, ral.modifiedBy,
      ral.attributes, ral.roadNumber, ral.roadPartNumber, ral.trackCode, ral.elyCode, ral.discontinuity,
      ral.startAddressM, ral.endAddressM, ral.startMValue, ral.endMValue, ral.sideCode, ral.startCalibrationPoint, ral.endCalibrationPoint,
      ral.anomaly, ral.lrmPositionId, LinkStatus.Unknown)
  }

  private def buildProjectRoadLink(rl: RoadLinkLike, projectLinks: Seq[ProjectLink]): Option[ProjectAddressLink] = {
    val pl = projectLinks.size match {
      case 0 => return None
      case 1 => projectLinks.head
      case _ => fuseProjectLinks(projectLinks)
    }
    Some(ProjectAddressLinkBuilder.build(rl, pl))
  }

  private def fuseProjectLinks(links: Seq[ProjectLink]) = {
    val linkIds = links.map(_.linkId).distinct
    if (linkIds.size != 1)
      throw new IllegalArgumentException(s"Multiple road link ids given for building one link: ${linkIds.mkString(", ")}")
    val (startM, endM, startA, endA) = (links.map(_.startMValue).min, links.map(_.endMValue).max,
      links.map(_.startAddrMValue).min, links.map(_.endAddrMValue).max)
    links.head.copy(startMValue = startM, endMValue = endM, startAddrMValue = startA, endAddrMValue = endA)
  }

  private def fetchRoadLinksWithComplementaryAndSuravage(boundingRectangle: BoundingRectangle, roadNumberLimits: Seq[(Int, Int)], municipalities: Set[Int],
                                                         everything: Boolean = false, publicRoads: Boolean = false): (Seq[RoadLink], Seq[VVHRoadlink]) = {
    val combinedFuture=  for{
      fStandard <- Future(roadLinkService.getViiteRoadLinksFromVVH(boundingRectangle, roadNumberLimits, municipalities, everything, publicRoads,frozenTimeVVHAPIServiceEnabled))
      fComplementary <- Future(roadLinkService.getComplementaryRoadLinksFromVVH(boundingRectangle, municipalities))
      fSuravage <- Future(roadLinkService.getSuravageLinksFromVVH(boundingRectangle,municipalities))
    } yield (fStandard, fComplementary, fSuravage)

    val (roadLinks, complementaryLinks, suravageLinks) = Await.result(combinedFuture, Duration.Inf)
    (roadLinks ++ complementaryLinks, suravageLinks)
  }

  private def fetchRoadLinksWithComplementary(boundingRectangle: BoundingRectangle, roadNumberLimits: Seq[(Int, Int)], municipalities: Set[Int],
                                              everything: Boolean = false, publicRoads: Boolean = false): (Seq[RoadLink], Set[Long]) = {
    val roadLinksF = Future(roadLinkService.getViiteRoadLinksFromVVH(boundingRectangle, roadNumberLimits, municipalities, everything, publicRoads,frozenTimeVVHAPIServiceEnabled))
    val complementaryLinksF = Future(roadLinkService.getComplementaryRoadLinksFromVVH(boundingRectangle, municipalities))
    val (roadLinks, complementaryLinks) = Await.result(roadLinksF.zip(complementaryLinksF), Duration.Inf)
    (roadLinks ++ complementaryLinks, complementaryLinks.map(_.linkId).toSet)
  }

  def getProjectStatusFromTR(projectId: Long) = {
    ViiteTierekisteriClient.getProjectStatus(projectId)
  }

  private def getStatusFromTRObject(trProject:Option[TRProjectStatus]):Option[ProjectState] = {
    trProject match {
      case Some(trPojectobject) => mapTRstateToViiteState(trPojectobject.status.getOrElse(""))
      case None => None
      case _ => None
    }
  }

  private def getTRErrorMessage(trProject:Option[TRProjectStatus]):String = {
    trProject match {
      case Some(trProjectobject) => trProjectobject.errorMessage.getOrElse("")
      case None => ""
      case _ => ""
    }
  }

  def setProjectStatusToSend2TR(projectId:Long) =
  {
    ProjectDAO.updateProjectStatus(projectId, ProjectState.Sent2TR,"")
  }

  def updateProjectStatusIfNeeded(currentStatus:ProjectState, newStatus:ProjectState, errorMessage:String,projectId:Long) :(ProjectState)= {
    if (currentStatus.value!=newStatus.value && newStatus != ProjectState.Unknown)
    {
      ProjectDAO.updateProjectStatus(projectId,newStatus,errorMessage)
    }
    if (newStatus != ProjectState.Unknown){
      newStatus
    } else
    {
      currentStatus
    }
  }

  private def getProjectsPendingInTR() :Seq[Long]= {
    withDynSession {
      ProjectDAO.getProjectsWithWaitingTRStatus()
    }
  }
  def updateProjectsWaitingResponseFromTR(): Unit =
  {
    val listOfPendingProjects=getProjectsPendingInTR()
    for(project<-listOfPendingProjects)
    {
      try {
        withDynSession {
          logger.info(s"Checking status for $project")
          val newStatus = checkAndUpdateProjectStatus(project)
          logger.info(s"new status is $newStatus")
        }
      } catch {
        case t: Throwable => logger.warn(s"Couldn't update project $project", t)
      }
    }

  }

  private def checkAndUpdateProjectStatus(projectID: Long): ProjectState =
  {
    ProjectDAO.getProjectStatus(projectID).map { currentState =>
      logger.info(s"Current status is $currentState")
      val trProjectState = ViiteTierekisteriClient.getProjectStatusObject(projectID)
      val newState = getStatusFromTRObject(trProjectState).getOrElse(ProjectState.Unknown)
      val errorMessage = getTRErrorMessage(trProjectState)
      logger.info(s"TR returned project status for $projectID: $currentState -> $newState, errMsg: $errorMessage")
      val updatedStatus = updateProjectStatusIfNeeded(currentState, newState, errorMessage, projectID)
      updateRoadAddressWithProject(newState, projectID)
      updatedStatus
    }.getOrElse(ProjectState.Unknown)
  }

  private def mapTRstateToViiteState(trState:String): Option[ProjectState] ={

    trState match {
      case "S" => Some(ProjectState.apply(ProjectState.TRProcessing.value))
      case "K" => Some(ProjectState.apply(ProjectState.TRProcessing.value))
      case "T" => Some(ProjectState.apply(ProjectState.Saved2TR.value))
      case "V" => Some(ProjectState.apply(ProjectState.ErroredInTR.value))
      case "null" => Some(ProjectState.apply(ProjectState.ErroredInTR.value))
      case _=> None
    }
  }

  def updateRoadAddressWithProject(newState: ProjectState, projectID: Long): Seq[Long] = {
    if(newState == Saved2TR){
      val delta = ProjectDeltaCalculator.delta(projectID)
      val changes = RoadAddressChangesDAO.fetchRoadAddressChanges(Set(projectID))
      val newLinks = delta.terminations.map(terminated => terminated.copy(id = NewRoadAddress,
        endDate = Some(changes.head.projectStartDate)))
      //Expiring old addresses
      roadAddressService.expireRoadAddresses(delta.terminations.map(_.id).toSet)
      //Creating new addresses with the applicable changes
      val newRoads = RoadAddressDAO.create(newLinks, None)
      //Remove the ProjectLinks from PROJECT_LINK table?
      //      val projectLinks = ProjectDAO.getProjectLinks(projectID, Some(LinkStatus.Terminated))
      //      ProjectDAO.removeProjectLinksById(projectLinks.map(_.projectId).toSet)
      newRoads
    } else {
      throw new RuntimeException(s"Project state not at Saved2TR: $newState")
    }
  }

  // TODO: remove when saving road type to project link table
  def withFetchedDataFromVVH(roadAdddresses: Seq[RoadAddress], roadLinks: Map[Long, RoadLink], Type: Object): Seq[RoadAddress] = {
    val fetchedAddresses = Type match {
      case RoadType =>
        val withRoadType: Seq[RoadAddress] = roadAdddresses.par.map {
          ra =>
            roadLinks.get(ra.linkId) match {
              case None => ra
              case Some(rl) =>
                val roadType = RoadAddressLinkBuilder.getRoadType(rl.administrativeClass, rl.linkType)
                ra.copy(roadType = roadType)
            }
        }.toList
        withRoadType
      case _ => roadAdddresses
    }
    fetchedAddresses
  }

  def setProjectEly(currentProjectId:Long, newEly: Long): Option[String] = {
    withDynTransaction {
      val currentProjectEly = getProjectEly(currentProjectId)
      if (currentProjectEly == -1) {
        ProjectDAO.updateProjectEly(currentProjectId, newEly)
        None
      } else if (currentProjectEly == newEly) {
        logger.debug("ProjectId: " + currentProjectId + " Ely is \"" + currentProjectEly + "\" no need to update")
        None
      } else {
        logger.info(s"The project can not handle multiple ELY areas (the project ELY range is ${currentProjectEly}). Recording was discarded.")
        Some(s"Projektissa ei voi käsitellä useita ELY-alueita (projektin ELY-alue on ${currentProjectEly}). Tallennus hylättiin.")
      }
    }
  }

  def getProjectEly(projectId: Long): Long = {
    ProjectDAO.getProjectEly(projectId).get
  }

  case class PublishResult(validationSuccess: Boolean, sendSuccess: Boolean, errorMessage: Option[String])
}

class ProjectValidationException(s: String) extends RuntimeException {
  override def getMessage: String = s
}
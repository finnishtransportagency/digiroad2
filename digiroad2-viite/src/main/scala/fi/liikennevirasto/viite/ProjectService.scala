package fi.liikennevirasto.viite

import java.util.Date

import fi.liikennevirasto.digiroad2
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.SuravageLinkInterface
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, BothDirections, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Sequences
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.{RoadAddressException, Track}
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.viite.dao.ProjectState._
import fi.liikennevirasto.viite.dao.{ProjectDAO, RoadAddressDAO, _}
import fi.liikennevirasto.viite.model.{ProjectAddressLink, RoadAddressLink, RoadAddressLinkLike}
import fi.liikennevirasto.viite.process._
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.slf4j.LoggerFactory

import scala.collection.mutable.ListBuffer
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
    val isReserved = RoadAddressDAO.isNotAvailableForProject(roadNumber, roadPart, project.id)
    if (!isReserved) {
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
      toProjectLink(projectAddressLink, NewRoadAddress, Track.apply(newTrackCode.toInt), project, sideCode, true)
    }

    def existingProjectLink(projectAddressLink: ProjectAddressLink, project: RoadAddressProject, sideCode: SideCode): ProjectLink = {
      toProjectLink(projectAddressLink, projectAddressLink.id, Track.apply(projectAddressLink.trackCode.toInt), project, sideCode, false)
    }

    def toProjectLink(projectAddressLink: ProjectAddressLink, id: Long, track: Track, project: RoadAddressProject,
                      sideCode: SideCode, isNewProjectLink:Boolean = false): ProjectLink = {
      ProjectLink(id, newRoadNumber, newRoadPartNumber, track,
        Discontinuity.apply(newDiscontinuity.toInt), projectAddressLink.startAddressM,
        projectAddressLink.endAddressM, Some(project.startDate), None, Some(project.createdBy), -1,
        projectAddressLink.linkId, projectAddressLink.startMValue, projectAddressLink.endMValue, sideCode,
        (projectAddressLink.startCalibrationPoint, projectAddressLink.endCalibrationPoint), floating = false,
        projectAddressLink.geometry, roadAddressProjectID, if (isNewProjectLink) LinkStatus.New else projectAddressLink.status, RoadType.apply(newRoadType.toInt),
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
      val isReserved = RoadAddressDAO.isNotAvailableForProject(number, part, currentProject.id)
      if (!isReserved) {
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
            existingProjectLink(projectLink, project, if (projectLink.status == LinkStatus.NotHandled && projectLink.sideCode.value < 5 ) projectLink.sideCode else randomSideCode)
        }).toMap
        val combinedLinks = (newProjectLinks.keySet ++ existingLinks.keySet).toSeq.map(
          linkId => newProjectLinks.getOrElse(linkId, existingLinks(linkId))
        )
        //Determine geometries for the mValues and addressMValues
        val linksWithMValues = ProjectSectionCalculator.assignMValues(combinedLinks)
        ProjectDAO.removeProjectLinksByLinkId(roadAddressProjectID, combinedLinks.map(c => c.linkId).toSet)
        ProjectDAO.create(linksWithMValues)
        None
      }
    } catch {
      case ex: ProjectValidationException => Some(ex.getMessage)
    }
  }

  private def withGeometry(projectLinks: Seq[ProjectLink], resetAddress: Boolean = false): Seq[ProjectLink] = {
    val linkGeometries = roadLinkService.getViiteRoadLinksByLinkIdsFromVVH(projectLinks.map(_.linkId).toSet,
      false, frozenTimeVVHAPIServiceEnabled).map(pal => pal.linkId -> pal.geometry).toMap
    projectLinks.map{pl =>
      val geom = GeometryUtils.truncateGeometry2D(linkGeometries(pl.linkId), pl.startMValue, pl.endMValue)
      pl.copy(geometry = geom,
        geometryLength = GeometryUtils.geometryLength(geom),
        startAddrMValue = if (resetAddress) 0L else pl.startAddrMValue,
        endAddrMValue = if (resetAddress) 0L else pl.endAddrMValue,
        calibrationPoints = if (resetAddress) (None, None) else pl.calibrationPoints)
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
              val adjLinks = withGeometry(projectLinks, resetAddress = true)
              ProjectSectionCalculator.assignMValues(adjLinks).foreach(
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
        if(ProjectDAO.projectLinksCountUnchanged(projectId, roadNumber, roadPartNumber) > 0)
          return Some("Tieosalle ei voi tehdä kasvusuunnan kääntöä, koska tieosalla on linkkejä, jotka on tässä projektissa määritelty säilymään ennallaan.")
        ProjectDAO.flipProjectLinksSideCodes(projectId, roadNumber, roadPartNumber)
        val projectLinks = ProjectDAO.getProjectLinks(projectId)
        val adjLinks = withGeometry(projectLinks, resetAddress = false)
        ProjectSectionCalculator.assignMValues(adjLinks).foreach(
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
        if (setProjectDeltaToDB(delta, projectId)) {
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

  /**
    * Update project links to given status and recalculate delta and change table
    * @param projectId Project's id
    * @param linkIds Set of link ids that are set to this status
    * @param linkStatus New status for given link ids
    * @param userName Username of the user that does this change
    * @return true, if the delta calculation is successful and change table has been updated.
    */
  def updateProjectLinkStatus(projectId: Long, linkIds: Set[Long], linkStatus: LinkStatus, userName: String): Boolean = {
    withDynTransaction{
      val projectLinks = withGeometry(ProjectDAO.getProjectLinks(projectId))
      val (updatedProjectLinks, unchangedProjectLinks) = projectLinks.filterNot(pl=> pl.status == LinkStatus.Terminated ).partition(pl => linkIds.contains(pl.linkId))
      if (linkStatus == LinkStatus.Terminated) {
        //Fetching road addresses in order to obtain the original addressMValues, since we may not have those values on project_link table, after previous recalculations
        val roadAddresses = RoadAddressDAO.fetchByLinkId(updatedProjectLinks.map(pl => pl.linkId).toSet)
        val updatedPL = updatedProjectLinks.map(pl => {
          val roadAddress = roadAddresses.find(_.linkId == pl.linkId)
          pl.copy(startAddrMValue = roadAddress.get.startAddrMValue, endAddrMValue = roadAddress.get.endAddrMValue)
        })
        ProjectDAO.updateProjectLinksToDB(updatedPL.map(_.copy(status = linkStatus, calibrationPoints = (None, None))), userName)
      }
      else
        ProjectDAO.updateProjectLinkStatus(updatedProjectLinks.map(_.id).toSet, linkStatus, userName)
      recalculateProjectLinks(projectId, userName)
    }
  }

  private def recalculateProjectLinks(projectId: Long, userName: String) = {
    val projectLinks = withGeometry(ProjectDAO.getProjectLinks(projectId))
    projectLinks.groupBy(
      pl => (pl.roadNumber, pl.roadPartNumber)).foreach {
      grp =>
        val recalculatedProjectLinks = ProjectSectionCalculator.assignMValues(projectLinks)
        ProjectDAO.updateProjectLinksToDB(recalculatedProjectLinks, userName)
    }
    recalculateChangeTable(projectId)
  }

  private def recalculateChangeTable(projectId: Long): Boolean = {
    try {
      val delta = ProjectDeltaCalculator.delta(projectId)
      setProjectDeltaToDB(delta, projectId)
    } catch {
      case ex: RoadAddressException =>
        logger.info("Delta calculation not possible: " + ex.getMessage)
        false
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
    RoadAddressChangesDAO.insertDeltaToRoadChangeTable(projectDelta, projectId)
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
      case Some(trProjectobject) => mapTRStateToViiteState(trProjectobject.status.getOrElse(""))
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

  def setProjectStatusToSend2TR(projectId:Long) :Unit=
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

  private def getProjectsPendingInTR:Seq[Long]= {
    withDynSession {
      ProjectDAO.getProjectsWithWaitingTRStatus()
    }
  }
  def updateProjectsWaitingResponseFromTR(): Unit =
  {
    val listOfPendingProjects=getProjectsPendingInTR
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
      if (updatedStatus == Saved2TR)
        updateRoadAddressWithProject(updatedStatus, projectID)
      updatedStatus
    }.getOrElse(ProjectState.Unknown)
  }

  private def mapTRStateToViiteState(trState:String): Option[ProjectState] ={

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
      val project=ProjectDAO.getRoadAddressProjectById(projectID)
      val projectStartDate= Some(project.head.startDate)
      val projectLinks=ProjectDAO.getProjectLinks(projectID)
      val floatingFalse=false
      val historyFalse=false
      if (projectLinks.isEmpty)
        throw new RuntimeException(s"Tried to import empty project to road address table after TR response : $newState")

      val roadAddressIDsToExpire= RoadAddressDAO.fetchByLinkId(projectLinks.map( x => x.linkId ).toSet,floatingFalse,historyFalse)
      //Expiring all old addresses by their ID
      roadAddressService.expireRoadAddresses(roadAddressIDsToExpire.map( x => x.id ).toSet)
      //Create endDate rows for old data that is "valid" (row should be ignored after end_date)
      RoadAddressDAO.create(roadAddressIDsToExpire.map(x=> x.copy(endDate =projectStartDate,id =NewRoadAddress)),Some(project.head.createdBy))
      //Create new rows to RoadAddress table defining when new address is used
      importProjectLinksToRoadAddressTable(projectLinks.map(x=>x.copy(endDate = None, startDate =projectStartDate)),roadAddressIDsToExpire,Some(project.head.createdBy))
      //Remove the ProjectLinks from PROJECT_LINK table?
    } else {
      throw new RuntimeException(s"Project state not at Saved2TR: $newState")
    }
  }

  private def importProjectLinksToRoadAddressTable(projectLinks:Seq[ProjectLink], existingRoadAddresses:Seq[RoadAddress],projectOwner:Option[String]) ={
    val existingRoadAddressLinkIds= existingRoadAddresses.map( x=> x.linkId)
    val existingProjectAddresses= projectLinks.filter( x=> existingRoadAddressLinkIds.contains(x.linkId))
    val newProjectLinks= projectLinks.filterNot( x=> existingRoadAddressLinkIds.contains(x.linkId))
    val suravageProjectLinks=newProjectLinks.filter(x=>x.linkGeomSource==LinkGeomSource.SuravageLinkInterface)
    val newNonSuravageLinks=newProjectLinks.filterNot(x=>x.linkGeomSource==LinkGeomSource.SuravageLinkInterface)
    //Fetch geometry for projectlinks from roadaddress table based on link-id
    val (roadLinksWithExistingGeometry,missingDBGeometry)= convertProjectLinksToRoadAddressesWithRoadAddressGeometry(existingProjectAddresses,existingRoadAddresses)
    //Fetches  geometry for newlinks from VVH (excluding suravagelinks) and combines it with projectlinkdata
    val (newRoads,missingNewRoadGeometry)=convertProjectLinkToRoadAddressWithVVHLinkGeometry(newNonSuravageLinks,roadLinkService.fetchVVHRoadlinks(newNonSuravageLinks.map( x=> x.linkId).toSet,frozenTimeVVHAPIServiceEnabled))
    //Fetches geometry for suravagelinks from VVH suravageInterface and combines it to projectLinkdata
    val (newSuravageRoads,missingSuravageGeometry)=convertProjectLinkToRoadAddressWithVVHLinkGeometry(suravageProjectLinks,roadLinkService.fetchSuravageLinksByLinkIdsFromVVH(suravageProjectLinks.map( x=> x.linkId).toSet))
    val projectLinksWithGeometry=roadLinksWithExistingGeometry++newRoads++newSuravageRoads
    val missingGeometry=missingDBGeometry++missingNewRoadGeometry++missingSuravageGeometry
    // TODO add check that geometry can be calculated to all roadparts = at least one known link from each roadpart
    val guessGeometry= guestimateGeometry(missingGeometry.sortBy(x=>x.roadNumber).sortBy(x=>x.roadPartNumber).sortBy(x=>x.startMValue),projectLinksWithGeometry)
    RoadAddressDAO.create(roadLinksWithExistingGeometry++newRoads++newSuravageRoads++guessGeometry,projectOwner)
  }


  /**
    * Loops through geometry and tries to find geometry to missing links
    * @param missingGeometry
    * @param projectRoadaddressGeometry
    * @return
    */

  private def guestimateGeometry(missingGeometry:Seq[RoadAddress],projectRoadaddressGeometry:Seq[RoadAddress]):Seq[RoadAddress]={
    missingGeometry.lastOption match {
      case  Some(missingRoadAddress)=>
      {
        val previousLink=projectRoadaddressGeometry.find(x=>x.startMValue==missingRoadAddress.endMValue && x.roadNumber==missingRoadAddress.roadNumber && x.roadPartNumber==missingRoadAddress.roadPartNumber && x.track==missingRoadAddress.track)
        val nextLink=projectRoadaddressGeometry.find(x=>x.endMValue==missingRoadAddress.startMValue && x.roadNumber==missingRoadAddress.roadNumber && x.roadPartNumber==missingRoadAddress.roadPartNumber && x.track==missingRoadAddress.track)
        (previousLink, nextLink) match {
          case (p,n) if p.nonEmpty && n.nonEmpty => //missing link in the middle
          {
            val roadaddressWithAddedGeometry=missingRoadAddress.copy(geometry=Seq(p.head.geometry.last,n.head.geometry.head))
          if (missingGeometry.size>1)
            guestimateGeometry(missingGeometry.dropRight(1), projectRoadaddressGeometry++Seq(roadaddressWithAddedGeometry) )
            else
             projectRoadaddressGeometry++Seq(roadaddressWithAddedGeometry)
          }
          case (p,n) =>  //since seq is ordered we can search links to "previous" direction
            {
              val foundGeometryOnRoadPartOrTrack= projectRoadaddressGeometry.filter(x=>x.roadNumber==missingRoadAddress.roadNumber && x.roadPartNumber==missingRoadAddress.roadPartNumber &&x.track==missingRoadAddress.track)
              if (foundGeometryOnRoadPartOrTrack.isEmpty)
                throw new RuntimeException(s"RoadPart or trackpart did not have geometry to form missing geometry")
              val previousLinkGeometry=  if (p.nonEmpty) p.head.geometry else None
              val missingGeometryonRoadPartOrTrack= missingGeometry.filter(x=>x.roadNumber==missingRoadAddress.roadNumber && x.roadPartNumber==missingRoadAddress.roadPartNumber && x.track==missingRoadAddress.track)
              val (adjacentLinksWithNoGeometry,previousLinkToChain)=getGeometryToPreviousLinks(missingGeometryonRoadPartOrTrack,foundGeometryOnRoadPartOrTrack,missingRoadAddress,Seq.empty[RoadAddress])
              val adjacentIdSeq=adjacentLinksWithNoGeometry.map(x=>x.id)
              val stillMissingGeometry=missingGeometry.filterNot(x=>adjacentIdSeq.contains(x.id))
              val guessedGeometryForAdjacentLinks=guessGeometryForAdjacentLinks(adjacentLinksWithNoGeometry,previousLinkToChain,None)
              if(stillMissingGeometry.isEmpty)
                return projectRoadaddressGeometry++guessedGeometryForAdjacentLinks
              guestimateGeometry(stillMissingGeometry, guessedGeometryForAdjacentLinks)
            }
          }
        }
      }
    }

  /**
    *
    * @param adjacentLinks Seq of adjacent links missing geometry
    * @param previousLink  link containing geometry that has same startM value as seq:s highest endM value
    * @param nextLink      link containing geometry that has same endM value as seq:s lowest startM value
         Either nextLink or previousLink exists, and error should be throw earlier if no geometry can be used to guess geometry for given links
    * @return              returns links containing geometry
    */
  private def guessGeometryForAdjacentLinks(adjacentLinks:Seq[RoadAddress], previousLink:Option[RoadAddress],nextLink:Option[RoadAddress]):Seq[RoadAddress]  ={
  adjacentLinks.map(x=>x.copy(geometry=Seq(Point(0,0),Point(0,0))))
  }


  /**
    * Gets link sequence missing geometry
     * @param missingGeometry roadlinks from roadpart or track section that are missing geometry
    * @param projectRoadaddressGeometry Known geometry from roadpart or track section
    * @return
    */
  private def getGeometryToPreviousLinks(missingGeometry:Seq[RoadAddress],projectRoadaddressGeometry:Seq[RoadAddress],currentLink:RoadAddress,currentMissingLinksSeq:Seq[RoadAddress]): (Seq[RoadAddress], Option[RoadAddress])={
  val nextLinkWithGeom=projectRoadaddressGeometry.filter(x=>x.endMValue==currentLink.startMValue)
  if (nextLinkWithGeom.nonEmpty) //found link with geometry
     (currentMissingLinksSeq++Seq(currentLink),Some(nextLinkWithGeom.head))
    else
    {
    val nextLinkFromMissingList=missingGeometry.filter(x=>x.endMValue==currentLink.startMValue)
      if (nextLinkFromMissingList.isEmpty) // we are probably on last link
         (currentMissingLinksSeq++Seq(currentLink),None)
      else //if we didnt find geometry, but found next missing geometry we search geometry from of next links next link
        {
          val reducedMissingGeometry=missingGeometry.filterNot(x=>x.id==currentLink.id)
          getGeometryToPreviousLinks(reducedMissingGeometry,projectRoadaddressGeometry,nextLinkFromMissingList.head,currentMissingLinksSeq++Seq(currentLink))
        }
    }
  }



    //val roadPartGroupM=missingGeometry.groupBy( roadParts=>(roadParts.roadNumber,roadParts.roadPartNumber) )
    //check if we can make a guess RoadPart should have at least one link

    //find closest missing link missingling -> search for start_m and end_m



  private def convertProjectLinkToRoadAddressWithVVHLinkGeometry(projectLinks: Seq[ProjectLink], vvhRoadLinks:  Seq[VVHRoadlink]) :(Seq[RoadAddress],Seq[RoadAddress])= {
    if (vvhRoadLinks==null)
      return (Seq.empty[RoadAddress],setProjectLinksAsFloating(projectLinks))
    var projectLinksWithVVHGeometry=new ListBuffer[RoadAddress]()
    var missingGeometry=new ListBuffer[RoadAddress]()
    projectLinks.foreach( x=>{
      val vvhLink= vvhRoadLinks.filter(r=>r.linkId==x.linkId)
      if (vvhLink.nonEmpty)
        projectLinksWithVVHGeometry+= RoadAddress(NewRoadAddress,x.roadNumber,x.roadPartNumber,x.roadType,x.track,x.discontinuity,x.startAddrMValue,x.endAddrMValue,x.startDate, x.endDate,x.modifiedBy,x.lrmPositionId,x.linkId,x.startMValue,x.endMValue,x.sideCode,22L,x.calibrationPoints,x.floating,vvhLink.head.geometry,x.linkGeomSource)
      else //If link has vanished from DB we put link with no geometry to floating state
        missingGeometry++=projectLinksWithVVHGeometry++setProjectLinksAsFloating(Seq(x))
    })
    (projectLinksWithVVHGeometry,missingGeometry)
  }

  private def setProjectLinksAsFloating(projectLinks: Seq[ProjectLink]) :Seq[RoadAddress]={
    var projectLinksWithVVHGeometry=new ListBuffer[RoadAddress]()
    projectLinks.foreach( x=>{
        projectLinksWithVVHGeometry+= RoadAddress(NewRoadAddress,x.roadNumber,x.roadPartNumber,x.roadType,x.track,x.discontinuity,x.startAddrMValue,x.endAddrMValue,x.startDate, x.endDate,x.modifiedBy,x.lrmPositionId,x.linkId,x.startMValue,x.endMValue,x.sideCode,22L,x.calibrationPoints,floating=true,Seq.empty[Point],x.linkGeomSource)
    })
    projectLinksWithVVHGeometry
  }

  private def convertProjectLinksToRoadAddressesWithRoadAddressGeometry(projectLinks:Seq[ProjectLink],roadaddresses:Seq[RoadAddress]) :(Seq[RoadAddress],Seq[RoadAddress])={
    var roadLinksWithExistingGeometry  = new ListBuffer[RoadAddress]()
    var missingGeometry=new ListBuffer[RoadAddress]()
    projectLinks.foreach( x=>{
      val roadAddressLinkF= roadaddresses.filter(r=> r.linkId==x.linkId)
      if (roadAddressLinkF.nonEmpty){
        val  roadAddressLink=roadAddressLinkF.head
        roadLinksWithExistingGeometry+=RoadAddress(NewRoadAddress,x.roadNumber,x.roadPartNumber,x.roadType,x.track,x.discontinuity,x.startAddrMValue,x.endAddrMValue,x.startDate, x.endDate,x.modifiedBy,x.lrmPositionId,x.linkId,x.startMValue,x.endMValue,x.sideCode,22L,x.calibrationPoints,x.floating,roadAddressLink.geometry,x.linkGeomSource)
      }
      else
        missingGeometry++=setProjectLinksAsFloating(Seq(x))
    }
    )
    (roadLinksWithExistingGeometry,missingGeometry)
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
        logger.info(s"The project can not handle multiple ELY areas (the project ELY range is $currentProjectEly). Recording was discarded.")
        Some(s"Projektissa ei voi käsitellä useita ELY-alueita (projektin ELY-alue on $currentProjectEly). Tallennus hylättiin.")
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
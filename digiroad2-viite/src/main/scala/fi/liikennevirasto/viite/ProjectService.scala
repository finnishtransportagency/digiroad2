package fi.liikennevirasto.viite

import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Sequences
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, RoadLinkService}
import fi.liikennevirasto.viite.dao._
import org.slf4j.LoggerFactory

import scala.collection.mutable.ListBuffer

class ProjectService(roadAddressService: RoadAddressService, roadLinkService: RoadLinkService, eventbus: DigiroadEventBus) {
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

  val logger = LoggerFactory.getLogger(getClass)

  /**
    *
    * @param roadNumber    Road's number (long)
    * @param roadStartPart Starting part (long)
    * @param roadEndPart   Ending part (long)
    * @return Optional error message, None if no error
    */
  def checkRoadAddressNumberAndSEParts(roadNumber: Long, roadStartPart: Long, roadEndPart: Long): Option[String] = {
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

  private def createNewProjectToDB(roadAddressProject: RoadAddressProject): RoadAddressProject = {
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

  def checkReservability(roadNumber: Long, startPart: Long, endPart: Long): Either[String, Seq[ReservedRoadPart]] = {
    withDynTransaction {
      var listOfAddressParts: ListBuffer[ReservedRoadPart] = ListBuffer.empty
      for (part <- startPart to endPart) {
        val reserved = ProjectDAO.roadPartReservedByProject(roadNumber, part)
        reserved match {
          case Some(projectname) => return Left(s"TIE $roadNumber OSA $part on jo varattuna projektissa $projectname, tarkista tiedot")
          case None =>
            val (roadpartID, linkID, length, discontinuity, ely, foundAddress) = getAddressPartinfo(roadNumber, part)
            if (foundAddress) // db search failed or we couldnt get info from VVH
              listOfAddressParts += ReservedRoadPart(roadpartID, roadNumber, part, length, Discontinuity.apply(discontinuity), ely)
        }
      }
      Right(listOfAddressParts)
    }
  }

  private def getAddressPartinfo(roadnumber: Long, roadpart: Long): (Long, Long, Double, String, Long, Boolean) = {
    RoadAddressDAO.getRoadPartInfo(roadnumber, roadpart) match {
      case Some((roadpartid, linkid, lenght, discontinuity)) => {
        val enrichment = false
        val roadLink = roadLinkService.getRoadLinksByLinkIdsFromVVH(Set(linkid), enrichment)
        val ely: Option[Long] = roadLink.headOption.map(rl => MunicipalityDAO.getMunicipalityRoadMaintainers.getOrElse(rl.municipalityCode, 0))
        ely match {
          case Some(value) if ely.nonEmpty && ely.get != 0 => (roadpartid, linkid, lenght, Discontinuity.apply(discontinuity.toInt).description, value, true)
          case _ => (0, 0, 0, "", 0, false)
        }
      }
      case None =>
        (0, 0, 0, "", 0, false)

    }
  }

  /**
    * Adds reserved road links (from road parts) to a road address project. Reservability is check before this.
    *
    * @param project
    * @return
    */
  private def addLinksToProject(project: RoadAddressProject): Option[String] = {
    def toProjectLink(roadAddress: RoadAddress): ProjectLink = {
      ProjectLink(id=NewRoadAddress, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track,
        roadAddress.discontinuity, roadAddress.startAddrMValue, roadAddress.endAddrMValue, roadAddress.startDate,
        roadAddress.endDate, modifiedBy=Option(project.createdBy), 0L, roadAddress.linkId, roadAddress.startMValue, roadAddress.endMValue,
        roadAddress.sideCode, roadAddress.calibrationPoints, floating=false, roadAddress.geom, project.id, LinkStatus.NotHandled)
    }
    //TODO: Check that there are no floating road addresses present when starting
    val errors = project.reservedParts.map(roadAddress =>
      if (!RoadAddressDAO.roadPartExists(roadAddress.roadNumber, roadAddress.roadPartNumber)) {
        s"TIE ${roadAddress.roadNumber} OSA: ${roadAddress.roadPartNumber}"
      } else "").filterNot(_ == "")
    if (errors.nonEmpty)
      return Some(s"Seuraavia tieosia ei löytynyt tietokannasta: ${errors.mkString(", ")}")
    else {
      val addresses = project.reservedParts.flatMap(roadaddress =>
        RoadAddressDAO.fetchByRoadPart(roadaddress.roadNumber, roadaddress.roadPartNumber, false).map(toProjectLink))
      ProjectDAO.create(addresses)
      None
    }
  }
  private def createFormOfReservedLinksToSavedRoadParts(project: RoadAddressProject): (Seq[ProjectFormLine], Option[ProjectLink]) = {
    val createdAddresses = ProjectDAO.getProjectLinks(project.id)
    val groupedAddresses = createdAddresses.groupBy { address =>
      (address.roadNumber, address.roadPartNumber)
    }.toSeq.sortBy(_._1._2)(Ordering[Long])
    val adddressestoform = groupedAddresses.map(addressGroup => {
      val lastAddressM = addressGroup._2.last.endAddrMValue
      val roadLink = roadLinkService.getRoadLinksByLinkIdsFromVVH(Set(addressGroup._2.last.linkId), false)
      val addressFormLine = ProjectFormLine(addressGroup._2.last.linkId, project.id, addressGroup._1._1,
        addressGroup._1._2, lastAddressM, MunicipalityDAO.getMunicipalityRoadMaintainers.getOrElse(roadLink.head.municipalityCode, -1),
        addressGroup._2.last.discontinuity.description)
      //TODO:case class RoadAddressProjectFormLine(projectId: Long, roadNumber: Long, roadPartNumber: Long, RoadLength: Long, ely : Long, discontinuity: String)
      addressFormLine
    })
    val addresses = createdAddresses.headOption
    (adddressestoform, addresses)
  }

  private def createNewRoadLinkProject(roadAddressProject: RoadAddressProject) = {
    withDynTransaction {
      val project = createNewProjectToDB(roadAddressProject)
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
      val project:RoadAddressProject = ProjectDAO.getRoadAddressProjects(projectId).head
      val createdAddresses = ProjectDAO.getProjectLinks(project.id)
      val groupedAddresses = createdAddresses.groupBy { address =>
        (address.roadNumber, address.roadPartNumber)
      }.toSeq.sortBy(_._1._2)(Ordering[Long])
      val formInfo: Seq[ProjectFormLine] = groupedAddresses.map(addressGroup => {
        val endAddressM = addressGroup._2.last.endAddrMValue
        val roadLink = roadLinkService.getRoadLinksByLinkIdsFromVVH(Set(addressGroup._2.head.linkId), false)
        val addressFormLine = ProjectFormLine(addressGroup._2.head.linkId, project.id,
          addressGroup._2.head.roadNumber, addressGroup._2.head.roadPartNumber, endAddressM,
          MunicipalityDAO.getMunicipalityRoadMaintainers.getOrElse(roadLink.head.municipalityCode, -1),
          addressGroup._2.last.discontinuity.description)
        addressFormLine
      })

      (project, formInfo)
    }
  }

  def getRoadAddressChangesAndSendToTR(projectId: Set[Long]) = {
    val roadAddressChanges = RoadAddressChangesDAO.fetchRoadAddressChanges(projectId)
    ViiteTierekisteriClient.sendRoadAddressChangeData(roadAddressChanges)
  }

  def getProjectStatusFromTR(projectId: Long) = {
    ViiteTierekisteriClient.getProjectStatus(projectId.toString)
  }




  val listOfExitStatuses=List(1,3,5) // closed, errorinTR,savedtotr magic numbers



  private def getStatusFromTRObject(trProject:Option[TRProjectStatus]):Option[ProjectState] = {
    trProject match {
      case Some(trPojectobject) => mapTRstateToViiteState(trPojectobject.status.getOrElse(""))
      case None => None
      case _ => None
    }
  }

  def updateProjectStatusIfNeeded(currentStatus:ProjectState, newStatus:ProjectState, projectId:Long) :(ProjectState)= {
    if (currentStatus.value!=newStatus.value && newStatus != ProjectState.Unknown) //magic numbers
    {
      ProjectDAO.updateProjectStatus(projectId,newStatus)
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
      withDynSession {
        checkprojectstatus(project)
      }
    }

  }

  private def checkprojectstatus(projectID: Long) =
  {
    val projectstatus=ProjectDAO.getProjectstatus(projectID)
    if (projectstatus.isDefined)
    {
      val currentState=projectstatus.getOrElse(ProjectState.Unknown)
      val newState =getStatusFromTRObject(ViiteTierekisteriClient.getProjectStatusObject(projectID)).getOrElse(ProjectState.Unknown)
      updateProjectStatusIfNeeded(currentState,newState,projectID)
    }
    {
      //TODO
      //copy & update new roads
      // remove links from project-link table
    }
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
}

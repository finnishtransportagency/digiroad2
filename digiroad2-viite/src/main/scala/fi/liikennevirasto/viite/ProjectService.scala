package fi.liikennevirasto.viite

import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, SideCode}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Sequences
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.{RoadAddressException, Track}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, RoadLinkService}
import fi.liikennevirasto.viite.dao.ProjectState._
import fi.liikennevirasto.viite.dao.{ProjectDAO, RoadAddressDAO, _}
import fi.liikennevirasto.viite.model.{ProjectAddressLink, ProjectAddressLinkLike, RoadAddressLink, RoadAddressLinkLike}
import fi.liikennevirasto.viite.process.{Delta, ProjectDeltaCalculator, RoadAddressFiller}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import org.joda.time.format.DateTimeFormat

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.Random
import scala.util.control.NonFatal

class ProjectService(roadAddressService: RoadAddressService, roadLinkService: RoadLinkService, eventbus: DigiroadEventBus) {
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

  val logger = LoggerFactory.getLogger(getClass)

  val allowedSideCodes = List(SideCode.TowardsDigitizing, SideCode.AgainstDigitizing)

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

  /**
    * Checks that new road address is not already reserved (currently only checks roadaddresstable)
    * @param roadNumber road number
    * @param roadPart road part number
    * @param project  roadaddress project needed for id and error message
    * @return
    */
  def checkNewRoadAddressNumberAndPart(roadNumber: Long, roadPart: Long, project :RoadAddressProject): Option[String] = {
      val roadAddresses = RoadAddressDAO.isNewRoadPartUsed(roadNumber, roadPart, project.id)
      if (roadAddresses.isEmpty) {
        None
      } else {
        val fmt = DateTimeFormat.forPattern("dd.MM.yyyy")

        Some(s"TIE $roadNumber OSA $roadPart on jo olemassa projektin alkupäivänä ${project.startDate.toString(fmt)}, tarkista tiedot.") //message to user if address is already in use
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
            val (roadpartID, linkID, length, discontinuity, ely, startDate, endDate, foundAddress) = getAddressPartinfo(roadNumber, part)
            if (foundAddress) // db search failed or we couldnt get info from VVH
              listOfAddressParts += ReservedRoadPart(roadpartID, roadNumber, part, length, Discontinuity.apply(discontinuity), ely, startDate, endDate)
        }
      }
      Right(listOfAddressParts)
    }
  }

  def projDateValidation(reservedParts:Seq[ReservedRoadPart], projDate:DateTime): Option[String] = {
    reservedParts.foreach( part => {
      if(part.startDate.nonEmpty && part.startDate.get.isAfter(projDate)) return Option(s"Tieosalla TIE ${part.roadNumber} OSA ${part.roadPartNumber} alkupäivämäärä ${part.startDate.get.toString("dd.MM.yyyy")} on uudempi kuin tieosoiteprojektin alkupäivämäärä ${projDate.toString("dd.MM.yyyy")}, tarkista tiedot.")
      if(part.endDate.nonEmpty && part.endDate.get.isAfter(projDate)) return Option(s"Tieosalla TIE ${part.roadNumber} OSA ${part.roadPartNumber} loppupäivämäärä ${part.endDate.get.toString("dd.MM.yyyy")} on uudempi kuin tieosoiteprojektin alkupäivämäärä ${projDate.toString("dd.MM.yyyy")}, tarkista tiedot.")
    })
    None
  }

  private def getAddressPartinfo(roadnumber: Long, roadpart: Long): (Long, Long, Double, String, Long, Option[DateTime], Option[DateTime], Boolean) = {
    RoadAddressDAO.getRoadPartInfo(roadnumber, roadpart) match {
      case Some((roadpartid, linkid, lenght, discontinuity, startDate, endDate)) => {
        val enrichment = false
        val roadLink = roadLinkService.getRoadLinksByLinkIdsFromVVH(Set(linkid), enrichment)
        val ely: Option[Long] = roadLink.headOption.map(rl => MunicipalityDAO.getMunicipalityRoadMaintainers.getOrElse(rl.municipalityCode, 0))
        ely match {
          case Some(value) if ely.nonEmpty && ely.get != 0 => (roadpartid, linkid, lenght, Discontinuity.apply(discontinuity.toInt).description, value, Option(startDate), Option(endDate), true)
          case _ => (0, 0, 0, "", 0, None, None, false)
        }
      }
      case None =>
        (0, 0, 0, "", 0, None, None, false)
    }
  }


  /**
    * Used when adding road address that does not have previous address
    */
  def addNewLinksToProject(roadLinks: Seq[ProjectAddressLink], roadAddressProjectID :Long, newRoadNumber : Long, newRoadPartNumber: Long, newTrackCode: Long, newDiscontinuity: Long):String = {

      val randomSideCode = allowedSideCodes(new Random(System.currentTimeMillis()).nextInt(allowedSideCodes.length))
      ProjectDAO.getRoadAddressProjectById(roadAddressProjectID) match {
        case Some(project) => {
          checkNewRoadAddressNumberAndPart(newRoadNumber, newRoadPartNumber, project) match {
            case Some(errorMessage) => errorMessage
            case None => {
              val newProjectLinks = roadLinks.map(projectLink => {
                ProjectLink(NewRoadAddress, newRoadNumber, newRoadPartNumber, Track.apply(newTrackCode.toInt), Discontinuity.apply(newDiscontinuity.toInt), projectLink.startAddressM,
                  projectLink.endAddressM, Some(project.startDate), None, Some(project.createdBy), -1, projectLink.linkId, projectLink.startMValue, projectLink.endMValue, randomSideCode,
                  (projectLink.startCalibrationPoint, projectLink.endCalibrationPoint), false, projectLink.geometry, roadAddressProjectID, projectLink.status, projectLink.roadType, projectLink.roadLinkSource)
              })
              ProjectDAO.create(newProjectLinks)
              ""
            }
          }
        }
        case None => "Projektikoodilla ei löytynyt projektia"
      }
  }
  def changeDirection(projectLink:Seq[Long]): String = {
    try {
    ProjectDAO.flipProjectLinksSideCodes(projectLink)
    ""
    } catch{
     case NonFatal(e) =>
    "Päivitys ei onnistunut"
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
        roadAddress.sideCode, roadAddress.calibrationPoints, floating=false, roadAddress.geom, project.id,
        LinkStatus.NotHandled, roadTypeMap.getOrElse(roadAddress.linkId, RoadType.Unknown))
    }
    //TODO: Check that there are no floating road addresses present when starting
    val errors = project.reservedParts.map(roadAddress =>
      if (!RoadAddressDAO.roadPartExists(roadAddress.roadNumber, roadAddress.roadPartNumber)) {
        s"TIE ${roadAddress.roadNumber} OSA: ${roadAddress.roadPartNumber}"
      } else "").filterNot(_ == "")
    if (errors.nonEmpty)
      Some(s"Seuraavia tieosia ei löytynyt tietokannasta: ${errors.mkString(", ")}")
    else {
      val elyErrors = project.reservedParts.map(roadAddress =>
      if (project.ely.nonEmpty && roadAddress.ely != project.ely.get) {
        s"TIE ${roadAddress.roadNumber} OSA: ${roadAddress.roadPartNumber} (ELY != ${project.ely.get})"
      } else "").filterNot(_ == "")
      if (elyErrors.nonEmpty)
        return Some(s"Seuraavat tieosat ovat eri ELY-numerolla kuin projektin muut osat: ${errors.mkString(", ")}")
      val addresses = project.reservedParts.flatMap { roadaddress =>
        val addressesOnPart = RoadAddressDAO.fetchByRoadPart(roadaddress.roadNumber, roadaddress.roadPartNumber, false)
        val mapping = roadLinkService.getRoadLinksByLinkIdsFromVVH(addressesOnPart.map(_.linkId).toSet, false)
          .map(rl => rl.linkId -> RoadAddressLinkBuilder.getRoadType(rl.administrativeClass, rl.linkType)).toMap
        addressesOnPart.map(toProjectLink(mapping))
      }
      val ely = project.reservedParts.map(_.ely)
      if (ely.distinct.size > 1) {
        Some(s"Tieosat ovat eri ELYistä")
      }  else {
        ProjectDAO.create(addresses)
        if (project.ely.isEmpty)
          ProjectDAO.updateProjectELY(project, ely.head)
        None
      }
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

  def getChangeProject(projectId:Long): Option[ChangeProject] = {
    withDynTransaction {
      try {
        val delta = ProjectDeltaCalculator.delta(projectId)
        //TODO would be better to give roadType to ProjectLinks table instead of calling vvh here
        val vvhRoadLinks = roadLinkService.getRoadLinksByLinkIdsFromVVH(delta.terminations.map(_.linkId).toSet, false)
        val filledTerminations = enrichTerminations(delta.terminations, vvhRoadLinks)

        if (setProjectDeltaToDB(delta.copy(terminations = filledTerminations), projectId)) {
          val roadAddressChanges = RoadAddressChangesDAO.fetchRoadAddressChanges(Set(projectId))
          return Some(ViiteTierekisteriClient.convertToChangeProject(roadAddressChanges.sortBy(r => (r.changeInfo.source.trackCode, r.changeInfo.source.startAddressM, r.changeInfo.source.startRoadPartNumber, r.changeInfo.source.roadNumber))))
        }
      } catch {
        case NonFatal(e) => logger.info(s"Change info not available for project $projectId: " + e.getMessage)
      }
    }
    None
  }

  def enrichTerminations(terminations: Seq[RoadAddress], roadlinks: Seq[RoadLink]): Seq[RoadAddress] = {
    val withRoadType = terminations.map{
      t =>
        val relatedRoadLink = roadlinks.filter(rl => rl.linkId == t.linkId).headOption
        relatedRoadLink match {
          case None => t
          case Some(rl) =>
            val roadType = RoadAddressLinkBuilder.getRoadType(rl.administrativeClass, rl.linkType)
            t.copy(roadType = roadType)
        }
    }
    withRoadType
  }

  def getRoadAddressChangesAndSendToTR(projectId: Set[Long]) = {
    val roadAddressChanges = RoadAddressChangesDAO.fetchRoadAddressChanges(projectId)
    ViiteTierekisteriClient.sendChanges(roadAddressChanges)
  }

  def getProjectRoadLinksByLinkIds(projectId: Long, linkIdsToGet : Set[Long]): Seq[ProjectAddressLink] = {
    def complementaryLinkFilter(roadNumberLimits: Seq[(Int, Int)], municipalities: Set[Int],
                                everything: Boolean = false, publicRoads: Boolean = false)(roadAddressLink: RoadAddressLink) = {
      everything || publicRoads || roadNumberLimits.exists {
        case (start, stop) => roadAddressLink.roadNumber >= start && roadAddressLink.roadNumber <= stop
      }
    }

    val fetchVVHStartTime = System.currentTimeMillis()
    val complementedRoadLinks = roadLinkService.getRoadLinksByLinkIdsFromVVH(linkIdsToGet)
    val linkIds = complementedRoadLinks.map(_.linkId).toSet
    val fetchVVHEndTime = System.currentTimeMillis()
    logger.info("End fetch vvh road links in %.3f sec".format((fetchVVHEndTime - fetchVVHStartTime) * 0.001))

    val buildStartTime = System.currentTimeMillis()

    val projectRoadLinks = complementedRoadLinks
      .map { rl =>
        val ra = Seq()
        val missed =  Seq()
        rl.linkId -> roadAddressService.buildRoadAddressLink(rl, ra, missed)
      }.toMap

    val filledProjectLinks = RoadAddressFiller.fillTopology(complementedRoadLinks, projectRoadLinks)

    filledProjectLinks._1.map(toProjectAddressLink)

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
    val (complementedRoadLinks, complementaryLinkIds) = fetchRoadLinksWithComplementary(boundingRectangle, roadNumberLimits, municipalities, everything, publicRoads)
    val linkIds = complementedRoadLinks.map(_.linkId).toSet
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

    val projectRoadLinks = complementedRoadLinks.map {
      rl =>
        val pl = projectLinks.getOrElse(rl.linkId, Seq())
        rl.linkId -> buildProjectRoadLink(rl, pl)
    }.filterNot { case (_, optPAL) => optPAL.isEmpty}.toMap.mapValues(_.get)

    val filledProjectLinks = RoadAddressFiller.fillProjectTopology(complementedRoadLinks, projectRoadLinks)

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

  private def buildProjectRoadLink(rl: RoadLink, projectLinks: Seq[ProjectLink]): Option[ProjectAddressLink] = {
    val pl = projectLinks.size match {
      case 0 => return None
      case 1 => projectLinks.head
      case _ => fuseProjectLinks(projectLinks)
    }

    Some(RoadAddressLinkBuilder.projectBuild(rl, pl))
  }

  private def fuseProjectLinks(links: Seq[ProjectLink]) = {
    val linkIds = links.map(_.linkId).distinct
    if (linkIds.size != 1)
      throw new IllegalArgumentException(s"Multiple road link ids given for building one link: ${linkIds.mkString(", ")}")
    val (startM, endM, startA, endA) = (links.map(_.startMValue).min, links.map(_.endMValue).max,
      links.map(_.startAddrMValue).min, links.map(_.endAddrMValue).max)
    links.head.copy(startMValue = startM, endMValue = endM, startAddrMValue = startA, endAddrMValue = endA)
  }

  private def fetchRoadLinksWithComplementary(boundingRectangle: BoundingRectangle, roadNumberLimits: Seq[(Int, Int)], municipalities: Set[Int],
                                              everything: Boolean = false, publicRoads: Boolean = false): (Seq[RoadLink], Set[Long]) = {
    val roadLinksF = Future(roadLinkService.getViiteRoadLinksFromVVH(boundingRectangle, roadNumberLimits, municipalities, everything, publicRoads))
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
      withDynSession {
        val status = checkProjectStatus(project)
        ProjectDAO.incrementCheckCounter(project, 1)
        status
      }
    }

  }

  private def checkProjectStatus(projectID: Long) =
  {
    val projectStatus=ProjectDAO.getProjectStatus(projectID)
    if (projectStatus.isDefined)
    {
      val currentState = projectStatus.getOrElse(ProjectState.Unknown)
      val trProjectState = ViiteTierekisteriClient.getProjectStatusObject(projectID)
      val newState = getStatusFromTRObject(trProjectState).getOrElse(ProjectState.Unknown)
      val errorMessage = getTRErrorMessage(trProjectState)
      val updatedStatus = updateProjectStatusIfNeeded(currentState,newState,errorMessage,projectID)
      updateRoadAddressWithProject(newState, projectID)
      updatedStatus
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

  def updateRoadAddressWithProject(newState: ProjectState, projectID: Long): Seq[Long] ={
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
  case class PublishResult(validationSuccess: Boolean, sendSuccess: Boolean, errorMessage: Option[String])
}

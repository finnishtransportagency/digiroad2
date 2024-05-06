package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.ConstructionType.{Planned, UnderConstruction}
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, CableFerry, CycleOrPedestrianPath, Municipality, PedestrianZone, RestArea, ServiceAccess, ServiceOrEmergencyRoad, SpecialTransportWithGate, SpecialTransportWithoutGate, State}
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.dao.{Queries, RoadLinkDAO}
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISSpeedLimitDao
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, UnknownSpeedLimit}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.SpeedLimitService
import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

class UnknownSpeedLimitUpdater {

  val dummyEventBus = new DummyEventBus
  val roadLinkClient: RoadLinkClient = new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
  val roadLinkService = new RoadLinkService(roadLinkClient, dummyEventBus, new DummySerializer)
  val speedLimitService = new SpeedLimitService(dummyEventBus, roadLinkService)
  val dao = new PostGISSpeedLimitDao(roadLinkService)
  val roadLinkDAO = new RoadLinkDAO()
  val logger = LoggerFactory.getLogger(getClass)

  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

  def updateUnknownSpeedLimits() = {
    logger.info("Update unnecessary unknown speed limits.")
    logger.info(DateTime.now().toString())

    withDynTransaction {
      val municipalities = Queries.getMunicipalities
      municipalities.foreach { municipality =>
        logger.info(s"Removing unnecessary unknown speed limits in municipality $municipality")
        removeByMunicipality(municipality)
        logger.info(s"Updating admin class and municipality of the unknown speed limits in municipality $municipality")
        updateAdminClassAndMunicipalityCode(municipality)
        logger.info(s"Generating unknown speed limits in municipality $municipality")
        generateUnknownSpeedLimitsByMunicipality(municipality)
      }
    }
    logger.info("Batch completed.")
  }

  private def isUnsupportedRoadLinkType(roadLink: RoadLink) = {
    !Seq(Municipality, State).contains(roadLink.administrativeClass) ||
      Seq(PedestrianZone, CycleOrPedestrianPath, ServiceAccess, SpecialTransportWithoutGate, SpecialTransportWithGate,
        CableFerry, ServiceOrEmergencyRoad, RestArea).contains(roadLink.linkType) ||
      Seq(Planned, UnderConstruction).contains(roadLink.constructionType)
  }

  def removeByMunicipality(municipality: Int) = {
    val linkIdsWithUnknownLimits = dao.getMunicipalitiesWithUnknown(municipality).map(_._1)
    val linkIdsWithExistingSpeedLimit = dao.fetchSpeedLimitsByLinkIds(linkIdsWithUnknownLimits).map(_.linkId)
    val roadLinksWithUnknown = roadLinkService.getRoadLinksAndComplementariesByLinkIds(linkIdsWithUnknownLimits.toSet, newTransaction = false)
    val unsupportedLinkIds = roadLinksWithUnknown.filter(rl => isUnsupportedRoadLinkType(rl)).map(_.linkId)
    val expiredLinkIds = roadLinkDAO.fetchExpiredByLinkIds(linkIdsWithUnknownLimits.toSet).map(_.linkId)

    if ((linkIdsWithExistingSpeedLimit ++ unsupportedLinkIds ++ expiredLinkIds).nonEmpty) {
      logger.info(s"Speed limits cover links - $linkIdsWithExistingSpeedLimit. Deleting unknown limits.")
      logger.info(s"Unknown speed limits created on unsupported link types $unsupportedLinkIds. Deleting unknown limits.")
      logger.info(s"Unknown speed limits found on expired road links $expiredLinkIds. Deleting unknown limits")
      dao.deleteUnknownSpeedLimits(linkIdsWithExistingSpeedLimit ++ unsupportedLinkIds ++ expiredLinkIds)
    }
  }

  def updateAdminClassAndMunicipalityCode(municipality: Int) = {
    val linksAndAdminsForUnknown = dao.getMunicipalitiesWithUnknown(municipality)
    val roadLinks = roadLinkService.getRoadLinksAndComplementariesByLinkIds(linksAndAdminsForUnknown.map(_._1).toSet, false)

    linksAndAdminsForUnknown.foreach { case (linkIdForUnknown, adminClassForUnknown) =>
      roadLinks.find(_.linkId == linkIdForUnknown) match {
        case Some(r) if r.administrativeClass != AdministrativeClass.apply(adminClassForUnknown) | r.municipalityCode != municipality =>
          logger.info("Updated link " + linkIdForUnknown + " admin class to " + r.administrativeClass.value + " and municipality code to " + r.municipalityCode)
          dao.updateUnknownSpeedLimitAdminClassAndMunicipality(linkIdForUnknown, r.administrativeClass, r.municipalityCode)
        case _ => //do nothing
      }
    }
  }

  def generateUnknownSpeedLimitsByMunicipality(municipality: Int) = {
    val roadLinks = roadLinkService.getRoadLinksAndComplementaryLinksByMunicipality(municipality, newTransaction = false).filterNot(rl => isUnsupportedRoadLinkType(rl))
    val linkIdsWithSpeedLimit = dao.fetchSpeedLimitsByLinkIds(roadLinks.map(_.linkId)).map(_.linkId)
    val roadLinksWithoutSpeedLimits = roadLinks.filterNot(rl => linkIdsWithSpeedLimit.contains(rl.linkId))
    val unknownsToGenerate = roadLinksWithoutSpeedLimits.map(rl => UnknownSpeedLimit(rl.linkId, rl.municipalityCode, rl.administrativeClass))
    if (unknownsToGenerate.nonEmpty) {
      logger.info(s"Generating unknown speed limits for links ${roadLinksWithoutSpeedLimits.map(_.linkId).mkString(", ")}")
      speedLimitService.persistUnknown(unknownsToGenerate, newTransaction = false)
    }
  }
}

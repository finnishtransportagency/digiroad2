package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.TrafficDirection.BothDirections
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, SideCode}
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISSpeedLimitDao
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

class UnknownSpeedLimitUpdater {

  val dummyEventBus = new DummyEventBus
  val roadLinkClient: RoadLinkClient = new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
  val roadLinkService = new RoadLinkService(roadLinkClient, dummyEventBus, new DummySerializer)
  val dao = new PostGISSpeedLimitDao(roadLinkService)
  val logger = LoggerFactory.getLogger(getClass)

  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

  def updateUnknownSpeedLimits() = {
    logger.info("Update unnecessary unknown speed limits.")
    logger.info(DateTime.now().toString())

    withDynTransaction {
      val municipalities = Queries.getMunicipalities
      municipalities.foreach { municipality =>
        logger.info(s"Removing unnecessary unknown speed limits in municipality  + $municipality")
        removeByMunicipality(municipality)
        logger.info(s"Updating admin class and municipality of the unknown speed limits in municipality + $municipality")
        updateAdminClassAndMunicipalityCode(municipality)
      }
    }
  }

  def removeByMunicipality(municipality: Int) = {
    val linkIdsWithUnknownLimits = dao.getMunicipalitiesWithUnknown(municipality).map(_._1)
    val speedLimits = dao.fetchSpeedLimitsByLinkIds(linkIdsWithUnknownLimits)
    val roadLinks = roadLinkService.getRoadLinksAndComplementariesByLinkIds(linkIdsWithUnknownLimits.toSet, false)
    val groupedSpeedLimits = speedLimits.groupBy(_.linkId)

    val linkIdsToDelete = groupedSpeedLimits.filter { grouped =>
      val sideCodes = grouped._2.map(_.sideCode)
      val speedLimitIsOnBothSides = sideCodes.contains(SideCode.BothDirections) || (sideCodes.contains(SideCode.TowardsDigitizing) && sideCodes.contains(SideCode.AgainstDigitizing))
      if (speedLimitIsOnBothSides) {
        true
      } else {
        roadLinks.find(_.linkId == grouped._1) match {
          case Some(roadLink) =>
            val roadLinkIsOneWay = roadLink.trafficDirection != BothDirections
            if (roadLinkIsOneWay) true else false
          case _ => throw new NoSuchElementException(s"Road link with id ${grouped._1} not found.")
        }
      }
    }.keys.toSeq

    if (linkIdsToDelete.nonEmpty) {
      logger.info(s"Speed limits cover links - $linkIdsToDelete. Deleting unknown limits.")
      dao.deleteUnknownSpeedLimits(linkIdsToDelete)
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
}

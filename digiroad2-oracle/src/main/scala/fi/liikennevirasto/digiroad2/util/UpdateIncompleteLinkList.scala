package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer}
import fi.liikennevirasto.digiroad2.asset.DateParser.DateTimePropertyFormat
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.Caching
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, FeatureClass, VVHClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.{AdjustedRoadLinksAndVVHRoadLink, IncompleteLink, RoadLinkChangeSet, RoadLinkService}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import slick.jdbc.StaticQuery.interpolation
import slick.driver.JdbcDriver.backend.Database.dynamicSession

object UpdateIncompleteLinkList {
  val logger = LoggerFactory.getLogger(getClass)

  lazy val vvhClient: VVHClient = {
    new VVHClient(Digiroad2Properties.vvhRestApiEndPoint)
  }

  lazy val roadLinkService: RoadLinkService = {
    new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer)
  }

  def runUpdate(): Unit = {
    logger.info("*** Delete incomplete links")
    clearIncompleteLinks()
    logger.info("*** Get municipalities")
    val municipalities: Seq[Int] = PostGISDatabase.withDynSession {
      Queries.getMunicipalities
    }

    var counter = 0
    municipalities.foreach { municipality =>
      val timer1 = System.currentTimeMillis()
      logger.info("*** Processing municipality: " + municipality)
      val roadLinks = getRoadLinksFromVvhAndUpdateProperties(municipality)
      counter += 1
      logger.info("*** Processed " + roadLinks.length + " road links with municipality " + municipality)
      logger.info(s" number of succeeding municipalities $counter from all ${municipalities.size}")
      logger.info("processing took: %.3f sec".format((System.currentTimeMillis() - timer1) * 0.001))
    }
  }

  def getRoadLinksFromVvhAndUpdateProperties(municipality: Int): Seq[RoadLink] = {
    val (links, _, complementaryLinks) = LogUtils.time(logger, "Get roadlinks and update properties for municipality " + municipality)(
      roadLinkService.reloadRoadLinksWithComplementaryAndChangesFromVVH(municipality, true))

    links ++ complementaryLinks
  }

  private def clearIncompleteLinks(): Unit = {
    PostGISDatabase.withDynTransaction {
      sqlu"""truncate table incomplete_link""".execute
    }
  }

  /**
    * This method performs formatting operations to given vvh road links:
    * - auto-generation of functional class and link type by feature class
    * - information transfer from old link to new link from change data
    *
    * @param allVvhRoadLinks
    * @param changes
    * @return Road links
    */
  def enrichAndGenerateProperties(allVvhRoadLinks: Seq[VVHRoadlink], changes: Seq[ChangeInfo] = Nil): Seq[RoadLink] = {
    val vvhRoadLinks = allVvhRoadLinks.filterNot(_.featureClass == FeatureClass.WinterRoads)

    def autoGenerateProperties(roadLink: RoadLink): RoadLink = {
      val vvhRoadLink = vvhRoadLinks.find(_.linkId == roadLink.linkId)
      vvhRoadLink.get.featureClass match {
        case FeatureClass.TractorRoad => roadLink.copy(functionalClass = 7, linkType = TractorRoad, modifiedBy = Some("automatic_generation"), modifiedAt = Some(DateTimePropertyFormat.print(DateTime.now())))
        case FeatureClass.DrivePath | FeatureClass.CarRoad_IIIb => roadLink.copy(functionalClass = 6, linkType = SingleCarriageway, modifiedBy = Some("automatic_generation"), modifiedAt = Some(DateTimePropertyFormat.print(DateTime.now())))
        case FeatureClass.CycleOrPedestrianPath => roadLink.copy(functionalClass = 8, linkType = CycleOrPedestrianPath, modifiedBy = Some("automatic_generation"), modifiedAt = Some(DateTimePropertyFormat.print(DateTime.now())))
        case FeatureClass.SpecialTransportWithoutGate => roadLink.copy(functionalClass = UnknownFunctionalClass.value, linkType = SpecialTransportWithoutGate, modifiedBy = Some("auto_generation"), modifiedAt = Some(DateTimePropertyFormat.print(DateTime.now())))
        case FeatureClass.SpecialTransportWithGate => roadLink.copy(functionalClass = UnknownFunctionalClass.value, linkType = SpecialTransportWithGate, modifiedBy = Some("auto_generation"), modifiedAt = Some(DateTimePropertyFormat.print(DateTime.now())))
        case FeatureClass.CarRoad_IIIa => vvhRoadLink.get.administrativeClass match {
          case State => roadLink.copy(functionalClass = 4, linkType = SingleCarriageway, modifiedBy = Some("automatic_generation"), modifiedAt = Some(DateTimePropertyFormat.print(DateTime.now())))
          case Municipality | Private => roadLink.copy(functionalClass = 5, linkType = SingleCarriageway, modifiedBy = Some("automatic_generation"), modifiedAt = Some(DateTimePropertyFormat.print(DateTime.now())))
          case _ => roadLink
        }
        case _ => roadLink //similar logic used in roadaddressbuilder
      }
    }

    def toIncompleteLink(roadLink: RoadLink): IncompleteLink = {
      val vvhRoadLink = vvhRoadLinks.find(_.linkId == roadLink.linkId)
      IncompleteLink(roadLink.linkId, vvhRoadLink.get.municipalityCode, roadLink.administrativeClass)
    }

    def canBeAutoGenerated(roadLink: RoadLink): Boolean = {
      vvhRoadLinks.find(_.linkId == roadLink.linkId).get.featureClass match {
        case FeatureClass.AllOthers => false
        case _ => true
      }
    }

    val roadLinkDataByLinkId: Seq[RoadLink] = LogUtils.time(logger, "TEST LOG roadLinkDataByLinkId") {
      roadLinkService.getRoadLinkDataByLinkIds(vvhRoadLinks)
    }
    val (incompleteLinks, completeLinks) = roadLinkDataByLinkId.partition(roadLinkService.isIncomplete)
    val (linksToAutoGenerate, incompleteOtherLinks) = incompleteLinks.partition(canBeAutoGenerated)
    val autoGeneratedLinks = linksToAutoGenerate.map(autoGenerateProperties)
    val (changedLinks, stillIncompleteLinks) = LogUtils.time(logger, "TEST LOG fillIncompleteLinksWithPreviousLinkData") {
      roadLinkService.fillIncompleteLinksWithPreviousLinkData(incompleteOtherLinks, changes)
    }
    val changedPartiallyIncompleteLinks = stillIncompleteLinks.filter(roadLinkService.isPartiallyIncomplete)
    val stillIncompleteLinksInUse = stillIncompleteLinks.filter(_.constructionType == ConstructionType.InUse)

    val adjustedRoadLinks = autoGeneratedLinks ++ changedLinks ++ changedPartiallyIncompleteLinks
    val vvhRoadLinksGroupBy = allVvhRoadLinks.groupBy(_.linkId)
    val pair = adjustedRoadLinks.map(r => AdjustedRoadLinksAndVVHRoadLink(r, vvhRoadLinksGroupBy(r.linkId).last))

    val changeSet = RoadLinkChangeSet(pair, stillIncompleteLinksInUse.map(toIncompleteLink), changes, roadLinkDataByLinkId)
    LogUtils.time(logger, "TEST LOG UpdateRoadLinkChanges") {
      updateRoadLinkChanges(changeSet)
    }

    completeLinks ++ autoGeneratedLinks ++ changedLinks ++ stillIncompleteLinks
  }

  /**
    * Updates road link data in OTH db.
    */
  def updateRoadLinkChanges(roadLinkChangeSet: RoadLinkChangeSet): Unit = {

    LogUtils.time(logger, s"updateRoadLinkChanges updateIncompleteLinks incompleteLinks: ${roadLinkChangeSet.incompleteLinks.size},") {
      roadLinkService.updateIncompleteLinks(roadLinkChangeSet.incompleteLinks)
    }

    LogUtils.time(logger, s"updateRoadLinkChanges updateAutoGeneratedProperties adjustedRoadLinks: ${roadLinkChangeSet.adjustedRoadLinks.size},") {
      roadLinkService.updateAutoGeneratedProperties(roadLinkChangeSet.adjustedRoadLinks)
    }

    LogUtils.time(logger, s"updateRoadLinkChanges fillRoadLinkAttributes roadLinks: ${roadLinkChangeSet.roadLinks.size}, changes: ${roadLinkChangeSet.changes.size},") {
      roadLinkService.fillRoadLinkAttributes(roadLinkChangeSet.roadLinks, roadLinkChangeSet.changes)
    }

  }

}

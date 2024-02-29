package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.client._
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.{RoadLinkReplacementWorkListService, RoadLinkService}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummyEventBus, DummySerializer, GeometryUtils}
import org.slf4j.{Logger, LoggerFactory}

case class MatchedRoadLinks(removedRoadLink: RoadLink, addedRoadLink: RoadLink, hausdorffSimilarityMeasure: Double, areaSimilarityMeasure: Double)

object RoadLinkReplacementFinder {
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  lazy val roadLinkChangeClient: RoadLinkChangeClient = new RoadLinkChangeClient
  lazy val roadLinkReplacementTypeId = 1
  lazy val replacementSearchRadius: Double = 10.0 // Radius in meters for searching nearby links
  lazy val hausdorffMeasureThreshold: Double = 0.6 // Threshold value for matching links by Hausdorff measure (0-1)
  lazy val areaMeasureBuffer: Double = 5 // Buffer size in meters for comparing geometries with area measure
  lazy val areaMeasureThreshold: Double = 0.6 // Threshold value for matching links by area measure (0-1)
  lazy val missingReplacementService: RoadLinkReplacementWorkListService = new RoadLinkReplacementWorkListService
  lazy val eventBus: DigiroadEventBus = new DummyEventBus
  lazy val roadLinkClient: RoadLinkClient = new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
  lazy val roadLinkService: RoadLinkService = new RoadLinkService(roadLinkClient, eventBus, new DummySerializer)

  def findMatchesWithSimilarity(addedLink: RoadLink, removedLinks: Seq[RoadLink]): Seq[MatchedRoadLinks] = {
    removedLinks.flatMap(removedLink => {
      val addedLinkCentroid = GeometryUtils.calculateCentroid(addedLink.geometry)
      val addedLinkCentered = GeometryUtils.centerGeometry(addedLink.geometry, addedLinkCentroid)
      val addedLineStringCentered = GeometryUtils.pointsToLineString(addedLinkCentered)

      val removedLinkCentroid = GeometryUtils.calculateCentroid(removedLink.geometry)
      val removedLinkCentered = GeometryUtils.centerGeometry(removedLink.geometry, removedLinkCentroid)
      val removedLineStringCentered = GeometryUtils.pointsToLineString(removedLinkCentered)

      val hausdorffSimilarityMeasure = GeometryUtils.getHausdorffSimilarityMeasure(addedLineStringCentered, removedLineStringCentered)
      val areaSimilarityMeasure = GeometryUtils.getAreaSimilarityMeasure(addedLineStringCentered.buffer(areaMeasureBuffer), removedLineStringCentered.buffer(areaMeasureBuffer))

      if(hausdorffSimilarityMeasure >= hausdorffMeasureThreshold || areaSimilarityMeasure >= areaMeasureThreshold) {
        Some(MatchedRoadLinks(removedLink, addedLink, hausdorffSimilarityMeasure, areaSimilarityMeasure))
      } else None
    })
  }

  def processChangeSets(): Unit = {
    val lastSuccess = PostGISDatabase.withDynSession(Queries.getLatestSuccessfulSamuutus(roadLinkReplacementTypeId))
    val changeSets = roadLinkChangeClient.getRoadLinkChanges(lastSuccess)
    val changes = changeSets.flatMap(_.changes)

    val matchedRoadLinks = LogUtils.time(logger, "Find possible road link replacements") {
      findMissingReplacements(changes)
    }

    LogUtils.time(logger, s"Insert ${matchedRoadLinks.size} matched road links to work list") {
      missingReplacementService.insertMatchedLinksToWorkList(matchedRoadLinks)
    }
  }

  def findMissingReplacements(changes: Seq[RoadLinkChange]): Seq[MatchedRoadLinks] = {
    val addedLinkIds = changes.filter(_.changeType == RoadLinkChangeType.Add).flatMap(change => change.newLinks.map(_.linkId))
    val removedLinkIds = changes.filter(_.changeType == RoadLinkChangeType.Remove).flatMap(change => change.oldLink.map(_.linkId))
    val addedRoadLinks = roadLinkService.getRoadLinksByLinkIds(addedLinkIds.toSet)
    val removedRoadLinks = roadLinkService.getExistingAndExpiredRoadLinksByLinkIds(removedLinkIds.toSet)
    val matchedLinks = matchRemovesAndAdds(addedRoadLinks, removedRoadLinks)
    matchedLinks
  }

  def matchRemovesAndAdds(addedRoadLinks: Seq[RoadLink], removedRoadLinks: Seq[RoadLink]): Seq[MatchedRoadLinks] = {

    var percentageProcessed = 0
    val matchedLinks = addedRoadLinks.zipWithIndex.flatMap(addedLinkAndIndex => {
      val (addedLink, index) = addedLinkAndIndex
      percentageProcessed = LogUtils.logArrayProgress(logger, "Find matches for added road links", addedRoadLinks.size, index, percentageProcessed)
      val nearbyRemovedLinks = removedRoadLinks.filter(removedRoadLink => {
        GeometryUtils.isAnyPointInsideRadius(addedLink.geometry.head, replacementSearchRadius, removedRoadLink.geometry) ||
          GeometryUtils.isAnyPointInsideRadius(addedLink.geometry.last, replacementSearchRadius, removedRoadLink.geometry)
      })
      findMatchesWithSimilarity(addedLink, nearbyRemovedLinks)
    })

    matchedLinks
  }


}

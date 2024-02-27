package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.GeometryUtils
import fi.liikennevirasto.digiroad2.client._
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkReplacementWorkListService
import org.slf4j.{Logger, LoggerFactory}

case class MatchedRoadLinks(removedLinkId: String, addedLinkId: String)

object RoadLinkReplacementFinder {
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  lazy val roadLinkChangeClient: RoadLinkChangeClient = new RoadLinkChangeClient
  lazy val roadLinkReplacementTypeId = 1
  lazy val replacementSearchRadius: Double = 200.0 // Radius in meters for searching nearby links
  lazy val hausdorffMeasureThreshold: Double = 0.6 // Threshold value for matching links by Hausdorff measure (0-1)
  lazy val areaMeasureThreshold: Double = 0.6 // Threshold value for matching links by area measure (0-1)
  lazy val missingReplacementService: RoadLinkReplacementWorkListService = new RoadLinkReplacementWorkListService

  def findMatchesWithSimilarity(addedLink: ReplaceInfoWithGeometry, removedLinks: Seq[ReplaceInfoWithGeometry]): Seq[ReplaceInfoWithGeometry] = {
    removedLinks.filter(removedLink => {
      val addedLinkCentroid = GeometryUtils.calculateCentroid(addedLink.newGeometry)
      val addedLinkCentered = GeometryUtils.centerGeometry(addedLink.newGeometry, addedLinkCentroid)
      val addedLineStringCentered = GeometryUtils.pointsToLineString(addedLinkCentered)

      val removedLinkCentroid = GeometryUtils.calculateCentroid(removedLink.oldGeometry)
      val removedLinkCentered = GeometryUtils.centerGeometry(removedLink.oldGeometry, removedLinkCentroid)
      val removedLineStringCentered = GeometryUtils.pointsToLineString(removedLinkCentered)

      val hausdorffSimilarityMeasure = GeometryUtils.getHausdorffSimilarityMeasure(addedLineStringCentered, removedLineStringCentered)
      val areaSimilarityMeasure = GeometryUtils.getAreaSimilarityMeasure(addedLineStringCentered, removedLineStringCentered)

      hausdorffSimilarityMeasure >= hausdorffMeasureThreshold || areaSimilarityMeasure >= areaMeasureThreshold
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
    val replaceInfos = changes.flatMap(_.replaceInfo)
    val replaceInfosWithoutReplacement = replaceInfos.filter(ri => ri.newLinkId.isEmpty || ri.oldLinkId.isEmpty)
    val replaceInfosWithGeometry = roadLinkChangeClient.withLinkGeometry(replaceInfosWithoutReplacement, changes)
    val matchedLinks = matchRemovesAndAdds(replaceInfosWithGeometry)
    matchedLinks
  }

  def matchRemovesAndAdds(replaceInfosWithGeometry: Seq[ReplaceInfoWithGeometry]): Seq[MatchedRoadLinks] = {
    val (removed, added) = replaceInfosWithGeometry.partition(_.newLinkId.isEmpty)

    var percentageProcessed = 0
    val matchedLinks = added.zipWithIndex.map(addedLinkAndIndex => {
      val (addedLink, index) = addedLinkAndIndex
      percentageProcessed = LogUtils.logArrayProgress(logger, "Find matches for added road links", added.size, index, percentageProcessed)
      val addedLinkCentroid = GeometryUtils.calculateCentroid(addedLink.newGeometry)
      val nearbyRemovedLinks = removed.filter(removedRoadLink => {
        GeometryUtils.isAnyPointInsideRadius(addedLinkCentroid, replacementSearchRadius, removedRoadLink.oldGeometry)
      })
      (findMatchesWithSimilarity(addedLink, nearbyRemovedLinks), addedLink)
    })

    matchedLinks.flatMap(removedLinksMatchedWithAdded => {
      val (removed, added) = removedLinksMatchedWithAdded
      removed.map(removedLink => MatchedRoadLinks(removedLink.oldLinkId.get, added.newLinkId.get))
    })
  }


}

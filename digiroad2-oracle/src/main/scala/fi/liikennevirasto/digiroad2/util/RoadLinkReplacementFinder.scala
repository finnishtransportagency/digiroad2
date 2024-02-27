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
  lazy val bufferWidth: Double = 15.0
  lazy val replacementSearchRadius: Double = 200.0
  lazy val hausdorffMeasureThreshold: Double = 0.6
  lazy val areaMeasureThreshold: Double = 0.6
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

  def withLinkGeometry(replaceInfos: Seq[ReplaceInfo], changes: Seq[RoadLinkChange]): Seq[ReplaceInfoWithGeometry] = {
    val oldLinks = changes.flatMap(_.oldLink)
    val newLinks = changes.flatMap(_.newLinks)
    replaceInfos.map(ri => {
      val oldLinkGeom = if(ri.oldLinkId.nonEmpty)  {
        oldLinks.find(_.linkId == ri.oldLinkId.get).get.geometry
      } else Nil
      val newLinkgeom = if(ri.newLinkId.nonEmpty)  {
        newLinks.find(_.linkId == ri.newLinkId.get).get.geometry
      } else Nil
      ReplaceInfoWithGeometry(ri.oldLinkId, oldLinkGeom, ri.newLinkId, newLinkgeom, ri.oldFromMValue, ri.oldToMValue, ri.newFromMValue, ri.newToMValue, ri.digitizationChange)
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
    val replaceInfosWithGeometry = withLinkGeometry(replaceInfosWithoutReplacement, changes)
    val matchedLinks = matchRemovesAndAdds(replaceInfosWithGeometry)
    matchedLinks
  }

  def matchRemovesAndAdds(replaceInfosWithGeometry: Seq[ReplaceInfoWithGeometry]): Seq[MatchedRoadLinks] = {
    val (removed, added) = replaceInfosWithGeometry.partition(_.newLinkId.isEmpty)

    val matchedLinks = added.map(addedLink => {
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

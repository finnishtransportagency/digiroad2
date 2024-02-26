package fi.liikennevirasto.digiroad2.util

import com.vividsolutions.jts.geom.Polygon
import fi.liikennevirasto.digiroad2.GeometryUtils
import fi.liikennevirasto.digiroad2.client._
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkMissingReplacementService

case class MatchedRoadLinks(removedLinkId: String, addedLinkId: String)

object RoadLinkReplacementFinder {

  lazy val roadLinkChangeClient: RoadLinkChangeClient = new RoadLinkChangeClient
  lazy val bufferWidth: Double = 15.0
  lazy val replacementSearchRadius: Double = 200.0
  lazy val hausdorffMeasureThreshold: Double = 0.6
  lazy val areaMeasureThreshold: Double = 0.6
  lazy val missingReplacementService: RoadLinkMissingReplacementService = new RoadLinkMissingReplacementService

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

  def polygonWithBuffer(replaceInfoWithGeometry: ReplaceInfoWithGeometry): Polygon = {
    val geomToUse = if(replaceInfoWithGeometry.oldGeometry.nonEmpty) replaceInfoWithGeometry.oldGeometry
    else replaceInfoWithGeometry.newGeometry
    val lineString = GeometryUtils.pointsToLineString(geomToUse)
    lineString.buffer(bufferWidth).asInstanceOf[Polygon]
  }

  def findMatchesWithBuffer(addedLink: ReplaceInfoWithGeometry, removedLinks: Seq[ReplaceInfoWithGeometry]) = {
    removedLinks.filter(removedLink => {
      val addedLinkPolygonWithBuffer = polygonWithBuffer(addedLink)
      val removedLinkPolygonWithBuffer = polygonWithBuffer(removedLink)

      // Check if polygons overlap
      val intersection = addedLinkPolygonWithBuffer.intersection(removedLinkPolygonWithBuffer)
      val addedLinkArea = addedLinkPolygonWithBuffer.getArea
      val removedLinkArea = removedLinkPolygonWithBuffer.getArea
      val overlapPercentage = (intersection.getArea / math.min(addedLinkArea, removedLinkArea)) * 100.0

      overlapPercentage > 50.0
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
    val lastSuccess = PostGISDatabase.withDynSession(Queries.getLatestSuccessfulSamuutus(???))
    val changeSets = roadLinkChangeClient.getRoadLinkChanges(lastSuccess)
    val changes = changeSets.flatMap(_.changes)

    val matchedRoadLinks = findMissingReplacements(changes)
    missingReplacementService.insertMatchedLinksToWorkList(matchedRoadLinks)
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

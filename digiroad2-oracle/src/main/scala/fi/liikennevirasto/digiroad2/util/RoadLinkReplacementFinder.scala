package fi.liikennevirasto.digiroad2.util

import com.vividsolutions.jts.geom.prep.PreparedGeometryFactory
import com.vividsolutions.jts.geom.{Coordinate, GeometryFactory, Polygon}
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}

import fi.liikennevirasto.digiroad2.client.{ReplaceInfo, ReplaceInfoWithGeometry, RoadLinkChange, RoadLinkChangeClient}

import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkMissingReplacementService
import org.geotools.geometry.jts.JTSFactoryFinder

import scala.io.Source

object RoadLinkReplacementFinder {

  lazy val roadLinkChangeClient: RoadLinkChangeClient = new RoadLinkChangeClient
  lazy val bufferTolerance: Double = 15.0
  lazy val parallelTolerance: Double = 5.0
  lazy val missingReplacementService: RoadLinkMissingReplacementService = new RoadLinkMissingReplacementService
  lazy val geometryFactory: GeometryFactory = JTSFactoryFinder.getGeometryFactory(null)

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
    //TODO Tälle oma assetTypeId latestSuccessfullTauluun, jotta voidaan hakea oikeat muutossetit
    val lastSuccess = PostGISDatabase.withDynSession(Queries.getLatestSuccessfulSamuutus(???))
    val changeSets = roadLinkChangeClient.getRoadLinkChanges(lastSuccess)
    val changes = changeSets.flatMap(_.changes)
  }

  def testiFunktio() ={
    val filePath: String = "C:\\Users\\antti.ahopelto\\IdeaProjects\\digiroad2\\digiroad2-oracle\\src\\test\\resources\\smallChangeSet.json"
    val jsonFile: String = Source.fromFile(filePath).mkString
    val testChanges: Seq[RoadLinkChange] = roadLinkChangeClient.convertToRoadLinkChange(jsonFile)
//    findMissingReplacementsWithPostGis(testChanges)
    findMissingReplacementsWithGeotools(testChanges)
  }

  def findMissingReplacementsWithPostGis(changes: Seq[RoadLinkChange]) = {
    val replaceInfos = changes.flatMap(_.replaceInfo)
    val replaceInfosWithoutReplacement = replaceInfos.filter(ri => ri.newLinkId.isEmpty || ri.oldLinkId.isEmpty)
    val replaceInfosWithGeometry = withLinkGeometry(replaceInfosWithoutReplacement, changes)
    missingReplacementService.insertReplaceInfos(replaceInfosWithGeometry)
  }

  def findMissingReplacementsWithGeotools(changes: Seq[RoadLinkChange]) = {
    val replaceInfos = changes.flatMap(_.replaceInfo)
    val replaceInfosWithoutReplacement = replaceInfos.filter(ri => ri.newLinkId.isEmpty || ri.oldLinkId.isEmpty)
    val replaceInfosWithGeometry = withLinkGeometry(replaceInfosWithoutReplacement, changes)
    matchRemovesAndAdds(replaceInfosWithGeometry)
  }

  def matchRemovesAndAdds(replaceInfosWithGeometry: Seq[ReplaceInfoWithGeometry]) = {


    def findMatchesWithParallel(addedLink: ReplaceInfoWithGeometry, removedLinks: Seq[ReplaceInfoWithGeometry]) = {

    }

    def polygonWithBuffer(replaceInfoWithGeometry: ReplaceInfoWithGeometry): Polygon = {
      val geomToUse = if(replaceInfoWithGeometry.oldGeometry.nonEmpty) replaceInfoWithGeometry.oldGeometry
      else replaceInfoWithGeometry.newGeometry

      val coordinates = geomToUse.map(p => new Coordinate(p.x, p.y)).toArray
      val lineString = geometryFactory.createLineString(coordinates)
      lineString.buffer(bufferTolerance).asInstanceOf[Polygon]
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

    val (removed, added) = replaceInfosWithGeometry.partition(_.newLinkId.isEmpty)
    val matchedLinksByBuffer = added.map(addedLink => (addedLink, findMatchesWithBuffer(addedLink,removed))).filter(_._2.nonEmpty)
    val matchedLinksByParallel = added.map(addedLink => (addedLink, findMatchesWithParallel(addedLink, removed)))
  }


}

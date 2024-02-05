package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.client.{ReplaceInfo, ReplaceInfoWithGeometry, RoadLinkChangeClient, RoadLinkChangeSet}
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkMissingReplacementService

object RoadLinkReplacementFinder {

  lazy val roadLinkChangeClient: RoadLinkChangeClient = new RoadLinkChangeClient
  lazy val bufferTolerance: Double = ???
  lazy val missingReplacementService: RoadLinkMissingReplacementService = new RoadLinkMissingReplacementService

  def withLinkGeometry(replaceInfos: Seq[ReplaceInfo], changeSets: Seq[RoadLinkChangeSet]): Seq[ReplaceInfoWithGeometry] = {
    val changes = changeSets.flatMap(_.changes)
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

    val replaceInfos = changeSets.flatMap(_.changes.flatMap(_.replaceInfo))
    val replaceInfosWithoutReplacement = replaceInfos.filter(ri => ri.newLinkId.isEmpty || ri.oldLinkId.isEmpty)
    val replaceInfosWithGeometry = withLinkGeometry(replaceInfosWithoutReplacement, changeSets)
    missingReplacementService.insertReplaceInfos(replaceInfosWithGeometry)


  }

  def findMissingReplacements(changeSet: RoadLinkChangeSet) = {
  ???
  }

}

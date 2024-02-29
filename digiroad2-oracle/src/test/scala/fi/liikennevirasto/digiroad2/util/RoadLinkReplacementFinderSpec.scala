package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.client.{RoadLinkChange, RoadLinkChangeClient}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkReplacementWorkListService
import org.scalatest.{FunSuite, Matchers}

import scala.io.Source

class RoadLinkReplacementFinderSpec extends FunSuite with Matchers {
  val roadLinkChangeClient = new RoadLinkChangeClient
  val missingReplacementService: RoadLinkReplacementWorkListService = new RoadLinkReplacementWorkListService
  val filePath: String = getClass.getClassLoader.getResource("smallChangeSet.json").getPath
  val jsonFile: String = Source.fromFile(filePath).mkString
  val testChanges: Seq[RoadLinkChange] = roadLinkChangeClient.convertToRoadLinkChange(jsonFile)
  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(PostGISDatabase.ds)(test)


  test("Link is removed, other replacing link is added as new. Find match") {
    runWithRollback {
      val matchedRoadLinks = RoadLinkReplacementFinder.findMissingReplacements(testChanges)
      matchedRoadLinks.size should equal(1)
      matchedRoadLinks.head.addedRoadLink.linkId should equal ("2b99971a-b8c8-42ad-bbb4-b3985e2c312b:1")
      matchedRoadLinks.head.removedRoadLink.linkId should equal ("91ac78c9-7a6d-44b3-aefe-7f76cc345f5d:1")
      missingReplacementService.insertMatchedLinksToWorkList(matchedRoadLinks, false)
      val items = missingReplacementService.getMatchedLinksWorkList(false)
      items.size should equal(1)
    }
  }

}

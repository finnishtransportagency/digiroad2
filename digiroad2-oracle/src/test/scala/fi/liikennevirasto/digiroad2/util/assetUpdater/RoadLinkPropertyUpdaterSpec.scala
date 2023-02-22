package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.client.RoadLinkChangeType.Remove
import fi.liikennevirasto.digiroad2.dao.RoadLinkOverrideDAO
import fi.liikennevirasto.digiroad2.dao.RoadLinkOverrideDAO.TrafficDirection
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.scalatest.{FunSuite, Matchers}

import scala.io.Source

class RoadLinkPropertyUpdaterSpec extends FunSuite with Matchers{

  val filePath = getClass.getClassLoader.getResource("smallChangeSet.json").getPath
  val jsonFile = Source.fromFile(filePath).mkString
  val roadLinkPropertyUpdater = new RoadLinkPropertyUpdater
  val roadLinkChangeClient = roadLinkPropertyUpdater.roadLinkChangeClient
  val changes = roadLinkChangeClient.convertToRoadLinkChange(jsonFile)
  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)



  test("overridden values of a removed link are deleted or expired") {
    val removeChanges = changes.filter(_.changeType == Remove)
    runWithRollback {
      val oldLinkIds = removeChanges.map(_.oldLink.get.linkId)
      oldLinkIds.foreach { linkId =>
        RoadLinkOverrideDAO.insert(TrafficDirection, linkId, Some("test"), 2)
        RoadLinkOverrideDAO.get(TrafficDirection, linkId).get should be(2)
      }
      roadLinkPropertyUpdater.removeProperties(removeChanges)
      oldLinkIds.foreach { linkId =>
        RoadLinkOverrideDAO.get(TrafficDirection, linkId) should be(None)
      }
    }
  }
}

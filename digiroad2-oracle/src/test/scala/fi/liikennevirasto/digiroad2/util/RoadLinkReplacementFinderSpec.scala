package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.client.{RoadLinkChange, RoadLinkChangeClient}
import org.scalatest.{FunSuite, Matchers}

import scala.io.Source

class RoadLinkReplacementFinderSpec extends FunSuite with Matchers {
  val roadLinkChangeClient = new RoadLinkChangeClient
  val filePath: String = getClass.getClassLoader.getResource("smallChangeSet.json").getPath
  val jsonFile: String = Source.fromFile(filePath).mkString
  val testChanges: Seq[RoadLinkChange] = roadLinkChangeClient.convertToRoadLinkChange(jsonFile)



}

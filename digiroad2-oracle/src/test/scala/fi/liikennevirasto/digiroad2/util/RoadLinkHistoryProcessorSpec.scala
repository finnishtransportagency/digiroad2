package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{Municipality, TrafficDirection}
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, HistoryRoadLink, RoadLinkFetched}
import org.scalatest.{FunSuite, Matchers}


class RoadLinkHistoryProcessorSpec extends FunSuite with Matchers {
  def id() = LinkIdGenerator.generateRandom();
  def id(linkId:String,version:Int) = s"${linkId.split(":")(0)}:$version"
  
  val linkProcessorDeletedOnly = new RoadLinkHistoryProcessor() // only returns deleted links
  val linkProcessorShowCurrentlyChanged = new RoadLinkHistoryProcessor(true,1.0,50.0) // returns also links which have current history in tolerance min 1 max 50
  val roadLinkFetchedEmpty = Seq.empty[RoadLinkFetched]
  val (linkId1, linkId2, linkId3, linkId4, linkId5) = (id(), id(), id(), id(), id())
  case class KmtkIdAndVersion(kmtkid:String, version:Int)
  def getVersion (linkId:String) = KmtkIdAndVersion(linkId.split(":")(0),linkId.split(":")(1).toInt)

  test("Chain one roadlink has 3  history links from one deleted link. Should return only one") {
    val attributes1 = Map(("LINKID_NEW", id()))
    val attributes2 = Map(("LINKID_NEW", id()))
    val roadLink1 = HistoryRoadLink(linkId1, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None,attributes= attributes1, version = getVersion(linkId1).version,kmtkid = getVersion(linkId1).kmtkid)
    val roadLink2 = HistoryRoadLink(linkId2, 235, Seq(Point(0.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes= attributes2,version = getVersion(linkId2).version,kmtkid = getVersion(linkId3).kmtkid)
    val roadLink3 = HistoryRoadLink(linkId3, 235, Seq(Point(0.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val roadLinksSeq = Seq(roadLink1, roadLink2,roadLink3)
    val filtteredHistoryLinks = linkProcessorDeletedOnly.process(roadLinksSeq, roadLinkFetchedEmpty)
    filtteredHistoryLinks.size should be(1)
  }

  test("History link has currentlink that is outside the tolerance") {
    val roadLink1 = HistoryRoadLink(linkId1, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None,version = getVersion(linkId1).version,kmtkid = getVersion(linkId1).kmtkid)
    val roadLink2 = RoadLinkFetched(linkId1, 235, Seq(Point(0.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val roadLinksSeq = Seq(roadLink1)
    val currentlinks= Seq(roadLink2)
    val filtteredHistoryLinks = linkProcessorShowCurrentlyChanged.process(roadLinksSeq, currentlinks)
    filtteredHistoryLinks.size should be(0)
  }

  test("History link has currentlink with in tolerance") {
    val roadLink1 = HistoryRoadLink(linkId1, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None,0)
    val roadLink2 = RoadLinkFetched(linkId1, 235, Seq(Point(10.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val roadLinksSeq = Seq(roadLink1)
    val currentlinks= Seq(roadLink2)
    val filtteredHistoryLinks = linkProcessorShowCurrentlyChanged.process(roadLinksSeq, currentlinks)
    filtteredHistoryLinks.size should be(1)
  }

  test("Basic list with two chains that  both end up being deleted links")
  {
    val attributes1 = Map(("LINKID_NEW", id()))
    val attributes2 = Map(("LINKID_NEW", id()))
    val attributes3 = Map(("LINKID_NEW", id()))
    val roadLink1 = HistoryRoadLink(linkId1, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes= attributes1,version = getVersion(linkId1).version,kmtkid = getVersion(linkId1).kmtkid)
    val roadLink2 = HistoryRoadLink(linkId2, 235, Seq(Point(0.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes= attributes2,version = getVersion(linkId2).version,kmtkid = getVersion(linkId2).kmtkid)
    val roadLink3 = HistoryRoadLink(linkId3, 235, Seq(Point(0.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None,version = getVersion(linkId3).version,kmtkid = getVersion(linkId3).kmtkid)
    val roadLink4 = HistoryRoadLink(linkId4, 235, Seq(Point(0.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes= attributes3,version = getVersion(linkId4).version,kmtkid = getVersion(linkId4).kmtkid)
    val roadLink5 = HistoryRoadLink(linkId5, 235, Seq(Point(0.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None,version = getVersion(linkId5).version,kmtkid = getVersion(linkId5).kmtkid)
    val roadLinksSeq = Seq(roadLink1, roadLink2, roadLink3, roadLink4, roadLink5)
    val filtteredHistoryLinks = linkProcessorDeletedOnly.process(roadLinksSeq, roadLinkFetchedEmpty)
    filtteredHistoryLinks.size should be(2)
  }
  
  test("Picks newest verson of link when multiple history links have same link-id ")
  {
    val versionOfOne1 =id(linkId1,1)
    val versionOfOne5 =id(linkId1,5)
    val versionOfOne3 =id(linkId1,3)
    
    val roadLink1 = HistoryRoadLink(versionOfOne1, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None,version = getVersion(versionOfOne1).version,kmtkid = getVersion(versionOfOne1).kmtkid)
    val roadLink2 = HistoryRoadLink(versionOfOne5, 235, Seq(Point(0.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None,version = getVersion(versionOfOne5).version,kmtkid = getVersion(versionOfOne5).kmtkid)
    val roadLink3 = HistoryRoadLink(versionOfOne1, 235, Seq(Point(0.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None,version = getVersion(versionOfOne1).version,kmtkid = getVersion(versionOfOne1).kmtkid)
    val roadLink4 = HistoryRoadLink(versionOfOne3, 235, Seq(Point(0.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None,version = getVersion(versionOfOne3).version,kmtkid = getVersion(versionOfOne3).kmtkid)
    val roadLinksSeq = Seq(roadLink1, roadLink2, roadLink3, roadLink4)
    val filtteredHistoryLinks = linkProcessorDeletedOnly.process(roadLinksSeq, roadLinkFetchedEmpty)
    filtteredHistoryLinks.size should be(1)
    filtteredHistoryLinks.head.version should be(5)
  }
  
  test("Picks newest version link when multiple history links have same link-id inside recursion")
  {
    val versioOfFour2 =id(linkId4,2)
    val versioOfFour5 =id(linkId4,5)
    val versioOfFour3 =id(linkId4,3)
    val versioOfFour1 =id(linkId4,1)
    
    val attributes1 = Map(("LINKID_NEW", id()))
    val attributes2 = Map(("LINKID_NEW", id()))
    val attributes3 = Map(("LINKID_NEW", id()))
    
    val roadLink1 = HistoryRoadLink(linkId1, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes=attributes1)
    val roadLink2 = HistoryRoadLink(linkId2, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes=attributes2)
    val roadLink3 = HistoryRoadLink(linkId3, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes=attributes3)
    val roadLink4 = HistoryRoadLink(versioOfFour2, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None,version = getVersion(versioOfFour2).version,kmtkid = getVersion(versioOfFour2).kmtkid)
    val roadLink5 = HistoryRoadLink(versioOfFour5, 235, Seq(Point(0.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None,0,version = getVersion(versioOfFour5).version,kmtkid = getVersion(versioOfFour5).kmtkid)
    val roadLink6 = HistoryRoadLink(versioOfFour3, 235, Seq(Point(0.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None,version = getVersion(versioOfFour3).version,kmtkid = getVersion(versioOfFour3).kmtkid)
    val roadLink7 = HistoryRoadLink(versioOfFour1, 235, Seq(Point(0.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None,version = getVersion(versioOfFour1).version,kmtkid = getVersion(versioOfFour1).kmtkid)
    val roadLinksSeq = Seq(roadLink1, roadLink2, roadLink3, roadLink4, roadLink5, roadLink6, roadLink7)
    val filtteredHistoryLinks = linkProcessorDeletedOnly.process(roadLinksSeq, roadLinkFetchedEmpty)
    filtteredHistoryLinks.size should be(1)
    val chosenLinksEndDate = filtteredHistoryLinks.head.version
    chosenLinksEndDate should be(5)
  }

  test("Basic link with current link with in coordinate tolerance")
  {
    val attributes1 = Map(("LINKID_NEW", linkId2))
    val roadLink1 = HistoryRoadLink(linkId1, 235, Seq(Point(10.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None,attributes=attributes1,version = getVersion(linkId1).version,kmtkid = getVersion(linkId1).kmtkid)
    val roadLink2 = RoadLinkFetched(linkId2, 235, Seq(Point(0.00005, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val historyRoadLinksSeq = Seq(roadLink1)
    val currentRoadLinkSeq = Seq(roadLink2)
    val filtteredHistoryLinks = linkProcessorShowCurrentlyChanged.process(historyRoadLinksSeq, currentRoadLinkSeq)
    filtteredHistoryLinks.size should be (1)
  }

  test("Finds link in recursion when change is inside the tolerance")
  {
    val attributes1 = Map(("LINKID_NEW", id()))
    val attributes2 = Map(("LINKID_NEW", id()))
    val attributes3 = Map(("LINKID_NEW", linkId4))
    val roadLink1 = HistoryRoadLink(linkId1, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None,attributes = attributes1,version = getVersion(linkId1).version,kmtkid = getVersion(linkId1).kmtkid)
    val roadLink2 = HistoryRoadLink(linkId2, 235, Seq(Point(0.00005, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None,attributes= attributes2,version = getVersion(linkId2).version,kmtkid = getVersion(linkId2).kmtkid)
    val roadLink3 = HistoryRoadLink(linkId3, 235, Seq(Point(10.00005, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None,attributes= attributes3,version = getVersion(linkId3).version,kmtkid = getVersion(linkId4).kmtkid)
    val roadLink4 = RoadLinkFetched(linkId4, 235, Seq(Point(0.00005, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val historyRoadLinksSeq = Seq(roadLink1,roadLink2,roadLink3)
    val currentRoadLinkSeq = Seq(roadLink4)
    val filtteredHistoryLinks = linkProcessorShowCurrentlyChanged.process(historyRoadLinksSeq, currentRoadLinkSeq)
    filtteredHistoryLinks.size should be (1)
  }
  
  test("ignores link chain (in recursion) when change is outside the tolerance")
  {
    val attributes1 = Map(("LINKID_NEW", id()))
    val attributes2 = Map(("LINKID_NEW", id()))
    val attributes3 = Map(("LINKID_NEW", id()))
    val roadLink1 = HistoryRoadLink(linkId1, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None,attributes= attributes1,version = getVersion(linkId1).version,kmtkid = getVersion(linkId1).kmtkid)
    val roadLink2 = HistoryRoadLink(linkId2, 235, Seq(Point(0.00005, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None,attributes= attributes2,version = getVersion(linkId2).version,kmtkid = getVersion(linkId2).kmtkid)
    val roadLink3 = HistoryRoadLink(linkId3, 235, Seq(Point(0.00005, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None,attributes= attributes3,version = getVersion(linkId3).version,kmtkid = getVersion(linkId3).kmtkid)
    val roadLink4 = RoadLinkFetched(linkId4, 235, Seq(Point(0.00005, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val historyRoadLinksSeq = Seq(roadLink1,roadLink2,roadLink3)
    val currentRoadLinkSeq = Seq(roadLink4)
    val filtteredHistoryLinks = linkProcessorShowCurrentlyChanged.process(historyRoadLinksSeq, currentRoadLinkSeq)
    filtteredHistoryLinks.size should be (0)
  }

  test("ignores link when change is not inside the tolerance") {

    val attributes1 = Map(("LINKID_NEW", id()))
    val roadLink1 = HistoryRoadLink(linkId1, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None,attributes= attributes1,version = getVersion(linkId1).version,kmtkid = getVersion(linkId1).kmtkid)
    val roadLink2 = RoadLinkFetched(linkId2, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val historyRoadLinksSeq = Seq(roadLink1)
    val currentRoadLinkSeq = Seq(roadLink2)
    val filtteredHistoryLinks = linkProcessorShowCurrentlyChanged.process(historyRoadLinksSeq, currentRoadLinkSeq)
    filtteredHistoryLinks.size should be (0)
  }

  test("Should ignore link which has current link with same id when only deleted links are requested") {
    
    val roadLink1 = HistoryRoadLink(linkId1, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None,version = getVersion(linkId1).version,kmtkid = getVersion(linkId1).kmtkid)
    val roadLink2 = RoadLinkFetched(linkId1, 235, Seq(Point(10.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val historyRoadLinksSeq = Seq(roadLink1)
    val currentRoadLinkSeq = Seq(roadLink2)
    val filtteredHistoryLinks = linkProcessorDeletedOnly.process(historyRoadLinksSeq, currentRoadLinkSeq)
    filtteredHistoryLinks.size should be (0)
  }

  test("Ignore link which has current link.  recursion test") {

    val attributes1 = Map(("LINKID_NEW", id()))
    val roadLink1 = HistoryRoadLink(linkId1, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None,attributes= attributes1,version = getVersion(linkId1).version,kmtkid = getVersion(linkId1).kmtkid)
    val roadLink2 = HistoryRoadLink(linkId2, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None,version = getVersion(linkId2).version,kmtkid = getVersion(linkId2).kmtkid)
    val roadLink3 = RoadLinkFetched(linkId2, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val historyRoadLinksSeq = Seq(roadLink1,roadLink2)
    val currentRoadLinkSeq = Seq(roadLink3)
    val filtteredHistoryLinks = linkProcessorDeletedOnly.process(historyRoadLinksSeq, currentRoadLinkSeq)
    filtteredHistoryLinks.size should be (0)
  }
  
  test("Iignore link which has current link with same id inside even deeper recursion") {
    val roadLink1 = HistoryRoadLink(linkId1, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes=Map(("LINKID_NEW", id())),version = getVersion(linkId1).version,kmtkid = getVersion(linkId1).kmtkid)
    val roadLink2 = HistoryRoadLink(linkId2, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes=Map(("LINKID_NEW", id())),version = getVersion(linkId2).version,kmtkid = getVersion(linkId2).kmtkid)
    val roadLink3 = HistoryRoadLink(linkId4, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None,version = getVersion(linkId4).version,kmtkid = getVersion(linkId4).kmtkid)
    val roadLink4 = RoadLinkFetched(linkId4, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val historyRoadLinksSeq = Seq(roadLink1,roadLink2,roadLink3)
    val currentRoadLinkSeq = Seq(roadLink4)
    val filtteredHistoryLinks = linkProcessorDeletedOnly.process(historyRoadLinksSeq, currentRoadLinkSeq)
    filtteredHistoryLinks.size should be (0)
  }

  test("ignores link inside even deeper recursion when comparison to current links is enabled, but change is not inside the tolerance" ) {
    val roadLink1 = HistoryRoadLink(linkId1, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes=Map(("LINKID_NEW", id())),version = getVersion(linkId1).version,kmtkid = getVersion(linkId1).kmtkid)
    val roadLink2 = HistoryRoadLink(linkId2, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes=Map(("LINKID_NEW", id())),version = getVersion(linkId2).version,kmtkid = getVersion(linkId2).kmtkid)
    val roadLink3 = HistoryRoadLink(linkId3, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes=Map(("LINKID_NEW", id())),version = getVersion(linkId3).version,kmtkid = getVersion(linkId3).kmtkid)
    val roadLink4 = HistoryRoadLink(linkId4, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None,version = getVersion(linkId4).version,kmtkid = getVersion(linkId4).kmtkid)
    val roadLink5 = RoadLinkFetched(linkId4, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val historyRoadLinksSeq = Seq(roadLink1,roadLink2,roadLink3,roadLink4)
    val currentRoadLinkSeq = Seq(roadLink5)
    val filtteredHistoryLinks = linkProcessorShowCurrentlyChanged.process(historyRoadLinksSeq, currentRoadLinkSeq)
    filtteredHistoryLinks.size should be (0)

  }

  test("Finds link inside even deeper recursion when comparison to current links is enabled" ) {
    val roadLink1 = HistoryRoadLink(linkId1, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes= Map(("LINKID_NEW", id())),version = getVersion(linkId1).version,kmtkid = getVersion(linkId1).kmtkid)
    val roadLink2 = HistoryRoadLink(linkId2, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes= Map(("LINKID_NEW", id())),version = getVersion(linkId2).version,kmtkid = getVersion(linkId2).kmtkid)
    val roadLink3 = HistoryRoadLink(linkId4, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val roadLink4 = RoadLinkFetched(linkId4, 235, Seq(Point(10.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val historyRoadLinksSeq = Seq(roadLink1,roadLink2,roadLink3)
    val currentRoadLinkSeq = Seq(roadLink4)
    val filtteredHistoryLinks = linkProcessorShowCurrentlyChanged.process(historyRoadLinksSeq, currentRoadLinkSeq)
    filtteredHistoryLinks.size should be (1)
  }
}

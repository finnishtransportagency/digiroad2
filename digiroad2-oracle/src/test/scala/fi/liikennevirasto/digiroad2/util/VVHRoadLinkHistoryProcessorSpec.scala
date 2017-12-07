package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.{Point, VVHRoadlink}
import fi.liikennevirasto.digiroad2.asset.{Municipality, TrafficDirection}
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, VVHRoadlink}
import org.scalatest.{FunSuite, Matchers}


class VVHRoadLinkHistoryProcessorSpec extends FunSuite with Matchers {

  val linkProcessorDeletedOnly = new VVHRoadLinkHistoryProcessor() // only returns deleted links
  val linkProcessorShowCurrentlyChanged = new VVHRoadLinkHistoryProcessor(true,1.0,50.0) // returns also links which have current history in tolerance min 1 max 50
  val emptyVVHLinkSeq = Seq.empty[VVHRoadlink]

  test("Chain one roadlink has 3  history links from one deleted link. Should return only one") {
    val attributes1 = Map(("LINKID_NEW", BigInt(2)), ("END_DATE", BigInt(3)))
    val attributes2 = Map(("LINKID_NEW", BigInt(3)), ("END_DATE", BigInt(3)))
    val roadLink1 = VVHRoadlink(1, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes1)
    val roadLink2 = VVHRoadlink(2, 235, Seq(Point(0.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes2)
    val roadLink3 = VVHRoadlink(3, 235, Seq(Point(0.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val roadLinksSeq = Seq(roadLink1, roadLink2,roadLink3)
    val filtteredHistoryLinks = linkProcessorDeletedOnly.process(roadLinksSeq, emptyVVHLinkSeq)
    filtteredHistoryLinks.size should be(1)
  }

  test("History link has currentlink that is outside the tolerance") {
    val roadLink1 = VVHRoadlink(1, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val roadLink2 = VVHRoadlink(1, 235, Seq(Point(0.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val roadLinksSeq = Seq(roadLink1)
    val currentlinks= Seq(roadLink2)
    val filtteredHistoryLinks = linkProcessorShowCurrentlyChanged.process(roadLinksSeq, currentlinks)
    filtteredHistoryLinks.size should be(0)
  }

  test("History link has currentlink with in tolerance") {
    val roadLink1 = VVHRoadlink(1, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val roadLink2 = VVHRoadlink(1, 235, Seq(Point(10.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val roadLinksSeq = Seq(roadLink1)
    val currentlinks= Seq(roadLink2)
    val filtteredHistoryLinks = linkProcessorShowCurrentlyChanged.process(roadLinksSeq, currentlinks)
    filtteredHistoryLinks.size should be(1)
  }

  test("Basic list with two chains that  both end up being deleted links")
  {
    val attributes1 = Map(("LINKID_NEW", BigInt(2)), ("END_DATE", BigInt(3)))
    val attributes2 = Map(("LINKID_NEW", BigInt(3)), ("END_DATE", BigInt(3)))
    val attributes4 = Map(("LINKID_NEW", BigInt(5)), ("END_DATE", BigInt(3)))
    val roadLink1 = VVHRoadlink(1, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes1)
    val roadLink2 = VVHRoadlink(2, 235, Seq(Point(0.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes2)
    val roadLink3 = VVHRoadlink(3, 235, Seq(Point(0.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val roadLink4 = VVHRoadlink(4, 235, Seq(Point(0.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes4)
    val roadLink5 = VVHRoadlink(5, 235, Seq(Point(0.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val roadLinksSeq = Seq(roadLink1, roadLink2, roadLink3, roadLink4, roadLink5)
    val filtteredHistoryLinks = linkProcessorDeletedOnly.process(roadLinksSeq, emptyVVHLinkSeq)
    filtteredHistoryLinks.size should be(2)
  }



  test("Picks newest end date link when multiple history links have same link-id ")
  {
    val attributes1 = Map(("END_DATE", BigInt(1)))
    val attributes2 = Map(("END_DATE", BigInt(3)))
    val attributes3 = Map(("END_DATE", BigInt(1)))
    val attributes4 = Map(("END_DATE", BigInt(5)))
    val roadLink1 = VVHRoadlink(1, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes1)
    val roadLink2 = VVHRoadlink(1, 235, Seq(Point(0.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes4)
    val roadLink3 = VVHRoadlink(1, 235, Seq(Point(0.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes3)
    val roadLink4 = VVHRoadlink(1, 235, Seq(Point(0.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes2)
    val roadLink5 = VVHRoadlink(1, 235, Seq(Point(0.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val roadLinksSeq = Seq(roadLink1, roadLink2, roadLink3, roadLink4, roadLink5)
    val filtteredHistoryLinks = linkProcessorDeletedOnly.process(roadLinksSeq, emptyVVHLinkSeq)
    filtteredHistoryLinks.size should be(1)
    val chosenLinksEndDate = filtteredHistoryLinks.head.attributes.getOrElse("END_DATE", 0)
    chosenLinksEndDate should be(5)
  }


  test("Picks newest end date link when multiple history links have same link-id inside recursion")
  {
    val attributes1 = Map(("LINKID_NEW", BigInt(2)))
    val attributes2 = Map(("LINKID_NEW", BigInt(3)))
    val attributes3 = Map(("LINKID_NEW", BigInt(4)))
    val attributes4 = Map(("END_DATE", BigInt(2)))
    val attributes5 = Map(("END_DATE", BigInt(3)))
    val attributes6 = Map(("END_DATE", BigInt(1)))
    val attributes7 = Map(("END_DATE", BigInt(5)))
    val roadLink1 = VVHRoadlink(1, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes1)
    val roadLink2 = VVHRoadlink(2, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes2)
    val roadLink3 = VVHRoadlink(3, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes3)
    val roadLink4 = VVHRoadlink(4, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes4)
    val roadLink5 = VVHRoadlink(4, 235, Seq(Point(0.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes7)
    val roadLink6 = VVHRoadlink(4, 235, Seq(Point(0.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes5)
    val roadLink7 = VVHRoadlink(4, 235, Seq(Point(0.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes6)
    val roadLinksSeq = Seq(roadLink1, roadLink2, roadLink3, roadLink4, roadLink5, roadLink6, roadLink7)
    val filtteredHistoryLinks = linkProcessorDeletedOnly.process(roadLinksSeq, emptyVVHLinkSeq)
    filtteredHistoryLinks.size should be(1)
    val chosenLinksEndDate = filtteredHistoryLinks.head.attributes.getOrElse("END_DATE", 0)
    chosenLinksEndDate should be(5)
  }

  test("Basic link with current link with in coordinate tolerance")
  {
    val attributes1 = Map(("LINKID_NEW", BigInt(2)))
    val roadLink1 = VVHRoadlink(1, 235, Seq(Point(10.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes1)
    val roadLink2 = VVHRoadlink(2, 235, Seq(Point(0.00005, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val historyRoadLinksSeq = Seq(roadLink1)
    val currentRoadLinkSeq = Seq(roadLink2)
    val filtteredHistoryLinks = linkProcessorShowCurrentlyChanged.process(historyRoadLinksSeq, currentRoadLinkSeq)
    filtteredHistoryLinks.size should be (1)
  }

  test("Finds link in recursion when change is inside the tolerance")
  {
    val attributes1 = Map(("LINKID_NEW", BigInt(2)))
    val attributes2 = Map(("LINKID_NEW", BigInt(3)))
    val attributes3 = Map(("LINKID_NEW", BigInt(4)))
    val roadLink1 = VVHRoadlink(1, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes1)
    val roadLink2 = VVHRoadlink(2, 235, Seq(Point(0.00005, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes2)
    val roadLink3 = VVHRoadlink(3, 235, Seq(Point(10.00005, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes3)
    val roadLink4 = VVHRoadlink(4, 235, Seq(Point(0.00005, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val historyRoadLinksSeq = Seq(roadLink1,roadLink2,roadLink3)
    val currentRoadLinkSeq = Seq(roadLink4)
    val filtteredHistoryLinks = linkProcessorShowCurrentlyChanged.process(historyRoadLinksSeq, currentRoadLinkSeq)
    filtteredHistoryLinks.size should be (1)
  }



  test("ignores link chain (in recursion) when change is outside the tolerance")
  {
    val attributes1 = Map(("LINKID_NEW", BigInt(2)))
    val attributes2 = Map(("LINKID_NEW", BigInt(3)))
    val attributes3 = Map(("LINKID_NEW", BigInt(4)))
    val roadLink1 = VVHRoadlink(1, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes1)
    val roadLink2 = VVHRoadlink(2, 235, Seq(Point(0.00005, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes2)
    val roadLink3 = VVHRoadlink(3, 235, Seq(Point(0.00005, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes3)
    val roadLink4 = VVHRoadlink(4, 235, Seq(Point(0.00005, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val historyRoadLinksSeq = Seq(roadLink1,roadLink2,roadLink3)
    val currentRoadLinkSeq = Seq(roadLink4)
    val filtteredHistoryLinks = linkProcessorShowCurrentlyChanged.process(historyRoadLinksSeq, currentRoadLinkSeq)
    filtteredHistoryLinks.size should be (0)
  }


  test("ignores link when change is not inside the tolerance") {

    val attributes1 = Map(("LINKID_NEW", BigInt(2)))
    val roadLink1 = VVHRoadlink(1, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes1)
    val roadLink2 = VVHRoadlink(2, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val historyRoadLinksSeq = Seq(roadLink1)
    val currentRoadLinkSeq = Seq(roadLink2)
    val filtteredHistoryLinks = linkProcessorShowCurrentlyChanged.process(historyRoadLinksSeq, currentRoadLinkSeq)
    filtteredHistoryLinks.size should be (0)
  }

  test("Should ignore link which has current link with same id when only deleted links are requested") {

    val attributes1 = Map(("LINKID_NEW", BigInt(2)))
    val roadLink1 = VVHRoadlink(1, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val roadLink2 = VVHRoadlink(1, 235, Seq(Point(10.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val historyRoadLinksSeq = Seq(roadLink1)
    val currentRoadLinkSeq = Seq(roadLink2)
    val filtteredHistoryLinks = linkProcessorDeletedOnly.process(historyRoadLinksSeq, currentRoadLinkSeq)
    filtteredHistoryLinks.size should be (0)
  }

  test("Ignore link which has current link.  recursion test") {

    val attributes1 = Map(("LINKID_NEW", BigInt(2)))
    val roadLink1 = VVHRoadlink(1, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes1)
    val roadLink2 = VVHRoadlink(2, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val roadLink3 = VVHRoadlink(2, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val historyRoadLinksSeq = Seq(roadLink1,roadLink2)
    val currentRoadLinkSeq = Seq(roadLink3)
    val filtteredHistoryLinks = linkProcessorDeletedOnly.process(historyRoadLinksSeq, currentRoadLinkSeq)
    filtteredHistoryLinks.size should be (0)
  }


  test("Iignore link which has current link with same id inside even deeper recursion") {
    val roadLink1 = VVHRoadlink(1, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, Map(("LINKID_NEW", BigInt(2))))
    val roadLink2 = VVHRoadlink(2, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, Map(("LINKID_NEW", BigInt(3))))
    val roadLink3 = VVHRoadlink(3, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None,Map(("LINKID_NEW", BigInt(4))))
    val roadLink4 = VVHRoadlink(4, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val roadLink5 = VVHRoadlink(4, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val historyRoadLinksSeq = Seq(roadLink1,roadLink2,roadLink4)
    val currentRoadLinkSeq = Seq(roadLink4)
    val filtteredHistoryLinks = linkProcessorDeletedOnly.process(historyRoadLinksSeq, currentRoadLinkSeq)
    filtteredHistoryLinks.size should be (0)
  }



  test("ignores link inside even deeper recursion when comparison to current links is enabled, but change is not inside the tolerance" ) {
    val roadLink1 = VVHRoadlink(1, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, Map(("LINKID_NEW", BigInt(2))))
    val roadLink2 = VVHRoadlink(2, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, Map(("LINKID_NEW", BigInt(3))))
    val roadLink3 = VVHRoadlink(3, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None,Map(("LINKID_NEW", BigInt(4))))
    val roadLink4 = VVHRoadlink(4, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val roadLink5 = VVHRoadlink(4, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val historyRoadLinksSeq = Seq(roadLink1,roadLink2,roadLink3,roadLink4)
    val currentRoadLinkSeq = Seq(roadLink5)
    val filtteredHistoryLinks = linkProcessorShowCurrentlyChanged.process(historyRoadLinksSeq, currentRoadLinkSeq)
    filtteredHistoryLinks.size should be (0)

  }



  test("Finds link inside even deeper recursion when comparison to current links is enabled" ) {
    val roadLink1 = VVHRoadlink(1, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, Map(("LINKID_NEW", BigInt(2))))
    val roadLink2 = VVHRoadlink(2, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, Map(("LINKID_NEW", BigInt(3))))
    val roadLink3 = VVHRoadlink(3, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None,Map(("LINKID_NEW", BigInt(4))))
    val roadLink4 = VVHRoadlink(4, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val roadLink5 = VVHRoadlink(4, 235, Seq(Point(10.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val historyRoadLinksSeq = Seq(roadLink1,roadLink2,roadLink4)
    val currentRoadLinkSeq = Seq(roadLink5)
    val filtteredHistoryLinks = linkProcessorShowCurrentlyChanged.process(historyRoadLinksSeq, currentRoadLinkSeq)
    filtteredHistoryLinks.size should be (1)
  }
}

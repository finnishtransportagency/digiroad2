package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.{FeatureClass, Point, VVHRoadlink}
import fi.liikennevirasto.digiroad2.asset.{Municipality, TrafficDirection}
import org.scalatest.{FunSuite, Matchers}


class VVHRoadLinkHistoryProcessorSpec extends FunSuite with Matchers {

  val linkprocessor = new VVHRoadLinkHistoryProcessor()
  val emptyVVHLinkSeq = Seq.empty[VVHRoadlink]


  test("Basic chain one roadlink has 3 history links for same road. Should return only one") {
    val attributes1 = Map(("LINKID_NEW", BigInt(2)), ("END_DATE", BigInt(3)))
    val attributes2 = Map(("LINKID_NEW", BigInt(3)), ("END_DATE", BigInt(3)))
    val roadLink1 = VVHRoadlink(1, 235, Seq(Point(0.0, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes1)
    val roadLink2 = VVHRoadlink(2, 235, Seq(Point(0.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes2)
    val roadLink3 = VVHRoadlink(3, 235, Seq(Point(0.0, 0.0), Point(1.1, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val roadLinksSeq = Seq(roadLink1, roadLink2)
    // picks links that are newest in each link chains history with that are with in set tolerance . Keeps ones with no current link
    val filtteredHistoryLinks = linkprocessor.process(roadLinksSeq, emptyVVHLinkSeq)
    filtteredHistoryLinks.size should be(1)
  }

  test("Basic list with two chains when no current links avaible")
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
    // picks links that are newest in each link chains history with that are with in set tolerance . Keeps ones with no current link
    val filtteredHistoryLinks = linkprocessor.process(roadLinksSeq, emptyVVHLinkSeq)
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
    // picks links that are newest in each link chains history with that are with in set tolerance . Keeps ones with no current link
    val filtteredHistoryLinks = linkprocessor.process(roadLinksSeq, emptyVVHLinkSeq)
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
    // picks links that are newest in each link chains history with that are with in set tolerance . Keeps ones with no current link
    val filtteredHistoryLinks = linkprocessor.process(roadLinksSeq, emptyVVHLinkSeq)
    filtteredHistoryLinks.size should be(1)
    val chosenLinksEndDate = filtteredHistoryLinks.head.attributes.getOrElse("END_DATE", 0)
    chosenLinksEndDate should be(5)
  }




  test("Basic link with current link with in coordinate range")
  {
    /*
  history roadlink has current link set as new link
   */
    val attributes1 = Map(("LINKID_NEW", BigInt(2)))
    val roadLink1 = VVHRoadlink(1, 235, Seq(Point(10.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes1)
    val roadLink2 = VVHRoadlink(2, 235, Seq(Point(0.00005, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val historyRoadLinksSeq = Seq(roadLink1)
    val currentRoadLinkSeq = Seq(roadLink2)
    val filtteredHistoryLinks = linkprocessor.process(historyRoadLinksSeq, currentRoadLinkSeq)
    filtteredHistoryLinks.size should be (1)
  }


  test("ignores link chain (in recursion) when change is not significant enough")
  {
    /*
  history roadlink has current link set as new link
   */
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
    val filtteredHistoryLinks = linkprocessor.process(historyRoadLinksSeq, currentRoadLinkSeq)
    filtteredHistoryLinks.size should be (0)
  }


  test("ignores link when change is not significant enough") {
    /*
  history roadlink has current link set as new link
   */
    val attributes1 = Map(("LINKID_NEW", BigInt(2)))
    val roadLink1 = VVHRoadlink(1, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, attributes1)
    val roadLink2 = VVHRoadlink(2, 235, Seq(Point(0.00001, 0.0), Point(1.0, 0.0)),
      Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, None)
    val historyRoadLinksSeq = Seq(roadLink1)
    val currentRoadLinkSeq = Seq(roadLink2)
    val filtteredHistoryLinks = linkprocessor.process(historyRoadLinksSeq, currentRoadLinkSeq)
    filtteredHistoryLinks.size should be (0)
  }

}

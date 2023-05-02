package fi.liikennevirasto.digiroad2.client

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{Municipality, State, Unknown}
import fi.liikennevirasto.digiroad2.asset.TrafficDirection.{AgainstDigitizing, BothDirections, TowardsDigitizing}
import org.joda.time.DateTime
import org.scalatest.{FunSuite, Matchers}
import fi.liikennevirasto.digiroad2.client.RoadLinkChangeType.{Add, Remove, Replace, Split}
import scala.io.Source

class RoadLinkChangeClientSpec extends FunSuite with Matchers {

  val roadLinkChangeClient = new RoadLinkChangeClient

  val filePath = getClass.getClassLoader.getResource("smallChangeSet.json").getPath
  val jsonFile = Source.fromFile(filePath).mkString
  val changes = roadLinkChangeClient.convertToRoadLinkChange(jsonFile)

  test("test json convert with whole set") {
    changes.size should be(31)
  }

  test("RoadLinkChange for 'add' contains correct info") {
    val addChange = changes(0)
    addChange.changeType should be(Add)
    addChange.oldLink should be(None)
    addChange.newLinks.size should be(1)
    addChange.newLinks.head.linkId should be("f2eba575-f306-4c37-b49d-a4d27a3fc049:1")
    addChange.newLinks.head.linkLength should be(226.20059703)
    addChange.newLinks.head.geometry.size should be(9)
    addChange.newLinks.head.geometry.head should be(Point(372164.164, 6680334.588, 39.069))
    addChange.newLinks.head.geometry.last should be(Point(372330.456, 6680485.857, 41.831))
    addChange.newLinks.head.roadClass should be(12314)
    addChange.newLinks.head.adminClass should be(State)
    addChange.newLinks.head.municipality should be(49)
    addChange.newLinks.head.trafficDirection should be(BothDirections)
    addChange.replaceInfo should be(Seq.empty)
  }

  test("RoadLinkChange for 'remove' contains correct info") {
    val removeChange = changes(2)
    removeChange.changeType should be(Remove)
    removeChange.oldLink.get.linkId should be("7766bff4-5f02-4c30-af0b-42ad3c0296aa:1")
    removeChange.oldLink.get.linkLength should be(30.92807173)
    removeChange.oldLink.get.geometry.size should be(5)
    removeChange.oldLink.get.geometry.head should be(Point(366408.515, 6674439.018, 3.933))
    removeChange.oldLink.get.geometry.last should be(Point(366425.832, 6674457.102, 5.957))
    removeChange.oldLink.get.roadClass should be(12314)
    removeChange.oldLink.get.adminClass should be(Unknown)
    removeChange.oldLink.get.municipality should be(49)
    removeChange.oldLink.get.trafficDirection should be(BothDirections)
    removeChange.replaceInfo should be(Seq.empty)
  }

  test("RoadLinkChange for 'replace' contains correct info") {
    val replaceChange = changes(4)
    replaceChange.changeType should be(Replace)
    replaceChange.oldLink.get.linkId should be("4f8a33d6-a939-4934-847d-d7b10193b7e9:1")
    replaceChange.oldLink.get.linkLength should be(16.4646643)
    replaceChange.oldLink.get.geometry.size should be(3)
    replaceChange.oldLink.get.geometry.head should be(Point(367074.545, 6675175.568, 17.744))
    replaceChange.oldLink.get.geometry.last should be(Point(367068.125, 6675190.727, 18.792))
    replaceChange.oldLink.get.roadClass should be(12122)
    replaceChange.oldLink.get.adminClass should be(Municipality)
    replaceChange.oldLink.get.municipality should be(49)
    replaceChange.oldLink.get.trafficDirection should be(BothDirections)

    replaceChange.newLinks.size should be(1)
    replaceChange.newLinks.head.linkId should be("4f8a33d6-a939-4934-847d-d7b10193b7e9:2")
    replaceChange.newLinks.head.linkLength should be(16.4646643)
    replaceChange.newLinks.head.geometry.size should be(3)
    replaceChange.newLinks.head.geometry.head should be(Point(367074.545, 6675175.568, 17.744))
    replaceChange.newLinks.head.geometry.last should be(Point(367068.125, 6675190.727, 18.792))
    replaceChange.newLinks.head.roadClass should be(12122)
    replaceChange.newLinks.head.adminClass should be(Municipality)
    replaceChange.newLinks.head.municipality should be(49)
    replaceChange.newLinks.head.trafficDirection should be(BothDirections)

    replaceChange.replaceInfo.size should be(1)
    replaceChange.replaceInfo.head.oldLinkId should be("4f8a33d6-a939-4934-847d-d7b10193b7e9:1")
    replaceChange.replaceInfo.head.newLinkId should be("4f8a33d6-a939-4934-847d-d7b10193b7e9:2")
    replaceChange.replaceInfo.head.oldFromMValue should be(0)
    replaceChange.replaceInfo.head.oldToMValue should be(16.465)
    replaceChange.replaceInfo.head.newFromMValue should be(0)
    replaceChange.replaceInfo.head.newToMValue should be(16.465)
    replaceChange.replaceInfo.head.digitizationChange should be(false)
  }

  test("RoadLinkChange for 'split' contains correct info") {
    val splitChange = changes(10)
    splitChange.changeType should be(Split)
    splitChange.oldLink.get.linkId should be("c19bd6b4-9923-4ce9-a9cb-09779708913e:1")
    splitChange.oldLink.get.linkLength should be(121.67349003)
    splitChange.oldLink.get.geometry.size should be(10)
    splitChange.oldLink.get.geometry.head should be(Point(370507.036, 6678446.659, 15.777))
    splitChange.oldLink.get.geometry.last should be(Point(370624.618, 6678469.114, 14.312))
    splitChange.oldLink.get.roadClass should be(12131)
    splitChange.oldLink.get.adminClass should be(State)
    splitChange.oldLink.get.municipality should be(49)
    splitChange.oldLink.get.trafficDirection should be(TowardsDigitizing)

    splitChange.newLinks.size should be(3)

    splitChange.newLinks.head.linkId should be("0d92e4a4-51cf-4abe-8f0e-8ee22241c3ff:1")
    splitChange.newLinks.head.linkLength should be(17.30470614)
    splitChange.newLinks.head.geometry.size should be(3)
    splitChange.newLinks.head.geometry.head should be(Point(370524.238, 6678444.954, 15.134))
    splitChange.newLinks.head.geometry.last should be(Point(370507.036, 6678446.659, 15.777))
    splitChange.newLinks.head.roadClass should be(12131)
    splitChange.newLinks.head.adminClass should be(State)
    splitChange.newLinks.head.municipality should be(49)
    splitChange.newLinks.head.trafficDirection should be(AgainstDigitizing)

    splitChange.newLinks(1).linkId should be("d59dd3a9-a94d-4f26-b311-6b9b8361c717:1")
    splitChange.newLinks(1).linkLength should be(86.94114036)
    splitChange.newLinks(1).geometry.size should be(6)
    splitChange.newLinks(1).geometry.head should be(Point(370541.665, 6678445.318, 14.602))
    splitChange.newLinks(1).geometry.last should be(Point(370624.618, 6678469.114, 14.312))
    splitChange.newLinks(1).roadClass should be(12131)
    splitChange.newLinks(1).adminClass should be(State)
    splitChange.newLinks(1).municipality should be(49)
    splitChange.newLinks(1).trafficDirection should be(TowardsDigitizing)

    splitChange.newLinks.last.linkId should be("e92c98c9-5a62-4995-a9c0-e40ae0b65747:1")
    splitChange.newLinks.last.linkLength should be(17.43162599)
    splitChange.newLinks.last.geometry.size should be(4)
    splitChange.newLinks.last.geometry.head should be(Point(370524.238, 6678444.954, 15.134))
    splitChange.newLinks.last.geometry.last should be(Point(370541.665, 6678445.318, 14.602))
    splitChange.newLinks.last.roadClass should be(12131)
    splitChange.newLinks.last.adminClass should be(State)
    splitChange.newLinks.last.municipality should be(49)
    splitChange.newLinks.last.trafficDirection should be(TowardsDigitizing)

    splitChange.replaceInfo.size should be(3)

    splitChange.replaceInfo.head.oldLinkId should be("c19bd6b4-9923-4ce9-a9cb-09779708913e:1")
    splitChange.replaceInfo.head.newLinkId should be("0d92e4a4-51cf-4abe-8f0e-8ee22241c3ff:1")
    splitChange.replaceInfo.head.oldFromMValue should be(0)
    splitChange.replaceInfo.head.oldToMValue should be(17.305)
    splitChange.replaceInfo.head.newFromMValue should be(0)
    splitChange.replaceInfo.head.newToMValue should be(17.305)
    splitChange.replaceInfo.head.digitizationChange should be(false)

    splitChange.replaceInfo(1).oldLinkId should be("c19bd6b4-9923-4ce9-a9cb-09779708913e:1")
    splitChange.replaceInfo(1).newLinkId should be("d59dd3a9-a94d-4f26-b311-6b9b8361c717:1")
    splitChange.replaceInfo(1).oldFromMValue should be(34.733)
    splitChange.replaceInfo(1).oldToMValue should be(121.673)
    splitChange.replaceInfo(1).newFromMValue should be(0)
    splitChange.replaceInfo(1).newToMValue should be(86.941)
    splitChange.replaceInfo(1).digitizationChange should be(false)

    splitChange.replaceInfo.last.oldLinkId should be("c19bd6b4-9923-4ce9-a9cb-09779708913e:1")
    splitChange.replaceInfo.last.newLinkId should be("e92c98c9-5a62-4995-a9c0-e40ae0b65747:1")
    splitChange.replaceInfo.last.oldFromMValue should be(17.305)
    splitChange.replaceInfo.last.oldToMValue should be(34.733)
    splitChange.replaceInfo.last.newFromMValue should be(0)
    splitChange.replaceInfo.last.newToMValue should be(17.432)
    splitChange.replaceInfo.last.digitizationChange should be(false)
  }

  // ignored by default, used to test locally that the fetch and convert process works
  ignore("fetch changes from S3 and convert to RoadLinkChange") {
    val roadlinkChanges_all = roadLinkChangeClient.getRoadLinkChanges()
    roadlinkChanges_all.size should not be(0)
    roadlinkChanges_all.foreach(change => change.changeType.isInstanceOf[RoadLinkChangeType] should be(true))
  }
}

package fi.liikennevirasto.digiroad2.util

import java.io.File

import fi.liikennevirasto.digiroad2.{ChangeInfo, Point}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import org.scalatest.{FunSuite, Matchers}

class JsonSerializerTest extends FunSuite with Matchers {

  val serializer = new JsonSerializer
  test("testWriteReadCachedGeometry") {
    val f = File.createTempFile("test", ".cache")
    val roadLinks = Seq(RoadLink(1L, Seq(Point(0.0, 1.0),Point(0.1, 2.0)), 1.1, State, 5, TrafficDirection.BothDirections, Motorway, modifiedAt = Option("yesterday"), modifiedBy = Option("someone"),
      Map()),
      RoadLink(2L, Seq(Point(2.0, 1.0),Point(0.1, 2.0)), 1.1, State, 5, TrafficDirection.BothDirections, Motorway, modifiedAt = Option("yesterday"), modifiedBy = Option("someone"),
      Map()))
    serializer.writeCache(f, roadLinks) should be (true)
    val result = serializer.readCachedGeometry(f)
    result should be (roadLinks)
  }

  test("testWriteReadCachedChanges") {
    val f = File.createTempFile("test", ".cache")
    val changes = Seq(ChangeInfo(Option(1L), Option(2L), 3L, 4, Option(0.0), Option(1.0), Option(1.5), Option(2.5), 10L))
    serializer.writeCache(f, changes) should be (true)
    val result = serializer.readCachedChanges(f)
    result should be (changes)
  }

}

package fi.liikennevirasto.digiroad2.util

import java.io.File

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.ChangeInfo
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

  // Takes some time to run, run manually if needed.
  ignore("testWriteHugeCachedGeometry") {
    val f = File.createTempFile("test", ".cache")
    val roadLink = RoadLink(1L, Seq(Point(0.0, 1.0),Point(0.1, 2.0)), 1.1, State, 5, TrafficDirection.BothDirections, Motorway, modifiedAt = Option("yesterday"), modifiedBy = Option("someone"),
      Map("TO_RIGHT"->104,"LAST_EDITED_DATE"->1476468913000L,"FROM_LEFT"->103,"MTKHEREFLIP"->1,"MTKID"->362888804,
        "ROADNAME_FI"->"Evitskogintie","VERTICALACCURACY"->201,"VALIDFROM"->1379548800000L,"CONSTRUCTIONTYPE"->0,
        "SURFACETYPE"->2,"MTKCLASS"->12122,"ROADPARTNUMBER"->4,"TO_LEFT"->103,
        "geometryWKT"->("LINESTRING ZM (358594.785 6678940.735 57.788000000000466 0, 358599.713 6678945.133 57.78100000000268 6.605100000000675" +
          "358594.785 6678940.735 57.788000000000466 0, 358599.713 6678945.133 57.78100000000268 6.605100000000675" +
          "358594.785 6678940.735 57.788000000000466 0, 358599.713 6678945.133 57.78100000000268 6.605100000000675)"),
        "VERTICALLEVEL"->0,"ROADNAME_SE"->"Evitskogsvägen","MUNICIPALITYCODE"->257,"FROM_RIGHT"->104,
        "CREATED_DATE"->1446132842000L,"GEOMETRY_EDITED_DATE"->1476468913000L,"HORIZONTALACCURACY"->3000,"ROADNUMBER"->1130))
    val hugeList = List.range(1, 500000).map(i => roadLink.copy(linkId = i))
    serializer.writeCache(f, hugeList) should be (true)
    f.length() > 1048576 should be (true)
  }

  test("testWriteReadCachedChanges") {
    val f = File.createTempFile("test", ".cache")
    val changes = Seq(ChangeInfo(Option(1L), Option(2L), 3L, 4, Option(0.0), Option(1.0), Option(1.5), Option(2.5), 10L))
    serializer.writeCache(f, changes) should be (true)
    val result = serializer.readCachedChanges(f)
    result should be (changes)
  }

}

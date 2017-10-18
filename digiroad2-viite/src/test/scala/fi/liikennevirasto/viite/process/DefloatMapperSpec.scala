package fi.liikennevirasto.viite.process

import java.util.Date

import fi.liikennevirasto.digiroad2.RoadLinkType.NormalRoadLinkType
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.RoadType
import fi.liikennevirasto.viite.dao.{CalibrationPoint, Discontinuity, RoadAddress}
import fi.liikennevirasto.viite.model.{Anomaly, RoadAddressLink}
import org.joda.time.DateTime
import org.scalatest.{FunSuite, Matchers}
import fi.liikennevirasto.viite.util._

class DefloatMapperSpec extends FunSuite with Matchers{
  val sources = Seq(
    createRoadAddressLink(193080L, 1021200L, Seq(Point(653051.929,6927199.581,105.14400000000023), Point(653031.198,6927221.85,105.55199999999604), Point(653011.425,6927242.865,105.92900000000373), Point(652995.475256991,6927260.328718615,106.23299510185589)), 4846L, 1L, 0, 4035, 4118, SideCode.AgainstDigitizing, Anomaly.None),
    createRoadAddressLink(233578L, 1021217L, Seq(Point(652995.475,6927260.329,106.2329999999929), Point(652988.767,6927267.695,106.42500000000291), Point(652983.363,6927272.531,106.53200000000652)), 4846L, 1L, 0, 4018, 4035, SideCode.AgainstDigitizing, Anomaly.None)
  )
  val targets = Seq(
    createRoadAddressLink(0L, 500073990L, Seq(Point(653003.293,6927251.369,106.06299999999464), Point(652993.291,6927263.081,106.29799999999523)), 0, 0, 99, 0, 0, SideCode.AgainstDigitizing, Anomaly.NoAddressGiven),
    createRoadAddressLink(0L, 500073981L, Seq(Point(653051.929,6927199.581,105.14400000000023), Point(653031.198,6927221.85,105.55199999999604), Point(653011.425,6927242.865,105.92900000000373), Point(653003.293,6927251.369,106.06299999999464)), 0, 0, 99, 0, 0, SideCode.AgainstDigitizing, Anomaly.NoAddressGiven),
    createRoadAddressLink(0L, 500073988L, Seq(Point(652993.291,6927263.081,106.29799999999523), Point(652988.767,6927267.695,106.42500000000291), Point(652983.363,6927272.531,106.53200000000652)), 0, 0, 99, 0, 0, SideCode.AgainstDigitizing, Anomaly.NoAddressGiven)
  )
  test("test create mapping") {
    val mapping = DefloatMapper.createAddressMap(sources, targets)
    sources.forall(s => mapping.exists(_.sourceLinkId == s.linkId)) should be (true)
    targets.forall(t => mapping.exists(_.targetLinkId == t.linkId)) should be (true)
    mapping.forall(ram => ram.sourceStartM == Double.NaN) should be (false)
    mapping.forall(ram => ram.targetStartM == Double.NaN) should be (false)
    mapping.forall(ram => ram.sourceEndM == Double.NaN) should be (false)
    mapping.forall(ram => ram.targetEndM == Double.NaN) should be (false)
  }

  test("test apply mapping") {
    val roadAddressSource = sources.map(roadAddressLinkToRoadAddress(true))
    val mapping = DefloatMapper.createAddressMap(sources, targets)
    val roadAddressTarget = roadAddressSource.flatMap(DefloatMapper.mapRoadAddresses(mapping))
    roadAddressTarget.size should be (4)
  }

  test("test mapping complex situations") {
    val roadAddressSource = sources.map(roadAddressLinkToRoadAddress(true))
    // Note: this mapping doesn't make sense, it's only for unit testing on complex situation
    val mapping = Seq(
      RoadAddressMapping(1021200L, 1021217L, 0.0, 10.0, 2.214, 5.0, Seq(Point(0.0, 0.0), Point(0.0,10.0)), Seq(Point(0.0, 0.0), Point(0.0,10.0))),
      RoadAddressMapping(1021200L, 1021200L, 10.0, 40.0, 40.0, 0.0, Seq(Point(0.0, 10.0), Point(0.0,40.0)), Seq(Point(0.0, 40.0), Point(0.0,0.0))),
      RoadAddressMapping(1021200L, 1021217L, 40.0, 82.925, 5.0, 17.215, Seq(Point(0.0, 40.0), Point(0.0,143.345)), Seq(Point(0.0, 5.0), Point(0.0,17.215))),
      RoadAddressMapping(1021217L, 1021217L, 0.0, 40.345, 0.0, 2.214/17.215*40.345, Seq(Point(0.0, 40.0), Point(0.0,143.345)), Seq(Point(0.0, 5.0), Point(0.0,17.215)))
    )
    val roadAddressTarget = roadAddressSource.flatMap(DefloatMapper.mapRoadAddresses(mapping))
    roadAddressTarget.size should be (4)
    roadAddressTarget.find(r => r.linkId == 1021200L)
      .map(r => r.startMValue).getOrElse(Double.NaN) should be (0.0 +- .00001)
    roadAddressTarget.find(r => r.linkId == 1021200L)
      .map(r => r.endMValue).getOrElse(Double.NaN) should be (40.0 +- .001)
    roadAddressTarget.find(r => r.linkId == 1021217L && r.startMValue == 0.0)
      .map(r => r.endMValue).getOrElse(Double.NaN) should be (2.214 +- .001)
    roadAddressTarget.find(r => r.linkId == 1021217L && r.startMValue >= 2.213 && r.startMValue <= 2.215)
      .map(r => r.endMValue).getOrElse(Double.NaN) should be (5.0 +- .001)
    roadAddressTarget.find(r => r.linkId == 1021217L && r.startMValue >= 4.999)
      .map(r => r.endMValue).getOrElse(Double.NaN) should be (17.215 +- .001)
  }

  test("test order road address link with intersection") {
    val sources = Seq(
      createRoadAddressLink(1L, 123L, Seq(Point(422739.942,7228000.062), Point(422654.464, 7228017.876)), 1L, 1L, 0, 100, 107, SideCode.TowardsDigitizing, Anomaly.None),
      createRoadAddressLink(3L, 125L, Seq(Point(422565.724, 7228023.602), Point(422556.5834168215, 7228025.871885278)), 1L, 1L, 0, 121, 135, SideCode.TowardsDigitizing, Anomaly.None),
      createRoadAddressLink(2L, 124L, Seq(Point(422654.464, 7228017.876), Point(422565.724,7228023.602)), 1L, 1L, 0, 107, 121, SideCode.AgainstDigitizing, Anomaly.None)
    )
    val targets = Seq(
      createRoadAddressLink(0L, 457L, Seq(Point(422566.54,7228030.756), Point(422557.481, 7228032.199)), 0, 0, 0, 0, 0, SideCode.Unknown, Anomaly.NoAddressGiven),
      createRoadAddressLink(0L, 456L, Seq(Point(422566.54,7228030.756), Point(422598.206, 7228229.117)), 0, 0, 0, 0, 0, SideCode.Unknown, Anomaly.NoAddressGiven),
      createRoadAddressLink(0L, 458L, Seq(Point(422739.942,7228000.062), Point(422566.54, 7228030.756)), 0, 0, 0, 0, 0, SideCode.Unknown, Anomaly.NoAddressGiven)
    )
    an [IllegalArgumentException] should be thrownBy DefloatMapper.orderRoadAddressLinks(sources, targets)
  }

  test("Order road address link sources and targets") {
    val sources = Seq(
      createRoadAddressLink(1L, 123L, Seq(Point(5.0,5.0), Point(10.0, 10.0)), 1L, 1L, 0, 100, 107, SideCode.TowardsDigitizing, Anomaly.None),
      createRoadAddressLink(3L, 125L, Seq(Point(20.0, 0.0), Point(30.0, 10.0)), 1L, 1L, 0, 121, 135, SideCode.TowardsDigitizing, Anomaly.None),
      createRoadAddressLink(2L, 124L, Seq(Point(20.0, 0.0), Point(10.0,10.0)), 1L, 1L, 0, 107, 121, SideCode.AgainstDigitizing, Anomaly.None)
    )
    val targets = Seq(
      createRoadAddressLink(0L, 457L, Seq(Point(19.0,1.0), Point(20.0, 0.0), Point(30.0, 10.0)), 0, 0, 0, 0, 0, SideCode.Unknown, Anomaly.NoAddressGiven),
      createRoadAddressLink(0L, 456L, Seq(Point(5.0,5.0), Point(10.0, 10.0), Point(19.0, 1.0)), 0, 0, 0, 0, 0, SideCode.Unknown, Anomaly.NoAddressGiven)
    )
    val (ordS, ordT) = DefloatMapper.orderRoadAddressLinks(sources, targets)
    ordS.map(_.id) should be (Seq(1L, 2L, 3L))
    ordT.map(_.linkId) should be (Seq(456L, 457L))
  }

  test("Yet another test case to Order road address link sources and targets") {
    val sources = Seq(
      createRoadAddressLink(193080L, 1021200L, Seq(Point(653051.929,6927199.581,105.14400000000023), Point(653031.198,6927221.85,105.55199999999604), Point(653011.425,6927242.865,105.92900000000373), Point(652995.475256991,6927260.328718615,106.23299510185589)), 4846L, 1L, 0, 4035, 4118, SideCode.AgainstDigitizing, Anomaly.None),
      createRoadAddressLink(233578L, 1021217L, Seq(Point(652995.475,6927260.329,106.2329999999929), Point(652988.767,6927267.695,106.42500000000291), Point(652983.363,6927272.531,106.53200000000652)), 4846L, 1L, 0, 4018, 4035, SideCode.AgainstDigitizing, Anomaly.None)
    )
    val targets = Seq(
      createRoadAddressLink(0L, 500073990L, Seq(Point(653003.293,6927251.369,106.06299999999464), Point(652993.291,6927263.081,106.29799999999523)), 0, 0, 99, 0, 0, SideCode.Unknown, Anomaly.NoAddressGiven),
      createRoadAddressLink(0L, 500073981L, Seq(Point(653051.929,6927199.581,105.14400000000023), Point(653031.198,6927221.85,105.55199999999604), Point(653011.425,6927242.865,105.92900000000373), Point(653003.293,6927251.369,106.06299999999464)), 0, 0, 99, 0, 0, SideCode.Unknown, Anomaly.NoAddressGiven),
      createRoadAddressLink(0L, 500073988L, Seq(Point(652993.291,6927263.081,106.29799999999523), Point(652988.767,6927267.695,106.42500000000291), Point(652983.363,6927272.531,106.53200000000652)), 0, 0, 99, 0, 0, SideCode.Unknown, Anomaly.NoAddressGiven)
    )
    val (ordS, ordT) = DefloatMapper.orderRoadAddressLinks(sources, targets)
    ordS.map(_.id) should be (Seq(233578L, 193080L))
    ordT.map(_.linkId) should be (Seq(500073988L, 500073990L, 500073981L))
  }

  test("Test mapping multiple road addresses back to target link on 5172091 -> 5172091") {
    //[{"modifiedAt":"12.02.2016 10:55:04","linkId":5172091,"startAddressM":835,"roadNameFi":"VT 5","roadPartNumber":205,"endDate":"","administrativeClass":"State","segmentId":46,"municipalityCode":749,"roadLinkType":-1,"constructionType":99,"roadNumber":5,"trackCode":1,"roadClass":1,"sideCode":2,"points":[{"x":533330.6491774883,"y":6988502.539965655,"z":112.91750199425307},{"x":533327.561,"y":6988514.938,"z":113.028999999995},{"x":533316.53,"y":6988561.507,"z":113.4149999999936},{"x":533299.757,"y":6988642.994,"z":113.67600000000675},{"x":533284.518,"y":6988725.655,"z":113.58000000000175},{"x":533270.822,"y":6988809.254,"z":113.16300000000047},{"x":533259.436,"y":6988892.555,"z":112.38000000000466},{"x":533249.153,"y":6988975.982,"z":111.625},{"x":533246.7652250581,"y":6988998.4090752285,"z":111.42255146135983}],"id":46,"roadType":"Yleinen tie","newGeometry":[{"x":533350.231,"y":6988423.725,"z":112.24599999999919},{"x":533341.188,"y":6988460.23,"z":112.53699999999662},{"x":533327.561,"y":6988514.938,"z":113.028999999995},{"x":533316.53,"y":6988561.507,"z":113.4149999999936},{"x":533299.757,"y":6988642.994,"z":113.67600000000675},{"x":533284.518,"y":6988725.655,"z":113.58000000000175},{"x":533270.822,"y":6988809.254,"z":113.16300000000047},{"x":533259.436,"y":6988892.555,"z":112.38000000000466},{"x":533249.153,"y":6988975.982,"z":111.625},{"x":533245.308,"y":6989012.096,"z":111.29899999999907},{"x":533244.289,"y":6989018.034,"z":111.36000000000058}],"anomaly":2,"startMValue":101.0,"endAddressM":1340,"endMValue":604.285,"linkType":99,"calibrationPoints":[],"mmlId":318834500,"startDate":"01.08.1992","modifiedBy":"vvh_modified","elyCode":8,"discontinuity":5,"roadLinkSource":5},{"modifiedAt":"12.02.2016 10:55:04","linkId":5172091,"startAddressM":734,"roadNameFi":"VT 5","roadPartNumber":205,"endDate":"","administrativeClass":"State","segmentId":45,"municipalityCode":749,"roadLinkType":-1,"constructionType":99,"roadNumber":5,"trackCode":1,"roadClass":1,"sideCode":2,"points":[{"x":533355.793,"y":6988404.722,"z":112.08900000000722},{"x":533341.188,"y":6988460.23,"z":112.53699999999662},{"x":533330.6491774883,"y":6988502.539965655,"z":112.91750199425307}],"id":45,"roadType":"Yleinen tie","newGeometry":[{"x":533350.231,"y":6988423.725,"z":112.24599999999919},{"x":533341.188,"y":6988460.23,"z":112.53699999999662},{"x":533327.561,"y":6988514.938,"z":113.028999999995},{"x":533316.53,"y":6988561.507,"z":113.4149999999936},{"x":533299.757,"y":6988642.994,"z":113.67600000000675},{"x":533284.518,"y":6988725.655,"z":113.58000000000175},{"x":533270.822,"y":6988809.254,"z":113.16300000000047},{"x":533259.436,"y":6988892.555,"z":112.38000000000466},{"x":533249.153,"y":6988975.982,"z":111.625},{"x":533245.308,"y":6989012.096,"z":111.29899999999907},{"x":533244.289,"y":6989018.034,"z":111.36000000000058}],"anomaly":2,"startMValue":0.0,"endAddressM":835,"endMValue":101.0,"linkType":99,"calibrationPoints":[],"mmlId":318834500,"startDate":"16.12.1991","modifiedBy":"vvh_modified","elyCode":8,"discontinuity":5,"roadLinkSource":5}]
    val base = Seq(
      createRoadAddressLink(46, 5172091L, Seq(Point(533330.6491774883,6988502.539965655,112.91750199425307),Point(533327.561,6988514.938,113.028999999995),Point(533316.53,6988561.507,113.4149999999936),Point(533299.757,6988642.994,
        113.67600000000675),Point(533284.518,6988725.655,113.58000000000175),Point(533270.822,6988809.254,113.16300000000047),Point(533259.436,6988892.555,112.38000000000466),Point(533249.153,
        6988975.982,111.625),Point(533246.7652250581,6988998.4090752285,111.42255146135983)), 5L, 205L, 1L, 835, 1340, SideCode.TowardsDigitizing, Anomaly.GeometryChanged),
      createRoadAddressLink(45, 5172091L, Seq(Point(533355.793, 6988404.722, 112.08900000000722),Point(533341.188, 6988460.23, 112.53699999999662),Point(533330.6491774883,6988502.539965655,112.91750199425307)),
        5L, 205L, 1L, 734, 835, SideCode.TowardsDigitizing, Anomaly.GeometryChanged)
    )
    val sources = Seq(
      base.head.copy(startMValue = 101.0, endMValue = 604.285),
      base.last.copy(startMValue = 0.0, endMValue = 101.0)
    )
    val targets = Seq(
      createRoadAddressLink(0L, 5172091L, sources.tail.head.geometry ++ sources.head.geometry, 0, 0, 99, 0, 0, SideCode.Unknown, Anomaly.NoAddressGiven)
    )
    val mappings = DefloatMapper.createAddressMap(sources, targets)
    mappings.exists(m => m.sourceStartM == 0.0 && m.targetStartM == 0.0) should be (true)
    mappings.exists(m => m.sourceEndM == 604.285 && m.targetEndM == 604.285) should be (true)
    mappings.size should be (1)
    val mapped = sources.map(roadAddressLinkToRoadAddress(true)).flatMap(DefloatMapper.mapRoadAddresses(mappings))
    mapped.exists(r =>
      mapped.filterNot(_ == r).exists(r2 =>
        !(r2.startAddrMValue >= r.endAddrMValue || r2.endAddrMValue <= r.startAddrMValue))
    ) should be (false)
  }

  test("post transfer check passes on correct input") {
    val seq = Seq(createRoadAddressLink(-1000, 2, Seq(), 1, 1, 0, 100, 104, SideCode.TowardsDigitizing, Anomaly.None)).map(roadAddressLinkToRoadAddress(false))
    val org = Seq(createRoadAddressLink(1, 1, Seq(), 1, 1, 0, 100, 102, SideCode.TowardsDigitizing, Anomaly.None),
      createRoadAddressLink(1, 2, Seq(), 1, 1, 0, 102, 104, SideCode.TowardsDigitizing, Anomaly.None)).map(roadAddressLinkToRoadAddress(false))
    DefloatMapper.postTransferChecks(seq, org)
  }

  test("post transfer check fails if target addresses are missing") {
    val seq = Seq(createRoadAddressLink(-1000, 2, Seq(), 1, 1, 0, 100, 104, SideCode.TowardsDigitizing, Anomaly.None)).map(roadAddressLinkToRoadAddress(false))
    val org = Seq(createRoadAddressLink(1, 1, Seq(), 1, 1, 0, 100, 108, SideCode.TowardsDigitizing, Anomaly.None)).map(roadAddressLinkToRoadAddress(false))
    val t = intercept[InvalidAddressDataException] {
      DefloatMapper.postTransferChecks(seq, org)
    }
    t.getMessage should be ("Generated address list does not end at 108 but 104")
  }

  test("post transfer check fails if target addresses has a gap") {
    val seq = Seq(createRoadAddressLink(-1000, 2, Seq(), 1, 1, 0, 100, 104, SideCode.TowardsDigitizing, Anomaly.None),
      createRoadAddressLink(-1000, 2, Seq(), 1, 1, 0, 105, 108, SideCode.TowardsDigitizing, Anomaly.None)).map(roadAddressLinkToRoadAddress(false))
    val org = Seq(createRoadAddressLink(1, 1, Seq(), 1, 1, 0, 100, 108, SideCode.TowardsDigitizing, Anomaly.None)).map(roadAddressLinkToRoadAddress(false))
    val t = intercept[InvalidAddressDataException] {
      DefloatMapper.postTransferChecks(seq, org)
    }
    t.getMessage should be ("Generated address list was non-continuous")
  }

  private def createRoadAddressLink(id: Long, linkId: Long, geom: Seq[Point], roadNumber: Long, roadPartNumber: Long, trackCode: Long,
                                    startAddressM: Long, endAddressM: Long, sideCode: SideCode, anomaly: Anomaly, startCalibrationPoint: Boolean = false,
                                    endCalibrationPoint: Boolean = false) = {
    val length = GeometryUtils.geometryLength(geom)
    RoadAddressLink(id, linkId, geom, length, State, LinkType.apply(1), NormalRoadLinkType,
      ConstructionType.InUse, NormalLinkInterface, RoadType.PublicRoad,"Vt5", BigInt(0), None, None, Map(), roadNumber, roadPartNumber,
      trackCode, 1, 5, startAddressM, endAddressM, "2016-01-01", "", 0.0, GeometryUtils.geometryLength(geom), sideCode,
      if (startCalibrationPoint) { Option(CalibrationPoint(linkId, if (sideCode == SideCode.TowardsDigitizing) 0.0 else length, startAddressM))} else None,
      if (endCalibrationPoint) { Option(CalibrationPoint(linkId, if (sideCode == SideCode.AgainstDigitizing) 0.0 else length, endAddressM))} else None,
      anomaly, 0)

  }

  private def roadAddressLinkToRoadAddress(floating: Boolean)(l: RoadAddressLink) = {
    RoadAddress(l.id, l.roadNumber, l.roadPartNumber, RoadType.Unknown, Track.apply(l.trackCode.toInt), Discontinuity.apply(l.discontinuity.toInt),
      l.startAddressM, l.endAddressM, Option(new DateTime(new Date())), None, None, 0, l.linkId, l.startMValue, l.endMValue, l.sideCode, l.attributes.getOrElse("ADJUSTED_TIMESTAMP", 0L).asInstanceOf[Long],
      (l.startCalibrationPoint, l.endCalibrationPoint), floating, l.geometry, l.roadLinkSource, l.elyCode)
  }
}

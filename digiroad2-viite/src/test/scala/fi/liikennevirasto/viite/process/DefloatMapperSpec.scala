package fi.liikennevirasto.viite.process

import java.util.Date

import fi.liikennevirasto.digiroad2.RoadLinkType.NormalRoadLinkType
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset.{ConstructionType, LinkType, SideCode, State}
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.RoadType
import fi.liikennevirasto.viite.dao.{CalibrationPoint, Discontinuity, RoadAddress}
import fi.liikennevirasto.viite.model.{Anomaly, RoadAddressLink}
import org.joda.time.DateTime
import org.scalatest.{FunSuite, Matchers}

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
    roadAddressTarget.foreach(println)
    roadAddressTarget.size should be (4)
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

  private def createRoadAddressLink(id: Long, linkId: Long, geom: Seq[Point], roadNumber: Long, roadPartNumber: Long, trackCode: Long,
                                    startAddressM: Long, endAddressM: Long, sideCode: SideCode, anomaly: Anomaly, startCalibrationPoint: Boolean = false,
                                    endCalibrationPoint: Boolean = false) = {
    val length = GeometryUtils.geometryLength(geom)
    RoadAddressLink(id, linkId, geom, length, State, LinkType.apply(1), NormalRoadLinkType,
      ConstructionType.InUse, NormalLinkInterface, RoadType.PublicRoad, None, None, Map(), roadNumber, roadPartNumber,
      trackCode, 1, 5, startAddressM, endAddressM, "2016-01-01", "", 0.0, GeometryUtils.geometryLength(geom), sideCode,
      if (startCalibrationPoint) { Option(CalibrationPoint(linkId, if (sideCode == SideCode.TowardsDigitizing) 0.0 else length, startAddressM))} else None,
      if (endCalibrationPoint) { Option(CalibrationPoint(linkId, if (sideCode == SideCode.AgainstDigitizing) 0.0 else length, endAddressM))} else None,
      anomaly, 0)

  }

  private def roadAddressLinkToRoadAddress(floating: Boolean)(l: RoadAddressLink) = {
    RoadAddress(l.id, l.roadNumber, l.roadPartNumber, Track.apply(l.trackCode.toInt), Discontinuity.apply(l.discontinuity.toInt),
      l.startAddressM, l.endAddressM, Option(new DateTime(new Date())), None, None, 0, l.linkId, l.startMValue, l.endMValue, l.sideCode,
      (l.startCalibrationPoint, l.endCalibrationPoint), floating, l.geometry)
  }
}

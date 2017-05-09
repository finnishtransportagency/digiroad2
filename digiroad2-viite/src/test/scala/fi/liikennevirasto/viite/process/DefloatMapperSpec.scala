package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.RoadLinkType.NormalRoadLinkType
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset.{ConstructionType, LinkType, SideCode, State}
import fi.liikennevirasto.viite.RoadType
import fi.liikennevirasto.viite.dao.CalibrationPoint
import fi.liikennevirasto.viite.model.{Anomaly, RoadAddressLink}
import org.scalatest.{FunSuite, Matchers}

class DefloatMapperSpec extends FunSuite with Matchers{
  test("test create mapping") {
    val sources = Seq(
      createRoadAddressLink(193080L, 1021200L, Seq(Point(653051.929,6927199.581,105.14400000000023), Point(653031.198,6927221.85,105.55199999999604), Point(653011.425,6927242.865,105.92900000000373), Point(652995.475256991,6927260.328718615,106.23299510185589)), 4846L, 1L, 0, 4035, 4118, SideCode.AgainstDigitizing, Anomaly.None),
      createRoadAddressLink(233578L, 1021217L, Seq(Point(652995.475,6927260.329,106.2329999999929), Point(652988.767,6927267.695,106.42500000000291), Point(652983.363,6927272.531,106.53200000000652)), 4846L, 1L, 0, 4018, 4035, SideCode.AgainstDigitizing, Anomaly.None)
    )
    val targets = Seq(
      createRoadAddressLink(0L, 500073990L, Seq(Point(653003.293,6927251.369,106.06299999999464), Point(652993.291,6927263.081,106.29799999999523)), 0, 0, 99, 0, 0, SideCode.AgainstDigitizing, Anomaly.NoAddressGiven),
      createRoadAddressLink(0L, 500073981L, Seq(Point(653051.929,6927199.581,105.14400000000023), Point(653031.198,6927221.85,105.55199999999604), Point(653011.425,6927242.865,105.92900000000373), Point(653003.293,6927251.369,106.06299999999464)), 0, 0, 99, 0, 0, SideCode.AgainstDigitizing, Anomaly.NoAddressGiven),
      createRoadAddressLink(0L, 500073988L, Seq(Point(652993.291,6927263.081,106.29799999999523), Point(652988.767,6927267.695,106.42500000000291), Point(652983.363,6927272.531,106.53200000000652)), 0, 0, 99, 0, 0, SideCode.AgainstDigitizing, Anomaly.NoAddressGiven)
    )
    val mapping = DefloatMapper.createAddressMap(sources, targets)
    sources.forall(s => mapping.exists(_.sourceLinkId == s.linkId)) should be (true)
    targets.forall(t => mapping.exists(_.targetLinkId == t.linkId)) should be (true)
    mapping.forall(ram => ram.sourceStartM == Double.NaN) should be (false)
    mapping.forall(ram => ram.targetStartM == Double.NaN) should be (false)
    mapping.forall(ram => ram.sourceEndM == Double.NaN) should be (false)
    mapping.forall(ram => ram.targetEndM == Double.NaN) should be (false)
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

}

package fi.liikennevirasto.viite.model

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.service.RoadLinkType.NormalRoadLinkType
import fi.liikennevirasto.digiroad2.asset.ConstructionType.InUse
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.service.RoadLinkType
import fi.liikennevirasto.viite.RoadType.PublicRoad
// Used in debugging when needed.
import org.scalatest.{FunSuite, Matchers}

/**
  * Created by venholat on 21.10.2016.
  */
class RoadAddressLinkPartitionerSpec extends FunSuite with Matchers {
  lazy val roadAddressLinks = Seq(
    makeRoadAddressLink(1, 0, 1, 1),
    makeRoadAddressLink(2, 0, 1, 1),
    makeRoadAddressLink(3, 0, 1, 1),
    makeRoadAddressLink(4, 0, 1, 1),
    makeRoadAddressLink(5, 0, 1, 1),
    makeRoadAddressLink(6, 0, 1, 1),
    makeRoadAddressLink(7, 0, 1, 1),
    makeRoadAddressLink(11, 0, 1, 2),
    makeRoadAddressLink(12, 0, 1, 2),
    makeRoadAddressLink(13, 0, 1, 2),
    makeRoadAddressLink(14, 0, 1, 2),
    makeRoadAddressLink(15, 0, 1, 2),
    makeRoadAddressLink(16, 0, 1, 2),
    makeRoadAddressLink(17, 0, 1, 2),
    makeRoadAddressLink(0, 1, 0, 0, 1.0, 1.0),
    makeRoadAddressLink(0, 1, 0, 0, 11.0, 1.0),
    makeRoadAddressLink(0, 1, 0, 0, 21.0, 1.0),
    makeRoadAddressLink(0, 1, 0, 0, 31.0, 1.0),
    makeRoadAddressLink(0, 1, 0, 0, 41.0, 1.0),
    makeRoadAddressLink(0, 1, 0, 0, 1.0, 11.0),
    makeRoadAddressLink(0, 1, 0, 0, 11.0, 11.0),
    makeRoadAddressLink(0, 1, 0, 0, 21.0, 11.0),
    makeRoadAddressLink(0, 1, 0, 0, 31.0, 11.0),
    makeRoadAddressLink(0, 1, 0, 0, 41.0, 11.0)
  )

  private def makeRoadAddressLink(id: Long, anomaly: Int, roadNumber: Long, roadPartNumber: Long, deltaX: Double = 0.0, deltaY: Double = 0.0) = {
    RoadAddressLink(id, id, Seq(Point(id*10.0 + deltaX, anomaly*10.0 + deltaY), Point((id+1)*10.0 + deltaX, anomaly*10.0 + deltaY)), 10.0, State, SingleCarriageway, NormalRoadLinkType, InUse, NormalLinkInterface, PublicRoad, "Vt5", BigInt(0),None, None, Map(), roadNumber, roadPartNumber, 1, 1, 1, id*10, (id+1)*10, "", "", 0.0, 10.0,SideCode.TowardsDigitizing, None, None, Anomaly.apply(anomaly), 0)
  }

  test("Partitions don't have differing anomalies") {
    val partitioned = RoadAddressLinkPartitioner.partition(roadAddressLinks)
    partitioned.size should be (4)
    partitioned.flatten.size should be (roadAddressLinks.size)
  }

  test("There should be separate anomalous roads") {
    val partitioned = RoadAddressLinkPartitioner.partition(roadAddressLinks)
    partitioned.count(l => l.exists(_.anomaly.value > 0)) should be (2)
  }

  test("Connected anomalous roads are combined") {
    val add = makeRoadAddressLink(0, 1, 0, 0, 1.0, 11.0)
    val mod = add.copy(geometry = Seq(Point(11.0,11.0), Point(11.0,21.0)))
    val partitioned = RoadAddressLinkPartitioner.partition(roadAddressLinks ++ Seq(mod))
    val anomalous = partitioned.filter(l => l.exists(_.anomaly.value > 0))
    anomalous.size should be (1)
  }

  test("Connected anomalous + other roads are not combined") {
    val add = makeRoadAddressLink(0, 1, 0, 0, 1.0, 11.0)
    val mod = add.copy(geometry = Seq(Point(10.0,21.0), Point(11.0,21.0)))
    val partitioned = RoadAddressLinkPartitioner.partition(roadAddressLinks ++ Seq(mod))
    partitioned.size should be (4)
  }

  test("Connected anomalous type NoAddressGiven & GeometryChanged roads are not combined") {
    val add = makeRoadAddressLink(0, Anomaly.GeometryChanged.value, 0, 0, 1.0, 11.0)
    val mod = add.copy(geometry = Seq(Point(10.0,21.0), Point(11.0,21.0)))
    val partitioned = RoadAddressLinkPartitioner.partition(roadAddressLinks ++ Seq(mod))
    partitioned.size should be (5)
  }

  test("Connected anomalous type GeometryChanged and floating roads are combined") {
    val add = makeRoadAddressLink(0, Anomaly.GeometryChanged.value, 0, 0, 1.0, 11.0)
    val mod = add.copy(geometry = Seq(Point(10.0,21.0), Point(11.0,21.0)),
      roadNumber = 2, roadPartNumber=1, trackCode = 0, roadLinkType = RoadLinkType.FloatingRoadLinkType)
    val add2 = makeRoadAddressLink(1536, Anomaly.None.value, 0, 0, 1.0, 11.0)
    val mod2 = add2.copy(geometry = Seq(Point(11.0,21.0), Point(11.0,22.0)),
      newGeometry = Option(Seq(Point(11.0,21.0), Point(11.0,25.0))),
      roadLinkSource = LinkGeomSource.HistoryLinkInterface, roadLinkType = RoadLinkType.FloatingRoadLinkType,
      startAddressM = 10, endAddressM = 20, roadNumber = 2, roadPartNumber=1, trackCode = 0)
    val partitioned = RoadAddressLinkPartitioner.partition(roadAddressLinks ++ Seq(mod, mod2))
    val group = partitioned.find(_.exists(r => r.roadNumber == 2))
    partitioned.size should be (5)
    group.nonEmpty should be (true)
    group.get.size should be (2)
  }

  test("Connected floating + other roads are not combined") {
    val add = Seq(makeRoadAddressLink(18, 0, 1, 2),makeRoadAddressLink(19, 0, 1, 2))
    val mod2 = add.map(_.copy(roadLinkType = RoadLinkType.FloatingRoadLinkType))
    val partitioned = RoadAddressLinkPartitioner.partition(roadAddressLinks.filter(_.id>0).map(ral => {
      if (ral.id % 2 == 0)
        ral.copy(roadLinkType = RoadLinkType.ComplementaryRoadLinkType)
      else
        ral
    }) ++ mod2)
    partitioned.size should be (3) // Floating road address links must be a group of their own

    partitioned.exists(_.exists(_.roadLinkType == RoadLinkType.ComplementaryRoadLinkType)) should be (true)
    partitioned.exists(_.exists(_.roadLinkType == RoadLinkType.NormalRoadLinkType)) should be (true)
    partitioned.exists(_.exists(_.roadLinkType == RoadLinkType.FloatingRoadLinkType)) should be (true)
  }
}

package fi.liikennevirasto.viite.model

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.RoadLinkType.NormalRoadLinkType
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.viite.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.viite.RoadType.PublicRoad
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
    RoadAddressLink(id, id, Seq(Point(id*10.0 + deltaX, anomaly*10.0 + deltaY), Point((id+1)*10.0 + deltaX, anomaly*10.0 + deltaY)), 10.0, State, SingleCarriageway, NormalRoadLinkType, NormalLinkInterface, PublicRoad, None, None, Map(), roadNumber, roadPartNumber, 1, 1, 1, id*10, (id+1)*10, "", "", 0.0, 10.0,SideCode.TowardsDigitizing, None, None, Anomaly.apply(anomaly))
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
}

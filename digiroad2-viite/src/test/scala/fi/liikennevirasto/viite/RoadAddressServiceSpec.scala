package fi.liikennevirasto.viite

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.VVHSerializer
import fi.liikennevirasto.viite.dao.CalibrationPoint
import fi.liikennevirasto.viite.model.{RoadAddressLink, RoadAddressLinkPartitioner}
import org.scalatest.{FunSuite, Matchers}

/**
  * Created by venholat on 12.9.2016.
  */
class RoadAddressServiceSpec extends FunSuite with Matchers{

  class TestService(rdService: RoadAddressService, eventBus: DigiroadEventBus = new DummyEventBus, vvhSerializer: VVHSerializer = new DummySerializer) {
  }

  private def calibrationPoint(geometry: Seq[Point], calibrationPoint: Option[CalibrationPoint]) = {
    calibrationPoint match {
      case Some(point) =>
        val mValue = point.segmentMValue match {
          case 0.0 => 0.0
          case _ => Math.min(point.segmentMValue, GeometryUtils.geometryLength(geometry))
        }
        Option(Seq(("point", GeometryUtils.calculatePointFromLinearReference(geometry, mValue)), ("value", point.addressMValue)).toMap)
      case _ => None
    }
  }

  def roadAddressLinkToApi(roadLink: RoadAddressLink): Map[String, Any] = {
    Map(
      "segmentId" -> roadLink.id,
      "linkId" -> roadLink.linkId,
      "mmlId" -> roadLink.attributes.get("MTKID"),
      "points" -> roadLink.geometry,
      "calibrationPoints" -> Seq(calibrationPoint(roadLink.geometry, roadLink.startCalibrationPoint),
        calibrationPoint(roadLink.geometry, roadLink.endCalibrationPoint)),
      "administrativeClass" -> roadLink.administrativeClass.toString,
      "linkType" -> roadLink.linkType.value,
      "functionalClass" -> roadLink.functionalClass,
      "trafficDirection" -> roadLink.trafficDirection.toString,
      "modifiedAt" -> roadLink.modifiedAt,
      "modifiedBy" -> roadLink.modifiedBy,
      "municipalityCode" -> roadLink.attributes.get("MUNICIPALITYCODE"),
      "verticalLevel" -> roadLink.attributes.get("VERTICALLEVEL"),
      "roadNameFi" -> roadLink.attributes.get("ROADNAME_FI"),
      "roadNameSe" -> roadLink.attributes.get("ROADNAME_SE"),
      "roadNameSm" -> roadLink.attributes.get("ROADNAME_SM"),
      "minAddressNumberRight" -> roadLink.attributes.get("FROM_RIGHT"),
      "maxAddressNumberRight" -> roadLink.attributes.get("TO_RIGHT"),
      "minAddressNumberLeft" -> roadLink.attributes.get("FROM_LEFT"),
      "maxAddressNumberLeft" -> roadLink.attributes.get("TO_LEFT"),
      "roadNumber" -> roadLink.roadNumber,
      "roadPartNumber" -> roadLink.roadPartNumber,
      "elyCode" -> roadLink.elyCode,
      "trackCode" -> roadLink.trackCode,
      "startAddressM" -> roadLink.startAddressM,
      "endAddressM" -> roadLink.endAddressM,
      "discontinuity" -> roadLink.discontinuity,
      "endDate" -> roadLink.endDate)
  }

  test("testGetCalibrationPoints") {
    //TODO
  }

  test("testRoadClass") {
    //TODO
  }

  test("test getRoadLinkFromVVH should have specific fields (still to be defined) not empty"){

    OracleDatabase.withDynTransaction {

      val roadLinks = Seq(RoadAddressLink(0,5171208,Seq(Point(532837.14110884,6993543.6296834,0.0),Point(533388.14110884,6994014.1296834,0.0)),0.0,Municipality,0,TrafficDirection.UnknownDirection,UnknownLinkType,None,None,Map("linkId" ->5171208, "segmentId" -> 63298 ),5,205,1,0,0,0,1,"2016-01-01",0.0,0.0,SideCode.Unknown,None,None))
      val partitionedRoadLinks = RoadAddressLinkPartitioner.partition(roadLinks)
      partitionedRoadLinks.map {
        _.map(roadAddressLinkToApi)
      }
      val roadPartNumber = partitionedRoadLinks.head.head.roadPartNumber
      val roadNumber = partitionedRoadLinks.head.head.roadNumber
      val trackCode = partitionedRoadLinks.head.head.trackCode
      val segmentId = partitionedRoadLinks.head.head.id

      segmentId should not be None
      roadNumber should be (5);
      roadPartNumber should be (205);
      trackCode should be (1);
    }
  }

}

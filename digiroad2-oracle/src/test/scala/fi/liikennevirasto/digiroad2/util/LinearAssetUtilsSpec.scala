package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode, TrafficDirection}
import fi.liikennevirasto.digiroad2.client.vvh.ChangeInfo
import fi.liikennevirasto.digiroad2.linearasset.{NumericValue, SpeedLimit}
import org.scalatest.{FunSuite, Matchers}

/**
  * Created by venholat on 30.3.2016.
  */
class LinearAssetUtilsSpec extends FunSuite with Matchers {

  test("testNewChangeInfoDetected") {
    // vvh time stamp is older
    val speedlimit = SpeedLimit(1, 1, SideCode.BothDirections,TrafficDirection.BothDirections,Some(NumericValue(40)),
      Seq(Point(0.0,0.0),Point(1.0,0.0)), 0.0, 1.0, None, None, None, None, 14000000, None, linkSource = NormalLinkInterface)
    // vvh time stamp is the same
    val speedlimit2 = SpeedLimit(1, 1, SideCode.BothDirections,TrafficDirection.BothDirections,Some(NumericValue(50)),
      Seq(Point(0.0,0.0),Point(1.0,0.0)), 0.0, 1.0, None, None, None, None, 15000000, None, linkSource = NormalLinkInterface)
    // no change info for this link
    val speedlimit3 = SpeedLimit(1, 2, SideCode.BothDirections,TrafficDirection.BothDirections,Some(NumericValue(60)),
      Seq(Point(0.0,0.0),Point(1.0,0.0)), 0.0, 1.0, None, None, None, None, 14000000, None, linkSource = NormalLinkInterface)
    val changeinfo = Seq(ChangeInfo(Some(1), Some(1), 1, 9, Some(0), Some(1), Some(1), Some(0), 15000000))
    LinearAssetUtils.newChangeInfoDetected(speedlimit, changeinfo) should be (true)
    LinearAssetUtils.newChangeInfoDetected(speedlimit2, changeinfo) should be (false)
    LinearAssetUtils.newChangeInfoDetected(speedlimit3, changeinfo) should be (false)
  }
}

package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.lane.{LaneEndPoints, LaneRoadAddressInfo}
import fi.liikennevirasto.digiroad2.service.RoadAddressForLink
import org.scalatest.{FunSuite, Matchers}

class LaneUtilsSpec extends FunSuite with Matchers {
  val addLaneToAddress1: LaneRoadAddressInfo = LaneRoadAddressInfo(1, 1, 10, 1, 120, 1)
  val addLaneToAddress2: LaneRoadAddressInfo = LaneRoadAddressInfo(1, 1, 10, 4, 120, 1)
  val addLaneToAddress3: LaneRoadAddressInfo = LaneRoadAddressInfo(1, 2, 10, 2, 120, 1)

  test("Return None for link with road addresses outside of scope") {
    val linkLength = 50.214
    val linkId = 1234567
    val addressesOnLink1 = Set( // At same road part but start after selection ends
      RoadAddressForLink(0, 1, 1, Track.RightSide, 120, 320, None, None, linkId, 0.0, 49.281, SideCode.Unknown, Seq(), false, None, None, None)
    )
    val addressesOnLink2 = Set( // At same road part but ends before selection starts
      RoadAddressForLink(0, 1, 1, Track.RightSide, 0, 10, None, None, linkId, 0.0, 49.281, SideCode.Unknown, Seq(), false, None, None, None)
    )
    val addressesOnLink3 = Set( // road part > selection.endRoadPart
      RoadAddressForLink(0, 1, 2, Track.RightSide, 10, 120, None, None, linkId, 0.0, 49.281, SideCode.Unknown, Seq(), false, None, None, None)
    )
    val addressesOnLink4 = Set( // road part < selection.startRoadPart
      RoadAddressForLink(0, 1, 1, Track.RightSide, 10, 120, None, None, linkId, 0.0, 49.281, SideCode.Unknown, Seq(), false, None, None, None)
    )
    val addressesOnLink5 = Set( // multiple addresses on link and all outside scope
      RoadAddressForLink(0, 1, 1, Track.RightSide, 120, 220, None, None, linkId, 0.0, 49.281, SideCode.Unknown, Seq(), false, None, None, None),
      RoadAddressForLink(0, 1, 1, Track.RightSide, 220, 320, None, None, linkId, 0.0, 49.281, SideCode.Unknown, Seq(), false, None, None, None)
    )

    val endPoints1 = LaneUtils.calculateStartAndEndPoint(addLaneToAddress1, addressesOnLink1, linkLength)
    val endPoints2 = LaneUtils.calculateStartAndEndPoint(addLaneToAddress1, addressesOnLink2, linkLength)
    val endPoints3 = LaneUtils.calculateStartAndEndPoint(addLaneToAddress1, addressesOnLink3, linkLength)
    val endPoints4 = LaneUtils.calculateStartAndEndPoint(addLaneToAddress3, addressesOnLink4, linkLength)
    val endPoints5 = LaneUtils.calculateStartAndEndPoint(addLaneToAddress1, addressesOnLink5, linkLength)

    endPoints1.isEmpty should be(true)
    endPoints2.isEmpty should be(true)
    endPoints3.isEmpty should be(true)
    endPoints4.isEmpty should be(true)
    endPoints5.isEmpty should be(true)
  }

  test("Return start and end point for link with one road address") {
    val linkId = 1234567
    val linkLength = 50.214
    val addressesOnLink1 = Set( // road part == selection.startRoadPart && road part == selection.endRoadPart
      RoadAddressForLink(0, 1, 1, Track.RightSide, 10, 120, None, None, linkId, 0.0, 49.281, SideCode.Unknown, Seq(), false, None, None, None)

    )
    val addressesOnLink2 = Set( // road part > selection.startRoadPart && road part < selection.endRoadPart
      RoadAddressForLink(0, 1, 3, Track.RightSide, 10, 120, None, None, linkId, 0.0, 49.281, SideCode.Unknown, Seq(), false, None, None, None)
    )

    val endPoints1 = LaneUtils.calculateStartAndEndPoint(addLaneToAddress1, addressesOnLink1, linkLength)
    val endPoints2 = LaneUtils.calculateStartAndEndPoint(addLaneToAddress2, addressesOnLink2, linkLength)

    endPoints1.isEmpty should be(false)
    endPoints2.isEmpty should be(false)

    endPoints1.get should be(LaneEndPoints(0.0, 50.214))
    endPoints2.get should be(LaneEndPoints(0.0, 50.214))
  }

  test("Selection ends before address ends") {
    val linkId = 1234567
    val linkLength = 50.214
    val addressesOnLink1a = Set( // TowardsDigitizing
      RoadAddressForLink(0, 1, 1, Track.RightSide, 10, 130, None, None, linkId, 0.0, 49.281, SideCode.TowardsDigitizing, Seq(), false, None, None, None))
    val addressesOnLink1b = Set( // AgainstDigitizing
      RoadAddressForLink(0, 1, 1, Track.RightSide, 10, 130, None, None, linkId, 0.0, 49.281, SideCode.AgainstDigitizing, Seq(), false, None, None, None)
    )

    val endPoints1a = LaneUtils.calculateStartAndEndPoint(addLaneToAddress1, addressesOnLink1a, linkLength)
    val endPoints1b = LaneUtils.calculateStartAndEndPoint(addLaneToAddress1, addressesOnLink1b, linkLength)

    endPoints1a.isEmpty should be(false)
    endPoints1b.isEmpty should be(false)

    endPoints1a.get should be(LaneEndPoints(0.0, 46.03))     // End adjusted
    endPoints1b.get should be(LaneEndPoints(4.185, 50.214))  // Start adjusted
  }

  test("Selection starts after start of address") {
    val linkId = 1234567
    val linkLength = 50.214
    val addressesOnLink1a = Set( // TowardsDigitizing
      RoadAddressForLink(0, 1, 1, Track.RightSide, 0, 120, None, None, linkId, 0.0, 49.281, SideCode.TowardsDigitizing, Seq(), false, None, None, None)
    )
    val addressesOnLink1b = Set( // AgainstDigitizing
      RoadAddressForLink(0, 1, 1, Track.RightSide, 0, 120, None, None, linkId, 0.0, 49.281, SideCode.AgainstDigitizing, Seq(), false, None, None, None)
    )

    val endPoints1a = LaneUtils.calculateStartAndEndPoint(addLaneToAddress1, addressesOnLink1a, linkLength)
    val endPoints1b = LaneUtils.calculateStartAndEndPoint(addLaneToAddress1, addressesOnLink1b, linkLength)

    endPoints1a.isEmpty should be(false)
    endPoints1b.isEmpty should be(false)

    endPoints1a.get should be(LaneEndPoints(4.185, 50.214)) // Start adjusted
    endPoints1b.get should be(LaneEndPoints(0.0, 46.03))    // End adjusted
  }

  test("Multiple addresses on link") {
    val linkId = 1234567
    val linkLength = 150.214
    val addressesOnLink1a = Set( // TowardsDigitizing
      RoadAddressForLink(0, 1, 1, Track.RightSide, 10, 50, None, None, linkId, 0.0, 49.281, SideCode.TowardsDigitizing, Seq(), false, None, None, None),
      RoadAddressForLink(0, 1, 1, Track.RightSide, 50, 120, None, None, linkId, 49.281, 182.984, SideCode.TowardsDigitizing, Seq(), false, None, None, None)
    )
    val addressesOnLink1b = Set( // AgainstDigitizing
      RoadAddressForLink(0, 1, 1, Track.RightSide, 10, 50, None, None, linkId, 49.281, 182.984, SideCode.AgainstDigitizing, Seq(), false, None, None, None),
      RoadAddressForLink(0, 1, 1, Track.RightSide, 50, 120, None, None, linkId, 0.0, 49.281, SideCode.AgainstDigitizing, Seq(), false, None, None, None)
    )
    val addressesOnLink2 = Set( // Road part changes during link
      RoadAddressForLink(0, 1, 1, Track.RightSide, 10, 50, None, None, linkId, 0.0, 49.281, SideCode.Unknown, Seq(), false, None, None, None),
      RoadAddressForLink(0, 1, 2, Track.RightSide, 0, 70, None, None, linkId, 49.281, 182.984, SideCode.Unknown, Seq(), false, None, None, None)
    )

    val endPoints1a = LaneUtils.calculateStartAndEndPoint(addLaneToAddress1, addressesOnLink1a, linkLength)
    val endPoints1b = LaneUtils.calculateStartAndEndPoint(addLaneToAddress1, addressesOnLink1b, linkLength)
    val endPoints2 = LaneUtils.calculateStartAndEndPoint(addLaneToAddress2, addressesOnLink2, linkLength)

    endPoints1a.isEmpty should be(false)
    endPoints1b.isEmpty should be(false)
    endPoints2.isEmpty should be(false)

    endPoints1a.get should be(LaneEndPoints(0.0, 150.214))
    endPoints1b.get should be(LaneEndPoints(0.0, 150.214))
    endPoints2.get should be(LaneEndPoints(0.0, 150.214))
  }
}

package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode}
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.RoadType.PublicRoad
import fi.liikennevirasto.viite.dao.LinkStatus.{Terminated, Transfer}
import fi.liikennevirasto.viite.dao.{Discontinuity, ProjectLink}
import org.scalatest.{FunSuite, Matchers}

class RoadAddressSplitMapperSpec extends FunSuite with Matchers {
  test("Split mapping, simple case") {
    val template = ProjectLink(1L, 1L, 1L, Track.Combined, Discontinuity.Continuous, 15L, 19L, None, None, None, 0L,
      123L, 15.0, 18.9, SideCode.TowardsDigitizing, (None, None), false, Seq(Point(0.0, 15.0), Point(0.0, 18.9)), 1L,
      Terminated, PublicRoad, LinkGeomSource.NormalLinkInterface, 3.9, 1L, 1L, false, Some(456L), 8750L)
    val suravage = ProjectLink(2L, 1L, 1L, Track.Combined, Discontinuity.Continuous, 0L, 15L, None, None, None, 0L,
      456L, 0.0, 15.0, SideCode.TowardsDigitizing, (None, None), false, Seq(Point(0.0, 0.0), Point(0.0, 15.0)), 1L,
      Transfer, PublicRoad, LinkGeomSource.SuravageLinkInterface, 15.0, 1L, 1L, false, Some(123L), 8750L)
    val map = RoadAddressSplitMapper.createAddressMap(Seq(template, suravage))
    map should have size (2)
    map.exists(m => m.sourceStartM == 0.0 && m.targetLinkId == 456L) should be (true)
    map.exists(_.sourceStartM == 15.0) should be (true)
    map.forall(m => m.sourceStartM == m.targetStartM && m.sourceEndM == m.targetEndM && m.sourceLinkId == 123L) should be (true)
  }

  test("Split mapping, reverse case") {
    val template = ProjectLink(1L, 1L, 1L, Track.Combined, Discontinuity.Continuous, 3L, 19L, None, None, None, 0L,
      123L, 0.0, 15.0, SideCode.AgainstDigitizing, (None, None), false, Seq(Point(0.0, 0.0), Point(0.0, 15.0)), 1L,
      Terminated, PublicRoad, LinkGeomSource.NormalLinkInterface, 15.0, 1L, 1L, false, Some(456L), 8750L)
    val suravage = ProjectLink(2L, 1L, 1L, Track.Combined, Discontinuity.Continuous, 0L, 3L, None, None, None, 0L,
      456L, 15.0, 18.9, SideCode.AgainstDigitizing, (None, None), false, Seq(Point(0.0, 15.0), Point(0.0, 18.9)), 1L,
      Transfer, PublicRoad, LinkGeomSource.SuravageLinkInterface, 3.9, 1L, 1L, false, Some(123L), 8750L)
    val map = RoadAddressSplitMapper.createAddressMap(Seq(template, suravage))
    map should have size (2)
    map.exists(m => m.sourceStartM == 15.0 && m.targetLinkId == 456L) should be (true)
    map.exists(m => m.sourceStartM == 0.0 && m.targetLinkId == 123L) should be (true)
    map.forall(m => m.sourceStartM == m.targetStartM && m.sourceEndM == m.targetEndM && m.sourceLinkId == 123L) should be (true)
  }

  test("Split mapping, changing digitization direction case") {
    val template = ProjectLink(1L, 1L, 1L, Track.Combined, Discontinuity.Continuous, 100L, 115L, None, None, None, 0L,
      123L, 0.0, 15.0, SideCode.TowardsDigitizing, (None, None), false, Seq(Point(0.0, 0.0), Point(0.0, 15.0)), 1L,
      Terminated, PublicRoad, LinkGeomSource.NormalLinkInterface, 15.0, 1L, 1L, false, Some(456L), 8750L)
    val suravage = ProjectLink(2L, 1L, 1L, Track.Combined, Discontinuity.Continuous, 115L, 119L, None, None, None, 0L,
      456L, 0.0, 3.9, SideCode.AgainstDigitizing, (None, None), false, Seq(Point(0.0, 18.9), Point(0.0, 15.0)), 1L,
      Transfer, PublicRoad, LinkGeomSource.SuravageLinkInterface, 3.9, 1L, 1L, true, Some(123L), 8750L)
    val newPart = ProjectLink(3L, 1L, 1L, Track.Combined, Discontinuity.Continuous, 105L, 115L, None, None, None, 0L,
      456L, 3.9, 13.9, SideCode.AgainstDigitizing, (None, None), false, Seq(Point(0.0, 15.0), Point(5.0, 15.0), Point(5.0, 20.0)), 1L,
      Transfer, PublicRoad, LinkGeomSource.SuravageLinkInterface, 10.0, 1L, 1L, false, Some(123L), 8750L)
    val map = RoadAddressSplitMapper.createAddressMap(Seq(template, suravage, newPart))
    map should have size (2)
    map.exists(m => m.sourceStartM == 15.0 && m.targetLinkId == 456L && m.targetEndM == 0.0) should be (true)
    map.exists(m => m.sourceStartM == 0.0 && m.targetLinkId == 123L) should be (true)
  }
}

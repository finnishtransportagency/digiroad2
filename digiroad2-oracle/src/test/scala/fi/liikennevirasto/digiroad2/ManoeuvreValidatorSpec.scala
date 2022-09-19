package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.process._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignService
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.dao.linearasset.manoeuvre.ManoeuvreDao
import fi.liikennevirasto.digiroad2.linearasset.ValidityPeriodDayOfWeek.Saturday
import fi.liikennevirasto.digiroad2.service.linearasset.{ElementTypes, Manoeuvre, ManoeuvreElement}
import fi.liikennevirasto.digiroad2.util.LinkIdGenerator
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class ManoeuvreValidatorSpec  extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
  val mockTrafficSignService = MockitoSugar.mock[TrafficSignService]
  val mockAssetDao: ManoeuvreDao = MockitoSugar.mock[ManoeuvreDao]

  class TestManoeuvreValidator extends ManoeuvreValidator {
    override lazy val manoeuvreDao: ManoeuvreDao = mockAssetDao
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val roadLinkClient: RoadLinkClient = mockRoadLinkClient
  }

  val linkId1 = LinkIdGenerator.generateRandom()
  val linkId2 = LinkIdGenerator.generateRandom()
  val linkId3 = LinkIdGenerator.generateRandom()
  val linkId4 = LinkIdGenerator.generateRandom()
  val linkId5 = LinkIdGenerator.generateRandom()

  val manoeuvreValidator = new TestManoeuvreValidator

  test("restriction sign without a match asset") {
    PostGISDatabase.withDynTransaction {
      val roadLink1 = RoadLink(linkId1, Seq(Point(0.0, 10.0), Point(10, 10.0)), 5, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(linkId2, Seq(Point(10.0, 10.0), Point(10, 5.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(linkId3, Seq(Point(10.0, 0.0), Point(10.0, 5.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val propNoLeftTurn = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(NoLeftTurn.OTHvalue.toString))))
      val trafficSign = PersistedTrafficSign(1, linkId3, 10, 2, 2, false, 0, 235, propNoLeftTurn, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

      when(mockTrafficSignService.getTrafficSign(Seq(linkId3))).thenReturn(Seq(trafficSign))
      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(linkId1, linkId2))).thenReturn(Seq(roadLink1, roadLink2))
      when(mockRoadLinkService.getRoadLinksWithComplementaryByBoundsAndMunicipalities(any[BoundingRectangle], any[Set[Int]], any[Boolean],any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3))
      when(mockRoadLinkService.pickForwardMost(roadLink2, Seq(roadLink3))).thenReturn(roadLink3)

      when(manoeuvreValidator.manoeuvreDao.getByRoadLinks(Seq(roadLink1,roadLink2, roadLink3).map(_.linkId))).thenReturn(Seq())
      val result = manoeuvreValidator.assetValidator(trafficSign)
      result should have size 1
      result.head.linkId should be (Some(roadLink3.linkId))

      dynamicSession.rollback()
    }
  }

  test("left turn sign restriction with a match asset") {
    PostGISDatabase.withDynTransaction {
      val roadLink1 = RoadLink(linkId1, Seq(Point(10.0, 0.0), Point(10, 5.0)), 5, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(linkId2, Seq(Point(10.0, 5.0), Point(10, 10.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(linkId3, Seq(Point(10.0, 10.0), Point(10.0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink4 = RoadLink(linkId4, Seq(Point(0.0, 20.0), Point(10.0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val start = ManoeuvreElement(1, linkId3, linkId4, ElementTypes.FirstElement)
      val end = ManoeuvreElement(1, linkId4, "0", ElementTypes.LastElement)

      val manoeuvre =  Manoeuvre(1l, Seq(start, end), Set(ValidityPeriod(0, 21, Saturday, 30, 45)), Nil, None, None, "", DateTime.now(), "", false)

      val propNoLeftTurn = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(NoLeftTurn.OTHvalue.toString))))
      val trafficSign = PersistedTrafficSign(1, linkId1, 10, 2, 2, false, 0, 235, propNoLeftTurn, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(linkId3, linkId4))).thenReturn(Seq(roadLink3, roadLink4))
      when(mockRoadLinkService.getRoadLinksWithComplementaryByBoundsAndMunicipalities(any[BoundingRectangle], any[Set[Int]], any[Boolean],any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3, roadLink4))
      when(manoeuvreValidator.manoeuvreDao.getByRoadLinks(any[Seq[String]])).thenReturn(Seq(manoeuvre))

      val result = manoeuvreValidator.assetValidator(trafficSign)
      result should have size 0

      dynamicSession.rollback()
    }
  }

  test("turn right sign restriction with a match asset") {
    PostGISDatabase.withDynTransaction {
      val roadLink1 = RoadLink(linkId1, Seq(Point(10.0, .0), Point(10, 5.0)), 5, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(linkId2, Seq(Point(10.0, 5.0), Point(10, 10.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(linkId3, Seq(Point(10.0, 10.0), Point(10.0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink4 = RoadLink(linkId4, Seq(Point(20.0, 20.0), Point(10.0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val start = ManoeuvreElement(1, linkId3, linkId4, ElementTypes.FirstElement)
      val end = ManoeuvreElement(1, linkId4, "0", ElementTypes.LastElement)

      val manoeuvre =  Manoeuvre(1l, Seq(start, end), Set(ValidityPeriod(0, 21, Saturday, 30, 45)), Nil, None, None, "", DateTime.now(), "", false)

      val propNoRightTurn = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(NoRightTurn.OTHvalue.toString))))
      val trafficSign = PersistedTrafficSign(1, linkId1, 2, 2, 2, false, 0, 235, propNoRightTurn, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(linkId3, linkId4))).thenReturn(Seq(roadLink3, roadLink4))
      when(mockRoadLinkService.getRoadLinksWithComplementaryByBoundsAndMunicipalities(any[BoundingRectangle], any[Set[Int]], any[Boolean],any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3, roadLink4))
      when(manoeuvreValidator.manoeuvreDao.getByRoadLinks(any[Seq[String]])).thenReturn(Seq(manoeuvre))

      val result = manoeuvreValidator.assetValidator(trafficSign)
      result should have size 0

      dynamicSession.rollback()
    }
  }

  test("turn U sign restriction with a match asset") {
    PostGISDatabase.withDynTransaction {
      val roadLink1 = RoadLink(linkId1, Seq(Point(10.0, .0), Point(10, 5.0)), 5, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(linkId2, Seq(Point(10.0, 5.0), Point(10, 10.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(linkId3, Seq(Point(10.0, 10.0), Point(10.0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink4 = RoadLink(linkId4, Seq(Point(5.0, 20.0), Point(10.0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink5 = RoadLink(linkId5, Seq(Point(5.0, 10.0), Point(5.0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val start = ManoeuvreElement(1, linkId3, linkId4, ElementTypes.FirstElement)
      val intermediate = ManoeuvreElement(1, linkId4, linkId5, ElementTypes.IntermediateElement)
      val end = ManoeuvreElement(1, linkId5, "0", ElementTypes.LastElement)

      val manoeuvre =  Manoeuvre(1l, Seq(start, intermediate, end), Set(ValidityPeriod(0, 21, Saturday, 30, 45)), Nil, None, None, "", DateTime.now(), "", false)

      val propNoUTurn = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(NoUTurn.OTHvalue.toString))))
      val trafficSign = PersistedTrafficSign(1, linkId1, 2, 2, 2, false, 0, 235, propNoUTurn, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(linkId3, linkId4, linkId5))).thenReturn(Seq(roadLink3, roadLink4, roadLink5))
      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(linkId3, linkId4))).thenReturn(Seq(roadLink3, roadLink4))
      when(mockRoadLinkService.getRoadLinksWithComplementaryByBoundsAndMunicipalities(any[BoundingRectangle], any[Set[Int]], any[Boolean],any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3, roadLink4))
      when(manoeuvreValidator.manoeuvreDao.getByRoadLinks(any[Seq[String]])).thenReturn(Seq(manoeuvre))

      val result = manoeuvreValidator.assetValidator(trafficSign)
      result should have size 0

      dynamicSession.rollback()
    }
  }

  test("turn U sign restriction with a left manoeuvre asset") {
    PostGISDatabase.withDynTransaction {
      val roadLink1 = RoadLink(linkId1, Seq(Point(10.0, .0), Point(10, 5.0)), 5, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(linkId2, Seq(Point(10.0, 5.0), Point(10, 10.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(linkId3, Seq(Point(10.0, 10.0), Point(10.0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink4 = RoadLink(linkId4, Seq(Point(5.0, 20.0), Point(10.0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink5 = RoadLink(linkId5, Seq(Point(5.0, 10.0), Point(5.0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val start = ManoeuvreElement(1, linkId3, linkId4, ElementTypes.FirstElement)
      val end = ManoeuvreElement(1, linkId4, "0", ElementTypes.LastElement)

      val manoeuvre =  Manoeuvre(1l, Seq(start, end), Set(ValidityPeriod(0, 21, Saturday, 30, 45)), Nil, None, None, "", DateTime.now(), "", false)

      val propNoUTurn = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(NoUTurn.OTHvalue.toString))))
      val trafficSign = PersistedTrafficSign(1, linkId1, 2, 2, 2, false, 0, 235, propNoUTurn, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(linkId3, linkId4, linkId5))).thenReturn(Seq(roadLink3, roadLink4, roadLink5))
      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(linkId3, linkId4))).thenReturn(Seq(roadLink3, roadLink4))
      when(mockRoadLinkService.getRoadLinksWithComplementaryByBoundsAndMunicipalities(any[BoundingRectangle], any[Set[Int]], any[Boolean],any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3, roadLink4))
      when(manoeuvreValidator.manoeuvreDao.getByRoadLinks(any[Seq[String]])).thenReturn(Seq(manoeuvre))

      val result = manoeuvreValidator.assetValidator(trafficSign)
      result should have size 1
      result.head.linkId should be (Some(linkId3))

      dynamicSession.rollback()
    }
  }

  test("turn right sign restriction with a match asset only after a roadLink intersection") {
    PostGISDatabase.withDynTransaction {
      val roadLink1 = RoadLink(linkId1, Seq(Point(10.0, 0.0), Point(10, 5.0)), 5, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(linkId2, Seq(Point(10.0, 5.0), Point(10, 10.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(linkId3, Seq(Point(10.0, 10.0), Point(10.0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink4 = RoadLink(linkId4, Seq(Point(20.0, 20.0), Point(10.0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink5 = RoadLink(linkId5, Seq(Point(10.0, 5.0), Point(0.0, 5.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val start = ManoeuvreElement(1, linkId3, linkId4, ElementTypes.FirstElement)
      val end = ManoeuvreElement(1, linkId4, "0", ElementTypes.LastElement)

      val manoeuvre =  Manoeuvre(1l, Seq(start, end), Set(ValidityPeriod(0, 21, Saturday, 30, 45)), Nil, None, None, "", DateTime.now(), "", false)

      val propNoRightTurn = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(NoRightTurn.OTHvalue.toString))))
      val trafficSign = PersistedTrafficSign(1, linkId1, 10, 2, 2, false, 0, 235, propNoRightTurn, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(linkId3, linkId4))).thenReturn(Seq(roadLink3, roadLink4))
      when(mockRoadLinkService.getRoadLinksWithComplementaryByBoundsAndMunicipalities(any[BoundingRectangle], any[Set[Int]], any[Boolean],any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3, roadLink4, roadLink5))
      when(manoeuvreValidator.manoeuvreDao.getByRoadLinks(any[Seq[String]])).thenReturn(Seq(manoeuvre))

      when(mockRoadLinkService.pickRightMost(roadLink1, Seq(roadLink2, roadLink5) )).thenReturn(roadLink2)
      when(mockRoadLinkService.pickRightMost(roadLink2, Seq(roadLink3) )).thenReturn(roadLink3)

      val result = manoeuvreValidator.assetValidator(trafficSign)
      result should have size 0

      dynamicSession.rollback()
    }
  }

  test("turn right sign restriction with a match asset after 50 meter") {
    PostGISDatabase.withDynTransaction {
      val roadLink1 = RoadLink(linkId1, Seq(Point(0, 10), Point(10, 10)), 5, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(linkId2, Seq(Point(10, 10), Point(55, 10)), 45.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(linkId3, Seq(Point(55, 10), Point(70, 10)), 15.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink4 = RoadLink(linkId4, Seq(Point(70, 10), Point(80, 0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val start = ManoeuvreElement(1, linkId3, linkId4, ElementTypes.FirstElement)
      val end = ManoeuvreElement(1, linkId4, "0", ElementTypes.LastElement)

      val manoeuvre = Manoeuvre(1l, Seq(start, end), Set(ValidityPeriod(0, 21, Saturday, 30, 45)), Nil, None, None, "", DateTime.now(), "", false)

      val propNoRightTurn = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(NoRightTurn.OTHvalue.toString))))
      val trafficSign = PersistedTrafficSign(1, linkId1, 2, 10, 2, false, 0, 235, propNoRightTurn, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(linkId3, linkId4))).thenReturn(Seq(roadLink3, roadLink4))
      when(mockRoadLinkService.getRoadLinksWithComplementaryByBoundsAndMunicipalities(any[BoundingRectangle], any[Set[Int]], any[Boolean],any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3, roadLink4))
      when(manoeuvreValidator.manoeuvreDao.getByRoadLinks(any[Seq[String]])).thenReturn(Seq(manoeuvre))

      when(mockRoadLinkService.pickRightMost(roadLink1, Seq(roadLink2))).thenReturn(roadLink2)
      when(mockRoadLinkService.pickRightMost(roadLink2, Seq(roadLink3))).thenReturn(roadLink3)

      val result = manoeuvreValidator.assetValidator(trafficSign)
      result should have size 1
      result.head.linkId should be(Some(linkId1))

      dynamicSession.rollback()
    }
  }

}
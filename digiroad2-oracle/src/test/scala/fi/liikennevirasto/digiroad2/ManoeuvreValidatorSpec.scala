package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.process._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.{TrafficSignService, TrafficSignType}
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.dao.linearasset.manoeuvre.ManoeuvreDao
import fi.liikennevirasto.digiroad2.linearasset.ValidityPeriodDayOfWeek.{Saturday, Weekday}
import fi.liikennevirasto.digiroad2.service.linearasset.{ElementTypes, Manoeuvre, ManoeuvreElement}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class ManoeuvreValidatorSpec  extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockTrafficSignService = MockitoSugar.mock[TrafficSignService]
  val mockAssetDao: ManoeuvreDao = MockitoSugar.mock[ManoeuvreDao]

  class TestManoeuvreValidator extends ManoeuvreValidator {
    override lazy val manoeuvreDao: ManoeuvreDao = mockAssetDao
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
  }

  val manoeuvreValidator = new TestManoeuvreValidator

  test("Restriction sign without a match assset") {
    OracleDatabase.withDynTransaction {
      val roadLink1 = RoadLink(1001l, Seq(Point(0.0, .0), Point(10, 10.0)), 5, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(1002l, Seq(Point(0.0, 10.0), Point(10, 10.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(1003l, Seq(Point(10.0, 0.0), Point(10.0, 5.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val propNoLeftTurn = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(TrafficSignType.NoLeftTurn.value.toString))))
      val trafficSign = PersistedTrafficSign(1, 10003l, 2, 2, 2, false, 0, 235, propNoLeftTurn, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

      when(mockTrafficSignService.getTrafficSign(Seq(1003l))).thenReturn(Seq(trafficSign))
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(1001l, 1002l))).thenReturn(Seq(roadLink1, roadLink2))
      when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3))
      when(mockRoadLinkService.pickForwardMost(roadLink2, Seq(roadLink3))).thenReturn(roadLink3)

      when(manoeuvreValidator.manoeuvreDao.getByRoadLinks(Seq(roadLink3.linkId))).thenReturn(Seq())
      val result = manoeuvreValidator.assetValidator(trafficSign)
      result should be (false)

      dynamicSession.rollback()
    }
  }

  test("Left turn sign restriction with a match asset") {
    OracleDatabase.withDynTransaction {
      val roadLink1 = RoadLink(1001l, Seq(Point(10.0, .0), Point(10, 5.0)), 5, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(1002l, Seq(Point(10.0, 5.0), Point(10, 10.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(1003l, Seq(Point(10.0, 10.0), Point(10.0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink4 = RoadLink(1004l, Seq(Point(0.0, 20.0), Point(10.0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val start = ManoeuvreElement(1, 1003l, 1004l, ElementTypes.FirstElement)
      val end = ManoeuvreElement(1, 1004l, 0, ElementTypes.LastElement)

      val manoeuvre =  Manoeuvre(1l, Seq(start, end), Set(ValidityPeriod(0, 21, Saturday, 30, 45)), Nil, None, None, "", DateTime.now(), "" )

      val propNoLeftTurn = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(TrafficSignType.NoLeftTurn.value.toString))))
      val trafficSign = PersistedTrafficSign(1, 1001l, 2, 2, 2, false, 0, 235, propNoLeftTurn, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(1003l, 1004l))).thenReturn(Seq(roadLink3, roadLink4))
      when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3, roadLink4))
      when(manoeuvreValidator.manoeuvreDao.getByRoadLinks(any[Seq[Long]])).thenReturn(Seq(manoeuvre))

      val result = manoeuvreValidator.assetValidator(trafficSign)
      result should be (true)

      dynamicSession.rollback()
    }
  }

  test("turn right sign restriction with a match asset") {
    OracleDatabase.withDynTransaction {
      val roadLink1 = RoadLink(1001l, Seq(Point(10.0, .0), Point(10, 5.0)), 5, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(1002l, Seq(Point(10.0, 5.0), Point(10, 10.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(1003l, Seq(Point(10.0, 10.0), Point(10.0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink4 = RoadLink(1004l, Seq(Point(20.0, 20.0), Point(10.0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val start = ManoeuvreElement(1, 1003l, 1004l, ElementTypes.FirstElement)
      val end = ManoeuvreElement(1, 1004l, 0, ElementTypes.LastElement)

      val manoeuvre =  Manoeuvre(1l, Seq(start, end), Set(ValidityPeriod(0, 21, Saturday, 30, 45)), Nil, None, None, "", DateTime.now(), "" )

      val propNoRightTurn = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(TrafficSignType.NoRightTurn.value.toString))))
      val trafficSign = PersistedTrafficSign(1, 1001l, 2, 2, 2, false, 0, 235, propNoRightTurn, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(1003l, 1004l))).thenReturn(Seq(roadLink3, roadLink4))
      when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3, roadLink4))
      when(manoeuvreValidator.manoeuvreDao.getByRoadLinks(any[Seq[Long]])).thenReturn(Seq(manoeuvre))

      val result = manoeuvreValidator.assetValidator(trafficSign)
      result should be (true)

      dynamicSession.rollback()
    }
  }

  test("turn U sign restriction with a match asset") {
    OracleDatabase.withDynTransaction {
      val roadLink1 = RoadLink(1001l, Seq(Point(10.0, .0), Point(10, 5.0)), 5, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(1002l, Seq(Point(10.0, 5.0), Point(10, 10.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(1003l, Seq(Point(10.0, 10.0), Point(10.0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink4 = RoadLink(1004l, Seq(Point(5.0, 20.0), Point(10.0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink5 = RoadLink(1005l, Seq(Point(5.0, 10.0), Point(5.0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val start = ManoeuvreElement(1, 1003l, 1004l, ElementTypes.FirstElement)
      val intermediate = ManoeuvreElement(1, 1004l, 1005l, ElementTypes.IntermediateElement)
      val end = ManoeuvreElement(1, 1005l, 0, ElementTypes.LastElement)

      val manoeuvre =  Manoeuvre(1l, Seq(start, intermediate, end), Set(ValidityPeriod(0, 21, Saturday, 30, 45)), Nil, None, None, "", DateTime.now(), "" )

      val propNoUTurn = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(TrafficSignType.NoUTurn.value.toString))))
      val trafficSign = PersistedTrafficSign(1, 1001l, 2, 2, 2, false, 0, 235, propNoUTurn, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(1003l, 1004l, 1005l))).thenReturn(Seq(roadLink3, roadLink4, roadLink5))
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(1003l, 1004l))).thenReturn(Seq(roadLink3, roadLink4))
      when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3, roadLink4))
      when(manoeuvreValidator.manoeuvreDao.getByRoadLinks(any[Seq[Long]])).thenReturn(Seq(manoeuvre))

      val result = manoeuvreValidator.assetValidator(trafficSign)
      result should be (true)

      dynamicSession.rollback()
    }
  }

  test("turn U sign restriction with a left manoeuvre asset") {
    OracleDatabase.withDynTransaction {
      val roadLink1 = RoadLink(1001l, Seq(Point(10.0, .0), Point(10, 5.0)), 5, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(1002l, Seq(Point(10.0, 5.0), Point(10, 10.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(1003l, Seq(Point(10.0, 10.0), Point(10.0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink4 = RoadLink(1004l, Seq(Point(5.0, 20.0), Point(10.0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink5 = RoadLink(1005l, Seq(Point(5.0, 10.0), Point(5.0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val start = ManoeuvreElement(1, 1003l, 1004l, ElementTypes.FirstElement)
      val end = ManoeuvreElement(1, 1004l, 0, ElementTypes.LastElement)

      val manoeuvre =  Manoeuvre(1l, Seq(start, end), Set(ValidityPeriod(0, 21, Saturday, 30, 45)), Nil, None, None, "", DateTime.now(), "" )

      val propNoUTurn = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(TrafficSignType.NoUTurn.value.toString))))
      val trafficSign = PersistedTrafficSign(1, 1001l, 2, 2, 2, false, 0, 235, propNoUTurn, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(1003l, 1004l, 1005l))).thenReturn(Seq(roadLink3, roadLink4, roadLink5))
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(1003l, 1004l))).thenReturn(Seq(roadLink3, roadLink4))
      when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3, roadLink4))
      when(manoeuvreValidator.manoeuvreDao.getByRoadLinks(any[Seq[Long]])).thenReturn(Seq(manoeuvre))

      val result = manoeuvreValidator.assetValidator(trafficSign)
      result should be (false)

      dynamicSession.rollback()
    }
  }

  test("turn right sign restriction with a match asset only after a roadLink intersection ???") {
    OracleDatabase.withDynTransaction {
      val roadLink1 = RoadLink(1001l, Seq(Point(10.0, .0), Point(10, 5.0)), 5, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(1002l, Seq(Point(10.0, 5.0), Point(10, 10.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(1003l, Seq(Point(10.0, 10.0), Point(10.0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink4 = RoadLink(1004l, Seq(Point(20.0, 20.0), Point(10.0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink5 = RoadLink(1005l, Seq(Point(10.0, 5.0), Point(20, 5.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val start = ManoeuvreElement(1, 1003l, 1004l, ElementTypes.FirstElement)
      val end = ManoeuvreElement(1, 1004l, 0, ElementTypes.LastElement)

      val manoeuvre =  Manoeuvre(1l, Seq(start, end), Set(ValidityPeriod(0, 21, Saturday, 30, 45)), Nil, None, None, "", DateTime.now(), "" )

      val propNoRightTurn = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(TrafficSignType.NoRightTurn.value.toString))))
      val trafficSign = PersistedTrafficSign(1, 1001l, 2, 2, 2, false, 0, 235, propNoRightTurn, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(1003l, 1004l))).thenReturn(Seq(roadLink3, roadLink4))
      when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3, roadLink4, roadLink5))
      when(manoeuvreValidator.manoeuvreDao.getByRoadLinks(any[Seq[Long]])).thenReturn(Seq(manoeuvre))

      val result = manoeuvreValidator.assetValidator(trafficSign)
      result should be (false)

      dynamicSession.rollback()
    }
  }

  test(" left manoeuvre without a sign") {
    OracleDatabase.withDynTransaction {
      val roadLink1 = RoadLink(1001l, Seq(Point(10.0, 5.0), Point(10, 10.0)), 5, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(1002l, Seq(Point(0.0, 10.0), Point(10, 10.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(1003l, Seq(Point(10.0, 0.0), Point(10.0, 5.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val start = ManoeuvreElement(1, 1001l, 1002l, ElementTypes.FirstElement)
      val end = ManoeuvreElement(1, 1002l, 0, ElementTypes.LastElement)

      val manoeuvre =  Manoeuvre(1l, Seq(start, end), Set(ValidityPeriod(0, 21, Saturday, 30, 45)), Nil, None, None, "", DateTime.now(), "" )

      val propNoLeftTurn = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(TrafficSignType.NoLeftTurn.value.toString))))
      val trafficSign = PersistedTrafficSign(1, 10003l, 2, 2, 2, false, 0, 235, propNoLeftTurn, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

      when(mockTrafficSignService.getTrafficSign(Seq(1003l))).thenReturn(Seq(trafficSign))
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(1001l, 1002l))).thenReturn(Seq(roadLink1, roadLink2))
      when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3))
      when(mockRoadLinkService.pickForwardMost(roadLink2, Seq(roadLink3))).thenReturn(roadLink3)

      val result = manoeuvreValidator.validateManoeuvre(manoeuvre)
      result should be (false)

      dynamicSession.rollback()
    }
  }

  test(" left manoeuvre with a sign turn Left") {
    OracleDatabase.withDynTransaction {
      val roadLink1 = RoadLink(1001l, Seq(Point(10.0, 5.0), Point(10, 10.0)), 5, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(1002l, Seq(Point(0.0, 10.0), Point(10, 10.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(1003l, Seq(Point(10.0, 0.0), Point(10.0, 5.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val start = ManoeuvreElement(1, 1001l, 1002l, ElementTypes.FirstElement)
      val end = ManoeuvreElement(1, 1002l, 0, ElementTypes.LastElement)

      val manoeuvre =  Manoeuvre(1l, Seq(start, end), Set(ValidityPeriod(0, 21, Saturday, 30, 45)), Nil, None, None, "", DateTime.now(), "" )

      val propNoLeftTurn = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(TrafficSignType.NoLeftTurn.value.toString))))
      val trafficSign = PersistedTrafficSign(1, 10003l, 2, 2, 2, false, 0, 235, propNoLeftTurn, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

      when(mockTrafficSignService.getTrafficSign(Seq(1003l))).thenReturn(Seq(trafficSign))
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(1001l, 1002l))).thenReturn(Seq(roadLink1, roadLink2))
      when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3))
      when(mockRoadLinkService.pickForwardMost(roadLink1, Seq(roadLink3))).thenReturn(roadLink3)

      val result = manoeuvreValidator.validateManoeuvre(manoeuvre)
      result should be (true)

      dynamicSession.rollback()
    }
  }

  test(" right manoeuvre with a sign turn right") {
    OracleDatabase.withDynTransaction {
      val roadLink1 = RoadLink(1001l, Seq(Point(10.0, 5.0), Point(10, 10.0)), 5, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(1002l, Seq(Point(20.0, 10.0), Point(10, 10.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(1003l, Seq(Point(10.0, 0.0), Point(10.0, 5.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val start = ManoeuvreElement(1, 1001l, 1002l, ElementTypes.FirstElement)
      val end = ManoeuvreElement(1, 1002l, 0, ElementTypes.LastElement)

      val manoeuvre =  Manoeuvre(1l, Seq(start, end), Set(ValidityPeriod(0, 21, Saturday, 30, 45)), Nil, None, None, "", DateTime.now(), "" )

      val propNoRightTurn = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(TrafficSignType.NoRightTurn.value.toString))))
      val trafficSign = PersistedTrafficSign(1, 10003l, 2, 2, 2, false, 0, 235, propNoRightTurn, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

      when(mockTrafficSignService.getTrafficSign(Seq(1003l))).thenReturn(Seq(trafficSign))
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(1001l, 1002l))).thenReturn(Seq(roadLink1, roadLink2))
      when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3))
      when(mockRoadLinkService.pickForwardMost(roadLink1, Seq(roadLink3))).thenReturn(roadLink3)

      val result = manoeuvreValidator.validateManoeuvre(manoeuvre)
      result should be (true)

      dynamicSession.rollback()
    }
  }

  test(" manoeuvre with Uturn sign") {
    OracleDatabase.withDynTransaction {
      val roadLink1 = RoadLink(1001l, Seq(Point(10.0, 5.0), Point(10, 10.0)), 5, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(1002l, Seq(Point(0.0, 10.0), Point(10, 10.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(1003l, Seq(Point(10.0, 2.0), Point(10.0, 5.0)), 3.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink4 = RoadLink(1004l, Seq(Point(10.0, 0.0), Point(10.0, 2.0)), 2.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink5 = RoadLink(1005l, Seq(Point(0.0, 0.0), Point(0.0, 10.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val start = ManoeuvreElement(1, 1001l, 1002l, ElementTypes.FirstElement)
      val end = ManoeuvreElement(1, 1005l, 0, ElementTypes.LastElement)
      val intermediate = ManoeuvreElement(1, 1002l, 1005l, ElementTypes.IntermediateElement)

      val manoeuvre =  Manoeuvre(1l, Seq(start, intermediate, end), Set(ValidityPeriod(0, 21, Saturday, 30, 45)), Nil, None, None, "", DateTime.now(), "" )

      val propNoLeftTurn = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(TrafficSignType.NoUTurn.value.toString))))
      val trafficSign = PersistedTrafficSign(1, 10003l, 2, 2, 2, false, 0, 235, propNoLeftTurn, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

      when(mockTrafficSignService.getTrafficSign(Seq(1003l))).thenReturn(Seq(trafficSign))
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(1001l, 1002l))).thenReturn(Seq(roadLink1, roadLink2))
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(1001l, 1002l, 1005l))).thenReturn(Seq(roadLink1, roadLink2, roadLink5))
      when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3))
      when(mockRoadLinkService.pickForwardMost(roadLink1, Seq(roadLink3))).thenReturn(roadLink3)
      when(mockRoadLinkService.pickForwardMost(roadLink3, Seq(roadLink4))).thenReturn(roadLink4)

      val result = manoeuvreValidator.validateManoeuvre(manoeuvre)
      result should be (true)

      dynamicSession.rollback()
    }
  }

  test(" manoeuvre only turn left and Uturn sign") {
    OracleDatabase.withDynTransaction {
      val roadLink1 = RoadLink(1001l, Seq(Point(10.0, 5.0), Point(10, 10.0)), 5, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(1002l, Seq(Point(0.0, 10.0), Point(10, 10.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(1003l, Seq(Point(10.0, 2.0), Point(10.0, 5.0)), 3.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val start = ManoeuvreElement(1, 1001l, 1002l, ElementTypes.FirstElement)
      val end = ManoeuvreElement(1, 1002l, 0, ElementTypes.LastElement)

      val manoeuvre =  Manoeuvre(1l, Seq(start, end), Set(ValidityPeriod(0, 21, Saturday, 30, 45)), Nil, None, None, "", DateTime.now(), "" )

      val propNoLeftTurn = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(TrafficSignType.NoUTurn.value.toString))))
      val trafficSign = PersistedTrafficSign(1, 10003l, 2, 2, 2, false, 0, 235, propNoLeftTurn, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

      when(mockTrafficSignService.getTrafficSign(Seq(1003l))).thenReturn(Seq(trafficSign))
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(1001l, 1002l))).thenReturn(Seq(roadLink1, roadLink2))
      when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3))
      when(mockRoadLinkService.pickForwardMost(roadLink1, Seq(roadLink3))).thenReturn(roadLink3)

      val result = manoeuvreValidator.validateManoeuvre(manoeuvre)
      result should be (false)

      dynamicSession.rollback()
    }
  }

  test(" manoeuvre without sign on a correct Road Link") {
    OracleDatabase.withDynTransaction {
      val roadLink1 = RoadLink(1001l, Seq(Point(100.0, 100.0), Point(150, 100.0)), 5, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(1002l, Seq(Point(150.0, 100.0), Point(200, 100.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(1003l, Seq(Point(200.0, 100.0), Point(200.0, 70.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink4 = RoadLink(1004l, Seq(Point(200.0, 70.0), Point(200.0, 50.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink5 = RoadLink(1005l, Seq(Point(200.0, 50.0), Point(250.0, 50.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink6 = RoadLink(1006l, Seq(Point(200.0, 50.0), Point(150.0, 50.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink7 = RoadLink(1007l, Seq(Point(200.0, 50.0), Point(200.0, 0.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val start = ManoeuvreElement(1, 1003l, 1002l, ElementTypes.FirstElement)
      val intermediate = ManoeuvreElement(1, 1002l, 1001l, ElementTypes.IntermediateElement)
      val end = ManoeuvreElement(1, 1001l, 0, ElementTypes.LastElement)

      val manoeuvre =  Manoeuvre(1l, Seq(start, intermediate, end), Set(ValidityPeriod(0, 21, Saturday, 30, 45)), Nil, None, None, "", DateTime.now(), "" )

      val propNoLeftTurn = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(TrafficSignType.NoLeftTurn.value.toString))))
      val trafficSign = PersistedTrafficSign(1, 10005l, 2, 2, 2, false, 0, 235, propNoLeftTurn, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

      when(mockTrafficSignService.getTrafficSign(Seq(1004l))).thenReturn(Seq())
      when(mockTrafficSignService.getTrafficSign(Seq(1006l))).thenReturn(Seq())
      when(mockTrafficSignService.getTrafficSign(Seq(1007l))).thenReturn(Seq())

      when(mockTrafficSignService.getTrafficSign(Seq(1005l))).thenReturn(Seq(trafficSign))
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(1001l, 1002l, 1003l))).thenReturn(Seq(roadLink1, roadLink2))
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(1003l, 1002l))).thenReturn(Seq(roadLink3, roadLink2))
      when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink2, roadLink3, roadLink4, roadLink5, roadLink6, roadLink7))
      when(mockRoadLinkService.pickForwardMost(roadLink3, Seq(roadLink4))).thenReturn(roadLink4)
      when(mockRoadLinkService.pickForwardMost(roadLink4, Seq(roadLink5, roadLink6, roadLink7))).thenReturn(roadLink7)

      val result = manoeuvreValidator.validateManoeuvre(manoeuvre)
      result should be (false)

      dynamicSession.rollback()
    }
  }

  test(" manoeuvre with a sign turn left Road Links ahead") {
    OracleDatabase.withDynTransaction {
      val roadLink1 = RoadLink(1001l, Seq(Point(100.0, 100.0), Point(150, 100.0)), 5, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(1002l, Seq(Point(150.0, 100.0), Point(200, 100.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(1003l, Seq(Point(200.0, 100.0), Point(200.0, 70.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink4 = RoadLink(1004l, Seq(Point(200.0, 70.0), Point(200.0, 50.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink5 = RoadLink(1005l, Seq(Point(200.0, 50.0), Point(250.0, 50.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink6 = RoadLink(1006l, Seq(Point(200.0, 50.0), Point(150.0, 50.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink7 = RoadLink(1007l, Seq(Point(200.0, 50.0), Point(200.0, 0.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val start = ManoeuvreElement(1, 1003l, 1002l, ElementTypes.FirstElement)
      val intermediate = ManoeuvreElement(1, 1002l, 1001l, ElementTypes.IntermediateElement)
      val end = ManoeuvreElement(1, 1001l, 0, ElementTypes.LastElement)

      val manoeuvre =  Manoeuvre(1l, Seq(start, intermediate, end), Set(ValidityPeriod(0, 21, Saturday, 30, 45)), Nil, None, None, "", DateTime.now(), "" )

      val propNoLeftTurn = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(TrafficSignType.NoLeftTurn.value.toString))))
      val trafficSign = PersistedTrafficSign(1, 10007l, 2, 2, 2, false, 0, 235, propNoLeftTurn, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

      when(mockTrafficSignService.getTrafficSign(Seq(1004l))).thenReturn(Seq())
      when(mockTrafficSignService.getTrafficSign(Seq(1007l))).thenReturn(Seq(trafficSign))
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(1001l, 1002l, 1003l))).thenReturn(Seq(roadLink1, roadLink2, roadLink3))
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(1003l, 1002l))).thenReturn(Seq(roadLink3, roadLink2))
      when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink2, roadLink3, roadLink4, roadLink5, roadLink6, roadLink7))
      when(mockRoadLinkService.pickForwardMost(roadLink3, Seq(roadLink4))).thenReturn(roadLink4)
      when(mockRoadLinkService.pickForwardMost(roadLink4, Seq(roadLink5, roadLink6, roadLink7))).thenReturn(roadLink7)

      val result = manoeuvreValidator.validateManoeuvre(manoeuvre)
      result should be (true)

      dynamicSession.rollback()
    }
  }
}
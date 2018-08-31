package fi.liikennevirasto.digiroad2


import fi.liikennevirasto.digiroad2.asset.{AxleWeightLimit, TotalWeightLimit, WidthLimit, _}
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.process._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset._
import fi.liikennevirasto.digiroad2.service.pointasset.{TrafficSignService, TrafficSignType}
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.linearasset.manoeuvre.ManoeuvreDao
import fi.liikennevirasto.digiroad2.linearasset.ValidityPeriodDayOfWeek.{Saturday, Weekday}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}

class AssetServiceValidatorSpec  extends FunSuite with Matchers with BeforeAndAfter {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  //  val mockVVHClient = MockitoSugar.mock[VVHClient]
  //  val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  //  val mockUserProvider = MockitoSugar.mock[OracleUserProvider]
  val mockTrafficSignService = MockitoSugar.mock[TrafficSignService]

  //  val validator = AssetServiceValidator(mockRoadLinkService, mockTrafficSignService) = {
  //    val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  //    val mockVVHClient = MockitoSugar.mock[VVHClient]
  //    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  //    val mockUserProvider = MockitoSugar.mock[OracleUserProvider]
  //    val mockTrafficSignService = MockitoSugar.mock[TrafficSignService]
  //  }
  //
  //  def toRoadLink(l: VVHRoadlink) = {
  //    RoadLink(l.linkId, l.geometry, GeometryUtils.geometryLength(l.geometry),
  //      l.administrativeClass, 1, l.trafficDirection, UnknownLinkType, None, None, l.attributes + ("MUNICIPALITYCODE" -> BigInt(l.municipalityCode)))
  //  }


  //  test("findnearest roadLink ") {
  //
  //    val roadLinks = Seq(roadLink1, roadLink2)
  //    val point = Point(10, 0)
  //
  //    validator.findNearestRoadLink(point, roadLinks) should be (roadLink1)
  //  }
  //
  //
  //  test("getAdjacent and next point") {
  //    val roadLinks = Seq(roadLink1, roadLink2, roadLink3, roadLink4, roadLink5)
  //
  //    val point = Point(0, 10)
  //
  //    val result = validator.getAdjacents(point, roadLinks)
  //    result.exists(_._1.linkId == roadLink1.linkId ) should be (true)
  //    result.exists(_._1.linkId == roadLink3.linkId ) should be (true)
  //    result.exists(_._1.linkId == roadLink2.linkId ) should be (false)
  //    result.exists(_._1.linkId == roadLink4.linkId ) should be (false)
  //    result.exists(_._1.linkId == roadLink5.linkId ) should be (false)
  //  }
  //


  val  manoeuvreValidator = new ManoeuvreServiceValidator(mockRoadLinkService, mockTrafficSignService) {
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def trafficSignService: TrafficSignService = mockTrafficSignService

    val mockManoeuvreDAO = MockitoSugar.mock[ManoeuvreDao]
    override def manoeuvreDao: ManoeuvreDao = mockManoeuvreDAO
  }

  test(" restriction sign without a match assset") {
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

  val  prohibitionValidator = new HazmatTransportProhibitionValidator(mockRoadLinkService, mockTrafficSignService) {
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def trafficSignService: TrafficSignService = mockTrafficSignService

    val mockLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]
    override def dao: OracleLinearAssetDao =  mockLinearAssetDao
  }

  test("  prohibition traffic sign validation should return false") {
    OracleDatabase.withDynTransaction {
      val roadLink1 = RoadLink(1001l, Seq(Point(10.0, 5.0), Point(10, 10.0)), 5, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(1002l, Seq(Point(0.0, 10.0), Point(10, 10.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(1003l, Seq(Point(10.0, 0.0), Point(10.0, 5.0)), 5.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val propHazmatProhibitionA = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(TrafficSignType.HazmatProhibitionA.value.toString))))
      val trafficSign = PersistedTrafficSign(1, 10002l, 2, 2, 2, false, 0, 235, propHazmatProhibitionA, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

      when(mockTrafficSignService.getTrafficSign(Seq(1003l))).thenReturn(Seq(trafficSign))
      when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(1001l, 1002l))).thenReturn(Seq(roadLink1, roadLink2))
      when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3))
      when(mockRoadLinkService.pickForwardMost(roadLink2, Seq(roadLink3))).thenReturn(roadLink3)

      val result = prohibitionValidator.assetValidator(trafficSign)
      result should be (false)

      dynamicSession.rollback()
    }
  }

  test("  prohibition traffic sign validation should find match asset") {
    OracleDatabase.withDynTransaction {
      val roadLink1 = RoadLink(1001l, Seq(Point(0.0, .0), Point(0, 10.0)), 10, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(1002l, Seq(Point(0.0, 10.0), Point(0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(1003l, Seq(Point(0.0, 20.0), Point(0.0, 30.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink4 = RoadLink(1004l, Seq(Point(0.0, 30.0), Point(0.0, 40.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val propHazmatProhibitionA = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(TrafficSignType.HazmatProhibitionA.value.toString))))
      val trafficSign = PersistedTrafficSign(1, 1002l, 2, 2, 2, false, 0, 235, propHazmatProhibitionA, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

      when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3, roadLink4))
      when(prohibitionValidator.dao.fetchProhibitionsByLinkIds(HazmatTransportProhibition.typeId, Seq(1003l), false)).thenReturn(Seq())

      val value = Prohibitions(Seq(ProhibitionValue(25, Set(ValidityPeriod(11, 12, Weekday)), Set.empty, null), ProhibitionValue(24, Set(ValidityPeriod(11, 12, Weekday)), Set.empty, null)))
      when(prohibitionValidator.dao.fetchProhibitionsByLinkIds(HazmatTransportProhibition.typeId, Seq(1004l), false))
        .thenReturn(Seq(PersistedLinearAsset(1, 1, 1, Some(value), 0.4, 9.6, None, None, None, None, false, 30, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None)))

      val result = prohibitionValidator.assetValidator(trafficSign)
      result should be (true)

      dynamicSession.rollback()
    }
  }

  test(" prohibition traffic validation should have all with asset") {
    OracleDatabase.withDynTransaction {
      val roadLink1 = RoadLink(1001l, Seq(Point(10.0, 0.0), Point(10, 10.0)), 10, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink2 = RoadLink(1002l, Seq(Point(10.0, 10.0), Point(10, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink3 = RoadLink(1003l, Seq(Point(10.0, 20.0), Point(0.0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val roadLink4 = RoadLink(1004l, Seq(Point(10.0, 20.0), Point(20.0, 20.0)), 25.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

      val propHazmatProhibitionA = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(TrafficSignType.HazmatProhibitionA.value.toString))))
      val trafficSign = PersistedTrafficSign(1, 1001l, 2, 2, 2, false, 0, 235, propHazmatProhibitionA, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

      when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3, roadLink4))
      when(prohibitionValidator.dao.fetchProhibitionsByLinkIds(HazmatTransportProhibition.typeId, Seq(1003l), false)).thenReturn(Seq())

      val value = Prohibitions(Seq(ProhibitionValue(25, Set(ValidityPeriod(11, 12, Weekday)), Set.empty, null), ProhibitionValue(24, Set(ValidityPeriod(11, 12, Weekday)), Set.empty, null)))
      when(prohibitionValidator.dao.fetchProhibitionsByLinkIds(HazmatTransportProhibition.typeId, Seq(1004l), false))
        .thenReturn(Seq(PersistedLinearAsset(1, 1, 1, Some(value), 0.4, 9.6, None, None, None, None, false, 30, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None)))

      val result = prohibitionValidator.assetValidator(trafficSign)
      result should be (false)

      dynamicSession.rollback()
    }
  }

  /*----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------*/
  /*------------------------------------------------------------------------------------ Mass Limitation Validator  ------------------------------------------------------------------------------------*/
  /*----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------*/
//  val roadLink1 = RoadLink(1001l, Seq(Point(0.0, 0.0), Point(0, 10.0)), 10, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
//  val roadLink2 = RoadLink(1002l, Seq(Point(0.0, 10.0), Point(0, 20.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
//  val roadLink3 = RoadLink(1003l, Seq(Point(0.0, 20.0), Point(0.0, 30.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
//  val roadLink4 = RoadLink(1004l, Seq(Point(0.0, 30.0), Point(0.0, 40.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
//
//  when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3, roadLink4))
//
//  val widthLimitValidator = new WidthLimitValidator(mockRoadLinkService, mockTrafficSignService) {
//    override def roadLinkService: RoadLinkService = mockRoadLinkService
//    override def trafficSignService: TrafficSignService = mockTrafficSignService
//
//    val mockLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]
//    override def dao: OracleLinearAssetDao =  mockLinearAssetDao
//  }
//
//  val heightLimitValidator = new HeightLimitValidator(mockRoadLinkService, mockTrafficSignService) {
//    override def roadLinkService: RoadLinkService = mockRoadLinkService
//    override def trafficSignService: TrafficSignService = mockTrafficSignService
//
//    val mockLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]
//    override def dao: OracleLinearAssetDao =  mockLinearAssetDao
//  }
//
//  val totalWeightLimitValidator = new TotalWeightLimitValidator(mockRoadLinkService, mockTrafficSignService) {
//    override def roadLinkService: RoadLinkService = mockRoadLinkService
//    override def trafficSignService: TrafficSignService = mockTrafficSignService
//
//    val mockLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]
//    override def dao: OracleLinearAssetDao =  mockLinearAssetDao
//  }
//
//  val trailerTruckWeightLimitValidator = new TrailerTruckWeightLimitValidator(mockRoadLinkService, mockTrafficSignService) {
//    override def roadLinkService: RoadLinkService = mockRoadLinkService
//    override def trafficSignService: TrafficSignService = mockTrafficSignService
//
//    val mockLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]
//    override def dao: OracleLinearAssetDao =  mockLinearAssetDao
//  }
//
//  val axleWeightLimitValidator = new AxleWeightLimitValidator(mockRoadLinkService, mockTrafficSignService) {
//    override def roadLinkService: RoadLinkService = mockRoadLinkService
//    override def trafficSignService: TrafficSignService = mockTrafficSignService
//
//    val mockLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]
//    override def dao: OracleLinearAssetDao =  mockLinearAssetDao
//  }
//
//  val bogieWeightLimitValidator = new BogieWeightLimitValidator(mockRoadLinkService, mockTrafficSignService) {
//    override def roadLinkService: RoadLinkService = mockRoadLinkService
//    override def trafficSignService: TrafficSignService = mockTrafficSignService
//
//    val mockLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]
//    override def dao: OracleLinearAssetDao =  mockLinearAssetDao
//  }
//
//  val lengthLimitValidator = new LengthLimitValidator(mockRoadLinkService, mockTrafficSignService) {
//    override def roadLinkService: RoadLinkService = mockRoadLinkService
//    override def trafficSignService: TrafficSignService = mockTrafficSignService
//
//    val mockLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]
//    override def dao: OracleLinearAssetDao =  mockLinearAssetDao
//  }
//
//  case class MassLimitationValidator(typeId: Int, service: MassLimitationValidator, trafficSign: TrafficSignType )
//  val massLimitationAssets = Seq(
//    MassLimitationValidator(WidthLimit.typeId, widthLimitValidator, TrafficSignType.FreeWidth),
//    MassLimitationValidator(HeightLimit.typeId, heightLimitValidator, TrafficSignType.MaxHeightExceeding),
//    MassLimitationValidator(TotalWeightLimit.typeId, totalWeightLimitValidator, TrafficSignType.MaxLadenExceeding),
//    MassLimitationValidator(TrailerTruckWeightLimit.typeId, trailerTruckWeightLimitValidator,TrafficSignType.MaxMassCombineVehiclesExceeding),
//    MassLimitationValidator(AxleWeightLimit.typeId, axleWeightLimitValidator, TrafficSignType.MaxTonsOneAxleExceeding),
//    MassLimitationValidator(BogieWeightLimit.typeId, bogieWeightLimitValidator, TrafficSignType.MaxTonsOnBogieExceeding),
//    MassLimitationValidator(LengthLimit.typeId, lengthLimitValidator, TrafficSignType.MaximumLength)
//  )
//
//  def massLimitationWithoutMatchedAssset(massLimitationAsset: MassLimitationValidator): Unit = {
//    OracleDatabase.withDynTransaction {
//
//      val propTrafficSign = Seq(
//        Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(massLimitationAsset.trafficSign.value.toString))),
//        Property(1, "trafficSigns_value", "", false, Seq(PropertyValue("100"))),
//        Property(2, "trafficSigns_info", "", false, Seq(PropertyValue("200"))))
//
//      val trafficSign = PersistedTrafficSign(1, 1002l, 2, 2, 2, false, 0, 235, propTrafficSign, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)
//
//      when(massLimitationAsset.service.dao.fetchLinearAssetsByLinkIds(widthLimitValidator.assetTypeInfo.typeId, Seq(1003l), LinearAssetTypes.numericValuePropertyId, false)).thenReturn(Seq())
//      when(massLimitationAsset.service.dao.fetchLinearAssetsByLinkIds(widthLimitValidator.assetTypeInfo.typeId, Seq(1004l), LinearAssetTypes.numericValuePropertyId, false))
//        .thenReturn(Seq(PersistedLinearAsset(1, 1, 1, Some(NumericValue(100)), 0.4, 9.6, None, None, None, None, false, massLimitationAsset.typeId, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None)))
//
//      val result = widthLimitValidator.assetValidator(trafficSign)
//      withClue("assetName " + AssetTypeInfo.apply(massLimitationAsset.typeId).toString) {
//        result should be(false)
//      }
//
//      dynamicSession.rollback()
//    }
//  }
//
//  def massLimitationWithtMatchedAssset(massLimitationAsset: MassLimitationValidator): Unit = {
//    OracleDatabase.withDynTransaction {
//      val propTrafficSign = Seq(
//        Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(massLimitationAsset.trafficSign.value.toString))),
//        Property(1, "trafficSigns_value", "", false, Seq()),
//        Property(2, "trafficSigns_info", "", false, Seq(PropertyValue("200"))))
//
//      val trafficSign = PersistedTrafficSign(1, 1002l, 2, 2, 2, false, 0, 235, propTrafficSign, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)
//
//      when(massLimitationAsset.service.dao.fetchLinearAssetsByLinkIds(widthLimitValidator.assetTypeInfo.typeId, Seq(1003l), LinearAssetTypes.numericValuePropertyId, false)).thenReturn(Seq())
//      when(massLimitationAsset.service.dao.fetchLinearAssetsByLinkIds(widthLimitValidator.assetTypeInfo.typeId, Seq(1004l), LinearAssetTypes.numericValuePropertyId, false))
//        .thenReturn(Seq(PersistedLinearAsset(1, 1, 1, Some(NumericValue(200)), 0.4, 9.6, None, None, None, None, false, massLimitationAsset.typeId, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None)))
//
//      val result = widthLimitValidator.assetValidator(trafficSign)
//      withClue("assetName " + AssetTypeInfo.apply(massLimitationAsset.typeId).toString) {
//        result should be(true)
//      }
//
//      dynamicSession.rollback()
//    }
//  }
//
//  test(" massLimitation traffic sign without match asset") {
//    massLimitationAssets.foreach { massLimitationAsset =>
//      massLimitationWithoutMatchedAssset(massLimitationAsset)
//    }
//  }
//
//  test(" widthLimit traffic sign should find a match asset") {
//    massLimitationAssets.foreach { massLimitationAsset =>
//      massLimitationWithtMatchedAssset(massLimitationAsset)
//    }
//  }



}









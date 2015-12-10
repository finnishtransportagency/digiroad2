package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.asset.oracle.OracleSpatialAssetDao
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

class MassTransitStopServiceSpec extends FunSuite with Matchers {
  val boundingBoxWithKauniainenAssets = BoundingRectangle(Point(374000,6677000), Point(374800,6677600))
  val userWithKauniainenAuthorization = User(
    id = 1,
    username = "Hannu",
    configuration = Configuration(authorizedMunicipalities = Set(235)))
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  when(mockVVHClient.fetchVVHRoadlinks(any[BoundingRectangle], any[Set[Int]])).thenReturn(List(
    VVHRoadlink(1140018963l, 90, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
    VVHRoadlink(388554364l, 235, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockVVHClient.fetchVVHRoadlink(388554364l))
    .thenReturn(Some(VVHRoadlink(388554364l, 235, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockVVHClient.fetchVVHRoadlink(123l))
    .thenReturn(Some(VVHRoadlink(123l, 91, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockVVHClient.fetchVVHRoadlink(388553080l))
    .thenReturn(Some(VVHRoadlink(388553080l, 235, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockVVHClient.fetchVVHRoadlink(1l))
    .thenReturn(Some(VVHRoadlink(1l, 235, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Municipality,
    TrafficDirection.BothDirections, FeatureClass.AllOthers)))

  class TestMassTransitStopService(val eventbus: DigiroadEventBus) extends MassTransitStopService {
    override def withDynSession[T](f: => T): T = f
    override def withDynTransaction[T](f: => T): T = f
    override def vvhClient: VVHClient = mockVVHClient
    override val spatialAssetDao: OracleSpatialAssetDao = new OracleSpatialAssetDao
  }

  object RollbackMassTransitStopService extends TestMassTransitStopService(new DummyEventBus)

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  test("Calculate mass transit stop validity periods") {
    runWithRollback {
      val massTransitStops = RollbackMassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBoxWithKauniainenAssets)
      massTransitStops.find(_.id == 300000).map(_.validityPeriod) should be(Some(MassTransitStopValidityPeriod.Current))
      massTransitStops.find(_.id == 300001).map(_.validityPeriod) should be(Some(MassTransitStopValidityPeriod.Past))
      massTransitStops.find(_.id == 300003).map(_.validityPeriod) should be(Some(MassTransitStopValidityPeriod.Future))
    }
  }

  test("Return mass transit stop types") {
    runWithRollback {
      val massTransitStops = RollbackMassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBoxWithKauniainenAssets)
      massTransitStops.find(_.id == 300000).map(_.stopTypes) should be(Some(Seq(2)))
      massTransitStops.find(_.id == 300001).map(_.stopTypes) should be(Some(Seq(2, 3, 4)))
      massTransitStops.find(_.id == 300003).map(_.stopTypes) should be(Some(Seq(2, 3)))
    }
  }

  test("Get stops by bounding box") {
    runWithRollback {
      val id = RollbackMassTransitStopService.create(NewMassTransitStop(5.0, 0.0, 1l, 2, Nil), "masstransitstopservice_spec", List(Point(0.0,0.0), Point(120.0, 0.0)), 235)
      val stops = RollbackMassTransitStopService.getByBoundingBox(
        userWithKauniainenAuthorization, BoundingRectangle(Point(0.0, 0.0), Point(10.0, 10.0)))
      stops.map(_.id) should be(Seq(id))
    }
  }

  test("Filter stops by authorization") {
    runWithRollback {
      val stops = RollbackMassTransitStopService.getByBoundingBox(User(0, "test", Configuration()), boundingBoxWithKauniainenAssets)
      stops should be(empty)
    }
  }

  test("Stop floats if road link does not exist") {
    runWithRollback {
      val stops = RollbackMassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBoxWithKauniainenAssets)
      stops.find(_.id == 300000).map(_.floating) should be(Some(true))
    }
  }

  test("Stop floats if stop and roadlink municipality codes differ") {
    runWithRollback {
      val stops = RollbackMassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBoxWithKauniainenAssets)
      stops.find(_.id == 300004).map(_.floating) should be(Some(true))
    }
  }

  test("Stop floats if stop is too far from linearly referenced location") {
    runWithRollback {
      val stops = RollbackMassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBoxWithKauniainenAssets)
      stops.find(_.id == 300008).map(_.floating) should be(Some(true))
    }
  }

  test("Persist mass transit stop floating status change") {
    runWithRollback {
      RollbackMassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBoxWithKauniainenAssets)
      val floating: Option[Boolean] = sql"""select floating from asset where id = 300008""".as[Boolean].firstOption
      floating should be(Some(true))
    }
  }

  test("Fetch mass transit stop by national id") {
    runWithRollback {
      val stop = RollbackMassTransitStopService.getMassTransitStopByNationalId(85755, _ => Unit)
      stop.map(_.floating) should be(Some(true))
    }
  }

  test("Assert user rights when fetching mass transit stop with id") {
    runWithRollback {
      an [Exception] should be thrownBy RollbackMassTransitStopService.getMassTransitStopByNationalId(85755, { municipalityCode => throw new Exception })
    }
  }

  test("Update mass transit stop road link mml id") {
    runWithRollback {
      val position = Some(Position(60.0, 0.0, 388554364l, None))
      RollbackMassTransitStopService.updateExistingById(300000, position, Set.empty, "user", _ => Unit)
      val mmlId = sql"""
            select lrm.mml_id from asset a
            join asset_link al on al.asset_id = a.id
            join lrm_position lrm on lrm.id = al.position_id
            where a.id = 300000
      """.as[Long].firstOption
      mmlId should be(Some(388554364l))
    }
  }

  test("Update mass transit stop bearing") {
    runWithRollback {
      val position = Some(Position(60.0, 0.0, 388554364l, Some(90)))
      RollbackMassTransitStopService.updateExistingById(300000, position, Set.empty, "user", _ => Unit)
      val bearing = sql"""
            select a.bearing from asset a
            join asset_link al on al.asset_id = a.id
            join lrm_position lrm on lrm.id = al.position_id
            where a.id = 300000
      """.as[Option[Int]].first
      bearing should be(Some(90))
    }
  }

  test("Update mass transit stop municipality") {
    runWithRollback {
      val position = Some(Position(60.0, 0.0, 123l, None))
      RollbackMassTransitStopService.updateExistingById(300000, position, Set.empty, "user", _ => Unit)
      val municipality = sql"""
            select a.municipality_code from asset a
            join asset_link al on al.asset_id = a.id
            join lrm_position lrm on lrm.id = al.position_id
            where a.id = 300000
      """.as[Int].firstOption
      municipality should be(Some(91))
    }
  }

  test("Update last modified info") {
    runWithRollback {
      RollbackMassTransitStopService.updateExistingById(300000, None, Set.empty, "user", _ => Unit)
      val modifier = sql"""
            select a.modified_by from asset a
            where a.id = 300000
      """.as[String].firstOption
      modifier should be(Some("user"))
    }
  }

  test("Update properties") {
    runWithRollback {
      val values = List(PropertyValue("New name"))
      val properties = Set(SimpleProperty("nimi_suomeksi", values))
      RollbackMassTransitStopService.updateExistingById(300000, None, properties, "user", _ => Unit)
      val modifier = sql"""
            select v.value_fi from text_property_value v
            join property p on v.property_id = p.id
            where v.asset_id = 300000 and p.public_id = 'nimi_suomeksi'
      """.as[String].firstOption
      modifier should be(Some("New name"))
    }
  }

  test("Persist floating on update") {
    runWithRollback {
      val position = Some(Position(60.0, 0.0, 123l, None))
      RollbackMassTransitStopService.updateExistingById(300002, position, Set.empty, "user", _ => Unit)
      val floating = sql"""
            select a.floating from asset a
            where a.id = 300002
      """.as[Int].firstOption
      floating should be(Some(0))
    }
  }

  test("Send event to event bus in update") {
    runWithRollback {
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus)
      val position = Some(Position(60.0, 0.0, 123l, None))
      service.updateExistingById(300002, position, Set.empty, "user", _ => Unit)
      verify(eventbus).publish(org.mockito.Matchers.eq("asset:saved"), any[EventBusMassTransitStop]())
    }
  }

  test("Assert user rights when updating a mass transit stop") {
    runWithRollback {
      val position = Some(Position(60.0, 0.0, 123l, None))
      an [Exception] should be thrownBy RollbackMassTransitStopService.updateExistingById(300002, position, Set.empty, "user", { municipalityCode => throw new Exception })
    }
  }

  test("Create new mass transit stop") {
    runWithRollback {
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus)
      val values = List(PropertyValue("1"))
      val properties = List(SimpleProperty("pysakin_tyyppi", values))
      val id = service.create(NewMassTransitStop(60.0, 0.0, 123l, 100, properties), "test", List(Point(0.0,0.0), Point(120.0, 0.0)), 91)
      val massTransitStop = service.getById(id).get
      massTransitStop.bearing should be(Some(100))
      massTransitStop.floating should be(false)
      massTransitStop.stopTypes should be(List(1))
      massTransitStop.validityPeriod should be(MassTransitStopValidityPeriod.Current)
      verify(eventbus).publish(org.mockito.Matchers.eq("asset:saved"), any[EventBusMassTransitStop]())
    }
  }

  test("Project stop location on two-point geometry") {
    val linkGeometry: Seq[Point] = List(Point(0.0, 0.0), Point(1.0, 0.0))
    val location: Point = Point(0.5, 0.5)
    val mValue: Double = RollbackMassTransitStopService.calculateLinearReferenceFromPoint(location, linkGeometry)
    mValue should be(0.5)
  }

  test("Project stop location on three-point geometry") {
    val linkGeometry: Seq[Point] = List(Point(0.0, 0.0), Point(1.0, 0.0), Point(1.0, 0.5))
    val location: Point = Point(1.2, 0.25)
    val mValue: Double = RollbackMassTransitStopService.calculateLinearReferenceFromPoint(location, linkGeometry)
    mValue should be(1.25)
  }

  test("Project stop location to beginning of geometry if point lies behind geometry") {
    val linkGeometry: Seq[Point] = List(Point(0.0, 0.0), Point(1.0, 0.0))
    val location: Point = Point(-0.5, 0.0)
    val mValue: Double = RollbackMassTransitStopService.calculateLinearReferenceFromPoint(location, linkGeometry)
    mValue should be(0.0)
  }

  test("Project stop location to the end of geometry if point lies beyond geometry") {
    val linkGeometry: Seq[Point] = List(Point(0.0, 0.0), Point(1.0, 0.0))
    val location: Point = Point(1.5, 0.5)
    val mValue: Double = RollbackMassTransitStopService.calculateLinearReferenceFromPoint(location, linkGeometry)
    mValue should be(1.0)
  }
}

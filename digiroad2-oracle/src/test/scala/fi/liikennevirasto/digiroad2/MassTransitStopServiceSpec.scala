package fi.liikennevirasto.digiroad2

import java.text.SimpleDateFormat
import java.util.Date

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.MassTransitStopDao
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util._
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
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
  val mockTierekisteriClient = MockitoSugar.mock[TierekisteriClient]
  when(mockTierekisteriClient.fetchMassTransitStop(any[String])).thenReturn(
    TierekisteriMassTransitStop(2, "2", RoadAddress(None, 1, 1, Track.Combined, 1, None), TRRoadSide.Unknown, StopType.Combined,
      false, equipments = Map(), None, None, None, "KX12356", None, None, None, new Date)
  )

  val vvhRoadLinks = List(
    VVHRoadlink(1611353, 90, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
    VVHRoadlink(1021227, 90, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
    VVHRoadlink(1021226, 90, Nil, Private, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
    VVHRoadlink(123l, 91, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
    VVHRoadlink(131573L, 235, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
    VVHRoadlink(6488445, 235, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
    VVHRoadlink(1611353, 235, Seq(Point(374603.57,6677262.009), Point(374684.567, 6677277.323)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
    VVHRoadlink(1611341l, 91, Seq(Point(374375.156,6677244.904), Point(374567.632, 6677255.6)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
     VVHRoadlink(1l, 235, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
    VVHRoadlink(1611601L, 235, Seq(Point(374668.195,6676884.282), Point(374805.498, 6676906.051)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers))

  val mockVVHClient = MockitoSugar.mock[VVHClient]
  when(mockVVHClient.fetchVVHRoadlinks(any[BoundingRectangle], any[Set[Int]])).thenReturn(vvhRoadLinks)
  vvhRoadLinks.foreach(rl =>
    when(mockVVHClient.fetchVVHRoadlink(rl.linkId))
    .thenReturn(Some(rl)))
  when(mockVVHClient.fetchVVHRoadlinks(any[Set[Long]])).thenReturn(vvhRoadLinks)
  val mockGeometryTransform = MockitoSugar.mock[GeometryTransform]
  when(mockGeometryTransform.coordToAddress(any[Point], any[Option[Int]], any[Option[Int]], any[Option[Int]], any[Option[Track]], any[Option[Double]], any[Option[Boolean]])).thenReturn(
    RoadAddress(Option("235"), 1, 1, Track.Combined, 0, None)
  )
  when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Option[Int]], any[Option[Int]], any[Option[Boolean]])).thenReturn(
    (RoadAddress(Option("235"), 1, 1, Track.Combined, 0, None), RoadSide.Right)
  )
  class TestMassTransitStopService(val eventbus: DigiroadEventBus) extends MassTransitStopService {
    override def withDynSession[T](f: => T): T = f
    override def withDynTransaction[T](f: => T): T = f
    override def vvhClient: VVHClient = mockVVHClient
    override val tierekisteriClient: TierekisteriClient = mockTierekisteriClient
    override val massTransitStopDao: MassTransitStopDao = new MassTransitStopDao
    override val tierekisteriEnabled = false
    override val geometryTransform = mockGeometryTransform
  }

  class TestMassTransitStopServiceWithTierekisteri(val eventbus: DigiroadEventBus) extends MassTransitStopService {
    override def withDynSession[T](f: => T): T = f
    override def withDynTransaction[T](f: => T): T = f
    override def vvhClient: VVHClient = mockVVHClient
    override val tierekisteriClient: TierekisteriClient = mockTierekisteriClient
    override val massTransitStopDao: MassTransitStopDao = new MassTransitStopDao
    override val tierekisteriEnabled = true
    override val geometryTransform = mockGeometryTransform
  }

  object RollbackMassTransitStopService extends TestMassTransitStopService(new DummyEventBus)

  object RollbackMassTransitStopServiceWithTierekisteri extends TestMassTransitStopServiceWithTierekisteri(new DummyEventBus)

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  test("update inventory date") {
    val props = Seq(SimpleProperty("foo", Seq()))
    val after = RollbackMassTransitStopService.updatedProperties(props)
    after should have size (2)
    val after2 = RollbackMassTransitStopService.updatedProperties(after)
    after2 should have size (2)
  }

  test("update empty inventory date") {
    val props = Seq(SimpleProperty("inventointipaiva", Seq()))
    val after = RollbackMassTransitStopService.updatedProperties(props)
    after should have size (1)
    after.head.values should have size(1)
    after.head.values.head.propertyValue should be ( DateTimeFormat.forPattern("yyyy-MM-dd").print(DateTime.now))
  }

  test("do not update existing inventory date") {
    val props = Seq(SimpleProperty("inventointipaiva", Seq(PropertyValue("2015-12-30"))))
    val after = RollbackMassTransitStopService.updatedProperties(props)
    after should have size (1)
    after.head.values should have size(1)
    after.head.values.head.propertyValue should be ( "2015-12-30")
  }

  test("Calculate mass transit stop validity periods") {
    runWithRollback {
      val massTransitStops = RollbackMassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBoxWithKauniainenAssets)
      massTransitStops.find(_.id == 300000).flatMap(_.validityPeriod) should be(Some(MassTransitStopValidityPeriod.Current))
      massTransitStops.find(_.id == 300001).flatMap(_.validityPeriod) should be(Some(MassTransitStopValidityPeriod.Past))
      massTransitStops.find(_.id == 300003).flatMap(_.validityPeriod) should be(Some(MassTransitStopValidityPeriod.Future))
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
      val vvhRoadLink = VVHRoadlink(11, 235, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)
      val id = RollbackMassTransitStopService.create(NewMassTransitStop(5.0, 0.0, 1l, 2, Nil), "masstransitstopservice_spec", vvhRoadLink.geometry, vvhRoadLink.municipalityCode, Some(vvhRoadLink.administrativeClass))
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

  test("Stop floats if a State road has got changed to a road owned by municipality"){
    val massTransitStopDao = new MassTransitStopDao
    runWithRollback{
      val assetId = 300006
      val boundingBox = BoundingRectangle(Point(370000,6077000), Point(374800,6677600))
      //Set administration class of the asset with State value
      RollbackMassTransitStopService.updateAdministrativeClassValue(assetId, State)
      val stops = RollbackMassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBox)
      stops.find(_.id == assetId).map(_.floating) should be(Some(true))
      massTransitStopDao.getAssetFloatingReason(assetId) should be(Some(FloatingReason.RoadOwnerChanged))
    }
  }

  test("Stop floats if a State road has got changed to a road owned to a private road"){
    val massTransitStopDao = new MassTransitStopDao
    runWithRollback{
      val assetId = 300012
      val boundingBox = BoundingRectangle(Point(370000,6077000), Point(374800,6677600))
      //Set administration class of the asset with State value
      RollbackMassTransitStopService.updateAdministrativeClassValue(assetId, State)
      val stops = RollbackMassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBox)
      stops.find(_.id == assetId).map(_.floating) should be(Some(true))
      massTransitStopDao.getAssetFloatingReason(assetId) should be(Some(FloatingReason.RoadOwnerChanged))
    }
  }

  test("Stops working list shouldn't have floating assets with floating reason RoadOwnerChanged if user is not operator"){
    val massTransitStopDao = new MassTransitStopDao
    runWithRollback {
      val assetId = 300012
      val boundingBox = BoundingRectangle(Point(370000,6077000), Point(374800,6677600))
      //Set administration class of the asset with State value
      RollbackMassTransitStopService.updateAdministrativeClassValue(assetId, State)
      //GetBoundingBox will set assets  to floating
      RollbackMassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBox)
      val workingList = RollbackMassTransitStopService.getFloatingAssets(Some(Set(235)), Some(false))
      //Get all external ids from the working list
      val externalIds = workingList.map(m => m._2.map(a => a._2).flatten).flatten

      //Should not find any external id of the asset with administration class changed
      externalIds.foreach{ externalId =>
        externalId should not be (8)
      }
    }
  }

  test("Stops working list should have all floating assets if user is operator"){
    val massTransitStopDao = new MassTransitStopDao
    runWithRollback {
      val assetId = 300012
      val boundingBox = BoundingRectangle(Point(370000,6077000), Point(374800,6677600))
      //Set administration class of the asset with State value
      RollbackMassTransitStopService.updateAdministrativeClassValue(assetId, State)
      //GetBoundingBox will set assets  to floating
      RollbackMassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBox)
      val workingList = RollbackMassTransitStopService.getFloatingAssets(Some(Set(235)), Some(true))
      //Get all external ids from the working list
      val externalIds = workingList.map(m => m._2.map(a => a._2).flatten).flatten

      //Should have the external id of the asset with administration class changed
      externalIds.find(_ == 8) should be(Some(8))
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
      val equipments = Map[Equipment, Existence](
        Equipment.BikeStand -> Existence.Yes,
        Equipment.CarParkForTakingPassengers -> Existence.Unknown,
        Equipment.ElectronicTimetables -> Existence.Yes,
        Equipment.RaisedBusStop -> Existence.No,
        Equipment.Lighting -> Existence.Unknown,
        Equipment.Roof -> Existence.Yes,
        Equipment.Seat -> Existence.Unknown,
        Equipment.Timetable -> Existence.No,
        Equipment.TrashBin -> Existence.Yes,
        Equipment.RoofMaintainedByAdvertiser -> Existence.Yes
      )
      val roadAddress = RoadAddress(None, 0, 0, Track.Unknown, 0, None)
      when(mockTierekisteriClient.fetchMassTransitStop("OTHJ85755")).thenReturn(
        TierekisteriMassTransitStop(85755, "OTHJ85755", roadAddress, TRRoadSide.Unknown, StopType.Unknown, false, equipments, None, Option("TierekisteriFi"), Option("TierekisteriSe"), "test", Option(new Date), Option(new Date), Option(new Date), new Date(2016,8,1))
      )
      val stop = RollbackMassTransitStopService.getMassTransitStopByNationalId(85755, _ => Unit)
      stop.map(_.floating) should be(Some(true))
    }
  }

  test("Fetch mass transit stop enriched by tierekisteri by national id"){

    runWithRollback{
      val roadAddress = RoadAddress(None, 0, 0, Track.Unknown, 0, None)
      val equipments = Map[Equipment, Existence](
        Equipment.BikeStand -> Existence.Yes,
        Equipment.CarParkForTakingPassengers -> Existence.Unknown,
        Equipment.ElectronicTimetables -> Existence.Yes,
        Equipment.RaisedBusStop -> Existence.No,
        Equipment.Lighting -> Existence.Unknown,
        Equipment.Roof -> Existence.Yes,
        Equipment.Seat -> Existence.Unknown,
        Equipment.Timetable -> Existence.No,
        Equipment.TrashBin -> Existence.Yes,
        Equipment.RoofMaintainedByAdvertiser -> Existence.Yes
      )
      when(mockTierekisteriClient.fetchMassTransitStop("OTHJ85755")).thenReturn(
        TierekisteriMassTransitStop(85755, "OTHJ85755", roadAddress, TRRoadSide.Unknown, StopType.Unknown, false, equipments, None, Option("TierekisteriFi"), Option("TierekisteriSe"), "test", Option(new Date), Option(new Date), Option(new Date), new Date(2016, 9, 2))
      )

      val stop = RollbackMassTransitStopServiceWithTierekisteri.getMassTransitStopByNationalId(85755, _ => Unit)
      equipments.foreach{
        case (equipment, existence) =>
          val property = stop.map(_.propertyData).get.find(p => p.publicId == equipment.publicId).get
          property.values should have size (1)
          property.values.head.propertyValue should be(existence.propertyValue.toString)
      }
    }
  }

  test("Get properties") {
    runWithRollback {
      val massTransitStop = RollbackMassTransitStopService.getMassTransitStopByNationalId(2, Int => Unit).map { stop =>
        Map("id" -> stop.id,
          "nationalId" -> stop.nationalId,
          "stopTypes" -> stop.stopTypes,
          "lat" -> stop.lat,
          "lon" -> stop.lon,
          "validityDirection" -> stop.validityDirection,
          "bearing" -> stop.bearing,
          "validityPeriod" -> stop.validityPeriod,
          "floating" -> stop.floating,
          "propertyData" -> stop.propertyData)
      }
    }
  }

  test("Assert user rights when fetching mass transit stop with id") {
    runWithRollback {
      an [Exception] should be thrownBy RollbackMassTransitStopService.getMassTransitStopByNationalId(85755, { municipalityCode => throw new Exception })
    }
  }

  test("Update mass transit stop road link mml id") {
    runWithRollback {
      val geom = Point(374708, 6676905)
      val position = Some(Position(geom.x, geom.y, 1611601L, Some(85)))
      RollbackMassTransitStopService.updateExistingById(300000, position, Set.empty, "user", _ => Unit)
      val linkId = sql"""
            select lrm.link_id from asset a
            join asset_link al on al.asset_id = a.id
            join lrm_position lrm on lrm.id = al.position_id
            where a.id = 300000
      """.as[Long].firstOption
      linkId should be(Some(1611601L))
    }
  }

  test("Update mass transit stop bearing") {
    runWithRollback {
      val geom = Point(375621, 6676556)
      val position = Some(Position(geom.x, geom.y, 1611341l, Some(90)))
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
      val geom = Point(375621, 6676556)
      val position = Some(Position(geom.x, geom.y, 1611341l, Some(85)))
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

  test("Update asset liVi identifier property when is Central ELY administration"){
    runWithRollback {
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus)
      val assetId = 300000
      val properties = List(
        SimpleProperty("tietojen_yllapitaja", List(PropertyValue("2"))),
        SimpleProperty("yllapitajan_koodi", List(PropertyValue("livi"))))
      val position = Some(Position(60.0, 0.0, 123l, None))
      RollbackMassTransitStopService.updateExistingById(assetId, position, properties.toSet, "user", _ => Unit)
      val massTransitStop = service.getById(assetId).get

      //The property yllapitajan_koodi should be overridden with OTHJ + NATIONAL ID
      val liviIdentifierProperty = massTransitStop.propertyData.find(p => p.publicId == "yllapitajan_koodi").get
      liviIdentifierProperty.values.head.propertyValue should be("OTHJ%d".format(massTransitStop.nationalId))

    }
  }

  test("Update asset liVi identifier property when is NOT Central ELY administration"){
    runWithRollback {
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus)
      val assetId = 300000
      val properties = List(
        SimpleProperty("tietojen_yllapitaja", List(PropertyValue("1"))),
        SimpleProperty("yllapitajan_koodi", List(PropertyValue("livi"))))
      val position = Some(Position(60.0, 0.0, 123l, None))
      RollbackMassTransitStopService.updateExistingById(assetId, position, properties.toSet, "user", _ => Unit)
      val massTransitStop = service.getById(assetId).get

      //The property yllapitajan_koodi should not have values
      val liviIdentifierProperty = massTransitStop.propertyData.find(p => p.publicId == "yllapitajan_koodi").get
      liviIdentifierProperty.values.size should be(0)

    }
  }

  test("Update last modified info") {
    runWithRollback {
      val geom = Point(375621, 6676556)
      val pos = Position(geom.x, geom.y, 131573L, Some(85))
      RollbackMassTransitStopService.updateExistingById(300000, Some(pos), Set.empty, "user", _ => Unit)
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
      val properties = List(
        SimpleProperty("pysakin_tyyppi", List(PropertyValue("1"))),
        SimpleProperty("tietojen_yllapitaja", List(PropertyValue("1"))),
        SimpleProperty("yllapitajan_koodi", List(PropertyValue("livi"))))
      val vvhRoadLink = VVHRoadlink(123l, 91, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)
      val id = service.create(NewMassTransitStop(60.0, 0.0, 123l, 100, properties), "test", vvhRoadLink.geometry, vvhRoadLink.municipalityCode, Some(vvhRoadLink.administrativeClass))
      val massTransitStop = service.getById(id).get
      massTransitStop.bearing should be(Some(100))
      massTransitStop.floating should be(false)
      massTransitStop.stopTypes should be(List(1))
      massTransitStop.validityPeriod should be(Some(MassTransitStopValidityPeriod.Current))

      //The property yllapitajan_koodi should not have values
      val liviIdentifierProperty = massTransitStop.propertyData.find(p => p.publicId == "yllapitajan_koodi").get
      liviIdentifierProperty.values.size should be(0)

      verify(eventbus).publish(org.mockito.Matchers.eq("asset:saved"), any[EventBusMassTransitStop]())
    }
  }

  test("Create new virtual mass transit stop with Central ELY administration") {
    runWithRollback {
      val massTransitStopDao = new MassTransitStopDao
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus)
      val properties = List(
        SimpleProperty("pysakin_tyyppi", List(PropertyValue("5"))),
        SimpleProperty("tietojen_yllapitaja", List(PropertyValue("2"))),
        SimpleProperty("yllapitajan_koodi", List(PropertyValue("livi"))))
      val vvhRoadLink = VVHRoadlink(123l, 91, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)
      val id = service.create(NewMassTransitStop(60.0, 0.0, 123l, 100, properties), "test", vvhRoadLink.geometry, vvhRoadLink.municipalityCode, Some(vvhRoadLink.administrativeClass))
      val massTransitStop = service.getById(id).get
      massTransitStop.bearing should be(Some(100))
      massTransitStop.floating should be(false)
      massTransitStop.stopTypes should be(List(5))
      massTransitStop.validityPeriod should be(Some(MassTransitStopValidityPeriod.Current))

      //The property yllapitajan_koodi should not have values
      val liviIdentifierProperty = massTransitStop.propertyData.find(p => p.publicId == "yllapitajan_koodi").get
      liviIdentifierProperty.values.size should be(0)

      verify(eventbus).publish(org.mockito.Matchers.eq("asset:saved"), any[EventBusMassTransitStop]())
    }
  }

  test("Create new mass transit stop with Central ELY administration") {
    runWithRollback {
      val massTransitStopDao = new MassTransitStopDao
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopServiceWithTierekisteri(eventbus)
      val properties = List(
        SimpleProperty("pysakin_tyyppi", List(PropertyValue("1"))),
        SimpleProperty("tietojen_yllapitaja", List(PropertyValue("2"))),
        SimpleProperty("yllapitajan_koodi", List(PropertyValue("livi"))),
        SimpleProperty("ensimmainen_voimassaolopaiva", List(PropertyValue("2013-01-01"))),
        SimpleProperty("viimeinen_voimassaolopaiva", List(PropertyValue("2017-01-01"))))
      val vvhRoadLink = VVHRoadlink(123l, 91, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)
      val id = service.create(NewMassTransitStop(60.0, 0.0, 123l, 100, properties), "test", vvhRoadLink.geometry, vvhRoadLink.municipalityCode, Some(vvhRoadLink.administrativeClass))
      val massTransitStop = service.getById(id).get
      massTransitStop.bearing should be(Some(100))
      massTransitStop.floating should be(false)
      massTransitStop.stopTypes should be(List(1))
      massTransitStop.validityPeriod should be(Some(MassTransitStopValidityPeriod.Current))

      //The property yllapitajan_koodi should be overridden with OTHJ + NATIONAL ID
      val liviIdentifierProperty = massTransitStop.propertyData.find(p => p.publicId == "yllapitajan_koodi").get
      liviIdentifierProperty.values.head.propertyValue should be("OTHJ%d".format(massTransitStop.nationalId))

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

  test("expire a mass transit stop") {
    runWithRollback {
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus)
      val properties = List(
        SimpleProperty("pysakin_tyyppi", List(PropertyValue("1"))),
        SimpleProperty("tietojen_yllapitaja", List(PropertyValue("1"))),
        SimpleProperty("yllapitajan_koodi", List(PropertyValue("livi"))))
      val vvhRoadLink = VVHRoadlink(123l, 91, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)
      val createdId = service.create(NewMassTransitStop(60.0, 0.0, 123l, 100, properties), "test", vvhRoadLink.geometry, vvhRoadLink.municipalityCode, Some(vvhRoadLink.administrativeClass))
      val massTransitStopAsset = sql"""select id, municipality_code, valid_from, valid_to from asset where id = $createdId""".as[(Long, Int, String, String)].firstOption
      massTransitStopAsset should be (Some(createdId, vvhRoadLink.municipalityCode, null, null))

      service.expireMassTransitStop("testusername", createdId)

      val expired = sql"""select case when a.valid_to <= sysdate then 1 else 0 end as expired from asset a where id = $createdId""".as[(Boolean)].firstOption
      expired should be(Some(true))
    }
  }

  test ("Convert PersistedMassTransitStop into TierekisteriMassTransitStop") {
    def massTransitStopTransformation(stop: PersistedMassTransitStop): (PersistedMassTransitStop, Option[FloatingReason]) = {
      (stop, None)
    }
    val dateFormatter = new SimpleDateFormat("yyyy-MM-dd")
    runWithRollback {
      val assetId = 300006
      val stopOption = RollbackMassTransitStopService.fetchPointAssets((s:String) => s"""$s where a.id = $assetId""").headOption
      stopOption.isEmpty should be (false)
      val stop = stopOption.get
      val transform = new GeometryTransform
      val geom = Point(375621, 6676556)
      val (address, roadSide) = transform.resolveAddressAndLocation(geom, stop.bearing.get)
      val trStop = TierekisteriBusStopMarshaller.toTierekisteriMassTransitStop(stop, address, Option(roadSide))
      roadSide should be (RoadSide.Left)
      trStop.nationalId should be (stop.nationalId)
      trStop.stopType should be (StopType.LongDistance)
      trStop.equipments.get(Equipment.Roof).get should be (Existence.Yes)
      trStop.equipments.filterNot( x => x._1 == Equipment.Roof).forall(_._2 == Existence.Unknown) should be (true)
      trStop.operatingFrom.isEmpty should be (false)
      trStop.operatingTo.isEmpty should be (false)
      val opFrom = stop.propertyData.find(_.publicId=="ensimmainen_voimassaolopaiva").flatMap(_.values.headOption.map(_.propertyValue))
      val opTo = stop.propertyData.find(_.publicId=="viimeinen_voimassaolopaiva").flatMap(_.values.headOption.map(_.propertyValue))
      opFrom shouldNot be (None)
      opTo shouldNot be (None)
      dateFormatter.format(trStop.operatingFrom.get) should be (opFrom.get)
      dateFormatter.format(trStop.operatingTo.get) should be (opTo.get)
    }
  }
}

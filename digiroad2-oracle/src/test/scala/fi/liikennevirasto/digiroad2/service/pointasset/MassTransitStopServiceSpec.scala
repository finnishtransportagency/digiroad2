package fi.liikennevirasto.digiroad2.service.pointasset

import java.text.SimpleDateFormat
import java.util.Date

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.tierekisteri._
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, VVHClient, VVHRoadLinkClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.{MassTransitStopDao, MunicipalityDao, MunicipalityInfo, Sequences}
import fi.liikennevirasto.digiroad2.dao.{RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop._
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util._
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.mockito.{ArgumentCaptor, Mockito}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

class MassTransitStopServiceSpec extends FunSuite with Matchers with BeforeAndAfter {
  val mockRoadAddressService = MockitoSugar.mock[RoadAddressService]
  val geometryTransform = new GeometryTransform(mockRoadAddressService)
  val boundingBoxWithKauniainenAssets = BoundingRectangle(Point(374000,6677000), Point(374800,6677600))
  val userWithKauniainenAuthorization = User(
    id = 1,
    username = "Hannu",
    configuration = Configuration(authorizedMunicipalities = Set(235)))
  val mockTierekisteriClient = MockitoSugar.mock[TierekisteriMassTransitStopClient]
  when(mockTierekisteriClient.fetchMassTransitStop(any[String])).thenReturn(Some(
    TierekisteriMassTransitStop(2, "2", RoadAddress(None, 1, 1, Track.Combined, 1), TRRoadSide.Unknown, StopType.Combined,
      false, equipments = Map(), None, None, None, "KX12356", None, None, None, new Date))
  )
  when(mockTierekisteriClient.isTREnabled).thenReturn(false)

  val vvhRoadLinks = List(
    VVHRoadlink(1611353, 90, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
    VVHRoadlink(1021227, 90, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
    VVHRoadlink(1021226, 90, Nil, Private, TrafficDirection.AgainstDigitizing, FeatureClass.AllOthers),
    VVHRoadlink(123l, 91, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
    VVHRoadlink(12333l, 91, List(Point(0.0,0.0), Point(120.0, 0.0)), State, TrafficDirection.BothDirections, FeatureClass.AllOthers),
    VVHRoadlink(131573L, 235, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
    VVHRoadlink(6488445, 235, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
    VVHRoadlink(1611353, 235, Seq(Point(374603.57,6677262.009), Point(374684.567, 6677277.323)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
    VVHRoadlink(1611341l, 91, Seq(Point(374375.156,6677244.904), Point(374567.632, 6677255.6)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
    VVHRoadlink(1l, 235, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
    VVHRoadlink(1611601L, 235, Seq(Point(374668.195,6676884.282), Point(374805.498, 6676906.051)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
    VVHRoadlink(1237l, 91, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.AgainstDigitizing, FeatureClass.AllOthers),
    VVHRoadlink(12345l, 91, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, attributes = Map("ROADNAME_SE" -> "roadname_se",
      "ROADNAME_FI" -> "roadname_fi")))

  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val mockGeometryTransform = MockitoSugar.mock[GeometryTransform]
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]

  def toRoadLink(l: VVHRoadlink) = {
    RoadLink(l.linkId, l.geometry, GeometryUtils.geometryLength(l.geometry),
      l.administrativeClass, 1, l.trafficDirection, UnknownLinkType, None, None, l.attributes + ("MUNICIPALITYCODE" -> BigInt(l.municipalityCode)))
  }
  before {
    // Reset the mocks here so individual tests don't have to
    when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
    when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBounds(any[BoundingRectangle], any[Set[Int]])).thenReturn(vvhRoadLinks)
    vvhRoadLinks.foreach(rl =>
      when(mockVVHRoadLinkClient.fetchByLinkId(rl.linkId))
        .thenReturn(Some(rl)))
    when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(vvhRoadLinks.map(toRoadLink))
    vvhRoadLinks.foreach(rl =>
      when(mockRoadLinkService.getRoadLinkByLinkIdFromVVH(rl.linkId, false))
        .thenReturn(Some(toRoadLink(rl))))
    when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean])).thenReturn(vvhRoadLinks.map(toRoadLink))
    when(mockRoadLinkService.getRoadLinkAndComplementaryFromVVH(any[Long], any[Boolean])).thenReturn(Some(toRoadLink(VVHRoadlink(1611601L, 91, Seq(Point(374668.195,6676884.282), Point(374805.498, 6676906.051)), State, TrafficDirection.BothDirections, FeatureClass.AllOthers))))
    when(mockRoadLinkService.getHistoryDataLinkFromVVH(any[Long], any[Boolean])).thenReturn(None)

  }
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  object RollbackBusStopStrategy extends BusStopStrategy(10, new MassTransitStopDao, mockRoadLinkService, mockEventBus, mockGeometryTransform)
  {
    def updateAdministrativeClassValueTest(assetId: Long, administrativeClass: AdministrativeClass): Unit ={
      super.updateAdministrativeClassValue(assetId, administrativeClass)
    }
  }

  class TestMassTransitStopService(val eventbus: DigiroadEventBus, val roadLinkService: RoadLinkService) extends MassTransitStopService {
    override def withDynSession[T](f: => T): T = f
    override def withDynTransaction[T](f: => T): T = f
    override val tierekisteriClient: TierekisteriMassTransitStopClient = mockTierekisteriClient
    override val massTransitStopDao: MassTransitStopDao = new MassTransitStopDao
    override val municipalityDao: MunicipalityDao = new MunicipalityDao
    override val geometryTransform: GeometryTransform = mockGeometryTransform
  }

  class TestMassTransitStopServiceWithTierekisteri(val eventbus: DigiroadEventBus, val roadLinkService: RoadLinkService) extends MassTransitStopService {
    override def withDynSession[T](f: => T): T = f
    override def withDynTransaction[T](f: => T): T = f
    override val tierekisteriClient: TierekisteriMassTransitStopClient = mockTierekisteriClient
    override val massTransitStopDao: MassTransitStopDao = new MassTransitStopDao
    override val municipalityDao: MunicipalityDao = new MunicipalityDao
    override val geometryTransform: GeometryTransform = mockGeometryTransform
  }

  object RollbackMassTransitStopService extends TestMassTransitStopService(new DummyEventBus, mockRoadLinkService)

  object RollbackMassTransitStopServiceWithTierekisteri extends TestMassTransitStopServiceWithTierekisteri(new DummyEventBus, mockRoadLinkService)

  class TestMassTransitStopServiceWithDynTransaction(val eventbus: DigiroadEventBus, val roadLinkService: RoadLinkService) extends MassTransitStopService {
    override def withDynSession[T](f: => T): T = TestTransactions.withDynSession()(f)
    override def withDynTransaction[T](f: => T): T = TestTransactions.withDynTransaction()(f)
    override val tierekisteriClient: TierekisteriMassTransitStopClient = mockTierekisteriClient
    override val massTransitStopDao: MassTransitStopDao = new MassTransitStopDao
    override val municipalityDao: MunicipalityDao = new MunicipalityDao
    override val geometryTransform: GeometryTransform = mockGeometryTransform
  }

  class MassTransitStopServiceWithTierekisteri(val eventbus: DigiroadEventBus, val roadLinkService: RoadLinkService) extends MassTransitStopService {
    override val tierekisteriClient: TierekisteriMassTransitStopClient = mockTierekisteriClient
    override val massTransitStopDao: MassTransitStopDao = new MassTransitStopDao
    override val municipalityDao: MunicipalityDao = new MunicipalityDao
    override val geometryTransform: GeometryTransform = mockGeometryTransform
  }

  def runWithRollback(test: => Unit): Unit = assetLock.synchronized {
    TestTransactions.runWithRollback()(test)
  }

  val assetLock = "Used to prevent deadlocks"

  test("create and update inventory date propertie") {

    val props = Seq(SimplePointAssetProperty("foo", Seq()))
    val roadLink = VVHRoadlink(12345l, 91, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)

    val after = MassTransitStopOperations.setPropertiesDefaultValues(props, roadLink)
    after should have size (4)
    after.filter(_.publicId == MassTransitStopOperations.InventoryDateId ) should have size (1)
    val after2 = MassTransitStopOperations.setPropertiesDefaultValues(after, roadLink)
    after2 should have size (4)
  }

  test("update empty inventory date") {
    val roadLink = VVHRoadlink(12345l, 91, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)
    val props = Seq(SimplePointAssetProperty(MassTransitStopOperations.InventoryDateId, Seq()))
    val after = MassTransitStopOperations.setPropertiesDefaultValues(props, roadLink)
    after should have size (3)
    after.filter(_.publicId == MassTransitStopOperations.InventoryDateId ) should have size(1)
    after.filter(_.publicId == MassTransitStopOperations.InventoryDateId ).head.values.head.asInstanceOf[PropertyValue].propertyValue should be ( DateTimeFormat.forPattern("yyyy-MM-dd").print(DateTime.now))
  }

  test("do not update existing inventory date") {
    val roadLink = VVHRoadlink(12345l, 91, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)
    val props = Seq(SimplePointAssetProperty(MassTransitStopOperations.InventoryDateId, Seq(PropertyValue("2015-12-30"))))
    val after = MassTransitStopOperations.setPropertiesDefaultValues(props, roadLink)
    after should have size (3)
    after.filter(_.publicId == MassTransitStopOperations.InventoryDateId ) should have size(1)
    after.filter(_.publicId == MassTransitStopOperations.InventoryDateId ).head.values should have size(1)
    after.filter(_.publicId == MassTransitStopOperations.InventoryDateId ).head.values.head.asInstanceOf[PropertyValue].propertyValue should be ( "2015-12-30")
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
      massTransitStops.find(_.id == 300000).get.stopTypes should be(Seq(2))
      massTransitStops.find(_.id == 300001).get.stopTypes.sorted should be(Seq(2, 3, 4))
      massTransitStops.find(_.id == 300003).get.stopTypes.sorted should be(Seq(2, 3))
    }
  }

  test("Get stops by bounding box") {
    runWithRollback {
      val roadLink = RoadLink(11, List(Point(0.0,0.0), Point(120.0, 0.0)), 120, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = RollbackMassTransitStopService.create(NewMassTransitStop(5.0, 0.0, 1l, 2,
        Seq(
          SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("1"))),
          SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("2"))),
          SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("2")))
        )), "masstransitstopservice_spec", roadLink)
      val stops = RollbackMassTransitStopService.getByBoundingBox(
        userWithKauniainenAuthorization, BoundingRectangle(Point(0.0, 0.0), Point(10.0, 10.0)))
      stops.map(_.id) should be(Seq(id))
    }
  }

  test("Stops should not be filtered by authorization") {
    runWithRollback {
      val stops = RollbackMassTransitStopService.getByBoundingBox(User(0, "test", Configuration()), boundingBoxWithKauniainenAssets)
      stops should not be(empty)
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

  test("Persist mass transit stop floating direction not match") {
    runWithRollback {
    val massTransitStopDao = new MassTransitStopDao
    val roadLink = RoadLink(1237l, List(Point(0.0,0.0), Point(120.0, 0.0)), 120, Municipality, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val id = RollbackMassTransitStopService.create(NewMassTransitStop(5.0, 0.0, 1237l, 2,
      Seq(
        SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("1"))),
        SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("2"))),
        SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("2"))) //TowardsDigitizin
      )), "masstransitstopservice_spec", roadLink)

      val stops = RollbackMassTransitStopService.getByBoundingBox(
      userWithKauniainenAuthorization, BoundingRectangle(Point(0.0, 0.0), Point(10.0, 10.0)))
      stops.find(_.id == id).map(_.floating) should be(Some(true))
      massTransitStopDao.getAssetFloatingReason(id) should be(Some(FloatingReason.TrafficDirectionNotMatch))
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
      val roadAddress = RoadAddress(None, 0, 0, Track.Unknown, 0)
      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[Long], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn((roadAddress , RoadSide.Right))
      when(mockTierekisteriClient.fetchMassTransitStop("OTHJ85755")).thenReturn(Some(
        TierekisteriMassTransitStop(85755, "OTHJ85755", roadAddress, TRRoadSide.Unknown, StopType.Unknown, false, equipments, None, Option("TierekisteriFi"), Option("TierekisteriSe"), "test", Option(new Date), Option(new Date), Option(new Date), new Date(2016,8,1))
      ))
      val (stop, showStatusCode, municipalityCode) = RollbackMassTransitStopService.getMassTransitStopByNationalIdWithTRWarnings(85755)
      stop.map(_.floating) should be(Some(true))

      showStatusCode should be (false)
    }
  }

  test("Fetch mass transit stop enriched by tierekisteri by national id"){

    runWithRollback{
      val roadAddress = RoadAddress(None, 0, 0, Track.Unknown, 0)
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
      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[Long], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn((roadAddress, RoadSide.Left))
      when(mockTierekisteriClient.fetchMassTransitStop("OTHJ85755")).thenReturn(Some(
        TierekisteriMassTransitStop(85755, "OTHJ85755", roadAddress, TRRoadSide.Unknown, StopType.Unknown, false, equipments, None, Option("TierekisteriFi"), Option("TierekisteriSe"), "test", Option(new Date), Option(new Date), Option(new Date), new Date(2016, 9, 2))
      ))

      val (stop, showStatusCode, municipalityCode) = RollbackMassTransitStopServiceWithTierekisteri.getMassTransitStopByNationalIdWithTRWarnings(85755)
      equipments.foreach{
        case (equipment, existence) if(equipment.isMaster) =>
          val property = stop.map(_.propertyData).get.find(p => p.publicId == equipment.publicId).get
          property.values should have size (1)
          property.values.head.asInstanceOf[PropertyValue].propertyValue should be(existence.propertyValue.toString)
        case _ => ;
      }
      val name_fi = stop.get.propertyData.find(_.publicId == MassTransitStopOperations.nameFiPublicId).get.values
      val name_se = stop.get.propertyData.find(_.publicId == MassTransitStopOperations.nameSePublicId).get.values
      name_fi should have size (1)
      name_se should have size (1)
      name_fi.head.asInstanceOf[PropertyValue].propertyValue should be ("TierekisteriFi")
      name_se.head.asInstanceOf[PropertyValue].propertyValue should be ("TierekisteriSe")
      showStatusCode should be (false)
    }
  }

  test("Tierekisteri master overrides set values set to Yes"){

    runWithRollback{
      val roadAddress = RoadAddress(None, 0, 0, Track.Unknown, 0)
      val equipments = Map[Equipment, Existence](
        Equipment.BikeStand -> Existence.Yes,
        Equipment.CarParkForTakingPassengers -> Existence.Yes,
        Equipment.ElectronicTimetables -> Existence.Yes,
        Equipment.RaisedBusStop -> Existence.Yes,
        Equipment.Lighting -> Existence.Yes,
        Equipment.Roof -> Existence.Yes,
        Equipment.Seat -> Existence.Yes,
        Equipment.Timetable -> Existence.Yes,
        Equipment.TrashBin -> Existence.Yes,
        Equipment.RoofMaintainedByAdvertiser -> Existence.Yes
      )
      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[Long], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn((roadAddress, RoadSide.Left))
      when(mockTierekisteriClient.fetchMassTransitStop("OTHJ85755")).thenReturn(Some(
        TierekisteriMassTransitStop(85755, "OTHJ85755", roadAddress, TRRoadSide.Unknown, StopType.Unknown, false, equipments, None, Option("TierekisteriFi"), Option("TierekisteriSe"), "test", Option(new Date), Option(new Date), Option(new Date), new Date(2016, 9, 2)))
      )

      val (stop, showStatusCode, municipalityCode) = RollbackMassTransitStopServiceWithTierekisteri.getMassTransitStopByNationalIdWithTRWarnings(85755)
      equipments.foreach{
        case (equipment, existence) if equipment.isMaster =>
          val property = stop.map(_.propertyData).get.find(p => p.publicId == equipment.publicId).get
          property.values should have size (1)
          property.values.head.asInstanceOf[PropertyValue].propertyValue should be(existence.propertyValue.toString)
        case _ => ;
      }
      showStatusCode should be (false)
    }
  }

  test("Tierekisteri master overrides set values set to No"){

    runWithRollback{
      val roadAddress = RoadAddress(None, 0, 0, Track.Unknown, 0)
      val equipments = Map[Equipment, Existence](
        Equipment.BikeStand -> Existence.No,
        Equipment.CarParkForTakingPassengers -> Existence.No,
        Equipment.ElectronicTimetables -> Existence.No,
        Equipment.RaisedBusStop -> Existence.No,
        Equipment.Lighting -> Existence.No,
        Equipment.Roof -> Existence.No,
        Equipment.Seat -> Existence.No,
        Equipment.Timetable -> Existence.No,
        Equipment.TrashBin -> Existence.No,
        Equipment.RoofMaintainedByAdvertiser -> Existence.No
      )
      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[Long], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn(
        (roadAddress, RoadSide.Left)
      )
      when(mockTierekisteriClient.fetchMassTransitStop("OTHJ85755")).thenReturn(Some(
        TierekisteriMassTransitStop(85755, "OTHJ85755", roadAddress, TRRoadSide.Unknown, StopType.Unknown, false, equipments, None, Option("TierekisteriFi"), Option("TierekisteriSe"), "test", Option(new Date), Option(new Date), Option(new Date), new Date(2016, 9, 2)))
      )

      val (stop, showStatusCode, municipalityCode) = RollbackMassTransitStopServiceWithTierekisteri.getMassTransitStopByNationalIdWithTRWarnings(85755)
      equipments.foreach{
        case (equipment, existence) if equipment.isMaster =>
          val property = stop.map(_.propertyData).get.find(p => p.publicId == equipment.publicId).get
          property.values should have size (1)
          property.values.head.asInstanceOf[PropertyValue].propertyValue should be(existence.propertyValue.toString)
        case _ => ;
      }
      showStatusCode should be (false)
    }
  }

  test("Tierekisteri master overrides set values set to Unknown"){

    runWithRollback{
      val roadAddress = RoadAddress(None, 0, 0, Track.Unknown, 0)
      val equipments = Map[Equipment, Existence](
        Equipment.BikeStand -> Existence.Unknown,
        Equipment.CarParkForTakingPassengers -> Existence.Unknown,
        Equipment.ElectronicTimetables -> Existence.Unknown,
        Equipment.RaisedBusStop -> Existence.Unknown,
        Equipment.Lighting -> Existence.Unknown,
        Equipment.Roof -> Existence.Unknown,
        Equipment.Seat -> Existence.Unknown,
        Equipment.Timetable -> Existence.Unknown,
        Equipment.TrashBin -> Existence.Unknown,
        Equipment.RoofMaintainedByAdvertiser -> Existence.Unknown
      )
      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[Long], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn(
        (roadAddress, RoadSide.Left)
      )
      when(mockTierekisteriClient.fetchMassTransitStop("OTHJ85755")).thenReturn(Some(
        TierekisteriMassTransitStop(85755, "OTHJ85755", roadAddress, TRRoadSide.Unknown, StopType.Unknown, false, equipments, None, Option("TierekisteriFi"), Option("TierekisteriSe"), "test", Option(new Date), Option(new Date), Option(new Date), new Date(2016, 9, 2)))
      )
      val (stop, showStatusCode, municipalityCode) = RollbackMassTransitStopServiceWithTierekisteri.getMassTransitStopByNationalIdWithTRWarnings(85755)
      equipments.foreach{
        case (equipment, existence) if equipment.isMaster =>
          val property = stop.map(_.propertyData).get.find(p => p.publicId == equipment.publicId).get
          property.values should have size (1)
          property.values.head.asInstanceOf[PropertyValue].propertyValue should be(existence.propertyValue.toString)
        case _ => ;
      }
      showStatusCode should be (false)
    }
  }

  test("Fetch mass transit stop by national id but does not exist in Tierekisteri"){
    runWithRollback{
      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[Long], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn(
        (RoadAddress(None, 1, 1, Track.Combined, 0), RoadSide.Left)
      )
      when(mockTierekisteriClient.fetchMassTransitStop("OTHJ85755")).thenReturn(None)
      val (stop, showStatusCode, municipalityCode) = RollbackMassTransitStopServiceWithTierekisteri.getMassTransitStopByNationalIdWithTRWarnings(85755)

      stop.size should be (1)
      stop.map(_.nationalId).get should be (85755)
      showStatusCode should be (true)
    }
  }

  test("Get properties") {
    runWithRollback {
      val massTransitStop = RollbackMassTransitStopService.getMassTransitStopByNationalIdWithTRWarnings(2)._1.map { stop =>
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

  test("Fetching mass transit stop with id requires no rights") {
    runWithRollback {
      val stop =  RollbackMassTransitStopService.getMassTransitStopByNationalIdWithTRWarnings(85755)
      stop._1.get.id should be (300008)
    }
  }

  test("Update mass transit stop road link mml id") {
    runWithRollback {
      val geom = Point(374450, 6677250)
      val position = Some(Position(geom.x, geom.y, 1611601L, Some(85)))
      RollbackMassTransitStopService.updateExistingById(300000, position, Set.empty, "user", (_,_) => Unit)
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
      val geom = Point(374450, 6677250)
      val position = Some(Position(geom.x, geom.y, 1611341l, Some(90)))
      RollbackMassTransitStopService.updateExistingById(300000, position, Set.empty, "user", (_,_) => Unit)
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
      val geom = Point(374450, 6677250)
      val position = Some(Position(geom.x, geom.y, 1611341l, Some(85)))
      RollbackMassTransitStopService.updateExistingById(300000, position, Set.empty, "user", (_,_) => Unit)
      val municipality = sql"""
            select a.municipality_code from asset a
            join asset_link al on al.asset_id = a.id
            join lrm_position lrm on lrm.id = al.position_id
            where a.id = 300000
      """.as[Int].firstOption
      municipality should be(Some(91))
    }
  }

  test("Do not overwrite asset liVi identifier property when already administered by ELY"){
    runWithRollback {
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus, mockRoadLinkService)
      val assetId = 300000
      sqlu"""update text_property_value set value_fi='livi1' where asset_id = 300000 and value_fi = 'OTHJ1'""".execute
      val dbResult = sql"""SELECT value_fi FROM text_property_value where value_fi='livi1' and asset_id = 300000""".as[String].list
      dbResult.size should be(1)
      dbResult.head should be("livi1")
      val properties = List(
        SimplePointAssetProperty("tietojen_yllapitaja", List(PropertyValue("2"))),
        SimplePointAssetProperty("yllapitajan_koodi", List(PropertyValue("OTHJ1"))))
      val position = Some(Position(374450, 6677250, 123l, None))
      RollbackMassTransitStopService.updateExistingById(assetId, position, properties.toSet, "user", (_,_) => Unit)
      val massTransitStop = service.getById(assetId).get

      //The property yllapitajan_koodi should be overridden with OTHJ + NATIONAL ID
      val liviIdentifierProperty = massTransitStop.propertyData.find(p => p.publicId == "yllapitajan_koodi").get
      liviIdentifierProperty.values.head.asInstanceOf[PropertyValue].propertyValue should be("livi1")
    }
  }

  test("Do not overwrite LiviId of ELY/HSL stops when Tietojen ylläpitäjä is empty in csv import file") {
    runWithRollback {
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus, mockRoadLinkService)
      val assetId = 300000
      // properties in csv import file: 1;;Swedish name;;;;;;;;;;;;;;; (national id and swedish name given)
      val properties = List(
        SimplePointAssetProperty("nimi_ruotsiksi", List(PropertyValue("Swedish name"))))
      RollbackMassTransitStopService.updateExistingById(assetId, None, properties.toSet, "user", (_,_) => Unit)
      val massTransitStop = service.getById(assetId).get

      val swedishNameProperty = massTransitStop.propertyData.find(p => p.publicId == "nimi_ruotsiksi").get
      swedishNameProperty.values.head.asInstanceOf[PropertyValue].propertyValue should be("Swedish name")

      val liviIdentifierProperty = massTransitStop.propertyData.find(p => p.publicId == "yllapitajan_koodi").get
      liviIdentifierProperty.values.head.asInstanceOf[PropertyValue].propertyValue should be("OTHJ1")
    }
  }

  test("Overwrite non-existent asset liVi identifier property when administered by ELY"){
    runWithRollback {
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus, mockRoadLinkService)
      val assetId = 300000
      val propertyValueId = sql"""SELECT id FROM text_property_value where value_fi='OTHJ1' and asset_id = $assetId""".as[String].list.head
      sqlu"""update text_property_value set value_fi=null where id = cast($propertyValueId as bigint)""".execute
      val dbResult = sql"""SELECT value_fi FROM text_property_value where id = cast($propertyValueId as bigint)""".as[String].list
      dbResult.size should be(1)
      dbResult.head should be(null)
      val properties = List(
        SimplePointAssetProperty("tietojen_yllapitaja", List(PropertyValue("2"))),
        SimplePointAssetProperty("yllapitajan_koodi", List(PropertyValue("OTHJ1"))))
      val position = Some(Position(374450, 6677250, 123l, None))
      RollbackMassTransitStopService.updateExistingById(assetId, position, properties.toSet, "user", (_,_) => Unit)
      val massTransitStop = service.getById(assetId).get

      //The property yllapitajan_koodi should be overridden with OTHJ + NATIONAL ID
      val liviIdentifierProperty = massTransitStop.propertyData.find(p => p.publicId == "yllapitajan_koodi").get
      liviIdentifierProperty.values.head.asInstanceOf[PropertyValue].propertyValue should be("OTHJ1")
    }
  }

  test("Update asset liVi identifier property when is NOT Central ELY administration"){
    runWithRollback {
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus, mockRoadLinkService)
      val assetId = 300000
      val propertyValueId = sql"""SELECT id FROM text_property_value where value_fi='OTHJ1' and asset_id = $assetId""".as[String].list.head
      sqlu"""update text_property_value set value_fi='livi123' where id = cast($propertyValueId AS bigint)""".execute
      val dbResult = sql"""SELECT value_fi FROM text_property_value where id = cast($propertyValueId AS bigint)""".as[String].list
      dbResult.size should be(1)
      dbResult.head should be("livi123")
      val properties = List(
        SimplePointAssetProperty("tietojen_yllapitaja", List(PropertyValue("1"))),
        SimplePointAssetProperty("yllapitajan_koodi", List(PropertyValue("livi"))))
      val position = Some(Position(374450, 6677250, 123l, None))
      RollbackMassTransitStopService.updateExistingById(assetId, position, properties.toSet, "user", (_,_) => Unit)
      val massTransitStop = service.getById(assetId).get

      //The property yllapitajan_koodi should not have values
      val liviIdentifierProperty = massTransitStop.propertyData.find(p => p.publicId == "yllapitajan_koodi").get
      liviIdentifierProperty.values.size should be(0)
    }
  }

  test("Update last modified info") {
    runWithRollback {
      val geom = Point(374450, 6677250)
      val pos = Position(geom.x, geom.y, 131573L, Some(85))
      RollbackMassTransitStopService.updateExistingById(300000, Some(pos), Set.empty, "user", (_,_) => Unit)
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
      val properties = Set(SimplePointAssetProperty("nimi_suomeksi", values))
      RollbackMassTransitStopService.updateExistingById(300000, None, properties, "user", (_,_) => Unit)
      val modifier = sql"""
            select v.value_fi from text_property_value v
            join property p on v.property_id = p.id
            where v.asset_id = 300000 and p.public_id = 'nimi_suomeksi'
      """.as[String].firstOption
      modifier should be(Some("New name"))
    }
  }

  test("Persist floating on update") {
    // This asset is actually supposed to be floating, but updateExisting shouldn't do a floating check
    runWithRollback {
      val position = Some(Position(60.0, 0.0, 123l, None))
      sql"""
            update asset a
            set floating = '0'
            where a.id = 300002
      """.asUpdate.execute
      RollbackMassTransitStopService.updateExistingById(300002, position, Set.empty, "user", (_, _) => Unit)
      val floating = sql"""
            select a.floating from asset a
            where a.id = 300002
      """.as[Boolean].firstOption
      floating should be(Some(false))
    }
  }

  test("Send event to event bus in update") {
    runWithRollback {
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus, mockRoadLinkService)
      val position = Some(Position(60.0, 0.0, 123l, None))
      service.updateExistingById(300002, position, Set.empty, "user", (_,_) => Unit)
      verify(eventbus).publish(org.mockito.ArgumentMatchers.eq("asset:saved"), any[EventBusMassTransitStop]())
    }
  }

  test("When moving over 50 meter and expiring in TR strategy send vallu message "){
    runWithRollback {
      val linkId = 123l
      val municipalityCode = 91
      val geometry = Seq(Point(0.0,0.0), Point(120.0, 0.0))
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopServiceWithTierekisteri(eventbus, mockRoadLinkService)
      when(mockTierekisteriClient.isTREnabled).thenReturn(true)
      val properties = List(
        SimplePointAssetProperty("nimi_suomeksi", List(PropertyValue("value is copied"))),
        SimplePointAssetProperty("pysakin_tyyppi", List(PropertyValue("1"))),
        SimplePointAssetProperty("tietojen_yllapitaja", List(PropertyValue("2"))),
        SimplePointAssetProperty("yllapitajan_koodi", List(PropertyValue("livi"))),
        SimplePointAssetProperty("vaikutussuunta", List(PropertyValue("2"))),
        SimplePointAssetProperty("ensimmainen_voimassaolopaiva", List(PropertyValue("2013-01-01")))
      )

      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[Long], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn(
        (RoadAddress(Some(municipalityCode.toString), 1, 1, Track.Combined, 0), RoadSide.Left)
      )
      val roadLink = RoadLink(linkId, geometry, 120, Municipality, 1, TrafficDirection.BothDirections,
        Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
      val oldAssetId = service.create(NewMassTransitStop(0, 0, linkId, 0, properties), "test", roadLink)
      val position = Some(Position(60.0, 0.0, 123l, None))
      val updatedAsset =service.updateExistingById(oldAssetId, position, Set.empty, "user", (_,_) => Unit)
      // check if new asset has same properties as old one
      updatedAsset.propertyData.find(p=>p.publicId =="nimi_suomeksi" ).get.values
        .head.asInstanceOf[PropertyValue].propertyValue should be("value is copied")

      updatedAsset.propertyData.find(p=>p.publicId =="pysakin_tyyppi" ).get.values
        .head.asInstanceOf[PropertyValue].propertyValue should be("1")

      updatedAsset.propertyData.find(p=>p.publicId =="tietojen_yllapitaja" ).get.values
        .head.asInstanceOf[PropertyValue].propertyValue should be("2")

      updatedAsset.propertyData.find(p=>p.publicId =="vaikutussuunta" ).get.values
        .head.asInstanceOf[PropertyValue].propertyValue should be("2")

      verify(eventbus).publish(org.mockito.ArgumentMatchers.eq("asset:expired"), any[EventBusMassTransitStop]())
    }
  }

  test("Assert user rights when updating a mass transit stop") {
    runWithRollback {
      val position = Some(Position(60.0, 0.0, 123l, None))
      an [Exception] should be thrownBy RollbackMassTransitStopService.updateExistingById(300002, position, Set.empty, "user", { (municipalityCode, _) => throw new Exception })
    }
  }

  test("Create new mass transit stop") {
    runWithRollback {
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus, mockRoadLinkService)
      val properties = List(
        SimplePointAssetProperty("pysakin_tyyppi", List(PropertyValue("1"))),
        SimplePointAssetProperty("tietojen_yllapitaja", List(PropertyValue("1"))),
        SimplePointAssetProperty("yllapitajan_koodi", List(PropertyValue("livi"))),
        SimplePointAssetProperty("vaikutussuunta", List(PropertyValue("2"))))

      val roadLink = RoadLink(123l, List(Point(0.0,0.0), Point(120.0, 0.0)), 120, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)))
      val id = service.create(NewMassTransitStop(60.0, 0.0, 123l, 100, properties), "test", roadLink)
      val massTransitStop = service.getById(id).get
      massTransitStop.bearing should be(Some(100))
      massTransitStop.floating should be(false)
      massTransitStop.stopTypes should be(List(1))
      massTransitStop.validityPeriod should be(Some(MassTransitStopValidityPeriod.Current))
      mockRoadLinkService.getRoadLinkAndComplementaryFromVVH(123l, newTransaction = false).get.linkSource.value should be (1)

      //The property yllapitajan_koodi should not have values
      val liviIdentifierProperty = massTransitStop.propertyData.find(p => p.publicId == "yllapitajan_koodi").get
      liviIdentifierProperty.values.size should be(0)

      verify(eventbus).publish(org.mockito.ArgumentMatchers.eq("asset:saved"), any[EventBusMassTransitStop]())
    }
  }

  test("Create new virtual mass transit stop with Central ELY administration") {
    runWithRollback {
      val massTransitStopDao = new MassTransitStopDao
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus, mockRoadLinkService)
      val properties = List(
        SimplePointAssetProperty("pysakin_tyyppi", List(PropertyValue("5"))),
        SimplePointAssetProperty("tietojen_yllapitaja", List(PropertyValue("2"))),
        SimplePointAssetProperty("yllapitajan_koodi", List(PropertyValue("livi"))),
        SimplePointAssetProperty("vaikutussuunta", List(PropertyValue("2"))))
      val roadLink = RoadLink(123l, List(Point(0.0,0.0), Point(120.0, 0.0)), 120, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)))
      val id = service.create(NewMassTransitStop(60.0, 0.0, 123l, 100, properties), "test", roadLink)
      val massTransitStop = service.getById(id).get
      massTransitStop.bearing should be(Some(100))
      massTransitStop.floating should be(false)
      massTransitStop.stopTypes should be(List(5))
      massTransitStop.validityPeriod should be(Some(MassTransitStopValidityPeriod.Current))

      //The property yllapitajan_koodi should not have values
      val liviIdentifierProperty = massTransitStop.propertyData.find(p => p.publicId == "yllapitajan_koodi").get
      liviIdentifierProperty.values.size should be(0)

      verify(eventbus).publish(org.mockito.ArgumentMatchers.eq("asset:saved"), any[EventBusMassTransitStop]())
    }
  }

  test("Create new mass transit stop with Central ELY administration") {
    runWithRollback {
      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[Long], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn(
        (RoadAddress(None, 1, 1, Track.Combined, 0), RoadSide.Left)
      )
      val massTransitStopDao = new MassTransitStopDao
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopServiceWithTierekisteri(eventbus, mockRoadLinkService)
      val properties = List(
        SimplePointAssetProperty("pysakin_tyyppi", List(PropertyValue("1"))),
        SimplePointAssetProperty("tietojen_yllapitaja", List(PropertyValue("2"))),
        SimplePointAssetProperty("yllapitajan_koodi", List(PropertyValue("livi"))),
        SimplePointAssetProperty("vaikutussuunta", List(PropertyValue("2"))),
        SimplePointAssetProperty("ensimmainen_voimassaolopaiva", List(PropertyValue("2013-01-01"))),
        SimplePointAssetProperty("viimeinen_voimassaolopaiva", List(PropertyValue(DateTime.now().plusDays(1).toString()))))
      val roadLink = RoadLink(123l, List(Point(0.0,0.0), Point(120.0, 0.0)), 120, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)))
      val id = service.create(NewMassTransitStop(60.0, 0.0, 123l, 100, properties), "test", roadLink)
      val massTransitStop = service.getById(id).get
      massTransitStop.bearing should be(Some(100))
      massTransitStop.floating should be(false)
      massTransitStop.stopTypes should be(List(1))
      massTransitStop.validityPeriod should be(Some(MassTransitStopValidityPeriod.Current))

      //The property yllapitajan_koodi should be overridden with OTHJ + NATIONAL ID
      val liviIdentifierProperty = massTransitStop.propertyData.find(p => p.publicId == "yllapitajan_koodi").get
      liviIdentifierProperty.values.head.asInstanceOf[PropertyValue].propertyValue should be("OTHJ%d".format(massTransitStop.nationalId))

      verify(eventbus).publish(org.mockito.ArgumentMatchers.eq("asset:saved"), any[EventBusMassTransitStop]())
    }
  }

  test("Create new mass transit stop with HSL administration and 'state' road link") {
    runWithRollback {
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopServiceWithTierekisteri(eventbus, mockRoadLinkService)
      when(mockTierekisteriClient.isTREnabled).thenReturn(true)
      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[Long], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn(
        (RoadAddress(None, 1, 1, Track.Combined, 0), RoadSide.Left)
      )

      val properties = List(
        SimplePointAssetProperty("pysakin_tyyppi", List(PropertyValue("1"))),
        SimplePointAssetProperty("tietojen_yllapitaja", List(PropertyValue("3"))),
        SimplePointAssetProperty("yllapitajan_koodi", List(PropertyValue("livi"))),
        SimplePointAssetProperty("ensimmainen_voimassaolopaiva", List(PropertyValue("2013-01-01"))),
        SimplePointAssetProperty("viimeinen_voimassaolopaiva", List(PropertyValue(DateTime.now().plusDays(1).toString()))),
        SimplePointAssetProperty("linkin_hallinnollinen_luokka", List(PropertyValue("1"))),
        SimplePointAssetProperty("vaikutussuunta", List(PropertyValue("3"))))

      val roadLink = RoadLink(12333l, List(Point(0.0,0.0), Point(120.0, 0.0)), 120, State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)))
      val id = service.create(NewMassTransitStop(60.0, 0.0, 12333l, 100, properties), "test", roadLink)
      val massTransitStop = service.getById(id).get
      massTransitStop.bearing should be(Some(100))
      massTransitStop.floating should be(false)
      massTransitStop.stopTypes should be(List(1))
      massTransitStop.validityPeriod should be(Some(MassTransitStopValidityPeriod.Current))

      val administratorProperty = massTransitStop.propertyData.find(p => p.publicId == "tietojen_yllapitaja").get
      administratorProperty.values.head.asInstanceOf[PropertyValue].propertyValue should be("3")

      val administrativeClassProperty = massTransitStop.propertyData.find(p => p.publicId == "linkin_hallinnollinen_luokka").get
      administrativeClassProperty.values.head.asInstanceOf[PropertyValue].propertyValue should be("1")

      val liviIdentifierProperty = massTransitStop.propertyData.find(p => p.publicId == "yllapitajan_koodi").get
      liviIdentifierProperty.values.head.asInstanceOf[PropertyValue].propertyValue should be("OTHJ%d".format(massTransitStop.nationalId))

      val liviId = liviIdentifierProperty.values.head.asInstanceOf[PropertyValue].propertyValue

      val captor: ArgumentCaptor[TierekisteriMassTransitStop] = ArgumentCaptor.forClass(classOf[TierekisteriMassTransitStop])
      verify(mockTierekisteriClient, Mockito.atLeastOnce).createMassTransitStop(captor.capture, any[Option[String]])
      val capturedStop = captor.getValue
      capturedStop.liviId should be (liviId)

      verify(eventbus).publish(org.mockito.ArgumentMatchers.eq("asset:saved"), any[EventBusMassTransitStop]())
    }
  }

  test("Create new mass transit stop with HSL administration and 'state' road link and turn it into a municipality stop") {
    runWithRollback {
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopServiceWithTierekisteri(eventbus, mockRoadLinkService)
      val properties = List(
        SimplePointAssetProperty("pysakin_tyyppi", List(PropertyValue("1"))),
        SimplePointAssetProperty("tietojen_yllapitaja", List(PropertyValue("3"))),
        SimplePointAssetProperty("yllapitajan_koodi", List(PropertyValue("livi"))),
        SimplePointAssetProperty("ensimmainen_voimassaolopaiva", List(PropertyValue("2013-01-01"))),
        SimplePointAssetProperty("viimeinen_voimassaolopaiva", List(PropertyValue("2027-01-01"))),
        SimplePointAssetProperty("vaikutussuunta", List(PropertyValue("2"))))

      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[Long], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn(
        (RoadAddress(None, 1, 1, Track.Combined, 0), RoadSide.Left)
      )
      val roadLink = RoadLink(123l, List(Point(0.0,0.0), Point(120.0, 0.0)), 120, State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)))
      when(mockRoadLinkService.getRoadLinkByLinkIdFromVVH(123l)).thenReturn(Some(roadLink))
      val id = service.create(NewMassTransitStop(60.0, 0.0, 123l, 100, properties), "test", roadLink)

      val newProperties = Set(
        SimplePointAssetProperty("tietojen_yllapitaja", List(PropertyValue("1")))
      )

      val massTransitStop = service.updateExistingById(id, None, newProperties, "test2", (Int, _) => Unit)

      val administratorProperty = massTransitStop.propertyData.find(p => p.publicId == "tietojen_yllapitaja").get
      administratorProperty.values.head.asInstanceOf[PropertyValue].propertyValue should be("1")

      val administrativeClassProperty = massTransitStop.propertyData.find(p => p.publicId == "linkin_hallinnollinen_luokka").get
      administrativeClassProperty.values.head.asInstanceOf[PropertyValue].propertyValue should be("1")

      val captor: ArgumentCaptor[TierekisteriMassTransitStop] = ArgumentCaptor.forClass(classOf[TierekisteriMassTransitStop])
      val stringCaptor: ArgumentCaptor[Option[String]] = ArgumentCaptor.forClass(classOf[Option[String]])
      val stringCaptor2: ArgumentCaptor[Option[String]] = ArgumentCaptor.forClass(classOf[Option[String]])
      verify(mockTierekisteriClient, Mockito.atLeastOnce).updateMassTransitStop(captor.capture, stringCaptor.capture, stringCaptor2.capture)
      val trStop = captor.getValue
      stringCaptor.getValue shouldNot be (None)
      stringCaptor2.getValue shouldNot be (Some("test"))
      val liviId = stringCaptor.getValue.get

      liviId shouldNot be ("")
      trStop.operatingTo.isEmpty should be (false)
      trStop.operatingTo.get.getTime - new Date().getTime < 5*60*1000L should be (true)
    }
  }

  test ("Convert PersistedMassTransitStop into TierekisteriMassTransitStop") {
    val dateFormatter = new SimpleDateFormat("yyyy-MM-dd")
    runWithRollback {

      val dummyRoadAddress = Some(ViiteRoadAddress(1, 921, 2, Track.RightSide, 0, 299, None, None, 1641830, 0, 298.694, SideCode.BothDirections, Seq(Point(4002, 3067), Point(385258.765,7300119.103)), false, None, None, None))

      when(mockRoadAddressService.getByLrmPosition(any[Long], any[Double])).thenReturn(dummyRoadAddress)

      val assetId = 300006
      val stopOption = RollbackMassTransitStopService.fetchPointAssets((s:String) => s"""$s where a.id = $assetId""").headOption
      stopOption.isEmpty should be (false)
      val stop = stopOption.get
      val (address, roadSide) = geometryTransform.resolveAddressAndLocation(Point(0,0), 0, stop.mValue, stop.linkId, stop.validityDirection.get, Some(stop.municipalityCode))
      val trStop = TierekisteriBusStopMarshaller.toTierekisteriMassTransitStop(stop, address, Option(roadSide))
      roadSide should be (RoadSide.Left)
      trStop.nationalId should be (stop.nationalId)
      trStop.stopType should be (StopType.LongDistance)
      trStop.equipments.get(Equipment.Roof).get should be (Existence.Yes)
      trStop.equipments.filterNot( x => x._1 == Equipment.Roof).forall(_._2 == Existence.Unknown) should be (true)
      trStop.operatingFrom.isEmpty should be (false)
      trStop.operatingTo.isEmpty should be (false)
      val opFrom = stop.propertyData.find(_.publicId=="ensimmainen_voimassaolopaiva").flatMap(_.values.headOption.map(_.asInstanceOf[PropertyValue].propertyValue))
      val opTo = stop.propertyData.find(_.publicId=="viimeinen_voimassaolopaiva").flatMap(_.values.headOption.map(_.asInstanceOf[PropertyValue].propertyValue))
      opFrom shouldNot be (None)
      opTo shouldNot be (None)
      dateFormatter.format(trStop.operatingFrom.get) should be (opFrom.get)
      dateFormatter.format(trStop.operatingTo.get) should be (opTo.get)
    }
  }

  test ("Test date conversions in Marshaller") {
    val dateFormatter = new SimpleDateFormat("yyyy-MM-dd")
    runWithRollback {

      val dummyRoadAddress = Some(ViiteRoadAddress(1, 921, 2, Track.RightSide, 0, 299, None, None, 1641830, 0, 298.694, SideCode.BothDirections, Seq(Point(4002, 3067), Point(385258.765,7300119.103)), false, None, None, None))

      when(mockRoadAddressService.getByLrmPosition(any[Long], any[Double])).thenReturn(dummyRoadAddress)

      val assetId = 300006
      val stopOption = RollbackMassTransitStopService.fetchPointAssets((s:String) => s"""$s where a.id = $assetId""").headOption
      stopOption.isEmpty should be (false)
      val stop = stopOption.get
      val (address, roadSide) = geometryTransform.resolveAddressAndLocation(Point(0,0), 0, stop.mValue, stop.linkId, stop.validityDirection.get, Some(stop.municipalityCode))
      val expireDate = new Date(10487450L)
      val trStop = TierekisteriBusStopMarshaller.toTierekisteriMassTransitStop(stop, address, Option(roadSide), Some(expireDate))
      trStop.operatingTo.isEmpty should be (false)
      trStop.operatingTo.get should be (expireDate)
      val trStopNoExpireDate = TierekisteriBusStopMarshaller.toTierekisteriMassTransitStop(stop, address, Option(roadSide), None)
      trStopNoExpireDate.operatingTo.isEmpty should be (false)
      trStopNoExpireDate.operatingTo.get shouldNot be (expireDate)
    }
  }

  test("Update existing masstransitstop if the new distance is greater than 50 meters"){
    runWithRollback {
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus, mockRoadLinkService)
      val properties = List(
        SimplePointAssetProperty("pysakin_tyyppi", List(PropertyValue("1"))),
        SimplePointAssetProperty("tietojen_yllapitaja", List(PropertyValue("2"))),
        SimplePointAssetProperty("yllapitajan_koodi", List(PropertyValue("livi"))),
        SimplePointAssetProperty("vaikutussuunta", List(PropertyValue("2"))))
      val linkId = 123l
      val municipalityCode = 91
      val geometry = Seq(Point(0.0,0.0), Point(120.0, 0.0))

      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[Long], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn(
        (RoadAddress(Some(municipalityCode.toString), 1, 1, Track.Combined, 0), RoadSide.Left)
      )
      val roadLink = RoadLink(linkId, geometry, 120, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))

      val oldAssetId = service.create(NewMassTransitStop(0, 0, linkId, 0, properties), "test", roadLink)
      val oldAsset = sql"""select id, municipality_code, valid_from, valid_to from asset where id = $oldAssetId""".as[(Long, Int, String, String)].firstOption
      oldAsset should be (Some(oldAssetId, municipalityCode, null, null))

      val updatedAssetId = service.updateExistingById(oldAssetId, Some(Position(0, 51, linkId, Some(0))), Set(), "test",  (_, _) => Unit).id

      val newAsset = sql"""select id, municipality_code, valid_from, valid_to from asset where id = $updatedAssetId""".as[(Long, Int, String, String)].firstOption
      newAsset should be (Some(updatedAssetId, municipalityCode, null, null))

      val expired = sql"""select case when a.valid_to <= current_timestamp then 1 else 0 end as expired from asset a where id = $oldAssetId""".as[(Boolean)].firstOption
      expired should be(Some(true))
    }
  }

  test("Should not copy existing masstransitstop if the new distance is less or equal than 50 meters"){
    runWithRollback {
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus, mockRoadLinkService)
      val properties = List(
        SimplePointAssetProperty("pysakin_tyyppi", List(PropertyValue("1"))),
        SimplePointAssetProperty("tietojen_yllapitaja", List(PropertyValue("2"))),
        SimplePointAssetProperty("yllapitajan_koodi", List(PropertyValue("livi"))),
        SimplePointAssetProperty("vaikutussuunta", List(PropertyValue("2"))))
      val linkId = 123l
      val municipalityCode = 91
      val geometry = Seq(Point(0.0,0.0), Point(120.0, 0.0))
      val roadLink = RoadLink(linkId, geometry, 120, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))

      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[Long], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn(
        (RoadAddress(Some(municipalityCode.toString), 1, 1, Track.Combined, 0), RoadSide.Left)
      )

      val assetId = service.create(NewMassTransitStop(0, 0, linkId, 0, properties), "test", roadLink)
      val asset = sql"""select id, municipality_code, valid_from, valid_to from asset where id = $assetId""".as[(Long, Int, String, String)].firstOption
      asset should be (Some(assetId, municipalityCode, null, null))

      val updatedAssetId = service.updateExistingById(assetId, Some(Position(0, 50, linkId, Some(0))), Set(), "test",  (_, _) => Unit).id
      updatedAssetId should be(assetId)

      val expired = sql"""select case when a.valid_to <= current_timestamp then 1 else 0 end as expired from asset a where id = $assetId""".as[(Boolean)].firstOption
      expired should be(Some(false))
    }
  }

  test("delete a TR kept mass transit stop") {
    runWithRollback {
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[Long], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn(
        (RoadAddress(Option("235"), 1, 1, Track.Combined, 0), RoadSide.Left)
      )
      val service = new TestMassTransitStopServiceWithTierekisteri(eventbus, mockRoadLinkService)
      when(mockTierekisteriClient.isTREnabled).thenReturn(true)
      val (stop, showStatusCode, municipalityCode) = service.getMassTransitStopByNationalIdWithTRWarnings(85755)
      service.deleteMassTransitStopData(stop.head.id)
      verify(mockTierekisteriClient).deleteMassTransitStop("OTHJ85755")
      service.getMassTransitStopByNationalIdWithTRWarnings(85755)._1.isEmpty should be(true)
      service.getMassTransitStopByNationalIdWithTRWarnings(85755)._2 should be(false)
    }
  }

  test("Send Vallus message when deleting stop"){
    runWithRollback {
      val linkId = 123l
      val municipalityCode = 91
      val geometry = Seq(Point(0.0,0.0), Point(120.0, 0.0))
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopServiceWithTierekisteri(eventbus, mockRoadLinkService)
      when(mockTierekisteriClient.isTREnabled).thenReturn(true)
      val properties = List(
        SimplePointAssetProperty("nimi_suomeksi", List(PropertyValue("value is copied"))),
        SimplePointAssetProperty("pysakin_tyyppi", List(PropertyValue("1"))),
        SimplePointAssetProperty("tietojen_yllapitaja", List(PropertyValue("2"))),
        SimplePointAssetProperty("vaikutussuunta", List(PropertyValue("2"))),
        SimplePointAssetProperty("ensimmainen_voimassaolopaiva", List(PropertyValue("2013-01-01")))
      )

      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[Long], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn(
        (RoadAddress(Some(municipalityCode.toString), 1, 1, Track.Combined, 0), RoadSide.Left)
      )
      val roadLink = RoadLink(linkId, geometry, 120, Municipality, 1, TrafficDirection.BothDirections,
        Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
      val assetId = service.create(NewMassTransitStop(0, 0, linkId, 0, properties), "test", roadLink)
      service.deleteMassTransitStopData(assetId)
      verify(eventbus).publish(org.mockito.ArgumentMatchers.eq("asset:expired"), any[EventBusMassTransitStop]())
    }
  }

  test("delete fails if a TR kept mass transit stop is not found") {
    val eventbus = MockitoSugar.mock[DigiroadEventBus]
    val service = new TestMassTransitStopServiceWithDynTransaction(eventbus, mockRoadLinkService)
    when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[Long], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn(
      (RoadAddress(Option("235"), 1, 1, Track.Combined, 0), RoadSide.Left)
    )
    val (stop, showStatusCode, municipalityCode) = service.getMassTransitStopByNationalIdWithTRWarnings(85755)
    when(mockTierekisteriClient.deleteMassTransitStop(any[String])).thenThrow(new TierekisteriClientException("foo"))
    intercept[TierekisteriClientException] {
      when(mockTierekisteriClient.isTREnabled).thenReturn(true)
      assetLock.synchronized {
        service.deleteMassTransitStopData(stop.head.id)
      }
    }
    assetLock.synchronized {
      service.getMassTransitStopByNationalIdWithTRWarnings(85755)._1.isEmpty should be(false)
    }
  }

  test("Updating the mass transit stop from others -> ELY should create a stop in TR") {
    runWithRollback {
      reset(mockTierekisteriClient)
      val roadLink = RoadLink(11, List(Point(0.0, 0.0), Point(120.0, 0.0)), 120, State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = RollbackMassTransitStopService.create(NewMassTransitStop(5.0, 0.0, 1l, 2,
        Seq(
          SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("1"))),
          SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("2"))),
          SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("2")))
        )), "masstransitstopservice_spec", roadLink)
      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[Long], any[Int], any[Option[Int]], any[Option[Int]]
      )).thenReturn((RoadAddress(Some("235"), 110, 10, Track.Combined, 108), RoadSide.Right))
      when(mockTierekisteriClient.isTREnabled).thenReturn(true)
      when(mockTierekisteriClient.fetchMassTransitStop(any[String])).thenReturn(None)
      val service = RollbackMassTransitStopServiceWithTierekisteri
      val stop = service.getById(id).get
      val props = stop.propertyData
      val admin = props.find(_.publicId == "tietojen_yllapitaja").get
      val newAdmin = admin.copy(values = List(PropertyValue("2")))
      val types = props.find(_.publicId == "pysakin_tyyppi").get
      val newTypes = types.copy(values = List(PropertyValue("2"), PropertyValue("3")))
      admin.values.exists(_.asInstanceOf[PropertyValue].propertyValue == "1") should be(true)
      types.values.exists(_.asInstanceOf[PropertyValue].propertyValue == "2") should be(true)
      types.values.exists(_.asInstanceOf[PropertyValue].propertyValue == "3") should be(false)
      val newStop = stop.copy(stopTypes = Seq(2, 3),
        propertyData = props.filterNot(_.publicId == "tietojen_yllapitaja").filterNot(_.publicId == "pysakin_tyyppi") ++
          Seq(newAdmin, newTypes))
      val newProps = newStop.propertyData.map(prop => SimplePointAssetProperty(prop.publicId, prop.values)).toSet
      service.updateExistingById(stop.id, None, newProps, "seppo", { (Int, _) => Unit })
      verify(mockTierekisteriClient, times(1)).createMassTransitStop(any[TierekisteriMassTransitStop], any[Option[String]])
      verify(mockTierekisteriClient, times(0)).updateMassTransitStop(any[TierekisteriMassTransitStop], any[Option[String]], any[Option[String]])
    }
  }

  test("Updating the mass transit stop from others -> HSL should create a stop in TR") {
    runWithRollback {
      reset(mockTierekisteriClient)
      val roadLink = RoadLink(11, List(Point(0.0,0.0), Point(120.0, 0.0)), 120, State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = RollbackMassTransitStopService.create(NewMassTransitStop(5.0, 0.0, 1l, 2,
        Seq(
          SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("1"))),
          SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("2"))),
          SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("2")))
        )), "masstransitstopservice_spec", roadLink)
      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[Long], any[Int], any[Option[Int]], any[Option[Int]]
      )).thenReturn((RoadAddress(Some("235"), 110, 10, Track.Combined, 108), RoadSide.Right))
      when(mockTierekisteriClient.isTREnabled).thenReturn(true)
      when(mockTierekisteriClient.fetchMassTransitStop(any[String])).thenReturn(None)

      val service = RollbackMassTransitStopServiceWithTierekisteri
      val stop = service.getById(id).get
      val props = stop.propertyData
      val admin = props.find(_.publicId == "tietojen_yllapitaja").get
      val newAdmin = admin.copy(values = List(PropertyValue("3")))
      val types = props.find(_.publicId == "pysakin_tyyppi").get
      val newTypes = types.copy(values = List(PropertyValue("2"), PropertyValue("3")))
      admin.values.exists(_.asInstanceOf[PropertyValue].propertyValue == "1") should be(true)
      types.values.exists(_.asInstanceOf[PropertyValue].propertyValue == "2") should be(true)
      types.values.exists(_.asInstanceOf[PropertyValue].propertyValue == "3") should be(false)
      val newStop = stop.copy(stopTypes = Seq(2, 3),
        propertyData = props.filterNot(_.publicId == "tietojen_yllapitaja").filterNot(_.publicId == "pysakin_tyyppi") ++
          Seq(newAdmin, newTypes))
      val newProps = newStop.propertyData.map(prop => SimplePointAssetProperty(prop.publicId, prop.values)).toSet
      service.updateExistingById(stop.id, None, newProps, "seppo", { (Int, _) => Unit })
      verify(mockTierekisteriClient, times(1)).createMassTransitStop(any[TierekisteriMassTransitStop], any[Option[String]])
      verify(mockTierekisteriClient, times(0)).updateMassTransitStop(any[TierekisteriMassTransitStop], any[Option[String]], any[Option[String]])
    }
  }

  test("Saving a mass transit stop should not create a stop in TR") {
    runWithRollback {
      reset(mockTierekisteriClient)
      val roadLink = RoadLink(11, List(Point(0.0,0.0), Point(120.0, 0.0)), 120, State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = RollbackMassTransitStopService.create(NewMassTransitStop(5.0, 0.0, 1l, 2,
        Seq(
          SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("1"))),
          SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("2"))),
          SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("2")))
        )), "masstransitstopservice_spec", roadLink)
      verify(mockTierekisteriClient, times(0)).updateMassTransitStop(any[TierekisteriMassTransitStop], any[Option[String]], any[Option[String]])
      verify(mockTierekisteriClient, times(0)).createMassTransitStop(any[TierekisteriMassTransitStop], any[Option[String]])
    }
  }
  test("Updating the mass transit stop from ELY -> others should expire a stop in TR") {
    runWithRollback {
      reset(mockTierekisteriClient)
      val roadLink = RoadLink(11, List(Point(0.0,0.0), Point(120.0, 0.0)), 120, State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = RollbackMassTransitStopService.create(NewMassTransitStop(5.0, 0.0, 1l, 2,
        Seq(
          SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("2"))),
          SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("2"), PropertyValue("3"))),
          SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("2")))
        )), "masstransitstopservice_spec", roadLink)
      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[Long], any[Int], any[Option[Int]], any[Option[Int]]
      )).thenReturn((RoadAddress(Some("235"), 110, 10, Track.Combined, 108), RoadSide.Right))
      when(mockTierekisteriClient.isTREnabled).thenReturn(true)
      val service = RollbackMassTransitStopServiceWithTierekisteri
      val stop = service.getById(id).get
      val props = stop.propertyData
      val admin = props.find(_.publicId == "tietojen_yllapitaja").get.copy(values = List(PropertyValue("1")))
      val newStop = stop.copy(stopTypes = Seq(2, 3), propertyData = props.filterNot(_.publicId == "tietojen_yllapitaja") ++ Seq(admin))
      val newProps = newStop.propertyData.map(prop => SimplePointAssetProperty(prop.publicId, prop.values)).toSet
      service.updateExistingById(stop.id, None, newProps, "seppo", { (Int, _) => Unit })
      verify(mockTierekisteriClient, times(0)).createMassTransitStop(any[TierekisteriMassTransitStop], any[Option[String]])
      verify(mockTierekisteriClient, times(1)).updateMassTransitStop(any[TierekisteriMassTransitStop], any[Option[String]], any[Option[String]])
    }
  }

  test ("Get enumerated property values") {
    runWithRollback {
      val propertyValues = RollbackMassTransitStopService.massTransitStopEnumeratedPropertyValues
      propertyValues.nonEmpty should be (true)
      propertyValues.forall(x => x._2.nonEmpty) should be (true)
      propertyValues.forall(x => x._1 != "") should be (true)
    }
  }

  test("Stop floats if a State road has got changed to a road owned by municipality"){
    when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBounds(any[BoundingRectangle], any[Set[Int]])).thenReturn(vvhRoadLinks)

    val massTransitStopDao = new MassTransitStopDao
    runWithRollback{
      val assetId = 300006
      val boundingBox = BoundingRectangle(Point(370000,6077000), Point(374800,6677600))
      //Set administration class of the asset with State value
      RollbackBusStopStrategy.updateAdministrativeClassValueTest(assetId, State)
      val stops = RollbackMassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBox)
      stops.find(_.id == assetId).map(_.floating) should be(Some(true))
      massTransitStopDao.getAssetFloatingReason(assetId) should be(Some(FloatingReason.RoadOwnerChanged))
    }
  }

  test("Stop floats if a State road has got changed to a road owned to a private road"){
    when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBounds(any[BoundingRectangle], any[Set[Int]])).thenReturn(vvhRoadLinks)

    val massTransitStopDao = new MassTransitStopDao
    runWithRollback{
      val assetId = 300012
      val boundingBox = BoundingRectangle(Point(370000,6077000), Point(374800,6677600))
      //Set administration class of the asset with State value
      RollbackBusStopStrategy.updateAdministrativeClassValueTest(assetId, State)
      val stops = RollbackMassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBox)
      stops.find(_.id == assetId).map(_.floating) should be(Some(true))
      massTransitStopDao.getAssetFloatingReason(assetId) should be(Some(FloatingReason.RoadOwnerChanged))
    }
  }

  test("Stop floats if a Municipality road has got changed to a road owned by state"){
    val vvhRoadLinks = List(
      VVHRoadlink(1021227, 90, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers))

    when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBounds(any[BoundingRectangle], any[Set[Int]])).thenReturn(vvhRoadLinks)
    when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(vvhRoadLinks.map(toRoadLink))

    val massTransitStopDao = new MassTransitStopDao
    runWithRollback{
      val assetId = 300006
      val boundingBox = BoundingRectangle(Point(370000,6077000), Point(374800,6677600))
      //Set administration class of the asset with State value
      RollbackBusStopStrategy.updateAdministrativeClassValueTest(assetId, Municipality)
      val stops = RollbackMassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBox)
      stops.find(_.id == assetId).map(_.floating) should be(Some(true))
      massTransitStopDao.getAssetFloatingReason(assetId) should be(Some(FloatingReason.RoadOwnerChanged))
    }
  }

  test("Stop floats if a Private road has got changed to a road owned by state"){
    val vvhRoadLinks = List(
      VVHRoadlink(1021227, 90, Nil, State, TrafficDirection.UnknownDirection, FeatureClass.AllOthers))

    when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBounds(any[BoundingRectangle], any[Set[Int]])).thenReturn(vvhRoadLinks)
    when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(vvhRoadLinks.map(toRoadLink))

    val massTransitStopDao = new MassTransitStopDao
    runWithRollback{
      val assetId = 300006
      val boundingBox = BoundingRectangle(Point(370000,6077000), Point(374800,6677600))
      //Set administration class of the asset with State value
      RollbackBusStopStrategy.updateAdministrativeClassValueTest(assetId, Private)
      val stops = RollbackMassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBox)
      stops.find(_.id == assetId).map(_.floating) should be(Some(true))
      massTransitStopDao.getAssetFloatingReason(assetId) should be(Some(FloatingReason.RoadOwnerChanged))
    }
  }

  test("Stops working list shouldn't have floating assets with floating reason RoadOwnerChanged if user is not operator"){
    when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBounds(any[BoundingRectangle], any[Set[Int]])).thenReturn(vvhRoadLinks)

    runWithRollback {
      val assetId = 300012
      val boundingBox = BoundingRectangle(Point(370000,6077000), Point(374800,6677600))
      //Set administration class of the asset with State value
      RollbackBusStopStrategy.updateAdministrativeClassValueTest(assetId, State)
      //GetBoundingBox will set assets  to floating
      RollbackMassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBox)
      val workingList = RollbackMassTransitStopService.getFloatingAssetsWithReason(Some(Set(235)), Some(false))
      //Get all external ids from the working list
      val externalIds = workingList.map(m => m._2.map(a => a._2).flatten).flatten

      //Should not find any external id of the asset with administration class changed
      externalIds.foreach{ externalId =>
        externalId.get("id") should not be (Some(8))
      }
    }
  }

  test("Stops working list should have all floating assets if user is operator"){
    when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBounds(any[BoundingRectangle], any[Set[Int]])).thenReturn(vvhRoadLinks)

    runWithRollback {
      val assetId = 300012
      val boundingBox = BoundingRectangle(Point(370000,6077000), Point(374800,6677600))
      //Set administration class of the asset with State value
      RollbackBusStopStrategy.updateAdministrativeClassValueTest(assetId, State)
      //GetBoundingBox will set assets  to floating
      RollbackMassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBox)
      val workingList = RollbackMassTransitStopService.getFloatingAssetsWithReason(Some(Set(235)), Some(true))
      //Get all external ids from the working list
      val externalIds = workingList.map(m => m._2.map(a => a._2).flatten).flatten

      //Should have the external id of the asset with administration class changed
      externalIds.map(_.get("id")) should contain (Some(8))
    }
  }

  test("getByMunicipality gets Tierekisteri Equipment") {
    val tierekisteriStrategy = new TierekisteriBusStopStrategy(10, new MassTransitStopDao, mockRoadLinkService, mockTierekisteriClient, mockEventBus, mockGeometryTransform)
    val combinations: List[(Existence, String)] = List(Existence.No, Existence.Yes, Existence.Unknown).zip(List("1", "2", "99"))
    combinations.foreach { case (e, v) =>
      reset(mockTierekisteriClient, mockRoadLinkService)
      when(mockTierekisteriClient.fetchMassTransitStop(any[String])).thenReturn(Some(
        TierekisteriMassTransitStop(2, "2", RoadAddress(None, 1, 1, Track.Combined, 1), TRRoadSide.Unknown, StopType.Combined,
          false, equipments = Equipment.values.map(equip => equip -> e).toMap, None, None, None, "KX12356", None, None, None, new Date))
      )
      when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[Int])).thenReturn(vvhRoadLinks.map(toRoadLink))

      runWithRollback {
        val stops = RollbackMassTransitStopServiceWithTierekisteri.getByMunicipality(235)
        val (trStops, _) = stops.partition(s => tierekisteriStrategy.was(s))
        val equipmentPublicIds = Equipment.values.filter(_.isMaster).map(_.publicId)
        // All should be unknown as set in the TRClientMock
        val equipments = trStops.map(t => t.propertyData.filter(p => equipmentPublicIds.contains(p.publicId)))
        equipments.forall(_.forall(p => p.values.nonEmpty && p.values.head.asInstanceOf[PropertyValue].propertyValue == v)) should be(true)
      }
      verify(mockRoadLinkService, times(1)).getRoadLinksWithComplementaryFromVVH(any[Int])
      verify(mockTierekisteriClient, Mockito.atLeast(1)).fetchMassTransitStop(any[String])
    }
  }

  test("Updating an existing stop should not create a new Livi ID") {
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
    runWithRollback {
      val rad = RoadAddress(Some("235"), 110, 10, Track.Combined, 108)
      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[Long], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn((rad, RoadSide.Right))
      when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBounds(any[BoundingRectangle], any[Set[Int]])).thenReturn(vvhRoadLinks)
      val trStop = TierekisteriMassTransitStop(85755, "livi114873", rad, TRRoadSide.Unknown, StopType.Unknown, false,
        equipments, None, Option("TierekisteriFi"), Option("TierekisteriSe"), "test", Option(new Date), Option(new Date), Option(new Date), new Date(2016, 8, 1))

      sqlu"""update asset set floating='1' where id = 300008""".execute
      sqlu"""update text_property_value set value_fi='livi114873' where asset_id = 300008 and value_fi = 'OTHJ85755'""".execute
      when(mockTierekisteriClient.fetchMassTransitStop("livi114873")).thenReturn(Some(trStop))
      when(mockTierekisteriClient.isTREnabled).thenReturn(true)
      val (stopOpt, showStatusCode, municipalityCode) = RollbackMassTransitStopServiceWithTierekisteri.getMassTransitStopByNationalIdWithTRWarnings(85755)
      val stop = stopOpt.get
      RollbackMassTransitStopServiceWithTierekisteri.updateExistingById(stop.id,
        None, stop.propertyData.map(p =>
          if (p.publicId != MassTransitStopOperations.LiViIdentifierPublicId)
            SimplePointAssetProperty(p.publicId, p.values)
          else
            SimplePointAssetProperty(p.publicId, Seq(PropertyValue("1")))
        ).toSet, "pekka", (Int, _) => Unit)
      val captor: ArgumentCaptor[TierekisteriMassTransitStop] = ArgumentCaptor.forClass(classOf[TierekisteriMassTransitStop])
      verify(mockTierekisteriClient, Mockito.atLeastOnce).updateMassTransitStop(captor.capture, any[Option[String]], any[Option[String]])
      val capturedStop = captor.getValue
      capturedStop.liviId should be ("livi114873")
      val dbResult = sql"""SELECT value_fi FROM text_property_value where value_fi='livi114873' and asset_id = 300008""".as[String].list
      dbResult.size should be (1)
      dbResult.head should be ("livi114873")
    }
  }

  test("Should rollback bus stop if tierekisteri throw exception") {
    val assetId = 300000
    val geom = Point(374550, 6677350)
    val pos = Position(geom.x, geom.y, 131573L, Some(85))
    val properties = List(
      SimplePointAssetProperty("pysakin_tyyppi", List(PropertyValue("1"))),
      SimplePointAssetProperty("tietojen_yllapitaja", List(PropertyValue("2"))),
      SimplePointAssetProperty("yllapitajan_koodi", List(PropertyValue("livi"))))

    val service = new TestMassTransitStopServiceWithDynTransaction(new DummyEventBus, mockRoadLinkService)
    when(mockTierekisteriClient.isTREnabled).thenReturn(true)
    when(mockTierekisteriClient.updateMassTransitStop(any[TierekisteriMassTransitStop], any[Option[String]], any[Option[String]])).thenThrow(new TierekisteriClientException("TR-test exception"))
    when(mockTierekisteriClient.createMassTransitStop(any[TierekisteriMassTransitStop], any[Option[String]])).thenThrow(new TierekisteriClientException("TR-test exception"))
    when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[Long], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn(
      (RoadAddress(Option("235"), 1, 1, Track.Combined, 0), RoadSide.Left)
    )

    intercept[TierekisteriClientException] {
      service.updateExistingById(300000, Some(pos), properties.toSet, "user", (_, _) => Unit)
    }

    val asset = service.getById(300000).get
    asset.validityPeriod should be(Some(MassTransitStopValidityPeriod.Current))
  }

  test("Should not crash TR stop without livi id"){
    runWithRollback {
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus, mockRoadLinkService)
      when(mockTierekisteriClient.isTREnabled).thenReturn(true)
      val assetId = 300000
      val propertyValueId = sql"""SELECT id FROM text_property_value where value_fi='OTHJ1' and asset_id = $assetId""".as[String].list.head
      sqlu"""delete from text_property_value where id = cast($propertyValueId AS bigint)""".execute
      val dbResult = sql"""SELECT value_fi FROM text_property_value where id = cast($propertyValueId AS bigint)""".as[String].list
      dbResult.size should be(0)
      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[Long], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn(
            (RoadAddress(Option("235"), 1, 1, Track.Combined, 0), RoadSide.Left)
      )
      val (stop, showStatusCode, municipalityCode) = RollbackMassTransitStopServiceWithTierekisteri.getMassTransitStopByNationalIdWithTRWarnings(1)
      stop.isDefined should be(true)
      val liviIdentifierProperty = stop.get.propertyData.find(p => p.publicId == "yllapitajan_koodi").get
      liviIdentifierProperty.values.isEmpty should be(true)
      showStatusCode should be(true)
    }
  }

  test("auto correct geometry with bounding box less than 3m") {
    runWithRollback {
      val roadLink = RoadLink(11, List(Point(0.0,0.0), Point(120.0, 0.0)), 120, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = RollbackMassTransitStopService.create(NewMassTransitStop(5.0, 1.0, 1l, 2,
        Seq(
          SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("1"))),
          SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("2"))),
          SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("2")))
        )), "masstransitstopservice_spec", roadLink)
      val stops = RollbackMassTransitStopService.getByBoundingBox(
        userWithKauniainenAuthorization, BoundingRectangle(Point(0.0, 0.0), Point(10.0, 10.0)))
      val asset = stops.find(_.id == id)
      asset.get.lon should be(5.0)
      asset.get.lat should be(0.0)
    }
  }

    test("create and update roadNames properties") {
      val attributes: Map[String, Any] =
        Map("ROADNAME_SE" -> "roadname_se",
          "ROADNAME_FI" -> "roadname_fi")

      val props = Seq(SimplePointAssetProperty(MassTransitStopOperations.InventoryDateId, Seq(PropertyValue("2015-12-30"))))
      val roadLink = VVHRoadlink(12345l, 91, List(Point(0.0, 0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, attributes = attributes)

      val after = MassTransitStopOperations.setPropertiesDefaultValues(props, roadLink)
      after should have size (3)
      after.filter(_.publicId == MassTransitStopOperations.RoadName_FI) should have size (1)
      after.filter(_.publicId == MassTransitStopOperations.RoadName_SE) should have size (1)
      after.filter(_.publicId == MassTransitStopOperations.RoadName_FI).head.values.head.asInstanceOf[PropertyValue].propertyValue should be ("roadname_fi")
      after.filter(_.publicId == MassTransitStopOperations.RoadName_SE).head.values.head.asInstanceOf[PropertyValue].propertyValue should be ("roadname_se")
    }

  test("Update roadNames properties when exist and not filled") {
    val attributes: Map[String, Any] =
      Map("ROADNAME_SE" -> "roadname_se",
        "ROADNAME_FI" -> "roadname_fi")

    val props = Seq(SimplePointAssetProperty(MassTransitStopOperations.RoadName_SE, Seq.empty[PropertyValue]), SimplePointAssetProperty(MassTransitStopOperations.RoadName_FI, Seq.empty[PropertyValue]))
    val roadLink = VVHRoadlink(12345l, 91, List(Point(0.0, 0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, attributes = attributes)

    val after = MassTransitStopOperations.setPropertiesDefaultValues(props, roadLink)
    after should have size (3)
    after.filter(_.publicId == MassTransitStopOperations.RoadName_FI) should have size (1)
    after.filter(_.publicId == MassTransitStopOperations.RoadName_SE) should have size (1)
    after.filter(_.publicId == MassTransitStopOperations.RoadName_FI).head.values.head.asInstanceOf[PropertyValue].propertyValue should be ("roadname_fi")
    after.filter(_.publicId == MassTransitStopOperations.RoadName_SE).head.values.head.asInstanceOf[PropertyValue].propertyValue should be ("roadname_se")
  }

  test("Not update when roadNames properties are filled") {
    val attributes: Map[String, Any] =
      Map("ROADNAME_SE" -> "roadname_se",
        "ROADNAME_FI" -> "roadname_fi")

    val props = Seq(SimplePointAssetProperty(MassTransitStopOperations.RoadName_SE, Seq(PropertyValue("user_road_name_se"))),
                    SimplePointAssetProperty(MassTransitStopOperations.RoadName_FI, Seq(PropertyValue("user_road_name_fi"))))
    val roadLink = VVHRoadlink(12345l, 91, List(Point(0.0, 0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, attributes = attributes)

    val after = MassTransitStopOperations.setPropertiesDefaultValues(props, roadLink)
    after should have size (3)
    after.filter(_.publicId == MassTransitStopOperations.RoadName_FI) should have size (1)
    after.filter(_.publicId == MassTransitStopOperations.RoadName_SE) should have size (1)
    after.filter(_.publicId == MassTransitStopOperations.RoadName_FI).head.values.head.asInstanceOf[PropertyValue].propertyValue should be ("user_road_name_fi")
    after.filter(_.publicId == MassTransitStopOperations.RoadName_SE).head.values.head.asInstanceOf[PropertyValue].propertyValue should be ("user_road_name_se")
  }

  test("Find more than one busStop with same passengerId"){
    runWithRollback {
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus, mockRoadLinkService)
      val mockMunicipalityDao: MunicipalityDao = MockitoSugar.mock[MunicipalityDao]

      val roadLink = Seq(RoadLink(11, List(Point(0.0,0.0), Point(10.0, 0.0)), 10, State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
      RoadLink(21, List(Point(0.0,0.0), Point(20.0, 0.0)), 20, State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91))))

      val ids = Seq(RollbackMassTransitStopService.create(NewMassTransitStop(5.0, 0.0, 1l, 2,
        Seq(
          SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("1"))),
          SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("2"))),
          SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("2"))),
          SimplePointAssetProperty("matkustajatunnus", Seq(PropertyValue("1000")))
        )), "masstransitstopservice_spec", roadLink.head),
      RollbackMassTransitStopService.create(NewMassTransitStop(15.0, 0.0, 1l, 2,
        Seq(
          SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("1"))),
          SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("2"))),
          SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("2"))),
          SimplePointAssetProperty("matkustajatunnus", Seq(PropertyValue("1000")))
        )), "masstransitstopservice_spec", roadLink.last))

      val result = service.getMassTransitStopByPassengerId("1000", _ => Unit)
      result should have size 2
      result.find(_.id == ids.head).head.municipalityName should be (Some("Kauniainen"))
      result.find(_.id == ids.last).last.municipalityName should be (Some("Helsinki"))
      }
    }

  test("Updating the mass transit stop from others -> ELY should not create a stop in TR when flagged") {
    runWithRollback {
      reset(mockTierekisteriClient)
      val roadLink = RoadLink(11, List(Point(0.0, 0.0), Point(120.0, 0.0)), 120, State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = RollbackMassTransitStopService.create(NewMassTransitStop(5.0, 0.0, 1l, 2,
        Seq(
          SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("1"))),
          SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("2"))),
          SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("2")))
        )), "masstransitstopservice_spec", roadLink)
      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[Long], any[Int], any[Option[Int]], any[Option[Int]]
      )).thenReturn((RoadAddress(Some("235"), 110, 10, Track.Combined, 108), RoadSide.Right))
      when(mockTierekisteriClient.isTREnabled).thenReturn(true)
      when(mockTierekisteriClient.fetchMassTransitStop(any[String])).thenReturn(None)
      val service = RollbackMassTransitStopServiceWithTierekisteri
      val stop = service.getById(id).get
      val props = stop.propertyData
      val admin = props.find(_.publicId == "tietojen_yllapitaja").get
      val newAdmin = admin.copy(values = List(PropertyValue("2")))
      val types = props.find(_.publicId == "pysakin_tyyppi").get
      val newTypes = types.copy(values = List(PropertyValue("2"), PropertyValue("3")))
      admin.values.exists(_.asInstanceOf[PropertyValue].propertyValue == "1") should be(true)
      types.values.exists(_.asInstanceOf[PropertyValue].propertyValue == "2") should be(true)
      types.values.exists(_.asInstanceOf[PropertyValue].propertyValue == "3") should be(false)
      val newStop = stop.copy(stopTypes = Seq(2, 3),
        propertyData = props.filterNot(_.publicId == "tietojen_yllapitaja").filterNot(_.publicId == "pysakin_tyyppi") ++
          Seq(newAdmin, newTypes))
      val newProps = newStop.propertyData.map(prop => SimplePointAssetProperty(prop.publicId, prop.values)).toSet ++ Set(SimplePointAssetProperty("trSave", Seq(PropertyValue("false"))))
      service.updateExistingById(stop.id, None, newProps, "seppo", { (Int, _) => Unit})
      verify(mockTierekisteriClient, times(0)).createMassTransitStop(any[TierekisteriMassTransitStop], any[Option[String]])
      verify(mockTierekisteriClient, times(0)).updateMassTransitStop(any[TierekisteriMassTransitStop], any[Option[String]], any[Option[String]])
    }
  }
}

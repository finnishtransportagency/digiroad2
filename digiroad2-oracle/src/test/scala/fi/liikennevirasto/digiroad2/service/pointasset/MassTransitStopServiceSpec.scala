package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2

import java.text.SimpleDateFormat
import java.util.Date
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{FeatureClass, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.dao.{MassTransitStopDao, MunicipalityDao, MunicipalityInfo, Sequences}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop._
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util.{GeometryTransform, LinkIdGenerator, RoadSide, TestTransactions}
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

  val testLinkId1 = "d06dbdc7-366a-4ee3-a41f-c1d5010859ae:1"
  val testLinkId2 = "1a47a9f8-a8e0-4be8-8bad-200ec31c6bce:1"
  val testLinkId3 = "1ff9ec6d-a9f4-4c16-90ad-d0df9bfb4b79:1"
  val testLinkId4 = "44dc1e56-79fb-452b-b35c-7ccb2b17b8aa:1"
  val testLinkId5 = "ad38f031-e25a-4440-9b6a-3ba696ae2105:1"

  val randomLinkId1: String = LinkIdGenerator.generateRandom()
  val randomLinkId2: String = LinkIdGenerator.generateRandom()
  val randomLinkId3: String = LinkIdGenerator.generateRandom()
  val randomLinkId4: String = LinkIdGenerator.generateRandom()
  val randomLinkId5: String = LinkIdGenerator.generateRandom()
  val randomLinkId6: String = LinkIdGenerator.generateRandom()
  val randomLinkId7: String = LinkIdGenerator.generateRandom()

  val roadLinks = List(
    RoadLinkFetched(testLinkId1, 90, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
    RoadLinkFetched(testLinkId2, 90, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
    RoadLinkFetched(testLinkId3, 90, Nil, Private, TrafficDirection.AgainstDigitizing, FeatureClass.AllOthers),
    RoadLinkFetched(randomLinkId1, 91, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
    RoadLinkFetched(randomLinkId2, 91, List(Point(0.0,0.0), Point(120.0, 0.0)), State, TrafficDirection.BothDirections, FeatureClass.AllOthers),
    RoadLinkFetched(randomLinkId3, 235, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
    RoadLinkFetched(randomLinkId4, 235, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
    RoadLinkFetched(testLinkId1, 235, Seq(Point(374603.57,6677262.009), Point(374684.567, 6677277.323)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
    RoadLinkFetched(testLinkId4, 91, Seq(Point(374375.156,6677244.904), Point(374567.632, 6677255.6)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
    RoadLinkFetched(randomLinkId5, 235, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
    RoadLinkFetched(testLinkId5, 235, Seq(Point(374668.195,6676884.282), Point(374805.498, 6676906.051)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers),
    RoadLinkFetched(randomLinkId6, 91, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.AgainstDigitizing, FeatureClass.AllOthers),
    RoadLinkFetched(randomLinkId7, 91, List(Point(0.0,0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, attributes = Map("ROADNAME_SE" -> "roadname_se",
      "ROADNAME_FI" -> "roadname_fi")))

  val mockGeometryTransform = MockitoSugar.mock[GeometryTransform]
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]

  def toRoadLink(l: RoadLinkFetched) = {
    RoadLink(l.linkId, l.geometry, GeometryUtils.geometryLength(l.geometry),
      l.administrativeClass, 1, l.trafficDirection, UnknownLinkType, None, None, l.attributes + ("MUNICIPALITYCODE" -> BigInt(l.municipalityCode)))
  }
  before {
    // Reset the mocks here so individual tests don't have to
    roadLinks.foreach(rl =>
      when(mockRoadLinkService.fetchNormalOrComplimentaryRoadLinkByLinkId(rl.linkId))
        .thenReturn(Some(rl)))
    when(mockRoadLinkService.getRoadLinksWithComplementaryByBoundsAndMunicipalities(any[BoundingRectangle], any[Set[Int]], any[Boolean],any[Boolean])).thenReturn(roadLinks.map(toRoadLink))
    roadLinks.foreach(rl =>
      when(mockRoadLinkService.getRoadLinkByLinkId(rl.linkId, false))
        .thenReturn(Some(toRoadLink(rl))))
    when(mockRoadLinkService.getRoadLinksByLinkIds(any[Set[String]], any[Boolean])).thenReturn(roadLinks.map(toRoadLink))
    when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(any[String], any[Boolean])).thenReturn(Some(toRoadLink(RoadLinkFetched(testLinkId5, 91, Seq(Point(374668.195,6676884.282), Point(374805.498, 6676906.051)), State, TrafficDirection.BothDirections, FeatureClass.AllOthers))))
    when(mockRoadLinkService.getHistoryDataLink(any[String], any[Boolean])).thenReturn(None)

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
    override val massTransitStopDao: MassTransitStopDao = new MassTransitStopDao
    override val municipalityDao: MunicipalityDao = new MunicipalityDao
    override val geometryTransform: GeometryTransform = mockGeometryTransform
  }

  object RollbackMassTransitStopService extends TestMassTransitStopService(new DummyEventBus, mockRoadLinkService)

  class TestMassTransitStopServiceWithDynTransaction(val eventbus: DigiroadEventBus, val roadLinkService: RoadLinkService) extends MassTransitStopService {
    override def withDynSession[T](f: => T): T = TestTransactions.withDynSession()(f)
    override def withDynTransaction[T](f: => T): T = TestTransactions.withDynTransaction()(f)
    override val massTransitStopDao: MassTransitStopDao = new MassTransitStopDao
    override val municipalityDao: MunicipalityDao = new MunicipalityDao
    override val geometryTransform: GeometryTransform = mockGeometryTransform
  }
  val mockMassTransitStopService = MockitoSugar.mock[MassTransitStopService]
  def runWithRollback(test: => Unit): Unit = assetLock.synchronized {
    TestTransactions.runWithRollback()(test)
  }

  val assetLock = "Used to prevent deadlocks"

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
      massTransitStops.find(_.id == 300001).get.stopTypes should be(Seq(2))
      massTransitStops.find(_.id == 300003).get.stopTypes.sorted should be(Seq(1,2))
    }
  }

  test("Get stops by bounding box") {
    runWithRollback {
      val linkId1 = LinkIdGenerator.generateRandom()
      val roadLink = RoadLink(linkId1, List(Point(0.0,0.0), Point(120.0, 0.0)), 120, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = RollbackMassTransitStopService.create(NewMassTransitStop(5.0, 0.0, randomLinkId5, 2,
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

  test("Fetch mass transit stop by national id") {

    runWithRollback {
      val roadAddress = RoadAddress(None, 0, 0, Track.Unknown, 0)
      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[String], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn((roadAddress , RoadSide.Right))
      val (stop, showStatusCode, municipalityCode) = RollbackMassTransitStopService.getMassTransitStopByNationalId(85755)
      stop.map(_.floating) should be(Some(true))

      showStatusCode should be (true)
    }
  }

  test("Get properties") {
    when(mockGeometryTransform.resolveAddressAndLocation(Point(374675.043988335, 6677274.14596169), 69, 109.0, testLinkId1, 2, road = None)).thenReturn((RoadAddress(Some("1"),1,1,Track(1),1),RoadSide(1)))
    runWithRollback {
      val massTransitStop = RollbackMassTransitStopService.getMassTransitStopByNationalId(2)._1.map { stop =>
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
    when(mockGeometryTransform.resolveAddressAndLocation(Point(374780.259160265, 6677546.84962279), 30, 113.0, randomLinkId4, 3, road = None)).thenReturn((RoadAddress(Some("1"),1,1,Track(1),1),RoadSide(1)))
    runWithRollback {
      val stop =  RollbackMassTransitStopService.getMassTransitStopByNationalId(85755)
      stop._1.get.id should be (300008)
    }
  }

  test("Update mass transit stop road link mml id") {
    runWithRollback {
      val geom = Point(374450, 6677250)
      val position = Some(Position(geom.x, geom.y, testLinkId5, Some(85)))
      RollbackMassTransitStopService.updateExistingById(300000, position, Set.empty, "user", (_,_) => Unit)
      val linkId = sql"""
            select lrm.link_id from asset a
            join asset_link al on al.asset_id = a.id
            join lrm_position lrm on lrm.id = al.position_id
            where a.id = 300000
      """.as[String].firstOption
      linkId should be(Some(testLinkId5))
    }
  }

  test("Update mass transit stop bearing") {
    runWithRollback {
      val geom = Point(374450, 6677250)
      val position = Some(Position(geom.x, geom.y, testLinkId4, Some(90)))
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
      val position = Some(Position(geom.x, geom.y, testLinkId4, Some(85)))
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
      val position = Some(Position(374450, 6677250, randomLinkId1, None))
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
      val position = Some(Position(374450, 6677250, randomLinkId1, None))
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
      val position = Some(Position(374450, 6677250, randomLinkId1, None))
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
      val pos = Position(geom.x, geom.y, randomLinkId3, Some(85))
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
      val position = Some(Position(60.0, 0.0, randomLinkId1, None))
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
      val position = Some(Position(60.0, 0.0, randomLinkId1, None))
      service.updateExistingById(300002, position, Set.empty, "user", (_,_) => Unit)
      verify(eventbus).publish(org.mockito.ArgumentMatchers.eq("asset:saved"), any[EventBusMassTransitStop]())
    }
  }

  test("Given a ELY administrated Motorway bus stop; " +
    "When the bus stop is moved over 50 meters; " +
    "Then the bus stop's nationalId should not change and the bus stop should not be expired"){
    runWithRollback {
      val linkId = randomLinkId1
      val municipalityCode = 91
      val geometry = Seq(Point(0.0,0.0), Point(120.0, 0.0))
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus, mockRoadLinkService)
      val properties = List(
        SimplePointAssetProperty("nimi_suomeksi", List(PropertyValue("value is copied"))),
        SimplePointAssetProperty("pysakin_tyyppi", List(PropertyValue("1"))),
        SimplePointAssetProperty("tietojen_yllapitaja", List(PropertyValue("2"))),
        SimplePointAssetProperty("yllapitajan_koodi", List(PropertyValue("livi"))),
        SimplePointAssetProperty("vaikutussuunta", List(PropertyValue("2"))),
        SimplePointAssetProperty("ensimmainen_voimassaolopaiva", List(PropertyValue("2013-01-01")))
      )

      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[String], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn(
        (RoadAddress(Some(municipalityCode.toString), 1, 1, Track.Combined, 0), RoadSide.Left)
      )
      val roadLink = RoadLink(linkId, geometry, 120, State, 1, TrafficDirection.BothDirections,
        Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
      val oldAssetId = service.create(NewMassTransitStop(0, 0, linkId, 0, properties), "test", roadLink)
      val oldAsset = service.getMassTransitStopById(oldAssetId)._1.get
      val position = Some(Position(60.0, 0.0, randomLinkId1, None))
      val updatedAsset =service.updateExistingById(oldAssetId, position, Set.empty, "user", (_,_) => Unit)

      updatedAsset.propertyData.find(p=>p.publicId =="nimi_suomeksi" ).get.values
        .head.asInstanceOf[PropertyValue].propertyValue should be("value is copied")
      updatedAsset.propertyData.find(p=>p.publicId =="pysakin_tyyppi" ).get.values
        .head.asInstanceOf[PropertyValue].propertyValue should be("1")
      updatedAsset.propertyData.find(p=>p.publicId =="tietojen_yllapitaja" ).get.values
        .head.asInstanceOf[PropertyValue].propertyValue should be("2")
      updatedAsset.propertyData.find(p=>p.publicId =="vaikutussuunta" ).get.values
        .head.asInstanceOf[PropertyValue].propertyValue should be("2")

      updatedAsset.nationalId should be(oldAsset.nationalId)

      verify(eventbus, times(2)).publish(org.mockito.ArgumentMatchers.eq("asset:saved"), any[EventBusMassTransitStop]())

      //Check that no expiration message was sent
      verify(eventbus, never()).publish(org.mockito.ArgumentMatchers.eq("asset:expired"), any[EventBusMassTransitStop]())
    }
  }

  test("Given a HSL administrated Motorway bus stop; " +
    "When the bus stop is moved over 50 meters; " +
    "Then the bus stop's nationalId should not change and the bus stop should not be expired") {
    runWithRollback {
      val linkId = randomLinkId1
      val municipalityCode = 91
      val geometry = Seq(Point(0.0, 0.0), Point(120.0, 0.0))
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus, mockRoadLinkService)
      val properties = List(
        SimplePointAssetProperty("nimi_suomeksi", List(PropertyValue("value is copied"))),
        SimplePointAssetProperty("pysakin_tyyppi", List(PropertyValue("1"))),
        SimplePointAssetProperty("tietojen_yllapitaja", List(PropertyValue("3"))),
        SimplePointAssetProperty("yllapitajan_koodi", List(PropertyValue("livi"))),
        SimplePointAssetProperty("vaikutussuunta", List(PropertyValue("2"))),
        SimplePointAssetProperty("ensimmainen_voimassaolopaiva", List(PropertyValue("2013-01-01")))
      )

      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[String], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn(
        (RoadAddress(Some(municipalityCode.toString), 1, 1, Track.Combined, 0), RoadSide.Left)
      )
      val roadLink = RoadLink(linkId, geometry, 120, State, 1, TrafficDirection.BothDirections,
        Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
      val oldAssetId = service.create(NewMassTransitStop(0, 0, linkId, 0, properties), "test", roadLink)
      val oldAsset = service.getMassTransitStopById(oldAssetId)._1.get
      val position = Some(Position(60.0, 0.0, randomLinkId1, None))
      val updatedAsset = service.updateExistingById(oldAssetId, position, Set.empty, "user", (_, _) => Unit)

      updatedAsset.propertyData.find(p => p.publicId == "nimi_suomeksi").get.values
        .head.asInstanceOf[PropertyValue].propertyValue should be("value is copied")
      updatedAsset.propertyData.find(p => p.publicId == "pysakin_tyyppi").get.values
        .head.asInstanceOf[PropertyValue].propertyValue should be("1")
      updatedAsset.propertyData.find(p => p.publicId == "tietojen_yllapitaja").get.values
        .head.asInstanceOf[PropertyValue].propertyValue should be("3")
      updatedAsset.propertyData.find(p => p.publicId == "vaikutussuunta").get.values
        .head.asInstanceOf[PropertyValue].propertyValue should be("2")

      updatedAsset.nationalId should be(oldAsset.nationalId)

      verify(eventbus, times(2)).publish(org.mockito.ArgumentMatchers.eq("asset:saved"), any[EventBusMassTransitStop]())

      //Check that no expiration message was sent
      verify(eventbus, never()).publish(org.mockito.ArgumentMatchers.eq("asset:expired"), any[EventBusMassTransitStop]())
    }
  }

  test("Assert user rights when updating a mass transit stop") {
    runWithRollback {
      val position = Some(Position(60.0, 0.0, randomLinkId1, None))
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

      val roadLink = RoadLink(randomLinkId1, List(Point(0.0,0.0), Point(120.0, 0.0)), 120, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)))
      val id = service.create(NewMassTransitStop(60.0, 0.0, randomLinkId1, 100, properties), "test", roadLink)
      val massTransitStop = service.getById(id).get
      massTransitStop.bearing should be(Some(100))
      massTransitStop.floating should be(false)
      massTransitStop.stopTypes should be(List(1))
      massTransitStop.validityPeriod should be(Some(MassTransitStopValidityPeriod.Current))
      mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(randomLinkId1, newTransaction = false).get.linkSource.value should be (1)

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
      val roadLink = RoadLink(randomLinkId1, List(Point(0.0,0.0), Point(120.0, 0.0)), 120, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)))
      val id = service.create(NewMassTransitStop(60.0, 0.0, randomLinkId1, 100, properties), "test", roadLink)
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
      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[String], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn(
        (RoadAddress(None, 1, 1, Track.Combined, 0), RoadSide.Left)
      )
      val massTransitStopDao = new MassTransitStopDao
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus, mockRoadLinkService)
      val properties = List(
        SimplePointAssetProperty("pysakin_tyyppi", List(PropertyValue("1"))),
        SimplePointAssetProperty("tietojen_yllapitaja", List(PropertyValue("2"))),
        SimplePointAssetProperty("yllapitajan_koodi", List(PropertyValue("livi"))),
        SimplePointAssetProperty("vaikutussuunta", List(PropertyValue("2"))),
        SimplePointAssetProperty("ensimmainen_voimassaolopaiva", List(PropertyValue("2013-01-01"))),
        SimplePointAssetProperty("viimeinen_voimassaolopaiva", List(PropertyValue(DateTime.now().plusDays(1).toString()))))
      val roadLink = RoadLink(randomLinkId1, List(Point(0.0,0.0), Point(120.0, 0.0)), 120, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)))
      val id = service.create(NewMassTransitStop(60.0, 0.0, randomLinkId1, 100, properties), "test", roadLink)
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
      val service = new TestMassTransitStopService(eventbus, mockRoadLinkService)
      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[String], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn(
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

      val roadLink = RoadLink(randomLinkId2, List(Point(0.0,0.0), Point(120.0, 0.0)), 120, State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)))
      val id = service.create(NewMassTransitStop(60.0, 0.0, randomLinkId2, 100, properties), "test", roadLink)
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

      val livi = service.getMassTransitStopById(id);
      getLiviIdValue(livi._1.get.propertyData).get should be (liviId)

      verify(eventbus).publish(org.mockito.ArgumentMatchers.eq("asset:saved"), any[EventBusMassTransitStop]())
    }
  }

  test("Create new mass transit stop with HSL administration and 'state' road link and turn it into a municipality stop") {
    runWithRollback {
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus, mockRoadLinkService)
      val properties = List(
        SimplePointAssetProperty("pysakin_tyyppi", List(PropertyValue("1"))),
        SimplePointAssetProperty("tietojen_yllapitaja", List(PropertyValue("3"))),
        SimplePointAssetProperty("yllapitajan_koodi", List(PropertyValue("livi"))),
        SimplePointAssetProperty("ensimmainen_voimassaolopaiva", List(PropertyValue("2013-01-01"))),
        SimplePointAssetProperty("viimeinen_voimassaolopaiva", List(PropertyValue("2027-01-01"))),
        SimplePointAssetProperty("vaikutussuunta", List(PropertyValue("2"))))

      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[String], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn(
        (RoadAddress(None, 1, 1, Track.Combined, 0), RoadSide.Left)
      )
      val roadLink = RoadLink(randomLinkId1, List(Point(0.0,0.0), Point(120.0, 0.0)), 120, State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91)))
      when(mockRoadLinkService.getRoadLinkByLinkId(randomLinkId1)).thenReturn(Some(roadLink))
      val id = service.create(NewMassTransitStop(60.0, 0.0, randomLinkId1, 100, properties), "test", roadLink)

      val newProperties = Set(
        SimplePointAssetProperty("tietojen_yllapitaja", List(PropertyValue("1")))
      )

      val massTransitStop = service.updateExistingById(id, None, newProperties, "test2", (Int, _) => Unit)

      val administratorProperty = massTransitStop.propertyData.find(p => p.publicId == "tietojen_yllapitaja").get
      administratorProperty.values.head.asInstanceOf[PropertyValue].propertyValue should be("1")

      val administrativeClassProperty = massTransitStop.propertyData.find(p => p.publicId == "linkin_hallinnollinen_luokka").get
      administrativeClassProperty.values.head.asInstanceOf[PropertyValue].propertyValue should be("1")

      val livi = service.getMassTransitStopById(id);
      val liviId = getLiviIdValue(livi._1.get.propertyData)
      liviId shouldNot be ("")
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
      val linkId = randomLinkId1
      val municipalityCode = 91
      val geometry = Seq(Point(0.0,0.0), Point(120.0, 0.0))
      val roadLink = RoadLink(linkId, geometry, 120, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))

      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[String], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn(
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

  test("Send Vallus message when deleting stop"){
    runWithRollback {
      val linkId = randomLinkId1
      val municipalityCode = 91
      val geometry = Seq(Point(0.0,0.0), Point(120.0, 0.0))
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus, mockRoadLinkService)
      val properties = List(
        SimplePointAssetProperty("nimi_suomeksi", List(PropertyValue("value is copied"))),
        SimplePointAssetProperty("pysakin_tyyppi", List(PropertyValue("1"))),
        SimplePointAssetProperty("tietojen_yllapitaja", List(PropertyValue("2"))),
        SimplePointAssetProperty("vaikutussuunta", List(PropertyValue("2"))),
        SimplePointAssetProperty("ensimmainen_voimassaolopaiva", List(PropertyValue("2013-01-01")))
      )

      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[String], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn(
        (RoadAddress(Some(municipalityCode.toString), 1, 1, Track.Combined, 0), RoadSide.Left)
      )
      val roadLink = RoadLink(linkId, geometry, 120, Municipality, 1, TrafficDirection.BothDirections,
        Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
      val assetId = service.create(NewMassTransitStop(0, 0, linkId, 0, properties), "test", roadLink)
      service.deleteMassTransitStopData(assetId)
      verify(eventbus).publish(org.mockito.ArgumentMatchers.eq("asset:expired"), any[EventBusMassTransitStop]())
    }
  }

  test("Updating the mass transit stop from others -> ELY should create a stop with liviID") {
    runWithRollback {
      val linkId1 = LinkIdGenerator.generateRandom()
      val roadLink = RoadLink(linkId1, List(Point(0.0, 0.0), Point(120.0, 0.0)), 120, State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = RollbackMassTransitStopService.create(NewMassTransitStop(5.0, 0.0, randomLinkId5, 2,
        Seq(
          SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("1"))),
          SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("2"))),
          SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("2")))
        )), "masstransitstopservice_spec", roadLink)
      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[String], any[Int], any[Option[Int]], any[Option[Int]]
      )).thenReturn((RoadAddress(Some("235"), 110, 10, Track.Combined, 108), RoadSide.Right))
      val service = RollbackMassTransitStopService
      val stop = service.getById(id).get
      val props = stop.propertyData
      val admin = props.find(_.publicId == "tietojen_yllapitaja").get
      val newAdmin = admin.copy(values = List(PropertyValue("2")))
      val types = props.find(_.publicId == "pysakin_tyyppi").get
      val newTypes = types.copy(values = List(PropertyValue("2"), PropertyValue("1")))
      admin.values.exists(_.asInstanceOf[PropertyValue].propertyValue == "1") should be(true)
      types.values.exists(_.asInstanceOf[PropertyValue].propertyValue == "2") should be(true)
      types.values.exists(_.asInstanceOf[PropertyValue].propertyValue == "1") should be(false)
      val newStop = stop.copy(stopTypes = Seq(2, 3),
        propertyData = props.filterNot(_.publicId == "tietojen_yllapitaja").filterNot(_.publicId == "pysakin_tyyppi") ++
          Seq(newAdmin, newTypes))
      val newProps = newStop.propertyData.map(prop => SimplePointAssetProperty(prop.publicId, prop.values)).toSet
      service.updateExistingById(stop.id, None, newProps, "seppo", { (Int, _) => Unit })

      val livi = service.getMassTransitStopById(id);
      getLiviIdValue(livi._1.get.propertyData) should not be None
    }
  }

  test("Updating the mass transit stop from others -> HSL should create a stop with liviID") {
    runWithRollback {
      val linkId1 = LinkIdGenerator.generateRandom()
      val roadLink = RoadLink(linkId1, List(Point(0.0,0.0), Point(120.0, 0.0)), 120, State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = RollbackMassTransitStopService.create(NewMassTransitStop(5.0, 0.0, randomLinkId5, 2,
        Seq(
          SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("1"))),
          SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("2"))),
          SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("2")))
        )), "masstransitstopservice_spec", roadLink)
      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[String], any[Int], any[Option[Int]], any[Option[Int]]
      )).thenReturn((RoadAddress(Some("235"), 110, 10, Track.Combined, 108), RoadSide.Right))

      val service = RollbackMassTransitStopService
      val stop = service.getById(id).get
      val props = stop.propertyData
      val admin = props.find(_.publicId == "tietojen_yllapitaja").get
      val newAdmin = admin.copy(values = List(PropertyValue("3")))
      val types = props.find(_.publicId == "pysakin_tyyppi").get
      val newTypes = types.copy(values = List(PropertyValue("2"), PropertyValue("1")))
      admin.values.exists(_.asInstanceOf[PropertyValue].propertyValue == "1") should be(true)
      types.values.exists(_.asInstanceOf[PropertyValue].propertyValue == "2") should be(true)
      types.values.exists(_.asInstanceOf[PropertyValue].propertyValue == "1") should be(false)
      val newStop = stop.copy(stopTypes = Seq(2, 3),
        propertyData = props.filterNot(_.publicId == "tietojen_yllapitaja").filterNot(_.publicId == "pysakin_tyyppi") ++
          Seq(newAdmin, newTypes))
      val newProps = newStop.propertyData.map(prop => SimplePointAssetProperty(prop.publicId, prop.values)).toSet
      service.updateExistingById(stop.id, None, newProps, "seppo", { (Int, _) => Unit })
      val livi = service.getMassTransitStopById(id);
      getLiviIdValue(livi._1.get.propertyData) should not be None
    }
  }

  test("Updating the mass transit stop from ELY -> others should remove liviID") {
    runWithRollback {
      val linkId1 = LinkIdGenerator.generateRandom()
      val roadLink = RoadLink(linkId1, List(Point(0.0,0.0), Point(120.0, 0.0)), 120, State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = RollbackMassTransitStopService.create(NewMassTransitStop(5.0, 0.0, randomLinkId5, 2,
        Seq(
          SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("2"))),
          SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("2"))),
          SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("2")))
        )), "masstransitstopservice_spec", roadLink)
      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[String], any[Int], any[Option[Int]], any[Option[Int]]
      )).thenReturn((RoadAddress(Some("235"), 110, 10, Track.Combined, 108), RoadSide.Right))
      val service = RollbackMassTransitStopService
      val stop = service.getById(id).get
      val props = stop.propertyData
      val admin = props.find(_.publicId == "tietojen_yllapitaja").get.copy(values = List(PropertyValue("1")))
      val newStop = stop.copy(stopTypes = Seq(2, 3), propertyData = props.filterNot(_.publicId == "tietojen_yllapitaja") ++ Seq(admin))
      val newProps = newStop.propertyData.map(prop => SimplePointAssetProperty(prop.publicId, prop.values)).toSet
      service.updateExistingById(stop.id, None, newProps, "seppo", { (Int, _) => Unit })
      val livi = service.getMassTransitStopById(id);
      getLiviIdValue(livi._1.get.propertyData) should be (None)
    }
  }

  def administrator(code: String) ={
    SimplePointAssetProperty(MassTransitStopOperations.AdministratorInfoPublicId, List(PropertyValue(code)))
  }
  def stopType(code: String) ={
    SimplePointAssetProperty(MassTransitStopOperations.MassTransitStopTypePublicId, List(PropertyValue(code)))
  }

  def float(code: String) ={
    SimplePointAssetProperty(MassTransitStopOperations.FloatingReasonPublicId, List(PropertyValue(code)))
  }
  def roadLinkFactory(code: AdministrativeClass) ={
    Option(RoadLink("0", List(Point(0.0,0.0), Point(120.0, 0.0)), 0, code, 1,
      TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91))
    ))
  }

  test("liviId when in private road but administrator is hsl or ely"){
    runWithRollback {
      val roadLink = roadLinkFactory(Private)
      val hsl:Seq[SimplePointAssetProperty] = Seq(administrator(MassTransitStopOperations.HSLPropertyValue),
        stopType(MassTransitStopOperations.BusStopPropertyValue))
      val ely:Seq[SimplePointAssetProperty] = Seq(administrator(MassTransitStopOperations.CentralELYPropertyValue),
        stopType(MassTransitStopOperations.BusStopPropertyValue))

      // hsl can not add liviID to bust stop in private road
      OthBusStopLifeCycleBusStopStrategy
        .isOthLiviId(hsl,roadLink.map(_.administrativeClass)) should be (false)

      // ely can add liviID to bust stop in private road
      OthBusStopLifeCycleBusStopStrategy
        .isOthLiviId(ely,roadLink.map(_.administrativeClass)) should be (true)
    }
  }
  test("liviId when in municipality road but administrator is hsl or ely"){
    runWithRollback {
      val roadLink = roadLinkFactory(Municipality)
      val hsl:Seq[SimplePointAssetProperty] = Seq(administrator(MassTransitStopOperations.HSLPropertyValue),
        stopType(MassTransitStopOperations.BusStopPropertyValue))
      val ely:Seq[SimplePointAssetProperty] = Seq(administrator(MassTransitStopOperations.CentralELYPropertyValue),
        stopType(MassTransitStopOperations.BusStopPropertyValue))

      // hsl can not add liviID to bust stop in municipality road
      OthBusStopLifeCycleBusStopStrategy
        .isOthLiviId(hsl,roadLink.map(_.administrativeClass)) should be (false)

      // ely can add liviID to bust stop in municipality road
      OthBusStopLifeCycleBusStopStrategy
        .isOthLiviId(ely,roadLink.map(_.administrativeClass)) should be (true)
    }
  }

  test("no liviId when on terminated road, but administrator hsl or ely"){
    runWithRollback {
      val roadLink = roadLinkFactory(State)
      val hsl:Seq[SimplePointAssetProperty] = Seq(administrator(MassTransitStopOperations.HSLPropertyValue),
        stopType(MassTransitStopOperations.BusStopPropertyValue),float(FloatingReason.TerminatedRoad.value.toString))
      val ely:Seq[SimplePointAssetProperty] = Seq(administrator(MassTransitStopOperations.CentralELYPropertyValue),
        stopType(MassTransitStopOperations.BusStopPropertyValue),float(FloatingReason.TerminatedRoad.value.toString))
      OthBusStopLifeCycleBusStopStrategy
        .isOthLiviId(hsl,roadLink.map(_.administrativeClass)) should be (false)
      OthBusStopLifeCycleBusStopStrategy
        .isOthLiviId(ely,roadLink.map(_.administrativeClass)) should be (false)
    }
  }

  test("no liviId when virtual, but administrator hsl or ely"){
    runWithRollback {
      val roadLink = roadLinkFactory(State)
      val hsl:Seq[SimplePointAssetProperty] = Seq(administrator(MassTransitStopOperations.HSLPropertyValue),
        stopType(MassTransitStopOperations.VirtualBusStopPropertyValue))
      val ely:Seq[SimplePointAssetProperty] = Seq(administrator(MassTransitStopOperations.CentralELYPropertyValue),
        stopType(MassTransitStopOperations.VirtualBusStopPropertyValue))
      OthBusStopLifeCycleBusStopStrategy
        .isOthLiviId(hsl,roadLink.map(_.administrativeClass)) should be (false)
      OthBusStopLifeCycleBusStopStrategy
        .isOthLiviId(ely,roadLink.map(_.administrativeClass)) should be (false)
    }
  }

  test("no liviId when in municipality road, but administrator municipality"){
    runWithRollback {
      val roadLink = roadLinkFactory(Municipality)
      val municipalityAdmin:Seq[SimplePointAssetProperty] = Seq(administrator(MassTransitStopOperations.MunicipalityPropertyValue),
        stopType(MassTransitStopOperations.BusStopPropertyValue))

      OthBusStopLifeCycleBusStopStrategy
        .isOthLiviId(municipalityAdmin,roadLink.map(_.administrativeClass)) should be (false)
    }
  }
  test("no liviId when in state road, but administrator municipality"){
    runWithRollback {
      val roadLink = roadLinkFactory(State)
      val municipalityAdmin:Seq[SimplePointAssetProperty] = Seq(administrator(MassTransitStopOperations.MunicipalityPropertyValue),
        stopType(MassTransitStopOperations.BusStopPropertyValue))

      OthBusStopLifeCycleBusStopStrategy
        .isOthLiviId(municipalityAdmin,roadLink.map(_.administrativeClass)) should be (false)
    }
  }

  test("liviId when not terminated and bus or servicePoint, HSL"){
    runWithRollback {
      val roadLink = roadLinkFactory(State)
      val hsl =  MassTransitStopOperations.HSLPropertyValue
      val commuter:Seq[SimplePointAssetProperty] = Seq(administrator(hsl),
        stopType(MassTransitStopOperations.BusStopPropertyValue))
      val servicePoint:Seq[SimplePointAssetProperty] = Seq(administrator(hsl),
        stopType(MassTransitStopOperations.ServicePointBusStopPropertyValue))

      OthBusStopLifeCycleBusStopStrategy
        .isOthLiviId(commuter,roadLink.map(_.administrativeClass)) should be (true)
      OthBusStopLifeCycleBusStopStrategy
        .isOthLiviId(servicePoint,roadLink.map(_.administrativeClass)) should be (true)
    }
  }

  test("liviId when not terminated and bus or servicePoint, Ely"){
    runWithRollback {
      val roadLink = roadLinkFactory(State)
      val ely =  MassTransitStopOperations.CentralELYPropertyValue
      val commuter:Seq[SimplePointAssetProperty] = Seq(administrator(ely),
        stopType(MassTransitStopOperations.BusStopPropertyValue))
      val servicePoint:Seq[SimplePointAssetProperty] = Seq(administrator(ely),
        stopType(MassTransitStopOperations.ServicePointBusStopPropertyValue))

      OthBusStopLifeCycleBusStopStrategy
        .isOthLiviId(commuter,roadLink.map(_.administrativeClass)) should be (true)
      OthBusStopLifeCycleBusStopStrategy
        .isOthLiviId(servicePoint,roadLink.map(_.administrativeClass)) should be (true)
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

  test("Stops working list shouldn't have floating assets with floating reason RoadOwnerChanged if user is not operator"){
    runWithRollback {
      val assetId = 300012
      //Set administration class of the asset with State value
      RollbackBusStopStrategy.updateAdministrativeClassValueTest(assetId, State)
      //Set assets manually to floating
      RollbackMassTransitStopService.updateFloating(assetId, floating = true, floatingReason = None)
      val workingList = RollbackMassTransitStopService.getFloatingAssetsWithReason(Some(Set(235)), Some(false))
      //Get all external ids from the working list
      val nationalIds = workingList.map(m => m._2.map(a => a._2).flatten).flatten

      //Should not find any external id of the asset with administration class changed
      nationalIds.foreach{ nationalId =>
        nationalId.get("id") should not be (Some(8))
      }
    }
  }

  test("Stops working list should have all floating assets if user is operator"){
    runWithRollback {
      val assetId = 300012
      //Set administration class of the asset with State value
      RollbackBusStopStrategy.updateAdministrativeClassValueTest(assetId, State)
      //Set assets manually to floating
      RollbackMassTransitStopService.updateFloating(assetId, floating = true, floatingReason = None)
      val workingList = RollbackMassTransitStopService.getFloatingAssetsWithReason(Some(Set(235)), Some(true))
      //Get all external ids from the working list
      val nationalIds = workingList.map(m => m._2.map(a => a._2).flatten).flatten

      //Should have the external id of the asset with administration class changed
      nationalIds.map(_.get("id")) should contain (Some(8))
    }
  }
  def getLiviIdValue(properties: Seq[AbstractProperty]) = {
    properties.find(_.publicId == MassTransitStopOperations.LiViIdentifierPublicId).flatMap(prop => prop.values.headOption).map(_.asInstanceOf[PropertyValue].propertyValue)
  }
  test("Updating an existing stop should not create a new Livi ID") {

    runWithRollback {
      val rad = RoadAddress(Some("235"), 110, 10, Track.Combined, 108)
      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[String], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn((rad, RoadSide.Right))
      sqlu"""update asset set floating='1' where id = 300008""".execute
      sqlu"""update text_property_value set value_fi='livi114873' where asset_id = 300008 and value_fi = 'OTHJ85755'""".execute
      val (stopOpt, showStatusCode, municipalityCode) = RollbackMassTransitStopService.getMassTransitStopByNationalId(85755)
      val stop = stopOpt.get
      RollbackMassTransitStopService.updateExistingById(stop.id,
        None, stop.propertyData.map(p =>
          if (p.publicId != MassTransitStopOperations.LiViIdentifierPublicId)
            SimplePointAssetProperty(p.publicId, p.values)
          else
            SimplePointAssetProperty(p.publicId, Seq(PropertyValue("1")))
        ).toSet, "pekka", (Int, _) => Unit)
      val captor = RollbackMassTransitStopService.getMassTransitStopByNationalId(85755)
      val capturedStop = captor._1.get
      getLiviIdValue(capturedStop.propertyData).get should be ("livi114873")
      val dbResult = sql"""SELECT value_fi FROM text_property_value where value_fi='livi114873' and asset_id = 300008""".as[String].list
      dbResult.size should be (1)
      dbResult.head should be ("livi114873")
    }
  }

  test("auto correct geometry with bounding box less than 3m") {
    runWithRollback {
      val linkId1 = LinkIdGenerator.generateRandom()
      val roadLink = RoadLink(linkId1, List(Point(0.0,0.0), Point(120.0, 0.0)), 120, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = RollbackMassTransitStopService.create(NewMassTransitStop(5.0, 1.0, randomLinkId5, 2,
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

      val props = Seq.empty[SimplePointAssetProperty]
      val roadLink = RoadLinkFetched(randomLinkId7, 91, List(Point(0.0, 0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, attributes = attributes)

      val after = MassTransitStopOperations.setPropertiesDefaultValues(props, roadLink)
      after should have size (2)
      after.filter(_.publicId == MassTransitStopOperations.RoadName_FI) should have size (1)
      after.filter(_.publicId == MassTransitStopOperations.RoadName_SE) should have size (1)
      after.filter(_.publicId == MassTransitStopOperations.RoadName_FI).head.values.head.asInstanceOf[PropertyValue].propertyValue should be ("roadname_fi")
      after.filter(_.publicId == MassTransitStopOperations.RoadName_SE).head.values.head.asInstanceOf[PropertyValue].propertyValue should be ("roadname_se")
    }

  test("Update roadNames properties when exist and not filled") {
    val attributes: Map[String, Any] =
      Map("ROADNAME_SE" -> "roadname_se",
        "ROADNAME_FI" -> "roadname_fi")

    val props = Seq(SimplePointAssetProperty(MassTransitStopOperations.RoadName_SE, Seq(PropertyValue(propertyValue = "", propertyDisplayValue = Some("osoite_ruotsiksi")))), SimplePointAssetProperty(MassTransitStopOperations.RoadName_FI, Seq(PropertyValue(propertyValue = "", propertyDisplayValue = Some("osoite_suomeksi")))))
    val roadLink = RoadLinkFetched(randomLinkId7, 91, List(Point(0.0, 0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, attributes = attributes)

    val after = MassTransitStopOperations.setPropertiesDefaultValues(props, roadLink)
    after should have size (2)
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
    val roadLink = RoadLinkFetched(randomLinkId7, 91, List(Point(0.0, 0.0), Point(120.0, 0.0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, attributes = attributes)

    val after = MassTransitStopOperations.setPropertiesDefaultValues(props, roadLink)
    after should have size (2)
    after.filter(_.publicId == MassTransitStopOperations.RoadName_FI) should have size (1)
    after.filter(_.publicId == MassTransitStopOperations.RoadName_SE) should have size (1)
    after.filter(_.publicId == MassTransitStopOperations.RoadName_FI).head.values.head.asInstanceOf[PropertyValue].propertyValue should be ("user_road_name_fi")
    after.filter(_.publicId == MassTransitStopOperations.RoadName_SE).head.values.head.asInstanceOf[PropertyValue].propertyValue should be ("user_road_name_se")
  }

  test("Find more than one busStop with same passengerId"){
    runWithRollback {
      val (linkId1, linkId2) = (LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom())
      val eventbus = MockitoSugar.mock[DigiroadEventBus]
      val service = new TestMassTransitStopService(eventbus, mockRoadLinkService)
      val mockMunicipalityDao: MunicipalityDao = MockitoSugar.mock[MunicipalityDao]

      val roadLink = Seq(RoadLink(linkId1, List(Point(0.0,0.0), Point(10.0, 0.0)), 10, State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
      RoadLink(linkId2, List(Point(0.0,0.0), Point(20.0, 0.0)), 20, State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(91))))

      val ids = Seq(RollbackMassTransitStopService.create(NewMassTransitStop(5.0, 0.0, randomLinkId5, 2,
        Seq(
          SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("1"))),
          SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("2"))),
          SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("2"))),
          SimplePointAssetProperty("matkustajatunnus", Seq(PropertyValue("1000")))
        )), "masstransitstopservice_spec", roadLink.head),
      RollbackMassTransitStopService.create(NewMassTransitStop(15.0, 0.0, randomLinkId5, 2,
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
  test("Updating the mass transit stop from others -> ELY should not create a stop with liviId when flagged") {
    runWithRollback {
      val linkId1 = LinkIdGenerator.generateRandom()
      val roadLink = RoadLink(linkId1, List(Point(0.0, 0.0), Point(120.0, 0.0)), 120, State, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val id = RollbackMassTransitStopService.create(NewMassTransitStop(5.0, 0.0, randomLinkId5, 2,
        Seq(
          SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("1"))),
          SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("2"))),
          SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("2")))
        )), "masstransitstopservice_spec", roadLink)
      when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[String], any[Int], any[Option[Int]], any[Option[Int]]
      )).thenReturn((RoadAddress(Some("235"), 110, 10, Track.Combined, 108), RoadSide.Right))
      val service = RollbackMassTransitStopService
      val stop = service.getById(id).get
      val props = stop.propertyData
      val admin = props.find(_.publicId == "tietojen_yllapitaja").get
      val newAdmin = admin.copy(values = List(PropertyValue("2")))
      val types = props.find(_.publicId == "pysakin_tyyppi").get
      val newTypes = types.copy(values = List(PropertyValue("2"), PropertyValue("1")))
      admin.values.exists(_.asInstanceOf[PropertyValue].propertyValue == "1") should be(true)
      types.values.exists(_.asInstanceOf[PropertyValue].propertyValue == "2") should be(true)
      types.values.exists(_.asInstanceOf[PropertyValue].propertyValue == "1") should be(false)
      val newStop = stop.copy(stopTypes = Seq(2, 3),
        propertyData = props.filterNot(_.publicId == "tietojen_yllapitaja").filterNot(_.publicId == "pysakin_tyyppi") ++
          Seq(newAdmin, newTypes))
      val newProps = newStop.propertyData.map(prop => SimplePointAssetProperty(prop.publicId, prop.values)).toSet ++ Set(SimplePointAssetProperty("liviIdSave", Seq(PropertyValue("false"))))
      service.updateExistingById(stop.id, None, newProps, "seppo", { (Int, _) => Unit})
      val livi = service.getMassTransitStopById(id);
      getLiviIdValue(livi._1.get.propertyData) should be (None)
    }
  }

  test("a stop without tram type can not be against traffic direction") {
    runWithRollback {
      val linkId = LinkIdGenerator.generateRandom()
      val roadLink = RoadLink(linkId, List(Point(0.0, 0.0), Point(120.0, 0.0)), 120, State, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val exception = intercept[Exception] {
        RollbackMassTransitStopService.create(NewMassTransitStop(5.0, 0.0, linkId, 2,
          Seq(
            SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("1"))),
            SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("2"))),
            SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("3")))
          )), "test", roadLink)
      }
      exception.getMessage should be("Invalid Mass Transit Stop direction")
    }
  }

  test("a stop with tram type can be against traffic direction") {
    runWithRollback {
      val linkId = LinkIdGenerator.generateRandom()
      val roadLink = RoadLink(linkId, List(Point(0.0, 0.0), Point(120.0, 0.0)), 120, State, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      RollbackMassTransitStopService.create(NewMassTransitStop(5.0, 0.0, linkId, 2,
        Seq(
          SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("1"))),
          SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("1"))),
          SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("2"))),
          SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("3")))
        )), "test", roadLink)
    }
  }

  test("removing tram type from a stop against traffic direction throws exception") {
    runWithRollback {
      val linkId = LinkIdGenerator.generateRandom()
      val roadLink = RoadLink(linkId, List(Point(0.0, 0.0), Point(120.0, 0.0)), 120, State, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val properties = Seq(
        SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("1"))),
        SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("1"))),
        SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("2"))),
        SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("3")))
      )
      val stopId = RollbackMassTransitStopService.create(NewMassTransitStop(5.0, 0.0, linkId, 2, properties
        ), "test", roadLink)
      val newProperties = Set(
        SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("1"))),
        SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("2"))),
        SimplePointAssetProperty("vaikutussuunta", Seq(PropertyValue("3")))
      )
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(any[String], any[Boolean])).thenReturn(Some(roadLink))
      val exception = intercept[Exception] {
        RollbackMassTransitStopService.updateExistingById(stopId, None, newProperties, "test", { (_, _) => Unit })
      }
      exception.getMessage should be("Invalid Mass Transit Stop direction")
    }
  }
}


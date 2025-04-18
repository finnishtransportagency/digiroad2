package fi.liikennevirasto.digiroad2.dataimport

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import fi.liikennevirasto.digiroad2.Digiroad2Context.userProvider
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{FeatureClass, RoadLinkClient, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.csvDataImporter._
import fi.liikennevirasto.digiroad2.dao.{MassTransitStopDao, MunicipalityDao, RoadLinkDAO}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.{MassTransitStopService, MassTransitStopWithProperties, NewMassTransitStop, PersistedMassTransitStop}
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util.{GeometryTransform, LinkIdGenerator, RoadSide}
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, Tag}

import java.io.{ByteArrayInputStream, InputStream, InputStreamReader}

class MassTransitStopCsvImporterSpec extends AuthenticatedApiSpec with BeforeAndAfter {
  val MunicipalityKauniainen = 235
  val testUserProvider = userProvider
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val mockRoadLinkDAO = MockitoSugar.mock[RoadLinkDAO]

  val mockService = MockitoSugar.mock[MassTransitStopService]
  val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]

  val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockRoadLinkClient, mockRoadLinkService, mockEventBus, mockService)
  val (linkId1, linkId2) = (LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom())
  val roadLinkFetcheds = Seq(RoadLinkFetched(linkId1, 235, Seq(Point(2, 2), Point(4, 4)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers))
  val roadLink = Seq(RoadLink(linkId2, Seq(Point(2, 2), Point(4, 4)), 3.5, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None))

  val testMassTransitStopService: MassTransitStopService = new MassTransitStopService {
    override def eventbus: DigiroadEventBus = new DummyEventBus

    override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

    override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)

    override val massTransitStopDao: MassTransitStopDao = new MassTransitStopDao
    override val municipalityDao: MunicipalityDao = new MunicipalityDao
    override val roadLinkService: RoadLinkService = mockRoadLinkService
    override val geometryTransform: GeometryTransform = mockGeometryTransform
  }

  when(mockRoadLinkService.getClosestRoadlinkForCarTraffic(any[User], any[Point], any[Boolean], any[Boolean])).thenReturn(roadLink)
  when(mockRoadLinkService.enrichFetchedRoadLinks(any[Seq[RoadLinkFetched]], any[Boolean])).thenReturn(roadLink)

  def runWithRollback(test: => Unit): Unit = sTestTransactions.runWithRollback()(test)

  trait MassTransitStopImporterTest extends MassTransitStopCsvImporter {
    val defaultKeys : List[String]

    def createCSV(assets: Map[String, Any]*): String = {
      val headers = defaultKeys.mkString(";") + "\n"
      val rows = assets.map { asset =>
        defaultKeys.map { key => asset.getOrElse(key, "") }.mkString(";")
      }.mkString("\n")
      headers + rows
    }

    def csvRead(assetFields: Map[String, Any]*) : List[Map[String, String]]   = {
      val headers = defaultKeys.mkString(";") + "\n"
      val rows = assetFields.map { asset =>
        defaultKeys.map { key => asset.getOrElse(key, "") }.mkString(";")
      }.mkString("\n")
      val csv = headers + rows
      val streamReader = new InputStreamReader(csvToInputStream(csv), "UTF-8")
      val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
        override val delimiter: Char = ';'
      })
      csvReader.allWithHeaders()
    }
  }

  object massTransitStopImporterCreate extends MassTransitStopImporterTest {
    override lazy val roadLinkClient: RoadLinkClient = MockitoSugar.mock[RoadLinkClient]
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def eventBus: DigiroadEventBus = mockEventBus
    override def withDynTransaction[T](f: => T): T = f

    override val defaultKeys = mappings.keys.toList ::: coordinateMappings.keys.toList

    def defaultValues(assets: Map[String, Any]) : Map[String, Any]  = {
      defaultKeys.map { key => key -> assets.getOrElse(key, "") }.toMap
    }
  }

  object massTransitStopImporterUpdate extends MassTransitStopImporterTest {
    override lazy val roadLinkClient: RoadLinkClient = MockitoSugar.mock[RoadLinkClient]
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def eventBus: DigiroadEventBus = mockEventBus
    override def withDynTransaction[T](f: => T): T = f
    override val defaultKeys = mappings.keys.toList ::: nationalIdMapping.keys.toList

    def defaultValues(assets: Map[String, Any]) : Map[String, Any]  = {
      defaultKeys.map { key => key -> assets.getOrElse(key, "") }.toMap
    }
  }

  object massTransitStopImporterPosition extends MassTransitStopImporterTest {
    override lazy val roadLinkClient: RoadLinkClient = MockitoSugar.mock[RoadLinkClient]
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def eventBus: DigiroadEventBus = mockEventBus
    override def withDynTransaction[T](f: => T): T = f
    override val defaultKeys = mappings.keys.toList ::: coordinateMappings.keys.toList ::: nationalIdMapping.keys.toList

    def defaultValues(assets: Map[String, Any]) : Map[String, Any]  = {
      defaultKeys.map { key => key -> assets.getOrElse(key, "") }.toMap
    }
  }

  private val testUser: User = User(id = 1, username = "CsvDataImportApiSpec", configuration = Configuration(authorizedMunicipalities = Set(MunicipalityKauniainen)))

  before {
    testUserProvider.setCurrentUser(testUser)
  }

  val mockGeometryTransform = MockitoSugar.mock[GeometryTransform]

  class TestMassTransitStopCsvOperation(roadLinkClientImpl: RoadLinkClient, roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus, massTransitStopServiceImpl: MassTransitStopService) extends
    MassTransitStopCsvOperation(roadLinkClientImpl: RoadLinkClient, roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) {
    override lazy val propertyUpdater = new Updater(roadLinkClientImpl: RoadLinkClient, roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) {
      override def withDynTransaction[T](f: => T): T = f
      override def withDynSession[T](f: => T): T = f
      override def roadLinkService: RoadLinkService = roadLinkServiceImpl
      override def roadLinkClient: RoadLinkClient = roadLinkClientImpl
      override def eventBus: DigiroadEventBus = eventBusImpl

      override val massTransitStopService: MassTransitStopService = massTransitStopServiceImpl
    }
  }

  private def mockWithMassTransitStops(stops: Seq[(Long, AdministrativeClass)]): (MassTransitStopService, RoadLinkClient) = {
    val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
    stops.foreach { case(id, administrativeClass) =>
      when(mockRoadLinkService.getRoadLinksAndComplementaryByLinkIds(ArgumentMatchers.eq(Set(id.toString)), ArgumentMatchers.eq(false))).thenReturn(Seq(RoadLink(id.toString, Seq(Point(0,0)),10, administrativeClass, FunctionalClass1.value, TrafficDirection.BothDirections, LinkType.apply(1), modifiedAt = Some(""), modifiedBy = Some(""))))
    }

    val mockMassTransitStopDao = MockitoSugar.mock[MassTransitStopDao]
    val mockGeometryTransform = MockitoSugar.mock[GeometryTransform]
    when(mockMassTransitStopDao.getAssetAdministrationClass(any[Long])).thenReturn(None)

    class TestMassTransitStopService(val eventbus: DigiroadEventBus, val roadLinkService: RoadLinkService) extends MassTransitStopService {
      override def withDynSession[T](f: => T): T = f
      override def withDynTransaction[T](f: => T): T = f
      override val massTransitStopDao: MassTransitStopDao = MockitoSugar.mock[MassTransitStopDao]
      override val municipalityDao: MunicipalityDao = MockitoSugar.mock[MunicipalityDao]
      override val geometryTransform: GeometryTransform = mockGeometryTransform
    }

    when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[String], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn((RoadAddress(None, 0, 0, Track.Unknown, 0) , RoadSide.Right))
    val mockMassTransitStopService = MockitoSugar.mock[MassTransitStopService]
    stops.foreach { case (id, administrativeClass) =>
      when(mockMassTransitStopService.getByNationalId(ArgumentMatchers.eq(id), anyObject(), anyObject(), anyBoolean())).thenAnswer(new Answer[Option[Object]] {
        override def answer(invocation: InvocationOnMock): Option[Object] = {
          val transformation: PersistedMassTransitStop => (Object, Object) = invocation.getArguments()(2).asInstanceOf[PersistedMassTransitStop => (Object, Object)]
          val stop = PersistedMassTransitStop(id, id, id.toString, Nil, 235, 0.0, 0.0, 0.0, None, None, None, false, 0, Modification(None, None), Modification(None, None), Nil, NormalLinkInterface)
          Some(transformation(stop)._1)
        }
      })
    }

    when(mockMassTransitStopService.isFloating(any[PersistedPointAsset], any[Option[RoadLinkFetched]])).thenAnswer(new Answer[Object] {
      override def answer(invocation: InvocationOnMock): Object = {
        val persistedPointAsset: PersistedPointAsset  = invocation.getArguments()(0).asInstanceOf[PersistedPointAsset]
        val roadLinkFetched: Option[RoadLinkFetched]  = invocation.getArguments()(1).asInstanceOf[Option[RoadLinkFetched]]

        val testMassTransitStopService = new TestMassTransitStopService(new DummyEventBus, MockitoSugar.mock[RoadLinkService])
        testMassTransitStopService.isFloating(persistedPointAsset, roadLinkFetched)
      }
    })

    (mockMassTransitStopService, mockRoadLinkClient)
  }

  test("validation fails when asset type is unknown") {
    val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockRoadLinkClient, mockRoadLinkService, mockEventBus, mockService)

    val assetFields = Map("valtakunnallinen id" -> 1, "tietojen ylläpitäjä" -> 1, "pysäkin tyyppi" -> "2,10")
    val invalidCsv = massTransitStopImporterUpdate.csvRead(assetFields)

    val result = massTransitStopCsvOperation.propertyUpdater.processing(invalidCsv, testUser)
    result.malformedRows.size should be (1)
    result.malformedRows.head.malformedParameters.size should be (1)
    result.malformedRows.head.malformedParameters.head should be ("pysäkin tyyppi")
    result.incompleteRows.isEmpty should be (true)
    result.excludedRows.isEmpty should be (true)
    result.notImportedData.isEmpty should be (true)
  }

  test("validation fails when stop type is obsolete") {
    val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockRoadLinkClient, mockRoadLinkService, mockEventBus, mockService)

    val assetFields = Map("valtakunnallinen id" -> 1, "tietojen ylläpitäjä" -> 2, "pysäkin tyyppi" -> "3") // old long distance bus type
    val invalidCsv = massTransitStopImporterUpdate.csvRead(assetFields)

    val result = massTransitStopCsvOperation.propertyUpdater.processing(invalidCsv, testUser)
    result.malformedRows.size should be (1)
    result.malformedRows.head.malformedParameters.size should be (1)
    result.malformedRows.head.malformedParameters.head should be ("pysäkin tyyppi")
    result.incompleteRows.isEmpty should be (true)
    result.excludedRows.isEmpty should be (true)
    result.notImportedData.isEmpty should be (true)
  }

  test("validation fails when stop administrative property is unknown") {
    val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockRoadLinkClient, mockRoadLinkService, mockEventBus, mockService)

    val assetFields = Map("valtakunnallinen id" -> 1, "tietojen ylläpitäjä" -> 4, "pysäkin tyyppi" -> 2)
    val invalidCsv = massTransitStopImporterUpdate.csvRead(assetFields)

    val result = massTransitStopCsvOperation.propertyUpdater.processing(invalidCsv, testUser)
    result.malformedRows.size should be(1)
    result.malformedRows.head.malformedParameters.size should be(1)
    result.malformedRows.head.malformedParameters.head should be("tietojen ylläpitäjä")
    result.incompleteRows.isEmpty should be(true)
    result.excludedRows.isEmpty should be(true)
    result.notImportedData.isEmpty should be(true)
  }

  test("update asset admin id by CSV import") {
    val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockRoadLinkClient, mockRoadLinkService, mockEventBus, mockService)

    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(1l), anyObject(), anyBoolean())).thenReturn(Some(MassTransitStopWithProperties(1, "",1, Nil, 0.0, 0.0, None, None, None, false, Nil))).thenReturn(Some(MassTransitStopWithProperties(1, "", 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))
    val csv = massTransitStopImporterUpdate.csvRead(Map("valtakunnallinen id" -> 1, "pysäkin tyyppi" -> 1, "tietojen ylläpitäjä" -> 1, "ylläpitäjän tunnus" -> "NewAdminId"))
    massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser, Set())

    val properties = Set(SimplePointAssetProperty("yllapitajan_tunnus", Seq(PropertyValue("NewAdminId"))), SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("1"))), SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("1"))))
    verify(mockService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject(), anyBoolean(), anyBoolean())
  }

  test("Should not update asset LiVi id by CSV import") {
    val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockRoadLinkClient, mockRoadLinkService, mockEventBus, mockService)

    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(1l), anyObject(), anyBoolean())).thenReturn(Some(MassTransitStopWithProperties(1, "", 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))
    val csv = massTransitStopImporterUpdate.csvRead(Map("valtakunnallinen id" -> 1, "pysäkin tyyppi" -> 1, "tietojen ylläpitäjä" -> 1, "ylläpitäjän tunnus" -> "Livi987654"))

    massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser, Set()) should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset())
    val properties = Set(SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("1"))), SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("1"))), SimplePointAssetProperty("yllapitajan_tunnus", Seq(PropertyValue("Livi987654")))).filterNot(_.publicId == "yllapitajan_koodi")
    verify(mockService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject(), anyBoolean(),anyBoolean())
  }

  test("update asset stop code by CSV import") {
    val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockRoadLinkClient, mockRoadLinkService, mockEventBus, mockService)

    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(1l), anyObject(), anyBoolean())).thenReturn(Some(MassTransitStopWithProperties(1, "", 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))
    val csv = massTransitStopImporterUpdate.csvRead(Map("valtakunnallinen id" -> 1, "pysäkin tyyppi" -> 1, "tietojen ylläpitäjä" -> 1, "matkustajatunnus" -> "H156"))

    massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser) should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset())
    val properties = Set(SimplePointAssetProperty("matkustajatunnus", Seq(PropertyValue("H156"))), SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("1"))), SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("1"))))
    verify(mockService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject(), anyBoolean(),anyBoolean())
  }

  test("update additional information by CSV import", Tag("db")) {
    val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockRoadLinkClient, mockRoadLinkService, mockEventBus, mockService)

    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(1l), anyObject(), anyBoolean())).thenReturn(Some(MassTransitStopWithProperties(1, "", 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))

    val csv = massTransitStopImporterUpdate.csvRead(Map("valtakunnallinen id" -> 1, "pysäkin tyyppi" -> 1, "tietojen ylläpitäjä" -> 1, "lisätiedot" -> "Updated additional info"))

    massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser) should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset())
    val properties = Set(SimplePointAssetProperty("lisatiedot", Seq(PropertyValue("Updated additional info"))), SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("1"))), SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("1"))))
    verify(mockService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject(), anyBoolean(), anyBoolean())
  }

  val exampleValues = Map(
    "nimi_suomeksi" -> ("Passila", "Pasila"),
    "yllapitajan_tunnus" -> ("1234", "1281"),
    "matkustajatunnus" -> ("sdjkal", "9877"),
    "pysakin_tyyppi" -> ("1", "2"),
    "nimi_ruotsiksi" -> ("Bölle", "Böle"),
    "liikennointisuunta" -> ("Itään", "Pohjoiseen"),
    "katos" -> ("1", "2"),
    "aikataulu" -> ("1", "2"),
    "mainoskatos" -> ("1", "2"),
    "penkki" -> ("1", "2"),
    "pyorateline" -> ("1", "2"),
    "sahkoinen_aikataulunaytto" -> ("1", "2"),
    "valaistus" -> ("1", "2"),
    "saattomahdollisuus_henkiloautolla" -> ("1", "2"),
    "lisatiedot" -> ("qwer", "asdf"),
    "tietojen_yllapitaja" -> ("1", "3"),
    "korotettu" -> ("1", "2"),
    "roska_astia" -> ("1", "2"),
    "sahkoinen_aikataulunaytto" -> ("1", "2"),
    "vyohyketieto" -> ("InfoZone", "InfoZone ready"),
    "pysakin_palvelutaso" -> ("1", "2"),
    "alternative_link_id" -> ("velink", "velinkid"),
    "pysakin_omistaja" -> ("omistaja", "pysak_omistaja"),
    "osoite_suomeksi" -> ("osoite-fi", "osoite_fi"),
    "osoite_ruotsiksi" -> ("osoite-se", "osoite_se"),
    "laiturinumero" -> ("laiturinro", "laiturino"),
    "esteettomyys_liikuntarajoitteiselle" -> ("esteet", "esteett"),
    "liityntapysakointipaikkojen_maara" -> ("100", "10"),
    "liityntapysakoinnin_lisatiedot" -> ("lplisa", "lplisat"),
    "palauteosoite" -> ("palaute", "posoite"),
    "ensimmainen_voimassaolopaiva" -> ("1.1.2000", "1.1.2001"),
    "viimeinen_voimassaolopaiva" -> ("1.1.2029", "1.1.2028"),
    "inventointipaiva" -> ("1.1.2020", "1.1.2025"),
    "maastokoordinaatti_x" -> ("775838", "848839"),
    "maastokoordinaatti_y" -> ("775839", "232233"),
    "maastokoordinaatti_z" -> ("0", "10")
  )

  when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[String], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn((RoadAddress(None, 0, 0, Track.Unknown, 0) , RoadSide.Right))

  when(mockService.getByNationalId(ArgumentMatchers.eq(1), anyObject(), anyObject(), anyBoolean())).thenAnswer(new Answer[Option[Object]] {
    override def answer(invocation: InvocationOnMock): Option[Object] = {
      val transformation: PersistedMassTransitStop => (Object, Object) = invocation.getArguments()(2).asInstanceOf[PersistedMassTransitStop => (Object, Object)]
      val stop = PersistedMassTransitStop(1, 1, "1", Nil, 235, 0.0, 0.0, 0.0, None, None, None, false, 0, Modification(None, None), Modification(None, None), Nil, NormalLinkInterface)
      Some(transformation(stop)._1)
    }
  })

  test("update asset's properties in a generic manner", Tag("db")) {
    val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockRoadLinkClient, mockRoadLinkService, mockEventBus, mockService)

    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(1l), anyObject(), anyBoolean())).thenReturn(Some(MassTransitStopWithProperties(1, "", 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))
    val csv =  massTransitStopImporterUpdate.csvRead(Map("valtakunnallinen id" -> 1) ++ massTransitStopImporterUpdate.mappings.mapValues(exampleValues(_)._2))

    massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser, Set()) should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset())
    val properties: Set[SimplePointAssetProperty] = exampleValues.map { case (key, value) =>
      SimplePointAssetProperty(key, Seq(PropertyValue(value._2)))
    }.filterNot(_.publicId == "yllapitajan_koodi").toSet

    verify(mockService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject(), anyBoolean(), anyBoolean())
  }

  test("When imported value is a comma (-), value should be set to empty for text fields and unknown for multiple choice fields") {
    val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockRoadLinkClient, mockRoadLinkService, mockEventBus, mockService)

    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(1l), anyObject(), anyBoolean())).thenReturn(Some(MassTransitStopWithProperties(1, "", 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))
    val csv = massTransitStopImporterUpdate.csvRead(Map("valtakunnallinen id" -> 1, "tietojen ylläpitäjä" -> 1, "pysäkin tyyppi" -> 1, "palauteosoite" -> "posoite", "pysäkin palvelutaso" -> "1"))

    massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser, Set()) should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset())

    val csvWithDash = massTransitStopImporterUpdate.csvRead(Map("valtakunnallinen id" -> 1, "tietojen ylläpitäjä" -> 1, "pysäkin tyyppi" -> 1, "palauteosoite" -> "-", "pysäkin palvelutaso" -> "-"))
    massTransitStopCsvOperation.propertyUpdater.processing(csvWithDash, testUser, Set()) should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset())

    val properties2: Set[SimplePointAssetProperty] = Set(SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("1"))), SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("1"))),SimplePointAssetProperty("palauteosoite", Seq(PropertyValue(""))), SimplePointAssetProperty("pysakin_palvelutaso", Seq(PropertyValue("99"))))
    verify(mockService).updateExistingById(ArgumentMatchers.eq(1L), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties2), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject(), anyBoolean(), anyBoolean())
  }

  test("raise an error when updating non-existent asset", Tag("db")) {
    val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockRoadLinkClient, mockRoadLinkService, mockEventBus, mockService)

    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(6l), anyObject(), anyBoolean())).thenReturn(None)
    val assetFields = Map("valtakunnallinen id" -> "6", "tietojen ylläpitäjä" -> 1, "pysäkin tyyppi" -> 1, "pysäkin nimi" -> "AssetName")
    val csv =  massTransitStopImporterUpdate.csvRead(assetFields)

    val result = massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser, Set())

    result should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset(
      notImportedData = List(massTransitStopCsvOperation.propertyUpdater.NotImportedData(reason = "Asset not found -> 6",
        csvRow = massTransitStopCsvOperation.propertyUpdater.rowToString(massTransitStopImporterUpdate.defaultValues(assetFields))))))
  }

  test("raise an error when csv row does not define required parameter", Tag("db")) {
    val mockService = MockitoSugar.mock[MassTransitStopService]
    val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockRoadLinkClient, mockRoadLinkService, mockEventBus, mockService)

    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(1l), anyObject(), anyBoolean())).thenReturn(Some(MassTransitStopWithProperties(1, "", 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))
    val assetFields = massTransitStopImporterUpdate.defaultValues(Map("valtakunnallinen id" -> 1, "tietojen ylläpitäjä" -> 1, "pysäkin tyyppi" -> 1))
    val csv = massTransitStopImporterUpdate.csvRead(assetFields).map(fields => fields.filterNot(field => field._1 == "pysäkin nimi"))

    val result = massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser, Set())
    result should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset(
      incompleteRows = List(IncompleteRow(missingParameters = List("pysäkin nimi"),
        csvRow = massTransitStopCsvOperation.propertyUpdater.rowToString(massTransitStopImporterUpdate.defaultValues(Map()) - "pysäkin nimi" ++ Map("valtakunnallinen id" -> 1, "tietojen ylläpitäjä" -> 1, "pysäkin tyyppi" -> 1))))))

    verify(mockService, never).updateExistingById(anyLong(), anyObject(), anyObject(), anyString(), anyObject(), anyBoolean(), anyBoolean())
  }
    test("ignore updates on other road types than streets when import is limited to streets") {
      val (mockMassTransitStopService, mockRoadLinkClient) = mockWithMassTransitStops(Seq((1l, State)))
      val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockRoadLinkClient, mockRoadLinkService, mockEventBus, mockMassTransitStopService)
      val assetFields = Map("valtakunnallinen id" -> 1, "tietojen ylläpitäjä" -> 1, "pysäkin tyyppi" -> 1, "pysäkin nimi" -> "NewName")
      val csv =  massTransitStopImporterUpdate.csvRead(assetFields)

      val result = massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser,  roadTypeLimitations = Set(State, Private))
      result should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset(
        excludedRows = List(ExcludedRow(affectedRows = "State", csvRow = massTransitStopCsvOperation.propertyUpdater.rowToString(massTransitStopImporterUpdate.defaultValues(assetFields))))))

      verify(mockMassTransitStopService, never).updateExistingById(anyLong(), anyObject(), anyObject(), anyString(), anyObject(), anyBoolean(), anyBoolean())
    }

    test("update asset on street when import is limited to streets") {
      val (mockMassTransitStopService, mockRoadLinkClient) = mockWithMassTransitStops(Seq((1l, Municipality)))
      val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockRoadLinkClient, mockRoadLinkService, mockEventBus, mockMassTransitStopService)
      val csv = massTransitStopImporterUpdate.csvRead(Map("valtakunnallinen id" -> 1, "tietojen ylläpitäjä" -> 1, "pysäkin tyyppi" -> 1, "pysäkin nimi" -> "NewName"))

      val result = massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser, roadTypeLimitations = Set(State, Private))
      result should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset())
      val properties = Set(SimplePointAssetProperty("nimi_suomeksi", Seq(PropertyValue("NewName"))), SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("1"))), SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("1"))))
      verify(mockMassTransitStopService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject(), anyBoolean(), anyBoolean())
    }

    test("update asset on roads and streets when import is limited to roads and streets") {
      val (mockMassTransitStopService, mockRoadLinkClient) = mockWithMassTransitStops(Seq((1l, Municipality), (2l, State)))
      val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockRoadLinkClient, mockRoadLinkService, mockEventBus, mockMassTransitStopService)
      val csv = massTransitStopImporterUpdate.csvRead(Map("valtakunnallinen id" -> 1, "tietojen ylläpitäjä" -> 1, "pysäkin tyyppi" -> 1, "pysäkin nimi" -> "NewName1"), Map("valtakunnallinen id" -> 2, "tietojen ylläpitäjä" -> 2, "pysäkin tyyppi" -> 2, "pysäkin nimi" -> "NewName2"))

      val result = massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser, roadTypeLimitations = Set(Private))
      result should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset())

      val properties1 = Set(SimplePointAssetProperty("nimi_suomeksi", List(PropertyValue("NewName1"))), SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("1"))), SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("1"))))
      val properties2 = Set(SimplePointAssetProperty("nimi_suomeksi", List(PropertyValue("NewName2"))), SimplePointAssetProperty("pysakin_tyyppi", Seq(PropertyValue("2"))), SimplePointAssetProperty("tietojen_yllapitaja", Seq(PropertyValue("2"))))
      verify(mockMassTransitStopService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties1), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject(), anyBoolean(), anyBoolean())
      verify(mockMassTransitStopService).updateExistingById(ArgumentMatchers.eq(2l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties2), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject(), anyBoolean(), anyBoolean())
    }

    test("ignore updates on all other road types than private roads when import is limited to private roads") {
      val (mockMassTransitStopService, mockRoadLinkClient) = mockWithMassTransitStops(Seq((1l, Municipality), (2l, State)))
      val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockRoadLinkClient, mockRoadLinkService, mockEventBus, mockMassTransitStopService)
      val assetOnStreetFields = Map("valtakunnallinen id" -> 1, "tietojen ylläpitäjä" -> 2, "pysäkin tyyppi" -> 2, "pysäkin nimi" -> "NewName1")
      val assetOnRoadFields = Map("valtakunnallinen id" -> 2, "tietojen ylläpitäjä" -> 1, "pysäkin tyyppi" -> 2, "pysäkin nimi" -> "NewName2")
      val csv = massTransitStopImporterUpdate.csvRead(assetOnStreetFields, assetOnRoadFields)

      val result = massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser, roadTypeLimitations = Set(State, Municipality))
      result should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset(
        excludedRows = List(ExcludedRow(affectedRows = "State", csvRow = massTransitStopCsvOperation.propertyUpdater.rowToString(massTransitStopImporterUpdate.defaultValues(assetOnRoadFields))),
          ExcludedRow(affectedRows = "Municipality", csvRow = massTransitStopCsvOperation.propertyUpdater.rowToString(massTransitStopImporterUpdate.defaultValues(assetOnStreetFields))))))

      verify(mockMassTransitStopService, never).updateExistingById(anyLong(), anyObject(), anyObject(), anyString(), anyObject(), anyBoolean(), anyBoolean())
    }

  private def csvToInputStream(csv: String): InputStream = new ByteArrayInputStream(csv.getBytes())
}

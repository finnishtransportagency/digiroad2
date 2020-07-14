package fi.liikennevirasto.digiroad2.util

import java.io.{BufferedWriter, File, FileWriter}
import java.security.InvalidParameterException
import java.sql.SQLIntegrityConstraintViolationException
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.{Date, NoSuchElementException, Properties}

import com.googlecode.flyway.core.Flyway
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.tierekisteri._
import fi.liikennevirasto.digiroad2.client.viite.SearchViiteClient
import fi.liikennevirasto.digiroad2.client.vvh.ChangeType.New
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.RoadLinkDAO.{AdministrativeClassDao, FunctionalClassDao, LinkAttributesDao}
import fi.liikennevirasto.digiroad2.dao.{OracleUserProvider, _}
import fi.liikennevirasto.digiroad2.dao.linearasset.{OracleLinearAssetDao, OracleSpeedLimitDao}
import fi.liikennevirasto.digiroad2.dao.pointasset.Obstacle
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.middleware.TrafficSignManager
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import fi.liikennevirasto.digiroad2.process.SpeedLimitValidator
import fi.liikennevirasto.digiroad2.service._
import fi.liikennevirasto.digiroad2.service.linearasset.{RoadWorkService, _}
import fi.liikennevirasto.digiroad2.service.pointasset._
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.{MassTransitStopOperations, MassTransitStopService, PersistedMassTransitStop, TierekisteriBusStopStrategyOperations}
import fi.liikennevirasto.digiroad2.user.{User, UserProvider}
import fi.liikennevirasto.digiroad2.util.AssetDataImporter.Conversion
import fi.liikennevirasto.digiroad2.{GeometryUtils, TrafficSignTypeGroup, _}
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import scala.collection.mutable.ListBuffer


object DataFixture {
  val TestAssetId = 300000
  lazy val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/bonecp.properties"))
    props
  }

  lazy val dr2properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }

  val dataImporter = new AssetDataImporter
  lazy val vvhClient: VVHClient = {
    new VVHClient(dr2properties.getProperty("digiroad2.VVHRestApiEndPoint"))
  }

  lazy val viiteClient: SearchViiteClient = {
    new SearchViiteClient(dr2properties.getProperty("digiroad2.viiteRestApiEndPoint"), HttpClientBuilder.create().build())
  }

  lazy val roadLinkService: RoadLinkService = {
    new RoadLinkService(vvhClient, eventbus, new DummySerializer)
  }

  lazy val obstacleService: ObstacleService = {
    new ObstacleService(roadLinkService)
  }

  lazy val tierekisteriClient: TierekisteriMassTransitStopClient = {
    new TierekisteriMassTransitStopClient(dr2properties.getProperty("digiroad2.tierekisteriRestApiEndPoint"),
      dr2properties.getProperty("digiroad2.tierekisteri.enabled").toBoolean,
      HttpClientBuilder.create().build())
  }

  lazy val eventbus: DigiroadEventBus = {
    new DigiroadEventBus
  }
  lazy val linearAssetService: LinearAssetService = {
    new LinearAssetService(roadLinkService, new DummyEventBus)
  }

  lazy val roadWidthService: RoadWidthService = {
    new RoadWidthService(roadLinkService, new DummyEventBus)
  }

  lazy val speedLimitService: SpeedLimitService = {
    new SpeedLimitService(new DummyEventBus, vvhClient, roadLinkService)
  }

  lazy val manoeuvreService: ManoeuvreService = {
    new ManoeuvreService(roadLinkService, new DummyEventBus)
  }

  lazy val prohibitionService: ProhibitionService = {
    new ProhibitionService(roadLinkService, eventbus)
  }

  lazy val trafficSignService: TrafficSignService = {
    new TrafficSignService(roadLinkService, eventbus)
  }

  lazy val trafficSignManager: TrafficSignManager = {
    new TrafficSignManager(manoeuvreService, roadLinkService)
  }

  lazy val speedLimitValidator: SpeedLimitValidator = {
    new SpeedLimitValidator(trafficSignService)
  }

  lazy val roadAddressService: RoadAddressService  = {
    new RoadAddressService(viiteClient)
  }

  lazy val massTransitStopService: MassTransitStopService = {
    class MassTransitStopServiceWithDynTransaction(val eventbus: DigiroadEventBus, val roadLinkService: RoadLinkService, val roadAddressService: RoadAddressService) extends MassTransitStopService {
      override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
      override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
      override val tierekisteriClient: TierekisteriMassTransitStopClient = DataFixture.tierekisteriClient
      override val massTransitStopDao: MassTransitStopDao = new MassTransitStopDao
      override val municipalityDao: MunicipalityDao = new MunicipalityDao
      override val geometryTransform: GeometryTransform = new GeometryTransform(roadAddressService)
    }
    new MassTransitStopServiceWithDynTransaction(eventbus, roadLinkService, roadAddressService)
  }

  lazy val geometryTransform: GeometryTransform = {
    new GeometryTransform(roadAddressService)
  }

  lazy val geometryVKMTransform: VKMGeometryTransform = {
    new VKMGeometryTransform()
  }

  lazy val oracleLinearAssetDao : OracleLinearAssetDao = {
    new OracleLinearAssetDao(vvhClient, roadLinkService)
  }

  lazy val inaccurateAssetDAO : InaccurateAssetDAO = {
    new InaccurateAssetDAO()
  }

  lazy val tierekisteriLightingAsset : TierekisteriLightingAssetClient = {
    new TierekisteriLightingAssetClient(getProperty("digiroad2.tierekisteriRestApiEndPoint"),
      getProperty("digiroad2.tierekisteri.enabled").toBoolean,
      HttpClientBuilder.create().build())
  }

  lazy val tierekisteriRoadWidthAsset : TierekisteriRoadWidthAssetClient = {
    new TierekisteriRoadWidthAssetClient(getProperty("digiroad2.tierekisteriRestApiEndPoint"),
      getProperty("digiroad2.tierekisteri.enabled").toBoolean,
      HttpClientBuilder.create().build())
  }

  lazy val maintenanceService: MaintenanceService = {
    new MaintenanceService(roadLinkService, new DummyEventBus)
  }

  lazy val roadWorkService: RoadWorkService = {
    new RoadWorkService(roadLinkService, eventbus)
  }

  lazy val tierekisteriSpeedLimitAsset : TierekisteriSpeedLimitAssetClient = {
    new TierekisteriSpeedLimitAssetClient(getProperty("digiroad2.tierekisteriRestApiEndPoint"),
      getProperty("digiroad2.tierekisteri.enabled").toBoolean,
      HttpClientBuilder.create().build())
  }

  lazy val assetDao : OracleAssetDao = {
    new OracleAssetDao()
  }

  lazy val dynamicLinearAssetDao : DynamicLinearAssetDao = {
    new DynamicLinearAssetDao()
  }

  lazy val dynamicLinearAssetService : DynamicLinearAssetService = {
    new DynamicLinearAssetService(roadLinkService, new DummyEventBus)
  }


  lazy val speedLimitDao: OracleSpeedLimitDao = {
    new OracleSpeedLimitDao(null, null)
  }

  lazy val verificationService: VerificationService = {
    new VerificationService( new DummyEventBus, roadLinkService)
  }

  lazy val roadLinkTempDao : RoadLinkTempDAO = {
    new RoadLinkTempDAO
  }

  lazy val trafficSignProhibitionGenerator: TrafficSignProhibitionGenerator = {
    new TrafficSignProhibitionGenerator(roadLinkService)
  }

  lazy val trafficSignRoadWorkGenerator: TrafficSignRoadWorkGenerator = {
    new TrafficSignRoadWorkGenerator(roadLinkService)
  }

  lazy val trafficSignHazmatTransportProhibitionGenerator: TrafficSignHazmatTransportProhibitionGenerator = {
    new TrafficSignHazmatTransportProhibitionGenerator(roadLinkService)
  }

  lazy val trafficSignParkingProhibitionGenerator: TrafficSignParkingProhibitionGenerator = {
    new TrafficSignParkingProhibitionGenerator(roadLinkService)
  }

  lazy val municipalityService: MunicipalityService = new MunicipalityService

  def getProperty(name: String) = {
    val property = dr2properties.getProperty(name)
    if(property != null)
      property
    else
      throw new RuntimeException(s"cannot find property $name")
  }

  def flyway: Flyway = {
    val flyway = new Flyway()
    flyway.setDataSource(ds)
    flyway.setInitVersion("-1")
    flyway.setInitOnMigrate(true)
    flyway.setLocations("db.migration")
    flyway
  }

  def migrateTo(version: String) = {
    val migrator = flyway
    migrator.setTarget(version.toString)
    migrator.migrate()
  }

  def migrateAll() = {
    flyway.migrate()
  }

  def tearDown() {
    flyway.clean()
  }

  def setUpTest() {
    migrateAll()
    importMunicipalityCodes()
    updateMunicipalities()
    SqlScriptRunner.runScripts(List(
      "insert_test_fixture.sql",
      "insert_users.sql",
      "kauniainen_production_speed_limits.sql",
      "kauniainen_total_weight_limits.sql",
      "kauniainen_manoeuvres.sql",
      "kauniainen_functional_classes.sql",
      "kauniainen_traffic_directions.sql",
      "kauniainen_link_types.sql",
      "test_fixture_sequences.sql",
      "kauniainen_lit_roads.sql",
      "kauniainen_vehicle_prohibitions.sql",
      "kauniainen_paved_roads.sql",
      "kauniainen_pedestrian_crossings.sql",
      "kauniainen_obstacles.sql",
      "kauniainen_european_roads.sql",
      "kauniainen_exit_numbers.sql",
      "kauniainen_traffic_lights.sql",
      "kauniainen_railway_crossings.sql",
      "kauniainen_traffic_signs.sql",
      "kauniainen_maximum_x7_restrictions.sql",
      "user_notification_examples.sql",
      "siilinjarvi_verificationService_test_data.sql"
    ))
  }

  def importMunicipalityCodes() {
    println("\nCommencing municipality code import at time: ")
    println(DateTime.now())
    new MunicipalityCodeImporter().importMunicipalityCodes()
    println("Municipality code import complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def updateMunicipalities() {
    println("\nCommencing municipality update at time: ")
    println(DateTime.now())
    new MunicipalityCodeImporter().updateMunicipalityCodes()
    println("Municipality update complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def importRoadLinkData() = {
    println("\nCommencing functional classes import from conversion DB\n")
    RoadLinkDataImporter.importFromConversionDB()
  }

  def splitSpeedLimitChains(): Unit = {
    println("\nCommencing Speed limit splitting at time: ")
    println(DateTime.now())
    println("split limits")
    dataImporter.splitMultiLinkSpeedLimitsToSingleLinkLimits()
    println("splitting complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def splitLinearAssets() {
    println("\nCommencing Linear asset splitting at time: ")
    println(DateTime.now())
    println("split assets")
    val assetTypes = Seq(30, 40, 50, 60, 70, 80, 90, 100)
    assetTypes.foreach { typeId =>
      println("Splitting asset type " + typeId)
      dataImporter.splitMultiLinkAssetsToSingleLinkAssets(typeId)
    }
    println("splitting complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def importEuropeanRoads(): Unit = {
    println(s"\nCommencing European road import from conversion at time: ${DateTime.now()}")
    dataImporter.importEuropeanRoads(Conversion.database(), dr2properties.getProperty("digiroad2.VVHServiceHost"))
    println(s"European road import complete at time: ${DateTime.now()}")
    println()
  }

  def importProhibitions(): Unit = {
    println(s"\nCommencing prohibition import from conversion at time: ${DateTime.now()}")
    dataImporter.importProhibitions(Conversion.database(), dr2properties.getProperty("digiroad2.VVHServiceHost"))
    println(s"Prohibition import complete at time: ${DateTime.now()}")
    println()
  }

  def importHazmatProhibitions(): Unit = {
    println(s"\nCommencing hazmat prohibition import at time: ${DateTime.now()}")
    dataImporter.importHazmatProhibitions()
    println(s"Prohibition import complete at time: ${DateTime.now()}")
    println()
  }

  def generateDroppedAssetsCsv(): Unit = {
    println("\nGenerating list of linear assets outside geometry")
    println(DateTime.now())
    val csvGenerator = new CsvGenerator(dr2properties.getProperty("digiroad2.VVHServiceHost"))
    csvGenerator.generateDroppedNumericalLimits()
    csvGenerator.generateCsvForTextualLinearAssets(260, "european_roads")
    csvGenerator.generateCsvForTextualLinearAssets(270, "exit_numbers")
    csvGenerator.generateDroppedProhibitions(190, "vehicle_prohibitions")
    csvGenerator.generateDroppedProhibitions(210, "hazmat_vehicle_prohibitions")
    println("complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def generateDroppedManoeuvres(): Unit = {
    println("\nGenerating list of manoeuvres outside geometry")
    println(DateTime.now())
    val csvGenerator = new CsvGenerator(dr2properties.getProperty("digiroad2.VVHServiceHost"))
    csvGenerator.generateDroppedManoeuvres()
    println("complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def unfloatLinearAssets(): Unit = {
    println("\nUnfloat multi link linear assets")
    println(DateTime.now())
    dataImporter.unfloatLinearAssets()
    println("complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def expireSplitAssetsWithoutMml(): Unit = {
    println("\nExpiring split linear assets that do not have mml id")
    println(DateTime.now())
    val assetTypes = Seq(30, 40, 50, 60, 70, 80, 90, 100)
    assetTypes.foreach { typeId =>
      println("Expiring asset type " + typeId)
      dataImporter.expireSplitAssetsWithoutMml(typeId)
    }
    println("complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def generateValuesForLitRoads(): Unit = {
    println("\nGenerating values for lit roads")
    println(DateTime.now())
    dataImporter.generateValuesForLitRoads()
    println("complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def adjustToNewDigitization(): Unit = {
    println("\nAdjusting side codes and m-values according new digitization directions")
    println(DateTime.now())
    dataImporter.adjustToNewDigitization(dr2properties.getProperty("digiroad2.VVHServiceHost"))
    println("complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  private def createAndFloat(incomingObstacle: IncomingObstacle) = {
    withDynTransaction {
      val id = dataImporter.createFloatingObstacle(incomingObstacle)
      println("Created floating obstacle id=" + id)
    }
  }

  /**
    * Gets list of masstransitstops and populates addresses field with street name found from VVH
    */
  private def getMassTransitStopAddressesFromVVH(): Unit =
  {
    println("\nCommencing address information import from VVH road links to mass transit stops at time: ")
    println(DateTime.now())
    dataImporter.getMassTransitStopAddressesFromVVH(dr2properties.getProperty("digiroad2.VVHRestApiEndPoint"))
    println("complete at time: ")
    println(DateTime.now())
    println("\n")

  }

  def linkFloatObstacleAssets(): Unit = {
    println("\nGenerating list of Obstacle assets to linking")
    println(DateTime.now())
    val vvhClient = new VVHClient(dr2properties.getProperty("digiroad2.VVHRestApiEndPoint"))
    val roadLinkService = new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer)
    val batchSize = 1000
    var obstaclesFound = true
    var lastIdUpdate : Long = 0
    var processedCount = 0
    var updatedCount = 0

    var updateList: List[Obstacle] = List()

    do {
      //Send "1" for get all floating Obstacles assets
      //lastIdUpdate - Id where to start the fetch
      //batchSize - Max number of obstacles to fetch at a time
      val floatingObstaclesAssets =
      withDynTransaction {
        obstacleService.getFloatingObstacles(1, lastIdUpdate, batchSize)
      }
      obstaclesFound = floatingObstaclesAssets.nonEmpty
      lastIdUpdate = floatingObstaclesAssets.map(_.id).reduceOption(_ max _).getOrElse(Long.MaxValue)
      for (obstacleData <- floatingObstaclesAssets) {
        println("Processing obstacle id "+obstacleData.id)

        //Call filtering operations according to rules where
        val obstacleToUpdate = dataImporter.updateObstacleToRoadLink(obstacleData, roadLinkService)
        //Save updated assets to database
        if (!obstacleData.equals(obstacleToUpdate)){
          updateList = updateList :+ obstacleToUpdate
          updatedCount += 1
        }
        processedCount += 1
      }
    } while (obstaclesFound)
    withDynTransaction {
      updateList.foreach(o => obstacleService.updateFloatingAsset(o))
    }

    println("\n")
    println("Processed "+processedCount+" obstacles")
    println("Updated "+updatedCount+" obstacles")
    println("Complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def checkUnknownSpeedlimits(): Unit = {
    println("\nCleaning SpeedLimits with value from UnknownSpeedlimit working list")
    println(DateTime.now())

    val unknowns = speedLimitService.getUnknown(Set(), None)
    println("\nVerifying " + unknowns.size + " Unknowns Speedlimits")
    unknowns.foreach { case (_, mapped) =>
      mapped.foreach {
        case (_, x) =>
          x match {
            case u: List[Any] =>
              speedLimitService.purgeUnknown(u.asInstanceOf[List[Long]].toSet, Seq())
            case _ =>
          }
        case _ =>
      }
    }

    println("\n")
    println("Complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def transisStopAssetsFloatingReason() : Unit = {
    println("\nSet mass transit stop asset with roadlink administrator class and floating reason")
    println(DateTime.now())

    val municipalities: Seq[Int] =
      OracleDatabase.withDynSession {
        Queries.getMunicipalities
    }

    val floatingReasonPublicId = "kellumisen_syy"
    val administrationClassPublicId = "linkin_hallinnollinen_luokka"

    OracleDatabase.withDynTransaction{

      val floatingReasonPropertyId = dataImporter.getPropertyTypeByPublicId(floatingReasonPublicId)
      val administrationClassPropertyId = dataImporter.getPropertyTypeByPublicId(administrationClassPublicId)

      municipalities.foreach { municipality =>
        println("Start processing municipality %d".format(municipality))

        println("Start setting floating reason")
        setTransitStopAssetFloatingReason(floatingReasonPublicId, floatingReasonPropertyId, municipality)

        println("Start setting the administration class")
        setTransitStopAssetAdministrationClass(administrationClassPublicId, administrationClassPropertyId, municipality)

        println("End processing municipality %d".format(municipality))
      }
    }

    println("\n")
    println("Complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  private def setTransitStopAssetFloatingReason(propertyPublicId: String, propertyId: Long, municipality: Int): Unit = {
    //Get all floating mass transit stops by municipality id
    val assets = dataImporter.getFloatingAssetsWithNumberPropertyValue(10, propertyPublicId, municipality)

    println("Processing %d assets floating".format(assets.length))

    if(assets.nonEmpty){

      val roadLinks = vvhClient.roadLinkData.fetchByLinkIds(assets.map(_._2).toSet)

      assets.foreach {
        _ match {
          case (assetId, linkId, point, mValue, None) =>
            val roadlink = roadLinks.find(_.linkId == linkId)
            //val point = bytesToPoint(geometry)
            PointAssetOperations.isFloating(municipalityCode = municipality, lon = point.x, lat = point.y,
              mValue = mValue, roadLink = roadlink) match {
              case (isFloating, Some(reason)) =>
                dataImporter.insertNumberPropertyData(propertyId, assetId, reason.value)
              case _ =>
                dataImporter.insertNumberPropertyData(propertyId, assetId, FloatingReason.Unknown.value)
            }
          case (assetId, linkId, point, mValue, Some(value)) =>
            println("The asset with id %d already have a floating reason".format(assetId))
        }
      }
    }

  }

  private def setTransitStopAssetAdministrationClass(propertyPublicId: String, propertyId: Long, municipality: Int): Unit = {
    //Get all no floating mass transit stops by municipality id
    val assets = dataImporter.getNonFloatingAssetsWithNumberPropertyValue(10, propertyPublicId, municipality)

    println("Processing %d assets not floating".format(assets.length))

    if(assets.nonEmpty){
      //Get All RoadLinks from VVH by asset link ids
      val roadLinks = vvhClient.roadLinkData.fetchByLinkIds(assets.map(_._2).toSet)

      assets.foreach{
        _ match {
          case (assetId, linkId, None) =>
            roadLinks.find(_.linkId == linkId) match {
              case Some(roadlink) =>
                dataImporter.insertNumberPropertyData(propertyId, assetId, roadlink.administrativeClass.value)
              case _ =>
                println("The roadlink with id %d was not found".format(linkId))
            }
          case (assetId, linkId, Some(value)) =>
            println("The administration class property already exists on the asset with id %d ".format(assetId))
        }
      }
    }
  }

  def verifyRoadLinkAdministrativeClassChanged(): Unit = {
    println("\nVerify if roadlink administrator class and floating reason of mass transit stop asset was modified")
    println(DateTime.now())

    val municipalities: Seq[Int] =
      OracleDatabase.withDynSession {
        Queries.getMunicipalities
      }

    val administrationClassPublicId = "linkin_hallinnollinen_luokka"

    OracleDatabase.withDynTransaction {

      val administrationClassPropertyId = dataImporter.getPropertyTypeByPublicId(administrationClassPublicId)

      municipalities.foreach { municipality =>
        println("Start processing municipality %d".format(municipality))

        println("Start verification if road link administrative class is changed")
        verifyIsChanged(administrationClassPublicId, administrationClassPropertyId, municipality)

        println("End processing municipality %d".format(municipality))
      }
    }

    println("\n")
    println("Complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  private def updateTierekisteriBusStopsWithoutOTHLiviId(dryRun: Boolean, boundsOffset: Double = 10): Unit ={

    case class NearestBusStops(trBusStop: TierekisteriMassTransitStop, othBusStop: PersistedMassTransitStop, distance: Double)
    def hasLiviIdPropertyValue(persistedStop: PersistedMassTransitStop): Boolean ={
      persistedStop.propertyData.
        exists(property => property.publicId == "yllapitajan_koodi" && property.values.exists(value => !value.asInstanceOf[PropertyValue].propertyValue.isEmpty))
    }

    println("\nGet the list of tierekisteri bus stops that doesn't have livi id in OTH")
    println(DateTime.now())

    val existingLiviIds = dataImporter.getExistingLiviIds()

    val trBusStops = tierekisteriClient.fetchActiveMassTransitStops().
      filterNot(stop => existingLiviIds.contains(stop.liviId))

    val liviIdPropertyId = OracleDatabase.withDynSession {dataImporter.getPropertyTypeByPublicId("yllapitajan_koodi")}

    println("Processing %d TR bus stops".format(trBusStops.length))

    val busStops = trBusStops.flatMap{
      trStop =>
        try {
          val stopPointOption = withDynSession{ geometryVKMTransform.addressToCoords(trStop.roadAddress).headOption }

          stopPointOption match {
            case Some(stopPoint) =>
              val leftPoint = Point(stopPoint.x - boundsOffset, stopPoint.y -boundsOffset, 0)
              val rightPoint = Point(stopPoint.x + boundsOffset, stopPoint.y + boundsOffset, 0)
              val bounds = BoundingRectangle(leftPoint, rightPoint)
              val boundingBoxFilter = OracleDatabase.boundingBoxFilter(bounds, "a.geometry")
              val filter = s" where $boundingBoxFilter and a.asset_type_id = 10 and (a.valid_to is null or a.valid_to > sysdate)"
              val persistedStops = OracleDatabase.withDynSession {massTransitStopService.fetchPointAssets(query => query + filter)}.
                filter(stop => TierekisteriBusStopStrategyOperations.isStoredInTierekisteri(Some(stop))).
                filterNot(hasLiviIdPropertyValue)

              if(persistedStops.isEmpty){
                println("Couldn't find any stop nearest TR bus stop without livi Id. TR Livi Id "+trStop.liviId)
                None
              }else{
                val (peristedStop, distance) = persistedStops.map(stop => (stop, stopPoint.distance2DTo(Point(stop.lon, stop.lat, 0)))).minBy(_._2)
                println("Nearest TR bus stop Livi Id "+trStop.liviId+" asset id "+peristedStop.id+" national ID "+peristedStop.nationalId+" distance "+distance)
                Some(NearestBusStops(trStop, peristedStop, distance))
              }
            case _ =>
              println("Can't resolve the coordenates of the TR bus stop address with livi Id "+ trStop.liviId)
              None
          }
        }catch {
          case e: RoadAddressException =>
            println("RoadAddress throw exception for the TR bus stop address with livi Id "+ trStop.liviId +" "+ e.getMessage)
            None
        }
    }

    val nearestBusStops = busStops.groupBy(busStop => busStop.othBusStop.linkId).mapValues(busStop => busStop.minBy(_.distance)).values

    OracleDatabase.withDynTransaction{
      nearestBusStops.foreach{
        nearestBusStop =>
          println("Persist livi Id "+nearestBusStop.trBusStop.liviId+" at OTH bus stop id "+nearestBusStop.othBusStop.id+" with national id "+nearestBusStop.othBusStop.nationalId+" and distance "+nearestBusStop.distance)
          if(!dryRun)
            dataImporter.createOrUpdateTextPropertyValue(nearestBusStop.othBusStop.id, liviIdPropertyId, nearestBusStop.trBusStop.liviId, "g1_busstop_fix")
      }
    }


    println("\n")
    println("Complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  private def verifyIsChanged(propertyPublicId: String, propertyId: Long, municipality: Int): Unit = {
    val floatingReasonPublicId = "kellumisen_syy"
    val floatingReasonPropertyId = dataImporter.getPropertyTypeByPublicId(floatingReasonPublicId)

    val typeId = 10
    //Get all no floating mass transit stops by municipality id
    val assets = dataImporter.getNonFloatingAssetsWithNumberPropertyValue(typeId, propertyPublicId, municipality)

    println("Processing %d assets not floating".format(assets.length))

    if (assets.nonEmpty) {

      val roadLinks = vvhClient.roadLinkData.fetchByLinkIds(assets.map(_._2).toSet)

      assets.foreach {
        _ match {
          case (assetId, linkId, None) =>
            println("Asset with asset-id: %d doesn't have Administration Class value.".format(assetId))
          case (assetId, linkId, adminClass) =>
            val roadlink = roadLinks.find(_.linkId == linkId)
            MassTransitStopOperations.isFloating(AdministrativeClass.apply(adminClass.get), roadlink) match {
              case (_, Some(reason)) =>
                dataImporter.updateFloating(assetId, true)
                dataImporter.updateNumberPropertyData(floatingReasonPropertyId, assetId, reason.value)
              case (_, None) =>
                println("Don't exist modifications in Administration Class at the asset with id %d .".format(assetId))
            }
        }
      }
    }
  }

  def importVVHRoadLinksByMunicipalities(): Unit = {
    println("\nExpire all RoadLinks and then migrate the road Links from VVH to OTH")
    println(DateTime.now())
    val roadLinkService = new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer)
    val assetTypeId = 110

    lazy val linearAssetService: LinearAssetService = {
      new LinearAssetService(roadLinkService, new DummyEventBus)
    }

    linearAssetService.expireImportRoadLinksVVHtoOTH(assetTypeId)

    println("\n")
    println("Complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def checkBusStopMatchingBetweenOTHandTR(dryRun: Boolean = false): Unit = {
    def checkModifierSize(user: Modification) = {
      user.modifier.map(_.length).getOrElse(0) > 10
    }

    def fixModifier(user: Modification) = {
      Modification(user.modificationTime, Some("k127773"))
    }

    println("\nVerify if OTH mass transit stop exist in Tierekisteri, if not present, create them. ")
    println(DateTime.now())

    var persistedStop: Seq[PersistedMassTransitStop] = Seq()
    var missedBusStopsOTH: Seq[PersistedMassTransitStop] = Seq()

    //Get a List of All Bus Stops present in Tierekisteri
    val busStopsTR = tierekisteriClient.fetchActiveMassTransitStops

    //Save Tierekisteri LiviIDs into a List
    val liviIdsListTR = busStopsTR.map(_.liviId)

    //Get All Municipalities
    val municipalities: Seq[Int] =
      OracleDatabase.withDynSession {
        Queries.getMunicipalities
      }

    municipalities.foreach { municipality =>
      println("Start processing municipality %d".format(municipality))

      //Get all OTH Bus Stops By Municipality
      persistedStop = massTransitStopService.getByMunicipality(municipality, false)

      //Get all road links from VVH
      val roadLinks = vvhClient.roadLinkData.fetchByLinkIds(persistedStop.map(_.linkId).toSet)

      persistedStop.foreach { stop =>
        // Validate if OTH stop are known in Tierekisteri and if is maintained by ELY
        val stopLiviId = stop.propertyData.
          find(property => property.publicId == MassTransitStopOperations.LiViIdentifierPublicId).
          flatMap(property => property.values.headOption).map(p => p.asInstanceOf[PropertyValue].propertyValue)

        if (stopLiviId.isDefined && !liviIdsListTR.contains(stopLiviId.get)) {

          //Add a list of missing stops with road addresses is available
          missedBusStopsOTH = missedBusStopsOTH ++ List(stop)

          //If modified or created username is bigger than 10 of length we set with PO user
          val adjustedStop = stop match {
            case asset if checkModifierSize(asset.modified) && checkModifierSize(asset.created) =>
              asset.copy(created = fixModifier(asset.created), modified = fixModifier(asset.modified))
            case asset if checkModifierSize(asset.modified) =>
              asset.copy(modified = fixModifier(asset.modified))
            case asset if checkModifierSize(asset.created) =>
              asset.copy(created = fixModifier(asset.created))
            case _ =>
              stop
          }

          try {
            //Create missed Bus Stop at the Tierekisteri
            if(!dryRun) {
              withDynSession {
                //TODO get it from the new variation if we need to execute this batch process again.
                //massTransitStopService.executeTierekisteriOperation(Operation.Create, adjustedStop, roadLinkByLinkId => roadLinks.find(r => r.linkId == roadLinkByLinkId), None, None)
              }
            }
          } catch {
            case roadAddrError: RoadAddressException => println("Bus stop with national Id: "+adjustedStop.nationalId+" returns the following error: "+roadAddrError.getMessage)
            case tre: TierekisteriClientException => println("Bus stop with national Id: "+adjustedStop.nationalId+" returns the following error: "+tre.getMessage)
          }
        }
      }
      println("End processing municipality %d".format(municipality))
    }

    //Print the List of missing stops with road addresses is available
    println("List of missing stops with road addresses is available:")
    missedBusStopsOTH.foreach { busStops =>
      println("External Id: " + busStops.nationalId)
    }

    println("\n")
    println("Complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def listingBusStopsWithSideCodeConflictWithRoadLinkDirection(): Unit = {
    println("\nCreate a listing of bus stops on one-way roads in Production that have side code against traffic direction of road link")
    println(DateTime.now())

    var persistedStop: Seq[PersistedMassTransitStop] = Seq()
    var conflictedBusStopsOTH: Seq[PersistedMassTransitStop] = Seq()

    //Get All Municipalities
    val municipalities: Seq[Int] =
      OracleDatabase.withDynSession {
        Queries.getMunicipalities
      }

    println("Bus stops with side code in conflict By Municipality")

    municipalities.foreach { municipality =>
      println("Start processing municipality %d".format(municipality))

      //Get all OTH Bus Stops By Municipality
      persistedStop = massTransitStopService.getByMunicipality(municipality, false)

      persistedStop.foreach { stop =>
        val massTransitStopDirectionValue = stop.validityDirection

        val roadLinkOfMassTransitStop = roadLinkService.getRoadLinkByLinkIdFromVVH(stop.linkId)
        val roadLinkDirectionValue = roadLinkOfMassTransitStop.map(rl => rl.trafficDirection).headOption

        roadLinkDirectionValue match {
          case Some(trafficDirection) =>
            // Validate if OTH Bus stop are in conflict with road link traffic direction
            if ((roadLinkDirectionValue.head.toString != SideCode.BothDirections.toString) && (roadLinkDirectionValue.head.toString != SideCode.apply(massTransitStopDirectionValue.get.toInt).toString())) {
              //Add a list of conflicted Bus Stops
              conflictedBusStopsOTH = conflictedBusStopsOTH ++ List(stop)
            }
          case _ =>
            None
        }
      }

      println("End processing municipality %d".format(municipality))
    }

    //Print the List of Bus stops with side code in conflict
    println("List of Bus Stops with side code in conflict:")
    conflictedBusStopsOTH.foreach { busStops =>
      println("External Id: " + busStops.nationalId)
    }

    println("\n")
    println("Complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def fillLaneAmountsMissingInRoadLink(): Unit = {
    val dao = new OracleLinearAssetDao(null, null)
    val roadLinkService = new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer)

    lazy val linearAssetService: LinearAssetService = {
      new LinearAssetService(roadLinkService, new DummyEventBus)
    }

    println("\nFill Lane Amounts in missing road links")
    println(DateTime.now())
    val username = "batch_process_"+DateTimeFormat.forPattern("yyyyMMdd").print(DateTime.now())

    val LanesNumberAssetTypeId = 140
    val NumOfRoadLanesMotorway = 2
    val NumOfRoadLanesSingleCarriageway = 1

    //Get All Municipalities
    val municipalities: Seq[Int] =
      OracleDatabase.withDynSession {
        Queries.getMunicipalities
      }

    println("Obtaining all Road Links By Municipality")

    //For each municipality get all VVH Roadlinks for pick link id and pavement data
    municipalities.foreach { municipality =>

      var countMotorway = 0
      var countSingleway = 0
      println("Start processing municipality %d".format(municipality))

      //Obtain all RoadLink by municipality
      val roadLinks = roadLinkService.getRoadLinksFromVVHByMunicipality(municipality)

      println ("Total roadlink by municipality -> " + roadLinks.size)

      OracleDatabase.withDynTransaction{
        //Obtain all existing RoadLinkId by AssetType and roadLinks
        val assetCreated = dataImporter.getAllLinkIdByAsset(LanesNumberAssetTypeId, roadLinks.map(_.linkId))

        println ("Total created previously      -> " + assetCreated.size)

        //Filter roadLink by Class
        val roadLinksFilteredByClass = roadLinks.filter(p => (p.administrativeClass == State))
        println ("Total RoadLink by State Class -> " + roadLinksFilteredByClass.size)

        //Obtain asset with a road link type Motorway or Freeway
        val roadLinkMotorwayFreeway  = roadLinksFilteredByClass.filter(road => road.linkType == asset.Motorway  || road.linkType == asset.Freeway)

        val (assetToExpire, assetPrevCreated) = assetCreated.partition{
          case(linkId, value, assetId) =>
            value <= NumOfRoadLanesSingleCarriageway && roadLinkMotorwayFreeway.map(_.linkId).contains(linkId)
        }

        //Expire all asset with road link type Motorway or Freeway with amount of lane equal 1
        println("Assets to expire - " + assetToExpire.size)
        assetToExpire.foreach{case(linkId, value, assetId) => dao.updateExpiration(assetId, expired = true, username)}

        //Exclude previously roadlink created
        val filteredRoadLinksByNonCreated = roadLinksFilteredByClass.filterNot(f => assetPrevCreated.contains(f.linkId))
        println ("Max possibles to insert       -> " + filteredRoadLinksByNonCreated.size )

        if (filteredRoadLinksByNonCreated.nonEmpty) {
          //Create new Assets for the RoadLinks from VVH
          filteredRoadLinksByNonCreated.foreach { roadLinkProp =>

            val endMeasure = GeometryUtils.geometryLength(roadLinkProp.geometry)
            roadLinkProp.linkType match {
              case asset.SingleCarriageway =>
                roadLinkProp.trafficDirection match {
                  case asset.TrafficDirection.BothDirections =>
                    dataImporter.insertNewAsset(LanesNumberAssetTypeId, roadLinkProp.linkId, 0, endMeasure, asset.SideCode.BothDirections.value , NumOfRoadLanesSingleCarriageway, username)
                    countSingleway = countSingleway+ 1
                  case _ =>
                    None
                }
              case asset.Motorway | asset.Freeway =>
                roadLinkProp.trafficDirection match {
                  case asset.TrafficDirection.TowardsDigitizing | asset.TrafficDirection.AgainstDigitizing => {
                    dataImporter.insertNewAsset(LanesNumberAssetTypeId, roadLinkProp.linkId, 0, endMeasure, asset.SideCode.BothDirections.value, NumOfRoadLanesMotorway, username)
                    countMotorway = countMotorway + 1
                  }
                  case _ =>
                    None
                }
              case _ =>
                None
            }
          }
        }
      }
      println("Inserts SingleCarriage - " + countSingleway)
      println("Inserts Motorway...    - " + countMotorway)
      println("End processing municipality %d".format(municipality))
      println("")
    }

    println("\n")
    println("Complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def fillRoadWidthInRoadLink(): Unit = {
    println("\nFill Road Width in missing and incomplete road links")
    println(DateTime.now())

    val dao = new OracleLinearAssetDao(null, null)
    val roadLinkService = new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer)

    lazy val roadWidthService: RoadWidthService = {
      new RoadWidthService(roadLinkService, new DummyEventBus)
    }

    val roadWidthAssetTypeId: Int = 120
    val maxAllowedError = 0.01
    val minAllowedLength = 2.0
    val minOfLength: Double = 0

    //Get All Municipalities
    val municipalities: Seq[Int] =
      OracleDatabase.withDynSession {
        Queries.getMunicipalities
      }

    municipalities.foreach { municipality =>
      println("Working on... municipality -> " + municipality)
      val (roadLinks, changes) = roadLinkService.getRoadLinksAndChangesFromVVHByMunicipality(municipality)
      //filter roadLink by administrative class and roadLink with MTKClass valid
      val roadLinkAdminClass = roadLinks.filter(road => road.administrativeClass == Municipality || road.administrativeClass == Private)
      val roadWithMTKClass = roadLinkAdminClass.filter(road => MTKClassWidth.values.toSeq.contains(road.extractMTKClass(road.attributes)))
      println("Road links with MTKClass valid -> " + roadWithMTKClass.size)

      OracleDatabase.withDynTransaction {
        val existingAssets = dao.fetchLinearAssetsByLinkIds(roadWidthAssetTypeId, roadWithMTKClass.map(_.linkId), LinearAssetTypes.numericValuePropertyId).filterNot(_.expired)
        println("Existing assets -> " + existingAssets.size)

        val lastChanges = changes.filter(_.newId.isDefined).groupBy(_.newId.get).mapValues(c => c.maxBy(_.vvhTimeStamp))
        println("Change info -> " + lastChanges.size)

        //Map all existing assets by roadLink and changeInfo
        val changedAssets = lastChanges.flatMap{
          case (linkId, changeInfo) =>
            roadWithMTKClass.find(road => road.linkId == linkId ).map {
              roadLink =>
                (roadLink, changeInfo, existingAssets.filter(_.linkId == linkId))
            }
        }

        println("Changed assets -> " + changedAssets.size)

        val expiredAssetsIds = changedAssets.flatMap {
          case (_, changeInfo, assets) =>
            assets.filter(asset => asset.modifiedBy.getOrElse(asset.createdBy.getOrElse("")) == "dr1_conversion" ||
              (asset.vvhTimeStamp < changeInfo.vvhTimeStamp && (asset.modifiedBy.getOrElse(asset.createdBy.getOrElse("")) == "vvh_mtkclass_default" ||
                asset.modifiedBy.getOrElse("") == "vvh_generated" && asset.createdBy.getOrElse("") == "vvh_mtkclass_default"))
            ).map(_.id)
        }.toSet

        println("Expired assets -> " + expiredAssetsIds.size)

        val newAssets = changedAssets.flatMap {
          case (roadLink, changeInfo, allAssets) =>
            val assets = allAssets.filterNot(asset => expiredAssetsIds.contains(asset.id))
            val roadLinkLength = GeometryUtils.geometryLength(roadLink.geometry)
            val measures = (assets.map(_.startMeasure) ++ assets.map(_.endMeasure) ++  Seq(minOfLength)).distinct.sorted

            val pointsOfInterest = if(roadLinkLength - measures.last > maxAllowedError)
              measures ++ Seq(roadLinkLength)
            else
              measures

            //Not create asset with the length less MinAllowedLength
            val pieces = pointsOfInterest.zip(pointsOfInterest.tail).filterNot{piece => (piece._2 - piece._1) < minAllowedLength}
            pieces.flatMap { measures =>
              Some(PersistedLinearAsset(0L, roadLink.linkId, SideCode.BothDirections.value, Some(NumericValue(roadLink.extractMTKClass(roadLink.attributes).width)),
                measures._1, measures._2, Some("vvh_mtkclass_default"), None, None, None, false, roadWidthAssetTypeId, changeInfo.vvhTimeStamp, None, linkSource = roadLink.linkSource, Some("vvh_mtkclass_default"), None, None))
            }.filterNot(a =>
              assets.
                exists(asset => math.abs(a.startMeasure - asset.startMeasure) < maxAllowedError && math.abs(a.endMeasure - asset.endMeasure) < maxAllowedError)
            )
        }

        println("New assets assets -> " + newAssets.size)

        if (expiredAssetsIds.nonEmpty)
          println("\nExpiring ids " + expiredAssetsIds.mkString(", "))

        expiredAssetsIds.foreach(dao.updateExpiration(_, expired = true, "vvh_mtkclass_default"))

        newAssets.foreach { linearAsset =>
          val roadLink = roadLinks.find(_.linkId == linearAsset.linkId).getOrElse(throw new IllegalStateException("Road link no longer available"))

          val id = dao.createLinearAsset(linearAsset.typeId, linearAsset.linkId, linearAsset.expired, linearAsset.sideCode,
            Measures(linearAsset.startMeasure, linearAsset.endMeasure), linearAsset.createdBy.getOrElse("vvh_mtkclass_default"), linearAsset.vvhTimeStamp, Some(roadLink.linkSource.value), geometry = roadLink.geometry)
          linearAsset.value match {
            case Some(NumericValue(intValue)) =>
              dao.insertValue(id, LinearAssetTypes.numericValuePropertyId, intValue)
            case _ => None
          }
        }
      }
    }

    println("Complete at time: " + DateTime.now())
  }

  def updateAreasOnAsset(): Unit = {
    println("\nStart Update areas on Asset at time ")
    println(DateTime.now())
    val MaintenanceRoadTypeId = 290

    //Get All Municipalities
    val municipalities: Seq[Int] =
      OracleDatabase.withDynSession {
        Queries.getMunicipalities
      }

    println("Obtaining all Road Links By Municipality")

    //For each municipality get all VVH Roadlinks for pick link id and pavement data
    municipalities.foreach { municipality =>

      //Obtain all RoadLink by municipality
      val roadLinks = roadLinkService.getRoadLinksFromVVHByMunicipality(municipality)

      OracleDatabase.withDynTransaction {
        //Obtain all existing RoadLinkId by AssetType and roadLinks
        val assets = dataImporter.getAssetsByLinkIds(MaintenanceRoadTypeId, roadLinks.map(_.linkId), includeExpire = true)

        println("Municipality -> " + municipality  + " MaintenanceRoad Assets -> " + assets.size )

        assets.foreach { asset =>
          try {
            val area = maintenanceService.getAssetArea(roadLinks.find(_.linkId == asset._2), Measures(asset._3, asset._4), None)
            assets.foreach(asset => oracleLinearAssetDao.updateArea(asset._1, area))
          } catch {
            case ex: Exception => {
              println(s"""asset id ${asset._1} in link id ${asset._2} as failed with the following exception ${ex.getMessage}""")
            }
          }
        }
      }
    }

    println("\nEnd Update areas on Asset at time: ")
    println(DateTime.now())
    println("\n")
  }

  def updateOTHBusStopWithTRInfo(): Unit = {
    println("\nSynchronize name (Swedish), korotettu and katos (shelter) info of bus stops according to the info saved in TR")
    println(DateTime.now())

    val username = "batch_process_sync_BS_with_TR_info"

    var persistedStop: Seq[PersistedMassTransitStop] = Seq()
    var outdatedBusStopsOTH: Seq[PersistedMassTransitStop] = Seq()

    //Get All Municipalities
    val municipalities: Seq[Int] =
      OracleDatabase.withDynSession {
        Queries.getMunicipalities
      }

    //Get a List of All Bus Stops present in Tierekisteri
    val allBusStopsTR = tierekisteriClient.fetchActiveMassTransitStops

    //Save Tierekisteri LiviIDs into a List
    val liviIdsListTR = allBusStopsTR.map(_.liviId)

    municipalities.foreach { municipality =>
      println("Start processing municipality %d".format(municipality))

      //Get all OTH Bus Stops By Municipality
      persistedStop = massTransitStopService.getByMunicipality(municipality, false)

      persistedStop.foreach { stop =>
        val stopLiviId = stop.propertyData.
          find(property => property.publicId == MassTransitStopOperations.LiViIdentifierPublicId).
          flatMap(property => property.values.headOption).map(p => p.asInstanceOf[PropertyValue].propertyValue)

        // Validate if OTH stop are known in Tierekisteri and if is maintained by ELY
        if (stopLiviId.isDefined && liviIdsListTR.contains(stopLiviId.get)) {
          //Data From OTH
          val stopNameSE =
            stop.propertyData.find(property => property.publicId == MassTransitStopOperations.nameSePublicId).
              flatMap(property => property.values.headOption).map(p => p.asInstanceOf[PropertyValue].propertyValue)
            match {
              case Some(roofValue) => roofValue
              case _ => ""
            }

          val stopRoofValue =
            stop.propertyData.find(property => property.publicId == MassTransitStopOperations.roofPublicId).
              flatMap(property => property.values.headOption).map(p => p.asInstanceOf[PropertyValue].propertyValue)
            match {
              case Some(roofValue) => Existence.fromPropertyValue(roofValue)
              case _ => ""
            }

          val stopRaisedBusStopValue =
            stop.propertyData.find(property => property.publicId == MassTransitStopOperations.raisePublicId).
              flatMap(property => property.values.headOption).map(p => p.asInstanceOf[PropertyValue].propertyValue)
            match {
              case Some(raisedValue) => Existence.fromPropertyValue(raisedValue)
              case _ => ""
            }

          //Data From TR
          val busStopsTR = allBusStopsTR.find(_.liviId == stopLiviId.get)

          val nameSEinTR = busStopsTR.head.nameSe match {
            case Some(name) => name
            case _ => ""
          }
          val roofValueinTR = busStopsTR.head.equipments.get(Equipment.Roof) match {
            case Some(roofValue) => roofValue
            case _ => ""
          }
          val raisedValueinTR = busStopsTR.head.equipments.get(Equipment.RaisedBusStop) match {
            case Some(raisedValue) => raisedValue
            case _ => ""
          }

          if ((stopNameSE != nameSEinTR) || (stopRoofValue != roofValueinTR) || (stopRaisedBusStopValue != raisedValueinTR)) {
            val propertiesToUpdate = Seq(
              SimplePointAssetProperty(MassTransitStopOperations.nameSePublicId, Seq(PropertyValue(nameSEinTR))),
              SimplePointAssetProperty(MassTransitStopOperations.roofPublicId, Seq(PropertyValue(roofValueinTR.asInstanceOf[Existence].propertyValue.toString))),
              SimplePointAssetProperty(MassTransitStopOperations.raisePublicId, Seq(PropertyValue(raisedValueinTR.asInstanceOf[Existence].propertyValue.toString)))
            )

            massTransitStopService.updatePropertiesForAsset(stop.id, propertiesToUpdate)

            //Add a list of outdated Bus Stops
            outdatedBusStopsOTH = outdatedBusStopsOTH ++ List(stop)
          }
        }
      }

      println("End processing municipality %d".format(municipality))
    }

    //Print the List of Bus stops where info is not the same
    println("List of Bus stops where info is not the same:")
    outdatedBusStopsOTH.foreach { busStops =>
      println("External Id: " + busStops.nationalId)
    }

    println("\n")
    println("Complete at time: ")
    println(DateTime.now())
    println("\n")
  }


  private def isKIdentifier(username: Option[String]): Boolean = {
    val identifiers: Set[String] = Set("k", "lx", "a", "u")
    username.exists(user => identifiers.exists(identifier => user.toLowerCase.startsWith(identifier)))
  }

  def updateInformationSource(): Unit = {

    println("\nUpdate Information Source for RoadWidth")
    println(DateTime.now())

    val roadLinkService = new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer)

    //    Get All Municipalities
    val municipalities: Seq[Int] =
      OracleDatabase.withDynSession {
        Queries.getMunicipalities
      }

    municipalities.foreach { municipality =>
      println("\nWorking on... municipality -> " + municipality)
      println("Fetching roadlinks")
      val (roadLinks, changes) = roadLinkService.getRoadLinksAndChangesFromVVHByMunicipality(municipality)

      OracleDatabase.withDynTransaction {

        val roadWithMTKClass = roadLinks.filter(road => MTKClassWidth.values.toSeq.contains(road.extractMTKClass(road.attributes)))
        println("Fetching assets")
        val existingAssets = oracleLinearAssetDao.fetchLinearAssetsByLinkIds(RoadWidth.typeId, roadLinks.map(_.linkId), LinearAssetTypes.numericValuePropertyId).filterNot(_.expired)

        println(s"Number of existing assets: ${existingAssets.length}")
        println(s"Start updating assets with Information Source")

        existingAssets.foreach { asset =>
          if(asset.createdBy.contains("vvh_mtkclass_default") && (asset.modifiedBy.isEmpty || asset.modifiedBy.contains("vvh_generated"))){
            if(!asset.informationSource.contains(MunicipalityMaintenainer))
              oracleLinearAssetDao.updateInformationSource(RoadWidth.typeId, asset.id, MmlNls)
          }
          else{
            if(( (asset.createdBy.contains("dr1_conversion") || asset.createdBy.contains("vvh_generated"))&& asset.modifiedBy.isEmpty)  ||
              (asset.createdBy.contains("dr1_conversion") && asset.modifiedBy.contains("vvh_generated"))) {
              if(!asset.informationSource.contains(MunicipalityMaintenainer)) {
                if (roadWithMTKClass.exists(_.linkId == asset.linkId)) {
                  println(s"Asset with ${asset.id} created by dr1_conversion or vvh_generated and with valid MTKCLASS")
                  oracleLinearAssetDao.updateInformationSource(RoadWidth.typeId, asset.id, MmlNls)
                } else
                  oracleLinearAssetDao.updateInformationSource(RoadWidth.typeId, asset.id, MunicipalityMaintenainer)
              }
            }
            else {
              if (asset.createdBy.contains("batch_process_roadWidth") && (asset.modifiedBy.isEmpty || asset.modifiedBy.contains("vvh_generated"))) {
                if (!asset.informationSource.contains(MunicipalityMaintenainer))
                  oracleLinearAssetDao.updateInformationSource(RoadWidth.typeId, asset.id, RoadRegistry)
              }
              else{
                if( isKIdentifier(asset.createdBy) || isKIdentifier(asset.modifiedBy) )
                  oracleLinearAssetDao.updateInformationSource(RoadWidth.typeId, asset.id, MunicipalityMaintenainer)

                else
                  println(s"Asset with ${asset.id} not updated with Information Source")
              }
            }
          }
        }
      }
    }
    println("Complete at time: " + DateTime.now())
  }


  def updatePavedRoadInformationSource(): Unit = {

    println("\nUpdate Information Source for Pavement")
    println(DateTime.now())

    val roadLinkService = new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer)

    //Get All Municipalities
    val municipalities: Seq[Int] =
      OracleDatabase.withDynSession {
        Queries.getMunicipalities
      }

    municipalities.foreach { municipality =>
      println("\nWorking on... municipality -> " + municipality)
      println("Fetching roadlinks")
      val (roadLinks, _) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVHByMunicipality(municipality)

      OracleDatabase.withDynTransaction {

        println("Fetching assets")
        val existingAssets = dynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(PavedRoad.typeId, roadLinks.map(_.linkId)).filterNot(_.expired)

        println(s"Number of existing assets: ${existingAssets.length}")
        println(s"Start updating assets with Information Source")

        existingAssets.foreach { asset =>
          if (asset.createdBy.contains("batch_process_pavedRoad") && (asset.modifiedBy.isEmpty || asset.modifiedBy.contains("vvh_generated"))) {
            oracleLinearAssetDao.updateInformationSource(PavedRoad.typeId, asset.id, RoadRegistry)
          } else {
            if (isKIdentifier(asset.createdBy) || isKIdentifier(asset.modifiedBy)) {
              oracleLinearAssetDao.updateInformationSource(PavedRoad.typeId, asset.id, MunicipalityMaintenainer)
            } else {
              if (asset.createdBy.contains("vvh_generated") && (asset.modifiedBy.isEmpty || asset.modifiedBy.contains("vvh_generated"))) {
                oracleLinearAssetDao.updateInformationSource(PavedRoad.typeId, asset.id, MmlNls)
              } else
                println(s"Asset with ${asset.id} not updated with Information Source")
            }
          }
        }
      }
    }
    println("Complete at time: " + DateTime.now())
  }

  def updateTrafficDirectionRoundabouts(): Unit = {
    println("\nStart Update roundadbouts traffic direction ")
    println(DateTime.now())

    //Get All Municipalities
    val municipalities: Seq[Int] =
      OracleDatabase.withDynSession {
        Queries.getMunicipalities
      }

    municipalities.foreach { municipality =>
      println("")
      println(s"Obtaining all Road Links for Municipality: $municipality")
      val roadLinks = roadLinkService.getRoadLinksFromVVHByMunicipality(municipality)

      println(s"Grouping roundabouts")
      val roundabouts = RoundaboutProcessor.groupByRoundabout(roadLinks, withIncomplete = false)

      println("")

      roundabouts.foreach {
        roundabout =>
          val (roadLinkWithTrafficDirection, trafficChanges) = RoundaboutProcessor.setTrafficDirection(roundabout)
          trafficChanges.trafficDirectionChanges.foreach {
            trafficChange =>
              OracleDatabase.withDynTransaction {

                roadLinkWithTrafficDirection.find(_.linkId == trafficChange.linkId) match {
                  case Some(roadLink) =>
                    println("")
                    val actualTrafficDirection = RoadLinkDAO.get("traffic_direction", roadLink.linkId)
                    println(s"Before -> linkId: ${roadLink.linkId}, trafficDirection: ${TrafficDirection.apply(actualTrafficDirection)}")

                    println(s"roadLink Processed ->linkId: ${roadLink.linkId} trafficDirection ${roadLink.trafficDirection}, linkType: ${roadLink.linkType.value}")

                    val linkProperty = LinkProperties(roadLink.linkId, roadLink.functionalClass, roadLink.linkType, roadLink.trafficDirection, roadLink.administrativeClass)

                    actualTrafficDirection match {
                      case Some(traffic) => RoadLinkDAO.update("traffic_direction", linkProperty, Some("batch_roundabout"), actualTrafficDirection.getOrElse(TrafficDirection.UnknownDirection.value))
                      case _ => RoadLinkDAO.insert("traffic_direction", linkProperty, Some("batch_roundabout"))
                    }

                    val updateTrafficDirection = RoadLinkDAO.get("traffic_direction", roadLink.linkId)
                    println(s"After -> linkId: ${roadLink.linkId}, trafficDirection: ${TrafficDirection.apply(updateTrafficDirection)}")

                  case _ => println("No roadlinks to process")
                }
              }
          }
      }
    }
  }

  def createManoeuvresUsingTrafficSigns(): Unit = {
    //Get All Municipalities
    println(s"Obtaining Municipalities")
    val municipalities: Seq[Int] =
      OracleDatabase.withDynSession {
        Queries.getMunicipalities
      }

    municipalities.foreach { municipality =>

      println(s"Obtaining all traffic Signs with turning restriction for municipality $municipality")
      //Get All Traffic Signs with traffic restriction

      val trafficSigns = trafficSignService.getTrafficSigns(municipality, trafficSignService.getRestrictionsEnumeratedValues(TrafficSignManager.manoeuvreRelatedSigns))
      println(s"Obtaining all Road Links for Municipality: $municipality")
      val roadLinks = roadLinkService.getRoadLinksFromVVHByMunicipality(municipality)
      println(s"End of roadLinks fetch for Municipality: $municipality")

      println("Start processing traffic signs, to create manoeuvres")
      trafficSigns.foreach(ts =>
        try {
          roadLinks.find(_.linkId == ts.linkId) match {
            case Some(roadLink) =>
              val trafficType = trafficSignService.getProperty(ts, trafficSignService.typePublicId).get.propertyValue.toInt
              manoeuvreService.createBasedOnTrafficSign(TrafficSignInfo(ts.id, ts.linkId, ts.validityDirection, trafficType, roadLink))
              println(s"manoeuvre created for traffic sign with id: ${ts.id}")
            case _ =>
              println(s"No roadLink available to create manouvre")
              println(s"Asset id ${ts.id} did not generate a manoeuvre ")
          }
        }catch {
          case ex: ManoeuvreCreationException =>
            println(s"""creation of manoeuvre on link id ${ts.linkId} from traffic sign ${ts.id} failed with the following exception ${ex.getMessage}""")
          case ex: InvalidParameterException =>
            println(s"""creation of manoeuvre on link id ${ts.linkId} from traffic sign ${ts.id} failed with the Invalid Parameter exception ${ex.getMessage}""")
        }
      )
    }
  }

  def mergeAdditionalPanelsToTrafficSigns(group: TrafficSignTypeGroup): Unit = {
    val errorLogBuffer: ListBuffer[String] = ListBuffer()

    println("\nMerging additional panels to nearest traffic signs")
    println(DateTime.now())

    //Get all municipalities
    val municipalities: Seq[Int] =
      OracleDatabase.withDynSession{
        Queries.getMunicipalities
      }

    OracleDatabase.withDynTransaction {
      val additionalPanelIdToExpire : Seq[(Option[Long], Long, Int)] = municipalities.flatMap { municipality =>
        println("")
        println(DateTime.now())
        println(s"Fetching Traffic Signs for Municipality: $municipality")

        val roadLinks = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVHByMunicipality(municipality, newTransaction = false)._1
        val existingAssets = trafficSignService.getPersistedAssetsByLinkIdsWithoutTransaction(roadLinks.map(_.linkId).toSet).filterNot(_.floating)
        val (panels, signs) = existingAssets.partition(asset => TrafficSignType.applyOTHValue(trafficSignService.getProperty(asset, trafficSignService.typePublicId).get.propertyValue.toInt).group == TrafficSignTypeGroup.AdditionalPanels)
        val signsByType = signs.filter(sign => TrafficSignType.applyOTHValue(trafficSignService.getProperty(sign, trafficSignService.typePublicId).get.propertyValue.toInt).group == group)

        println("")
        println(s"Number of existing assets: ${signsByType.length}")
        println("")

        signsByType.flatMap { sign =>
          try {
            val roadLink = roadLinks.find(_.linkId == sign.linkId).get
            val signType = trafficSignService.getProperty(sign, trafficSignService.typePublicId).get.propertyValue.toInt
            val additionalPanels = panels.filter(panel => GeometryUtils.geometryLength(Seq(Point(sign.lon, sign.lat), Point(panel.lon, panel.lat))) <= 2).map { panel =>
              AdditionalPanelInfo(panel.mValue, panel.linkId, panel.propertyData.map(x => SimplePointAssetProperty(x.publicId, x.values)).toSet, panel.validityDirection, id = Some(panel.id))
            }.toSet

            val additionalPanelsInRadius = trafficSignService.getAdditionalPanels(sign.linkId, sign.mValue, sign.validityDirection, signType, roadLink.geometry, additionalPanels, roadLinks)
            val uniquePanels = trafficSignService.distinctPanels(additionalPanelsInRadius)
            try{
              if (uniquePanels.size <= 3 && additionalPanelsInRadius.nonEmpty) {
                val additionalPanels = trafficSignService.additionalPanelProperties(uniquePanels)
                val propertyData = sign.propertyData.filterNot(prop => prop.publicId == trafficSignService.additionalPublicId).map(x => SimplePointAssetProperty(x.publicId, x.values)) ++ additionalPanels
                val updatedTrafficSign = IncomingTrafficSign(sign.lon, sign.lat, sign.linkId, propertyData.toSet, sign.validityDirection, sign.bearing)

                trafficSignService.updateWithoutTransaction(sign.id, updatedTrafficSign, roadLink, "batch_process_panel_merge", Some(sign.mValue), Some(sign.vvhTimeStamp))
                additionalPanelsInRadius.map(asset => (asset.id, asset.linkId, trafficSignService.getProperty(asset.propertyData, trafficSignService.typePublicId).get.propertyValue.toInt)).toSeq
              } else {
                errorLogBuffer += s"Traffic Sign with ID: ${sign.id}, LinkID: ${sign.linkId}, failed to merge additional panels. Number of additional panels detected: ${additionalPanelsInRadius.size}"
                Seq()
              }
            } catch {
              case e: Exception => throw new UnsupportedOperationException(s"panels: ${additionalPanelsInRadius.mkString("/")} with exception: ${e.getMessage}")
              case _ : Throwable => throw new UnsupportedOperationException(s"panels: ${additionalPanelsInRadius.mkString("/")}")
            }
          } catch {
            case e: Exception => throw new UnsupportedOperationException(s"id: ${sign.id}, linkId: ${sign.linkId} additional info: ${e.getMessage}")
            case _ : Throwable => throw new UnsupportedOperationException(s"id: ${sign.id}, linkId: ${sign.linkId}")
          }
        }
      }
      additionalPanelIdToExpire.foreach { case (id, linkId, signType) =>
        //this code is commented until final OK is given by the client to delete additional signs. improvements to this batch were made in DROTH-1917
        //uncomment to perform one time batch in which additional panel properties are copied to main sign and then deleted.
//        trafficSignService.expireAssetWithoutTransaction(trafficSignService.withIds(Set(id).flatten), Some("batch_process_panel_merge"))
        println(s"Additional panel expired with id $id and type ${TrafficSignType.applyOTHValue(signType).toString} on linkId $linkId")
      }
    }
    println("")
    errorLogBuffer.foreach(println)
    println("Complete at time: " + DateTime.now())
  }

  def removeExistingTrafficSignsDuplicates(): Unit = {
    println("\nStarting removing of traffic signs duplicates")
    println(DateTime.now())

    //Get All Municipalities
    val municipalities: Seq[Int] =
      OracleDatabase.withDynSession {
        Queries.getMunicipalities
      }

    municipalities.foreach { municipality =>
      println("")
      println(s"Fetching Traffic Signs for Municipality: $municipality")

      val existingAssets = trafficSignService.getByMunicipality(municipality)
      println("")
      println(s"Number of existing assets: ${existingAssets.length}")
      println("")

      val groupedAssets = existingAssets.groupBy(_.linkId)

      existingAssets.foreach { sign =>
        println(s"Analyzing Traffic Sign with => Id: ${sign.id}, LinkID: ${sign.linkId}")
        val trafficSignsInRadius = trafficSignService.getTrafficSignsByDistance(sign, groupedAssets, 10)

        if (trafficSignsInRadius.size > 1) {
          OracleDatabase.withDynTransaction {
            val latestModifiedAsset = trafficSignService.getLatestModifiedAsset(trafficSignsInRadius)

            println("")
            println(s"Cleaning duplicates in 10 Meters")
            trafficSignsInRadius.filterNot(_.id == latestModifiedAsset.id).foreach {
              tsToDelete =>
                trafficSignService.expireWithoutTransaction(tsToDelete.id, "batch_deleteDuplicateTrafficSigns")
                println(s"TrafficSign with Id: ${tsToDelete.id} and LinkId: ${tsToDelete.linkId} expired!")
            }
            println("")
          }
        }
      }
      println("")
      println("Complete at time: " + DateTime.now())
    }
  }

  def addGeometryToLinearAssets(): Unit ={
    println("\nStart process to add geometry on linear assets")
    println(DateTime.now())

    val assetTypes = Set(DamagedByThaw.typeId, LitRoad.typeId, NumberOfLanes.typeId, TotalWeightLimit.typeId)
    //Get All Municipalities
    val municipalities: Seq[Int] =  OracleDatabase.withDynSession {
      Queries.getMunicipalities
    }

    municipalities.foreach {
      municipality =>

        println(s"Obtaining all Road Links for Municipality: $municipality")
        val roadLinks = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVHByMunicipality(municipality)._1
        println(s"End of roadLinks fetch for Municipality: $municipality")
        OracleDatabase.withDynTransaction {
          println("Fetching assets")
          val assets = assetDao.getAssetsByTypesAndLinkId(assetTypes, roadLinks.map(_.linkId))
          println(s"Number of fetched assets: ${assets.length}")
          roadLinks.foreach {
            roadLink =>
              println(s"Begining of process for linkId ${roadLink.linkId}")
              val assetsOnLink = assets.filter(_.linkId == roadLink.linkId)
              assetsOnLink.foreach {
                asset =>
                  println(s"Calculating geometry of asset with id ${asset.id}")
                  val geometry = GeometryUtils.truncateGeometry2D(roadLink.geometry, asset.startMeasure, asset.endMeasure)
                  println(s"Updating asset with id ${asset.id}")
                  if(geometry.nonEmpty)
                    assetDao.updateAssetsWithGeometry(asset, geometry.head, geometry.last)
              }
          }
        }
    }
    println("Complete at time: " + DateTime.now())
  }

  def updatePrivateRoads(): Unit = {
    println("\nStart of update private roads")
    println(DateTime.now())
    val assetTypes = Set(Prohibition.typeId, TotalWeightLimit.typeId, TrailerTruckWeightLimit.typeId, AxleWeightLimit.typeId, BogieWeightLimit.typeId)
    //Get All Municipalities
    val municipalities: Seq[Int] = OracleDatabase.withDynSession { Queries.getMunicipalities  }

    municipalities.foreach { municipality =>
      println(s"Obtaining all Road Links for Municipality: $municipality")
      val roadLinksWithAssets =  OracleDatabase.withDynTransaction {
        val roadLinks = roadLinkService.getRoadLinksFromVVHByMunicipality(municipality, newTransaction = false).filter(_.administrativeClass == Private)
        val linkIds = roadLinks.map(_.linkId)

        val existingAssets = oracleLinearAssetDao.fetchAssetsByLinkIds(assetTypes, linkIds)
        roadLinks.filter(roadLink => existingAssets.map(_.linkId).toSet.contains(roadLink.linkId))
      }
      roadLinksWithAssets.foreach { roadLink =>
        val linkProperty = LinkProperties(roadLink.linkId, roadLink.functionalClass, roadLink.linkType, roadLink.trafficDirection, roadLink.administrativeClass, Some(""), Some(AdditionalInformation.DeliveredWithRestrictions), Some(""))
        roadLinkService.updateLinkProperties(linkProperty, Option("update_private_roads_process"), (_, _) => {})
      }
    }
  }

  private def updateFloatingStopsOnTerminatedRoads(): Unit ={
    val dateFormatter = new SimpleDateFormat("yyyy-MM-dd")

    def convertDateToString(dateOpt: Option[Date]): String = {
      dateOpt match {
        case Some(date) => dateFormatter.format(date)
        case _ => LocalDate.now().toString
      }
    }

    println("Starting fetch of OTH stops")
    println(DateTime.now())

    val trStops = dataImporter.getTierekisteriStops()
    val floatingTerminated = trStops.filter(stop => stop._2 > 0 && stop._4 == FloatingReason.TerminatedRoad.value)

    println("Starting fetch of Tierekisteri stops")
    println(DateTime.now())
    val (terminated, active) = tierekisteriClient.fetchActiveMassTransitStops().partition(stop => convertDateToString(stop.removalDate).compareTo(LocalDate.now().toString) == -1)

    val stopsToUpdateFloatingReason = trStops.filter(stop => terminated.map(_.liviId).contains(stop._3))
    val stopsToRemoveFloatingReason = floatingTerminated.filter(stop => active.map(_.liviId).contains(stop._3))

    OracleDatabase.withDynTransaction {
      stopsToUpdateFloatingReason.foreach(stop => massTransitStopService.updateFloating(stop._1, floating = true, Some(FloatingReason.TerminatedRoad)))

      if(stopsToRemoveFloatingReason.nonEmpty)
        stopsToRemoveFloatingReason.foreach(stop => massTransitStopService.updateFloating(stop._1, floating = false, None))
    }

    println("\n")
    println("Complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def removeUnnecessaryUnknownSpeedLimits(): Unit = {
    println("\nStart delete/update unknown speedLimits")
    println(DateTime.now())

    //Get All Municipalities
    val municipalities: Seq[Int] =
      OracleDatabase.withDynSession {
        Queries.getMunicipalities
      }

    OracleDatabase.withDynTransaction {
      municipalities.foreach { municipality =>
        println(s"Obtaining all Road Links and unknown SpeedLimits for Municipality: $municipality")

        val unknownSpeedLimitByMunicipality = speedLimitDao.getMunicipalitiesWithUnknown(municipality)
        val allUnknownSpeedLimitLinkIds = unknownSpeedLimitByMunicipality.map(_._1)

        val roadLinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(allUnknownSpeedLimitLinkIds.toSet, false)
        val filterRoadLinks = roadLinks.filterNot(_.isSimpleCarTrafficRoad).map(_.linkId) ++ allUnknownSpeedLimitLinkIds.diff(roadLinks.map(_.linkId))

        if (filterRoadLinks.nonEmpty) {
          println(s"Deleting linkIds - $filterRoadLinks")
          speedLimitDao.deleteUnknownSpeedLimits(filterRoadLinks)
        }

        // Validate and update AdminClass/MunicipalityCode at unknown SpeedLimit table
        unknownSpeedLimitByMunicipality.foreach { case (unknownLinkId, unknownAdminClass) =>
          roadLinks.find(_.linkId == unknownLinkId) match {
            case Some(r) if r.administrativeClass != AdministrativeClass.apply(unknownAdminClass) | r.municipalityCode != municipality =>
              println("Updated link " + unknownLinkId + " admin class to " + r.administrativeClass.value + " and municipality code to " + r.municipalityCode)
              speedLimitDao.updateUnknownSpeedLimitAdminClassAndMunicipality(unknownLinkId, r.administrativeClass, r.municipalityCode)
            case _ => None
          }
        }
      }
    }

    println("\n")
    println("Complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def printSpeedLimitsIncorrectlyCreatedOnUnknownSpeedLimitLinks(): Unit = {
    println("\nStart checking unknown speedLimits on top on wrong links")
    println(DateTime.now())

    //Get All Municipalities
    val municipalities: Seq[Int] = OracleDatabase.withDynSession { Queries.getMunicipalities  }
    println(s"Municipality_code; AssetId; StartMeasure; EndMeasure; SideCode; Value; linkId, LinkType; AdministrativeClass")
    OracleDatabase.withDynTransaction {
      municipalities.foreach { municipality =>

        val roads = roadLinkService.getRoadLinksFromVVHByMunicipality(municipality, false)
        val speedLimits = speedLimitDao.fetchSpeedLimitsByLinkIds(roads.map(_.linkId))

        val roadLinks = roads.filter(road => speedLimits.exists(speed => speed.linkId == road.linkId))
        val filterRoadLinks = roadLinks.filterNot(_.isSimpleCarTrafficRoad)

        if (filterRoadLinks.nonEmpty) {
          filterRoadLinks.foreach { roadLink =>
            speedLimits.filter(_.linkId == roadLink.linkId).foreach { speedLimit =>
              println(s" $municipality ; ${speedLimit.id} ; ${speedLimit.startMeasure}; ${speedLimit.endMeasure}; ${speedLimit.sideCode.toString}; ${speedLimit.value}; ${roadLink.linkId}, ${roadLink.linkType.toString}; ${roadLink.administrativeClass.toString}")
            }
          }
        }
      }
    }

    println("\n")
    println("Complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def importPrivateRoadInformation() : Unit = {
    println("\nStart process to import road information")
    println(DateTime.now())

    val username = "external_private_road_info"

    def insert(linkProperties: LinkProperties, name: String, value: String, mmlId: Option[Long]): Unit = {
      try {
        LinkAttributesDao.insertAttributeValue(linkProperties, username, name, value, mmlId)
      } catch {
        case ex: SQLIntegrityConstraintViolationException =>
          println(s" Already exist attribute for linkId: ${linkProperties.linkId} with attribute name $name")
        case e: Exception => throw new RuntimeException("SQL exception " + e.getMessage)
      }
    }

    //Get All Municipalities
    val municipalities: Seq[Int] = OracleDatabase.withDynSession {
      Queries.getMunicipalities
    }
    OracleDatabase.withDynTransaction {
      municipalities.foreach { municipality =>
        println(s"Working on municipality : $municipality")

        val privateRoadInfo = ImportShapeFileDAO.getPrivateRoadExternalInfo(municipality).groupBy(_._1)

        if (privateRoadInfo.nonEmpty) {
          println(s"Number of records to update ${privateRoadInfo.keySet.size}")

          val roadLinksVVH = roadLinkService.fetchVVHRoadlinksAndComplementary(privateRoadInfo.keySet)
          val roadLinks = roadLinkService.enrichRoadLinksFromVVH(roadLinksVVH)

          val missingRoadLinks = privateRoadInfo.keySet.diff(roadLinks.map(_.linkId).toSet)
          if (missingRoadLinks.nonEmpty)
            println(s"LinkId not found ${missingRoadLinks.mkString(",")}")

          val (privateRoad, otherRoad) = roadLinks.partition(_.administrativeClass == Private)

          otherRoad.foreach { road =>
            println(s"Change Administrative Class for link ${road.linkId}")
            val linkProperties = LinkProperties(road.linkId, road.functionalClass, road.linkType, road.trafficDirection, road.administrativeClass)
            if (road.administrativeClass != Unknown)
              AdministrativeClassDao.updateValues(linkProperties, roadLinksVVH.find(_.linkId == road.linkId).get, Some(username), Private.value, privateRoadInfo(road.linkId).map(_._2).headOption)
            else
              AdministrativeClassDao.insertValues(linkProperties, roadLinksVVH.find(_.linkId == road.linkId).get, Some(username), Private.value, privateRoadInfo(road.linkId).map(_._2).headOption)
          }

          (privateRoad ++ otherRoad).foreach { road =>
            val linkProperties = LinkProperties(road.linkId, road.functionalClass, road.linkType, road.trafficDirection, road.administrativeClass)
            privateRoadInfo(road.linkId).foreach { case (_, mmlId, _, accessRight, name) =>
              if (accessRight.nonEmpty)
                insert(linkProperties, roadLinkService.accessRightIDPublicId, accessRight.get, Some(mmlId))

              if (name.nonEmpty)
                insert(linkProperties, roadLinkService.privateRoadAssociationPublicId, name.get, Some(mmlId))
            }
          }
        }
      }
    }
    println("Complete at time: " + DateTime.now())
  }

  def getStateRoadWithFunctionalClassOverridden(): Unit = {
    println("\nStart process to get StateRoads With Functional Class Overridden")
    println(DateTime.now())
    println("")

    val functionalClassValue = 5
    val sinceDate = Some("20190101") //Format required YYYYMMDD

    OracleDatabase.withDynTransaction {
      val linkIdsOverridden = FunctionalClassDao.getLinkIdByValue(functionalClassValue, sinceDate).toSet
      val roadLinks = roadLinkService.getRoadsLinksFromVVH(linkIdsOverridden, false).filter(_.administrativeClass == State)

      roadLinks.foreach { roadLink =>
        println(roadLink.linkId + ", " + roadLink.administrativeClass + ", " + roadLink.functionalClass + ", " + roadLink.linkType)
      }
    }

    println("")
    println("Complete at time: " + DateTime.now())
  }

  def getStateRoadWithFunctionalClassUndefined(): Unit = {
    println("\nStart process to get StateRoads With Functional Class Undefined")
    println(DateTime.now())
    println("")

    val functionalClassValue = 99

    //Get All Municipalities
    val municipalities: Seq[Int] = OracleDatabase.withDynSession {
      Queries.getMunicipalities
    }
    OracleDatabase.withDynTransaction {
      municipalities.foreach { municipality =>
        val roadLinks = roadLinkService.getRoadLinksFromVVHByMunicipality(municipality, false).filter(rl => rl.administrativeClass == State && rl.functionalClass == functionalClassValue)

        roadLinks.foreach { roadLink =>
          println(roadLink.linkId + ", " + roadLink.administrativeClass + ", " + roadLink.functionalClass + ", " + roadLink.linkType + ", " + municipality)
        }
      }

      println("")
      println("Complete at time: " + DateTime.now())
    }
  }

  def addObstaclesShapefile(): Unit = {
    println("\nStart process to add new obstacles by using the table created by the shapefile import")
    println(DateTime.now())
    println("")
    val userProvider: UserProvider = new OracleUserProvider
    val user = userProvider.getUser("k903846").get
    val minimumDistanceFromRoadLink: Double = 3.0
    val username = "batch_to_add_obstacles"

    println("\nGetting all obstacles information from the table created by the shapefile import")
    val obstaclesInformation: Seq[ObstacleShapefile] = OracleDatabase.withDynSession {
      ImportShapeFileDAO.getObstaclesFromShapefileTable
    }

    OracleDatabase.withDynTransaction {
      obstaclesInformation.foreach { obstacle =>
        println("")
        println("Creating a obstacle with coordinates -> " + "x:" + obstacle.lon + " y:" + obstacle.lat)
        val pointObstacle = Point(obstacle.lon, obstacle.lat)

        roadLinkService.getClosestRoadlinkFromVVH(user, pointObstacle, 10) match {
          case Some(link) =>
            val nearestRoadLinks = roadLinkService.enrichRoadLinksFromVVH(Seq(link))

            if(nearestRoadLinks.nonEmpty){
              val nearestRoadLink = nearestRoadLinks.head
              println("Nearest roadLink -> " + nearestRoadLink.linkId)

              val floating = GeometryUtils.minimumDistance(pointObstacle, nearestRoadLink.geometry) >= minimumDistanceFromRoadLink
              val newObstacle = IncomingObstacle(pointObstacle.x, pointObstacle.y, nearestRoadLink.linkId,
                Set(SimplePointAssetProperty(obstacleService.typePublicId, Seq(PropertyValue(obstacle.obstacleType.toString)))))

              val id = obstacleService.createFromCoordinates(newObstacle, nearestRoadLink, username, floating)
              println("Obstacle created with id " + id)
            }else{
              println("No roadlink found when enrich the road link -> " + link.linkId)
              println("Obstacle not created")
            }

          case _ =>
            println("Closest roadlink not found")
            println("Obstacle not created")
        }
      }
    }
    println("")
    println("Complete at time: " + DateTime.now())
  }

  def importCyclingAndWalkingInfo(): Unit = {
    println("\nStart process to insert all cycling and walking info")
    println(DateTime.now())

    val username = "batch_to_import_cycling_walking"
    val assetType = asset.CyclingAndWalking.typeId

    val municipalities: Seq[Int] = OracleDatabase.withDynSession {
      Queries.getMunicipalities
    }

    OracleDatabase.withDynTransaction {
      municipalities.foreach { municipality =>
        println(s"Working on municipality : $municipality")
        val cyclingAndWalkingInfo = ImportShapeFileDAO.getCyclingAndWalkingInfo(municipality)

        println(s"Number of records to update ${cyclingAndWalkingInfo.size}")
        if (cyclingAndWalkingInfo.nonEmpty) {
          val roadLinks = roadLinkService.getRoadLinksFromVVHByMunicipality(municipality, false)

          cyclingAndWalkingInfo.foreach { asset =>
            val roadlink = roadLinks.find(_.linkId == asset.linkId)
            val value = DynamicValue(DynamicAssetValue(Seq(DynamicProperty("cyclingAndWalking_type", "single_choice", true, Seq(DynamicPropertyValue(asset.value))))))

            roadlink match {
              case Some(link) =>
                val id = dynamicLinearAssetService.createWithoutTransaction(typeId = assetType,
                  linkId = asset.linkId,
                  value = value,
                  sideCode = SideCode.BothDirections.value,
                  measures = Measures(0, GeometryUtils.geometryLength(link.geometry)),
                  username = username,
                  roadLink = roadlink)
                println(s"Asset created with id $id in the roadlink ${asset.linkId}")
              case _ => println(s"Error: Can't create asset in the roadlink ${asset.linkId}")
            }
          }
        }
        println()
      }
    }
    println("Complete at time: " + DateTime.now())
  }

  def normalizeUserRoles(): Unit = {
    def printUser(user: User): Unit = {
      val configuration = user.configuration
      println(s" id -> ${user.id}; username -> ${user.username}; " +
        s"configuration {  ${configuration.zoom.map(zoom => s"zoom = $zoom")} ${configuration.east.map(east => s"east = $east")} north = ${configuration.north.map(north => s"north = $north ")} " +
        s"municipalityNumber = ${configuration.municipalityNumber.mkString(",")} authorizedMunicipalities = ${configuration.authorizedMunicipalities.mkString(",")} authorizedAreas = ${configuration.authorizedAreas.mkString(",")} " +
        s"roles = ${configuration.roles.mkString(",")} lastNotificationDate = ${configuration.lastNotificationDate}  lastLoginDate = ${configuration.lastLoginDate}}")
    }
    println("\nStart process to remove additional roles from operators users")
    println(DateTime.now())

    val userProvider: UserProvider = new OracleUserProvider
    println("\nGetting operators with additional roles")

    val users: Seq[User] = OracleDatabase.withDynSession {
      userProvider.getUsers()
    }

    users.foreach { user =>
      if (user.isOperator() || user.configuration.roles("premium")) {
        println("update -> user to operator and clean authorizedMunicipalities and authorizedAreas")
        printUser(user)
        userProvider.updateUserConfiguration(user.copy(configuration = user.configuration.copy(roles = Set("operator"), authorizedMunicipalities = Set(), authorizedAreas = Set())))
      }
      else if (user.configuration.roles.size == 1 && (user.configuration.roles("busStopMaintainer") || user.isServiceRoadMaintainer()) || user.configuration.roles.size == 2) {
        if (user.configuration.roles.size == 2 && user.configuration.roles("busStopMaintainer") && user.isServiceRoadMaintainer())
          println(s"Wrong users combination -> ${user.configuration}")

        //Check busStopMaintainer and convert to ElyMaintainer
        if (user.configuration.roles("busStopMaintainer")) {
          val municipalities: Set[Int] = user.configuration.authorizedMunicipalities

          if (user.configuration.authorizedMunicipalities.nonEmpty) {
            val municipalityInfo = municipalityService.getMunicipalitiesNameAndIdByCode(municipalities)
            val elyMunicipalities: Set[Int] = municipalityService.getMunicipalitiesNameAndIdByEly(municipalityInfo.map(_.ely).toSet).map(_.id).toSet

            val diffMunicipalities = elyMunicipalities.diff(municipalities) ++ municipalities.diff(elyMunicipalities)
            if(diffMunicipalities.nonEmpty)
              println("inaccurate authorizedMunicipalities for elys ")
              if (elyMunicipalities.diff(municipalities).nonEmpty) print(s"missing user municipalities -> ${elyMunicipalities.diff(municipalities)}" )
              if (municipalities.diff(elyMunicipalities).nonEmpty) {
                print(s"exceeded user municipalities -> ${municipalities.diff(elyMunicipalities)}" )
                userProvider.updateUserConfiguration(user.copy(configuration = user.configuration.copy(authorizedMunicipalities = elyMunicipalities)))
              }

            //Normally the user shouldn't have more than 4 ely
            if (municipalityInfo.map(_.ely).toSet.size > 4)
              println("inaccurate authorizedMunicipalities for elys")

            println("update -> user to elyMaintainer")
            printUser(user)
            userProvider.updateUserConfiguration(user.copy(configuration = user.configuration.copy(roles = Set("elyMaintainer"))))
          }
        }
        //Check serviceRoadMaintainer
        if (user.isServiceRoadMaintainer() && user.configuration.authorizedAreas.isEmpty) {
          println(s"wrong configuration for serviceRoadMaintainer -> ${user.id}")
        }
      }

      if (user.configuration.roles.isEmpty && user.configuration.authorizedMunicipalities.isEmpty)
        println(s"wrong configuration  ${user.id}")
    }
    println("Completed at time: " + DateTime.now())
  }

  def loadMunicipalitiesVerificationInfo(): Unit = {
    println("\nRefreshing information on municipality verification")
    println(DateTime.now())

    //Get All Municipalities
    val municipalities: Seq[Int] = OracleDatabase.withDynSession { Queries.getMunicipalities  }
    OracleDatabase.withDynTransaction {
      municipalities.foreach { municipality =>
        println(s"Working on municipality : $municipality")
        val roadLinks = roadLinkService.getRoadLinksFromVVHByMunicipality(municipality, false)
        verificationService.refreshVerificationInfo(municipality, roadLinks.map(_.linkId), Some(DateTime.now()))
      }
    }

    println("\n")
    println("Complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def transformLorryParkingIntoDatex2(): Unit = {
    //This Batch will use the table PARKS_TO_DATEX previus populated by a shape file gived to the transformation (Example: DROTH-1998)
    //That table was generated when the conversion of shapefile to ours database
    println("\nStart process transform lorry parkings into Datex2 format")
    println(DateTime.now())
    println()

    val datex2Generator = new Datex2Generator()
    OracleDatabase.withDynTransaction {
      val lorryParkingInfo = Queries.getLorryParkingToTransform()
      datex2Generator.convertToDatex2(lorryParkingInfo)
    }


    println()
    println()
    println("Complete at time: " + DateTime.now())
  }

  def removeRoadWorksCreatedLastYear(): Unit = {
    println("\nStart process to remove all road works assets created during the last year")
    println(DateTime.now())

    val actualYear = DateTime.now().getYear
    val username = "batch_to_expire_roadworks_on_previous_year"

    //Get All Municipalities
    val municipalities: Seq[Int] =
      OracleDatabase.withDynSession {
        Queries.getMunicipalities
      }

    municipalities.foreach { municipality =>
      println("\nWorking on... municipality -> " + municipality)
      println("Fetching roadlinks")
      val (roadLinks, _) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVHByMunicipality(municipality)

      OracleDatabase.withDynTransaction {
        println("Fetching assets")
        val existingAssets =
          roadWorkService.enrichPersistedLinearAssetProperties(dynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(RoadWorksAsset.typeId, roadLinks.map(_.linkId))).filterNot(_.expired)

        val existingAssetsOnLastYear = existingAssets.filter { asset =>
          asset.value.map(_.asInstanceOf[DynamicValue]) match {
            case Some(value) =>
              val roadWorkProps = value.value.properties
              roadWorkProps.find(_.publicId == "arvioitu_kesto") match {
                case Some(dateProperty) =>
                  dateProperty.values.map(x => DatePeriodValue.fromMap(x.value.asInstanceOf[Map[String, String]])).exists { property =>
                    val endDateYear = DateParser.stringToDate(property.endDate, DateParser.DatePropertyFormat).getYear
                    endDateYear < actualYear
                  }
                case _ => false
              }
            case _ => false
          }
        }

        println(s"Number of existing assets: ${existingAssetsOnLastYear.length}")
        println(s"Start expiring valid roadWorks assets")


        existingAssetsOnLastYear.foreach { asset =>
          roadWorkService.expireAsset(RoadWorksAsset.typeId, asset.id, username, true, false)
          println(s"Asset id ${asset.id} expired. ")
        }
      }
      println("Complete at time: " + DateTime.now())
    }
  }

  def extractTrafficSigns(group: Option[String]): Unit = {
    val signGroup = group match {
      case Some(x) => trafficSignGroup(x)
      case _ => throw new UnsupportedOperationException("Please provide a traffic sign group")
    }

    println(s"Starting extract of $group at ${DateTime.now()}")
    println("")
    println("")
    println("linkId;koordinaatti_x;koordinaatti_y;type;value;additionalInformation;linkSource;muokattu_viimeksi;id;trafficDirection;m_value")
    val municipalities: Seq[Int] =
      OracleDatabase.withDynSession{
        Queries.getMunicipalities
      }
    withDynTransaction{
      municipalities.foreach{ municipality =>
        val roadLinks = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVHByMunicipality(municipality, newTransaction = false)._1
        val existingAssets = trafficSignService.getPersistedAssetsByLinkIdsWithoutTransaction(roadLinks.map(_.linkId).toSet)
          .filterNot(_.floating)
          .filter(sign => TrafficSignType.applyOTHValue(trafficSignService.getProperty(sign, trafficSignService.typePublicId).get.propertyValue.toInt).group == signGroup)
        existingAssets.foreach{sign =>
          val signType = TrafficSignType.applyOTHValue(trafficSignService.getProperty(sign, trafficSignService.typePublicId).get.propertyValue.toInt).TRvalue
          val signValue = trafficSignService.getProperty(sign, trafficSignService.valuePublicId).flatMap(_.propertyDisplayValue).getOrElse("")
          val signInfo = trafficSignService.getProperty(sign, trafficSignService.infoPublicId).flatMap(_.propertyDisplayValue).getOrElse("")
          val lastModified = sign.modifiedBy.getOrElse("")
          println(s"${sign.linkId};${sign.lon};${sign.lat};$signType;$signValue;$signInfo;${sign.linkSource};$lastModified;${sign.id};${SideCode.toTrafficDirection(SideCode(sign.validityDirection))};${sign.mValue}")
        }
      }
    }
  }



  def mergeMunicipalities(): Unit = {
    val municipalityToDelete = 911
    val municipalityToMerge = 541

    println(s"\nStart process of merging municipality $municipalityToDelete into $municipalityToMerge")
    println(DateTime.now())
    println("")

    OracleDatabase.withDynTransaction{
      Queries.mergeMunicipalities(municipalityToDelete, municipalityToMerge)
    }

    println("")
    println("Complete at time: " + DateTime.now())
  }

  def fillNewRoadLinksWithPreviousInfo(): Unit = {
    def getAdjacentsRoadLinks(allAdjacentsRoadLinks: Seq[RoadLink], point: Point): Seq[RoadLink] = {
      allAdjacentsRoadLinks.filter(r => GeometryUtils.areAdjacent(r.geometry, point))
    }

    def createNewSpeedLimits(newSpeedLimits: Seq[SpeedLimit], roadlink: RoadLink): Unit = {
      //Create new SpeedLimits on gaps
      newSpeedLimits.foreach { speedLimit =>
        speedLimitDao.createSpeedLimit(LinearAssetTypes.VvhGenerated, speedLimit.linkId, Measures(speedLimit.startMeasure, speedLimit.endMeasure), speedLimit.sideCode, speedLimit.value.get, Some(vvhClient.roadLinkData.createVVHTimeStamp()), linkSource = roadlink.linkSource)
        println("New SpeedLimit created at Link Id: " + speedLimit.linkId + " with value: " + speedLimit.value.get.value + " and sidecode: " + speedLimit.sideCode)

        //Remove linkIds from Unknown Speed Limits working list after speedLimit creation
        speedLimitDao.purgeFromUnknownSpeedLimits(speedLimit.linkId, GeometryUtils.geometryLength(roadlink.geometry))
        println("\nRemoved linkId " + speedLimit.linkId + " from UnknownSpeedLimits working list")
        println("")
      }
    }

    println("\nStart process to fill new road links with previous data, only for change type 12")
    println(DateTime.now())

    //Get All Municipalities
    val municipalities: Seq[Int] = OracleDatabase.withDynSession {
      Queries.getMunicipalities
    }


    municipalities.foreach { municipality =>
      OracleDatabase.withDynTransaction {
        println("\nWorking at Municipailty: " + municipality)
        val (roadLinks, changes) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVHByMunicipality(municipality, newTransaction = false)
        val filteredRoadLinks = roadLinks.filter(r => r.isCarRoadOrCyclePedestrianPath)
        val changesToTreat = changes.filter(c => c.changeType == New.value && c.newId.nonEmpty && filteredRoadLinks.exists(_.linkId == c.newId.get))
        val roadLinksToTreat = filteredRoadLinks.filter(r => changesToTreat.exists(_.newId.get == r.linkId))
        val speedLimitsAlreadyExistents = speedLimitDao.getCurrentSpeedLimitsByLinkIds(Some(roadLinksToTreat.map(_.linkId).toSet))

        val changesWithoutSpeedLimitCreated = changesToTreat.filterNot(ctt => speedLimitsAlreadyExistents.exists(_.linkId == ctt.newId.get))
        println("Number of Changes to Treat: " + changesWithoutSpeedLimitCreated.size + "(RoadLinks without asset values in the adjacents are not treated)")

        changesWithoutSpeedLimitCreated.foreach { cws =>
          val adjacents = roadLinkService.getAdjacent(cws.newId.get, false)
          val speedLimitsOnAdjacents = speedLimitDao.getCurrentSpeedLimitsByLinkIds(Some(adjacents.map(_.linkId).toSet))

          roadLinksToTreat.find(_.linkId == cws.newId.get).foreach { changeRoadLink =>
            val assetAndPoints: Seq[(Point, SpeedLimit)] = speedLimitService.getAssetsAndPoints(speedLimitsOnAdjacents, roadLinks, (cws, changeRoadLink))

            if (assetAndPoints.nonEmpty) {
              println("\nTreating changes for the LinkId: " + cws.newId.get)
              val (firstRoadLinkPoint, lastRoadLinkPoint) = GeometryUtils.geometryEndpoints(changeRoadLink.geometry)

              val assetAdjFirst = speedLimitService.getAdjacentAssetByPoint(assetAndPoints, firstRoadLinkPoint)
              val assetAdjLast = speedLimitService.getAdjacentAssetByPoint(assetAndPoints, lastRoadLinkPoint)

              val groupBySideCodeFirst = assetAdjFirst.groupBy(_.sideCode)
              val groupBySideCodeLast = assetAdjLast.groupBy(_.sideCode)

              val adjacentsToFirstPoint = getAdjacentsRoadLinks(adjacents, firstRoadLinkPoint)
              val adjacentsToLastPoint = getAdjacentsRoadLinks(adjacents, lastRoadLinkPoint)

              val speedLimitsToCreate =
                if (assetAdjFirst.nonEmpty && assetAdjLast.nonEmpty) {
                  groupBySideCodeFirst.keys.flatMap { sideCode =>
                    groupBySideCodeFirst(sideCode).find { asset =>
                      val lastAdjsWithFirstSideCode = groupBySideCodeLast.get(sideCode)
                      lastAdjsWithFirstSideCode.isDefined && lastAdjsWithFirstSideCode.get.exists(_.value.equals(asset.value))
                    }.map { asset =>
                      asset.copy(id = 0, linkId = changeRoadLink.linkId, startMeasure = 0L.toDouble, endMeasure = GeometryUtils.geometryLength(changeRoadLink.geometry))
                    }
                  }.toSeq

                } else if (assetAdjFirst.isEmpty && adjacentsToFirstPoint.isEmpty && assetAdjLast.nonEmpty) {
                  groupBySideCodeLast.keys.map { sideCode =>
                    groupBySideCodeLast(sideCode).head.copy(id = 0, linkId = changeRoadLink.linkId, startMeasure = 0L.toDouble, endMeasure = GeometryUtils.geometryLength(changeRoadLink.geometry))
                  }.toSeq

                } else if (assetAdjFirst.nonEmpty && adjacentsToLastPoint.isEmpty && assetAdjLast.isEmpty) {
                  groupBySideCodeFirst.keys.map { sideCode =>
                    groupBySideCodeFirst(sideCode).head.copy(id = 0, linkId = changeRoadLink.linkId, startMeasure = 0L.toDouble, endMeasure = GeometryUtils.geometryLength(changeRoadLink.geometry))
                  }.toSeq

                } else {
                  Seq()
                }

              createNewSpeedLimits(speedLimitsToCreate, changeRoadLink)
            }
          }
        }
      }
    }

    println("\n")
    println("Complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def updateLastModifiedAssets(): Unit = {
    println("\nUpdating last modified assets information")
    println(DateTime.now())

    val municipalities: Seq[Int] = OracleDatabase.withDynSession { Queries.getMunicipalities }

    println("\n")
    println("Municipalities fetched after: " + DateTime.now())
    println("\n")


    municipalities.foreach { municipality =>
      OracleDatabase.withDynTransaction {
        println("Working on municipality " + municipality)
        val municipalityRoadLinks = roadLinkService.getRoadLinksFromVVHByMunicipality(municipality, false).toSet
        val modifiedAssetTypes = verificationService.dao.getModifiedAssetTypes(municipalityRoadLinks.map(_.linkId))

        modifiedAssetTypes.foreach { asset =>
          verificationService.dao.insertAssetModified(municipality, asset)
        }
        println("Modified assets transferred for municipality " + municipality + " in " + DateTime.now())
        println("\n")
      }
    }
  }

  //TODO: There will be a CSV generator in the future, when that is done then we can apply that to this function
  def extractCsvPrivateRoadAssociationInfo(): Unit = {
    def geometryToWKT(geometry: Seq[Point]): String = {
      val wktString = geometry.map { case p =>
        p.x + " " + p.y + " " + p.z
      }.mkString(", ")
      s"LINESTRING Z($wktString)"
    }

    println("\nStart extracting private road association information to a csv file")
    println(DateTime.now())

    //Get All Municipalities
    val municipalities: Seq[Int] = OracleDatabase.withDynSession { Queries.getMunicipalities }

    val newCsvFile = new File("private_road_association_information.csv")
    val bw = new BufferedWriter(new FileWriter(newCsvFile))
    bw.write("WKT;ASSOCIINFO\n")

    municipalities.foreach { municipality =>
      println(s"Obtaining all road links and private road association information for Municipality: $municipality")
      val roadLinks = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVHByMunicipality(municipality)._1
      val privateInfo = roadLinkService.getPrivateRoadsInfoByLinkIds(roadLinks.map(_.linkId).toSet)

      val privateRoadAssociationInfo = privateInfo.filter{ case (_, attributeInfo) =>
        attributeInfo match {
          case Some((name, value)) if name == roadLinkService.privateRoadAssociationPublicId && value.trim.nonEmpty => true
          case _ => false
        }
      }

      val rows = privateRoadAssociationInfo.map{ case (linkId, associationInfo) =>
        val roadLink = roadLinks.find(_.linkId == linkId).get
        val wtkGeometry = geometryToWKT(roadLink.geometry)
        val associationInfoValue = associationInfo.get._2

        s"$wtkGeometry;$associationInfoValue"
      }.mkString("\n")

      println("Writing information into file")
      bw.write(rows + "\n")
    }
    bw.close()

    println("\n")
    println("Complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  private val trafficSignGroup = Map[String, TrafficSignTypeGroup] (
    "SpeedLimits" -> TrafficSignTypeGroup.SpeedLimits,
    "RegulatorySigns" ->  TrafficSignTypeGroup.RegulatorySigns,
    "MaximumRestrictions" ->  TrafficSignTypeGroup.MaximumRestrictions,
    "GeneralWarningSigns" ->  TrafficSignTypeGroup.GeneralWarningSigns,
    "ProhibitionsAndRestrictions" ->  TrafficSignTypeGroup.ProhibitionsAndRestrictions,
    "MandatorySigns" ->  TrafficSignTypeGroup.MandatorySigns,
    "PriorityAndGiveWaySigns" ->  TrafficSignTypeGroup.PriorityAndGiveWaySigns,
    "InformationSigns" ->  TrafficSignTypeGroup.InformationSigns,
    "ServiceSigns" ->  TrafficSignTypeGroup.ServiceSigns
  )

  def main(args:Array[String]) : Unit = {
    import scala.util.control.Breaks._
    val username = properties.getProperty("bonecp.username")
    if (!username.startsWith("dr2dev")) {
      println("*************************************************************************************")
      println("YOU ARE RUNNING FIXTURE RESET AGAINST A NON-DEVELOPER DATABASE, TYPE 'YES' TO PROCEED")
      println("*************************************************************************************")
      breakable {
        while (true) {
          val input = Console.readLine()
          if (input.trim() == "YES") {
            break()
          }
        }
      }
    } else
      println("")

    args.headOption match {
      case Some("test") =>
        tearDown()
        setUpTest()
        val typeProps = dataImporter.getTypeProperties
        BusStopTestData.generateTestData.foreach(x => dataImporter.insertBusStops(x, typeProps))
        TrafficSignTestData.createTestData
        ServicePointTestData.createTestData
      case Some("import_roadlink_data") =>
        importRoadLinkData()
      case Some("repair") =>
        flyway.repair()
      case Some("split_speedlimitchains") =>
        splitSpeedLimitChains()
      case Some("split_linear_asset_chains") =>
        splitLinearAssets()
      case Some("dropped_assets_csv") =>
        generateDroppedAssetsCsv()
      case Some("dropped_manoeuvres_csv") =>
        generateDroppedManoeuvres()
      case Some("generate_values_for_lit_roads") =>
        generateValuesForLitRoads()
      case Some("unfloat_linear_assets") =>
        unfloatLinearAssets()
      case Some("expire_split_assets_without_mml") =>
        expireSplitAssetsWithoutMml()
      case Some("prohibitions") =>
        importProhibitions()
      case Some("hazmat_prohibitions") =>
        importHazmatProhibitions()
      case Some("european_roads") =>
        importEuropeanRoads()
      case Some("adjust_digitization") =>
        adjustToNewDigitization()
      case Some("import_link_ids") =>
        LinkIdImporter.importLinkIdsFromVVH(dr2properties.getProperty("digiroad2.VVHRestApiEndPoint"))
      case Some("generate_floating_obstacles") =>
        FloatingObstacleTestData.generateTestData.foreach(createAndFloat)
      case Some("get_addresses_to_masstransitstops_from_vvh") =>
        getMassTransitStopAddressesFromVVH()
      case Some ("link_float_obstacle_assets") =>
        linkFloatObstacleAssets()
      case Some ("check_unknown_speedlimits") =>
        checkUnknownSpeedlimits()
      case Some ("import_VVH_RoadLinks_by_municipalities") =>
        importVVHRoadLinksByMunicipalities()
      case Some("set_transitStops_floating_reason") =>
        transisStopAssetsFloatingReason()
      case Some ("verify_roadLink_administrative_class_changed") =>
        verifyRoadLinkAdministrativeClassChanged()
      case Some("check_TR_bus_stops_without_OTH_LiviId") =>
        updateTierekisteriBusStopsWithoutOTHLiviId(true)
      case Some("set_TR_bus_stops_without_OTH_LiviId") =>
        updateTierekisteriBusStopsWithoutOTHLiviId(false)
      case Some("check_bus_stop_matching_between_OTH_TR") =>
        val dryRun = args.length == 2 && args(1) == "dry-run"
        checkBusStopMatchingBetweenOTHandTR(dryRun)
      case Some("listing_bus_stops_with_side_code_conflict_with_roadLink_direction") =>
        listingBusStopsWithSideCodeConflictWithRoadLinkDirection()
      case Some("fill_lane_amounts_in_missing_road_links") =>
        fillLaneAmountsMissingInRoadLink()
      case Some("fill_roadWidth_in_road_links") =>
        fillRoadWidthInRoadLink()
      case Some("update_areas_on_asset") =>
        updateAreasOnAsset()
      case Some("update_OTH_BS_with_TR_info") =>
        updateOTHBusStopWithTRInfo()
      case Some("update_information_source_on_existing_assets") =>
        updateInformationSource()
      case Some("update_information_source_on_paved_road_assets") =>
        updatePavedRoadInformationSource()
      case Some("update_traffic_direction_on_roundabouts") =>
        updateTrafficDirectionRoundabouts()
      case Some("import_municipality_codes") =>
        importMunicipalityCodes()
      case Some("update_municipalities") =>
        updateMunicipalities()
      case Some("create_manoeuvres_using_traffic_signs") =>
        createManoeuvresUsingTrafficSigns()
      case Some("remove_existing_trafficSigns_duplicates") =>
        removeExistingTrafficSignsDuplicates()
      case Some("merge_additional_panels_to_trafficSigns") =>
        args.lastOption match {
          case Some(group) =>
            mergeAdditionalPanelsToTrafficSigns(trafficSignGroup(group))
          case _ => println("Please provide a traffic sign group")
        }
      case Some("update_floating_stops_on_terminated_roads") =>
        updateFloatingStopsOnTerminatedRoads()
      case Some("update_private_roads") =>
        updatePrivateRoads()
      case Some("add_geometry_to_linear_assets") =>
        addGeometryToLinearAssets()
      case Some("remove_roadWorks_created_last_year") =>
        removeRoadWorksCreatedLastYear()
      case Some("traffic_sign_extract") =>
        extractTrafficSigns(args.lastOption)
      case Some("remove_unnecessary_unknown_speedLimits") =>
        removeUnnecessaryUnknownSpeedLimits()
      case Some("list_incorrect_SpeedLimits_created") =>
        printSpeedLimitsIncorrectlyCreatedOnUnknownSpeedLimitLinks()
      case Some("create_prohibition_using_traffic_signs") =>
        trafficSignProhibitionGenerator.createLinearAssetUsingTrafficSigns()
      case Some("create_hazmat_transport_prohibition_using_traffic_signs") =>
        trafficSignHazmatTransportProhibitionGenerator.createLinearAssetUsingTrafficSigns()
      case Some("create_parking_prohibition_using_traffic_signs") =>
        trafficSignParkingProhibitionGenerator.createLinearAssetUsingTrafficSigns()
      case Some("create_roadWorks_using_traffic_signs") =>
        trafficSignRoadWorkGenerator.createRoadWorkAssetUsingTrafficSign()
      case Some("load_municipalities_verification_info") =>
        loadMunicipalitiesVerificationInfo()
      case Some("resolving_Frozen_Links") =>
        ResolvingFrozenRoadLinks.process()
      case Some("import_private_road_info") =>
        importPrivateRoadInformation()
      case Some("normalize_user_roles") =>
        normalizeUserRoles()
      case Some("get_state_roads_with_overridden_functional_class") =>
        getStateRoadWithFunctionalClassOverridden()
      case Some("get_state_roads_with_undefined_functional_class") =>
        getStateRoadWithFunctionalClassUndefined()
      case Some("add_obstacles_shapefile") =>
        addObstaclesShapefile()
      case Some("merge_municipalities") =>
        mergeMunicipalities()
      case Some("transform_lorry_parking_into_datex2") =>
        transformLorryParkingIntoDatex2()
      case Some("fill_new_roadLinks_info") =>
        fillNewRoadLinksWithPreviousInfo()
      case Some("update_last_modified_assets_info") =>
        updateLastModifiedAssets()
      case Some("import_cycling_walking_info") =>
        importCyclingAndWalkingInfo()
      case Some("extract_csv_private_road_association_info") =>
        extractCsvPrivateRoadAssociationInfo()
      case _ => println("Usage: DataFixture test | import_roadlink_data |" +
        " split_speedlimitchains | split_linear_asset_chains | dropped_assets_csv | dropped_manoeuvres_csv |" +
        " unfloat_linear_assets | expire_split_assets_without_mml | generate_values_for_lit_roads | get_addresses_to_masstransitstops_from_vvh |" +
        " prohibitions | hazmat_prohibitions | adjust_digitization | repair | link_float_obstacle_assets |" +
        " generate_floating_obstacles | import_VVH_RoadLinks_by_municipalities | " +
        " check_unknown_speedlimits | set_transitStops_floating_reason | verify_roadLink_administrative_class_changed | set_TR_bus_stops_without_OTH_LiviId |" +
        " check_TR_bus_stops_without_OTH_LiviId | check_bus_stop_matching_between_OTH_TR | listing_bus_stops_with_side_code_conflict_with_roadLink_direction |" +
        " fill_lane_amounts_in_missing_road_links | update_areas_on_asset | update_OTH_BS_with_TR_info | fill_roadWidth_in_road_links |" +
        " verify_inaccurate_speed_limit_assets | update_information_source_on_existing_assets  | update_traffic_direction_on_roundabouts |" +
        " update_information_source_on_paved_road_assets | import_municipality_codes | update_municipalities | remove_existing_trafficSigns_duplicates |" +
        " create_manoeuvres_using_traffic_signs | update_floating_stops_on_terminated_roads | update_private_roads | add_geometry_to_linear_assets | " +
        " merge_additional_panels_to_trafficSigns | create_traffic_signs_using_linear_assets | create_prohibitions_using_traffic_signs | resolving_Frozen_Links |" +
        " create_hazmat_transport_prohibition_using_traffic_signs | create_parking_prohibition_using_traffic_signs | " +
        " load_municipalities_verification_info | import_private_road_info | normalize_user_roles | get_state_roads_with_overridden_functional_class | get_state_roads_with_undefined_functional_class |" +
        " add_obstacles_shapefile | merge_municipalities | transform_lorry_parking_into_datex2 | fill_new_roadLinks_info | update_last_modified_assets_info | import_cycling_walking_info |" +
        " create_roadWorks_using_traffic_signs | extract_csv_private_road_association_info")
    }
  }
}

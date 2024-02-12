package fi.liikennevirasto.digiroad2.util

import java.io.{BufferedWriter, File, FileWriter}
import java.security.InvalidParameterException
import java.sql.{SQLException, SQLIntegrityConstraintViolationException}
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.{Date, NoSuchElementException, Properties}

import com.googlecode.flyway.core.Flyway
import fi.liikennevirasto.digiroad2.asset.{HeightLimit, RoadLinkProperties => RoadLinkPropertiesAsset, _}
import fi.liikennevirasto.digiroad2.client.VKMClient
import fi.liikennevirasto.digiroad2.client.viite.SearchViiteClient
import fi.liikennevirasto.digiroad2.client.vvh.ChangeType.New
import fi.liikennevirasto.digiroad2.client.{RoadLinkClient, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.dao.RoadLinkOverrideDAO.{AdministrativeClassDao, FunctionalClassDao, LinkAttributes, LinkAttributesDao}
import fi.liikennevirasto.digiroad2.dao.{PostGISUserProvider, _}
import fi.liikennevirasto.digiroad2.dao.linearasset.{PostGISLinearAssetDao, PostGISSpeedLimitDao}
import fi.liikennevirasto.digiroad2.dao.pointasset.Obstacle
import fi.liikennevirasto.digiroad2.dao.pointasset.PostGISTrafficSignDao
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.middleware.TrafficSignManager
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase._
import fi.liikennevirasto.digiroad2.process.SpeedLimitValidator
import fi.liikennevirasto.digiroad2.service._
import fi.liikennevirasto.digiroad2.service.linearasset.{RoadWorkService, _}
import fi.liikennevirasto.digiroad2.service.pointasset._
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.{MassTransitStopOperations, MassTransitStopService, PersistedMassTransitStop}
import fi.liikennevirasto.digiroad2.user.{User, UserProvider}
import fi.liikennevirasto.digiroad2.util.AssetDataImporter.Conversion
import fi.liikennevirasto.digiroad2.{GeometryUtils, TrafficSignTypeGroup, _}
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.slf4j.LoggerFactory

import javax.sql.DataSource
import scala.collection.mutable.ListBuffer
import scala.sys.exit

class IllegalDatabaseConnectionException(msg: String) extends SQLException(msg)

object DataFixture {
  val TestAssetId = 300000

  val dataImporter = new AssetDataImporter

  val logger = LoggerFactory.getLogger(getClass)

  lazy val roadLinkClient: RoadLinkClient = {
    new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
  }

  lazy val viiteClient: SearchViiteClient = {
    new SearchViiteClient(Digiroad2Properties.viiteRestApiEndPoint, HttpClientBuilder.create().build())
  }

  lazy val roadLinkService: RoadLinkService = {
    new RoadLinkService(roadLinkClient, eventbus, new DummySerializer)
  }

  lazy val obstacleService: ObstacleService = {
    new ObstacleService(roadLinkService)
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
    new SpeedLimitService(new DummyEventBus, roadLinkService)
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
      override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
      override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
      override val massTransitStopDao: MassTransitStopDao = new MassTransitStopDao
      override val municipalityDao: MunicipalityDao = new MunicipalityDao
      override val geometryTransform: GeometryTransform = new GeometryTransform(roadAddressService)
    }
    new MassTransitStopServiceWithDynTransaction(eventbus, roadLinkService, roadAddressService)
  }

  lazy val geometryTransform: GeometryTransform = {
    new GeometryTransform(roadAddressService)
  }

  lazy val vkmClient: VKMClient = {
    new VKMClient()
  }

  lazy val postGISLinearAssetDao : PostGISLinearAssetDao = {
    new PostGISLinearAssetDao()
  }

  lazy val inaccurateAssetDAO : InaccurateAssetDAO = {
    new InaccurateAssetDAO()
  }

  lazy val maintenanceService: MaintenanceService = {
    new MaintenanceService(roadLinkService, new DummyEventBus)
  }

  lazy val roadWorkService: RoadWorkService = {
    new RoadWorkService(roadLinkService, eventbus)
  }

  lazy val assetDao : PostGISAssetDao = {
    new PostGISAssetDao()
  }

  lazy val dynamicLinearAssetDao : DynamicLinearAssetDao = {
    new DynamicLinearAssetDao()
  }

  lazy val dynamicLinearAssetService : DynamicLinearAssetService = {
    new DynamicLinearAssetService(roadLinkService, new DummyEventBus)
  }

  lazy val speedLimitDao: PostGISSpeedLimitDao = {
    new PostGISSpeedLimitDao(null)
  }

  lazy val verificationService: VerificationService = {
    new VerificationService( new DummyEventBus, roadLinkService)
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

  lazy val redundantTrafficDirectionRemoval = new RedundantTrafficDirectionRemoval(roadLinkService)

  lazy val roadWidthGenerator = new RoadWidthGenerator

  lazy val unknownSpeedLimitUpdater = new UnknownSpeedLimitUpdater

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
    dataImporter.importEuropeanRoads(Conversion.database(), Digiroad2Properties.vvhServiceHost)
    println(s"European road import complete at time: ${DateTime.now()}")
    println()
  }

  def importProhibitions(): Unit = {
    println(s"\nCommencing prohibition import from conversion at time: ${DateTime.now()}")
    dataImporter.importProhibitions(Conversion.database(), Digiroad2Properties.vvhServiceHost)
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
    val csvGenerator = new CsvGenerator(Digiroad2Properties.vvhServiceHost)
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
    val csvGenerator = new CsvGenerator(Digiroad2Properties.vvhServiceHost)
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
    dataImporter.adjustToNewDigitization(Digiroad2Properties.vvhServiceHost)
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
    dataImporter.getMassTransitStopAddressesFromVVH(Digiroad2Properties.vvhRestApiEndPoint)
    println("complete at time: ")
    println(DateTime.now())
    println("\n")

  }

  def linkFloatObstacleAssets(): Unit = {
    println("\nGenerating list of Obstacle assets to linking")
    println(DateTime.now())
    val roadLinkClient = new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
    val roadLinkService = new RoadLinkService(roadLinkClient, new DummyEventBus, new DummySerializer)
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
              speedLimitService.purgeUnknown(u.asInstanceOf[List[String]].toSet, Seq())
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
      PostGISDatabase.withDynSession {
        Queries.getMunicipalities
      }

    val floatingReasonPublicId = "kellumisen_syy"
    val administrationClassPublicId = "linkin_hallinnollinen_luokka"

    PostGISDatabase.withDynTransaction{

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

      val roadLinks = roadLinkService.fetchRoadlinksByIds(assets.map(_._2).toSet)

      assets.foreach {
        _ match {
          case (assetId, linkId, point, mValue, None) =>
            val roadLink = roadLinks.find(_.linkId == linkId)
            PointAssetOperations.isFloating(municipalityCode = municipality, lon = point.x, lat = point.y,
              mValue = mValue, roadLink = roadLink) match {
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
      //Get All RoadLinks from database by asset link ids
      val roadLinks = roadLinkService.fetchRoadlinksByIds(assets.map(_._2).toSet)

      assets.foreach{
        _ match {
          case (assetId, linkId, None) =>
            roadLinks.find(_.linkId == linkId) match {
              case Some(roadLink) =>
                dataImporter.insertNumberPropertyData(propertyId, assetId, roadLink.administrativeClass.value)
              case _ =>
                println(s"The roadlink with id $linkId was not found")
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
      PostGISDatabase.withDynSession {
        Queries.getMunicipalities
      }

    val administrationClassPublicId = "linkin_hallinnollinen_luokka"

    PostGISDatabase.withDynTransaction {

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

  private def verifyIsChanged(propertyPublicId: String, propertyId: Long, municipality: Int): Unit = {
    val floatingReasonPublicId = "kellumisen_syy"
    val floatingReasonPropertyId = dataImporter.getPropertyTypeByPublicId(floatingReasonPublicId)

    val typeId = 10
    //Get all no floating mass transit stops by municipality id
    val assets = dataImporter.getNonFloatingAssetsWithNumberPropertyValue(typeId, propertyPublicId, municipality)

    println("Processing %d assets not floating".format(assets.length))

    if (assets.nonEmpty) {

      val roadLinks = roadLinkService.fetchRoadlinksByIds(assets.map(_._2).toSet)

      assets.foreach {
        _ match {
          case (assetId, linkId, None) =>
            println("Asset with asset-id: %d doesn't have Administration Class value.".format(assetId))
          case (assetId, linkId, adminClass) =>
            val roadLink = roadLinks.find(_.linkId == linkId)
            MassTransitStopOperations.isFloating(AdministrativeClass.apply(adminClass.get), roadLink) match {
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

  def listingBusStopsWithSideCodeConflictWithRoadLinkDirection(): Unit = {
    println("\nCreate a listing of bus stops on one-way roads in Production that have side code against traffic direction of road link")
    println(DateTime.now())

    var persistedStop: Seq[PersistedMassTransitStop] = Seq()
    var conflictedBusStopsOTH: Seq[PersistedMassTransitStop] = Seq()

    //Get All Municipalities
    val municipalities: Seq[Int] =
      PostGISDatabase.withDynSession {
        Queries.getMunicipalities
      }

    println("Bus stops with side code in conflict By Municipality")

    municipalities.foreach { municipality =>
      println("Start processing municipality %d".format(municipality))

      //Get all OTH Bus Stops By Municipality
      persistedStop = massTransitStopService.getByMunicipality(municipality, false)

      persistedStop.foreach { stop =>
        val massTransitStopDirectionValue = stop.validityDirection

        val roadLinkOfMassTransitStop = roadLinkService.getRoadLinkByLinkId(stop.linkId)
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
    val dao = new PostGISLinearAssetDao()
    val roadLinkService = new RoadLinkService(roadLinkClient, new DummyEventBus, new DummySerializer)

    lazy val linearAssetService: LinearAssetService = {
      new LinearAssetService(roadLinkService, new DummyEventBus)
    }

    println("\nFill Lane Amounts in missing road links")
    println(DateTime.now())
    val username = AutoGeneratedUsername.batchProcessPrefix+DateTimeFormat.forPattern("yyyyMMdd").print(DateTime.now())

    val LanesNumberAssetTypeId = 140
    val NumOfRoadLanesMotorway = 2
    val NumOfRoadLanesSingleCarriageway = 1

    //Get All Municipalities
    val municipalities: Seq[Int] =
      PostGISDatabase.withDynSession {
        Queries.getMunicipalities
      }

    println("Obtaining all Road Links By Municipality")

    //For each municipality get all roadlinks from db for pick link id and pavement data
    municipalities.foreach { municipality =>

      var countMotorway = 0
      var countSingleway = 0
      println("Start processing municipality %d".format(municipality))

      //Obtain all RoadLink by municipality
      val roadLinks = roadLinkService.getRoadLinksByMunicipality(municipality)

      println ("Total roadlink by municipality -> " + roadLinks.size)

      PostGISDatabase.withDynTransaction{
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
          //Create new Assets for the RoadLinks
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

  def updateAreasOnAsset(): Unit = {
    println("\nStart Update areas on Asset at time ")
    println(DateTime.now())
    val MaintenanceRoadTypeId = 290

    //Get All Municipalities
    val municipalities: Seq[Int] =
      PostGISDatabase.withDynSession {
        Queries.getMunicipalities
      }

    println("Obtaining all Road Links By Municipality")

    //For each municipality get all roadlinks from database
    municipalities.foreach { municipality =>

      //Obtain all RoadLink by municipality
      val roadLinks = roadLinkService.getRoadLinksByMunicipality(municipality)

      PostGISDatabase.withDynTransaction {
        //Obtain all existing RoadLinkId by AssetType and roadLinks
        val assets = dataImporter.getAssetsByLinkIds(MaintenanceRoadTypeId, roadLinks.map(_.linkId), includeExpire = true)

        println("Municipality -> " + municipality  + " MaintenanceRoad Assets -> " + assets.size )

        assets.foreach { asset =>
          try {
            val area = maintenanceService.getAssetArea(roadLinks.find(_.linkId == asset._2), Measures(asset._3, asset._4), None)
            assets.foreach(asset => postGISLinearAssetDao.updateArea(asset._1, area))
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

  private def isKIdentifier(username: Option[String]): Boolean = {
    val identifiers: Set[String] = Set("k", "lx", "a", "u")
    username.exists(user => identifiers.exists(identifier => user.toLowerCase.startsWith(identifier)))
  }

  def updateInformationSource(): Unit = {

    println("\nUpdate Information Source for RoadWidth")
    println(DateTime.now())

    val roadLinkService = new RoadLinkService(roadLinkClient, new DummyEventBus, new DummySerializer)

    //    Get All Municipalities
    val municipalities: Seq[Int] =
      PostGISDatabase.withDynSession {
        Queries.getMunicipalities
      }

    municipalities.foreach { municipality =>
      println("\nWorking on... municipality -> " + municipality)
      println("Fetching roadlinks")
      val (roadLinks, changes) = roadLinkService.getRoadLinksAndChangesByMunicipality(municipality)

      PostGISDatabase.withDynTransaction {

        val roadWithMTKClass = roadLinks.filter(road => MTKClassWidth.values.toSeq.contains(road.extractMTKClassWidth(road.attributes)))
        println("Fetching assets")
        val existingAssets = postGISLinearAssetDao.fetchLinearAssetsByLinkIds(RoadWidth.typeId, roadLinks.map(_.linkId), LinearAssetTypes.numericValuePropertyId).filterNot(_.expired)

        println(s"Number of existing assets: ${existingAssets.length}")
        println(s"Start updating assets with Information Source")

        existingAssets.foreach { asset =>
          if(asset.createdBy.contains(AutoGeneratedUsername.mtkClassDefault) && (asset.modifiedBy.isEmpty || asset.modifiedBy.contains(AutoGeneratedUsername.generatedInUpdate))){
            if(!asset.informationSource.contains(MunicipalityMaintenainer))
              postGISLinearAssetDao.updateInformationSource(RoadWidth.typeId, asset.id, MmlNls)
          }
          else{
            if(( (asset.createdBy.contains(AutoGeneratedUsername.dr1Conversion) || asset.createdBy.contains(AutoGeneratedUsername.generatedInUpdate))&& asset.modifiedBy.isEmpty)  ||
              (asset.createdBy.contains(AutoGeneratedUsername.dr1Conversion) && asset.modifiedBy.contains(AutoGeneratedUsername.generatedInUpdate))) {
              if(!asset.informationSource.contains(MunicipalityMaintenainer)) {
                if (roadWithMTKClass.exists(_.linkId == asset.linkId)) {
                  println(s"Asset with ${asset.id} created by dr1_conversion or generated_in_update and with valid MTKCLASS")
                  postGISLinearAssetDao.updateInformationSource(RoadWidth.typeId, asset.id, MmlNls)
                } else
                  postGISLinearAssetDao.updateInformationSource(RoadWidth.typeId, asset.id, MunicipalityMaintenainer)
              }
            }
            else {
              if (asset.createdBy.contains(AutoGeneratedUsername.batchProcessPrefix + "roadWidth") && (asset.modifiedBy.isEmpty || asset.modifiedBy.contains(AutoGeneratedUsername.generatedInUpdate))) {
                if (!asset.informationSource.contains(MunicipalityMaintenainer))
                  postGISLinearAssetDao.updateInformationSource(RoadWidth.typeId, asset.id, RoadRegistry)
              }
              else{
                if( isKIdentifier(asset.createdBy) || isKIdentifier(asset.modifiedBy) )
                  postGISLinearAssetDao.updateInformationSource(RoadWidth.typeId, asset.id, MunicipalityMaintenainer)

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

    val roadLinkService = new RoadLinkService(roadLinkClient, new DummyEventBus, new DummySerializer)

    //Get All Municipalities
    val municipalities: Seq[Int] =
      PostGISDatabase.withDynSession {
        Queries.getMunicipalities
      }

    municipalities.foreach { municipality =>
      println("\nWorking on... municipality -> " + municipality)
      println("Fetching roadlinks")
      val (roadLinks, _) = roadLinkService.getRoadLinksWithComplementaryAndChangesByMunicipality(municipality)

      PostGISDatabase.withDynTransaction {

        println("Fetching assets")
        val existingAssets = dynamicLinearAssetDao.fetchDynamicLinearAssetsByLinkIds(PavedRoad.typeId, roadLinks.map(_.linkId)).filterNot(_.expired)

        println(s"Number of existing assets: ${existingAssets.length}")
        println(s"Start updating assets with Information Source")

        existingAssets.foreach { asset =>
          if (asset.createdBy.contains(AutoGeneratedUsername.batchProcessPrefix + "pavedRoad") && (asset.modifiedBy.isEmpty || asset.modifiedBy.contains(AutoGeneratedUsername.generatedInUpdate))) {
            postGISLinearAssetDao.updateInformationSource(PavedRoad.typeId, asset.id, RoadRegistry)
          } else {
            if (isKIdentifier(asset.createdBy) || isKIdentifier(asset.modifiedBy)) {
              postGISLinearAssetDao.updateInformationSource(PavedRoad.typeId, asset.id, MunicipalityMaintenainer)
            } else {
              if (asset.createdBy.contains(AutoGeneratedUsername.generatedInUpdate) && (asset.modifiedBy.isEmpty || asset.modifiedBy.contains(AutoGeneratedUsername.generatedInUpdate))) {
                postGISLinearAssetDao.updateInformationSource(PavedRoad.typeId, asset.id, MmlNls)
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
      PostGISDatabase.withDynSession {
        Queries.getMunicipalities
      }

    municipalities.foreach { municipality =>
      println("")
      println(s"Obtaining all Road Links for Municipality: $municipality")
      val roadLinks = roadLinkService.getRoadLinksByMunicipality(municipality)

      println(s"Grouping roundabouts")
      val roundabouts = RoundaboutProcessor.groupByRoundabout(roadLinks, withIncomplete = false)

      println("")

      roundabouts.foreach {
        roundabout =>
          val (roadLinkWithTrafficDirection, trafficChanges) = RoundaboutProcessor.setTrafficDirection(roundabout)
          trafficChanges.trafficDirectionChanges.foreach {
            trafficChange =>
              PostGISDatabase.withDynTransaction {

                roadLinkWithTrafficDirection.find(_.linkId == trafficChange.linkId) match {
                  case Some(roadLink) =>
                    println("")
                    val actualTrafficDirection = RoadLinkOverrideDAO.get("traffic_direction", roadLink.linkId)
                    println(s"Before -> linkId: ${roadLink.linkId}, trafficDirection: ${TrafficDirection.apply(actualTrafficDirection)}")

                    println(s"roadLink Processed ->linkId: ${roadLink.linkId} trafficDirection ${roadLink.trafficDirection}, linkType: ${roadLink.linkType.value}")

                    val linkProperty = LinkProperties(roadLink.linkId, roadLink.functionalClass, roadLink.linkType, roadLink.trafficDirection, roadLink.administrativeClass)

                    actualTrafficDirection match {
                      case Some(traffic) => RoadLinkOverrideDAO.update("traffic_direction", linkProperty, Some("batch_roundabout"), actualTrafficDirection.getOrElse(TrafficDirection.UnknownDirection.value))
                      case _ => RoadLinkOverrideDAO.insert("traffic_direction", linkProperty, Some("batch_roundabout"))
                    }

                    val updateTrafficDirection = RoadLinkOverrideDAO.get("traffic_direction", roadLink.linkId)
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
      PostGISDatabase.withDynSession {
        Queries.getMunicipalities
      }

    municipalities.foreach { municipality =>

      println(s"Obtaining all traffic Signs with turning restriction for municipality $municipality")
      //Get All Traffic Signs with traffic restriction

      val trafficSigns = trafficSignService.getTrafficSigns(municipality, trafficSignService.getRestrictionsEnumeratedValues(TrafficSignManager.manoeuvreRelatedSigns))
      println(s"Obtaining all Road Links for Municipality: $municipality")
      val roadLinks = roadLinkService.getRoadLinksByMunicipality(municipality)
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
      PostGISDatabase.withDynSession{
        Queries.getMunicipalities
      }

    PostGISDatabase.withDynTransaction {
      val additionalPanelIdToExpire : Seq[(Option[Long], String, Int)] = municipalities.flatMap { municipality =>
        println("")
        println(DateTime.now())
        println(s"Fetching Traffic Signs for Municipality: $municipality")

        val roadLinks = roadLinkService.getRoadLinksWithComplementaryAndChangesByMunicipality(municipality, newTransaction = false)._1
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

                trafficSignService.updateWithoutTransaction(sign.id, updatedTrafficSign, roadLink, AutoGeneratedUsername.batchProcessPrefix + "panel_merge", Some(sign.mValue), Some(sign.timeStamp))
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
        //        trafficSignService.expireAssetWithoutTransaction(trafficSignService.withIds(Set(id).flatten), Some(AutoGeneratedUsername.batchProcessPrefix + "panel_merge"))
        println(s"Additional panel expired with id $id and type ${TrafficSignType.applyOTHValue(signType).toString} on linkId $linkId")
      }
    }
    println("")
    errorLogBuffer.foreach(println)
    println("Complete at time: " + DateTime.now())
  }

  def updateTrafficSignProperties(): Unit = {
    println("\nStarting traffic sign updates ")
    println(DateTime.now())

    val municipalities: Seq[Int] = {
      PostGISDatabase.withDynSession {
        Queries.getMunicipalities
      }
    }

    // Fetch property ID's.
    val sign_material_propertyId: Long = {
      PostGISDatabase.withDynSession {
        Queries.getPropertyIdByPublicId("sign_material")
      }
    }

    val size_propertyId: Long = {
      PostGISDatabase.withDynSession {
        Queries.getPropertyIdByPublicId("size")
      }
    }

    val structure_propertyId: Long = {
      PostGISDatabase.withDynSession {
        Queries.getPropertyIdByPublicId("structure")
      }
    }

    val life_cycle_propertyId: Long = {
      PostGISDatabase.withDynSession {
        Queries.getPropertyIdByPublicId("life_cycle")
      }
    }

    val repair_propertyId: Long = {
      PostGISDatabase.withDynSession {
        Queries.getPropertyIdByPublicId("urgency_of_repair")
      }
    }

    val damage_propertyId: Long = {
      PostGISDatabase.withDynSession {
        Queries.getPropertyIdByPublicId("type_of_damage")
      }
    }

    val lane_propertyId: Long = {
      PostGISDatabase.withDynSession {
        Queries.getPropertyIdByPublicId("lane_type")
      }
    }

    val coating_propertyId: Long = {
      PostGISDatabase.withDynSession {
        Queries.getPropertyIdByPublicId("coating_type")
      }
    }

    val locationSpecifier_propertyId: Long = {
      PostGISDatabase.withDynSession {
        Queries.getPropertyIdByPublicId("location_specifier")
      }
    }

    val condition_propertyId: Long = {
      PostGISDatabase.withDynSession {
        Queries.getPropertyIdByPublicId("condition")
      }
    }

    val old_trafficSign_code_propertyId: Long = {
      PostGISDatabase.withDynSession {
        Queries.getPropertyIdByPublicId("old_traffic_code")
      }
    }

    municipalities.foreach { municipality =>
      println(s"Fetching traffic signs for municipality: $municipality")
      val trafficSigns = trafficSignService.getByMunicipality(municipality)
      println(s"Number of existing assets: ${trafficSigns.length}")
      trafficSigns.foreach { trafficSign =>
        val createdDate = trafficSign.createdAt.get
        val migrationDate = DateTime.parse("2020-05-14T15:32:46", DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss"))
        if (createdDate.isBefore(migrationDate)) {
          PostGISDatabase.withDynTransaction {
            PostGISTrafficSignDao.createOrUpdateProperties(trafficSign.id, "sign_material", sign_material_propertyId, "single_choice", propertyValues = Seq(PropertyValue("99", Some("Ei tietoa"), false)))
            PostGISTrafficSignDao.createOrUpdateProperties(trafficSign.id, "size", size_propertyId, "single_choice", propertyValues = Seq(PropertyValue("99", Some("Ei tietoa"), false)))
            PostGISTrafficSignDao.createOrUpdateProperties(trafficSign.id, "structure", structure_propertyId, "single_choice", propertyValues = Seq(PropertyValue("99", Some("Ei tietoa"), false)))
            PostGISTrafficSignDao.createOrUpdateProperties(trafficSign.id, "life_cycle", life_cycle_propertyId, "single_choice", propertyValues = Seq(PropertyValue("3", Some("Käytössä pysyvästi"), false)))
            PostGISTrafficSignDao.createOrUpdateProperties(trafficSign.id, "urgency_of_repair", repair_propertyId, "single_choice", propertyValues = Seq(PropertyValue("99", Some("Ei tiedossa"), false)))
            PostGISTrafficSignDao.createOrUpdateProperties(trafficSign.id, "type_of_damage", damage_propertyId, "single_choice", propertyValues = Seq(PropertyValue("99", Some("Ei tiedossa"), false)))
            PostGISTrafficSignDao.createOrUpdateProperties(trafficSign.id, "lane_type", lane_propertyId, "single_choice", propertyValues = Seq(PropertyValue("99", Some("Ei tiedossa"), false)))
            PostGISTrafficSignDao.createOrUpdateProperties(trafficSign.id, "coating_type", coating_propertyId, "single_choice", propertyValues = Seq(PropertyValue("99", Some("Ei tietoa"), false)))
            PostGISTrafficSignDao.createOrUpdateProperties(trafficSign.id, "condition", condition_propertyId, "single_choice", propertyValues = Seq(PropertyValue("99", Some("Ei tietoa"), false)))
            PostGISTrafficSignDao.createOrUpdateProperties(trafficSign.id, "location_specifier", locationSpecifier_propertyId, "single_choice", propertyValues = Seq(PropertyValue("99", Some("Ei tietoa"), false)))
            PostGISTrafficSignDao.createOrUpdateProperties(trafficSign.id, "old_traffic_code", old_trafficSign_code_propertyId, "checkbox", propertyValues = Seq(PropertyValue("1", Some("Väärä"), false)))
            Queries.updateAdditionalPanelProperties(trafficSign.id)
          }
        }
        else {
          println("New traffic sign, no need to update - " + trafficSign.id)
        }
      }
      println("Traffic Sign updates complete " + DateTime.now())
    }
  }

  def removeExistingTrafficSignsDuplicates(): Unit = {
    println("\nStarting removing of traffic signs duplicates")
    println(DateTime.now())

    //Get All Municipalities
    val municipalities: Seq[Int] =
      PostGISDatabase.withDynSession {
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
          PostGISDatabase.withDynTransaction {
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
    val municipalities: Seq[Int] =  PostGISDatabase.withDynSession {
      Queries.getMunicipalities
    }

    municipalities.foreach {
      municipality =>

        println(s"Obtaining all Road Links for Municipality: $municipality")
        val roadLinks = roadLinkService.getRoadLinksWithComplementaryAndChangesByMunicipality(municipality)._1
        println(s"End of roadLinks fetch for Municipality: $municipality")
        PostGISDatabase.withDynTransaction {
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
    val municipalities: Seq[Int] = PostGISDatabase.withDynSession { Queries.getMunicipalities  }

    municipalities.foreach { municipality =>
      println(s"Obtaining all Road Links for Municipality: $municipality")
      val roadLinksWithAssets =  PostGISDatabase.withDynTransaction {
        val roadLinks = roadLinkService.getRoadLinksByMunicipality(municipality, newTransaction = false).filter(_.administrativeClass == Private)
        val linkIds = roadLinks.map(_.linkId)

        val existingAssets = postGISLinearAssetDao.fetchAssetsByLinkIds(assetTypes, linkIds)
        roadLinks.filter(roadLink => existingAssets.map(_.linkId).toSet.contains(roadLink.linkId))
      }
      roadLinksWithAssets.foreach { roadLink =>
        val linkProperty = LinkProperties(roadLink.linkId, roadLink.functionalClass, roadLink.linkType, roadLink.trafficDirection, roadLink.administrativeClass, Some(""), Some(AdditionalInformation.DeliveredWithRestrictions), Some(""))
        roadLinkService.updateLinkProperties(linkProperty, Option("update_private_roads_process"), (_, _) => {})
      }
    }
  }


  def printSpeedLimitsIncorrectlyCreatedOnUnknownSpeedLimitLinks(): Unit = {
    println("\nStart checking unknown speedLimits on top on wrong links")
    println(DateTime.now())

    //Get All Municipalities
    val municipalities: Seq[Int] = PostGISDatabase.withDynSession { Queries.getMunicipalities  }
    println(s"Municipality_code; AssetId; StartMeasure; EndMeasure; SideCode; Value; linkId, LinkType; AdministrativeClass")
    PostGISDatabase.withDynTransaction {
      municipalities.foreach { municipality =>

        val roads = roadLinkService.getRoadLinksByMunicipality(municipality, false)
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
    val municipalities: Seq[Int] = PostGISDatabase.withDynSession {
      Queries.getMunicipalities
    }
    PostGISDatabase.withDynTransaction {
      municipalities.foreach { municipality =>
        println(s"Working on municipality : $municipality")

        val privateRoadInfo = ImportShapeFileDAO.getPrivateRoadExternalInfo(municipality).groupBy(_._1)

        if (privateRoadInfo.nonEmpty) {
          println(s"Number of records to update ${privateRoadInfo.keySet.size}")

          val fetchedRoadLinks = roadLinkService.fetchRoadlinksAndComplementaries(privateRoadInfo.keySet)
          val roadLinks = roadLinkService.enrichFetchedRoadLinks(fetchedRoadLinks)

          val missingRoadLinks = privateRoadInfo.keySet.diff(roadLinks.map(_.linkId).toSet)
          if (missingRoadLinks.nonEmpty)
            println(s"LinkId not found ${missingRoadLinks.mkString(",")}")

          val (privateRoad, otherRoad) = roadLinks.partition(_.administrativeClass == Private)

          otherRoad.foreach { road =>
            println(s"Change Administrative Class for link ${road.linkId}")
            val linkProperties = LinkProperties(road.linkId, road.functionalClass, road.linkType, road.trafficDirection, road.administrativeClass)
            if (road.administrativeClass != Unknown)
              AdministrativeClassDao.updateValues(linkProperties, fetchedRoadLinks.find(_.linkId == road.linkId).get, Some(username), Private.value, privateRoadInfo(road.linkId).map(_._2).headOption)
            else
              AdministrativeClassDao.insertValues(linkProperties, fetchedRoadLinks.find(_.linkId == road.linkId).get, Some(username), Private.value, privateRoadInfo(road.linkId).map(_._2).headOption)
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

    PostGISDatabase.withDynTransaction {
      val linkIdsOverridden = FunctionalClassDao.getLinkIdByValue(functionalClassValue, sinceDate).toSet
      val roadLinks = roadLinkService.getRoadLinksByLinkIds(linkIdsOverridden, false).filter(_.administrativeClass == State)

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
    val municipalities: Seq[Int] = PostGISDatabase.withDynSession {
      Queries.getMunicipalities
    }
    PostGISDatabase.withDynTransaction {
      municipalities.foreach { municipality =>
        val roadLinks = roadLinkService.getRoadLinksByMunicipality(municipality, false).filter(rl => rl.administrativeClass == State && rl.functionalClass == functionalClassValue)

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
    val userProvider: UserProvider = new PostGISUserProvider
    val user = userProvider.getUser("k903846").get
    val minimumDistanceFromRoadLink: Double = 3.0
    val username = "batch_to_add_obstacles"

    println("\nGetting all obstacles information from the table created by the shapefile import")
    val obstaclesInformation: Seq[ObstacleShapefile] = PostGISDatabase.withDynSession {
      ImportShapeFileDAO.getObstaclesFromShapefileTable
    }

    PostGISDatabase.withDynTransaction {
      obstaclesInformation.foreach { obstacle =>
        println("")
        println("Creating a obstacle with coordinates -> " + "x:" + obstacle.lon + " y:" + obstacle.lat)
        val pointObstacle = Point(obstacle.lon, obstacle.lat)

        roadLinkService.getClosestRoadlink(user, pointObstacle, 10) match {
          case Some(link) =>
            val nearestRoadLinks = roadLinkService.enrichFetchedRoadLinks(Seq(link))

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

    val municipalities: Seq[Int] = PostGISDatabase.withDynSession {
      Queries.getMunicipalities
    }

    PostGISDatabase.withDynTransaction {
      municipalities.foreach { municipality =>
        println(s"Working on municipality : $municipality")
        val cyclingAndWalkingInfo = ImportShapeFileDAO.getCyclingAndWalkingInfo(municipality)

        println(s"Number of records to update ${cyclingAndWalkingInfo.size}")
        if (cyclingAndWalkingInfo.nonEmpty) {
          val roadLinks = roadLinkService.getRoadLinksByMunicipality(municipality, false)

          cyclingAndWalkingInfo.foreach { asset =>
            val roadLink = roadLinks.find(_.linkId == asset.linkId)
            val value = DynamicValue(DynamicAssetValue(Seq(DynamicProperty("cyclingAndWalking_type", "single_choice", true, Seq(DynamicPropertyValue(asset.value))))))

            roadLink match {
              case Some(link) =>
                val id = dynamicLinearAssetService.createWithoutTransaction(typeId = assetType,
                  linkId = asset.linkId,
                  value = value,
                  sideCode = SideCode.BothDirections.value,
                  measures = Measures(0, GeometryUtils.geometryLength(link.geometry)),
                  username = username,
                  roadLink = roadLink)
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

    val userProvider: UserProvider = new PostGISUserProvider
    println("\nGetting operators with additional roles")

    val users: Seq[User] = PostGISDatabase.withDynSession {
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
    val municipalities: Seq[Int] = PostGISDatabase.withDynSession { Queries.getMunicipalities  }
    var counter = 0
    PostGISDatabase.withDynTransaction {
      municipalities.foreach { municipality =>
        counter += 1
        println(s"Working on municipality $municipality ($counter/${municipalities.size})")
        val roadLinkIds = roadLinkService.getRoadLinksIdsByMunicipality(municipality)
        verificationService.refreshVerificationInfo(municipality, roadLinkIds, Some(DateTime.now()))
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
    PostGISDatabase.withDynTransaction {
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
      PostGISDatabase.withDynSession {
        Queries.getMunicipalities
      }

    municipalities.foreach { municipality =>
      println("\nWorking on... municipality -> " + municipality)
      println("Fetching roadlinks")
      val (roadLinks, _) = roadLinkService.getRoadLinksWithComplementaryAndChangesByMunicipality(municipality)

      PostGISDatabase.withDynTransaction {
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
      PostGISDatabase.withDynSession{
        Queries.getMunicipalities
      }
    withDynTransaction{
      municipalities.foreach{ municipality =>
        val roadLinks = roadLinkService.getRoadLinksWithComplementaryAndChangesByMunicipality(municipality, newTransaction = false)._1
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
    val municipalityToDelete = 99
    val municipalityToMerge = 214

    println(s"\nStart process of merging municipality $municipalityToDelete into $municipalityToMerge")
    println(DateTime.now())
    println("")

    PostGISDatabase.withDynTransaction{
      Queries.mergeMunicipalities(municipalityToDelete, municipalityToMerge)
    }

    println("")
    println("Complete at time: " + DateTime.now())
  }

  def fillNewRoadLinksWithPreviousInfo(): Unit = {
    def getAdjacentsRoadLinks(allAdjacentsRoadLinks: Seq[RoadLink], point: Point): Seq[RoadLink] = {
      allAdjacentsRoadLinks.filter(r => GeometryUtils.areAdjacent(r.geometry, point))
    }

    def createNewSpeedLimits(newSpeedLimits: Seq[PieceWiseLinearAsset], roadLink: RoadLink): Unit = {
      //Create new SpeedLimits on gaps
      newSpeedLimits.foreach { speedLimit =>
        speedLimitDao.createSpeedLimit(AutoGeneratedUsername.generatedInUpdate, speedLimit.linkId, Measures(speedLimit.startMeasure, speedLimit.endMeasure), speedLimit.sideCode, speedLimitService.getSpeedLimitValue(speedLimit.value).get, Some(LinearAssetUtils.createTimeStamp()), None, None, None, roadLink.linkSource)
        println("New SpeedLimit created at Link Id: " + speedLimit.linkId + " with value: " + speedLimit.value.get + " and sidecode: " + speedLimit.sideCode)

        //Remove linkIds from Unknown Speed Limits working list after speedLimit creation
        speedLimitDao.purgeFromUnknownSpeedLimits(speedLimit.linkId, GeometryUtils.geometryLength(roadLink.geometry))
        println("\nRemoved linkId " + speedLimit.linkId + " from UnknownSpeedLimits working list")
        println("")
      }
    }

    println("\nStart process to fill new road links with previous data, only for change type 12")
    println(DateTime.now())

    //Get All Municipalities
    val municipalities: Seq[Int] = PostGISDatabase.withDynSession {
      Queries.getMunicipalities
    }


    municipalities.foreach { municipality =>
      PostGISDatabase.withDynTransaction {
        println("\nWorking at Municipailty: " + municipality)
        val (roadLinks, changes) = roadLinkService.getRoadLinksWithComplementaryAndChangesByMunicipality(municipality, newTransaction = false)
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
            val assetAndPoints: Seq[(Point, PieceWiseLinearAsset)] = speedLimitService.getAssetsAndPoints(speedLimitsOnAdjacents, roadLinks, (cws, changeRoadLink))

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

    val municipalities: Seq[Int] = PostGISDatabase.withDynSession { Queries.getMunicipalities }

    println("\n")
    println("Municipalities fetched after: " + DateTime.now())
    println("\n")

    var counter = 0
    municipalities.foreach { municipality =>
      PostGISDatabase.withDynTransaction {
        counter += 1
        println(s"Working on municipality $municipality ($counter/${municipalities.size})")
        val modifiedAssetTypes = LogUtils.time(logger, "BATCH LOG get modified asset types")(
          verificationService.dao.getModifiedAssetTypes(Set(municipality))
        )

        LogUtils.time(logger, "BATCH LOG insert modified asset types")(
          verificationService.dao.insertModifiedAssetTypes(municipality, modifiedAssetTypes)
        )

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
    val municipalities: Seq[Int] = PostGISDatabase.withDynSession { Queries.getMunicipalities }

    val newCsvFile = new File("private_road_association_information.csv")
    val bw = new BufferedWriter(new FileWriter(newCsvFile))
    bw.write("WKT;ASSOCIINFO;ACCESS_RIGHT_ID\n")

    municipalities.foreach { municipality =>
      println(s"Obtaining all road links and private road association information for Municipality: $municipality")
      val roadLinks = roadLinkService.getRoadLinksWithComplementaryAndChangesByMunicipality(municipality)._1
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
        val accessRightId: String = GetAccessRightID(linkId)

        s"$wtkGeometry;$associationInfoValue;$accessRightId"
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

  def GetAccessRightID(linkId: String):String ={
    PostGISDatabase.withDynSession {
      val response= LinkAttributesDao.getExistingValues(linkId).getOrElse("ACCESS_RIGHT_ID", "")
      response
    }

  }

  def restoreExpiredAssetsFromTRImport() = {
    val processName = "batch_restore_expired_assets"
    val municipalitiesToRestoreFrom = municipalityService.getMunicipalitiesNameAndIdByEly(Set(AhvenanmaaEly.id)).map(_.id)
    val assetTypeIdsToRestore = Seq(
      BogieWeightLimit.typeId, AxleWeightLimit.typeId, TotalWeightLimit.typeId, TrailerTruckWeightLimit.typeId,
      HeightLimit.typeId, LitRoad.typeId, PavedRoad.typeId, DamagedByThaw.typeId, MassTransitLane.typeId, EuropeanRoads.typeId
    )
    val startDate = "2020-08-12 04:30:00"
    val endDate = "2020-08-13 12:05:00"

    val roadLinksFromMunicipalities = municipalitiesToRestoreFrom
      .flatMap { municipality =>
        println(s"Obtaining all road links and for Municipality: $municipality")
        roadLinkService.getRoadLinksByMunicipality(municipality)
      }
      .map(_.linkId).toSet
    println(s"All road links obtained")

    PostGISDatabase.withDynTransaction {
      println(s"Fetching all assets wrongfully expired in TR import")
      val expiredAssets = assetDao.getExpiredAssetsByRoadLinkAndTypeIdAndBetweenDates(roadLinksFromMunicipalities, assetTypeIdsToRestore, startDate, endDate)
      println(s"All assets fetched")

      println(s"Starting restoration process for the expired assets")
      assetDao.restoreExpiredAssetsByIds(expiredAssets.toSet, processName)
      println(s"Restoration process completed")
    }
  }

  def moveOldExpiredAssets() = {
    println("\nStart transferring old expired asset information until last year to the history tables")
    println(DateTime.now())

    val historyService = new HistoryService
    val excludedAssetTypes = Seq(UnknownAssetTypeId, Lanes, MassTransitStopAsset, RoadLinkPropertiesAsset)
    val assetTypes = AssetTypeInfo.values.filterNot(excludedAssetTypes.contains)
    val yearGap = 2 //current and previous X years are to maintain (1 = until one year ago, 2 = until two years ago, etc.)

    assetTypes.foreach { at =>
      PostGISDatabase.withDynTransaction {
        println(s"\nFetching all relevant expired assets with asset type ${at.typeId}")

        val assetIds = historyService.getExpiredAssetsIdsByAssetTypeAndYearGap(at, yearGap)
        println(s"Transferring ${assetIds.size} assets")

        assetIds.foreach { id =>
          println(s"Transferring asset $id")
          historyService.transferExpiredAssetToHistoryById(id, at)
        }
      }
    }

    println("\n")
    println("Completed at time: ")
    println(DateTime.now())
    println("\n")
  }

  def newRoadAddressFromViite(): Unit ={
    AutomaticLaneCreationModificationProcess.process()
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

  def flyway: Flyway = {
    val flyway = new Flyway()
    flyway.setDataSource(ds)
    flyway.setInitVersion("-1")
    flyway.setLocations("db.migration")
    flyway
  }

  def flywayInit() {
    flyway.init()
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
    // flyway.clean()
    // This old version of Flyway tries to drop the postgis extension too, so we clean the database manually instead
    SqlScriptRunner.runScriptInClasspath("/clear-db.sql")
    try {
      SqlScriptRunner.executeStatement("delete from schema_version where version_rank > 1")
    } catch {
      case e: Exception => println(s"Failed to reset schema_version table: ${e.getMessage}")
    }
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
      "siilinjarvi_verificationService_test_data.sql",
      "samuutus_test_links.sql"
    ))
  }

  def main(args:Array[String]) : Unit = {
    val batchMode = Digiroad2Properties.batchMode
    if (!batchMode) {
      println("*************************************************************************************")
      println("TURN ENV batchMode true TO RUN DATAFIXTURE BATCHES")
      println("*************************************************************************************")
      exit()
    } else
      println("")

    args.headOption match {
      case Some("test") =>
        if (!(PostGISDatabase.isLocalDbConnection || PostGISDatabase.isAwsUnitTestConnection)) {
          throw new IllegalDatabaseConnectionException("not connected to local database, reset aborted")
        } else {
          logger.info("resetting database")
          tearDown()
          setUpTest()
          val typeProps = dataImporter.getTypeProperties
          BusStopTestData.generateTestData.foreach(x => dataImporter.insertBusStops(x, typeProps))
          TrafficSignTestData.createTestData
          ServicePointTestData.createTestData
        }
      case Some("flyway_init") =>
        flywayInit()
      case Some("flyway_migrate") =>
        migrateAll()
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
        LinkIdImporter.changeLinkIdIntoKMTKVersion()
      case Some("generate_floating_obstacles") =>
        FloatingObstacleTestData.generateTestData.foreach(createAndFloat)
      case Some("get_addresses_to_masstransitstops_from_vvh") =>
        getMassTransitStopAddressesFromVVH()
      case Some ("link_float_obstacle_assets") =>
        linkFloatObstacleAssets()
      case Some ("check_unknown_speedlimits") =>
        checkUnknownSpeedlimits()
      case Some("set_transitStops_floating_reason") =>
        transisStopAssetsFloatingReason()
      case Some ("verify_roadLink_administrative_class_changed") =>
        verifyRoadLinkAdministrativeClassChanged()
      case Some("listing_bus_stops_with_side_code_conflict_with_roadLink_direction") =>
        listingBusStopsWithSideCodeConflictWithRoadLinkDirection()
      case Some("fill_lane_amounts_in_missing_road_links") =>
        fillLaneAmountsMissingInRoadLink()
      case Some("fill_roadWidth_in_road_links") =>
        roadWidthGenerator.fillRoadWidths()
      case Some("update_areas_on_asset") =>
        updateAreasOnAsset()
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
      case Some("update_trafficSign_properties") =>
        updateTrafficSignProperties()
      case Some("remove_existing_trafficSigns_duplicates") =>
        removeExistingTrafficSignsDuplicates()
      case Some("merge_additional_panels_to_trafficSigns") =>
        args.lastOption match {
          case Some(group) =>
            mergeAdditionalPanelsToTrafficSigns(trafficSignGroup(group))
          case _ => println("Please provide a traffic sign group")
        }
      case Some("update_private_roads") =>
        updatePrivateRoads()
      case Some("add_geometry_to_linear_assets") =>
        addGeometryToLinearAssets()
      case Some("remove_roadWorks_created_last_year") =>
        removeRoadWorksCreatedLastYear()
      case Some("traffic_sign_extract") =>
        extractTrafficSigns(args.lastOption)
      case Some("remove_unnecessary_unknown_speedLimits") =>
        unknownSpeedLimitUpdater.updateUnknownSpeedLimits()
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
      case Some("restore_expired_assets_from_TR_import") =>
        restoreExpiredAssetsFromTRImport()
      case Some("move_old_expired_assets") =>
        moveOldExpiredAssets()
      case Some("new_road_address_from_viite") =>
        newRoadAddressFromViite()
      case Some("populate_new_link_with_main_lanes") =>
        MainLanePopulationProcess.process()
      case Some("initial_main_lane_population") =>
        MainLanePopulationProcess.initialProcess()
      case Some("redundant_traffic_direction_removal") =>
        withDynTransaction(redundantTrafficDirectionRemoval.deleteRedundantTrafficDirectionFromDB())
      case Some("refresh_road_link_cache") =>
        RefreshRoadLinkCache.refreshCache()
        exit()  //For a currently unknown reason refreshCache batch doesn't exit automatically upon completion
      case Some("lane_end_date_expirer") =>
        LaneEndDateExpirer.expireLanesByEndDates()
      case Some("resolving_frozen_links") =>
        ResolvingFrozenRoadLinks.process()
      case Some("topology_validation") =>
        ValidateAssets.validateAll()
      case Some("handle_expired_road_links") =>
        ExpiredRoadLinkHandlingProcess.process()
      case _ => println("Usage: DataFixture test | import_roadlink_data |" +
        " split_speedlimitchains | split_linear_asset_chains | dropped_assets_csv | dropped_manoeuvres_csv |" +
        " unfloat_linear_assets | expire_split_assets_without_mml | generate_values_for_lit_roads | get_addresses_to_masstransitstops_from_vvh |" +
        " prohibitions | hazmat_prohibitions | adjust_digitization | repair | link_float_obstacle_assets |" +
        " generate_floating_obstacles | " +
        " check_unknown_speedlimits | set_transitStops_floating_reason | verify_roadLink_administrative_class_changed |" +
        " listing_bus_stops_with_side_code_conflict_with_roadLink_direction |" +
        " fill_lane_amounts_in_missing_road_links | update_areas_on_asset | fill_roadWidth_in_road_links |" +
        " verify_inaccurate_speed_limit_assets | update_information_source_on_existing_assets  | update_traffic_direction_on_roundabouts |" +
        " update_information_source_on_paved_road_assets | import_municipality_codes | update_municipalities | remove_existing_trafficSigns_duplicates |" +
        " create_manoeuvres_using_traffic_signs | update_private_roads | add_geometry_to_linear_assets | " +
        " merge_additional_panels_to_trafficSigns | create_traffic_signs_using_linear_assets | create_prohibitions_using_traffic_signs | " +
        " create_hazmat_transport_prohibition_using_traffic_signs | create_parking_prohibition_using_traffic_signs | " +
        " load_municipalities_verification_info | import_private_road_info | normalize_user_roles | get_state_roads_with_overridden_functional_class | get_state_roads_with_undefined_functional_class |" +
        " add_obstacles_shapefile | merge_municipalities | transform_lorry_parking_into_datex2 | fill_new_roadLinks_info | update_last_modified_assets_info | import_cycling_walking_info |" +
        " create_roadWorks_using_traffic_signs | extract_csv_private_road_association_info | restore_expired_assets_from_TR_import | move_old_expired_assets | new_road_address_from_viite |" +
        " populate_new_link_with_main_lanes | initial_main_lane_population | redundant_traffic_direction_removal |" +
        " refresh_road_link_cache | lane_end_date_expirer | resolving_frozen_links | handle_expired_road_links | topology_validation |")
    }
  }
}

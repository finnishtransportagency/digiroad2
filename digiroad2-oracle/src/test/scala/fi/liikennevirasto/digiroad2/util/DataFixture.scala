package fi.liikennevirasto.digiroad2.util

import java.util.Properties

import com.googlecode.flyway.core.Flyway
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.asset.oracle.OracleAssetDao
import fi.liikennevirasto.digiroad2.client.tierekisteri._
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.linearasset.{MTKClassWidth, NumericValue, PersistedLinearAsset}
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.masstransitstop.{MassTransitStopOperations, TierekisteriBusStopStrategy, TierekisteriBusStopStrategyOperations}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.{MassTransitStopDao, Queries}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import fi.liikennevirasto.digiroad2.pointasset.oracle.Obstacle
import fi.liikennevirasto.digiroad2.roadaddress.oracle.RoadAddressDAO
import fi.liikennevirasto.digiroad2.util.AssetDataImporter.Conversion
import fi.liikennevirasto.digiroad2.{MassTransitStopService, asset, _}
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import slick.jdbc.{StaticQuery => Q}


object DataFixture {
  val TestAssetId = 300000
  lazy val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/bonecp.properties"))
    props
  }

  lazy val propertiesDigiroad: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
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
  lazy val tierekisteriDataImporter: TierekisteriDataImporter = {
    new TierekisteriDataImporter(vvhClient, oracleLinearAssetDao, roadAddressDao, linearAssetService)
  }
  lazy val speedLimitService: SpeedLimitService = {
    new SpeedLimitService(new DummyEventBus, vvhClient, roadLinkService)
  }

  lazy val massTransitStopService: MassTransitStopService = {
    class MassTransitStopServiceWithDynTransaction(val eventbus: DigiroadEventBus, val roadLinkService: RoadLinkService) extends MassTransitStopService {
      override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
      override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
      override val tierekisteriClient: TierekisteriMassTransitStopClient = DataFixture.tierekisteriClient
      override val massTransitStopDao: MassTransitStopDao = new MassTransitStopDao
    }
    new MassTransitStopServiceWithDynTransaction(eventbus, roadLinkService)
  }

  lazy val geometryTransform: GeometryTransform = {
    new GeometryTransform()
  }
  lazy val oracleLinearAssetDao : OracleLinearAssetDao = {
    new OracleLinearAssetDao(vvhClient, roadLinkService)
  }
  lazy val roadAddressDao : RoadAddressDAO = {
    new RoadAddressDAO()
  }

  lazy val tierekisteriTrafficVolumeAsset : TierekisteriTrafficVolumeAssetClient = {
    new TierekisteriTrafficVolumeAssetClient(getProperty("digiroad2.tierekisteriRestApiEndPoint"),
      getProperty("digiroad2.tierekisteri.enabled").toBoolean,
      HttpClientBuilder.create().build())
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

  lazy val tierekisteriSpeedLimitAsset : TierekisteriSpeedLimitAssetClient = {
    new TierekisteriSpeedLimitAssetClient(getProperty("digiroad2.tierekisteriRestApiEndPoint"),
      getProperty("digiroad2.tierekisteri.enabled").toBoolean,
      HttpClientBuilder.create().build())
  }

  lazy val assetDao : OracleAssetDao = {
    new OracleAssetDao()
  }

  def getProperty(name: String) = {
    val property = propertiesDigiroad.getProperty(name)
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
//      "siilijarvi_functional_classes.sql",
//      "siilijarvi_link_types.sql",
//      "siilijarvi_traffic_directions.sql",
//      "siilinjarvi_speed_limits.sql",
//      "siilinjarvi_linear_assets.sql",
      "insert_road_address_data.sql",
      "insert_floating_road_addresses.sql",
      "insert_project_link_data.sql"
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

  def importRoadAddresses(): Unit = {
    println("\nDeprecated! Use \nsbt \"project digiroad2-viite\" \"test:run-main fi.liikennevirasto.viite.util.DataFixture import_road_addresses\"\n instead")
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
    val vvhClient = new VVHClient(dr2properties.getProperty("digiroad2.VVHRestApiEndPoint"))
    val roadLinkService = new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer)
    val speedLimitService = new SpeedLimitService(new DummyEventBus, vvhClient, roadLinkService)
    val unknowns = speedLimitService.getUnknown(None)
    unknowns.foreach { case (_, mapped) =>
      mapped.foreach {
        case (_, x) =>
          x match {
            case u: List[Any] =>
              speedLimitService.purgeUnknown(u.asInstanceOf[List[Long]].toSet)
            case _ =>
          }
        case _ =>
      }
    }
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

    if(assets.length > 0){

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

    if(assets.length > 0){
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
        exists(property => property.publicId == "yllapitajan_koodi" && property.values.exists(value => !value.propertyValue.isEmpty))
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
          val stopPointOption = withDynSession{ geometryTransform.addressToCoords(trStop.roadAddress.road, trStop.roadAddress.roadPart, trStop.roadAddress.track, trStop.roadAddress.mValue) }

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
            case _ => {
              println("Can't resolve the coordenates of the TR bus stop address with livi Id "+ trStop.liviId)
              None
            }
          }
        }catch {
          case e: RoadAddressException => {
            println("RoadAddress throw exception for the TR bus stop address with livi Id "+ trStop.liviId +" "+ e.getMessage)
            None
          }
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

    if (assets.length > 0) {

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
          flatMap(property => property.values.headOption).map(p => p.propertyValue)

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
            if ((roadLinkDirectionValue.head.toString() != SideCode.BothDirections.toString()) && (roadLinkDirectionValue.head.toString() != SideCode.apply(massTransitStopDirectionValue.get.toInt).toString())) {
              //Add a list of conflicted Bus Stops
              conflictedBusStopsOTH = conflictedBusStopsOTH ++ List(stop)
            }
          case _ => {
            None
          }
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

      if (filteredRoadLinksByNonCreated.size != 0) {
          //Create new Assets for the RoadLinks from VVH
          filteredRoadLinksByNonCreated.foreach { roadLinkProp =>

            val endMeasure = GeometryUtils.geometryLength(roadLinkProp.geometry)
            roadLinkProp.linkType match {
              case asset.SingleCarriageway =>
                roadLinkProp.trafficDirection match {
                  case asset.TrafficDirection.BothDirections => {
                    dataImporter.insertNewAsset(LanesNumberAssetTypeId, roadLinkProp.linkId, 0, endMeasure, asset.SideCode.BothDirections.value , NumOfRoadLanesSingleCarriageway, username)
                    countSingleway = countSingleway+ 1
                  }
                  case _ => {
                    None
                  }
                }
              case asset.Motorway | asset.Freeway =>
                roadLinkProp.trafficDirection match {
                  case asset.TrafficDirection.TowardsDigitizing | asset.TrafficDirection.AgainstDigitizing => {
                    dataImporter.insertNewAsset(LanesNumberAssetTypeId, roadLinkProp.linkId, 0, endMeasure, asset.SideCode.BothDirections.value, NumOfRoadLanesMotorway, username)
                    countMotorway = countMotorway + 1
                  }
                  case _ => {
                    None
                  }
                }
              case _ => {
                None
              }
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
            assets.filter(asset =>
              asset.createdBy.contains("dr1_conversion") ||
              (asset.vvhTimeStamp < changeInfo.vvhTimeStamp && asset.createdBy.contains("vvh_mtkclass_default"))
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
                measures._1, measures._2, Some("vvh_mtkclass_default"), None, None, None, false, roadWidthAssetTypeId, changeInfo.vvhTimeStamp, None, linkSource = roadLink.linkSource, Some("vvh_mtkclass_default"), None))
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
            Measures(linearAsset.startMeasure, linearAsset.endMeasure), linearAsset.createdBy.getOrElse("vvh_mtkclass_default"), linearAsset.vvhTimeStamp, Some(roadLink.linkSource.value))
          linearAsset.value match {
            case Some(NumericValue(intValue)) =>
              dao.insertValue(id, LinearAssetTypes.numericValuePropertyId, intValue)
            case _ => None
          }
        }
      }
    }

    println("\nFill Road Width in missing and incomplete road links")
    println(DateTime.now())
  }

  def importAllTrafficVolumeDataFromTR() {
    println("\nStart TrafficVolume import at time: ")
    println(DateTime.now())

    tierekisteriDataImporter.importTrafficVolumeAsset(tierekisteriTrafficVolumeAsset)

    println("TrafficVolume import complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def importAllLitRoadDataFromTR() {
    println("\nStart LitRoad import at time: ")
    println(DateTime.now())

    tierekisteriDataImporter.importLitRoadAsset

    println("LitRoad import complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def importAllRoadWidthDataFromTR() {
    println("\nStart RoadWidth import at time: ")
    println(DateTime.now())

    tierekisteriDataImporter.importRoadWidthAsset

    println("RoadWidth import complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def importAllTrafficSignDataFromTR(): Unit ={
    println("\nStart Traffic Signs import at time: ")
    println(DateTime.now())

    tierekisteriDataImporter.importTrafficSigns

    println("Traffic signs import complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def importAllSpeedLimitDataFromTR(): Unit ={
    println("\nStart Speed Limits import at time: ")
    println(DateTime.now())

    tierekisteriDataImporter.importSpeedLimits()

    println("Speed Limits import complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def importAllPavedRoadDataFromTR(): Unit ={
    println("\nStart PavedRoad import at time: ")
    println(DateTime.now())

    tierekisteriDataImporter.importPavedRoadAsset

    println("PavedRoad import complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def importAllMassTransitLaneDataFromTR(): Unit ={
    println("\nStart MassTransitLane import at time: ")
    println(DateTime.now())

    tierekisteriDataImporter.importMassTransitLaneAsset

    println("MassTransitLane import complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def importAllDamagedByThawDataFromTR(): Unit ={
    println("\nStart DamagedByThaw import at time: ")
    println(DateTime.now())

    tierekisteriDataImporter.importDamagedByThawAsset

    println("DamagedByThaw import complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def importAllEuropeanRoadDataFromTR(): Unit ={
    println("\nStart EuropeanRoad import at time: ")
    println(DateTime.now())

    tierekisteriDataImporter.importEuropeanRoadAsset

    println("EuropeanRoad import complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def importSpeedLimitAssetFromTR(): Unit ={
    println("\nStart Speed limit import at time: ")
    println(DateTime.now())

    tierekisteriDataImporter.importSpeedLimitAsset()

    println("Speed limit import complete at time: ")
    println(DateTime.now())
    println("\n")

  }

  def updateLitRoadDataFromTR(): Unit ={
    println("\nStart lighting update at: ")
    println(DateTime.now())

    tierekisteriDataImporter.updateLitRoadAsset()

    println("lLighting update complete at time: ")
    println(DateTime.now())
    println("\n")

  }

  def updateRoadWidthDataFromTR(): Unit = {
    println("\nStart roadWidth update at: ")
    println(DateTime.now())

    tierekisteriDataImporter.updateRoadWidthAsset()

    println("RoadWidth update complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def updateTrafficSignDataFromTR(): Unit ={
    println("\nStart Traffic Signs update at: ")
    println(DateTime.now())

    tierekisteriDataImporter.updateTrafficSigns

    println("Traffic Signs update complete at time: ")
    println(DateTime.now())
    println("\n")
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

  def updateSpeedLimitDataFromTR(): Unit = {
    println("\nStart Speed Limits update at: ")
    println(DateTime.now())

    tierekisteriDataImporter.updateSpeedLimits()

    println("Speed Limits update complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def updatePavedRoadDataFromTR(): Unit ={
    println("\nStart PavedRoad update at: ")
    println(DateTime.now())

    tierekisteriDataImporter.updatePavedRoadAsset()

    println("PavedRoad update complete at time: ")
    println(DateTime.now())
    println("\n")

  }

  def updateMassTransitLaneAssetDataFromTR(): Unit ={
    println("\nStart MassTransitLane update at: ")
    println(DateTime.now())

    tierekisteriDataImporter.updateMassTransitLaneAsset()

    println("MassTransitLane update complete at time: ")
    println(DateTime.now())
    println("\n")

  }

  def updateDamagedByThawAssetDataFromTR(): Unit ={
    println("\nStart DamagedByThaw update at: ")
    println(DateTime.now())

    tierekisteriDataImporter.updateDamagedByThawAsset()

    println("DamagedByThaw update complete at time: ")
    println(DateTime.now())
    println("\n")

  }

  def updateEuropeanRoadDataFromTR(): Unit ={
    println("\nStart EuropeanRoad update at: ")
    println(DateTime.now())

    tierekisteriDataImporter.updateEuropeanRoadAsset()

    println("EuropeanRoad update complete at time: ")
    println(DateTime.now())
    println("\n")

  }

  def updateSpeedLimitAssetFromTR(): Unit ={
    println("\nStart Speed limit update at: ")
    println(DateTime.now())

    tierekisteriDataImporter.updateSpeedLimitAssets()

    println("Speed limit import complete at time: ")
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
          flatMap(property => property.values.headOption).map(p => p.propertyValue)

        // Validate if OTH stop are known in Tierekisteri and if is maintained by ELY
        if (stopLiviId.isDefined && liviIdsListTR.contains(stopLiviId.get)) {
          //Data From OTH
          val stopNameSE =
            stop.propertyData.find(property => property.publicId == MassTransitStopOperations.nameSePublicId).
              flatMap(property => property.values.headOption).map(p => p.propertyValue)
            match {
              case Some(roofValue) => roofValue
              case _ => ""
            }

          val stopRoofValue =
            stop.propertyData.find(property => property.publicId == MassTransitStopOperations.roofPublicId).
              flatMap(property => property.values.headOption).map(p => p.propertyValue)
            match {
              case Some(roofValue) => Existence.fromPropertyValue(roofValue)
              case _ => ""
            }

          val stopRaisedBusStopValue =
            stop.propertyData.find(property => property.publicId == MassTransitStopOperations.raisePublicId).
              flatMap(property => property.values.headOption).map(p => p.propertyValue)
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
              SimpleProperty(MassTransitStopOperations.nameSePublicId, Seq(PropertyValue(nameSEinTR))),
              SimpleProperty(MassTransitStopOperations.roofPublicId, Seq(PropertyValue(roofValueinTR.asInstanceOf[Existence].propertyValue.toString()))),
              SimpleProperty(MassTransitStopOperations.raisePublicId, Seq(PropertyValue(raisedValueinTR.asInstanceOf[Existence].propertyValue.toString())))
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
    }

    args.headOption match {
      case Some("test") =>
        tearDown()
        setUpTest()
        val typeProps = dataImporter.getTypeProperties
        BusStopTestData.generateTestData.foreach(x => dataImporter.insertBusStops(x, typeProps))
        importMunicipalityCodes()
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
      case Some ("import_road_addresses") =>
        importRoadAddresses()
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
      case Some("import_all_trafficVolume_from_TR_to_OTH") =>
        importAllTrafficVolumeDataFromTR()
      case Some("import_all_litRoad_from_TR_to_OTH") =>
        importAllLitRoadDataFromTR()
      case Some("import_all_roadWidth_from_TR_to_OTH") =>
        importAllRoadWidthDataFromTR()
      case Some("import_all_trafficSigns_from_TR_to_OTH") =>
        importAllTrafficSignDataFromTR()
      case Some("import_all_pavedRoad_from_TR_to_OTH") =>
        importAllPavedRoadDataFromTR()
      case Some("import_all_massTransitLane_from_TR_to_OTH") =>
        importAllMassTransitLaneDataFromTR()
      case Some("import_all_damagedByThaw_from_TR_to_OTH") =>
        importAllDamagedByThawDataFromTR()
      case Some("import_all_europeanRoad_from_TR_to_OTH") =>
        importAllEuropeanRoadDataFromTR()
      case Some("update_litRoad_from_TR_to_OTH") =>
        updateLitRoadDataFromTR()
      case Some("update_roadWidth_from_TR_to_OTH") =>
        updateRoadWidthDataFromTR()
      case Some("update_trafficSigns_from_TR_to_OTH") =>
        updateTrafficSignDataFromTR()
      case Some("update_pavedRoad_from_TR_to_OTH") =>
        updatePavedRoadDataFromTR()
      case Some("update_massTransitLane_from_TR_to_OTH") =>
        updateMassTransitLaneAssetDataFromTR()
      case Some("update_damagedByThaw_from_TR_to_OTH") =>
        updateDamagedByThawAssetDataFromTR()
      case Some("import_all_speedLimits_from_TR_to_OTH") =>
        importAllSpeedLimitDataFromTR()
      case Some("update_speedLimits_from_TR_to_OTH") =>
        updateSpeedLimitDataFromTR()
      case Some("update_europeanRoad_from_TR_to_OTH") =>
        updateEuropeanRoadDataFromTR()
      case Some("update_areas_on_asset") =>
        updateAreasOnAsset()
      case Some("update_OTH_BS_with_TR_info") =>
        updateOTHBusStopWithTRInfo()
      case Some("import_speed_limit_asset_from_TR_to_OTH") =>
        importSpeedLimitAssetFromTR()
      case Some("update_speed_limit_asset_from_TR_to_OTH") =>
        updateSpeedLimitAssetFromTR()
      case _ => println("Usage: DataFixture test | import_roadlink_data |" +
        " split_speedlimitchains | split_linear_asset_chains | dropped_assets_csv | dropped_manoeuvres_csv |" +
        " unfloat_linear_assets | expire_split_assets_without_mml | generate_values_for_lit_roads | get_addresses_to_masstransitstops_from_vvh |" +
        " prohibitions | hazmat_prohibitions | european_roads | adjust_digitization | repair | link_float_obstacle_assets |" +
        " generate_floating_obstacles | import_VVH_RoadLinks_by_municipalities | " +
        " check_unknown_speedlimits | set_transitStops_floating_reason | verify_roadLink_administrative_class_changed | set_TR_bus_stops_without_OTH_LiviId |" +
        " check_TR_bus_stops_without_OTH_LiviId | check_bus_stop_matching_between_OTH_TR | listing_bus_stops_with_side_code_conflict_with_roadLink_direction |" +
        " fill_lane_amounts_in_missing_road_links | import_all_trafficVolume_from_TR_to_OTH | import_all_litRoad_from_TR_to_OTH | import_all_roadWidth_from_TR_to_OTH |" +
        " import_all_trafficSigns_from_TR_to_OTH | import_all_pavedRoad_from_TR_to_OTH | import_all_massTransitLane_from_TR_to_OTH | update_litRoad_from_TR_to_OTH | " +
        " update_roadWidth_from_TR_to_OTH | update_trafficSigns_from_TR_to_OTH | update_pavedRoad_from_TR_to_OTH | update_massTransitLane_from_TR_to_OTH" +
        " import_all_damagedByThaw_from_TR_to_OTH | update_damagedByThaw_from_TR_to_OTH | import_all_europeanRoad_from_TR_to_OTH | update_speedLimits_from_TR_to_OTH | " +
        " update_europeanRoad_from_TR_to_OTH | update_areas_on_asset | update_OTH_BS_with_TR_info | fill_roadWidth_in_road_links")
    }
  }
}

package fi.liikennevirasto.digiroad2.util

import java.util.Properties

import com.googlecode.flyway.core.Flyway
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.linearasset.{NumericValue, NewLinearAsset}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Queries
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import fi.liikennevirasto.digiroad2.pointasset.oracle.{Obstacle, OracleObstacleDao}
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.util.AssetDataImporter.Conversion
import org.joda.time.DateTime
import org.scalatest.mock.MockitoSugar
import slick.jdbc.{StaticQuery => Q}

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
  lazy val obstacleService: ObstacleService = {
    new ObstacleService(vvhClient)
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
//      "siilijarvi_functional_classes.sql",
//      "siilijarvi_link_types.sql",
//      "siilijarvi_traffic_directions.sql",
//      "siilinjarvi_speed_limits.sql",
//      "siilinjarvi_linear_assets.sql",
      "insert_road_address_data.sql"
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
    println(s"\nCommencing road address import from conversion at time: ${DateTime.now()}")
    dataImporter.importRoadAddressData(Conversion.database())
    println(s"Road address import complete at time: ${DateTime.now()}")
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

      val roadLinks = vvhClient.fetchVVHRoadlinks(assets.map(_._2).toSet)

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
      val roadLinks = vvhClient.fetchVVHRoadlinks(assets.map(_._2).toSet)

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
      case _ => println("Usage: DataFixture test | import_roadlink_data |" +
        " split_speedlimitchains | split_linear_asset_chains | dropped_assets_csv | dropped_manoeuvres_csv |" +
        " unfloat_linear_assets | expire_split_assets_without_mml | generate_values_for_lit_roads | get_addresses_to_masstransitstops_from_vvh |" +
        " prohibitions | hazmat_prohibitions | european_roads | adjust_digitization | repair | link_float_obstacle_assets |" +
        " generate_floating_obstacles | import_VVH_RoadLinks_by_municipalities | " +
        " check_unknown_speedlimits | set_transitStops_floating_reason | import_road_addresses")
    }
  }
}

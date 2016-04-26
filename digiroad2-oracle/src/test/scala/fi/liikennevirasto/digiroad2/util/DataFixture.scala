package fi.liikennevirasto.digiroad2.util

import java.util.Properties

import com.googlecode.flyway.core.Flyway
import fi.liikennevirasto.digiroad2.IncomingObstacle
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.{VVHClient, Point, ObstacleService}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import fi.liikennevirasto.digiroad2.pointasset.oracle.OracleObstacleDao
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
  val obstacleService = new ObstacleService(null)
  val mockVVHClient = MockitoSugar.mock[VVHClient]

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
      "siilijarvi_functional_classes.sql",
      "siilijarvi_link_types.sql",
      "siilijarvi_traffic_directions.sql",
      "siilinjarvi_speed_limits.sql"
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

  def linkFloatObstacleAssets(): Unit = {
    println("\nGenerating list of Obstacle assets to linking")
    println(DateTime.now())
    val lineRange = obstacleService.countFloatingAssets()
    var endLine = false
    var lineMin = lineRange - 1000
    var lineMax = lineRange
    var countUpdate = 0


    do {
      withDynTransaction {
        //Send "1" for get all floating Obstacles assets
        //lineMin - Min Value to do the fetch
        //lineMax - Max Value to do the fetch
        val floatingObstaclesAssets = obstacleService.getFloatingObstacle(1, lineMin, lineMax)
        if (floatingObstaclesAssets == null) {
          endLine = true
        } else {
          countUpdate += 1
          lineMin -= countUpdate
          lineMax -= countUpdate

          for (obstacleData <- floatingObstaclesAssets) {
            //Call filtering operations according to rules where
            var ObstaclesToUpdate = dataImporter.updateObstacleToRoadLink(obstacleData, mockVVHClient)

            //Save updated assets to database
            if (!(obstacleData.equals(ObstaclesToUpdate)))
              obstacleService.updateFloatingAssets(ObstaclesToUpdate)
          }
        }
      }
    } while (endLine)

    println("complete at time: ")
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
        LinkIdImporter.importLinkIdsFromVVH(dr2properties.getProperty("digiroad2.VVHServiceHost"))
      case Some("generate_floating_obstacles") =>
        FloatingObstacleTestData.generateTestData.foreach(createAndFloat)
      case Some ("link_float_obstacle_assets") =>
        linkFloatObstacleAssets()
      case _ => println("Usage: DataFixture test | import_roadlink_data |" +
        " split_speedlimitchains | split_linear_asset_chains | dropped_assets_csv | dropped_manoeuvres_csv |" +
        " unfloat_linear_assets | expire_split_assets_without_mml | generate_values_for_lit_roads |" +
        " prohibitions | hazmat_prohibitions | european_roads | adjust_digitization | repair | link_float_obstacle_assets |" +
        " generate_floating_obstacles")
    }
  }
}

package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.util.AssetDataImporter.{Conversion, TemporaryTables}
import fi.liikennevirasto.digiroad2.util.RoadLinkDataImporter._
import org.joda.time.DateTime
import scala.concurrent.forkjoin.ForkJoinPool
import java.util.Properties
import com.googlecode.flyway.core.Flyway
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import scala.Some
import java.io.{File, PrintWriter}
import scala.collection.parallel.ForkJoinTaskSupport
import slick.driver.JdbcDriver.backend.{Database, DatabaseDef, Session}
import slick.jdbc.{StaticQuery => Q, _}
import Database.dynamicSession

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
      "kauniainen_lit_roads.sql"))
  }

  def importSpeedLimitsFromConversion(taskPool: ForkJoinPool) {
    print("\nCommencing speed limit import from conversion: ")
    println(DateTime.now())
    dataImporter.importSpeedLimits(Conversion, taskPool)
    print("Speed limit import complete: ")
    println(DateTime.now())
    println("\n")
  }

  def importTotalWeightLimitsFromConversion() {
    print("\nCommencing total weight limit import from conversion: ")
    println(DateTime.now())
    dataImporter.importTotalWeightLimits(Conversion.database())
    print("Total weight limit import complete: ")
    println(DateTime.now())
    println("\n")
  }

  def importWeightLimitsFromConversion() {
    print("\nCommencing weight limit import from conversion: ")
    println(DateTime.now())
    dataImporter.importNumericalLimits(Conversion.database(), 20, 40)
    dataImporter.importNumericalLimits(Conversion.database(), 21, 50)
    dataImporter.importNumericalLimits(Conversion.database(), 24, 60)
    print("Weight limit import complete: ")
    println(DateTime.now())
    println("\n")
  }

  def importDimensionLimitsFromConversion() {
    print("\nCommencing dimension limit import from conversion: ")
    println(DateTime.now())
    dataImporter.importNumericalLimits(Conversion.database(), 18, 70)
    dataImporter.importNumericalLimits(Conversion.database(), 19, 80)
    dataImporter.importNumericalLimits(Conversion.database(), 23, 90)
    print("Dimension limit import complete: ")
    println(DateTime.now())
    println("\n")
  }

  def importManoeuvresFromConversion() {
    print("\nCommencing manoeuvre import from conversion: ")
    println(DateTime.now())
    dataImporter.importManoeuvres(Conversion.database())
    print("Manoeuvre import complete: ")
    println(DateTime.now())
    println("\n")
  }

  def importMunicipalityCodes() {
    println("\nCommencing municipality code import at time: ")
    println(DateTime.now())
    new MunicipalityCodeImporter().importMunicipalityCodes()
    println("Municipality code import complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def importMMLIdsOnMassTransitStops() {
    println("\nCommencing MML ID import on mass transit stops at time: ")
    println(DateTime.now())
    dataImporter.importMMLIdsOnMassTransitStops(Conversion.database())
    println("MML ID import complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def importRoadLinkData() = {
    println("\nCommencing functional classes import from conversion DB\n")
    RoadLinkDataImporter.importFromConversionDB()
  }

  def importMMLIdsOnNumericalLimits(): Unit = {
    println("\nCommencing MML ID import on numerical limits at time: ")
    println(DateTime.now())
    println("import mml ids for total weight limits")
    dataImporter.importMMLIdsOnNumericalLimit(Conversion.database(), 30)
    println("import mml ids for trailer truck weight limits")
    dataImporter.importMMLIdsOnNumericalLimit(Conversion.database(), 40)
    println("import mml ids for axle weight limits")
    dataImporter.importMMLIdsOnNumericalLimit(Conversion.database(), 50)
    println("import mml ids for bogie weight limits")
    dataImporter.importMMLIdsOnNumericalLimit(Conversion.database(), 60)
    println("import mml ids for height limits")
    dataImporter.importMMLIdsOnNumericalLimit(Conversion.database(), 70)
    println("import mml ids for length limits")
    dataImporter.importMMLIdsOnNumericalLimit(Conversion.database(), 80)
    println("import mml ids for width limits")
    dataImporter.importMMLIdsOnNumericalLimit(Conversion.database(), 90)
    println("import mml ids for lit roads")
    dataImporter.importMMLIdsOnNumericalLimit(Conversion.database(), 100)
    println("MML ID import complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def importMMLIdsOnSpeedLimits(): Unit = {
    println("\nCommencing MML ID import on speed limits at time: ")
    println(DateTime.now())
    println("import mml ids for speed limits")
    dataImporter.importMMLIdsOnNumericalLimit(Conversion.database(), 20)
    println("MML ID import complete at time: ")
    println(DateTime.now())
    println("\n")
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

  def importLitRoadsFromConversion(): Unit = {
    println("\nCommencing lit roads import from conversion at time: ")
    println(DateTime.now())
    println("import lit roads")
    dataImporter.importLitRoadsFromConversion(Conversion.database())
    println("Lit roads import complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def generateDroppedNumericalLimits(): Unit = {
    println("\nGenerating list of numerical limits outside geometry")
    println(DateTime.now())
    dataImporter.generateDroppedNumericalLimits(dr2properties.getProperty("digiroad2.VVHServiceHost"))
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
      case Some("speedlimits") =>
        val taskPool = new ForkJoinPool(8)
        importSpeedLimitsFromConversion(taskPool)
      case Some("totalweightlimits") =>
        importTotalWeightLimitsFromConversion()
      case Some("weightlimits") =>
        importWeightLimitsFromConversion()
      case Some("dimensionlimits") =>
        importDimensionLimitsFromConversion()
      case Some("manoeuvres") =>
        importManoeuvresFromConversion()
      case Some("mml_masstransitstops") =>
        importMMLIdsOnMassTransitStops()
      case Some("mml_numericallimits") =>
        importMMLIdsOnNumericalLimits()
      case Some("mml_speedlimits") =>
        importMMLIdsOnSpeedLimits()
      case Some("import_roadlink_data") =>
        importRoadLinkData()
      case Some("repair") =>
        flyway.repair()
      case Some("split_speedlimitchains") =>
        splitSpeedLimitChains()
      case Some("split_linear_asset_chains") =>
        splitLinearAssets()
      case Some("litroads") =>
        importLitRoadsFromConversion()
      case Some("dropped_numericallimits") =>
        generateDroppedNumericalLimits()
      case Some("generate_values_for_lit_roads") =>
        generateValuesForLitRoads()
      case Some("unfloat_linear_assets") =>
        unfloatLinearAssets()
      case Some("expire_split_assets_without_mml") =>
        expireSplitAssetsWithoutMml()
      case _ => println("Usage: DataFixture test | speedlimits | totalweightlimits | weightlimits | dimensionlimits |" +
        " manoeuvres | mml_masstransitstops | mml_numericallimits | mml_speedlimits | import_roadlink_data |" +
        " split_speedlimitchains | split_linear_asset_chains | litroads | dropped_numericallimits |" +
        " unfloat_linear_assets | expire_split_assets_without_mml | generate_values_for_lit_roads | repair")
    }
  }
}

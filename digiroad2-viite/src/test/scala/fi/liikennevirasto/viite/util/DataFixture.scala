package fi.liikennevirasto.viite.util

import java.util.Properties

import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, LinkGeomSource}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.SqlScriptRunner
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.viite.dao._
import fi.liikennevirasto.viite.process.{ContinuityChecker, FloatingChecker, InvalidAddressDataException, LinkRoadAddressCalculator}
import fi.liikennevirasto.viite.util.AssetDataImporter.Conversion
import fi.liikennevirasto.viite.{ProjectService, RoadAddressLinkBuilder, RoadAddressService}
import org.joda.time.format.PeriodFormatterBuilder
import org.joda.time.{DateTime, Period}
import scala.language.postfixOps

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

  lazy val continuityChecker = new ContinuityChecker(new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer))

  private lazy val hms = new PeriodFormatterBuilder() minimumPrintedDigits(2) printZeroAlways() appendHours() appendSeparator(":") appendMinutes() appendSuffix(":") appendSeconds() toFormatter

  private lazy val geometryFrozen: Boolean = dr2properties.getProperty("digiroad2.VVHRoadlink.frozen", "false").toBoolean

  private def loopRoadParts(roadNumber: Int) = {
    var partNumberOpt = RoadAddressDAO.fetchNextRoadPartNumber(roadNumber, 0)
    while (partNumberOpt.nonEmpty) {
      val partNumber = partNumberOpt.get
      val roads = RoadAddressDAO.fetchByRoadPart(roadNumber, partNumber, true)
      try {
        val adjusted = LinkRoadAddressCalculator.recalculate(roads)
        assert(adjusted.size == roads.size) // Must not lose any
        val (changed, unchanged) = adjusted.partition(ra =>
            roads.exists(oldra => ra.id == oldra.id && (oldra.startAddrMValue != ra.startAddrMValue || oldra.endAddrMValue != ra.endAddrMValue))
          )
        println(s"Road $roadNumber, part $partNumber: ${changed.size} updated, ${unchanged.size} kept unchanged")
        changed.foreach(addr => RoadAddressDAO.update(addr, None))
      } catch {
        case ex: InvalidAddressDataException => println(s"!!! Road $roadNumber, part $partNumber contains invalid address data - part skipped !!!")
          ex.printStackTrace()
      }
      partNumberOpt = RoadAddressDAO.fetchNextRoadPartNumber(roadNumber, partNumber)
    }
  }

  def recalculate():Unit = {
    OracleDatabase.withDynTransaction {
      var roadNumberOpt = RoadAddressDAO.fetchNextRoadNumber(0)
      while (roadNumberOpt.nonEmpty) {
        loopRoadParts(roadNumberOpt.get)
        roadNumberOpt = RoadAddressDAO.fetchNextRoadNumber(roadNumberOpt.get)
      }
    }
  }

  def importRoadAddresses(isDevDatabase: Boolean): Unit = {
    println(s"\nCommencing road address import from conversion at time: ${DateTime.now()}")
    val vvhClient = new VVHClient(dr2properties.getProperty("digiroad2.VVHRestApiEndPoint"))
    val geometryAdjustedTimeStamp = dr2properties.getProperty("digiroad2.viite.importTimeStamp", "")
    if (geometryAdjustedTimeStamp == "" || geometryAdjustedTimeStamp.toLong == 0L) {
      println(s"****** Missing or bad value for digiroad2.viite.importTimeStamp in properties: '$geometryAdjustedTimeStamp' ******")
    } else {
      println(s"****** Road address geometry timestamp is $geometryAdjustedTimeStamp ******")
      val vvhClientProd = if (isDevDatabase) {
        Some(new VVHClient(dr2properties.getProperty("digiroad2.VVHProdRestApiEndPoint", "http://172.17.204.39:6080/arcgis/rest/services/VVH_OTH/")))
      } else {
        None
      }
      val importOptions = ImportOptions(
        onlyComplementaryLinks = false,
        useFrozenLinkService = dr2properties.getProperty("digiroad2.VVHRoadlink.frozen", "false").toBoolean,
        geometryAdjustedTimeStamp.toLong)
      dataImporter.importRoadAddressData(Conversion.database(), vvhClient, vvhClientProd, importOptions)
    }
    println(s"Road address import complete at time: ${DateTime.now()}")
    println()
  }

  def importRoadAddressesHistory(isDevDatabase: Boolean): Unit = {
    println(s"\nCommencing road address import from conversion at time: ${DateTime.now()}")
    val vvhClient = new VVHClient(dr2properties.getProperty("digiroad2.VVHRestApiEndPoint"))
    val geometryAdjustedTimeStamp = dr2properties.getProperty("digiroad2.viite.importTimeStamp", "")
    if (geometryAdjustedTimeStamp == "" || geometryAdjustedTimeStamp.toLong == 0L) {
      println(s"****** Missing or bad value for digiroad2.viite.importTimeStamp in properties: '$geometryAdjustedTimeStamp' ******")
    } else {
      println(s"****** Road address geometry timestamp is $geometryAdjustedTimeStamp ******")
      val vvhClientProd = if (isDevDatabase) {
        Some(new VVHClient(dr2properties.getProperty("digiroad2.VVHProdRestApiEndPoint", "http://172.17.204.39:6080/arcgis/rest/services/VVH_OTH/")))
      } else {
        None
      }
      val importOptions = ImportOptions(
        onlyComplementaryLinks = false,
        useFrozenLinkService = dr2properties.getProperty("digiroad2.VVHRoadlink.frozen", "false").toBoolean,
        geometryAdjustedTimeStamp.toLong)
      dataImporter.importRoadAddressData(Conversion.database(), vvhClient, vvhClientProd, importOptions)
    }
    println(s"Road address import complete at time: ${DateTime.now()}")
    println()
  }

  def updateRoadAddressesValues(vVHClient: VVHClient): Unit = {
    println(s"\nStarting road address update values from conversion at time: ${DateTime.now()}")
    dataImporter.updateRoadAddressesValues(Conversion.database(), vvhClient)
  }

  def updateMissingRoadAddresses(): Unit = {
    println(s"\nUpdating missing road address table at time: ${DateTime.now()}")
    val vvhClient = new VVHClient(dr2properties.getProperty("digiroad2.VVHRestApiEndPoint"))
    dataImporter.updateMissingRoadAddresses(vvhClient)
    println(s"Missing address update complete at time: ${DateTime.now()}")
    println()
  }

  def updateRoadAddressesGeometry(filterRoadAddresses: Boolean): Unit = {
    println(s"\nUpdating road address table geometries at time: ${DateTime.now()}")
    val vVHClient = new VVHClient(dr2properties.getProperty("digiroad2.VVHRestApiEndPoint"))
    dataImporter.updateRoadAddressesGeometry(vvhClient, filterRoadAddresses)
    println(s"Road addresses geometry update complete at time: ${DateTime.now()}")
    println()
  }

  def findFloatingRoadAddresses(): Unit = {
    println(s"\nFinding road addresses that are floating at time: ${DateTime.now()}")
    val vvhClient = new VVHClient(dr2properties.getProperty("digiroad2.VVHRestApiEndPoint"))
    val username = properties.getProperty("bonecp.username")
    val roadLinkService = new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer)
    val roadAddressService = new RoadAddressService(roadLinkService, new DummyEventBus)
    OracleDatabase.withDynTransaction {
      val checker = new FloatingChecker(roadLinkService)
      val roads = checker.checkRoadNetwork(username)
      println(s"${roads.size} segment(s) found")
      roadAddressService.checkRoadAddressFloatingWithoutTX(roads.map(_.id).toSet, true)
    }
    println(s"\nRoad Addresses floating field update complete at time: ${DateTime.now()}")
    println()
  }

  private def importComplementaryRoadAddress(): Unit ={
    println(s"\nCommencing complementary road address import at time: ${DateTime.now()}")
    OracleDatabase.withDynTransaction {
      OracleDatabase.setSessionLanguage()
    }
    SqlScriptRunner.runViiteScripts(List(
      "insert_complementary_geometry_data.sql"
    ))
    println(s"complementary road address import completed at time: ${DateTime.now()}")
    println()
  }

  private def combineMultipleSegmentsOnLinks(): Unit ={
    println(s"\nCombining multiple segments on links at time: ${DateTime.now()}")
    OracleDatabase.withDynTransaction {
      OracleDatabase.setSessionLanguage()
      RoadAddressDAO.getCurrentValidRoadNumbers().foreach(road => {
        val roadAddresses = RoadAddressDAO.fetchMultiSegmentLinkIds(road).groupBy(_.linkId)
        val replacements = roadAddresses.mapValues(RoadAddressLinkBuilder.fuseRoadAddress)
        roadAddresses.foreach{ case (linkId, list) =>
          val currReplacement = replacements(linkId)
          if (list.size != currReplacement.size) {
            val (kept, removed) = list.partition(ra => currReplacement.exists(_.id == ra.id))
            val (created) = currReplacement.filterNot(ra => kept.exists(_.id == ra.id))
            RoadAddressDAO.remove(removed)
            RoadAddressDAO.create(created, Some("Automatic_merged"))
          }
        }
      })
    }
    println(s"\nFinished the combination of multiple segments on links at time: ${DateTime.now()}")
  }

  private def importRoadAddressChangeTestData(): Unit ={
    println(s"\nCommencing road address change test data import at time: ${DateTime.now()}")
    OracleDatabase.withDynTransaction {
      OracleDatabase.setSessionLanguage()
    }
    SqlScriptRunner.runViiteScripts(List(
      "insert_road_address_change_test_data.sql"
    ))
    println(s"Road Address Change Test Data import completed at time: ${DateTime.now()}")
    println()
  }

  private def applyChangeInformationToRoadAddressLinks(): Unit = {
    val roadLinkService = new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer)
    val roadAddressService = new RoadAddressService(roadLinkService, new DummyEventBus)

    //Get All Municipalities
    val municipalities: Seq[Long] =
      OracleDatabase.withDynTransaction {
        MunicipalityDAO.getMunicipalityMapping.keySet.toSeq
      }

    //For each municipality get all VVH Roadlinks
    municipalities.par.foreach { municipality =>
      println("Start processing municipality %d".format(municipality))

      //Obtain all RoadLink by municipality and change info from VVH
      val (roadLinks, changedRoadLinks) = roadLinkService.getFrozenViiteRoadLinksAndChangesFromVVH(municipality.toInt,properties.getProperty("digiroad2.VVHRoadlink.frozen", "false").toBoolean)
      println ("Total roadlink for municipality " + municipality + " -> " + roadLinks.size)
      println ("Total of changes for municipality " + municipality + " -> " + changedRoadLinks.size)
      if(roadLinks.nonEmpty) {
        //  Get road address from viite DB from the roadLinks ids
        val roadAddresses: List[RoadAddress] =  OracleDatabase.withDynTransaction {
          RoadAddressDAO.fetchByLinkId(roadLinks.map(_.linkId).toSet)
        }
        try {
          val groupedAddresses = roadAddresses.groupBy(_.linkId)
          val timestamps = groupedAddresses.mapValues(_.map(_.adjustedTimestamp).min)
          val affectingChanges = changedRoadLinks.filter(ci =>
            ci.oldId.nonEmpty && timestamps.get(ci.oldId.get).nonEmpty &&
              ci.affects(ci.oldId.get, timestamps(ci.oldId.get)))
          println ("Affecting changes for municipality " + municipality + " -> " + affectingChanges.size)

          roadAddressService.applyChanges(roadLinks, affectingChanges, groupedAddresses)
        } catch {
          case e: Exception => println("ERR! -> " + e.getMessage)
        }
      }

      println("End processing municipality %d".format(municipality))
    }

  }

  private def updateProjectLinkGeom(): Unit = {
    val roadLinkService = new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer)
    val roadAddressService = new RoadAddressService(roadLinkService, new DummyEventBus)
    val projectService = new  ProjectService(roadAddressService,roadLinkService, new DummyEventBus)
    val projectsIDs= projectService.getRoadAddressAllProjects().map(x=>x.id)
    val projectCount=projectsIDs.size
    var c=0
    projectsIDs.foreach(x=>
    {
      c+=1
      println("Updating Geometry for project " +c+ "/"+projectCount)
      projectService.updateProjectLinkGeometry(x,"BJ")
    })

  }

  private def correctNullElyCodeProjects(): Unit = {
    val roadLinkService = new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer)
    val roadAddressService = new RoadAddressService(roadLinkService, new DummyEventBus)
    val projectService = new  ProjectService(roadAddressService,roadLinkService, new DummyEventBus)
    val startTime = DateTime.now()
    println(s"Starting project Ely code correct now")
    projectService.correctNullProjectEly()
    println(s"Project Ely's correct in  ${hms.print(new Period(startTime, DateTime.now()))}")
  }


  private def updateRoadAddressGeometrySource(): Unit = {
    val roadLinkService = new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer)

    //Get All Roads
    val roads: Seq[Long] =
      OracleDatabase.withDynTransaction {
        RoadAddressDAO.getCurrentValidRoadNumbers()
      }

    //For each municipality get all VVH Roadlinks
    roads.par.foreach { road =>
      println("%d: Fetch road addresses for road #%d".format(road, road))
      OracleDatabase.withDynTransaction {
        val roadAddressSeq = RoadAddressDAO.fetchByRoad(road)
        // Floating addresses are ignored
        val linkIds = roadAddressSeq.map(_.linkId).toSet
        println("%d: %d address rows fetched on %d links".format(road, roadAddressSeq.size, linkIds.size))
        val cacLinks = roadLinkService.getCurrentAndComplementaryVVHRoadLinks(linkIds)
          .map(rl => rl.linkId -> rl.linkSource).toMap
        // If not present in current and complementary, check the historic links, too
        val vvhHistoryLinks = roadLinkService.getViiteRoadLinksHistoryFromVVH(linkIds -- cacLinks.keySet)
          .map(rl => rl.linkId -> LinkGeomSource.HistoryLinkInterface).toMap
        val vvhLinks = cacLinks ++ vvhHistoryLinks
        val updated = roadAddressSeq
          .filterNot(ra => vvhLinks.getOrElse(ra.linkId, ra.linkGeomSource) == ra.linkGeomSource)
          .count(ra =>
            RoadAddressDAO.updateLRM(ra.lrmPositionId, vvhLinks(ra.linkId))
          )
        println("%d: %d addresses updated".format(road, updated))
      }
    }

  }

  private def showFreezeInfo() = {
    println("Road link geometry freeze is active; exiting without changes")
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
      case Some ("find_floating_road_addresses")  if geometryFrozen =>
        showFreezeInfo()
      case Some ("find_floating_road_addresses") =>
        findFloatingRoadAddresses()
      case Some ("import_road_addresses") =>
        importRoadAddresses(username.startsWith("dr2dev") || username.startsWith("dr2test"))
      case Some("import_complementary_road_address") =>
        importComplementaryRoadAddress()
      case Some("update_road_addresses_ely_and_road_type") =>
        updateRoadAddressesValues(vvhClient)
      case Some ("recalculate_addresses") =>
        recalculate()
      case Some ("update_missing") if geometryFrozen =>
        showFreezeInfo()
      case Some ("update_missing") =>
        updateMissingRoadAddresses()
      case Some("fuse_multi_segment_road_addresses") =>
        combineMultipleSegmentsOnLinks()
      case Some("update_road_addresses_geometry_no_complementary") if geometryFrozen =>
        showFreezeInfo()
      case Some("update_road_addresses_geometry_no_complementary") =>
        updateRoadAddressesGeometry(true)
      case Some("update_road_addresses_geometry") if geometryFrozen =>
        showFreezeInfo()
      case Some("update_road_addresses_geometry") =>
        updateRoadAddressesGeometry(false)
      case Some ("import_road_address_change_test_data") =>
        importRoadAddressChangeTestData()
      case Some ("apply_change_information_to_road_address_links") if geometryFrozen =>
        showFreezeInfo()
      case Some ("apply_change_information_to_road_address_links") =>
        applyChangeInformationToRoadAddressLinks()
      case Some ("update_road_address_link_source") if geometryFrozen =>
        showFreezeInfo()
      case Some ("update_road_address_link_source") =>
        updateRoadAddressGeometrySource()
      case Some ("update_project_link_geom") =>
        updateProjectLinkGeom()
      case Some("correct_null_ely_code_projects") =>
        correctNullElyCodeProjects()
      case Some("import_road_addresses_history") =>
        importRoadAddressesHistory(username.startsWith("dr2dev") || username.startsWith("dr2test"))
      case _ => println("Usage: DataFixture import_road_addresses | recalculate_addresses | update_missing | " +
        "find_floating_road_addresses | import_complementary_road_address | fuse_multi_segment_road_addresses " +
        "| update_road_addresses_geometry_no_complementary | update_road_addresses_geometry | import_road_address_change_test_data "+
        "| apply_change_information_to_road_address_links | update_road_address_link_source | correct_null_ely_code_projects")
    }
  }
}

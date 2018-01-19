package fi.liikennevirasto.digiroad2.util

import java.util.Properties

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode}
import fi.liikennevirasto.digiroad2.client.tierekisteri.TierekisteriTrafficVolumeAssetClient
import fi.liikennevirasto.digiroad2.client.tierekisteri.importer._
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.{OracleAssetDao, RoadAddressDAO}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetService, LinearAssetTypes, Measures}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

object TierekisteriDataImporter {

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

  lazy val vvhClient: VVHClient = {
    new VVHClient(dr2properties.getProperty("digiroad2.VVHRestApiEndPoint"))
  }

  lazy val roadLinkService : RoadLinkService = {
    new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer)
  }

  lazy val linearAssetService: LinearAssetService = {
    new LinearAssetService(roadLinkService, new DummyEventBus)
  }

  lazy val assetDao : OracleAssetDao = {
    new OracleAssetDao()
  }

  lazy val oracleLinearAssetDao : OracleLinearAssetDao = {
    new OracleLinearAssetDao(vvhClient, roadLinkService)
  }

  lazy val roadAddressDao : RoadAddressDAO = {
    new RoadAddressDAO()
  }

  lazy val litRoadImporterOperations: LitRoadTierekisteriImporter = {
    new LitRoadTierekisteriImporter()
  }

  lazy val roadWidthImporterOperations: RoadWidthTierekisteriImporter = {
    new RoadWidthTierekisteriImporter()
  }

  lazy val trafficSignTierekisteriImporter: TrafficSignTierekisteriImporter = {
    new TrafficSignTierekisteriImporter()
  }

  lazy val pavedRoadImporterOperations: PavedRoadTierekisteriImporter = {
    new PavedRoadTierekisteriImporter()
  }

  lazy val massTransitLaneImporterOperations: MassTransitLaneTierekisteriImporter = {
    new MassTransitLaneTierekisteriImporter()
  }

  lazy val damagedByThawImporterOperations: DamagedByThawTierekisteriImporter = {
    new DamagedByThawTierekisteriImporter()
  }

  lazy val europeanRoadImporterOperations: EuropeanRoadTierekisteriImporter = {
    new EuropeanRoadTierekisteriImporter()
  }

  lazy val stateSpeedLimitTierekisteriImporter: StateSpeedLimitTierekisteriImporter = {
    new StateSpeedLimitTierekisteriImporter()
  }

  lazy val speedLimitTierekisteriImporter: SpeedLimitTierekisteriImporter = {
    new SpeedLimitTierekisteriImporter()
  }

//TODO this was never tested just imported from DROTH-810
//  lazy val winterSpeedLimitImporterOperations: WinterSpeedLimitTierekisteriImporter = {
//    new WinterSpeedLimitTierekisteriImporter()
//  }

  lazy val totalWeightLimitTierekisteriImporter: TotalWeightLimitTierekisteriImporter = {
    new TotalWeightLimitTierekisteriImporter()
  }

  lazy val trailerTruckWeightLimitTierekisteriImporter: TrailerTruckWeightLimitTierekisteriImporter = {
    new TrailerTruckWeightLimitTierekisteriImporter()
  }

  lazy val axleWeightLimitTierekisteriImporter: AxleWeightLimitTierekisteriImporter = {
    new AxleWeightLimitTierekisteriImporter()
  }

  lazy val bogieWeightLimitTierekisteriImporter: BogieWeightLimitTierekisteriImporter = {
    new BogieWeightLimitTierekisteriImporter()
  }

  lazy val heightLimitTierekisteriImporter: HeightLimitTierekisteriImporter = {
    new HeightLimitTierekisteriImporter()
  }

  lazy val widthLimitTierekisteriImporter: WidthLimitTierekisteriImporter = {
    new WidthLimitTierekisteriImporter()
  }

  def getLastExecutionDate(tierekisteriAssetImporter: TierekisteriAssetImporterOperations): Option[DateTime] = {
    OracleDatabase.withDynSession{
      val assetId = tierekisteriAssetImporter.getAssetTypeId
      val assetName = tierekisteriAssetImporter.getAssetName
      assetDao.getLastExecutionDate(assetId, s"batch_process_$assetName")
    }
  }

  //TODO migrate this import asset to TierekisteriImporterOperations
  def importTrafficVolumeAsset(tierekisteriTrafficVolumeAsset: TierekisteriTrafficVolumeAssetClient) = {
    val trafficVolumeId = 170
    println("\nExpiring Traffic Volume From OTH Database")
    OracleDatabase.withDynSession {
      oracleLinearAssetDao.expireAllAssetsByTypeId(trafficVolumeId)
    }
    println("\nTraffic Volume data Expired")

    println("\nFetch Road Numbers From Viite")
    val roadNumbers = OracleDatabase.withDynSession {
      roadAddressDao.getRoadNumbers()
    }
    println("\nEnd of Fetch ")

    println("roadNumbers: ")
    roadNumbers.foreach(ra => println(ra))

    roadNumbers.foreach {
      case roadNumber =>
        println("\nFetch Traffic Volume by Road Number " + roadNumber)
        val trTrafficVolume = tierekisteriTrafficVolumeAsset.fetchActiveAssetData(roadNumber)

        trTrafficVolume.foreach { tr => println("\nTR: roadNumber, roadPartNumber, start, end and kvt " + tr.roadNumber + " " + tr.startRoadPartNumber + " " + tr.startAddressMValue + " " + tr.endAddressMValue + " " + tr.assetValue) }

        val r = trTrafficVolume.groupBy(trTrafficVolume => (trTrafficVolume.roadNumber, trTrafficVolume.startRoadPartNumber, trTrafficVolume.startAddressMValue, trTrafficVolume.endAddressMValue)).map(_._2.head)

        r.foreach { tr =>
          OracleDatabase.withDynTransaction {

            println("\nFetch road addresses to link ids using Viite, trRoadNumber, roadPartNumber start and end " + tr.roadNumber + " " + tr.startRoadPartNumber + " " + tr.startAddressMValue + " " + tr.endAddressMValue)
            val roadAddresses = roadAddressDao.getRoadAddressesFiltered(tr.roadNumber, tr.startRoadPartNumber, tr.startAddressMValue, tr.endAddressMValue)

            val roadAddressLinks = roadAddresses.map(ra => ra.linkId).toSet
            val vvhRoadlinks = roadLinkService.fetchVVHRoadlinks(roadAddressLinks)

            println("roadAddresses fetched: ")
            roadAddresses.filter(ra => vvhRoadlinks.exists(t => t.linkId == ra.linkId)).foreach(ra => println(ra.linkId))

            roadAddresses
              .filter(ra => vvhRoadlinks.exists(t => t.linkId == ra.linkId))
              .foreach { ra =>
                val assetId = linearAssetService.dao.createLinearAsset(trafficVolumeId, ra.linkId, false, SideCode.BothDirections.value,
                  Measures(ra.startMValue, ra.endMValue), "batch_process_trafficVolume", vvhClient.createVVHTimeStamp(), Some(LinkGeomSource.NormalLinkInterface.value))
                println("\nCreated OTH traffic volume assets form TR data with assetId " + assetId)

                linearAssetService.dao.insertValue(assetId, LinearAssetTypes.numericValuePropertyId, tr.assetValue)
                println("\nCreated OTH property value with value " + tr.assetValue + " and assetId " + assetId)
              }
          }
        }
    }
    println("\nEnd of Traffic Volume fetch")
    println("\nEnd of creation OTH traffic volume assets form TR data")
  }

  val tierekisteriDataImporters = Map[String, TierekisteriAssetImporterOperations](
    "litRoad" -> litRoadImporterOperations,
    "roadWidth" -> roadWidthImporterOperations,
    "trafficSign" -> trafficSignTierekisteriImporter,
    "pavedRoad" -> pavedRoadImporterOperations,
    "massTransitLane" -> massTransitLaneImporterOperations,
    "damagedByThaw" -> damagedByThawImporterOperations,
    "europeanRoad" -> europeanRoadImporterOperations,
    "stateSpeedLimit" -> stateSpeedLimitTierekisteriImporter,
    "speedLimit" -> speedLimitTierekisteriImporter,
    //"winterSpeedLimit" -> winterSpeedLimitImporterOperations,
    "totalWeightLimit" -> totalWeightLimitTierekisteriImporter,
    "trailerTruckWeightLimit" -> trailerTruckWeightLimitTierekisteriImporter,
    "axleWeightLimit" -> axleWeightLimitTierekisteriImporter,
    "bogieWeightLimit" -> bogieWeightLimitTierekisteriImporter,
    "heightLimit" -> heightLimitTierekisteriImporter,
    "widthLimit" -> widthLimitTierekisteriImporter
  )

  private def importAssets(tierekisteriAssetImporter: TierekisteriAssetImporterOperations): Unit = {
    val assetType = tierekisteriAssetImporter.getAssetName

    println()
    println(s"Start $assetType import at: ")
    println(DateTime.now())

    tierekisteriAssetImporter.importAssets()

    println(s"$assetType import complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  private def updateAssets(tierekisteriAssetImporter: TierekisteriAssetImporterOperations, lastExecutionDateOption: Option[DateTime] = None): Unit = {
    val assetType = tierekisteriAssetImporter.getAssetName

    println()
    println(s"Start $assetType update at: ")
    println(DateTime.now())

    val lastExecutionDate = lastExecutionDateOption.
      getOrElse(getLastExecutionDate(tierekisteriAssetImporter).
      getOrElse(throw new Exception("Any last execution, was found")))

    println(s"Last execution date: $lastExecutionDate")

    tierekisteriAssetImporter.updateAssets(lastExecutionDate)

    println(s"$assetType update complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  private def convertStringToDate(dateFormat: String, str: Option[String]): Option[DateTime] = {
    if(str.exists(_.trim.nonEmpty))
      Some(DateTimeFormat.forPattern(dateFormat).parseDateTime(str.get))
    else
      None
  }

  private def getDateFromArgs(args:Array[String]): Option[DateTime] = {
    if(args.size >= 4)
      convertStringToDate("yyyy-MM-dd hh:mm:ss", Some(args(2) + " " + args(3)))
    else if(args.size >= 3)
      convertStringToDate("yyyy-MM-dd", Some(args(2)))
    else
      None
  }

  def main(args:Array[String]) : Unit = {
    import scala.util.control.Breaks._
    val username = properties.getProperty("bonecp.username")
    if (!username.startsWith("dr2dev")) {
      println("*******************************************************************************************")
      println("YOU ARE RUNNING TIEREKISTERI IMPORT AGAINST A NON-DEVELOPER DATABASE, TYPE 'YES' TO PROCEED")
      println("*******************************************************************************************")
      breakable {
        while (true) {
          val input = Console.readLine()
          if (input.trim() == "YES") {
            break()
          }
        }
      }
    }

    if(args.size < 2){
      println("Usage: TierekisteriDataImporter <operation> <assetType> [<args>]")
    }else{
      val operation = args(0)
      val assetType = args(1)

      val availableAssetTypes = tierekisteriDataImporters.keySet ++ Set("trafficVolume")

      if(availableAssetTypes.contains(assetType)){
        operation match {
          case "import" =>
            importAssets(tierekisteriDataImporters.get(assetType).get)
          case "update" =>
            val lastExecutionDate = getDateFromArgs(args)
            updateAssets(tierekisteriDataImporters.get(assetType).get, lastExecutionDate)
        }
      }else{
        println(s"The asset type $assetType is not supported")
        println()
        println("Supported asset types: " + availableAssetTypes.mkString(" | "))
      }
    }
  }
}

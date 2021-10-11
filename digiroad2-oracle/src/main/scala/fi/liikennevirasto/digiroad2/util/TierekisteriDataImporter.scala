package fi.liikennevirasto.digiroad2.util

import java.util.Properties
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode, TrafficVolume}
import fi.liikennevirasto.digiroad2.client.tierekisteri.TierekisteriTrafficVolumeAssetClient
import fi.liikennevirasto.digiroad2.client.tierekisteri.importer._
import fi.liikennevirasto.digiroad2.client.viite.SearchViiteClient
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.PostGISAssetDao
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetService, LinearAssetTypes, Measures}
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import scala.sys.exit

object TierekisteriDataImporter {

  lazy val vvhClient: VVHClient = {
    new VVHClient(Digiroad2Properties.vvhRestApiEndPoint)
  }

  lazy val roadLinkService : RoadLinkService = {
    new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer)
  }

  lazy val linearAssetService: LinearAssetService = {
    new LinearAssetService(roadLinkService, new DummyEventBus)
  }

  lazy val assetDao : PostGISAssetDao = {
    new PostGISAssetDao()
  }

  lazy val postGisLinearAssetDao : PostGISLinearAssetDao = {
    new PostGISLinearAssetDao(vvhClient, roadLinkService)
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

  lazy val trafficSignSpeedLimitTierekisteriImporter: TrafficSignSpeedLimitTierekisteriImporter = {
    new TrafficSignSpeedLimitTierekisteriImporter()
  }

  lazy val trafficSignRegulatorySignsTierekisteriImporter: TrafficSignRegulatorySignsTierekisteriImporter = {
    new TrafficSignRegulatorySignsTierekisteriImporter()
  }

  lazy val trafficSignMaximumRestrictionsTierekisteriImporter: TrafficSignMaximumRestrictionsTierekisteriImporter = {
    new TrafficSignMaximumRestrictionsTierekisteriImporter()
  }

  lazy val trafficSignGeneralWarningSignsTierekisteriImporter: TrafficSignGeneralWarningSignsTierekisteriImporter = {
    new TrafficSignGeneralWarningSignsTierekisteriImporter()
  }

  lazy val trafficSignProhibitionsAndRestrictionsTierekisteriImporter: TrafficSignProhibitionsAndRestrictionsTierekisteriImporter = {
    new TrafficSignProhibitionsAndRestrictionsTierekisteriImporter()
  }

  lazy val trafficSignMandatorySignsTierekisteriImporter: TrafficSignMandatorySignsTierekisteriImporter = {
    new TrafficSignMandatorySignsTierekisteriImporter()
  }

  lazy val trafficSignPriorityAndGiveWaySignsTierekisteriImporter: TrafficSignPriorityAndGiveWaySignsTierekisteriImporter = {
    new TrafficSignPriorityAndGiveWaySignsTierekisteriImporter()
  }

  lazy val trafficSignInformationSignsTierekisteriImporter: TrafficSignInformationSignsTierekisteriImporter = {
    new TrafficSignInformationSignsTierekisteriImporter()
  }

  lazy val trafficSignServiceSignsTierekisteriImporter: TrafficSignServiceSignsTierekisteriImporter = {
    new TrafficSignServiceSignsTierekisteriImporter()
  }
  //TODO remove this code after merge US 1707
  lazy val trafficSignAdditionalPanelsTierekisteriImporter: TrafficSignAdditionalPanelsTierekisteriImporter = {
    new TrafficSignAdditionalPanelsTierekisteriImporter()
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

  lazy val careClassTierekisteriImporter: CareClassTierekisteriImporter = {
    new CareClassTierekisteriImporter()
  }

  lazy val pedestrianCrossingTierekisteriImporter: PedestrianCrossingTierekisteriImporter = {
    new PedestrianCrossingTierekisteriImporter()
  }

  lazy val carryingCapacityTierekisteriImporter: CarryingCapacityTierekisteriImporter = {
    new CarryingCapacityTierekisteriImporter()
  }

  lazy val animalWarningsTierekisteriImporter: AnimalWarningsTierekisteriImporter = {
    new AnimalWarningsTierekisteriImporter()
  }

  lazy val bogieWeightLimitImporter: BogieWeightLimitImporter = {
    new BogieWeightLimitImporter()
  }

  lazy val axleWeightLimitImporter: AxleWeightLimitImporter = {
    new AxleWeightLimitImporter()
  }

  lazy val truckWeightLimitImporter: TruckWeightLimitImporter = {
    new TruckWeightLimitImporter()
  }

  lazy val totalWeightLimitImporter: TotalWeightLimitImporter = {
    new TotalWeightLimitImporter()
  }

  lazy val heightLimitImporter: HeightLimitImporter = {
    new HeightLimitImporter()
  }

  def getLastExecutionDate(tierekisteriAssetImporter: TierekisteriImporterOperations): Option[DateTime] = {
    PostGISDatabase.withDynSession{
      tierekisteriAssetImporter.getLastExecutionDate
    }
  }

  //TODO delete this client after migrate the import asset to TierekisteriImporterOperations
  lazy val tierekisteriTrafficVolumeAssetClient : TierekisteriTrafficVolumeAssetClient = {
    new TierekisteriTrafficVolumeAssetClient(Digiroad2Properties.tierekisteriRestApiEndPoint,
      Digiroad2Properties.tierekisteriEnabled,
      HttpClientBuilder.create().build())
  }

  lazy val viiteClient: SearchViiteClient = {
    new SearchViiteClient(Digiroad2Properties.viiteRestApiEndPoint, HttpClientBuilder.create().build())
  }

  lazy val roadAddressService: RoadAddressService = {
    new RoadAddressService(viiteClient)
  }

  //TODO migrate this import asset to TierekisteriImporterOperations
  def importTrafficVolumeAsset(tierekisteriTrafficVolumeAsset: TierekisteriTrafficVolumeAssetClient) = {
    val trafficVolumeId = TrafficVolume.typeId
    println("\nExpiring Traffic Volume From OTH Database")
    PostGISDatabase.withDynSession {
      postGisLinearAssetDao.expireAllAssetsByTypeId(trafficVolumeId)
    }
    println("\nTraffic Volume data Expired")

    println("\nFetch Road Numbers From Viite")
    val roadNumbers = roadAddressService.getAllRoadNumbers()
    println("\nEnd of Fetch ")

    println("roadNumbers: ")
    roadNumbers.foreach(ra => println(ra))

    roadNumbers.foreach {
      roadNumber =>
        println("\nFetch Traffic Volume by Road Number " + roadNumber)
        val trTrafficVolume = tierekisteriTrafficVolumeAsset.fetchActiveAssetData(roadNumber)

        trTrafficVolume.foreach { tr => println("\nTR: roadNumber, roadPartNumber, start, end and kvt " + tr.roadNumber + " " + tr.startRoadPartNumber + " " + tr.startAddressMValue + " " + tr.endAddressMValue + " " + tr.assetValue) }

        val r = trTrafficVolume.groupBy(trTrafficVolume => (trTrafficVolume.roadNumber, trTrafficVolume.startRoadPartNumber, trTrafficVolume.startAddressMValue, trTrafficVolume.endAddressMValue)).map(_._2.head)

        val allRoadAddresses = roadAddressService.getAllByRoadNumber(roadNumber)

        r.foreach { tr =>
          PostGISDatabase.withDynTransaction {

            println("\nFetch road addresses to link ids using Viite, trRoadNumber, roadPartNumber start and end " + tr.roadNumber + " " + tr.startRoadPartNumber + " " + tr.startAddressMValue + " " + tr.endAddressMValue)
            //This was a direct migration from the where clause on the previous RoadAddressDAO query
            val roadAddresses = allRoadAddresses.filter(ra =>
              (
                (ra.startAddrMValue >= tr.startAddressMValue && ra.endAddrMValue <= tr.endAddressMValue) ||
                (tr.startAddressMValue >= ra.startAddrMValue && tr.startAddressMValue < ra.endAddrMValue) ||
                (tr.endAddressMValue > ra.startAddrMValue && tr.endAddressMValue <= ra.endAddrMValue)
              ) &&
              ra.roadNumber == roadNumber && ra.roadPartNumber == tr.startRoadPartNumber
            )

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

  val tierekisteriDataImporters = Map[String, TierekisteriImporterOperations](
    "litRoad" -> litRoadImporterOperations,
    "roadWidth" -> roadWidthImporterOperations,
    //"trafficSign" -> trafficSignTierekisteriImporter,
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
    "widthLimit" -> widthLimitTierekisteriImporter,
    "careClass" -> careClassTierekisteriImporter,
    "carryingCapacity" -> carryingCapacityTierekisteriImporter,
    "pedestrianCrossing" -> pedestrianCrossingTierekisteriImporter,
    "animalWarnings" -> animalWarningsTierekisteriImporter
  )

  val tierekisteriDataConverter = Map[String, TierekisteriImporterOperations](
    "bogieWeightConverterImporter" -> bogieWeightLimitImporter,
    "axleWeightConverterImporter" -> axleWeightLimitImporter,
    "totalWeightConverterImporter" -> totalWeightLimitImporter,
    "trailerTruckWeightConverterImporter" -> truckWeightLimitImporter,
    "heightLimitConverterImporter" -> heightLimitImporter
  )

  val trafficSignGroup = Map[String, TierekisteriImporterOperations] (
    "SpeedLimits" -> trafficSignSpeedLimitTierekisteriImporter,
    "RegulatorySigns" ->  trafficSignRegulatorySignsTierekisteriImporter,
    "MaximumRestrictions" ->  trafficSignMaximumRestrictionsTierekisteriImporter,
    "GeneralWarningSigns" ->  trafficSignGeneralWarningSignsTierekisteriImporter,
    "ProhibitionsAndRestrictions" ->  trafficSignProhibitionsAndRestrictionsTierekisteriImporter,
    "MandatorySigns" ->  trafficSignMandatorySignsTierekisteriImporter,
    "PriorityAndGiveWaySigns" ->  trafficSignPriorityAndGiveWaySignsTierekisteriImporter,
    "InformationSigns" ->  trafficSignInformationSignsTierekisteriImporter,
    "ServiceSigns" ->  trafficSignServiceSignsTierekisteriImporter,
    "AdditionalPanels" -> trafficSignAdditionalPanelsTierekisteriImporter //TODO remove this line after merge US1707
  )

  private def importAssets(tierekisteriAssetImporter: TierekisteriImporterOperations): Unit = {
    val assetType = tierekisteriAssetImporter.getAssetName

    println()
    println(s"Start $assetType import at: ")
    println(DateTime.now())

    tierekisteriAssetImporter.importAssets()

    println(s"$assetType import complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  private def updateAssets(tierekisteriAssetImporter: TierekisteriImporterOperations, lastExecutionDateOption: Option[DateTime] = None): Unit = {
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
    if(args.length >= 4)
      convertStringToDate("yyyy-MM-dd hh:mm:ss", Some(args(3) + " " + args(4)))
    else if(args.length >= 3)
      convertStringToDate("yyyy-MM-dd", Some(args(3)))
    else
      None
  }

  def main(args:Array[String]) : Unit = {
    val batchMode = Digiroad2Properties.batchMode
    if (!batchMode) {
      println("*******************************************************************************************")
      println("TURN ENV batchMode true TO RUN TIEREKISTERI IMPORT")
      println("*******************************************************************************************")
      exit()
    }

    if(args.length < 2){
      println("Usage: TierekisteriDataImporter <operation> <assetType> [<args>]")
    }else{
      val operation = args(0)
      val assetType = args(1)

      val availableAssetTypes = tierekisteriDataImporters.keySet ++ tierekisteriDataConverter.keySet ++ Set("trafficVolume", "trafficSign")

      if(availableAssetTypes.contains(assetType)){
        operation match {
          case "import" =>
            if(assetType == "trafficVolume")
              importTrafficVolumeAsset(tierekisteriTrafficVolumeAssetClient)
            else if (assetType == "trafficSign"){
             if (args.length == 3 ) args(2) else throw new IllegalArgumentException("Missing Traffic Sign Group")
              importAssets(trafficSignGroup(args(2)))
            } else
              importAssets(tierekisteriDataImporters(assetType))
          case "update" =>
            val lastExecutionDate = getDateFromArgs(args)
            if(assetType == "trafficVolume")
              println("The asset type trafficVolume doesn't support update operation.")
            else if (assetType == "trafficSign") {
              if (args.length == 3) args(2) else throw new IllegalArgumentException("Missing Traffic Sign Group")
                updateAssets(trafficSignGroup(args(2)), lastExecutionDate)
            }else
              updateAssets(tierekisteriDataImporters(assetType), lastExecutionDate)
          case "converter" =>
            importAssets(tierekisteriDataConverter(assetType))
        }
      }else{
        println(s"The asset type $assetType is not supported")
        println()
        println("Supported asset types: " + availableAssetTypes.mkString(" | "))
      }
    }
  }
}

package fi.liikennevirasto.digiroad2

import java.util.Properties
import java.util.concurrent.TimeUnit
import akka.actor.{Actor, ActorSystem, Props}
import fi.liikennevirasto.digiroad2.client.tierekisteri.TierekisteriMassTransitStopClient
import fi.liikennevirasto.digiroad2.client.viite.SearchViiteClient
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.pointasset.OraclePointMassLimitationDao
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MassTransitStopDao, MunicipalityDao}
import fi.liikennevirasto.digiroad2.lane.{LaneFiller, PersistedLane}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.ChangeSet
import fi.liikennevirasto.digiroad2.linearasset.{PersistedLinearAsset, SpeedLimit, UnknownSpeedLimit}
import fi.liikennevirasto.digiroad2.middleware.{CsvDataExporterInfo, CsvDataImporterInfo, DataExportManager, DataImportManager, TrafficSignManager}
import fi.liikennevirasto.digiroad2.municipality.MunicipalityProvider
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.process.{WidthLimitValidator, _}
import fi.liikennevirasto.digiroad2.service._
import fi.liikennevirasto.digiroad2.service.feedback.{FeedbackApplicationService, FeedbackDataService}
import fi.liikennevirasto.digiroad2.service.lane.LaneService
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearTotalWeightLimitService, _}
import fi.liikennevirasto.digiroad2.service.pointasset._
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop._
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.digiroad2.util.{GeometryTransform, JsonSerializer}
import fi.liikennevirasto.digiroad2.vallu.ValluSender
import org.apache.http.impl.client.HttpClientBuilder
import org.slf4j.LoggerFactory
import scala.concurrent.duration.FiniteDuration


class ValluActor(massTransitStopService: MassTransitStopService) extends Actor {
  val municipalityService: MunicipalityService = Digiroad2Context.municipalityService
  def withDynSession[T](f: => T): T = massTransitStopService.withDynSession(f)
  def receive = {
    case (massTransitStop: PersistedMassTransitStop) => persistedAssetChanges(massTransitStop)
    case _                                          => println("received unknown message")
  }

  def persistedAssetChanges(busStop: PersistedMassTransitStop) = {
    withDynSession {
      val municipalityName = municipalityService.getMunicipalityNameByCode(busStop.municipalityCode, newTransaction = false)
      val massTransitStop = MassTransitStopOperations.eventBusMassTransitStop(busStop, municipalityName)
      ValluSender.postToVallu(massTransitStop)
      massTransitStopService.saveIdPrintedOnValluLog(busStop.id)
    }
  }
}

class ValluTerminalActor(massTransitStopService: MassTransitStopService) extends Actor {
  val municipalityService: MunicipalityService = Digiroad2Context.municipalityService
  def withDynSession[T](f: => T): T = massTransitStopService.withDynSession(f)
  def receive = {
    case x: AbstractPublishInfo => persistedAssetChanges(x.asInstanceOf[TerminalPublishInfo])
    case x                                          => println("received unknown message" + x)
  }

  def persistedAssetChanges(terminalPublishInfo: TerminalPublishInfo) = {
    withDynSession {
      val persistedStop = massTransitStopService.getPersistedAssetsByIdsEnriched((terminalPublishInfo.attachedAsset ++ terminalPublishInfo.detachAsset).toSet)

      persistedStop.foreach { busStop =>
        val municipalityName = municipalityService.getMunicipalityNameByCode(busStop.municipalityCode, false)
        val massTransitStop = MassTransitStopOperations.eventBusMassTransitStop(busStop, municipalityName)
        ValluSender.postToVallu(massTransitStop)
        massTransitStopService.saveIdPrintedOnValluLog(busStop.id)
      }
    }
  }
}

class LanesUpdater(laneService: LaneService) extends Actor {
  def receive = {
    case x: LaneFiller.ChangeSet => laneService.updateChangeSet(x )
    case _            => println("LinearAssetUpdater: Received unknown message")
  }
}

class LanesSaveModified[T](laneService: LaneService) extends Actor {
  def receive = {
    case x: Seq[T] => laneService.persistModifiedLinearAssets(x.asInstanceOf[Seq[PersistedLane]])
    case _             => println("laneSaveModified: Received unknown message")
  }
}

class LinearAssetUpdater(linearAssetService: LinearAssetService) extends Actor {
  def receive = {
    case x: ChangeSet => persistLinearAssetChanges(x)
    case _            => println("LinearAssetUpdater: Received unknown message")
  }

  def persistLinearAssetChanges(changeSet: ChangeSet) {
    linearAssetService.updateChangeSet(changeSet)
  }
}

class DataImporter(dataImportManager: DataImportManager) extends Actor {
  def receive = {
    case x: CsvDataImporterInfo => dataImportManager.importer(x)
    case _ => println("DataImporter: Received unknown message")
  }
}

class DataExporter(dataExportManager: DataExportManager) extends Actor {
  def receive = {
    case x: CsvDataExporterInfo => dataExportManager.processExport(x)
    case _ => println("DataImporter: Received unknown message")
  }
}

class DynamicAssetUpdater(dynamicAssetService: DynamicLinearAssetService) extends Actor {
  def receive = {
    case x: ChangeSet => dynamicAssetService.updateChangeSet(x)
    case _            => println("DynamicAssetUpdater: Received unknown message")
  }
}

class RoadWorksAssetUpdater(roadWorkAssetService: RoadWorkService) extends Actor {
  def receive = {
    case x: ChangeSet => roadWorkAssetService.updateChangeSet(x)
    case _            => println("RoadWorksAssetUpdater: Received unknown message")
  }
}

class DamagedByThawUpdater(damagedByThawService: DamagedByThawService) extends Actor {
  def receive = {
    case x: ChangeSet => damagedByThawService.updateChangeSet(x)
    case _            => println("DamagedByThawUpdater: Received unknown message")
  }
}

class ProhibitionUpdater(prohibitionService: ProhibitionService) extends Actor {
  def receive = {
    case x: ChangeSet => prohibitionService.updateChangeSet(x)
    case _            => println("ProhibitionUpdater: Received unknown message")
  }
}

class RoadWidthUpdater(roadWidthService: RoadWidthService) extends Actor {
  def receive = {
    case x: ChangeSet => persistRoadWidthChanges(x)
    case _            => println("RoadWidthUpdater: Received unknown message")
  }

  def persistRoadWidthChanges(changeSet: ChangeSet) {
    roadWidthService.updateChangeSet(changeSet)
  }
}

class LinearAssetSaveProjected[T](linearAssetProvider: LinearAssetService) extends Actor {
  def receive = {
    case x: Seq[T] => linearAssetProvider.persistProjectedLinearAssets(x.asInstanceOf[Seq[PersistedLinearAsset]])
    case _             => println("linearAssetSaveProjected: Received unknown message")
  }
}

class MaintenanceRoadSaveProjected[T](maintenanceRoadProvider: MaintenanceService) extends Actor {
  def receive = {
    case x: Seq[T] => maintenanceRoadProvider.persistProjectedLinearAssets(x.asInstanceOf[Seq[PersistedLinearAsset]])
    case _             => println("maintenanceRoadSaveProjected: Received unknown message")
  }
}

class RoadWidthSaveProjected[T](roadWidthProvider: RoadWidthService) extends Actor {
  def receive = {
    case x: Seq[T] => roadWidthProvider.persistProjectedLinearAssets(x.asInstanceOf[Seq[PersistedLinearAsset]])
    case _             => println("roadWidthSaveProjected: Received unknown message")
  }
}

class PavedRoadSaveProjected[T](pavedRoadProvider: PavedRoadService) extends Actor {
  def receive = {
    case x: Seq[T] => pavedRoadProvider.persistProjectedLinearAssets(x.asInstanceOf[Seq[PersistedLinearAsset]])
    case _             => println("pavedRoadSaveProjected: Received unknown message")
  }
}

class DynamicAssetSaveProjected[T](dynamicAssetProvider: DynamicLinearAssetService) extends Actor {
  def receive = {
    case x: Seq[T] => dynamicAssetProvider.persistProjectedLinearAssets(x.asInstanceOf[Seq[PersistedLinearAsset]])
    case _             => println("dynamicAssetSaveProjected: Received unknown message")
  }
}

class SpeedLimitUpdater[A, B, C](speedLimitProvider: SpeedLimitService) extends Actor {
  def receive = {
    case (affectedLinkIds: Set[A], expiredLinkIds: Seq[A]) => speedLimitProvider.purgeUnknown(affectedLinkIds.asInstanceOf[Set[Long]], expiredLinkIds.asInstanceOf[Seq[Long]])
    case x: Seq[B] => speedLimitProvider.persistUnknown(x.asInstanceOf[Seq[UnknownSpeedLimit]])
    case x: ChangeSet => speedLimitProvider.updateChangeSet(x)
    case _      => println("speedLimitFiller: Received unknown message")
  }
}

class SpeedLimitSaveProjected[T](speedLimitProvider: SpeedLimitService) extends Actor {
  def receive = {
    case x: Seq[T] => speedLimitProvider.persistProjectedLimit(x.asInstanceOf[Seq[SpeedLimit]])
    case _             => println("speedLimitSaveProjected: Received unknown message")
  }
}

class LinkPropertyUpdater(roadLinkService: RoadLinkService) extends Actor {
  def receive = {
    case w: RoadLinkChangeSet => roadLinkService.updateRoadLinkChanges(w)
    case _                    => println("linkPropertyUpdater: Received unknown message")
  }
}

class ProhibitionSaveProjected[T](prohibitionProvider: ProhibitionService) extends Actor {
  def receive = {
    case x: Seq[T] => prohibitionProvider.persistProjectedLinearAssets(x.asInstanceOf[Seq[PersistedLinearAsset]])
    case _ => println("prohibitionSaveProjected: Received unknown message")
  }
}

class HazmatTransportProhibitionValidation(prohibitionValidator: HazmatTransportProhibitionValidator) extends Actor {
  def receive = {
    case x: AssetValidatorInfo => prohibitionValidator.reprocessRelevantTrafficSigns(x.asInstanceOf[AssetValidatorInfo])
    case _ => println("hazmatTransportProhibitionValidator: Received unknown message")
  }
}

class TotalWeightLimitValidation(totalWeightLimitValidator: TotalWeightLimitValidator) extends Actor {
  def receive = {
    case x: AssetValidatorInfo => totalWeightLimitValidator.reprocessRelevantTrafficSigns(x.asInstanceOf[AssetValidatorInfo])
    case _ => println("totalWeightLimitValidator: Received unknown message")
  }
}

class AxleWeightLimitValidation(axleWeightLimitValidator: AxleWeightLimitValidator) extends Actor {
  def receive = {
    case x: AssetValidatorInfo => axleWeightLimitValidator.reprocessRelevantTrafficSigns(x.asInstanceOf[AssetValidatorInfo])
    case _ => println("axleWeightLimitValidator: Received unknown message")
  }
}

class BogieWeightLimitValidation(bogieWeightLimitValidator: BogieWeightLimitValidator) extends Actor {
  def receive = {
    case x: AssetValidatorInfo => bogieWeightLimitValidator.reprocessRelevantTrafficSigns(x.asInstanceOf[AssetValidatorInfo])
    case _ => println("bogieWeightLimitValidator: Received unknown message")
  }
}

class HeightLimitValidation(heightLimitValidator: HeightLimitValidator) extends Actor {
  def receive = {
    case x: AssetValidatorInfo => heightLimitValidator.reprocessRelevantTrafficSigns(x.asInstanceOf[AssetValidatorInfo])
    case _ => println("heightLimitValidator: Received unknown message")
  }
}

class LengthLimitValidation(lengthLimitValidator: LengthLimitValidator) extends Actor {
  def receive = {
    case x: AssetValidatorInfo => lengthLimitValidator.reprocessRelevantTrafficSigns(x.asInstanceOf[AssetValidatorInfo])
    case _ => println("lengthLimitValidator: Received unknown message")
  }
}

class TrailerTruckWeightLimitValidation(trailerTruckWeightLimitValidator: TrailerTruckWeightLimitValidator) extends Actor {
  def receive = {
    case x: AssetValidatorInfo => trailerTruckWeightLimitValidator.reprocessRelevantTrafficSigns(x.asInstanceOf[AssetValidatorInfo])
    case _ => println("trailerTruckWeightLimitValidator: Received unknown message")
  }
}

class WidthLimitValidation(widthLimitValidator: WidthLimitValidator) extends Actor {
  def receive = {
    case x: AssetValidatorInfo => widthLimitValidator.reprocessRelevantTrafficSigns(x.asInstanceOf[AssetValidatorInfo])
    case _ => println("widthLimitValidator: Received unknown message")
  }
}

class ManoeuvreValidation(manoeuvreValidator: ManoeuvreValidator) extends Actor {
  def receive = {
    case x: AssetValidatorInfo => manoeuvreValidator.reprocessRelevantTrafficSigns(x.asInstanceOf[AssetValidatorInfo])
    case _ => println("manoeuvreValidator: Received unknown message")
  }
}

class PedestrianCrossingValidation(pedestrianCrossingValidation: PedestrianCrossingValidator) extends Actor {
  def receive = {
    case x: AssetValidatorInfo => pedestrianCrossingValidation.reprocessRelevantTrafficSigns(x.asInstanceOf[AssetValidatorInfo])
    case _ => println("pedestrianCrossingValidator: Received unknown message")
  }
}

class TrafficSignCreateAssets(trafficSignManager: TrafficSignManager) extends Actor {
  def receive = {
    case x: TrafficSignInfo => trafficSignManager.createAssets(x)
    case _ => println("trafficSignCreateAssets: Received unknown message")
  }
}

class TrafficSignExpireAssets(trafficSignService: TrafficSignService, trafficSignManager: TrafficSignManager) extends Actor {
  def receive = {
    case x: Long => trafficSignService.getPersistedAssetsByIdsWithExpire(Set(x)).headOption match {
      case Some(trafficType) => trafficSignManager.deleteAssets(Seq(trafficType))
      case _ => println("Nonexistent traffic Sign Type")
    }
    case _ => println("trafficSignExpireAssets: Received unknown message")
  }
}

class TrafficSignUpdateAssets(trafficSignService: TrafficSignService, trafficSignManager: TrafficSignManager) extends Actor {
  def receive = {
    case x: TrafficSignInfoUpdate =>
            trafficSignManager.deleteAssets(Seq(x.oldSign))
            trafficSignManager.createAssets(x.newSign)
    case _ => println("trafficSignUpdateAssets: Received unknown message")
  }
}

object Digiroad2Context {
  val logger = LoggerFactory.getLogger(getClass)

  val Digiroad2ServerOriginatedResponseHeader = "Digiroad2-Server-Originated-Response"
  lazy val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }
  lazy val revisionInfo: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/revision.properties"))
    props
  }

  val system = ActorSystem("Digiroad2")

  import system.dispatcher

  system.scheduler.schedule(FiniteDuration(2, TimeUnit.MINUTES), FiniteDuration(1, TimeUnit.MINUTES)) {
    try {
      logger.info("Send feedback scheduler started.")
      applicationFeedback.sendFeedbacks()
    } catch {
      case ex: Exception => logger.error(s"Exception at send feedback: ${ex.getMessage}")
    }
  }

  val vallu = system.actorOf(Props(classOf[ValluActor], massTransitStopService), name = "vallu")
  eventbus.subscribe(vallu, "asset:saved")

  val valluTerminal = system.actorOf(Props(classOf[ValluTerminalActor], massTransitStopService), name = "valluTerminal")
  eventbus.subscribe(valluTerminal, "terminal:saved")

  val importCSVDataUpdater = system.actorOf(Props(classOf[DataImporter], dataImportManager), name = "importCSVDataUpdater")
  eventbus.subscribe(importCSVDataUpdater, "importCSVData")

  val exportCSVDataUpdater = system.actorOf(Props(classOf[DataExporter], dataExportManager), name = "exportCSVDataUpdater")
  eventbus.subscribe(exportCSVDataUpdater, "exportCSVData")

  val linearAssetUpdater = system.actorOf(Props(classOf[LinearAssetUpdater], linearAssetService), name = "linearAssetUpdater")
  eventbus.subscribe(linearAssetUpdater, "linearAssets:update")

  val dynamicAssetUpdater = system.actorOf(Props(classOf[DynamicAssetUpdater], dynamicLinearAssetService), name = "dynamicAssetUpdater")
  eventbus.subscribe(dynamicAssetUpdater, "dynamicAsset:update")

  val roadWorksUpdater = system.actorOf(Props(classOf[RoadWorksAssetUpdater], roadWorkService), name = "roadWorksUpdater")
  eventbus.subscribe(roadWorksUpdater, "roadWorks:update")

  val damagedByThawUpdater = system.actorOf(Props(classOf[DamagedByThawUpdater], damagedByThawService), name = "damagedByThawUpdater")
  eventbus.subscribe(damagedByThawUpdater, "damagedByThaw:update")

  val prohibitionUpdater = system.actorOf(Props(classOf[ProhibitionUpdater], prohibitionService), name = "prohibitionUpdater")
  eventbus.subscribe(prohibitionUpdater, "prohibition:update")

  val linearAssetSaveProjected = system.actorOf(Props(classOf[LinearAssetSaveProjected[PersistedLinearAsset]], linearAssetService), name = "linearAssetSaveProjected")
  eventbus.subscribe(linearAssetSaveProjected, "linearAssets:saveProjectedLinearAssets")

  val maintenanceRoadSaveProjected = system.actorOf(Props(classOf[MaintenanceRoadSaveProjected[PersistedLinearAsset]], maintenanceRoadService), name = "maintenanceRoadSaveProjected")
  eventbus.subscribe(maintenanceRoadSaveProjected, "maintenanceRoads:saveProjectedMaintenanceRoads")

  val roadWidthUpdater = system.actorOf(Props(classOf[RoadWidthUpdater], roadWidthService), name = "roadWidthUpdater")
  eventbus.subscribe(roadWidthUpdater, "roadWidth:update")

  val roadWidthSaveProjected = system.actorOf(Props(classOf[RoadWidthSaveProjected[PersistedLinearAsset]], roadWidthService), name = "roadWidthSaveProjected")
  eventbus.subscribe(roadWidthSaveProjected, "RoadWidth:saveProjectedRoadWidth")

  val pavedRoadSaveProjected = system.actorOf(Props(classOf[PavedRoadSaveProjected[PersistedLinearAsset]], pavedRoadService), name = "pavedRoadSaveProjected")
  eventbus.subscribe(pavedRoadSaveProjected, "pavedRoad:saveProjectedPavedRoad")

  val dynamicAssetSaveProjected = system.actorOf(Props(classOf[DynamicAssetSaveProjected[PersistedLinearAsset]], dynamicLinearAssetService), name = "dynamicAssetSaveProjected")
  eventbus.subscribe(dynamicAssetSaveProjected, "dynamicAsset:saveProjectedAssets")

  val speedLimitSaveProjected = system.actorOf(Props(classOf[SpeedLimitSaveProjected[SpeedLimit]], speedLimitService), name = "speedLimitSaveProjected")
  eventbus.subscribe(speedLimitSaveProjected, "speedLimits:saveProjectedSpeedLimits")

  val speedLimitUpdater = system.actorOf(Props(classOf[SpeedLimitUpdater[Long, UnknownSpeedLimit, ChangeSet]], speedLimitService), name = "speedLimitUpdater")
  eventbus.subscribe(speedLimitUpdater, "speedLimits:purgeUnknownLimits")
  eventbus.subscribe(speedLimitUpdater, "speedLimits:persistUnknownLimits")
  eventbus.subscribe(speedLimitUpdater, "speedLimits:update")

  val linkPropertyUpdater = system.actorOf(Props(classOf[LinkPropertyUpdater], roadLinkService), name = "linkPropertyUpdater")
  eventbus.subscribe(linkPropertyUpdater, "linkProperties:changed")

  val prohibitionSaveProjected = system.actorOf(Props(classOf[ProhibitionSaveProjected[PersistedLinearAsset]], prohibitionService), name = "prohibitionSaveProjected")
  eventbus.subscribe(prohibitionSaveProjected, "prohibition:saveProjectedProhibition")

  val trafficSignExpire = system.actorOf(Props(classOf[TrafficSignExpireAssets], trafficSignService, trafficSignManager), name = "trafficSignExpire")
  eventbus.subscribe(trafficSignExpire, "trafficSign:expire")

  val trafficSignCreate = system.actorOf(Props(classOf[TrafficSignCreateAssets], trafficSignManager), name = "trafficSignCreate")
  eventbus.subscribe(trafficSignCreate, "trafficSign:create")

  val trafficSignUpdate = system.actorOf(Props(classOf[TrafficSignUpdateAssets], trafficSignService, trafficSignManager), name = "trafficSignUpdate")
  eventbus.subscribe(trafficSignUpdate, "trafficSign:update")

  val hazmatTransportProhibitionVerifier = system.actorOf(Props(classOf[HazmatTransportProhibitionValidation], hazmatTransportProhibitionValidator), name = "hazmatTransportProhibitionValidator")
  eventbus.subscribe(hazmatTransportProhibitionVerifier, "hazmatTransportProhibition:Validator")

  val axleWeightLimitVerifier = system.actorOf(Props(classOf[AxleWeightLimitValidation], axleWeightLimitValidator), name = "axleWeightLimitValidator")
  eventbus.subscribe(axleWeightLimitVerifier, "axleWeightLimit:Validator")

  val totalWeightLimitVerifier = system.actorOf(Props(classOf[TotalWeightLimitValidation], totalWeightLimitValidator), name = "totalWeightLimitValidator")
  eventbus.subscribe(totalWeightLimitVerifier, "totalWeightLimit:Validator")

  val bogieWeightLimitVerifier = system.actorOf(Props(classOf[BogieWeightLimitValidation], bogieWeightLimitValidator), name = "bogieWeightLimitValidator")
  eventbus.subscribe(bogieWeightLimitVerifier, "bogieWeightLimit:Validator")

  val heightLimitVerifier = system.actorOf(Props(classOf[HeightLimitValidation], heightLimitValidator), name = "heightLimitValidator")
  eventbus.subscribe(heightLimitVerifier, "heightLimit:Validator")

  val lengthLimitVerifier = system.actorOf(Props(classOf[LengthLimitValidation], lengthLimitValidator), name = "lengthLimitValidator")
  eventbus.subscribe(lengthLimitVerifier, "lengthLimit:Validator")

  val trailerTruckWeightLimitVerifier = system.actorOf(Props(classOf[TrailerTruckWeightLimitValidation], trailerTruckWeightLimitValidator), name = "trailerTruckWeightLimitValidator")
  eventbus.subscribe(trailerTruckWeightLimitVerifier, "trailerTruckWeightLimit:Validator")

  val widthLimitVerifier = system.actorOf(Props(classOf[WidthLimitValidation], widthLimitValidator), name = "widthLimitValidator")
    eventbus.subscribe(widthLimitVerifier, "widthLimit:Validator")

  val manoeuvreVerifier = system.actorOf(Props(classOf[ManoeuvreValidation], manoeuvreValidator), name = "manoeuvreValidator")
  eventbus.subscribe(manoeuvreVerifier, "manoeuvre:Validator")

  val pedestrianCrossingVerifier = system.actorOf(Props(classOf[PedestrianCrossingValidation], pedestrianCrossingValidator), name = "pedestrianCrossingValidator")
  eventbus.subscribe(pedestrianCrossingVerifier, "pedestrianCrossing:Validator")

  val lanesUpdater = system.actorOf(Props(classOf[LanesUpdater], laneService), name = "lanesUpdater")
  eventbus.subscribe(lanesUpdater, "lanes:updater")

  val lanesSaveModified = system.actorOf(Props(classOf[LanesSaveModified[PersistedLane]], laneService), name = "saveModifiedLanes")
  eventbus.subscribe(lanesSaveModified, "lanes:saveModifiedLanes")


  lazy val authenticationTestModeEnabled: Boolean = {
    properties.getProperty("digiroad2.authenticationTestMode", "false").toBoolean
  }

  lazy val assetPropertyService: AssetPropertyService = {
    new AssetPropertyService(eventbus, userProvider, DefaultDatabaseTransaction)
  }

  lazy val linearMassLimitationService: LinearMassLimitationService = {
    new LinearMassLimitationService(roadLinkService, new DynamicLinearAssetDao)
  }

  lazy val speedLimitService: SpeedLimitService = {
    new SpeedLimitService(eventbus, vvhClient, roadLinkService)
  }

  lazy val userProvider: UserProvider = {
    Class.forName(properties.getProperty("digiroad2.userProvider")).newInstance().asInstanceOf[UserProvider]
  }

  lazy val municipalityProvider: MunicipalityProvider = {
    Class.forName(properties.getProperty("digiroad2.municipalityProvider")).newInstance().asInstanceOf[MunicipalityProvider]
  }

  lazy val eventbus: DigiroadEventBus = {
    Class.forName(properties.getProperty("digiroad2.eventBus")).newInstance().asInstanceOf[DigiroadEventBus]
  }

  lazy val vvhClient: VVHClient = {
    new VVHClient(getProperty("digiroad2.VVHRestApiEndPoint"))
  }

  lazy val viiteClient: SearchViiteClient = {
    new SearchViiteClient(getProperty("digiroad2.viiteRestApiEndPoint"), HttpClientBuilder.create().build())
  }

  lazy val linearAssetDao: OracleLinearAssetDao = {
    new OracleLinearAssetDao(vvhClient, roadLinkService)
  }

  lazy val tierekisteriClient: TierekisteriMassTransitStopClient = {
    new TierekisteriMassTransitStopClient(getProperty("digiroad2.tierekisteriRestApiEndPoint"),
      getProperty("digiroad2.tierekisteri.enabled").toBoolean,
      HttpClientBuilder.create().build)
  }

  lazy val roadLinkService: RoadLinkService = {
    new RoadLinkService(vvhClient, eventbus, new JsonSerializer)
  }

  lazy val roadAddressService: RoadAddressService = {
    new RoadAddressService(viiteClient)
  }

  lazy val assetService: AssetService = {
    new AssetService(eventbus)
  }

  lazy val verificationService: VerificationService = {
    new VerificationService(eventbus, roadLinkService)
  }

  lazy val municipalityService: MunicipalityService = {
    new MunicipalityService
  }

  lazy val dynamicLinearAssetService: DynamicLinearAssetService = {
    new DynamicLinearAssetService(roadLinkService, eventbus)
  }

  lazy val linearLengthLimitService: LinearLengthLimitService = {
    new LinearLengthLimitService(roadLinkService, eventbus)
  }

  lazy val linearTotalWeightLimitService: LinearTotalWeightLimitService = {
    new LinearTotalWeightLimitService(roadLinkService, eventbus)
  }

  lazy val linearAxleWeightLimitService: LinearAxleWeightLimitService = {
    new LinearAxleWeightLimitService(roadLinkService, eventbus)
  }

  lazy val linearBogieWeightLimitService: LinearBogieWeightLimitService = {
    new LinearBogieWeightLimitService(roadLinkService, eventbus)
  }

  lazy val linearTrailerTruckWeightLimitService: LinearTrailerTruckWeightLimitService = {
    new LinearTrailerTruckWeightLimitService(roadLinkService, eventbus)
  }

  lazy val linearWidthLimitService: LinearWidthLimitService = {
    new LinearWidthLimitService(roadLinkService, eventbus)
  }

  lazy val linearHeightLimitService: LinearHeightLimitService = {
    new LinearHeightLimitService(roadLinkService, eventbus)
  }

  lazy val trafficSignManager: TrafficSignManager = {
    new TrafficSignManager(manoeuvreService, roadLinkService)
  }

  lazy val damagedByThawService: DamagedByThawService = {
    new DamagedByThawService(roadLinkService, eventbus)
  }

  lazy val userNotificationService: UserNotificationService = {
    new UserNotificationService()
  }

  lazy val revision: String = {
    revisionInfo.getProperty("digiroad2.revision")
  }
  lazy val deploy_date: String = {
    revisionInfo.getProperty("digiroad2.latestDeploy")
  }

  lazy val massTransitStopService: MassTransitStopService = {
    class ProductionMassTransitStopService(val eventbus: DigiroadEventBus, val roadLinkService: RoadLinkService, val roadAddressService: RoadAddressService) extends MassTransitStopService {
      override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
      override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
      override val massTransitStopDao: MassTransitStopDao = new MassTransitStopDao
      override val municipalityDao: MunicipalityDao = new MunicipalityDao
      override val tierekisteriClient: TierekisteriMassTransitStopClient = Digiroad2Context.tierekisteriClient
      override val geometryTransform: GeometryTransform = new GeometryTransform(roadAddressService)
    }
    new ProductionMassTransitStopService(eventbus, roadLinkService, roadAddressService)
  }

  lazy val servicePointStopService: ServicePointStopService = {
    new ServicePointStopService(eventbus)
  }

  lazy val dataImportManager: DataImportManager = {
    new DataImportManager(vvhClient, roadLinkService, eventbus)
  }

  lazy val dataExportManager: DataExportManager = {
    new DataExportManager(roadLinkService, eventbus, userProvider)
  }

  lazy val maintenanceRoadService: MaintenanceService = {
    new MaintenanceService(roadLinkService, eventbus)
  }

  lazy val pavedRoadService: PavedRoadService = {
    new PavedRoadService(roadLinkService, eventbus)
  }

  lazy val roadWidthService: RoadWidthService = {
    new RoadWidthService(roadLinkService, eventbus)
  }

  lazy val linearAssetService: LinearAssetService = {
    new LinearAssetService(roadLinkService, eventbus)
  }

  lazy val onOffLinearAssetService: OnOffLinearAssetService = {
    new OnOffLinearAssetService(roadLinkService, eventbus)
  }

  lazy val prohibitionService: ProhibitionService = {
    new ProhibitionService(roadLinkService, eventbus)
  }

  lazy val hazmatTransportProhibitionService: HazmatTransportProhibitionService = {
    new HazmatTransportProhibitionService(roadLinkService, eventbus)
  }

  lazy val textValueLinearAssetService: TextValueLinearAssetService = {
    new TextValueLinearAssetService(roadLinkService, eventbus)
  }

  lazy val numericValueLinearAssetService: NumericValueLinearAssetService = {
    new NumericValueLinearAssetService(roadLinkService, eventbus)
  }

  lazy val pedestrianCrossingService: PedestrianCrossingService = {
    new PedestrianCrossingService(roadLinkService, eventbus)
  }

  lazy val trafficLightService: TrafficLightService = {
    new TrafficLightService(roadLinkService)
  }

  lazy val obstacleService: ObstacleService = {
    new ObstacleService(roadLinkService)
  }

  lazy val railwayCrossingService: RailwayCrossingService = {
    new RailwayCrossingService(roadLinkService)
  }

  lazy val directionalTrafficSignService: DirectionalTrafficSignService = {
    new DirectionalTrafficSignService(roadLinkService)
  }

  lazy val trafficSignService: TrafficSignService = {
    new TrafficSignService(roadLinkService, eventbus)
  }

  lazy val manoeuvreService = {
    new ManoeuvreService(roadLinkService, eventbus)
  }

  lazy val weightLimitService: WeightLimitService = {
    new TotalWeightLimitService(roadLinkService)
  }

  lazy val axleWeightLimitService: AxleWeightLimitService = {
    new AxleWeightLimitService(roadLinkService)
  }

  lazy val bogieWeightLimitService: BogieWeightLimitService = {
    new BogieWeightLimitService(roadLinkService)
  }

  lazy val trailerTruckWeightLimitService: TrailerTruckWeightLimitService = {
    new TrailerTruckWeightLimitService(roadLinkService)
  }

  lazy val heightLimitService: HeightLimitService = {
    new HeightLimitService(roadLinkService)
  }

  lazy val widthLimitService: WidthLimitService = {
    new WidthLimitService(roadLinkService)
  }

  lazy val pointMassLimitationService: PointMassLimitationService = {
    new PointMassLimitationService(roadLinkService, new OraclePointMassLimitationDao)
  }

  lazy val servicePointService: ServicePointService = new ServicePointService()

  lazy val massTransitLaneService: MassTransitLaneService = {
    new MassTransitLaneService(roadLinkService, eventbus)
  }

  lazy val numberOfLanesService: NumberOfLanesService = {
    new NumberOfLanesService(roadLinkService, eventbus)
  }

  lazy val roadWorkService: RoadWorkService = {
    new RoadWorkService(roadLinkService, eventbus)
  }

  lazy val parkingProhibitionService: ParkingProhibitionService = {
    new ParkingProhibitionService(roadLinkService, eventbus)
  }

  lazy val cyclingAndWalkingService: CyclingAndWalkingService = {
    new CyclingAndWalkingService(roadLinkService, eventbus)
  }

  lazy val laneService: LaneService = {
    new LaneService(roadLinkService, eventbus)
  }

  lazy val applicationFeedback : FeedbackApplicationService = new FeedbackApplicationService()

  lazy val dataFeedback : FeedbackDataService = new FeedbackDataService()

  lazy val hazmatTransportProhibitionValidator: HazmatTransportProhibitionValidator = {
    new HazmatTransportProhibitionValidator()
  }

  lazy val axleWeightLimitValidator: AxleWeightLimitValidator = {
    new AxleWeightLimitValidator()
  }

  lazy val totalWeightLimitValidator: TotalWeightLimitValidator = {
    new TotalWeightLimitValidator()
  }

  lazy val bogieWeightLimitValidator: BogieWeightLimitValidator = {
    new BogieWeightLimitValidator()
  }

  lazy val heightLimitValidator: HeightLimitValidator = {
    new HeightLimitValidator()
  }

  lazy val lengthLimitValidator: LengthLimitValidator = {
    new LengthLimitValidator()
  }

  lazy val trailerTruckWeightLimitValidator: TrailerTruckWeightLimitValidator = {
    new TrailerTruckWeightLimitValidator()
  }

  lazy val widthLimitValidator: WidthLimitValidator = {
    new WidthLimitValidator()
  }

  lazy val manoeuvreValidator: ManoeuvreValidator = {
    new ManoeuvreValidator()
  }

  lazy val pedestrianCrossingValidator: PedestrianCrossingValidator = {
    new PedestrianCrossingValidator()
  }
  lazy val lengthOfRoadAxisService: LengthOfRoadAxisService = {
    new LengthOfRoadAxisService(roadLinkService, eventbus)
  }
  val env = System.getProperty("env")
  def getProperty(name: String) = {
    val property = properties.getProperty(name)
    if(property != null)
      property
    else
      throw new RuntimeException(s"cannot find property $name for enviroment: $env")
  }
}
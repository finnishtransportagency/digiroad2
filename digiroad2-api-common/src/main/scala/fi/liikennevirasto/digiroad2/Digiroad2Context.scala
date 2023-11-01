package fi.liikennevirasto.digiroad2

import akka.actor.{Actor, ActorSystem, Props}
import fi.liikennevirasto.digiroad2.asset.{HeightLimit => HeightLimitInfo, WidthLimit => WidthLimitInfo, _}
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.client.viite.SearchViiteClient
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.pointasset.PostGISPointMassLimitationDao
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MassTransitStopDao, MunicipalityDao}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.ChangeSet
import fi.liikennevirasto.digiroad2.linearasset.UnknownSpeedLimit
import fi.liikennevirasto.digiroad2.middleware._
import fi.liikennevirasto.digiroad2.municipality.MunicipalityProvider
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.process._
import fi.liikennevirasto.digiroad2.service.{AssetsOnExpiredLinksService, _}
import fi.liikennevirasto.digiroad2.service.feedback.{FeedbackApplicationService, FeedbackDataService}
import fi.liikennevirasto.digiroad2.service.lane.{LaneService, LaneWorkListService}
import fi.liikennevirasto.digiroad2.service.linearasset.{SpeedLimitService, _}
import fi.liikennevirasto.digiroad2.service.pointasset._
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop._
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.digiroad2.util.{Digiroad2Properties, GeometryTransform, JsonSerializer}
import fi.liikennevirasto.digiroad2.vallu.{ValluSender, ValluStoreStopChangeMessage}
import org.apache.http.client.config.{CookieSpecs, RequestConfig}
import org.apache.http.impl.client.{CloseableHttpClient, HttpClientBuilder}
import org.slf4j.LoggerFactory

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration


class ValluActor(massTransitStopService: MassTransitStopService) extends Actor {
  val municipalityService: MunicipalityService = Digiroad2Context.municipalityService
  def withDynSession[T](f: => T): T = massTransitStopService.withDynSession(f)
  def receive = {
    case (massTransitStop: PersistedMassTransitStop) => persistedAssetChanges(massTransitStop)
    case (massTransitStop: PersistedMassTransitStop,deleteEvent:Boolean) => persistedAssetChanges(massTransitStop,deleteEvent)
    case _                                          => println("received unknown message")
  }

  def persistedAssetChanges(busStop: PersistedMassTransitStop,deleteEvent:Boolean = false) = {
    withDynSession {
      val busStopTypes = ValluStoreStopChangeMessage.getPropertyValuesByPublicId("pysakin_tyyppi", busStop.propertyData).map(x => x.propertyValue.toLong)
      val justTram = busStopTypes.size == 1 && busStopTypes.contains(1)
      if (!justTram) {
        val municipalityName = municipalityService.getMunicipalityNameByCode(busStop.municipalityCode, newTransaction = false)
        val massTransitStop = MassTransitStopOperations.eventBusMassTransitStop(busStop, municipalityName)
        ValluSender.postToVallu(massTransitStop)
        if (!deleteEvent)
          massTransitStopService.saveIdPrintedOnValluLog(busStop.id)
      }
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


class SpeedLimitUpdater[A, B, C](speedLimitProvider: SpeedLimitService) extends Actor {
  def receive = {
    case (affectedLinkIds: Set[A], expiredLinkIds: Seq[A]) => speedLimitProvider.purgeUnknown(affectedLinkIds.asInstanceOf[Set[String]], expiredLinkIds.asInstanceOf[Seq[String]])
    case _ => println("speedLimitFiller: Received unknown message")
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

class LaneWorkListInsertItem(laneWorkListService: LaneWorkListService) extends Actor {
  def receive = {
    case linkPropertyChange: LinkPropertyChange =>
      laneWorkListService.insertToLaneWorkList(linkPropertyChange)
    case _ => println("LaneWorkListInsertItem: Received unknown message")
  }
}

class AssetUpdater(linearAssetService: LinearAssetService) extends Actor {
  val logger = LoggerFactory.getLogger(getClass)
  def receive: Receive = {
    case a: AssetUpdateActor =>
      lazy val eventbus = new DummyEventBus
      lazy val roadLinkService: RoadLinkService = {
        new RoadLinkService(new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint), eventbus, new JsonSerializer)
      }
      def getLinearAssetService(typeId: Int): LinearAssetOperations = {
        typeId match {
          case MaintenanceRoadAsset.typeId => new MaintenanceService(roadLinkService, eventbus)
          case PavedRoad.typeId => new PavedRoadService(roadLinkService, eventbus)
          case RoadWidth.typeId => new RoadWidthService(roadLinkService, eventbus)
          case Prohibition.typeId => new ProhibitionService(roadLinkService, eventbus)
          case HazmatTransportProhibition.typeId => new HazmatTransportProhibitionService(roadLinkService, eventbus)
          case EuropeanRoads.typeId | ExitNumbers.typeId => new TextValueLinearAssetService(roadLinkService, eventbus)
          case CareClass.typeId | CarryingCapacity.typeId | LitRoad.typeId => new DynamicLinearAssetService(roadLinkService, eventbus)
          case HeightLimitInfo.typeId => new LinearHeightLimitService(roadLinkService, eventbus)
          case LengthLimit.typeId => new LinearLengthLimitService(roadLinkService, eventbus)
          case WidthLimitInfo.typeId => new LinearWidthLimitService(roadLinkService, eventbus)
          case TotalWeightLimit.typeId => new LinearTotalWeightLimitService(roadLinkService, eventbus)
          case TrailerTruckWeightLimit.typeId => new LinearTrailerTruckWeightLimitService(roadLinkService, eventbus)
          case AxleWeightLimit.typeId => new LinearAxleWeightLimitService(roadLinkService, eventbus)
          case BogieWeightLimit.typeId => new LinearBogieWeightLimitService(roadLinkService, eventbus)
          case MassTransitLane.typeId => new MassTransitLaneService(roadLinkService, eventbus)
          case NumberOfLanes.typeId => new NumberOfLanesService(roadLinkService, eventbus)
          case DamagedByThaw.typeId =>  new DamagedByThawService(roadLinkService, eventbus)
          case RoadWorksAsset.typeId => new RoadWorkService(roadLinkService, eventbus)
          case ParkingProhibition.typeId => new ParkingProhibitionService(roadLinkService, eventbus)
          case CyclingAndWalking.typeId => new CyclingAndWalkingService(roadLinkService, eventbus)
          case SpeedLimitAsset.typeId => new SpeedLimitService(eventbus,roadLinkService)
          case _ => linearAssetService
        }
      }
      if (a.roadLinkUpdate) getLinearAssetService(a.typeId).adjustLinearAssetsAction(a.linksIds,a.typeId, adjustSideCode = true)
      else getLinearAssetService(a.typeId).adjustLinearAssetsAction(a.linksIds,a.typeId)
    case _ => logger.info("AssetUpdater: Received unknown message")
  }
}

object Digiroad2Context {
  val logger = LoggerFactory.getLogger(getClass)

  val Digiroad2ServerOriginatedResponseHeader = "Digiroad2-Server-Originated-Response"

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
  eventbus.subscribe(vallu, "asset:expired") // vallu message for expired/deleted bus stop
  val valluTerminal = system.actorOf(Props(classOf[ValluTerminalActor], massTransitStopService), name = "valluTerminal")
  eventbus.subscribe(valluTerminal, "terminal:saved")

  val importCSVDataUpdater = system.actorOf(Props(classOf[DataImporter], dataImportManager), name = "importCSVDataUpdater")
  eventbus.subscribe(importCSVDataUpdater, "importCSVData")

  val exportCSVDataUpdater = system.actorOf(Props(classOf[DataExporter], dataExportManager), name = "exportCSVDataUpdater")
  eventbus.subscribe(exportCSVDataUpdater, "exportCSVData")

  val speedLimitUpdater = system.actorOf(Props(classOf[SpeedLimitUpdater[Long, UnknownSpeedLimit, ChangeSet]], speedLimitService), name = "speedLimitUpdater")
  eventbus.subscribe(speedLimitUpdater, "speedLimits:purgeUnknownLimits")

  val trafficSignExpire = system.actorOf(Props(classOf[TrafficSignExpireAssets], trafficSignService, trafficSignManager), name = "trafficSignExpire")
  eventbus.subscribe(trafficSignExpire, "trafficSign:expire")

  val trafficSignCreate = system.actorOf(Props(classOf[TrafficSignCreateAssets], trafficSignManager), name = "trafficSignCreate")
  eventbus.subscribe(trafficSignCreate, "trafficSign:create")

  val trafficSignUpdate = system.actorOf(Props(classOf[TrafficSignUpdateAssets], trafficSignService, trafficSignManager), name = "trafficSignUpdate")
  eventbus.subscribe(trafficSignUpdate, "trafficSign:update")

  val laneWorkListInsert = system.actorOf(Props(classOf[LaneWorkListInsertItem], laneWorkListService), name = "laneWorkListInsert")
  eventbus.subscribe(laneWorkListInsert, "laneWorkList:insert")

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

  val assetUpdater = system.actorOf(Props(classOf[AssetUpdater], linearAssetService), name = "linearAssetUpdater")
  eventbus.subscribe(assetUpdater, "linearAssetUpdater")

  lazy val authenticationTestModeEnabled: Boolean = {
    Digiroad2Properties.authenticationTestMode
  }
  private def clientBuilder(maxConnTotal: Int = 1000,
                            maxConnPerRoute: Int = 1000,
                            timeout:Int = 60*1000
                           ): CloseableHttpClient = {
    HttpClientBuilder.create()
      .setDefaultRequestConfig(
        RequestConfig.custom()
          .setCookieSpec(CookieSpecs.STANDARD)
          .setSocketTimeout(timeout)
          .setConnectTimeout(timeout) 
          .build()
      )
      .setMaxConnTotal(maxConnTotal)
      .setMaxConnPerRoute(maxConnPerRoute)
      .build()
  }

  lazy val assetPropertyService: AssetPropertyService = {
    new AssetPropertyService(eventbus, userProvider, DefaultDatabaseTransaction)
  }

  lazy val linearMassLimitationService: LinearMassLimitationService = {
    new LinearMassLimitationService(roadLinkService, new DynamicLinearAssetDao)
  }

  lazy val speedLimitService: SpeedLimitService = {
    new SpeedLimitService(eventbus, roadLinkService)
  }

  lazy val userProvider: UserProvider = {
    Class.forName(Digiroad2Properties.userProvider).newInstance().asInstanceOf[UserProvider]
  }

  lazy val municipalityProvider: MunicipalityProvider = {
    Class.forName(Digiroad2Properties.municipalityProvider).newInstance().asInstanceOf[MunicipalityProvider]
  }

  lazy val eventbus: DigiroadEventBus = {
    Class.forName(Digiroad2Properties.eventBus).newInstance().asInstanceOf[DigiroadEventBus]
  }

  lazy val roadLinkClient: RoadLinkClient = {
    new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
  }

  lazy val viiteClient: SearchViiteClient = {
    new SearchViiteClient(Digiroad2Properties.viiteRestApiEndPoint, clientBuilder(
      10000,10000))
  }
  
  lazy val linearAssetDao: PostGISLinearAssetDao = {
    new PostGISLinearAssetDao()
  }

  lazy val roadLinkService: RoadLinkService = {
    new RoadLinkService(roadLinkClient, eventbus, new JsonSerializer)
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

  lazy val massTransitStopService: MassTransitStopService = {
    class ProductionMassTransitStopService(val eventbus: DigiroadEventBus, val roadLinkService: RoadLinkService, val roadAddressService: RoadAddressService) extends MassTransitStopService {
      override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
      override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
      override val massTransitStopDao: MassTransitStopDao = new MassTransitStopDao
      override val municipalityDao: MunicipalityDao = new MunicipalityDao
      override val geometryTransform: GeometryTransform = new GeometryTransform(roadAddressService)
    }
    new ProductionMassTransitStopService(eventbus, roadLinkService, roadAddressService)
  }

  lazy val servicePointStopService: ServicePointStopService = {
    new ServicePointStopService(eventbus)
  }

  lazy val dataImportManager: DataImportManager = {
    new DataImportManager(roadLinkClient, roadLinkService, eventbus)
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

  lazy val laneWorkListService: LaneWorkListService = {
    new LaneWorkListService()
  }

  lazy val assetsOnExpiredLinksService: AssetsOnExpiredLinksService = {
    new AssetsOnExpiredLinksService()
  }

  lazy val municipalityAssetMappingService: MunicipalityAssetMappingService = {
    new MunicipalityAssetMappingService()
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
    new PointMassLimitationService(roadLinkService, new PostGISPointMassLimitationDao)
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
    new LaneService(roadLinkService, eventbus, roadAddressService)
  }

  lazy val awsService: AwsService = {
    new AwsService()
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

}
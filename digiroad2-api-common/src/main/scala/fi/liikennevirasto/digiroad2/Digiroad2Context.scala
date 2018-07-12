package fi.liikennevirasto.digiroad2

import java.util.Properties
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorSystem, Props}
import fi.liikennevirasto.digiroad2.client.tierekisteri.TierekisteriMassTransitStopClient
import fi.liikennevirasto.digiroad2.client.viite.SearchViiteClient
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.{MassLimitationDao, MassTransitStopDao, MunicipalityDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.pointasset.OraclePointMassLimitationDao
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.ChangeSet
import fi.liikennevirasto.digiroad2.linearasset.{PersistedLinearAsset, SpeedLimit, UnknownSpeedLimit}
import fi.liikennevirasto.digiroad2.municipality.MunicipalityProvider
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service._
import fi.liikennevirasto.digiroad2.service.feedback.{FeedbackOperations, FeedbackApplicationService, FeedbackDataService}
import fi.liikennevirasto.digiroad2.service.linearasset._
import fi.liikennevirasto.digiroad2.service.pointasset._
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop._
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.digiroad2.util.{GeometryTransform, JsonSerializer}
import fi.liikennevirasto.digiroad2.vallu.ValluSender
import org.apache.http.impl.client.HttpClientBuilder
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration

class ValluActor(massTransitStopService: MassTransitStopService) extends Actor {
  def withDynSession[T](f: => T): T = massTransitStopService.withDynSession(f)
  def receive = {
    case (massTransitStop: PersistedMassTransitStop) => persistedAssetChanges(massTransitStop)
    case _                                          => println("received unknown message")
  }

  def persistedAssetChanges(busStop: PersistedMassTransitStop) = {
    withDynSession {
      val municipalityName = massTransitStopService.massTransitStopDao.getMunicipalityNameByCode(busStop.municipalityCode)
      val massTransitStop = MassTransitStopOperations.eventBusMassTransitStop(busStop, municipalityName)
      ValluSender.postToVallu(massTransitStop)
    }
  }
}

class ValluTerminalActor(massTransitStopService: MassTransitStopService) extends Actor {
  def withDynSession[T](f: => T): T = massTransitStopService.withDynSession(f)
  def receive = {
    case x: AbstractPublishInfo => persistedAssetChanges(x.asInstanceOf[TerminalPublishInfo])
    case x                                          => println("received unknown message" + x)
  }

  def persistedAssetChanges(terminalPublishInfo: TerminalPublishInfo) = {
    withDynSession {
    val persistedStop = massTransitStopService.getPersistedAssetsByIdsEnriched((terminalPublishInfo.attachedAsset++terminalPublishInfo.detachAsset).toSet)

    persistedStop.foreach { busStop =>
        val municipalityName = massTransitStopService.massTransitStopDao.getMunicipalityNameByCode(busStop.municipalityCode)
        val massTransitStop = MassTransitStopOperations.eventBusMassTransitStop(busStop, municipalityName)
        ValluSender.postToVallu(massTransitStop)
      }
    }
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

class PavingSaveProjected[T](pavingProvider: PavingService) extends Actor {
  def receive = {
    case x: Seq[T] => pavingProvider.persistProjectedLinearAssets(x.asInstanceOf[Seq[PersistedLinearAsset]])
    case _             => println("pavingSaveProjected: Received unknown message")
  }
}

class SpeedLimitUpdater[A, B](speedLimitProvider: SpeedLimitService) extends Actor {
  def receive = {
    case x: Set[A] => speedLimitProvider.purgeUnknown(x.asInstanceOf[Set[Long]])
    case x: Seq[B] => speedLimitProvider.persistUnknown(x.asInstanceOf[Seq[UnknownSpeedLimit]])
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

  val linearAssetUpdater = system.actorOf(Props(classOf[LinearAssetUpdater], linearAssetService), name = "linearAssetUpdater")
  eventbus.subscribe(linearAssetUpdater, "linearAssets:update")

  val linearAssetSaveProjected = system.actorOf(Props(classOf[LinearAssetSaveProjected[PersistedLinearAsset]], linearAssetService), name = "linearAssetSaveProjected")
  eventbus.subscribe(linearAssetSaveProjected, "linearAssets:saveProjectedLinearAssets")

  val maintenanceRoadSaveProjected = system.actorOf(Props(classOf[MaintenanceRoadSaveProjected[PersistedLinearAsset]], maintenanceRoadService), name = "maintenanceRoadSaveProjected")
  eventbus.subscribe(maintenanceRoadSaveProjected, "maintenanceRoads:saveProjectedMaintenanceRoads")

  val roadWidthUpdater = system.actorOf(Props(classOf[RoadWidthUpdater], roadWidthService), name = "roadWidthUpdater")
  eventbus.subscribe(roadWidthUpdater, "roadWidth:update")

  val roadWidthSaveProjected = system.actorOf(Props(classOf[RoadWidthSaveProjected[PersistedLinearAsset]], roadWidthService), name = "roadWidthSaveProjected")
  eventbus.subscribe(roadWidthSaveProjected, "RoadWidth:saveProjectedRoadWidth")

  val pavingSaveProjected = system.actorOf(Props(classOf[PavingSaveProjected[PersistedLinearAsset]], pavingService), name = "pavingSaveProjected")
  eventbus.subscribe(pavingSaveProjected, "paving:saveProjectedPaving")

  val speedLimitSaveProjected = system.actorOf(Props(classOf[SpeedLimitSaveProjected[SpeedLimit]], speedLimitService), name = "speedLimitSaveProjected")
  eventbus.subscribe(speedLimitSaveProjected, "speedLimits:saveProjectedSpeedLimits")

  val speedLimitUpdater = system.actorOf(Props(classOf[SpeedLimitUpdater[Long, UnknownSpeedLimit]], speedLimitService), name = "speedLimitUpdater")
  eventbus.subscribe(speedLimitUpdater, "speedLimits:purgeUnknownLimits")
  eventbus.subscribe(speedLimitUpdater, "speedLimits:persistUnknownLimits")

  val linkPropertyUpdater = system.actorOf(Props(classOf[LinkPropertyUpdater], roadLinkService), name = "linkPropertyUpdater")
  eventbus.subscribe(linkPropertyUpdater, "linkProperties:changed")

  val prohibitionSaveProjected = system.actorOf(Props(classOf[ProhibitionSaveProjected[PersistedLinearAsset]], prohibitionService), name = "prohibitionSaveProjected")
  eventbus.subscribe(prohibitionSaveProjected, "prohibition:saveProjectedProhibition")

  lazy val authenticationTestModeEnabled: Boolean = {
    properties.getProperty("digiroad2.authenticationTestMode", "false").toBoolean
  }

  lazy val assetPropertyService: AssetPropertyService = {
    new AssetPropertyService(eventbus, userProvider, DefaultDatabaseTransaction)
  }

  lazy val linearMassLimitationService: LinearMassLimitationService = {
    new LinearMassLimitationService(roadLinkService, new MassLimitationDao)
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

  lazy val roadAddressesService: RoadAddressesService = {
    new RoadAddressesService(viiteClient)
  }

  lazy val assetService: AssetService = {
    new AssetService(eventbus)
  }

  lazy val verificationService: VerificationService = {
    new VerificationService(eventbus, roadLinkService)
  }

  lazy val municipalityService: MunicipalityService = {
    new MunicipalityService(eventbus, roadLinkService)
  }

  lazy val dynamicLinearAssetService: DynamicLinearAssetService = {
    new DynamicLinearAssetService(roadLinkService, eventbus)
  }

  lazy val revision: String = {
    revisionInfo.getProperty("digiroad2.revision")
  }
  lazy val deploy_date: String = {
    revisionInfo.getProperty("digiroad2.latestDeploy")
  }

  lazy val massTransitStopService: MassTransitStopService = {
    class ProductionMassTransitStopService(val eventbus: DigiroadEventBus, val roadLinkService: RoadLinkService, val roadAddressService: RoadAddressesService) extends MassTransitStopService {
      override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
      override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
      override val massTransitStopDao: MassTransitStopDao = new MassTransitStopDao
      override val municipalityDao: MunicipalityDao = new MunicipalityDao
      override val tierekisteriClient: TierekisteriMassTransitStopClient = Digiroad2Context.tierekisteriClient
      override val geometryTransform: GeometryTransform = new GeometryTransform(roadAddressService)
    }
    new ProductionMassTransitStopService(eventbus, roadLinkService, roadAddressesService)
  }

  lazy val maintenanceRoadService: MaintenanceService = {
    new MaintenanceService(roadLinkService, eventbus)
  }

  lazy val pavingService: PavingService = {
    new PavingService(roadLinkService, eventbus)
  }

  lazy val roadWidthService: RoadWidthService = {
    new RoadWidthService(roadLinkService, eventbus)
  }

  lazy val linearAssetService: LinearAssetService = {
    new LinearAssetService(roadLinkService, eventbus)
  }

  lazy val onOffLinearAssetService: OnOffLinearAssetService = {
    new OnOffLinearAssetService(roadLinkService, eventbus, linearAssetDao)
  }

  lazy val prohibitionService: ProhibitionService = {
    new ProhibitionService(roadLinkService, eventbus)
  }

  lazy val textValueLinearAssetService: TextValueLinearAssetService = {
    new TextValueLinearAssetService(roadLinkService, eventbus)
  }

  lazy val numericValueLinearAssetService: NumericValueLinearAssetService = {
    new NumericValueLinearAssetService(roadLinkService, eventbus)
  }

  lazy val pedestrianCrossingService: PedestrianCrossingService = {
    new PedestrianCrossingService(roadLinkService)
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
    new TrafficSignService(roadLinkService, userProvider)
  }

  lazy val manoeuvreService = {
    new ManoeuvreService(roadLinkService)
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

  lazy val applicationFeedback : FeedbackApplicationService = new FeedbackApplicationService()

  lazy val dataFeedback : FeedbackDataService = new FeedbackDataService()

  val env = System.getProperty("env")
  def getProperty(name: String) = {
    val property = properties.getProperty(name)
    if(property != null)
      property
    else
      throw new RuntimeException(s"cannot find property $name for enviroment: $env")
  }
}
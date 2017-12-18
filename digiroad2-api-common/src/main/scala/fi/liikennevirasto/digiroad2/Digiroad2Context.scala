package fi.liikennevirasto.digiroad2

import java.util.Properties
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorSystem, Props}
import fi.liikennevirasto.digiroad2.client.tierekisteri.TierekisteriMassTransitStopClient
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.{MassTransitStopDao, MunicipalityDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.ChangeSet
import fi.liikennevirasto.digiroad2.linearasset.{PersistedLinearAsset, SpeedLimit, UnknownSpeedLimit}
import fi.liikennevirasto.digiroad2.municipality.MunicipalityProvider
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service._
import fi.liikennevirasto.digiroad2.service.linearasset._
import fi.liikennevirasto.digiroad2.service.pointasset._
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.MassTransitStopService
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.digiroad2.util.JsonSerializer
import fi.liikennevirasto.digiroad2.vallu.ValluSender
import fi.liikennevirasto.viite.dao.MissingRoadAddress
import fi.liikennevirasto.viite.process.RoadAddressFiller.LRMValueAdjustment
import fi.liikennevirasto.viite.{ProjectService, RoadAddressMerge, RoadAddressService}
import org.apache.http.impl.client.HttpClientBuilder

import scala.concurrent.duration.FiniteDuration

class ValluActor extends Actor {
  def receive = {
    case (massTransitStop: EventBusMassTransitStop) => ValluSender.postToVallu(massTransitStop)
    case _                                          => println("received unknown message")
  }
}

class LinearAssetUpdater(linearAssetService: LinearAssetService) extends Actor {
  def receive = {
    case x: ChangeSet => persistLinearAssetChanges(x)
    case _            => println("LinearAssetUpdater: Received unknown message")
  }

  def persistLinearAssetChanges(changeSet: ChangeSet) {
    //TODO just for test propose
    /*
    linearAssetService.drop(changeSet.droppedAssetIds)
    linearAssetService.persistMValueAdjustments(changeSet.adjustedMValues)
    linearAssetService.persistSideCodeAdjustments(changeSet.adjustedSideCodes)
    linearAssetService.expire(changeSet.expiredAssetIds.toSeq, LinearAssetTypes.VvhGenerated)
    */
    linearAssetService.updateChangeSet(changeSet);
  }
}

class RoadWidthUpdater(roadWidthService: RoadWidthService) extends Actor {
  def receive = {
    case x: ChangeSet => persistRoadWidthChanges(x)
    case _            => println("RoadWidthUpdater: Received unknown message")
  }

  def persistRoadWidthChanges(changeSet: ChangeSet) {
    roadWidthService.updateChangeSet(changeSet);
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

class RoadAddressUpdater(roadAddressService: RoadAddressService) extends Actor {
  def receive = {
    case w: Seq[any] => roadAddressService.createMissingRoadAddress(w.asInstanceOf[Seq[MissingRoadAddress]])
    case _                    => println("roadAddressUpdater: Received unknown message")
  }
}

class RoadAddressMerger(roadAddressService: RoadAddressService) extends Actor {
  def receive = {
    case w: RoadAddressMerge => roadAddressService.mergeRoadAddress(w.asInstanceOf[RoadAddressMerge])
    case _                    => println("roadAddressMerger: Received unknown message")
  }
}

class RoadAddressAdjustment(roadAddressService: RoadAddressService) extends Actor {
  def receive = {
    case w: Seq[any] => roadAddressService.saveAdjustments(w.asInstanceOf[Seq[LRMValueAdjustment]])
    case _                    => println("roadAddressUpdater: Received unknown message")
  }
}

class RoadAddressFloater(roadAddressService: RoadAddressService) extends Actor {
  def receive = {
    case w: Set[any] => roadAddressService.checkRoadAddressFloating(w.asInstanceOf[Set[Long]])
    case _                    => println("roadAddressUpdater: Received unknown message")
  }
}

object Digiroad2Context {
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
  system.scheduler.schedule(FiniteDuration(2, TimeUnit.MINUTES),FiniteDuration(10, TimeUnit.MINUTES)) { //first query after 2 mins, then every 10 mins
    try {
      projectService.updateProjectsWaitingResponseFromTR()
    } catch {
      case ex: Exception => System.err.println("Exception at TR checks: " + ex.getMessage)
    }
  }

  val vallu = system.actorOf(Props[ValluActor], name = "vallu")
  eventbus.subscribe(vallu, "asset:saved")

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

  val roadAddressUpdater = system.actorOf(Props(classOf[RoadAddressUpdater], roadAddressService), name = "roadAddressUpdater")
  eventbus.subscribe(roadAddressUpdater, "roadAddress:persistMissingRoadAddress")

  val roadAddressMerger = system.actorOf(Props(classOf[RoadAddressMerger], roadAddressService), name = "roadAddressMerger")
  eventbus.subscribe(roadAddressMerger, "roadAddress:mergeRoadAddress")

  val roadAddressAdjustment = system.actorOf(Props(classOf[RoadAddressAdjustment], roadAddressService), name = "roadAddressAdjustment")
  eventbus.subscribe(roadAddressAdjustment, "roadAddress:persistAdjustments")

  val roadAddressFloater = system.actorOf(Props(classOf[RoadAddressFloater], roadAddressService), name = "roadAddressFloater")
  eventbus.subscribe(roadAddressFloater, "roadAddress:floatRoadAddress")

  lazy val roadAddressService: RoadAddressService = {
    new RoadAddressService(roadLinkService, eventbus, properties.getProperty("digiroad2.VVHRoadlink.frozen", "false").toBoolean)
  }

  lazy val projectService: ProjectService = {
    new ProjectService(roadAddressService, roadLinkService, eventbus,properties.getProperty("digiroad2.VVHRoadlink.frozen", "false").toBoolean)
  }

  lazy val authenticationTestModeEnabled: Boolean = {
    properties.getProperty("digiroad2.authenticationTestMode", "false").toBoolean
  }

  lazy val assetPropertyService: AssetPropertyService = {
    new AssetPropertyService(eventbus, userProvider, DefaultDatabaseTransaction)
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
    new RoadAddressesService(eventbus, roadLinkService)
  }

  lazy val revision: String = {
    revisionInfo.getProperty("digiroad2.revision")
  }
  lazy val deploy_date: String = {
    revisionInfo.getProperty("digiroad2.latestDeploy")
  }

  lazy val massTransitStopService: MassTransitStopService = {
    class ProductionMassTransitStopService(val eventbus: DigiroadEventBus, val roadLinkService: RoadLinkService) extends MassTransitStopService {
      override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
      override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
      override val massTransitStopDao: MassTransitStopDao = new MassTransitStopDao
      override val municipalityDao: MunicipalityDao = new MunicipalityDao
      override val tierekisteriClient: TierekisteriMassTransitStopClient = Digiroad2Context.tierekisteriClient
    }
    new ProductionMassTransitStopService(eventbus, roadLinkService)
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
    new TrafficSignService(roadLinkService)
  }

  lazy val manoeuvreService = {
    new ManoeuvreService(roadLinkService)
  }

  lazy val servicePointService: ServicePointService = new ServicePointService()

  val env = System.getProperty("env")
  def getProperty(name: String) = {
    val property = properties.getProperty(name)
    if(property != null)
      property
    else
      throw new RuntimeException(s"cannot find property $name for enviroment: $env")
  }
}
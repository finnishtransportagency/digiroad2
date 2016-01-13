package fi.liikennevirasto.digiroad2

import java.util.Properties

import akka.actor.{Actor, ActorSystem, Props}
import fi.liikennevirasto.digiroad2.asset.AssetProvider
import fi.liikennevirasto.digiroad2.asset.oracle.{OracleSpatialAssetDao, DatabaseTransaction, DefaultDatabaseTransaction}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.ChangeSet
import fi.liikennevirasto.digiroad2.linearasset.{SpeedLimitProvider, UnknownSpeedLimit}
import fi.liikennevirasto.digiroad2.municipality.MunicipalityProvider
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.digiroad2.vallu.ValluSender

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
    linearAssetService.drop(changeSet.droppedAssetIds)
    linearAssetService.persistMValueAdjustments(changeSet.adjustedMValues)
    linearAssetService.persistSideCodeAdjustments(changeSet.adjustedSideCodes)
  }
}

class SpeedLimitUpdater[A, B](speedLimitProvider: SpeedLimitProvider) extends Actor {
  def receive = {
    case x: Set[A] => speedLimitProvider.purgeUnknown(x.asInstanceOf[Set[Long]])
    case x: Seq[B] => speedLimitProvider.persistUnknown(x.asInstanceOf[Seq[UnknownSpeedLimit]])
    case _      => println("speedLimitFiller: Received unknown message")
  }
}

class LinkPropertyUpdater(roadLinkService: RoadLinkService) extends Actor {
  def receive = {
    case w: RoadLinkChangeSet => roadLinkService.updateRoadLinkChanges(w)
    case _                    => println("linkPropertyUpdater: Received unknown message")
  }
}

object Digiroad2Context {
  val Digiroad2ServerOriginatedResponseHeader = "Digiroad2-Server-Originated-Response"
  lazy val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }

  val system = ActorSystem("Digiroad2")

  val vallu = system.actorOf(Props[ValluActor], name = "vallu")
  eventbus.subscribe(vallu, "asset:saved")

  val linearAssetUpdater = system.actorOf(Props(classOf[LinearAssetUpdater], linearAssetService), name = "linearAssetUpdater")
  eventbus.subscribe(linearAssetUpdater, "linearAssets:update")

  val speedLimitUpdater = system.actorOf(Props(classOf[SpeedLimitUpdater[Long, UnknownSpeedLimit]], speedLimitProvider), name = "speedLimitUpdater")
  eventbus.subscribe(speedLimitUpdater, "speedLimits:purgeUnknownLimits")
  eventbus.subscribe(speedLimitUpdater, "speedLimits:persistUnknownLimits")

  val linkPropertyUpdater = system.actorOf(Props(classOf[LinkPropertyUpdater], roadLinkService), name = "linkPropertyUpdater")
  eventbus.subscribe(linkPropertyUpdater, "linkProperties:changed")

  lazy val authenticationTestModeEnabled: Boolean = {
    properties.getProperty("digiroad2.authenticationTestMode", "false").toBoolean
  }

  lazy val spatialAssetDao: OracleSpatialAssetDao = new OracleSpatialAssetDao

  lazy val assetProvider: AssetProvider = {
    Class.forName(properties.getProperty("digiroad2.featureProvider"))
         .getDeclaredConstructor(classOf[OracleSpatialAssetDao], classOf[DigiroadEventBus], classOf[UserProvider], classOf[DatabaseTransaction])
         .newInstance(spatialAssetDao, eventbus, userProvider, DefaultDatabaseTransaction)
         .asInstanceOf[AssetProvider]
  }

  lazy val speedLimitProvider: SpeedLimitProvider = {
    Class.forName(properties.getProperty("digiroad2.speedLimitProvider"))
      .getDeclaredConstructor(classOf[DigiroadEventBus], classOf[VVHClient], classOf[RoadLinkService])
      .newInstance(eventbus, vvhClient, roadLinkService)
      .asInstanceOf[SpeedLimitProvider]
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
    new VVHClient(getProperty("digiroad2.VVHServiceHost"))
  }

  lazy val roadLinkService: RoadLinkService = {
    new RoadLinkService(vvhClient, eventbus)
  }

  lazy val massTransitStopService: MassTransitStopService = {
    class ProductionMassTransitStopService(val eventbus: DigiroadEventBus) extends MassTransitStopService {
      override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
      override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
      override val spatialAssetDao: OracleSpatialAssetDao = Digiroad2Context.spatialAssetDao
      override def vvhClient: VVHClient = Digiroad2Context.vvhClient
    }
    new ProductionMassTransitStopService(eventbus)
  }

  lazy val linearAssetService: LinearAssetService = {
    new LinearAssetService(roadLinkService, eventbus)
  }

  lazy val pedestrianCrossingService: PedestrianCrossingService = {
    new PedestrianCrossingService(vvhClient)
  }

  lazy val trafficLightService: TrafficLightService = {
    new TrafficLightService(vvhClient)
  }

  lazy val obstacleService: ObstacleService = {
    new ObstacleService(vvhClient)
  }

  lazy val railwayCrossingService: RailwayCrossingService = {
    new RailwayCrossingService(vvhClient)
  }

  lazy val directionalTrafficSignService: DirectionalTrafficSignService = {
    new DirectionalTrafficSignService(vvhClient)
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
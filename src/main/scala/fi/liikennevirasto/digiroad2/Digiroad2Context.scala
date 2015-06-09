package fi.liikennevirasto.digiroad2

import java.util.Properties

import akka.actor.{Actor, ActorSystem, Props}
import fi.liikennevirasto.digiroad2.SpeedLimitFiller.SpeedLimitChangeSet
import fi.liikennevirasto.digiroad2.asset.oracle.{DatabaseTransaction, DefaultDatabaseTransaction}
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, AssetProvider}
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.{RoadLinkForSpeedLimit, LinearAssetProvider}
import fi.liikennevirasto.digiroad2.municipality.MunicipalityProvider
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.digiroad2.vallu.ValluSender
import scala.slick.driver.JdbcDriver.backend.Database

class ValluActor extends Actor {
  def receive = {
    case (massTransitStop: EventBusMassTransitStop) => ValluSender.postToVallu(massTransitStop)
    case _                               => println("received unknown message")
  }
}

class SpeedLimitFillerActor(linearAssetProvider: LinearAssetProvider) extends Actor {
  def receive = {
    case x: Map[Long, RoadLinkForSpeedLimit]  => "bal"//linearAssetProvider.fillPartiallyFilledRoadLinks(x)
    case _                                    => println("speedLimitFiller: Received unknown message")
  }
}

class SpeedLimitUpdater(linearAssetProvider: LinearAssetProvider) extends Actor {
  def receive = {
    case x: SpeedLimitChangeSet => persistSpeedLimitChanges(x)
    case _                      => println("speedLimitFiller: Received unknown message")
  }

  def persistSpeedLimitChanges(speedLimitChangeSet: SpeedLimitChangeSet) {
    linearAssetProvider.markSpeedLimitsFloating(speedLimitChangeSet.droppedSpeedLimitIds)
    linearAssetProvider.persistMValueAdjustments(speedLimitChangeSet.adjustedMValues)
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

  val speedLimitFiller = system.actorOf(Props(classOf[SpeedLimitFillerActor], linearAssetProvider), name = "speedLimitFiller")
  eventbus.subscribe(speedLimitFiller, "speedLimits:linkGeometriesRetrieved")

  val speedLimitUpdater = system.actorOf(Props(classOf[SpeedLimitUpdater], linearAssetProvider), name = "speedLimitUpdater")
  eventbus.subscribe(speedLimitUpdater, "speedLimits:update")

  lazy val authenticationTestModeEnabled: Boolean = {
    properties.getProperty("digiroad2.authenticationTestMode", "false").toBoolean
  }

  lazy val assetProvider: AssetProvider = {
    Class.forName(properties.getProperty("digiroad2.featureProvider"))
         .getDeclaredConstructor(classOf[DigiroadEventBus], classOf[UserProvider], classOf[DatabaseTransaction])
         .newInstance(eventbus, userProvider, DefaultDatabaseTransaction)
         .asInstanceOf[AssetProvider]
  }

  lazy val linearAssetProvider: LinearAssetProvider = {
    Class.forName(properties.getProperty("digiroad2.linearAssetProvider"))
      .getDeclaredConstructor(classOf[DigiroadEventBus], classOf[RoadLinkService])
      .newInstance(eventbus, roadLinkService)
      .asInstanceOf[LinearAssetProvider]
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
    if (useVVHGeometry) {
      new VVHRoadLinkService(vvhClient)
    } else RoadLinkService
  }

  lazy val assetService: AssetService = {
    new AssetService(roadLinkService)
  }

  lazy val massTransitStopService: MassTransitStopService = {
    class ProductionMassTransitStopService(val eventbus: DigiroadEventBus) extends MassTransitStopService {
      override def roadLinkService: RoadLinkService = Digiroad2Context.roadLinkService
      override def withDynTransaction[T](f: => T): T = Database.forDataSource(OracleDatabase.ds).withDynTransaction(f)
      override def withDynSession[T](f: => T): T = Database.forDataSource(OracleDatabase.ds).withDynSession(f)
    }
    new ProductionMassTransitStopService(eventbus)
  }

  lazy val useVVHGeometry: Boolean = properties.getProperty("digiroad2.useVVHGeometry", "false").toBoolean

  val env = System.getProperty("env")
  def getProperty(name: String) = {
    val property = properties.getProperty(name)
    if(property != null)
      property
    else
      throw new RuntimeException(s"cannot find property $name for enviroment: $env")
  }
}
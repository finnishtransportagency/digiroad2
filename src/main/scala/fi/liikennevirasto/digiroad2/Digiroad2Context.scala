package fi.liikennevirasto.digiroad2

import java.util.Properties
import fi.liikennevirasto.digiroad2.asset.AssetProvider
import fi.liikennevirasto.digiroad2.linearasset.{RoadLinkUncoveredBySpeedLimit, LinearAssetProvider}
import fi.liikennevirasto.digiroad2.user.{User, UserProvider}
import fi.liikennevirasto.digiroad2.municipality.MunicipalityProvider
import fi.liikennevirasto.digiroad2.vallu.ValluSender
import fi.liikennevirasto.digiroad2.asset.AssetWithProperties
import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props

class ValluActor extends Actor {
  def receive = {
    case (municipalityName: String, asset: AssetWithProperties) => ValluSender.postToVallu(municipalityName, asset)
    case _                               => println("received unknown message")
  }
}

class SpeedLimitFiller(linearAssetProvider: LinearAssetProvider) extends Actor {
  def receive = {
    case x: Map[Long, RoadLinkUncoveredBySpeedLimit] => linearAssetProvider.fillUncoveredRoadLinks(x)
    case _                                           => println("speedLimitFiller: Received unknown message")
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

  val speedLimitFiller = system.actorOf(Props(classOf[SpeedLimitFiller], linearAssetProvider), name = "speedLimitFiller")
  eventbus.subscribe(speedLimitFiller, "speedLimits:uncoveredRoadLinksFound")

  lazy val authenticationTestModeEnabled: Boolean = {
    properties.getProperty("digiroad2.authenticationTestMode", "false").toBoolean
  }

  lazy val assetProvider: AssetProvider = {
    Class.forName(properties.getProperty("digiroad2.featureProvider"))
         .getDeclaredConstructor(classOf[DigiroadEventBus], classOf[UserProvider])
         .newInstance(eventbus, userProvider)
         .asInstanceOf[AssetProvider]
  }

  lazy val linearAssetProvider: LinearAssetProvider = {
    Class.forName(properties.getProperty("digiroad2.linearAssetProvider"))
      .getDeclaredConstructor(classOf[DigiroadEventBus])
      .newInstance(eventbus)
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

  val env = System.getProperty("env")
  def getProperty(name: String) = {
    val property = properties.getProperty(name)
    if(property != null)
      property
    else
      throw new RuntimeException(s"cannot find property $name for enviroment: $env")
  }
}
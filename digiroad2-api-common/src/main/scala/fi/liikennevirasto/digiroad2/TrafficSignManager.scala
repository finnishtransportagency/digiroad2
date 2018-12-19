package fi.liikennevirasto.digiroad2

import java.util.Properties

import akka.actor.{Actor, Props}
import fi.liikennevirasto.digiroad2.Digiroad2Context.{eventbus, system, trafficSignService}
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{ManoeuvreService, ProhibitionService}
import fi.liikennevirasto.digiroad2.service.pointasset.{TrafficSignInfo, TrafficSignService}
import fi.liikennevirasto.digiroad2.util.JsonSerializer

object TrafficSignManager {

  lazy val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }

  lazy val eventbus: DigiroadEventBus = {
    Class.forName(properties.getProperty("digiroad2.eventBus")).newInstance().asInstanceOf[DigiroadEventBus]
  }

  lazy val vvhClient: VVHClient = {
    new VVHClient(getProperty("digiroad2.VVHRestApiEndPoint"))
  }

  lazy val prohibitionService: ProhibitionService = {
    new ProhibitionService(roadLinkService, eventbus)
  }

  lazy val manoeuvreService = {
    new ManoeuvreService(roadLinkService, eventbus)
  }

  lazy val roadLinkService: RoadLinkService = {
    new RoadLinkService(vvhClient, eventbus, new JsonSerializer)
  }

  val trafficSignExpire = system.actorOf(Props(classOf[TrafficSignExpireAssets], trafficSignService), name ="trafficSignExpire" )
  eventbus.subscribe(trafficSignExpire, "trafficSign:expire")

  val trafficSignCreate = system.actorOf(Props(classOf[TrafficSignCreateAssets], trafficSignService), name ="trafficSignCreate" )
  eventbus.subscribe(trafficSignCreate, "trafficSign:create")


  val env = System.getProperty("env")
  def getProperty(name: String) = {
    val property = properties.getProperty(name)
    if(property != null)
      property
    else
      throw new RuntimeException(s"cannot find property $name for enviroment: $env")
  }

  class TrafficSignCreateAssets(trafficSignService: TrafficSignService) extends Actor {
    def receive = {
      case x: TrafficSignInfo => trafficSignsCreateAssets(x)
      case _ => println("trafficSignCreateAssets: Received unknown message")
    }
  }

  class TrafficSignExpireAssets(trafficSignService: TrafficSignService) extends Actor {
    def receive = {
      case x: Long => trafficSignService.getTrafficType(x) match {
        case Some(trafficType) => trafficSignsDeleteAssets(x, trafficType)
        case _ => println("Nonexistent traffic Sign Type")
      }
      case _ => println("trafficSignExpireAssets: Received unknown message")
    }
  }

  def trafficSignsCreateAssets(trafficSignInfo: TrafficSignInfo, newTransaction: Boolean = true ): Unit = {

    if (TrafficSignType.belongsToManoeuvre(trafficSignInfo.signType)) {
      manoeuvreService.createBasedOnTrafficSign(trafficSignInfo, newTransaction)
    }
    else if (TrafficSignType.belongsToProhibition(trafficSignInfo.signType)) {
      prohibitionService.createBasedOnTrafficSign(trafficSignInfo, newTransaction)
    }
  }

  def trafficSignsDeleteAssets(id: Long, trafficSignType: Int): Unit = {
    val username = Some("automatic_trafficSign_deleted")
    if (TrafficSignType.belongsToManoeuvre(trafficSignType)) {
      manoeuvreService.deleteManoeuvreFromSign(manoeuvreService.withIds(Set(id)), username)
    }
    else if (TrafficSignType.belongsToProhibition(trafficSignType)) {
      prohibitionService.deleteAssetBasedOnSign(prohibitionService.withId(id), username)
    }
  }
}
package fi.liikennevirasto.digiroad2.util

import java.util.Properties

import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummyEventBus, DummySerializer}
import fi.liikennevirasto.digiroad2.asset.AdditionalPanel
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.middleware.TrafficSignManager
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase.withDynSession
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{HazmatTransportProhibitionService, ManoeuvreService, ProhibitionService}
import fi.liikennevirasto.digiroad2.service.pointasset.{TrafficSignInfo, TrafficSignService}
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.digiroad2.util.AssetValidatorProcess.getClass
import org.joda.time.DateTime


object TrafficSgingLinearAssetGeneratorProcess {

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

  val dataImporter = new AssetDataImporter
  lazy val vvhClient: VVHClient = {
    new VVHClient(dr2properties.getProperty("digiroad2.VVHRestApiEndPoint"))
  }

  lazy val roadLinkService: RoadLinkService = {
    new RoadLinkService(vvhClient, eventbus, new DummySerializer)
  }

  lazy val userProvider: UserProvider = {
    Class.forName(dr2properties.getProperty("digiroad2.userProvider")).newInstance().asInstanceOf[UserProvider]
  }

  lazy val eventbus: DigiroadEventBus = {
    new DigiroadEventBus
  }

  lazy val manoeuvreService: ManoeuvreService = {
    new ManoeuvreService(roadLinkService, new DummyEventBus)
  }

  lazy val prohibitionService: ProhibitionService = {
    new ProhibitionService(roadLinkService, eventbus)
  }

  lazy val hazmatTransportProhibitionService: HazmatTransportProhibitionService = {
    new HazmatTransportProhibitionService(roadLinkService, eventbus)
  }

  lazy val trafficSignService: TrafficSignService = {
    new TrafficSignService(roadLinkService, userProvider, eventbus)
  }

  lazy val trafficSignManager: TrafficSignManager = {
    new TrafficSignManager(manoeuvreService, prohibitionService, hazmatTransportProhibitionService)
  }

  lazy val oracleLinearAssetDao : OracleLinearAssetDao = {
    new OracleLinearAssetDao(vvhClient, roadLinkService)
  }

  def createLinearAssetUsingTrafficSigns(): Unit = {
    println("\nStarting create Linear Assets using traffic signs")
    println(DateTime.now())
    println("")

    withDynSession {
      val lastExecutionDate = oracleLinearAssetDao.getLastExecutionDateOfConnectedAsset.getOrElse(DateTime.now().minusDays(2))
      println(s"Last Execution Date of the batch: ${lastExecutionDate.toString} ")
      println("")

      println(s"Obtaining created/modified/deleted traffic Signs after the date: ${lastExecutionDate.toString}")
      //Get Traffic Signs
      val trafficSigns = trafficSignService.getAfterDate(lastExecutionDate)
      println(s"Number of Traffic Signs to execute: ${trafficSigns.size} ")

      val trafficSignsToTransform =
        trafficSigns.filter { ts =>
          val signType = trafficSignService.getProperty(ts, trafficSignService.typePublicId).get.propertyValue.toInt
          TrafficSignManager.belongsToProhibition(signType)
        }

      if (trafficSignsToTransform.nonEmpty) {
        println("")
        println(s"Obtaining all Road Links for filtered Traffic Signs")
        val roadLinks = roadLinkService.getRoadLinksByLinkIdsFromVVH(trafficSignsToTransform.map(_.linkId).toSet, false)
        println(s"End of roadLinks fetch for filtered Traffic Signs")

        println("")
        println("Start processing traffic signs")
        trafficSignsToTransform.foreach { t =>
          val tsCreatedDate = t.createdBy
          val tsModifiedDate = t.modifiedAt

          if (t.expired) {
            // Delete actions
            println("********************************************")
            println("DETECTED AS A DELETED")
            println("********************************************")

          } else if (tsCreatedDate.nonEmpty && tsModifiedDate.isEmpty) {
            //Create actions
            println(s"Start creating prohibition according the traffic sign with ID: ${t.id}")

            val signType = trafficSignService.getProperty(t, trafficSignService.typePublicId).get.propertyValue.toInt
            val additionalPanel = trafficSignService.getAllProperties(t, trafficSignService.additionalPublicId).map(_.asInstanceOf[AdditionalPanel])
            trafficSignManager.createAssets(TrafficSignInfo(t.id, t.linkId, t.validityDirection, signType, t.mValue, roadLinks.find(_.linkId == t.linkId).head, additionalPanel), false)

            println(s"Prohibition related with traffic sign with ID: ${t.id} created")
            println("")

          } else {
            //Modify actions
            println("********************************************")
            println("DETECTED AS A MODIFIED")
            println("********************************************")
          }

        }
      }
    }
    println("")
    println("Complete at time: " + DateTime.now())
  }

  def main(args: Array[String]): Unit = {
    import scala.util.control.Breaks._
    val username = properties.getProperty("bonecp.username")
    if (!username.startsWith("dr2dev")) {
      println("*************************************************************************************")
      println("YOU ARE RUNNING FIXTURE RESET AGAINST A NON-DEVELOPER DATABASE, TYPE 'YES' TO PROCEED")
      println("*************************************************************************************")
      breakable {
        while (true) {
          val input = Console.readLine()
          if (input.trim() == "YES") {
            break()
          }
        }
      }
    }

    createLinearAssetUsingTrafficSigns()
  }
}

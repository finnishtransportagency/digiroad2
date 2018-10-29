package fi.liikennevirasto.digiroad2.util

import java.util.Properties

import fi.liikennevirasto.digiroad2.asset.{Municipality, SpeedLimitAsset}
import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer}
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.{InaccurateAssetDAO, Queries}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleSpeedLimitDao
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.process.{AssetServiceValidator, _}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignService
import fi.liikennevirasto.digiroad2.user.UserProvider
import org.joda.time.DateTime

object AssetValidatorProcess {

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

  lazy val vvhClient: VVHClient = {
    new VVHClient(dr2properties.getProperty("digiroad2.VVHRestApiEndPoint"))
  }

  lazy val userProvider: UserProvider = {
    Class.forName(dr2properties.getProperty("digiroad2.userProvider")).newInstance().asInstanceOf[UserProvider]
  }

  lazy val roadLinkService : RoadLinkService = {
    new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer)
  }

  lazy val trafficSignService: TrafficSignService = {
    new TrafficSignService(roadLinkService, userProvider, new DummyEventBus)
  }

  lazy val manoeuvreServiceValidator: ManoeuvreValidator = {
    new ManoeuvreValidator()
  }

  lazy val hazmatTransportProhibitionValidator: HazmatTransportProhibitionValidator = {
    new HazmatTransportProhibitionValidator()
  }

  lazy val heightLimitValidator: HeightLimitValidator = {
    new HeightLimitValidator()
  }

  lazy val totalWeightLimitValidator: TotalWeightLimitValidator = {
    new TotalWeightLimitValidator()
  }

  lazy val trailerTruckWeightLimitValidator: TrailerTruckWeightLimitValidator = {
    new TrailerTruckWeightLimitValidator()
  }

  lazy val axleWeightLimitValidator: AxleWeightLimitValidator = {
    new AxleWeightLimitValidator()
  }

  lazy val bogieWeightLimitValidator: BogieWeightLimitValidator = {
    new BogieWeightLimitValidator()
  }

  lazy val widthLimitValidator: WidthLimitValidator = {
    new WidthLimitValidator()
  }

  lazy val lengthLimitValidator: LengthLimitValidator = {
    new LengthLimitValidator()
  }

  lazy val speedLimitValidator: SpeedLimitValidator = {
    new SpeedLimitValidator(trafficSignService)
  }

  lazy val inaccurateAssetDAO : InaccurateAssetDAO = {
    new InaccurateAssetDAO()
  }

  def verifyInaccurateSpeedLimits(): Unit = {
    println("Start inaccurate SpeedLimit verification\n")
    println(DateTime.now())
    println("")

    val polygonTools: PolygonTools = new PolygonTools()
    val dao = new OracleSpeedLimitDao(null, null)

    //Expire all inaccuratedAssets
    OracleDatabase.withDynTransaction {
      inaccurateAssetDAO.deleteAllInaccurateAssets(SpeedLimitAsset.typeId)
    }

    //Get All Municipalities
    val municipalities: Seq[Int] =
      OracleDatabase.withDynSession {
        Queries.getMunicipalities
      }

    municipalities.foreach { municipality =>
      println("Working on... municipality -> " + municipality)
      val roadLinks = roadLinkService.getRoadLinksFromVVHByMunicipality(municipality).filter(_.administrativeClass == Municipality).groupBy(_.linkId)

      OracleDatabase.withDynTransaction {
        val speedLimitsByLinkId = dao.getCurrentSpeedLimitsByLinkIds(Some(roadLinks.keys.toSet)).groupBy(_.linkId)

        val inaccurateAssets = speedLimitsByLinkId.flatMap {
          case (linkId, speedLimits) =>
            val trafficSigns = trafficSignService.getPersistedAssetsByLinkIdWithoutTransaction(linkId)
            val roadLink = roadLinks(linkId).head
            speedLimitValidator.checkSpeedLimitUsingTrafficSign(trafficSigns, roadLink, speedLimits).map {
              inaccurateAsset =>
                println(s"Inaccurate asset ${inaccurateAsset.id} found ")
                (inaccurateAsset, roadLink.administrativeClass)
            }
        }

        inaccurateAssets.foreach { case (speedLimit, administrativeClass) =>
          inaccurateAssetDAO.createInaccurateAsset(speedLimit.id, SpeedLimitAsset.typeId, municipality, administrativeClass)
        }
      }
    }

    println("")
    println("Ended inaccurate SpeedLimit verification\n")
    println(DateTime.now())
  }

  private val validatorProcessAssets = Map[String, AssetServiceValidator](
      "manoeuvre" -> manoeuvreServiceValidator,
      "hazmatTransportProhibition" -> hazmatTransportProhibitionValidator,
      "heighLimit" -> heightLimitValidator,
      "totalWeightLimit" -> totalWeightLimitValidator,
      "trailerTruckWeight" -> trailerTruckWeightLimitValidator,
      "axleWeightLimit" -> axleWeightLimitValidator,
      "bogieWeightLimit" -> bogieWeightLimitValidator,
      "widthLimit" -> widthLimitValidator,
      "lengthLimit" -> lengthLimitValidator
  )

  def main(args:Array[String]) : Unit = {
    import scala.util.control.Breaks._
    val username = properties.getProperty("bonecp.username")
    if (!username.startsWith("dr2dev")) {
      println("*******************************************************************************************")
      println("YOU ARE RUNNING VALIDATOR ASSET PROCESS AGAINST A NON-DEVELOPER DATABASE, TYPE 'YES' TO PROCEED")
      println("*******************************************************************************************")
      breakable {
        while (true) {
          val input = Console.readLine()
          if (input.trim() == "YES") {
            break()
          }
        }
      }
    }

    if(args.size < 1){
      println("Usage: AssetValidatorProcess <asset> [<radiousDistance>]")
    }else{
      val assetName = args(0)
      val availableAssets = validatorProcessAssets.keySet ++ Set("speedLimit")

      if(availableAssets.contains(assetName)){
        if(assetName == "speedLimit")
          verifyInaccurateSpeedLimits()
        else
          validateAssets(validatorProcessAssets.get(assetName).get)
      }else{
        println(s"The asset with name $assetName is not supported")
        println()
        println("Supported asset types: " + availableAssets.mkString(" | "))
      }
    }
  }

  private def validateAssets(assetServiceValidator: AssetServiceValidator): Unit = {
    val assetType = assetServiceValidator.getAssetName

    println()
    println(s"Start $assetType import at: ")
    println(DateTime.now())

    assetServiceValidator.verifyInaccurate()

    println(s"$assetType import complete at time: ")
    println(DateTime.now())
    println("\n")
  }
}

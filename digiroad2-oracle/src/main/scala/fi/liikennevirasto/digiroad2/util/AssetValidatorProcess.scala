package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.{Municipality, SpeedLimitAsset, State}
import fi.liikennevirasto.digiroad2.DummyEventBus
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.dao.{InaccurateAssetDAO, Queries}
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISSpeedLimitDao
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.process._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{ManoeuvreService, ProhibitionService}
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignService
import fi.liikennevirasto.digiroad2.user.UserProvider
import org.joda.time.DateTime

import scala.sys.exit

object AssetValidatorProcess {

  lazy val roadLinkClient: RoadLinkClient = {
    new RoadLinkClient()
  }

  lazy val userProvider: UserProvider = {
    Class.forName(Digiroad2Properties.userProvider).newInstance().asInstanceOf[UserProvider]
  }

  lazy val roadLinkService : RoadLinkService = {
    new RoadLinkService(roadLinkClient, new DummyEventBus)
  }

  lazy val trafficSignService: TrafficSignService = {
    new TrafficSignService(roadLinkService, new DummyEventBus)
  }

  lazy val manoeuvreService: ManoeuvreService = {
    new ManoeuvreService(roadLinkService, new DummyEventBus)
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

  lazy val pedestrianCrossingValidator: PedestrianCrossingValidator = {
    new PedestrianCrossingValidator()
  }

  lazy val inaccurateAssetDAO : InaccurateAssetDAO = {
    new InaccurateAssetDAO()
  }

  def verifyInaccurateSpeedLimits(): Unit = {
    println("Start inaccurate SpeedLimit verification\n")
    println(DateTime.now())
    val dao = new PostGISSpeedLimitDao(null)

    //Expire all inaccuratedAssets
    PostGISDatabase.withDynTransaction {
      inaccurateAssetDAO.deleteAllInaccurateAssets(SpeedLimitAsset.typeId)

      //Get All Municipalities
      val municipalities: Seq[Int] = Queries.getMunicipalities

      municipalities.foreach { municipality =>
        println("Working on... municipality -> " + municipality)
        val roadLinks = roadLinkService.getRoadLinksByMunicipality(municipality, false).filter(roadLink => Seq(Municipality, State).contains(roadLink.administrativeClass)).groupBy(_.linkId)
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
      "lengthLimit" -> lengthLimitValidator,
      "pedestrianCrossing" -> pedestrianCrossingValidator
  )

  def main(args:Array[String]) : Unit = {
    val batchMode = Digiroad2Properties.batchMode
    if (!batchMode) {
      println("*******************************************************************************************")
      println("TURN batchMode true TO RUN VALIDATOR ASSET PROCESS")
      println("*******************************************************************************************")
      exit()
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
          validateAssets(validatorProcessAssets(assetName))
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

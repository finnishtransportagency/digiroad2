package fi.liikennevirasto.digiroad2.util

import java.util.Properties

import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer}
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.process.{AssetServiceValidator, _}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignService
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.digiroad2.util.TierekisteriDataImporter.getClass
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
    new TrafficSignService(roadLinkService, userProvider)
  }

  lazy val manoeuvreServiceValidator: ManoeuvreServiceValidator = {
    new ManoeuvreServiceValidator()
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

  val validatorProcessAssets = Map[String, AssetServiceValidator](
      "manoeuvre" -> manoeuvreServiceValidator,
      "hazmatTransportProhibition" -> hazmatTransportProhibitionValidator,
      "heighLimit" -> heightLimitValidator,
      "totalWeightLimit" -> totalWeightLimitValidator,
      "trailerTruckWeight" -> trailerTruckWeightLimitValidator,
      "axleWeightLimit" -> axleWeightLimitValidator,
      "bogieWeightLimt" -> bogieWeightLimitValidator,
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
      val radiousDistance = args.size match {
        case 2 => Some(args(1).toInt)
        case _ => None
      }

      val availableAssets = validatorProcessAssets.keySet

      if(availableAssets.contains(assetName)){
        validateAssets(validatorProcessAssets.get(assetName).get, radiousDistance)
      }else{
        println(s"The asset with name $assetName is not supported")
        println()
        println("Supported asset types: " + availableAssets.mkString(" | "))
      }
    }
  }

  private def validateAssets(assetServiceValidator: AssetServiceValidator, radiousDistance: Option[Int]): Unit = {
    val assetType = assetServiceValidator.getAssetName()

    println()
    println(s"Start $assetType import at: ")
    println(DateTime.now())

    //TODO Pass the meters
//    assetServiceValidator.validate(radiousDistance)

    println(s"$assetType import complete at time: ")
    println(DateTime.now())
    println("\n")
  }
}

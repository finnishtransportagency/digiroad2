package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.client.VelhoClient

import scala.sys.exit

object VelhoAssetImport {
  lazy val velhoClient = new VelhoClient

  def main(args: Array[String]): Unit = {
    val batchMode = Digiroad2Properties.batchMode
    if (!batchMode) {
      println("*******************************************************************************************")
      println("TURN batchMode true TO RUN VELHO ASSET IMPORT")
      println("*******************************************************************************************")
      exit()
    }

    if (args.length < 3) {

    } else {
      val assetName = args(0)
      // TODO change to env and aws secrets
      val username = args(1)
      val password = args(2)
      val path = "varusteet/valaistukset" // path for lit road for testing
      

      assetName match {
        case "lit_road" => velhoClient.importAssetsFromLatauspalvelu(username, password, path)
        case "traffic_signs" => velhoClient.importAssetsFromHakupalvelu(username, password)
        case _ => throw new IllegalArgumentException("Invalid asset name.")
      }
    }
  }
}

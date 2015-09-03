package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.Digiroad2Context
import fi.liikennevirasto.digiroad2.asset.oracle.OracleSpatialAssetDao
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase

object UpdateIncompleteLinkList {
  def runUpdate(): Unit = {
    val municipalities: Seq[Int] = OracleDatabase.withDynSession {
      OracleSpatialAssetDao.getMunicipalities
    }

    municipalities.foreach { municipality =>
      println("*** Processing municipality: " + municipality)
      val roadLinks = Digiroad2Context.roadLinkService.getRoadLinksFromVVH(municipality)
      println("*** Processed " + roadLinks.length + " road links")
    }
  }

  def main(args:Array[String]) : Unit = {
    try {
      UpdateIncompleteLinkList.runUpdate()
    } catch {
      case e: Exception => e.printStackTrace()
    }
    Digiroad2Context.system.shutdown()
  }
}

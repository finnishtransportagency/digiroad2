package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.{RoadLinkService, Digiroad2Context}
import fi.liikennevirasto.digiroad2.asset.oracle.OracleSpatialAssetDao
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase.ds

import scala.slick.driver.JdbcDriver.backend.Database

object UpdateIncompleteLinkList {
  def runUpdate(): Unit = {
    val municipalities: Seq[Int] = Database.forDataSource(ds).withDynSession {
      OracleSpatialAssetDao.getMunicipalities
    }

    municipalities.foreach { municipality =>
      println("*** Processing municipality: " + municipality)
      val roadLinks = Digiroad2Context.roadLinkService.getRoadLinksFromVVH(municipality)
      println("*** Processed " + roadLinks.length + " road links")
    }
  }

  def main(args:Array[String]) : Unit = {
    UpdateIncompleteLinkList.runUpdate()
    System.exit(0)
  }
}

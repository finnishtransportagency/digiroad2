package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.Digiroad2Context
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

import scala.sys.exit

object UpdateIncompleteLinkList {
  def main(args:Array[String]) : Unit = {
    val batchMode = Digiroad2Properties.batchMode
    if (!batchMode) {
      println("*******************************************************************************************")
      println("TURN ENV batchMode true TO RUN UpdateIncompleteLinkList")
      println("*******************************************************************************************")
      exit()
    }
    try {
      UpdateIncompleteLinkList.runUpdate()
    } finally {
      Digiroad2Context.system.terminate()
    }
  }

  private def runUpdate(): Unit = {
    println("*** Delete incomplete links")
    clearIncompleteLinks()
    println("*** Get municipalities")
    val municipalities: Seq[Int] = PostGISDatabase.withDynSession {
      Queries.getMunicipalities
    }
    municipalities.foreach { municipality =>
      println("*** Processing municipality: " + municipality)
      val roadLinks = Digiroad2Context.roadLinkService.getRoadLinksFromVVH(municipality)
      println("*** Processed " + roadLinks.length + " road links")
    }
  }

  private def clearIncompleteLinks(): Unit = {
    PostGISDatabase.withDynTransaction {
      sqlu"""truncate table incomplete_link""".execute
    }
  }
}

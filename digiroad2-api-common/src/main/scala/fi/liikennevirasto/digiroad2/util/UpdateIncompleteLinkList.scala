package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.Digiroad2Context
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

import scala.sys.exit

object UpdateIncompleteLinkList {
  val logger = LoggerFactory.getLogger(getClass)
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
    logger.info("*** Delete incomplete links")
    clearIncompleteLinks()
    logger.info("*** Get municipalities")
    val municipalities: Seq[Int] = PostGISDatabase.withDynSession {
      Queries.getMunicipalities
    }

    var counter = 0
    municipalities.foreach { municipality =>
      val timer1 = System.currentTimeMillis()
      logger.info("*** Processing municipality: " + municipality)
      val roadLinks = Digiroad2Context.roadLinkService.getRoadLinksFromVVH(municipality)
      counter += 1
      logger.info("*** Processed " + roadLinks.length + " road links with municipality " + municipality)
      logger.info(s" number of succeeding municipalities $counter from all ${municipalities.size}")
      logger.info("processing took: %.3f sec".format((System.currentTimeMillis() - timer1) * 0.001))
      logger.info("thread: " + Thread.currentThread().getName)
    }
    // await minute to make sure akka inbox is fully processed
    Thread.sleep(1000L * 6L)
  }

  private def clearIncompleteLinks(): Unit = {
    PostGISDatabase.withDynTransaction {
      sqlu"""truncate table incomplete_link""".execute
    }
  }
}

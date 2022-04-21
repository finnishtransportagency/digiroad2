package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.Digiroad2Context
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import org.slf4j.LoggerFactory

import scala.sys.exit

object RefreshRoadLinkCache {

  val logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    try {
      RefreshRoadLinkCache.refreshCache()
    } finally {
      exit()
    }
  }

  def refreshCache(): Unit ={
    PostGISDatabase.withDynSession {
      val municipalities: Seq[Int] = Queries.getMunicipalities

      municipalities.foreach(municipality => {
        Digiroad2Context.roadLinkService.fillAndRefreshRoadLinkCache(municipality)
      })
    }
  }
}

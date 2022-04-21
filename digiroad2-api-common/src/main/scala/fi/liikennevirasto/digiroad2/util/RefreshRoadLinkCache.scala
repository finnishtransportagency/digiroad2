package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.Digiroad2Context
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import org.slf4j.LoggerFactory

object RefreshRoadLinkCache {

  val logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    val municipalities: Seq[Int] = PostGISDatabase.withDynSession {
      Queries.getMunicipalities
    }
    PostGISDatabase.withDynSession {
      municipalities.foreach(municipality => {
        Digiroad2Context.roadLinkService.fillAndRefreshRoadLinkCache(municipality)
      })
    }
  }
}

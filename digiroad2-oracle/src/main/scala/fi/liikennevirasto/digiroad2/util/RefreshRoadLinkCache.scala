package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.client.Caching
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer}
import org.slf4j.{Logger, LoggerFactory}



object RefreshRoadLinkCache {
  val logger: Logger = LoggerFactory.getLogger(getClass)
  lazy val vvhClient: VVHClient = {
    new VVHClient(Digiroad2Properties.vvhRestApiEndPoint)
  }

  lazy val roadLinkService: RoadLinkService = {
    new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer)
  }

  def refreshCache(): Unit = {
    if (Digiroad2Properties.caching){
      val municipalities: Seq[Int] = PostGISDatabase.withDynSession {
        Queries.getMunicipalities
      }
      val flushSuccess = LogUtils.time(logger, "Flushing cache"){
        Caching.flush()
      }

      if (flushSuccess) {
        val roadLinks = municipalities.flatMap(municipality => {
          roadLinkService.getRoadLinksAndComplementaryLinksFromVVHByMunicipality(municipality)
        })
        logger.info("Cached " + roadLinks.size + "roadlinks with updated properties")
      }
      else logger.error("Flushing cache failed")
    }
    else logger.error("Caching disabled in properties, can't refresh cache")
  }
}

package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService

class MunicipalityService (eventbus: DigiroadEventBus, roadLinkService: RoadLinkService) {

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

  def getMunicipalities: Seq[(Int, String)] = {
    withDynSession {
      Queries.getMunicipalitiesInfo
    }
  }
}

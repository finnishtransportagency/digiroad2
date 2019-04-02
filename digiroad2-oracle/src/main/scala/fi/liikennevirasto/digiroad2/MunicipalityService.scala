package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, MunicipalityInfo}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService

class MunicipalityService (eventbus: DigiroadEventBus, roadLinkService: RoadLinkService) {

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  def municipalityDao: MunicipalityDao = new MunicipalityDao

  def getMunicipalities: Seq[(Int, String)] = {
    withDynSession {
      municipalityDao.getMunicipalitiesInfo
    }
  }

  def getMunicipalityNameByCode(municipalityId: Int): String = {
    withDynSession {
      municipalityDao.getMunicipalityNameByCode(municipalityId)
    }
  }

  def getMunicipalitiesNameAndIdByCode(municipalityCodes: Set[Int]): List[MunicipalityInfo] = {
    withDynSession {
      municipalityDao.getMunicipalitiesNameAndIdByCode(municipalityCodes)
    }
  }
}

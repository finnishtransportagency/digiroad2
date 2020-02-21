package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, MunicipalityInfo}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService

class MunicipalityService {

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  def municipalityDao: MunicipalityDao = new MunicipalityDao

  def getMunicipalities: Seq[(Int, String)] = {
    withDynSession {
      municipalityDao.getMunicipalitiesInfo
    }
  }

  def getMunicipalityNameByCode(municipalityId: Int, newTransaction: Boolean = true): String = {
    if (newTransaction) {
      withDynSession {
        municipalityDao.getMunicipalityNameByCode(municipalityId)
      }
    } else
      municipalityDao.getMunicipalityNameByCode(municipalityId)
  }

  def getMunicipalitiesNameAndIdByCode(municipalityCodes: Set[Int], newTransaction: Boolean = true): List[MunicipalityInfo] = {
    if(newTransaction) {
      withDynSession {
        municipalityDao.getMunicipalitiesNameAndIdByCode(municipalityCodes)
      }
    } else {
      municipalityDao.getMunicipalitiesNameAndIdByCode(municipalityCodes)
    }
  }

  def getMunicipalitiesNameAndIdByEly(ely: Set[Int], newTransaction: Boolean = true): List[MunicipalityInfo] = {
    if(newTransaction) {
      withDynSession {
        municipalityDao.getMunicipalitiesNameAndIdByEly(ely)
      }
    } else {
      municipalityDao.getMunicipalitiesNameAndIdByEly(ely)
    }
  }

  def getMunicipalityByCoordinates(coordinates: Point, newTransaction: Boolean = true): Seq[MunicipalityInfo] = {
    if (newTransaction) {
      withDynSession {
        municipalityDao.getMunicipalityByCoordinates(coordinates)
      }
    } else
      municipalityDao.getMunicipalityByCoordinates(coordinates)
  }

  def getElyByCoordinates(coordinates: Point, newTransaction: Boolean = true): Seq[(Int, String)] = {
    if (newTransaction) {
      withDynSession {
        municipalityDao.getElysIdAndNamesByCoordinates(coordinates.x.toInt, coordinates.y.toInt)
      }
    } else
      municipalityDao.getElysIdAndNamesByCoordinates(coordinates.x.toInt, coordinates.y.toInt)
  }
}

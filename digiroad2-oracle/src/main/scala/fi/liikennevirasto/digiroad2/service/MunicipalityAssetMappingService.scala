package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.dao.{MunicipalityAssetMapping, MunicipalityAssetMappingDao}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import org.slf4j.{Logger, LoggerFactory}

class MunicipalityAssetMappingService {
  def dao: MunicipalityAssetMappingDao = new MunicipalityAssetMappingDao

  val logger: Logger = LoggerFactory.getLogger(getClass)

  def getByAssetId(assetId: Long, newTransaction: Boolean = true): Option[MunicipalityAssetMapping] = {
    if ( newTransaction ) {
      PostGISDatabase.withDynSession {
        dao.getByAssetId(assetId)
      }
    } else dao.getByAssetId(assetId)
  }

  def updateIdMappings(oldAssetId: Long, newAssetId: Long, newTransaction: Boolean = true): Unit = {
    val existingMapping = getByAssetId(oldAssetId, newTransaction = newTransaction)

    if ( existingMapping.isDefined ) {
      logger.info(s"Updating asset id $newAssetId to ${existingMapping.get}")

      if ( newTransaction ) {
        PostGISDatabase.withDynTransaction {
          dao.replaceAssetId(oldAssetId, newAssetId)
        }
      } else dao.replaceAssetId(oldAssetId, newAssetId)
    }
  }
}

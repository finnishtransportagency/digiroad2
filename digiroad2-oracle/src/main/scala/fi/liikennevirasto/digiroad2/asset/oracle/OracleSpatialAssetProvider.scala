package fi.liikennevirasto.digiroad2.asset.oracle

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, EventBusMassTransitStop}
import org.slf4j.LoggerFactory

trait DatabaseTransaction {
  def withDynTransaction[T](f: => T): T
}
object DefaultDatabaseTransaction extends DatabaseTransaction {
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
}

// FIXME:
// - move common asset functionality to asset service
class OracleSpatialAssetProvider(spatialAssetDao: OracleSpatialAssetDao, eventbus: DigiroadEventBus, userProvider: UserProvider, databaseTransaction: DatabaseTransaction = DefaultDatabaseTransaction) extends AssetProvider {
  val logger = LoggerFactory.getLogger(getClass)

  private def userCanModifyMunicipality(municipalityNumber: Int): Boolean = {
    val user = userProvider.getCurrentUser()
    user.isOperator() || user.isAuthorizedToWrite(municipalityNumber)
  }

  private def userCanModifyAsset(asset: AssetWithProperties): Boolean =
    userCanModifyMunicipality(asset.municipalityNumber)

  def getAssetById(assetId: Long): Option[AssetWithProperties] = {
    databaseTransaction.withDynTransaction {
      spatialAssetDao.getAssetById(assetId)
    }
  }

  private def eventBusMassTransitStop(asset: AssetWithProperties, municipalityName: String): EventBusMassTransitStop = {
    EventBusMassTransitStop(municipalityNumber = asset.municipalityNumber, municipalityName = municipalityName,
      nationalId = asset.nationalId, lon = asset.lon, lat = asset.lat, bearing = asset.bearing, validityDirection = asset.validityDirection,
      created = asset.created, modified = asset.modified, propertyData = asset.propertyData)
  }

  def updateAsset(assetId: Long, position: Option[Position], properties: Seq[SimpleProperty]): AssetWithProperties = {
    databaseTransaction.withDynTransaction {
      val asset = spatialAssetDao.getAssetById(assetId).get
      if (!userCanModifyAsset(asset)) { throw new IllegalArgumentException("User does not have write access to municipality") }
      val updatedAsset = spatialAssetDao.updateAsset(assetId, position, userProvider.getCurrentUser().username, properties)
      val municipalityName = spatialAssetDao.getMunicipalityNameByCode(updatedAsset.municipalityNumber)
      eventbus.publish("asset:saved", eventBusMassTransitStop(updatedAsset, municipalityName))
      updatedAsset
    }
  }

  def getEnumeratedPropertyValues(assetTypeId: Long): Seq[EnumeratedPropertyValue] = {
    AssetPropertyConfiguration.commonAssetPropertyEnumeratedValues ++
      databaseTransaction.withDynTransaction {
        spatialAssetDao.getEnumeratedPropertyValues(assetTypeId)
      }
  }

  def availableProperties(assetTypeId: Long): Seq[Property] = {
    (AssetPropertyConfiguration.commonAssetProperties.values.map(_.propertyDescriptor).toSeq ++ databaseTransaction.withDynTransaction {
      spatialAssetDao.availableProperties(assetTypeId)
    }).sortBy(_.propertyUiIndex)
  }

  def getMunicipalities: Seq[Int] = {
    OracleDatabase.withDynSession {
      spatialAssetDao.getMunicipalities
    }
  }

  def assetPropertyNames(language: String): Map[String, String] = {
    AssetPropertyConfiguration.assetPropertyNamesByLanguage(language) ++ databaseTransaction.withDynTransaction {
      spatialAssetDao.assetPropertyNames(language)
    }
  }
}

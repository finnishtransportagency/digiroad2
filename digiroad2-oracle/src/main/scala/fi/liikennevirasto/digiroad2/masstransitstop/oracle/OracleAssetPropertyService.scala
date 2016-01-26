package fi.liikennevirasto.digiroad2.masstransitstop.oracle

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

class OracleAssetPropertyService(eventbus: DigiroadEventBus, userProvider: UserProvider, databaseTransaction: DatabaseTransaction = DefaultDatabaseTransaction) extends AssetPropertyService {
  def getEnumeratedPropertyValues(assetTypeId: Long): Seq[EnumeratedPropertyValue] = {
    AssetPropertyConfiguration.commonAssetPropertyEnumeratedValues ++
      databaseTransaction.withDynTransaction {
        Queries.getEnumeratedPropertyValues(assetTypeId)
      }
  }

  def availableProperties(assetTypeId: Long): Seq[Property] = {
    (AssetPropertyConfiguration.commonAssetProperties.values.map(_.propertyDescriptor).toSeq ++ databaseTransaction.withDynTransaction {
      Queries.availableProperties(assetTypeId)
    }).sortBy(_.propertyUiIndex)
  }

  def assetPropertyNames(language: String): Map[String, String] = {
    AssetPropertyConfiguration.assetPropertyNamesByLanguage(language) ++ databaseTransaction.withDynTransaction {
      Queries.assetPropertyNames(language)
    }
  }
}

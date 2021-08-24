package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.{AssetPropertyConfiguration, Queries}
import fi.liikennevirasto.digiroad2.postgis.{PostGISDatabase, _}
import fi.liikennevirasto.digiroad2.user.UserProvider

trait DatabaseTransaction {
  def withDynTransaction[T](f: => T): T
}
object DefaultDatabaseTransaction extends DatabaseTransaction {
  override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
}

class AssetPropertyService(eventbus: DigiroadEventBus, userProvider: UserProvider, databaseTransaction: DatabaseTransaction = DefaultDatabaseTransaction) {
  def getEnumeratedPropertyValues(assetTypeId: Long): Seq[EnumeratedPropertyValue] = {
    AssetPropertyConfiguration.commonAssetPropertyEnumeratedValues ++
      databaseTransaction.withDynTransaction {
        Queries.getEnumeratedPropertyValues(assetTypeId)
      }
  }

  def availableProperties(assetTypeId: Long): Seq[Property] = {
    AssetPropertyConfiguration.commonAssetProperties.values.map(_.propertyDescriptor).toSeq ++ databaseTransaction.withDynTransaction {
      Queries.availableProperties(assetTypeId)
    }
  }

  def getProperties(assetTypeId: Long): Seq[Property] = {
    databaseTransaction.withDynTransaction {
      Queries.availableProperties(assetTypeId)
    }
  }

  def assetPropertyNames(language: String): Map[String, String] = {
    AssetPropertyConfiguration.assetPropertyNamesByLanguage(language) ++ databaseTransaction.withDynTransaction {
      Queries.assetPropertyNames(language)
    }
  }

  def getAssetTypeMetadata(assetTypeId: Long): Seq[Map[String, Any]] = {
    val metadataRows = databaseTransaction.withDynTransaction {
      GenericQueries.getAssetTypeMetadataRow(assetTypeId)
    }
    //order map should be replace by orderField in table
    metadataRows.groupBy(_.publicId).toSeq.sortBy(_._1)(Ordering[String]).map { case (key, rows) =>
       val row = rows.head
      Map("publicId" -> row.publicId,
        "propertyType" -> row.propertyType,
        "propertyRequired" -> row.propertyRequired,
        "propertyName" -> row.propertyName,
        "value" -> rows.map{ rowValue => if(rowValue.valueName.isEmpty) None else Map("Name" -> rowValue.valueName.get, "Value" -> rowValue.valueValue.get)})
    }
  }
}

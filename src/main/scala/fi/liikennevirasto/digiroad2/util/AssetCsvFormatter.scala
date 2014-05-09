package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.{PropertyTypes, PropertyValue, Property, AssetWithProperties}
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import fi.liikennevirasto.digiroad2.asset.oracle.OracleSpatialAssetProvider

trait AssetCsvFormatter {
  val provider = new OracleSpatialAssetProvider(new OracleUserProvider)

  protected def addStopId(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    (asset, asset.externalId.getOrElse("").toString :: result)
  }

  protected def getItemsFromPropertyByPublicId(name: String, properties: Seq[Property]) = {
    try {
      val property = properties.find(x => x.publicId == name).get
      sanitizedPropertyValues(property.propertyType, property.values)
    }
    catch {
      case e: Exception => println(s"""$name with $properties"""); throw e
    }
  }

  private def sanitizePropertyDisplayValue(displayValue: Option[String]): Option[String] = {
    displayValue.map { value => value.replace("\n", " ") }
  }

  private def sanitizedPropertyValues(propertyType: String, values: Seq[PropertyValue]): Seq[PropertyValue] = {
    propertyType match {
      case PropertyTypes.Text | PropertyTypes.LongText => values.map { value =>
        value.copy(propertyDisplayValue = sanitizePropertyDisplayValue(value.propertyDisplayValue))
      }
      case _ => values
    }
  }
}

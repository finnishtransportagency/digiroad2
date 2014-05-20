package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.{PropertyTypes, PropertyValue, Property, AssetWithProperties}
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import fi.liikennevirasto.digiroad2.asset.oracle.OracleSpatialAssetProvider
import fi.liikennevirasto.digiroad2.vallu.ValluTransformer

trait AssetCsvFormatter {
  protected def addStopId(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    (asset, asset.externalId.getOrElse("").toString :: result)
  }

  protected def getPropertyValuesByPublicId(name: String, properties: Seq[Property]): Seq[PropertyValue] = {
    ValluTransformer.getPropertyValuesByPublicId(name, properties)
  }
}

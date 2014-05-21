package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.AssetWithProperties

trait AssetCsvFormatter {
  protected def addStopId(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    (asset, asset.externalId.getOrElse("").toString :: result)
  }
}

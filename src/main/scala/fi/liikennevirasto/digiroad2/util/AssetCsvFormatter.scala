package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.NationalStop

trait AssetCsvFormatter {
  protected def addStopId[T <: NationalStop](params: (T, List[String])) = {
    val (asset, result) = params
    (asset, asset.nationalId.toString :: result)
  }
}

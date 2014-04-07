package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.AssetWithProperties

object AssetLMJFormatter {
  val fields = "stop_id,stop_name,stop_lat,stop_lon"

  def formatFromAssetWithProperties(asset: AssetWithProperties): String = {
    (addExternalId _)
     .andThen ((addName _ curried)("Nimi suomeksi")(_))
      .andThen (addXCoord _)
      .andThen (addYCoord _)
      .apply(asset, List())._2.reverse.mkString(",")
  }

  private def addExternalId(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    (asset, asset.externalId.getOrElse("").toString :: result)
  }

  private def addXCoord(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    (asset, asset.wgslon.toString :: result)
  }

  private def addYCoord(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    (asset, asset.wgslat.toString :: result)
  }

  private def addName(language: String, params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val name = AssetCsvFormatter.getItemsFromPropertyByPublicId(language, asset.propertyData)
    (asset, name.headOption.map(_.propertyDisplayValue).getOrElse("") :: result)
  }
}

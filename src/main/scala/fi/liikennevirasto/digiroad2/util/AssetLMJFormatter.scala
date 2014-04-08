package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.AssetWithProperties

object AssetLMJFormatter {
  val isolator = ","
  val fields = "stop_id,stop_name,stop_desc,stop_lat,stop_lon,zone_id,stop_url,location_type,parent_station"

  def formatFromAssetWithProperties(asset: AssetWithProperties): String = {
    (addExternalId _)
     .andThen ((addName _ curried)("Nimi suomeksi")(_))
      .andThen (addIsolator _)
      .andThen (addXCoord _)
      .andThen (addYCoord _)
      .andThen (addZoneId _)
      .andThen (addIsolator _)
      .andThen (addIsolator _)
      .andThen (addIsolator _)
      .apply(asset, List())._2.reverse.mkString(isolator)
  }

  private def addIsolator(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    (asset, "" :: result)
  }

  private def addZoneId(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    (asset, "1" :: result)
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
    val name = AssetCsvFormatter.getItemsFromPropertyByName(language, asset.propertyData)
    (asset, name.headOption.map(property => "\"" + property.propertyDisplayValue  + "\"").getOrElse("") :: result)
  }
}

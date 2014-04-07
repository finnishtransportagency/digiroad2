package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.{Property, AssetWithProperties}

/**
 * Created by exko on 04/04/14.
 */
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
    val name = getItemsFromPropertyByName(language, asset.propertyData)
    (asset, name.headOption.map(_.propertyDisplayValue).getOrElse("") :: result)
  }

  private def getItemsFromPropertyByName(name: String, property: Seq[Property]) = {
    try {
      property.find(x => x.propertyName == name).get.values
    }
    catch {
      case e: Exception => println(s"""$name with $property"""); throw e
    }
  }
}

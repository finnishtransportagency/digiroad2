package fi.liikennevirasto.digiroad2.util

import scala.collection.immutable
import fi.liikennevirasto.digiroad2.asset.{PropertyValue, AssetWithProperties}
import scala.language.postfixOps

object AssetValluCsvFormatter extends AssetCsvFormatter {
  val fields = "STOP_ID;ADMIN_STOP_ID;STOP_CODE;NAME_FI;NAME_SV;COORDINATE_X;COORDINATE_Y;ADDRESS;" +
    "ROAD_NUMBER;BEARING;BEARING_DESCRIPTION;DIRECTION;LOCAL_BUS;EXPRESS_BUS;NON_STOP_EXPRESS_BUS;" +
    "VIRTUAL_STOP;EQUIPMENT;REACHABILITY;SPECIAL_NEEDS;MODIFIED_TIMESTAMP;MODIFIED_BY;VALID_FROM;" +
    "VALID_TO;ADMINISTRATOR_CODE;MUNICIPALITY_CODE;MUNICIPALITY_NAME;COMMENTS;CONTACT_EMAILS"

  def valluCsvRowsFromAssets(assets: immutable.Iterable[AssetWithProperties], complementaryBusStopNames: Map[Long, String]): Iterable[String] = {
    assets.map(fetchNameFromValluImport(complementaryBusStopNames, _)).filterNot(isOnlyTramStop).map(formatFromAssetWithPropertiesValluCsv)
  }

  def formatAssetsWithProperties(assets: Iterable[AssetWithProperties]): Iterable[String] = {
    assets.map(formatFromAssetWithPropertiesValluCsv)
  }

  def formatFromAssetWithPropertiesValluCsv(asset: AssetWithProperties): String = {
    (addStopId _)
      .andThen (addAdminStopId _)
      .andThen (addStopCode _)
      .andThen ((addName _ curried)("nimi_suomeksi")(_))
      .andThen ((addName _ curried)("nimi_ruotsiksi")(_))
      .andThen (addXCoord _)
      .andThen (addYCoord _)
      .andThen (addAddress _)
      .andThen (addRoadNumber _)
      .andThen (addBearing _)
      .andThen (addBearingDescription _)
      .andThen (addValidityDirection _)
      .andThen (addBusStopTypes _)
      .andThen (addEquipment _)
      .andThen (addReachability _)
      .andThen (addSpecialNeeds _)
      .andThen (addModifiedInfo _)
      .andThen (addValidityPeriods _)
      .andThen (addMaintainerId _)
      .andThen (addMunicipalityInfo _)
      .andThen (addComments _)
      .andThen (addContactEmail _)
      .apply(asset, List())._2.reverse.mkString(";")
  }

  private def isOnlyTramStop(asset: AssetWithProperties): Boolean = {
    val tramStopType = 1L
    val busstopType: Seq[Long] = getItemsFromPropertyByPublicId("pysakin_tyyppi", asset.propertyData).map(property => property.propertyValue.toLong)
    busstopType.contains(tramStopType) && (busstopType.size == 1)
  }

  private def fetchNameFromValluImport(complementaryBusStopNames: Map[Long, String], asset: AssetWithProperties): AssetWithProperties = {
    asset.copy(propertyData = asset.propertyData.map { property =>
      if (property.publicId != "nimi_suomeksi") {
        property
      } else {
        val complementaryName = asset.externalId.flatMap(complementaryBusStopNames.get)
        if (property.values.isEmpty && complementaryName.isDefined) {
          property.copy(values = List(PropertyValue(propertyValue = complementaryName.get, propertyDisplayValue = Some(complementaryName.get))))
        } else {
          property
        }
      }
    })
  }
}

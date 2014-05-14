package fi.liikennevirasto.digiroad2.util

import scala.collection.immutable
import fi.liikennevirasto.digiroad2.asset.{PropertyValue, AssetWithProperties}
import scala.language.postfixOps
import org.joda.time.format.DateTimeFormat
import fi.liikennevirasto.digiroad2.asset.oracle.AssetPropertyConfiguration
import fi.liikennevirasto.digiroad2.asset.oracle.OracleSpatialAssetDao

object AssetValluCsvFormatter extends AssetCsvFormatter {
  val fields = "STOP_ID;ADMIN_STOP_ID;STOP_CODE;NAME_FI;NAME_SV;COORDINATE_X;COORDINATE_Y;ADDRESS;" +
    "ROAD_NUMBER;BEARING;BEARING_DESCRIPTION;DIRECTION;LOCAL_BUS;EXPRESS_BUS;NON_STOP_EXPRESS_BUS;" +
    "VIRTUAL_STOP;EQUIPMENT;REACHABILITY;SPECIAL_NEEDS;MODIFIED_TIMESTAMP;MODIFIED_BY;VALID_FROM;" +
    "VALID_TO;ADMINISTRATOR_CODE;MUNICIPALITY_CODE;MUNICIPALITY_NAME;COMMENTS;CONTACT_EMAILS"
  val OutputDateTimeFormat = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss")

  def valluCsvRowsFromAssets(municipalityId: Long, municipalityName: String, assets: immutable.Iterable[AssetWithProperties], complementaryBusStopNames: Map[Long, String]): Iterable[String] = {
    assets.map(fetchNameFromValluImport(complementaryBusStopNames, _)).filterNot(isOnlyTramStop).map(formatFromAssetWithPropertiesValluCsv(municipalityId, municipalityName, _))
  }

  def formatAssetsWithProperties(municipalityId: Long, municipalityName: String, assets: Iterable[AssetWithProperties]): Iterable[String] = {
    assets.map(formatFromAssetWithPropertiesValluCsv(municipalityId, municipalityName, _))
  }

  def formatFromAssetWithPropertiesValluCsv(municipalityId: Long, municipalityName: String, asset: AssetWithProperties): String = {
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
      .andThen (addMunicipalityInfo(municipalityId, municipalityName, _))
      .andThen (addComments _)
      .andThen (addContactEmail _)
      .apply(asset, List())._2.reverse.mkString(";")
  }

  private def isOnlyTramStop(asset: AssetWithProperties): Boolean = {
    val tramStopType = 1L
    val massTransitStopTypes: Seq[Long] = getPropertyValuesByPublicId("pysakin_tyyppi", asset.propertyData).map(property => property.propertyValue.toLong)
    massTransitStopTypes.contains(tramStopType) && (massTransitStopTypes.size == 1)
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

  private def addXCoord(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    (asset, asset.lon.toString :: result)
  }

  private def addYCoord(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    (asset, asset.lat.toString :: result)
  }

  private def addName(language: String, params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val name = getPropertyValuesByPublicId(language, asset.propertyData)
    (asset, name.headOption.map(_.propertyDisplayValue.getOrElse("")).getOrElse("") :: result)
  }

  private def addAdminStopId(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val id = getPropertyValuesByPublicId("yllapitajan_tunnus", asset.propertyData)
    (asset, id.headOption.map(x => x.propertyDisplayValue.getOrElse("")).getOrElse("") :: result)
  }

  private def addStopCode(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val id = getPropertyValuesByPublicId("matkustajatunnus", asset.propertyData)
    (asset, id.headOption.map(x => x.propertyDisplayValue.getOrElse("")).getOrElse("") :: result)
  }

  private def addContactEmail(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val email = getPropertyValuesByPublicId("palauteosoite", asset.propertyData)
    (asset, email.headOption.map(x => x.propertyDisplayValue.getOrElse("")).getOrElse("") :: result)
  }

  private def addComments(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val comments = getPropertyValuesByPublicId("lisatiedot", asset.propertyData)
    (asset, comments.headOption.map(x => x.propertyDisplayValue.getOrElse("")).getOrElse("") :: result)
  }

  private def addMunicipalityInfo(municipalityId: Long, municipalityName: String, params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    (asset, municipalityName :: municipalityId.toString :: result)
  }

  private def addAddress(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    // address not known
    (asset, "" :: result)
  }

  private def addRoadNumber(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    // roadnumber not known
    (asset, "" :: result)
  }

  private def addMaintainerId(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val maintainer = getPropertyValuesByPublicId("tietojen_yllapitaja", asset.propertyData)
    (asset, maintainer.headOption.map(x => x.propertyDisplayValue.getOrElse("")).getOrElse("") :: result)
  }

  private def addValidityPeriods(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val validFrom = getPropertyValuesByPublicId("ensimmainen_voimassaolopaiva", asset.propertyData)
    val validTo = getPropertyValuesByPublicId("viimeinen_voimassaolopaiva", asset.propertyData)
    (asset, validTo.head.propertyDisplayValue.map(formatOutputDate).getOrElse("") ::
      validFrom.head.propertyDisplayValue.map(formatOutputDate).getOrElse("") ::
      result)
  }

  private def addSpecialNeeds(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    // special needs not known
    (asset, "" :: result)
  }

  private def formatOutputDate(date: String): String = {
    val dateFormat = DateTimeFormat.forPattern("yyyy-MM-dd")
    OutputDateTimeFormat.print(dateFormat.parseDateTime(date))
  }

  private def addModifiedInfo(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params

    val lastModified = getPropertyValuesByPublicId("muokattu_viimeksi", asset.propertyData)
    val inserted = getPropertyValuesByPublicId("lisatty_jarjestelmaan", asset.propertyData)

    val lastModifiedValue = lastModified.head.propertyDisplayValue.getOrElse("").trim
    val insertedValue = inserted.head.propertyDisplayValue.getOrElse("").trim

    val modifiedTime =
      if(lastModifiedValue == "-")
        insertedValue.takeRight(20).trim
      else
        lastModifiedValue.takeRight(20).trim
    val modifiedBy =
      if(lastModifiedValue == "-")
        insertedValue.take(insertedValue.length - 20).trim
      else
        lastModifiedValue.take(lastModified.head.propertyDisplayValue.getOrElse("").length - 20).trim

    (asset, modifiedBy :: formatOutputDateTime(modifiedTime) :: result)
  }

  private def formatOutputDateTime(dateTime: String): String = {
    OutputDateTimeFormat.print(AssetPropertyConfiguration.Format.parseDateTime(dateTime))
  }

  private[util] def addBearing(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val validityDirection = asset.validityDirection.getOrElse(1)
    (asset, calculateActualBearing(validityDirection, asset.bearing).getOrElse("").toString :: result)
  }

  private def calculateActualBearing(validityDirection: Int, bearing: Option[Int]): Option[Int] = {
    if(validityDirection != 3) {
      bearing
    } else {
      bearing.map(_  - 180).map(x => if(x < 0) x + 360 else x)
    }
  }

  private[util] def addBearingDescription(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val bearingDescription = getPropertyValuesByPublicId("liikennointisuuntima", asset.propertyData)
    (asset, bearingDescription.headOption.map(_.propertyDisplayValue.getOrElse("")).getOrElse("") :: result)
  }

  private def addValidityDirection(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val id = getPropertyValuesByPublicId("liikennointisuunta", asset.propertyData)
    (asset, id.headOption.map(_.propertyDisplayValue.getOrElse("")).getOrElse("") :: result)
  }

  private def addReachability(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val reachability = getPropertyValuesByPublicId("esteettomyys_liikuntarajoitteiselle", asset.propertyData)
    val value = reachability.headOption.map(_.propertyDisplayValue.getOrElse("")).getOrElse("")
    (asset, (if(value.equalsIgnoreCase("ei tiedossa")) "" else value) :: result)
  }

  private def addEquipment(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val busstopShelter: Seq[Long] = getPropertyValuesByPublicId("katos", asset.propertyData).map(x => x.propertyValue.toLong)
    val shelter = (if(busstopShelter.contains(2)) "katos" else "")
    (asset, shelter :: result)
  }

  private def addBusStopTypes(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val busstopType: Seq[Long] = getPropertyValuesByPublicId("pysakin_tyyppi", asset.propertyData).map(x => x.propertyValue.toLong)
    val local = (if (busstopType.contains(2)) "1" else "0")
    val express = (if (busstopType.contains(3)) "1" else "0")
    val nonStopExpress = (if (busstopType.contains(4)) "1" else "0")
    val virtual = (if (busstopType.contains(5)) "1" else "0")
    (asset, virtual :: nonStopExpress :: express :: local :: result)
  }
}

package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.{PropertyTypes, PropertyValue, Property, AssetWithProperties}
import scala.language.postfixOps
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import fi.liikennevirasto.digiroad2.asset.oracle.{AssetPropertyConfiguration, OracleSpatialAssetProvider}
import org.joda.time.format.DateTimeFormat

object AssetCsvFormatter {

  val fields = "STOP_ID;ADMIN_STOP_ID;STOP_CODE;NAME_FI;NAME_SV;COORDINATE_X;COORDINATE_Y;ADDRESS;" +
               "ROAD_NUMBER;BEARING;BEARING_DESCRIPTION;DIRECTION;LOCAL_BUS;EXPRESS_BUS;NON_STOP_EXPRESS_BUS;" +
               "VIRTUAL_STOP;EQUIPMENT;REACHABILITY;SPECIAL_NEEDS;MODIFIED_TIMESTAMP;MODIFIED_BY;VALID_FROM;" +
               "VALID_TO;ADMINISTRATOR_CODE;MUNICIPALITY_CODE;MUNICIPALITY_NAME;COMMENTS;CONTACT_EMAILS"

  val provider = new OracleSpatialAssetProvider(new OracleUserProvider)

  val OutputDateTimeFormat = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss")

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

  private def addStopId(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    (asset, asset.externalId.getOrElse("").toString :: result)
  }

  private def addAdminStopId(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val id = getItemsFromPropertyByPublicId("yllapitajan_tunnus", asset.propertyData)
    (asset, id.headOption.map(x => x.propertyDisplayValue.getOrElse("")).getOrElse("") :: result)
  }

  private def addStopCode(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val id = getItemsFromPropertyByPublicId("matkustajatunnus", asset.propertyData)
    (asset, id.headOption.map(x => x.propertyDisplayValue.getOrElse("")).getOrElse("") :: result)
  }

  private def addContactEmail(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val email = getItemsFromPropertyByPublicId("palauteosoite", asset.propertyData)
    (asset, email.headOption.map(x => x.propertyDisplayValue.getOrElse("")).getOrElse("") :: result)
  }

  private def addComments(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val comments = getItemsFromPropertyByPublicId("lisatiedot", asset.propertyData)
    (asset, comments.headOption.map(x => x.propertyDisplayValue.getOrElse("")).getOrElse("") :: result)
  }

  private def addName(language: String, params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val name = getItemsFromPropertyByPublicId(language, asset.propertyData)
    (asset, name.headOption.map(_.propertyDisplayValue.getOrElse("")).getOrElse("") :: result)
  }

  private def addMunicipalityInfo(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val code = asset.municipalityNumber.get
    val name = provider.getMunicipalityNameByCode(code)
    (asset, name :: code.toString :: result)
  }

  private def addXCoord(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    (asset, asset.lon.toString :: result)
  }

  private def addYCoord(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    (asset, asset.lat.toString :: result)
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
    val maintainer = getItemsFromPropertyByPublicId("tietojen_yllapitaja", asset.propertyData)
    (asset, maintainer.headOption.map(x => x.propertyDisplayValue.getOrElse("")).getOrElse("") :: result)
  }

  private def addValidityPeriods(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val validFrom = getItemsFromPropertyByPublicId("ensimmainen_voimassaolopaiva", asset.propertyData)
    val validTo = getItemsFromPropertyByPublicId("viimeinen_voimassaolopaiva", asset.propertyData)
    (asset, validTo.head.propertyDisplayValue.getOrElse("") :: validFrom.head.propertyDisplayValue.getOrElse("") :: result)
  }

  def formatOutputDateTime(dateTime: String): String = {
    OutputDateTimeFormat.print(AssetPropertyConfiguration.Format.parseDateTime(dateTime))
  }

  private def addModifiedInfo(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params

    val lastModified = getItemsFromPropertyByPublicId("muokattu_viimeksi", asset.propertyData)
    val inserted = getItemsFromPropertyByPublicId("lisatty_jarjestelmaan", asset.propertyData)

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

  private[util] def addBearing(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val validityDirection = asset.validityDirection.getOrElse(1)
    (asset, calculateActualBearing(validityDirection, asset.bearing).getOrElse("").toString :: result)
  }
  
  private[this] def calculateActualBearing(validityDirection: Int, bearing: Option[Int]): Option[Int] = {
    if(validityDirection != 3) {
      bearing
    } else {
      bearing.map(_  - 180).map(x => if(x < 0) x + 360 else x)
    }
  }

  private def addSpecialNeeds(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    // special needs not known
    (asset, "" :: result)
  }

  private[util] def addBearingDescription(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val description = calculateActualBearing(asset.validityDirection.getOrElse(1), asset.bearing).getOrElse(0) match {
      case x if 46 to 135 contains x => "Itään"
      case x if 136 to 225 contains x => "Etelään"
      case x if 226 to 315 contains x => "Länteen"
      case _ => "Pohjoiseen"
    }
    (asset, description :: result)
  }

  private def addValidityDirection(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val id = getItemsFromPropertyByPublicId("liikennointisuunta", asset.propertyData)
    (asset, id.headOption.map(_.propertyDisplayValue.getOrElse("")).getOrElse("") :: result)
  }

  private def addReachability(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val reachability = getItemsFromPropertyByPublicId("esteettomyys_liikuntarajoitteiselle", asset.propertyData)
    val value = reachability.headOption.map(_.propertyDisplayValue.getOrElse("")).getOrElse("")
    (asset, (if(value.equalsIgnoreCase("ei tiedossa")) "" else value) :: result)
  }

  private def addEquipment(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val busstopShelter: Seq[Long] = getItemsFromPropertyByPublicId("katos", asset.propertyData).map(x => x.propertyValue.toLong)
    val shelter = (if(busstopShelter.contains(2)) "katos" else "")
    (asset, shelter :: result)
  }

  private def addBusStopTypes(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val busstopType: Seq[Long] = getItemsFromPropertyByPublicId("pysakin_tyyppi", asset.propertyData).map(x => x.propertyValue.toLong)
    val local = (if (busstopType.contains(2)) "1" else "0")
    val express = (if (busstopType.contains(3)) "1" else "0")
    val nonStopExpress = (if (busstopType.contains(4)) "1" else "0")
    val virtual = (if (busstopType.contains(5)) "1" else "0")
    (asset, virtual :: nonStopExpress :: express :: local :: result)
  }

  def getItemsFromPropertyByPublicId(name: String, properties: Seq[Property]) = {
    try {
      val property = properties.find(x => x.publicId == name).get
      sanitizedPropertyValues(property.propertyType, property.values)
    }
    catch {
      case e: Exception => println(s"""$name with $properties"""); throw e
    }
  }

  def sanitizePropertyDisplayValue(displayValue: Option[String]): Option[String] = {
    displayValue.map { value => value.replace("\n", " ") }
  }

  def sanitizedPropertyValues(propertyType: String, values: Seq[PropertyValue]): Seq[PropertyValue] = {
    propertyType match {
      case PropertyTypes.Text | PropertyTypes.LongText => values.map { value =>
        value.copy(propertyDisplayValue = sanitizePropertyDisplayValue(value.propertyDisplayValue))
      }
      case _ => values
    }
  }
}

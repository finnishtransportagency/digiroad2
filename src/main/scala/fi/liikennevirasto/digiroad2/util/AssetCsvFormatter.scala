package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.{PropertyTypes, PropertyValue, Property, AssetWithProperties}
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import fi.liikennevirasto.digiroad2.asset.oracle.{AssetPropertyConfiguration, OracleSpatialAssetProvider}
import org.joda.time.format.DateTimeFormat

trait AssetCsvFormatter {
  val provider = new OracleSpatialAssetProvider(new OracleUserProvider)
  val OutputDateTimeFormat = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss")

  protected def addStopId(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    (asset, asset.externalId.getOrElse("").toString :: result)
  }

  protected def addAdminStopId(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val id = getItemsFromPropertyByPublicId("yllapitajan_tunnus", asset.propertyData)
    (asset, id.headOption.map(x => x.propertyDisplayValue.getOrElse("")).getOrElse("") :: result)
  }

  protected def addStopCode(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val id = getItemsFromPropertyByPublicId("matkustajatunnus", asset.propertyData)
    (asset, id.headOption.map(x => x.propertyDisplayValue.getOrElse("")).getOrElse("") :: result)
  }

  protected def addContactEmail(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val email = getItemsFromPropertyByPublicId("palauteosoite", asset.propertyData)
    (asset, email.headOption.map(x => x.propertyDisplayValue.getOrElse("")).getOrElse("") :: result)
  }

  protected def addComments(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val comments = getItemsFromPropertyByPublicId("lisatiedot", asset.propertyData)
    (asset, comments.headOption.map(x => x.propertyDisplayValue.getOrElse("")).getOrElse("") :: result)
  }

  protected def addName(language: String, params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val name = getItemsFromPropertyByPublicId(language, asset.propertyData)
    (asset, name.headOption.map(_.propertyDisplayValue.getOrElse("")).getOrElse("") :: result)
  }

  protected def addMunicipalityInfo(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val code = asset.municipalityNumber.get
    val name = provider.getMunicipalityNameByCode(code)
    (asset, name :: code.toString :: result)
  }

  protected def addXCoord(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    (asset, asset.lon.toString :: result)
  }

  protected def addYCoord(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    (asset, asset.lat.toString :: result)
  }

  protected def addAddress(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    // address not known
    (asset, "" :: result)
  }

  protected def addRoadNumber(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    // roadnumber not known
    (asset, "" :: result)
  }

  protected def addMaintainerId(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val maintainer = getItemsFromPropertyByPublicId("tietojen_yllapitaja", asset.propertyData)
    (asset, maintainer.headOption.map(x => x.propertyDisplayValue.getOrElse("")).getOrElse("") :: result)
  }

  private def formatOutputDate(date: String): String = {
    val dateFormat = DateTimeFormat.forPattern("yyyy-MM-dd")
    OutputDateTimeFormat.print(dateFormat.parseDateTime(date))
  }

  protected def addValidityPeriods(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val validFrom = getItemsFromPropertyByPublicId("ensimmainen_voimassaolopaiva", asset.propertyData)
    val validTo = getItemsFromPropertyByPublicId("viimeinen_voimassaolopaiva", asset.propertyData)
    (asset, validTo.head.propertyDisplayValue.map(formatOutputDate).getOrElse("") ::
      validFrom.head.propertyDisplayValue.map(formatOutputDate).getOrElse("") ::
      result)
  }

  private def formatOutputDateTime(dateTime: String): String = {
    OutputDateTimeFormat.print(AssetPropertyConfiguration.Format.parseDateTime(dateTime))
  }

  protected def addModifiedInfo(params: (AssetWithProperties, List[String])) = {
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

  protected def addSpecialNeeds(params: (AssetWithProperties, List[String])) = {
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

  protected def addValidityDirection(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val id = getItemsFromPropertyByPublicId("liikennointisuunta", asset.propertyData)
    (asset, id.headOption.map(_.propertyDisplayValue.getOrElse("")).getOrElse("") :: result)
  }

  protected def addReachability(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val reachability = getItemsFromPropertyByPublicId("esteettomyys_liikuntarajoitteiselle", asset.propertyData)
    val value = reachability.headOption.map(_.propertyDisplayValue.getOrElse("")).getOrElse("")
    (asset, (if(value.equalsIgnoreCase("ei tiedossa")) "" else value) :: result)
  }

  protected def addEquipment(params: (AssetWithProperties, List[String])) = {
    val (asset, result) = params
    val busstopShelter: Seq[Long] = getItemsFromPropertyByPublicId("katos", asset.propertyData).map(x => x.propertyValue.toLong)
    val shelter = (if(busstopShelter.contains(2)) "katos" else "")
    (asset, shelter :: result)
  }

  protected def addBusStopTypes(params: (AssetWithProperties, List[String])) = {
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

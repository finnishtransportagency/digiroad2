package fi.liikennevirasto.digiroad2.vallu

import fi.liikennevirasto.digiroad2.asset.{PropertyTypes, PropertyValue, Property, AssetWithProperties}
import org.joda.time.format.{DateTimeFormat, ISODateTimeFormat}

object ValluStoreStopChangeMessage {

  def create(municipalityName: String, asset: AssetWithProperties): String = {
    """<?xml version="1.0" encoding="UTF-8"?>""" +
    (<Stops xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
      <Stop>
        <StopId>{asset.externalId.get}</StopId>
        <AdminStopId>{if (propertyIsDefined(asset, "yllapitajan_tunnus")) extractPropertyValue(asset, "yllapitajan_tunnus") }</AdminStopId>
        <StopCode>{if (propertyIsDefined(asset, "matkustajatunnus")) extractPropertyValue(asset, "matkustajatunnus") }</StopCode>
        { if (localizedNameIsDefined(asset))
            <Names>
            { if (propertyIsDefined(asset, "nimi_suomeksi")) <Name lang="fi">{extractPropertyValue(asset, "nimi_suomeksi") }</Name> }
            { if (propertyIsDefined(asset, "nimi_ruotsiksi")) <Name lang="sv">{extractPropertyValue(asset, "nimi_ruotsiksi") }</Name> }
            </Names>
        }
        <Coordinate>
          <xCoordinate>{asset.wgslon.toInt}</xCoordinate>
          <yCoordinate>{asset.wgslat.toInt}</yCoordinate>
        </Coordinate>
        <Bearing>{asset.bearing.flatMap { bearing =>
          asset.validityDirection.map { validityDirection =>
            ValluTransformer.calculateActualBearing(validityDirection, bearing)
          }
        }.getOrElse("")}</Bearing>
        { if (propertyIsDefined(asset, "liikennointisuuntima")) <BearingDescription>{ extractPropertyDisplayValue(asset, "liikennointisuuntima") }</BearingDescription>}
        { if (propertyIsDefined(asset, "liikennointisuunta")) <Direction>{extractPropertyValue(asset, "liikennointisuunta") }</Direction> }
        <StopAttribute>
          { val busStopTypes = getBusStopTypes(asset)
          <StopType name="LOCAL_BUS">{busStopTypes._1}</StopType>
          <StopType name="EXPRESS_BUS">{busStopTypes._2}</StopType>
          <StopType name="NON_STOP_EXPRESS_BUS">{busStopTypes._3}</StopType>
          <StopType name="VIRTUAL_STOP">{busStopTypes._4}</StopType> }
        </StopAttribute>
        <Equipment>{getEquipment(asset)}</Equipment>
        <Reachability>{getReachability(asset)}</Reachability>
        <SpecialNeeds>{if (propertyIsDefined(asset, "esteettomyys_liikuntarajoitteiselle")) extractPropertyValue(asset, "esteettomyys_liikuntarajoitteiselle") }</SpecialNeeds>
        { val modification = asset.modified.modificationTime match {
            case Some(_) => asset.modified
            case _ => asset.created
          }
          <ModifiedTimestamp>{ISODateTimeFormat.dateHourMinuteSecond.print(modification.modificationTime.get)}</ModifiedTimestamp>
          <ModifiedBy>{modification.modifier.get}</ModifiedBy>
        }
        {if (! propertyIsDefined(asset, "ensimmainen_voimassaolopaiva") || propertyIsEmpty(asset, "ensimmainen_voimassaolopaiva"))
            <ValidFrom xsi:nil="true" />
         else
            <ValidFrom>{ValluTransformer.transformToISODate(extractPropertyValueOption(asset, "ensimmainen_voimassaolopaiva"))}</ValidFrom>}
        {if (! propertyIsDefined(asset, "viimeinen_voimassaolopaiva") || propertyIsEmpty(asset, "viimeinen_voimassaolopaiva"))
          <ValidTo xsi:nil="true" />
         else
          <ValidTo>{ValluTransformer.transformToISODate(extractPropertyValueOption(asset, "viimeinen_voimassaolopaiva"))}</ValidTo>}
        <AdministratorCode>{extractPropertyDisplayValue(asset, "tietojen_yllapitaja")}</AdministratorCode>
        <MunicipalityCode>{asset.municipalityNumber.get}</MunicipalityCode>
        <MunicipalityName>{municipalityName}</MunicipalityName>
        <Comments>{if (propertyIsDefined(asset, "lisatiedot")) extractPropertyValue(asset, "lisatiedot") }</Comments>
        <ContactEmails>
          <Contact>pysakit@liikennevirasto.fi</Contact>
        </ContactEmails>
      </Stop>
    </Stops>).toString()
  }

  private def getBusStopTypes(asset: AssetWithProperties) =  {
    val busstopType: Seq[Long] = getPropertyValuesByPublicId("pysakin_tyyppi", asset.propertyData).map(x => x.propertyValue.toLong)
    println(busstopType)
    val local = (if (busstopType.contains(2)) "1" else "0")
    val express = (if (busstopType.contains(3)) "1" else "0")
    val nonStopExpress = (if (busstopType.contains(4)) "1" else "0")
    val virtual = (if (busstopType.contains(5)) "1" else "0")
    (local, express, nonStopExpress, virtual)
  }

  protected def getPropertyValuesByPublicId(name: String, properties: Seq[Property]): Seq[PropertyValue] = {
    try {
      val property = properties.find(x => x.publicId == name).get
      sanitizedPropertyValues(property.propertyType, property.values)
    }
    catch {
      case e: Exception => println(s"""$name with $properties"""); throw e
    }
  }

  private def sanitizedPropertyValues(propertyType: String, values: Seq[PropertyValue]): Seq[PropertyValue] = {
    propertyType match {
      case PropertyTypes.Text | PropertyTypes.LongText => values.map { value =>
        value.copy(propertyDisplayValue = sanitizePropertyDisplayValue(value.propertyDisplayValue))
      }
      case _ => values
    }
  }

  private def sanitizePropertyDisplayValue(displayValue: Option[String]): Option[String] = {
    displayValue.map { value => value.replace("\n", " ") }
  }

  private def getEquipment(asset: AssetWithProperties): String = {
    val result = List(
      extractPropertyValueOption(asset, "aikataulu").map { continuousParking =>
        if (continuousParking == "2") "Aikataulu" else ""
      }.filterNot(_.isEmpty),
      extractPropertyValueOption(asset, "katos").map { continuousParking =>
        if (continuousParking == "2") "Katos" else ""
      }.filterNot(_.isEmpty),
      extractPropertyValueOption(asset, "mainoskatos").map { continuousParking =>
        if (continuousParking == "2") "Mainoskatos" else ""
      }.filterNot(_.isEmpty),
      extractPropertyValueOption(asset, "penkki").map { continuousParking =>
        if (continuousParking == "2") "Penkki" else ""
      }.filterNot(_.isEmpty),
      extractPropertyValueOption(asset, "pyorateline").map { continuousParking =>
        if (continuousParking == "2") "Pyöräteline" else ""
      }.filterNot(_.isEmpty),
      extractPropertyValueOption(asset, "sahkoinen_aikataulunaytto").map { continuousParking =>
        if (continuousParking == "2") "Sähköinen aikataulunnäyttö" else ""
      }.filterNot(_.isEmpty),extractPropertyValueOption(asset, "valaistus").map { continuousParking =>
        if (continuousParking == "2") "Valaistus" else ""
      }.filterNot(_.isEmpty)).flatten.mkString(", ")
      result
  }

  private def getReachability(asset: AssetWithProperties): String = {
    val result = List(
      extractPropertyValueOption(asset, "saattomahdollisuus_henkiloautolla").map { continuousParking =>
          if (continuousParking == "2") "Liityntäpysäköinti" else ""
      }.filterNot(_.isEmpty),
      extractPropertyValueOption(asset, "liityntapysakointipaikkojen_maara").map { parkingSpaces =>
        parkingSpaces + " pysäköintipaikkaa"
      },
      extractPropertyValueOption(asset, "liityntapysakoinnin_lisatiedot")).flatten.mkString(", ")
    result
  }

  private def localizedNameIsDefined(asset: AssetWithProperties): Boolean = {
    propertyIsDefined(asset, "nimi_suomeksi") || propertyIsDefined(asset, "nimi_ruotsiksi")
  }

  private def propertyIsDefined(asset: AssetWithProperties, propertyPublicId: String): Boolean = {
   extractPropertyValueOption(asset, propertyPublicId).isDefined
  }

  private def propertyIsEmpty(asset: AssetWithProperties, propertyPublicId: String): Boolean = {
    propertyIsDefined(asset, propertyPublicId) && extractPropertyValue(asset, propertyPublicId).isEmpty
  }

  private def extractPropertyValue(asset: AssetWithProperties, propertyPublicId: String): String = {
    extractPropertyValueOption(asset, propertyPublicId).get
  }

  private def extractPropertyValueOption(asset: AssetWithProperties, propertyPublicId: String): Option[String] = {
    asset.propertyData
      .find(property => property.publicId == propertyPublicId)
      .flatMap(property => property.values.headOption)
      .map(value => value.propertyValue)
  }

  private def extractPropertyDisplayValue(asset: AssetWithProperties, propertyPublicId: String): String = {
    asset.propertyData.find(property => property.publicId == propertyPublicId)
      .head.values.head.propertyDisplayValue.get
  }
}

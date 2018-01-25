package fi.liikennevirasto.digiroad2.vallu

import fi.liikennevirasto.digiroad2.EventBusMassTransitStop
import fi.liikennevirasto.digiroad2.asset.Property
import fi.liikennevirasto.digiroad2.util.AssetPropertiesReader
import fi.liikennevirasto.digiroad2.vallu.ValluTransformer._
import org.joda.time.format.ISODateTimeFormat
import scala.language.reflectiveCalls

object ValluStoreStopChangeMessage extends AssetPropertiesReader {
  def create(stop: EventBusMassTransitStop): String = {
    """<?xml version="1.0" encoding="UTF-8"?>""" +
    <Stops xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
      <Stop>
        <StopId>{stop.nationalId}</StopId>
        <AdminStopId>{if (propertyIsDefined(stop, "yllapitajan_tunnus")) extractPropertyValue(stop, "yllapitajan_tunnus") }</AdminStopId>
        <StopCode>{if (propertyIsDefined(stop, "matkustajatunnus")) extractPropertyValue(stop, "matkustajatunnus") }</StopCode>
        { if (localizedNameIsDefined(stop))
            <Names>
            { if (propertyIsDefined(stop, "nimi_suomeksi")) <Name lang="fi">{extractPropertyValue(stop, "nimi_suomeksi") }</Name> }
            { if (propertyIsDefined(stop, "nimi_ruotsiksi")) <Name lang="sv">{extractPropertyValue(stop, "nimi_ruotsiksi") }</Name> }
            </Names>
        }
        <Coordinate>
          <xCoordinate>{stop.lon.toInt}</xCoordinate>
          <yCoordinate>{stop.lat.toInt}</yCoordinate>
        </Coordinate>
        <Bearing>{stop.bearing.flatMap { bearing =>
          stop.validityDirection.map { validityDirection =>
            calculateActualBearing(validityDirection, bearing)
          }
        }.getOrElse("")}</Bearing>
        { if (propertyIsDefined(stop, "liikennointisuuntima")) <BearingDescription>{ extractPropertyDisplayValue(stop, "liikennointisuuntima") }</BearingDescription>}
        { if (propertyIsDefined(stop, "liikennointisuunta")) <Direction>{extractPropertyValue(stop, "liikennointisuunta") }</Direction> }
        <StopAttribute>{getBusstopBlock(stop)}</StopAttribute>
        <Equipment>{describeEquipments(stop)}</Equipment>
        <Reachability>{describeReachability(stop)}</Reachability>
        <SpecialNeeds>{if (propertyIsDefined(stop, "esteettomyys_liikuntarajoitteiselle")) extractPropertyValue(stop, "esteettomyys_liikuntarajoitteiselle") }</SpecialNeeds>
        { val modification = stop.modified.modificationTime match {
            case Some(_) => stop.modified
            case _ => stop.created
          }
          <ModifiedTimestamp>{ISODateTimeFormat.dateHourMinuteSecond.print(modification.modificationTime.get)}</ModifiedTimestamp>
          <ModifiedBy>{modification.modifier.get}</ModifiedBy>
        }
        {if (! propertyIsDefined(stop, "ensimmainen_voimassaolopaiva") || propertyIsEmpty(stop, "ensimmainen_voimassaolopaiva"))
            <ValidFrom xsi:nil="true" />
         else
            <ValidFrom>{transformToISODate(extractPropertyValueOption(stop, "ensimmainen_voimassaolopaiva"))}</ValidFrom>}
        {if (! propertyIsDefined(stop, "viimeinen_voimassaolopaiva") || propertyIsEmpty(stop, "viimeinen_voimassaolopaiva"))
          <ValidTo xsi:nil="true" />
         else
          <ValidTo>{transformToISODate(extractPropertyValueOption(stop, "viimeinen_voimassaolopaiva"))}</ValidTo>}
        <AdministratorCode>{if (propertyIsDefined(stop, "tietojen_yllapitaja")) extractPropertyDisplayValue(stop, "tietojen_yllapitaja") else "Ei tiedossa" }</AdministratorCode>
        <MunicipalityCode>{stop.municipalityNumber}</MunicipalityCode>
        <MunicipalityName>{stop.municipalityName}</MunicipalityName>
        <Comments>{if (propertyIsDefined(stop, "lisatiedot")) extractPropertyValue(stop, "lisatiedot") }</Comments>
        <PlatformCode>{if (propertyIsDefined(stop, "laiturinumero")) extractPropertyValue(stop, "laiturinumero")}</PlatformCode>
        <ConnectedToTerminal>{extractPropertyValueOption(stop, "liitetty_terminaaliin_ulkoinen_tunnus").getOrElse("")}</ConnectedToTerminal>
        <ContactEmails>
          <Contact>pysakit@digiroad.fi</Contact>
        </ContactEmails>
      </Stop>
    </Stops>.toString
  }

  def getBusstopBlock(asset: {val propertyData: Seq[Property]}) = {
    val busStopTypes = getPropertyValuesByPublicId("pysakin_tyyppi", asset.propertyData).map(x => x.propertyValue.toLong)
    busStopTypes.map { busStopType =>
      <StopType>{busStopType}</StopType>
    }
  }

  private def localizedNameIsDefined(asset: {val propertyData: Seq[Property]}): Boolean = {
    propertyIsDefined(asset, "nimi_suomeksi") || propertyIsDefined(asset, "nimi_ruotsiksi")
  }

  private def propertyIsDefined(asset: {val propertyData: Seq[Property]}, propertyPublicId: String): Boolean = {
    extractPropertyValueOption(asset, propertyPublicId).isDefined
  }

  private def propertyIsEmpty(asset: {val propertyData: Seq[Property]}, propertyPublicId: String): Boolean = {
    propertyIsDefined(asset, propertyPublicId) && extractPropertyValue(asset, propertyPublicId).isEmpty
  }

  private def extractPropertyValue(asset: {val propertyData: Seq[Property]}, propertyPublicId: String): String = {
    extractPropertyValueOption(asset, propertyPublicId).get
  }

  private def extractPropertyDisplayValue(asset: {val propertyData: Seq[Property]}, propertyPublicId: String): String = {
    asset.propertyData.find(property => property.publicId == propertyPublicId)
      .head.values.head.propertyDisplayValue.get
  }
}

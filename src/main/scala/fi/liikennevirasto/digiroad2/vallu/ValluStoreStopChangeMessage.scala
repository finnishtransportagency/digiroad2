package fi.liikennevirasto.digiroad2.vallu

import fi.liikennevirasto.digiroad2.asset.{AssetWithProperties}

object ValluStoreStopChangeMessage {

  def create(municipalityName: String, asset: AssetWithProperties): String = {
    """<?xml version="1.0" encoding="UTF-8"?>""" +
    (<Stops>
      <Stop>
        <StopId>{asset.externalId.get}</StopId>
        { if (propertyIsDefined(asset, "yllapitajan_tunnus")) <AdminStopId>{extractPropertyValue(asset, "yllapitajan_tunnus") }</AdminStopId> }
        { if (propertyIsDefined(asset, "matkustajatunnus")) <StopCode>{extractPropertyValue(asset, "matkustajatunnus") }</StopCode> }
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
        <StopAttribute>
          <StopType name="LOCAL_BUS">0</StopType>
          <StopType name="EXPRESS_BUS">1</StopType>
          <StopType name="NON_STOP_EXPRESS_BUS">0</StopType>
          <StopType name="VIRTUAL_STOP">0</StopType>
        </StopAttribute>
        <Equipment/>
        <ModifiedBy>Digiroad 2 app</ModifiedBy>
        <MunicipalityName>{municipalityName}</MunicipalityName>
        <ContactEmails>
          <Contact>rewre@gfdgfd.fi</Contact>
        </ContactEmails>
      </Stop>
    </Stops>).toString()
  }

  private def localizedNameIsDefined(asset: AssetWithProperties): Boolean = {
    propertyIsDefined(asset, "nimi_suomeksi") || propertyIsDefined(asset, "nimi_ruotsiksi")
  }

  private def propertyIsDefined(asset: AssetWithProperties, propertyPublicId: String): Boolean = {
   extractPropertyValueOption(asset, propertyPublicId).isDefined
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
}

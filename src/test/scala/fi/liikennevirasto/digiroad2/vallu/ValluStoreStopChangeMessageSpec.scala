package fi.liikennevirasto.digiroad2.vallu

import org.scalatest._
import fi.liikennevirasto.digiroad2.asset.AssetWithProperties

class ValluStoreStopChangeMessageSpec extends FlatSpec with MustMatchers {
  it must "create containing element for stops" in {
    val message: String = ValluStoreStopChangeMessage.create(AssetWithProperties(
      id = 1,
      externalId = None,
      assetTypeId = 1,
      lon = 1,
      lat = 1,
      roadLinkId = 1,
      wgslon = 1,
      wgslat = 1))

    message must equal(
      """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<Stops>
        |</Stops>""".stripMargin)
  }
}

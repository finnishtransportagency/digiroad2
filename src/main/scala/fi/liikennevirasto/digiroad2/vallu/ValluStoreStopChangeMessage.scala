package fi.liikennevirasto.digiroad2.vallu

import fi.liikennevirasto.digiroad2.asset.AssetWithProperties

object ValluStoreStopChangeMessage {
  def create(asset: AssetWithProperties): String = {
    """
      |<?xml version="1.0" encoding="UTF-8"?>
      |<Stops>
      |</Stops>""".stripMargin
  }
}

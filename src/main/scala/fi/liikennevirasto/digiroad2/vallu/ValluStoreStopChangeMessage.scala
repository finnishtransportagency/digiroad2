package fi.liikennevirasto.digiroad2.vallu

import fi.liikennevirasto.digiroad2.asset.AssetWithProperties

object ValluStoreStopChangeMessage {
  def create(asset: AssetWithProperties): String = {
      val message =
      <Stops>
        <StopId>{asset.externalId.get}</StopId>
      </Stops>
    """<?xml version="1.0" encoding="UTF-8"?>""" + message.toString
  }
}

package fi.liikennevirasto.digiroad2.client.viite

import fi.liikennevirasto.digiroad2.util.Digiroad2Properties

class ViiteAuthPropertyReader {


  def getViiteApiKey: String = {
    Digiroad2Properties.viiteApiKey
  }

}
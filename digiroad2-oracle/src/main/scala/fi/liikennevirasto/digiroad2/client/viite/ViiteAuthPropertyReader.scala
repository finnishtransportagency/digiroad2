package fi.liikennevirasto.digiroad2.client.viite

import fi.liikennevirasto.digiroad2.util.Digiroad2Properties
import org.apache.commons.codec.binary.Base64

class ViiteAuthPropertyReader {

  private def getUsername: String = {
    Digiroad2Properties.viiteUsername
  }

  private def getPassword: String = {
    Digiroad2Properties.viitePassword
  }

  def getViiteApiKey: String = {
    Digiroad2Properties.viiteApiKey
  }

  def getAuthInBase64: String = {
    Base64.encodeBase64String((getUsername + ":" + getPassword).getBytes)
  }

}
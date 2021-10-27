package fi.liikennevirasto.digiroad2.util

import java.util.Properties

class SmtpPropertyReader {

  def getDestination: String = {
    val loadedKeyString = Digiroad2Properties.emailTo
    if (loadedKeyString == null)
      throw new IllegalArgumentException("Missing email Password")
    loadedKeyString
  }


  def getHost: String = {
    val loadedKeyString = Digiroad2Properties.emailHost
    if (loadedKeyString == null)
      throw new IllegalArgumentException("Missing email Password")
    loadedKeyString
  }


  def getPort: String = {
    val loadedKeyString = Digiroad2Properties.emailPort
    if (loadedKeyString == null)
      throw new IllegalArgumentException("Missing email Password")
    loadedKeyString
  }
}

package fi.liikennevirasto.viite.util

import java.util.Properties

import org.apache.commons.codec.binary.Base64

class ViiteTierekisteriAuthPropertyReader {

  lazy val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/keys.properties"))
    props
  }

  private def getUsername: String = {
    val loadedKeyString = properties.getProperty("viitetierekisteri.username")
    if (loadedKeyString == null)
      throw new IllegalArgumentException("Missing TR username")
    loadedKeyString
  }

  private def getPassword: String = {
    val loadedKeyString = properties.getProperty("viitetierekisteri.password")
    if (loadedKeyString == null)
      throw new IllegalArgumentException("Missing TR Password")
    loadedKeyString
  }

  def getAuthInBase64: String = {
    Base64.encodeBase64String((getUsername + ":" + getPassword).getBytes)
  }
}

package fi.liikennevirasto.digiroad2.client.viite

import java.util.Properties

import org.apache.commons.codec.binary.Base64

class ViiteAuthPropertyReader {

  lazy val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/keys.properties"))
    props
  }

  private def getPropertyValue(name: String) : String = {
    val loadedKeyString = properties.getProperty(name)
    if (loadedKeyString == null)
      throw new IllegalArgumentException(s"Missing $name")
    loadedKeyString
  }

  private def getUsername: String = {
    getPropertyValue("viite.username")
  }

  private def getPassword: String = {
    getPropertyValue("viite.password")
  }

  def getAuthInBase64: String = {
    Base64.encodeBase64String((getUsername + ":" + getPassword).getBytes)
  }

}

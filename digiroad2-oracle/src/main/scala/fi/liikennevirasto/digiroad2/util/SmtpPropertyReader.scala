package fi.liikennevirasto.digiroad2.util

import java.util.Properties

class SmtpPropertyReader {

  lazy val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/smtp.properties"))
    props
  }

  def getDestination: String = {
    val loadedKeyString = properties.getProperty("email.to")
    if (loadedKeyString == null)
      throw new IllegalArgumentException("Missing email Password")
    loadedKeyString
  }


  def getHost: String = {
    val loadedKeyString = properties.getProperty("email.host")
    if (loadedKeyString == null)
      throw new IllegalArgumentException("Missing email Password")
    loadedKeyString
  }


  def getPort: String = {
    val loadedKeyString = properties.getProperty("email.port")
    if (loadedKeyString == null)
      throw new IllegalArgumentException("Missing email Password")
    loadedKeyString
  }

}
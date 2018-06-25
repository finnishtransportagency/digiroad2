package fi.liikennevirasto.digiroad2.util

import java.util.Properties

class EmailAuthPropertyReader {

  lazy val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/keys.properties"))
    props
  }

  def getUsername: String = {
    val loadedKeyString = properties.getProperty("email.username")
    if (loadedKeyString == null)
      throw new IllegalArgumentException("Missing email username")
    loadedKeyString
  }

  def getPassword: String = {
    val loadedKeyString = properties.getProperty("email.password")
    if (loadedKeyString == null)
      throw new IllegalArgumentException("Missing email Password")
    loadedKeyString
  }
}
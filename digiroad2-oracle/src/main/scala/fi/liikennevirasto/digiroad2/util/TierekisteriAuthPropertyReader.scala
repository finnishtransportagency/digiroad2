package fi.liikennevirasto.digiroad2.util

import java.util.Properties
import org.apache.commons.codec.binary.Base64

class TierekisteriAuthPropertyReader {

  lazy val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/keys.properties"))
    props
  }

  private def getUsername: String = {
    val loadedKeyString = properties.getProperty("tierekisteri.username")
    println("u = "+loadedKeyString)
    if (loadedKeyString == null)
      throw new IllegalArgumentException("Missing TR username")
    loadedKeyString
  }

  private def getPassword: String = {
    val loadedKeyString = properties.getProperty("tierekisteri.password")
    if (loadedKeyString == null)
      throw new IllegalArgumentException("Missing TR Password")
    loadedKeyString
  }

  private def getOldUsername: String = {
    val loadedKeyString = properties.getProperty("tierekisteri.old.username")
    println("u = "+loadedKeyString)
    if (loadedKeyString == null)
      throw new IllegalArgumentException("Missing TR username")
    loadedKeyString
  }

  private def getOldPassword: String = {
    val loadedKeyString = properties.getProperty("tierekisteri.old.password")
    if (loadedKeyString == null)
      throw new IllegalArgumentException("Missing TR Password")
    loadedKeyString
  }

  def getAuthInBase64: String = {
    Base64.encodeBase64String((getUsername + ":" + getPassword).getBytes)
  }

  def getOldAuthInBase64: String = {
    Base64.encodeBase64String((getOldUsername + ":" + getOldPassword).getBytes)
  }
}

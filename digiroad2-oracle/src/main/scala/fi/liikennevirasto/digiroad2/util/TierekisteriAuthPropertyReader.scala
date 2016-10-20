package fi.liikennevirasto.digiroad2.util

import java.util.Properties
import org.apache.commons.codec.binary.Base64



class TierekisteriAuthPropertyReader {

  lazy val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/keys.properties"))
    props
  }

  private def getUsername(): String = {

    val loadedkeyString = properties.getProperty("tierekisteri.username")
    if (loadedkeyString == null)
      throw new IllegalArgumentException("Missing TR username")
    loadedkeyString
  }

  private def getPassword(): String = {
    val loadedkeyString =properties.getProperty("tierekisteri.password")
    if (loadedkeyString == null)
      throw new IllegalArgumentException("Missing TR Password")
    loadedkeyString
  }

  def getAuthinBase64(): String = {
 Base64.encodeBase64String((getUsername + ":" + getPassword).getBytes)
  }
}

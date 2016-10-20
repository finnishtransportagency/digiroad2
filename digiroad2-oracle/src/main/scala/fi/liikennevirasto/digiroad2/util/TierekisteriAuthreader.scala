package fi.liikennevirasto.digiroad2.util

import java.util.Properties
import org.apache.commons.codec.binary.Base64



class TierekisteriAuthreader {

  lazy val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/keys.properties"))
  }

  private def getUsername(): String = {
    properties.getProperty("tierekisteri.client_id")
  }

  private def getPassword(): String = {
    properties.getProperty("tierekisteri.client_id")
  }

  def getAuthinBase64(): String = {
    Base64.encodeBase64((getUsername + ":" + getPassword).getBytes())
    }
}

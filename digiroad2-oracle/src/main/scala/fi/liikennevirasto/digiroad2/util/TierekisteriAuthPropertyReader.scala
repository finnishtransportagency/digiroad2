package fi.liikennevirasto.digiroad2.util

import java.util.Properties
import org.apache.commons.codec.binary.Base64

class TierekisteriAuthPropertyReader {

  private def getUsername: String = {
    val loadedKeyString = Digiroad2Properties.tierekisteriUsername
    println("u = "+loadedKeyString)
    if (loadedKeyString == null)
      throw new IllegalArgumentException("Missing TR username")
    loadedKeyString
  }

  private def getPassword: String = {
    val loadedKeyString = Digiroad2Properties.tierekisteriPassword
    if (loadedKeyString == null)
      throw new IllegalArgumentException("Missing TR Password")
    loadedKeyString
  }

  private def getOldUsername: String = {
    val loadedKeyString = Digiroad2Properties.tierekisteriOldUsername
    println("u = "+loadedKeyString)
    if (loadedKeyString == null)
      throw new IllegalArgumentException("Missing TR username")
    loadedKeyString
  }

  private def getOldPassword: String = {
    val loadedKeyString = Digiroad2Properties.tierekisteriOldPassword
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

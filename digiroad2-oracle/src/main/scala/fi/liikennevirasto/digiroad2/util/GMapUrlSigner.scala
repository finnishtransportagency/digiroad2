package fi.liikennevirasto.digiroad2.util

import java.util.{Base64, Properties}
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class GMapUrlSigner
{
  //TODO make it fail if file exists, but doesnt have keys
  var propertiesread=true
  lazy val properties: Properties =
  {
    //Property loader. Should load from digiroad2-oracle.conf.dev folder
    val props = new Properties()
      props.load(getClass.getResourceAsStream("/keys.properties"))
    props
  }

  val key: Array[Byte] =
  {
    // Load & converts the key from 'web safe' base 64 to binary.
    var loadedkeyString = properties.getProperty("googlemapapi.crypto_key")
    if (loadedkeyString == null)
      loadedkeyString = "Av2W1c-qJKih0B8cg0e8YH6hkYs="
    val wskey = loadedkeyString.replace('-', '+').replace('_', '/')
    // Base64 is JDK +1.8 only - older versions may need to use Apache Commons or similar.
    Base64.getDecoder().decode(wskey)
  }

  val clientid = properties.getProperty("googlemapapi.client_id")

  def signRequest(wgsX: String, wgsY: String): String =
  {
    // Retrieve the proper URL components to sign
    val resource = s"/maps/api/streetview?location=$wgsX,$wgsY&size=360x180&client=$clientid"
    // Get an HMAC-SHA1 Mac instance and initialize it with the HMAC-SHA1 key
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(new SecretKeySpec(key, "HmacSHA1"))
    // compute the binary signature for the request
    val sigBytes = mac.doFinal(resource.getBytes())
    // Base64 is JDK 1.8 only - older versions may need to use Apache Commons or similar. Convert the signature to 'web safe' base 64
    val signature = (Base64.getEncoder().encodeToString(sigBytes)).replace('+', '-').replace('/', '_')
    "https://maps.googleapis.com" + resource + "&signature=" + signature
  }
}

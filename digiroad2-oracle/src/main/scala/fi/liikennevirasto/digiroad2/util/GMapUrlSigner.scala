package fi.liikennevirasto.digiroad2.util
import java.util.Properties
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.apache.commons.codec.binary.Base64

class GMapUrlSigner
{
  val key: Array[Byte] =
  {
    // Load & converts the key from 'web safe' base 64 to binary.
    val loadedkeyString = Digiroad2Properties.googleMapApiCryptoKey
    if (loadedkeyString == null)
      throw new IllegalArgumentException("Missing Google Crypto-key")
    val wskey = loadedkeyString.replace('-', '+').replace('_', '/')
    // Base64 is JDK +1.8 only - older versions may need to use Apache Commons or similar.
    Base64.decodeBase64(wskey)
  }

  val clientid = Digiroad2Properties.googleMapApiClientId
  if (clientid == null)
    throw new IllegalArgumentException("Missing Client id")

  def signRequest(wgsX: String, wgsY: String,heading:String): String =
  {
    // Retrieve URL components to sign
    val resource = s"/maps/api/streetview?location=$wgsX,$wgsY&size=360x180&client=$clientid&fov=110&heading=$heading&pitch=-10&sensor=false"
    // Get an HMAC-SHA1 Mac instance and initialize it with the HMAC-SHA1 key
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(new SecretKeySpec(key, "HmacSHA1"))
    // compute the binary signature for the request
    val sigBytes = mac.doFinal(resource.getBytes())
    // Base64 is JDK 1.8 only - older versions may need to use Apache Commons or similar. Convert the signature to 'web safe' base 64
    val signature = Base64.encodeBase64URLSafeString(sigBytes)
    "https://maps.googleapis.com" + resource + "&signature=" + signature
  }
}
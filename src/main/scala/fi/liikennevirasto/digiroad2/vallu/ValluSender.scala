package fi.liikennevirasto.digiroad2.vallu

import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.{StringEntity, ContentType}
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import java.nio.charset.Charset

object ValluSender {

  // TODO: read from config
  val httppost = new HttpPost("http://localhost:9002")
  val httpclient = HttpClients.createDefault()

  // TODO: use XML Element
  def postToVallu(payload: String) = {
    val entity = new StringEntity(payload, ContentType.create("text/plain", "UTF-8"))
    httppost.setEntity(entity)
    val response = httpclient.execute(httppost)
    try {
      // TODO: println "handling" out
      println(EntityUtils.toString(response.getEntity , Charset.forName("UTF-8")))
      EntityUtils.consume(entity)
    } finally {
      response.close()
    }
  }
}

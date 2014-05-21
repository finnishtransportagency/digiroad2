package fi.liikennevirasto.digiroad2.vallu

import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.{StringEntity, ContentType}
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import java.nio.charset.Charset
import fi.liikennevirasto.digiroad2.asset.AssetWithProperties
import org.slf4j.LoggerFactory

object ValluSender {
  val messageLogger = LoggerFactory.getLogger("ValluMsgLogger")
  val applicationLogger = LoggerFactory.getLogger(getClass)

  // TODO: read from config
  val httpPost = new HttpPost("http://localhost:9002")
  val httpClient = HttpClients.createDefault()

  def postToVallu(municipalityName: String, asset: AssetWithProperties) {
    val payload = ValluStoreStopChangeMessage.create(municipalityName, asset)
    withLogging(payload) {
      postToVallu
    }
  }

  private def postToVallu(payload: String) = {
    val entity = new StringEntity(payload, ContentType.create("text/xml", "UTF-8"))
    httpPost.setEntity(entity)
    val response = httpClient.execute(httpPost)
    try {
      // TODO: println "handling" out
      println(EntityUtils.toString(response.getEntity , Charset.forName("UTF-8")))
      EntityUtils.consume(entity)
    } finally {
      response.close()
    }
  }

  def withLogging[A](payload: String)(thunk: String => Unit) {
    try {
      thunk(payload)
      messageLogger.info(payload)
    } catch {
      case e: Exception => {
        applicationLogger.error("Error occurred", e)
        messageLogger.error("=====Error in sending Message to Vallu, message below ======")
        messageLogger.error(payload)
      }
    }
  }
}

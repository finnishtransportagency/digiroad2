package fi.liikennevirasto.digiroad2.vallu

import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.{StringEntity, ContentType}
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import java.nio.charset.Charset
import fi.liikennevirasto.digiroad2.asset.AssetWithProperties
import org.slf4j.LoggerFactory
import fi.liikennevirasto.digiroad2.Digiroad2Context

object ValluSender {
  val messageLogger = LoggerFactory.getLogger("ValluMsgLogger")
  val applicationLogger = LoggerFactory.getLogger(getClass)
  val sendingEnabled = Digiroad2Context.getProperty("digiroad2.vallu.server.sending_enabled").toBoolean
  val address = Digiroad2Context.getProperty("digiroad2.vallu.server.address")
  val httpClient = HttpClients.createDefault()

  def postToVallu(municipalityName: String, asset: AssetWithProperties) {
    val payload = ValluStoreStopChangeMessage.create(municipalityName, asset)
    withLogging(payload) {
      postToVallu
    }
  }

  private def postToVallu(payload: String) = {
    val entity = new StringEntity(payload, ContentType.create("text/xml", "UTF-8"))
    val httpPost = new HttpPost(address)
    httpPost.setEntity(entity)
    val response = httpClient.execute(httpPost)
    try {
      messageLogger.info(s"Got response ${EntityUtils.toString(response.getEntity , Charset.forName("UTF-8"))}")
      EntityUtils.consume(entity)
    } finally {
      response.close()
    }
  }

  def withLogging[A](payload: String)(thunk: String => Unit) {
    try {
      if(sendingEnabled) {
        messageLogger.info(s"Sending to vallu: $payload")
        thunk(payload)
      } else {
        messageLogger.info(s"Messaging is disabled, xml was $payload")
      }
    } catch {
      case e: Exception => {
        applicationLogger.error("Error occurred", e)
        messageLogger.error("=====Error in sending Message to Vallu, message below ======")
        messageLogger.error(payload)
      }
    }
  }
}

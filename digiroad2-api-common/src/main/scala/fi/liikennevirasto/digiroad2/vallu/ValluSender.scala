package fi.liikennevirasto.digiroad2.vallu

import java.nio.charset.Charset

import com.newrelic.api.agent.NewRelic
import fi.liikennevirasto.digiroad2.{EventBusMassTransitStop, Digiroad2Context}
import fi.liikennevirasto.digiroad2.util.AssetPropertiesReader
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.slf4j.LoggerFactory

object ValluSender extends AssetPropertiesReader {
  val messageLogger = LoggerFactory.getLogger("ValluMsgLogger")
  val applicationLogger = LoggerFactory.getLogger(getClass)
  val sendingEnabled = Digiroad2Context.getProperty("digiroad2.vallu.server.sending_enabled").toBoolean
  val address = Digiroad2Context.getProperty("digiroad2.vallu.server.address")

  val config = RequestConfig.custom()
    .setSocketTimeout(60 * 1000)
    .setConnectTimeout(60 * 1000)
    .build()

  val httpClient = HttpClients.custom().setDefaultRequestConfig(config).build()

  def postToVallu(massTransitStop: EventBusMassTransitStop) {
    val payload = ValluStoreStopChangeMessage.create(massTransitStop)
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
      messageLogger.info(s"Got response (${response.getStatusLine.getStatusCode}) ${EntityUtils.toString(response.getEntity , Charset.forName("UTF-8"))}")
      EntityUtils.consume(entity)
    } finally {
      response.close()
    }
  }

  def withLogging[A](payload: String)(thunk: String => Unit) {
    try {
      if (sendingEnabled) {
        messageLogger.info(s"Sending to vallu: $payload")
        thunk(payload)
      } else {
        messageLogger.info(s"Messaging is disabled, xml was $payload")
      }
    } catch {
      case e: Exception => {
        NewRelic.noticeError(e)
        applicationLogger.error("Error occurred", e)
        messageLogger.error("=====Error in sending Message to Vallu, message below ======")
        messageLogger.error(payload)
      }
    }
  }
}

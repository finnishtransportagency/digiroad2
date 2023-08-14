package fi.liikennevirasto.digiroad2.vallu

import java.nio.charset.Charset
import fi.liikennevirasto.digiroad2.EventBusMassTransitStop
import fi.liikennevirasto.digiroad2.util.{AssetPropertiesReader, Digiroad2Properties}
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.slf4j.LoggerFactory

object ValluSender extends AssetPropertiesReader {
  val applicationLogger = LoggerFactory.getLogger(getClass)
  val sendingEnabled = Digiroad2Properties.valluServerSendingEnabled
  val address = Digiroad2Properties.valluServerAddress

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
    httpPost.addHeader("X-API-Key",Digiroad2Properties.valluApikey)
    httpPost.setEntity(entity)
    val response = httpClient.execute(httpPost)
    try {
      applicationLogger.info(s"VALLU Got response (${response.getStatusLine.getStatusCode}) ${EntityUtils.toString(response.getEntity , Charset.forName("UTF-8"))}")
      EntityUtils.consume(entity)
    } finally {
      response.close()
    }
  }

  def withLogging[A](payload: String)(thunk: String => Unit) {
    try {
      if (sendingEnabled) {
        applicationLogger.info(s"VALLU Sending to vallu: $payload")
        thunk(payload)
      } else {
        applicationLogger.info(s"VALLU Messaging is disabled, xml was $payload")
      }
    } catch {
      case e: Exception => {

        applicationLogger.error("VALLU Error occurred", e)
        applicationLogger.error("=====Error in sending Message to Vallu, message below ======")
        applicationLogger.error(payload)

      }
    }
  }
}

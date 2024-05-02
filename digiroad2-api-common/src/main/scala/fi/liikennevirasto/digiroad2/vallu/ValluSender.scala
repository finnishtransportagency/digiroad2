package fi.liikennevirasto.digiroad2.vallu

import fi.liikennevirasto.digiroad2.EventBusMassTransitStop
import fi.liikennevirasto.digiroad2.util.{AssetPropertiesReader, Digiroad2Properties}
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.slf4j.LoggerFactory

import java.nio.charset.Charset

object ValluSender extends AssetPropertiesReader {
  val logger = LoggerFactory.getLogger(getClass)
  val sendingEnabled = Digiroad2Properties.valluServerSendingEnabled
  val address = Digiroad2Properties.valluServerAddress

  val config = RequestConfig.custom()
    .setSocketTimeout(60 * 1000)
    .setConnectTimeout(60 * 1000)
    .build()
  def postToVallu(massTransitStop: EventBusMassTransitStop) {
    val payload = ValluStoreStopChangeMessage.create(massTransitStop)
    withLogging(payload) {
      postToVallu
    }
  }
  
  private def postToVallu(payload: String) = {
    // create new client when sending rather than use global variable.
    // Scala object is singleton so it will reuse already created client.
    // For Vallu message speed is not priority but that messages is jus sent.
    val client = HttpClients.custom().setDefaultRequestConfig(config).build()
    val entity = new StringEntity(payload, ContentType.create("application/xml", "UTF-8"))
    val httpPost = new HttpPost(address)
    httpPost.addHeader("X-API-Key",Digiroad2Properties.valluApikey)
    httpPost.setEntity(entity)
    val response = client.execute(httpPost)
    try {
      logger.info(s"VALLU Got response (${response.getStatusLine.getStatusCode})")
      logger.info(s"VALLU Response body was ${EntityUtils.toString(response.getEntity , Charset.forName("UTF-8"))}")
      EntityUtils.consume(entity)
    } finally {
      response.close()
    }
  }

  def withLogging[A](payload: String)(thunk: String => Unit) {
    try {
      val payloadForLogging = payload.replaceAll("\n","")
      if (sendingEnabled) {
        logger.info(s"VALLU Sending to vallu: $payloadForLogging")
        thunk(payload)
      } else {
        logger.info(s"VALLU Messaging is disabled, xml was $payloadForLogging")
      }
    } catch {
      case e: Exception => {
        logger.error("VALLU Error occurred", e)
        logger.error("=====Error in sending Message to Vallu, message below ======")
        logger.error(payload.replaceAll("\n",""))
      }
    }
  }
}

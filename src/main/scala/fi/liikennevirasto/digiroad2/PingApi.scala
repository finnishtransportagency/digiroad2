package fi.liikennevirasto.digiroad2

import java.util.Properties

import org.apache.http.client.config.RequestConfig
import org.apache.http.conn.{ConnectTimeoutException, HttpHostConnectException}
import org.scalatra.{BadGateway, InternalServerError, Ok, ScalatraServlet}
import org.slf4j.LoggerFactory
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder

class PingApi extends ScalatraServlet {
  val logger = LoggerFactory.getLogger(getClass)

  get("/") {
    try {
      logger.info("ping()")
      Digiroad2Context.userProvider.getUser("")
      logger.info("ping()")
      val properties = new Properties()
      logger.info("ping()")
      properties.load(getClass.getResourceAsStream("/digiroad2.properties"))
      logger.info("ping() props")
      val role = properties.getProperty("digiroad2.server.role", "unknown")
      logger.info("ping() role = " + role)
      if ("standby".equalsIgnoreCase(role) && handleStandby(properties) || "master".equalsIgnoreCase(role)) {
        logger.info("ping() OK")
        Ok("OK")
      } else {
        if (!"standby".equalsIgnoreCase(role)) {
          logger.info("no role specified!")
          InternalServerError("No role specified!")
        } else {
          BadGateway("Master is alive")
        }
      }
    } catch {
      case e: Exception =>
        logger.error("DB connection error", e)
        InternalServerError("Database ping failed")
    }
  }
  def handleStandby(properties: Properties) = {
    val url = properties.getProperty("digiroad2.server.master", "http://172.17.205.43:8080/digiroad/monitor")
    val config = RequestConfig.custom().
      setConnectTimeout(2000).setConnectionRequestTimeout(2000).setSocketTimeout(2000).build()
    val request = new HttpGet(url)
    val builder = HttpClientBuilder.create().setDefaultRequestConfig(config).build()
    try {
      val response = builder.execute(request)
      !response.getStatusLine.getStatusCode.equals(200)
    } catch {
      case e: ConnectTimeoutException =>
        true
      case e: HttpHostConnectException =>
        true
    }
  }
}

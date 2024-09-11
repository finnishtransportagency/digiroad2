package fi.liikennevirasto.digiroad2.util

import org.apache.http.client.config.{CookieSpecs, RequestConfig}
import org.apache.http.impl.client.{CloseableHttpClient, HttpClientBuilder}
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.slf4j.{Logger, LoggerFactory}

import scala.annotation.tailrec

object ClientUtils {

  val connectionManager = new PoolingHttpClientConnectionManager()
  connectionManager.setMaxTotal(1000)
  connectionManager.setDefaultMaxPerRoute(1000)

  @tailrec
  def retry[T](retries: Int, logger: Logger,exponentToLoop: Int =1, firstRun : Boolean= true, commentForFailing : String = "")(fn: => T): T = {
    var exponent = exponentToLoop
    try {
      fn
    } catch {
      case e: Throwable =>
        logger.error(s"TEST LOG Query failed (retries left: ${retries - 1}). Error: ${e.getMessage}")
        
        if (firstRun && commentForFailing.nonEmpty) {
          logger.error(commentForFailing)
        }
        
        if (retries > 1){
          Thread.sleep(500*exponent) // wait before making new request
          exponent+=1
          retry(retries - 1, logger,exponent, firstRun = false)(fn)
        }
        else throw e
      
    }
  }

  private val logger = LoggerFactory.getLogger(this.getClass)

  def clientBuilder(timeout: Int = 60 * 1000): CloseableHttpClient = {

    HttpClientBuilder.create()
      .setConnectionManager(connectionManager)
      .setDefaultRequestConfig(
        RequestConfig.custom()
          .setCookieSpec(CookieSpecs.STANDARD)
          .setSocketTimeout(timeout)
          .setConnectTimeout(timeout)
          .build()
      )
      .build()
  }

  def logConnectionPoolStats(): Unit = {
    val totalStats = connectionManager.getTotalStats
    logger.info(s"Max connections: ${totalStats.getMax}")
    logger.info(s"Available connections: ${totalStats.getAvailable}")
    logger.info(s"Leased (active) connections: ${totalStats.getLeased}")
    logger.info(s"Pending connections: ${totalStats.getPending}")
  }
}

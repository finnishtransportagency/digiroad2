package fi.liikennevirasto.digiroad2.util

import org.apache.http.client.config.{CookieSpecs, RequestConfig}
import org.apache.http.impl.NoConnectionReuseStrategy
import org.apache.http.impl.client.{CloseableHttpClient, HttpClientBuilder}
import org.apache.http.impl.conn.BasicHttpClientConnectionManager
import org.slf4j.Logger

import scala.annotation.tailrec

object ClientUtils {

  @tailrec
  def retry[T](retries: Int, logger: Logger, exponentToLoop: Int = 1, firstRun: Boolean = true, commentForFailing: String = "")(fn: => T): T = {
    var exponent = exponentToLoop
    try {
      fn
    } catch {
      case e: Throwable =>
        logger.error(s"TEST LOG Query failed (retries left: ${retries - 1}). Error: ${e.getMessage}")
        if (retries > 1) {
          Thread.sleep(500 * exponent) // wait before making new request
          exponent += 1
          retry(retries - 1, logger, exponent, firstRun = false)(fn)
        }
        else {
          if (commentForFailing.nonEmpty) {
            logger.error(commentForFailing)
          }
          throw e
        }
    }
  }
  

  def clientBuilder(timeout: Int = 10 * 1000): CloseableHttpClient = {

    HttpClientBuilder.create()
      .setConnectionManager(new BasicHttpClientConnectionManager()) // Use a basic connection manager (no pooling)
      .setConnectionReuseStrategy(NoConnectionReuseStrategy.INSTANCE) // Disable connection reuse (no persistent connections)
      .setDefaultRequestConfig(
        RequestConfig.custom()
          .setCookieSpec(CookieSpecs.STANDARD)
          .setSocketTimeout(timeout)
          .setConnectTimeout(timeout)
          .build()
      )
      .build()
  }
}

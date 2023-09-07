package fi.liikennevirasto.digiroad2.util

import org.apache.http.client.config.{CookieSpecs, RequestConfig}
import org.apache.http.impl.client.{CloseableHttpClient, HttpClientBuilder}
import org.slf4j.Logger

import scala.annotation.tailrec

object ClientUtils {

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

  def clientBuilder(
                     maxConnTotal: Int = 1000,
                     maxConnPerRoute: Int = 1000,
                     timeout: Int = 60 * 1000
                   ): CloseableHttpClient = {
    HttpClientBuilder.create()
      .setDefaultRequestConfig(
        RequestConfig.custom()
          .setCookieSpec(CookieSpecs.STANDARD)
          .setSocketTimeout(timeout)
          .setConnectTimeout(timeout)
          .build()
      )
      .setMaxConnTotal(maxConnTotal)
      .setMaxConnPerRoute(maxConnPerRoute)
      .build()
  }
}

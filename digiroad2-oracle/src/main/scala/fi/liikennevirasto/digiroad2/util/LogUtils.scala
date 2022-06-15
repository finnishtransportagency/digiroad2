package fi.liikennevirasto.digiroad2.util

import org.slf4j.Logger

object LogUtils {
  val timeLoggingThresholdInMs = 100

  def time[R](logger: Logger, operationName: String, noFilter: Boolean = false, url :Option[String] = None)(f: => R): R = {
    val begin = System.currentTimeMillis()
    val result = f
    val duration = System.currentTimeMillis() - begin
    val urlString = if ( url.isDefined) {s"URL: ${url.get}"}else ""
    if (noFilter) {
      logger.info(s"$operationName completed in $duration ms and in second ${duration / 1000}, ${urlString}")
    } else {
      if (duration >= timeLoggingThresholdInMs) {
        logger.info(s"$operationName completed in $duration ms and in second ${duration / 1000}, ${urlString}")
      }
    }
    result
  }

}
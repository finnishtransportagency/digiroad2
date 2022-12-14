package fi.liikennevirasto.digiroad2.util

import org.slf4j.Logger

object LogUtils {
  val timeLoggingThresholdInMs = 100

  def time[R](logger: Logger, operationName: String, noFilter: Boolean = false, url :Option[String] = None)(f: => R): R = {
    val begin = System.currentTimeMillis()

    try {
      val result = f
      val duration = System.currentTimeMillis() - begin
      val urlString = if (url.isDefined) {
        s"URL: ${url.get}"
      } else ""
      if (noFilter) {
        logger.info(s"$operationName completed in $duration ms and in second ${duration / 1000}, ${urlString}")
      } else {
        if (duration >= timeLoggingThresholdInMs) {
          logger.info(s"$operationName completed in $duration ms and in second ${duration / 1000}, ${urlString}")
        }
      }
      result
    } catch {
      case e: Exception =>
        val errorString = e.getStackTrace.find(st => st.getClassName.startsWith("fi.liikennevirasto")) match {
          case Some(lineFound) => s"at $lineFound"
          case _ => s""
        }
        val duration = System.currentTimeMillis() - begin
        logger.error(s"$operationName failed,it took  $duration ms and in second ${duration / 1000}. Operation run ${e.getClass.getName}: ${e.getMessage} $errorString")
        throw e
    }
    
  }

}
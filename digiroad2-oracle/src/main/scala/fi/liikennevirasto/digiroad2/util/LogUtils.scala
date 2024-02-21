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
      if (noFilter || duration >= timeLoggingThresholdInMs) {
        logger.info(s"$operationName completed in $duration ms and in second ${duration / 1000}, $urlString")
      }
      result
    } catch {
      case e: Exception =>
        val errorString = e.getStackTrace.find(st => st.getClassName.startsWith("fi.liikennevirasto")) match {
          case Some(lineFound) => s"at $lineFound"
          case _ => s""
        }
        val duration = System.currentTimeMillis() - begin
        logger.error(s"$operationName failed at $duration ms. Operation run ${e.getClass.getName}: ${e.getMessage} $errorString")
        throw e
    }
    
  }

  /**
    * Used to log progress of processing an array of elements every 10%
    * @param arraySize Size of the array to process
    * @param index Index of the current element to process
    * @param lastTenPercent Last 10% threshold to be printed
    * @return Percentage of elements processed, rounded down to next 10
    */
  def logArrayProgress(logger: Logger, operationName: String, arraySize: Long, index: Long, lastTenPercent: Int): Int = {
    val percentageProcessed = (((index + 1).toDouble / arraySize.toDouble) * 100).toInt
    val currentTenPercent = (percentageProcessed / 10) * 10

    if (currentTenPercent != lastTenPercent) {
      logger.info(s"$operationName is $currentTenPercent% complete")
    }
    currentTenPercent
  }

}
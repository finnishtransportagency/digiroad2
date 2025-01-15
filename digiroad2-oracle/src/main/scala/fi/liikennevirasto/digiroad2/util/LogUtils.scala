package fi.liikennevirasto.digiroad2.util

import java.util.function.BiConsumer
import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}
import org.slf4j.Logger

object LogUtils {
  val timeLoggingThresholdInMs = 100

  def time[R](logger: Logger, operationName: String, noFilter: Boolean = false, url :Option[String] = None, startLogging:Boolean= false)(f: => R): R = {
    val begin = System.currentTimeMillis()

    try {
      if (startLogging) {
        logger.info(s"$operationName started")
      }
      val result = f
      val duration = System.currentTimeMillis() - begin
      val urlString = if (url.isDefined) {
        s"URL: ${url.get}"
      } else ""
      
      if (noFilter || duration >= timeLoggingThresholdInMs) {
        logger.info(s"$operationName completed in $duration ms and in second ${duration / 1000}, $urlString")
      }
      if (startLogging) {
        logger.info(s"$operationName finished")
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

  /**
   * Logs the state of all active threads at a fixed interval.
   *
   * @param logger          The logger to use for output.
   * @param intervalSeconds The interval in seconds between each thread state dump.
   * @return ScheduledExecutorService to allow managing the scheduled task.
   */
  def logThreadStatesAtInterval(logger: Logger, intervalSeconds: Long): ScheduledExecutorService = {
    val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(1)

    val logThreadStates: Runnable = new Runnable {
      override def run(): Unit = {
        val allThreads = Thread.getAllStackTraces
        logger.info(s"=== Thread State Dump at ${java.time.LocalDateTime.now} ===")

        allThreads.forEach(new BiConsumer[Thread, Array[StackTraceElement]] {
          override def accept(thread: Thread, stackTrace: Array[StackTraceElement]): Unit = {
            logger.info(s"Thread: ${thread.getName}, State: ${thread.getState}")
          }
        })

        logger.info("=============================================")
      }
    }

    executor.scheduleAtFixedRate(logThreadStates, 0, intervalSeconds, TimeUnit.SECONDS)
    executor
  }

}
package fi.liikennevirasto.digiroad2

import org.scalatra.{Ok, ScalatraServlet}
import org.slf4j.LoggerFactory

import java.lang.management.ManagementFactory

class DebugApi extends ScalatraServlet {
 private val logger = LoggerFactory.getLogger(getClass)
 private val runtime: Runtime = Runtime.getRuntime

  private def logRuntimeStatistics(runtime: Runtime): Unit = {
    val mb = 1024 * 1024
    
    logger.info(s"Total number of threads in instance: ${ManagementFactory.getThreadMXBean.getThreadCount}")
    logger.info(s"CPU count of instance: ${runtime.availableProcessors()}")
    logger.info(s"Used memory of instance: ${(runtime.totalMemory() - runtime.freeMemory()) / mb} MB")
    logger.info(s"Free memory of instance: ${runtime.freeMemory() / mb} MB")
    logger.info(s"Total memory of instance: ${runtime.totalMemory() / mb} MB")
    logger.info(s"Max memory of instance: ${runtime.maxMemory() / mb} MB")
  }
  
  get("/") {
    try {
      logRuntimeStatistics(runtime)
      Ok()
    } catch {
      case e: Exception =>
        logger.error(s"Some error showing debug info: ${e.toString}")
    }
  }
}

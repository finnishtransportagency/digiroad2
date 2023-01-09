package fi.liikennevirasto.digiroad2.util

import org.slf4j.Logger
import scala.annotation.tailrec

object ClientUtils {

  @tailrec
  def retry[T](retries: Int, logger: Logger,exponentToLoop: Int =1)(fn: => T): T = {
    var exponent = exponentToLoop
    try {
      fn
    } catch {
      case e: Throwable =>
        logger.error(s"TEST LOG Query failed (retries left: ${retries - 1}). Error: ${e.getMessage}")
        if (retries > 1){
          Thread.sleep(500*exponent) // wait before making new request
          exponent+=1
          retry(retries - 1, logger,exponent)(fn)
        }
        else throw e
      
    }
  }
}

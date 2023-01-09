package fi.liikennevirasto.digiroad2.util

import org.slf4j.Logger
import scala.annotation.tailrec

object ClientUtils {

  @tailrec
  def retry[T](retries: Int, logger: Logger)(fn: => T): T = {
    val exponent = 1
    try {
      fn
    } catch {
      case e: Throwable =>
        logger.error(s"TEST LOG Query failed (retries left: ${retries - 1}). Error: ${e.getMessage}")
        if (retries > 1){
          Thread.sleep(500*(exponent + 1)) // wait before making new request
          retry(retries - 1, logger)(fn)
        } 
        else throw e
    }
  }


}

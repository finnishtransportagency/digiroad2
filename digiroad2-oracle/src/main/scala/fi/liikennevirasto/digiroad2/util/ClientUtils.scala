package fi.liikennevirasto.digiroad2.util

import scala.annotation.tailrec

object ClientUtils {

  @tailrec
  def retry[T](retries: Int)(fn: => T): T = {
    try {
      fn
    } catch {
      case e =>
        if (retries > 1) retry(retries - 1)(fn)
        else throw e
    }
  }


}

package fi.liikennevirasto.digiroad2

import org.scalatra.{InternalServerError, Ok, ScalatraServlet}
import org.slf4j.LoggerFactory

class PingApi extends ScalatraServlet {
  val logger = LoggerFactory.getLogger(getClass)

  get("/") {
    try {
      Digiroad2Context.userProvider.getUser("")
      Ok("OK")
    } catch {
      case e: Exception =>
        logger.error("DB connection error", e)
        InternalServerError("Database ping failed")
    }
  }
}

package fi.liikennevirasto.digiroad2

import org.json4s.DefaultFormats
import org.json4s.Formats
import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport
import org.slf4j.LoggerFactory

class IntegrationApi extends ScalatraServlet with JacksonJsonSupport {
  val logger = LoggerFactory.getLogger(getClass)
  protected implicit val jsonFormats: Formats = DefaultFormats

  get("/data") {
    "Hello, world!\n"
  }
}

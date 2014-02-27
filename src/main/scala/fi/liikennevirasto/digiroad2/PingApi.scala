package fi.liikennevirasto.digiroad2

import org.scalatra.{Ok, ScalatraServlet}

class PingApi extends ScalatraServlet {
  get("/ping") {
    Ok("OK")
  }
}

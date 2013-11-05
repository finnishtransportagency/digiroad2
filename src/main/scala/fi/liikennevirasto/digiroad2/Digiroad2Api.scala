package fi.liikennevirasto.digiroad2

import org.scalatra._

class Digiroad2Api extends ScalatraServlet {

  get("/ping") {
    "pong"
  }
  
}

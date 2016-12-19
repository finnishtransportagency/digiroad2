package fi.liikennevirasto.digiroad2.authentication

import org.scalatra._
import fi.liikennevirasto.digiroad2.Digiroad2Context._
import org.slf4j.LoggerFactory
import fi.liikennevirasto.digiroad2.user.Configuration

class SessionApi extends ScalatraServlet {
  val logger = LoggerFactory.getLogger(getClass)
  def defaultConfig(municipalityNumber: String): Configuration = municipalityNumber match {
    case "235" => Configuration(zoom = Some(8), east = Some(373560), north = Some(6677676), municipalityNumber = Some(235), authorizedMunicipalities = Set(235))
    case "837" => Configuration(zoom = Some(7), east = Some(328308), north = Some(6822545), municipalityNumber = Some(837), authorizedMunicipalities = Set(837))
    case _ => Configuration()
  }

  before() {
    response.setHeader(Digiroad2ServerOriginatedResponseHeader, "true")
  }

  post("/session") {
    val username = request.getParameter("username")
    cookies.set("testusername", username)
    redirect(url("index.html"))
  }

  post("/user") {
    val (username, municipalityNumber) = (request.getParameter("username"), request.getParameter("municipalityNumber"))
    val configMap = defaultConfig(municipalityNumber)
    userProvider.getUser(username) match {
      case Some(u) => {
        logger.info("User exists: " + username + "(" + u + ")")
        BadRequest("User exists")
      }
      case _ => {
        userProvider.createUser(username, configMap)
        Ok("User created")
      }
    }
  }
}
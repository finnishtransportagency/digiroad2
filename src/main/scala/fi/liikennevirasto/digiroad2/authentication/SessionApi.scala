package fi.liikennevirasto.digiroad2.authentication

import org.scalatra._
import fi.liikennevirasto.digiroad2.Digiroad2Context._
import org.apache.commons.validator.routines.EmailValidator
import org.slf4j.LoggerFactory

class SessionApi extends ScalatraServlet with AuthenticationSupport {
  val logger = LoggerFactory.getLogger(getClass)
  def defaultConfig(municipalityNumber: String): Map[String, String] = municipalityNumber match {
    case "235" => Map("zoom" -> "8", "east" -> "373560", "north"-> "6677676", "municipalityNumber" -> "235")
    case "837" => Map("zoom" -> "7", "east" -> "328308", "north"-> "6822545", "municipalityNumber" -> "837")
    case _ => Map()
  }

  post("/session") {
    scentry.authenticate()
    if (isAuthenticated) {
      redirect("/index.html")
    } else {
      redirect("/login.html")
    }
  }

  post("/user") {
    val (username, email, password, passwordConfirm, municipalityNumber) =
      (request.getParameter("username"), request.getParameter("email"),
        request.getParameter("password"), request.getParameter("passwordConfirm"), request.getParameter("municipalityNumber"))
    val configMap = defaultConfig(municipalityNumber)
    if (password != passwordConfirm) {
      BadRequest("Passwords do not match")
    } else if (password.length < 6) {
        BadRequest("Password must be at least 6 characters")
    } else if (!EmailValidator.getInstance().isValid(email)) {
        BadRequest("Must provide a valid email address")
    } else {
      userProvider.getUser(username) match {
        case Some(u) => {
          logger.info("User exists: " + username + "(" + u + ")")
          BadRequest("User exists")
        }
        case _ => {
          userProvider.createUser(username, password, email, configMap)
          Ok("User created")
        }
      }
    }

  }
}
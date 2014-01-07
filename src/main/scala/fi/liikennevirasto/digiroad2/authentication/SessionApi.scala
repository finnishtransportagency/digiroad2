package fi.liikennevirasto.digiroad2.authentication

import org.scalatra._
import fi.liikennevirasto.digiroad2.Digiroad2Context._
import org.apache.commons.validator.routines.EmailValidator

class SessionApi extends ScalatraServlet with AuthenticationSupport {

  post("/session") {
    scentry.authenticate()
    if (isAuthenticated) {
      redirect("/index.html")
    } else {
      redirect("/login.html")
    }
  }

  post("/user") {
    val (username, email, password, passwordConfirm) =
      (request.getParameter("username"), request.getParameter("email"),
        request.getParameter("password"), request.getParameter("passwordConfirm"))
    if (password != passwordConfirm) {
      BadRequest("Passwords do not match")
    } else if (password.length < 6) {
        BadRequest("Password must be at least 6 characters")
    } else if (!EmailValidator.getInstance().isValid(email)) {
        BadRequest("Must provide a valid email address")
    } else {
      userProvider.getUser(username) match {
        case Some(u) => BadRequest("User exists")
        case _ => {
          userProvider.createUser(username, password, email)
          Ok("User created")
        }
      }
    }

  }
}
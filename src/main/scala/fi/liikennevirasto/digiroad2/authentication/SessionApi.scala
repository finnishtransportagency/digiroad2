package fi.liikennevirasto.digiroad2.authentication

import org.scalatra.ScalatraServlet


class SessionApi extends ScalatraServlet with AuthenticationSupport {

  post("/session") {
    scentry.authenticate()
    if (isAuthenticated) {
      redirect("/index.html")
    } else {
      redirect("/login.html")
    }
  }

  // Never do this in a real app. State changes should never happen as a result of a GET request. However, this does
  // make it easier to illustrate the logout code.
  get("/logout") {
    scentry.logout()
    redirect("/")
  }
}
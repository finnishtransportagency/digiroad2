package fi.liikennevirasto.digiroad2.authentication

import fi.liikennevirasto.digiroad2.user.User
import org.scalatra.ScalatraBase
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.scalatra.auth.ScentryStrategy
import org.slf4j.LoggerFactory
import fi.liikennevirasto.digiroad2.Digiroad2Context

class UserPasswordAuthStrategy(protected override val app: ScalatraBase)(implicit request: HttpServletRequest, response: HttpServletResponse)
  extends ScentryStrategy[User] {

  val logger = LoggerFactory.getLogger(getClass)

  override def name: String = "UserPassword"

  private def login = app.params.getOrElse("login", "")
  private def password = app.params.getOrElse("password", "")

  override def isValid(implicit request: HttpServletRequest) = {
    logger.info("UserPasswordAuthStrategy: determining isValid: " + (login == password).toString())
    (login != "" && password != "")
  }

  def authenticate()(implicit request: HttpServletRequest, response: HttpServletResponse): Option[User] = {
    Digiroad2Context.userProvider.getAuthenticatedUser(login, password)
  }

  override def unauthenticated()(implicit request: HttpServletRequest, response: HttpServletResponse) {
    app.redirect("/login.html")
  }
}
package fi.liikennevirasto.digiroad2.authentication

import fi.liikennevirasto.digiroad2.user.User
import org.scalatra.ScalatraBase
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.scalatra.auth.ScentryStrategy
import org.slf4j.LoggerFactory

class UserPasswordAuthStrategy(protected override val app: ScalatraBase)(implicit request: HttpServletRequest, response: HttpServletResponse)
  extends ScentryStrategy[User] {

  val logger = LoggerFactory.getLogger(getClass)

  override def name: String = "UserPassword"

  private def login = app.params.getOrElse("login", "")
  private def password = app.params.getOrElse("password", "")

  override def isValid(implicit request: HttpServletRequest) = {
    // TODO: placeholder validation
    logger.info("UserPasswordStrategy: determining isValid: " + (login == password).toString())
    (login == password && login != "")
  }

  def authenticate()(implicit request: HttpServletRequest, response: HttpServletResponse): Option[User] = {
    // TODO: retrieve user data
    logger.info("UserPasswordStrategy: attempting authentication")

    if(login == password && login != "") {
      logger.info("UserPasswordStrategy: login succeeded")
      Some(User(login))
    } else {
      logger.info("UserPasswordStrategy: login failed")
      None
    }
  }

  override def unauthenticated()(implicit request: HttpServletRequest, response: HttpServletResponse) {
    app.redirect("/login.html")
  }
}